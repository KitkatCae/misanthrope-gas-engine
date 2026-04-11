package exp.CCnewmods.mge.dimension;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

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

        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            if (!(entry.getValue() instanceof AtmosphereBlockEntity atm)) continue;
            if (atm.isDirtyForTick()) continue;
            if (hasBeenCustomised(atm, finalProfile)) continue;

            atm.setComposition(finalProfile.createGasComposition());
            if (MgeConfig.enableParticulates) {
                ParticulateComposition parts = finalProfile.createParticulateComposition();
                if (!parts.isEmpty()) atm.setParticulates(parts);
            }
            atm.clearDirtyFlag();
        }

        // Enqueue chunk centre so profile diffuses naturally
        ChunkPos cp = chunk.getPos();
        Mge.getScheduler(level).enqueue(new BlockPos(
                cp.getMiddleBlockX(), level.getMinBuildHeight(), cp.getMiddleBlockZ()));
    }

    /**
     * Returns true if the block appears to already have a real (non-default) composition
     * that should not be overwritten. Logic:
     * <ul>
     *   <li>Empty composition → not customised, overwrite it.</li>
     *   <li>Profile is non-breathable but block has O₂ near standard → still constructor
     *       default, overwrite it.</li>
     *   <li>Profile is breathable and O₂ matches standard → already correct, skip.</li>
     *   <li>Any other state → assume player/world modified, skip.</li>
     * </ul>
     */
    private static boolean hasBeenCustomised(AtmosphereBlockEntity atm,
                                               DimensionAtmosphereProfile profile) {
        if (atm.getComposition().isEmpty()) return false;

        float o2 = atm.getComposition().get(GasRegistry.OXYGEN);

        // Block has constructor-default Earth air AND profile is non-breathable → overwrite
        if (!profile.breathable && o2 > 100f) return false;

        // Block has standard air AND profile is also standard → no-op but safe to skip
        if (profile.breathable && Math.abs(o2 - 209.5f) < 5.0f) return false;

        return true;
    }
}
