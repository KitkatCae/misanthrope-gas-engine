package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.util.ChunkIterator;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.drmangotea.tfmg.content.machinery.misc.exhaust.ExhaustBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.smokestack.SmokestackBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace.BlastFurnaceOutputBlockEntity;

import java.lang.reflect.Field;

/**
 * TFMG compat — hooks exhaust, smokestack, and blast furnace block entities.
 * Uses reflection to access package-private fields smokeTimer and isActive.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TfmgCompat {

    public static final String TFMG_MODID = "tfmg";
    private static boolean loaded = false;
    private static final int SCAN_INTERVAL = 10;
    private static int tick = 0;

    // Reflected fields — resolved once at load time
    private static Field exhaustSmokeTimer;
    private static Field smokestackSmokeTimer;
    private static Field blastFurnaceIsActive;

    private TfmgCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(TFMG_MODID)) return;
        try {
            exhaustSmokeTimer = ExhaustBlockEntity.class.getDeclaredField("smokeTimer");
            exhaustSmokeTimer.setAccessible(true);

            smokestackSmokeTimer = SmokestackBlockEntity.class.getDeclaredField("smokeTimer");
            smokestackSmokeTimer.setAccessible(true);

            blastFurnaceIsActive = BlastFurnaceOutputBlockEntity.class.getDeclaredField("isActive");
            blastFurnaceIsActive.setAccessible(true);
        } catch (Exception e) {
            Mge.LOGGER.warn("[MGE] TFMG: could not reflect fields — {}", e.getMessage());
            return;
        }
        loaded = true;
        Mge.LOGGER.info("[MGE] TFMG detected — furnace/exhaust atmosphere emissions active.");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!loaded || event.phase != TickEvent.Phase.END) return;
        if (++tick % SCAN_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;
        for (ServerLevel level : event.getServer().getAllLevels()) tickTfmg(level);
    }

    private static void tickTfmg(ServerLevel level) {
        ChunkIterator.forEach(level, holder -> {
            var chunk = holder.getTickingChunk();
            if (chunk == null) return;
            chunk.getBlockEntities().forEach((pos, be) -> {
                if (be.isRemoved()) return;
                try {
                    if (be instanceof ExhaustBlockEntity exhaust) {
                        if ((int) exhaustSmokeTimer.get(exhaust) <= 0) return;
                        float fill = exhaust.tankInventory.getFluidAmount() /
                                     (float) Math.max(1, exhaust.tankInventory.getCapacity());
                        emitExhaust(level, pos.above(), fill * 0.8f, true);

                    } else if (be instanceof SmokestackBlockEntity smokestack) {
                        if ((int) smokestackSmokeTimer.get(smokestack) <= 0) return;
                        float fill = smokestack.tankInventory.getFluidAmount() /
                                     (float) Math.max(1, smokestack.tankInventory.getCapacity());
                        emitExhaust(level, pos.above(), fill * 0.6f, false);

                    } else if (be instanceof BlastFurnaceOutputBlockEntity blastFurnace) {
                        if (!(boolean) blastFurnaceIsActive.get(blastFurnace)) return;
                        emitBlastFurnace(level, pos.above());
                    }
                } catch (Exception e) { /* skip silently */ }
            });
        });
    }

    private static void emitExhaust(ServerLevel level, BlockPos pos,
                                     float scale, boolean intense) {
        if (!level.isLoaded(pos)) return;
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.CARBON_DIOXIDE,  scale * (intense ? 8f : 5f));
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.CARBON_MONOXIDE, scale * (intense ? 3f : 1.5f));
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.SULFUR_DIOXIDE,  scale * (intense ? 2f : 1f));
        float o2 = GridAtmosphereCompat.getGas(level, pos, GasRegistry.OXYGEN);
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.OXYGEN, -Math.min(o2, scale * 4f));
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SMOKE_AEROSOL, scale * (intense ? 20f : 12f));
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SOOT,          scale * (intense ? 8f : 4f));
        EnvironmentGrid.enqueue(level, pos);
    }

    private static void emitBlastFurnace(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        float o2 = GridAtmosphereCompat.getGas(level, pos, GasRegistry.OXYGEN);
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.OXYGEN,         -Math.min(o2, 15f));
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.CARBON_DIOXIDE,  12f);
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.CARBON_MONOXIDE,  8f);
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.SULFUR_DIOXIDE,   3f);
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SOOT,          15f);
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SMOKE_AEROSOL, 25f);
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.ASH_CLOUD,     10f);
        EnvironmentGrid.enqueue(level, pos);
    }
}
