package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Alex's Caves compat.
 *
 * <ul>
 *   <li><b>Tremorzilla</b> — periodic EM/steam pulse: WATER_VAPOR + IONISED_AIR +
 *       IONISED_PARTICLES from the electrical discharge. Every step also shakes
 *       dust from surrounding terrain: DUST + COAL_DUST + GRAVEL_DUST + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AlexsCavesCompat {

    public static final String MODID = "alexscaves";
    private static boolean loaded = false;

    // Two separate intervals — EM pulse every 3 s, dust every 1 s
    private static final int EM_TICK_INTERVAL   = 60;
    private static final int DUST_TICK_INTERVAL = 20;
    private static int tick = 0;

    private AlexsCavesCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Alex's Caves detected — Tremorzilla atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    // ── Tick emissions ────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        // Registry ID confirmed from loot tables: alexscaves:tremorzilla
        if (!type.equals("alexscaves:tremorzilla")) return;

        ++tick;
        BlockPos pos = entity.blockPosition();

        // Footstep dust shockwave — every second
        if (tick % DUST_TICK_INTERVAL == 0) {
            gasRadius(level, pos, GasRegistry.WATER_VAPOR, 8f, 3);
            partRadius(level, pos, ParticulateType.DUST,       30f, 4);
            partRadius(level, pos, ParticulateType.COAL_DUST,  10f, 3);
            partRadius(level, pos, ParticulateType.GRAVEL_DUST, 15f, 3);
            // Ground shockwave from footsteps
            ShockwaveHandler.spawn(level, pos, 3.5f);
        }

        // EM/steam pulse — every 3 seconds
        if (tick % EM_TICK_INTERVAL == 0) {
            gasRadius(level, pos, GasRegistry.WATER_VAPOR,  25f, 5);
            gasRadius(level, pos, GasRegistry.IONISED_AIR,  20f, 4);
            gasRadius(level, pos, GasRegistry.OZONE,         8f, 3);
            partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 30f, 4);
            // Larger EM shockwave ring
            ShockwaveHandler.spawn(level, pos, 7f);
        }
    }
}
