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

    private static final Map<String, Gas>    BY_ID    = new LinkedHashMap<>();
    private static final List<Gas>           ALL_GASES = new ArrayList<>();
    private static final Map<String, Integer> ORDINALS  = new LinkedHashMap<>();

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
    // Soul / spectral gases
    // =========================================================================

    /**
     * Soul Essence — the concentrated necrotic energy released when a spectral or
     * undead entity is destroyed. At high partial pressure it applies the Wither
     * effect and suppresses natural health regeneration. Drifts upward slowly
     * (lighter than air — soul energy rises). Dissipates in sunlight.
     */
    public static final Gas SOUL_ESSENCE = register("soul_essence",
            GasProperties.builder(20.0)
                    .density(0.75)
                    .windSensitivity(0.6f)
                    .color(0x6033AACC)
                    .toxic(15f, ToxicEffect.WITHER)
                    .reactivity(ReactivityFlag.OPAQUE_DENSE)
                    .build());

    // =========================================================================
    // Dragon / elemental magical gases
    // =========================================================================

    /**
     * Dragon Ice Cloud — the freezing breath of an Ice Dragon. An aerosol of
     * supercooled micro-droplets and ice nucleation agents. Causes Slowness and
     * eventually Freezing at high concentration. Settles into frost on surfaces.
     * Reacts with Water Vapour in the air to form additional ice crystal deposits.
     */
    public static final Gas DRAGON_ICE_CLOUD = register("dragon_ice_cloud",
            GasProperties.builder(30.0)
                    .density(1.4)
                    .windSensitivity(0.5f)
                    .color(0x8888CCFF)
                    .toxic(20f, ToxicEffect.SLOWNESS)
                    .reactivity(ReactivityFlag.CONDENSABLE | ReactivityFlag.OPAQUE_DENSE)
                    .build());

    // =========================================================================
    // Ionised / plasma gases
    // =========================================================================

    /**
     * Ionised Air — partially ionised diatomic nitrogen and oxygen mix, produced
     * by high-energy events: lightning strikes, Warden sonic booms, electrical
     * discharges. Short-lived; slowly recombines back into N₂ and O₂.
     * Applies Weakness at sustained exposure (disrupts bioelectric signalling).
     * Contributes a faint blue-white luminescent tint.
     */
    public static final Gas IONISED_AIR = register("ionised_air",
            GasProperties.builder(28.5)
                    .density(0.98)
                    .windSensitivity(1.0f)
                    .color(0x5599BBFF)
                    .toxic(30f, ToxicEffect.WEAKNESS)
                    .reactivity(ReactivityFlag.OXIDISER)
                    .build());

    // =========================================================================
    // Metal vapors — elemental
    // =========================================================================
    // All produced by high-temperature smelting, volcanic outgassing, or fire
    // touching the pure metal. Relevant to MGE's thermal system and bloomery/
    // forge interactions. Densities computed from molar mass / 28.97 (air molar mass).

    /** Mercury vapor — outgassed from cinnabar ore roasting and liquid mercury spills.
     *  One of the most hazardous industrial vapors: CNS damage, tremors, kidney failure.
     *  Condenses back to liquid below 357 °C. Very heavy — pools at floor level. */
    public static final Gas MERCURY_VAPOR = register("mercury_vapor",
            GasProperties.builder(200.592)
                    .density(6.924)
                    .windSensitivity(0.08f)
                    .color(0x60CCCCCC)
                    .toxic(0.5f, ToxicEffect.WITHER)   // IDLH 10 mg/m³ — extremely low threshold
                    .build());

    /** Lead vapor — produced when lead is heated above its boiling point (~1750 °C),
     *  e.g. in a bloomery running too hot or during alchemical roasting.
     *  Heavy neurotoxin; settles quickly due to extreme density. */
    public static final Gas LEAD_VAPOR = register("lead_vapor",
            GasProperties.builder(207.200)
                    .density(7.150)
                    .windSensitivity(0.05f)
                    .color(0x40888888)
                    .toxic(1f, ToxicEffect.WITHER)
                    .build());

    /** Cadmium vapor — released when zinc ore or galvanized metal is smelted.
     *  Severe pulmonary toxin; causes metal fume fever and long-term kidney damage. */
    public static final Gas CADMIUM_VAPOR = register("cadmium_vapor",
            GasProperties.builder(112.414)
                    .density(3.881)
                    .windSensitivity(0.15f)
                    .color(0x30AAAAAA)
                    .toxic(1f, ToxicEffect.WITHER)
                    .build());

    /** Zinc vapor — produced during brass/zinc smelting. Causes metal fume fever
     *  (fever, chills, nausea) at moderate concentrations. Much less acutely toxic
     *  than cadmium but very common in forge environments. */
    public static final Gas ZINC_VAPOR = register("zinc_vapor",
            GasProperties.builder(65.380)
                    .density(2.257)
                    .windSensitivity(0.35f)
                    .color(0x20BBBBCC)
                    .toxic(20f, ToxicEffect.NAUSEA)    // metal fume fever onset
                    .build());

    /** Tin vapor — released during high-temperature tin smelting. Lower acute toxicity
     *  than lead/cadmium; causes respiratory irritation. Relevant to bronze alloy work. */
    public static final Gas TIN_VAPOR = register("tin_vapor",
            GasProperties.builder(118.710)
                    .density(4.098)
                    .windSensitivity(0.12f)
                    .color(0x18CCCCCC)
                    .toxic(40f, ToxicEffect.NAUSEA)
                    .build());

    /** Copper vapor — produced at extreme forge temperatures. Distinctly blue-green tint
     *  (same pigment as copper patina). Low acute toxicity but a useful atmospheric marker
     *  that a copper smelter or bronze furnace is nearby. */
    public static final Gas COPPER_VAPOR = register("copper_vapor",
            GasProperties.builder(63.546)
                    .density(2.194)
                    .windSensitivity(0.35f)
                    .color(0x3044AA66)   // blue-green
                    .toxic(80f, ToxicEffect.NAUSEA)
                    .build());

    /** Bismuth vapor — from bismuth smelting. Very low toxicity among heavy metals;
     *  used in alloys and pharmaceuticals. Iridescent pink-silver color at low pressure. */
    public static final Gas BISMUTH_VAPOR = register("bismuth_vapor",
            GasProperties.builder(208.980)
                    .density(7.214)
                    .windSensitivity(0.05f)
                    .color(0x28DDAACC)   // iridescent pink tinge
                    .toxic(150f, ToxicEffect.NAUSEA)   // relatively benign
                    .build());

    /** Antimony vapor — from stibnite roasting. Toxic metalloid vapor; causes antimony
     *  pneumoconiosis and cardiac effects at sustained exposure. */
    public static final Gas ANTIMONY_VAPOR = register("antimony_vapor",
            GasProperties.builder(121.760)
                    .density(4.203)
                    .windSensitivity(0.12f)
                    .color(0x28888899)
                    .toxic(5f, ToxicEffect.WITHER)
                    .build());

    /** Thallium vapor — extremely toxic, produced from thallium-bearing sulfide ore smelting.
     *  Historically used as rat poison. Causes hair loss and severe neurological damage.
     *  Thallotoxicosis modeled here as slow wither. */
    public static final Gas THALLIUM_VAPOR = register("thallium_vapor",
            GasProperties.builder(204.383)
                    .density(7.055)
                    .windSensitivity(0.05f)
                    .color(0x30999988)
                    .toxic(0.5f, ToxicEffect.WITHER)   // extremely potent
                    .build());

    /** Beryllium vapor — produced at extreme temperatures from beryl ore.
     *  One of the most acutely toxic metal vapors known; causes berylliosis
     *  (chronic lung granulomatosis). IDLH 4 mg/m³ — modeled as near-instant damage. */
    public static final Gas BERYLLIUM_VAPOR = register("beryllium_vapor",
            GasProperties.builder(9.012)
                    .density(0.311)
                    .windSensitivity(1.8f)   // very light, disperses rapidly upward
                    .color(0x20AAFFBB)
                    .toxic(0.5f, ToxicEffect.INSTANT_DAMAGE)
                    .build());

    /** Manganese vapor — produced in manganese steel and ferromanganese smelting.
     *  Causes manganism (Parkinson's-like neurological syndrome) at chronic exposure.
     *  Modeled as weakness + slow wither for game feel. */
    public static final Gas MANGANESE_VAPOR = register("manganese_vapor",
            GasProperties.builder(54.938)
                    .density(1.897)
                    .windSensitivity(0.45f)
                    .color(0x28AA9988)
                    .toxic(5f, ToxicEffect.WEAKNESS)
                    .build());

    /** Chromium vapor / chromium(VI) fumes — produced during stainless steel welding
     *  and high-temp chrome ore processing. Cr(VI) is a potent carcinogen and irritant. */
    public static final Gas CHROMIUM_VAPOR = register("chromium_vapor",
            GasProperties.builder(51.996)
                    .density(1.795)
                    .windSensitivity(0.5f)
                    .color(0x30889966)
                    .toxic(3f, ToxicEffect.WITHER)
                    .build());

    /** Nickel carbonyl — Ni(CO)₄, the highly toxic volatile compound formed when carbon
     *  monoxide passes over heated nickel (Mond process). Spontaneously forms in CO-rich
     *  forges running nickel alloys. Decomposed back to Ni + CO at higher temps. */
    public static final Gas NICKEL_CARBONYL = register("nickel_carbonyl",
            GasProperties.builder(170.734)
                    .density(5.893)
                    .windSensitivity(0.08f)
                    .color(0x28AACC88)
                    .toxic(0.5f, ToxicEffect.INSTANT_DAMAGE)   // IDLH 7 ppm — lethal
                    .flammable(0.02f, 0.034f)
                    .reactivity(ReactivityFlag.COMBUSTS_TO_CO2)
                    .build());

    /** Iron oxide fumes — produced during iron/steel welding and smelting. Not acutely
     *  toxic but causes siderosis (benign lung condition) at sustained exposure.
     *  The characteristic orange-brown smog of a blacksmith's forge. */
    public static final Gas IRON_OXIDE_FUMES = register("iron_oxide_fumes",
            GasProperties.builder(159.688)
                    .density(5.512)
                    .windSensitivity(0.1f)
                    .color(0x50AA5500)   // rust-orange
                    .toxic(200f, ToxicEffect.MINING_FATIGUE)   // benign at low conc
                    .reactivity(ReactivityFlag.OPAQUE_DENSE)
                    .build());

    /** Sodium vapor — produced by burning sodium metal or in sodium-cooled reactors.
     *  Famous for its intense yellow emission (used in street lamps).
     *  Reactive with water; at high concentrations the yellow glow is game-visible. */
    public static final Gas SODIUM_VAPOR = register("sodium_vapor",
            GasProperties.builder(22.990)
                    .density(0.794)
                    .windSensitivity(1.1f)
                    .color(0x60FFEE00)   // intense sodium-yellow
                    .toxic(30f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.HYPERGOLIC)
                    .build());

    /** Potassium vapor — similar to sodium vapor but even more reactive.
     *  Characteristic violet-red flame emission. Produced in potassium metal fires. */
    public static final Gas POTASSIUM_VAPOR = register("potassium_vapor",
            GasProperties.builder(39.098)
                    .density(1.350)
                    .windSensitivity(0.7f)
                    .color(0x50CC44FF)   // violet flame
                    .toxic(20f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.HYPERGOLIC)
                    .build());

    /** Lithium vapor — from lithium metal fires or battery thermal runaway.
     *  Crimson-red flame color. Lithium hydroxide fumes cause caustic burns. */
    public static final Gas LITHIUM_VAPOR = register("lithium_vapor",
            GasProperties.builder(6.941)
                    .density(0.240)
                    .windSensitivity(1.9f)   // extremely light
                    .color(0x40FF2222)   // crimson-red
                    .toxic(15f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.HYPERGOLIC)
                    .build());

    /** Aluminium oxide fumes (alumina dust) — produced during aluminium smelting and welding.
     *  Causes aluminosis at chronic exposure. Fine enough to behave as a gas phase.
     *  Characteristic white/pale gray cloud. */
    public static final Gas ALUMINIUM_OXIDE_FUMES = register("aluminium_oxide_fumes",
            GasProperties.builder(101.961)
                    .density(3.519)
                    .windSensitivity(0.25f)
                    .color(0x40DDDDDD)
                    .toxic(150f, ToxicEffect.MINING_FATIGUE)
                    .reactivity(ReactivityFlag.OPAQUE_DENSE)
                    .build());

    /** Tungsten vapor — extremely high boiling point (5555 °C); only produced in
     *  arc furnaces or magical high-temperature environments. Very heavy.
     *  Models rare exotic forge conditions. */
    public static final Gas TUNGSTEN_VAPOR = register("tungsten_vapor",
            GasProperties.builder(183.840)
                    .density(6.346)
                    .windSensitivity(0.06f)
                    .color(0x30BBBBBB)
                    .toxic(30f, ToxicEffect.WITHER)
                    .build());

    /** Osmium tetroxide — OsO₄, the highly volatile and extremely toxic oxide of osmium
     *  formed when osmium metal contacts air at elevated temperatures.
     *  Used in electron microscopy; causes corneal and pulmonary damage.
     *  Relevant to Misanthrope's osmium ore processing. */
    public static final Gas OSMIUM_TETROXIDE = register("osmium_tetroxide",
            GasProperties.builder(254.228)
                    .density(8.775)
                    .windSensitivity(0.04f)
                    .color(0x40AABBAA)
                    .toxic(0.5f, ToxicEffect.INSTANT_DAMAGE)   // IDLH 1 mg/m³ — extremely toxic
                    .build());

    /** Silver vapor — produced in cupellation (silver refining) and argentite roasting.
     *  Low acute toxicity but argyria (permanent skin discoloration) at chronic exposure.
     *  Near-invisible at low concentration. */
    public static final Gas SILVER_VAPOR = register("silver_vapor",
            GasProperties.builder(107.868)
                    .density(3.723)
                    .windSensitivity(0.15f)
                    .color(0x18DDDDEE)
                    .toxic(100f, ToxicEffect.NAUSEA)
                    .build());

    /** Gold vapor — extremely high boiling point (2856 °C); only produced in magical
     *  or extreme industrial contexts. Dense and essentially inert gas-phase. */
    public static final Gas GOLD_VAPOR = register("gold_vapor",
            GasProperties.builder(196.967)
                    .density(6.799)
                    .windSensitivity(0.06f)
                    .color(0x28FFDD44)
                    .toxic(200f, ToxicEffect.NAUSEA)   // relatively inert
                    .build());

    /** Platinum vapor — from platinum group metal smelting. Extremely rare; faint gray.
     *  Essentially biologically inert as a simple vapor. */
    public static final Gas PLATINUM_VAPOR = register("platinum_vapor",
            GasProperties.builder(195.084)
                    .density(6.733)
                    .windSensitivity(0.06f)
                    .color(0x18DDDDDD)
                    .toxic(300f, ToxicEffect.NAUSEA)
                    .build());

    /** Titanium tetrachloride (tickle) — TiCl₄, produced when titanium ore reacts with
     *  chlorine during the Kroll process. Fumes heavily in moist air forming HCl + TiO₂.
     *  Used as a smoke screen historically; extremely irritating. */
    public static final Gas TITANIUM_TETRACHLORIDE = register("titanium_tetrachloride",
            GasProperties.builder(189.679)
                    .density(6.547)
                    .windSensitivity(0.07f)
                    .color(0x60DDDDCC)
                    .toxic(3f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID | ReactivityFlag.OPAQUE_DENSE)
                    .build());

    // =========================================================================
    // Metalloid vapors
    // =========================================================================

    /** Selenium vapor — from copper/sulfide ore roasting (selenium is a byproduct).
     *  Garlic-like odor. Toxic; causes selenosis (hair/nail loss, garlic breath).
     *  Pale red-brown vapor. */
    public static final Gas SELENIUM_VAPOR = register("selenium_vapor",
            GasProperties.builder(78.971)
                    .density(2.726)
                    .windSensitivity(0.3f)
                    .color(0x28AA3300)
                    .toxic(5f, ToxicEffect.WITHER)
                    .build());

    /** Tellurium vapor — byproduct of copper/gold refining. Causes tellurium breath
     *  (garlic odor) and CNS effects. Dense vapor, reddish-brown tint. */
    public static final Gas TELLURIUM_VAPOR = register("tellurium_vapor",
            GasProperties.builder(127.600)
                    .density(4.404)
                    .windSensitivity(0.12f)
                    .color(0x28993300)
                    .toxic(8f, ToxicEffect.NAUSEA)
                    .build());

    /** Germanium tetrafluoride — volatile fluoride of germanium formed during
     *  fluorine-based refining. Analogous to SiF₄ (silicon tetrafluoride).
     *  Fumes in moist air to form HF. Moderately toxic. */
    public static final Gas GERMANIUM_TETRAFLUORIDE = register("germanium_tetrafluoride",
            GasProperties.builder(148.630)
                    .density(5.130)
                    .windSensitivity(0.1f)
                    .color(0x20DDEEEE)
                    .toxic(10f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    /** Silicon tetrafluoride — SiF₄, produced when fluorine or HF contacts silica.
     *  Very common in fluoride processing and geothermal fumaroles. Fumes in air.
     *  Sharp, choking odor. */
    public static final Gas SILICON_TETRAFLUORIDE = register("silicon_tetrafluoride",
            GasProperties.builder(104.079)
                    .density(3.593)
                    .windSensitivity(0.2f)
                    .color(0x28EEEEFF)
                    .toxic(5f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    // =========================================================================
    // Volcanic / geothermal gases (real-world fumarolic emissions)
    // =========================================================================

    /** Volcanic sulfur dioxide plume — same chemistry as SULFUR_DIOXIDE but
     *  registered separately as a higher-concentration bulk emission type for
     *  volcanoes/geothermal vents. Provides a distinct emission source tag for
     *  dimension profiles. */
    public static final Gas VOLCANIC_FUMES = register("volcanic_fumes",
            GasProperties.builder(55.0)      // mixed SO₂/H₂S/HCl/CO₂ average
                    .density(1.9)
                    .windSensitivity(0.5f)
                    .color(0x60AAAA00)
                    .toxic(4f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.SULFUROUS | ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID)
                    .build());

    /** Hydrochloric acid gas plume — volcanic HCl, emitted by lava/seawater interaction
     *  and high-chloride magma degassing. Sharper and more irritating than SO₂.
     *  Already covered by HYDROGEN_CHLORIDE but this bulk-emission form carries
     *  OPAQUE_DENSE for visual volcanic clouds. */
    public static final Gas VOLCANIC_HCL_PLUME = register("volcanic_hcl_plume",
            GasProperties.builder(36.461)
                    .density(1.268)
                    .windSensitivity(0.7f)
                    .color(0x40DDEE88)
                    .toxic(5f, ToxicEffect.POISON)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID | ReactivityFlag.OPAQUE_DENSE)
                    .build());

    /** Magmatic carbon dioxide — high-pressure CO₂ degassing from magma chambers.
     *  Identical chemistry to CARBON_DIOXIDE; registered separately for source-tagging
     *  and dimension atmosphere profiles (e.g. volcanic calderas). */
    public static final Gas MAGMATIC_CO2 = register("magmatic_co2",
            GasProperties.builder(44.010)
                    .density(1.519)
                    .windSensitivity(0.5f)
                    .color(0x18334444)
                    .toxic(30f, ToxicEffect.SUFFOCATION)
                    .reactivity(ReactivityFlag.GREENHOUSE)
                    .build());

    // =========================================================================
    // Organic biological / decomposition gases
    // =========================================================================

    /** Putrescine vapor — 1,4-diaminobutane, the characteristic odor of rotting flesh.
     *  Produced by the Corpse system's DECAYED and SKELETAL stages.
     *  Irritating at high concentration; causes nausea. */
    public static final Gas PUTRESCINE = register("putrescine",
            GasProperties.builder(88.151)
                    .density(3.044)
                    .windSensitivity(0.25f)
                    .color(0x30448833)
                    .toxic(20f, ToxicEffect.NAUSEA)
                    .build());

    /** Cadaverine — 1,5-diaminopentane, produced alongside putrescine in decaying tissue.
     *  Similar profile to putrescine; contributes to decomposition odor. */
    public static final Gas CADAVERINE = register("cadaverine",
            GasProperties.builder(102.178)
                    .density(3.527)
                    .windSensitivity(0.2f)
                    .color(0x30338833)
                    .toxic(25f, ToxicEffect.NAUSEA)
                    .build());

    /** Indole vapor — produced during tryptophan decomposition. Strong fecal/putrid smell
     *  at high concentrations. Relevant to BLOATED corpse stage. */
    public static final Gas INDOLE = register("indole",
            GasProperties.builder(117.149)
                    .density(4.046)
                    .windSensitivity(0.15f)
                    .color(0x20336633)
                    .toxic(30f, ToxicEffect.NAUSEA)
                    .build());

    /** Skatole (3-methylindole) — alongside indole in fecal/putrid decomposition.
     *  Very characteristic; nausea effect at lower threshold than indole. */
    public static final Gas SKATOLE = register("skatole",
            GasProperties.builder(131.175)
                    .density(4.530)
                    .windSensitivity(0.15f)
                    .color(0x20335533)
                    .toxic(20f, ToxicEffect.NAUSEA)
                    .build());

    /** Acetoin vapor — produced during fermentation and dough leavening (Farmers Delight).
     *  Buttery smell; essentially non-toxic at ambient concentrations. */
    public static final Gas ACETOIN_VAPOR = register("acetoin_vapor",
            GasProperties.builder(88.106)
                    .density(3.042)
                    .windSensitivity(0.25f)
                    .toxic(200f, ToxicEffect.NAUSEA)
                    .build());

    /** Diacetyl vapor — the intense buttery compound from brewing/fermentation.
     *  Causes bronchiolitis obliterans (popcorn lung) at very high industrial concentrations.
     *  Relevant to tavern/brewing environments. */
    public static final Gas DIACETYL = register("diacetyl",
            GasProperties.builder(86.090)
                    .density(2.973)
                    .windSensitivity(0.3f)
                    .color(0x10FFEECC)
                    .toxic(50f, ToxicEffect.MINING_FATIGUE)
                    .build());

    /** Acetic acid vapor — vinegar; produced during fermentation, pickling, and
     *  acetylation reactions. Mildly irritating to eyes/respiratory system.
     *  Relevant to Farmers Delight cooking and tanning lye preparation. */
    public static final Gas ACETIC_ACID_VAPOR = register("acetic_acid_vapor",
            GasProperties.builder(60.052)
                    .density(2.074)
                    .windSensitivity(0.45f)
                    .color(0x10EEEEDD)
                    .toxic(60f, ToxicEffect.NAUSEA)
                    .flammable(0.04f, 0.196f)
                    .reactivity(ReactivityFlag.FORMS_ACID)
                    .build());

    /** Formic acid vapor — produced by ant venom, wood distillation, and some
     *  industrial processes. More acutely irritating than acetic acid.
     *  Could be relevant to myrmex mob interactions. */
    public static final Gas FORMIC_ACID_VAPOR = register("formic_acid_vapor",
            GasProperties.builder(46.026)
                    .density(1.590)
                    .windSensitivity(0.55f)
                    .color(0x18DDDDCC)
                    .toxic(30f, ToxicEffect.POISON)
                    .flammable(0.18f, 0.57f)
                    .reactivity(ReactivityFlag.FORMS_ACID)
                    .build());

    /** Turpentine vapor — from pine resin distillation; relevant to Create distillation
     *  or tanning (pine tar). Flammable, irritant, characteristic piney smell. */
    public static final Gas TURPENTINE_VAPOR = register("turpentine_vapor",
            GasProperties.builder(136.234)
                    .density(4.703)
                    .windSensitivity(0.12f)
                    .color(0x18DDCC99)
                    .toxic(35f, ToxicEffect.NAUSEA)
                    .flammable(0.008f, 0.095f)
                    .build());

    /** Phenol vapor — carbolic acid; produced by coal tar distillation and wood
     *  pyrolysis. Strong antiseptic smell. Absorbed through skin; systemic toxin. */
    public static final Gas PHENOL_VAPOR = register("phenol_vapor",
            GasProperties.builder(94.111)
                    .density(3.250)
                    .windSensitivity(0.22f)
                    .color(0x20CCCC88)
                    .toxic(10f, ToxicEffect.WITHER)
                    .flammable(0.017f, 0.089f)
                    .build());

    /** Coal tar vapor — mixed aromatic compounds from coal distillation/coking.
     *  Carcinogenic PAH mixture; applies sustained wither. Relevant to coke ovens. */
    public static final Gas COAL_TAR_VAPOR = register("coal_tar_vapor",
            GasProperties.builder(120.0)
                    .density(4.144)
                    .windSensitivity(0.12f)
                    .color(0x50222211)
                    .toxic(15f, ToxicEffect.WITHER)
                    .flammable(0.01f, 0.07f)
                    .reactivity(ReactivityFlag.OPAQUE_DENSE)
                    .build());

    // =========================================================================
    // Nether-adjacent real chemistry
    // =========================================================================

    /** Sulfuryl fluoride — SO₂F₂. Used as a fumigant; more toxic than SO₂.
     *  Could form in the Nether where sulfur and fluorine coexist.
     *  Very dense, sinks into lava-floor depressions. */
    public static final Gas SULFURYL_FLUORIDE = register("sulfuryl_fluoride",
            GasProperties.builder(102.062)
                    .density(3.523)
                    .windSensitivity(0.2f)
                    .color(0x28CCCC88)
                    .toxic(5f, ToxicEffect.WITHER)
                    .build());

    /** Thionyl chloride — SOCl₂. Reactive intermediate produced when SO₂ and Cl₂ coexist
     *  in the right conditions. Fumes heavily in moist air. Corrosive. */
    public static final Gas THIONYL_CHLORIDE = register("thionyl_chloride",
            GasProperties.builder(118.970)
                    .density(4.107)
                    .windSensitivity(0.15f)
                    .color(0x40DDCC66)
                    .toxic(3f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID | ReactivityFlag.SULFUROUS)
                    .build());

    /** Disulfur dichloride — S₂Cl₂. Byproduct of sulfur chlorination; extremely
     *  corrosive and toxic. Relevant to alchemical sulfur processing in modpack. */
    public static final Gas DISULFUR_DICHLORIDE = register("disulfur_dichloride",
            GasProperties.builder(135.036)
                    .density(4.662)
                    .windSensitivity(0.12f)
                    .color(0x38BBAA00)
                    .toxic(2f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.WATER_SOLUBLE | ReactivityFlag.FORMS_ACID | ReactivityFlag.SULFUROUS)
                    .build());

    // =========================================================================
    // End / dimensional exotic gases
    // =========================================================================

    /** Void breath — the near-vacuum particulate trace present at extreme End altitudes
     *  and inside end voids. Not truly a gas; a plasma-adjacent state.
     *  At high 'concentration' (i.e. very low total pressure environment) causes
     *  barotrauma-style damage. */
    public static final Gas VOID_BREATH = register("void_breath",
            GasProperties.builder(0.001)
                    .density(0.0001)
                    .windSensitivity(0.0f)
                    .color(0x30110022)
                    .toxic(5f, ToxicEffect.INSTANT_DAMAGE)
                    .build());

    /** Shulker acid mist — ejected during a Shulker's levitation projectile impact.
     *  Brief caustic cloud; fades quickly. Applies instant damage at contact. */
    public static final Gas SHULKER_ACID_MIST = register("shulker_acid_mist",
            GasProperties.builder(40.0)
                    .density(1.38)
                    .windSensitivity(0.6f)
                    .color(0x50AA88CC)
                    .toxic(1f, ToxicEffect.INSTANT_DAMAGE)
                    .reactivity(ReactivityFlag.FORMS_ACID | ReactivityFlag.CONDENSABLE)
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
        ORDINALS.put(id.toString(), ALL_GASES.size() - 1);
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

    /** Returns the registration ordinal of a gas (stable for the session). */
    public static int ordinalOf(Gas gas) {
        return ORDINALS.getOrDefault(gas.id().toString(), -1);
    }

    /** Returns the gas registered at the given ordinal, or null if out of range. */
    @javax.annotation.Nullable
    public static Gas byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= ALL_GASES.size()) return null;
        return ALL_GASES.get(ordinal);
    }

    /** Get default value for a gas from standard atmosphere map. */
    public static float getOrDefault(Gas gas, float defaultVal) {
        Float v = (Float) standardAtmosphere().get(gas);
        return v != null ? v : defaultVal;
    }
}

