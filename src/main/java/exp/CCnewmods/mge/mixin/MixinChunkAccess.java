package exp.CCnewmods.mge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla air with {@code mge:atmosphere} at the chunk level.
 *
 * <p>Two separate inner mixin classes target {@link LevelChunk} (runtime) and
 * {@link ProtoChunk} (world generation). They cannot share a helper method on
 * the outer class because the mixin transformer forbids injected code from
 * directly referencing any class in the mixin package. The shared logic lives
 * in {@link AtmosphereUtil#replaceIfAir} instead.</p>
 */
public abstract class MixinChunkAccess {

    @Mixin(LevelChunk.class)
    public abstract static class MixinLevelChunk {
        @Inject(
                method = "m_6978_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
                at = @At("HEAD"),
                cancellable = true,
                remap = false
        )
        private void mge$replaceAir(BlockPos pPos, BlockState pState, boolean pIsMoving,
                                    CallbackInfoReturnable<BlockState> cir) {
            // Disabled: air replacement is no longer needed with the grid-based
            // EnvironmentSection system.  The mixin class is retained so the mixin
            // config file doesn't need changes, but the body is intentionally empty.
        }
    }

    @Mixin(ProtoChunk.class)
    public abstract static class MixinProtoChunk {
        @Inject(
                method = "m_6978_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
                at = @At("HEAD"),
                cancellable = true,
                remap = false
        )
        private void mge$replaceAir(BlockPos pPos, BlockState pState, boolean pIsMoving,
                                    CallbackInfoReturnable<BlockState> cir) {
            // Disabled: see LevelChunk variant above.
        }
    }
}
