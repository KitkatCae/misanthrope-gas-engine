package exp.CCnewmods.mge.gas;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Central registry of all gases known to MGE.
 *
 * <p>Standard Earth atmosphere partial pressures (mbar, summing to ~1013.25):
 * N₂ 780.9, O₂ 209.5, Ar 9.3, CO₂ 0.4, Ne 0.018, He 0.005, CH₄ 0.002, Kr 0.001 ...</p>
 *
 * <p>Toxicity thresholds are approximate IDLH (Immediately Dangerous to Life/Health) values
 * translated into partial-pressure mbar for game-feel balance. They are intentionally
 * lower than real-world IDLH to make gameplay consequences noticeable.</p>
 */
public final class GasRegistry {

    private static final Map<String, Gas> BY_ID    = new LinkedHashMap<>();
    private static final List<Gas>        ALL_GASES = new ArrayList<>();

    // =========================================================================
    // Atmospheric bulk gases
    // =========================================================================

    public static final Gas NITROGEN = register("nitrogen",
            GasProperties.builder(28.014)
                    .density(0.967)
                    .windSensitivity(0.8f)
                    .build());

    public static final Gas OXYGEN = register("oxygen",
            GasProperties.builder(32.000)
                    .density(1.105)
                    .windSensitivity(0.8f)
                    .breathable(1.0f)
                    .reactivity(ReactivityFlag.OXIDISER)
                    .build());

    public static final Gas ARGON = register("argon",
            GasProperties.builder(39.948)
                    .density(1.379)
                    .windSensitivity(0.6f)
                    .build());

    public static final Gas CARBON_DIOXIDE = register("carbon_dioxide",
            GasProperties.builder(44.010)
                    .density(1.519)
                    .windSensitivity(0.5f)
                    .color(0x10334444)
                    .toxic(50f, ToxicEffect.SUFFOCATION)   // asphyxiant at high conc.
                    .reactivity(ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas NEON = register("neon",
            GasProperties.builder(20.180)
                    .density(0.696)
                    .windSensitivity(1.1f)
                    .color(0x18FF6633)   // faint orange glow at high conc
                    .build());

    public static final Gas HELIUM = register("helium",
            GasProperties.builder(4.003)
                    .density(0.138)
                    .windSensitivity(2.0f)
                    .build());

    public static final Gas METHANE = register("methane",
            GasProperties.builder(16.043)
                    .density(0.554)
                    .windSensitivity(1.3f)
                    .flammable(0.05f, 0.15f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2 | ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas KRYPTON = register("krypton",
            GasProperties.builder(83.798)
                    .density(2.868)
                    .windSensitivity(0.3f)
                    .build());

    public static final Gas HYDROGEN = register("hydrogen",
            GasProperties.builder(2.016)
                    .density(0.0696)
                    .windSensitivity(2.0f)
                    .flammable(0.04f, 0.75f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas NITROUS_OXIDE = register("nitrous_oxide",
            GasProperties.builder(44.013)
                    .density(1.530)
                    .windSensitivity(0.5f)
                    .color(0x12FFFFFF)
                    .toxic(200f, ToxicEffect.NAUSEA)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas CARBON_MONOXIDE = register("carbon_monoxide",
            GasProperties.builder(28.010)
                    .density(0.967)
                    .windSensitivity(0.9f)
                    .toxic(30f, ToxicEffect.WITHER)         // binds hemoglobin
                    .flammable(0.125f, 0.74f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas XENON = register("xenon",
            GasProperties.builder(131.293)
                    .density(4.524)
                    .windSensitivity(0.1f)
                    .color(0x0AAAAAFF)
                    .build());

    // =========================================================================
    // Industrial / combustion gases
    // =========================================================================

    public static final Gas SULFUR_DIOXIDE = register("sulfur_dioxide",
            GasProperties.builder(64.066)
                    .density(2.264)
                    .windSensitivity(0.4f)
                    .color(0x30CCCC00)
                    .toxic(5f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID | ReactivityFlag.SULFUROUS)
                    .build());

    public static final Gas SULFUR_TRIOXIDE = register("sulfur_trioxide",
            GasProperties.builder(80.066)
                    .density(2.759)
                    .windSensitivity(0.3f)
                    .color(0x40CCAA00)
                    .toxic(3f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID | ReactivityFlag.SULFUROUS)
                    .build());

    public static final Gas HYDROGEN_SULFIDE = register("hydrogen_sulfide",
            GasProperties.builder(34.082)
                    .density(1.176)
                    .windSensitivity(0.7f)
                    .color(0x28AAAA00)
                    .toxic(10f, ToxicEffect.WITHER)
                    .flammable(0.04f, 0.44f)
                    .reactivity(ReactivityFlag.SULFUROUS | ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas AMMONIA = register("ammonia",
            GasProperties.builder(17.031)
                    .density(0.588)
                    .windSensitivity(1.2f)
                    .color(0x18AAAAFF)
                    .toxic(15f, ToxicEffect.POISON)
                    .flammable(0.15f, 0.28f)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas CHLORINE = register("chlorine",
            GasProperties.builder(70.906)
                    .density(2.482)
                    .windSensitivity(0.3f)
                    .color(0x5066CC22)
                    .toxic(3f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas HYDROGEN_CHLORIDE = register("hydrogen_chloride",
            GasProperties.builder(36.461)
                    .density(1.268)
                    .windSensitivity(0.7f)
                    .color(0x28DDDD88)
                    .toxic(10f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas HYDROGEN_FLUORIDE = register("hydrogen_fluoride",
            GasProperties.builder(20.006)
                    .density(0.713)
                    .windSensitivity(1.1f)
                    .color(0x30EEEEFF)
                    .toxic(2f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas NITROGEN_DIOXIDE = register("nitrogen_dioxide",
            GasProperties.builder(46.006)
                    .density(1.587)
                    .windSensitivity(0.5f)
                    .color(0x48CC6600)
                    .toxic(8f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas NITRIC_OXIDE = register("nitric_oxide",
            GasProperties.builder(30.006)
                    .density(1.036)
                    .windSensitivity(0.8f)
                    .color(0x20AA8844)
                    .toxic(20f, ToxicEffect.WITHER)
                    .build());

    public static final Gas OZONE = register("ozone",
            GasProperties.builder(48.000)
                    .density(1.658)
                    .windSensitivity(0.5f)
                    .color(0x20448899)
                    .toxic(5f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.HYPERGOLIC)
                    .build());

    public static final Gas PHOSGENE = register("phosgene",
            GasProperties.builder(98.916)
                    .density(3.416)
                    .windSensitivity(0.2f)
                    .color(0x38CCDDAA)
                    .toxic(2f, ToxicEffect.INSTANT_DAMAGE)
                    .build());

    // =========================================================================
    // Hydrocarbons
    // =========================================================================

    public static final Gas ETHANE = register("ethane",
            GasProperties.builder(30.069)
                    .density(1.038)
                    .windSensitivity(0.8f)
                    .flammable(0.03f, 0.125f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas PROPANE = register("propane",
            GasProperties.builder(44.097)
                    .density(1.522)
                    .windSensitivity(0.5f)
                    .flammable(0.021f, 0.095f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas BUTANE = register("butane",
            GasProperties.builder(58.124)
                    .density(2.006)
                    .windSensitivity(0.4f)
                    .flammable(0.018f, 0.084f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas ETHYLENE = register("ethylene",
            GasProperties.builder(28.054)
                    .density(0.969)
                    .windSensitivity(0.9f)
                    .flammable(0.027f, 0.36f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas ACETYLENE = register("acetylene",
            GasProperties.builder(26.038)
                    .density(0.899)
                    .windSensitivity(1.0f)
                    .flammable(0.025f, 0.80f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas PROPYLENE = register("propylene",
            GasProperties.builder(42.081)
                    .density(1.453)
                    .windSensitivity(0.6f)
                    .flammable(0.02f, 0.115f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas ISOBUTANE = register("isobutane",
            GasProperties.builder(58.124)
                    .density(2.006)
                    .windSensitivity(0.4f)
                    .flammable(0.018f, 0.084f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas PENTANE = register("pentane",
            GasProperties.builder(72.151)
                    .density(2.491)
                    .windSensitivity(0.3f)
                    .flammable(0.014f, 0.076f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    // =========================================================================
    // Noble / rare atmospheric gases
    // =========================================================================

    public static final Gas RADON = register("radon",
            GasProperties.builder(222.018)
                    .density(7.686)
                    .windSensitivity(0.05f)
                    .color(0x10FF4444)
                    .reactivity(ReactivityFlag.RADIOACTIVE)
                    .build());

    // =========================================================================
    // Water-related / atmospheric humidity
    // =========================================================================

    public static final Gas WATER_VAPOR = register("water_vapor",
            GasProperties.builder(18.015)
                    .density(0.622)
                    .windSensitivity(1.3f)
                    .color(0x10CCDDFF)
                    .reactivity(ReactivityFlag.CONDENSABLE | ReactivityFlag.GREENHOUSE)
                    .build());

    // =========================================================================
    // Refrigerants and industrial halocarbons
    // =========================================================================

    public static final Gas FREON_12 = register("freon_12",           // CCl₂F₂
            GasProperties.builder(120.910)
                    .density(4.178)
                    .windSensitivity(0.15f)
                    .color(0x08EEEEFF)
                    .reactivity(ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas FREON_22 = register("freon_22",           // CHClF₂
            GasProperties.builder(86.468)
                    .density(2.986)
                    .windSensitivity(0.2f)
                    .reactivity(ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas TETRAFLUOROETHYLENE = register("tetrafluoroethylene",
            GasProperties.builder(100.016)
                    .density(3.455)
                    .windSensitivity(0.2f)
                    .flammable(0.0f, 0.0f)   // non-flammable under normal conditions
                    .build());

    // =========================================================================
    // Nether / otherworldly gases  (non-real-world but lore-consistent)
    // =========================================================================

    public static final Gas SOUL_SMOKE = register("soul_smoke",
            GasProperties.builder(50.0)
                    .density(1.8)
                    .windSensitivity(0.4f)
                    .color(0x6033AAFF)
                    .toxic(50f, ToxicEffect.WITHER)
                    .reactivity(ReactivityFlag.OPAQUE_DENSE)
                    .build());

    public static final Gas BLAZE_FUME = register("blaze_fume",
            GasProperties.builder(35.0)
                    .density(0.9)
                    .windSensitivity(0.7f)
                    .color(0x40FFAA00)
                    .toxic(30f, ToxicEffect.FIRE)
                    .flammable(0.01f, 0.99f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas ENDER_PARTICULATE = register("ender_particulate",
            GasProperties.builder(200.0)
                    .density(0.01)            // strange — defies normal density rules
                    .windSensitivity(0.0f)
                    .color(0x508833AA)
                    .toxic(100f, ToxicEffect.BLINDNESS)
                    .build());

    // =========================================================================
    // Additional real gases — toxics, industrials, biologicals
    // =========================================================================

    public static final Gas ARSINE = register("arsine",
            GasProperties.builder(77.945)
                    .density(2.695)
                    .windSensitivity(0.3f)
                    .color(0x28889966)
                    .toxic(2f, ToxicEffect.WITHER)
                    .flammable(0.05f, 0.78f)
                    .build());

    public static final Gas PHOSPHINE = register("phosphine",
            GasProperties.builder(33.998)
                    .density(1.179)
                    .windSensitivity(0.7f)
                    .color(0x20AACC88)
                    .toxic(5f, ToxicEffect.WITHER)
                    .flammable(0.017f, 0.98f)
                    .build());

    public static final Gas SILANE = register("silane",
            GasProperties.builder(32.117)
                    .density(1.110)
                    .windSensitivity(0.8f)
                    .toxic(15f, ToxicEffect.POISON)
                    .flammable(0.014f, 0.96f)
                    .reactivity(ReactivityFlag.HYPERGOLIC)  // self-ignites in air
                    .build());

    public static final Gas DIBORANE = register("diborane",
            GasProperties.builder(27.670)
                    .density(0.956)
                    .windSensitivity(0.9f)
                    .color(0x20DDDDAA)
                    .toxic(3f, ToxicEffect.INSTANT_DAMAGE)
                    .flammable(0.008f, 0.88f)
                    .reactivity(ReactivityFlag.HYPERGOLIC)
                    .build());

    public static final Gas FLUORINE = register("fluorine",
            GasProperties.builder(37.997)
                    .density(1.312)
                    .windSensitivity(0.7f)
                    .color(0x38EEFFCC)
                    .toxic(1f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.HYPERGOLIC | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas BROMINE_GAS = register("bromine_gas",
            GasProperties.builder(159.808)
                    .density(5.514)
                    .windSensitivity(0.1f)
                    .color(0x50AA3300)
                    .toxic(3f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.WATER_SOLUBLE)
                    .build());

    public static final Gas IODINE_GAS = register("iodine_gas",
            GasProperties.builder(253.809)
                    .density(8.784)
                    .windSensitivity(0.05f)
                    .color(0x50550066)
                    .toxic(10f, ToxicEffect.POISON)
                    .build());

    public static final Gas BORON_TRIFLUORIDE = register("boron_trifluoride",
            GasProperties.builder(67.806)
                    .density(2.344)
                    .windSensitivity(0.35f)
                    .color(0x28EEEEFF)
                    .toxic(3f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas TUNGSTEN_HEXAFLUORIDE = register("tungsten_hexafluoride",
            GasProperties.builder(297.830)
                    .density(12.4)
                    .windSensitivity(0.02f)
                    .color(0x30DDDDCC)
                    .toxic(2f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas NITROGEN_TRIFLUORIDE = register("nitrogen_trifluoride",
            GasProperties.builder(71.002)
                    .density(2.455)
                    .windSensitivity(0.35f)
                    .color(0x18AABBCC)
                    .toxic(20f, ToxicEffect.WITHER)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas SULFUR_HEXAFLUORIDE = register("sulfur_hexafluoride",
            GasProperties.builder(146.060)
                    .density(6.164)
                    .windSensitivity(0.1f)
                    .toxic(600f, ToxicEffect.SUFFOCATION)   // asphyxiant at extreme conc only
                    .reactivity(ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas CARBON_DISULFIDE = register("carbon_disulfide",
            GasProperties.builder(76.139)
                    .density(2.634)
                    .windSensitivity(0.3f)
                    .color(0x20AAAA66)
                    .toxic(15f, ToxicEffect.NAUSEA)
                    .flammable(0.01f, 0.50f)
                    .reactivity(ReactivityFlag.SULFUROUS)
                    .build());

    public static final Gas DIMETHYL_ETHER = register("dimethyl_ether",
            GasProperties.builder(46.068)
                    .density(1.590)
                    .windSensitivity(0.5f)
                    .flammable(0.034f, 0.27f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    public static final Gas FORMALDEHYDE = register("formaldehyde",
            GasProperties.builder(30.026)
                    .density(1.040)
                    .windSensitivity(0.8f)
                    .color(0x20DDDDAA)
                    .toxic(5f, ToxicEffect.POISON)
                    .flammable(0.07f, 0.73f)
                    .build());

    public static final Gas ACETALDEHYDE = register("acetaldehyde",
            GasProperties.builder(44.053)
                    .density(1.521)
                    .windSensitivity(0.5f)
                    .color(0x18EEEECC)
                    .toxic(30f, ToxicEffect.NAUSEA)
                    .flammable(0.04f, 0.57f)
                    .build());

    public static final Gas ACROLEIN = register("acrolein",
            GasProperties.builder(56.064)
                    .density(1.938)
                    .windSensitivity(0.4f)
                    .color(0x30CCAA88)
                    .toxic(2f, ToxicEffect.INSTANT_DAMAGE)
                    .flammable(0.028f, 0.31f)
                    .build());

    public static final Gas ETHYLENE_OXIDE = register("ethylene_oxide",
            GasProperties.builder(44.053)
                    .density(1.521)
                    .windSensitivity(0.5f)
                    .color(0x18EEDDCC)
                    .toxic(5f, ToxicEffect.WITHER)
                    .flammable(0.03f, 0.80f)
                    .reactivity(ReactivityFlag.HYPERGOLIC)
                    .build());

    public static final Gas METHYL_CHLORIDE = register("methyl_chloride",
            GasProperties.builder(50.488)
                    .density(1.785)
                    .windSensitivity(0.5f)
                    .toxic(20f, ToxicEffect.NAUSEA)
                    .flammable(0.085f, 0.175f)
                    .build());

    public static final Gas VINYL_CHLORIDE = register("vinyl_chloride",
            GasProperties.builder(62.498)
                    .density(2.156)
                    .windSensitivity(0.4f)
                    .color(0x20CCCCEE)
                    .toxic(15f, ToxicEffect.WITHER)
                    .flammable(0.038f, 0.31f)
                    .build());

    public static final Gas TRICHLOROSILANE = register("trichlorosilane",
            GasProperties.builder(135.452)
                    .density(4.678)
                    .windSensitivity(0.15f)
                    .color(0x28DDEEEE)
                    .toxic(5f, ToxicEffect.POISON)
                    .flammable(0.07f, 0.61f)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas NITRIC_ACID_VAPOR = register("nitric_acid_vapor",
            GasProperties.builder(63.013)
                    .density(2.177)
                    .windSensitivity(0.4f)
                    .color(0x38FFEE44)
                    .toxic(3f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas SULFURIC_ACID_VAPOR = register("sulfuric_acid_vapor",
            GasProperties.builder(98.079)
                    .density(3.389)
                    .windSensitivity(0.2f)
                    .color(0x40EECC00)
                    .toxic(2f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID | ReactivityFlag.SULFUROUS)
                    .build());

    public static final Gas HYDRAZINE = register("hydrazine",
            GasProperties.builder(32.045)
                    .density(1.107)
                    .windSensitivity(0.8f)
                    .color(0x20AABBAA)
                    .toxic(3f, ToxicEffect.WITHER)
                    .flammable(0.025f, 1.00f)
                    .reactivity(ReactivityFlag.HYPERGOLIC)
                    .build());

    public static final Gas OXYGEN_DIFLUORIDE = register("oxygen_difluoride",
            GasProperties.builder(54.000)
                    .density(1.865)
                    .windSensitivity(0.5f)
                    .color(0x30EEEEFF)
                    .toxic(1f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.HYPERGOLIC | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas CHLORINE_TRIFLUORIDE = register("chlorine_trifluoride",
            GasProperties.builder(92.448)
                    .density(3.195)
                    .windSensitivity(0.2f)
                    .color(0x40DDFF88)
                    .toxic(1f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.HYPERGOLIC | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas NITROGEN_MUSTARD_GAS = register("nitrogen_mustard",
            GasProperties.builder(156.070)
                    .density(5.394)
                    .windSensitivity(0.1f)
                    .color(0x50886644)
                    .toxic(1f, ToxicEffect.INSTANT_DAMAGE)
                    .build());

    public static final Gas CYANOGEN = register("cyanogen",
            GasProperties.builder(52.035)
                    .density(1.797)
                    .windSensitivity(0.5f)
                    .color(0x20AAAAAA)
                    .toxic(5f, ToxicEffect.WITHER)
                    .flammable(0.062f, 0.428f)
                    .build());

    public static final Gas HYDROGEN_CYANIDE = register("hydrogen_cyanide",
            GasProperties.builder(27.026)
                    .density(0.934)
                    .windSensitivity(0.9f)
                    .color(0x18DDDDCC)
                    .toxic(3f, ToxicEffect.WITHER)
                    .flammable(0.056f, 0.40f)
                    .build());

    public static final Gas CARBONYL_SULFIDE = register("carbonyl_sulfide",
            GasProperties.builder(60.075)
                    .density(2.073)
                    .windSensitivity(0.4f)
                    .toxic(25f, ToxicEffect.NAUSEA)
                    .flammable(0.12f, 0.29f)
                    .reactivity(ReactivityFlag.SULFUROUS)
                    .build());

    public static final Gas METHYL_BROMIDE = register("methyl_bromide",
            GasProperties.builder(94.939)
                    .density(3.278)
                    .windSensitivity(0.25f)
                    .color(0x28CC9988)
                    .toxic(5f, ToxicEffect.WITHER)
                    .build());

    public static final Gas PERCHLOROETHYLENE = register("perchloroethylene",
            GasProperties.builder(165.833)
                    .density(5.724)
                    .windSensitivity(0.1f)
                    .color(0x18CCDDDD)
                    .toxic(25f, ToxicEffect.NAUSEA)
                    .build());

    public static final Gas TRICHLOROETHYLENE = register("trichloroethylene",
            GasProperties.builder(131.388)
                    .density(4.538)
                    .windSensitivity(0.15f)
                    .color(0x20CCDDDD)
                    .toxic(30f, ToxicEffect.NAUSEA)
                    .build());

    public static final Gas DICHLOROMETHANE = register("dichloromethane",
            GasProperties.builder(84.933)
                    .density(2.930)
                    .windSensitivity(0.25f)
                    .color(0x18DDEEEE)
                    .toxic(40f, ToxicEffect.NAUSEA)
                    .build());

    public static final Gas DIMETHYLSULFIDE = register("dimethylsulfide",
            GasProperties.builder(62.130)
                    .density(2.147)
                    .windSensitivity(0.4f)
                    .color(0x18AAAA66)
                    .toxic(50f, ToxicEffect.NAUSEA)
                    .flammable(0.022f, 0.195f)
                    .reactivity(ReactivityFlag.SULFUROUS)
                    .build());

    public static final Gas METHANETHIOL = register("methanethiol",
            GasProperties.builder(48.107)
                    .density(1.661)
                    .windSensitivity(0.5f)
                    .color(0x20AAAA55)
                    .toxic(15f, ToxicEffect.NAUSEA)
                    .flammable(0.037f, 0.215f)
                    .reactivity(ReactivityFlag.SULFUROUS)
                    .build());

    public static final Gas ACETONE_VAPOR = register("acetone_vapor",
            GasProperties.builder(58.080)
                    .density(2.005)
                    .windSensitivity(0.4f)
                    .color(0x10EEEEDD)
                    .toxic(80f, ToxicEffect.NAUSEA)
                    .flammable(0.025f, 0.128f)
                    .build());

    public static final Gas ISOPROPANOL_VAPOR = register("isopropanol_vapor",
            GasProperties.builder(60.096)
                    .density(2.076)
                    .windSensitivity(0.4f)
                    .flammable(0.02f, 0.123f)
                    .build());

    public static final Gas TOLUENE_VAPOR = register("toluene_vapor",
            GasProperties.builder(92.141)
                    .density(3.180)
                    .windSensitivity(0.25f)
                    .color(0x18DDDDAA)
                    .toxic(40f, ToxicEffect.NAUSEA)
                    .flammable(0.012f, 0.071f)
                    .build());

    public static final Gas BENZENE_VAPOR = register("benzene_vapor",
            GasProperties.builder(78.114)
                    .density(2.697)
                    .windSensitivity(0.3f)
                    .color(0x18EEEEAA)
                    .toxic(20f, ToxicEffect.WITHER)
                    .flammable(0.014f, 0.081f)
                    .build());

    public static final Gas ETHANOL_VAPOR = register("ethanol_vapor",
            GasProperties.builder(46.068)
                    .density(1.590)
                    .windSensitivity(0.5f)
                    .flammable(0.033f, 0.19f)
                    .build());

    public static final Gas STYRENE_VAPOR = register("styrene_vapor",
            GasProperties.builder(104.150)
                    .density(3.597)
                    .windSensitivity(0.2f)
                    .color(0x18DDDDA0)
                    .toxic(50f, ToxicEffect.NAUSEA)
                    .flammable(0.009f, 0.068f)
                    .build());

    public static final Gas ACRYLONITRILE = register("acrylonitrile",
            GasProperties.builder(53.063)
                    .density(1.834)
                    .windSensitivity(0.5f)
                    .color(0x28BBDDCC)
                    .toxic(10f, ToxicEffect.WITHER)
                    .flammable(0.027f, 0.17f)
                    .build());

    public static final Gas DIMETHYLAMINE = register("dimethylamine",
            GasProperties.builder(45.084)
                    .density(1.559)
                    .windSensitivity(0.5f)
                    .color(0x18AAAAFF)
                    .toxic(20f, ToxicEffect.POISON)
                    .flammable(0.028f, 0.145f)
                    .build());

    public static final Gas TRIMETHYLAMINE = register("trimethylamine",
            GasProperties.builder(59.111)
                    .density(2.040)
                    .windSensitivity(0.4f)
                    .color(0x20AABBFF)
                    .toxic(30f, ToxicEffect.NAUSEA)
                    .flammable(0.02f, 0.115f)
                    .build());

    public static final Gas METHYLAMINE = register("methylamine",
            GasProperties.builder(31.057)
                    .density(1.074)
                    .windSensitivity(0.8f)
                    .color(0x18AAAAFF)
                    .toxic(15f, ToxicEffect.POISON)
                    .flammable(0.049f, 0.208f)
                    .build());

    public static final Gas BUTADIENE = register("butadiene",
            GasProperties.builder(54.092)
                    .density(1.869)
                    .windSensitivity(0.45f)
                    .flammable(0.02f, 0.12f)
                    .build());

    public static final Gas ISOPRENE = register("isoprene",
            GasProperties.builder(68.118)
                    .density(2.353)
                    .windSensitivity(0.35f)
                    .flammable(0.019f, 0.085f)
                    .build());

    public static final Gas CYCLOPROPANE = register("cyclopropane",
            GasProperties.builder(42.081)
                    .density(1.453)
                    .windSensitivity(0.6f)
                    .toxic(200f, ToxicEffect.NAUSEA)     // anesthetic at high conc
                    .flammable(0.024f, 0.105f)
                    .build());

    public static final Gas DIFLUOROMETHANE = register("difluoromethane",
            GasProperties.builder(52.024)
                    .density(1.797)
                    .windSensitivity(0.5f)
                    .flammable(0.148f, 0.31f)
                    .reactivity(ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas TETRAFLUOROMETHANE = register("tetrafluoromethane",
            GasProperties.builder(88.005)
                    .density(3.034)
                    .windSensitivity(0.25f)
                    .reactivity(ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas HEXAFLUOROETHANE = register("hexafluoroethane",
            GasProperties.builder(138.012)
                    .density(4.766)
                    .windSensitivity(0.15f)
                    .reactivity(ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas PERFLUOROPROPANE = register("perfluoropropane",
            GasProperties.builder(188.020)
                    .density(6.495)
                    .windSensitivity(0.08f)
                    .reactivity(ReactivityFlag.GREENHOUSE)
                    .build());

    public static final Gas NITROGEN_PENTOXIDE = register("nitrogen_pentoxide",
            GasProperties.builder(108.010)
                    .density(3.731)
                    .windSensitivity(0.2f)
                    .color(0x40FFEE66)
                    .toxic(2f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    public static final Gas DINITROGEN_TETROXIDE = register("dinitrogen_tetroxide",
            GasProperties.builder(92.011)
                    .density(3.178)
                    .windSensitivity(0.25f)
                    .color(0x38DDAA00)
                    .toxic(3f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.OXIDISER | ReactivityFlag.HYPERGOLIC)
                    .build());

    public static final Gas SMOKE_PARTICULATE = register("smoke_particulate",
            GasProperties.builder(40.0)
                    .density(1.3)
                    .windSensitivity(0.6f)
                    .color(0x70444444)
                    .toxic(100f, ToxicEffect.MINING_FATIGUE)
                    .reactivity(ReactivityFlag.OPAQUE_DENSE)
                    .build());

    public static final Gas ASH_CLOUD = register("ash_cloud",
            GasProperties.builder(60.0)
                    .density(2.1)
                    .windSensitivity(0.5f)
                    .color(0x80555555)
                    .toxic(150f, ToxicEffect.SUFFOCATION)
                    .reactivity(ReactivityFlag.OPAQUE_DENSE)
                    .build());

    // =========================================================================
    // Wither Storm / biological / exotic gases
    // =========================================================================

    /**
     * Wither miasma — the necrotic carrier gas exhaled by the Wither Storm organism.
     * Applies wither sickness. At runtime, {@link exp.CCnewmods.mge.compat.WitherStormCompat}
     * attempts to map this to {@code witherstormmod:wither_sickness} if the addon is present;
     * falls back to vanilla {@link ToxicEffect#WITHER}.
     */
    public static final Gas WITHER_MIASMA = register("wither_miasma",
            GasProperties.builder(45.0)
                    .density(1.55)
                    .windSensitivity(0.3f)
                    .color(0x70331144)
                    .toxic(2f, ToxicEffect.WITHER)
                    .reactivity(ReactivityFlag.OPAQUE_DENSE)
                    .build());

    /** Necrotic acid vapour — hydrochloric and organic acid outgassing from digested matter. */
    public static final Gas NECROTIC_ACID_VAPOR = register("necrotic_acid_vapor",
            GasProperties.builder(52.0)
                    .density(1.8)
                    .windSensitivity(0.2f)
                    .color(0x50226622)
                    .toxic(1f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.FORMS_ACID | ReactivityFlag.WATER_SOLUBLE)
                    .build());

    /** Methane-rich biogas produced by anaerobic digestion inside the Wither Storm. */
    public static final Gas BIOGAS = register("biogas",
            GasProperties.builder(22.0)
                    .density(0.75)
                    .windSensitivity(1.0f)
                    .color(0x28448833)
                    .toxic(40f, ToxicEffect.NAUSEA)
                    .flammable(0.05f, 0.15f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    // =========================================================================
    // Upside Down specific gases
    // =========================================================================

    /**
     * Elemental fluorine — dominant reactive gas in the Upside Down atmosphere.
     * Already registered as {@link #FLUORINE} above, included here for documentation.
     * The Upside Down uses fluorine as its primary oxidiser instead of oxygen —
     * things can still combust but via fluorination reactions.
     */

    /**
     * Hydrogen fluoride gas — forms continuously wherever F₂ meets moisture or H₂.
     * Already registered as {@link #HYDROGEN_FLUORIDE} above.
     */

    /**
     * Mind Flayer spore gas — biological particulate suspension unique to the Upside Down.
     * Suspended spores at near-gaseous concentration. Highly toxic and hallucinogenic.
     */
    public static final Gas MIND_FLAYER_SPORE_GAS = register("mind_flayer_spore_gas",
            GasProperties.builder(30.0)
                    .density(1.05)
                    .windSensitivity(1.1f)
                    .color(0x60553366)
                    .toxic(5f, ToxicEffect.WITHER)
                    .reactivity(ReactivityFlag.OPAQUE_DENSE)
                    .build());

    // =========================================================================
    // Standard atmosphere composition constant
    // =========================================================================

    /**
     * Returns a map of gas → partial pressure (mbar) representing clean Earth air.
     * Total ≈ 1013.25 mbar.
     */
    public static Map<Gas, Float> standardAtmosphere() {
        Map<Gas, Float> atm = new LinkedHashMap<>();
        atm.put(NITROGEN,       780.9f);
        atm.put(OXYGEN,         209.5f);
        atm.put(ARGON,           9.30f);
        atm.put(CARBON_DIOXIDE,  0.40f);
        atm.put(NEON,            0.018f);
        atm.put(HELIUM,          0.005f);
        atm.put(METHANE,         0.002f);
        atm.put(KRYPTON,         0.001f);
        atm.put(HYDROGEN,        0.0005f);
        atm.put(NITROUS_OXIDE,   0.0003f);
        atm.put(WATER_VAPOR,     10.0f);   // ~1% humidity baseline
        return atm;
    }

    // =========================================================================
    // Registry internals
    // =========================================================================

    private static Gas register(String path, GasProperties props) {
        ResourceLocation id = new ResourceLocation(exp.CCnewmods.mge.Mge.MODID, path);
        Gas gas = new Gas(id, props);
        BY_ID.put(id.toString(), gas);
        ALL_GASES.add(gas);
        return gas;
    }

    /** Returns all registered gases. */
    public static List<Gas> all() {
        return Collections.unmodifiableList(ALL_GASES);
    }

    /** Looks up a gas by its full resource location string, e.g. {@code "mge:nitrogen"}. */
    public static Optional<Gas> get(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Optional<Gas> get(ResourceLocation id) {
        return get(id.toString());
    }

    private GasRegistry() {}
}
