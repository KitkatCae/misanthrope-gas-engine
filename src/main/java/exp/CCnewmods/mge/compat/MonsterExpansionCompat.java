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
 * Monster Expansion compat (modid: monsterexpansion).
 *
 * Entity IDs confirmed from ModEntities.class:
 * <ul>
 *   <li><b>skrythe</b> — spectral/ender hunter: SOUL_ESSENCE + ENDER_PARTICULATE aura.</li>
 *   <li><b>leivekilth</b> — necrotic decay beast: H₂S + ORGANIC_AEROSOL heavy emission.
 *       Death: large toxic burst.</li>
 *   <li><b>rakoth</b> — fire demon lord: BLAZE_FUME + SO₂ + O₂ drain. Death: inferno burst.</li>
 *   <li><b>rhyza</b> — wither-touched abomination: WITHER_MIASMA + SOUL_SMOKE aura.</li>
 *   <li><b>distortion_orb / volatile_glob</b> — projectile impacts: ender/toxic bursts.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MonsterExpansionCompat {

    public static final String MODID = "monsterexpansion";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private MonsterExpansionCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Monster Expansion detected — boss atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("monsterexpansion:")) return;

        BlockPos pos = entity.blockPosition();

        // _part and _tail variants share the head entity's position via multipart,
        // but we only emit from the primary entity type to avoid multiplicative spam.
        switch (type) {
            case "monsterexpansion:skrythe" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,      10f);
                gas(level, pos, GasRegistry.ENDER_PARTICULATE,  8f);
                part(level, pos, ParticulateType.SOUL_WISPS,   12f);
                drain(level, pos, GasRegistry.OXYGEN, 6f);
            }
            case "monsterexpansion:leivekilth" -> {
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,   12f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 18f);
            }
            case "monsterexpansion:rakoth" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     18f, 3);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 10f, 2);
                drainRadius(level, pos, GasRegistry.OXYGEN, 15f, 2);
                partRadius(level, pos, ParticulateType.ASH_CLOUD, 25f, 2);
            }
            case "monsterexpansion:rhyza" -> {
                gas(level, pos, GasRegistry.WITHER_MIASMA, 10f);
                gas(level, pos, GasRegistry.SOUL_SMOKE,     7f);
                part(level, pos, ParticulateType.SOUL_WISPS, 12f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("monsterexpansion:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        switch (type) {
            case "monsterexpansion:leivekilth" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 60f, 6);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 80f, 6);
                ShockwaveHandler.spawn(level, pos, 8f);
            }
            case "monsterexpansion:rakoth" -> {
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,     80f, 7);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 40f, 5);
                partRadius(level, pos, ParticulateType.ASH_CLOUD, 120f, 6);
                ShockwaveHandler.spawn(level, pos, 12f);
            }
            case "monsterexpansion:skrythe" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,      50f, 5);
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 30f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS,   60f, 5);
                ShockwaveHandler.spawn(level, pos, 7f);
            }
            case "monsterexpansion:rhyza" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 50f, 5);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,    30f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 60f, 5);
                ShockwaveHandler.spawn(level, pos, 8f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("monsterexpansion:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "monsterexpansion:distortion_orb" -> {
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 25f, 3);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,      15f, 2);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "monsterexpansion:volatile_glob" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 20f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 35f, 3);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "monsterexpansion:supercooled_orb" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 30f, 3);
                partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 40f, 3);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
        }
    }
}
