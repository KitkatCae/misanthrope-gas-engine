package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.fluid.GasFluidRegistry;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Chemica compat.
 *
 * Chemica (modid "chemica") registers many of the same gases as MGE but as
 * Create {@code VirtualFluid}s used for crafting recipes. The compat here is
 * handled entirely by {@link GasFluidRegistry}'s alias table — when Chemica
 * is present, MGE's {@code IFluidHandler} on atmosphere blocks returns Chemica's
 * fluid types for shared gases (oxygen, nitrogen, methane, etc.) so Chemica
 * machines and Create pipes see a single unified fluid type.
 *
 * No separate event subscribers needed — the alias resolution in
 * {@link GasFluidRegistry#resolveAliases()} handles everything at load time.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChemicaCompat {

    public static final String CHEMICA_MODID = "chemica";
    private static boolean loaded = false;

    private ChemicaCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(CHEMICA_MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Chemica detected — gas fluid aliases active. " +
                "MGE atmosphere blocks will expose Chemica fluid types for shared gases.");
    }

    public static boolean isLoaded() { return loaded; }
}
