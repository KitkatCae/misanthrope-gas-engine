package exp.CCnewmods.mge.mirage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks a single active phantom structure mirage being shown to the client.
 *
 * Lifetime: spawned when mirage conditions are met, dies when:
 *   - Player gets closer than {@link MirageDefinition#minDistance()} / 2
 *   - Player moves toward it (heading within 25° of mirage direction)
 *   - Conditions no longer met (night, rain, not hot biome)
 *   - Age exceeds {@link #MAX_LIFETIME_TICKS}
 */
public final class MirageInstance {

    public static final int MAX_LIFETIME_TICKS = 20 * 60 * 5; // 5 minutes

    public final MirageDefinition definition;
    public final BlockPos         origin;       // base position of the phantom structure
    public final Vec3             worldOrigin;  // Vec3 centre for distance/angle checks

    private int   age            = 0;
    private float alpha          = 0f;          // 0..1, lerped each tick
    private boolean dead         = false;

    public MirageInstance(MirageDefinition definition, BlockPos origin) {
        this.definition  = definition;
        this.origin      = origin;
        this.worldOrigin = Vec3.atCenterOf(origin);
    }

    /** Called each client tick. Returns false when the instance should be removed. */
    public boolean tick(Vec3 playerPos, Vec3 playerLook) {
        if (dead) return false;
        age++;

        double dist = playerPos.distanceTo(worldOrigin);
        int halfMin = definition.minDistance() / 2;

        // Die if player is too close
        if (dist < halfMin) { dead = true; return false; }

        // Die if player is looking/moving toward mirage (within 25°)
        Vec3 toMirage = worldOrigin.subtract(playerPos).normalize();
        double dot = toMirage.dot(playerLook.normalize());
        if (dot > Math.cos(Math.toRadians(25)) && dist < definition.maxDistance() * 0.6) {
            // Player is heading toward it — have it "run away" (die so next tick it respawns further)
            dead = true; return false;
        }

        // Die if too old
        if (age > MAX_LIFETIME_TICKS) { dead = true; return false; }

        // Alpha: fade in from minDistance..maxDistance, fade out below minDistance * 1.5
        float targetAlpha;
        if (dist > definition.maxDistance()) {
            targetAlpha = 0f;
        } else if (dist > definition.minDistance()) {
            targetAlpha = (float)((definition.maxDistance() - dist)
                    / (definition.maxDistance() - definition.minDistance()));
        } else if (dist > halfMin * 1.5) {
            targetAlpha = (float)((dist - halfMin) / (halfMin * 0.5));
        } else {
            targetAlpha = 0f;
        }

        alpha += (targetAlpha - alpha) * 0.05f; // smooth lerp
        return true;
    }

    public float getAlpha()    { return alpha; }
    public int   getAge()      { return age; }
    public boolean isDead()    { return dead; }
    public void kill()         { dead = true; }
}
