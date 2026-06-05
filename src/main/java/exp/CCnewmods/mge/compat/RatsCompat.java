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
 * Rats compat (modid: rats).
 *
 * All entity IDs confirmed from RatsEntityRegistry.class and loot tables.
 *
 * <ul>
 *   <li><b>rat_king</b> — boss: CADAVERINE + PUTRESCINE + H₂S heavy field.
 *       Death: large toxic burst.</li>
 *   <li><b>plague_beast</b> — CADAVERINE + H₂S + OPHIOCORDYCEPS_HUMANUS (plague carrier).</li>
 *   <li><b>black_death</b> — full plague: CADAVERINE + PUTRESCINE + INDOLE + H₂S +
 *       OPHIOCORDYCEPS_HUMANUS. Most toxic entity in the mod.</li>
 *   <li><b>plague_doctor</b> — mild ORGANIC_AEROSOL (herbal medicine smells) + H₂S.</li>
 *   <li><b>pied_piper</b> — ORGANIC_AEROSOL (the scent that drives rats).</li>
 *   <li><b>demon_rat</b> — SOUL_ESSENCE + BLAZE_FUME (hellish rat).</li>
 *   <li><b>ratlantean_spirit / ghost_pirat</b> — SOUL_ESSENCE aura.</li>
 *   <li><b>rat_baron</b> — CADAVERINE + PUTRESCINE (elite rat).</li>
 *   <li><b>Projectiles</b>: plague_shot → full plague gas burst;
 *       rat_dragon_fire → BLAZE_FUME + CO₂;
 *       plague_cloud → sustained CADAVERINE + H₂S + OPHIOCORDYCEPS_HUMANUS;
 *       thrown_block → DUST + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RatsCompat {

    public static final String MODID = "rats";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private RatsCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Rats detected — plague atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("rats:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            // ── Base mod ──────────────────────────────────────────────────────
            case "rats:rat_king" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,       12f, 4);
                gasRadius(level, pos, GasRegistry.PUTRESCINE,       12f, 4);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE,  8f, 3);
                gasRadius(level, pos, GasRegistry.INDOLE,            6f, 3);
                partRadius(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 15f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL,        10f, 3);
            }
            case "rats:plague_beast" -> {
                gas(level, pos, GasRegistry.CADAVERINE,       8f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 6f);
                part(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 10f);
            }
            case "rats:black_death" -> {
                gas(level, pos, GasRegistry.CADAVERINE,       10f);
                gas(level, pos, GasRegistry.PUTRESCINE,       10f);
                gas(level, pos, GasRegistry.INDOLE,            6f);
                gas(level, pos, GasRegistry.SKATOLE,           5f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,  8f);
                part(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 14f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL,        8f);
            }
            case "rats:plague_doctor" -> {
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 3f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 5f);
            }
            case "rats:pied_piper" ->
                    part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
            case "rats:demon_rat" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 8f);
                gas(level, pos, GasRegistry.BLAZE_FUME,   6f);
                part(level, pos, ParticulateType.SOUL_WISPS, 8f);
            }
            case "rats:ghost_pirat" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,   6f);
                part(level, pos, ParticulateType.SOUL_WISPS, 8f);
            }
            case "rats:rat_baron" -> {
                gas(level, pos, GasRegistry.CADAVERINE,       5f);
                gas(level, pos, GasRegistry.PUTRESCINE,       5f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 3f);
            }
            // ── Ratlantis ─────────────────────────────────────────────────────
            // IDs confirmed from RatlantisEntityRegistry.class
            case "rats:ratlantean_automaton",
                 "rats:ratlantean_ratbot",
                 "rats:rat_mount_automaton",
                 "rats:rat_mount_biplane",
                 "rats:rat_baron_plane" -> {
                gas(level, pos, GasRegistry.IONISED_AIR, 8f);
                gas(level, pos, GasRegistry.OZONE,        4f);
                part(level, pos, ParticulateType.IONISED_PARTICLES, 6f);
            }
            case "rats:pirat",
                 "rats:dutchrat",
                 "rats:feral_ratlantean",
                 "rats:neo_ratlantean",
                 "rats:rat_protector" -> {
                gas(level, pos, GasRegistry.CADAVERINE,       4f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 3f);
            }
            case "rats:ratlantean_spirit" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,   8f);
                part(level, pos, ParticulateType.SOUL_WISPS, 10f);
            }
            case "rats:ratlantean_spirit_flame" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 6f);
                gas(level, pos, GasRegistry.BLAZE_FUME,   6f);
                part(level, pos, ParticulateType.SOUL_WISPS, 8f);
            }
            case "rats:ratfish" -> {
                gas(level, pos, GasRegistry.WATER_VAPOR,    6f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 3f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("rats:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        switch (type) {
            case "rats:rat_king" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,       60f, 7);
                gasRadius(level, pos, GasRegistry.PUTRESCINE,       60f, 7);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 40f, 6);
                gasRadius(level, pos, GasRegistry.INDOLE,           30f, 5);
                partRadius(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 80f, 7);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL,        60f, 6);
                ShockwaveHandler.spawn(level, pos, 10f);
                ShockwaveDataPacket.sendToNear(level, vec, 10f, 80f);
            }
            case "rats:black_death" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,       40f, 5);
                gasRadius(level, pos, GasRegistry.PUTRESCINE,       40f, 5);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 30f, 4);
                partRadius(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 60f, 5);
                ShockwaveHandler.spawn(level, pos, 6f);
                ShockwaveDataPacket.sendToNear(level, vec, 6f, 56f);
            }
            case "rats:plague_beast" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,       25f, 4);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 18f, 3);
                partRadius(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 35f, 4);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("rats:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        switch (type) {
            case "rats:plague_shot",
                 "rats:plague_cloud" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,       15f, 3);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 10f, 2);
                partRadius(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 20f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL,        15f, 2);
            }
            case "rats:rat_dragon_fire" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     20f, 2);
                gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 10f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  30f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
            case "rats:thrown_block" -> {
                partRadius(level, pos, ParticulateType.DUST,        25f, 2);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 15f, 2);
                ShockwaveHandler.spawn(level, pos, 4f);
                ShockwaveDataPacket.sendToNear(level, vec, 4f, 36f);
            }
            // Ratlantis projectiles
            case "rats:ratlantean_automaton_beam",
                 "rats:laser_beam",
                 "rats:rattling_gun_bullet",
                 "rats:ratlantis_arrow" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 15f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 18f, 2);
                ShockwaveHandler.spawn(level, pos, 2f);
                ShockwaveDataPacket.sendToNear(level, vec, 2f, 18f);
            }
            case "rats:cheese_cannonball" -> {
                partRadius(level, pos, ParticulateType.DUST,       20f, 2);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 12f, 2);
                ShockwaveHandler.spawn(level, pos, 4f);
                ShockwaveDataPacket.sendToNear(level, vec, 4f, 36f);
            }
            case "rats:vial_of_sentience" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 12f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 15f, 2);
            }
        }
    }
}
