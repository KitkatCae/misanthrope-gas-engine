package exp.CCnewmods.mge.gas;

/**
 * Bitmask constants for special chemical interaction behaviours.
 * Combine with bitwise OR in {@link GasProperties.Builder#reactivity(int)}.
 *
 * <p>Example: {@code ReactivityFlag.OXIDISER | ReactivityFlag.WATER_SOLUBLE}</p>
 */
public final class ReactivityFlag {
    private ReactivityFlag() {}

    public static final int NONE             = 0;

    /** Accelerates combustion of flammable gases in the same block. */
    public static final int OXIDISER         = 1;

    /** Dissolves in rain / water contact — removed from atmosphere on water touch. */
    public static final int WATER_SOLUBLE    = 1 << 1;

    /** Reacts with O₂ to produce CO₂ (combustion product gases like CO, CH₄ post-burn). */
    public static final int COMBUSTS_TO_CO2  = 1 << 2;

    /** Reacts with water vapour to produce an acid — damages players on skin contact. */
    public static final int FORMS_ACID       = 1 << 3;

    /** Condenses to liquid at ambient Minecraft temperatures — removed from gas mix in cold biomes. */
    public static final int CONDENSABLE      = 1 << 4;

    /** Reacts with O₂ to produce SO₂ or similar — for sulfur-bearing gases. */
    public static final int SULFUROUS        = 1 << 5;

    /** Explosive on contact with oxidiser even without ignition (e.g. fluorine, ozone). */
    public static final int HYPERGOLIC       = 1 << 6;

    /** Greenhouse effect — shifts biome temperature tag upward when above threshold. */
    public static final int GREENHOUSE       = 1 << 7;

    /** Radioactive — applies radiation damage (wither) without a toxicity threshold. */
    public static final int RADIOACTIVE      = 1 << 8;

    /** Absorbs light — increases local darkness / void fog when concentrated. */
    public static final int OPAQUE_DENSE     = 1 << 9;
}
