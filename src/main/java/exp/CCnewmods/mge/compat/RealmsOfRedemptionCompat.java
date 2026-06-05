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
 * Realms of Redemption compat (modid: ror).
 *
 * Entity IDs derived from entity class names (snake_case conversion).
 *
 * Entities grouped by atmospheric archetype:
 * <ul>
 *   <li><b>Robot/construct</b> (laserbot, guardbot, killbot, scanbot, gold_scanbot,
 *       heavy_laserbot, undercopter, crystalcopter, small_robot, cowbot):
 *       IONISED_AIR + OZONE (electrical propulsion/operation).</li>
 *   <li><b>Crystal golems</b> (blue/green/red/light_blue/yellow/gold_crystal_golem):
 *       IONISED_AIR + OZONE (crystalline energy discharge).</li>
 *   <li><b>Ghost/spectral</b> (ghost, ghost_angel, googloid_ghost, undeadneonresident):
 *       SOUL_ESSENCE aura.</li>
 *   <li><b>Organic</b> (meat_puddle, malicious_root, swarm_spider):
 *       H₂S + ORGANIC_AEROSOL.</li>
 *   <li><b>Titania (boss)</b> — enhanced crystal energy + shockwave on tick.</li>
 *   <li><b>Prismkeeper</b> — IONISED_AIR + ozone + SOUL_ESSENCE.</li>
 *   <li><b>Projectiles</b>: laser/laser_blast/laserbeam → IONISED_AIR burst;
 *       wand projectiles → SOUL_ESSENCE; galactic/prismite/tourmaline shuriken →
 *       IONISED_AIR + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RealmsOfRedemptionCompat {

    public static final String MODID = "ror";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private static final Set<String> ROBOTS = Set.of(
        "ror:laserbot", "ror:guardbot", "ror:killbot", "ror:scanbot",
        "ror:gold_scanbot", "ror:heavy_laserbot", "ror:undercopter",
        "ror:crystalcopter", "ror:small_robot", "ror:cowbot",
        "ror:placer", "ror:spawner_placer"
    );
    private static final Set<String> CRYSTAL_GOLEMS = Set.of(
        "ror:blue_crystal_golem", "ror:green_crystal_golem", "ror:red_crystal_golem",
        "ror:light_blue_crystal_golem", "ror:yellow_crystal_golem", "ror:gold_construct",
        "ror:corrundumgolem", "ror:corrundodile", "ror:blue_crystal_construct",
        "ror:green_crystal_construct", "ror:red_crystal_construct",
        "ror:light_blue_crystal_construct", "ror:yellow_crystal_construct"
    );
    private static final Set<String> SPECTRAL = Set.of(
        "ror:ghost", "ror:ghost_angel", "ror:googloid_ghost",
        "ror:undead_neon_resident", "ror:gold_undead_neon_resident"
    );
    private static final Set<String> ORGANIC = Set.of(
        "ror:meat_puddle", "ror:malicious_root", "ror:swarm_spider", "ror:small_snail"
    );

    private RealmsOfRedemptionCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Realms of Redemption detected — robot/crystal atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("ror:")) return;

        BlockPos pos = entity.blockPosition();

        if (type.equals("ror:titania")) {
            gasRadius(level, pos, GasRegistry.IONISED_AIR,  18f, 4);
            gasRadius(level, pos, GasRegistry.OZONE,         8f, 3);
            gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  8f, 3);
            partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 22f, 4);
        } else if (type.equals("ror:prismkeeper") || type.equals("ror:rainboom")) {
            gas(level, pos, GasRegistry.IONISED_AIR,  12f);
            gas(level, pos, GasRegistry.OZONE,          5f);
            gas(level, pos, GasRegistry.SOUL_ESSENCE,   6f);
            part(level, pos, ParticulateType.IONISED_PARTICLES, 10f);
        } else if (ROBOTS.contains(type)) {
            gas(level, pos, GasRegistry.IONISED_AIR, 6f);
            gas(level, pos, GasRegistry.OZONE,        3f);
        } else if (CRYSTAL_GOLEMS.contains(type)) {
            gas(level, pos, GasRegistry.IONISED_AIR, 8f);
            gas(level, pos, GasRegistry.OZONE,        4f);
            part(level, pos, ParticulateType.IONISED_PARTICLES, 6f);
        } else if (SPECTRAL.contains(type)) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE,   6f);
            part(level, pos, ParticulateType.SOUL_WISPS, 8f);
        } else if (ORGANIC.contains(type)) {
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 5f);
            part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("ror:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        if (type.equals("ror:titania")) {
            gasRadius(level, pos, GasRegistry.IONISED_AIR,  60f, 7);
            gasRadius(level, pos, GasRegistry.OZONE,        25f, 5);
            gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 30f, 5);
            partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 80f, 7);
            ShockwaveHandler.spawn(level, pos, 12f);
            ShockwaveDataPacket.sendToNear(level, vec, 12f, 100f);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("ror:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        switch (type) {
            case "ror:laser", "ror:laser_blast", "ror:laserbeam" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 18f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
            case "ror:fairy_wand_projectile",
                 "ror:rainbow_wand_projectile",
                 "ror:devourer_wand_projectile",
                 "ror:jaw_wand_projectile",
                 "ror:mechanical_wand_projectile" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  12f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 15f, 2);
            }
            case "ror:thrown_galactic_shuriken",
                 "ror:thrown_prismite_shuriken",
                 "ror:thrown_tourmaline_shuriken",
                 "ror:thrown_magnolite_shuriken",
                 "ror:thrown_tsavorite_shuriken" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 12f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 14f, 2);
                ShockwaveHandler.spawn(level, pos, 2f);
                ShockwaveDataPacket.sendToNear(level, vec, 2f, 18f);
            }
            case "ror:blue_crystal_star",
                 "ror:green_crystal_star",
                 "ror:red_crystal_star",
                 "ror:light_blue_crystal_star",
                 "ror:yellow_crystal_star" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 14f, 2);
                gasRadius(level, pos, GasRegistry.OZONE,        5f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 16f, 2);
            }
            case "ror:corrundumspike",
                 "ror:corrunding" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 8f, 2);
                partRadius(level, pos, ParticulateType.DUST,   15f, 2);
            }
        }
    }
}
