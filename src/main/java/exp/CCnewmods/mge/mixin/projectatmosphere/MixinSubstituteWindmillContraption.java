package exp.CCnewmods.mge.mixin.projectatmosphere;

import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import exp.CCnewmods.mge.contraption.MisanthropeWindmillContraption;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Substitutes {@link MisanthropeWindmillContraption} in place of a plain
 * {@link BearingContraption} at the exact point
 * {@code MechanicalBearingBlockEntity.assemble()} constructs one — see this
 * build's handoff doc for the full investigation that led here, summarized:
 * <p>
 * Confirmed via direct bytecode inspection of {@code assemble()}'s body
 * that Create constructs the contraption with a plain {@code new
 * BearingContraption(isWindmill, facing)} — {@code NEW} immediately
 * followed by {@code INVOKESPECIAL <init>(Z,Direction)V} — not a factory
 * method call. {@code @Redirect} on a constructor invocation is Mixin's
 * standard mechanism for exactly this shape: it intercepts the
 * {@code INVOKESPECIAL <init>} call, and Mixin handles dropping the
 * preceding {@code NEW}/{@code DUP} of the original type, substituting
 * whatever object this redirect method returns instead. Since
 * {@link MisanthropeWindmillContraption} extends {@link BearingContraption}
 * and exposes the identical {@code (boolean, Direction)} constructor, the
 * substitution is a drop-in replacement — every other line of
 * {@code assemble()} (calling {@code .assemble(level, pos)} on the result,
 * reading {@code .getSailBlocks()}, etc.) keeps working unchanged because
 * they only ever call methods declared on {@code BearingContraption} or
 * inherited from {@code Contraption}, all of which this subclass either
 * inherits as-is or overrides compatibly (see that class's own doc comment
 * for the {@code getType()}/{@code readNBT}/{@code writeNBT} overrides).
 * <p>
 * This redirect fires for windmill bearings AND ordinary (non-windmill)
 * mechanical bearings alike, since both go through the same
 * {@code MechanicalBearingBlockEntity.assemble()} method — windmill-ness is
 * just the {@code isWindmill} boolean argument, already passed straight
 * through unchanged. A plain mechanical bearing (not a windmill) ends up
 * with a {@link MisanthropeWindmillContraption} too, which is harmless: its
 * burn-state map simply never gets populated for a non-windmill bearing,
 * since nothing in this build's ignition path (once written) will have any
 * reason to call {@code igniteAt} on one — sail material/dampness/burn are
 * all windmill-specific concepts. Narrowing this redirect to
 * windmill-only would require re-deriving {@code isWindmill} independently
 * inside the redirect method, which is more fragile than just accepting
 * the harmless universal substitution.
 * <p>
 * {@code require = 1} (the Mixin default) is intentional here, unlike the
 * {@code require = 0} soft-injections in {@code MixinBearingContraptionMaterial}:
 * if this specific {@code new BearingContraption(...)} call site ever
 * disappears or changes shape in a future Create version, silently falling
 * back to vanilla {@code BearingContraption} would mean burn-state NBT
 * silently stops round-tripping with no error — worse than a loud mixin
 * load failure that immediately points at this file.
 */
@Mixin(value = MechanicalBearingBlockEntity.class, remap = false)
public abstract class MixinSubstituteWindmillContraption {

    @Redirect(
            method = "assemble",
            at = @At(
                    value = "NEW",
                    target = "com/simibubi/create/content/contraptions/bearing/BearingContraption"
            ),
            remap = false
    )
    private BearingContraption misanthrope_substituteWindmillContraption(
            boolean isWindmill, Direction facing) {
        return new MisanthropeWindmillContraption(isWindmill, facing);
    }
}
