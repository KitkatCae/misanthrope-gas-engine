package exp.CCnewmods.mge.grid;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.grid.section.EnvironmentSection;
import exp.CCnewmods.mge.grid.tick.SectionDiffusionTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages which {@link EnvironmentSection}s are actively ticking and at what
 * rate, and owns the {@link SectionDiffusionTicker} dirty queue.
 *
 * ── Tick priority tiers ────────────────────────────────────────────────────────
 * PRIORITY_FULL (0):        section is near a player → ticks every server tick
 * PRIORITY_BACKGROUND (1):  section has a keepalive marker or heat source → every 10 ticks
 * PRIORITY_FROZEN (2):      section is unloaded or irrelevant → no ticks
 *
 * ── Unloaded Activity integration ─────────────────────────────────────────────
 * If the Unloaded Activity mod is present, its {@code ChunkTimeData.getLastTick()} API
 * is used to retrieve the list of game-time stamps when the chunk was loaded.
 * From those we compute missed ticks and run compressed batch simulation on load.
 * If UA is absent, we fall back to {@code level.getGameTime() - lastTickGameTime}.
 *
 * ── Keepalive markers ─────────────────────────────────────────────────────────
 * Any code can call {@link #addKeepalive(ServerLevel, BlockPos)} to keep a
 * section in BACKGROUND priority even when no players are nearby.  Used by
 * the thermal structure system for large furnaces (rotary kiln, etc.).
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SectionLoadManager {

    // ── Player proximity radius for FULL priority ─────────────────────────────
    private static final int FULL_PRIORITY_CHUNK_RADIUS = 8; // chunks (~128 blocks)

    // ── Background tick interval ──────────────────────────────────────────────
    public static final int BACKGROUND_TICK_INTERVAL = 10;

    // ── Priority re-evaluation interval ──────────────────────────────────────
    private static final int PRIORITY_EVAL_INTERVAL = 40; // every 2 seconds

    // ── Per-level state ───────────────────────────────────────────────────────

    private static final Map<ServerLevel, LevelState> STATES = new ConcurrentHashMap<>();

    private static boolean uaLoaded = false;
    private static boolean uaChecked = false;

    // ── Chunk load/unload callbacks ───────────────────────────────────────────

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk,
                                    EnvironmentChunkData data) {
        LevelState state = STATES.computeIfAbsent(level, LevelState::new);
        state.onChunkLoad(chunk, data);
    }

    public static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
        LevelState state = STATES.get(level);
        if (state != null) state.onChunkUnload(chunk);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!uaChecked) {
            uaLoaded = ModList.get().isLoaded("unloaded_activity") ||
                       ModList.get().isLoaded("unloadedactivity");
            uaChecked = true;
            if (uaLoaded) Mge.LOGGER.info("[MGE Grid] Unloaded Activity detected — catch-up simulation enabled.");
        }
        for (LevelState state : STATES.values()) {
            state.tick();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        STATES.clear();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Add a keepalive marker at a world position (keeps section at BACKGROUND priority). */
    public static void addKeepalive(ServerLevel level, BlockPos pos) {
        LevelState state = STATES.get(level);
        if (state != null) state.addKeepalive(new ChunkPos(pos));
    }

    public static void removeKeepalive(ServerLevel level, BlockPos pos) {
        LevelState state = STATES.get(level);
        if (state != null) state.removeKeepalive(new ChunkPos(pos));
    }

    /** Get the diffusion ticker's dirty queue for enqueueing positions. */
    public static SectionDiffusionTicker getScheduler(ServerLevel level) {
        return STATES.computeIfAbsent(level, LevelState::new).ticker;
    }

    /**
     * Returns an unmodifiable snapshot of the chunk positions currently tracked
     * as loaded by MGE for the given level.
     *
     * <p>Safe to iterate from the server thread. Used by plant respiration and
     * other systems that need to walk loaded chunks without going through
     * {@code ChunkMap} (which has protected access).</p>
     */
    public static java.util.Set<net.minecraft.world.level.ChunkPos> getLoadedChunkPositions(ServerLevel level) {
        LevelState state = STATES.get(level);
        if (state == null) return java.util.Collections.emptySet();
        return java.util.Collections.unmodifiableSet(state.loadedChunks.keySet());
    }

    public static boolean isUALoaded() { return uaLoaded; }

    // ── LevelState ────────────────────────────────────────────────────────────

    private static final class LevelState {
        private final ServerLevel level;
        final SectionDiffusionTicker ticker;

        /** Chunks currently loaded with their data. */
        private final Map<ChunkPos, EnvironmentChunkData> loadedChunks = new HashMap<>();

        /** Set of chunk positions with keepalive markers. */
        private final Set<ChunkPos> keepalives = new HashSet<>();

        /** Chunk positions currently in FULL priority (near players). */
        private final Set<ChunkPos> fullPriorityChunks = new HashSet<>();

        private int evalTick = 0;

        LevelState(ServerLevel level) {
            this.level  = level;
            this.ticker = new SectionDiffusionTicker(level);
        }

        void onChunkLoad(LevelChunk chunk, EnvironmentChunkData data) {
            ChunkPos cp = chunk.getPos();
            loadedChunks.put(cp, data);

            // Compute missed ticks for catch-up simulation
            long missedTicks = computeMissedTicks(chunk, data);

            if (missedTicks > 0) {
                catchUp(data, missedTicks);
            }

            // Record current game time in all sections of this chunk
            long now = level.getGameTime();
            for (int i = 0; i < data.sectionCount(); i++) {
                EnvironmentSection sec = data.getByIndex(i);
                if (sec != null) sec.lastTickGameTime = now;
            }

            // Immediately evaluate priority for this chunk
            evaluatePriority(cp, data);

            // Enqueue all non-default sections for diffusion
            for (int i = 0; i < data.sectionCount(); i++) {
                EnvironmentSection sec = data.getByIndex(i);
                if (sec != null && sec.hasAnyGasData()) {
                    int bottomY = level.getMinBuildHeight() + i * 16;
                    int midX = chunk.getPos().getMiddleBlockX();
                    int midZ = chunk.getPos().getMiddleBlockZ();
                    ticker.enqueue(new BlockPos(midX, bottomY + 8, midZ));
                }
            }
        }

        void onChunkUnload(LevelChunk chunk) {
            ChunkPos cp = chunk.getPos();
            EnvironmentChunkData data = loadedChunks.remove(cp);
            if (data == null) return;

            // Mark sections as frozen and record unload time
            long now = level.getGameTime();
            for (int i = 0; i < data.sectionCount(); i++) {
                EnvironmentSection sec = data.getByIndex(i);
                if (sec != null) {
                    sec.lastTickGameTime = now;
                    sec.tickPriority = EnvironmentSection.PRIORITY_FROZEN;
                }
            }
            fullPriorityChunks.remove(cp);
        }

        void addKeepalive(ChunkPos cp) { keepalives.add(cp); }
        void removeKeepalive(ChunkPos cp) { keepalives.remove(cp); }

        void tick() {
            // Re-evaluate priorities periodically
            if (++evalTick >= PRIORITY_EVAL_INTERVAL) {
                evalTick = 0;
                reevaluateAllPriorities();
            }

            // Run the diffusion ticker
            ticker.tick(level.getGameTime());
        }

        private void reevaluateAllPriorities() {
            // Compute which chunks are near players
            Set<ChunkPos> nearPlayer = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                ChunkPos pp = player.chunkPosition();
                for (int dx = -FULL_PRIORITY_CHUNK_RADIUS; dx <= FULL_PRIORITY_CHUNK_RADIUS; dx++) {
                    for (int dz = -FULL_PRIORITY_CHUNK_RADIUS; dz <= FULL_PRIORITY_CHUNK_RADIUS; dz++) {
                        nearPlayer.add(new ChunkPos(pp.x + dx, pp.z + dz));
                    }
                }
            }
            fullPriorityChunks.clear();
            fullPriorityChunks.addAll(nearPlayer);

            // Update all loaded chunks
            for (Map.Entry<ChunkPos, EnvironmentChunkData> entry : loadedChunks.entrySet()) {
                evaluatePriority(entry.getKey(), entry.getValue());
            }
        }

        private void evaluatePriority(ChunkPos cp, EnvironmentChunkData data) {
            byte priority;
            if (fullPriorityChunks.contains(cp)) {
                priority = EnvironmentSection.PRIORITY_FULL;
            } else if (keepalives.contains(cp)) {
                priority = EnvironmentSection.PRIORITY_BACKGROUND;
            } else {
                priority = EnvironmentSection.PRIORITY_BACKGROUND; // all loaded chunks get at least background
            }
            for (int i = 0; i < data.sectionCount(); i++) {
                EnvironmentSection sec = data.getByIndex(i);
                if (sec != null) sec.tickPriority = priority;
            }
        }

        // ── Catch-up simulation ───────────────────────────────────────────────

        private long computeMissedTicks(LevelChunk chunk, EnvironmentChunkData data) {
            // Try Unloaded Activity first
            if (uaLoaded) {
                try {
                    var ctd = (forge.lol.zanspace.unloadedactivity.interfaces.ChunkTimeData) chunk;
                    // getLastTick() returns the game time (in ticks) when this chunk was last active.
                    // Delta vs current game time gives us how long the chunk was unloaded.
                    long lastTick = ctd.getLastTick();
                    if (lastTick > 0) {
                        long missed = level.getGameTime() - lastTick;
                        if (missed > 0) return missed;
                    }
                } catch (Exception e) {
                    // UA not available at runtime despite being loaded — fall through
                }
            }

            // Fall back to game time delta
            long earliest = Long.MAX_VALUE;
            for (int i = 0; i < data.sectionCount(); i++) {
                EnvironmentSection sec = data.getByIndex(i);
                if (sec != null && sec.lastTickGameTime > 0) {
                    earliest = Math.min(earliest, sec.lastTickGameTime);
                }
            }
            if (earliest == Long.MAX_VALUE) return 0;
            long missed = level.getGameTime() - earliest;
            return Math.max(0, missed);
        }

        private void catchUp(EnvironmentChunkData data, long missedTicks) {
            if (missedTicks <= 0) return;

            // Cap at 24 hours of missed ticks to avoid runaway simulation
            long cappedTicks = Math.min(missedTicks, 20 * 60 * 60 * 24L);

            // Compressed batch: run one "super tick" that represents the missed time.
            // For gas diffusion this means scaling the diffusion rate proportionally.
            // We don't simulate every individual tick — we compute the equilibrium state
            // that would result from diffusion over that time.
            //
            // Diffusion over N ticks with rate R: final = initial + (equilibrium - initial) * (1 - (1-R)^N)
            // For large N this converges toward equilibrium. We use this formula directly.
            ticker.enqueueCatchUp(data, cappedTicks);
        }
    }
}
