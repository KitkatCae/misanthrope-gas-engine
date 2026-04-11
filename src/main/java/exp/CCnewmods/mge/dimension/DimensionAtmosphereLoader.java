package exp.CCnewmods.mge.dimension;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import exp.CCnewmods.mge.Mge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@link DimensionAtmosphereProfile} data from
 * {@code data/<namespace>/dimension_atmosphere/<path>.json} on server start and
 * datapack reload.
 *
 * <p>File naming convention: replace the dimension ID's {@code :} with {@code -}
 * and place under {@code data/mge/dimension_atmosphere/}. Examples:</p>
 * <ul>
 *   <li>{@code minecraft:overworld}       → {@code data/mge/dimension_atmosphere/minecraft-overworld.json}</li>
 *   <li>{@code minecraft:the_nether}      → {@code data/mge/dimension_atmosphere/minecraft-the_nether.json}</li>
 *   <li>{@code aether:the_aether}         → {@code data/mge/dimension_atmosphere/aether-the_aether.json}</li>
 * </ul>
 *
 * <p>Third-party modpacks or addon mods can add their own profiles under any namespace
 * by placing JSON files in their own datapack's {@code data/mge/dimension_atmosphere/} folder.</p>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DimensionAtmosphereLoader
        extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    /** Singleton instance registered as a reload listener. */
    public static final DimensionAtmosphereLoader INSTANCE = new DimensionAtmosphereLoader();

    /** Map from dimension ResourceLocation → profile. Populated on each reload. */
    private static final Map<ResourceLocation, DimensionAtmosphereProfile> PROFILES = new HashMap<>();

    private DimensionAtmosphereLoader() {
        super(GSON, "dimension_atmosphere");
    }

    // -------------------------------------------------------------------------
    // Reload listener registration
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    // -------------------------------------------------------------------------
    // SimpleJsonResourceReloadListener implementation
    // -------------------------------------------------------------------------

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> map,
                          ResourceManager resourceManager,
                          ProfilerFiller profiler) {
        PROFILES.clear();
        int loaded = 0;

        for (Map.Entry<ResourceLocation, JsonObject> entry : map.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                DimensionAtmosphereProfile profile =
                        DimensionAtmosphereProfile.fromJson(fileId, entry.getValue());
                PROFILES.put(profile.dimension, profile);
                loaded++;
            } catch (Exception e) {
                Mge.LOGGER.error("[MGE] Failed to load atmosphere profile {}: {}",
                        fileId, e.getMessage());
            }
        }

        Mge.LOGGER.info("[MGE] Loaded {} dimension atmosphere profiles.", loaded);
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Returns the profile for the given dimension, or {@code null} if no profile
     * has been defined. Callers should fall back to {@link #STANDARD_OVERWORLD_FALLBACK}.
     */
    public static DimensionAtmosphereProfile get(ResourceLocation dimension) {
        return PROFILES.get(dimension);
    }

    public static Map<ResourceLocation, DimensionAtmosphereProfile> all() {
        return Collections.unmodifiableMap(PROFILES);
    }

    /**
     * Hardcoded fallback used when no JSON profile exists for a dimension.
     * Returns standard Earth air — safe default for unknown overworld-like dimensions.
     */
    public static final DimensionAtmosphereProfile STANDARD_OVERWORLD_FALLBACK;

    static {
        // Build inline using the profile builder approach
        Map<String, Float> gases = new java.util.LinkedHashMap<>();
        gases.put("mge:nitrogen",       780.9f);
        gases.put("mge:oxygen",         209.5f);
        gases.put("mge:argon",            9.3f);
        gases.put("mge:carbon_dioxide",   0.4f);
        gases.put("mge:water_vapor",     10.0f);
        gases.put("mge:neon",             0.018f);
        gases.put("mge:helium",           0.005f);
        gases.put("mge:methane",          0.002f);

        com.google.gson.JsonObject fallbackJson = new com.google.gson.JsonObject();
        fallbackJson.addProperty("dimension", "minecraft:overworld");
        fallbackJson.addProperty("breathable", true);
        fallbackJson.addProperty("has_sky_access", true);
        fallbackJson.addProperty("base_pressure_mbar", 1013.25f);
        com.google.gson.JsonObject gasJson = new com.google.gson.JsonObject();
        gases.forEach(gasJson::addProperty);
        fallbackJson.add("gases", gasJson);

        STANDARD_OVERWORLD_FALLBACK = DimensionAtmosphereProfile.fromJson(
                new ResourceLocation("mge", "fallback"), fallbackJson);
    }
}
