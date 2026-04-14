package exp.CCnewmods.mge.cave;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.gas.ReactivityFlag;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Checks atmosphere blocks for explosive/combustion conditions and triggers
 * detonations when gas concentrations and ignition sources align.
 *
 * <h3>Two categories:</h3>
 *
 * <b>Flammable gas detonation</b> — any gas registered with LEL/UEL values
 * (methane, hydrogen, acetylene, etc.) detonates when:
 *   1. Fraction of total pressure is within [LEL, UEL]
 *   2. Sufficient oxidiser is present (O₂ or OXIDISER-flagged gas ≥ 16% by pressure)
 *   3. An ignition source exists within 2 blocks (fire, lava, very hot block)
 *
 * Strength scales with total flammable mbar × gas-specific energy density.
 * Acetylene and hydrogen produce the strongest blasts; methane moderate.
 *
 * <b>Coal dust explosion</b> — COAL_DUST particulate ≥ 150 mg/m³ with O₂ ≥ 160 mbar
 * and any fire block within 3 blocks triggers a dust explosion. Weaker than gas
 * detonations but still significant.
 *
 * <h3>Called from</h3>
 * {@link exp.CCnewmods.mge.propagation.AtmosphereTickScheduler#tick()} — checked
 * on blocks as they pass through the dirty queue, not every block every tick.
 */
public final class GasDetonationHandler {

    /**
     * Coal dust explosion threshold in mg/m³.
     * Real-world lower explosive limit is ~50-150 g/m³; we use a game-feel value.
     */
    public static final float COAL_DUST_LEL_MG_M3 = 150f;

    /** Minimum O₂ for any combustion/detonation to occur. */
    public static final float MIN_O2_MBAR = 160f;

    /** Radius in blocks to check for ignition sources. */
    private static final int IGNITION_RADIUS = 2;
    private static final int COAL_DUST_IGNITION_RADIUS = 3;

    private GasDetonationHandler() {}

    /**
     * Check a single atmosphere block for detonation conditions.
     * Called from AtmosphereTickScheduler during the dirty queue processing pass.
     * Returns true if a detonation occurred (block was consumed).
     */
    public static boolean checkDetonation(ServerLevel level, BlockPos pos,
                                           AtmosphereBlockEntity atm) {
        if (!MgeConfig.enableGasEffects) return false;

        GasComposition comp = atm.getComposition();
        float totalPressure = comp.totalPressure();
        if (totalPressure <= 0f) return false;

        // Compute oxidiser fraction
        float oxidiserMbar = comp.get(GasRegistry.OXYGEN);
        for (String key : comp.getTag().getAllKeys()) {
            Gas g = GasRegistry.get(key).orElse(null);
            if (g != null && g.properties().hasReactivity(ReactivityFlag.OXIDISER)) {
                oxidiserMbar += comp.get(key);
            }
        }
        float oxidiserFraction = oxidiserMbar / totalPressure;

        // ── Gas detonation check ──────────────────────────────────────────────
        if (oxidiserFraction >= 0.16f && comp.get(GasRegistry.OXYGEN) >= MIN_O2_MBAR) {
            for (String key : comp.getTag().getAllKeys()) {
                Gas gas = GasRegistry.get(key).orElse(null);
                if (gas == null || !gas.properties().isFlammable()) continue;

                float fraction = comp.get(key) / totalPressure;
                float lel = gas.properties().lowerExplosiveLimit();
                float uel = gas.properties().upperExplosiveLimit();
                if (fraction < lel || fraction > uel) continue;

                // In explosive range — check for ignition source
                if (!hasIgnitionSource(level, pos, IGNITION_RADIUS)) continue;

                // Detonate!
                float mbar = comp.get(key);
                float strength = calculateBlastStrength(gas, mbar);
                triggerGasDetonation(level, pos, atm, gas, strength);
                return true;
            }
        }

        // ── Coal dust explosion check ─────────────────────────────────────────
        ParticulateComposition parts = atm.getParticulates();
        float coalDust = parts.get(ParticulateType.COAL_DUST);
        if (coalDust >= COAL_DUST_LEL_MG_M3
                && comp.get(GasRegistry.OXYGEN) >= MIN_O2_MBAR
                && hasIgnitionSource(level, pos, COAL_DUST_IGNITION_RADIUS)) {
            triggerCoalDustExplosion(level, pos, atm, coalDust);
            return true;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Detonation implementations
    // -------------------------------------------------------------------------

    private static void triggerGasDetonation(ServerLevel level, BlockPos pos,
                                              AtmosphereBlockEntity atm,
                                              Gas gas, float strength) {
        // Consume the flammable gas and oxidiser
        GasComposition comp = atm.getComposition();
        float mbar = comp.get(gas);
        comp.add(gas, -mbar);
        float o2 = comp.get(GasRegistry.OXYGEN);
        comp.add(GasRegistry.OXYGEN, -Math.min(o2, mbar * 2f));
        comp.add(GasRegistry.CARBON_DIOXIDE, mbar * 0.6f);
        comp.add(GasRegistry.CARBON_MONOXIDE, mbar * 0.2f);
        atm.setComposition(comp);

        // Inject explosion products
        ParticulateComposition parts = atm.getParticulates();
        parts.add(ParticulateType.SMOKE_AEROSOL, mbar * 5f);
        parts.add(ParticulateType.SOOT, mbar * 2f);
        parts.add(ParticulateType.ASH_CLOUD, mbar * 1f);
        atm.setParticulates(parts);

        // Trigger world explosion
        Vec3 centre = Vec3.atCenterOf(pos);
        level.explode(null, centre.x, centre.y, centre.z,
                Math.max(1f, Math.min(8f, strength)),
                net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);

        Mge.LOGGER.debug("[MGE] Gas detonation at {} — gas: {}, strength: {}",
                pos, gas.id(), strength);
    }

    private static void triggerCoalDustExplosion(ServerLevel level, BlockPos pos,
                                                   AtmosphereBlockEntity atm,
                                                   float dustAmount) {
        // Consume coal dust and O₂
        ParticulateComposition parts = atm.getParticulates();
        parts.add(ParticulateType.COAL_DUST, -Math.min(dustAmount, COAL_DUST_LEL_MG_M3 * 2f));
        parts.add(ParticulateType.ASH_CLOUD, dustAmount * 0.5f);
        parts.add(ParticulateType.SOOT,      dustAmount * 0.3f);
        atm.setParticulates(parts);

        GasComposition comp = atm.getComposition();
        float o2 = comp.get(GasRegistry.OXYGEN);
        comp.add(GasRegistry.OXYGEN, -Math.min(o2, dustAmount * 0.1f));
        comp.add(GasRegistry.CARBON_DIOXIDE, dustAmount * 0.05f);
        atm.setComposition(comp);

        float strength = Math.min(4f, dustAmount / COAL_DUST_LEL_MG_M3 * 2.5f);
        Vec3 centre = Vec3.atCenterOf(pos);
        level.explode(null, centre.x, centre.y, centre.z, strength,
                net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);

        Mge.LOGGER.debug("[MGE] Coal dust explosion at {} — dust: {}, strength: {}",
                pos, dustAmount, strength);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static float calculateBlastStrength(Gas gas, float mbar) {
        // Base energy density — higher for energetic fuels
        double molarMass = gas.properties().molarMassGPerMol();
        // Acetylene (26 g/mol), hydrogen (2 g/mol) both have high energy density
        // We approximate with a simple inverse-molar-mass curve
        float energyFactor;
        if (molarMass <= 4)       energyFactor = 3.0f;  // H₂, He
        else if (molarMass <= 28) energyFactor = 2.0f;  // CO, ethylene, acetylene
        else if (molarMass <= 44) energyFactor = 1.5f;  // propane, CO₂ region
        else                      energyFactor = 1.0f;  // heavier fuels

        return Math.min(8f, (mbar / 50f) * energyFactor);
    }

    static boolean hasIgnitionSource(ServerLevel level, BlockPos centre, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos check = centre.offset(dx, dy, dz);
                    if (!level.isLoaded(check)) continue;
                    BlockState state = level.getBlockState(check);
                    if (state.getBlock() instanceof FireBlock) return true;
                    if (state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) return true;
                    // Thermodynamica hot blocks if loaded
                    if (isThermodynamicaHot(level, check)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isThermodynamicaHot(ServerLevel level, BlockPos pos) {
        try {
            double celsius = exp.CCnewmods.mge.compat.ThermodynamicaCompat.getCelsiusAt(level, pos);
            return !Double.isNaN(celsius) && celsius > 400.0;
        } catch (Exception e) {
            return false;
        }
    }
}
