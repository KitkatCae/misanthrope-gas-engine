package exp.CCnewmods.mge.breathing;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active gas exchange between living entities and the atmosphere:
 *
 * <h3>Tier 1 — Player active breathing (every tick that matters)</h3>
 * <p>Every {@link #PLAYER_BREATH_INTERVAL_TICKS} ticks, each online player consumes
 * their required gas and exhales their exhale gas at the atmosphere block they occupy.
 * Players have explicit JSON profiles defining what they breathe and exhale.
 * This is the highest-fidelity tier and drives the core gameplay loop.</p>
 *
 * <h3>Tier 2 — Mob population sampling (once per chunk per N ticks)</h3>
 * <p>Rather than ticking every mob every tick (catastrophic at scale), we scan each
 * loaded chunk every {@link #MOB_SAMPLE_INTERVAL_TICKS} ticks, count living entities
 * by type, estimate their collective gas consumption from their profiles, and apply
 * the aggregate delta to the chunk's representative atmosphere block. This is
 * O(chunks) not O(entities) — completely negligible.</p>
 *
 * <h3>Tier 3 — Plant photosynthesis via random tick hook</h3>
 * <p>Grass, leaves, crops, and other plant blocks already receive vanilla random ticks.
 * We hook {@link exp.CCnewmods.mge.mixin.MixinRandomTick} — but rather than immediately marking
 * the block's atmosphere dirty (which would cascade through the scheduler), we
 * accumulate deltas in a per-chunk staging map and flush once per second.
 * Net effect: surface plant life slowly scrubs CO₂ and produces O₂ over time.</p>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ActiveBreathingHandler {

    /** How often players actively breathe (consume O₂, produce CO₂). Ticks. */
    private static final int PLAYER_BREATH_INTERVAL_TICKS = 10;

    /** How often mob population sampling runs per chunk. Ticks (20s). */
    private static final int MOB_SAMPLE_INTERVAL_TICKS = 400;

    /**
     * How often accumulated plant photosynthesis deltas are flushed to atmosphere blocks.
     * Ticks (1s). Decouples the random tick rate from the scheduler enqueue rate.
     */
    private static final int PLANT_FLUSH_INTERVAL_TICKS = 20;

    /**
     * O₂ produced per plant random tick, in mbar, at the block's position.
     * Scaled by how many random ticks occur per second (~3 per chunk section by default).
     */
    private static final float PLANT_O2_PER_TICK  =  0.8f;
    private static final float PLANT_CO2_PER_TICK =  0.6f;

    /** Mob gas consumption per entity per sample interval, scaled from real respiratory rates. */
    private static final float MOB_O2_CONSUMPTION_PER_SAMPLE  = 0.5f;
    private static final float MOB_CO2_PRODUCTION_PER_SAMPLE  = 0.4f;

    // ── Plant staging: ChunkPos.asLong → accumulated O₂ delta (positive = gain) ──
    private static final Map<Long, Float> PLANT_O2_STAGING  = new ConcurrentHashMap<>();
    private static final Map<Long, Float> PLANT_CO2_STAGING = new ConcurrentHashMap<>();

    private static int globalTick = 0;

    private ActiveBreathingHandler() {}

    // =========================================================================
    // Server tick driver
    // =========================================================================

    /**
     * Called every server tick by the Forge TickEvent, via
     * {@link exp.CCnewmods.mge.Mge#onServerTick}. Drives all three tiers.
     */
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event,
                                     net.minecraft.server.MinecraftServer server) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!MgeConfig.enableGasEffects || !MgeConfig.enableActiveBreathing) return;

        globalTick++;

        // Tier 1: player breathing — light, runs every PLAYER_BREATH_INTERVAL_TICKS
        if (globalTick % PLAYER_BREATH_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayerBreathing(player);
            }
        }

        // Tier 2: mob population sampling — runs per-level on MOB_SAMPLE_INTERVAL_TICKS
        if (globalTick % MOB_SAMPLE_INTERVAL_TICKS == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                sampleMobPopulation(level);
            }
        }

        // Tier 3: flush accumulated plant photosynthesis deltas
        if (globalTick % PLANT_FLUSH_INTERVAL_TICKS == 0) {
            flushPlantDeltas(server);
        }
    }

    // =========================================================================
    // Tier 1: Player breathing
    // =========================================================================

    private static void tickPlayerBreathing(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.isSpectator()) return;

        EntityBreathingProfile profile = EntityBreathingLoader.get(player);

        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        if (!level.isLoaded(eyePos)) return;
        var be = level.getBlockEntity(eyePos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        var comp = atm.getComposition();

        if (profile.needsToBreathe) {
            Gas required = profile.resolvedRequiredGas();
            float currentMbar = comp.get(required);

            if (currentMbar >= profile.minimumPressureMbar) {
                // Breathing fine — consume required gas
                float consume = PLAYER_BREATH_INTERVAL_TICKS * 0.004f; // ~0.04 mbar/tick
                comp.add(required, -Math.min(currentMbar * 0.001f, consume));
                BreathingTracker.resetCountdown(player, profile);
            } else {
                // Below threshold — count down tolerance
                int remaining = BreathingTracker.decrementCountdown(
                        player, PLAYER_BREATH_INTERVAL_TICKS);
                if (remaining <= 0) {
                    // Suffocating — apply drown damage, scaled to how far below threshold
                    float severity = Math.max(0f,
                            1f - currentMbar / Math.max(1f, profile.minimumPressureMbar));
                    player.hurt(player.damageSources().drown(),
                            0.5f + severity * 1.5f);
                }
            }
        }

        // Active exhalation — produce exhale gas regardless of suffocation state
        if (profile.hasActiveExhalation()) {
            Gas exhale = profile.resolvedExhaleGas();
            if (exhale != null) {
                float rate = profile.exhaleRateMbarPerTick * PLAYER_BREATH_INTERVAL_TICKS;
                comp.add(exhale, rate);
            }
        }

        atm.setComposition(comp);
        Mge.getScheduler(level).enqueue(eyePos);
    }

    // =========================================================================
    // Tier 2: Mob population sampling
    // =========================================================================

    private static void sampleMobPopulation(ServerLevel level) {
        // Iterate loaded chunks via the public getChunkSource().getLoadedChunksCount()
        // approach — we walk entity sections instead of the protected chunkMap.
        level.getAllEntities().forEach(entity -> {
            if (!(entity instanceof LivingEntity living)) return;
            if (living instanceof Player) return;

            EntityBreathingProfile profile = EntityBreathingLoader.get(living);
            if (!profile.needsToBreathe) return;

            // Only sample a fraction each interval to avoid doing this for every entity
            // every 400 ticks — use entity ID mod to spread the work
            if (Math.abs(living.getId()) % MOB_SAMPLE_INTERVAL_TICKS != globalTick % MOB_SAMPLE_INTERVAL_TICKS) return;

            BlockPos pos = living.blockPosition();
            if (!level.isLoaded(pos)) return;
            var be = level.getBlockEntity(pos);
            if (!(be instanceof AtmosphereBlockEntity atm)) return;

            var comp = atm.getComposition();
            float o2 = comp.get(GasRegistry.OXYGEN);
            comp.add(GasRegistry.OXYGEN,         -Math.min(o2, MOB_O2_CONSUMPTION_PER_SAMPLE));
            comp.add(GasRegistry.CARBON_DIOXIDE,  MOB_CO2_PRODUCTION_PER_SAMPLE);
            atm.setComposition(comp);
            Mge.getScheduler(level).enqueue(pos);
        });
    }

    // =========================================================================
    // Tier 3: Plant photosynthesis — random tick hook + staged flush
    // =========================================================================

    /** Plant block tags — blocks that perform photosynthesis when random-ticked. */
    private static boolean isPhotosyntheticBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.GRASS_BLOCK
                || block == Blocks.GRASS
                || block == Blocks.TALL_GRASS
                || block == Blocks.FERN
                || block == Blocks.LARGE_FERN
                || block == Blocks.OAK_LEAVES || block == Blocks.BIRCH_LEAVES
                || block == Blocks.SPRUCE_LEAVES || block == Blocks.JUNGLE_LEAVES
                || block == Blocks.ACACIA_LEAVES || block == Blocks.DARK_OAK_LEAVES
                || block == Blocks.MANGROVE_LEAVES || block == Blocks.AZALEA_LEAVES
                || block == Blocks.FLOWERING_AZALEA_LEAVES
                || block == Blocks.WHEAT || block == Blocks.CARROTS
                || block == Blocks.POTATOES || block == Blocks.BEETROOTS
                || block == Blocks.SUGAR_CANE || block == Blocks.BAMBOO
                || block == Blocks.LILY_PAD || block == Blocks.VINE
                || block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS
                || block == Blocks.KELP || block == Blocks.KELP_PLANT
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.CROPS);
    }

    // Plant photosynthesis accumulation is driven by MixinRandomTick,
    // which calls ActiveBreathingHandler.onPlantRandomTick() directly.
    // See exp.CCnewmods.mge.mixin.MixinRandomTick.

    /**
     * Called by {@link exp.CCnewmods.mge.mixin.MixinRandomTick} whenever a block
     * receives a random tick on the server. Accumulates photosynthesis deltas for
     * photosynthetic blocks with sky access; the staging maps are flushed once per
     * second by {@link #flushPlantDeltas}.
     */
    public static void onPlantRandomTick(BlockState state, ServerLevel level, BlockPos pos) {
        if (!MgeConfig.enableGasEffects || !MgeConfig.enableActiveBreathing
                || !MgeConfig.enablePlantPhotosynthesis) return;
        if (!isPhotosyntheticBlock(state)) return;
        if (!level.canSeeSky(pos.above())) return;

        long chunkKey = new net.minecraft.world.level.ChunkPos(pos).toLong();
        PLANT_O2_STAGING.merge(chunkKey, PLANT_O2_PER_TICK, Float::sum);
        PLANT_CO2_STAGING.merge(chunkKey, PLANT_CO2_PER_TICK, Float::sum);
    }

    private static void flushPlantDeltas(net.minecraft.server.MinecraftServer server) {
        if (PLANT_O2_STAGING.isEmpty()) return;

        // Snapshot and clear atomically
        var o2Snapshot  = Map.copyOf(PLANT_O2_STAGING);
        var co2Snapshot = Map.copyOf(PLANT_CO2_STAGING);
        PLANT_O2_STAGING.clear();
        PLANT_CO2_STAGING.clear();

        for (ServerLevel level : server.getAllLevels()) {
            for (var entry : o2Snapshot.entrySet()) {
                long chunkKey = entry.getKey();
                float o2Delta  = entry.getValue();
                float co2Delta = co2Snapshot.getOrDefault(chunkKey, 0f);

                var chunkPos = new net.minecraft.world.level.ChunkPos(chunkKey);
                if (!level.hasChunk(chunkPos.x, chunkPos.z)) continue;

                // Find a surface atmosphere block to apply the delta
                BlockPos centre = level.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        new BlockPos(chunkPos.getMiddleBlockX(), 0, chunkPos.getMiddleBlockZ()))
                        .below();

                if (!level.isLoaded(centre)) continue;
                var be = level.getBlockEntity(centre);
                if (!(be instanceof AtmosphereBlockEntity atm)) continue;

                var comp = atm.getComposition();
                // Plants consume CO₂ and produce O₂ during photosynthesis
                comp.add(GasRegistry.OXYGEN,        o2Delta);
                float co2 = comp.get(GasRegistry.CARBON_DIOXIDE);
                comp.add(GasRegistry.CARBON_DIOXIDE, -Math.min(co2, co2Delta));
                atm.setComposition(comp);
                Mge.getScheduler(level).enqueue(centre);
            }
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            BreathingTracker.remove(living);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BreathingTracker.clear();
        PLANT_O2_STAGING.clear();
        PLANT_CO2_STAGING.clear();
    }
}
