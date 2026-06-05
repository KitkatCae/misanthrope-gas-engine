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
 * Intense Horror compat (modid: intensehorror).
 *
 * All entity IDs confirmed from IntensehorrorModEntities.class.
 *
 * <ul>
 *   <li><b>demon</b> — SOUL_ESSENCE + WITHER_MIASMA aura.</li>
 *   <li><b>reaper</b> — SOUL_ESSENCE + WITHER_MIASMA + O₂ drain.</li>
 *   <li><b>swamp_thing</b> — H₂S + ORGANIC_AEROSOL (bog creature).</li>
 *   <li><b>creature_from_the_deep</b> — WATER_VAPOR + H₂S (deep sea decay).</li>
 *   <li><b>pumpking</b> — boss: BLAZE_FUME + SO₂ + SOUL_ESSENCE.
 *       Death: large burst + shockwave.</li>
 *   <li><b>mourning_wood</b> — ORGANIC_AEROSOL + BROWN_MUSHROOM_SPORES (rotting wood).</li>
 *   <li><b>werewolf</b> — ORGANIC_AEROSOL (animal musk + blood).</li>
 *   <li><b>vampire / vampire_bat</b> — SOUL_ESSENCE + faint H₂S (undead predator).</li>
 *   <li><b>mummy</b> — DUST + faint H₂S (ancient decay).</li>
 *   <li><b>frankenstein</b> — IONISED_AIR (lightning-animated body).</li>
 *   <li><b>headless_horseman</b> — BLAZE_FUME + SOUL_ESSENCE.</li>
 *   <li><b>witch</b> — ORGANIC_AEROSOL + faint SOUL_ESSENCE (potion fumes).</li>
 *   <li><b>Projectiles</b>: projectile_demon_wave → SOUL_ESSENCE + shockwave;
 *       projectile_ectobomb_projectile → SOUL_ESSENCE burst;
 *       projectile_flaming_spike → BLAZE_FUME + shockwave;
 *       projectile_pumpkin_projectile → BLAZE_FUME + SO₂.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IntenseHorrorCompat {

    public static final String MODID = "intensehorror";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private IntenseHorrorCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Intense Horror detected — horror atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("intensehorror:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "intensehorror:demon" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,   10f);
                gas(level, pos, GasRegistry.WITHER_MIASMA,   7f);
                part(level, pos, ParticulateType.SOUL_WISPS, 12f);
            }
            case "intensehorror:reaper" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,   14f);
                gas(level, pos, GasRegistry.WITHER_MIASMA,  10f);
                part(level, pos, ParticulateType.SOUL_WISPS, 16f);
                drain(level, pos, GasRegistry.OXYGEN, 10f);
            }
            case "intensehorror:swamp_thing" -> {
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 8f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 10f);
            }
            case "intensehorror:creature_from_the_deep" -> {
                gas(level, pos, GasRegistry.WATER_VAPOR,    10f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 6f);
            }
            case "intensehorror:pumpking",
                 "intensehorror:pumpking_spawn" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,    14f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,  7f, 2);
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 8f);
                drainRadius(level, pos, GasRegistry.OXYGEN, 10f, 2);
            }
            case "intensehorror:mourning_wood" -> {
                part(level, pos, ParticulateType.BROWN_MUSHROOM_SPORES, 6f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL,  8f);
                part(level, pos, ParticulateType.SPORE_CLUSTER,    4f);
            }
            case "intensehorror:werewolf" ->
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
            case "intensehorror:vampire",
                 "intensehorror:vampire_bat" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,    5f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 2f);
            }
            case "intensehorror:mummy" -> {
                part(level, pos, ParticulateType.DUST, 10f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 3f);
            }
            case "intensehorror:frankenstein" -> {
                gas(level, pos, GasRegistry.IONISED_AIR, 8f);
                part(level, pos, ParticulateType.IONISED_PARTICLES, 6f);
            }
            case "intensehorror:headless_horseman" -> {
                gas(level, pos, GasRegistry.BLAZE_FUME,   8f);
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 6f);
                part(level, pos, ParticulateType.SOUL_WISPS, 8f);
            }
            case "intensehorror:witch" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 3f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("intensehorror:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        switch (type) {
            case "intensehorror:pumpking" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     60f, 6);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 30f, 4);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   25f, 4);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  80f, 5);
                ShockwaveHandler.spawn(level, pos, 10f);
                ShockwaveDataPacket.sendToNear(level, vec, 10f, 80f);
            }
            case "intensehorror:demon",
                 "intensehorror:reaper" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  35f, 4);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 22f, 3);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 45f, 4);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("intensehorror:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        switch (type) {
            case "intensehorror:projectile_demon_wave" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   20f, 3);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,  12f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 25f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
            case "intensehorror:projectile_ectobomb_projectile" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   15f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 20f, 2);
            }
            case "intensehorror:projectile_flaming_spike" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     20f, 2);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,  8f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  35f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
            case "intensehorror:projectile_pumpkin_projectile" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     15f, 2);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,  6f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  25f, 2);
            }
        }
    }
}
