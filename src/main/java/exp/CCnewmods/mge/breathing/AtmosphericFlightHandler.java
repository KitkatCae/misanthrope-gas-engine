package exp.CCnewmods.mge.breathing;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.compat.GliderCompatRegistry;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.wind.WindProviderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Per-tick atmospheric physics for all airborne entities — gliders and flying mobs.
 *
 * <h3>Density effects on gliders</h3>
 * <p>Atmospheric density is proportional to total pressure. Standard sea-level
 * air is ~1013 mbar. Below this, wings generate less lift and the glide ratio
 * degrades. Above it, drag increases and horizontal speed bleeds faster.</p>
 *
 * <p>For elytra-type gliders (vanilla, ornithopter via Caelus) and VCGliders,
 * we apply a downward velocity correction each tick:
 * <pre>
 *   densityRatio = totalPressure / STANDARD_PRESSURE_MBAR
 *   liftDeficit  = (1.0 - densityRatio) * LIFT_LOSS_SCALE   [thin air → negative lift]
 *   dragExcess   = (densityRatio - 1.0) * DRAG_GAIN_SCALE   [dense air → horizontal drag]
 * </pre>
 * Below {@link #MIN_GLIDE_PRESSURE_MBAR} the air is too thin to sustain any glide
 * at all and the entity falls freely (full gravity, no glide).</p>
 *
 * <h3>Wind effects on gliders</h3>
 * <p>The wind vector from {@link WindProviderManager} is applied directly to
 * {@code deltaMovement}. Tailwind adds speed; headwind slows the glider.
 * The vertical component provides or removes lift, modelling thermals and
 * downdrafts naturally. Scaled by {@link MgeConfig#gliderWindSensitivity}.</p>
 *
 * <h3>Wind effects on flying mobs</h3>
 * <p>The same wind vector is applied to airborne mobs, scaled by
 * {@link EntityBreathingProfile#windSensitivity}. Ground-contact mobs are
 * unaffected. The effect is intentionally small relative to their AI thrust
 * so mobs are nudged, not flung.</p>
 *
 * <h3>Interaction with {@link ActiveBreathingHandler}</h3>
 * <p>Flight-pressure grounding (countdown + stall) stays in
 * {@link ActiveBreathingHandler#onFlyingMobTick} since it already has the
 * {@link FlightPressureTracker} dependency. This handler is purely additive
 * atmospheric physics — density and wind — and does not duplicate that logic.</p>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AtmosphericFlightHandler {

    // ── Physical constants ────────────────────────────────────────────────────

    /**
     * Standard sea-level atmospheric pressure in mbar. Glider physics are
     * normalised to this value — ratios above/below drive lift and drag corrections.
     */
    static final float STANDARD_PRESSURE_MBAR = 1013.25f;

    /**
     * Total pressure below which an elytra/glider cannot maintain any lift at
     * all. The glider still deploys but the entity descends at free-fall rate.
     * ~50 mbar ≈ top of Earth's stratosphere; deep space / End void would be 0.
     */
    static final float MIN_GLIDE_PRESSURE_MBAR = 80.0f;

    /**
     * Maximum downward velocity correction per tick from thin-air lift loss.
     * Caps how fast the lift deficit can accelerate descent so it feels like
     * a gradual stall rather than instant drop.
     */
    private static final double MAX_LIFT_LOSS_PER_TICK = 0.08;

    /**
     * Scale factor for the lift-loss calculation.
     * At 0 mbar (vacuum), lift loss = 1.0 * this factor per tick.
     * At 500 mbar (half pressure), lift loss = 0.5 * this factor per tick.
     */
    private static final double LIFT_LOSS_SCALE = 0.06;

    /**
     * Scale factor for the horizontal drag increase in dense atmospheres.
     * At 2× standard pressure, horizontal speed is multiplied by (1 - DRAG_GAIN_SCALE).
     */
    private static final double DRAG_GAIN_SCALE = 0.012;

    private AtmosphericFlightHandler() {}

    // =========================================================================
    // Main tick handler
    // =========================================================================

    @SubscribeEvent
    public static void onEntityTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();

        // Server-side only
        if (!(entity.level() instanceof ServerLevel level)) return;
        // Skip if features are off
        if (!MgeConfig.enableAtmosphericFlightPhysics) return;

        BlockPos pos = entity.blockPosition();
        if (!level.isLoaded(pos)) return;

        float totalPressure = GridAtmosphereCompat.getTotalPressure(level, pos);
        Vec3  wind          = WindProviderManager.getWind(level, pos);

        boolean isGliding   = GliderCompatRegistry.isGliding(entity);
        boolean isPlayer    = entity instanceof Player;

        if (isGliding) {
            applyGliderPhysics(entity, totalPressure, wind);
        } else if (!isPlayer && isAirborne(entity)) {
            // Flying mob — apply wind nudge (grounding is handled in ActiveBreathingHandler)
            EntityBreathingProfile profile = EntityBreathingLoader.get(entity);
            if (profile.windSensitivity > 0f) {
                applyMobWindNudge(entity, wind, profile.windSensitivity);
            }
        }
    }

    // =========================================================================
    // Glider physics
    // =========================================================================

    /**
     * Applies density-based lift/drag correction and wind push to a gliding entity.
     *
     * <p>This runs after the mod's own glide tick has already set {@code deltaMovement},
     * so our additions are purely additive — we never zero out the mod's work.</p>
     */
    private static void applyGliderPhysics(LivingEntity entity,
                                            float totalPressure,
                                            Vec3 wind) {
        Vec3 mv = entity.getDeltaMovement();

        // ── 1. Density lift/drag ──────────────────────────────────────────────
        double densityRatio = totalPressure / STANDARD_PRESSURE_MBAR;
        double newVx = mv.x, newVy = mv.y, newVz = mv.z;

        if (totalPressure < MIN_GLIDE_PRESSURE_MBAR) {
            // Too thin to glide — cancel all upward contribution and add full gravity
            // The entity's glide code already ran; we override upward y completely.
            newVy = Math.min(newVy, 0) - 0.08; // vanilla gravity constant
        } else if (densityRatio < 1.0) {
            // Thinner than standard — lift deficit, glide ratio degrades
            double liftLoss = (1.0 - densityRatio) * LIFT_LOSS_SCALE;
            liftLoss = Math.min(liftLoss, MAX_LIFT_LOSS_PER_TICK);
            newVy -= liftLoss;
        } else if (densityRatio > 1.0) {
            // Denser than standard — extra drag on horizontal movement
            double dragFactor = 1.0 - (densityRatio - 1.0) * DRAG_GAIN_SCALE;
            dragFactor = Math.max(0.5, dragFactor); // never more than 50% drag
            newVx *= dragFactor;
            newVz *= dragFactor;
            // Dense air also gives a small extra buoyancy / lift bonus
            newVy += (densityRatio - 1.0) * 0.005;
        }

        // ── 2. Wind push ──────────────────────────────────────────────────────
        // Wind is in blocks/tick. Scale by config sensitivity for gliders.
        // Vertical wind component provides thermals (positive) or downdrafts (negative).
        double ws = MgeConfig.gliderWindSensitivity;
        if (ws > 0 && wind.lengthSqr() > 0) {
            newVx += wind.x * ws;
            newVy += wind.y * ws;
            newVz += wind.z * ws;
        }

        entity.setDeltaMovement(newVx, newVy, newVz);
    }

    // =========================================================================
    // Mob wind nudge
    // =========================================================================

    /**
     * Applies a gentle wind-direction push to an airborne flying mob.
     *
     * <p>Intentionally small — this nudges, not propels. The mob's AI
     * thrust is orders of magnitude larger; we're adding atmospheric
     * drift, not overriding navigation.</p>
     */
    private static void applyMobWindNudge(LivingEntity entity, Vec3 wind, float sensitivity) {
        if (wind.lengthSqr() < 1e-6) return;

        // Scale wind to a per-tick nudge, then multiply by the mob's wind sensitivity.
        // MOB_WIND_SCALE keeps the effect subtle relative to AI movement.
        final double MOB_WIND_SCALE = 0.015;
        Vec3 mv = entity.getDeltaMovement();
        entity.setDeltaMovement(
                mv.x + wind.x * MOB_WIND_SCALE * sensitivity,
                mv.y + wind.y * MOB_WIND_SCALE * sensitivity,
                mv.z + wind.z * MOB_WIND_SCALE * sensitivity);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns true if the entity is currently airborne — not on the ground,
     * not in water, not in a vehicle. Used to gate wind-nudge for flying mobs
     * so ground-walking mobs aren't pushed around.
     */
    private static boolean isAirborne(LivingEntity entity) {
        return !entity.onGround()
                && !entity.isInWater()
                && !entity.isInLava()
                && !entity.isPassenger();
    }
}
