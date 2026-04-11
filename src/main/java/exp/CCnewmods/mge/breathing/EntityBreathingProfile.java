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
 *   "exhale_rate_mbar_per_tick": 0.04
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

    private EntityBreathingProfile(ResourceLocation entityType,
                                    boolean needsToBreathe,
                                    ResourceLocation requiredGasId,
                                    float minimumPressureMbar,
                                    int toleranceTicks,
                                    float toxicSensitivity,
                                    ResourceLocation exhaleGasId,
                                    float exhaleRateMbarPerTick) {
        this.entityType            = entityType;
        this.needsToBreathe        = needsToBreathe;
        this.requiredGasId         = requiredGasId;
        this.minimumPressureMbar   = minimumPressureMbar;
        this.toleranceTicks        = toleranceTicks;
        this.toxicSensitivity      = toxicSensitivity;
        this.exhaleGasId           = exhaleGasId;
        this.exhaleRateMbarPerTick = exhaleRateMbarPerTick;
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
                json.has("entity") ? json.get("entity").getAsString() : fileId.toString());

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

        return new EntityBreathingProfile(entityType, needsToBreathe, requiredGasId,
                minPressure, tolerance, toxicSens, exhaleGasId, exhaleRate);
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
                    null, 0f   // no active exhalation by default — population sampling handles mobs
            );

    /** Profile for entities that do not need to breathe (undead, constructs, fish in water). */
    public static final EntityBreathingProfile NON_BREATHER =
            new EntityBreathingProfile(
                    new ResourceLocation("mge", "non_breather"),
                    false,
                    new ResourceLocation("mge", "oxygen"),
                    0f, 0, 0f,
                    null, 0f
            );
}
