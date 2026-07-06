package exp.CCnewmods.mge.sail;

import exp.CCnewmods.mge.Mge;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers the four burn-stage visual blocks ({@link BurningSailBlock}
 * instances) through MGE's existing {@code Mge.BLOCKS}
 * {@code DeferredRegister<Block>} — the first blocks MGE has ever
 * registered through it (everything else in this mod is gas/fluid
 * simulation with no block content of its own until this sub-project).
 * <p>
 * ── Five-stage sequence (only four new blocks — "normal" is the original,
 * unmodified Create sail) ─────────────────────────────────────────────────
 * <p>
 * Per Caelan's explicit direction, modeled after Burnt's own multi-stage
 * log/grass burn vocabulary rather than the simpler 2-stage canvas→frame
 * design from an earlier pass:
 * <ol>
 *   <li><b>normal</b> — the original, unmodified vanilla/Create sail block.
 *       Not represented here; this is just whatever sail block was already
 *       captured into the contraption before ignition.</li>
 *   <li><b>{@link #SINGED_SAIL}</b> — first visible fire damage, full
 *       sail geometry still present, nothing actually consumed yet. The
 *       "this is about to really burn" warning stage.</li>
 *   <li><b>{@link #BURNING_SAIL_CANVAS}</b> — canvas actively on fire, full
 *       sail geometry, brighter light level than singed.</li>
 *   <li><b>{@link #BURNING_SAIL_FRAME}</b> — canvas has burned away
 *       entirely (frame-only geometry from here on), the wooden frame
 *       itself now actively on fire.</li>
 *   <li><b>{@link #CHARRED_SAIL_FRAME}</b> — terminal state. Frame-only
 *       geometry, fire has gone out, blackened/ash remnant. Deliberately
 *       NOT lit (light level 0, no {@code ignitedByLava()}) — by this
 *       stage there's nothing left to actively burn, matching how Burnt's
 *       own terminal "burnt"/"ash" log states read as cooled-down end
 *       states rather than still-active fire. See this build's handoff for
 *       the explicit design call that charred is a permanent end state
 *       (the windmill keeps a blackened blade forever) rather than
 *       eventually vanishing — Caelan's answer didn't pin this down
 *       explicitly, so this was the most defensible default; flagged
 *       clearly for review.</li>
 * </ol>
 * <p>
 * No {@code BlockItem}s registered for any of the four — these are never
 * meant to be obtained, placed, or held; they only ever exist as a direct
 * {@link net.minecraft.world.level.block.state.BlockState} substitution
 * inside a windmill contraption's block map (see
 * {@code WindmillBurnTickMixin}, the actual burn-tick driver). A
 * {@code BlockItem} would let a player obtain one via pick-block/creative
 * inventory search, which makes no sense for a state that's supposed to be
 * transient and fire-driven only (or, for {@code charred_sail_frame},
 * permanent-but-never-player-obtainable).
 */
public final class MisanthropeSailBlocks {

    private MisanthropeSailBlocks() {
    }

    /**
     * Stage 1 — first fire damage, full geometry, not yet actively burning
     * cloth. Subtle light level (4) — just enough to read as "something's
     * wrong here" without looking like an active flame yet.
     */
    public static final RegistryObject<BurningSailBlock> SINGED_SAIL =
            Mge.BLOCKS.register("singed_sail", () -> BurningSailBlock.withCanvas(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .sound(SoundType.WOOL)
                            .strength(0.2f)
                            .noOcclusion()
                            .noCollission()
                            .lightLevel(state -> 4)
                            .ignitedByLava()
            ));

    /**
     * Stage 2 — canvas actively on fire, full geometry. Brightest of the
     * canvas-bearing stages.
     */
    public static final RegistryObject<BurningSailBlock> BURNING_SAIL_CANVAS =
            Mge.BLOCKS.register("burning_sail_canvas", () -> BurningSailBlock.withCanvas(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.FIRE)
                            .sound(SoundType.WOOL)
                            .strength(0.2f)
                            .noOcclusion()
                            .noCollission()
                            .lightLevel(state -> 9)
                            .ignitedByLava()
            ));

    /**
     * Stage 3 — canvas already gone (frame-only geometry from here on),
     * the wooden frame itself actively on fire. Brightest stage overall —
     * a fully-engulfed wooden frame reads as the most intense moment in the
     * sequence.
     */
    public static final RegistryObject<BurningSailBlock> BURNING_SAIL_FRAME =
            Mge.BLOCKS.register("burning_sail_frame", () -> BurningSailBlock.frameOnly(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.FIRE)
                            .sound(SoundType.WOOD)
                            .strength(0.5f)
                            .noOcclusion()
                            .lightLevel(state -> 11)
                            .ignitedByLava()
            ));

    /**
     * Stage 4 (terminal) — fire has gone out, blackened frame remnant.
     * Deliberately unlit and not {@code ignitedByLava()} — see class doc
     * comment for the reasoning. This is the permanent resting state for a
     * burned-out windmill blade.
     */
    public static final RegistryObject<BurningSailBlock> CHARRED_SAIL_FRAME =
            Mge.BLOCKS.register("charred_sail_frame", () -> BurningSailBlock.frameOnly(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .sound(SoundType.WOOD)
                            .strength(0.5f)
                            .noOcclusion()
            ));

    /**
     * Forces this class's static initializer (and therefore the four
     * {@code RegistryObject} fields above) to run. Call once from
     * {@code Mge}'s constructor — see that file's constructor for where
     * this is wired in, immediately after {@code BLOCKS.register(modBus)}.
     * {@code RegistryObject.register} inside a {@code DeferredRegister}
     * only actually schedules the registration once the field's owning
     * class has been loaded by the JVM, so a class that's never referenced
     * anywhere else needs an explicit touch like this to guarantee it loads
     * before the {@code RegisterEvent} fires.
     */
    public static void touch() {
        // Intentionally empty — referencing this class from the caller is
        // the entire point of the call.
    }
}
