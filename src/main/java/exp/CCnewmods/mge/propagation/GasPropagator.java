package exp.CCnewmods.mge.propagation;

import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.wind.WindProviderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles displacement of gases AND particulates when a solid block is placed
 * into an atmosphere block position.
 *
 * <p>Both the {@link GasComposition} and {@link ParticulateComposition} of the
 * displaced atmosphere block are redistributed across all 26 neighbours in a
 * 3×3×3 cube, weighted by inverse-square distance, gas density / particulate
 * settle behaviour, and the wind vector at that position.</p>
 *
 * <p>Particulates use the same weight calculation as gases but with an additional
 * downward bias based on {@link ParticulateType.SettleBehaviour} — heavy
 * particulates (INSTANT/FAST) strongly prefer the downward neighbours.</p>
 */
public final class GasPropagator {

    private static final int[][] NEIGHBOURS_26;

    static {
        List<int[]> offsets = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    offsets.add(new int[]{dx, dy, dz});
                }
        NEIGHBOURS_26 = offsets.toArray(new int[0][]);
    }

    private GasPropagator() {}

    /**
     * Called (via {@link exp.CCnewmods.mge.mixin.MixinLevelSetBlock}) immediately
     * before the atmosphere block at {@code displacedPos} is overwritten.
     * Reads both gas and particulate data from the dying block entity and
     * pushes them into valid neighbouring atmosphere blocks.
     */
    public static void onAtmosphereDisplaced(Level level, BlockPos displacedPos) {
        if (level.isClientSide()) return;

        BlockEntity be = level.getBlockEntity(displacedPos);
        if (!(be instanceof AtmosphereBlockEntity displaced)) return;

        GasComposition gasSource  = displaced.getComposition().copy();
        ParticulateComposition partSource = displaced.getParticulates().copy();

        boolean hasGas  = !gasSource.isEmpty();
        boolean hasPart = !partSource.isEmpty();
        if (!hasGas && !hasPart) return;

        Vec3 wind = WindProviderManager.getWind(level, displacedPos);
        float windX = (float) wind.x, windY = (float) wind.y, windZ = (float) wind.z;

        // ── Collect valid neighbours and compute raw weights ──────────────────
        record NeighbourEntry(BlockPos pos, AtmosphereBlockEntity entity,
                              int dx, int dy, int dz, float rawWeight) {}
        List<NeighbourEntry> valid = new ArrayList<>(26);
        float totalWeight = 0f;

        float avgGasDensity = hasGas ? averageGasDensity(gasSource) : 1.0f;

        for (int[] offset : NEIGHBOURS_26) {
            int dx = offset[0], dy = offset[1], dz = offset[2];
            BlockPos nPos = displacedPos.offset(dx, dy, dz);
            BlockEntity nbe = level.getBlockEntity(nPos);
            if (!(nbe instanceof AtmosphereBlockEntity atm)) continue;

            float distSq = dx * dx + dy * dy + dz * dz;
            float w = 1.0f / distSq;

            // Vertical density bias from average gas composition
            if (dy < 0) w *= (float) Math.min(2.0, avgGasDensity);
            else if (dy > 0) w *= (float) Math.max(0.2, 2.0 - avgGasDensity);

            // Wind bias
            float offsetLen = (float) Math.sqrt(distSq);
            float windDot = (dx * windX + dy * windY + dz * windZ) / offsetLen;
            w *= (1.0f + Math.max(0f, windDot));

            totalWeight += w;
            valid.add(new NeighbourEntry(nPos, atm, dx, dy, dz, w));
        }

        if (valid.isEmpty() || totalWeight <= 0f) return;

        // ── Distribute gases ──────────────────────────────────────────────────
        if (hasGas) {
            for (NeighbourEntry entry : valid) {
                float normWeight = entry.rawWeight() / totalWeight;
                GasComposition nComp = entry.entity().getComposition();
                for (String key : gasSource.getTag().getAllKeys()) {
                    float mbar = gasSource.get(key);
                    if (mbar <= 0f) continue;
                    Gas gas = GasRegistry.get(key).orElse(null);
                    if (gas == null) continue;
                    nComp.add(gas, mbar * normWeight);
                }
                entry.entity().setComposition(nComp);
            }
        }

        // ── Distribute particulates ───────────────────────────────────────────
        if (hasPart) {
            for (NeighbourEntry entry : valid) {
                ParticulateComposition nParts = entry.entity().getParticulates();
                for (ParticulateType type : ParticulateType.values()) {
                    float mgM3 = partSource.get(type);
                    if (mgM3 <= 0f) continue;

                    // Recalculate weight with settle-behaviour vertical bias
                    float distSq = entry.dx() * entry.dx() + entry.dy() * entry.dy() + entry.dz() * entry.dz();
                    float w = 1.0f / distSq;

                    // Heavy particulates strongly prefer downward neighbours
                    float settleBias = type.settle.mgM3PerTick / ParticulateType.SettleBehaviour.MEDIUM.mgM3PerTick;
                    if (entry.dy() < 0) w *= Math.max(1.0f, settleBias * 2f);
                    else if (entry.dy() > 0) w *= Math.max(0.1f, 1.0f / settleBias);

                    float offsetLen = (float) Math.sqrt(distSq);
                    float windDot = (entry.dx() * windX + entry.dy() * windY + entry.dz() * windZ) / offsetLen;
                    w *= (1.0f + Math.max(0f, windDot) * type.windSensitivity);

                    // Re-normalise this one type's weight against the total
                    nParts.add(type, mgM3 * (w / totalWeight));
                }
                entry.entity().setParticulates(nParts);
            }
        }
    }

    private static float averageGasDensity(GasComposition comp) {
        float totalMbar = 0f, weightedDensity = 0f;
        for (String key : comp.getTag().getAllKeys()) {
            float mbar = comp.get(key);
            Gas gas = GasRegistry.get(key).orElse(null);
            if (gas == null) continue;
            weightedDensity += mbar * (float) gas.properties().densityRatioToAir();
            totalMbar += mbar;
        }
        return totalMbar > 0 ? weightedDensity / totalMbar : 1.0f;
    }
}
