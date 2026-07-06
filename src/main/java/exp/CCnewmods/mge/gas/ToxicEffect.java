package exp.CCnewmods.mge.gas;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * The type of harm a gas inflicts on a player when its partial pressure exceeds
 * the {@link GasProperties#toxicThresholdMbar()} for the block they occupy.
 */
public enum ToxicEffect {
    NONE(null, 0),

    // Asphyxiants — displace O₂
    SUFFOCATION(null, 0),       // handled specially: triggers vanilla suffocation logic

    // Chemical irritants / systemic toxins
    POISON(MobEffects.POISON, 1),
    WITHER(MobEffects.WITHER, 1),
    NAUSEA(MobEffects.CONFUSION, 0),
    WEAKNESS(MobEffects.WEAKNESS, 0),
    BLINDNESS(MobEffects.BLINDNESS, 0),
    SLOWNESS(MobEffects.MOVEMENT_SLOWDOWN, 1),

    // Severe / immediately dangerous
    INSTANT_DAMAGE(MobEffects.HARM, 1),
    FIRE(null, 0),              // handled specially: sets entity on fire

    // Narcotic / CNS
    LEVITATION(MobEffects.LEVITATION, 0),   // repurposed for gas narcosis disorientation
    MINING_FATIGUE(MobEffects.DIG_SLOWDOWN, 2),

    /**
     * Failed alchemy potion gas. The actual MobEffect applied is not stored here —
     * it is looked up at application time via
     * {@link exp.CCnewmods.mge.event.PlayerGasEffectHandler#getPotionGasEffect(net.minecraft.core.BlockPos)},
     * which reads from a side-channel map written by the alchemy outcome resolver.
     * This entry uses null/0 as a sentinel; {@link #isSpecialHandled()} returns true,
     * routing it through the special-case branch in PlayerGasEffectHandler.
     */
    POTION_EFFECT(null, 0);

    /** The vanilla MobEffect to apply, or null for specially-handled effects. */
    public final MobEffect effect;
    /** Amplifier passed to the MobEffect (0 = level I, 1 = level II, etc.). */
    public final int amplifier;

    ToxicEffect(MobEffect effect, int amplifier) {
        this.effect = effect;
        this.amplifier = amplifier;
    }

    public boolean isSpecialHandled() {
        return effect == null;
    }
}
