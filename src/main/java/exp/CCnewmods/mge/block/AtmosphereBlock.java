package exp.CCnewmods.mge.block;

import exp.CCnewmods.mge.Mge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The atmosphere block — MGE's replacement for all vanilla air variants.
 *
 * <p>Behaviourally identical to vanilla air from the game's perspective:
 * no collision, no selection box, not solid, isAir() returns true.
 * The difference is it carries a {@link AtmosphereBlockEntity} storing
 * the local gas composition as NBT.</p>
 *
 * <p>Rendering is handled by {@link exp.CCnewmods.mge.render.AtmosphereRenderer}
 * on the client side — the block itself reports {@link RenderShape#INVISIBLE}
 * so no default cube is drawn.</p>
 */
public class AtmosphereBlock extends AirBlock implements EntityBlock {

    public AtmosphereBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .noCollission()
                .noLootTable()
                .air()
                // Air blocks must not tick themselves — the AtmosphereTickScheduler handles that
                .noOcclusion()
                .isSuffocating((pState, pLevel, pPos) -> false)
                .isViewBlocking((pState, pLevel, pPos) -> false)
        );
    }

    // -------------------------------------------------------------------------
    // Air contract
    // -------------------------------------------------------------------------

    @Override
    public boolean isAir(BlockState pState) {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        // The renderer is handled by AtmosphereRenderer via a Mixin into the fog/sky pipeline.
        // The block itself renders nothing.
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel,
                                        BlockPos pPos, CollisionContext pContext) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel,
                               BlockPos pPos, CollisionContext pContext) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState pState, BlockGetter pReader,
                                     BlockPos pPos, CollisionContext pContext) {
        return Shapes.empty();
    }

    @Override
    public boolean skipRendering(BlockState pState, BlockState pAdjacentBlockState, net.minecraft.core.Direction pSide) {
        // Never cull adjacent block faces — atmosphere is transparent
        return true;
    }

    // -------------------------------------------------------------------------
    // BlockEntity
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new AtmosphereBlockEntity(pPos, pState);
    }

    // -------------------------------------------------------------------------
    // Entity interaction — pass-through like vanilla air
    // -------------------------------------------------------------------------

    @Override
    public void entityInside(BlockState pState, Level pLevel, BlockPos pPos,
                             Entity pEntity) {
        // PlayerGasEffectHandler handles the per-tick effect processing via the
        // LivingEvent.LivingTickEvent, not here, to keep this block lightweight.
    }
}
