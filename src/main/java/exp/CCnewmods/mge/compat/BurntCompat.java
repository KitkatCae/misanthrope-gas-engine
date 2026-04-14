package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.event.WorldEventHandler;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Burnt compat.
 *
 * Burnt adds persistent fire blocks (BlazingLog, BlazingWood, etc.) and a
 * vision-obscuring smoke fog system. MGE replaces Burnt's fog system by routing
 * all Burnt fire emissions through the atmosphere composition. Smoke from Burnt
 * fires injects SMOKE_AEROSOL and SOOT into the local atmosphere block, which
 * then drifts with wind via the normal MGE diffusion system — producing the same
 * large-area smoky fog effect Burnt creates, but physically tied to wind direction.
 *
 * Block placement of any Burnt fire block triggers a heavy smoke/soot injection.
 * Neighbour notify events (Burnt's own fire spread) trigger lighter injections.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BurntCompat {

    public static final String BURNT_MODID = "burnt";
    private static boolean loaded = false;

    // Burnt fire block class name prefixes — checked by simple instanceof against
    // the block's class hierarchy. We use reflection-safe class name matching
    // since we don't want a hard compile dependency on Burnt.
    private static final String BURNT_PACKAGE = "net.pixelbank.burnt.block";

    private BurntCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(BURNT_MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Burnt detected — fire block atmosphere emissions active. " +
                "MGE fog system replaces Burnt's smoke fog.");
    }

    public static boolean isLoaded() { return loaded; }

    // -------------------------------------------------------------------------
    // Block place — Burnt fire blocks placed by world events
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (!isBurntFireBlock(event.getPlacedBlock())) return;

        BlockPos pos = event.getPos();
        // Heavy initial smoke burst when a Burnt fire block is placed/ignites
        injectBurntFireSmoke(level, pos, 80f, 40f);
        WorldEventHandler.mutateFire(level, pos.above(), 15f);
    }

    // -------------------------------------------------------------------------
    // Neighbour notify — Burnt fire spreading to adjacent blocks
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNeighbourNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (!isBurntFireBlock(level.getBlockState(event.getPos()))) return;

        // Lighter continuous emission from burning neighbours
        injectBurntFireSmoke(level, event.getPos(), 15f, 8f);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isBurntFireBlock(BlockState state) {
        return state.getBlock().getClass().getName().startsWith(BURNT_PACKAGE);
    }

    static void injectBurntFireSmoke(ServerLevel level, BlockPos pos,
                                      float smokeAmount, float sootAmount) {
        // Inject into the block at pos and the one above — smoke rises
        for (BlockPos target : new BlockPos[]{ pos, pos.above(), pos.above(2) }) {
            if (!level.isLoaded(target)) continue;
            BlockEntity be = level.getBlockEntity(target);
            if (!(be instanceof AtmosphereBlockEntity atm)) continue;

            var comp = atm.getComposition();
            float o2 = comp.get(GasRegistry.OXYGEN);
            float consumed = Math.min(o2, smokeAmount * 0.05f);
            comp.add(GasRegistry.OXYGEN,         -consumed);
            comp.add(GasRegistry.CARBON_DIOXIDE,  consumed * 0.8f);
            comp.add(GasRegistry.CARBON_MONOXIDE, consumed * 0.15f);
            atm.setComposition(comp);

            var parts = atm.getParticulates();
            // Distribute smoke across height — most at pos, less higher up
            float heightFactor = 1.0f / (target.getY() - pos.getY() + 1);
            parts.add(ParticulateType.SMOKE_AEROSOL, smokeAmount * heightFactor);
            parts.add(ParticulateType.SOOT,          sootAmount  * heightFactor);
            parts.add(ParticulateType.ASH_CLOUD,     sootAmount * 0.3f * heightFactor);
            atm.setParticulates(parts);

            Mge.getScheduler(level).enqueue(target);
        }
    }
}
