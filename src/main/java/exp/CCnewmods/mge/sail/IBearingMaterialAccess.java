package exp.CCnewmods.mge.sail;

/**
 * Implemented by {@code MixinBearingContraptionMaterial}'s mixin target
 * ({@code com.simibubi.create.content.contraptions.bearing.BearingContraption})
 * via Mixin's interface-injection, so other code can read the windmill-wide
 * material profile without needing Mixin's own tooling — just a plain
 * instanceof check and cast, the standard pattern for exposing
 * mixin-injected behaviour to non-mixin code.
 * <p>
 * Usage from a bearing block entity:
 * <pre>{@code
 * Contraption c = bearingEntity.getMovedContraption().getContraption();
 * SailMaterialRegistry.WindmillMaterialProfile profile =
 *     (c instanceof IBearingMaterialAccess access)
 *         ? access.misanthrope_getMaterialProfile()
 *         : SailMaterialRegistry.WindmillMaterialProfile.EMPTY;
 * }</pre>
 */
public interface IBearingMaterialAccess {

    SailMaterialRegistry.WindmillMaterialProfile misanthrope_getMaterialProfile();
}
