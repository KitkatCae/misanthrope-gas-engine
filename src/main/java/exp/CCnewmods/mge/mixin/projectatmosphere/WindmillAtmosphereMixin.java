package exp.CCnewmods.mge.mixin.projectatmosphere;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import exp.CCnewmods.mge.compat.projectatmosphere.GasFlowSampler;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.sail.IBearingMaterialAccess;
import exp.CCnewmods.mge.sail.SailMaterialRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WindmillBearingBlockEntity.class, remap = false)
public class WindmillAtmosphereMixin {

    /**
     * Water-vapor partial pressure (mbar) above which a sail starts losing
     * speed to dampness. ~10 mbar is roughly the dry-standard-atmosphere
     * baseline (see {@code GasRegistry}'s default atmosphere comment), so
     * this threshold is well above ambient humidity — only rain, steam, or
     * genuinely wet/foggy conditions should trigger it.
     */
    private static final float DAMPNESS_THRESHOLD_MBAR = 40f;

    /**
     * mbar of water vapor above {@link #DAMPNESS_THRESHOLD_MBAR} at which
     * dampness reaches its maximum penalty. Linear ramp between the
     * threshold and this ceiling.
     */
    private static final float DAMPNESS_SATURATION_MBAR = 400f;

    /**
     * Maximum fractional speed loss from dampness alone (0–1). A fully
     * sodden cloth sail never drops below {@code 1 - DAMPNESS_MAX_PENALTY}
     * of its dry speed — heavy and sluggish, not completely inert, since
     * wind pressure is still physically pushing on the wet fabric.
     */
    private static final float DAMPNESS_MAX_PENALTY = 0.5f;

    /**
     * Intercepts the generated-speed return value and applies a flow scalar
     * derived from {@link GasFlowSampler} — combined wind (via the
     * provider-agnostic {@code WindProviderManager}) and MGE gas-grid
     * pressure-differential flow at this block's position — then layers on
     * two material-dependent effects: aerodynamic coefficient (better fins
     * spin faster) and dampness (water vapor in the swept area slows any
     * sail down, cloth and fin alike).
     * <p>
     * The flow scalar accounts for:
     * - Combined wind + local gas-pressure-gradient flow magnitude
     * - Flow direction vs. bearing facing (alignment dot product)
     * - Altitude jet-stream bonus and storm intensity bonus (provider-supplied)
     * - Physical obstruction of the upwind column and swept plane
     * - Underground burial (buried = 0 output)
     * <p>
     * This single read replaces the old PA-only wind read — a bearing sitting
     * in front of a venting boiler now spins from that steam's pressure
     * differential the same physical way it used to spin from open-air wind,
     * with no separate code path.
     * <p>
     * <b>Corrosion and ignition are NOT applied here.</b> Corrosion needs
     * Part A's persistent {@code CorrosionStateMap} and a real per-block
     * position to accumulate against, and ignition needs the not-yet-built
     * custom contraption's burn-state machine — neither is meaningful for a
     * detached, spinning contraption block the way dampness and aerodynamics
     * are (both of those only need a read, not a position to write back to).
     * See this build's handoff doc for the full plan.
     */
    @Inject(
            method = "getGeneratedSpeed()F",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void misanthrope_applyFlowScalar(CallbackInfoReturnable<Float> cir) {
        float baseSpeed = cir.getReturnValueF();
        if (baseSpeed == 0f) return;

        WindmillBearingBlockEntity self = (WindmillBearingBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return;

        BlockPos pos = self.getBlockPos();
        BlockState state = self.getBlockState();

        Direction facing = Direction.UP; // fallback for unexpected states
        if (state.hasProperty(BlockStateProperties.FACING)) {
            facing = state.getValue(BlockStateProperties.FACING);
        }

        GasFlowSampler.SampleResult sample = GasFlowSampler.sample(level, pos, facing);

        float aerodynamicCoefficient = misanthrope_resolveAerodynamicCoefficient(self);
        float dampnessFactor = misanthrope_computeDampnessFactor(sample);

        cir.setReturnValue(baseSpeed * sample.drivingScalar() * aerodynamicCoefficient * dampnessFactor);
    }

    /**
     * Reads the windmill-wide material profile tallied at assembly time (see
     * {@code MixinBearingContraptionMaterial}), if the moved contraption is
     * present and implements {@link IBearingMaterialAccess}. Falls back to
     * the all-cloth default ({@code 1.0}) for a windmill that hasn't
     * assembled yet, isn't currently moving a contraption, or whose
     * contraption type doesn't carry a material profile for some reason —
     * all of which should degrade to vanilla-equivalent behaviour rather
     * than throwing.
     */
    private static float misanthrope_resolveAerodynamicCoefficient(WindmillBearingBlockEntity self) {
        var movedContraption = self.getMovedContraption();
        if (movedContraption == null) return SailMaterialRegistry.DEFAULT_AERODYNAMIC_COEFFICIENT;

        Contraption contraption = movedContraption.getContraption();
        if (!(contraption instanceof IBearingMaterialAccess access)) {
            return SailMaterialRegistry.DEFAULT_AERODYNAMIC_COEFFICIENT;
        }

        return access.misanthrope_getMaterialProfile().aerodynamicCoefficient();
    }

    /**
     * Computes the dampness speed-penalty factor from the sampled water-vapor
     * partial pressure at the bearing's swept area. Linear ramp from {@code
     * 1.0} (dry) at {@link #DAMPNESS_THRESHOLD_MBAR} down to
     * {@code 1.0 - DAMPNESS_MAX_PENALTY} (saturated) at
     * {@link #DAMPNESS_SATURATION_MBAR}.
     * <p>
     * Applies identically to every sail material today — there is no
     * per-material dampness resistance yet. A future fin material could
     * reasonably resist dampness (sealed metal vs. absorbent cloth), but
     * that's not part of {@link exp.CCnewmods.mge.sail.ISailMaterial} as
     * currently scoped; flagged for a future pass rather than added
     * speculatively now.
     */
    private static float misanthrope_computeDampnessFactor(GasFlowSampler.SampleResult sample) {
        float waterVaporMbar = sample.composition().get(GasRegistry.WATER_VAPOR);
        if (waterVaporMbar <= DAMPNESS_THRESHOLD_MBAR) return 1.0f;

        float t = Math.min(1.0f,
                (waterVaporMbar - DAMPNESS_THRESHOLD_MBAR)
                        / (DAMPNESS_SATURATION_MBAR - DAMPNESS_THRESHOLD_MBAR));
        return 1.0f - t * DAMPNESS_MAX_PENALTY;
    }
}
