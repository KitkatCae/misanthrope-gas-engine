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
 * Alex's Mobs compat.
 *
 * <ul>
 *   <li><b>Sunbird</b> — tick: OZONE + CO₂ + IONISED_AIR, drains O₂,
 *       SMOKE_AEROSOL + ASH_CLOUD trail.</li>
 *   <li><b>Bone Serpent</b> — tick: H₂S + BLAZE_FUME from its rotting fire body.</li>
 *   <li><b>Soul Vulture</b> — tick: SOUL_ESSENCE aura.</li>
 *   <li><b>Spectre</b> — tick: SOUL_ESSENCE flicker.</li>
 *   <li><b>Enderiophage / Endergrade / Farseer</b> — tick: ENDER_PARTICULATE.</li>
 *   <li><b>Skelewag</b> — tick: trace H₂S (decaying undead fish).</li>
 *   <li><b>Enderiophage rocket</b> — projectile impact: ENDER_PARTICULATE burst + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AlexsMobsCompat {

    public static final String MODID = "alexsmobs";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private AlexsMobsCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Alex's Mobs detected — mob atmosphere emissions active.");
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
        // Registry IDs confirmed from AMEntityRegistry.class:
        // alexsmobs:sunbird, alexsmobs:bone_serpent, alexsmobs:soul_vulture,
        // alexsmobs:spectre, alexsmobs:enderiophage, alexsmobs:endergrade,
        // alexsmobs:farseer, alexsmobs:skelewag
        if (!type.contains("alexsmobs:")) return;

        BlockPos pos = entity.blockPosition();

        if (type.equals("alexsmobs:sunbird")) {
            // Superheated solar entity — plasma-temperature body
            gas(level, pos,  GasRegistry.OZONE,        10f);
            gas(level, pos,  GasRegistry.CARBON_DIOXIDE, 8f);
            gas(level, pos,  GasRegistry.IONISED_AIR,  12f);
            drain(level, pos, GasRegistry.OXYGEN, 15f);
            part(level, pos, ParticulateType.SMOKE_AEROSOL, 20f);
            part(level, pos, ParticulateType.ASH_CLOUD,     15f);

        } else if (type.equals("alexsmobs:bone_serpent")) {
            // Undead fire serpent — rotting + combustion mix
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 8f);
            gas(level, pos, GasRegistry.BLAZE_FUME,       10f);

        } else if (type.equals("alexsmobs:soul_vulture")) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE, 8f);
            part(level, pos, ParticulateType.SOUL_WISPS, 10f);

        } else if (type.equals("alexsmobs:spectre")) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE, 5f);
            part(level, pos, ParticulateType.SOUL_WISPS, 6f);

        } else if (type.equals("alexsmobs:enderiophage") || type.equals("alexsmobs:endergrade")
                || type.equals("alexsmobs:farseer")) {
            gas(level, pos, GasRegistry.ENDER_PARTICULATE, 8f);

        } else if (type.equals("alexsmobs:skelewag")) {
            // Skeletal fish — trace decay gas, very faint
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 2f);
        }
    }

    // ── Projectile impacts ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;

        String type = proj.getType().toString();
        if (!type.contains("alexsmobs:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        // Registry ID confirmed: alexsmobs:enderiophage_rocket
        if (type.equals("alexsmobs:enderiophage_rocket")) {
            // Enderiophage rocket — ender particulate burst + shockwave
            gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 30f, 3);
            ShockwaveHandler.spawn(level, pos, 5f);
        }
    }
}
