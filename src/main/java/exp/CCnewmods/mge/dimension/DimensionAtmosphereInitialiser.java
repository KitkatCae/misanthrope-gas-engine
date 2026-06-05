package exp.CCnewmods.mge.dimension;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;

import exp.CCnewmods.mge.MgeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


/**
 * Initialises atmosphere block entities in freshly loaded chunks with the correct
 * default composition for their dimension, sourced from {@link DimensionAtmosphereLoader}.
 *
 * <p>Only replaces the default constructor-set Earth air — blocks that have already
 * been mutated by world events retain their actual composition.</p>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DimensionAtmosphereInitialiser {

    private DimensionAtmosphereInitialiser() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ResourceLocation dimKey = level.dimension().location();
        DimensionAtmosphereProfile profile = DimensionAtmosphereLoader.get(dimKey);

        if (profile == null) {
            if (MgeConfig.standardAirOnGeneration) {
                profile = DimensionAtmosphereLoader.STANDARD_OVERWORLD_FALLBACK;
            } else {
                return;
            }
        }

        final DimensionAtmosphereProfile finalProfile = profile;

        // With the grid system, dimension profile is set on the EnvironmentChunkData
        // capability during attachment — default gas values are returned from the
        // profile for any unallocated section automatically.  No explicit initialisation
        // of individual cells is needed here.  We just enqueue the chunk centre so
        // any existing non-default gas data starts diffusing immediately.
        ChunkPos cp = chunk.getPos();
        EnvironmentGrid.enqueue(level, new BlockPos(
                cp.getMiddleBlockX(), level.getMinBuildHeight(), cp.getMiddleBlockZ()));
    }

}
