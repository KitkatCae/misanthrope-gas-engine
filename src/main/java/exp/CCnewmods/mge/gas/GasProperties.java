package exp.CCnewmods.mge.gas;

/**
 * Immutable record describing the physical and chemical properties of a gas.
 *
 * <p>Partial pressures in the atmosphere block NBT are stored in millibars (mbar).
 * Standard Earth atmosphere = 1013.25 mbar total.</p>
 *
 * @param molarMassGPerMol      Molar mass in g/mol. Used for density weighting during propagation.
 * @param densityRatioToAir     Density relative to dry air at STP (air = 1.0).
 *                              > 1.0 = heavier than air (sinks), < 1.0 = lighter (rises).
 * @param colorARGB             ARGB tint applied to the atmosphere block renderer when this gas
 *                              is present. Alpha scales with concentration. 0 = invisible (default air).
 * @param toxicThresholdMbar    Partial pressure above which this gas causes harm to players (mbar).
 *                              0 = non-toxic.
 * @param toxicEffect           The effect applied when toxicThresholdMbar is exceeded.
 *                              Use {@link ToxicEffect#NONE} for inert gases.
 * @param lowerExplosiveLimit   Fraction of total pressure (0.0–1.0) below which the gas cannot ignite.
 *                              0 = non-flammable.
 * @param upperExplosiveLimit   Fraction of total pressure above which the gas cannot ignite (too rich).
 *                              0 = non-flammable.
 * @param breathable            True if this gas can substitute for O₂ in a breathable mix.
 *                              Only O₂ itself and a handful of exotic mixes should be true.
 * @param o2EquivalentFraction  If breathable, the fraction of O₂-equivalent respiration this gas provides.
 *                              1.0 for O₂ itself, 0 for all others.
 * @param windSensitivity       Multiplier on wind-vector contribution during propagation (0.0–2.0).
 *                              Light gases like H₂ and He have high sensitivity; heavy gases like Rn have low.
 * @param reactivityFlags       Bitmask of {@link ReactivityFlag} values for special interaction handling.
 */
public record GasProperties(
        double molarMassGPerMol,
        double densityRatioToAir,
        int    colorARGB,
        float  toxicThresholdMbar,
        ToxicEffect toxicEffect,
        float  lowerExplosiveLimit,
        float  upperExplosiveLimit,
        boolean breathable,
        float  o2EquivalentFraction,
        float  windSensitivity,
        int    reactivityFlags
) {
    /** Non-flammable, non-toxic, invisible default — matches standard N₂ behaviour. */
    public static final GasProperties INERT_INVISIBLE = new GasProperties(
            28.014, 0.967, 0x00000000, 0f, ToxicEffect.NONE,
            0f, 0f, false, 0f, 0.8f, ReactivityFlag.NONE
    );

    public boolean isFlammable() {
        return lowerExplosiveLimit > 0f && upperExplosiveLimit > lowerExplosiveLimit;
    }

    public boolean isToxic() {
        return toxicThresholdMbar > 0f && toxicEffect != ToxicEffect.NONE;
    }

    public boolean hasReactivity(int flag) {
        return (reactivityFlags & flag) != 0;
    }

    // -------------------------------------------------------------------------
    // Builder — fluent API for GasRegistry declarations
    // -------------------------------------------------------------------------

    public static Builder builder(double molarMass) {
        return new Builder(molarMass);
    }

    public static final class Builder {
        private final double molarMass;
        private double  densityRatio    = 1.0;
        private int     color           = 0x00000000;
        private float   toxicThreshold  = 0f;
        private ToxicEffect toxicEffect = ToxicEffect.NONE;
        private float   lel             = 0f;
        private float   uel             = 0f;
        private boolean breathable      = false;
        private float   o2Equiv         = 0f;
        private float   windSens        = 0.8f;
        private int     reactivity      = ReactivityFlag.NONE;

        private Builder(double molarMass) { this.molarMass = molarMass; }

        public Builder density(double ratio)              { densityRatio = ratio;     return this; }
        public Builder color(int argb)                    { color = argb;             return this; }
        /** Partial pressure in mbar above which the gas harms the player. */
        public Builder toxic(float mbar, ToxicEffect e)  { toxicThreshold = mbar; toxicEffect = e; return this; }
        public Builder flammable(float lel, float uel)   { this.lel = lel; this.uel = uel; return this; }
        public Builder breathable(float o2Equiv)          { breathable = true; this.o2Equiv = o2Equiv; return this; }
        public Builder windSensitivity(float s)           { windSens = s;             return this; }
        public Builder reactivity(int flags)              { reactivity = flags;       return this; }

        public GasProperties build() {
            return new GasProperties(molarMass, densityRatio, color,
                    toxicThreshold, toxicEffect, lel, uel,
                    breathable, o2Equiv, windSens, reactivity);
        }
    }
}
