package exp.CCnewmods.mge.grid.section;

import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.dimension.DimensionAtmosphereProfile;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * A 16×16×16 block section storing gas composition and temperature for every
 * position in the section independently of the block grid.
 *
 * ── Storage format ────────────────────────────────────────────────────────────
 * Gas: sparse map of gas ordinal → float[4096].  Gases entirely absent from
 *      the section have no array.  Positions at the dimension's default for a
 *      gas store that default value explicitly — only gases whose array would
 *      be all-default are omitted entirely.  This means reads are always O(1)
 *      array lookups after the initial check.
 *
 * Temperature: float[4096] array + long[64] occupancy bitset.
 *              Only explicitly set cells have a non-NaN value.  All others
 *              return {@link #AMBIENT_SENTINEL} and callers fall back to
 *              ColdSweat / Thermodynamica.
 *
 * ── Coordinates ───────────────────────────────────────────────────────────────
 * All coordinates are section-local: x,y,z ∈ [0,15].
 * Use {@link #index(int,int,int)} = x + z*16 + y*256.
 * World-to-local: localX = worldX & 15, localY = (worldY - minY) & 15, etc.
 */
public final class EnvironmentSection {

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final int SIZE     = 16;
    public static final int VOLUME   = SIZE * SIZE * SIZE; // 4096
    public static final float AMBIENT_SENTINEL = Float.NaN;

    /** Tick priority values. */
    public static final byte PRIORITY_FULL       = 0; // near player, every tick
    public static final byte PRIORITY_BACKGROUND = 1; // keepalive, every 10 ticks
    public static final byte PRIORITY_FROZEN     = 2; // unloaded — no ticking

    // ── State ─────────────────────────────────────────────────────────────────

    /** The world-space Y of the bottom of this section (section index * 16 + minBuildHeight). */
    public final int sectionBottomY;

    /** Returns the world Y coordinate of the bottom of this section (inclusive). */
    public int getSectionBottomY() { return sectionBottomY; }

    /**
     * Gas data: maps gas ordinal (index in {@link GasRegistry#all()}) to a
     * flat float[4096] of mbar values.  Null entry = gas entirely absent from
     * section; readers return the dimension default for that gas.
     */
    private final Map<Integer, float[]> gasArrays = new HashMap<>();

    /**
     * Particulate data: maps ParticulateType ordinal → float[4096] of mg/m³ values.
     * Null entry = entirely absent (reads return 0). Default is 0 for all types.
     */
    private final Map<Integer, float[]> particulateArrays = new HashMap<>();

    /**
     * Temperature overrides.  Null = no explicit temperatures set in section.
     * When non-null, cells marked in {@link #tempOccupied} have an explicit °C.
     * Unmarked cells return AMBIENT_SENTINEL.
     */
    @Nullable private float[] temperature = null;

    /** Bitset: bit (x + z*16 + y*256) set = temperature[index] is valid. */
    private final long[] tempOccupied = new long[64];

    /** Profile of the dimension this section belongs to — used for default gas values. */
    @Nullable private DimensionAtmosphereProfile dimensionProfile = null;

    public byte tickPriority = PRIORITY_FROZEN;
    public long lastTickGameTime = 0;
    public boolean dirty = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public EnvironmentSection(int sectionBottomY) {
        this.sectionBottomY = sectionBottomY;
    }

    public void setDimensionProfile(@Nullable DimensionAtmosphereProfile profile) {
        this.dimensionProfile = profile;
    }

    // ── Indexing ──────────────────────────────────────────────────────────────

    public static int index(int lx, int ly, int lz) {
        return lx | (lz << 4) | (ly << 8);
    }

    public static int[] unpack(int idx) {
        return new int[]{ idx & 15, (idx >> 8) & 15, (idx >> 4) & 15 };
    }

    // ── Gas read ──────────────────────────────────────────────────────────────

    /**
     * Get the partial pressure of a gas at a section-local position.
     * Returns the dimension-profile default if no explicit value has been set.
     */
    public float getGas(Gas gas, int lx, int ly, int lz) {
        int ordinal = GasRegistry.ordinalOf(gas);
        float[] arr = gasArrays.get(ordinal);
        if (arr == null) return defaultFor(gas);
        return arr[index(lx, ly, lz)];
    }

    /**
     * Returns the full gas composition at a section-local position.
     * Constructs a GasComposition by reading each registered gas from its array.
     * Moderately expensive — prefer getGas() for single-gas queries.
     */
    public GasComposition getComposition(int lx, int ly, int lz) {
        GasComposition comp = GasComposition.empty();
        int idx = index(lx, ly, lz);
        for (Gas gas : GasRegistry.all()) {
            int ordinal = GasRegistry.ordinalOf(gas);
            float[] arr = gasArrays.get(ordinal);
            float val = (arr != null) ? arr[idx] : defaultFor(gas);
            if (val > 0f) comp.set(gas, val);
        }
        return comp;
    }

    // ── Gas write ─────────────────────────────────────────────────────────────

    /**
     * Set the partial pressure of a gas at a section-local position.
     * Allocates the gas array on first write.
     */
    public void setGas(Gas gas, int lx, int ly, int lz, float mbar) {
        int ordinal = GasRegistry.ordinalOf(gas);
        float[] arr = gasArrays.computeIfAbsent(ordinal, k -> initArray(gas));
        arr[index(lx, ly, lz)] = Math.max(0f, mbar);
        dirty = true;
    }

    public void addGas(Gas gas, int lx, int ly, int lz, float deltaMbar) {
        float current = getGas(gas, lx, ly, lz);
        setGas(gas, lx, ly, lz, current + deltaMbar);
    }

    /**
     * Write a full GasComposition into a single cell.
     */
    public void setComposition(int lx, int ly, int lz, GasComposition comp) {
        for (Gas gas : GasRegistry.all()) {
            float val = comp.get(gas);
            if (val > 0f || gasArrays.containsKey(GasRegistry.ordinalOf(gas))) {
                setGas(gas, lx, ly, lz, val);
            }
        }
        dirty = true;
    }

    /**
     * Prune gas arrays where every cell is at or below threshold.
     * Called periodically to reclaim memory.
     */
    public void pruneGasArrays(float thresholdMbar) {
        gasArrays.entrySet().removeIf(entry -> {
            for (float v : entry.getValue()) if (v > thresholdMbar) return false;
            return true;
        });
    }

    // ── Temperature read ──────────────────────────────────────────────────────

    /**
     * Get the temperature at a section-local position in °C.
     * Returns {@link #AMBIENT_SENTINEL} (NaN) if no explicit value is set —
     * callers should fall back to ColdSweat / Thermodynamica.
     */
    public float getTemperature(int lx, int ly, int lz) {
        if (temperature == null) return AMBIENT_SENTINEL;
        int idx = index(lx, ly, lz);
        if (!getBit(tempOccupied, idx)) return AMBIENT_SENTINEL;
        return temperature[idx];
    }

    // ── Temperature write ─────────────────────────────────────────────────────

    public void setTemperature(int lx, int ly, int lz, float celsius) {
        if (temperature == null) temperature = new float[VOLUME];
        int idx = index(lx, ly, lz);
        temperature[idx] = celsius;
        setBit(tempOccupied, idx, true);
        dirty = true;
    }

    /**
     * Clear an explicit temperature value — the position reverts to ambient.
     */
    public void clearTemperature(int lx, int ly, int lz) {
        if (temperature == null) return;
        int idx = index(lx, ly, lz);
        setBit(tempOccupied, idx, false);
        temperature[idx] = 0f;
        dirty = true;
    }

    public boolean hasExplicitTemperature(int lx, int ly, int lz) {
        if (temperature == null) return false;
        return getBit(tempOccupied, index(lx, ly, lz));
    }

    // ── Particulate read ──────────────────────────────────────────────────────

    /** Get the concentration of a particulate type at a section-local position (mg/m³). */
    public float getParticulate(ParticulateType type, int lx, int ly, int lz) {
        float[] arr = particulateArrays.get(type.ordinal());
        if (arr == null) return 0f;
        return arr[index(lx, ly, lz)];
    }

    /**
     * Returns the full ParticulateComposition at a section-local position.
     * Constructs by reading each type from its array.
     */
    public ParticulateComposition getParticulates(int lx, int ly, int lz) {
        ParticulateComposition comp = ParticulateComposition.empty();
        int idx = index(lx, ly, lz);
        for (ParticulateType type : ParticulateType.values()) {
            float[] arr = particulateArrays.get(type.ordinal());
            if (arr != null && arr[idx] > 0f) comp.set(type, arr[idx]);
        }
        return comp;
    }

    // ── Particulate write ─────────────────────────────────────────────────────

    /** Set the concentration of a particulate type at a section-local position. */
    public void setParticulate(ParticulateType type, int lx, int ly, int lz, float mgM3) {
        float[] arr = particulateArrays.computeIfAbsent(type.ordinal(), k -> new float[VOLUME]);
        arr[index(lx, ly, lz)] = Math.max(0f, mgM3);
        dirty = true;
    }

    public void addParticulate(ParticulateType type, int lx, int ly, int lz, float delta) {
        float current = getParticulate(type, lx, ly, lz);
        setParticulate(type, lx, ly, lz, current + delta);
    }

    /** Write a full ParticulateComposition into a single cell. */
    public void setParticulates(int lx, int ly, int lz, ParticulateComposition comp) {
        for (ParticulateType type : ParticulateType.values()) {
            float val = comp.get(type);
            if (val > 0f) setParticulate(type, lx, ly, lz, val);
        }
    }

    // ── Bulk transfer (for diffusion tick) ───────────────────────────────────

    /**
     * Returns the raw gas array for a gas if it exists, or null.
     * Used by {@link exp.CCnewmods.mge.grid.tick.SectionDiffusionTicker} to
     * read/write bulk data without per-cell overhead.
     */
    @Nullable
    public float[] gasArrayDirect(Gas gas) {
        return gasArrays.get(GasRegistry.ordinalOf(gas));
    }

    /**
     * Ensure a gas array exists and return it, initialising from dimension
     * defaults if newly allocated.
     */
    public float[] gasArrayOrCreate(Gas gas) {
        return gasArrays.computeIfAbsent(GasRegistry.ordinalOf(gas), k -> initArray(gas));
    }

    @Nullable
    public float[] temperatureArrayDirect() { return temperature; }
    public long[]  tempOccupiedDirect()     { return tempOccupied; }

    @Nullable
    public float[] particulateArrayDirect(ParticulateType type) {
        return particulateArrays.get(type.ordinal());
    }

    public float[] particulateArrayOrCreate(ParticulateType type) {
        return particulateArrays.computeIfAbsent(type.ordinal(), k -> new float[VOLUME]);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private float defaultFor(Gas gas) {
        if (dimensionProfile == null) {
            // Fall back to standard atmosphere
            return GasRegistry.standardAtmosphere().getOrDefault(gas, 0f);
        }
        Float val = dimensionProfile.gases.get(gas.id().toString());
        return val != null ? val : 0f;
    }

    private float[] initArray(Gas gas) {
        float[] arr = new float[VOLUME];
        float def = defaultFor(gas);
        if (def > 0f) {
            java.util.Arrays.fill(arr, def);
        }
        return arr;
    }

    private static boolean getBit(long[] bits, int idx) {
        return (bits[idx >> 6] & (1L << (idx & 63))) != 0;
    }

    private static void setBit(long[] bits, int idx, boolean val) {
        if (val) bits[idx >> 6] |=  (1L << (idx & 63));
        else     bits[idx >> 6] &= ~(1L << (idx & 63));
    }

    // ── Dimension state ───────────────────────────────────────────────────────

    /** True if any gas array is allocated (i.e. section deviates from pure defaults). */
    public boolean hasAnyGasData() { return !gasArrays.isEmpty(); }

    /** True if any explicit temperature has been set. */
    public boolean hasAnyTempData() { return temperature != null; }

    /** True if any particulate data has been written. */
    public boolean hasAnyParticulateData() { return !particulateArrays.isEmpty(); }

    // ── NBT serialisation ─────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("LastTick", lastTickGameTime);

        // Gas arrays — only save non-default arrays
        if (!gasArrays.isEmpty()) {
            CompoundTag gasTag = new CompoundTag();
            for (Map.Entry<Integer, float[]> entry : gasArrays.entrySet()) {
                Gas gas = GasRegistry.byOrdinal(entry.getKey());
                if (gas == null) continue;
                // Check if array is just all-default (don't bother saving)
                float def = defaultFor(gas);
                float[] arr = entry.getValue();
                boolean allDefault = true;
                for (float v : arr) {
                    if (Math.abs(v - def) > 0.001f) { allDefault = false; break; }
                }
                if (allDefault) continue;
                // Save as raw bytes (float→int bits for compact storage)
                int[] ints = new int[VOLUME];
                for (int i = 0; i < VOLUME; i++) ints[i] = Float.floatToRawIntBits(arr[i]);
                gasTag.putIntArray(gas.id().toString(), ints);
            }
            if (!gasTag.isEmpty()) tag.put("Gases", gasTag);
        }

        // Temperature — only save if non-empty
        if (temperature != null) {
            // Save occupancy bitset
            tag.putLongArray("TempBits", tempOccupied.clone());
            // Save only occupied cells as a compacted array
            int count = 0;
            for (long b : tempOccupied) count += Long.bitCount(b);
            if (count > 0) {
                int[] indices = new int[count];
                float[] values = new float[count];
                int k = 0;
                for (int i = 0; i < VOLUME; i++) {
                    if (getBit(tempOccupied, i)) {
                        indices[k] = i;
                        values[k]  = temperature[i];
                        k++;
                    }
                }
                tag.putIntArray("TempIdx", indices);
                int[] valBits = new int[count];
                for (int i = 0; i < count; i++) valBits[i] = Float.floatToRawIntBits(values[i]);
                tag.putIntArray("TempVal", valBits);
            }
        }

        // Particulate arrays
        if (!particulateArrays.isEmpty()) {
            CompoundTag partTag = new CompoundTag();
            for (Map.Entry<Integer, float[]> entry : particulateArrays.entrySet()) {
                int ordinal = entry.getKey();
                if (ordinal < 0 || ordinal >= ParticulateType.values().length) continue;
                ParticulateType type = ParticulateType.values()[ordinal];
                float[] arr = entry.getValue();
                boolean allZero = true;
                for (float v : arr) if (v > 0.001f) { allZero = false; break; }
                if (allZero) continue;
                int[] ints = new int[VOLUME];
                for (int i = 0; i < VOLUME; i++) ints[i] = Float.floatToRawIntBits(arr[i]);
                partTag.putIntArray(type.id, ints);
            }
            if (!partTag.isEmpty()) tag.put("Particulates", partTag);
        }

        dirty = false;
        return tag;
    }

    public static EnvironmentSection load(CompoundTag tag, int sectionBottomY,
                                           @Nullable DimensionAtmosphereProfile profile) {
        EnvironmentSection sec = new EnvironmentSection(sectionBottomY);
        sec.dimensionProfile = profile;
        sec.lastTickGameTime = tag.getLong("LastTick");

        // Gas arrays
        if (tag.contains("Gases", Tag.TAG_COMPOUND)) {
            CompoundTag gasTag = tag.getCompound("Gases");
            for (String key : gasTag.getAllKeys()) {
                Gas gas = GasRegistry.get(key).orElse(null);
                if (gas == null) continue;
                int[] ints = gasTag.getIntArray(key);
                if (ints.length != VOLUME) continue;
                float[] arr = new float[VOLUME];
                for (int i = 0; i < VOLUME; i++) arr[i] = Float.intBitsToFloat(ints[i]);
                sec.gasArrays.put(GasRegistry.ordinalOf(gas), arr);
            }
        }

        // Particulate arrays
        if (tag.contains("Particulates", Tag.TAG_COMPOUND)) {
            CompoundTag partTag = tag.getCompound("Particulates");
            for (ParticulateType type : ParticulateType.values()) {
                if (!partTag.contains(type.id)) continue;
                int[] ints = partTag.getIntArray(type.id);
                if (ints.length != VOLUME) continue;
                float[] arr = new float[VOLUME];
                for (int i = 0; i < VOLUME; i++) arr[i] = Float.intBitsToFloat(ints[i]);
                sec.particulateArrays.put(type.ordinal(), arr);
            }
        }

        // Temperature
        if (tag.contains("TempBits") && tag.contains("TempIdx")) {
            long[] bits = tag.getLongArray("TempBits");
            if (bits.length == 64) {
                System.arraycopy(bits, 0, sec.tempOccupied, 0, 64);
                int[] indices = tag.getIntArray("TempIdx");
                int[] valBits = tag.getIntArray("TempVal");
                if (indices.length == valBits.length && indices.length > 0) {
                    sec.temperature = new float[VOLUME];
                    for (int k = 0; k < indices.length; k++) {
                        sec.temperature[indices[k]] = Float.intBitsToFloat(valBits[k]);
                    }
                }
            }
        }

        return sec;
    }
}
