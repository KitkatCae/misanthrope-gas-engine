package exp.CCnewmods.mge.shockwave;

import net.minecraft.core.BlockPos;

public final class ShockwaveFront {
    public final BlockPos origin;
    public final float initialStrength;
    public final int maxRadius;
    public int currentRadius = 1;
    public boolean dead = false;

    public ShockwaveFront(BlockPos origin, float strength) {
        this.origin = origin.immutable();
        this.initialStrength = strength;
        this.maxRadius = Math.max(3, (int) Math.sqrt(strength / 0.1f) + 1);
    }

    public float currentStrength() {
        return currentRadius <= 0 ? initialStrength
                : initialStrength / (currentRadius * currentRadius);
    }

    public void advance() {
        currentRadius++;
        if (currentRadius > maxRadius) dead = true;
    }
}
