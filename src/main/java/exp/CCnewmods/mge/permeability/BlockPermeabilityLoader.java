package exp.CCnewmods.mge.permeability;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exp.CCnewmods.mge.Mge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlockPermeabilityLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    public static final BlockPermeabilityLoader INSTANCE = new BlockPermeabilityLoader();
    private static final Map<ResourceLocation, Float>             BASE   = new HashMap<>();
    private static final Map<ResourceLocation, Map<String,Float>> STATES = new HashMap<>();

    private BlockPermeabilityLoader() { super(GSON, "gas_permeability"); }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map,
                          ResourceManager manager, ProfilerFiller profiler) {
        BASE.clear(); STATES.clear();
        int n = 0;
        for (var entry : map.entrySet()) {
            try {
                JsonObject j = entry.getValue().getAsJsonObject();
                ResourceLocation id = new ResourceLocation(j.get("block").getAsString());
                if (j.has("states")) {
                    Map<String,Float> sm = new HashMap<>();
                    j.getAsJsonObject("states").entrySet()
                     .forEach(e -> sm.put(e.getKey(), e.getValue().getAsFloat()));
                    STATES.put(id, sm);
                } else {
                    BASE.put(id, j.get("permeability").getAsFloat());
                }
                n++;
            } catch (Exception e) {
                Mge.LOGGER.error("[MGE] Bad permeability {}: {}", entry.getKey(), e.getMessage());
            }
        }
        Mge.LOGGER.info("[MGE] Loaded {} block permeability definitions.", n);
    }

    public static float getPermeability(BlockGetter level, BlockPos pos, BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id != null) {
            Map<String,Float> sm = STATES.get(id);
            if (sm != null) {
                String props = stateProps(state);
                Float v = sm.get(props);
                if (v != null) return v;
                for (var e : sm.entrySet()) if (props.contains(e.getKey())) return e.getValue();
            }
            Float base = BASE.get(id);
            if (base != null) return base;
        }
        if (state.isAir()) return 1.0f;
        if (state.isSolidRender(level, pos)) return 0.0f;
        return 0.7f;
    }

    static String stateProps(BlockState s) {
        var sb = new StringBuilder();
        s.getProperties().forEach(p -> {
            if (sb.length() > 0) sb.append(',');
            sb.append(p.getName()).append('=').append(s.getValue(p));
        });
        return sb.toString();
    }
}
