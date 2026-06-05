package exp.CCnewmods.mge.mixin.projectatmosphere;

import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import exp.CCnewmods.mge.compat.projectatmosphere.WindmillWindIntegration;
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
     * Intercepts the generated-speed return value and applies a wind scalar
     * derived from Project Atmosphere's weather data at this block's position.
     * <p>
     * The scalar accounts for:
     * - Local wind speed (PA WeatherSnapshot.windSpeedMps)
     * - Wind direction vs. bearing facing (alignment dot product)
     * - Altitude jet-stream bonus (linear ramp Y 128 → 220)
     * - Storm intensity bonus
     * - Physical obstruction of the upwind column and swept plane
     * - Underground burial (buried = 0 output)
     */
    @Inject(
            method = "getGeneratedSpeed()F",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void misanthrope_applyWindScalar(CallbackInfoReturnable<Float> cir) {
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

        float scalar = WindmillWindIntegration.computeWindScalar(level, pos, facing);
        cir.setReturnValue(baseSpeed * scalar);
    }
}
