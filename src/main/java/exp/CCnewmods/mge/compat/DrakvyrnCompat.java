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
 * Choup's Drakvyrn Mod compat (modid: choups_drakvyrn_mod).
 *
 * All entity IDs confirmed from ChoupsDrakvyrnModModEntities.class.
 * Dragon variants are grouped by elemental chemistry:
 *
 * <ul>
 *   <li><b>Fire variants</b> (drakvyrn, brown_draklet, drakthal, drave, bronze_drakvyrn,
 *       red tamed variants, ground_drakvryn): fire breath via IceAndFire helper.</li>
 *   <li><b>Ice variants</b> (drozen, tamed_drozen, draekhalde): DRAGON_ICE_CLOUD +
 *       ICE_CRYSTALS aura.</li>
 *   <li><b>Warped/void variants</b> (warped_drakvyrn, tamed_warped_drakvyrn):
 *       VOID_BREATH + ENDER_PARTICULATE.</li>
 *   <li><b>Spectral variants</b> (purple_drakvyrn, amaranth_drakvyrn and tamed):
 *       SOUL_ESSENCE aura.</li>
 *   <li><b>Silver/green variants</b>: mild IONISED_AIR (lightning sub-type).</li>
 *   <li><b>Projectiles</b>: dragon_breath_ball_projectile → fire burst + shockwave;
 *       drozen_spear_projectile → ICE_CRYSTAL_SHARDS + DRAGON_ICE_CLOUD;
 *       frosted_drozen_spear_projectile → heavier ice burst.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DrakvyrnCompat {

    public static final String MODID = "choups_drakvyrn_mod";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private static final Set<String> FIRE_VARIANTS = Set.of(
        "choups_drakvyrn_mod:drakvyrn",
        "choups_drakvyrn_mod:agressive_drakvyrn",
        "choups_drakvyrn_mod:idle_drakvyrn",
        "choups_drakvyrn_mod:passive_drakvyrn",
        "choups_drakvyrn_mod:brown_draklet",
        "choups_drakvyrn_mod:agressive_brown_draklet",
        "choups_drakvyrn_mod:idle_brown_draklet",
        "choups_drakvyrn_mod:passive_brown_draklet",
        "choups_drakvyrn_mod:drakthal",
        "choups_drakvyrn_mod:drave",
        "choups_drakvyrn_mod:bronze_drakvyrn",
        "choups_drakvyrn_mod:ground_drakvryn",
        "choups_drakvyrn_mod:tamed_bronze_drakvyrn",
        "choups_drakvyrn_mod:tamed_baby_bronze_drakvyrn",
        "choups_drakvyrn_mod:tamed_red_drakvyrn",
        "choups_drakvyrn_mod:tamed_baby_red_drakvyrn"
    );

    private static final Set<String> ICE_VARIANTS = Set.of(
        "choups_drakvyrn_mod:drozen",
        "choups_drakvyrn_mod:tamed_drozen",
        "choups_drakvyrn_mod:draekhalde"
    );

    private static final Set<String> VOID_VARIANTS = Set.of(
        "choups_drakvyrn_mod:warped_drakvyrn",
        "choups_drakvyrn_mod:tamed_warped_drakvyrn",
        "choups_drakvyrn_mod:tamed_baby_warped_drakvyrn"
    );

    private static final Set<String> SPECTRAL_VARIANTS = Set.of(
        "choups_drakvyrn_mod:purple_drakvyrn",
        "choups_drakvyrn_mod:amaranth_drakvyrn",
        "choups_drakvyrn_mod:tamed_amaranth_drakvyrn",
        "choups_drakvyrn_mod:tamed_baby_amaranth_drakvyrn",
        "choups_drakvyrn_mod:tamed_baby_purple_drakvyrn"
    );

    private static final Set<String> LIGHTNING_VARIANTS = Set.of(
        "choups_drakvyrn_mod:silver_drakvyrn_new",
        "choups_drakvyrn_mod:green_drakvyrn",
        "choups_drakvyrn_mod:tamed_silver_drakvyrn",
        "choups_drakvyrn_mod:tamed_baby_silver_drakvyrn",
        "choups_drakvyrn_mod:tamed_green_drakvyrn",
        "choups_drakvyrn_mod:tamed_baby_green_drakvyrn",
        "choups_drakvyrn_mod:tamed_black_drakvyrn",
        "choups_drakvyrn_mod:tamed_baby_black_drakvyrn"
    );

    private DrakvyrnCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Choup's Drakvyrn Mod detected — drakvyrn atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("choups_drakvyrn_mod:")) return;

        BlockPos pos = entity.blockPosition();

        if (FIRE_VARIANTS.contains(type)) {
            IceAndFireCompat.emitFireDragonBreath(level, pos, 0.8f);
        } else if (ICE_VARIANTS.contains(type)) {
            gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 18f, 3);
            partRadius(level, pos, ParticulateType.ICE_CRYSTALS, 22f, 3);
        } else if (VOID_VARIANTS.contains(type)) {
            gas(level, pos, GasRegistry.VOID_BREATH,       12f);
            gas(level, pos, GasRegistry.ENDER_PARTICULATE, 10f);
            drain(level, pos, GasRegistry.OXYGEN, 8f);
        } else if (SPECTRAL_VARIANTS.contains(type)) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE,   10f);
            part(level, pos, ParticulateType.SOUL_WISPS, 12f);
        } else if (LIGHTNING_VARIANTS.contains(type)) {
            gas(level, pos, GasRegistry.IONISED_AIR, 10f);
            gas(level, pos, GasRegistry.OZONE,         4f);
            part(level, pos, ParticulateType.IONISED_PARTICLES, 8f);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("choups_drakvyrn_mod:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        if (VOID_VARIANTS.contains(type)) {
            gasRadius(level, pos, GasRegistry.VOID_BREATH,       50f, 5);
            gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 30f, 4);
            drainRadius(level, pos, GasRegistry.OXYGEN,  30f, 4);
            ShockwaveHandler.spawn(level, pos, 8f);
        } else if (ICE_VARIANTS.contains(type)) {
            gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 60f, 6);
            partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 80f, 5);
            ShockwaveHandler.spawn(level, pos, 7f);
        } else if (FIRE_VARIANTS.contains(type)) {
            gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 40f, 5);
            gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 20f, 4);
            partRadius(level, pos, ParticulateType.ASH_CLOUD,  80f, 5);
            ShockwaveHandler.spawn(level, pos, 7f);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("choups_drakvyrn_mod:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "choups_drakvyrn_mod:dragon_breath_ball_projectile" -> {
                IceAndFireCompat.emitFireDragonBreath(level, pos, 1.0f);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "choups_drakvyrn_mod:drozen_spear_projectile" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD,  30f, 3);
                partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 40f, 3);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "choups_drakvyrn_mod:frosted_drozen_spear_projectile" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD,  50f, 4);
                partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 70f, 4);
                partRadius(level, pos, ParticulateType.ICE_CRYSTALS,       35f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
        }
    }
}
