package exp.CCnewmods.mge.mixin;

import exp.CCnewmods.mge.util.AtmosphereUtil;
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
                method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
                at = @At("HEAD"),
                cancellable = true
        )
        private void mge$replaceAir(BlockPos pPos, BlockState pState, boolean pIsMoving,
                                     CallbackInfoReturnable<BlockState> cir) {
            BlockState replacement = AtmosphereUtil.replaceIfAir(pState);
            if (replacement != pState) {
                LevelChunk self = (LevelChunk) (Object) this;
                cir.setReturnValue(self.setBlockState(pPos, replacement, pIsMoving));
            }
        }
    }

    @Mixin(ProtoChunk.class)
    public abstract static class MixinProtoChunk {
        @Inject(
                method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
                at = @At("HEAD"),
                cancellable = true
        )
        private void mge$replaceAir(BlockPos pPos, BlockState pState, boolean pIsMoving,
                                     CallbackInfoReturnable<BlockState> cir) {
            BlockState replacement = AtmosphereUtil.replaceIfAir(pState);
            if (replacement != pState) {
                ProtoChunk self = (ProtoChunk) (Object) this;
                cir.setReturnValue(self.setBlockState(pPos, replacement, pIsMoving));
            }
        }
    }
}
