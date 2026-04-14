package exp.CCnewmods.mge.mixin;

import exp.CCnewmods.mge.breathing.ActiveBreathingHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link BlockBehaviour#randomTick} to feed photosynthetic block ticks
 * into {@link ActiveBreathingHandler#onPlantRandomTick}.
 *
 * <p>In 1.20.1, {@code randomTick} is declared on {@link BlockBehaviour}, not
 * {@link net.minecraft.world.level.block.Block}. Targeting {@code Block} fails
 * because the method doesn't exist there — it's only inherited. The mixin must
 * target the declaring class.</p>
 */
@Mixin(BlockBehaviour.class)
public abstract class MixinRandomTick {

    @Inject(
            method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD")
    )
    private void mge$onRandomTick(BlockState state, ServerLevel level,
                                   BlockPos pos, RandomSource random,
                                   CallbackInfo ci) {
        ActiveBreathingHandler.onPlantRandomTick(state, level, pos);
        // Oreganized ore continuous emission
        exp.CCnewmods.mge.compat.OreganizedCompat.onOreRandomTick(state, level, pos);
    }
}
