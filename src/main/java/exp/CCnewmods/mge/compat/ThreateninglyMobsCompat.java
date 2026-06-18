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
 * Threateningly Mobs compat (modid: threateningly_mobs).
 *
 * All entity IDs confirmed from ThreateninglyMobsModEntities.class.
 *
 * Entities grouped by atmospheric archetype:
 * <ul>
 *   <li><b>Fire</b>: fire_lizard, flamelarva, scorch_golem, terra_dragon_re,
 *       flower_of_dragon, the_inferno, forest_drake, flame_horn_re, flamestorm,
 *       inferno → BLAZE_FUME + SO₂.</li>
 *   <li><b>Ice</b>: ferox_iceworm, ice_brood_mother, ice_weaver, frostbite,
 *       snow_servent → DRAGON_ICE_CLOUD + ICE_CRYSTALS.</li>
 *   <li><b>Undead/spectral</b>: lich_re, dungeon_guardian, corrupted_heroicsoul,
 *       executioner, holy_coffin, hypocritical_saint, tide_specter →
 *       SOUL_ESSENCE + WITHER_MIASMA.</li>
 *   <li><b>Earth/worm</b>: sandworm, ferox_death_worm, the_earthloong →
 *       DUST + GRAVEL_DUST + shockwave.</li>
 *   <li><b>Shadow</b>: shadow_spider, shadowmoon_butterfly → SOUL_ESSENCE.</li>
 *   <li><b>Water/sea</b>: the_regalhart, giant_seacucumber, riptooth,
 *       hippo_fish → WATER_VAPOR + H₂S.</li>
 *   <li><b>Bosses</b>: the_regalhart, the_earthloong, lich_re —
 *       enhanced emissions + death bursts.</li>
 *   <li><b>Projectiles</b>: inferno_projectile, ice_worm_acid, red_worm_acid,
 *       moon_ball_bullet, slash_wave, posion_goo_bullet, magic_ammo variants.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThreateninglyMobsCompat {

    public static final String MODID = "threateningly_mobs";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private static final Set<String> FIRE = Set.of(
        "threateningly_mobs:fire_lizard", "threateningly_mobs:flamelarva",
        "threateningly_mobs:scorch_golem", "threateningly_mobs:terra_dragon_re",
        "threateningly_mobs:flower_of_dragon", "threateningly_mobs:the_inferno",
        "threateningly_mobs:forest_drake", "threateningly_mobs:forest_drake_tamed",
        "threateningly_mobs:flame_horn_re", "threateningly_mobs:flamestorm",
        "threateningly_mobs:inferno"
    );

    private static final Set<String> ICE = Set.of(
        "threateningly_mobs:ferox_iceworm", "threateningly_mobs:ice_brood_mother",
        "threateningly_mobs:ice_weaver", "threateningly_mobs:frostbite",
        "threateningly_mobs:snow_servent", "threateningly_mobs:icefairy",
        "threateningly_mobs:ice_fairy_guardian"
    );

    private static final Set<String> UNDEAD = Set.of(
        "threateningly_mobs:lich_re", "threateningly_mobs:dungeon_guardian",
        "threateningly_mobs:corrupted_heroicsoul", "threateningly_mobs:executioner",
        "threateningly_mobs:holy_coffin", "threateningly_mobs:hypocritical_saint",
        "threateningly_mobs:tide_specter", "threateningly_mobs:skeleton_predator",
        "threateningly_mobs:skeleton_minion", "threateningly_mobs:elite_zombie_warrior",
        "threateningly_mobs:strong_zombie", "threateningly_mobs:strong_zombie_warrior"
    );

    private static final Set<String> SHADOW = Set.of(
        "threateningly_mobs:shadow_spider", "threateningly_mobs:shadowmoon_butterfly",
        "threateningly_mobs:nibbler"
    );

    private static final Set<String> WORM = Set.of(
        "threateningly_mobs:sandworm", "threateningly_mobs:ferox_death_worm",
        "threateningly_mobs:the_earthloong"
    );

    private static final Set<String> WATER = Set.of(
        "threateningly_mobs:giant_seacucumber", "threateningly_mobs:riptooth",
        "threateningly_mobs:hippo_fish", "threateningly_mobs:red_triplefish",
        "threateningly_mobs:diplocaulus"
    );

    private ThreateninglyMobsCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Threateningly Mobs detected — mob atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("threateningly_mobs:")) return;

        BlockPos pos = entity.blockPosition();

        // Boss-scale variants get radius emissions
        if (type.equals("threateningly_mobs:the_regalhart")) {
            gasRadius(level, pos, GasRegistry.WATER_VAPOR,    12f, 3);
            gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 6f, 2);
            ShockwaveHandler.spawn(level, pos, 2f);
        } else if (type.equals("threateningly_mobs:the_earthloong")) {
            partRadius(level, pos, ParticulateType.DUST,        30f, 4);
            partRadius(level, pos, ParticulateType.GRAVEL_DUST, 20f, 3);
            ShockwaveHandler.spawn(level, pos, 3f);
        } else if (type.equals("threateningly_mobs:lich_re")) {
            gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  14f, 3);
            gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 10f, 2);
            partRadius(level, pos, ParticulateType.SOUL_WISPS, 18f, 3);
        } else if (FIRE.contains(type)) {
            gas(level, pos, GasRegistry.BLAZE_FUME,     8f);
            gas(level, pos, GasRegistry.SULFUR_DIOXIDE,  4f);
            drain(level, pos, GasRegistry.OXYGEN, 5f);
        } else if (ICE.contains(type)) {
            gas(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 10f);
            part(level, pos, ParticulateType.ICE_CRYSTALS, 12f);
        } else if (UNDEAD.contains(type)) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE,   6f);
            gas(level, pos, GasRegistry.WITHER_MIASMA,  4f);
            part(level, pos, ParticulateType.SOUL_WISPS, 8f);
        } else if (SHADOW.contains(type)) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE,   5f);
            part(level, pos, ParticulateType.SOUL_WISPS, 6f);
        } else if (WORM.contains(type)) {
            part(level, pos, ParticulateType.DUST,        18f);
            part(level, pos, ParticulateType.GRAVEL_DUST, 12f);
        } else if (WATER.contains(type)) {
            gas(level, pos, GasRegistry.WATER_VAPOR,    8f);
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 3f);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("threateningly_mobs:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        switch (type) {
            case "threateningly_mobs:lich_re" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  60f, 6);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 40f, 5);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 80f, 6);
                ShockwaveHandler.spawn(level, pos, 10f);
            }
            case "threateningly_mobs:the_regalhart" -> {
                gasRadius(level, pos, GasRegistry.WATER_VAPOR,      50f, 7);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 30f, 5);
                ShockwaveHandler.spawn(level, pos, 12f);
            }
            case "threateningly_mobs:the_earthloong" -> {
                partRadius(level, pos, ParticulateType.DUST,        180f, 10);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 120f,  8);
                gasRadius(level, pos, GasRegistry.WATER_VAPOR,      30f,  6);
                ShockwaveHandler.spawn(level, pos, 18f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("threateningly_mobs:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "threateningly_mobs:inferno_projectile" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     30f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 12f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  60f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            case "threateningly_mobs:ice_worm_acid",
                 "threateningly_mobs:red_worm_acid" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 25f, 3);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 10f, 2);
                partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 30f, 3);
            }
            case "threateningly_mobs:moon_ball_bullet" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   18f, 3);
                gasRadius(level, pos, GasRegistry.IONISED_AIR,    12f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 22f, 3);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "threateningly_mobs:slash_wave" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 12f, 2);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "threateningly_mobs:posion_goo_bullet" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 12f, 2);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 18f, 2);
            }
            case "threateningly_mobs:magic_ammo",
                 "threateningly_mobs:summon_magic",
                 "threateningly_mobs:summon_magic_large",
                 "threateningly_mobs:summon_magic_medium" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 10f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 12f, 2);
            }
            case "threateningly_mobs:light_energy_ammo" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 15f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 18f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
        }
    }
}
