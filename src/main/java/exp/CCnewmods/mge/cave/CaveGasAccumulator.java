package exp.CCnewmods.mge.cave;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates trapped gas pockets in caves and heavy gas pooling in topographic lows.
 *
 * <h3>Cave gas pockets</h3>
 * Enclosed underground spaces (no sky access, solid blocks on most sides) act as
 * natural traps for gases released from the surrounding geology:
 * <ul>
 *   <li>CO₂ from carbonate rock decomposition (radon from granite, H₂S from sulfides)</li>
 *   <li>Methane from coal seams and organic matter</li>
 *   <li>Radon from uranium-bearing rock</li>
 * </ul>
 * The accumulator checks enclosure and reinforces these gases slowly over time.
 *
 * <h3>Valley/low-point pooling</h3>
 * Gases denser than air (CO₂ density 1.52×, H₂S 1.18×, radon 7.5×) naturally pool
 * in topographic depressions because convective mixing is weak there. We detect
 * surface-level blocks that are lower than their 8 horizontal neighbours and apply
 * a slow accumulation multiplier for heavy gases.
 *
 * <h3>Performance</h3>
 * Runs every 60 ticks, processes one random chunk per level per call. O(chunks not
 * O(blocks)) — only processes a surface or underground sample position per chunk.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaveGasAccumulator {

    private static final int RUN_INTERVAL = 60;
    private static int tick = 0;

    // Accumulation rates — very slow to be geological in feel
    private static final float CAVE_CO2_RATE   = 0.08f;
    private static final float CAVE_RADON_RATE = 0.04f;
    private static final float CAVE_CH4_RATE   = 0.03f;
    private static final float CAVE_H2S_RATE   = 0.02f;
    private static final float VALLEY_RATE_MULT = 1.5f;  // extra pooling in valleys

    // Enclosure check: fraction of 6 cardinal neighbours that must be solid
    private static final int MIN_SOLID_NEIGHBOURS = 4;

    private CaveGasAccumulator() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tick % RUN_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            tickLevel(level);
        }
    }

    private static void tickLevel(ServerLevel level) {
        var rand = level.getRandom();
        // Process a random sample of loaded chunks
        level.getChunkSource().chunkMap.getChunks().forEach(holder -> {
            if (rand.nextInt(20) != 0) return; // ~5% of chunks per interval
            var chunk = holder.getTickingChunk();
            if (chunk == null) return;

            var cp = chunk.getPos();
            int cx = cp.getMiddleBlockX();
            int cz = cp.getMiddleBlockZ();

            // --- Underground cave check at random Y below surface ---
            int surfaceY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                    new BlockPos(cx, 0, cz)).getY();
            int caveY = level.getMinBuildHeight() + rand.nextInt(
                    Math.max(1, surfaceY - level.getMinBuildHeight()));
            BlockPos cavePos = new BlockPos(cx, caveY, cz);

            if (level.isLoaded(cavePos) && isEnclosed(level, cavePos)) {
                accumulate(level, cavePos, false);
            }

            // --- Surface valley check ---
            BlockPos surfacePos = new BlockPos(cx, surfaceY - 1, cz);
            if (level.isLoaded(surfacePos) && isValleyLow(level, surfacePos)) {
                accumulate(level, surfacePos, true);
            }
        });
    }

    private static void accumulate(ServerLevel level, BlockPos pos, boolean isValley) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        // Only accumulate if there's no sky access (caves) or if it's a valley (surface)
        if (!isValley && level.canSeeSky(pos)) return;

        GasComposition comp = atm.getComposition();
        float mult = isValley ? VALLEY_RATE_MULT : 1.0f;

        comp.add(GasRegistry.CARBON_DIOXIDE, CAVE_CO2_RATE * mult);
        comp.add(GasRegistry.RADON,          CAVE_RADON_RATE * mult);

        // Methane only accumulates where organic matter could be present
        // (heuristic: near coal/stone — we just apply everywhere underground)
        if (!isValley) {
            comp.add(GasRegistry.METHANE,         CAVE_CH4_RATE);
            comp.add(GasRegistry.HYDROGEN_SULFIDE, CAVE_H2S_RATE);
        }

        atm.setComposition(comp);
        Mge.getScheduler(level).enqueue(pos);
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    /** Returns true if this position is enclosed (cave-like) — no sky + solid neighbours. */
    private static boolean isEnclosed(ServerLevel level, BlockPos pos) {
        if (level.canSeeSky(pos)) return false;

        int solidCount = 0;
        BlockPos[] cardinals = {
            pos.above(), pos.below(),
            pos.north(), pos.south(), pos.east(), pos.west()
        };
        for (BlockPos n : cardinals) {
            if (level.getBlockState(n).isSolidRender(level, n)) solidCount++;
        }
        return solidCount >= MIN_SOLID_NEIGHBOURS;
    }

    /**
     * Returns true if this surface block is a topographic low relative to
     * its 8 horizontal neighbours — indicating a valley or depression.
     */
    private static boolean isValleyLow(ServerLevel level, BlockPos pos) {
        int myY = pos.getY();
        int higherCount = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos neighbour = new BlockPos(pos.getX() + dx * 16, 0, pos.getZ() + dz * 16);
                int neighbourY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, neighbour).getY() - 1;
                if (neighbourY > myY + 4) higherCount++; // at least 4 blocks higher
            }
        }
        return higherCount >= 5; // at least 5 of 8 neighbours are significantly higher
    }
}
