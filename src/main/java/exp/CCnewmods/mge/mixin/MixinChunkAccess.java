package exp.CCnewmods.mge.mixin;

import exp.CCnewmods.mge.Mge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla air block states with {@code mge:atmosphere} at the chunk level.
 *
 * <p>{@link ChunkAccess#setBlockState} is abstract in 1.20.1 — the real implementations
 * are in {@link LevelChunk} (loaded chunks) and {@link ProtoChunk} (generation-time chunks).
 * We mixin to both concrete classes to cover all code paths:
 * world generation, structure placement, fill commands, and player-placed blocks.</p>
 *
 * <p>Recursion safety: the guard checks {@code pState.is(Blocks.AIR/CAVE_AIR/VOID_AIR)}.
 * The atmosphere block state fails all three checks so the recursive call through
 * {@code cir.setReturnValue} passes without re-triggering. Mixin injection is cancelled
 * before the recursive call proceeds, so there is no stack overflow.</p>
 */
public abstract class MixinChunkAccess {

    private static BlockState mge$replaceIfAir(BlockState pState) {
        if (pState.is(Blocks.AIR)
                || pState.is(Blocks.CAVE_AIR)
                || pState.is(Blocks.VOID_AIR)) {
            return Mge.ATMOSPHERE_BLOCK.get().defaultBlockState();
        }
        return pState;
    }

    // ── LevelChunk (runtime loaded chunks) ───────────────────────────────────

    @Mixin(LevelChunk.class)
    public abstract static class MixinLevelChunk {
        @Inject(
                method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
                at = @At("HEAD"),
                cancellable = true
        )
        private void mge$replaceAir(BlockPos pPos, BlockState pState, boolean pIsMoving,
                                     CallbackInfoReturnable<BlockState> cir) {
            BlockState replacement = mge$replaceIfAir(pState);
            if (replacement != pState) {
                // Re-invoke with atmosphere state — this call won't trigger the mixin again
                // because atmosphere is not Blocks.AIR/CAVE_AIR/VOID_AIR
                LevelChunk self = (LevelChunk) (Object) this;
                cir.setReturnValue(self.setBlockState(pPos, replacement, pIsMoving));
            }
        }
    }

    // ── ProtoChunk (world-generation time chunks) ─────────────────────────────

    @Mixin(ProtoChunk.class)
    public abstract static class MixinProtoChunk {
        @Inject(
                method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
                at = @At("HEAD"),
                cancellable = true
        )
        private void mge$replaceAir(BlockPos pPos, BlockState pState, boolean pIsMoving,
                                     CallbackInfoReturnable<BlockState> cir) {
            BlockState replacement = mge$replaceIfAir(pState);
            if (replacement != pState) {
                ProtoChunk self = (ProtoChunk) (Object) this;
                cir.setReturnValue(self.setBlockState(pPos, replacement, pIsMoving));
            }
        }
    }
}
