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
 * Mutant Monsters compat (modid: mutantmonsters).
 *
 * Entity IDs confirmed from loot tables and entity class names.
 * Projectile IDs derived from class names: MutantArrow → mutant_arrow,
 * SkullSpirit → skull_spirit, EndersoulFragment → endersoul_fragment,
 * ThrowableBlock → throwable_block.
 *
 * <ul>
 *   <li><b>mutant_creeper</b> — passive: CO₂ + sulfurous off-gassing. Death:
 *       massive vacuum-compression explosion — O₂ drain + CO₂/CO burst + shockwave.</li>
 *   <li><b>mutant_enderman</b> — VOID_BREATH + ENDER_PARTICULATE aura.
 *       Death: large void/ender burst.</li>
 *   <li><b>mutant_zombie</b> — CADAVERINE + PUTRESCINE + INDOLE + SKATOLE aura
 *       (the definitive decay gas profile — this thing reeks).</li>
 *   <li><b>mutant_skeleton</b> — DUST + SOUL_ESSENCE + faint H₂S.</li>
 *   <li><b>mutant_snow_golem</b> — DRAGON_ICE_CLOUD + ICE_CRYSTALS radius.</li>
 *   <li><b>creeper_minion</b> — faint CO₂ + sulfur (tiny creeper).</li>
 *   <li><b>endersoul_clone</b> — VOID_BREATH + ENDER_PARTICULATE (soul copy).</li>
 *   <li><b>Projectiles</b>: skull_spirit → SOUL_ESSENCE burst + shockwave;
 *       endersoul_fragment → VOID_BREATH + ender burst;
 *       throwable_block → DUST + GRAVEL_DUST + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MutantMonstersCompat {

    public static final String MODID = "mutantmonsters";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private MutantMonstersCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Mutant Monsters detected — mutant atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("mutantmonsters:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "mutantmonsters:mutant_creeper" -> {
                // Explosive chemistry leaking continuously
                gas(level, pos, GasRegistry.CARBON_DIOXIDE, 10f);
                gas(level, pos, GasRegistry.SULFUR_DIOXIDE,  4f);
                gas(level, pos, GasRegistry.NITROGEN_DIOXIDE, 2f);
                drain(level, pos, GasRegistry.OXYGEN, 6f);
            }
            case "mutantmonsters:mutant_enderman" -> {
                gas(level, pos, GasRegistry.VOID_BREATH,        12f);
                gas(level, pos, GasRegistry.ENDER_PARTICULATE,  10f);
                drain(level, pos, GasRegistry.OXYGEN, 8f);
                drain(level, pos, GasRegistry.NITROGEN, 4f);
            }
            case "mutantmonsters:mutant_zombie" -> {
                // Full decay gas profile — maximum biological off-gassing
                gas(level, pos, GasRegistry.CADAVERINE,  8f);
                gas(level, pos, GasRegistry.PUTRESCINE,  8f);
                gas(level, pos, GasRegistry.INDOLE,      5f);
                gas(level, pos, GasRegistry.SKATOLE,     4f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 6f);
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 12f);
            }
            case "mutantmonsters:mutant_skeleton" -> {
                part(level, pos, ParticulateType.DUST, 12f);
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 5f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 2f);
            }
            case "mutantmonsters:mutant_snow_golem" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 20f, 3);
                partRadius(level, pos, ParticulateType.ICE_CRYSTALS, 25f, 3);
            }
            case "mutantmonsters:creeper_minion" -> {
                gas(level, pos, GasRegistry.CARBON_DIOXIDE, 3f);
                gas(level, pos, GasRegistry.SULFUR_DIOXIDE, 1f);
            }
            case "mutantmonsters:endersoul_clone" -> {
                gas(level, pos, GasRegistry.VOID_BREATH,       8f);
                gas(level, pos, GasRegistry.ENDER_PARTICULATE, 6f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("mutantmonsters:")) return;

        BlockPos pos = event.getEntity().blockPosition();

        switch (type) {
            case "mutantmonsters:mutant_creeper" -> {
                // Vacuum-compression: strips local atmosphere then backfills with combustion products
                var comp = GridAtmosphereCompat.getComposition(level, pos);
                float o2 = comp.get(GasRegistry.OXYGEN);
                float n2 = comp.get(GasRegistry.NITROGEN);
                comp.add(GasRegistry.OXYGEN,         -o2 * 0.8f);
                comp.add(GasRegistry.NITROGEN,        -n2 * 0.6f);
                comp.add(GasRegistry.CARBON_DIOXIDE,   o2 * 0.5f);
                comp.add(GasRegistry.CARBON_MONOXIDE,  o2 * 0.2f);
                comp.add(GasRegistry.SULFUR_DIOXIDE,   30f);
                GridAtmosphereCompat.setComposition(level, pos, comp);
                // Then radius burst of combustion gases outward
                gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE,  60f, 6);
                gasRadius(level, pos, GasRegistry.CARBON_MONOXIDE, 20f, 4);
                gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE,  25f, 4);
                partRadius(level, pos, ParticulateType.ASH_CLOUD,  120f, 6);
                ShockwaveHandler.spawn(level, pos, 16f);
            }
            case "mutantmonsters:mutant_enderman" -> {
                gasRadius(level, pos, GasRegistry.VOID_BREATH,       50f, 6);
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 35f, 5);
                drainRadius(level, pos, GasRegistry.OXYGEN,   30f, 5);
                drainRadius(level, pos, GasRegistry.NITROGEN, 20f, 4);
                ShockwaveHandler.spawn(level, pos, 10f);
            }
            case "mutantmonsters:mutant_zombie" -> {
                gasRadius(level, pos, GasRegistry.CADAVERINE,       30f, 4);
                gasRadius(level, pos, GasRegistry.PUTRESCINE,       30f, 4);
                gasRadius(level, pos, GasRegistry.INDOLE,           20f, 3);
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 25f, 4);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 50f, 4);
            }
            case "mutantmonsters:mutant_skeleton" -> {
                partRadius(level, pos, ParticulateType.DUST,  40f, 4);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 20f, 3);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("mutantmonsters:")) return;

        BlockPos pos = BlockPos.containing(proj.position());

        switch (type) {
            case "mutantmonsters:skull_spirit" -> {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,   20f, 3);
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA,  10f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 25f, 3);
                ShockwaveHandler.spawn(level, pos, 4f);
            }
            case "mutantmonsters:endersoul_fragment" -> {
                gasRadius(level, pos, GasRegistry.VOID_BREATH,       15f, 2);
                gasRadius(level, pos, GasRegistry.ENDER_PARTICULATE, 12f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
            case "mutantmonsters:throwable_block" -> {
                partRadius(level, pos, ParticulateType.DUST,        30f, 3);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 5f);
            }
        }
    }
}
