package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Saint's Dragons compat (modid: saintsdragons).
 *
 * All entity IDs confirmed from ModEntities.class.
 * Projectile IDs confirmed: volitans_poison_ball, volitans_water_breath,
 * volitans_spine, raevyx_lightning_chain, raevyx_ground_rend_trail,
 * ignivorus_flame, ignivorus_nova, ignivorus_nova_ring, ignivorus_magma_block,
 * ignivorus_magma_pillar, cindervane_magma_block.
 *
 * <ul>
 *   <li><b>raevyx</b> — lightning dragon: IONISED_AIR + OZONE + NITRIC_OXIDE aura.
 *       raevyx_lightning_chain impact: plasma channel vacuum + shockwave.
 *       raevyx_ground_rend_trail: DUST + GRAVEL_DUST + shockwave.</li>
 *   <li><b>cindervane</b> — fire dragon: reuses IceAndFire fire breath helper.
 *       cindervane_magma_block: VOLCANIC_FUMES + BLAZE_FUME + shockwave.</li>
 *   <li><b>volitans</b> — water/venom dragon: WATER_VAPOR + H₂S aura.
 *       volitans_poison_ball: H₂S + ORGANIC_AEROSOL burst.
 *       volitans_water_breath: WATER_VAPOR saturation.
 *       volitans_spine: HYDROGEN_SULFIDE trace.</li>
 *   <li><b>ignivorus</b> — magma dragon: VOLCANIC_FUMES + MAGMATIC_CO + BLAZE_FUME.
 *       ignivorus_flame/nova/nova_ring: VOLCANIC_FUMES + shockwave.
 *       ignivorus_magma_block/pillar: VOLCANIC_FUMES + DUST + shockwave.</li>
 *   <li><b>nulljaw</b> — void dragon: VOID_BREATH + ENDER_PARTICULATE, drains all gases.
 *       Death: massive void burst + vacuum implosion.</li>
 *   <li><b>varasuchus</b> — aquatic ambush predator: WATER_VAPOR + H₂S.</li>
 *   <li><b>ivy_oleander</b> — toxic plant dragon: HYDROGEN_SULFIDE + ORGANIC_AEROSOL.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SaintsDragonsCompat {

    public static final String MODID = "saintsdragons";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private SaintsDragonsCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Saint's Dragons detected — dragon atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("saintsdragons:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "saintsdragons:raevyx" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR,  20f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,          8f, 2);
                gasRadius(level, pos, GasRegistry.NITRIC_OXIDE,   5f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 25f, 3);
            }
            case "saintsdragons:cindervane" ->
                IceAndFireCompat.emitFireDragonBreath(level, pos, 1.1f);
            case "saintsdragons:volitans" -> {
                gas(level, pos, GasRegistry.WATER_VAPOR,    12f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 6f);
            }
            case "saintsdragons:ignivorus" -> {
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES, 18f, 3);
                gasRadius(level, pos, GasRegistry.MAGMATIC_CO2,    10f, 2);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     12f, 3);
                drainRadius(level, pos, GasRegistry.OXYGEN,       12f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  20f, 2);
            }
            case "saintsdragons:nulljaw" -> {
                // Void dragon — actively consumes all local gases
                gas(level, pos, GasRegistry.VOID_BREATH,       15f);
                gas(level, pos, GasRegistry.ENDER_PARTICULATE, 10f);
                drain(level, pos, GasRegistry.OXYGEN,   12f);
                drain(level, pos, GasRegistry.NITROGEN, 10f);
                drain(level, pos, GasRegistry.CARBON_DIOXIDE, 5f);
            }
            case "saintsdragons:varasuchus" -> {
                gas(level, pos, GasRegistry.WATER_VAPOR,    8f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 4f);
            }
            case "saintsdragons:ivy_oleander" -> {
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,  6f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 8f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("saintsdragons:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        switch (type) {
            case "saintsdragons:nulljaw" -> {
                // Void implosion — strips atmosphere entirely then VOID_BREATH fills the gap
                var comp = GridAtmosphereCompat.getComposition(level, pos);
                float n2 = comp.get(GasRegistry.NITROGEN);
                float o2 = comp.get(GasRegistry.OXYGEN);
                comp.add(GasRegistry.NITROGEN, -n2 * 0.9f);
                comp.add(GasRegistry.OXYGEN,   -o2 * 0.9f);
                comp.add(GasRegistry.VOID_BREATH, 60f);
                GridAtmosphereCompat.setComposition(level, pos, comp);
                gasRadius(level, pos, GasRegistry.VOID_BREATH,       80f, 7);
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 50f, 5);
                drainRadius(level, pos, GasRegistry.OXYGEN,   60f, 6);
                drainRadius(level, pos, GasRegistry.NITROGEN, 50f, 5);
                ShockwaveHandler.spawn(level, pos, 12f);
            }
            case "saintsdragons:ignivorus" -> {
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES, 80f, 7);
                gasRadius(level, pos, GasRegistry.MAGMATIC_CO2,    40f, 5);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     60f, 6);
                partRadius(level, pos, ParticulateType.ASH_CLOUD, 140f, 7);
                ShockwaveHandler.spawn(level, pos, 12f);
            }
            case "saintsdragons:raevyx" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 60f, 6);
                gasRadius(level, pos, GasRegistry.OZONE,       25f, 4);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 80f, 6);
                ShockwaveHandler.spawn(level, pos, 10f);
            }
            case "saintsdragons:cindervane" -> {
                gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 50f, 5);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 25f, 4);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  90f, 5);
                ShockwaveHandler.spawn(level, pos, 9f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("saintsdragons:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "saintsdragons:raevyx_lightning_chain" -> {
                // Plasma channel — same logic as vanilla lightning but dragon-scale
                var comp = GridAtmosphereCompat.getComposition(level, pos);
                float n2 = comp.get(GasRegistry.NITROGEN);
                float o2 = comp.get(GasRegistry.OXYGEN);
                float reacted = Math.min(n2, o2) * 0.40f;
                comp.add(GasRegistry.NITROGEN,    -reacted);
                comp.add(GasRegistry.OXYGEN,      -reacted);
                comp.add(GasRegistry.NITRIC_OXIDE, reacted * 1.8f);
                comp.add(GasRegistry.IONISED_AIR,  (n2 + o2) * 0.15f);
                comp.add(GasRegistry.OZONE,         o2 * 0.06f);
                GridAtmosphereCompat.setComposition(level, pos, comp);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 6f);
            }
            case "saintsdragons:raevyx_ground_rend_trail" -> {
                partRadius(level, pos, ParticulateType.DUST,        35f, 3);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 25f, 2);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            case "saintsdragons:cindervane_magma_block" -> {
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES, 25f, 3);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     20f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  50f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            case "saintsdragons:volitans_poison_ball" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 20f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 30f, 3);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "saintsdragons:volitans_water_breath" ->
                gasRadius(level, pos, GasRegistry.WATER_VAPOR, 30f, 3);
            case "saintsdragons:volitans_spine" ->
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 5f);
            case "saintsdragons:ignivorus_flame",
                 "saintsdragons:ignivorus_nova",
                 "saintsdragons:ignivorus_nova_ring" -> {
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES, 30f, 3);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     20f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  60f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            case "saintsdragons:ignivorus_magma_block",
                 "saintsdragons:ignivorus_magma_pillar" -> {
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES, 20f, 3);
                gasRadius(level, pos, GasRegistry.MAGMATIC_CO2,    10f, 2);
                partRadius(level, pos, ParticulateType.DUST,       40f, 3);
                ShockwaveHandler.spawn(level, pos, 6f);
            }
        }
    }
}
