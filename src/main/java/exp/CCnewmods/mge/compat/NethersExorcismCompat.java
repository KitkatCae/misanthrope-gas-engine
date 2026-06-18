package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Nether's Exorcism Reborn compat (modid: nethers_exorcism_reborn).
 *
 * Entity IDs confirmed from loot tables:
 * <ul>
 *   <li><b>brogg</b> — volcanic demon boss: VOLCANIC_FUMES + VOLCANIC_HCL_PLUME +
 *       MAGMATIC_CO + SO₂ + heavy O₂ drain. Death: pyroclastic burst + shockwave.</li>
 *   <li><b>indigo_scyphozoa</b> — deep-nether jellyfish: WATER_VAPOR + H₂S.
 *       Like the Better Nether hydrogen jellyfish but acid-based, not flammable.</li>
 *   <li><b>strampler</b> — heavy nether creature: BLAZE_FUME + DUST from footsteps.</li>
 *   <li><b>basalt_crab_2</b> — lives in basalt columns: VOLCANIC_FUMES + DUST
 *       (basalt particle emission).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NethersExorcismCompat {

    public static final String MODID = "nethers_exorcism_reborn";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private NethersExorcismCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Nether's Exorcism Reborn detected — nether mob emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("nethers_exorcism_reborn:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "nethers_exorcism_reborn:brogg" -> {
                // Volcanic demon — pyroclastic output
                gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,     20f, 4);
                gasRadius(level, pos, GasRegistry.VOLCANIC_HCL_PLUME, 10f, 3);
                gasRadius(level, pos, GasRegistry.MAGMATIC_CO2,         8f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,     12f, 3);
                drainRadius(level, pos, GasRegistry.OXYGEN, 15f, 3);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  30f, 3);
            }
            case "nethers_exorcism_reborn:indigo_scyphozoa" -> {
                // Deep-nether acid jellyfish — water + sulfide off-gas
                for (int dy = 1; dy <= 3; dy++) {
                    gas(level, pos.above(dy), GasRegistry.WATER_VAPOR,    6f / dy);
                    gas(level, pos.above(dy), GasRegistry.HYDROGEN_SULFIDE, 4f / dy);
                }
            }
            case "nethers_exorcism_reborn:strampler" -> {
                gas(level, pos, GasRegistry.BLAZE_FUME, 8f);
                part(level, pos, ParticulateType.DUST,   15f);
                part(level, pos, ParticulateType.ASH_CLOUD, 6f);
            }
            case "nethers_exorcism_reborn:basalt_crab_2" -> {
                gas(level, pos, GasRegistry.VOLCANIC_FUMES, 6f);
                part(level, pos, ParticulateType.DUST,       10f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("nethers_exorcism_reborn:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        if (type.equals("nethers_exorcism_reborn:brogg")) {
            // Pyroclastic collapse
            gasRadius(level, pos, GasRegistry.VOLCANIC_FUMES,     80f, 8);
            gasRadius(level, pos, GasRegistry.VOLCANIC_HCL_PLUME, 40f, 6);
            gasRadius(level, pos, GasRegistry.MAGMATIC_CO2,        30f, 5);
            gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,     50f, 6);
            drainRadius(level, pos, GasRegistry.OXYGEN, 50f, 6);
            partRadius(level, pos, ParticulateType.ASH_CLOUD,  150f, 7);
            partRadius(level, pos, ParticulateType.DUST,        80f, 6);
            ShockwaveHandler.spawn(level, pos, 14f);
        }
    }
}
