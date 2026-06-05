package exp.CCnewmods.mge.breathing;

import com.google.gson.JsonObject;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Defines how a specific entity type breathes within the MGE atmosphere system.
 *
 * <h3>JSON format (data/mge/entity_breathing/&lt;modid&gt;-&lt;entity_path&gt;.json):</h3>
 * <pre>{@code
 * {
 *   "entity": "minecraft:player",
 *   "needs_to_breathe": true,
 *   "required_gas": "mge:oxygen",
 *   "minimum_partial_pressure_mbar": 160.0,
 *   "tolerance_ticks": 20,
 *   "toxic_sensitivity": 1.0,
 *   "exhale_gas": "mge:carbon_dioxide",
 *   "exhale_rate_mbar_per_tick": 0.04,
 *   "min_flight_pressure_mbar": 0.0,
 *   "max_flight_pressure_mbar": -1.0,
 *   "flight_pressure_tolerance_ticks": 60
 * }
 * }</pre>
 *
 * <p>Fields:</p>
 * <ul>
 *   <li>{@code entity} — the entity type resource location this profile applies to.</li>
 *   <li>{@code needs_to_breathe} — if false, entity ignores all gas suffocation checks
 *       (undead, constructs, etc.). Toxic gas effects still apply unless
 *       {@code toxic_sensitivity} is 0.</li>
 *   <li>{@code required_gas} — the gas resource location the entity needs.
 *       Defaults to {@code mge:oxygen}.</li>
 *   <li>{@code minimum_partial_pressure_mbar} — partial pressure below which the entity
 *       begins suffocating. Default 160 mbar.</li>
 *   <li>{@code tolerance_ticks} — how many ticks the entity can survive below the
 *       threshold before taking damage. Default 20 (1 second).</li>
 *   <li>{@code toxic_sensitivity} — multiplier on all toxic gas thresholds.
 *       0.0 = immune to all toxins, 2.0 = doubly sensitive. Default 1.0.</li>
 *   <li>{@code exhale_gas} — the gas produced when the entity breathes. Omit or set
 *       to {@code "none"} to disable active exhalation (performance-safe default).</li>
 *   <li>{@code exhale_rate_mbar_per_tick} — mbar of exhale_gas produced per tick.
 *       Only relevant when exhale_gas is set. Default 0.04 (≈ 2.4 mbar/minute).</li>
 *   <li>{@code min_flight_pressure_mbar} — minimum total atmospheric pressure (mbar) at
 *       which this entity can sustain flight. Below this it loses flight ability and is
 *       grounded after {@code flight_pressure_tolerance_ticks}. 0 = no minimum (default).</li>
 *   <li>{@code max_flight_pressure_mbar} — maximum total atmospheric pressure (mbar) at
 *       which this entity can sustain flight. Above this it is too dense to fly and is
 *       grounded. ≤ 0 = no maximum (default).</li>
 *   <li>{@code flight_pressure_tolerance_ticks} — how many ticks the entity can be outside
 *       its flight pressure window before being grounded. Default 60 (3 seconds).</li>
 * </ul>
 */
public final class EntityBreathingProfile {

    public final ResourceLocation entityType;
    public final boolean          needsToBreathe;
    public final ResourceLocation requiredGasId;
    public final float            minimumPressureMbar;
    public final int              toleranceTicks;
    public final float            toxicSensitivity;
    public final ResourceLocation exhaleGasId;     // null = no active exhalation
    public final float            exhaleRateMbarPerTick;

    /**
     * Minimum total atmospheric pressure (mbar) required for flight.
     * 0 = no lower bound. Below this value the entity is grounded after
     * {@link #flightPressureToleranceTicks}.
     */
    public final float minFlightPressureMbar;

    /**
     * Maximum total atmospheric pressure (mbar) at which flight is possible.
     * ≤ 0 = no upper bound (most entities). Above this value the atmosphere is
     * too dense and the entity is grounded after {@link #flightPressureToleranceTicks}.
     */
    public final float maxFlightPressureMbar;

    /**
     * How strongly atmospheric wind currents push this entity when it is airborne.
     * Scaled against the wind vector from {@link exp.CCnewmods.mge.wind.WindProviderManager}.
     * <ul>
     *   <li>0.0 = completely unaffected by wind (golems, wither, etc.)</li>
     *   <li>1.0 = standard sensitivity (most flying mobs)</li>
     *   <li>2.0+ = highly sensitive (small/light creatures like bats, bees)</li>
     * </ul>
     * Default: 1.0. JSON key: {@code wind_sensitivity}.
     */
    public final float windSensitivity;

    /**
     * How many ticks the entity can remain outside its flight-pressure window before
     * being grounded. Resets as soon as pressure returns to the valid range.
     */
    public final int flightPressureToleranceTicks;

    private EntityBreathingProfile(ResourceLocation entityType,
                                    boolean needsToBreathe,
                                    ResourceLocation requiredGasId,
                                    float minimumPressureMbar,
                                    int toleranceTicks,
                                    float toxicSensitivity,
                                    ResourceLocation exhaleGasId,
                                    float exhaleRateMbarPerTick,
                                    float minFlightPressureMbar,
                                    float maxFlightPressureMbar,
                                    int flightPressureToleranceTicks,
                                    float windSensitivity) {
        this.entityType                   = entityType;
        this.needsToBreathe               = needsToBreathe;
        this.requiredGasId                = requiredGasId;
        this.minimumPressureMbar          = minimumPressureMbar;
        this.toleranceTicks               = toleranceTicks;
        this.toxicSensitivity             = toxicSensitivity;
        this.exhaleGasId                  = exhaleGasId;
        this.exhaleRateMbarPerTick        = exhaleRateMbarPerTick;
        this.minFlightPressureMbar        = minFlightPressureMbar;
        this.maxFlightPressureMbar        = maxFlightPressureMbar;
        this.flightPressureToleranceTicks = flightPressureToleranceTicks;
        this.windSensitivity              = windSensitivity;
    }

    // -------------------------------------------------------------------------
    // Flight pressure helpers
    // -------------------------------------------------------------------------

    /** Returns true if this profile imposes any flight-pressure constraints. */
    public boolean hasFlightPressureConstraint() {
        return minFlightPressureMbar > 0f || maxFlightPressureMbar > 0f;
    }

    /**
     * Returns true if {@code totalPressureMbar} is within this profile's valid flight range.
     * Always returns true when {@link #hasFlightPressureConstraint()} is false.
     */
    public boolean isFlightPressureValid(float totalPressureMbar) {
        if (minFlightPressureMbar > 0f && totalPressureMbar < minFlightPressureMbar) return false;
        if (maxFlightPressureMbar > 0f && totalPressureMbar > maxFlightPressureMbar) return false;
        return true;
    }

    // -------------------------------------------------------------------------
    // Resolved gas accessors (look up at use time, not at parse time, to avoid
    // order-of-init issues with the gas registry)
    // -------------------------------------------------------------------------

    /** Returns the resolved required gas, or O₂ if not found. */
    public Gas resolvedRequiredGas() {
        if (requiredGasId == null) return GasRegistry.OXYGEN;
        return GasRegistry.get(requiredGasId).orElse(GasRegistry.OXYGEN);
    }

    /** Returns the resolved exhale gas, or null if exhalation is disabled. */
    public Gas resolvedExhaleGas() {
        if (exhaleGasId == null) return null;
        return GasRegistry.get(exhaleGasId).orElse(null);
    }

    public boolean hasActiveExhalation() {
        return exhaleGasId != null && exhaleRateMbarPerTick > 0f;
    }

    // -------------------------------------------------------------------------
    // JSON parsing
    // -------------------------------------------------------------------------

    public static EntityBreathingProfile fromJson(ResourceLocation fileId, JsonObject json) {
        ResourceLocation entityType = new ResourceLocation(
                json.has("entity") && !json.get("entity").isJsonNull()
                        ? json.get("entity").getAsString() : fileId.toString());

        boolean needsToBreathe = !json.has("needs_to_breathe")
                || json.get("needs_to_breathe").getAsBoolean();

        ResourceLocation requiredGasId = json.has("required_gas")
                ? new ResourceLocation(json.get("required_gas").getAsString())
                : new ResourceLocation("mge", "oxygen");

        float minPressure = json.has("minimum_partial_pressure_mbar")
                ? json.get("minimum_partial_pressure_mbar").getAsFloat()
                : 160.0f;

        int tolerance = json.has("tolerance_ticks")
                ? json.get("tolerance_ticks").getAsInt()
                : 20;

        float toxicSens = json.has("toxic_sensitivity")
                ? json.get("toxic_sensitivity").getAsFloat()
                : 1.0f;

        String exhaleStr = json.has("exhale_gas")
                ? json.get("exhale_gas").getAsString()
                : null;
        ResourceLocation exhaleGasId = (exhaleStr == null || exhaleStr.equals("none"))
                ? null
                : new ResourceLocation(exhaleStr);

        float exhaleRate = json.has("exhale_rate_mbar_per_tick")
                ? json.get("exhale_rate_mbar_per_tick").getAsFloat()
                : 0.04f;

        float minFlightPressure = json.has("min_flight_pressure_mbar")
                ? json.get("min_flight_pressure_mbar").getAsFloat()
                : 0f;

        float maxFlightPressure = json.has("max_flight_pressure_mbar")
                ? json.get("max_flight_pressure_mbar").getAsFloat()
                : 0f; // 0 = no upper bound

        int flightTolerance = json.has("flight_pressure_tolerance_ticks")
                ? json.get("flight_pressure_tolerance_ticks").getAsInt()
                : 60;

        float windSensitivity = json.has("wind_sensitivity")
                ? json.get("wind_sensitivity").getAsFloat()
                : 1.0f;

        return new EntityBreathingProfile(entityType, needsToBreathe, requiredGasId,
                minPressure, tolerance, toxicSens, exhaleGasId, exhaleRate,
                minFlightPressure, maxFlightPressure, flightTolerance,
                windSensitivity);
    }

    // -------------------------------------------------------------------------
    // Built-in defaults
    // -------------------------------------------------------------------------

    /** Standard oxygen-breathing, CO₂-exhaling profile. Used for most living mobs. */
    public static final EntityBreathingProfile DEFAULT_BREATHING =
            new EntityBreathingProfile(
                    new ResourceLocation("mge", "default"),
                    true,
                    new ResourceLocation("mge", "oxygen"),
                    160.0f, 20, 1.0f,
                    null, 0f,
                    0f, 0f, 60,
                    1.0f  // standard wind sensitivity
            );

    /** Profile for entities that do not need to breathe (undead, constructs, fish in water). */
    public static final EntityBreathingProfile NON_BREATHER =
            new EntityBreathingProfile(
                    new ResourceLocation("mge", "non_breather"),
                    false,
                    new ResourceLocation("mge", "oxygen"),
                    0f, 0, 0f,
                    null, 0f,
                    0f, 0f, 60,
                    0.0f  // non-breathers unaffected by wind by default
            );
}
