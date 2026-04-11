package exp.CCnewmods.mge.breathing;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-entity suffocation countdown ticks for MGE's custom breathing system.
 *
 * <p>Rather than using a Forge capability (which requires registration, serialisation,
 * and sync boilerplate), we use a simple server-side UUID → countdown map. The map
 * is cleared on server stop. Entities that leave the world naturally fall off the map
 * via {@link #remove(LivingEntity)}.</p>
 *
 * <p>The countdown represents how many ticks the entity has remaining before they
 * start taking suffocation damage from inadequate required-gas partial pressure.
 * It starts at {@link EntityBreathingProfile#toleranceTicks} and counts down each
 * check interval while the entity is below the threshold, resetting when they're in
 * adequate atmosphere.</p>
 */
public final class BreathingTracker {

    /** UUID → remaining tolerance ticks before suffocation damage begins. */
    private static final Map<UUID, Integer> SUFFOCATION_COUNTDOWN = new ConcurrentHashMap<>();

    private BreathingTracker() {}

    /**
     * Returns the remaining tolerance ticks for this entity.
     * If absent, initialises to the profile's full tolerance.
     */
    public static int getCountdown(LivingEntity entity, EntityBreathingProfile profile) {
        return SUFFOCATION_COUNTDOWN.computeIfAbsent(
                entity.getUUID(), k -> profile.toleranceTicks);
    }

    /** Decrements the countdown by the given amount, flooring at 0. Returns new value. */
    public static int decrementCountdown(LivingEntity entity, int amount) {
        return SUFFOCATION_COUNTDOWN.compute(entity.getUUID(), (k, v) ->
                Math.max(0, (v == null ? 0 : v) - amount));
    }

    /** Resets the countdown to the profile's full tolerance (entity is breathing fine). */
    public static void resetCountdown(LivingEntity entity, EntityBreathingProfile profile) {
        SUFFOCATION_COUNTDOWN.put(entity.getUUID(), profile.toleranceTicks);
    }

    /** Removes the entity's countdown entry (called on entity removal). */
    public static void remove(LivingEntity entity) {
        SUFFOCATION_COUNTDOWN.remove(entity.getUUID());
    }

    /** Clears all entries — called on server stop. */
    public static void clear() {
        SUFFOCATION_COUNTDOWN.clear();
    }

    public static int size() { return SUFFOCATION_COUNTDOWN.size(); }
}
