package exp.CCnewmods.mge.dimension;

import com.google.gson.JsonObject;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Defines the default atmospheric composition for a dimension.
 *
 * <p>Loaded from JSON at {@code data/mge/dimension_atmosphere/<dimension_id_path>.json}.
 * The file name uses the dimension's path component only (the namespace is implicit from
 * the subdirectory structure, but since we support profiles for any mod's dimension we
 * use the full namespaced path with {@code /} replaced by {@code -} in the filename).</p>
 *
 * <p>Example: {@code data/mge/dimension_atmosphere/minecraft-the_nether.json}</p>
 *
 * <h3>JSON format:</h3>
 * <pre>{@code
 * {
 *   "dimension": "minecraft:the_nether",
 *   "breathable": false,
 *   "has_sky_access": false,
 *   "base_pressure_mbar": 1200.0,
 *   "gases": {
 *     "mge:sulfur_dioxide": 80.0,
 *     "mge:carbon_monoxide": 120.0,
 *     "mge:nitrogen": 600.0,
 *     "mge:carbon_dioxide": 200.0
 *   },
 *   "particulates": {
 *     "ash_cloud": 30.0,
 *     "soul_dust": 15.0
 *   }
 * }
 * }</pre>
 *
 * <p>Blocks in this dimension whose {@link exp.CCnewmods.mge.block.AtmosphereBlockEntity}
 * has not been player-modified (dirty flag clear) will be initialised from this profile
 * on chunk load.</p>
 */
public final class DimensionAtmosphereProfile {

    /** The dimension this profile applies to. */
    public final ResourceLocation dimension;

    /**
     * Whether the base composition supports player breathing.
     * If false, players entering without protection immediately begin suffocating
     * regardless of O₂ partial pressure (which may be zero anyway).
     */
    public final boolean breathable;

    /**
     * Whether Project Atmosphere weather sync should apply to surface blocks in this dimension.
     * True for outdoor dimensions (Overworld, Aether, Eden Ring).
     * False for enclosed/pocket dimensions (Nether, Undergarden, Limbo).
     */
    public final boolean hasSkyAccess;

    /** Total base pressure in mbar. Atmosphere blocks initialise proportionally. */
    public final float basePressureMbar;

    /**
     * Base gas composition. Keys are full gas resource location strings (e.g. {@code "mge:nitrogen"}).
     * Values are partial pressures in mbar. These will be scaled so their sum equals
     * {@link #basePressureMbar} at initialisation time.
     */
    public final Map<String, Float> gases;

    /**
     * Base particulate composition. Keys are {@link ParticulateType#id} strings (e.g. {@code "soul_dust"}).
     * Values are concentrations in mg/m³.
     */
    public final Map<String, Float> particulates;

    private DimensionAtmosphereProfile(ResourceLocation dimension, boolean breathable,
                                        boolean hasSkyAccess, float basePressureMbar,
                                        Map<String, Float> gases, Map<String, Float> particulates) {
        this.dimension = dimension;
        this.breathable = breathable;
        this.hasSkyAccess = hasSkyAccess;
        this.basePressureMbar = basePressureMbar;
        this.gases = Collections.unmodifiableMap(gases);
        this.particulates = Collections.unmodifiableMap(particulates);
    }

    // -------------------------------------------------------------------------
    // Factory — instantiate a GasComposition from this profile
    // -------------------------------------------------------------------------

    /**
     * Creates a fresh {@link GasComposition} initialised to this profile's base gases.
     * The amounts are normalised so their sum equals {@link #basePressureMbar}.
     */
    public GasComposition createGasComposition() {
        GasComposition comp = GasComposition.empty();
        if (gases.isEmpty()) return comp;

        float rawSum = (float) gases.values().stream().mapToDouble(f -> f).sum();
        float scale  = rawSum > 0 ? basePressureMbar / rawSum : 1.0f;

        for (Map.Entry<String, Float> e : gases.entrySet()) {
            Gas gas = GasRegistry.get(e.getKey()).orElse(null);
            if (gas != null) comp.set(gas, e.getValue() * scale);
        }
        return comp;
    }

    /**
     * Creates a fresh {@link ParticulateComposition} initialised to this profile's base particulates.
     */
    public ParticulateComposition createParticulateComposition() {
        ParticulateComposition parts = ParticulateComposition.empty();
        for (Map.Entry<String, Float> e : particulates.entrySet()) {
            for (ParticulateType type : ParticulateType.values()) {
                if (type.id.equals(e.getKey())) {
                    parts.set(type, e.getValue());
                    break;
                }
            }
        }
        return parts;
    }

    // -------------------------------------------------------------------------
    // JSON deserialisation
    // -------------------------------------------------------------------------

    /**
     * Parses a profile from a {@link JsonObject} loaded by {@link DimensionAtmosphereLoader}.
     */
    public static DimensionAtmosphereProfile fromJson(ResourceLocation fileId, JsonObject json) {
        ResourceLocation dimension = new ResourceLocation(
                json.has("dimension") && !json.get("dimension").isJsonNull()
                        ? json.get("dimension").getAsString() : fileId.toString());

        boolean breathable    = json.has("breathable")     && json.get("breathable").getAsBoolean();
        boolean hasSkyAccess  = !json.has("has_sky_access") || json.get("has_sky_access").getAsBoolean();
        float basePressure    = json.has("base_pressure_mbar")
                ? json.get("base_pressure_mbar").getAsFloat() : 1013.25f;

        Map<String, Float> gases = new LinkedHashMap<>();
        if (json.has("gases")) {
            json.getAsJsonObject("gases").entrySet().forEach(e ->
                    gases.put(e.getKey(), e.getValue().getAsFloat()));
        }

        Map<String, Float> particulates = new LinkedHashMap<>();
        if (json.has("particulates")) {
            json.getAsJsonObject("particulates").entrySet().forEach(e ->
                    particulates.put(e.getKey(), e.getValue().getAsFloat()));
        }

        return new DimensionAtmosphereProfile(dimension, breathable, hasSkyAccess,
                basePressure, gases, particulates);
    }

    @Override
    public String toString() {
        return "DimensionAtmosphereProfile{" + dimension + ", breathable=" + breathable + "}";
    }
}
