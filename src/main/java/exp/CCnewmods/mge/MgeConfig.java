package exp.CCnewmods.mge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Runtime-configurable parameters for MGE, written to {@code mge-common.toml}.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MgeConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ── Performance ───────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_TICK_SPEC =
            BUILDER.comment("Max atmosphere blocks processed per server tick (64–8192). Default: 512.")
                   .defineInRange("maxBlocksPerTick", 512, 64, 8192);

    private static final ForgeConfigSpec.DoubleValue DIFFUSION_RATE_SPEC =
            BUILDER.comment("Fraction of gas diffusing to neighbours per tick (0.001–0.5). Default: 0.02.")
                   .defineInRange("diffusionRate", 0.02, 0.001, 0.5);

    private static final ForgeConfigSpec.DoubleValue GAS_PRUNE_THRESHOLD_SPEC =
            BUILDER.comment("Gas amounts below this mbar are discarded from NBT. Default: 0.0001.")
                   .defineInRange("gasPruneThresholdMbar", 0.0001, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue PART_PRUNE_THRESHOLD_SPEC =
            BUILDER.comment("Particulate amounts below this mg/m³ are discarded. Default: 0.01.")
                   .defineInRange("particulatePruneThresholdMgM3", 0.01, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue SETTLE_RATE_MULTIPLIER_SPEC =
            BUILDER.comment("Global multiplier for particulate settling speed. "
                          + "1.0 = realistic, 2.0 = faster clearing, 0.5 = longer hang time. Default: 1.0.")
                   .defineInRange("settleRateMultiplier", 1.0, 0.1, 10.0);

    // ── Gameplay ──────────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.DoubleValue O2_BREATHABLE_THRESHOLD_SPEC =
            BUILDER.comment("O₂ partial pressure (mbar) below which suffocation begins. Default: 160.")
                   .defineInRange("o2BreathableThresholdMbar", 160.0, 0.0, 1013.25);

    private static final ForgeConfigSpec.BooleanValue ENABLE_GAS_EFFECTS_SPEC =
            BUILDER.comment("If false, all gas and particulate toxicity effects on players are disabled.")
                   .define("enableGasEffects", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_ACTIVE_BREATHING_SPEC =
            BUILDER.comment("Enable active gas exchange: players consume O₂ and exhale CO₂, "
                    + "mob populations sample O₂ consumption, "
                    + "plants produce O₂ from CO₂. Disable if atmosphere composition changes are unwanted.")
                   .define("enableActiveBreathing", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_PLANT_PHOTOSYNTHESIS_SPEC =
            BUILDER.comment("Enable plant photosynthesis via random tick: grass, leaves and crops slowly "
                    + "convert CO₂ to O₂ when they have sky access. Requires enableActiveBreathing.")
                   .define("enablePlantPhotosynthesis", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_PLANT_RESPIRATION_SPEC =
            BUILDER.comment("Enable plant respiration: all photosynthetic plants continuously consume a "
                    + "small amount of O₂ and release CO₂, regardless of light level. During daylight "
                    + "this is largely offset by photosynthesis, so the effect is only significant at "
                    + "night or in sealed / underground spaces. Requires enableActiveBreathing. Default: true.")
                   .define("enablePlantRespiration", true);

    private static final ForgeConfigSpec.DoubleValue PLANT_RESPIRATION_RATE_SPEC =
            BUILDER.comment("Multiplier on plant respiration O₂ consumption and CO₂ production rates. "
                    + "1.0 = realistic (~0.05 mbar O₂ per plant block per 10 s). Raise for faster "
                    + "atmosphere depletion in sealed rooms; lower for a near-cosmetic effect. Default: 1.0.")
                   .defineInRange("plantRespirationRateMultiplier", 1.0, 0.0, 10.0);

    private static final ForgeConfigSpec.BooleanValue ENABLE_FLIGHT_PRESSURE_SPEC =
            BUILDER.comment("Enable atmospheric pressure constraints on flying mobs. When enabled, entities "
                    + "with min_flight_pressure_mbar or max_flight_pressure_mbar set in their breathing "
                    + "profile will be weakened and eventually grounded when pressure is out of range. "
                    + "Default: true.")
                   .define("enableFlightPressureConstraints", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_ATMOSPHERIC_FLIGHT_PHYSICS_SPEC =
            BUILDER.comment("Enable atmospheric density and wind effects on gliders (elytra, ornithopter, "
                    + "VCGliders) and airborne flying mobs. Thin atmospheres reduce glide ratio; dense "
                    + "atmospheres increase drag. Wind vectors from Project Atmosphere (if loaded) push "
                    + "gliders and mobs in the wind direction. Default: true.")
                   .define("enableAtmosphericFlightPhysics", true);

    private static final ForgeConfigSpec.DoubleValue GLIDER_WIND_SENSITIVITY_SPEC =
            BUILDER.comment("How strongly atmospheric wind currents affect gliding players. "
                    + "1.0 = realistic wind push (~1 block/s at 20 m/s wind). "
                    + "0 = gliders completely unaffected by wind. Default: 0.6.")
                   .defineInRange("gliderWindSensitivity", 0.6, 0.0, 5.0);

    private static final ForgeConfigSpec.BooleanValue ENABLE_PARTICULATES_SPEC =
            BUILDER.comment("If false, particulate tracking is disabled entirely (saves NBT space).")
                   .define("enableParticulates", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_RENDERER_SPEC =
            BUILDER.comment("If false, the client-side fog/tint renderer is disabled.")
                   .define("enableAtmosphereRenderer", true);

    private static final ForgeConfigSpec.BooleanValue STANDARD_AIR_ON_GENERATION_SPEC =
            BUILDER.comment("If true, freshly generated atmosphere blocks start with Earth-standard air. "
                          + "If false, they start empty (vacuum). Default: true.")
                   .define("standardAirOnGeneration", true);

    // ── Compat ────────────────────────────────────────────────────────────────

    private static final ForgeConfigSpec.IntValue PA_WEATHER_SYNC_INTERVAL_SPEC =
            BUILDER.comment("How many server ticks between Project Atmosphere weather→atmosphere syncs. "
                          + "Lower = more responsive humidity/rain/snow effects. Default: 40.")
                   .defineInRange("paWeatherSyncIntervalTicks", 40, 5, 1200);

    // ── Shockwave shader ─────────────────────────────────────────────────────

    /**
     * Hard upper bound on the desaturation-pass wave array. The GLSL uniform
     * array in shockwave_desaturate.fsh is sized to exactly this constant —
     * changing this value requires updating the shader source to match, it
     * is NOT picked up automatically since GLSL array sizes must be a
     * compile-time constant.
     */
    public static final int SHOCKWAVE_DESAT_HARD_CAP = 16;

    private static final ForgeConfigSpec.IntValue SHOCKWAVE_MAX_DESAT_WAVES_SPEC =
            BUILDER.comment("Max simultaneous shockwaves tracked by the shared desaturation/whitening "
                          + "post-process pass. Waves beyond this cap still get their own additive "
                          + "refraction+rim-glow pass — this only limits how many can contribute to "
                          + "the one shared desaturation effect at once. Higher = more accurate with "
                          + "many simultaneous explosions, at a small fragment-shader cost. "
                          + "Hard-capped at " + SHOCKWAVE_DESAT_HARD_CAP + " by the shader's array size. "
                          + "Default: 8.")
                   .defineInRange("shockwaveMaxDesaturationWaves", 8, 1, SHOCKWAVE_DESAT_HARD_CAP);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ── Resolved values ───────────────────────────────────────────────────────

    public static int     maxBlocksPerTick;
    public static float   diffusionRate;
    public static float   gasPruneThresholdMbar;
    public static float   particulatePruneThresholdMgM3;
    public static float   settleRateMultiplier;
    public static float   o2BreathableThresholdMbar;
    public static boolean enableGasEffects;
    public static boolean enableActiveBreathing;
    public static boolean enablePlantPhotosynthesis;
    public static boolean enablePlantRespiration;
    public static float   plantRespirationRateMultiplier;
    public static boolean enableFlightPressureConstraints;
    public static boolean enableAtmosphericFlightPhysics;
    public static float   gliderWindSensitivity;
    public static boolean enableParticulates;
    public static boolean enableAtmosphereRenderer;
    public static boolean standardAirOnGeneration;
    public static int     paWeatherSyncIntervalTicks;
    public static int     shockwaveMaxDesaturationWaves;

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        maxBlocksPerTick               = MAX_BLOCKS_PER_TICK_SPEC.get();
        diffusionRate                  = DIFFUSION_RATE_SPEC.get().floatValue();
        gasPruneThresholdMbar          = GAS_PRUNE_THRESHOLD_SPEC.get().floatValue();
        particulatePruneThresholdMgM3  = PART_PRUNE_THRESHOLD_SPEC.get().floatValue();
        settleRateMultiplier           = SETTLE_RATE_MULTIPLIER_SPEC.get().floatValue();
        o2BreathableThresholdMbar      = O2_BREATHABLE_THRESHOLD_SPEC.get().floatValue();
        enableGasEffects               = ENABLE_GAS_EFFECTS_SPEC.get();
        enableActiveBreathing          = ENABLE_ACTIVE_BREATHING_SPEC.get();
        enablePlantPhotosynthesis      = ENABLE_PLANT_PHOTOSYNTHESIS_SPEC.get();
        enablePlantRespiration         = ENABLE_PLANT_RESPIRATION_SPEC.get();
        plantRespirationRateMultiplier = PLANT_RESPIRATION_RATE_SPEC.get().floatValue();
        enableFlightPressureConstraints  = ENABLE_FLIGHT_PRESSURE_SPEC.get();
        enableAtmosphericFlightPhysics   = ENABLE_ATMOSPHERIC_FLIGHT_PHYSICS_SPEC.get();
        gliderWindSensitivity            = GLIDER_WIND_SENSITIVITY_SPEC.get().floatValue();
        enableParticulates             = ENABLE_PARTICULATES_SPEC.get();
        enableAtmosphereRenderer       = ENABLE_RENDERER_SPEC.get();
        standardAirOnGeneration        = STANDARD_AIR_ON_GENERATION_SPEC.get();
        paWeatherSyncIntervalTicks     = PA_WEATHER_SYNC_INTERVAL_SPEC.get();
        shockwaveMaxDesaturationWaves  = SHOCKWAVE_MAX_DESAT_WAVES_SPEC.get();
    }

    private MgeConfig() {}
}
