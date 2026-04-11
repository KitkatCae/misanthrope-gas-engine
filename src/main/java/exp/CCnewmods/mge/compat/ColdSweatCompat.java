package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.momosoftworks.coldsweat.api.event.common.temperautre.TemperatureChangedEvent;
import com.momosoftworks.coldsweat.api.util.Temperature;

/**
 * Compat bridge for Cold Sweat 2.4.x.
 *
 * <p>Subscribes to {@link TemperatureChangedEvent} on the Forge event bus.
 * Cold Sweat tracks per-entity body temperature across several traits —
 * we care primarily about {@link Temperature.Trait#BODY} (core temp) and
 * {@link Temperature.Trait#WORLD} (ambient temp felt by the entity).</p>
 *
 * <h3>What we do with temperature data:</h3>
 * <ul>
 *   <li><b>WORLD temp rising (hot environment)</b> — the block the entity is in loses
 *       some water vapour to "drying out" and gains a trace of evaporated volatile gases
 *       if Thermodynamica is also present.</li>
 *   <li><b>WORLD temp falling (cold environment)</b> — ice crystal particulates increase
 *       around the entity; water vapour decreases (condensation).</li>
 *   <li><b>BODY temp extreme (hypo/hyperthermia)</b> — the entity's breathing changes the
 *       local O₂/CO₂ balance slightly more aggressively (gasping, hyperventilation).</li>
 * </ul>
 *
 * <p>Registered as a static Forge event subscriber only if Cold Sweat is loaded.</p>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ColdSweatCompat {

    public static final String CS_MODID = "cold_sweat";
    private static boolean loaded = false;

    private ColdSweatCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(CS_MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Cold Sweat detected — entity temperature → atmosphere reactions active.");
    }

    public static boolean isLoaded() { return loaded; }

    // =========================================================================
    // Cold Sweat event listener
    // =========================================================================

    @SubscribeEvent
    public static void onTemperatureChanged(TemperatureChangedEvent event) {
        if (!loaded) return;

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        Temperature.Trait trait = event.getTrait();
        double newTemp = event.getTemperature();  // Cold Sweat internal unit (roughly MC temperature scale)
        double oldTemp = event.getOldTemperature();
        double delta   = newTemp - oldTemp;

        if (Math.abs(delta) < 0.01) return; // ignore trivial changes

        BlockPos eyePos = BlockPos.containing(entity.getEyePosition());
        BlockEntity be  = level.getBlockEntity(eyePos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;

        switch (trait) {
            case WORLD -> applyWorldTemp(level, eyePos, atm, newTemp, delta);
            case BODY  -> applyBodyTemp(level, eyePos, atm, newTemp, delta);
            default    -> {} // FREEZING_POINT, BURNING_POINT, BASE, etc. — ignore
        }
    }

    // ── World temperature effects ─────────────────────────────────────────────

    /**
     * World temperature (how hot/cold the environment feels) affects gas and particulate
     * balance in the atmosphere block the entity occupies.
     *
     * <p>CS world temperature scale: roughly 0.0 = freezing, 1.0 = comfortable, 2.0 = scorching.
     * Values outside [0, 2] are possible in extreme biomes.</p>
     */
    private static void applyWorldTemp(ServerLevel level, BlockPos pos,
                                        AtmosphereBlockEntity atm, double worldTemp, double delta) {
        var comp  = atm.getComposition();
        var parts = atm.getParticulates();
        boolean changed = false;

        if (worldTemp > 1.5 && delta > 0) {
            // Hot environment rising — evaporate water vapour, expand gases slightly
            float vapourBoost = (float) (delta * 10.0);
            comp.add(GasRegistry.WATER_VAPOR, vapourBoost);
            changed = true;

            // Very hot (worldTemp > 1.8): trace volatile gas release
            if (worldTemp > 1.8) {
                double blockCelsius = ThermodynamicaCompat.getCelsiusAt(level, pos);
                if (!Double.isNaN(blockCelsius) && blockCelsius > 150.0) {
                    comp.add(GasRegistry.CARBON_MONOXIDE, 0.5f);
                    parts.add(ParticulateType.SOOT, 2f);
                }
            }
        } else if (worldTemp < 0.3 && delta < 0) {
            // Cold environment falling — condense vapour, crystallise
            float removedVapour = Math.min(comp.get(GasRegistry.WATER_VAPOR),
                                            (float) Math.abs(delta) * 5.0f);
            comp.add(GasRegistry.WATER_VAPOR, -removedVapour);
            parts.add(ParticulateType.ICE_CRYSTALS, removedVapour * 2f);
            changed = true;
        }

        if (changed) {
            atm.setComposition(comp);
            atm.setParticulates(parts);
            Mge.getScheduler(level).enqueue(pos);
        }
    }

    // ── Body temperature effects ──────────────────────────────────────────────

    /**
     * Body temperature extremes (hypothermia / hyperthermia) cause the entity to
     * breathe harder, affecting local O₂/CO₂ balance more aggressively than normal.
     *
     * <p>CS body temperature: roughly -2.0 (severely hypothermic) to +2.0 (severely hyperthermic).
     * Normal range is approximately -0.5 to +0.5.</p>
     */
    private static void applyBodyTemp(ServerLevel level, BlockPos pos,
                                       AtmosphereBlockEntity atm, double bodyTemp, double delta) {
        var comp = atm.getComposition();
        boolean changed = false;

        // Hyperventilation from cold (hypothermia) — rapid shallow breathing
        if (bodyTemp < -0.8) {
            float hyperventFactor = (float) Math.min(3.0, Math.abs(bodyTemp));
            float o2Consumed  = 0.3f * hyperventFactor;
            float co2Produced = 0.25f * hyperventFactor;
            float o2 = comp.get(GasRegistry.OXYGEN);
            if (o2 > o2Consumed) {
                comp.add(GasRegistry.OXYGEN,       -o2Consumed);
                comp.add(GasRegistry.CARBON_DIOXIDE, co2Produced);
                changed = true;
            }
        }

        // Heat exhaustion (hyperthermia) — shallow, inefficient breathing
        if (bodyTemp > 0.8) {
            float heatFactor  = (float) Math.min(2.0, bodyTemp);
            float o2Consumed  = 0.2f * heatFactor;
            float o2 = comp.get(GasRegistry.OXYGEN);
            if (o2 > o2Consumed) {
                comp.add(GasRegistry.OXYGEN,       -o2Consumed);
                comp.add(GasRegistry.CARBON_DIOXIDE, o2Consumed * 0.8f);
                // Sweating → water vapour injection
                comp.add(GasRegistry.WATER_VAPOR,   heatFactor * 0.3f);
                changed = true;
            }
        }

        if (changed) {
            atm.setComposition(comp);
            Mge.getScheduler(level).enqueue(pos);
        }
    }
}
