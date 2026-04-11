package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.wind.IWindProvider;
import exp.CCnewmods.mge.wind.WindProviderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import net.Gabou.projectatmosphere.api.AtmoApi;
import net.Gabou.projectatmosphere.api.ForecastSampling;
import net.Gabou.projectatmosphere.api.WeatherSnapshot;

/**
 * Direct compat bridge for Project Atmosphere 0.8.x.
 * Uses real PA API: AtmoApi, ForecastSampling, WeatherSnapshot.
 * Registered only when PA is loaded. Call tryLoad() in commonSetup.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProjectAtmosphereCompat implements IWindProvider {

    public static final String PA_MODID = "projectatmosphere";
    private static final int WEATHER_SYNC_INTERVAL_TICKS = 40;
    private static boolean loaded = false;
    private static int tickCounter = 0;

    private ProjectAtmosphereCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(PA_MODID)) return;
        WindProviderManager.setProvider(new ProjectAtmosphereCompat());
        loaded = true;
        Mge.LOGGER.info("[MGE] Project Atmosphere detected — direct wind/weather integration active.");
    }

    // ── IWindProvider ─────────────────────────────────────────────────────────

    @Override
    public Vec3 getWindAt(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl)) return Vec3.ZERO;
        try {
            WeatherSnapshot snap = AtmoApi.getInstance().getCurrentWeather(sl, pos);
            if (snap == null) return Vec3.ZERO;
            float speed = snap.windSpeedMps();
            float angle = snap.windAngleRad();
            // PA angle: 0 = north (-Z), clockwise. Convert to MC XZ vector.
            float x = (float)  Math.sin(angle) * speed;
            float z = (float) -Math.cos(angle) * speed;
            return new Vec3(x, 0, z);
        } catch (Exception e) {
            return Vec3.ZERO;
        }
    }

    // ── Periodic weather → atmosphere sync ───────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!loaded || event.phase != TickEvent.Phase.END) return;
        if (++tickCounter % WEATHER_SYNC_INTERVAL_TICKS != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            syncWeatherToAtmosphere(level);
        }
    }

    private static void syncWeatherToAtmosphere(ServerLevel level) {
        BlockPos samplePos = level.getSharedSpawnPos();
        try {
            WeatherSnapshot snap = AtmoApi.getInstance().getCurrentWeather(level, samplePos);
            if (snap == null) return;
            float humidity    = ForecastSampling.getHumidityPercent(level, samplePos);
            float tempC       = ForecastSampling.getTemperatureC(level, samplePos);
            float pressureHpa = ForecastSampling.getPressureHpa(level, samplePos);
            applyHumidity(level, samplePos, humidity, tempC);
            applyRain(level, samplePos, snap);
            applySnow(level, samplePos, snap, tempC);
            applyPressure(level, samplePos, pressureHpa);
        } catch (Exception e) {
            Mge.LOGGER.debug("[MGE] PA weather sync error: {}", e.getMessage());
        }
    }

    // Humidity → water vapour mbar (Magnus formula for saturation vapour pressure)
    private static void applyHumidity(ServerLevel level, BlockPos pos,
                                       float humidityPct, float tempC) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        double satHpa = 6.1078 * Math.exp(17.27 * tempC / (tempC + 237.3));
        float targetMbar = (float) (satHpa * (humidityPct / 100.0) * 10.0);
        float current = atm.getComposition().get(GasRegistry.WATER_VAPOR);
        float delta = (targetMbar - current) * 0.1f;
        if (Math.abs(delta) > 0.1f) {
            atm.getComposition().add(GasRegistry.WATER_VAPOR, delta);
            atm.setComposition(atm.getComposition());
            Mge.getScheduler(level).enqueueWithNeighbours(pos);
        }
        // High humidity → water droplet particulates
        if (humidityPct > 95f || (tempC < 10f && humidityPct > 80f)) {
            var parts = atm.getParticulates();
            parts.add(ParticulateType.WATER_DROPLETS, humidityPct * 0.05f);
            atm.setParticulates(parts);
        }
    }

    // Rain scrubs water-soluble gases, washes out particulates
    private static void applyRain(ServerLevel level, BlockPos pos, WeatherSnapshot snap) {
        if (snap.rainIntensity() <= 0.01f) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;
        float scrub = snap.rainIntensity() * 2f;
        var comp = atm.getComposition();
        comp.add(GasRegistry.SULFUR_DIOXIDE,   -Math.min(comp.get(GasRegistry.SULFUR_DIOXIDE), scrub));
        comp.add(GasRegistry.AMMONIA,           -Math.min(comp.get(GasRegistry.AMMONIA), scrub));
        comp.add(GasRegistry.HYDROGEN_CHLORIDE, -Math.min(comp.get(GasRegistry.HYDROGEN_CHLORIDE), scrub));
        comp.add(GasRegistry.NITROGEN_DIOXIDE,  -Math.min(comp.get(GasRegistry.NITROGEN_DIOXIDE), scrub * 0.5f));
        comp.add(GasRegistry.WATER_VAPOR, snap.rainIntensity() * 5f);
        atm.setComposition(comp);
        var parts = atm.getParticulates();
        parts.add(ParticulateType.DUST,         -Math.min(parts.get(ParticulateType.DUST), scrub * 5f));
        parts.add(ParticulateType.POLLEN,       -Math.min(parts.get(ParticulateType.POLLEN), scrub * 8f));
        parts.add(ParticulateType.SMOKE_AEROSOL,-Math.min(parts.get(ParticulateType.SMOKE_AEROSOL), scrub * 3f));
        atm.setParticulates(parts);
        Mge.getScheduler(level).enqueueWithNeighbours(pos);
    }

    // Snow → ice crystal particulates, freeze-out water vapour
    private static void applySnow(ServerLevel level, BlockPos pos,
                                   WeatherSnapshot snap, float tempC) {
        if (!snap.isSnowing()) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;
        var parts = atm.getParticulates();
        parts.add(ParticulateType.ICE_CRYSTALS, Math.max(0f, -tempC) * 0.5f + 5f);
        atm.setParticulates(parts);
        if (tempC < 0f) {
            var comp = atm.getComposition();
            comp.add(GasRegistry.WATER_VAPOR, -Math.min(comp.get(GasRegistry.WATER_VAPOR), 2f));
            atm.setComposition(comp);
        }
        Mge.getScheduler(level).enqueue(pos);
    }

    // Barometric pressure → O₂ density effect
    private static void applyPressure(ServerLevel level, BlockPos pos, float pressureHpa) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;
        var comp = atm.getComposition();
        if (pressureHpa < 990f) {
            float deficit = (990f - pressureHpa) * 0.05f;
            comp.add(GasRegistry.OXYGEN, -Math.min(comp.get(GasRegistry.OXYGEN), deficit));
        } else if (pressureHpa > 1020f) {
            comp.add(GasRegistry.OXYGEN, Math.min(1f, (pressureHpa - 1020f) * 0.02f));
        }
        atm.setComposition(comp);
    }
}
