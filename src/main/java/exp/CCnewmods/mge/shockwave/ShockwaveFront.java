package exp.CCnewmods.mge.shockwave;

import net.minecraft.core.BlockPos;

/**
 * Represents one expanding shockwave sphere.
 *
 * <p>This is the updated version of {@code ShockwaveFront} with two new fields
 * for solid-block propagation:
 * <ul>
 *   <li>{@link #transmittedStrength} — accumulated wave energy that passed
 *       through solid blocks this tick. If above the spawn threshold, a
 *       secondary solid-propagation front is spawned at the end of the tick.</li>
 *   <li>{@link #isSolidPropagation} — true for secondary fronts created by
 *       solid transmission. These skip gas/entity/particulate effects and only
 *       inject structural stress.</li>
 * </ul>
 *
 * All existing fields and behaviour are unchanged.
 */
public class ShockwaveFront {

    // ── Existing fields (unchanged) ───────────────────────────────────────────
    public final BlockPos origin;
    public final float initialStrength;
    public final float maxRadius;
    public float currentRadius = 0f;
    public float currentStrength;
    public boolean dead = false;

    // ── New fields ────────────────────────────────────────────────────────────

    /**
     * Accumulated transmission strength from solid blocks hit this tick.
     * Reset to 0 at the end of each tick after spawning a secondary front.
     */
    public float transmittedStrength = 0f;

    /**
     * True for secondary fronts spawned by solid-block transmission.
     * Solid-propagation fronts skip gas/entity/particulate effects and only
     * call {@code processSolidBlock} in {@link ShockwaveHandler}.
     */
    public final boolean isSolidPropagation;

    /** Minimum transmitted strength before spawning a secondary front. */
    public static final float MIN_PROPAGATION_THRESHOLD = 0.05f;

    /** Blocks of radius per unit of transmitted strength for secondary fronts. */
    public static final float SOLID_PROPAGATION_RANGE = 3.0f;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Primary front — created by explosions / kinetic impacts. */
    public ShockwaveFront(BlockPos origin, float strength) {
        this.origin              = origin.immutable();
        this.initialStrength     = strength;
        this.maxRadius           = strength * 8f; // same as original
        this.currentStrength     = strength;
        this.isSolidPropagation  = false;
    }

    /** Secondary front — spawned by solid-block transmission from a primary front. */
    public ShockwaveFront(BlockPos solidContactCentroid, float transmittedStrength,
                          boolean solidPropagation) {
        this.origin              = solidContactCentroid.immutable();
        this.initialStrength     = transmittedStrength;
        this.maxRadius           = transmittedStrength * SOLID_PROPAGATION_RANGE;
        this.currentStrength     = transmittedStrength;
        this.isSolidPropagation  = solidPropagation;
    }

    // ── Advance ───────────────────────────────────────────────────────────────

    /**
     * Advance the wave front by one tick.
     * Returns the shell strength at the current radius for use by
     * {@link ShockwaveHandler#processShellBlock}.
     */
    public float advance() {
        currentRadius += 1f;
        // Inverse-square attenuation: strength drops with distance
        float distFactor = Math.max(1f, currentRadius);
        currentStrength  = initialStrength / (distFactor * distFactor);
        if (currentRadius >= maxRadius || currentStrength < 0.005f) {
            dead = true;
        }
        return currentStrength;
    }

    public float strength() { return currentStrength; }
}
