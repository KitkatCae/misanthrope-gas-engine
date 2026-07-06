package exp.CCnewmods.mge.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * Provides a wind vector at a given block position.
 *
 * <p>MGE never imports Project Atmosphere directly. Instead, the compat layer
 * ({@link exp.CCnewmods.mge.compat.ProjectAtmosphereCompat}) implements this
 * interface by delegating to PA's API, and {@link NullWindProvider} is used
 * when PA is not present.</p>
 *
 * <p>Wind vectors are in block-space units per tick. A magnitude of 1.0 means
 * one block of displacement per tick at 100% wind sensitivity.</p>
 */
public interface IWindProvider {

    /**
     * Returns the wind vector at the given position.
     *
     * @param level The level (world) being queried.
     * @param pos   The block position.
     * @return Wind velocity as a {@link Vec3}. Must never be null — return {@link Vec3#ZERO}
     *         when wind data is unavailable.
     */
    Vec3 getWindAt(LevelAccessor level, BlockPos pos);

    /**
     * Returns an altitude-based multiplier on wind strength at the given
     * position — e.g. a jet-stream bonus at high Y levels. Applied
     * multiplicatively on top of {@link #getWindAt}'s magnitude by callers
     * that want altitude effects (windmill/turbine RPM, sail wear, etc.).
     *
     * <p>Providers with no altitude model (most of them — this is a niche,
     * PA-specific weather feature) should not override this; the default
     * of {@code 1.0f} (no bonus, no penalty) is correct for them.
     *
     * @return Multiplier, normally &gt;= 1.0. Default 1.0 (no altitude effect).
     */
    default float getAltitudeMultiplier(LevelAccessor level, BlockPos pos) {
        return 1.0f;
    }

    /**
     * Returns a storm-intensity multiplier on wind strength at the given
     * position. Applied multiplicatively on top of {@link #getWindAt}'s
     * magnitude by callers that want storm effects.
     *
     * <p>Providers with no storm/weather-event model should not override
     * this; the default of {@code 1.0f} (no bonus) is correct for them.
     *
     * @return Multiplier, normally &gt;= 1.0. Default 1.0 (no storm effect).
     */
    default float getStormMultiplier(LevelAccessor level, BlockPos pos) {
        return 1.0f;
    }
}
