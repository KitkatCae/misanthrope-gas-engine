package exp.CCnewmods.mge.event;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Listens to Forge world events and mutates atmosphere block gas and particulate
 * compositions accordingly.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldEventHandler {

    private WorldEventHandler() {}

    // ── Block placement ───────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockState placed = event.getPlacedBlock();
        if (placed.getBlock() instanceof FireBlock || placed.getBlock() instanceof CampfireBlock) {
            mutateFire(level, event.getPos(), 10f);
        }

        Mge.getScheduler(level).enqueueWithNeighbours(event.getPos());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // Breaking blocks creates dust particulates
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AtmosphereBlockEntity atm) {
            var parts = atm.getParticulates();
            parts.add(ParticulateType.DUST, 20f);
            atm.setParticulates(parts);
        }
        Mge.getScheduler(level).enqueueWithNeighbours(pos);
    }

    // ── Explosions ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        List<BlockPos> affected = event.getAffectedBlocks();
        for (BlockPos pos : affected) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof AtmosphereBlockEntity atm)) continue;

            GasComposition comp = atm.getComposition();
            float o2 = comp.get(GasRegistry.OXYGEN);
            float consumed = Math.min(o2, 50f);
            comp.add(GasRegistry.OXYGEN,         -consumed);
            comp.add(GasRegistry.CARBON_DIOXIDE,  consumed * 0.6f);
            comp.add(GasRegistry.CARBON_MONOXIDE, consumed * 0.2f);
            atm.setComposition(comp);

            // Explosion produces heavy smoke and soot
            var parts = atm.getParticulates();
            parts.add(ParticulateType.SMOKE_AEROSOL, 120f);
            parts.add(ParticulateType.SOOT,           40f);
            parts.add(ParticulateType.DUST,            30f);
            atm.setParticulates(parts);

            Mge.getScheduler(level).enqueue(pos);
        }
    }

    // ── Nether portal ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onNetherPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        for (int dy = 0; dy <= 4; dy++) injectNetherGases(level, pos.above(dy));
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    /** Fire combustion: consumes the dominant oxidiser, produces appropriate products. */
    public static void mutateFire(ServerLevel level, BlockPos pos, float intensity) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        GasComposition comp = atm.getComposition();
        float totalPressure = Math.max(1f, comp.totalPressure());
        float fluorineFraction = comp.get(GasRegistry.FLUORINE) / totalPressure;

        if (fluorineFraction > 0.1f) {
            // Fluorine atmosphere — combustion produces HF instead of CO₂
            float consumed = Math.min(comp.get(GasRegistry.FLUORINE), intensity * 2f);
            comp.add(GasRegistry.FLUORINE,         -consumed);
            comp.add(GasRegistry.HYDROGEN_FLUORIDE, consumed * 0.8f);
            comp.add(GasRegistry.CARBON_DIOXIDE,    intensity * 0.05f);
        } else {
            float o2 = comp.get(GasRegistry.OXYGEN);
            float consumed = Math.min(o2, intensity);
            comp.add(GasRegistry.OXYGEN,         -consumed);
            comp.add(GasRegistry.CARBON_DIOXIDE,  consumed * 0.7f);
            comp.add(GasRegistry.CARBON_MONOXIDE, consumed * 0.15f);
        }
        atm.setComposition(comp);

        var parts = atm.getParticulates();
        parts.add(ParticulateType.SOOT,        consumed * 1.5f);
        parts.add(ParticulateType.SMOKE_AEROSOL, consumed * 3f);
        atm.setParticulates(parts);

        Mge.getScheduler(level).enqueue(pos);
    }

    /** Inject Nether-characteristic gases and soul dust near a portal. */
    public static void injectNetherGases(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        GasComposition comp = atm.getComposition();
        comp.add(GasRegistry.SULFUR_DIOXIDE,   15f);
        comp.add(GasRegistry.BLAZE_FUME,        8f);
        comp.add(GasRegistry.SOUL_SMOKE,        5f);
        comp.add(GasRegistry.CARBON_MONOXIDE,  10f);
        atm.setComposition(comp);

        var parts = atm.getParticulates();
        parts.add(ParticulateType.SOUL_DUST,         8f);
        parts.add(ParticulateType.NETHER_QUARTZ_DUST, 5f);
        atm.setParticulates(parts);

        Mge.getScheduler(level).enqueue(pos);
    }

    /** Water vapour injection (called by compat layers on lava/water contact, etc.). */
    public static void injectWaterVapour(ServerLevel level, BlockPos pos, float mbar) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;
        atm.getComposition().add(GasRegistry.WATER_VAPOR, mbar);
        atm.setComposition(atm.getComposition());
        Mge.getScheduler(level).enqueue(pos);
    }

    /** General gas injection — for compat layers and future extensions. */
    public static void injectGas(ServerLevel level, BlockPos pos,
                                  exp.CCnewmods.mge.gas.Gas gas, float mbar) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;
        atm.getComposition().add(gas, mbar);
        atm.setComposition(atm.getComposition());
        Mge.getScheduler(level).enqueue(pos);
    }

    /** General particulate injection — for compat layers and future extensions. */
    public static void injectParticulate(ServerLevel level, BlockPos pos,
                                          ParticulateType type, float mgM3) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;
        atm.getParticulates().add(type, mgM3);
        atm.setParticulates(atm.getParticulates());
        Mge.getScheduler(level).enqueue(pos);
    }
}
