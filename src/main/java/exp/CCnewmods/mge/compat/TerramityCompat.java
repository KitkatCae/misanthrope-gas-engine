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

import java.util.Set;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Terramity compat (modid: terramity).
 *
 * Entity IDs derived from entity class names (snake_case conversion).
 *
 * <ul>
 *   <li><b>gundalf</b> — magical artillery golem: IONISED_AIR + BLAZE_FUME from weapon fire.</li>
 *   <li><b>thunker</b> — heavy impact creature: DUST + GRAVEL_DUST + shockwave.</li>
 *   <li><b>hellrok / duskrok</b> — volcanic rock creature: BLAZE_FUME + SO₂ + VOLCANIC_FUMES.</li>
 *   <li><b>uvogre</b> — cave ogre: ORGANIC_AEROSOL + GRAVEL_DUST.</li>
 *   <li><b>virtue</b> — holy being: SOUL_ESSENCE + IONISED_AIR (divine energy).</li>
 *   <li><b>gatmancer</b> — void mage: WITHER_MIASMA + VOID_BREATH.</li>
 *   <li><b>conjurling</b> — ender summoner: ENDER_PARTICULATE.</li>
 *   <li><b>fairies</b> (blue/green/pink_fairy): BROWN_MUSHROOM_SPORES + IONISED_AIR (nature dust).</li>
 *   <li><b>dungeon_sentry / simple_turret / ultra_sniffer / super_sniffer</b>: IONISED_AIR.</li>
 *   <li><b>Projectiles</b>: black_hole_bomb/micro_black_hole → VOID_BREATH + massive vacuum;
 *       antimatter → VOID_BREATH + O₂ drain; laser_projectile → IONISED_AIR;
 *       rocket → BLAZE_FUME + shockwave; fireball → BLAZE_FUME + SO₂;
 *       holy_beam → SOUL_ESSENCE; unholy_beam/lance → WITHER_MIASMA;
 *       shockbolt → IONISED_AIR + shockwave; plague_pellet → CADAVERINE + H₂S;
 *       meteor → massive DUST + shockwave; spirit_bomb → SOUL_ESSENCE burst.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerramityCompat {

    public static final String MODID = "terramity";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private static final Set<String> TURRET = Set.of(
        "terramity:dungeon_sentry", "terramity:simple_turret",
        "terramity:ultra_sniffer", "terramity:super_sniffer",
        "terramity:trial_guardian"
    );

    private TerramityCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Terramity detected — mob atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("terramity:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "terramity:gundalf", "terramity:bomb_flower_gundalf" -> {
                gas(level, pos, GasRegistry.IONISED_AIR,     10f);
                gas(level, pos, GasRegistry.BLAZE_FUME,       6f);
                part(level, pos, ParticulateType.IONISED_PARTICLES, 8f);
            }
            case "terramity:thunker" -> {
                part(level, pos, ParticulateType.DUST,         20f);
                part(level, pos, ParticulateType.GRAVEL_DUST,  14f);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "terramity:hellrok", "terramity:duskrok" -> {
                gas(level, pos, GasRegistry.BLAZE_FUME,       10f);
                gas(level, pos, GasRegistry.SULFUR_DIOXIDE,    5f);
                gas(level, pos, GasRegistry.VOLCANIC_FUMES,    6f);
                drain(level, pos, GasRegistry.OXYGEN,          6f);
            }
            case "terramity:uvogre", "terramity:megabite" -> {
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
                part(level, pos, ParticulateType.GRAVEL_DUST,    10f);
            }
            case "terramity:virtue" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,     10f);
                gas(level, pos, GasRegistry.IONISED_AIR,       6f);
                part(level, pos, ParticulateType.SOUL_WISPS,  12f);
            }
            case "terramity:gatmancer", "terramity:sorceress_circe",
                 "terramity:stygian_soul" -> {
                gas(level, pos, GasRegistry.WITHER_MIASMA,    8f);
                gas(level, pos, GasRegistry.VOID_BREATH,       6f);
                part(level, pos, ParticulateType.SOUL_WISPS,  10f);
            }
            case "terramity:conjurling", "terramity:apparition" -> {
                gas(level, pos, GasRegistry.ENDER_PARTICULATE, 8f);
                drain(level, pos, GasRegistry.OXYGEN, 4f);
            }
            case "terramity:blue_fairy", "terramity:green_fairy", "terramity:pink_fairy" -> {
                part(level, pos, ParticulateType.BROWN_MUSHROOM_SPORES, 6f);
                gas(level, pos, GasRegistry.IONISED_AIR, 4f);
            }
            case "terramity:elite_skeleton", "terramity:echo_bug" ->
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 4f);
            case "terramity:gob", "terramity:gnome", "terramity:royal_gnome" ->
                part(level, pos, ParticulateType.DUST, 8f);
            default -> {
                if (TURRET.contains(type)) {
                    gas(level, pos, GasRegistry.IONISED_AIR, 5f);
                    part(level, pos, ParticulateType.IONISED_PARTICLES, 4f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("terramity:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        switch (type) {
            case "terramity:thunker" -> {
                partRadius(level, pos, ParticulateType.DUST,        60f, 5);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 40f, 4);
                ShockwaveHandler.spawn(level, pos, 8f);
            }
            case "terramity:virtue" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   40f, 5);
                gasRadius(level, pos, GasRegistry.IONISED_AIR,    20f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 50f, 5);
                ShockwaveHandler.spawn(level, pos, 7f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("terramity:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "terramity:black_hole_bomb_entity",
                 "terramity:macro_black_hole_bomb_entity",
                 "terramity:micro_black_hole",
                 "terramity:black_hole" -> {
                // Micro-singularity — strips all atmosphere in a wide radius
                var comp = GridAtmosphereCompat.getComposition(level, pos);
                float n2 = comp.get(GasRegistry.NITROGEN);
                float o2 = comp.get(GasRegistry.OXYGEN);
                comp.add(GasRegistry.NITROGEN, -n2 * 0.95f);
                comp.add(GasRegistry.OXYGEN,   -o2 * 0.95f);
                comp.add(GasRegistry.VOID_BREATH, 30f);
                GridAtmosphereCompat.setComposition(level, pos, comp);
                drainRadius(level, pos, GasRegistry.OXYGEN,   60f, 6);
                drainRadius(level, pos, GasRegistry.NITROGEN, 50f, 5);
                gasRadius(level, pos, GasRegistry.VOID_BREATH, 40f, 5);
                ShockwaveHandler.spawn(level, pos, 10f);
            }
            case "terramity:antimatter_bomb_entity",
                 "terramity:antimatter_supernova",
                 "terramity:antimatter_round_projectile_projectile" -> {
                gasRadius(level, pos, GasRegistry.VOID_BREATH,  20f, 4);
                drainRadius(level, pos, GasRegistry.OXYGEN,     30f, 4);
                drainRadius(level, pos, GasRegistry.NITROGEN,   20f, 3);
                ShockwaveHandler.spawn(level, pos, 8f);
            }
            case "terramity:laser_projectile",
                 "terramity:soul_laser_projectile",
                 "terramity:guided_energy_blast_projectile" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 18f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "terramity:rocket_projectile",
                 "terramity:safe_rocket_projectile",
                 "terramity:final_kamehameha",
                 "terramity:galick_gun",
                 "terramity:sniffer_kamehameha" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     35f, 4);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 15f, 3);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  70f, 4);
                ShockwaveHandler.spawn(level, pos, 7f);
            }
            case "terramity:fireball_projectile_projectile",
                 "terramity:hellfire_pellet" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     20f, 2);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,  8f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  35f, 2);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "terramity:holy_beam", "terramity:holy_round_aoe",
                 "terramity:holy_round_projectile_no_gravity",
                 "terramity:holy_round_projectile_projectile" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 15f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 18f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "terramity:unholy_beam",
                 "terramity:unholy_lance_projectile" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 18f, 2);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,    10f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "terramity:shock_bolt",
                 "terramity:lightning_bolt" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 20f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,        8f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 22f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            case "terramity:plague_pellet" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,      12f, 2);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 8f, 2);
                partRadius(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 15f, 2);
            }
            case "terramity:meteor", "terramity:safe_meteor" -> {
                partRadius(level, pos, ParticulateType.DUST,        100f, 7);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST,  70f, 6);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,    50f, 5);
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,   30f, 5);
                ShockwaveHandler.spawn(level, pos, 14f);
            }
            case "terramity:spirit_bomb" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 30f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 40f, 4);
                ShockwaveHandler.spawn(level, pos, 6f);
            }
            case "terramity:shadowflame_bullet_projectile",
                 "terramity:shadowflame_ring" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 12f, 2);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,    10f, 2);
            }
        }
    }
}
