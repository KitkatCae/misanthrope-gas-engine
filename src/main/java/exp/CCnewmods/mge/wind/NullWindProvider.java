package exp.CCnewmods.mge.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * Fallback {@link IWindProvider} used when Project Atmosphere is not loaded.
 * Always returns {@link Vec3#ZERO} — gas propagation will be purely density-driven.
 */
public final class NullWindProvider implements IWindProvider {

    public static final NullWindProvider INSTANCE = new NullWindProvider();

    private NullWindProvider() {}

    @Override
    public Vec3 getWindAt(LevelAccessor level, BlockPos pos) {
        return Vec3.ZERO;
    }
}
