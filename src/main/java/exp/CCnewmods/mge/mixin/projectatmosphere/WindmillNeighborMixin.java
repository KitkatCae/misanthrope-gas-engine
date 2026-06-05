package exp.CCnewmods.mge.mixin.projectatmosphere;

import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import exp.CCnewmods.mge.compat.projectatmosphere.WindmillWindIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates the obstruction cache entry for a WindmillBearingBlockEntity
 * whenever a neighboring block changes.
 * <p>
 * Without this, placing a wall directly upwind of a windmill would not be
 * noticed until the 60-tick cache TTL expires.  With this mixin the cache
 * is evicted immediately on any neighbor change, so the very next tick
 * gets fresh obstruction data.
 * <p>
 * The target method is SmartBlockEntity.neighborChanged, which is the
 * correct lifecycle hook for block-update notifications in Create's BE hierarchy.
 * We use the Create method name (not an SRG name) since remap=false.
 */
@Mixin(value = WindmillBearingBlockEntity.class, remap = false)
public class WindmillNeighborMixin {

    /**
     * SmartBlockEntity.neighborChanged(BlockState, Level, BlockPos, BlockPos)
     * is called by the block whenever a neighbor changes state.
     * <p>
     * The signature in Create is:
     * void neighborChanged(BlockState state, Level level,
     * BlockPos pos, BlockPos fromPos)
     * <p>
     * We target the HEAD and simply evict our cache — no cancellation needed.
     */
    @Inject(
            method = "neighborChanged",
            at = @At("HEAD"),
            remap = false,
            require = 0  // soft — if the method signature differs in a future Create version, fail gracefully
    )
    private void misanthrope_invalidateWindCache(
            BlockState state, Level level, BlockPos pos, BlockPos fromPos,
            CallbackInfo ci) {
        WindmillWindIntegration.invalidateCache(pos);
    }
}
