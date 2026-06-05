package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Mowzie's Mobs compat.
 *
 * <ul>
 *   <li><b>Frostmaw</b> — tick: continuous DRAGON_ICE_CLOUD + ICE_CRYSTALS passive emission
 *       from its enormous frozen body. Ice breath projectile impact: large ICE_CRYSTAL_SHARDS
 *       burst + DRAGON_ICE_CLOUD saturation.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MowziesCompat {

    public static final String MODID = "mowziesmobs";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private MowziesCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Mowzie's Mobs detected — Frostmaw atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    // ── Tick emissions ────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        // Registry ID confirmed from EntityHandler.class: mowziesmobs:frostmaw
        if (!type.equals("mowziesmobs:frostmaw")) return;

        BlockPos pos = entity.blockPosition();
        // Frostmaw is enormous — emit in a wider radius from its body
        gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 20f, 3);
        partRadius(level, pos, ParticulateType.ICE_CRYSTALS, 25f, 3);
    }

    // ── Projectile impacts ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;

        String type = proj.getType().toString();
        // Registry ID confirmed from EntityHandler.class: mowziesmobs:ice_ball
        if (!type.equals("mowziesmobs:ice_ball")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();
        // Frostmaw ice breath impact — large crystal shard burst
        gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 50f, 4);
        partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 80f, 4);
        partRadius(level, pos, ParticulateType.ICE_CRYSTALS, 40f, 3);
        ShockwaveHandler.spawn(level, pos, 4f);
        ShockwaveDataPacket.sendToNear(level, vec, 4f, 40f);
    }
}
