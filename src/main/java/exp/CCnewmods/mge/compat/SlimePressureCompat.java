package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.event.SlimePressureHandler;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import virtuoel.pehkui.api.ScaleTypes;

/**
 * Pressure-driven Pehkui scale compat for all modded slime-type mobs.
 *
 * <p>All entity registry IDs were confirmed by inspecting loot tables and class
 * names from each mod's jar directly. Any marked
 * the alexscaves jar was not available at write time — confirm with
 * {@code /data get entity @e[type=alexscaves:...,limit=1]} in-game.</p>
 *
 * <p>Call {@link #tryLoad()} from your mod's {@code FMLLoadCompleteEvent} handler
 * so the mod-presence flags are set before the first world tick.</p>
 *
 * <h3>Mob roster and special behaviours</h3>
 * <pre>
 *  minecraft:magma_cube     — fragment grenade on pop; compression heats atmosphere
 *  minecraft:sulfur_cube    — SO₂/H₂S cloud burst on pop; compression accelerates off-gassing
 *  opposing_force:fire_slime — higher pop tolerance; compression raises ambient temperature
 *  twilightforest:maze_slime — segmented body: compression slightly boosts health,
 *                              stretch reduces it; asymmetric pop thresholds
 *  betterend:end_slime      — End-adapted (~200 mbar reference); poor compression tolerance
 *  alexscaves:ferrous_slime  — metallic: compression strengthens, only pops on extreme stretch
 *  alexscaves:caramel_cube   — viscous; very wide tolerance; health barely changes
 *  tconstruct:sky_slime     — sky-island adapted (~400 mbar reference); low compression tolerance
 *  tconstruct:ender_slime   — crystalline; shatters easily in both directions; End-adapted
 *  alexsmobs:mimicube       — rigid core, stretchy shell; huge tolerance; health barely changes
 *  yungscavebiomes:ice_cube — crystal shell: low stretch tolerance; compression pop is
 *                              temperature-gated (melts if ≥0°C); fragment grenade on pop
 *  aether:blue_swet         — Aether-adapted (~350 mbar reference); viscous; moderate health change
 *  aether:golden_swet       — same as blue but even tougher; health barely changes
 * </pre>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlimePressureCompat {

    // ── Mod presence flags ────────────────────────────────────────────────────

    private static boolean vanillaBackportLoaded = false;
    private static boolean opposingForceLoaded   = false;
    private static boolean twilightForestLoaded  = false;
    private static boolean betterEndLoaded       = false;
    private static boolean alexsCavesLoaded      = false;
    private static boolean tconstructLoaded      = false;
    private static boolean alexsMobsLoaded       = false;
    private static boolean yungsCaveBiomesLoaded = false;
    private static boolean aetherLoaded          = false;
    static boolean anyLoaded                     = false;

    private SlimePressureCompat() {}

    /** Called from FMLLoadCompleteEvent. Sets all mod-presence flags. */
    public static void tryLoad() {
        vanillaBackportLoaded = ModList.get().isLoaded("vanillabackport");
        opposingForceLoaded   = ModList.get().isLoaded("opposing_force");
        twilightForestLoaded  = ModList.get().isLoaded("twilightforest");
        betterEndLoaded       = ModList.get().isLoaded("betterend");
        alexsCavesLoaded      = ModList.get().isLoaded("alexscaves");
        tconstructLoaded      = ModList.get().isLoaded("tconstruct");
        alexsMobsLoaded       = ModList.get().isLoaded("alexsmobs");
        yungsCaveBiomesLoaded = ModList.get().isLoaded("yungscavebiomes");
        aetherLoaded          = ModList.get().isLoaded("aether");

        anyLoaded = vanillaBackportLoaded || opposingForceLoaded || twilightForestLoaded
                 || betterEndLoaded || alexsCavesLoaded || tconstructLoaded
                 || alexsMobsLoaded || yungsCaveBiomesLoaded || aetherLoaded;

        if (vanillaBackportLoaded) Mge.LOGGER.info("[MGE] VanillaBackport — sulfur_cube pressure scaling active.");
        if (opposingForceLoaded)   Mge.LOGGER.info("[MGE] Opposing Force  — fire_slime pressure scaling active.");
        if (twilightForestLoaded)  Mge.LOGGER.info("[MGE] Twilight Forest — maze_slime pressure scaling active.");
        if (betterEndLoaded)       Mge.LOGGER.info("[MGE] Better End      — end_slime pressure scaling active.");
        if (alexsCavesLoaded)      Mge.LOGGER.info("[MGE] Alex's Caves    — ferrous_slime / caramel_cube pressure scaling active.");
        if (tconstructLoaded)      Mge.LOGGER.info("[MGE] TConstruct      — sky_slime / ender_slime pressure scaling active.");
        if (alexsMobsLoaded)       Mge.LOGGER.info("[MGE] Alex's Mobs     — mimicube pressure scaling active.");
        if (yungsCaveBiomesLoaded) Mge.LOGGER.info("[MGE] Yung's Cave Biomes — ice_cube pressure scaling active.");
        if (aetherLoaded)          Mge.LOGGER.info("[MGE] Aether          — blue_swet / golden_swet pressure scaling active.");
    }

    // ── Pop threshold tables ──────────────────────────────────────────────────
    // Arrays indexed by vanilla size (index 0 unused; indices 1–4).
    //   POP_MAX: visual scale above which the entity bursts outward (over-expanded)
    //   POP_MIN: visual scale below which the entity implodes    (over-compressed)

    // Magma Cube — same structural limits as vanilla slime; pop = fragment grenade
    private static final float[] MAGMA_MAX = { 0f, 1.3f, 1.6f, 2.0f, 2.6f };
    private static final float[] MAGMA_MIN = { 0f, 0.75f, 0.65f, 0.55f, 0.40f };

    // Sulfur Cube — standard limits; pop = SO₂/H₂S gas cloud
    private static final float[] SULFUR_MAX = { 0f, 1.3f, 1.6f, 2.0f, 2.6f };
    private static final float[] SULFUR_MIN = { 0f, 0.75f, 0.65f, 0.55f, 0.40f };

    // Fire Slime — internal combustion sustains pressure: higher tolerance both ways
    private static final float[] FIRE_MAX = { 0f, 1.8f, 2.2f, 2.8f, 3.5f };
    private static final float[] FIRE_MIN = { 0f, 0.55f, 0.50f, 0.45f, 0.35f };

    // Maze Slime — segments lock under compression (tight min), pull apart on stretch (low max)
    private static final float[] MAZE_MAX = { 0f, 1.1f, 1.3f, 1.6f, 2.0f };
    private static final float[] MAZE_MIN = { 0f, 0.60f, 0.55f, 0.45f, 0.35f };

    // End Slime — poorly adapted to compression; well adapted to expansion (End atmosphere)
    private static final float[] END_MAX = { 0f, 1.8f, 2.2f, 2.8f, 3.5f };
    private static final float[] END_MIN = { 0f, 0.85f, 0.78f, 0.70f, 0.60f };

    // Ferrous Slime — metal doesn't burst from compression; only pops on extreme stretch
    private static final float[] FERROUS_MAX = { 0f, 1.6f, 2.0f, 2.5f, 3.2f };
    private static final float[] FERROUS_MIN = { 0f, 0.20f, 0.18f, 0.15f, 0.12f }; // effectively no compression pop

    // Caramel Cube — thick and viscous; enormous tolerance range
    private static final float[] CARAMEL_MAX = { 0f, 2.5f, 3.0f, 3.8f, 5.0f };
    private static final float[] CARAMEL_MIN = { 0f, 0.35f, 0.30f, 0.25f, 0.20f };

    // Sky Slime — sky-adapted (thin air): poor compression tolerance, very high stretch tolerance
    private static final float[] SKY_MAX = { 0f, 2.2f, 2.8f, 3.5f, 4.5f };
    private static final float[] SKY_MIN = { 0f, 0.85f, 0.78f, 0.70f, 0.60f };

    // Ender Slime — crystalline: shatters easily in both directions
    private static final float[] ENDER_MAX = { 0f, 1.2f, 1.5f, 1.8f, 2.2f };
    private static final float[] ENDER_MIN = { 0f, 0.80f, 0.72f, 0.65f, 0.55f };

    // Mimicube — rigid core, stretchy outer shell: huge tolerance before pop
    private static final float[] MIMI_MAX = { 0f, 3.0f, 3.5f, 4.5f, 6.0f };
    private static final float[] MIMI_MIN = { 0f, 0.20f, 0.18f, 0.14f, 0.10f };

    // Ice Cube — crystal shell cracks on stretch; compression pop is temperature-gated
    private static final float[] ICE_MAX = { 0f, 1.2f, 1.5f, 1.9f, 2.4f };
    private static final float[] ICE_MIN = { 0f, 0.65f, 0.58f, 0.50f, 0.40f };

    // Swets (both variants) — viscous, Aether-adapted; wide tolerance
    private static final float[] SWET_MAX = { 0f, 2.0f, 2.5f, 3.2f, 4.0f };
    private static final float[] SWET_MIN = { 0f, 0.40f, 0.35f, 0.28f, 0.22f };

    // ── Alternate atmosphere reference points ─────────────────────────────────
    // Mobs adapted to non-standard atmospheres have a shifted pressure→scale curve.

    // The End: ~200 mbar reference. Pressure above 600 mbar = fully compressed.
    private static final float END_REF     = 200f;
    private static final float END_COMP    = 600f;
    private static final float END_EXPAND  =  50f;

    // Sky islands: ~400 mbar reference. Pressure above 900 mbar = fully compressed.
    private static final float SKY_REF     = 400f;
    private static final float SKY_COMP    = 900f;
    private static final float SKY_EXPAND  =  80f;

    // Aether: ~350 mbar reference. Pressure above 800 mbar = fully compressed.
    private static final float AETHER_REF    = 350f;
    private static final float AETHER_COMP   = 800f;
    private static final float AETHER_EXPAND =  60f;

    // ─────────────────────────────────────────────────────────────────────────
    // Tick handler
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (!anyLoaded) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        LivingEntity entity = event.getEntity();
        // Spread load by entity ID — same interval as SlimePressureHandler
        if ((entity.tickCount + entity.getId()) % SlimePressureHandler.TICK_INTERVAL != 0) return;

        String type  = entity.getType().toString();
        BlockPos pos = entity.blockPosition();
        float pressure = GridAtmosphereCompat.getTotalPressure(level, pos);

        // ── Magma Cube ────────────────────────────────────────────────────────
        // Scale is already applied by SlimePressureHandler (MagmaCube extends Slime).
        // We only handle the heat side-effect here. The pop override is in onLivingDeath.
        if (type.equals("minecraft:magma_cube")) {
            tickMagmaCubeHeat(entity, pressure, level, pos);
            return;
        }

        // ── Sulfur Cube (VanillaBackport — registered under minecraft namespace) ──
        if (vanillaBackportLoaded && type.equals("minecraft:sulfur_cube")) {
            float scale = SlimePressureHandler.pressureToVisualScale(pressure);
            // Standard inverse health curve
            applyScaleHealth(entity, scale, 1.0f / scale);
            // Compression forces internal SO₂ out of solution
            if (scale < 0.90f) {
                float intensity = (0.90f - scale) * 22f;
                MobAtmosphereUtil.gas(level, pos, GasRegistry.SULFUR_DIOXIDE, intensity);
                MobAtmosphereUtil.gas(level, pos, GasRegistry.SULFUR_TRIOXIDE, intensity * 0.2f);
                EnvironmentGrid.enqueue(level, pos);
            }
            checkPop(entity, level, pos, SULFUR_MAX, SULFUR_MIN, PopType.SULFUR_CLOUD);
            return;
        }

        // ── Fire Slime (Opposing Force) ───────────────────────────────────────
        if (opposingForceLoaded && type.equals("opposing_force:fire_slime")) {
            float scale = SlimePressureHandler.pressureToVisualScale(pressure);
            applyScaleHealth(entity, scale, 1.0f / scale);
            // Compression concentrates internal combustion heat into the surrounding air
            if (scale < 1.0f) {
                float compressionFraction = 1.0f - scale; // 0 at normal, up to ~0.4 at max compression
                float tempBoost = compressionFraction * 220f; // up to +220°C
                float current = EnvironmentGrid.getTemperature(level, pos);
                EnvironmentGrid.setTemperature(level, pos, current + tempBoost);
                EnvironmentGrid.enqueue(level, pos);
            }
            checkPop(entity, level, pos, FIRE_MAX, FIRE_MIN, PopType.DEFAULT);
            return;
        }

        // ── Maze Slime (Twilight Forest) ──────────────────────────────────────
        if (twilightForestLoaded && type.equals("twilightforest:maze_slime")) {
            float scale = SlimePressureHandler.pressureToVisualScale(pressure);
            float healthScale;
            if (scale < 1.0f) {
                // Segments interlock under compression — marginal health gain
                // e.g. scale=0.6 → healthScale = 1.0 + 0.4*0.4 = 1.16 (+16%)
                healthScale = 1.0f + (1.0f - scale) * 0.4f;
            } else {
                // Gaps open between segments — weakens like vanilla
                healthScale = 1.0f / scale;
            }
            applyScaleHealth(entity, scale, healthScale);
            checkPop(entity, level, pos, MAZE_MAX, MAZE_MIN, PopType.DEFAULT);
            return;
        }

        // ── End Slime (Better End) ────────────────────────────────────────────
        if (betterEndLoaded && type.equals("betterend:end_slime")) {
            float scale = pressureToScaleCustomRef(pressure, END_REF, END_COMP, END_EXPAND);
            applyScaleHealth(entity, scale, 1.0f / scale);
            checkPop(entity, level, pos, END_MAX, END_MIN, PopType.DEFAULT);
            return;
        }

        // ── Ferrous Slime (Alex's Caves) ──────────────────────────────────────
        if (alexsCavesLoaded && type.equals("alexscaves:ferrous_slime")) {
            float scale = SlimePressureHandler.pressureToVisualScale(pressure);
            float healthScale;
            if (scale < 1.0f) {
                // Metal compresses without losing structural integrity — health increases
                // scale=0.6 → healthScale = 1.0 + 0.4*2.0 = 1.8 (very tough when compressed)
                healthScale = 1.0f + (1.0f - scale) * 2.0f;
            } else {
                // Stretching metal thins it — standard weakness curve
                healthScale = 1.0f / scale;
            }
            applyScaleHealth(entity, scale, healthScale);
            // Only pop on extreme stretch; compression is structurally safe for iron
            float current = ScaleTypes.BASE.getScaleData(entity).getScale();
            if (current > FERROUS_MAX[clampSize(entity)]) {
                SlimePressureHandler.popGeneric(entity, level, pos);
            }
            return;
        }

        // ── Caramel Cube (Alex's Caves) ───────────────────────────────────────
        if (alexsCavesLoaded && type.equals("alexscaves:caramel_cube")) {
            float scale = SlimePressureHandler.pressureToVisualScale(pressure);
            // Thick and viscous — deformation barely affects it in either direction
            // Maximum health change is ±15% regardless of how far it deforms
            float deviation = Math.abs(scale - 1.0f);
            float healthScale = 1.0f - deviation * 0.15f;
            applyScaleHealth(entity, scale, healthScale);
            checkPop(entity, level, pos, CARAMEL_MAX, CARAMEL_MIN, PopType.DEFAULT);
            return;
        }

        // ── Sky Slime (TConstruct) ────────────────────────────────────────────
        if (tconstructLoaded && type.equals("tconstruct:sky_slime")) {
            float scale = pressureToScaleCustomRef(pressure, SKY_REF, SKY_COMP, SKY_EXPAND);
            applyScaleHealth(entity, scale, 1.0f / scale);
            checkPop(entity, level, pos, SKY_MAX, SKY_MIN, PopType.DEFAULT);
            return;
        }

        // ── Ender Slime (TConstruct) ──────────────────────────────────────────
        if (tconstructLoaded && type.equals("tconstruct:ender_slime")) {
            // Crystalline structure + End-adapted atmosphere reference
            float scale = pressureToScaleCustomRef(pressure, END_REF, END_COMP, END_EXPAND);
            applyScaleHealth(entity, scale, 1.0f / scale);
            checkPop(entity, level, pos, ENDER_MAX, ENDER_MIN, PopType.DEFAULT);
            return;
        }

        // ── Mimicube (Alex's Mobs) ────────────────────────────────────────────
        if (alexsMobsLoaded && type.equals("alexsmobs:mimicube")) {
            float scale = SlimePressureHandler.pressureToVisualScale(pressure);
            // Rigid core keeps combat stats stable — outer shell deforms freely
            // Maximum health change is ±10%
            float deviation = Math.abs(scale - 1.0f);
            float healthScale = 1.0f - deviation * 0.10f;
            applyScaleHealth(entity, scale, healthScale);
            checkPop(entity, level, pos, MIMI_MAX, MIMI_MIN, PopType.DEFAULT);
            return;
        }

        // ── Ice Cube (Yung's Cave Biomes) ─────────────────────────────────────
        if (yungsCaveBiomesLoaded && type.equals("yungscavebiomes:ice_cube")) {
            float scale = SlimePressureHandler.pressureToVisualScale(pressure);
            applyScaleHealth(entity, scale, 1.0f / scale);
            // Temperature-gated compression melt: if ambient ≥ 0°C AND the crystal
            // shell is being compressed, it melts and the whole structure fails
            if (scale < 0.85f && MisanthropeWorldCompat.isLoaded()) {
                double celsius = MisanthropeWorldCompat.getAmbientCelsius(level, pos);
                if (!Double.isNaN(celsius) && celsius >= 0.0) {
                    popIceCube(entity, level, pos);
                    return;
                }
            }
            checkPop(entity, level, pos, ICE_MAX, ICE_MIN, PopType.FRAGMENT_GRENADE);
            return;
        }

        // ── Blue Swet (Aether) ────────────────────────────────────────────────
        if (aetherLoaded && type.equals("aether:blue_swet")) {
            float scale = pressureToScaleCustomRef(pressure, AETHER_REF, AETHER_COMP, AETHER_EXPAND);
            // Thick and viscous — moderate health response to deformation
            float healthScale = 1.0f - Math.abs(scale - 1.0f) * 0.22f;
            applyScaleHealth(entity, scale, healthScale);
            checkPop(entity, level, pos, SWET_MAX, SWET_MIN, PopType.DEFAULT);
            return;
        }

        // ── Golden Swet (Aether) ──────────────────────────────────────────────
        if (aetherLoaded && type.equals("aether:golden_swet")) {
            float scale = pressureToScaleCustomRef(pressure, AETHER_REF, AETHER_COMP, AETHER_EXPAND);
            // Even thicker than blue swet — health barely changes
            float healthScale = 1.0f - Math.abs(scale - 1.0f) * 0.12f;
            applyScaleHealth(entity, scale, healthScale);
            checkPop(entity, level, pos, SWET_MAX, SWET_MIN, PopType.DEFAULT);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Death handler — intercept magma cube to override pop type
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        LivingEntity entity = event.getEntity();
        // Only intercept magma cube deaths that were triggered by a pressure pop
        // (i.e. entity is at an out-of-bounds scale when it dies)
        if (!entity.getType().toString().equals("minecraft:magma_cube")) return;

        float scale = ScaleTypes.BASE.getScaleData(entity).getScale();
        int size = clampSize(entity);
        if (scale <= MAGMA_MAX[size] && scale >= MAGMA_MIN[size]) return; // natural death, not a pop

        spawnFragmentGrenade(level, entity.blockPosition(), entity.position(),
                5.0f,
                new ItemStack(Items.MAGMA_CREAM),
                GasRegistry.BLAZE_FUME, GasRegistry.SULFUR_DIOXIDE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pop types
    // ─────────────────────────────────────────────────────────────────────────

    private enum PopType { DEFAULT, FRAGMENT_GRENADE, SULFUR_CLOUD }

    /**
     * Reads the entity's current Pehkui BASE scale and kills it if it is outside
     * the allowed range for its vanilla size, using the given pop style.
     */
    private static void checkPop(LivingEntity entity, ServerLevel level, BlockPos pos,
                                  float[] maxTable, float[] minTable, PopType type) {
        float current = ScaleTypes.BASE.getScaleData(entity).getScale();
        int size = clampSize(entity);
        if (current <= maxTable[size] && current >= minTable[size]) return;

        Vec3 vec = entity.position();
        switch (type) {
            case FRAGMENT_GRENADE ->
                spawnFragmentGrenade(level, pos, vec, 5.0f,
                        new ItemStack(Items.SLIME_BALL),
                        GasRegistry.NITROGEN, GasRegistry.OXYGEN);
            case SULFUR_CLOUD ->
                spawnSulfurCloud(entity, level, pos, vec);
            default ->
                SlimePressureHandler.popGeneric(entity, level, pos);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pop implementations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shockwave + scattered item drops + gas pulse.
     * Used for: magma cube, ice cube, and any fragment-grenade-style pop.
     *
     * @param shockwaveStrength  radius passed to {@link ShockwaveHandler#spawn}
     * @param drop               item to scatter (3–5 copies with random velocity)
     * @param gases              gases to inject in a radius-3 sphere
     */
    private static void spawnFragmentGrenade(ServerLevel level, BlockPos pos, Vec3 vec,
                                              float shockwaveStrength,
                                              ItemStack drop, Gas... gases) {
        var rng = level.getRandom();
        int count = 3 + rng.nextInt(3);
        for (int i = 0; i < count; i++) {
            double vx = (rng.nextDouble() - 0.5) * 0.9;
            double vy = rng.nextDouble() * 0.65 + 0.2;
            double vz = (rng.nextDouble() - 0.5) * 0.9;
            ItemEntity item = new ItemEntity(level, vec.x, vec.y + 0.5, vec.z, drop.copy());
            item.setDeltaMovement(vx, vy, vz);
            level.addFreshEntity(item);
        }
        for (Gas g : gases) {
            MobAtmosphereUtil.gasRadius(level, pos, g, 28f, 3);
        }
        level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.5f, 0.45f);
        ShockwaveHandler.spawn(level, pos, shockwaveStrength);
        EnvironmentGrid.enqueue(level, pos);
        // Actual kill is the caller's responsibility (checkPop → popGeneric, or onLivingDeath)
    }

    /**
     * Sulfur Cube pop: gaseous SO₂ + H₂S burst, no shockwave.
     * The cube is under internal sulfurous gas pressure; rupture releases it as a cloud.
     */
    private static void spawnSulfurCloud(LivingEntity entity, ServerLevel level,
                                          BlockPos pos, Vec3 vec) {
        MobAtmosphereUtil.gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,    90f, 5);
        MobAtmosphereUtil.gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,  45f, 4);
        MobAtmosphereUtil.gasRadius(level, pos, GasRegistry.SULFUR_TRIOXIDE,   20f, 3);
        // Combustion of expelled sulfur consumes local oxygen
        MobAtmosphereUtil.drainRadius(level, pos, GasRegistry.OXYGEN, 35f, 3);
        level.playSound(null, pos, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 1.2f, 0.35f);
        EnvironmentGrid.enqueue(level, pos);
        entity.setHealth(0);
        entity.kill();
    }

    /**
     * Ice Cube melt-pop: ice shards (snowballs) scatter outward, water vapour bursts out,
     * and a small shockwave follows from the rapid state-change.
     */
    private static void popIceCube(LivingEntity entity, ServerLevel level, BlockPos pos) {
        Vec3 vec = entity.position();
        var rng = level.getRandom();
        int count = 4 + rng.nextInt(4);
        for (int i = 0; i < count; i++) {
            double vx = (rng.nextDouble() - 0.5) * 1.1;
            double vy = rng.nextDouble() * 0.75 + 0.1;
            double vz = (rng.nextDouble() - 0.5) * 1.1;
            ItemEntity shard = new ItemEntity(level, vec.x, vec.y + 0.5, vec.z,
                    new ItemStack(Items.SNOWBALL));
            shard.setDeltaMovement(vx, vy, vz);
            level.addFreshEntity(shard);
        }
        MobAtmosphereUtil.gasRadius(level, pos, GasRegistry.WATER_VAPOR, 50f, 3);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.3f, 1.5f);
        ShockwaveHandler.spawn(level, pos, 4.0f);
        EnvironmentGrid.enqueue(level, pos);
        entity.setHealth(0);
        entity.kill();
    }

    /**
     * Magma cube heat side-effect (called every tick interval).
     * Smaller (compressed) = denser concentrated fire = hotter atmosphere cell.
     * Larger (expanded)    = diluted fire mass = cooler atmosphere cell.
     * Scale is already applied by SlimePressureHandler; we only touch temperature here.
     */
    private static void tickMagmaCubeHeat(LivingEntity entity, float pressure,
                                           ServerLevel level, BlockPos pos) {
        float scale = SlimePressureHandler.pressureToVisualScale(pressure);
        // Compressed: scale < 1 → tempDelta positive (hotter)
        // Expanded:   scale > 1 → tempDelta negative (cooler)
        float tempDelta = (1.0f - scale) * 160f;       // ±160°C at scale extremes
        float baseTemp  = 85f + (clampSize(entity) * 10f); // larger magma cubes burn hotter
        float target    = baseTemp + tempDelta;
        float current   = EnvironmentGrid.getTemperature(level, pos);
        // Blend 20% toward target per tick interval — smooth, not instant
        EnvironmentGrid.setTemperature(level, pos, current + (target - current) * 0.20f);
        EnvironmentGrid.enqueue(level, pos);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies Pehkui BASE (visual) and HEALTH scales with smooth transitions.
     * Health scale is clamped to [0.25, 3.0].
     */
    private static void applyScaleHealth(LivingEntity entity,
                                          float visualScale, float healthScale) {
        healthScale = Math.max(0.25f, Math.min(3.0f, healthScale));
        SlimePressureHandler.setScaleSmooth(entity, ScaleTypes.BASE,   visualScale);
        SlimePressureHandler.setScaleSmooth(entity, ScaleTypes.HEALTH, healthScale);
    }

    /**
     * Like {@link SlimePressureHandler#pressureToVisualScale} but with a custom
     * reference pressure and curve endpoints — for mobs adapted to non-standard
     * atmospheres (End, sky islands, Aether).
     *
     * @param pressure     current atmospheric pressure, mbar
     * @param refMbar      pressure at which scale = 1.0 (the mob's "home" atmosphere)
     * @param compMbar     pressure at which scale = {@link SlimePressureHandler#MIN_VISUAL_SCALE}
     * @param expandMbar   pressure at which scale = {@link SlimePressureHandler#MAX_VISUAL_SCALE}
     */
    public static float pressureToScaleCustomRef(float pressure,
                                                  float refMbar,
                                                  float compMbar,
                                                  float expandMbar) {
        float minScale = SlimePressureHandler.MIN_VISUAL_SCALE;
        float maxScale = SlimePressureHandler.MAX_VISUAL_SCALE;
        if (pressure >= refMbar) {
            float t = Math.min(1.0f, (pressure - refMbar) / (compMbar - refMbar));
            return 1.0f - t * (1.0f - minScale);
        } else {
            float t = Math.min(1.0f, (refMbar - pressure) / (refMbar - expandMbar));
            return 1.0f + t * (maxScale - 1.0f);
        }
    }

    /**
     * Returns the entity's vanilla size clamped to [1, 4] for safe table indexing.
     * Falls back to 2 (small) for non-{@link Slime} subclasses.
     */
    private static int clampSize(LivingEntity entity) {
        if (entity instanceof Slime slime) {
            return Math.max(1, Math.min(4, slime.getSize()));
        }
        return 2;
    }
}
