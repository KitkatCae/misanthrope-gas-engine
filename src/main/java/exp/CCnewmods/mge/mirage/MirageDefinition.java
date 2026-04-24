package exp.CCnewmods.mge.mirage;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Data object for a single mirage structure definition.
 * Loaded from {@code data/mge/mirage_structures/<name>.json}.
 */
public record MirageDefinition(
        ResourceLocation structureId,
        List<String>     biomeFilters,   // biome RLs or tags like "#minecraft:is_dry"
        int              minDistance,
        int              maxDistance,
        float            scale,
        float            frequency,      // 0..1 chance per condition-check
        float            floatHeight     // blocks above terrain the mirage floats
) {
    public static MirageDefinition fromJson(JsonObject json) {
        ResourceLocation structureId = new ResourceLocation(
                json.get("structure").getAsString());

        List<String> biomes = new ArrayList<>();
        if (json.has("biomes")) {
            json.getAsJsonArray("biomes").forEach(e -> biomes.add(e.getAsString()));
        }

        int minDist     = json.has("min_distance")   ? json.get("min_distance").getAsInt()   : 60;
        int maxDist     = json.has("max_distance")   ? json.get("max_distance").getAsInt()   : 120;
        float scale     = json.has("scale")          ? json.get("scale").getAsFloat()        : 1.0f;
        float frequency = json.has("frequency")      ? json.get("frequency").getAsFloat()    : 0.3f;
        float floatH    = json.has("float_height")   ? json.get("float_height").getAsFloat() : 2.0f;

        return new MirageDefinition(structureId, biomes, minDist, maxDist,
                scale, frequency, floatH);
    }

    /**
     * Returns true if this definition should appear in the given biome.
     * Supports plain RL match and tag prefix (#).
     */
    public boolean matchesBiome(ResourceLocation biomeId) {
        if (biomeFilters.isEmpty()) return true; // no filter = show everywhere
        for (String filter : biomeFilters) {
            if (filter.startsWith("#")) {
                // Tag match — check common tag patterns
                String tag = filter.substring(1);
                if (matchesBiomeTag(biomeId, tag)) return true;
            } else {
                if (biomeId.toString().equals(filter)) return true;
            }
        }
        return false;
    }

    private boolean matchesBiomeTag(ResourceLocation biomeId, String tag) {
        // Simple heuristic matching for common biome tags
        String path = biomeId.getPath();
        return switch (tag) {
            case "minecraft:is_dry", "minecraft:is_badlands" ->
                path.contains("desert") || path.contains("badlands") || path.contains("savanna");
            case "minecraft:is_hot" ->
                path.contains("desert") || path.contains("badlands")
                    || path.contains("savanna") || path.contains("jungle");
            case "minecraft:is_snowy" ->
                path.contains("snow") || path.contains("frozen") || path.contains("ice");
            case "minecraft:is_jungle" -> path.contains("jungle");
            default -> biomeId.toString().contains(tag.replace("minecraft:", ""));
        };
    }
}
