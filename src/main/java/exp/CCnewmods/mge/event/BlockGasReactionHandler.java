package exp.CCnewmods.mge.event;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.util.ChunkIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Block-gas chemical reactions.
 *
 * Reactions implemented:
 *   Lava + F₂   → HF + SiF₄ (consumes F₂, injects HF + SO₂)
 *   Lava + CH₄  → combustion (consumes CH₄ + O₂, injects CO₂ + CO)
 *   Lava         → continuous SO₂ + CO₂ off-gassing
 *   Water + SO₂  → dissolved (removes SO₂, slight humidity rise)
 *   Water + Cl₂  → HCl (consumes Cl₂, injects HCl)
 *   Water + NH₃  → dissolves (removes NH₃)
 *   Water + C₂H₂ → slight dissolution
 *   Ice/Snow    → condenses water vapour
 *   Stone/Granite → trace radon off-gassing
 *   Magma       → SO₂ + CO₂
 *
 * Periodic scan (40 ticks, 25% of chunks) for slow geological reactions.
 * NeighbourNotify event for immediate reactions on block change.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlockGasReactionHandler {

    private static final int SCAN_INTERVAL = 40;
    private static int tick = 0;

    private BlockGasReactionHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tick % SCAN_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ChunkIterator.forEach(level, holder -> {
                var chunk = holder.getTickingChunk();
                if (chunk == null || level.getRandom().nextInt(4) != 0) return;
                chunk.getBlockEntities().forEach((pos, be) -> {
                    if (!(be instanceof AtmosphereBlockEntity atm)) return;
                    reactWithNeighbours(level, pos, atm, 0.05f);
                });
            });
        }
    }

    @SubscribeEvent
    public static void onNeighbourNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!MgeConfig.enableGasEffects || event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState changed = level.getBlockState(pos);
        for (BlockPos n : new BlockPos[]{
                pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (!level.isLoaded(n)) continue;
            BlockEntity be = level.getBlockEntity(n);
            if (!(be instanceof AtmosphereBlockEntity atm)) continue;
            reactBlock(level, pos, changed, n, atm, 1.0f);
        }
    }

    private static void reactWithNeighbours(ServerLevel level, BlockPos atmPos,
                                             AtmosphereBlockEntity atm, float rate) {
        for (BlockPos n : new BlockPos[]{
                atmPos.above(), atmPos.below(),
                atmPos.north(), atmPos.south(), atmPos.east(), atmPos.west()}) {
            if (!level.isLoaded(n)) continue;
            reactBlock(level, n, level.getBlockState(n), atmPos, atm, rate);
        }
    }

    private static void reactBlock(ServerLevel level, BlockPos blockPos, BlockState state,
                                    BlockPos atmPos, AtmosphereBlockEntity atm, float rate) {
        var comp = atm.getComposition();
        boolean changed = false;

        if (state.is(Blocks.LAVA)) {
            // F₂ + lava silica → HF
            float f2 = comp.get(GasRegistry.FLUORINE);
            if (f2 > 0.5f) {
                float c = Math.min(f2, rate * 8f);
                comp.add(GasRegistry.FLUORINE, -c);
                comp.add(GasRegistry.HYDROGEN_FLUORIDE, c * 0.8f);
                comp.add(GasRegistry.SULFUR_DIOXIDE, rate * 2f);
                atm.getParticulates().add(ParticulateType.SOOT, rate * 5f);
                changed = true;
            }
            // CH₄ ignites near lava
            float ch4 = comp.get(GasRegistry.METHANE);
            if (ch4 > 10f) {
                float c = Math.min(ch4, rate * 20f);
                comp.add(GasRegistry.METHANE, -c);
                float o2 = comp.get(GasRegistry.OXYGEN);
                comp.add(GasRegistry.OXYGEN, -Math.min(o2, c * 2f));
                comp.add(GasRegistry.CARBON_DIOXIDE, c * 0.8f);
                comp.add(GasRegistry.CARBON_MONOXIDE, c * 0.1f);
                changed = true;
            }
            // Baseline volcanic off-gassing
            comp.add(GasRegistry.SULFUR_DIOXIDE, rate * 0.5f);
            comp.add(GasRegistry.CARBON_DIOXIDE, rate * 0.3f);
            changed = true;
        }

        if (state.is(Blocks.WATER)) {
            float so2 = comp.get(GasRegistry.SULFUR_DIOXIDE);
            if (so2 > 1f) {
                float d = Math.min(so2, rate * 2f);
                comp.add(GasRegistry.SULFUR_DIOXIDE, -d);
                comp.add(GasRegistry.WATER_VAPOR, d * 0.3f);
                changed = true;
            }
            float cl2 = comp.get(GasRegistry.CHLORINE);
            if (cl2 > 0.5f) {
                float d = Math.min(cl2, rate * 3f);
                comp.add(GasRegistry.CHLORINE, -d);
                comp.add(GasRegistry.HYDROGEN_CHLORIDE, d * 0.9f);
                changed = true;
            }
            float nh3 = comp.get(GasRegistry.AMMONIA);
            if (nh3 > 0.5f) {
                comp.add(GasRegistry.AMMONIA, -Math.min(nh3, rate * 5f));
                changed = true;
            }
            float c2h2 = comp.get(GasRegistry.ACETYLENE);
            if (c2h2 > 0.5f) {
                comp.add(GasRegistry.ACETYLENE, -Math.min(c2h2, rate * 0.5f));
                changed = true;
            }
        }

        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK)) {
            float vapor = comp.get(GasRegistry.WATER_VAPOR);
            if (vapor > 2f) {
                comp.add(GasRegistry.WATER_VAPOR, -Math.min(vapor, rate * 3f));
                changed = true;
            }
        }

        if (state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.GRAVEL)) {
            if (level.getRandom().nextInt(100) == 0) {
                comp.add(GasRegistry.RADON, rate * 0.1f);
                changed = true;
            }
        }

        if (state.is(Blocks.MAGMA_BLOCK)) {
            comp.add(GasRegistry.SULFUR_DIOXIDE, rate * 0.3f);
            comp.add(GasRegistry.CARBON_DIOXIDE, rate * 0.2f);
            changed = true;
        }

        if (changed) {
            atm.setComposition(comp);
            Mge.getScheduler(level).enqueue(atmPos);
        }
    }
}
