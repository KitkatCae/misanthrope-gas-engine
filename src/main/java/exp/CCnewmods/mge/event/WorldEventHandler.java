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

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AtmosphereBlockEntity atm) {
            var parts = atm.getParticulates();
            parts.add(ParticulateType.DUST, 20f);

            // Coal ore and coal blocks emit coal dust when mined
            var blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                    .getKey(event.getState().getBlock());
            if (blockId != null) {
                String path = blockId.getPath();
                if (path.contains("coal") && (path.contains("ore") || path.equals("coal_block")
                        || path.equals("coal"))) {
                    parts.add(ParticulateType.COAL_DUST, 80f);
                    // Also inject above
                    BlockEntity above = level.getBlockEntity(pos.above());
                    if (above instanceof AtmosphereBlockEntity aboveAtm) {
                        var ap = aboveAtm.getParticulates();
                        ap.add(ParticulateType.COAL_DUST, 40f);
                        aboveAtm.setParticulates(ap);
                        Mge.getScheduler(level).enqueue(pos.above());
                    }
                }
            }
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
        if (affected.isEmpty()) return;

        net.minecraft.world.phys.Vec3 centre = event.getExplosion().getPosition();
        BlockPos centrePos = BlockPos.containing(centre);
        float baseRadius = event.getExplosion().radius;

        // Check atmosphere at centre for amplification / vacuum suppression
        BlockEntity centreBE = level.getBlockEntity(centrePos);
        if (centreBE instanceof AtmosphereBlockEntity centreAtm) {
            float pressure = centreAtm.getComposition().totalPressure();

            // Vacuum: suppress explosion (no medium to carry blast)
            if (pressure < exp.CCnewmods.mge.vacuum.VacuumHandler.VACUUM_THRESHOLD_MBAR) {
                affected.subList(affected.size() / 2, affected.size()).clear();
            } else {
                // Atmospheric amplification from flammable gas
                float amp = checkAtmosphericAmplification(centreAtm);
                if (amp > 1f) {
                    exp.CCnewmods.mge.shockwave.ShockwaveHandler.spawn(
                            level, centrePos, baseRadius * amp * 2f);
                    exp.CCnewmods.mge.shockwave.ShockwaveDataPacket.sendToNear(
                            level, centre, baseRadius * amp * 2f, baseRadius * 32f);
                }
            }
        }

        // Mutate atmosphere in all affected blocks
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

            var parts = atm.getParticulates();
            parts.add(ParticulateType.SMOKE_AEROSOL, 120f);
            parts.add(ParticulateType.SOOT,           40f);
            parts.add(ParticulateType.DUST,            30f);
            atm.setParticulates(parts);
            Mge.getScheduler(level).enqueue(pos);
        }

        // Shockwave from every explosion
        exp.CCnewmods.mge.shockwave.ShockwaveHandler.spawn(level, centrePos, baseRadius * 1.5f);
        exp.CCnewmods.mge.shockwave.ShockwaveDataPacket.sendToNear(
                level, centre, baseRadius * 1.5f, baseRadius * 24f);
    }

    private static float checkAtmosphericAmplification(AtmosphereBlockEntity atm) {
        GasComposition comp = atm.getComposition();
        float total = comp.totalPressure();
        if (total <= 0) return 1f;
        float oxidiser = comp.get(GasRegistry.OXYGEN);
        for (String key : comp.getTag().getAllKeys()) {
            exp.CCnewmods.mge.gas.Gas g = exp.CCnewmods.mge.gas.GasRegistry.get(key).orElse(null);
            if (g != null && g.properties().hasReactivity(exp.CCnewmods.mge.gas.ReactivityFlag.OXIDISER))
                oxidiser += comp.get(key);
        }
        if (oxidiser / total < 0.16f) return 1f;
        float maxAmp = 1f;
        for (String key : comp.getTag().getAllKeys()) {
            exp.CCnewmods.mge.gas.Gas gas = exp.CCnewmods.mge.gas.GasRegistry.get(key).orElse(null);
            if (gas == null || !gas.properties().isFlammable()) continue;
            float frac = comp.get(key) / total;
            if (frac >= gas.properties().lowerExplosiveLimit()
                    && frac <= gas.properties().upperExplosiveLimit()) {
                float amp = 1f + frac * 5f;
                if (amp > maxAmp) maxAmp = amp;
            }
        }
        return maxAmp;
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
        float consumed;

        if (fluorineFraction > 0.1f) {
            // Fluorine atmosphere — combustion produces HF instead of CO₂
            consumed = Math.min(comp.get(GasRegistry.FLUORINE), intensity * 2f);
            comp.add(GasRegistry.FLUORINE,         -consumed);
            comp.add(GasRegistry.HYDROGEN_FLUORIDE, consumed * 0.8f);
            comp.add(GasRegistry.CARBON_DIOXIDE,    intensity * 0.05f);
        } else {
            float o2 = comp.get(GasRegistry.OXYGEN);
            consumed = Math.min(o2, intensity);
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
