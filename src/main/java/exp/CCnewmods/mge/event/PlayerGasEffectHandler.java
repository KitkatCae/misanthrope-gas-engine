package exp.CCnewmods.mge.event;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.breathing.EntityBreathingLoader;
import exp.CCnewmods.mge.breathing.EntityBreathingProfile;
import exp.CCnewmods.mge.gas.*;
import exp.CCnewmods.mge.gas.ReactivityFlag;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies gas and particulate effects to living entities based on the atmosphere
 * block at their eye position, evaluated every {@link #CHECK_INTERVAL_TICKS} ticks.
 *
 * <h3>Gas effects:</h3>
 * <ul>
 *   <li>O₂ below 160 mbar → suffocation / hypoxia</li>
 *   <li>Toxic gases above their threshold → corresponding MobEffect or special handling</li>
 *   <li>Flammable gas + O₂ in LEL–UEL range + entity on fire → combustion damage</li>
 * </ul>
 *
 * <h3>Particulate effects:</h3>
 * <ul>
 *   <li>Each {@link ParticulateType} with a toxicity threshold applies its effect
 *       when concentration exceeds that threshold.</li>
 *   <li>High opacity particulates (sand, ash) apply blindness/mining fatigue
 *       scaling with concentration.</li>
 *   <li>Ice crystals apply slowness and mild freeze damage.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerGasEffectHandler {

    private static final int   CHECK_INTERVAL_TICKS  = 11;
    private static final int   EFFECT_DURATION_TICKS  = CHECK_INTERVAL_TICKS * 3;

    private PlayerGasEffectHandler() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity.tickCount % CHECK_INTERVAL_TICKS != 0) return;

        BlockPos eyePos = BlockPos.containing(entity.getEyePosition());
        BlockEntity be  = level.getBlockEntity(eyePos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        GasComposition gases = atm.getComposition();
        ParticulateComposition particulates = atm.getParticulates();

        // Resolve this entity's breathing profile — drives all gas effect checks
        EntityBreathingProfile profile = EntityBreathingLoader.get(entity);

        if (profile.needsToBreathe) applyBreathingEffects(entity, gases, profile);
        if (profile.toxicSensitivity > 0f) applyToxicGasEffects(entity, gases, profile);
        applyFlammabilityIgnition(entity, level, eyePos, gases, atm);
        applyParticulateEffects(entity, particulates);
    }

    // =========================================================================
    // Gas effects
    // =========================================================================

    private static void applyBreathingEffects(LivingEntity entity,
                                               GasComposition comp,
                                               EntityBreathingProfile profile) {
        Gas required = profile.resolvedRequiredGas();
        float partial = comp.get(required);
        float threshold = profile.minimumPressureMbar;
        if (partial >= threshold) return;

        if (partial < threshold * 0.5f) {
            // Severe deprivation — direct suffocation damage
            entity.hurt(entity.damageSources().drown(), 2.0f);
        } else {
            // Moderate deprivation — cognitive and physical impairment
            addEffect(entity, MobEffects.DIG_SLOWDOWN, EFFECT_DURATION_TICKS, 1);
            addEffect(entity, MobEffects.CONFUSION,    EFFECT_DURATION_TICKS, 0);
        }
    }

    private static void applyToxicGasEffects(LivingEntity entity,
                                              GasComposition comp,
                                              EntityBreathingProfile profile) {
        for (String key : comp.getTag().getAllKeys()) {
            Gas gas = GasRegistry.get(key).orElse(null);
            if (gas == null || !gas.properties().isToxic()) continue;

            float mbar = comp.get(key);
            float effectiveThreshold = gas.properties().toxicThresholdMbar() / profile.toxicSensitivity;
            if (mbar < effectiveThreshold) continue;

            float excess = mbar - effectiveThreshold;
            int amp = Math.min(3, (int) (excess / effectiveThreshold));

            // Special case: wither_miasma uses the Wither Storm mod's wither_sickness
            // effect if present, falling back to vanilla wither
            if (exp.CCnewmods.mge.compat.WitherStormCompat.isWitherMiasma(key)
                    && exp.CCnewmods.mge.compat.WitherStormCompat.WITHER_SICKNESS_EFFECT != null) {
                addEffect(entity,
                        exp.CCnewmods.mge.compat.WitherStormCompat.WITHER_SICKNESS_EFFECT,
                        EFFECT_DURATION_TICKS, amp);
                continue;
            }

            switch (gas.properties().toxicEffect()) {
                case NONE        -> {}
                case SUFFOCATION -> entity.hurt(entity.damageSources().drown(), 1.0f);
                case FIRE        -> entity.setSecondsOnFire(3);
                default -> {
                    MobEffect effect = gas.properties().toxicEffect().effect;
                    if (effect != null) addEffect(entity, effect, EFFECT_DURATION_TICKS, amp);
                }
            }
        }
    }

    private static void applyFlammabilityIgnition(
            LivingEntity entity, ServerLevel level, BlockPos pos,
            GasComposition comp, AtmosphereBlockEntity atm) {
        if (!entity.isOnFire()) return;
        if (entity instanceof Player p && p.isCreative()) return;

        float totalPressure = comp.totalPressure();
        if (totalPressure <= 0f) return;

        // Compute total oxidiser fraction — O₂ plus any gas with OXIDISER flag.
        // This lets fluorine (Upside Down) and N₂O₄ (Nether) support combustion.
        float oxidiserMbar = 0f;
        for (String key : comp.getTag().getAllKeys()) {
            Gas oxGas = GasRegistry.get(key).orElse(null);
            if (oxGas == null) continue;
            if (oxGas.properties().hasReactivity(ReactivityFlag.OXIDISER)
                    || oxGas == GasRegistry.OXYGEN) {
                oxidiserMbar += comp.get(key);
            }
        }
        // Need at least 16% effective oxidiser by pressure to sustain combustion
        if (oxidiserMbar / totalPressure < 0.16f) return;

        for (String key : comp.getTag().getAllKeys()) {
            Gas gas = GasRegistry.get(key).orElse(null);
            if (gas == null || !gas.properties().isFlammable()) continue;

            float fraction = comp.get(key) / totalPressure;
            if (fraction < gas.properties().lowerExplosiveLimit()) continue;
            if (fraction > gas.properties().upperExplosiveLimit()) continue;

            // Combustion event — products depend on what oxidiser is dominant
            GasComposition c = atm.getComposition();
            float available = comp.get(key);
            float consumed  = Math.min(available, 50f);
            c.add(gas, -consumed);

            float fluorineFraction = c.get(GasRegistry.FLUORINE) / Math.max(1f, c.totalPressure());

            if (fluorineFraction > 0.1f) {
                // Fluorine-dominated combustion: produces HF, very little CO₂
                c.add(GasRegistry.FLUORINE,          -Math.min(c.get(GasRegistry.FLUORINE), consumed * 2f));
                c.add(GasRegistry.HYDROGEN_FLUORIDE,  consumed * 1.5f);
                c.add(GasRegistry.CARBON_DIOXIDE,     consumed * 0.1f);
            } else {
                // Normal O₂-based combustion
                c.add(GasRegistry.OXYGEN,         -Math.min(c.get(GasRegistry.OXYGEN), 30f));
                c.add(GasRegistry.CARBON_DIOXIDE,  25f);
                c.add(GasRegistry.CARBON_MONOXIDE,  5f);
            }

            atm.setComposition(c);

            // Inject smoke particulates
            var parts = atm.getParticulates();
            parts.add(ParticulateType.SMOKE_AEROSOL, 60f);
            parts.add(ParticulateType.SOOT,           20f);
            atm.setParticulates(parts);

            Mge.getScheduler(level).enqueueWithNeighbours(pos);
            entity.hurt(entity.damageSources().explosion(null, null), 4.0f);
            entity.setSecondsOnFire(5);
            return; // one combustion event per check interval
        }
    }

    // =========================================================================
    // Particulate effects
    // =========================================================================

    private static void applyParticulateEffects(LivingEntity entity,
                                                  ParticulateComposition particulates) {
        for (ParticulateType type : ParticulateType.values()) {
            float mgM3 = particulates.get(type);
            if (mgM3 <= 0f) continue;

            // Always apply visibility/breathing effects for high-density opaque particulates
            applyParticulateOpacityEffects(entity, type, mgM3);

            // Toxicity threshold effects
            if (!type.isToxic()) continue;
            if (mgM3 < type.toxicThresholdMgM3) continue;

            float excess = mgM3 - type.toxicThresholdMgM3;
            int amp = Math.min(3, (int) (excess / type.toxicThresholdMgM3));

            applyParticulateToxicEffect(entity, type, amp);
        }
    }

    private static void applyParticulateOpacityEffects(LivingEntity entity,
                                                         ParticulateType type, float mgM3) {
        // Sand/dust/ash at high concentrations → blindness
        if ((type == ParticulateType.SAND || type == ParticulateType.DUST
                || type == ParticulateType.VOLCANIC_ASH
                || type == ParticulateType.ASH_CLOUD)
                && mgM3 > 100f) {
            int amp = mgM3 > 500f ? 1 : 0;
            addEffect(entity, MobEffects.BLINDNESS, EFFECT_DURATION_TICKS, amp);
        }

        // Ice crystals → slowness + freeze damage at high concentration
        if (type == ParticulateType.ICE_CRYSTALS && mgM3 > 50f) {
            addEffect(entity, MobEffects.MOVEMENT_SLOWDOWN, EFFECT_DURATION_TICKS, 0);
            if (mgM3 > 200f) {
                entity.hurt(entity.damageSources().freeze(), 0.5f);
            }
        }

        // Smoke aerosol → mining fatigue (respiratory impairment)
        if ((type == ParticulateType.SMOKE_AEROSOL || type == ParticulateType.SOOT)
                && mgM3 > 80f) {
            addEffect(entity, MobEffects.DIG_SLOWDOWN, EFFECT_DURATION_TICKS, 0);
        }
    }

    /** Maps {@link ParticulateType.ToxicEffect} to vanilla MobEffects. */
    private static void applyParticulateToxicEffect(LivingEntity entity,
                                                      ParticulateType type, int amplifier) {
        // Oreganized/Carcinogenius override — applies modded effects for lead and asbestos
        if (exp.CCnewmods.mge.compat.OreganizedCompat.applyOverrideEffect(entity, type, amplifier)) {
            return;
        }
        switch (type.toxicEffect) {
            case NONE          -> {}
            case MINING_FATIGUE -> addEffect(entity, MobEffects.DIG_SLOWDOWN,       EFFECT_DURATION_TICKS, amplifier);
            case NAUSEA         -> addEffect(entity, MobEffects.CONFUSION,           EFFECT_DURATION_TICKS, 0);
            case WITHER         -> addEffect(entity, MobEffects.WITHER,              EFFECT_DURATION_TICKS, amplifier);
            case SUFFOCATION    -> entity.hurt(entity.damageSources().drown(), 1.0f);
            case LEVITATION     -> addEffect(entity, MobEffects.LEVITATION,          EFFECT_DURATION_TICKS, 0);
            case SLOWNESS       -> addEffect(entity, MobEffects.MOVEMENT_SLOWDOWN,   EFFECT_DURATION_TICKS, amplifier);
        }
    }

    // =========================================================================
    // Util
    // =========================================================================

    private static void addEffect(LivingEntity entity, MobEffect effect,
                                   int durationTicks, int amplifier) {
        entity.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, true));
    }
}
