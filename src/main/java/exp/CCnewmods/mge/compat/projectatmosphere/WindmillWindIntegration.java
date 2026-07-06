package exp.CCnewmods.mge.compat.projectatmosphere;

import net.Gabou.projectatmosphere.api.AtmoApi;
import net.Gabou.projectatmosphere.api.WeatherSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated SUPERSEDED by {@link GasFlowSampler} as of the gas-flow unification
 * pass. Nothing in the codebase calls this class anymore — both mixins
 * ({@code WindmillAtmosphereMixin}, {@code WindmillNeighborMixin}) were
 * repointed at {@code GasFlowSampler}. Left in place rather than deleted
 * since it's a complete, working reference implementation of the old
 * PA-only wind model and may be useful to compare against. Safe to delete
 * once you've confirmed GasFlowSampler's behaviour matches expectations
 * in-game.
 * <p>
 * All Project Atmosphere wind math for the windmill integration.
 * <p>
 * Separated from the Mixin so this class can be guarded behind a
 * ModList.isLoaded("projectatmosphere") check at load time without
 * forcing the Mixin to reference PA classes directly.
 * <p>
 * ── Wind model ──────────────────────────────────────────────────────────────
 * <p>
 * The final RPM scalar is:
 * <p>
 * scalar = baseWindFactor * alignmentFactor * altitudeMultiplier
 * * stormMultiplier * airflowFactor
 * <p>
 * baseWindFactor:
 * Linear ramp from 0 at 0 m/s to 1.0 at WIND_FULL_SPEED_MPS (default 12 m/s).
 * A windmill in dead calm produces no power.
 * <p>
 * alignmentFactor:
 * |cos(θ)| where θ is the angle between wind direction and the windmill's
 * facing axis.  A windmill aimed directly into the wind = 1.0; perpendicular = 0.
 * <p>
 * altitudeMultiplier:
 * Linear ramp from 1.0 at JET_STREAM_BASE_Y (default 128) to
 * JET_STREAM_PEAK_MULTIPLIER (default 3.0) at JET_STREAM_PEAK_Y (default 220).
 * Represents the jet stream — high altitude = faster, more consistent wind.
 * <p>
 * stormMultiplier:
 * Flat multiplier when PA reports isStorming.  Default 2.0.
 * <p>
 * airflowFactor:
 * Computed by WindObstructionSampler.  Accounts for:
 * - Terrain / structures directly upwind (most impactful)
 * - Partial occlusion of the swept plane (arms inside walls, etc.)
 * - Underground burial (returns 0 completely)
 * Cached for OBSTRUCTION_CACHE_TICKS ticks to avoid per-tick world scanning.
 * <p>
 * ────────────────────────────────────────────────────────────────────────────
 */
@Deprecated
public final class WindmillWindIntegration {

    private WindmillWindIntegration() {
    }

    // ── Tunables ─────────────────────────────────────────────────────────────

    /**
     * Wind speed (m/s) at which the wind factor saturates to 1.0.
     */
    private static final float WIND_FULL_SPEED_MPS = 12f;

    /**
     * Minimum scalar in non-zero-airflow conditions — prevents complete
     * shutdown in dead calm so gently-placed contraptions don't freeze on load.
     * Note: airflowFactor can still drive output to 0 for buried windmills.
     */
    private static final float WIND_MINIMUM_SCALAR = 0.05f;

    /**
     * Y level at which the altitude bonus starts. Below this = ×1.0.
     */
    private static final int JET_STREAM_BASE_Y = 128;

    /**
     * Y level at which the altitude bonus peaks.
     */
    private static final int JET_STREAM_PEAK_Y = 220;

    /**
     * Maximum altitude multiplier (at or above JET_STREAM_PEAK_Y).
     */
    private static final float JET_STREAM_PEAK_MULTIPLIER = 3.0f;

    /**
     * Additional multiplier applied during a storm.
     */
    private static final float STORM_MULTIPLIER = 2.0f;

    /**
     * How many game ticks between obstruction recalculations per windmill.
     * 60 ticks = 3 seconds.  Terrain doesn't change that fast.
     */
    private static final int OBSTRUCTION_CACHE_TICKS = 60;

    // ── Obstruction cache ─────────────────────────────────────────────────────

    private record CacheEntry(float airflowFactor, long expiresTick) {
    }

    // Keyed by BlockPos.asLong().  One entry per bearing.
    private static final Map<Long, CacheEntry> CACHE = new HashMap<>();

    // ── Main entry point ─────────────────────────────────────────────────────

    /**
     * Computes the wind scalar to multiply the windmill's base RPM by.
     *
     * @param level  The server level.
     * @param pos    The windmill bearing's block position.
     * @param facing The Direction the bearing block is facing (its rotation axis).
     * @return A float scalar; 0 for buried/blocked, >0 for exposed windmills.
     */
    public static float computeWindScalar(Level level, BlockPos pos, Direction facing) {
        // AtmoApi.getCurrentWeather requires a ServerLevel.
        // The mixin already guards isClientSide(), but we cast here defensively.
        if (!(level instanceof ServerLevel serverLevel)) return 1.0f;

        WeatherSnapshot snapshot;
        try {
            AtmoApi api = AtmoApi.getInstance();
            snapshot = api.getCurrentWeather(serverLevel, pos);
        } catch (Exception e) {
            return 1.0f;
        }
        if (snapshot == null) return 1.0f;

        // ── 1. Airflow / obstruction factor (cached) ──────────────────────────
        // Check this first — if completely buried we can short-circuit everything.
        float airflowFactor = getCachedAirflow(level, pos, facing);
        if (airflowFactor <= 0f) return 0f;

        // ── 2. Base wind factor ───────────────────────────────────────────────
        float windSpeedMps = snapshot.windSpeedMps();
        float baseWindFactor = Math.min(1.0f, windSpeedMps / WIND_FULL_SPEED_MPS);

        // ── 3. Alignment factor ───────────────────────────────────────────────
        float windAngleRad = snapshot.windAngleRad();
        float alignmentFactor = computeAlignmentFactor(facing, windAngleRad);

        // ── 4. Altitude multiplier ────────────────────────────────────────────
        float altitudeMultiplier = computeAltitudeMultiplier(pos.getY());

        // ── 5. Storm multiplier ───────────────────────────────────────────────
        float stormMultiplier = snapshot.isStorming() ? STORM_MULTIPLIER : 1.0f;

        // ── 6. Compose ────────────────────────────────────────────────────────
        float scalar = baseWindFactor * alignmentFactor * altitudeMultiplier
                * stormMultiplier * airflowFactor;

        // Minimum floor is scaled by airflow so a partially-obstructed windmill
        // still has a lower floor than a fully-exposed one in calm conditions.
        return Math.max(WIND_MINIMUM_SCALAR * airflowFactor, scalar);
    }

    // ── Obstruction cache management ─────────────────────────────────────────

    private static float getCachedAirflow(Level level, BlockPos pos, Direction facing) {
        long key = pos.asLong();
        long now = level.getGameTime();

        CacheEntry entry = CACHE.get(key);
        if (entry != null && now < entry.expiresTick()) {
            return entry.airflowFactor();
        }

        float fresh = WindObstructionSampler.computeAirflowFactor(level, pos, facing);
        CACHE.put(key, new CacheEntry(fresh, now + OBSTRUCTION_CACHE_TICKS));

        // Prune stale entries if the cache grows too large
        if (CACHE.size() > 512) {
            CACHE.entrySet().removeIf(e -> now >= e.getValue().expiresTick());
        }

        return fresh;
    }

    /**
     * Evict the cache entry for a bearing position.
     * Call from a BlockEntityTick or block-break event when the bearing or
     * surrounding terrain changes, so the next tick gets fresh obstruction data.
     */
    public static void invalidateCache(BlockPos pos) {
        CACHE.remove(pos.asLong());
    }

    // ── Wind math helpers ─────────────────────────────────────────────────────

    /**
     * Returns a [0, 1] alignment factor: how well the windmill's facing axis
     * is aligned with the incoming wind direction.
     * <p>
     * PA wind angle: 0 rad = wind FROM north (blowing south), clockwise.
     * We convert to the wind's travel vector and dot it with the facing axis.
     * <p>
     * Vertical-axis windmills (FACING = UP/DOWN) return 1.0 — they catch wind
     * from all horizontal directions equally.
     */
    private static float computeAlignmentFactor(Direction facing, float windAngleRad) {
        if (facing.getAxis() == Direction.Axis.Y) return 1.0f;

        // Wind travel vector: angle 0 = FROM north = blowing SOUTH (+Z)
        // Clockwise: π/2 = FROM east = blowing west (-X)
        float windDx = -(float) Math.sin(windAngleRad);
        float windDz = (float) Math.cos(windAngleRad);

        float facingDx = facing.getStepX();
        float facingDz = facing.getStepZ();

        float dot = Math.abs(facingDx * windDx + facingDz * windDz);
        return Math.min(1.0f, Math.max(0.0f, dot));
    }

    /**
     * Linear ramp from 1.0 at base Y to peak multiplier at peak Y.
     */
    private static float computeAltitudeMultiplier(int y) {
        if (y <= JET_STREAM_BASE_Y) return 1.0f;
        if (y >= JET_STREAM_PEAK_Y) return JET_STREAM_PEAK_MULTIPLIER;
        float t = (float) (y - JET_STREAM_BASE_Y) / (float) (JET_STREAM_PEAK_Y - JET_STREAM_BASE_Y);
        return 1.0f + t * (JET_STREAM_PEAK_MULTIPLIER - 1.0f);
    }
}
