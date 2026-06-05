package exp.CCnewmods.mge.compat.projectatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Computes an airflow factor in [0, 1] describing how much wind is actually
 * able to reach and pass through the windmill's swept area.
 * <p>
 * ── Obstruction model ───────────────────────────────────────────────────────
 * <p>
 * A windmill's swept plane is perpendicular to its FACING axis.  Wind blows
 * along that axis.  We sample two zones:
 * <p>
 * 1. UPWIND COLUMN — blocks directly in the path of incoming wind, scanned from
 * 1 to UPWIND_SCAN_DEPTH blocks upwind of the bearing.  These are weighted by
 * inverse distance: a solid cliff 1 block upwind is catastrophic; the same
 * cliff 8 blocks away barely matters.  A solid hit within 2 blocks short-
 * circuits the scan and treats everything beyond as equally blocked.
 * <p>
 * 2. SWEPT AREA — a cross-shaped sample of the plane the sails rotate in,
 * centred on the bearing, extending SWEPT_RADIUS blocks along each of the
 * two perpendicular axes.  A sail arm completely inside a wall can't spin;
 * partial enclosure reduces effective swept area proportionally.
 * <p>
 * 3. UNDERGROUND CHECK — if fewer than SKY_MIN_CLEAR of the SKY_CHECK_HEIGHT
 * blocks directly above the bearing are passable, the windmill is considered
 * buried and returns 0 (no wind underground, inside mountains, etc.).
 * <p>
 * Block scoring:
 * - Air / non-solid (flowers, torches, etc.): 0.0 obstruction
 * - Full opaque cube (dirt, stone, wood, etc.): 1.0 obstruction
 * - Solid but non-opaque (glass, leaves, slabs, stairs, fences): 0.4 obstruction
 * - Fluids: 0.0 (wind blows over water surfaces; underwater handled by burial)
 * <p>
 * Final airflow = 1 - (upwindObstruction x UPWIND_WEIGHT
 * + sweptObstruction  x SWEPT_WEIGHT)
 * Clamped to [0, 1].
 * <p>
 * ────────────────────────────────────────────────────────────────────────────
 */
public final class WindObstructionSampler {

    private WindObstructionSampler() {
    }

    // ── Tunables ──────────────────────────────────────────────────────────────

    private static final int UPWIND_SCAN_DEPTH = 8;
    private static final int SWEPT_RADIUS = 4;
    private static final float UPWIND_WEIGHT = 0.65f;
    private static final float SWEPT_WEIGHT = 0.35f;
    private static final float PARTIAL_BLOCK_OBSTRUCTION = 0.4f;
    private static final int SKY_CHECK_HEIGHT = 6;
    private static final int SKY_MIN_CLEAR = 3;

    // ── Main entry point ──────────────────────────────────────────────────────

    public static float computeAirflowFactor(Level level, BlockPos bearingPos, Direction facing) {
        if (isBuried(level, bearingPos)) return 0f;

        float upwindObstruction = sampleUpwindColumn(level, bearingPos, facing);
        float sweptObstruction = sampleSweptArea(level, bearingPos, facing);

        float totalObstruction = (upwindObstruction * UPWIND_WEIGHT)
                + (sweptObstruction * SWEPT_WEIGHT);

        return Math.max(0f, 1f - totalObstruction);
    }

    // ── Underground check ─────────────────────────────────────────────────────

    private static boolean isBuried(Level level, BlockPos bearingPos) {
        int clearCount = 0;
        for (int i = 1; i <= SKY_CHECK_HEIGHT; i++) {
            BlockState bs = level.getBlockState(bearingPos.above(i));
            if (!bs.isSolid() && bs.getFluidState().isEmpty()) {
                clearCount++;
            }
        }
        return clearCount < SKY_MIN_CLEAR;
    }

    // ── Upwind column ─────────────────────────────────────────────────────────

    private static float sampleUpwindColumn(Level level, BlockPos bearingPos, Direction facing) {
        Direction upwind = facing.getOpposite();

        float totalWeight = 0f;
        float weightedObstruction = 0f;
        boolean earlyExit = false;

        for (int d = 1; d <= UPWIND_SCAN_DEPTH; d++) {
            float w = 1f / d;
            totalWeight += w;

            if (earlyExit) {
                // Everything beyond the close solid wall is also blocked
                weightedObstruction += w;
                continue;
            }

            BlockPos scanPos = bearingPos.relative(upwind, d);
            float obs = blockObstructionScore(level, scanPos);
            weightedObstruction += obs * w;

            // Close solid wall: shadow everything beyond
            if (obs >= 1f && d <= 2) {
                earlyExit = true;
            }
        }

        return totalWeight > 0f ? (weightedObstruction / totalWeight) : 0f;
    }

    // ── Swept area ────────────────────────────────────────────────────────────

    private static float sampleSweptArea(Level level, BlockPos bearingPos, Direction facing) {
        Direction[] perp = getPerpendicularDirections(facing);
        Direction perpA = perp[0];
        Direction perpB = perp[1];

        int totalSamples = 0;
        float totalObstruction = 0f;

        for (int r = 1; r <= SWEPT_RADIUS; r++) {
            totalObstruction += blockObstructionScore(level, bearingPos.relative(perpA, r));
            totalObstruction += blockObstructionScore(level, bearingPos.relative(perpA.getOpposite(), r));
            totalObstruction += blockObstructionScore(level, bearingPos.relative(perpB, r));
            totalObstruction += blockObstructionScore(level, bearingPos.relative(perpB.getOpposite(), r));
            totalSamples += 4;
        }

        return totalSamples > 0 ? (totalObstruction / totalSamples) : 0f;
    }

    // ── Block scoring ─────────────────────────────────────────────────────────

    private static float blockObstructionScore(Level level, BlockPos pos) {
        BlockState bs = level.getBlockState(pos);

        if (bs.isAir()) return 0f;

        // Pure fluid blocks don't obstruct wind
        FluidState fs = bs.getFluidState();
        if (!fs.isEmpty() && bs.isAir()) return 0f;

        // Non-solid decorative blocks
        if (!bs.isSolid()) return 0f;

        // Full opaque cube (conducts redstone signal = full cube)
        if (bs.isRedstoneConductor(level, pos)) return 1f;

        // Solid but irregular/transparent: glass, leaves, slabs, stairs, fences
        return PARTIAL_BLOCK_OBSTRUCTION;
    }

    // ── Geometry ─────────────────────────────────────────────────────────────

    private static Direction[] getPerpendicularDirections(Direction facing) {
        return switch (facing.getAxis()) {
            case X -> new Direction[]{Direction.NORTH, Direction.UP};
            case Z -> new Direction[]{Direction.EAST, Direction.UP};
            case Y -> new Direction[]{Direction.NORTH, Direction.EAST};
        };
    }
}
