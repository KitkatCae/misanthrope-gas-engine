package exp.CCnewmods.mge.sail;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraftforge.fml.ModList;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

import java.util.Optional;

/**
 * Implemented by a {@link net.minecraft.world.level.block.Block} that wants
 * to act as a non-default windmill bearing sail — most notably MCT's turbine
 * fin blocks (Part D of the turbine fins build), but open to any future
 * "better than cloth" sail material.
 * <p>
 * ── Why an interface, not a tag ─────────────────────────────────────────────
 * <p>
 * MGE has a {@code compileOnly} dependency on Misanthrope World and no
 * dependency at all on MCT (the reverse is true: MCT depends on MGE). A data
 * tag would have worked too, but would only carry a boolean "is this a
 * special sail" — every numeric multiplier below would still need a second
 * lookup somewhere. An interface lets the implementing block own its own
 * numbers directly, with compile-time type safety on the MCT side, at the
 * cost of MCT needing this interface on its classpath — which it already has,
 * since MCT compiles against MGE.
 * <p>
 * ── Scope: aerodynamics and heat only, NOT corrosion ────────────────────────
 * <p>
 * Corrosion resistance is deliberately NOT part of this interface. It's
 * already fully covered by Part A's {@code BlockPhysicsData.PhReactivity}
 * (specifically {@code resistanceFactor}), which every block — sail or
 * otherwise — can already declare via {@code material_properties} JSON. A
 * turbine fin block that wants to resist acid exposure just needs a low
 * {@code resistance_factor} in its material_properties entry; it doesn't
 * need to repeat that number here. Keeping this interface narrow avoids two
 * places to edit for the same physical property.
 * <p>
 * ── Default sail behaviour ──────────────────────────────────────────────────
 * <p>
 * Vanilla Create cloth sails (and any other block that does NOT implement
 * this interface) get the baseline profile via
 * {@link SailMaterialRegistry#DEFAULT_CLOTH} — aerodynamic coefficient 1.0,
 * not heat-immune. A block only needs to implement this interface to claim
 * something BETTER (or, in principle, worse) than that baseline.
 * <p>
 * ── TiC material access ──────────────────────────────────────────────────────
 * <p>
 * {@link #getTinkersMaterial} is a default method returning {@code empty()}.
 * Blocks that carry a per-instance TiC material (e.g. MCT's turbine fin
 * BlockEntity) override it to read their stored {@link MaterialId}.
 * <p>
 * This method is ONLY valid at assembly time (when
 * {@link exp.CCnewmods.mge.mixin.projectatmosphere.MixinBearingContraptionMaterial}
 * processes {@code addBlock}) — the block still exists in the world at that
 * point and its BlockEntity is live. Once detached into a spinning contraption
 * the block has no live world position, so callers MUST capture the result
 * once and store it (e.g. in
 * {@link exp.CCnewmods.mge.contraption.MisanthropeWindmillContraption#sailMaterials})
 * rather than calling this repeatedly mid-spin.
 */
public interface ISailMaterial {

    /**
     * Aerodynamic coefficient — multiplier on the flow-driven RPM contribution
     * from this specific sail/fin block, relative to a vanilla cloth sail's
     * baseline of {@code 1.0}. A well-shaped metal turbine fin should return
     * something modestly above 1.0 (e.g. 1.1–1.5); there's no hard ceiling,
     * but values should stay physically plausible — this is a shape/finish
     * efficiency factor, not a free RPM multiplier.
     * <p>
     * This is the static, BlockState-only baseline used for blocks that have
     * no per-instance NBT (e.g. cloth sails). For fin blocks whose aerodynamic
     * efficiency is derived from their stored TiC material, the per-instance
     * value is resolved at assembly time in
     * {@link SailMaterialRegistry#resolve(net.minecraft.world.level.block.state.BlockState,
     * BlockGetter, BlockPos)} and baked into the per-blade {@link BlockSailProfile}.
     */
    float getAerodynamicCoefficient();

    /**
     * Whether this block is immune to the high-ambient-temperature ignition
     * check in Part C's sail reactivity pass. Vanilla cloth sails are NOT
     * immune (they burn); a steel or higher-temperature-rated turbine fin
     * should return {@code true}.
     * <p>
     * This must be answerable from BlockState alone — it is called from
     * {@link exp.CCnewmods.mge.mixin.projectatmosphere.WindmillBurnTickMixin}
     * where no live BlockPos is available (the block is inside a detached
     * spinning contraption). For fin blocks, return {@code true} unconditionally
     * here; material-specific melt/ignite thresholds are handled by the
     * per-material PhaseTransition evaluation in MisWorld, which operates at
     * assembly time and is baked into the contraption's state.
     */
    boolean isHeatResistant();

    /**
     * The Tinkers' Construct material this block was cast or forged from.
     * <p>
     * Default returns {@code empty()} — vanilla Create sails and any block that
     * does not carry a per-instance TiC material do not override this.
     * <p>
     * <strong>Only call this at assembly time</strong> (inside
     * {@code MixinBearingContraptionMaterial.addBlock}) when the block's
     * BlockEntity is still live in the world. Store the result in
     * {@code MisanthropeWindmillContraption.sailMaterials} rather than calling
     * this again mid-spin. See interface doc comment for why.
     * <p>
     * Soft-guarded: if {@code tconstruct} is not loaded this method is never
     * called — callers check {@code ModList.get().isLoaded("tconstruct")} first.
     */
    default Optional<MaterialId> getTinkersMaterial(BlockGetter level, BlockPos pos) {
        return Optional.empty();
    }
}
