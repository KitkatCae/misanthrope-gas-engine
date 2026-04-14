package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.drmangotea.tfmg.content.machinery.misc.exhaust.ExhaustBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.smokestack.SmokestackBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace.BlastFurnaceOutputBlockEntity;

/**
 * TFMG (The Factory Must Grow) compat.
 *
 * Hooks three TFMG block entities:
 *
 * ExhaustBlock / SmokestackBlock — both have a smokeTimer and fluid tank. When
 * smokeTimer > 0 (actively exhausting), inject combustion products above them:
 * CO₂, CO, SO₂, smoke aerosol, soot. Rate scales with tank fill level.
 *
 * BlastFurnaceOutputBlockEntity — isActive flag. When smelting, inject CO₂, CO,
 * SO₂ and consume local O₂ (enriched O₂ in the atmosphere increases heat output
 * via FurnaceEnvironmentSampler from misanthrope_core).
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TfmgCompat {

    public static final String TFMG_MODID = "tfmg";
    private static boolean loaded = false;
    private static final int SCAN_INTERVAL = 10; // every 10 ticks = 0.5s
    private static int tick = 0;

    private TfmgCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(TFMG_MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] TFMG detected — furnace/exhaust atmosphere emissions active.");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!loaded || event.phase != TickEvent.Phase.END) return;
        if (++tick % SCAN_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            tickTfmg(level);
        }
    }

    private static void tickTfmg(ServerLevel level) {
        level.blockEntityList.forEach(be -> {
            if (be.isRemoved()) return;

            if (be instanceof ExhaustBlockEntity exhaust) {
                if (exhaust.smokeTimer <= 0) return;
                float fill = exhaust.tankInventory.getFluidAmount() /
                             (float) Math.max(1, exhaust.tankInventory.getCapacity());
                emitExhaust(level, be.getBlockPos().above(), fill * 0.8f, true);

            } else if (be instanceof SmokestackBlockEntity smokestack) {
                if (smokestack.smokeTimer <= 0) return;
                float fill = smokestack.tankInventory.getFluidAmount() /
                             (float) Math.max(1, smokestack.tankInventory.getCapacity());
                emitExhaust(level, be.getBlockPos().above(), fill * 0.6f, false);

            } else if (be instanceof BlastFurnaceOutputBlockEntity blastFurnace) {
                if (!blastFurnace.isActive) return;
                emitBlastFurnace(level, be.getBlockPos().above());
            }
        });
    }

    /**
     * Emit combustion exhaust above a position.
     * @param intense true for exhaust (direct fluid burn), false for smokestack (diffuse)
     */
    private static void emitExhaust(ServerLevel level, BlockPos pos, float scale, boolean intense) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        var comp = atm.getComposition();
        comp.add(GasRegistry.CARBON_DIOXIDE,  scale * (intense ? 8f : 5f));
        comp.add(GasRegistry.CARBON_MONOXIDE, scale * (intense ? 3f : 1.5f));
        comp.add(GasRegistry.SULFUR_DIOXIDE,  scale * (intense ? 2f : 1f));
        float o2 = comp.get(GasRegistry.OXYGEN);
        comp.add(GasRegistry.OXYGEN, -Math.min(o2, scale * 4f));
        atm.setComposition(comp);

        var parts = atm.getParticulates();
        parts.add(ParticulateType.SMOKE_AEROSOL, scale * (intense ? 20f : 12f));
        parts.add(ParticulateType.SOOT,          scale * (intense ? 8f : 4f));
        atm.setParticulates(parts);

        Mge.getScheduler(level).enqueue(pos);
    }

    private static void emitBlastFurnace(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        var comp = atm.getComposition();
        float o2 = comp.get(GasRegistry.OXYGEN);
        comp.add(GasRegistry.OXYGEN,         -Math.min(o2, 15f));
        comp.add(GasRegistry.CARBON_DIOXIDE,  12f);
        comp.add(GasRegistry.CARBON_MONOXIDE,  8f); // blast furnaces produce significant CO
        comp.add(GasRegistry.SULFUR_DIOXIDE,   3f);
        atm.setComposition(comp);

        var parts = atm.getParticulates();
        parts.add(ParticulateType.SOOT,          15f);
        parts.add(ParticulateType.SMOKE_AEROSOL, 25f);
        parts.add(ParticulateType.ASH_CLOUD,     10f);
        atm.setParticulates(parts);

        Mge.getScheduler(level).enqueue(pos);
    }
}
