package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.util.ChunkIterator;
import exp.CCnewmods.mge.gas.GasRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Misanthrope Core temperature system compat.
 * Reads MisTemperatureAPI.getAmbientCelsius() reflectively to drive
 * temperature-dependent gas chemistry on sampled chunk positions.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MisanthropeCoreCompat {

    public static final String MISCORE_MODID = "misanthrope_core";
    private static boolean loaded = false;
    private static final int SAMPLE_INTERVAL = 40;
    private static int tick = 0;

    private MisanthropeCoreCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MISCORE_MODID)) return;
        try {
            Class.forName("exp.CCnewmods.misanthrope_core.temperature.api.MisTemperatureAPI");
        } catch (ClassNotFoundException e) {
            Mge.LOGGER.warn("[MGE] misanthrope_core present but MisTemperatureAPI not found.");
            return;
        }
        loaded = true;
        Mge.LOGGER.info("[MGE] Misanthrope Core detected — ambient temperature gas chemistry active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!loaded || event.phase != TickEvent.Phase.END) return;
        if (++tick % SAMPLE_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;
        for (ServerLevel level : event.getServer().getAllLevels()) sampleLevel(level);
    }

    private static void sampleLevel(ServerLevel level) {
        var rand = level.getRandom();
        ChunkIterator.forEach(level, holder -> {
            if (rand.nextInt(8) != 0) return;
            var chunk = holder.getTickingChunk();
            if (chunk == null) return;

            var cp = chunk.getPos();
            int cx = cp.getMiddleBlockX(), cz = cp.getMiddleBlockZ();
            BlockPos surfPos = new BlockPos(cx, 0, cz);
            int surfY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, surfPos).getY();
            BlockPos samplePos = new BlockPos(cx, surfY - 1, cz);

            if (!level.isLoaded(samplePos)) return;
            BlockEntity be = level.getBlockEntity(samplePos);
            if (!(be instanceof AtmosphereBlockEntity atm)) return;

            applyAmbientChemistry(level, samplePos, atm);
        });
    }

    private static void applyAmbientChemistry(ServerLevel level, BlockPos pos,
                                               AtmosphereBlockEntity atm) {
        double celsius = getAmbientCelsius(level, pos);
        if (Double.isNaN(celsius)) return;

        var comp = atm.getComposition();
        boolean changed = false;

        if (celsius > 80.0) {
            comp.add(GasRegistry.WATER_VAPOR, (float) Math.min(2.0, (celsius - 80.0) * 0.02));
            changed = true;
        } else if (celsius < 0.0) {
            float vapor = comp.get(GasRegistry.WATER_VAPOR);
            float remove = Math.min(vapor, (float) Math.abs(celsius) * 0.01f);
            if (remove > 0f) { comp.add(GasRegistry.WATER_VAPOR, -remove); changed = true; }
        }
        if (celsius > 200.0) {
            comp.add(GasRegistry.CARBON_MONOXIDE,
                    (float) Math.min(0.5, (celsius - 200.0) * 0.001));
            changed = true;
        }
        if (celsius < -20.0) {
            comp.add(GasRegistry.CARBON_DIOXIDE, 0.05f);
            changed = true;
        }

        if (changed) {
            atm.setComposition(comp);
            Mge.getScheduler(level).enqueue(pos);
        }
    }

    public static double getAmbientCelsius(Level level, BlockPos pos) {
        try {
            Class<?> api = Class.forName(
                "exp.CCnewmods.misanthrope_core.temperature.api.MisTemperatureAPI");
            java.lang.reflect.Method m = api.getMethod(
                "getAmbientCelsius", Level.class, BlockPos.class);
            return (double) m.invoke(null, level, pos);
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
