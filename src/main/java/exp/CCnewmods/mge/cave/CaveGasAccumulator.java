package exp.CCnewmods.mge.cave;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.util.ChunkIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Geological gas accumulation in caves and valley topographic lows.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaveGasAccumulator {

    private static final int RUN_INTERVAL = 60;
    private static int tick = 0;

    private static final float CAVE_CO2_RATE   = 0.08f;
    private static final float CAVE_RADON_RATE = 0.04f;
    private static final float CAVE_CH4_RATE   = 0.03f;
    private static final float CAVE_H2S_RATE   = 0.02f;
    private static final float VALLEY_RATE_MULT = 1.5f;
    private static final int   MIN_SOLID_NEIGHBOURS = 4;

    private CaveGasAccumulator() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tick % RUN_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;
        for (ServerLevel level : event.getServer().getAllLevels()) tickLevel(level);
    }

    private static void tickLevel(ServerLevel level) {
        var rand = level.getRandom();
        ChunkIterator.forEach(level, holder -> {
            if (rand.nextInt(20) != 0) return;
            var chunk = holder.getTickingChunk();
            if (chunk == null) return;

            var cp = chunk.getPos();
            int cx = cp.getMiddleBlockX(), cz = cp.getMiddleBlockZ();
            BlockPos surfRef = new BlockPos(cx, 0, cz);
            int surfaceY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, surfRef).getY();

            // Underground cave sample
            int range = Math.max(1, surfaceY - level.getMinBuildHeight());
            int caveY = level.getMinBuildHeight() + rand.nextInt(range);
            BlockPos cavePos = new BlockPos(cx, caveY, cz);
            if (level.isLoaded(cavePos) && isEnclosed(level, cavePos)) {
                accumulate(level, cavePos, false);
            }

            // Surface valley sample
            BlockPos surfacePos = new BlockPos(cx, surfaceY - 1, cz);
            if (level.isLoaded(surfacePos) && isValleyLow(level, surfacePos)) {
                accumulate(level, surfacePos, true);
            }
        });
    }

    private static void accumulate(ServerLevel level, BlockPos pos, boolean isValley) {
        if (!isValley && level.canSeeSky(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        float mult = isValley ? VALLEY_RATE_MULT : 1.0f;
        var comp = atm.getComposition();
        comp.add(GasRegistry.CARBON_DIOXIDE, CAVE_CO2_RATE * mult);
        comp.add(GasRegistry.RADON,          CAVE_RADON_RATE * mult);
        if (!isValley) {
            comp.add(GasRegistry.METHANE,          CAVE_CH4_RATE);
            comp.add(GasRegistry.HYDROGEN_SULFIDE, CAVE_H2S_RATE);
        }
        atm.setComposition(comp);
        Mge.getScheduler(level).enqueue(pos);
    }

    private static boolean isEnclosed(ServerLevel level, BlockPos pos) {
        if (level.canSeeSky(pos)) return false;
        int solid = 0;
        for (BlockPos n : new BlockPos[]{ pos.above(), pos.below(),
                pos.north(), pos.south(), pos.east(), pos.west() }) {
            if (level.getBlockState(n).isSolidRender(level, n)) solid++;
        }
        return solid >= MIN_SOLID_NEIGHBOURS;
    }

    private static boolean isValleyLow(ServerLevel level, BlockPos pos) {
        int myY = pos.getY();
        int higher = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos n = new BlockPos(pos.getX() + dx * 16, 0, pos.getZ() + dz * 16);
                int nY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, n).getY() - 1;
                if (nY > myY + 4) higher++;
            }
        }
        return higher >= 5;
    }
}
