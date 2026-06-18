package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
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

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Legendary Monsters compat (modid: legendary_monsters).
 *
 * Entity IDs confirmed from ModEntities.class:
 * <ul>
 *   <li><b>the_obliterator</b> — IONISED_AIR + ozone + shockwave every tick.</li>
 *   <li><b>withered_abomination</b> — WITHER_MIASMA + SOUL_SMOKE.</li>
 *   <li><b>flameborn_guard / flameborn_warrior</b> — BLAZE_FUME + SO₂.</li>
 *   <li><b>frostbitten_golem</b> — DRAGON_ICE_CLOUD + ICE_CRYSTALS.</li>
 *   <li><b>hovering_hurricane</b> — atmospheric vortex: centrifugal gas displacement.</li>
 *   <li><b>haunted_guard / haunted_knight</b> — SOUL_ESSENCE aura.</li>
 *   <li><b>skeletosaurus</b> — DUST + GRAVEL_DUST footstep emissions.</li>
 *   <li><b>overgrown_colossus</b> — ORGANIC_AEROSOL + spores from plant matter.</li>
 *   <li><b>warped_fungussus</b> — WARPED_SPORES + WITHER_MIASMA.</li>
 *   <li><b>annihilation_pursuer</b> — ENDER_PARTICULATE + O₂ drain.</li>
 *   <li><b>lava_eater</b> — BLAZE_FUME + SO₂ + WATER_VAPOR (absorbs lava, emits steam).</li>
 *   <li><b>Projectiles</b>: flame_rocket → BLAZE_FUME + shockwave;
 *       annihilation_beam/geyser → ENDER_PARTICULATE + vacuum;
 *       energy_beam/laser → IONISED_AIR + shockwave;
 *       poison_shockwave → ORGANIC_AEROSOL + H₂S burst.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegendaryMonstersCompat {

    public static final String MODID = "legendary_monsters";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private LegendaryMonstersCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Legendary Monsters detected — mob atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("legendary_monsters:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "legendary_monsters:the_obliterator" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 18f, 4);
                gasRadius(level, pos, GasRegistry.OZONE,        8f, 3);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 22f, 4);
                drainRadius(level, pos, GasRegistry.OXYGEN, 10f, 3);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "legendary_monsters:withered_abomination" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 14f, 3);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,     9f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 16f, 3);
            }
            case "legendary_monsters:flameborn_guard",
                 "legendary_monsters:flameborn_warrior" -> {
                gas(level, pos, GasRegistry.BLAZE_FUME,     10f);
                gas(level, pos, GasRegistry.SULFUR_DIOXIDE,  5f);
                drain(level, pos, GasRegistry.OXYGEN,        6f);
            }
            case "legendary_monsters:frostbitten_golem" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 16f, 3);
                partRadius(level, pos, ParticulateType.ICE_CRYSTALS, 20f, 3);
            }
            case "legendary_monsters:hovering_hurricane" -> {
                // Atmospheric vortex — centrifugal evacuation at core,
                // pressure build-up at perimeter
                var comp = GridAtmosphereCompat.getComposition(level, pos);
                float n2 = comp.get(GasRegistry.NITROGEN);
                float o2 = comp.get(GasRegistry.OXYGEN);
                comp.add(GasRegistry.NITROGEN, -n2 * 0.25f);
                comp.add(GasRegistry.OXYGEN,   -o2 * 0.25f);
                comp.add(GasRegistry.IONISED_AIR, (n2 + o2) * 0.10f);
                GridAtmosphereCompat.setComposition(level, pos, comp);
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 12f, 4);
                gasRadius(level, pos, GasRegistry.OZONE,        5f, 3);
            }
            case "legendary_monsters:haunted_guard",
                 "legendary_monsters:haunted_knight" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,   8f);
                part(level, pos, ParticulateType.SOUL_WISPS, 10f);
            }
            case "legendary_monsters:skeletosaurus" -> {
                part(level, pos, ParticulateType.DUST,        20f);
                part(level, pos, ParticulateType.GRAVEL_DUST, 14f);
                ShockwaveHandler.spawn(level, pos, 2f);
            }
            case "legendary_monsters:overgrown_colossus" -> {
                partRadius(level, pos, ParticulateType.BROWN_MUSHROOM_SPORES, 8f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 12f, 3);
                partRadius(level, pos, ParticulateType.SPORE_CLUSTER,    8f, 2);
            }
            case "legendary_monsters:warped_fungussus" -> {
                part(level, pos, ParticulateType.WARPED_SPORES,  10f);
                gas(level, pos, GasRegistry.WITHER_MIASMA,   6f);
                part(level, pos, ParticulateType.SPORE_CLUSTER, 8f);
            }
            case "legendary_monsters:annihilation_pursuer" -> {
                gas(level, pos, GasRegistry.ENDER_PARTICULATE, 10f);
                drain(level, pos, GasRegistry.OXYGEN, 8f);
            }
            case "legendary_monsters:lava_eater" -> {
                gas(level, pos, GasRegistry.BLAZE_FUME,     12f);
                gas(level, pos, GasRegistry.SULFUR_DIOXIDE,  6f);
                gas(level, pos, GasRegistry.WATER_VAPOR,     8f);
                drain(level, pos, GasRegistry.OXYGEN,        8f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("legendary_monsters:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        switch (type) {
            case "legendary_monsters:the_obliterator" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 60f, 7);
                gasRadius(level, pos, GasRegistry.OZONE,       25f, 5);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 80f, 6);
                ShockwaveHandler.spawn(level, pos, 14f);
            }
            case "legendary_monsters:withered_abomination" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 50f, 5);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,    30f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 60f, 5);
                ShockwaveHandler.spawn(level, pos, 8f);
            }
            case "legendary_monsters:overgrown_colossus" -> {
                partRadius(level, pos, ParticulateType.BROWN_MUSHROOM_SPORES, 40f, 6);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 60f, 5);
                partRadius(level, pos, ParticulateType.SPORE_CLUSTER,   50f, 5);
                ShockwaveHandler.spawn(level, pos, 10f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("legendary_monsters:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "legendary_monsters:flame_rocket" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     30f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 12f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  60f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            case "legendary_monsters:annihilation_beam",
                 "legendary_monsters:annihilation_geyser" -> {
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 25f, 3);
                drainRadius(level, pos, GasRegistry.OXYGEN,          20f, 2);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "legendary_monsters:energy_beam",
                 "legendary_monsters:energy_laser",
                 "legendary_monsters:lightning_beam" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 20f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,        8f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 25f, 3);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "legendary_monsters:poison_shockwave" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 18f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 30f, 3);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "legendary_monsters:chorus_breath",
                 "legendary_monsters:chorus_bomb" -> {
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 18f, 3);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
        }
    }
}
