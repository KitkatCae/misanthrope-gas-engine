package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.util.ChunkIterator;
import exp.CCnewmods.misanthrope_world.temperature.api.MisTemperatureAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Misanthrope World temperature system compat.
 *
 * <p><b>Migration note:</b> temperature and collapse physics were moved from
 * Misanthrope Core to Misanthrope World (modid {@code misanthrope_world},
 * package {@code exp.CCnewmods.misanthrope_world.*}) in pack version 2.0.6.
 * This class replaces the old {@code MisanthropeCoreCompat}, which pointed at
 * the retired {@code misanthrope_core} modid/package and is no longer valid.
 *
 * <p>Now that we have the real Misanthrope World jar to compile against,
 * this calls {@link MisTemperatureAPI} directly instead of reflectively —
 * reflection was only ever a hedge against not having the jar at compile
 * time, which no longer applies.
 *
 * <p>Reads {@code MisTemperatureAPI.getAmbientCelsius()} to drive
 * temperature-dependent gas chemistry on sampled chunk positions, and is
 * also used by {@link SlimePressureCompat} (ice-cube melt check) and the
 * shockwave system (thermal rim-glow gating).
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MisanthropeWorldCompat {

    public static final String MODID = "misanthrope_world";
    private static boolean loaded = false;
    private static final int SAMPLE_INTERVAL = 40;
    private static int tick = 0;

    private MisanthropeWorldCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Misanthrope World detected — ambient temperature gas chemistry active.");
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
            applyAmbientChemistry(level, samplePos);
        });
    }

    private static void applyAmbientChemistry(ServerLevel level, BlockPos pos) {
        double celsius = getAmbientCelsius(level, pos);
        if (Double.isNaN(celsius)) return;

        var comp = GridAtmosphereCompat.getComposition(level, pos);
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
            GridAtmosphereCompat.setComposition(level, pos, comp);
            EnvironmentGrid.enqueue(level, pos);
        }
    }

    /**
     * Returns the raw simulated ambient Celsius at {@code pos}, or
     * {@code Double.NaN} if Misanthrope World isn't loaded or the call fails.
     *
     * <p>Uses {@code getAmbientCelsius} (the raw simulated value) rather than
     * {@code getVisualCelsius} (the tint/display-adjusted value) — callers
     * doing physics decisions (gas chemistry, ice melt, shockwave thermal
     * gating) want the real simulated number, not the display one.
     */
    public static double getAmbientCelsius(Level level, BlockPos pos) {
        if (!loaded) return Double.NaN;
        try {
            return MisTemperatureAPI.getAmbientCelsius(level, pos);
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * Convenience float overload with a fallback, for callers (like the
     * shockwave renderer) that want a usable number rather than a NaN check.
     */
    public static float getCelsiusAt(Level level, BlockPos pos, float fallbackC) {
        double c = getAmbientCelsius(level, pos);
        return Double.isNaN(c) ? fallbackC : (float) c;
    }
}
