package exp.CCnewmods.mge.shockwave;

import exp.CCnewmods.mge.particulate.ParticulateComposition;
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
 * <p>And two further fields for the client-side shockwave shader:
 * <ul>
 *   <li>{@link #accumulatedParticulates} — a running tally of every
 *       particulate type the wave has picked up while expanding, seeded
 *       from whatever was already in the air at the origin and added to as
 *       {@link ShockwaveHandler#processShellBlock} pushes particulates
 *       outward through the shell. Sent to the client (bucketed via
 *       {@link ParticulateBucket}) so the shader can decide how dusty/ashy
 *       the wave looks and whether to spawn smoke particles.</li>
 *   <li>{@link #spawnTemperatureC} — ambient Celsius sampled once at spawn
 *       via {@code MisanthropeWorldCompat}, used to gate the shader's hot
 *       rim-glow effect.</li>
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

    // ── New fields (solid propagation) ──────────────────────────────────────

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

    // ── New fields (shader data) ────────────────────────────────────────────

    /**
     * Unique ID for this wave instance, server-assigned at construction.
     * Lets the client match periodic {@link ShockwaveDataPacket} updates to
     * the correct active visual wave rather than spawning a new one each
     * time. Not meaningful across server restarts — purely a per-session
     * correlation key.
     */
    public final long waveId;

    private static final java.util.concurrent.atomic.AtomicLong NEXT_ID =
            new java.util.concurrent.atomic.AtomicLong(1);

    /**
     * Running tally of particulates this wave has picked up — seeded at
     * spawn from whatever was already airborne at the origin, then added to
     * each tick as the shell pushes particulates outward. Server-only;
     * {@link ParticulateBucket#sumBuckets} compresses this down to 4 floats
     * before it's sent to the client in {@link ShockwaveDataPacket}.
     */
    public final ParticulateComposition accumulatedParticulates = ParticulateComposition.empty();

    /**
     * Ambient Celsius at the spawn position, sampled once via
     * {@code MisanthropeWorldCompat.getCelsiusAt} when the wave is created.
     * Not re-sampled per tick — the wave's *carried* heat is what the
     * shader cares about, not the ambient air it's currently passing
     * through, which would wash out the rim-glow as the wave spreads into
     * cooler open air.
     */
    public final float spawnTemperatureC;

    /** Ticks since this wave last sent a client update packet. */
    public int ticksSinceLastUpdate = 0;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Primary front — created by explosions / kinetic impacts. */
    public ShockwaveFront(BlockPos origin, float strength) {
        this(origin, strength, 20.0f);
    }

    /**
     * Primary front with an explicit spawn temperature. Prefer this overload
     * when the caller already has a Celsius reading (e.g.
     * {@link ShockwaveHandler#spawn} sampling via {@code MisanthropeWorldCompat}),
     * to avoid sampling twice.
     */
    public ShockwaveFront(BlockPos origin, float strength, float spawnTemperatureC) {
        this.origin              = origin.immutable();
        this.initialStrength     = strength;
        this.maxRadius           = strength * 8f; // same as original
        this.currentStrength     = strength;
        this.isSolidPropagation  = false;
        this.spawnTemperatureC   = spawnTemperatureC;
        this.waveId              = NEXT_ID.getAndIncrement();
    }

    /** Secondary front — spawned by solid-block transmission from a primary front. */
    public ShockwaveFront(BlockPos solidContactCentroid, float transmittedStrength,
                          boolean solidPropagation) {
        this(solidContactCentroid, transmittedStrength, solidPropagation, 20.0f);
    }

    /**
     * Secondary front with an explicit carried temperature, so heat carries
     * over from the primary front that spawned it rather than resetting to
     * ambient.
     */
    public ShockwaveFront(BlockPos solidContactCentroid, float transmittedStrength,
                          boolean solidPropagation, float spawnTemperatureC) {
        this.origin              = solidContactCentroid.immutable();
        this.initialStrength     = transmittedStrength;
        this.maxRadius           = transmittedStrength * SOLID_PROPAGATION_RANGE;
        this.currentStrength     = transmittedStrength;
        this.isSolidPropagation  = solidPropagation;
        this.spawnTemperatureC   = spawnTemperatureC;
        this.waveId              = NEXT_ID.getAndIncrement();
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

    /**
     * Bucketed particulate totals (mg/m³) for the client packet, indexed by
     * {@link ParticulateBucket#ordinal()}. Computed on demand rather than
     * kept continuously in sync, since it's only needed once per network
     * send, not once per tick.
     */
    public float[] particulateBuckets() {
        return ParticulateBucket.sumBuckets(accumulatedParticulates);
    }
}
