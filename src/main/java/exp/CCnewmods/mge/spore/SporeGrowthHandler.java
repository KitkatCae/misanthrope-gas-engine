package exp.CCnewmods.mge.spore;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.util.ChunkIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fungal spore propagation and growth.
 *
 * Mushroom/fungus random ticks emit spore gases into the atmosphere above.
 * Every GROWTH_INTERVAL ticks, sampled atmosphere cells with sufficient spore
 * concentration attempt to grow a mushroom on the block below if conditions met.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SporeGrowthHandler {

    private static final int GROWTH_INTERVAL = 100;
    private static final float GROWTH_THRESHOLD = 5f;
    private static int tick = 0;

    private SporeGrowthHandler() {}

    /** Called from MixinRandomTick for every block random tick. */
    public static void onMushroomRandomTick(BlockState state, ServerLevel level, BlockPos pos) {
        if (!MgeConfig.enableGasEffects) return;
        BlockPos above = pos.above();

        if (state.is(Blocks.BROWN_MUSHROOM)) {
            GridAtmosphereCompat.addParticulate(level, above, ParticulateType.BROWN_MUSHROOM_SPORES, 1.5f);
        } else if (state.is(Blocks.RED_MUSHROOM)) {
            GridAtmosphereCompat.addParticulate(level, above, ParticulateType.RED_MUSHROOM_SPORES, 1.5f);
        } else if (state.is(Blocks.CRIMSON_FUNGUS) || state.is(Blocks.CRIMSON_NYLIUM)) {
            GridAtmosphereCompat.addParticulate(level, above, ParticulateType.CRIMSON_SPORES, 2f);
        } else if (state.is(Blocks.WARPED_FUNGUS) || state.is(Blocks.WARPED_NYLIUM)) {
            GridAtmosphereCompat.addParticulate(level, above, ParticulateType.WARPED_SPORES, 2f);
        } else if (state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM)) {
            GridAtmosphereCompat.addParticulate(level, above, ParticulateType.BROWN_MUSHROOM_SPORES, 3f);
            GridAtmosphereCompat.addParticulate(level, above, ParticulateType.RED_MUSHROOM_SPORES, 3f);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!MgeConfig.enableGasEffects) return;
        if (++tick % GROWTH_INTERVAL != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            ChunkIterator.forEach(level, holder -> {
                var chunk = holder.getTickingChunk();
                if (chunk == null || level.getRandom().nextInt(8) != 0) return;
                // Sample random positions in chunk and check spore levels
                var cp = chunk.getPos();
                for (int attempt = 0; attempt < 4; attempt++) {
                    int x = cp.getMinBlockX() + level.getRandom().nextInt(16);
                    int z = cp.getMinBlockZ() + level.getRandom().nextInt(16);
                    int y = level.getMinBuildHeight()
                            + level.getRandom().nextInt(level.getHeight());
                    BlockPos pos = new BlockPos(x, y, z);
                    tryGrow(level, pos);
                }
            });
        }
    }

    private static void tryGrow(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        if (!level.getBlockState(pos).isAir()) return;
        BlockPos floor = pos.below();
        BlockState floorState = level.getBlockState(floor);

        var parts = GridAtmosphereCompat.getParticulates(level, pos);
        float brown = parts.get(ParticulateType.BROWN_MUSHROOM_SPORES);
        float red   = parts.get(ParticulateType.RED_MUSHROOM_SPORES);
        float crim  = parts.get(ParticulateType.CRIMSON_SPORES);
        float warp  = parts.get(ParticulateType.WARPED_SPORES);

        int light = level.getRawBrightness(pos, 0);

        if (brown >= GROWTH_THRESHOLD && light <= 12 && isMushSurface(floorState)) {
            if (level.getRandom().nextFloat() < 0.02f * (brown / GROWTH_THRESHOLD)) {
                level.setBlock(pos, Blocks.BROWN_MUSHROOM.defaultBlockState(), 3);
                GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.BROWN_MUSHROOM_SPORES, -brown * 0.8f);
            }
        } else if (red >= GROWTH_THRESHOLD && light <= 12 && isMushSurface(floorState)) {
            if (level.getRandom().nextFloat() < 0.02f * (red / GROWTH_THRESHOLD)) {
                level.setBlock(pos, Blocks.RED_MUSHROOM.defaultBlockState(), 3);
                GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.RED_MUSHROOM_SPORES, -red * 0.8f);
            }
        } else if (crim >= GROWTH_THRESHOLD
                && (floorState.is(Blocks.CRIMSON_NYLIUM) || floorState.is(Blocks.NETHERRACK))) {
            if (level.getRandom().nextFloat() < 0.015f * (crim / GROWTH_THRESHOLD)) {
                level.setBlock(pos, Blocks.CRIMSON_FUNGUS.defaultBlockState(), 3);
                GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.CRIMSON_SPORES, -crim * 0.8f);
            }
        } else if (warp >= GROWTH_THRESHOLD
                && (floorState.is(Blocks.WARPED_NYLIUM) || floorState.is(Blocks.NETHERRACK))) {
            if (level.getRandom().nextFloat() < 0.015f * (warp / GROWTH_THRESHOLD)) {
                level.setBlock(pos, Blocks.WARPED_FUNGUS.defaultBlockState(), 3);
                GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.WARPED_SPORES, -warp * 0.8f);
            }
        }
    }

    private static boolean isMushSurface(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL)
                || s.is(Blocks.MYCELIUM) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.STONE)
                || s.is(Blocks.NETHERRACK) || s.is(Blocks.CRIMSON_NYLIUM)
                || s.is(Blocks.WARPED_NYLIUM);
    }
}
