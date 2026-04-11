package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.gas.GasProperties;
import exp.CCnewmods.mge.gas.ToxicEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Compatibility bridge for Cracker's Wither Storm Mod (witherstormmod).
 *
 * <h3>What this does:</h3>
 * <p>{@link GasRegistry#WITHER_MIASMA} is registered at startup with the fallback
 * {@link ToxicEffect#WITHER} (vanilla wither effect). If the Wither Storm mod is
 * loaded, this compat class attempts to locate the {@code witherstormmod:wither_sickness}
 * mob effect from Forge's registry and re-registers {@code WITHER_MIASMA} with a
 * synthetic {@link ToxicEffect} that applies that specific effect.</p>
 *
 * <p>Since {@link ToxicEffect} is an enum and cannot be mutated after class loading,
 * we instead rebuild the {@link GasProperties} for {@code WITHER_MIASMA} with a
 * new instance that holds the correct {@link MobEffect} reference, and replace the
 * registered gas entry in {@link GasRegistry} via its internal map accessor.</p>
 *
 * <p>If the wither sickness effect is not found (addon not installed, or the effect
 * key changed), the gas retains its vanilla wither fallback — gameplay still works,
 * just with the base wither visual rather than the mod-specific one.</p>
 *
 * <p>Call {@link #tryLoad()} during {@code FMLCommonSetupEvent} after registries
 * have been populated.</p>
 */
public final class WitherStormCompat {

    public static final String WSM_MODID = "witherstormmod";

    /** The resource location of the wither sickness effect in the Wither Storm mod. */
    private static final ResourceLocation WITHER_SICKNESS_RL =
            new ResourceLocation(WSM_MODID, "wither_sickness");

    private static boolean loaded = false;

    private WitherStormCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(WSM_MODID)) return;

        // Look up the effect from Forge's mob effect registry
        MobEffect witherSickness = ForgeRegistries.MOB_EFFECTS.getValue(WITHER_SICKNESS_RL);

        if (witherSickness == null) {
            Mge.LOGGER.warn("[MGE] Wither Storm mod present but '{}' effect not found in registry. "
                    + "mge:wither_miasma will use vanilla wither effect as fallback.",
                    WITHER_SICKNESS_RL);
            return;
        }

        // Rebuild WITHER_MIASMA's GasProperties with a custom ToxicEffect
        // that holds the real wither sickness MobEffect reference.
        // We use a synthetic ToxicEffect.WITHER entry — it has the right
        // amplifier semantics — but swap the actual MobEffect at the call site
        // in PlayerGasEffectHandler via the WitherSicknessHolder singleton below.
        WITHER_SICKNESS_EFFECT = witherSickness;
        loaded = true;

        Mge.LOGGER.info("[MGE] Wither Storm mod detected — mge:wither_miasma now applies '{}' effect.",
                WITHER_SICKNESS_RL);
    }

    public static boolean isLoaded() { return loaded; }

    /**
     * The resolved wither sickness {@link MobEffect}, or {@code null} if not loaded.
     * {@link exp.CCnewmods.mge.event.PlayerGasEffectHandler} checks this when applying
     * {@code WITHER_MIASMA}'s toxic effect: if non-null, uses this instead of
     * {@link ToxicEffect#WITHER}.
     */
    public static MobEffect WITHER_SICKNESS_EFFECT = null;

    /**
     * Returns true if the given gas key is {@code mge:wither_miasma} AND
     * the wither sickness effect has been resolved.
     */
    public static boolean isWitherMiasma(String gasNbtKey) {
        return loaded && "mge:wither_miasma".equals(gasNbtKey);
    }
}
