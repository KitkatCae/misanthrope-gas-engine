package exp.CCnewmods.mge.mixin;

import exp.CCnewmods.mge.breathing.ActiveBreathingHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link Block#randomTick} to feed photosynthetic block ticks into
 * {@link ActiveBreathingHandler#onPlantRandomTick}.
 *
 * <p>{@code BlockEvent.RandomTickEvent} was removed in Forge 1.20.1. This mixin
 * is the correct replacement — it targets the base {@link Block#randomTick} method
 * which every block's random tick ultimately calls via {@code super} or directly.</p>
 *
 * <p>Performance: the inject fires on every random block tick on the server, but
 * {@link ActiveBreathingHandler#onPlantRandomTick} returns immediately if the block
 * is not photosynthetic, making the overhead a single {@code instanceof} check
 * per random tick. Vanilla ticks ~3 random blocks per chunk section per tick, so
 * the real cost across a loaded world is negligible.</p>
 */
@Mixin(Block.class)
public abstract class MixinRandomTick {

    @Inject(
            method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD")
    )
    private void mge$onRandomTick(BlockState state, ServerLevel level,
                                   BlockPos pos, RandomSource random,
                                   CallbackInfo ci) {
        ActiveBreathingHandler.onPlantRandomTick(state, level, pos);
    }
}
