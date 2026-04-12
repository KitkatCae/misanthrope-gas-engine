package exp.CCnewmods.mge.util;

import exp.CCnewmods.mge.Mge;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Static utilities callable from mixin-injected code.
 *
 * <p>Mixin classes live in a protected package — injected code cannot reference
 * other classes in that package directly (the mixin transformer raises
 * {@code IllegalClassLoadError}). Any helper logic shared between multiple
 * mixins must live outside the mixin package, here.</p>
 */
public final class AtmosphereUtil {

    private AtmosphereUtil() {}

    /**
     * Returns the MGE atmosphere block state if {@code state} is any vanilla air
     * variant, otherwise returns {@code state} unchanged.
     */
    public static BlockState replaceIfAir(BlockState state) {
        if (state.is(Blocks.AIR)
                || state.is(Blocks.CAVE_AIR)
                || state.is(Blocks.VOID_AIR)) {
            return Mge.ATMOSPHERE_BLOCK.get().defaultBlockState();
        }
        return state;
    }
}
