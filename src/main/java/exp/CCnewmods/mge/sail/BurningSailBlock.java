package exp.CCnewmods.mge.sail;

import com.simibubi.create.AllShapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;

/**
 * Visual/structural stand-in for a Create sail block while it's mid-burn,
 * substituted in by {@code MisanthropeWindmillContraption}'s burn-tick
 * driver via the same {@code getBlocks()}-mutation +
 * {@code ContraptionBlockChangedPacket} mechanism used for every other
 * burn-stage transition (see that class and
 * {@code WindmillBurnTickMixin} for the actual driver).
 * <p>
 * ── Four instances, one class ────────────────────────────────────────────
 * <p>
 * Mirrors {@code SailBlock}'s own {@code frame} boolean split rather than
 * being four separate classes — all four burn-stage blocks
 * ({@code singed_sail}, {@code burning_sail_canvas},
 * {@code burning_sail_frame}, {@code charred_sail_frame}; see
 * {@code MisanthropeSailBlocks}) share identical behaviour (same shape
 * lookup pattern, same "purely visual, mutated externally by timer"
 * contract), differing only in which {@code AllShapes} constant, light
 * level, and texture apply. Two static factories below
 * ({@link #withCanvas} / {@link #frameOnly}) split by geometry shape, not
 * by individual stage — {@code singed_sail}/{@code burning_sail_canvas}
 * both use {@link #withCanvas}, {@code burning_sail_frame}/
 * {@code charred_sail_frame} both use {@link #frameOnly} — mirroring
 * {@code SailBlock.withCanvas}/{@code SailBlock.frame}'s own factory-method
 * convention.
 * <p>
 * ── Why a real {@code Block}, not just reusing {@code SailBlock} + an NBT
 * flag ───────────────────────────────────────────────────────────────────
 * <p>
 * The burn-down sequence is a 5-stage progression — normal sail → singed →
 * burning canvas → burning frame → charred frame (terminal) — explicitly
 * modeled after Burnt's own multi-stage log/grass burn vocabulary
 * (sooty/smoldering/ember/blazing-style escalation) per Caelan's direct
 * request, see this build's handoff for the full design note. That's a
 * sequence of distinct {@link BlockState}s being swapped into the
 * contraption's block map over time — exactly what
 * {@code ContraptionBlockChangedPacket} is built to sync. Real, separate
 * blocks (rather than piggybacking flags onto {@code SailBlock} itself, a
 * Create class this mod doesn't own) keep the burn-visual content entirely
 * within MGE, with its own texture/model naming, matching Burnt's aesthetic
 * without taking a hard dependency on Burnt's actual block classes (per
 * Caelan's explicit decision — Burnt's own burn-stage classes operate only
 * on real world {@code BlockPos}, confirmed unusable on detached
 * contraption-internal blocks across two separate investigations this
 * build).
 * <p>
 * ── Shape reuse ──────────────────────────────────────────────────────────
 * <p>
 * Deliberately reuses Create's own {@code AllShapes.SAIL} /
 * {@code AllShapes.SAIL_FRAME} / {@code AllShapes.SAIL_FRAME_COLLISION}
 * {@code VoxelShaper}s rather than redefining the sail panel geometry from
 * scratch — a burning sail should occupy exactly the same space as the
 * sail it replaced, mid-spin, with no visual pop/seam. Mirrors
 * {@code SailBlock.getShape()}/{@code getCollisionShape()}'s exact dispatch
 * pattern (frame variant gets a dedicated collision shaper; canvas variant's
 * collision shape falls through to the visual shape, since cloth has no
 * separate solid hitbox) — confirmed via bytecode of those two methods, not
 * assumed from the non-burning class's general shape.
 * <p>
 * {@code VoxelShaper} itself (and {@code AllShapes.SAIL}/{@code SAIL_FRAME}/
 * {@code SAIL_FRAME_COLLISION}'s backing class,
 * {@code net.createmod.catnip.math.VoxelShaper}) is fully verified as of
 * this pass — Caelan supplied the {@code Ponder} jar, which shades Catnip,
 * and {@code VoxelShaper.get(Direction): VoxelShape} was confirmed to match
 * exactly what this class calls. No outstanding caveat on this point
 * anymore (a prior pass had flagged it as unverified).
 */
public class BurningSailBlock extends DirectionalBlock {

    protected final boolean frame;

    protected BurningSailBlock(Properties properties, boolean frame) {
        super(properties);
        this.frame = frame;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.UP));
    }

    /**
     * Full sail geometry (frame + canvas), matching {@code AllShapes.SAIL}.
     * Used by the two pre-canvas-loss burn stages — {@code singed_sail} and
     * {@code burning_sail_canvas} — both of which still have visible cloth.
     * Named for the geometry shape rather than a specific burn stage, since
     * two different stage blocks now share this same shape (see
     * {@code MisanthropeSailBlocks}).
     */
    public static BurningSailBlock withCanvas(Properties properties) {
        return new BurningSailBlock(properties, false);
    }

    /**
     * Frame-only geometry (no canvas), matching {@code AllShapes.SAIL_FRAME}.
     * Used by the two post-canvas-loss burn stages — {@code burning_sail_frame}
     * and {@code charred_sail_frame} — both of which represent the canvas
     * having already burned away, leaving only the wooden frame.
     */
    public static BurningSailBlock frameOnly(Properties properties) {
        return new BurningSailBlock(properties, true);
    }

    public boolean isFrame() {
        return frame;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return frame
                ? AllShapes.SAIL_FRAME.get(facing)
                : AllShapes.SAIL.get(facing);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (frame) {
            return AllShapes.SAIL_FRAME_COLLISION.get(state.getValue(FACING));
        }
        return getShape(state, level, pos, context);
    }

    /**
     * Burning blocks are never placed by a player and never go through
     * normal {@code getStateForPlacement} — they only ever appear as a
     * direct {@link BlockState} substitution inside an already-assembled
     * contraption's block map, carrying over the {@code FACING} value from
     * whatever sail block they replaced (see the burn-tick driver, which
     * always copies {@code FACING} forward explicitly rather than relying
     * on placement context). No override needed here; the inherited
     * {@code DirectionalBlock} behaviour is simply never exercised for
     * these blocks, which is fine since nothing calls it.
     */
}
