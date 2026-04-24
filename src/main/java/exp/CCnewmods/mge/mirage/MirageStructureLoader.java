package exp.CCnewmods.mge.mirage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exp.CCnewmods.mge.Mge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.util.*;

/**
 * Loads mirage structure definitions from two co-located files per entry:
 *
 * <h3>Sidecar JSON (data/mge/mirage_structures/my_structure.json)</h3>
 * <pre>{@code
 * {
 *   "structure": "mge:mirage_structures/my_structure",
 *   "biomes": ["#minecraft:is_badlands", "#minecraft:is_dry", "minecraft:desert"],
 *   "min_distance": 60,
 *   "max_distance": 120,
 *   "scale": 1.0,
 *   "frequency": 0.3,
 *   "float_height": 2.0
 * }
 * }</pre>
 *
 * <h3>NBT structure (assets/mge/structures/my_structure.nbt)</h3>
 * Standard vanilla structure block export format. Copy any .nbt file from
 * {@code .minecraft/saves/<world>/generated/<namespace>/structures/} here.
 * The structure is rendered client-side as ghost blocks — it is never placed
 * in the world.
 *
 * <h3>Built-in structures</h3>
 * MGE ships sidecar JSONs pointing at vanilla structures (desert_temple,
 * desert_well, desert_village_house) that already exist in vanilla's assets.
 * These are loaded from vanilla's resource pack, not duplicated here.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MirageStructureLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    public static final MirageStructureLoader INSTANCE = new MirageStructureLoader();

    /** All loaded mirage definitions, keyed by their resource location. */
    private static final Map<ResourceLocation, MirageDefinition> DEFINITIONS
            = new LinkedHashMap<>();

    /** Cached structure templates, keyed by structure RL. Populated during apply(). */
    private static final Map<ResourceLocation, StructureTemplate> TEMPLATES
            = new HashMap<>();

    private MirageStructureLoader() { super(GSON, "mirage_structures"); }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map,
                          ResourceManager manager, ProfilerFiller profiler) {
        DEFINITIONS.clear();
        TEMPLATES.clear();
        int loaded = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                MirageDefinition def = MirageDefinition.fromJson(json);

                // Load the NBT structure template
                ResourceLocation nbtId = def.structureId();
                // Structure NBT lives in assets/<ns>/structures/<path>.nbt
                ResourceLocation nbtPath = new ResourceLocation(
                        nbtId.getNamespace(),
                        "structures/" + nbtId.getPath() + ".nbt");

                Optional<Resource> resource = manager.getResource(nbtPath);
                if (resource.isEmpty()) {
                    // Try vanilla's built-in structures path
                    nbtPath = new ResourceLocation("minecraft",
                            "structures/" + nbtId.getPath() + ".nbt");
                    resource = manager.getResource(nbtPath);
                }

                if (resource.isPresent()) {
                    try (InputStream stream = resource.get().open()) {
                        CompoundTag tag = NbtIo.readCompressed(stream);
                        StructureTemplate template = new StructureTemplate();
                        template.load(net.minecraftforge.registries.ForgeRegistries.BLOCKS,
                                tag);
                        TEMPLATES.put(nbtId, template);
                        DEFINITIONS.put(entry.getKey(), def);
                        loaded++;
                    }
                } else {
                    Mge.LOGGER.warn("[MGE] Mirage structure NBT not found: {}", nbtPath);
                }
            } catch (Exception e) {
                Mge.LOGGER.error("[MGE] Failed to load mirage {}: {}",
                        entry.getKey(), e.getMessage());
            }
        }
        Mge.LOGGER.info("[MGE] Loaded {} mirage structure definitions.", loaded);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Query
    // ─────────────────────────────────────────────────────────────────────────

    public static Collection<MirageDefinition> all() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    public static StructureTemplate getTemplate(ResourceLocation id) {
        return TEMPLATES.get(id);
    }

    /** Returns a list of definitions compatible with the given biome id, weighted by frequency. */
    public static List<MirageDefinition> forBiome(ResourceLocation biomeId) {
        List<MirageDefinition> result = new ArrayList<>();
        for (MirageDefinition def : DEFINITIONS.values()) {
            if (def.matchesBiome(biomeId)) result.add(def);
        }
        return result;
    }
}
