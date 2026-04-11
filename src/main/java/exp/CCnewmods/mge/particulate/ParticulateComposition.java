package exp.CCnewmods.mge.particulate;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks airborne particulate matter in an atmosphere block.
 *
 * <p>Stored as a sub-tag {@code "particulates"} inside the atmosphere block entity's NBT.
 * Values are in mg/m³. Settling and wind dispersal are handled by
 * {@link exp.CCnewmods.mge.propagation.AtmosphereTickScheduler}.</p>
 */
public final class ParticulateComposition {

    private final CompoundTag tag;

    public ParticulateComposition(CompoundTag tag) {
        this.tag = tag;
    }

    public static ParticulateComposition empty() {
        return new ParticulateComposition(new CompoundTag());
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public float get(ParticulateType type) {
        return tag.contains(type.nbtKey()) ? tag.getFloat(type.nbtKey()) : 0f;
    }

    public float totalConcentration() {
        float total = 0f;
        for (String key : tag.getAllKeys()) total += tag.getFloat(key);
        return total;
    }

    public boolean isEmpty() { return tag.isEmpty(); }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void set(ParticulateType type, float mgM3) {
        if (mgM3 <= 0f) tag.remove(type.nbtKey());
        else tag.putFloat(type.nbtKey(), mgM3);
    }

    public void add(ParticulateType type, float delta) {
        set(type, Math.max(0f, get(type) + delta));
    }

    public void prune(float threshold) {
        List<String> remove = new ArrayList<>();
        for (String k : tag.getAllKeys()) if (tag.getFloat(k) < threshold) remove.add(k);
        remove.forEach(tag::remove);
    }

    // -------------------------------------------------------------------------
    // Settling — called by tick scheduler
    // -------------------------------------------------------------------------

    /**
     * Apply one tick of gravitational settling and wind lofting.
     * Returns the total mg/m³ that should be transferred downward
     * to the block below (caller is responsible for applying it).
     *
     * @param windSpeed Wind speed magnitude in m/s from Project Atmosphere (0 if absent).
     */
    public float applySettling(float windSpeed) {
        float totalSettled = 0f;
        for (ParticulateType type : ParticulateType.values()) {
            float current = get(type);
            if (current <= 0f) continue;

            float settling = type.settle.mgM3PerTick;
            // Wind reduces settling: high wind sensitivity + high wind speed = stays aloft
            float windLoft = windSpeed * type.windSensitivity * 0.5f;
            float netSettle = Math.max(0f, settling - windLoft);

            float settled = Math.min(current, netSettle);
            add(type, -settled);
            totalSettled += settled;
        }
        return totalSettled;
    }

    // -------------------------------------------------------------------------
    // NBT serialisation
    // -------------------------------------------------------------------------

    public CompoundTag getTag() { return tag; }

    public void writeTo(CompoundTag parent) {
        parent.put("particulates", tag.copy());
    }

    public static ParticulateComposition readFrom(CompoundTag parent) {
        if (parent.contains("particulates"))
            return new ParticulateComposition(parent.getCompound("particulates").copy());
        return empty();
    }

    public ParticulateComposition copy() {
        return new ParticulateComposition(tag.copy());
    }
}
