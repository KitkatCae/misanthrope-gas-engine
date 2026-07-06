package exp.CCnewmods.mge.mixin.projectatmosphere;

import com.simibubi.create.AllPackets;
import com.simibubi.create.content.contraptions.ContraptionBlockChangedPacket;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import exp.CCnewmods.mge.compat.projectatmosphere.GasFlowSampler;
import exp.CCnewmods.mge.contraption.MisanthropeWindmillContraption;
import exp.CCnewmods.mge.sail.BurningSailBlock;
import exp.CCnewmods.mge.sail.ISailMaterial;
import exp.CCnewmods.mge.sail.MisanthropeSailBlocks;
import exp.CCnewmods.mge.sail.SailMaterialRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the windmill sail burn-down spectacle end to end: ignition trigger,
 * per-block independent burn timers, and the actual block-state mutation +
 * client sync.
 * <p>
 * ── Design recap (Caelan's explicit decisions, carried forward exactly) ──
 * <ul>
 *   <li>Both canvas and frame sail variants are flammable — no
 *       {@code isFrame()} special-casing for ignition eligibility.</li>
 *   <li><b>Five-stage sequence</b> (revised this pass — see class doc
 *       comment on {@code MisanthropeSailBlocks} for the full per-stage
 *       breakdown): normal sail → singed → burning canvas → burning frame →
 *       charred frame (terminal, permanent). Explicitly modeled after
 *       Burnt's own multi-stage log/grass burn vocabulary per Caelan's
 *       direct request, rather than the simpler 2-stage canvas→frame→gone
 *       design from an earlier pass. <b>The block no longer disappears at
 *       the end</b> — {@code charred_sail_frame} is a permanent resting
 *       state, not a removal. This is a deliberate change from the prior
 *       pass's "advance to gone" terminal behaviour.</li>
 *   <li>Sails keep spinning while burning — this mixin only ever mutates
 *       {@code Contraption.getBlocks()} entries and the per-block burn-state
 *       map; it never touches angle, speed, or assembly state, so the
 *       existing spin physics in {@code WindmillAtmosphereMixin} is
 *       completely unaffected.</li>
 *   <li>Per-sail-block independent timer — each burning block has its own
 *       countdown, so a windmill can end up with some blades still bare
 *       frame, others mid-canvas-burn, others already charred, all at
 *       once.</li>
 * </ul>
 * <p>
 * ── Ignition trigger — windmill-wide eligibility, per-block independent
 * progression ──────────────────────────────────────────────────────────────
 * <p>
 * {@link GasFlowSampler.SampleResult#temperature()} is sampled once per tick
 * at the bearing's own world position. There is no per-sail-block
 * temperature reading available — a spinning contraption's blocks have no
 * live world position to sample at. So ignition eligibility is necessarily
 * windmill-wide: when the bearing's ambient temperature is sustained above
 * {@link #IGNITION_TEMP_THRESHOLD_C} for {@link #IGNITION_SUSTAIN_TICKS},
 * every currently-eligible sail block ignites (advances to
 * {@code singed_sail}) together in the same tick. From that point on, each
 * ignited block's burn-down <i>progression</i> is independent — they're all
 * lit at the same moment, but {@link #pickStageDuration}'s per-block jitter,
 * re-rolled at every one of the four stage transitions, compounds across
 * stages and produces real spread by the time blocks reach the later
 * stages — the "some blades bare frame, others mid-burn" look.
 * <p>
 * ── Per-block burnability check ───────────────────────────────────────────
 * <p>
 * Mirrors {@code SailMaterialRegistry.resolve(state)}'s own heat-resistance
 * lookup rather than inventing a second classification system. A block is
 * burn-eligible if {@code !resolve(state).heatResistant()} AND it's
 * actually a sail-shaped block (vanilla {@link SailBlock} or any future
 * {@code ISailMaterial} block).
 */
@Mixin(value = WindmillBearingBlockEntity.class, remap = false)
public abstract class WindmillBurnTickMixin {

    /** °C above which sustained heat begins counting toward ignition. */
    private static final float IGNITION_TEMP_THRESHOLD_C = 600f;

    /** Ticks the bearing must stay above threshold before sails ignite. */
    private static final int IGNITION_SUSTAIN_TICKS = 100; // 5 seconds

    /**
     * Base duration (ticks) each stage holds before advancing to the next.
     * Actual per-block value is jittered ±25% (see {@link #pickStageDuration})
     * — re-rolled independently at every stage transition, so simultaneously
     * -ignited blocks visibly desync more and more as they progress, rather
     * than advancing in lockstep.
     * <p>
     * Four stages, four durations — index matches {@link #STAGE_SINGED}
     * through {@link #STAGE_CHARRED}, i.e. {@code STAGE_DURATIONS[stage]}
     * is the hold time for that stage before advancing to {@code stage + 1}.
     * {@code STAGE_CHARRED}'s entry is unused (terminal state, nothing to
     * advance to) but kept in the array for index alignment rather than
     * special-cased out.
     */
    private static final int[] STAGE_DURATIONS = {
            60,   // STAGE_SINGED: ~3s warning before canvas catches
            100,  // STAGE_BURNING_CANVAS: ~5s of canvas fire
            140,  // STAGE_BURNING_FRAME: ~7s of frame fire
            0,    // STAGE_CHARRED: terminal, unused
    };

    /** Burn stage constants — index into {@link #STAGE_DURATIONS} and the block-lookup table. */
    private static final int STAGE_SINGED = 0;
    private static final int STAGE_BURNING_CANVAS = 1;
    private static final int STAGE_BURNING_FRAME = 2;
    private static final int STAGE_CHARRED = 3;

    /**
     * Stage → block lookup, parallel to {@link #STAGE_DURATIONS}. Avoids a
     * long if/else chain in {@link #misanthrope_tickOneBlock} — advancing a
     * stage is just "look up {@code STAGE_BLOCKS[newStage]}, swap to it."
     */
    private static final RegistryObject<BurningSailBlock>[] STAGE_BLOCKS =
            makeStageBlocksArray();

    @SuppressWarnings("unchecked")
    private static RegistryObject<BurningSailBlock>[] makeStageBlocksArray() {
        // Isolated into its own method (rather than a field initializer)
        // so the @SuppressWarnings("unchecked") for the inherent generic-
        // array-creation warning is scoped as tightly as possible — it
        // applies to this one array-construction expression, not to the
        // whole class or some larger block of code where an unrelated real
        // warning could get silently swallowed alongside it.
        return new RegistryObject[]{
                MisanthropeSailBlocks.SINGED_SAIL,
                MisanthropeSailBlocks.BURNING_SAIL_CANVAS,
                MisanthropeSailBlocks.BURNING_SAIL_FRAME,
                MisanthropeSailBlocks.CHARRED_SAIL_FRAME,
        };
    }

    /**
     * Per-bearing sustained-heat counter, keyed by the bearing's own world
     * {@link BlockPos}. Cleared automatically whenever a bearing's ambient
     * temperature drops back below threshold.
     */
    private static final Map<BlockPos, Integer> SUSTAIN_TICKS = new HashMap<>();

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void misanthrope_tickBurn(CallbackInfo ci) {
        WindmillBearingBlockEntity self = (WindmillBearingBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return;

        ControlledContraptionEntity movedContraption = self.getMovedContraption();
        if (movedContraption == null) return;
        if (!(movedContraption.getContraption() instanceof MisanthropeWindmillContraption contraption)) return;

        BlockPos bearingPos = self.getBlockPos();

        misanthrope_tickIgnitionTrigger(level, bearingPos, self, movedContraption, contraption);
        misanthrope_tickBurnProgression(level, movedContraption, contraption);
    }

    // ── Ignition trigger ─────────────────────────────────────────────────

    private void misanthrope_tickIgnitionTrigger(
            Level level, BlockPos bearingPos, WindmillBearingBlockEntity self,
            ControlledContraptionEntity movedContraption, MisanthropeWindmillContraption contraption) {

        Direction facing = Direction.UP;
        BlockState bearingState = self.getBlockState();
        if (bearingState.hasProperty(BlockStateProperties.FACING)) {
            facing = bearingState.getValue(BlockStateProperties.FACING);
        }

        GasFlowSampler.SampleResult sample = GasFlowSampler.sample(level, bearingPos, facing);
        float temperature = sample.temperature();

        if (Float.isNaN(temperature) || temperature < IGNITION_TEMP_THRESHOLD_C) {
            SUSTAIN_TICKS.remove(bearingPos);
            return;
        }

        int sustained = SUSTAIN_TICKS.merge(bearingPos, 1, Integer::sum);
        if (sustained < IGNITION_SUSTAIN_TICKS) return;

        // Sustained threshold reached. Reset the counter immediately so a
        // windmill sitting in a permanently hot environment doesn't retry
        // every tick.
        SUSTAIN_TICKS.remove(bearingPos);

        // Snapshot eligible positions before mutating anything — we're
        // about to write into the same getBlocks() map this iterates,
        // indirectly, via misanthrope_igniteBlock below.
        List<BlockPos> toIgnite = new ArrayList<>();
        for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry
                : contraption.getBlocks().entrySet()) {
            BlockPos localPos = entry.getKey();
            BlockState state = entry.getValue().state();
            if (state == null) continue;
            if (contraption.isBurning(localPos)) continue;
            if (!misanthrope_isBurnEligible(state)) continue;
            toIgnite.add(localPos);
        }

        for (BlockPos localPos : toIgnite) {
            misanthrope_igniteBlock(level, movedContraption, contraption, localPos);
        }
    }

    /**
     * A block is burn-eligible if it resolves to a non-heat-resistant sail
     * material. Vanilla cloth sails (canvas and frame alike — both
     * flammable, per Caelan's explicit "the frame is wooden, it's fucking
     * wood") resolve through the default-cloth path and are always eligible
     * unless a future {@code ISailMaterial} block overrides
     * {@code isHeatResistant()}.
     */
    private static boolean misanthrope_isBurnEligible(BlockState state) {
        boolean isSailShaped = state.getBlock() instanceof SailBlock
                || state.getBlock() instanceof ISailMaterial;
        if (!isSailShaped) return false;
        return !SailMaterialRegistry.resolve(state).heatResistant();
    }

    /**
     * First ignition: marks the bookkeeping at {@link #STAGE_SINGED} via
     * {@code igniteAt}, then immediately swaps the live block to the singed
     * visual and syncs to clients. Bookkeeping and visual swap happen
     * together here (unlike the later stage advances in
     * {@link #misanthrope_tickOneBlock}, which are timer-driven) since
     * ignition is itself the triggering event.
     */
    private void misanthrope_igniteBlock(
            Level level, ControlledContraptionEntity movedContraption,
            MisanthropeWindmillContraption contraption, BlockPos localPos) {

        StructureTemplate.StructureBlockInfo currentInfo = contraption.getBlocks().get(localPos);
        if (currentInfo == null) return;

        Direction facing = misanthrope_facingOf(currentInfo.state());
        BlockState singedState = STAGE_BLOCKS[STAGE_SINGED].get()
                .defaultBlockState().setValue(BlockStateProperties.FACING, facing);

        misanthrope_swapBlock(level, movedContraption, contraption, localPos, currentInfo, singedState);
        contraption.igniteAt(localPos, pickStageDuration(STAGE_SINGED, level));
    }

    // ── Per-block burn-down progression ──────────────────────────────────

    private void misanthrope_tickBurnProgression(
            Level level, ControlledContraptionEntity movedContraption,
            MisanthropeWindmillContraption contraption) {

        Map<BlockPos, Integer> stages = contraption.getBurnStagesView();
        if (stages.isEmpty()) return;

        // Filter out charred (terminal) blocks here rather than relying on
        // the early-return inside misanthrope_tickOneBlock — charred blocks
        // stay in burnStages forever now that the terminal stage is
        // permanent (see class doc comment), so without this filter every
        // fully-charred sail on every windmill in the world would cost one
        // wasted method call + map lookup, every tick, forever. Snapshotting
        // into a new list (rather than iterating the live view) is still
        // needed regardless, since misanthrope_tickOneBlock writes into
        // burnStages via advanceStage as it runs.
        List<BlockPos> burningPositions = new ArrayList<>();
        for (Map.Entry<BlockPos, Integer> entry : stages.entrySet()) {
            if (entry.getValue() < STAGE_CHARRED) {
                burningPositions.add(entry.getKey());
            }
        }

        for (BlockPos localPos : burningPositions) {
            misanthrope_tickOneBlock(level, movedContraption, contraption, localPos);
        }
    }

    private void misanthrope_tickOneBlock(
            Level level, ControlledContraptionEntity movedContraption,
            MisanthropeWindmillContraption contraption, BlockPos localPos) {

        // stage is guaranteed < STAGE_CHARRED here — the caller already
        // filtered terminal blocks out of burningPositions.
        int stage = contraption.getBurnStage(localPos);
        Map<BlockPos, Integer> ticksRemainingMap = contraption.getBurnTicksRemainingView();
        int ticksRemaining = ticksRemainingMap.getOrDefault(localPos, 0) - 1;

        if (ticksRemaining > 0) {
            ticksRemainingMap.put(localPos, ticksRemaining);
            return;
        }

        StructureTemplate.StructureBlockInfo currentInfo = contraption.getBlocks().get(localPos);
        if (currentInfo == null) {
            // Block already left the contraption by some other means —
            // nothing left to burn, just clear our own bookkeeping.
            contraption.clearBurn(localPos);
            return;
        }

        int nextStage = stage + 1;
        Direction facing = misanthrope_facingOf(currentInfo.state());
        BlockState nextState = STAGE_BLOCKS[nextStage].get()
                .defaultBlockState().setValue(BlockStateProperties.FACING, facing);

        misanthrope_swapBlock(level, movedContraption, contraption, localPos, currentInfo, nextState);

        if (nextStage == STAGE_CHARRED) {
            // Terminal — advance the stage marker. Future ticks won't even
            // reach this method for this position anymore, since
            // misanthrope_tickBurnProgression filters stage>=STAGE_CHARRED
            // out of burningPositions before the loop that calls this
            // method (see that method's doc comment). There is no "next
            // duration" to roll here (STAGE_DURATIONS[STAGE_CHARRED] is the
            // unused placeholder — see that array's doc comment); 0 is the
            // honest ticksRemaining value rather than a fake positive
            // number that implies more ticking will happen.
            contraption.advanceStage(localPos, STAGE_CHARRED, 0);
        } else {
            contraption.advanceStage(localPos, nextStage, pickStageDuration(nextStage, level));
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────

    private static Direction misanthrope_facingOf(BlockState state) {
        if (state != null && state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return Direction.UP;
    }

    /**
     * Mutates the live {@code getBlocks()} entry at {@code localPos} to
     * {@code newState} (constructing a new immutable {@code StructureBlockInfo}
     * record that preserves the original {@code pos}/{@code nbt}) and syncs
     * the change to nearby clients.
     */
    private void misanthrope_swapBlock(
            Level level, ControlledContraptionEntity movedContraption,
            MisanthropeWindmillContraption contraption, BlockPos localPos,
            StructureTemplate.StructureBlockInfo currentInfo, BlockState newState) {

        StructureTemplate.StructureBlockInfo newInfo =
                new StructureTemplate.StructureBlockInfo(currentInfo.pos(), newState, currentInfo.nbt());
        contraption.getBlocks().put(localPos, newInfo);

        misanthrope_sendBlockChange(level, movedContraption, localPos, newState);
    }

    /**
     * Confirmed signatures: {@code ContraptionBlockChangedPacket(int
     * entityID, BlockPos localPos, BlockState newState)} and
     * {@code AllPackets.sendToNear(Level, BlockPos, int radius, Object
     * packet)}. 64-block sync radius matches vanilla's typical entity
     * tracking range order of magnitude.
     */
    private void misanthrope_sendBlockChange(
            Level level, ControlledContraptionEntity movedContraption, BlockPos localPos, BlockState newState) {
        AllPackets.sendToNear(
                level,
                movedContraption.blockPosition(),
                64,
                new ContraptionBlockChangedPacket(movedContraption.getId(), localPos, newState)
        );
    }

    /**
     * Jitters a stage's base duration by ±25%, re-rolled independently at
     * every stage transition — see class doc comment's "ignition trigger"
     * section for why this compounding jitter (rather than a single
     * one-time roll at ignition) is what produces real spread across a
     * windmill's blades by the time they reach the later stages.
     */
    private static int pickStageDuration(int stage, Level level) {
        int baseTicks = STAGE_DURATIONS[stage];
        float jitter = 0.75f + level.getRandom().nextFloat() * 0.5f; // [0.75, 1.25)
        return Math.max(1, Math.round(baseTicks * jitter));
    }
}
