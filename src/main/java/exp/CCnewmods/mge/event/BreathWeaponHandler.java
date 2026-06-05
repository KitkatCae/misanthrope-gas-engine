package exp.CCnewmods.mge.event;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.MgeConfig;
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
            injectAt(level, pos, p -> {
                float o2 = GridAtmosphereCompat.getGas(level, p, GasRegistry.OXYGEN);
                GridAtmosphereCompat.addGas(level, p, GasRegistry.OXYGEN,          -Math.min(o2, 80f));
                GridAtmosphereCompat.addGas(level, p, GasRegistry.CARBON_DIOXIDE,   30f);
                GridAtmosphereCompat.addGas(level, p, GasRegistry.WITHER_MIASMA,    60f);
                GridAtmosphereCompat.addParticulate(level, p, ParticulateType.SMOKE_AEROSOL, 200f);
                GridAtmosphereCompat.addParticulate(level, p, ParticulateType.SOOT,           50f);
            });
            // Spread to adjacent blocks
            for (BlockPos adj : new BlockPos[]{pos.north(),pos.south(),pos.east(),pos.west(),pos.above()}) {
                injectAt(level, adj, p -> {
                    GridAtmosphereCompat.addGas(level, p, GasRegistry.WITHER_MIASMA, 20f);
                    GridAtmosphereCompat.addParticulate(level, p, ParticulateType.SMOKE_AEROSOL, 80f);
                });
            }
        } else if (proj instanceof LargeFireball || proj instanceof SmallFireball) {
            // Ghast / blaze fireball — blaze fume, SO₂, combustion
            Entity owner = proj instanceof LargeFireball fb ? fb.getOwner()
                         : ((SmallFireball) proj).getOwner();
            boolean isGhast = owner instanceof Ghast;
            injectAt(level, pos, p -> {
                float o2 = GridAtmosphereCompat.getGas(level, p, GasRegistry.OXYGEN);
                GridAtmosphereCompat.addGas(level, p, GasRegistry.OXYGEN,         -Math.min(o2, 40f));
                GridAtmosphereCompat.addGas(level, p, GasRegistry.CARBON_DIOXIDE,  25f);
                GridAtmosphereCompat.addGas(level, p, GasRegistry.BLAZE_FUME,      isGhast ? 30f : 15f);
                GridAtmosphereCompat.addGas(level, p, GasRegistry.SULFUR_DIOXIDE,  10f);
                GridAtmosphereCompat.addParticulate(level, p, ParticulateType.SMOKE_AEROSOL, isGhast ? 150f : 60f);
                GridAtmosphereCompat.addParticulate(level, p, ParticulateType.SOOT,           30f);
            });
            WorldEventHandler.mutateFire(level, pos, isGhast ? 20f : 10f);
        } else if (proj instanceof WitherSkull skull) {
            // Wither skull — necrotic wither_miasma + soul smoke
            injectAt(level, pos, p -> {
                GridAtmosphereCompat.addGas(level, p, GasRegistry.WITHER_MIASMA, skull.isDangerous() ? 80f : 40f);
                GridAtmosphereCompat.addGas(level, p, GasRegistry.SOUL_SMOKE,    30f);
                GridAtmosphereCompat.addParticulate(level, p, ParticulateType.SOUL_DUST,      50f);
                GridAtmosphereCompat.addParticulate(level, p, ParticulateType.SMOKE_AEROSOL,  80f);
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
        injectAt(level, pos, p -> {
            GridAtmosphereCompat.addGas(level, p, GasRegistry.BLAZE_FUME,      5f);
            GridAtmosphereCompat.addGas(level, p, GasRegistry.SULFUR_DIOXIDE,  2f);
            // Blazes are biological pyrotheum colonies — their blazing blood off-gases
            // pyrotheum dust continuously. The dust ionises surrounding air.
            GridAtmosphereCompat.addParticulate(level, p, exp.CCnewmods.mge.particulate.ParticulateType.PYROTHEUM_DUST, 8f);
            GridAtmosphereCompat.addGas(level, p, GasRegistry.IONISED_AIR, 3f);
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
        injectAt(level, pos, p -> {
            GridAtmosphereCompat.addGas(level, p, GasRegistry.CARBON_DIOXIDE,    3f);
            GridAtmosphereCompat.addGas(level, p, GasRegistry.ENDER_PARTICULATE, 5f);
        });
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    // Inject helper — replaced with direct grid calls below
    private static void injectAt(ServerLevel level, BlockPos pos,
                                  java.util.function.Consumer<BlockPos> action) {
        if (!level.isLoaded(pos)) return;
        action.accept(pos);
        EnvironmentGrid.enqueue(level, pos);
    }
}
