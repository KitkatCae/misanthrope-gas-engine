package exp.CCnewmods.mge.grid.tick;

import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.EnvironmentChunkData;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.SectionLoadManager;
import exp.CCnewmods.mge.grid.section.EnvironmentSection;
import exp.CCnewmods.mge.permeability.BlockPermeabilityLoader;
import exp.CCnewmods.mge.wind.WindProviderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Per-{@link ServerLevel} gas diffusion processor.
 *
 * Maintains a dirty queue of {@link BlockPos} entries.  Each server tick,
 * dequeues up to {@link MgeConfig#maxBlocksPerTick} positions and runs the
 * 26-neighbour diffusion kernel on the corresponding {@link EnvironmentSection}
 * cells.  Background-priority cells are processed every
 * {@link SectionLoadManager#BACKGROUND_TICK_INTERVAL} ticks.
 *
 * ── Diffusion kernel ──────────────────────────────────────────────────────────
 * For each gas present at the source cell:
 *   transfer = amount × diffusionRate × permeability(neighbour) × weight(direction)
 *   weight = densityBias(dy) × windDot(neighbour_dir) / distanceSquared
 *
 * The same physics as the old AtmosphereTickScheduler, now operating on
 * section float[] arrays instead of block entity NBT.  Significantly faster
 * because there is no NBT serialization per diffusion step.
 *
 * ── Cross-section diffusion ───────────────────────────────────────────────────
 * When a cell at the edge of a section (lx=0,15 / ly=0,15 / lz=0,15) diffuses
 * into a neighbour position that is in an adjacent section or even an adjacent
 * chunk, this ticker resolves the neighbour via {@link EnvironmentGrid} and
 * writes to that section directly.  No special cross-section code needed.
 */
public final class SectionDiffusionTicker {

    private static final int[][] OFFSETS_26 = buildOffsets();

    private final Queue<BlockPos> queue = new ConcurrentLinkedQueue<>();
    private final Set<Long>   queued = Collections.synchronizedSet(new HashSet<>());

    // Catch-up queue: (chunkData, missedTicks) pairs processed at low priority
    private final Queue<CatchUpEntry> catchUpQueue = new ArrayDeque<>();

    private record CatchUpEntry(EnvironmentChunkData data, long missedTicks) {}

    public SectionDiffusionTicker(ServerLevel level) {}

    // ── Enqueue ───────────────────────────────────────────────────────────────

    public void enqueue(BlockPos pos) {
        if (queued.add(pos.asLong())) queue.add(pos.immutable());
    }

    public void enqueueWithNeighbours(BlockPos pos) {
        enqueue(pos);
        for (int[] o : OFFSETS_26) enqueue(pos.offset(o[0], o[1], o[2]));
    }

    public void enqueueCatchUp(EnvironmentChunkData data, long missedTicks) {
        catchUpQueue.add(new CatchUpEntry(data, missedTicks));
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    public void tick(long gameTime) {
        // Process catch-up entries first (one per tick to spread the cost)
        CatchUpEntry catchUp = catchUpQueue.poll();
        if (catchUp != null) processCatchUp(catchUp);

        int processed = 0;
        int budget = MgeConfig.maxBlocksPerTick;

        while (processed < budget) {
            BlockPos pos = queue.poll();
            if (pos == null) break;
            queued.remove(pos.asLong());

            // Priority check — background sections only process every N ticks
            EnvironmentSection sec = getSectionFor(pos);
            if (sec == null) continue;
            if (sec.tickPriority == EnvironmentSection.PRIORITY_FROZEN) continue;
            if (sec.tickPriority == EnvironmentSection.PRIORITY_BACKGROUND) {
                if (gameTime % SectionLoadManager.BACKGROUND_TICK_INTERVAL != 0) {
                    // Re-enqueue for later — don't drop it
                    enqueue(pos);
                    continue;
                }
            }

            diffuseCell(pos, sec);
            processed++;
        }
    }

    // ── Diffusion kernel ──────────────────────────────────────────────────────

    private void diffuseCell(BlockPos pos, EnvironmentSection srcSec) {
        // We use the pos as the key to get the level — passed through a ThreadLocal set by Mge
        ServerLevel level = currentLevel.get();
        if (level == null) return;

        Vec3 wind = WindProviderManager.getWind(level, pos);
        float wx = (float) wind.x, wy = (float) wind.y, wz = (float) wind.z;

        int lx = pos.getX() & 15;
        int ly = (pos.getY() - srcSec.getSectionBottomY()) & 15;
        int lz = pos.getZ() & 15;
        int srcIdx = EnvironmentSection.index(lx, ly, lz);

        // Precompute neighbour permeabilities and weights
        float[] weights   = new float[26];
        float[] perms     = new float[26];
        float[] totalW    = new float[GasRegistry.all().size()]; // per-gas weight sum
        boolean[] hasNeighbour = new boolean[26];

        // First pass: compute weights
        for (int n = 0; n < 26; n++) {
            int dx = OFFSETS_26[n][0], dy = OFFSETS_26[n][1], dz = OFFSETS_26[n][2];
            BlockPos nPos = pos.offset(dx, dy, dz);
            if (!level.isLoaded(nPos)) continue;

            float perm = BlockPermeabilityLoader.getPermeability(
                    level, nPos, level.getBlockState(nPos));
            if (perm <= 0.001f) continue;

            float distSq = dx*dx + dy*dy + dz*dz;
            float w = perm / distSq; // inverse square, modulated by permeability

            // Wind bias
            float len = (float) Math.sqrt(distSq);
            float dot = (dx*wx + dy*wy + dz*wz) / len;
            w *= (1f + Math.max(0f, dot));

            weights[n]    = w;
            perms[n]      = perm;
            hasNeighbour[n] = true;
        }

        // Second pass: diffuse each gas
        float rate = MgeConfig.diffusionRate;

        for (Gas gas : GasRegistry.all()) {
            float[] srcArr = srcSec.gasArrayDirect(gas);
            float srcMbar;
            if (srcArr != null) {
                srcMbar = srcArr[srcIdx];
            } else {
                continue; // gas entirely absent from section — nothing to diffuse
            }
            if (srcMbar <= MgeConfig.gasPruneThresholdMbar) continue;

            double density = gas.properties().densityRatioToAir();
            float windSens = gas.properties().windSensitivity();

            float totalWeight = 0f;
            for (int n = 0; n < 26; n++) {
                if (!hasNeighbour[n] || weights[n] <= 0f) continue;
                int dy = OFFSETS_26[n][1];

                // Check if neighbour has lower concentration
                BlockPos nPos = pos.offset(OFFSETS_26[n][0], dy, OFFSETS_26[n][2]);
                EnvironmentSection nSec = EnvironmentGrid.getSection(level, nPos);
                float nMbar = nSec != null ? nSec.getGas(gas, nPos.getX()&15,
                        (nPos.getY()-nSec.getSectionBottomY())&15, nPos.getZ()&15)
                        : EnvironmentGrid.getGas(level, nPos, gas); // default
                if (nMbar >= srcMbar) continue; // no gradient, skip

                // Density weighting
                float dw = weights[n];
                if (dy < 0) dw *= (float) Math.min(2.0, density);
                else if (dy > 0) dw *= (float) Math.max(0.1, 2.0 - density);

                // Wind sensitivity
                float len = (float) Math.sqrt(OFFSETS_26[n][0]*OFFSETS_26[n][0]+dy*dy+OFFSETS_26[n][2]*OFFSETS_26[n][2]);
                float dot = (OFFSETS_26[n][0]*wx + dy*wy + OFFSETS_26[n][2]*wz) / len;
                dw *= (1f + windSens * Math.max(0f, dot));

                totalWeight += dw;
            }
            if (totalWeight <= 0f) continue;

            float totalTransfer = srcMbar * rate;
            srcArr[srcIdx] -= totalTransfer;
            srcSec.dirty = true;

            for (int n = 0; n < 26; n++) {
                if (!hasNeighbour[n] || weights[n] <= 0f) continue;
                int dx = OFFSETS_26[n][0], dy = OFFSETS_26[n][1], dz = OFFSETS_26[n][2];
                BlockPos nPos = pos.offset(dx, dy, dz);

                EnvironmentSection nSec = EnvironmentGrid.getOrCreateSection(level, nPos);
                if (nSec == null) continue;

                float dw = weights[n];
                if (dy < 0) dw *= (float) Math.min(2.0, gas.properties().densityRatioToAir());
                else if (dy > 0) dw *= (float) Math.max(0.1, 2.0 - gas.properties().densityRatioToAir());
                float len = (float) Math.sqrt(dx*dx+dy*dy+dz*dz);
                float dot = (dx*wx + dy*wy + dz*wz) / len;
                dw *= (1f + gas.properties().windSensitivity() * Math.max(0f, dot));

                float transfer = totalTransfer * (dw / totalWeight) * perms[n];
                if (transfer <= 0f) continue;

                nSec.addGas(gas, nPos.getX()&15,
                        (nPos.getY()-nSec.getSectionBottomY())&15, nPos.getZ()&15, transfer);
                enqueue(nPos);
            }
        }
    }

    // ── Catch-up simulation ───────────────────────────────────────────────────

    private void processCatchUp(CatchUpEntry entry) {
        // For catch-up, we use the asymptotic formula:
        // finalMbar = equilibrium + (initial - equilibrium) * (1 - rate)^missedTicks
        // Since we don't know equilibrium per-position, we just apply a scaled diffusion step.
        // This is approximate but good enough for "things happened while you were away."
        // The catch-up factor is capped at 0.95 (95% toward equilibrium) to avoid overshoot.
        double rate = MgeConfig.diffusionRate;
        double catchUpFactor = 1.0 - Math.pow(1.0 - rate, Math.min(entry.missedTicks(), 10000));
        catchUpFactor = Math.min(catchUpFactor, 0.95);

        EnvironmentChunkData data = entry.data();
        for (int i = 0; i < data.sectionCount(); i++) {
            EnvironmentSection sec = data.getByIndex(i);
            if (sec == null || !sec.hasAnyGasData()) continue;
            // For each allocated gas array, scale non-default values toward default
            // (this approximates diffusion equalising concentrations over time)
            for (Gas gas : GasRegistry.all()) {
                float[] arr = sec.gasArrayDirect(gas);
                if (arr == null) continue;
                // This is a rough approximation — real diffusion would need neighbour data
                // which we don't have for unloaded chunks
                float dimDefault = sec.getGas(gas, 0, 0, 0); // reads default for section
                for (int idx = 0; idx < EnvironmentSection.VOLUME; idx++) {
                    float v = arr[idx];
                    if (Math.abs(v - dimDefault) > 0.01f) {
                        arr[idx] = v + (float)((dimDefault - v) * catchUpFactor);
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** ThreadLocal set by Mge's server tick handler so the diffusion kernel can access the level. */
    public static final ThreadLocal<ServerLevel> currentLevel = new ThreadLocal<>();

    @Nullable
    private EnvironmentSection getSectionFor(BlockPos pos) {
        ServerLevel level = currentLevel.get();
        if (level == null) return null;
        return EnvironmentGrid.getSection(level, pos);
    }

    private static int[][] buildOffsets() {
        List<int[]> list = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dy != 0 || dz != 0)
                        list.add(new int[]{dx, dy, dz});
        return list.toArray(new int[0][]);
    }

    public int queueSize() { return queue.size(); }
}
