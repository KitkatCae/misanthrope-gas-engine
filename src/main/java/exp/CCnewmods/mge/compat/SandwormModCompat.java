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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Sandworm Mod compat (modid: sandworm_mod).
 *
 * Entity IDs confirmed from ModEntities.class:
 * {@code sandworm_mod:worm_head_segment, sandworm_mod:worm_segment, sandworm_mod:worm_chain}.
 *
 * This is a distinct mod from the BossesRise sandworm — this one is the full
 * dedicated sandworm mod with a proper segmented body. Much larger scale.
 *
 * <ul>
 *   <li><b>worm_head_segment</b> — the active head: massive DUST + GRAVEL_DUST + COAL_DUST
 *       displacement every 10 ticks + continuous shockwave from underground movement.
 *       Also emits WATER_VAPOR (displaced subterranean moisture).</li>
 *   <li><b>worm_segment</b> — body segments: lighter dust trail, no shockwave.</li>
 *   <li><b>worm_chain</b> — connector entity: minimal emission, just dust trace.</li>
 *   <li><b>Death</b>: surface eruption burst — the worm's death causes ground collapse.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SandwormModCompat {

    public static final String MODID = "sandworm_mod";
    private static boolean loaded = false;

    private static final int HEAD_TICK     = 8;  // fast — constant movement
    private static final int SEGMENT_TICK  = 20;
    private static int tick = 0;

    private SandwormModCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Sandworm Mod detected — seismic atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("sandworm_mod:")) return;

        ++tick;
        BlockPos pos = entity.blockPosition();
        Vec3 vec = entity.position();

        switch (type) {
            case "sandworm_mod:worm_head_segment" -> {
                if (tick % HEAD_TICK != 0) return;
                // Enormous seismic displacement
                partRadius(level, pos, ParticulateType.DUST,        60f, 6);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 45f, 5);
                partRadius(level, pos, ParticulateType.COAL_DUST,   15f, 4);
                gasRadius(level, pos,  GasRegistry.WATER_VAPOR,     12f, 4);
                // Continuous ground shockwave
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 64f);
            }
            case "sandworm_mod:worm_segment" -> {
                if (tick % SEGMENT_TICK != 0) return;
                // Body segments create lighter trail
                part(level, pos, ParticulateType.DUST,        20f);
                part(level, pos, ParticulateType.GRAVEL_DUST, 14f);
            }
            case "sandworm_mod:worm_chain" -> {
                if (tick % SEGMENT_TICK != 0) return;
                part(level, pos, ParticulateType.DUST, 8f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();

        // Only trigger death burst on the head
        if (!type.equals("sandworm_mod:worm_head_segment")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        // Surface eruption — the worm's death causes ground collapse and gas escape
        gasRadius(level, pos,  GasRegistry.WATER_VAPOR,     50f, 8);
        partRadius(level, pos, ParticulateType.DUST,        250f, 12);
        partRadius(level, pos, ParticulateType.GRAVEL_DUST, 180f, 10);
        partRadius(level, pos, ParticulateType.COAL_DUST,    60f,  8);
        ShockwaveHandler.spawn(level, pos, 20f);
        ShockwaveDataPacket.sendToNear(level, vec, 20f, 200f);
    }
}
