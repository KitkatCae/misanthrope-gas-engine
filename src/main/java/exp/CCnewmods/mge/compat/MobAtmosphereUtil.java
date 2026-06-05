package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Static utility helpers for mob compat classes.
 *
 * <p>All compat files do a {@code import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;}
 * and then call these methods directly with short names, keeping the compat code readable.</p>
 *
 * <h3>Single-cell methods</h3>
 * <pre>
 *   gas(level, pos, GasRegistry.SULFUR_DIOXIDE, 20f)   // inject 20 mbar SO₂ at pos
 *   drain(level, pos, GasRegistry.OXYGEN, 30f)          // remove up to 30 mbar O₂
 *   part(level, pos, ParticulateType.ASH_CLOUD, 80f)    // inject 80 mg/m³ ash
 * </pre>
 *
 * <h3>Radius methods</h3>
 * <pre>
 *   gasRadius(level, pos, GasRegistry.BLAZE_FUME, 15f, 3)   // inject 15 mbar in radius 3
 *   drainRadius(level, pos, GasRegistry.OXYGEN, 40f, 2)      // drain up to 40 mbar in radius 2
 *   partRadius(level, pos, ParticulateType.SOOT, 50f, 2)     // inject 50 mg/m³ in radius 2
 * </pre>
 *
 * <p>All radius methods apply a linear falloff from full-strength at the centre to
 * zero at the edge, so the total atmospheric impact is proportional to radius.</p>
 */
public final class MobAtmosphereUtil {

    private MobAtmosphereUtil() {}

    // ── Single-cell ───────────────────────────────────────────────────────────

    /**
     * Inject {@code mbar} of {@code gas} into the cell at {@code pos}.
     */
    public static void gas(ServerLevel level, BlockPos pos, Gas gas, float mbar) {
        GridAtmosphereCompat.addGas(level, pos, gas, mbar);
    }

    /**
     * Remove up to {@code mbar} of {@code gas} from the cell at {@code pos}.
     * Clamps to the amount present — never goes negative.
     */
    public static void drain(ServerLevel level, BlockPos pos, Gas gas, float mbar) {
        float present = GridAtmosphereCompat.getGas(level, pos, gas);
        GridAtmosphereCompat.addGas(level, pos, gas, -Math.min(present, mbar));
    }

    /**
     * Inject {@code mgM3} of {@code type} particulate into the cell at {@code pos}.
     */
    public static void part(ServerLevel level, BlockPos pos, ParticulateType type, float mgM3) {
        GridAtmosphereCompat.addParticulate(level, pos, type, mgM3);
    }

    // ── Radius — gas ─────────────────────────────────────────────────────────

    /**
     * Inject {@code mbar} of {@code gas} into all cells within {@code radius} blocks
     * of {@code centre}, with linear falloff to zero at the edge.
     */
    public static void gasRadius(ServerLevel level, BlockPos centre,
                                  Gas gas, float mbar, int radius) {
        iterRadius(centre, radius, (pos, falloff) ->
                GridAtmosphereCompat.addGas(level, pos, gas, mbar * falloff));
    }

    /**
     * Remove up to {@code mbar} of {@code gas} from all cells within {@code radius},
     * with linear falloff. Clamps per-cell to amount present.
     */
    public static void drainRadius(ServerLevel level, BlockPos centre,
                                    Gas gas, float mbar, int radius) {
        iterRadius(centre, radius, (pos, falloff) -> {
            float amount = mbar * falloff;
            float present = GridAtmosphereCompat.getGas(level, pos, gas);
            GridAtmosphereCompat.addGas(level, pos, gas, -Math.min(present, amount));
        });
    }

    // ── Radius — particulate ──────────────────────────────────────────────────

    /**
     * Inject {@code mgM3} of {@code type} particulate into all cells within
     * {@code radius}, with linear falloff.
     */
    public static void partRadius(ServerLevel level, BlockPos centre,
                                   ParticulateType type, float mgM3, int radius) {
        iterRadius(centre, radius, (pos, falloff) ->
                GridAtmosphereCompat.addParticulate(level, pos, type, mgM3 * falloff));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface RadiusAction {
        void apply(BlockPos pos, float falloff);
    }

    /**
     * Iterates all cells within a spherical radius of {@code centre} and calls
     * {@code action} with each pos and a linear falloff factor (1.0 at centre,
     * 0.0 at edge, exclusive — cells at exactly radius+1 are skipped).
     */
    private static void iterRadius(BlockPos centre, int radius, RadiusAction action) {
        float r = radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > r) continue;
                    float falloff = 1f - (dist / (r + 1f));
                    action.apply(centre.offset(dx, dy, dz), falloff);
                }
            }
        }
    }
}
