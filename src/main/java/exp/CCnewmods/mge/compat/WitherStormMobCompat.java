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
 * Wither Storm Mod mob atmosphere emissions (modid: witherstormmod).
 *
 * <p>Note: {@link WitherStormCompat} already handles the gas registry side
 * (re-registering WITHER_MIASMA with the mod's custom wither sickness effect).
 * This class handles all entity tick emissions, death bursts, and projectile impacts.</p>
 *
 * Entity IDs confirmed from WitherStormModEntityTypes.class:
 * <ul>
 *   <li><b>wither_storm</b> — main body: massive WITHER_MIASMA + SOUL_SMOKE + O₂ drain
 *       at wide radius every tick. Death: apocalyptic burst.</li>
 *   <li><b>wither_storm_head / wither_storm_segment</b> — scaled sub-body emissions.</li>
 *   <li><b>withered_symbiont</b> — WITHER_MIASMA + H₂S necrotic parasite.</li>
 *   <li><b>sickened_*</b> variants — faint WITHER_MIASMA. Small puff on death.</li>
 *   <li><b>tainted_slime</b> — WITHER_MIASMA pulse.</li>
 *   <li><b>tentacle_spike</b> — impact: WITHER_MIASMA + shockwave.</li>
 *   <li><b>formidibomb</b> — detonation: WITHER_MIASMA + SOUL_SMOKE megaburst.</li>
 *   <li><b>flaming_wither_skull / blue_flaming_wither_skull</b> — impact: SOUL_SMOKE +
 *       BLAZE_FUME + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WitherStormMobCompat {

    public static final String MODID = "witherstormmod";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL         = 20;
    private static final int SICKENED_TICK_INTERVAL = 60;
    private static int tick = 0;

    private static final Set<String> SICKENED = Set.of(
            "witherstormmod:sickened_bee",
            "witherstormmod:sickened_cat",
            "witherstormmod:sickened_chicken",
            "witherstormmod:sickened_cow",
            "witherstormmod:sickened_creeper",
            "witherstormmod:sickened_iron_golem",
            "witherstormmod:sickened_mushroom_cow",
            "witherstormmod:sickened_parrot",
            "witherstormmod:sickened_phantom",
            "witherstormmod:sickened_pig",
            "witherstormmod:sickened_pillager",
            "witherstormmod:sickened_skeleton",
            "witherstormmod:sickened_snow_golem",
            "witherstormmod:sickened_spider",
            "witherstormmod:sickened_villager",
            "witherstormmod:sickened_vindicator",
            "witherstormmod:sickened_wolf",
            "witherstormmod:sickened_zombie"
    );

    private WitherStormMobCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Wither Storm Mod entity emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("witherstormmod:")) return;

        ++tick;
        BlockPos pos = entity.blockPosition();

        if (SICKENED.contains(type)) {
            if (tick % SICKENED_TICK_INTERVAL == 0)
                gas(level, pos, GasRegistry.WITHER_MIASMA, 3f);
            return;
        }

        if (tick % TICK_INTERVAL != 0) return;

        switch (type) {
            case "witherstormmod:wither_storm" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 40f, 10);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,    25f,  8);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  15f,  6);
                drainRadius(level, pos, GasRegistry.OXYGEN,      30f,  8);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 50f, 8);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  30f, 6);
            }
            case "witherstormmod:wither_storm_head" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 20f, 5);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,    12f, 4);
                drainRadius(level, pos, GasRegistry.OXYGEN,      15f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 25f, 4);
            }
            case "witherstormmod:wither_storm_segment" -> {
                gas(level, pos, GasRegistry.WITHER_MIASMA, 10f);
                gas(level, pos, GasRegistry.SOUL_SMOKE,     6f);
                drain(level, pos, GasRegistry.OXYGEN,       8f);
            }
            case "witherstormmod:command_block" -> {
                // The exposed command block core — the storm's active fighting brain.
                // Emits a concentrated wither field even heavier than the main body.
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 50f, 8);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,    30f, 6);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  20f, 5);
                drainRadius(level, pos, GasRegistry.OXYGEN,      40f, 7);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 60f, 7);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  35f, 5);
            }
            case "witherstormmod:withered_symbiont" -> {
                gas(level, pos, GasRegistry.WITHER_MIASMA,    8f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,  5f);
                part(level, pos, ParticulateType.SOUL_WISPS,   6f);
            }
            case "witherstormmod:tainted_slime" ->
                    gas(level, pos, GasRegistry.WITHER_MIASMA, 5f);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("witherstormmod:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        if (SICKENED.contains(type)) {
            gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 8f, 2);
            return;
        }

        switch (type) {
            case "witherstormmod:wither_storm" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 200f, 20);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,    120f, 16);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   80f, 12);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 250f, 18);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  180f, 14);
                ShockwaveHandler.spawn(level, pos, 30f);
                ShockwaveDataPacket.sendToNear(level, vec, 30f, 320f);
            }
            case "witherstormmod:command_block" -> {
                // Core destruction — all the wither energy concentrated in one point
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 150f, 15);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,     90f, 12);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   60f,  9);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 180f, 14);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  120f, 10);
                ShockwaveHandler.spawn(level, pos, 22f);
                ShockwaveDataPacket.sendToNear(level, vec, 22f, 240f);
            }
            case "witherstormmod:withered_symbiont" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,   20f, 3);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 10f, 2);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("witherstormmod:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        switch (type) {
            case "witherstormmod:block_cluster" -> {
                // Orbiting debris cloud — pure physical impact on collision
                partRadius(level, pos, ParticulateType.DUST,        50f, 4);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 35f, 3);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,    8f, 2);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
            case "witherstormmod:tentacle_spike" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,  15f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 4f);
                ShockwaveDataPacket.sendToNear(level, vec, 4f, 40f);
            }
            case "witherstormmod:formidibomb" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,  80f, 8);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,     50f, 6);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 100f, 7);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,   60f, 5);
                ShockwaveHandler.spawn(level, pos, 18f);
                ShockwaveDataPacket.sendToNear(level, vec, 18f, 180f);
            }
            case "witherstormmod:flaming_wither_skull" -> {
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,   20f, 3);
                gasRadius(level, pos, GasRegistry.BLAZE_FUME,   15f, 2);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 12f, 2);
                ShockwaveHandler.spawn(level, pos, 4f);
                ShockwaveDataPacket.sendToNear(level, vec, 4f, 40f);
            }
            case "witherstormmod:blue_flaming_wither_skull" -> {
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,   25f, 3);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 20f, 3);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  10f, 2);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
        }
    }
}
