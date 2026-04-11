package exp.CCnewmods.mge.mixin;

import exp.CCnewmods.mge.block.AtmosphereBlock;
import exp.CCnewmods.mge.propagation.GasPropagator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link Level#setBlock} to detect when an atmosphere block is about
 * to be replaced by a non-atmosphere block.
 *
 * <p>The injection fires BEFORE the block is actually replaced, so the old
 * block entity (and its gas NBT) is still accessible. {@link GasPropagator}
 * reads that NBT and distributes it to the 26 surrounding neighbours.</p>
 */
@Mixin(Level.class)
public abstract class MixinLevelSetBlock {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("HEAD")
    )
    private void mge$onSetBlock(BlockPos pPos, BlockState pState, int pFlags,
                                 CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;

        // Only on the server, only when the old block is atmosphere and the new one isn't
        if (self.isClientSide()) return;
        if (!(self instanceof ServerLevel)) return;

        BlockState existing = self.getBlockState(pPos);
        if (!(existing.getBlock() instanceof AtmosphereBlock)) return;
        if (pState.getBlock() instanceof AtmosphereBlock) return;

        // Fire propagation BEFORE the replace — the block entity still exists here
        GasPropagator.onAtmosphereDisplaced((Level) (Object) this, pPos);
    }
}
