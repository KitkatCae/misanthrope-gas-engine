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
 * Opposing Force compat (modid: opposing_force).
 *
 * All entity IDs confirmed from loot tables and entity class names
 * (snake_case derived: DizerLaserEntity → dicer_laser, ElectricChargeEntity →
 * electric_charge, WhizzBombEntity → whizz_bomb, etc.).
 *
 * <ul>
 *   <li><b>volt</b> — living electric organism: continuous IONISED_AIR + OZONE aura.</li>
 *   <li><b>skyvern / skyvern_segment</b> — aerial electric serpent: IONISED_AIR + OZONE.</li>
 *   <li><b>trembler</b> — seismic creature: DUST + GRAVEL_DUST + shockwave every tick.</li>
 *   <li><b>fire_slime</b> — BLAZE_FUME + SO₂ (pyrotheum-adjacent fire slime).</li>
 *   <li><b>guzzler</b> — biological waste processor: H₂S + ORGANIC_AEROSOL.</li>
 *   <li><b>frowzy</b> — necrotic decay creature: CADAVERINE + H₂S + ORGANIC_AEROSOL.</li>
 *   <li><b>tart</b> — acid spitter: SHULKER_ACID_MIST passive leak.</li>
 *   <li><b>dicer</b> — laser-armed construct: IONISED_AIR from weapon discharge.</li>
 *   <li><b>terror / terror_legs</b> — multi-part horror: SOUL_ESSENCE + ORGANIC_AEROSOL.</li>
 *   <li><b>hanging_spider / umber_spider</b> — ORGANIC_AEROSOL + H₂S (giant spiders).</li>
 *   <li><b>rambler / slug / ladybug</b> — mild ORGANIC_AEROSOL (insectoid fauna).</li>
 *   <li><b>Projectiles</b>:
 *     dicer_laser → narrow IONISED_AIR beam;
 *     electric_charge / electric_explosion → IONISED_AIR + OZONE + shockwave;
 *     laser_bolt → IONISED_AIR;
 *     whizz / whizz_bomb → IONISED_AIR concussive burst;
 *     fire_bomb → BLAZE_FUME + SO₂ + shockwave;
 *     kinetic_bomb → massive DUST + shockwave;
 *     lightning_bomb → IONISED_AIR + NITRIC_OXIDE + shockwave;
 *     gloom_toxin → H₂S + ORGANIC_AEROSOL blob;
 *     acid_glob / acid_spit → SHULKER_ACID_MIST burst;
 *     skyvern_bolt → IONISED_AIR + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OpposingForceCompat {

    public static final String MODID = "opposing_force";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL  = 20;
    private static final int TREMBLER_TICK  = 10; // fast seismic tick
    private static int tick = 0;

    private OpposingForceCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Opposing Force detected — mob atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("opposing_force:")) return;

        ++tick;
        BlockPos pos = entity.blockPosition();
        Vec3 vec = entity.position();

        // Trembler on its own fast tick
        if (type.equals("opposing_force:trembler")) {
            if (tick % TREMBLER_TICK != 0) return;
            partRadius(level, pos, ParticulateType.DUST,        30f, 4);
            partRadius(level, pos, ParticulateType.GRAVEL_DUST, 20f, 3);
            ShockwaveHandler.spawn(level, pos, 3f);
            ShockwaveDataPacket.sendToNear(level, vec, 3f, 32f);
            return;
        }

        if (tick % TICK_INTERVAL != 0) return;

        switch (type) {
            case "opposing_force:volt" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 14f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,         6f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 12f, 3);
            }
            case "opposing_force:skyvern",
                 "opposing_force:skyvern_segment" -> {
                gas(level, pos, GasRegistry.IONISED_AIR, 10f);
                gas(level, pos, GasRegistry.OZONE,         5f);
                part(level, pos, ParticulateType.IONISED_PARTICLES, 8f);
            }
            case "opposing_force:fire_slime" -> {
                gas(level, pos, GasRegistry.BLAZE_FUME,     10f);
                gas(level, pos, GasRegistry.SULFUR_DIOXIDE,  5f);
                drain(level, pos, GasRegistry.OXYGEN, 6f);
            }
            case "opposing_force:guzzler" -> {
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 6f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 8f);
            }
            case "opposing_force:frowzy" -> {
                gas(level, pos, GasRegistry.CADAVERINE,       8f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 5f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
            }
            case "opposing_force:tart" ->
                gas(level, pos, GasRegistry.SHULKER_ACID_MIST, 5f);
            case "opposing_force:dicer" -> {
                gas(level, pos, GasRegistry.IONISED_AIR, 8f);
                part(level, pos, ParticulateType.IONISED_PARTICLES, 6f);
            }
            case "opposing_force:terror",
                 "opposing_force:terror_legs" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 5f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
            }
            case "opposing_force:hanging_spider",
                 "opposing_force:umber_spider" -> {
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 3f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 4f);
            }
            case "opposing_force:rambler",
                 "opposing_force:slug",
                 "opposing_force:ladybug" ->
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 3f);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("opposing_force:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        switch (type) {
            case "opposing_force:volt" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 40f, 5);
                gasRadius(level, pos, GasRegistry.OZONE,       18f, 4);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 50f, 5);
                ShockwaveHandler.spawn(level, pos, 7f);
                ShockwaveDataPacket.sendToNear(level, vec, 7f, 60f);
            }
            case "opposing_force:trembler" -> {
                partRadius(level, pos, ParticulateType.DUST,        100f, 7);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST,  70f, 6);
                ShockwaveHandler.spawn(level, pos, 12f);
                ShockwaveDataPacket.sendToNear(level, vec, 12f, 100f);
            }
            case "opposing_force:frowzy" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,       25f, 4);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 18f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 35f, 4);
            }
            case "opposing_force:terror" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 20f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 25f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("opposing_force:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        switch (type) {
            // Laser / electric projectiles
            case "opposing_force:dicer_laser" -> {
                // Narrow cutting beam — intense ionisation along path, small footprint
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 20f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 22f, 2);
                ShockwaveHandler.spawn(level, pos, 2f);
                ShockwaveDataPacket.sendToNear(level, vec, 2f, 18f);
            }
            case "opposing_force:electric_charge",
                 "opposing_force:electric_explosion" -> {
                // Full electrical burst — strips N₂/O₂ into ionised products
                var comp = GridAtmosphereCompat.getComposition(level, pos);
                float n2 = comp.get(GasRegistry.NITROGEN);
                float o2 = comp.get(GasRegistry.OXYGEN);
                float reacted = Math.min(n2, o2) * 0.30f;
                comp.add(GasRegistry.NITROGEN,     -reacted);
                comp.add(GasRegistry.OXYGEN,       -reacted);
                comp.add(GasRegistry.NITRIC_OXIDE,  reacted * 1.6f);
                comp.add(GasRegistry.IONISED_AIR,  (n2 + o2) * 0.12f);
                comp.add(GasRegistry.OZONE,          o2 * 0.06f);
                GridAtmosphereCompat.setComposition(level, pos, comp);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 30f, 3);
                ShockwaveHandler.spawn(level, pos, 6f);
                ShockwaveDataPacket.sendToNear(level, vec, 6f, 56f);
            }
            case "opposing_force:laser_bolt" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 16f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 18f, 2);
                ShockwaveHandler.spawn(level, pos, 2f);
                ShockwaveDataPacket.sendToNear(level, vec, 2f, 18f);
            }
            case "opposing_force:skyvern_bolt" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 20f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,        8f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 24f, 3);
                ShockwaveHandler.spawn(level, pos, 4f);
                ShockwaveDataPacket.sendToNear(level, vec, 4f, 40f);
            }
            // Explosive projectiles
            case "opposing_force:whizz",
                 "opposing_force:whizz_bomb" -> {
                // Sonic/kinetic concussion — ionised air burst
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 22f, 3);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 25f, 3);
                ShockwaveHandler.spawn(level, pos, 6f);
                ShockwaveDataPacket.sendToNear(level, vec, 6f, 56f);
            }
            case "opposing_force:fire_bomb" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     30f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 12f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  60f, 3);
                ShockwaveHandler.spawn(level, pos, 6f);
                ShockwaveDataPacket.sendToNear(level, vec, 6f, 56f);
            }
            case "opposing_force:kinetic_bomb" -> {
                // Pure physical impact — max dust, max shockwave
                partRadius(level, pos, ParticulateType.DUST,        80f, 5);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 55f, 4);
                ShockwaveHandler.spawn(level, pos, 10f);
                ShockwaveDataPacket.sendToNear(level, vec, 10f, 90f);
            }
            case "opposing_force:lightning_bomb" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR,   35f, 4);
                gasRadius(level, pos, GasRegistry.OZONE,          14f, 3);
                gasRadius(level, pos, GasRegistry.NITRIC_OXIDE,    7f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 40f, 4);
                ShockwaveHandler.spawn(level, pos, 8f);
                ShockwaveDataPacket.sendToNear(level, vec, 8f, 72f);
            }
            // Biological / chemical projectiles
            case "opposing_force:gloom_toxin" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 18f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 25f, 3);
            }
            case "opposing_force:acid_glob",
                 "opposing_force:acid_spit" -> {
                gasRadius(level, pos, GasRegistry.SHULKER_ACID_MIST, 20f, 3);
                ShockwaveHandler.spawn(level, pos, 2f);
                ShockwaveDataPacket.sendToNear(level, vec, 2f, 18f);
            }
        }
    }
}
