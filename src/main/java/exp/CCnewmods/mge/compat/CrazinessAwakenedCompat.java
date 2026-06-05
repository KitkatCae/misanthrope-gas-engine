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
 * Craziness Awakened compat (modid: craziness_awakened).
 *
 * Entity IDs confirmed from CrazinessAwakenedModEntities.class:
 * <ul>
 *   <li><b>mobzilla</b> — kaiju-scale; massive EM/dust/shockwave every tick, larger
 *       than Tremorzilla. Death: cataclysmic shockwave + ash cloud.</li>
 *   <li><b>rotator</b> — IONISED_AIR rotational vortex displacement every tick.</li>
 *   <li><b>large_worm / medium_worm / small_worm</b> — DUST + GRAVEL_DUST underground
 *       movement, scaled by size.</li>
 *   <li><b>dragon</b> — fire breath using IceAndFire helper.</li>
 *   <li><b>emperor_scorpion</b> — H₂S + ORGANIC_AEROSOL (venom/chitin off-gassing).</li>
 *   <li><b>mobzillaball</b> — projectile: BLAZE_FUME + massive shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrazinessAwakenedCompat {

    public static final String MODID = "craziness_awakened";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL  = 20;
    private static final int FAST_TICK      = 10;
    private static int tick = 0;

    private CrazinessAwakenedCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Craziness Awakened detected — kaiju atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("craziness_awakened:")) return;

        ++tick;
        BlockPos pos = entity.blockPosition();
        Vec3 vec = entity.position();

        switch (type) {
            case "craziness_awakened:mobzilla" -> {
                // Kaiju-scale — enormous EM/seismic output, larger than Tremorzilla
                if (tick % FAST_TICK == 0) {
                    partRadius(level, pos, ParticulateType.DUST,        60f, 6);
                    partRadius(level, pos, ParticulateType.GRAVEL_DUST, 40f, 5);
                    partRadius(level, pos, ParticulateType.COAL_DUST,   20f, 4);
                    ShockwaveHandler.spawn(level, pos, 6f);
                    ShockwaveDataPacket.sendToNear(level, vec, 6f, 80f);
                }
                if (tick % TICK_INTERVAL == 0) {
                    gasRadius(level, pos, GasRegistry.WATER_VAPOR,   20f, 5);
                    gasRadius(level, pos, GasRegistry.IONISED_AIR,   15f, 4);
                    gasRadius(level, pos, GasRegistry.OZONE,          6f, 3);
                    partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 25f, 4);
                }
            }
            case "craziness_awakened:rotator" -> {
                if (tick % TICK_INTERVAL != 0) return;
                // Rotational vortex — pulls and displaces gases in a spin pattern
                var comp = GridAtmosphereCompat.getComposition(level, pos);
                float n2 = comp.get(GasRegistry.NITROGEN);
                float o2 = comp.get(GasRegistry.OXYGEN);
                // Centrifugal evacuation at core
                comp.add(GasRegistry.NITROGEN, -n2 * 0.3f);
                comp.add(GasRegistry.OXYGEN,   -o2 * 0.3f);
                comp.add(GasRegistry.IONISED_AIR, (n2 + o2) * 0.15f);
                GridAtmosphereCompat.setComposition(level, pos, comp);
                // Displaced gas at perimeter
                gasRadius(level, pos, GasRegistry.IONISED_AIR,   20f, 4);
                gasRadius(level, pos, GasRegistry.OZONE,           8f, 3);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 15f, 3);
            }
            case "craziness_awakened:large_worm" -> {
                if (tick % FAST_TICK != 0) return;
                partRadius(level, pos, ParticulateType.DUST,        25f, 3);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 18f, 3);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 32f);
            }
            case "craziness_awakened:medium_worm" -> {
                if (tick % FAST_TICK != 0) return;
                partRadius(level, pos, ParticulateType.DUST,       14f, 2);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 10f, 2);
                ShockwaveHandler.spawn(level, pos, 2f);
                ShockwaveDataPacket.sendToNear(level, vec, 2f, 20f);
            }
            case "craziness_awakened:small_worm" -> {
                if (tick % TICK_INTERVAL != 0) return;
                part(level, pos, ParticulateType.DUST, 8f);
            }
            case "craziness_awakened:dragon" -> {
                if (tick % TICK_INTERVAL != 0) return;
                IceAndFireCompat.emitFireDragonBreath(level, pos, 0.8f);
            }
            case "craziness_awakened:emperor_scorpion" -> {
                if (tick % TICK_INTERVAL != 0) return;
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 4f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("craziness_awakened:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        if (type.equals("craziness_awakened:mobzilla")) {
            // Cataclysmic death — city-levelling shockwave
            gasRadius(level, pos, GasRegistry.WATER_VAPOR, 60f, 10);
            partRadius(level, pos, ParticulateType.DUST,        300f, 12);
            partRadius(level, pos, ParticulateType.GRAVEL_DUST, 200f, 10);
            partRadius(level, pos, ParticulateType.ASH_CLOUD,   150f, 8);
            ShockwaveHandler.spawn(level, pos, 24f);
            ShockwaveDataPacket.sendToNear(level, vec, 24f, 250f);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("craziness_awakened:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        if (type.equals("craziness_awakened:mobzillaball")) {
            gasRadius(level, pos, GasRegistry.BLAZE_FUME,     60f, 5);
            gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 25f, 4);
            partRadius(level, pos, ParticulateType.ASH_CLOUD, 120f, 5);
            ShockwaveHandler.spawn(level, pos, 12f);
            ShockwaveDataPacket.sendToNear(level, vec, 12f, 120f);
        } else if (type.equals("craziness_awakened:big_fireball")
                || type.equals("craziness_awakened:huge_fireball")) {
            gasRadius(level, pos, GasRegistry.BLAZE_FUME,     35f, 4);
            gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 15f, 3);
            partRadius(level, pos, ParticulateType.ASH_CLOUD,  70f, 4);
            ShockwaveHandler.spawn(level, pos, 7f);
            ShockwaveDataPacket.sendToNear(level, vec, 7f, 64f);
        }
    }
}
