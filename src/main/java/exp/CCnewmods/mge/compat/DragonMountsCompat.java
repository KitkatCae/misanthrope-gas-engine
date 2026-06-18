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
 * Dragon Mounts Legacy compat (modid: dragonmounts).
 *
 * The single entity type is {@code dragonmounts:dragon} — the breed is determined
 * at runtime via the dragon's data/NBT tag. We detect the breed from the entity type
 * name suffix, which DML appends to the display name. Since we can't hard-depend on
 * the DML API (compileOnly), we use the entity's custom name or read breed via
 * reflection from the DragonStateHandler capability — but the safest approach is to
 * hook on the breath projectile entity IDs which ARE breed-specific.
 *
 * Breath IDs confirmed from DMLRegistry.class:
 * {@code dragonmounts:black_fire_breath, dragonmounts:blue_fire_breath,
 * dragonmounts:ice_breath, dragonmounts:storm_breath, dragonmounts:sculk_breath}.
 * The dragon entity itself is {@code dragonmounts:dragon} for all breeds.
 *
 * <ul>
 *   <li><b>dragon (generic tick)</b> — small IONISED_AIR aura (all dragons disturb
 *       local atmosphere), plus EnderDragon baseline CO₂ is already handled by
 *       {@link exp.CCnewmods.mge.event.BreathWeaponHandler}.</li>
 *   <li><b>black_fire_breath</b> — combustion: CO₂ + CO + SO₂ + O₂ drain (fire variant).</li>
 *   <li><b>blue_fire_breath</b> — hotter, more energetic: adds IONISED_AIR + higher SO₂.</li>
 *   <li><b>ice_breath</b> — DRAGON_ICE_CLOUD + ICE_CRYSTAL_SHARDS.</li>
 *   <li><b>storm_breath</b> — IONISED_AIR + OZONE + NITRIC_OXIDE + shockwave.</li>
 *   <li><b>sculk_breath</b> — VOID_BREATH + ENDER_PARTICULATE + SHULKER_ACID_MIST.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DragonMountsCompat {

    public static final String MODID = "dragonmounts";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 40; // dragons are big — 2 s interval
    private static int tick = 0;

    private DragonMountsCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Dragon Mounts Legacy detected — dragon breath emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.equals("dragonmounts:dragon")) return;

        BlockPos pos = entity.blockPosition();
        // All DML dragons disturb local atmosphere — breed-specific
        // chemistry comes from their breath projectile impacts below
        gasRadius(level, pos, GasRegistry.IONISED_AIR, 5f, 2);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!event.getEntity().getType().toString().equals("dragonmounts:dragon")) return;

        BlockPos pos = event.getEntity().blockPosition();
        // Generic dragon death — breed-specific products depend on what breath they used;
        // use a neutral atmospheric disruption burst
        gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 40f, 5);
        gasRadius(level, pos, GasRegistry.IONISED_AIR,    20f, 4);
        partRadius(level, pos, ParticulateType.ASH_CLOUD,  60f, 5);
        ShockwaveHandler.spawn(level, pos, 10f);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("dragonmounts:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "dragonmounts:black_fire_breath" -> {
                // Standard fire combustion — reuse the IaF fire breath helper logic inline
                var comp = exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat.getComposition(level, pos);
                float o2 = comp.get(GasRegistry.OXYGEN);
                float consumed = Math.min(o2, 25f);
                comp.add(GasRegistry.OXYGEN,          -consumed);
                comp.add(GasRegistry.CARBON_DIOXIDE,   consumed * 0.65f);
                comp.add(GasRegistry.CARBON_MONOXIDE,  consumed * 0.15f);
                comp.add(GasRegistry.SULFUR_DIOXIDE,   6f);
                exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat.setComposition(level, pos, comp);
                partRadius(level, pos, ParticulateType.ASH_CLOUD, 30f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "dragonmounts:blue_fire_breath" -> {
                // Hotter — more energetic combustion, ionisation
                var comp = exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat.getComposition(level, pos);
                float o2 = comp.get(GasRegistry.OXYGEN);
                float consumed = Math.min(o2, 30f);
                comp.add(GasRegistry.OXYGEN,          -consumed);
                comp.add(GasRegistry.CARBON_DIOXIDE,   consumed * 0.55f);
                comp.add(GasRegistry.CARBON_MONOXIDE,  consumed * 0.20f);
                comp.add(GasRegistry.SULFUR_DIOXIDE,   12f);
                comp.add(GasRegistry.IONISED_AIR,       8f);
                exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat.setComposition(level, pos, comp);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,         35f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 15f, 2);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "dragonmounts:ice_breath" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD,  35f, 3);
                partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 45f, 3);
                partRadius(level, pos, ParticulateType.ICE_CRYSTALS,       25f, 2);
            }
            case "dragonmounts:storm_breath" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR,   30f, 3);
                gasRadius(level, pos, GasRegistry.OZONE,          12f, 2);
                gasRadius(level, pos, GasRegistry.NITRIC_OXIDE,    6f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 35f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
            case "dragonmounts:sculk_breath" -> {
                gasRadius(level, pos, GasRegistry.VOID_BREATH,       20f, 3);
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 15f, 2);
                gasRadius(level, pos, GasRegistry.SHULKER_ACID_MIST, 10f, 2);
                drainRadius(level, pos, GasRegistry.OXYGEN,  15f, 2);
                drainRadius(level, pos, GasRegistry.NITROGEN, 8f, 2);
            }
        }
    }
}
