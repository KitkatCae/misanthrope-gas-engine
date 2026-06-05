package exp.CCnewmods.mge.grid.compat;

import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Drop-in replacements for the old AtmosphereBlockEntity-based read/write
 * patterns used throughout the existing MGE event handlers.
 *
 * Every site that previously did:
 *   BlockEntity be = level.getBlockEntity(pos);
 *   if (!(be instanceof AtmosphereBlockEntity atm)) return;
 *   atm.getComposition().add(gas, mbar);
 *   atm.setComposition(atm.getComposition());
 *   Mge.getScheduler(level).enqueue(pos);
 *
 * Can now be replaced with:
 *   GridAtmosphereCompat.addGas(level, pos, gas, mbar);
 *
 * This class handles the enqueue automatically.
 *
 * ── Migration plan ────────────────────────────────────────────────────────────
 * Phase 1 (current): these methods exist alongside the old AtmosphereBlockEntity
 * code.  Old code continues to work via the backward-compat proxy in
 * AtmosphereBlockEntity, which now delegates to the grid.
 *
 * Phase 2: replace all call sites in WorldEventHandler, VacuumHandler,
 * CaveGasAccumulator, ActiveBreathingHandler etc. with these methods.
 *
 * Phase 3: delete AtmosphereBlockEntity and AtmosphereBlock.
 */
public final class GridAtmosphereCompat {

    private GridAtmosphereCompat() {}

    // ── Gas operations ────────────────────────────────────────────────────────

    public static float getGas(Level level, BlockPos pos, Gas gas) {
        return EnvironmentGrid.getGas(level, pos, gas);
    }

    public static GasComposition getComposition(Level level, BlockPos pos) {
        return EnvironmentGrid.getComposition(level, pos);
    }

    public static void setGas(Level level, BlockPos pos, Gas gas, float mbar) {
        EnvironmentGrid.setGas(level, pos, gas, mbar);
    }

    public static void addGas(Level level, BlockPos pos, Gas gas, float mbar) {
        EnvironmentGrid.addGas(level, pos, gas, mbar);
    }

    public static void setComposition(Level level, BlockPos pos, GasComposition comp) {
        EnvironmentGrid.setComposition(level, pos, comp);
    }

    /**
     * Apply a full GasComposition delta to a position — adds each gas amount.
     */
    public static void addComposition(Level level, BlockPos pos, GasComposition delta) {
        for (String key : delta.getTag().getAllKeys()) {
            exp.CCnewmods.mge.gas.GasRegistry.get(key).ifPresent(gas ->
                    addGas(level, pos, gas, delta.get(key)));
        }
    }

    // ── Particulate operations ────────────────────────────────────────────────
    // Particulatess are not yet migrated to the grid — they remain in
    // AtmosphereBlockEntity for now, forwarded from here for easy future migration.

    public static ParticulateComposition getParticulates(Level level, BlockPos pos) {
        return EnvironmentGrid.getParticulates(level, pos);
    }

    public static void addParticulate(Level level, BlockPos pos, ParticulateType type, float mgM3) {
        EnvironmentGrid.addParticulate(level, pos, type, mgM3);
    }

    public static void setParticulates(Level level, BlockPos pos, ParticulateComposition comp) {
        EnvironmentGrid.setParticulates(level, pos, comp);
    }

    // ── Pressure helpers ──────────────────────────────────────────────────────

    public static float getTotalPressure(Level level, BlockPos pos) {
        return getComposition(level, pos).totalPressure();
    }

    public static boolean isVacuum(Level level, BlockPos pos) {
        return getTotalPressure(level, pos) < exp.CCnewmods.mge.vacuum.VacuumHandler.VACUUM_THRESHOLD_MBAR;
    }

    public static float getO2Mbar(Level level, BlockPos pos) {
        return getGas(level, pos, exp.CCnewmods.mge.gas.GasRegistry.OXYGEN);
    }

    public static boolean isBreathable(Level level, BlockPos pos) {
        return getO2Mbar(level, pos) >= exp.CCnewmods.mge.MgeConfig.o2BreathableThresholdMbar;
    }
}
