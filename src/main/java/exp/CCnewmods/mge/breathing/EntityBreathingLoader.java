package exp.CCnewmods.mge.breathing;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import exp.CCnewmods.mge.Mge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@link EntityBreathingProfile} data from
 * {@code data/<namespace>/entity_breathing/<entity_modid>-<entity_path>.json}
 * on server start and datapack reload.
 *
 * <h3>File naming:</h3>
 * <p>Replace the colon in the entity type ID with a hyphen. Examples:</p>
 * <ul>
 *   <li>{@code minecraft:player}   → {@code data/mge/entity_breathing/minecraft-player.json}</li>
 *   <li>{@code minecraft:zombie}   → {@code data/mge/entity_breathing/minecraft-zombie.json}</li>
 *   <li>{@code undergarden:scintling} → {@code data/mge/entity_breathing/undergarden-scintling.json}</li>
 * </ul>
 *
 * <h3>Fallback resolution order (when no explicit profile is found):</h3>
 * <ol>
 *   <li>Explicit JSON profile for this entity type.</li>
 *   <li>Mob category default: {@code UNDEAD} and {@code MISC} → {@link EntityBreathingProfile#NON_BREATHER}.
 *       Everything else → {@link EntityBreathingProfile#DEFAULT_BREATHING}.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EntityBreathingLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    public static final EntityBreathingLoader INSTANCE = new EntityBreathingLoader();

    private static final Map<ResourceLocation, EntityBreathingProfile> PROFILES = new HashMap<>();

    private EntityBreathingLoader() {
        super(GSON, "entity_breathing");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> map,
                          ResourceManager manager,
                          ProfilerFiller profiler) {
        PROFILES.clear();
        int loaded = 0;

        for (Map.Entry<ResourceLocation, JsonObject> entry : map.entrySet()) {
            try {
                EntityBreathingProfile profile =
                        EntityBreathingProfile.fromJson(entry.getKey(), entry.getValue());
                PROFILES.put(profile.entityType, profile);
                loaded++;
            } catch (Exception e) {
                Mge.LOGGER.error("[MGE] Failed to load entity breathing profile {}: {}",
                        entry.getKey(), e.getMessage());
            }
        }

        Mge.LOGGER.info("[MGE] Loaded {} entity breathing profiles.", loaded);
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Returns the breathing profile for the given entity, resolving through fallbacks.
     * Never returns null.
     */
    public static EntityBreathingProfile get(LivingEntity entity) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (typeId != null) {
            EntityBreathingProfile explicit = PROFILES.get(typeId);
            if (explicit != null) return explicit;
        }
        return categoryDefault(entity.getType());
    }

    /** Returns the profile for a specific entity type ID, or the category default. */
    public static EntityBreathingProfile get(ResourceLocation entityTypeId) {
        EntityBreathingProfile explicit = PROFILES.get(entityTypeId);
        if (explicit != null) return explicit;

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityTypeId);
        if (type != null) return categoryDefault(type);

        return EntityBreathingProfile.DEFAULT_BREATHING;
    }

    public static Map<ResourceLocation, EntityBreathingProfile> all() {
        return Collections.unmodifiableMap(PROFILES);
    }

    // -------------------------------------------------------------------------
    // Category defaults
    // -------------------------------------------------------------------------

    private static EntityBreathingProfile categoryDefault(EntityType<?> type) {
        MobCategory cat = type.getCategory();
        return switch (cat) {
            case MISC, WATER_CREATURE, WATER_AMBIENT, AXOLOTLS
                    -> EntityBreathingProfile.NON_BREATHER;
            // UNDEAD is not a MobCategory in 1.20 — undead mobs use MONSTER.
            // We detect them by checking if they're in MobCategory.MONSTER and are undead
            // via EntityType. Since we can't check that at category level, MONSTER defaults
            // to breathing — individual undead types (zombie, skeleton, etc.) should have
            // explicit NON_BREATHER JSON profiles.
            default -> EntityBreathingProfile.DEFAULT_BREATHING;
        };
    }
}
