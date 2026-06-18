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

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * More Critters compat (modid: more_critters).
 *
 * All entity IDs confirmed from loot tables and entity class names.
 * Projectile IDs derived from class names: ThunderballProjectile → thunderball_projectile,
 * CannonBallProjectile → cannon_ball_projectile, ShriekbombProjectile → shriekbomb_projectile.
 *
 * <ul>
 *   <li><b>amalgam</b> — the ultimate decay abomination: full cadaverine + putrescine +
 *       indole + skatole + H₂S + OPHIOCORDYCEPS_HUMANUS particulate profile.
 *       Death: massive toxic burst.</li>
 *   <li><b>shriekbat / tester_shriek</b> — sonic emitters: IONISED_AIR + ozone
 *       from ultrasonic pressure waves.</li>
 *   <li><b>bomb_jelly_large / medium / small</b> — H₂ off-gas like hydrogen jellyfish.
 *       Death: H₂ burst picked up by GasDetonationHandler.</li>
 *   <li><b>fungal_zombie</b> — CADAVERINE + PUTRESCINE + OPHIOCORDYCEPS_HUMANUS spores
 *       (cordyceps-infected undead).</li>
 *   <li><b>frightshroom / mightshroom</b> — BROWN_MUSHROOM_SPORES + SPORE_CLUSTER.</li>
 *   <li><b>corpse_captain / mate / lookout / quartermaster / tank / parrot</b> —
 *       CADAVERINE + H₂S (undead pirates).</li>
 *   <li><b>warptrap</b> — ENDER_PARTICULATE (ender-anchored trap).</li>
 *   <li><b>nervoid</b> — IONISED_AIR (bioelectric creature).</li>
 *   <li><b>Projectiles</b>: thunderball_projectile → IONISED_AIR + shockwave;
 *       shriekbomb_projectile → IONISED_AIR concussive burst;
 *       cannon_ball_projectile → DUST + GRAVEL_DUST + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MoreCrittersCompat {

    public static final String MODID = "more_critters";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL      = 20;
    private static final int JELLY_TICK_INTERVAL = 20;
    private static int tick = 0;

    private MoreCrittersCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] More Critters detected — critter atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("more_critters:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "more_critters:amalgam" -> {
                // Maximum biological off-gassing — every decay compound at once
                gas(level, pos, GasRegistry.CADAVERINE,       10f);
                gas(level, pos, GasRegistry.PUTRESCINE,       10f);
                gas(level, pos, GasRegistry.INDOLE,            6f);
                gas(level, pos, GasRegistry.SKATOLE,           5f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,  8f);
                part(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 12f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL,        10f);
            }
            case "more_critters:shriekbat",
                 "more_critters:tester_shriek" -> {
                // Sonic emitters — ionise surrounding air via ultrasonic pressure
                gas(level, pos, GasRegistry.IONISED_AIR, 10f);
                gas(level, pos, GasRegistry.OZONE,         4f);
                part(level, pos, ParticulateType.IONISED_PARTICLES, 8f);
            }
            case "more_critters:bomb_jelly_large" -> {
                // H₂ off-gas upward — walking bomb like hydrogen jellyfish
                for (int dy = 1; dy <= 3; dy++) {
                    gas(level, pos.above(dy), GasRegistry.HYDROGEN, 10f / dy);
                }
            }
            case "more_critters:bomb_jelly_medium" -> {
                for (int dy = 1; dy <= 3; dy++) {
                    gas(level, pos.above(dy), GasRegistry.HYDROGEN, 6f / dy);
                }
            }
            case "more_critters:bomb_jelly_small" -> {
                gas(level, pos.above(1), GasRegistry.HYDROGEN, 4f);
            }
            case "more_critters:fungal_zombie" -> {
                gas(level, pos, GasRegistry.CADAVERINE,  5f);
                gas(level, pos, GasRegistry.PUTRESCINE,  5f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 3f);
                part(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 10f);
            }
            case "more_critters:frightshroom",
                 "more_critters:mightshroom" -> {
                part(level, pos, ParticulateType.BROWN_MUSHROOM_SPORES, 12f);
                part(level, pos, ParticulateType.SPORE_CLUSTER,          6f);
            }
            case "more_critters:corpse_captain",
                 "more_critters:corpse_mate",
                 "more_critters:corpse_lookout",
                 "more_critters:corpse_quartermaster",
                 "more_critters:corpse_tank",
                 "more_critters:corpse_parrot" -> {
                gas(level, pos, GasRegistry.CADAVERINE,       5f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 3f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 4f);
            }
            case "more_critters:warptrap" ->
                gas(level, pos, GasRegistry.ENDER_PARTICULATE, 8f);
            case "more_critters:nervoid" -> {
                gas(level, pos, GasRegistry.IONISED_AIR, 8f);
                part(level, pos, ParticulateType.IONISED_PARTICLES, 6f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("more_critters:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        switch (type) {
            case "more_critters:amalgam" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,       40f, 5);
                gasRadius(level, pos, GasRegistry.PUTRESCINE,       40f, 5);
                gasRadius(level, pos, GasRegistry.INDOLE,           25f, 4);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 35f, 5);
                partRadius(level, pos, ParticulateType.OPHIOCORDYCEPS_HUMANUS, 60f, 5);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL,        50f, 5);
                ShockwaveHandler.spawn(level, pos, 7f);
            }
            case "more_critters:bomb_jelly_large" -> {
                // H₂ death burst — same pattern as BetterNether hydrogen jellyfish
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = 0; dy <= 4; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                            float falloff = Math.max(0f, 1f - dist / 4f);
                            gas(level, pos.offset(dx, dy, dz), GasRegistry.HYDROGEN, 50f * falloff);
                        }
                    }
                }
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "more_critters:bomb_jelly_medium" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN, 30f, 2);
                ShockwaveHandler.spawn(level, pos, 2f);
            }
            case "more_critters:bomb_jelly_small" ->
                gasRadius(level, pos, GasRegistry.HYDROGEN, 12f, 1);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("more_critters:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "more_critters:thunderball_projectile" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR,  25f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,         10f, 2);
                gasRadius(level, pos, GasRegistry.NITRIC_OXIDE,   5f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 30f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            case "more_critters:shriekbomb_projectile" -> {
                // Sonic concussion — rapid overpressure then vacuum
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 18f, 3);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 6f);
            }
            case "more_critters:cannon_ball_projectile",
                 "more_critters:cold_cannon_ball_projectile" -> {
                partRadius(level, pos, ParticulateType.DUST,        30f, 3);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
        }
    }
}
