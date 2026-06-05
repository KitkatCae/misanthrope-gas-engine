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
 * Rediscovered compat (modid: rediscovered).
 *
 * Entity IDs confirmed from RediscoveredEntityTypes.class and loot tables.
 *
 * <ul>
 *   <li><b>red_dragon</b> — fire breath using IceAndFire helper at 0.9× scale
 *       (smaller than the full IaF fire dragon). red_dragon_offspring at 0.3×.</li>
 *   <li><b>zombie_pigman / melee_pigman / ranged_pigman</b> — H₂S + BLAZE_FUME
 *       (nether undead with fire).</li>
 *   <li><b>black_steve / beast_boy / rana</b> — beta-era spectral mobs:
 *       faint SOUL_ESSENCE aura (they're echoes of deleted content).</li>
 *   <li><b>scarecrow</b> — ORGANIC_AEROSOL + DUST (dry straw off-gassing).</li>
 *   <li><b>purple_arrow</b> — projectile: SOUL_ESSENCE burst.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RediscoveredCompat {

    public static final String MODID = "rediscovered";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private RediscoveredCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Rediscovered detected — red dragon and beta mob emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("rediscovered:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "rediscovered:red_dragon" ->
                // Red dragon — fire breath, slightly smaller than IaF fire dragon
                IceAndFireCompat.emitFireDragonBreath(level, pos, 0.9f);
            case "rediscovered:red_dragon_offspring" ->
                IceAndFireCompat.emitFireDragonBreath(level, pos, 0.3f);
            case "rediscovered:zombie_pigman",
                 "rediscovered:melee_pigman",
                 "rediscovered:ranged_pigman" -> {
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 5f);
                gas(level, pos, GasRegistry.BLAZE_FUME,       4f);
            }
            case "rediscovered:black_steve",
                 "rediscovered:beast_boy",
                 "rediscovered:rana" -> {
                // Beta ghosts — echoes of deleted mobs, barely there
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 4f);
                part(level, pos, ParticulateType.SOUL_WISPS, 5f);
            }
            case "rediscovered:scarecrow" -> {
                part(level, pos, ParticulateType.DUST,          8f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 4f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("rediscovered:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        if (type.equals("rediscovered:red_dragon")) {
            gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 40f, 5);
            gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 20f, 4);
            partRadius(level, pos, ParticulateType.ASH_CLOUD,  80f, 5);
            ShockwaveHandler.spawn(level, pos, 9f);
            ShockwaveDataPacket.sendToNear(level, vec, 9f, 72f);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("rediscovered:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        if (type.equals("rediscovered:purple_arrow")) {
            gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   12f, 2);
            partRadius(level, pos, ParticulateType.SOUL_WISPS, 15f, 2);
            ShockwaveHandler.spawn(level, pos, 2f);
            ShockwaveDataPacket.sendToNear(level, vec, 2f, 20f);
        }
    }
}
