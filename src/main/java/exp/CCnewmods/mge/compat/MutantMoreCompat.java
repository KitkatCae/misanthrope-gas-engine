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

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Mutant More compat (modid: mutantmore).
 *
 * All entity and projectile IDs confirmed from EntityTypeInit.class and loot tables.
 *
 * <ul>
 *   <li><b>mutant_blaze</b> — pyrotheum-colony fire titan: heaviest PYROTHEUM_DUST
 *       output of any non-boss entity. BLAZE_FUME + IONISED_AIR + VOLCANIC_FUMES.
 *       Death: pyroclastic + pyrotheum burst.</li>
 *   <li><b>mutant_blaze_part</b> — detached blaze segment: lighter pyrotheum emission.</li>
 *   <li><b>mutant_frozen_zombie</b> — DRAGON_ICE_CLOUD + ICE_CRYSTALS + CADAVERINE + H₂S
 *       (frozen decay).</li>
 *   <li><b>mutant_hoglin</b> — BLAZE_FUME + H₂S (nether beast, hot and rank).</li>
 *   <li><b>mutant_husk</b> — DUST + CADAVERINE + H₂S (desert undead).</li>
 *   <li><b>mutant_jungle_zombie</b> — CADAVERINE + PUTRESCINE + OPHIOCORDYCEPS_HUMANUS
 *       (jungle cordyceps infection vector).</li>
 *   <li><b>mutant_phantom</b> — SOUL_ESSENCE + ENDER_PARTICULATE.</li>
 *   <li><b>mutant_shulker / mutant_shulker_turret</b> — SHULKER_ACID_MIST + VOID_BREATH.</li>
 *   <li><b>mutant_wither_skeleton</b> — WITHER_MIASMA + PYROTHEUM_DUST + SOUL_ESSENCE.
 *       Heaviest wither skeleton profile — nether ecosystem max exposure.</li>
 *   <li><b>rodling</b> — baby blaze (per handoff design note): BLAZE_FUME +
 *       PYROTHEUM_DUST at reduced rate.</li>
 *   <li><b>sentry_vine</b> — ORGANIC_AEROSOL + BROWN_MUSHROOM_SPORES.</li>
 *   <li><b>Projectiles</b>:
 *     mutant_blaze_fireball → PYROTHEUM_DUST + VOLCANIC_FUMES + shockwave;
 *     mutant_blaze_rod_projectile → BLAZE_FUME burst;
 *     giant_snowball → DRAGON_ICE_CLOUD + ICE_CRYSTAL_SHARDS + shockwave;
 *     icicle_spike / thrown_icicle → ICE_CRYSTAL_SHARDS;
 *     mutant_shulker_bullet → SHULKER_ACID_MIST + VOID_BREATH;
 *     wither_bomb → WITHER_MIASMA + shockwave;
 *     wither_slash → WITHER_MIASMA trace;
 *     mutation_cloud / thrown_mutation_potion → CADAVERINE + ORGANIC_AEROSOL;
 *     sand_boulder → DUST + GRAVEL_DUST + shockwave;
 *     ice_cube → DRAGON_ICE_CLOUD burst;
 *     rodling_fireball → BLAZE_FUME + PYROTHEUM_DUST.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MutantMoreCompat {

    public static final String MODID = "mutantmore";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private MutantMoreCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Mutant More detected — mutant atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("mutantmore:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "mutantmore:mutant_blaze" -> {
                // Pyrotheum-colony fire titan — highest non-boss pyrotheum output
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST, 20f, 4);
                gasRadius(level, pos, GasRegistry.IONISED_AIR,         18f, 4);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,          15f, 3);
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,      10f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,                6f, 2);
                drainRadius(level, pos, GasRegistry.OXYGEN,            15f, 3);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,      12f, 2);
            }
            case "mutantmore:mutant_blaze_part" -> {
                part(level, pos, ParticulateType.PYROTHEUM_DUST, 8f);
                gas(level, pos, GasRegistry.BLAZE_FUME,          8f);
                gas(level, pos, GasRegistry.IONISED_AIR,         6f);
                drain(level, pos, GasRegistry.OXYGEN,             5f);
            }
            case "mutantmore:mutant_frozen_zombie" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 15f, 3);
                partRadius(level, pos, ParticulateType.ICE_CRYSTALS, 18f, 3);
                gas(level, pos, GasRegistry.CADAVERINE,               6f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,         4f);
            }
            case "mutantmore:mutant_hoglin" -> {
                gas(level, pos, GasRegistry.BLAZE_FUME,           10f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,      6f);
                drain(level, pos, GasRegistry.OXYGEN,              5f);
            }
            case "mutantmore:mutant_husk" -> {
                part(level, pos, ParticulateType.DUST,             14f);
                gas(level, pos, GasRegistry.CADAVERINE,             6f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,       4f);
            }
            case "mutantmore:mutant_jungle_zombie" -> {
                gas(level, pos, GasRegistry.CADAVERINE,             8f);
                gas(level, pos, GasRegistry.PUTRESCINE,             8f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,       4f);
                part(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 12f);
            }
            case "mutantmore:mutant_phantom" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,           10f);
                gas(level, pos, GasRegistry.ENDER_PARTICULATE,       6f);
                part(level, pos, ParticulateType.SOUL_WISPS,        12f);
                drain(level, pos, GasRegistry.OXYGEN,                5f);
            }
            case "mutantmore:mutant_shulker",
                 "mutantmore:mutant_shulker_turret" -> {
                gas(level, pos, GasRegistry.SHULKER_ACID_MIST,     10f);
                gas(level, pos, GasRegistry.VOID_BREATH,             6f);
                drain(level, pos, GasRegistry.OXYGEN,                5f);
            }
            case "mutantmore:mutant_wither_skeleton",
                 "mutantmore:mutant_wither_skeleton_parts" -> {
                gas(level, pos, GasRegistry.WITHER_MIASMA,          12f);
                gas(level, pos, GasRegistry.SOUL_ESSENCE,            8f);
                part(level, pos, ParticulateType.PYROTHEUM_DUST,     6f);
                part(level, pos, ParticulateType.SOUL_WISPS,        10f);
            }
            case "mutantmore:rodling" -> {
                // Baby blaze — reduced emission rate per handoff design note
                part(level, pos, ParticulateType.PYROTHEUM_DUST,     4f);
                gas(level, pos, GasRegistry.BLAZE_FUME,              4f);
                gas(level, pos, GasRegistry.IONISED_AIR,             2f);
            }
            case "mutantmore:sentry_vine" -> {
                part(level, pos, ParticulateType.ORGANIC_AEROSOL,    6f);
                part(level, pos, ParticulateType.BROWN_MUSHROOM_SPORES, 5f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("mutantmore:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        switch (type) {
            case "mutantmore:mutant_blaze" -> {
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST, 60f, 7);
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,     70f, 7);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,         60f, 6);
                gasRadius(level, pos, GasRegistry.IONISED_AIR,        50f, 6);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,     100f, 7);
                drainRadius(level, pos, GasRegistry.OXYGEN,           60f, 7);
                ShockwaveHandler.spawn(level, pos, 14f);
                ShockwaveDataPacket.sendToNear(level, vec, 14f, 120f);
            }
            case "mutantmore:mutant_wither_skeleton" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,      50f, 6);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,       30f, 5);
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST, 20f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS,    60f, 6);
                ShockwaveHandler.spawn(level, pos, 7f);
                ShockwaveDataPacket.sendToNear(level, vec, 7f, 56f);
            }
            case "mutantmore:mutant_frozen_zombie" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD,   50f, 5);
                partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 70f, 5);
                gasRadius(level, pos, GasRegistry.CADAVERINE,         20f, 3);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,   15f, 3);
                ShockwaveHandler.spawn(level, pos, 6f);
                ShockwaveDataPacket.sendToNear(level, vec, 6f, 50f);
            }
            case "mutantmore:mutant_phantom" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,       40f, 5);
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE,  25f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS,    50f, 5);
                ShockwaveHandler.spawn(level, pos, 6f);
                ShockwaveDataPacket.sendToNear(level, vec, 6f, 50f);
            }
            case "mutantmore:mutant_jungle_zombie" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,         30f, 4);
                gasRadius(level, pos, GasRegistry.PUTRESCINE,         30f, 4);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,   20f, 3);
                partRadius(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 45f, 4);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("mutantmore:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        switch (type) {
            case "mutantmore:mutant_blaze_fireball" -> {
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST, 25f, 4);
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,      30f, 4);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,          20f, 3);
                gasRadius(level, pos, GasRegistry.IONISED_AIR,         15f, 3);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,       55f, 4);
                drainRadius(level, pos, GasRegistry.OXYGEN,            18f, 3);
                ShockwaveHandler.spawn(level, pos, 8f);
                ShockwaveDataPacket.sendToNear(level, vec, 8f, 72f);
            }
            case "mutantmore:mutant_blaze_rod_projectile",
                 "mutantmore:mutant_blaze_shields" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,          18f, 2);
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST,  8f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,      25f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
            case "mutantmore:rodling_fireball" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,          12f, 2);
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST,  5f, 1);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,      15f, 2);
                ShockwaveHandler.spawn(level, pos, 2f);
                ShockwaveDataPacket.sendToNear(level, vec, 2f, 18f);
            }
            case "mutantmore:giant_snowball" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD,    40f, 4);
                partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 60f, 4);
                partRadius(level, pos, ParticulateType.ICE_CRYSTALS,   30f, 3);
                ShockwaveHandler.spawn(level, pos, 6f);
                ShockwaveDataPacket.sendToNear(level, vec, 6f, 55f);
            }
            case "mutantmore:icicle_spike",
                 "mutantmore:thrown_icicle" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD,    20f, 2);
                partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 30f, 2);
            }
            case "mutantmore:ice_cube" ->
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD,    15f, 2);
            case "mutantmore:mutant_shulker_bullet",
                 "mutantmore:custom_shulker_bullet",
                 "mutantmore:mutant_shulker_trap" -> {
                gasRadius(level, pos, GasRegistry.SHULKER_ACID_MIST,   18f, 2);
                gasRadius(level, pos, GasRegistry.VOID_BREATH,          10f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
            case "mutantmore:wither_bomb" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,       35f, 4);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,          20f, 3);
                partRadius(level, pos, ParticulateType.SOUL_WISPS,     45f, 4);
                ShockwaveHandler.spawn(level, pos, 7f);
                ShockwaveDataPacket.sendToNear(level, vec, 7f, 64f);
            }
            case "mutantmore:wither_slash" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,       12f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS,     15f, 2);
            }
            case "mutantmore:mutation_cloud",
                 "mutantmore:thrown_mutation_potion",
                 "mutantmore:concoction_w" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,          12f, 3);
                gasRadius(level, pos, GasRegistry.PUTRESCINE,          10f, 2);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 20f, 3);
                partRadius(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 15f, 2);
            }
            case "mutantmore:sand_boulder" -> {
                partRadius(level, pos, ParticulateType.DUST,            50f, 4);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST,     35f, 3);
                ShockwaveHandler.spawn(level, pos, 6f);
                ShockwaveDataPacket.sendToNear(level, vec, 6f, 55f);
            }
            case "mutantmore:zombie_resurrection",
                 "mutantmore:area_damage" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,           8f, 2);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,     5f, 2);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 10f, 2);
            }
        }
    }
}
