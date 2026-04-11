package exp.CCnewmods.mge.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * Singleton manager for the active {@link IWindProvider}.
 *
 * <p>At startup, {@link exp.CCnewmods.mge.compat.ProjectAtmosphereCompat} replaces
 * the default {@link NullWindProvider} with its own implementation if PA is loaded.</p>
 */
public final class WindProviderManager {

    private static IWindProvider provider = NullWindProvider.INSTANCE;

    private WindProviderManager() {}

    /** Called by compat modules during mod setup to register their wind implementation. */
    public static void setProvider(IWindProvider newProvider) {
        provider = newProvider;
    }

    /** Returns the wind vector at the given position using the active provider. */
    public static Vec3 getWind(LevelAccessor level, BlockPos pos) {
        return provider.getWindAt(level, pos);
    }
}
