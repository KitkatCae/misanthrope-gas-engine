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
 * Twilight Forest compat.
 *
 * <ul>
 *   <li><b>Ur-Ghast</b> — tick: BLAZE_FUME + SOUL_SMOKE + SO₂ at high intensity.
 *       Fireball impact: large shockwave.</li>
 *   <li><b>Lich</b> — tick: SOUL_ESSENCE + WITHER_MIASMA aura.</li>
 *   <li><b>Knight Phantom</b> — tick: faint SOUL_ESSENCE.</li>
 *   <li><b>Twilight Hydra</b> — tick: BLAZE_FUME + CO₂ (multi-headed fire).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TwilightForestCompat {

    public static final String MODID = "twilightforest";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private TwilightForestCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Twilight Forest detected — boss atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    // ── Tick emissions ────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        BlockPos pos = entity.blockPosition();
        String type = entity.getType().toString();

        // Registry IDs confirmed from loot tables and trophy items:
        // twilightforest:ur_ghast, twilightforest:lich, twilightforest:knight_phantom,
        // twilightforest:hydra
        if (!type.contains("twilightforest:")) return;

        if (type.equals("twilightforest:ur_ghast")) {
            // Ur-Ghast — enormous fire ghast, large emission radius
            gasRadius(level, pos, GasRegistry.BLAZE_FUME, 20f, 4);
            gasRadius(level, pos, GasRegistry.SOUL_SMOKE,  12f, 3);
            gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 10f, 3);
            drainRadius(level, pos, GasRegistry.OXYGEN, 15f, 3);
            partRadius(level, pos, ParticulateType.ASH_CLOUD, 30f, 3);

        } else if (type.equals("twilightforest:lich")) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE, 12f);
            gas(level, pos, GasRegistry.WITHER_MIASMA, 8f);
            part(level, pos, ParticulateType.SOUL_WISPS, 15f);

        } else if (type.equals("twilightforest:knight_phantom")) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE, 5f);
            part(level, pos, ParticulateType.SOUL_WISPS, 6f);

        } else if (type.equals("twilightforest:hydra")) {
            // Twilight Hydra — multi-headed, heavy fire output
            gas(level, pos, GasRegistry.BLAZE_FUME, 18f);
            gas(level, pos, GasRegistry.CARBON_DIOXIDE, 12f);
            gas(level, pos, GasRegistry.SULFUR_DIOXIDE, 6f);
            drainRadius(level, pos, GasRegistry.OXYGEN, 12f, 2);
            part(level, pos, ParticulateType.ASH_CLOUD, 25f);
        }
    }

    // ── Projectile impacts ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;

        String type = proj.getType().toString();
        if (!type.contains("twilightforest:")) return;

        BlockPos pos = proj.blockPosition();
        Vec3 vec = proj.position();

        if (type.equals("twilightforest:ur_ghast_fireball")) {
            // Ur-Ghast fireball — large shockwave + heavy blaze fume burst
            gasRadius(level, pos, GasRegistry.BLAZE_FUME, 40f, 4);
            gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 20f, 3);
            drainRadius(level, pos, GasRegistry.OXYGEN, 30f, 3);
            partRadius(level, pos, ParticulateType.ASH_CLOUD, 120f, 4);
            ShockwaveHandler.spawn(level, pos, 9f);
            ShockwaveDataPacket.sendToNear(level, vec, 9f, 80f);

        } else if (type.equals("twilightforest:lich_bolt") || type.equals("twilightforest:lich_bomb")) {
            // Lich magic bolt/bomb — soul essence + wither burst
            gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 15f, 2);
            gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 10f, 2);
            partRadius(level, pos, ParticulateType.SOUL_WISPS, 25f, 2);
            ShockwaveHandler.spawn(level, pos, 3f);
            ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
        }
    }
}