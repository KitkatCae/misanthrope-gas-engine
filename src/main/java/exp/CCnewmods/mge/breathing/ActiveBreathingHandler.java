package exp.CCnewmods.mge.breathing;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;

import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LightLayer;
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
 *
 * <h3>Tier 4 — Plant respiration (always-on, darkness-gated)</h3>
 * <p>All living plants respire continuously: they consume O₂ and release CO₂ regardless
 * of light. During the day, photosynthesis (Tier 3) more than offsets this, so the net
 * effect is still O₂ gain. At night — or indoors / underground where sky light is absent —
 * only respiration runs, slowly drawing down O₂ and raising CO₂ in sealed spaces.
 * Rather than scanning every block every tick, we walk loaded chunks once per
 * {@link #RESPIRATION_SAMPLE_INTERVAL_TICKS} and count photosynthetic blocks via the
 * chunk's heightmap, then apply a per-chunk aggregate delta. The rate is intentionally
 * much smaller than photosynthesis so a well-ventilated outdoor area stays balanced.</p>
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

    // ── Tier 4: Plant respiration ─────────────────────────────────────────────

    /**
     * How often the per-level plant-respiration scan runs. Every 10 s (200 ticks).
     * Kept infrequent because it walks all loaded chunks in each level.
     */
    private static final int RESPIRATION_SAMPLE_INTERVAL_TICKS = 200;

    /**
     * O₂ consumed and CO₂ produced per plant-block count unit per sample interval.
     * Intentionally much smaller than {@link #PLANT_O2_PER_TICK} so that daytime
     * photosynthesis dominates and only sealed/night environments see net depletion.
     *
     * <p>Effective rate ≈ 0.05 mbar O₂ per photosynthetic block per 10 s, which for
     * a ~16×16 chunk with ~50 surface plants equals ~2.5 mbar/10 s — noticeable over
     * minutes in a sealed room, negligible outdoors where diffusion replenishes it.</p>
     */
    private static final float RESPIRATION_O2_PER_BLOCK  = 0.05f;
    private static final float RESPIRATION_CO2_PER_BLOCK = 0.04f;

    /**
     * Sky-light level at or below which a block is considered to be in darkness for
     * respiration purposes. During a clear day at the surface this is 15; at night it
     * drops to ~4. We treat ≤ {@value} as "not photosynthesising", so plants indoors,
     * underground, and on the night side all respire without offsetting photosynthesis.
     */
    private static final int DARK_SKY_LIGHT_THRESHOLD = 7;

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

        // Tier 4: plant respiration — always-on O₂ drain / CO₂ release
        if (MgeConfig.enablePlantRespiration
                && globalTick % RESPIRATION_SAMPLE_INTERVAL_TICKS == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                samplePlantRespiration(level);
            }
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
        var comp = GridAtmosphereCompat.getComposition(level, eyePos);

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

        GridAtmosphereCompat.setComposition(level, eyePos, comp);
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

            // Only sample a fraction each interval to avoid doing this for every entity
            // every 400 ticks — use entity ID mod to spread the work
            if (Math.abs(living.getId()) % MOB_SAMPLE_INTERVAL_TICKS != globalTick % MOB_SAMPLE_INTERVAL_TICKS) return;

            BlockPos pos = living.blockPosition();
            if (!level.isLoaded(pos)) return;

            // ── Standard O₂ consumption ──────────────────────────────────────
            if (profile.needsToBreathe) {
                GridAtmosphereCompat.addGas(level, pos, GasRegistry.OXYGEN,
                        -Math.min(GridAtmosphereCompat.getGas(level, pos, GasRegistry.OXYGEN),
                                  MOB_O2_CONSUMPTION_PER_SAMPLE));
                GridAtmosphereCompat.addGas(level, pos, GasRegistry.CARBON_DIOXIDE, MOB_CO2_PRODUCTION_PER_SAMPLE);
            }

            // ── Flight pressure constraint ────────────────────────────────────
            if (MgeConfig.enableFlightPressureConstraints && profile.hasFlightPressureConstraint()) {
                float totalPressure = GridAtmosphereCompat.getComposition(level, pos).totalPressure();
                if (!profile.isFlightPressureValid(totalPressure)) {
                    // Outside valid range — tick down the tolerance counter.
                    // Actual movement suppression happens every tick in onFlyingMobTick()
                    // so we don't need to touch effects or velocity here.
                    FlightPressureTracker.decrementCountdown(living, MOB_SAMPLE_INTERVAL_TICKS);

                    // Apply Weakness as a gameplay signal that the mob is struggling.
                    // Duration slightly longer than the sample interval so it doesn't flicker.
                    int effectDuration = MOB_SAMPLE_INTERVAL_TICKS + 20;
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, effectDuration, 0, false, false));
                } else {
                    // Back in valid range — reset so the mob can fly freely again
                    FlightPressureTracker.resetCountdown(living, profile);
                }
            }
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
                // Plants consume CO₂ and produce O₂ during photosynthesis
                GridAtmosphereCompat.addGas(level, centre, GasRegistry.OXYGEN, o2Delta);
                float existingCo2 = GridAtmosphereCompat.getGas(level, centre, GasRegistry.CARBON_DIOXIDE);
                GridAtmosphereCompat.addGas(level, centre, GasRegistry.CARBON_DIOXIDE,
                        -Math.min(existingCo2, co2Delta));
            }
        }
    }

    // =========================================================================
    // Tier 4: Plant respiration — always-on, darkness-gated
    // =========================================================================

    /**
     * Scans every loaded chunk in {@code level}, counts photosynthetic blocks near
     * the surface, and applies a small O₂ drain / CO₂ gain to the chunk's
     * representative atmosphere block.
     *
     * <p>The drain is applied unconditionally — respiration never stops — but
     * {@link #onPlantRandomTick} (Tier 3) produces roughly 10× more O₂ per second
     * when plants are photosynthesising, so the net daytime/outdoor effect is still
     * strongly positive. Only in darkness (night, underground, sealed rooms) does
     * respiration outpace photosynthesis and produce a net CO₂ rise.</p>
     *
     * <p>To avoid scanning every block in the chunk we sample a 5×5 column grid
     * centred on the chunk's middle, checking the surface block at each column via
     * the MOTION_BLOCKING heightmap. This gives a cheap, representative plant-density
     * estimate without iterating the full 16×16×256 volume.</p>
     */
    private static void samplePlantRespiration(ServerLevel level) {
        // Use SectionLoadManager's own loaded-chunk registry instead of ChunkMap.getChunks()
        // (which has protected access). MGE already tracks every loaded chunk; this set is
        // always a subset of the truly-ticking chunks so it's safe to sample without guards.
        for (net.minecraft.world.level.ChunkPos chunkPos
                : exp.CCnewmods.mge.grid.SectionLoadManager.getLoadedChunkPositions(level)) {

            // Sample a 5×5 grid of columns within the chunk (every ~3 blocks).
            int plantCount = 0;
            for (int sx = 0; sx < 5; sx++) {
                for (int sz = 0; sz < 5; sz++) {
                    int worldX = chunkPos.getMinBlockX() + 1 + sx * 3;
                    int worldZ = chunkPos.getMinBlockZ() + 1 + sz * 3;

                    BlockPos surface = level.getHeightmapPos(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                            new BlockPos(worldX, 0, worldZ)).below();

                    if (!level.isLoaded(surface)) continue;
                    BlockState state = level.getBlockState(surface);
                    if (isPhotosyntheticBlock(state)) {
                        plantCount++;
                    }
                }
            }

            if (plantCount == 0) continue;

            // Use the chunk centre surface block as the atmosphere representative.
            BlockPos centre = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    new BlockPos(chunkPos.getMiddleBlockX(), 0, chunkPos.getMiddleBlockZ()))
                    .below();
            if (!level.isLoaded(centre)) continue;

            float o2Loss  = plantCount * RESPIRATION_O2_PER_BLOCK * MgeConfig.plantRespirationRateMultiplier;
            float co2Gain = plantCount * RESPIRATION_CO2_PER_BLOCK * MgeConfig.plantRespirationRateMultiplier;

            // If plants can see sky AND sky light is above the dark threshold, they are
            // actively photosynthesising — Tier 3 already handles that side.  We still
            // apply respiration here, but at a reduced rate (photosynthesis dominates).
            // In true darkness (night / underground / sealed) the full rate applies.
            int skyLight = level.getBrightness(
                    net.minecraft.world.level.LightLayer.SKY, centre);
            if (skyLight > DARK_SKY_LIGHT_THRESHOLD) {
                // Daytime / well-lit: respiration is real but partially masked by
                // ongoing photosynthesis — apply at 20 % to avoid double-counting.
                o2Loss  *= 0.2f;
                co2Gain *= 0.2f;
            }

            float existingO2 = GridAtmosphereCompat.getGas(level, centre, GasRegistry.OXYGEN);
            GridAtmosphereCompat.addGas(level, centre, GasRegistry.OXYGEN,
                    -Math.min(existingO2, o2Loss));
            GridAtmosphereCompat.addGas(level, centre, GasRegistry.CARBON_DIOXIDE, co2Gain);
        }
    }

    // =========================================================================
    // Flight grounding — per-tick movement suppression
    // =========================================================================

    /**
     * Called every server tick for every living entity via {@link LivingEvent.LivingTickEvent}.
     *
     * <p>When {@link FlightPressureTracker} reports that an entity's tolerance has
     * expired (countdown ≤ 0), this handler overrides whatever vertical thrust the
     * mob's AI produced this tick:</p>
     *
     * <ul>
     *   <li><b>Pre-tolerance (warning phase):</b> the countdown is ticking down but
     *       hasn't hit zero yet. We don't interfere — the Weakness effect from the
     *       sample loop is the only signal. The mob can still fly, just weakly.</li>
     *   <li><b>Post-tolerance (grounded phase):</b> we clamp {@code deltaMovement.y}
     *       to ≤ 0 and apply a small downward pull each tick. This fights the AI's
     *       upward thrust without teleporting or stunning the mob — producing a
     *       natural stall-and-glide descent. Horizontal movement is untouched.</li>
     *   <li><b>Recovery:</b> once the sample loop resets the countdown (pressure
     *       returns to valid range), this handler stops interfering entirely.</li>
     * </ul>
     *
     * <p>Deliberately avoids swapping navigation, potion effects, or
     * {@code setNoGravity} — pure velocity clamping is the smallest intervention.</p>
     */
    @SubscribeEvent
    public static void onFlyingMobTick(LivingEvent.LivingTickEvent event) {
        if (!MgeConfig.enableFlightPressureConstraints) return;
        if (!(event.getEntity().level() instanceof ServerLevel)) return;

        LivingEntity living = event.getEntity();
        if (living instanceof Player) return;

        // Only act once the tolerance countdown has fully expired
        if (!FlightPressureTracker.isGrounded(living)) return;

        EntityBreathingProfile profile = EntityBreathingLoader.get(living);
        if (!profile.hasFlightPressureConstraint()) return;

        // Stall: cancel any upward velocity the AI just assigned this tick and apply
        // a gentle downward pull. GLIDE_GRAVITY is intentionally small so the mob
        // drifts down like a stalling glider rather than dropping like a stone.
        final double GLIDE_GRAVITY = 0.04;
        var mv = living.getDeltaMovement();
        living.setDeltaMovement(mv.x, Math.min(mv.y, 0) - GLIDE_GRAVITY, mv.z);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            BreathingTracker.remove(living);
            FlightPressureTracker.remove(living);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BreathingTracker.clear();
        FlightPressureTracker.clear();
        PLANT_O2_STAGING.clear();
        PLANT_CO2_STAGING.clear();
    }
}
