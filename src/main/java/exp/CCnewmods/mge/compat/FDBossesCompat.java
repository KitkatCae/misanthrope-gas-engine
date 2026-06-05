package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
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

import java.util.Set;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * FD Bosses compat (modid: fdbosses).
 *
 * All entity IDs confirmed from BossEntities.class:
 * <ul>
 *   <li><b>chesed</b> — electric/tech boss: IONISED_AIR + OZONE aura.
 *       fire_malkuth_warrior / ice_malkuth_warrior — warrior variants.
 *       Projectiles: chesed_electric_sphere → IONISED_AIR burst;
 *       chesed_mini_ray → narrow IONISED_AIR channel.</li>
 *   <li><b>malkuth</b> — volcanic/earth boss: VOLCANIC_FUMES + BLAZE_FUME.
 *       Earthquake attacks produce massive DUST + shockwaves.
 *       Projectiles: malkuth_fireball → BLAZE_FUME burst;
 *       malkuth_cannon_projectile → VOLCANIC_FUMES + shockwave;
 *       malkuth_boulder → DUST + GRAVEL_DUST + shockwave;
 *       malkuth_earthquake / geburah_earthquake → ground shockwave + dust;
 *       earth_shatter → massive area shockwave.</li>
 *   <li><b>geburah</b> — divine wrath boss: SOUL_ESSENCE + WITHER_MIASMA (judgment).
 *       Projectiles: geburah_judgement_ball → SOUL_ESSENCE + shockwave;
 *       flying_sword → SOUL_ESSENCE trace;
 *       justice_hammer → massive shockwave + WITHER_MIASMA.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FDBossesCompat {

    public static final String MODID = "fdbosses";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private static final Set<String> MALKUTH_WARRIOR = Set.of(
        "fdbosses:fire_malkuth_warrior",
        "fdbosses:ice_malkuth_warrior"
    );

    private FDBossesCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] FD Bosses detected — boss atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("fdbosses:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "fdbosses:chesed" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR,  20f, 4);
                gasRadius(level, pos, GasRegistry.OZONE,         8f, 3);
                gasRadius(level, pos, GasRegistry.NITRIC_OXIDE,  4f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 25f, 4);
            }
            case "fdbosses:malkuth" -> {
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES, 15f, 4);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     12f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,  8f, 3);
                drainRadius(level, pos, GasRegistry.OXYGEN, 10f, 3);
                partRadius(level, pos, ParticulateType.ASH_CLOUD, 20f, 3);
            }
            case "fdbosses:geburah" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  15f, 4);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 10f, 3);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 20f, 4);
                drainRadius(level, pos, GasRegistry.OXYGEN, 8f, 3);
            }
            default -> {
                if (MALKUTH_WARRIOR.contains(type)) {
                    if (type.contains("fire")) {
                        gas(level, pos, GasRegistry.BLAZE_FUME,     8f);
                        gas(level, pos, GasRegistry.SULFUR_DIOXIDE,  4f);
                    } else { // ice
                        gas(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 8f);
                        part(level, pos, ParticulateType.ICE_CRYSTALS, 10f);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("fdbosses:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        switch (type) {
            case "fdbosses:chesed" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 70f, 7);
                gasRadius(level, pos, GasRegistry.OZONE,       28f, 5);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 90f, 7);
                ShockwaveHandler.spawn(level, pos, 12f);
                ShockwaveDataPacket.sendToNear(level, vec, 12f, 100f);
            }
            case "fdbosses:malkuth" -> {
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES, 100f, 10);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,  60f,  8);
                gasRadius(level, pos, GasRegistry.MAGMATIC_CO2,     40f,  7);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  180f,  9);
                partRadius(level, pos, ParticulateType.DUST,       120f,  8);
                ShockwaveHandler.spawn(level, pos, 18f);
                ShockwaveDataPacket.sendToNear(level, vec, 18f, 180f);
            }
            case "fdbosses:geburah" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  80f, 8);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 50f, 6);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 100f, 8);
                ShockwaveHandler.spawn(level, pos, 14f);
                ShockwaveDataPacket.sendToNear(level, vec, 14f, 120f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("fdbosses:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        switch (type) {
            // Chesed electric projectiles
            case "fdbosses:chesed_electric_sphere",
                 "fdbosses:electric_sphere" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 25f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,       10f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 30f, 3);
                ShockwaveHandler.spawn(level, pos, 4f);
                ShockwaveDataPacket.sendToNear(level, vec, 4f, 40f);
            }
            case "fdbosses:chesed_mini_ray",
                 "fdbosses:chesed_vertical_ray_attack" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 15f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 18f, 2);
            }
            // Malkuth earth projectiles
            case "fdbosses:malkuth_fireball" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     35f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 15f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  70f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
            case "fdbosses:malkuth_cannon_projectile",
                 "fdbosses:malkuth_player_fireball" -> {
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES, 30f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 15f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  60f, 3);
                ShockwaveHandler.spawn(level, pos, 6f);
                ShockwaveDataPacket.sendToNear(level, vec, 6f, 56f);
            }
            case "fdbosses:malkuth_boulder",
                 "fdbosses:flying_block",
                 "fdbosses:block_projectile" -> {
                partRadius(level, pos, ParticulateType.DUST,        50f, 4);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 35f, 3);
                ShockwaveHandler.spawn(level, pos, 7f);
                ShockwaveDataPacket.sendToNear(level, vec, 7f, 64f);
            }
            case "fdbosses:malkuth_earthquake",
                 "fdbosses:geburah_earthquake",
                 "fdbosses:radial_earthquake",
                 "fdbosses:earth_shatter" -> {
                partRadius(level, pos, ParticulateType.DUST,        80f, 6);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 60f, 5);
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,  15f, 4);
                ShockwaveHandler.spawn(level, pos, 10f);
                ShockwaveDataPacket.sendToNear(level, vec, 10f, 90f);
            }
            // Geburah divine projectiles
            case "fdbosses:geburah_judgement_ball" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   25f, 3);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,  15f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 30f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
            case "fdbosses:flying_sword",
                 "fdbosses:malkuth_slash",
                 "fdbosses:malkuth_giant_sword_slash" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 10f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 12f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
            case "fdbosses:justice_hammer" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,  40f, 5);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   25f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 50f, 5);
                ShockwaveHandler.spawn(level, pos, 12f);
                ShockwaveDataPacket.sendToNear(level, vec, 12f, 100f);
            }
        }
    }
}
