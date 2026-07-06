package exp.CCnewmods.mge.sail;

import exp.CCnewmods.mge.compat.MisanthropeWorldCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

import java.util.Optional;

/**
 * Resolves the per-block {@link ISailMaterial} profile for any block that
 * might be captured into a windmill bearing's contraption, and combines
 * many such per-block profiles into a single windmill-wide aggregate
 * ({@link WindmillMaterialProfile}).
 * <p>
 * Two independent properties are looked up per block, from two independent
 * sources:
 * <ul>
 *   <li>Aerodynamics / heat resistance — {@link ISailMaterial}, implemented
 *       directly by the block class. Blocks that don't implement it (every
 *       vanilla Create sail) get {@link #DEFAULT_AERODYNAMIC_COEFFICIENT}/
 *       {@link #DEFAULT_HEAT_RESISTANT}.</li>
 *   <li>Corrosion resistance — Part A's
 *       {@code BlockPhysicsData.PhReactivity.resistanceFactor}, read via
 *       Misanthrope World's {@code BlockPhysicsRegistry} if that mod is
 *       loaded. Blocks with no {@code material_properties} entry (or no
 *       {@code ph_reactivity} block within it) get
 *       {@link #DEFAULT_CORROSION_RESISTANCE_FACTOR}.</li>
 * </ul>
 * <p>
 * ── Two resolve overloads ────────────────────────────────────────────────────
 * <p>
 * {@link #resolve(BlockState)} — BlockState-only, for call sites that have no
 * live world position (notably {@link
 * exp.CCnewmods.mge.mixin.projectatmosphere.WindmillBurnTickMixin}). Always
 * returns {@code tinkersMaterial = empty()}.
 * <p>
 * {@link #resolve(BlockState, BlockGetter, BlockPos)} — full resolution at
 * assembly time, when the block still exists in the world and its BlockEntity
 * is live. Calls {@link ISailMaterial#getTinkersMaterial} if the block
 * implements the interface and {@code tconstruct} is loaded. Used by
 * {@link exp.CCnewmods.mge.mixin.projectatmosphere.MixinBearingContraptionMaterial}.
 */
public final class SailMaterialRegistry {

    private static final boolean TIC = ModList.get().isLoaded("tconstruct");

    private SailMaterialRegistry() {
    }

    // ── Defaults — vanilla cloth sail baseline ──────────────────────────────

    public static final float DEFAULT_AERODYNAMIC_COEFFICIENT = 1.0f;
    public static final boolean DEFAULT_HEAT_RESISTANT = false;
    public static final float DEFAULT_CORROSION_RESISTANCE_FACTOR = 1.0f;

    // ── Per-block lookup (BlockState-only) ───────────────────────────────────

    /**
     * Resolves the full per-block sail profile from BlockState alone.
     * {@code tinkersMaterial} is always {@code empty()} in this overload.
     * Used by WindmillBurnTickMixin (no live position available there).
     */
    public static BlockSailProfile resolve(BlockState state) {
        float aero = DEFAULT_AERODYNAMIC_COEFFICIENT;
        boolean heatResistant = DEFAULT_HEAT_RESISTANT;

        if (state.getBlock() instanceof ISailMaterial material) {
            aero = material.getAerodynamicCoefficient();
            heatResistant = material.isHeatResistant();
        }

        float corrosionResistance = resolveCorrosionResistance(state);
        return new BlockSailProfile(aero, heatResistant, corrosionResistance, Optional.empty());
    }

    // ── Per-block lookup (assembly-time, with live BlockEntity) ─────────────

    /**
     * Full resolution: same as {@link #resolve(BlockState)} plus a
     * {@link ISailMaterial#getTinkersMaterial} call when possible.
     * <p>
     * Only call this at assembly time (inside {@code MixinBearingContraptionMaterial.addBlock})
     * when the block's BlockEntity is still live. The result's
     * {@code tinkersMaterial} is stored in
     * {@code MisanthropeWindmillContraption.sailMaterials} for the windmill's
     * lifetime and never re-resolved mid-spin.
     */
    public static BlockSailProfile resolve(BlockState state, BlockGetter level, BlockPos pos) {
        float aero = DEFAULT_AERODYNAMIC_COEFFICIENT;
        boolean heatResistant = DEFAULT_HEAT_RESISTANT;
        Optional<MaterialId> tinkersMaterial = Optional.empty();

        if (state.getBlock() instanceof ISailMaterial material) {
            aero = material.getAerodynamicCoefficient();
            heatResistant = material.isHeatResistant();
            if (TIC) {
                tinkersMaterial = material.getTinkersMaterial(level, pos);
            }
        }

        float corrosionResistance = resolveCorrosionResistance(state);
        return new BlockSailProfile(aero, heatResistant, corrosionResistance, tinkersMaterial);
    }

    /**
     * Reads {@code ph_reactivity.resistance_factor} for this block from
     * Misanthrope World's material-properties registry, if that mod is loaded.
     */
    private static float resolveCorrosionResistance(BlockState state) {
        if (!ModList.get().isLoaded(MisanthropeWorldCompat.MODID)) {
            return DEFAULT_CORROSION_RESISTANCE_FACTOR;
        }
        try {
            var data = exp.CCnewmods.misanthrope_world.physics.BlockPhysicsRegistry.get(state);
            if (data == null || data.phReactivity == null) {
                return DEFAULT_CORROSION_RESISTANCE_FACTOR;
            }
            return (float) data.phReactivity.resistanceFactor();
        } catch (Exception e) {
            return DEFAULT_CORROSION_RESISTANCE_FACTOR;
        }
    }

    // ── Per-block result ─────────────────────────────────────────────────────

    /**
     * Resolved profile for one captured sail/fin block.
     *
     * @param tinkersMaterial The TiC material this block was cast from, or
     *                        {@code empty()} for non-fin blocks and any block
     *                        resolved without a live position. Stored per-blade
     *                        in {@code MisanthropeWindmillContraption.sailMaterials}.
     */
    public record BlockSailProfile(
            float aerodynamicCoefficient,
            boolean heatResistant,
            float corrosionResistanceFactor,
            Optional<MaterialId> tinkersMaterial
    ) {
    }

    // ── Windmill-wide aggregate ──────────────────────────────────────────────

    /**
     * Combines every captured sail block's {@link BlockSailProfile} into one
     * windmill-wide profile, computed once at assembly time and reused for the
     * windmill's entire spinning lifetime.
     * <p>
     * Aggregation rules:
     * <ul>
     *   <li>Aerodynamic coefficient — block-count-weighted average.</li>
     *   <li>Corrosion resistance — block-count-weighted average.</li>
     *   <li>Heat resistance — AND of all blocks (one cloth sail in an otherwise
     *       all-fin windmill makes the whole windmill burn-eligible).</li>
     * </ul>
     * Note: {@code tinkersMaterial} is NOT aggregated into this profile — it is
     * stored per-blade in {@code MisanthropeWindmillContraption.sailMaterials}
     * for future per-blade queries (melt progress, etc.).
     */
    public static WindmillMaterialProfile aggregate(java.util.List<BlockSailProfile> profiles) {
        if (profiles.isEmpty()) {
            return new WindmillMaterialProfile(
                    DEFAULT_AERODYNAMIC_COEFFICIENT,
                    DEFAULT_HEAT_RESISTANT,
                    DEFAULT_CORROSION_RESISTANCE_FACTOR,
                    0
            );
        }

        double aeroSum = 0;
        double corrosionSum = 0;
        boolean allHeatResistant = true;

        for (BlockSailProfile p : profiles) {
            aeroSum += p.aerodynamicCoefficient();
            corrosionSum += p.corrosionResistanceFactor();
            if (!p.heatResistant()) allHeatResistant = false;
        }

        int count = profiles.size();
        return new WindmillMaterialProfile(
                (float) (aeroSum / count),
                allHeatResistant,
                (float) (corrosionSum / count),
                count
        );
    }

    public record WindmillMaterialProfile(
            float aerodynamicCoefficient,
            boolean heatResistant,
            float corrosionResistanceFactor,
            int sailBlockCount
    ) {
        public static final WindmillMaterialProfile EMPTY = new WindmillMaterialProfile(
                DEFAULT_AERODYNAMIC_COEFFICIENT,
                DEFAULT_HEAT_RESISTANT,
                DEFAULT_CORROSION_RESISTANCE_FACTOR,
                0
        );
    }
}
