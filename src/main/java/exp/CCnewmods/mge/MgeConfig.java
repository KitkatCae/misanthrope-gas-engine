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
    public static boolean enableParticulates;
    public static boolean enableAtmosphereRenderer;
    public static boolean standardAirOnGeneration;
    public static int     paWeatherSyncIntervalTicks;

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
        enableParticulates             = ENABLE_PARTICULATES_SPEC.get();
        enableAtmosphereRenderer       = ENABLE_RENDERER_SPEC.get();
        standardAirOnGeneration        = STANDARD_AIR_ON_GENERATION_SPEC.get();
        paWeatherSyncIntervalTicks     = PA_WEATHER_SYNC_INTERVAL_SPEC.get();
    }

    private MgeConfig() {}
}
