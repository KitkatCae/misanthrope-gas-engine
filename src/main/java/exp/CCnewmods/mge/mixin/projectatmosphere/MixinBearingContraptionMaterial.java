package exp.CCnewmods.mge.mixin.projectatmosphere;

import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import exp.CCnewmods.mge.contraption.MisanthropeWindmillContraption;
import exp.CCnewmods.mge.sail.IBearingMaterialAccess;
import exp.CCnewmods.mge.sail.SailMaterialRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tallies sail/fin material composition as a windmill bearing's contraption
 * assembles, since the assembled contraption detaches its blocks from live
 * world positions into in-memory relative-offset storage.
 * <p>
 * {@code addBlock} is called once per captured block while the real
 * {@link BlockState} and {@link Level} are still available — the correct
 * moment to resolve per-instance data (TiC material, aero coefficient) that
 * cannot be read mid-spin.
 * <p>
 * The resulting {@link SailMaterialRegistry.WindmillMaterialProfile} and
 * per-blade {@code MaterialId} map are stored and exposed via
 * {@link IBearingMaterialAccess} and, for the contraption subclass,
 * directly on {@link MisanthropeWindmillContraption}.
 */
@Mixin(value = BearingContraption.class, remap = false)
public abstract class MixinBearingContraptionMaterial implements IBearingMaterialAccess {

    @Unique
    private final List<SailMaterialRegistry.BlockSailProfile> misanthrope_sailProfiles = new ArrayList<>();

    @Unique
    private SailMaterialRegistry.WindmillMaterialProfile misanthrope_materialProfile =
            SailMaterialRegistry.WindmillMaterialProfile.EMPTY;

    @Inject(
            method = "assemble",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void misanthrope_clearSailTally(
            Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        misanthrope_sailProfiles.clear();
        // If this is a MisanthropeWindmillContraption, clear its per-blade maps too.
        if ((Object) this instanceof MisanthropeWindmillContraption mwc) {
            mwc.clearPerBladeMaps();
        }
    }

    /**
     * Captures this block's sail material profile at assembly time, using
     * the full position-aware resolve so TiC material is read from live BEs.
     * Also writes per-blade material directly into
     * {@link MisanthropeWindmillContraption#sailMaterials} when this is the
     * Misanthrope custom contraption type.
     */
    @Inject(
            method = "addBlock",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void misanthrope_tallySailMaterial(
            Level level, BlockPos pos, Pair<StructureTemplate.StructureBlockInfo, ?> pair,
            CallbackInfo ci) {
        if (pair == null) return;
        Object left = pair.getLeft();
        if (!(left instanceof StructureTemplate.StructureBlockInfo info)) return;
        BlockState state = info.state();
        if (state == null) return;

        // Use position-aware resolve: reads TiC material from live BE if available.
        SailMaterialRegistry.BlockSailProfile profile =
                SailMaterialRegistry.resolve(state, level, pos);
        misanthrope_sailProfiles.add(profile);

        // Write per-blade material into contraption if this is our subclass.
        if ((Object) this instanceof MisanthropeWindmillContraption mwc) {
            profile.tinkersMaterial().ifPresent(mat -> mwc.sailMaterials.put(pos, mat));
        }
    }

    @Inject(
            method = "assemble",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void misanthrope_finalizeMaterialProfile(
            Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        misanthrope_materialProfile = SailMaterialRegistry.aggregate(misanthrope_sailProfiles);
    }

    @Override
    @Unique
    public SailMaterialRegistry.WindmillMaterialProfile misanthrope_getMaterialProfile() {
        return misanthrope_materialProfile;
    }
}
