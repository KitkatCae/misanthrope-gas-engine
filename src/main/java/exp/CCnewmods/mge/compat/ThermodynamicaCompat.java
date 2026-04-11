package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import com.Tribulla.thermodynamica.api.HeatAPI;
import com.Tribulla.thermodynamica.api.HeatTier;
import com.Tribulla.thermodynamica.api.TemperatureChangeEvent;

/**
 * Compat bridge for Thermodynamica 0.2.x.
 *
 * <p>Uses {@link HeatAPI#onTemperatureChange(java.util.function.Consumer)} to register a
 * callback that fires whenever a block's simulated temperature changes. We use this to:</p>
 * <ul>
 *   <li>Evaporate water vapour from nearby atmosphere blocks when temperatures rise.</li>
 *   <li>Condense water vapour (and inject ice crystal particulates) when temperatures drop.</li>
 *   <li>Accelerate gas chemistry at high temperatures — flash-boil volatile gases,
 *       decompose compounds at extreme tiers (POS4/POS5).</li>
 *   <li>Suppress gas diffusion at very cold tiers (NEG4/NEG5) — gases freeze out.</li>
 * </ul>
 *
 * <p>Also exposes {@link #getCelsiusAt(Level, BlockPos)} as a convenience for
 * other MGE systems (e.g. {@link ColdSweatCompat}) that need block temperature.</p>
 */
public final class ThermodynamicaCompat {

    public static final String THERMO_MODID = "thermodynamica";
    private static boolean loaded = false;

    private ThermodynamicaCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(THERMO_MODID)) return;

        HeatAPI api = HeatAPI.get();
        if (api == null) {
            Mge.LOGGER.warn("[MGE] Thermodynamica present but HeatAPI.get() returned null — skipping.");
            return;
        }

        // Register temperature-change callback
        api.onTemperatureChange(ThermodynamicaCompat::onTemperatureChange);

        loaded = true;
        Mge.LOGGER.info("[MGE] Thermodynamica detected — block-temperature gas reactions active.");
    }

    public static boolean isLoaded() { return loaded; }

    // =========================================================================
    // Temperature change callback
    // =========================================================================

    private static void onTemperatureChange(TemperatureChangeEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;

        BlockPos pos = event.getPos();
        double newC  = event.getNewCelsius();
        double oldC  = event.getOldCelsius();
        HeatTier newTier = event.getNewTier();

        // Check the atmosphere block at this position and the one directly above
        for (BlockPos target : new BlockPos[]{ pos, pos.above() }) {
            BlockEntity be = level.getBlockEntity(target);
            if (!(be instanceof AtmosphereBlockEntity atm)) continue;

            applyTemperatureEffects(sl, target, atm, oldC, newC, newTier);
        }
    }

    private static void applyTemperatureEffects(ServerLevel level, BlockPos pos,
                                                  AtmosphereBlockEntity atm,
                                                  double oldC, double newC, HeatTier tier) {
        var comp = atm.getComposition();
        var parts = atm.getParticulates();
        boolean gasChanged = false;
        boolean partChanged = false;

        // ── Water vapour evaporation / condensation ───────────────────────────
        if (newC > 100.0 && newC > oldC) {
            // Boiling point exceeded — inject water vapour aggressively
            float delta = (float) Math.min(5.0, (newC - 100.0) * 0.05);
            comp.add(GasRegistry.WATER_VAPOR, delta);
            gasChanged = true;
        } else if (newC < 0.0 && newC < oldC) {
            // Freezing — remove water vapour, add ice crystals
            float removedVapour = Math.min(comp.get(GasRegistry.WATER_VAPOR),
                                            (float) Math.abs(newC) * 0.02f);
            comp.add(GasRegistry.WATER_VAPOR, -removedVapour);
            parts.add(ParticulateType.ICE_CRYSTALS, removedVapour * 3f);
            gasChanged = true;
            partChanged = true;
        }

        // ── Extreme heat tiers — volatile chemistry ───────────────────────────
        if (tier == HeatTier.POS4 || tier == HeatTier.POS5) {
            // At extreme heat: methane + propane decompose faster, O₂ rapidly consumed
            float o2 = comp.get(GasRegistry.OXYGEN);
            float ch4 = comp.get(GasRegistry.METHANE);
            if (ch4 > 0f && o2 > 0f) {
                float burned = Math.min(ch4, 0.5f);
                comp.add(GasRegistry.METHANE,       -burned);
                comp.add(GasRegistry.OXYGEN,        -burned * 2f);
                comp.add(GasRegistry.CARBON_DIOXIDE, burned);
                comp.add(GasRegistry.WATER_VAPOR,    burned * 0.5f);
                gasChanged = true;
            }
            // Inject smoke/soot at extreme heat
            parts.add(ParticulateType.SOOT, (float)(newC - 300.0) * 0.001f);
            partChanged = true;
        }

        // ── Extreme cold tiers — gas freeze-out ───────────────────────────────
        if (tier == HeatTier.NEG4 || tier == HeatTier.NEG5) {
            // CO₂ sublimates at −78°C, remove a fraction
            float co2 = comp.get(GasRegistry.CARBON_DIOXIDE);
            if (co2 > 0f) {
                comp.add(GasRegistry.CARBON_DIOXIDE, -Math.min(co2, 0.1f));
                gasChanged = true;
            }
        }

        if (gasChanged) atm.setComposition(comp);
        if (partChanged) atm.setParticulates(parts);
        if (gasChanged || partChanged) Mge.getScheduler(level).enqueue(pos);
    }

    // =========================================================================
    // Public temperature query (used by ColdSweatCompat and PlayerGasEffectHandler)
    // =========================================================================

    /**
     * Returns the simulated block temperature at a position in °C.
     * Falls back to biome ambient temperature if Thermodynamica has no data for that block.
     *
     * @return Temperature in Celsius, or {@link Double#NaN} if unavailable.
     */
    public static double getCelsiusAt(Level level, BlockPos pos) {
        if (!loaded) return Double.NaN;
        try {
            var opt = HeatAPI.get().getSimulatedCelsius(level, pos);
            if (opt.isPresent()) return opt.getAsDouble();
            // Fall back to biome offset
            return HeatAPI.get().getBiomeOffset(level, pos);
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
