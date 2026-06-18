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
 * Bosses of Mass Destruction compat (modid: bosses_of_mass_destruction).
 *
 * Entity IDs confirmed from BMDEntities.class:
 * <ul>
 *   <li><b>lich</b> — tick: SOUL_ESSENCE + WITHER_MIASMA necrotic aura.
 *       Comet projectile impact: IONISED_AIR burst + shockwave.</li>
 *   <li><b>void_blossom</b> — tick: ENDER_PARTICULATE + O₂ drain.
 *       Petal blade projectile: ENDER_PARTICULATE burst.</li>
 *   <li><b>gauntlet</b> — tick: BLAZE_FUME + O₂ drain (superheated metal).
 *       Missile/laser projectile: heat flash BLAZE_FUME + shockwave.</li>
 *   <li><b>obsidilith</b> — tick: WITHER_MIASMA + SOUL_SMOKE heavy field.
 *       Death: massive burst + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossesMassDestructionCompat {

    public static final String MODID = "bosses_of_mass_destruction";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private BossesMassDestructionCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Bosses of Mass Destruction detected — boss atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("bosses_of_mass_destruction:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "bosses_of_mass_destruction:lich" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,  14f);
                gas(level, pos, GasRegistry.WITHER_MIASMA, 10f);
                part(level, pos, ParticulateType.SOUL_WISPS, 18f);
                drain(level, pos, GasRegistry.OXYGEN, 8f);
            }
            case "bosses_of_mass_destruction:void_blossom" -> {
                gas(level, pos, GasRegistry.ENDER_PARTICULATE, 12f);
                drain(level, pos, GasRegistry.OXYGEN, 10f);
                drain(level, pos, GasRegistry.NITROGEN, 5f);
            }
            case "bosses_of_mass_destruction:gauntlet" -> {
                gas(level, pos, GasRegistry.BLAZE_FUME, 12f);
                gas(level, pos, GasRegistry.SULFUR_DIOXIDE, 5f);
                drain(level, pos, GasRegistry.OXYGEN, 8f);
            }
            case "bosses_of_mass_destruction:obsidilith" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 15f, 4);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,    10f, 3);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 20f, 3);
                drainRadius(level, pos, GasRegistry.OXYGEN, 12f, 3);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("bosses_of_mass_destruction:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        switch (type) {
            case "bosses_of_mass_destruction:lich" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  60f, 6);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 40f, 5);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 80f, 6);
                ShockwaveHandler.spawn(level, pos, 10f);
            }
            case "bosses_of_mass_destruction:obsidilith" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 80f, 8);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,    50f, 6);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 100f, 7);
                ShockwaveHandler.spawn(level, pos, 14f);
            }
            case "bosses_of_mass_destruction:void_blossom" -> {
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 50f, 5);
                ShockwaveHandler.spawn(level, pos, 8f);
            }
            case "bosses_of_mass_destruction:gauntlet" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME, 40f, 5);
                partRadius(level, pos, ParticulateType.ASH_CLOUD, 80f, 4);
                ShockwaveHandler.spawn(level, pos, 10f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("bosses_of_mass_destruction:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        // Confirmed projectile IDs from BMDEntities: comet, missile, petal_blade, spore_ball
        switch (type) {
            case "bosses_of_mass_destruction:comet" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR,  20f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,         8f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 25f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            case "bosses_of_mass_destruction:missile" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,   30f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 12f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD, 60f, 3);
                ShockwaveHandler.spawn(level, pos, 6f);
            }
            case "bosses_of_mass_destruction:petal_blade" -> {
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "bosses_of_mass_destruction:spore_ball" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 15f, 2);
                partRadius(level, pos, ParticulateType.SPORE_CLUSTER, 40f, 3);
            }
        }
    }
}
