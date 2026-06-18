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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * The Ravenous compat (modid: the_ravenous).
 *
 * All entity IDs confirmed from TheRavenousModEntities.class and entity class names:
 * the_ravenous, the_ravenous1–6 (Theravenous5), the_screecher, mini_rav, rav_scream.
 *
 * <ul>
 *   <li><b>the_ravenous (all variants 1–6)</b> — cosmic horror built from biological matter:
 *       maximum decay gas profile plus VOID_BREATH (extradimensional origin).
 *       Larger scale on higher-numbered variants.</li>
 *   <li><b>the_screecher</b> — sonic predator: IONISED_AIR + ozone from subsonic screech.</li>
 *   <li><b>mini_rav</b> — juvenile: trace decay gases.</li>
 *   <li><b>rav_scream</b> — sonic projectile: IONISED_AIR concussive burst + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TheRavenousCompat {

    public static final String MODID = "the_ravenous";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    // All confirmed variant IDs from entity class names (TheRavenousEntity,
    // TheRavenous1Entity .. TheRavenous4Entity, TheRavenous6Entity, Theravenous5Entity)
    private static final Set<String> MAIN_VARIANTS = Set.of(
        "the_ravenous:the_ravenous",
        "the_ravenous:the_ravenous1",
        "the_ravenous:the_ravenous2",
        "the_ravenous:the_ravenous3",
        "the_ravenous:the_ravenous4",
        "the_ravenous:theravenous5",
        "the_ravenous:the_ravenous6"
    );

    private TheRavenousCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] The Ravenous detected — cosmic decay atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("the_ravenous:")) return;

        BlockPos pos = entity.blockPosition();

        if (MAIN_VARIANTS.contains(type)) {
            // Scale emission by variant number — higher = bigger horror
            float scale = scaleForVariant(type);
            gas(level, pos, GasRegistry.CADAVERINE,       8f * scale);
            gas(level, pos, GasRegistry.PUTRESCINE,       8f * scale);
            gas(level, pos, GasRegistry.INDOLE,           5f * scale);
            gas(level, pos, GasRegistry.SKATOLE,          4f * scale);
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 6f * scale);
            gas(level, pos, GasRegistry.VOID_BREATH,      6f * scale);
            part(level, pos, ParticulateType.ORGANIC_AEROSOL, 10f * scale);
            drain(level, pos, GasRegistry.OXYGEN, 8f * scale);
        } else if (type.equals("the_ravenous:the_screecher")) {
            gas(level, pos, GasRegistry.IONISED_AIR, 12f);
            gas(level, pos, GasRegistry.OZONE,         5f);
            part(level, pos, ParticulateType.IONISED_PARTICLES, 10f);
        } else if (type.equals("the_ravenous:mini_rav")) {
            gas(level, pos, GasRegistry.CADAVERINE,       3f);
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 2f);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("the_ravenous:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        if (MAIN_VARIANTS.contains(type)) {
            float scale = scaleForVariant(type);
            gasRadius(level, pos, GasRegistry.CADAVERINE,       40f * scale, (int)(4 * scale));
            gasRadius(level, pos, GasRegistry.PUTRESCINE,       40f * scale, (int)(4 * scale));
            gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 30f * scale, (int)(4 * scale));
            gasRadius(level, pos, GasRegistry.VOID_BREATH,      25f * scale, (int)(3 * scale));
            partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 50f * scale, (int)(4 * scale));
            ShockwaveHandler.spawn(level, pos, 8f * scale);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("the_ravenous:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        if (type.equals("the_ravenous:rav_scream")) {
            gasRadius(level, pos, GasRegistry.IONISED_AIR, 20f, 3);
            gasRadius(level, pos, GasRegistry.OZONE,        8f, 2);
            partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 22f, 3);
            ShockwaveHandler.spawn(level, pos, 6f);
        }
    }

    private static float scaleForVariant(String type) {
        if (type.contains("6")) return 2.5f;
        if (type.contains("5")) return 2.0f;
        if (type.contains("4")) return 1.8f;
        if (type.contains("3")) return 1.5f;
        if (type.contains("2")) return 1.3f;
        if (type.contains("1")) return 1.1f;
        return 1.0f;
    }
}
