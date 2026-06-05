package exp.CCnewmods.mge.event;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.compat.BetterNetherEndCompat;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MobDeathAtmosphereHandler {

    private MobDeathAtmosphereHandler() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (event.getEntity().level().isClientSide()) return;
        ServerLevel level = (ServerLevel) event.getEntity().level();
        String type = event.getEntity().getType().toString();
        BlockPos pos = event.getEntity().blockPosition();

        if (type.contains("hydrogen_jellyfish"))
            BetterNetherEndCompat.onHydrogenJellyfishDeath(level, pos);

        if (type.contains("spectre") || type.contains("ghost")
                || type.contains("soul_vulture") || type.contains("wisp")
                || (type.contains("spirit") && !type.contains("frost"))) {
            gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 20f, 2);
            partRadius(level, pos, ParticulateType.SOUL_WISPS, 40f, 2);
        }
        if (type.contains("wither_skeleton")) {
            gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 15f, 1);
        }
        if (type.contains("sunbird")) {
            gasRadius(level, pos, GasRegistry.OZONE, 30f, 3);
            gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 20f, 3);
            drainRadius(level, pos, GasRegistry.OXYGEN, 40f, 3);
            partRadius(level, pos, ParticulateType.ASH_CLOUD, 80f, 3);
        }
        if (type.contains("firedragon") || (type.contains("iceandfire") && type.contains("dragon"))) {
            gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 50f, 4);
            gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 20f, 4);
            partRadius(level, pos, ParticulateType.ASH_CLOUD, 100f, 4);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String blockId = event.getState().getBlock().getDescriptionId();
        if (blockId.contains("taint") || blockId.contains("wither_taint")) {
            BlockPos pos = event.getPos();
            gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 12f, 1);
            partRadius(level, pos, ParticulateType.SOUL_WISPS, 20f, 1);
        }
    }
}
