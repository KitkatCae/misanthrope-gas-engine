package exp.CCnewmods.mge.gas;

import net.minecraft.nbt.CompoundTag;

import java.util.*;

/**
 * A mutable, NBT-backed map of gas → partial pressure (mbar).
 *
 * <p>This is the runtime representation of the gas data stored in an
 * {@link exp.CCnewmods.mge.block.AtmosphereBlockEntity}. It is backed directly
 * by a {@link CompoundTag} so serialisation is a no-op — just hand back the tag.</p>
 */
public final class GasComposition {

    private final CompoundTag tag;

    public GasComposition(CompoundTag tag) {
        this.tag = tag;
    }

    /** Creates a new, empty composition. */
    public static GasComposition empty() {
        return new GasComposition(new CompoundTag());
    }

    /** Creates a composition from the standard Earth atmosphere. */
    public static GasComposition standard() {
        GasComposition comp = empty();
        GasRegistry.standardAtmosphere().forEach((gas, mbar) -> comp.set(gas, mbar));
        return comp;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** Returns the partial pressure of this gas in mbar, or 0 if absent. */
    public float get(Gas gas) {
        return tag.contains(gas.nbtKey()) ? tag.getFloat(gas.nbtKey()) : 0f;
    }

    public float get(String nbtKey) {
        return tag.contains(nbtKey) ? tag.getFloat(nbtKey) : 0f;
    }

    /** Total pressure of all gases in mbar. */
    public float totalPressure() {
        float total = 0f;
        for (String key : tag.getAllKeys()) total += tag.getFloat(key);
        return total;
    }

    /** O₂ partial pressure — determines breathability. */
    public float oxygenPressure() {
        return get(GasRegistry.OXYGEN);
    }

    /**
     * Whether this composition is breathable for a player.
     * Requires O₂ partial pressure ≥ 160 mbar (roughly 16% of 1 atm).
     */
    public boolean isBreathable() {
        return oxygenPressure() >= 160f;
    }

    /** True if no gases are stored (pure vacuum / uninitialized block). */
    public boolean isEmpty() {
        return tag.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void set(Gas gas, float mbar) {
        if (mbar <= 0f) {
            tag.remove(gas.nbtKey());
        } else {
            tag.putFloat(gas.nbtKey(), mbar);
        }
    }

    public void add(Gas gas, float deltaMbar) {
        float current = get(gas);
        set(gas, Math.max(0f, current + deltaMbar));
    }

    public void scale(Gas gas, float factor) {
        float current = get(gas);
        set(gas, current * factor);
    }

    /** Remove all traces of a gas below the given threshold. */
    public void prune(float thresholdMbar) {
        List<String> toRemove = new ArrayList<>();
        for (String key : tag.getAllKeys()) {
            if (tag.getFloat(key) < thresholdMbar) toRemove.add(key);
        }
        toRemove.forEach(tag::remove);
    }

    // -------------------------------------------------------------------------
    // Propagation helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the share of this composition that should be displaced into a
     * neighbouring block at the given relative offset, given a wind vector.
     *
     * <p>Each gas is allocated a share proportional to:
     * <pre>
     *   base_share × density_weight × (1 + wind_bonus × gas.windSensitivity)
     * </pre>
     * where {@code base_share} = 1/26 (equal split across all 26 neighbours before weighting),
     * {@code density_weight} biases heavy gases downward and light gases upward,
     * and {@code wind_bonus} = dot(offset_unit, windVec) clamped to [0, 1].</p>
     *
     * @param dx        Relative X offset of the neighbour (−1, 0, or 1)
     * @param dy        Relative Y offset of the neighbour (−1, 0, or 1)
     * @param dz        Relative Z offset of the neighbour (−1, 0, or 1)
     * @param windX     Wind vector X component
     * @param windY     Wind vector Y component
     * @param windZ     Wind vector Z component
     * @param totalWeight Pre-computed sum of all neighbour weights (normalizer)
     * @return A new GasComposition holding the amounts to transfer to this neighbour.
     */
    public GasComposition computeShareForNeighbour(
            int dx, int dy, int dz,
            float windX, float windY, float windZ,
            float totalWeight
    ) {
        // Magnitude of offset vector (1.0 for face, √2 for edge, √3 for corner)
        float offsetLen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Wind dot product — positive = downwind
        float windDot = (dx * windX + dy * windY + dz * windZ) / Math.max(offsetLen, 1e-5f);
        float windBonus = Math.max(0f, windDot);   // only downwind gets a bonus

        GasComposition share = GasComposition.empty();

        for (String key : tag.getAllKeys()) {
            float mbar = tag.getFloat(key);
            if (mbar <= 0f) continue;

            Gas gas = GasRegistry.get(key).orElse(null);
            if (gas == null) continue;

            GasProperties props = gas.properties();

            // Density weighting: heavy gas prefers dy=-1, light gas prefers dy=+1
            float densityBias;
            if (dy < 0) {
                densityBias = (float) Math.min(2.0, props.densityRatioToAir());
            } else if (dy > 0) {
                densityBias = (float) Math.max(0.1, 2.0 - props.densityRatioToAir());
            } else {
                densityBias = 1.0f;
            }

            float windFactor = 1.0f + windBonus * props.windSensitivity();
            float weight = densityBias * windFactor / (offsetLen * offsetLen); // inverse-square distance

            float gasShare = mbar * (weight / totalWeight);
            share.set(gas, gasShare);
        }

        return share;
    }

    // -------------------------------------------------------------------------
    // Serialisation
    // -------------------------------------------------------------------------

    /** Returns the underlying NBT tag — this IS the live data, not a copy. */
    public CompoundTag getTag() {
        return tag;
    }

    /** Writes a copy of this composition into an existing CompoundTag under the key "gases". */
    public void writeTo(CompoundTag parent) {
        parent.put("gases", tag.copy());
    }

    /** Reads a composition from a CompoundTag written by {@link #writeTo}. */
    public static GasComposition readFrom(CompoundTag parent) {
        if (parent.contains("gases")) {
            return new GasComposition(parent.getCompound("gases").copy());
        }
        return standard();
    }

    /** Deep copy. */
    public GasComposition copy() {
        return new GasComposition(tag.copy());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GasComposition{");
        tag.getAllKeys().stream().sorted().forEach(k ->
                sb.append(k).append('=').append(tag.getFloat(k)).append("mbar,"));
        if (!tag.isEmpty()) sb.deleteCharAt(sb.length() - 1);
        return sb.append('}').toString();
    }
}
