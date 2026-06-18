package exp.CCnewmods.mge.compat.projectatmosphere;

import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Load-time guard for the Project Atmosphere compatibility layer.
 * <p>
 * The Mixins in this package are listed in mge.mixins.json (this project's
 * own mixin config — not part of Misanthrope World, despite the similar
 * naming history) and target Create classes unconditionally, but the actual
 * PA API calls are wrapped in try/catch in WindmillWindIntegration so
 * nothing explodes if PA is absent at runtime.
 * <p>
 * Call ProjectAtmosphereCompat.isLoaded() from any non-mixin code that
 * needs to branch on PA presence (config screens, commands, debug utils).
 */
public final class ProjectAtmosphereCompat {

    private static final boolean PA_LOADED = ModList.get().isLoaded("projectatmosphere");

    private ProjectAtmosphereCompat() {
    }

    public static boolean isLoaded() {
        return PA_LOADED;
    }

    }
