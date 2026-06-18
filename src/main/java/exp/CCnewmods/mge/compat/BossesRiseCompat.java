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
 * Block Factory's Bosses (Bosses Rise) compat (modid: block_factorys_bosses).
 *
 * Entity IDs confirmed from loot table paths:
 * <ul>
 *   <li><b>infernal_dragon</b> — fire breath reusing IceAndFireCompat helper at 1.2× scale.</li>
 *   <li><b>sandworm</b> — every tick: DUST + GRAVEL_DUST + COAL_DUST + shockwave from
 *       underground movement. Death: massive dust eruption.</li>
 *   <li><b>soul_skeleton / soul_knight_wither_skeleton</b> — SOUL_ESSENCE + WITHER_MIASMA.</li>
 *   <li><b>underworld_knight</b> — BLAZE_FUME + SO₂.</li>
 *   <li><b>yeti</b> — DRAGON_ICE_CLOUD + ICE_CRYSTALS.</li>
 *   <li><b>flaming_skeleton_guard_fireball</b> — fire projectile impact: BLAZE_FUME + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossesRiseCompat {

    public static final String MODID = "block_factorys_bosses";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL      = 20;
    private static final int SANDWORM_DUST_TICK  = 10; // faster — it moves constantly underground
    private static int tick = 0;

    private BossesRiseCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Block Factory's Bosses detected — boss atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("block_factorys_bosses:")) return;

        ++tick;
        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "block_factorys_bosses:infernal_dragon" -> {
                if (tick % TICK_INTERVAL != 0) return;
                // Reuse IceAndFire fire breath at 1.2× scale
                IceAndFireCompat.emitFireDragonBreath(level, pos, 1.2f);
            }
            case "block_factorys_bosses:sandworm" -> {
                if (tick % SANDWORM_DUST_TICK != 0) return;
                // Massive underground movement — constant dust displacement
                gasRadius(level, pos, GasRegistry.WATER_VAPOR, 5f, 2);
                partRadius(level, pos, ParticulateType.DUST,        40f, 5);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 30f, 4);
                partRadius(level, pos, ParticulateType.COAL_DUST,   10f, 3);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "block_factorys_bosses:soul_skeleton",
                 "block_factorys_bosses:soul_knight_wither_skeleton" -> {
                if (tick % TICK_INTERVAL != 0) return;
                gas(level, pos, GasRegistry.SOUL_ESSENCE,   8f);
                gas(level, pos, GasRegistry.WITHER_MIASMA,  6f);
                part(level, pos, ParticulateType.SOUL_WISPS, 10f);
            }
            case "block_factorys_bosses:underworld_knight" -> {
                if (tick % TICK_INTERVAL != 0) return;
                gas(level, pos, GasRegistry.BLAZE_FUME,     10f);
                gas(level, pos, GasRegistry.SULFUR_DIOXIDE,  6f);
                drain(level, pos, GasRegistry.OXYGEN, 6f);
            }
            case "block_factorys_bosses:yeti" -> {
                if (tick % TICK_INTERVAL != 0) return;
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 18f, 3);
                partRadius(level, pos, ParticulateType.ICE_CRYSTALS, 22f, 3);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("block_factorys_bosses:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        switch (type) {
            case "block_factorys_bosses:sandworm" -> {
                // Death eruption — bursts out of the ground
                gasRadius(level, pos, GasRegistry.WATER_VAPOR, 30f, 6);
                partRadius(level, pos, ParticulateType.DUST,        150f, 8);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 100f, 6);
                partRadius(level, pos, ParticulateType.COAL_DUST,    40f, 5);
                ShockwaveHandler.spawn(level, pos, 16f);
            }
            case "block_factorys_bosses:infernal_dragon" -> {
                gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 50f, 5);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 25f, 4);
                partRadius(level, pos, ParticulateType.ASH_CLOUD, 100f, 5);
                ShockwaveHandler.spawn(level, pos, 10f);
            }
            case "block_factorys_bosses:yeti" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 60f, 6);
                partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 80f, 5);
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
        if (!type.startsWith("block_factorys_bosses:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        if (type.equals("block_factorys_bosses:flaming_skeleton_guard_fireball")) {
            gasRadius(level, pos, GasRegistry.BLAZE_FUME,     25f, 3);
            gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 10f, 2);
            partRadius(level, pos, ParticulateType.ASH_CLOUD,  50f, 3);
            ShockwaveHandler.spawn(level, pos, 4f);
        }
    }
}
