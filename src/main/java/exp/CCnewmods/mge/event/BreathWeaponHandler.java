package exp.CCnewmods.mge.event;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles interactions between breath-weapon mobs and the atmosphere.
 *
 * Dragon fire breath injects dragon_breath_gas (corrosive purple cloud) and
 * consumes local O₂. Ghast fireballs inject blaze fume and SO₂ on impact.
 * Wither skulls inject wither_miasma and soul smoke. Blazes continuously
 * off-gas blaze fume from their body position.
 *
 * All projectile impacts also trigger WorldEventHandler.mutateFire() for
 * the fire/combustion gas effects.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BreathWeaponHandler {

    private static final int BLAZE_TICK_INTERVAL = 20;
    private static int tick = 0;

    private BreathWeaponHandler() {}

    // ── Projectile impact ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;

        BlockPos pos = BlockPos.containing(proj.position());

        if (proj instanceof DragonFireball) {
            // Dragon breath — corrosive cloud, consumes O₂
            injectAt(level, pos, atm -> {
                var comp = atm.getComposition();
                float o2 = comp.get(GasRegistry.OXYGEN);
                comp.add(GasRegistry.OXYGEN, -Math.min(o2, 80f));
                comp.add(GasRegistry.CARBON_DIOXIDE, 30f);
                // Inject wither_miasma as the dragon breath carrier
                comp.add(GasRegistry.WITHER_MIASMA, 60f);
                atm.setComposition(comp);
                var parts = atm.getParticulates();
                parts.add(ParticulateType.SMOKE_AEROSOL, 200f);
                parts.add(ParticulateType.SOOT, 50f);
                atm.setParticulates(parts);
            });
            // Spread to adjacent blocks
            for (BlockPos adj : new BlockPos[]{pos.north(),pos.south(),pos.east(),pos.west(),pos.above()}) {
                injectAt(level, adj, atm -> {
                    atm.getComposition().add(GasRegistry.WITHER_MIASMA, 20f);
                    atm.setComposition(atm.getComposition());
                    atm.getParticulates().add(ParticulateType.SMOKE_AEROSOL, 80f);
                    atm.setParticulates(atm.getParticulates());
                });
            }
        } else if (proj instanceof LargeFireball || proj instanceof SmallFireball) {
            // Ghast / blaze fireball — blaze fume, SO₂, combustion
            Entity owner = proj instanceof LargeFireball fb ? fb.getOwner()
                         : ((SmallFireball) proj).getOwner();
            boolean isGhast = owner instanceof Ghast;
            injectAt(level, pos, atm -> {
                var comp = atm.getComposition();
                float o2 = comp.get(GasRegistry.OXYGEN);
                comp.add(GasRegistry.OXYGEN, -Math.min(o2, 40f));
                comp.add(GasRegistry.CARBON_DIOXIDE, 25f);
                comp.add(GasRegistry.BLAZE_FUME, isGhast ? 30f : 15f);
                comp.add(GasRegistry.SULFUR_DIOXIDE, 10f);
                atm.setComposition(comp);
                var parts = atm.getParticulates();
                parts.add(ParticulateType.SMOKE_AEROSOL, isGhast ? 150f : 60f);
                parts.add(ParticulateType.SOOT, 30f);
                atm.setParticulates(parts);
            });
            WorldEventHandler.mutateFire(level, pos, isGhast ? 20f : 10f);
        } else if (proj instanceof WitherSkull skull) {
            // Wither skull — necrotic wither_miasma + soul smoke
            injectAt(level, pos, atm -> {
                var comp = atm.getComposition();
                comp.add(GasRegistry.WITHER_MIASMA, skull.isDangerous() ? 80f : 40f);
                comp.add(GasRegistry.SOUL_SMOKE, 30f);
                atm.setComposition(comp);
                var parts = atm.getParticulates();
                parts.add(ParticulateType.SOUL_DUST, 50f);
                parts.add(ParticulateType.SMOKE_AEROSOL, 80f);
                atm.setParticulates(parts);
            });
        }
    }

    // ── Continuous blaze off-gassing ─────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (++tick % BLAZE_TICK_INTERVAL != 0) return;
        if (!(event.getEntity() instanceof Blaze blaze)) return;
        if (!(blaze.level() instanceof ServerLevel level)) return;
        if (!blaze.isOnFire()) return;

        BlockPos pos = blaze.blockPosition();
        injectAt(level, pos, atm -> {
            atm.getComposition().add(GasRegistry.BLAZE_FUME, 5f);
            atm.getComposition().add(GasRegistry.SULFUR_DIOXIDE, 2f);
            atm.setComposition(atm.getComposition());
            Mge.getScheduler(level).enqueue(pos);
        });
    }

    // ── Dragon breath cloud — fires from dragon entity tick ──────────────────

    @SubscribeEvent
    public static void onDragonTick(LivingEvent.LivingTickEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        if (!(dragon.level() instanceof ServerLevel level)) return;
        if (tick % 10 != 0) return; // only every 10 ticks

        // Dragon constantly off-gases ender particulate and CO₂ from wing beats
        BlockPos pos = dragon.blockPosition();
        injectAt(level, pos, atm -> {
            atm.getComposition().add(GasRegistry.CARBON_DIOXIDE, 3f);
            atm.getComposition().add(GasRegistry.ENDER_PARTICULATE, 5f);
            atm.setComposition(atm.getComposition());
        });
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @FunctionalInterface interface AtmlAction { void apply(AtmosphereBlockEntity atm); }

    private static void injectAt(ServerLevel level, BlockPos pos, AtmlAction action) {
        if (!level.isLoaded(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;
        action.apply(atm);
        Mge.getScheduler(level).enqueue(pos);
    }
}
