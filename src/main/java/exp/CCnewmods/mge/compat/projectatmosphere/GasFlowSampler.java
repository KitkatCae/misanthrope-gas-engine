package exp.CCnewmods.mge.compat.projectatmosphere;

import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.wind.WindProviderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Unified gas-flow sampler for windmill/turbine bearings.
 * <p>
 * ── What changed from the old wind-only model ──────────────────────────────
 * <p>
 * {@code WindmillWindIntegration} previously read Project Atmosphere's
 * {@code WeatherSnapshot} directly and treated "wind" as the only thing a
 * sail or fin could ever catch. This sampler replaces that with a single,
 * provider-agnostic read: bulk gas flow is wind ({@link WindProviderManager})
 * PLUS the pressure gradient already present in MGE's own
 * {@link EnvironmentGrid} — the same gas-grid that real steam, smoke, and
 * every other gas in the world actually lives in. A sail standing in front
 * of a venting boiler now catches that steam the same physical way it
 * catches open-air wind, with no separate "steam turbine" code path needed.
 * <p>
 * This means {@code WindmillBearingBlockEntity} (vanilla cloth sails today,
 * turbine fin blocks from a later step) all go through exactly this one
 * physics path — the block attached to the bearing only changes the
 * <em>multipliers</em> applied downstream (aerodynamic coefficient, heat/
 * corrosion resistance), never the sampling itself.
 * <p>
 * ── Flow model ──────────────────────────────────────────────────────────────
 * <p>
 * 1. WIND TERM — {@link WindProviderManager#getWind} at the bearing position,
 *    in block-space units per tick (same units {@link
 *    exp.CCnewmods.mge.grid.tick.SectionDiffusionTicker} already uses to bias
 *    diffusion). Zero when no wind provider is active (underground, PA not
 *    loaded, etc.) — see {@link exp.CCnewmods.mge.wind.NullWindProvider}.
 * <p>
 * 2. PRESSURE-GRADIENT TERM — derived the same way real diffusion derives
 *    transfer in {@code SectionDiffusionTicker.diffuseCell}: sample {@link
 *    GasComposition#totalPressure()} at the bearing and at one offset step
 *    along each axis, build a gradient vector pointing from high pressure to
 *    low pressure. A boiler venting steam nearby creates a local positive
 *    pressure differential strong enough to dominate the wind term entirely
 *    — exactly the "thick steam pressure should dominate over ambient wind"
 *    behaviour discussed when this design was agreed.
 * <p>
 * 3. COMBINE — the two terms are simply summed as vectors (not blended by a
 *    weight), since they're physically the same kind of quantity (bulk gas
 *    velocity) expressed in the same units; a strong localized pressure
 *    gradient naturally outweighs a weak ambient wind once they're added,
 *    without needing a tunable mixing factor.
 * <p>
 * 4. OCCLUSION — {@link WindObstructionSampler}'s existing swept-area /
 *    upwind-column geometry is reused completely unchanged. Obstruction is
 *    purely about physical geometry (is there a wall in the way), which
 *    doesn't care what gas is trying to get through.
 * <p>
 * 5. DRIVING PRESSURE — final scalar fed to the bearing's RPM is
 *    {@code |flowVector| * alignmentFactor * occlusionFactor}, where
 *    alignment is the same {@code |cos(theta)|} dot-product concept
 *    {@code WindmillWindIntegration} used, just measured against the combined
 *    flow vector instead of PA's wind angle alone.
 * <p>
 * ── What this sampler does NOT do ──────────────────────────────────────────
 * <p>
 * Altitude/storm bonuses live on {@link exp.CCnewmods.mge.wind.IWindProvider}
 * itself now (see {@code getAltitudeMultiplier}/{@code getStormMultiplier})
 * rather than here, so they apply uniformly to anything reading wind through
 * the provider, not just windmills. This sampler applies them as flat
 * multipliers on the combined scalar, same as the old code did.
 * <p>
 * Material reactivity (dampness, corrosion, ignition) is NOT computed here —
 * this sampler only reports what's physically present ({@link
 * SampleResult#composition}, {@link SampleResult#temperature}) so a later
 * pass can decide what a cloth sail vs. a steel fin does with that exposure.
 */
public final class GasFlowSampler {

    private GasFlowSampler() {
    }

    // ── Tunables ─────────────────────────────────────────────────────────────

    /**
     * Distance (blocks) at which the pressure gradient is sampled along each
     * axis. Mirrors a single diffusion-kernel hop, not a long-range scan —
     * the gradient close to the bearing is what actually drives the sails.
     */
    private static final int PRESSURE_SAMPLE_DISTANCE = 1;

    /**
     * Pressure differential (mbar) below which the gradient term is treated
     * as noise and ignored. Standard atmosphere has minor cell-to-cell
     * variance from diffusion rounding; this avoids that contributing a
     * phantom flow direction in perfectly calm, gas-uniform conditions.
     */
    private static final float PRESSURE_NOISE_FLOOR_MBAR = 0.5f;

    /**
     * Divisor converting a sampled mbar gradient over {@link
     * #PRESSURE_SAMPLE_DISTANCE} blocks into the same block-per-tick velocity
     * units {@link WindProviderManager#getWind} uses. Tuned so a vigorously
     * venting boiler (tens of mbar differential at close range) produces a
     * flow comparable in magnitude to a strong open-air wind (PA's
     * WIND_FULL_SPEED_MPS ~= 12), rather than either swamping the other by
     * orders of magnitude.
     */
    private static final float PRESSURE_TO_VELOCITY_SCALE = 1f / 40f;

    /**
     * Wind speed (block-units/tick) at which the wind term alone saturates
     * the alignment-independent magnitude factor to 1.0. Same role
     * {@code WindmillWindIntegration.WIND_FULL_SPEED_MPS} played, kept under
     * a new name since this now also has to make sense for a combined
     * wind+pressure vector, not wind alone.
     */
    private static final float FLOW_FULL_MAGNITUDE = 12f;

    /**
     * Minimum scalar in non-zero-airflow conditions — prevents complete
     * shutdown in dead calm so gently-placed contraptions don't freeze on
     * load. airflowFactor (occlusion) can still drive output to 0 for
     * buried windmills regardless of this floor.
     */
    private static final float FLOW_MINIMUM_SCALAR = 0.05f;

    /**
     * How many game ticks between obstruction recalculations per bearing.
     * 60 ticks = 3 seconds — terrain doesn't change that fast. Mirrors
     * {@code WindmillWindIntegration}'s original cache exactly, just scoped
     * to ONLY the occlusion geometry now — wind and gas pressure are read
     * live every call, since a venting boiler or a gust of wind picking up
     * is meant to be felt immediately, unlike a wall being built upwind.
     */
    private static final int OBSTRUCTION_CACHE_TICKS = 60;

    private record ObstructionCacheEntry(float airflowFactor, long expiresTick) {
    }

    // Keyed by BlockPos.asLong(). One entry per bearing.
    private static final java.util.Map<Long, ObstructionCacheEntry> OBSTRUCTION_CACHE
            = new java.util.HashMap<>();

    private static float getCachedOcclusion(Level level, BlockPos pos, Direction facing) {
        long key = pos.asLong();
        long now = level.getGameTime();

        ObstructionCacheEntry entry = OBSTRUCTION_CACHE.get(key);
        if (entry != null && now < entry.expiresTick()) {
            return entry.airflowFactor();
        }

        float fresh = WindObstructionSampler.computeAirflowFactor(level, pos, facing);
        OBSTRUCTION_CACHE.put(key, new ObstructionCacheEntry(fresh, now + OBSTRUCTION_CACHE_TICKS));

        // Prune stale entries if the cache grows too large
        if (OBSTRUCTION_CACHE.size() > 512) {
            OBSTRUCTION_CACHE.entrySet().removeIf(e -> now >= e.getValue().expiresTick());
        }

        return fresh;
    }

    /**
     * Evict the obstruction cache entry for a bearing position. Call from a
     * BlockEntityTick or block-break/neighbour-change event when the bearing
     * or surrounding terrain changes, so the next tick gets fresh obstruction
     * data. Same contract {@code WindmillWindIntegration.invalidateCache} had.
     */
    public static void invalidateCache(BlockPos pos) {
        OBSTRUCTION_CACHE.remove(pos.asLong());
    }

    // ── Result ───────────────────────────────────────────────────────────────

    /**
     * @param flowVector     Combined wind + pressure-gradient velocity,
     *                       block-space units per tick.
     * @param drivingScalar  Final RPM multiplier: magnitude * alignment *
     *                       occlusion * altitude * storm, floored at {@link
     *                       #FLOW_MINIMUM_SCALAR} scaled by occlusion. This
     *                       is the direct drop-in replacement for what
     *                       {@code WindmillWindIntegration.computeWindScalar}
     *                       used to return.
     * @param composition    Gas composition at the bearing position, for
     *                       material-reactivity checks (dampness, corrosion).
     * @param temperature    Ambient temperature (°C) at the bearing position,
     *                       NaN if no grid data — for ignition checks.
     */
    public record SampleResult(
            Vec3 flowVector,
            float drivingScalar,
            GasComposition composition,
            float temperature
    ) {
    }

    // ── Main entry point ────────────────────────────────────────────────────

    /**
     * Computes the combined gas-flow sample for a windmill/turbine bearing.
     * Drop-in replacement for {@code WindmillWindIntegration.computeWindScalar}
     * — same call shape ({@code level, pos, facing}) — but returns the full
     * sample so Part C's material reactivity can reuse the same read instead
     * of re-querying the grid a second time per tick.
     */
    public static SampleResult sample(Level level, BlockPos pos, Direction facing) {
        Vec3 windTerm = WindProviderManager.getWind(level, pos);
        Vec3 pressureTerm = computePressureGradient(level, pos);
        Vec3 flowVector = windTerm.add(pressureTerm);

        // ── Occlusion (geometry only — reused verbatim, cached) ─────────────
        float occlusion = getCachedOcclusion(level, pos, facing);
        if (occlusion <= 0f) {
            return new SampleResult(flowVector, 0f,
                    EnvironmentGrid.getComposition(level, pos),
                    EnvironmentGrid.getTemperature(level, pos));
        }

        // ── Alignment: |cos(theta)| between flow vector and facing axis ────
        float alignment = computeAlignmentFactor(facing, flowVector);

        // ── Magnitude factor: linear ramp to saturation ─────────────────────
        double magnitude = flowVector.length();
        float magnitudeFactor = (float) Math.min(1.0, magnitude / FLOW_FULL_MAGNITUDE);

        // ── Altitude / storm — provider-supplied, defaults to 1.0 ──────────
        float altitudeMultiplier = WindProviderManager.getAltitudeMultiplier(level, pos);
        float stormMultiplier = WindProviderManager.getStormMultiplier(level, pos);

        float scalar = magnitudeFactor * alignment * occlusion
                * altitudeMultiplier * stormMultiplier;
        scalar = Math.max(FLOW_MINIMUM_SCALAR * occlusion, scalar);

        return new SampleResult(flowVector, scalar,
                EnvironmentGrid.getComposition(level, pos),
                EnvironmentGrid.getTemperature(level, pos));
    }

    // ── Pressure gradient ────────────────────────────────────────────────────

    /**
     * Derives a velocity vector from the local total-pressure gradient
     * around {@code pos}, the same conceptual approach
     * {@code SectionDiffusionTicker.diffuseCell} uses to find where a gas
     * should transfer to — except here we want the gradient itself as a
     * direction, not a per-neighbour transfer amount.
     * <p>
     * Samples total pressure one block in each of the six axis directions,
     * builds a vector pointing from the highest-pressure neighbour toward
     * the lowest (i.e. the direction gas would actually flow), scaled by
     * how strong that differential is.
     */
    private static Vec3 computePressureGradient(Level level, BlockPos pos) {
        float centerP = EnvironmentGrid.getComposition(level, pos).totalPressure();

        float dxPos = samplePressureDelta(level, pos, Direction.EAST, centerP);
        float dxNeg = samplePressureDelta(level, pos, Direction.WEST, centerP);
        float dyPos = samplePressureDelta(level, pos, Direction.UP, centerP);
        float dyNeg = samplePressureDelta(level, pos, Direction.DOWN, centerP);
        float dzPos = samplePressureDelta(level, pos, Direction.SOUTH, centerP);
        float dzNeg = samplePressureDelta(level, pos, Direction.NORTH, centerP);

        // Gradient component per axis: gas flows from high pressure to low.
        // dxPos = how much LOWER the +X neighbour is than centre (positive =
        // east is lower, so flow should point toward +X). dxNeg = the same
        // for the -X neighbour. The net pull toward +X is "how much east
        // wants it" minus "how much west wants it": gx = dxPos - dxNeg.
        // Verified against two cases: east 20 mbar lower (west unremarkable)
        // -> gx = +20 (flow toward +X, correct); west 30 mbar lower (east
        // unremarkable) -> gx = -30 (flow toward -X, correct).
        float gx = dxPos - dxNeg;
        float gy = dyPos - dyNeg;
        float gz = dzPos - dzNeg;

        Vec3 gradient = new Vec3(gx, gy, gz);
        if (gradient.lengthSqr() < 1e-6) return Vec3.ZERO;

        return gradient.scale(PRESSURE_TO_VELOCITY_SCALE);
    }

    /**
     * Returns {@code centerPressure - neighbourPressure} along one direction,
     * or 0 if the differential is within the noise floor.
     */
    private static float samplePressureDelta(Level level, BlockPos pos, Direction dir, float centerP) {
        BlockPos neighbourPos = pos.relative(dir, PRESSURE_SAMPLE_DISTANCE);
        if (!level.isLoaded(neighbourPos)) return 0f;
        float neighbourP = EnvironmentGrid.getComposition(level, neighbourPos).totalPressure();
        float delta = centerP - neighbourP;
        return Math.abs(delta) < PRESSURE_NOISE_FLOOR_MBAR ? 0f : delta;
    }

    // ── Alignment ────────────────────────────────────────────────────────────

    /**
     * Returns a [0, 1] alignment factor: how well the bearing's facing axis
     * is aligned with the combined flow vector's direction. Same shape as
     * {@code WindmillWindIntegration.computeAlignmentFactor}, but measured
     * against the actual flow vector instead of reconstructing a direction
     * from a separate angle scalar — there's no PA-specific angle convention
     * to convert here since {@link Vec3} is already in world XZ space.
     * <p>
     * Vertical-axis bearings (FACING = UP/DOWN) return 1.0 — they catch flow
     * from all horizontal directions equally, same as before.
     */
    private static float computeAlignmentFactor(Direction facing, Vec3 flowVector) {
        if (facing.getAxis() == Direction.Axis.Y) return 1.0f;
        if (flowVector.lengthSqr() < 1e-6) return 0f;

        Vec3 horizontalFlow = new Vec3(flowVector.x, 0, flowVector.z);
        if (horizontalFlow.lengthSqr() < 1e-6) return 0f;
        horizontalFlow = horizontalFlow.normalize();

        float facingDx = facing.getStepX();
        float facingDz = facing.getStepZ();

        float dot = (float) Math.abs(facingDx * horizontalFlow.x + facingDz * horizontalFlow.z);
        return Math.min(1.0f, Math.max(0.0f, dot));
    }
}
