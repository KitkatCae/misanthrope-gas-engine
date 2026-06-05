package exp.CCnewmods.mge.breathing;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-entity flight-pressure countdown ticks for MGE's atmospheric flight
 * constraint system.
 *
 * <p>Works identically in structure to {@link BreathingTracker}: a server-side
 * UUID → countdown map, cleared on server stop and pruned when entities leave the
 * world. The countdown represents how many ticks the entity has remaining before
 * it is grounded by atmospheric pressure being outside its valid flight range
 * (see {@link EntityBreathingProfile#minFlightPressureMbar} /
 * {@link EntityBreathingProfile#maxFlightPressureMbar}).</p>
 *
 * <p>A separate tracker (rather than reusing {@link BreathingTracker}) is used so
 * that grounding and suffocation countdowns are fully independent — an entity can
 * be fine to breathe but too pressurised to fly, or vice-versa.</p>
 */
public final class FlightPressureTracker {

    /** UUID → remaining ticks before the entity is grounded by pressure. */
    private static final Map<UUID, Integer> FLIGHT_COUNTDOWN = new ConcurrentHashMap<>();

    private FlightPressureTracker() {}

    /**
     * Returns the remaining flight-pressure tolerance ticks for this entity.
     * Initialises to the profile's full {@link EntityBreathingProfile#flightPressureToleranceTicks}
     * if absent.
     */
    public static int getCountdown(LivingEntity entity, EntityBreathingProfile profile) {
        return FLIGHT_COUNTDOWN.computeIfAbsent(
                entity.getUUID(), k -> profile.flightPressureToleranceTicks);
    }

    /**
     * Decrements the countdown by {@code amount}, flooring at 0.
     *
     * @return the new countdown value
     */
    public static int decrementCountdown(LivingEntity entity, int amount) {
        return FLIGHT_COUNTDOWN.compute(entity.getUUID(), (k, v) ->
                Math.max(0, (v == null ? 0 : v) - amount));
    }

    /**
     * Resets the countdown to the profile's full tolerance.
     * Called when pressure returns to the valid range.
     */
    public static void resetCountdown(LivingEntity entity, EntityBreathingProfile profile) {
        FLIGHT_COUNTDOWN.put(entity.getUUID(), profile.flightPressureToleranceTicks);
    }

    /** Returns {@code true} if the entity's countdown has reached zero (should be grounded). */
    public static boolean isGrounded(LivingEntity entity) {
        Integer v = FLIGHT_COUNTDOWN.get(entity.getUUID());
        return v != null && v <= 0;
    }

    /** Removes the entity's entry — called when it leaves the world. */
    public static void remove(LivingEntity entity) {
        FLIGHT_COUNTDOWN.remove(entity.getUUID());
    }

    /** Clears all entries — called on server stop. */
    public static void clear() {
        FLIGHT_COUNTDOWN.clear();
    }

    public static int size() { return FLIGHT_COUNTDOWN.size(); }
}
