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
 * L_Ender's Cataclysm compat (modid: cataclysm).
 *
 * All entity IDs confirmed from ModEntities.class.
 *
 * <ul>
 *   <li><b>ignis</b> — ignitium-body fire titan. Emits PYROTHEUM_DUST at very high
 *       rate (ignitium surface continuously ionises surrounding air). Also VOLCANIC_FUMES
 *       + BLAZE_FUME + IONISED_AIR. Death: pyroclastic + pyrotheum burst.</li>
 *   <li><b>the_leviathan / babyleviathan / the_baby_leviathan</b> — deep ocean horror:
 *       WATER_VAPOR + H₂S + SHULKER_ACID_MIST. Death: massive water/acid burst.</li>
 *   <li><b>draugr / elite_draugr / royal_draugr / drowned_host / ignited_berserker /
 *       ignited_revenant</b> — undead: WITHER_MIASMA + H₂S.
 *       Ignited variants add BLAZE_FUME.</li>
 *   <li><b>deepling family</b> (deepling, deepling_angler, deepling_brute, deepling_priest,
 *       deepling_warlock) — WATER_VAPOR + SHULKER_ACID_MIST.</li>
 *   <li><b>ender_golem / ender_guardian</b> — VOID_BREATH + ENDER_PARTICULATE.</li>
 *   <li><b>maledictus</b> — WITHER_MIASMA + SOUL_ESSENCE + CADAVERINE (putrid sorcerer).</li>
 *   <li><b>the_harbinger</b> — boss: VOID_BREATH + massive O₂ drain.
 *       Death: void implosion + shockwave.</li>
 *   <li><b>scylla</b> — sea monster: WATER_VAPOR + H₂S + SHULKER_ACID_MIST wide radius.</li>
 *   <li><b>storm_serpent</b> — IONISED_AIR + OZONE lightning snake.</li>
 *   <li><b>the_watcher / nameless_sorcerer</b> — SOUL_ESSENCE + VOID_BREATH.</li>
 *   <li><b>Projectiles</b>: ignis_fireball/ignis_abyss_fireball → PYROTHEUM_DUST +
 *       VOLCANIC_FUMES + shockwave; abyss_blast → VOID_BREATH + vacuum;
 *       lightning_spear/storm → IONISED_AIR + shockwave; water_spear → WATER_VAPOR;
 *       coral_spear → WATER_VAPOR + H₂S; ashen_breath → ASH_CLOUD + BLAZE_FUME;
 *       cursed_sandstorm → DUST mass; void_shard/vortex → VOID_BREATH.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CataclysmCompat {

    public static final String MODID = "cataclysm";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private static final Set<String> DRAUGR = Set.of(
        "cataclysm:draugr", "cataclysm:elite_draugr", "cataclysm:royal_draugr",
        "cataclysm:drowned_host", "cataclysm:kobolediator"
    );
    private static final Set<String> IGNITED_UNDEAD = Set.of(
        "cataclysm:ignited_berserker", "cataclysm:ignited_revenant"
    );
    private static final Set<String> DEEPLING = Set.of(
        "cataclysm:deepling", "cataclysm:deepling_angler", "cataclysm:deepling_brute",
        "cataclysm:deepling_priest", "cataclysm:deepling_warlock",
        "cataclysm:urchin", "cataclysm:hippocamtus", "cataclysm:lionfish", "cataclysm:octo"
    );
    private static final Set<String> LEVIATHAN = Set.of(
        "cataclysm:the_leviathan", "cataclysm:babyleviathan", "cataclysm:the_baby_leviathan"
    );

    private CataclysmCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] L_Ender's Cataclysm detected — boss atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("cataclysm:")) return;

        BlockPos pos = entity.blockPosition();
        Vec3 vec = entity.position();

        switch (type) {
            case "cataclysm:ignis" -> {
                // Ignitium-body: the surface continuously discharges pyrotheum dust and plasma
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST,  25f, 4);
                gasRadius(level, pos, GasRegistry.IONISED_AIR,          20f, 4);
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,       18f, 4);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,           15f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,                 8f, 3);
                gasRadius(level, pos, GasRegistry.MAGMATIC_CO2,         10f, 3);
                drainRadius(level, pos, GasRegistry.OXYGEN,             20f, 4);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,       20f, 3);
            }
            case "cataclysm:the_harbinger" -> {
                gasRadius(level, pos, GasRegistry.VOID_BREATH,          18f, 5);
                drainRadius(level, pos, GasRegistry.OXYGEN,             20f, 5);
                drainRadius(level, pos, GasRegistry.NITROGEN,           15f, 4);
                gas(level, pos, GasRegistry.ENDER_PARTICULATE,          10f);
            }
            case "cataclysm:scylla" -> {
                gasRadius(level, pos, GasRegistry.WATER_VAPOR,          15f, 5);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,      8f, 4);
                gasRadius(level, pos, GasRegistry.SHULKER_ACID_MIST,   10f, 4);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 32f);
            }
            case "cataclysm:maledictus" -> {
                gas(level, pos, GasRegistry.WITHER_MIASMA,              10f);
                gas(level, pos, GasRegistry.SOUL_ESSENCE,                8f);
                gas(level, pos, GasRegistry.CADAVERINE,                  5f);
                part(level, pos, ParticulateType.SOUL_WISPS,            12f);
            }
            case "cataclysm:the_watcher",
                 "cataclysm:nameless_sorcerer",
                 "cataclysm:wall_watcher",
                 "cataclysm:eye_of_dungeon" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,                8f);
                gas(level, pos, GasRegistry.VOID_BREATH,                 6f);
                part(level, pos, ParticulateType.SOUL_WISPS,            10f);
            }
            case "cataclysm:ender_golem",
                 "cataclysm:ender_guardian",
                 "cataclysm:endermaptera",
                 "cataclysm:cindaria" -> {
                gas(level, pos, GasRegistry.VOID_BREATH,                 8f);
                gas(level, pos, GasRegistry.ENDER_PARTICULATE,           8f);
                drain(level, pos, GasRegistry.OXYGEN,                    6f);
            }
            case "cataclysm:storm_serpent" -> {
                gas(level, pos, GasRegistry.IONISED_AIR,                12f);
                gas(level, pos, GasRegistry.OZONE,                       6f);
                gas(level, pos, GasRegistry.NITRIC_OXIDE,                3f);
                part(level, pos, ParticulateType.IONISED_PARTICLES,     10f);
            }
            case "cataclysm:ancient_remnant",
                 "cataclysm:ancient_ancient_remnant",
                 "cataclysm:modern_remnant",
                 "cataclysm:the_prowler",
                 "cataclysm:ministrosit",
                 "cataclysm:ministrosity",
                 "cataclysm:netherite_ministrosity",
                 "cataclysm:netherite_monstrosity" -> {
                gas(level, pos, GasRegistry.WITHER_MIASMA,               6f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,            4f);
            }
            default -> {
                if (DRAUGR.contains(type)) {
                    gas(level, pos, GasRegistry.WITHER_MIASMA,           6f);
                    gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,        4f);
                    part(level, pos, ParticulateType.SOUL_WISPS,         6f);
                } else if (IGNITED_UNDEAD.contains(type)) {
                    gas(level, pos, GasRegistry.WITHER_MIASMA,           5f);
                    gas(level, pos, GasRegistry.BLAZE_FUME,              6f);
                    gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,        3f);
                    part(level, pos, ParticulateType.PYROTHEUM_DUST,     4f);
                } else if (DEEPLING.contains(type)) {
                    gas(level, pos, GasRegistry.WATER_VAPOR,             6f);
                    gas(level, pos, GasRegistry.SHULKER_ACID_MIST,      4f);
                } else if (LEVIATHAN.contains(type)) {
                    float scale = type.contains("baby") ? 0.4f : 1.0f;
                    gasRadius(level, pos, GasRegistry.WATER_VAPOR,      15f * scale, (int)(5 * scale + 1));
                    gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,  8f * scale, (int)(4 * scale + 1));
                    gasRadius(level, pos, GasRegistry.SHULKER_ACID_MIST,10f * scale, (int)(4 * scale + 1));
                    if (!type.contains("baby")) {
                        ShockwaveHandler.spawn(level, pos, 3f);
                        ShockwaveDataPacket.sendToNear(level, vec, 3f, 32f);
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
        if (!type.startsWith("cataclysm:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        switch (type) {
            case "cataclysm:ignis" -> {
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST,  80f, 8);
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,      100f, 9);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,           80f, 8);
                gasRadius(level, pos, GasRegistry.IONISED_AIR,          60f, 7);
                gasRadius(level, pos, GasRegistry.MAGMATIC_CO2,         40f, 6);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,      150f, 8);
                drainRadius(level, pos, GasRegistry.OXYGEN,             80f, 8);
                ShockwaveHandler.spawn(level, pos, 18f);
                ShockwaveDataPacket.sendToNear(level, vec, 18f, 180f);
            }
            case "cataclysm:the_harbinger" -> {
                gasRadius(level, pos, GasRegistry.VOID_BREATH,          80f, 10);
                drainRadius(level, pos, GasRegistry.OXYGEN,             80f, 10);
                drainRadius(level, pos, GasRegistry.NITROGEN,           60f,  8);
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE,    40f,  7);
                ShockwaveHandler.spawn(level, pos, 20f);
                ShockwaveDataPacket.sendToNear(level, vec, 20f, 200f);
            }
            case "cataclysm:the_leviathan" -> {
                gasRadius(level, pos, GasRegistry.WATER_VAPOR,          80f, 10);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,     50f,  8);
                gasRadius(level, pos, GasRegistry.SHULKER_ACID_MIST,   40f,  7);
                ShockwaveHandler.spawn(level, pos, 16f);
                ShockwaveDataPacket.sendToNear(level, vec, 16f, 160f);
            }
            case "cataclysm:scylla" -> {
                gasRadius(level, pos, GasRegistry.WATER_VAPOR,          50f, 7);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,     30f, 5);
                gasRadius(level, pos, GasRegistry.SHULKER_ACID_MIST,   25f, 5);
                ShockwaveHandler.spawn(level, pos, 10f);
                ShockwaveDataPacket.sendToNear(level, vec, 10f, 80f);
            }
            case "cataclysm:maledictus" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,        50f, 6);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,         35f, 5);
                gasRadius(level, pos, GasRegistry.CADAVERINE,           25f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS,      60f, 6);
                ShockwaveHandler.spawn(level, pos, 8f);
                ShockwaveDataPacket.sendToNear(level, vec, 8f, 64f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("cataclysm:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        switch (type) {
            case "cataclysm:ignis_fireball" -> {
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST,  30f, 4);
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,       35f, 4);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,           25f, 3);
                gasRadius(level, pos, GasRegistry.IONISED_AIR,          20f, 3);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,       60f, 4);
                drainRadius(level, pos, GasRegistry.OXYGEN,             20f, 3);
                ShockwaveHandler.spawn(level, pos, 7f);
                ShockwaveDataPacket.sendToNear(level, vec, 7f, 64f);
            }
            case "cataclysm:ignis_abyss_fireball",
                 "cataclysm:flame_strike" -> {
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST,  20f, 3);
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,       20f, 3);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,           15f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,       35f, 3);
                ShockwaveHandler.spawn(level, pos, 4f);
                ShockwaveDataPacket.sendToNear(level, vec, 4f, 40f);
            }
            case "cataclysm:abyss_blast",
                 "cataclysm:mini_abyss_blast",
                 "cataclysm:portal_abyss_blast",
                 "cataclysm:abyss_blast_portal" -> {
                gasRadius(level, pos, GasRegistry.VOID_BREATH,          25f, 3);
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE,    15f, 2);
                drainRadius(level, pos, GasRegistry.OXYGEN,             20f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
            case "cataclysm:void_shard",
                 "cataclysm:void_vortex",
                 "cataclysm:void_howitzer",
                 "cataclysm:void_scatter_arrow" -> {
                gasRadius(level, pos, GasRegistry.VOID_BREATH,          15f, 2);
                drainRadius(level, pos, GasRegistry.OXYGEN,             12f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
            case "cataclysm:lightning_spear",
                 "cataclysm:lightning_storm",
                 "cataclysm:lightning_area_effect",
                 "cataclysm:bolt_strike" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR,          25f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,                10f, 2);
                gasRadius(level, pos, GasRegistry.NITRIC_OXIDE,          5f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES,30f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
            case "cataclysm:water_spear",
                 "cataclysm:wave" ->
                gasRadius(level, pos, GasRegistry.WATER_VAPOR,          20f, 3);
            case "cataclysm:coral_spear",
                 "cataclysm:tidal_hook",
                 "cataclysm:tidal_tentacle" -> {
                gasRadius(level, pos, GasRegistry.WATER_VAPOR,          12f, 2);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,      6f, 2);
            }
            case "cataclysm:ashen_breath" -> {
                partRadius(level, pos, ParticulateType.ASH_CLOUD,       40f, 3);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,           15f, 2);
            }
            case "cataclysm:cursed_sandstorm",
                 "cataclysm:sandstorm_projectile" -> {
                partRadius(level, pos, ParticulateType.DUST,            60f, 5);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST,     40f, 4);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
            case "cataclysm:wither_missile",
                 "cataclysm:wither_howitzer",
                 "cataclysm:wither_homing_missile" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,        25f, 3);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,           15f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS,      30f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
            case "cataclysm:earthquake",
                 "cataclysm:earth_shard" -> {
                partRadius(level, pos, ParticulateType.DUST,            50f, 4);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST,     35f, 3);
                ShockwaveHandler.spawn(level, pos, 8f);
                ShockwaveDataPacket.sendToNear(level, vec, 8f, 72f);
            }
            case "cataclysm:poison_dart",
                 "cataclysm:urchin_spike",
                 "cataclysm:lionfish_spike" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,     8f, 2);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 10f, 2);
            }
            case "cataclysm:octo_ink" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,    10f, 2);
                gasRadius(level, pos, GasRegistry.SHULKER_ACID_MIST,   8f, 2);
            }
            case "cataclysm:ender_guardian_bullet",
                 "cataclysm:phantom_arrow",
                 "cataclysm:phantom_halberd" -> {
                gasRadius(level, pos, GasRegistry.VOID_BREATH,          12f, 2);
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE,     8f, 2);
            }
            case "cataclysm:blazing_bone",
                 "cataclysm:flare_bomb" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,           18f, 2);
                partRadius(level, pos, ParticulateType.PYROTHEUM_DUST,  10f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,       30f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
        }
    }
}
