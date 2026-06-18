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
 * Box of Horrors compat (modid: boh).
 *
 * All entity IDs confirmed from BohModEntities.class.
 * Entities grouped by atmospheric archetype:
 *
 * <ul>
 *   <li><b>Kaiju-scale</b> (goji, rexy, rolling_giant, siren_head, stiltwalker):
 *       massive DUST/GRAVEL_DUST/WATER_VAPOR + continuous shockwave footsteps.</li>
 *   <li><b>Spectral/ghost</b> (ghost, sadako, kirie_himuro, willowisp, gold_lost,
 *       phantom_bb/chica/foxy/freddy/mangle/puppet, tails_doll):
 *       SOUL_ESSENCE + SOUL_WISPS aura.</li>
 *   <li><b>Demonic</b> (demon, pyramid_head, torment_pyramid, horror_sans,
 *       taken_hands/pillar, demogorgon):
 *       SOUL_ESSENCE + WITHER_MIASMA aura, stronger on bosses.</li>
 *   <li><b>Organic/swamp</b> (swamp_monster, the_thing_dog, the_thing_villager,
 *       newborn, chestburster, xenomorph, facehugger, lifeform):
 *       H₂S + ORGANIC_AEROSOL decay cloud.</li>
 *   <li><b>Undead/corpse</b> (vampire, werewolf, mummy-adjacent undead):
 *       H₂S + faint SOUL_ESSENCE.</li>
 *   <li><b>Alien/sci-fi</b> (gray_alien, martian_drone, flatwoods_monster, saucer):
 *       IONISED_AIR + OZONE (propulsion field).</li>
 *   <li><b>Wendigo/cryptid</b> (wendigo, rake, slender_man, seed_eater, nothing_there,
 *       russian_sleep_experiment): ORGANIC_AEROSOL + SOUL_ESSENCE.</li>
 *   <li><b>Projectiles</b>: gaster_blaster_projectile → IONISED_AIR + shockwave;
 *       hypno_shot_projectile → SOUL_ESSENCE burst;
 *       tar_ball → ORGANIC_AEROSOL splash;
 *       mothmanbast_projectile → IONISED_AIR.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BoxOfHorrorsCompat {

    public static final String MODID = "boh";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL      = 20;
    private static final int KAIJU_DUST_TICK     = 10;
    private static int tick = 0;

    // ── Archetype sets (all IDs confirmed from BohModEntities.class) ──────────

    private static final Set<String> KAIJU = Set.of(
        "boh:goji", "boh:rexy", "boh:rolling_giant", "boh:siren_head", "boh:stiltwalker"
    );

    private static final Set<String> SPECTRAL = Set.of(
        "boh:ghost", "boh:sadako", "boh:kirie_himuro", "boh:willowisp",
        "boh:gold_lost", "boh:tails_doll",
        "boh:phantom_bb", "boh:phantom_chica", "boh:phantom_foxy",
        "boh:phantom_freddy", "boh:phantom_mangle", "boh:phantom_puppet"
    );

    private static final Set<String> DEMONIC = Set.of(
        "boh:demon", "boh:pyramid_head", "boh:torment_pyramid",
        "boh:horror_sans", "boh:taken_hands", "boh:taken_pillar", "boh:demogorgon"
    );

    private static final Set<String> ORGANIC = Set.of(
        "boh:swamp_monster", "boh:the_thing_dog", "boh:the_thing_villager",
        "boh:newborn", "boh:chestburster", "boh:xenomorph", "boh:facehugger", "boh:lifeform"
    );

    private static final Set<String> UNDEAD = Set.of(
        "boh:vampire", "boh:vampire_bat", "boh:werewolf",
        "boh:ben_drowned", "boh:boiled_one"
    );

    private static final Set<String> ALIEN = Set.of(
        "boh:gray_alien", "boh:martian_drone", "boh:flatwoods_monster", "boh:saucer"
    );

    private static final Set<String> CRYPTID = Set.of(
        "boh:wendigo", "boh:rake", "boh:slender_man", "boh:seed_eater",
        "boh:nothing_there", "boh:russian_sleep_experiment", "boh:mothman",
        "boh:grafton_monster", "boh:fresno_nightcrawler", "boh:fuwatti"
    );

    private BoxOfHorrorsCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Box of Horrors detected — horror atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("boh:")) return;

        ++tick;
        BlockPos pos = entity.blockPosition();

        if (KAIJU.contains(type)) {
            // Continuous seismic dust
            if (tick % KAIJU_DUST_TICK == 0) {
                partRadius(level, pos, ParticulateType.DUST,        50f, 5);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 35f, 4);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            if (tick % TICK_INTERVAL == 0) {
                gasRadius(level, pos, GasRegistry.WATER_VAPOR, 15f, 4);
            }
        } else if (SPECTRAL.contains(type)) {
            if (tick % TICK_INTERVAL != 0) return;
            gas(level, pos, GasRegistry.SOUL_ESSENCE,   8f);
            part(level, pos, ParticulateType.SOUL_WISPS, 10f);
        } else if (DEMONIC.contains(type)) {
            if (tick % TICK_INTERVAL != 0) return;
            gas(level, pos, GasRegistry.SOUL_ESSENCE,   10f);
            gas(level, pos, GasRegistry.WITHER_MIASMA,   7f);
            part(level, pos, ParticulateType.SOUL_WISPS, 12f);
        } else if (ORGANIC.contains(type)) {
            if (tick % TICK_INTERVAL != 0) return;
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 6f);
            part(level, pos, ParticulateType.ORGANIC_AEROSOL, 8f);
        } else if (UNDEAD.contains(type)) {
            if (tick % TICK_INTERVAL != 0) return;
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 4f);
            gas(level, pos, GasRegistry.SOUL_ESSENCE,     3f);
        } else if (ALIEN.contains(type)) {
            if (tick % TICK_INTERVAL != 0) return;
            gas(level, pos, GasRegistry.IONISED_AIR, 8f);
            gas(level, pos, GasRegistry.OZONE,        4f);
            part(level, pos, ParticulateType.IONISED_PARTICLES, 6f);
        } else if (CRYPTID.contains(type)) {
            if (tick % TICK_INTERVAL != 0) return;
            gas(level, pos, GasRegistry.SOUL_ESSENCE,  5f);
            part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("boh:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        if (KAIJU.contains(type)) {
            partRadius(level, pos, ParticulateType.DUST,        200f, 10);
            partRadius(level, pos, ParticulateType.GRAVEL_DUST, 140f,  8);
            gasRadius(level, pos, GasRegistry.WATER_VAPOR,      50f,  7);
            ShockwaveHandler.spawn(level, pos, 20f);
        } else if (DEMONIC.contains(type)) {
            gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  30f, 4);
            gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 20f, 3);
            partRadius(level, pos, ParticulateType.SOUL_WISPS, 40f, 4);
            ShockwaveHandler.spawn(level, pos, 5f);
        } else if (SPECTRAL.contains(type)) {
            gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 20f, 3);
            partRadius(level, pos, ParticulateType.SOUL_WISPS, 28f, 3);
        } else if (ORGANIC.contains(type)) {
            gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 20f, 3);
            partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 30f, 3);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("boh:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "boh:gaster_blaster_projectile" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 25f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,       10f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 30f, 3);
                ShockwaveHandler.spawn(level, pos, 7f);
            }
            case "boh:hypno_shot_projectile" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 15f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 20f, 2);
            }
            case "boh:tar_ball" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 10f, 2);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 20f, 2);
            }
            case "boh:mothmanbast_projectile" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 15f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 18f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
        }
    }
}
