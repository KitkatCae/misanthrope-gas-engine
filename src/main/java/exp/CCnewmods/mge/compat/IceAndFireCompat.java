package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.event.WorldEventHandler;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Ice and Fire compat.
 *
 * <ul>
 *   <li><b>Fire Dragon</b> — tick: O₂ drain + CO₂/CO/SO₂ breath; fluorine atmosphere
 *       check swaps products to HF. Death burst: CO₂ + SO₂ + ash.</li>
 *   <li><b>Ice Dragon</b> — tick: DRAGON_ICE_CLOUD + ICE_CRYSTALS.</li>
 *   <li><b>Lightning Dragon</b> — tick: IONISED_AIR + OZONE.</li>
 *   <li><b>Ghost</b> — tick: faint SOUL_ESSENCE.</li>
 *   <li><b>Hydra</b> — tick: fire breath ×1.4 intensity.</li>
 *   <li><b>Projectiles</b>: dragonfirecharge (shockwave + mutateFire),
 *       dragonicecharge (lava contact → steam + shockwave),
 *       dragonlightningcharge (vacuum + shockwave).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IceAndFireCompat {

    public static final String MODID = "iceandfire";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private IceAndFireCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Ice and Fire detected — dragon atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    // ── Tick emissions ────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        BlockPos pos = entity.blockPosition();
        String type = entity.getType().toString();

        if (type.startsWith("iceandfire:")) {
            if (type.equals("iceandfire:fire_dragon")) {
                emitFireDragonBreath(level, pos, 1.0f);
            } else if (type.equals("iceandfire:ice_dragon")) {
                gas(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 25f);
                part(level, pos, ParticulateType.ICE_CRYSTALS, 30f);
            } else if (type.equals("iceandfire:lightning_dragon")) {
                gas(level, pos, GasRegistry.IONISED_AIR, 20f);
                gas(level, pos, GasRegistry.OZONE, 8f);
                gas(level, pos, GasRegistry.NITRIC_OXIDE, 4f);
                part(level, pos, ParticulateType.IONISED_PARTICLES, 12f);
            } else if (type.equals("iceandfire:ghost")) {
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 6f);
            } else if (type.equals("iceandfire:hydra")) {
                // Three-headed — 1.4× fire intensity
                emitFireDragonBreath(level, pos, 1.4f);
            }
        }
    }

    // ── Projectile impacts ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;

        String type = proj.getType().toString();
        BlockPos pos = BlockPos.containing(proj.position());

        // Registry IDs confirmed: iceandfire:fire_dragon_charge, iceandfire:ice_dragon_charge,
        // iceandfire:lightning_dragon_charge
        if (!type.contains("iceandfire:")) return;

        if (type.equals("iceandfire:fire_dragon_charge")) {
            // Fire charge — combustion + shockwave
            WorldEventHandler.mutateFire(level, pos, 30f);
            gasRadius(level, pos, GasRegistry.CARBON_DIOXIDE, 20f, 3);
            gasRadius(level, pos, GasRegistry.SULFUR_DIOXIDE, 8f, 2);
            partRadius(level, pos, ParticulateType.ASH_CLOUD, 80f, 3);
            ShockwaveHandler.spawn(level, pos, 5f);

        } else if (type.equals("iceandfire:ice_dragon_charge")) {
            // Ice charge — burst of ice cloud + crystals; if lava nearby, steam explosion
            gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 40f, 3);
            partRadius(level, pos, ParticulateType.ICE_CRYSTAL_SHARDS, 60f, 3);
            // Check for lava adjacency → steam
            for (var adj : new BlockPos[]{pos.north(),pos.south(),pos.east(),pos.west(),pos.below()}) {
                if (level.getFluidState(adj).is(net.minecraft.tags.FluidTags.LAVA)) {
                    gasRadius(level, pos, GasRegistry.WATER_VAPOR, 50f, 2);
                    ShockwaveHandler.spawn(level, pos, 4f);
                    break;
                }
            }

        } else if (type.equals("iceandfire:lightning_dragon_charge")) {
            // Lightning charge — micro-vacuum along impact + ionisation + shockwave
            var comp = GridAtmosphereCompat.getComposition(level, pos);
            comp.add(GasRegistry.NITROGEN, -comp.get(GasRegistry.NITROGEN) * 0.5f);
            comp.add(GasRegistry.OXYGEN,   -comp.get(GasRegistry.OXYGEN)   * 0.5f);
            comp.add(GasRegistry.IONISED_AIR, 15f);
            comp.add(GasRegistry.OZONE, 8f);
            GridAtmosphereCompat.setComposition(level, pos, comp);
            partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 20f, 2);
            ShockwaveHandler.spawn(level, pos, 6f);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fire dragon breath emission — checks local atmosphere for fluorine fraction
     * and swaps combustion products to HF if dominant oxidiser is fluorine.
     */
    static void emitFireDragonBreath(ServerLevel level, BlockPos pos, float scale) {
        var comp = GridAtmosphereCompat.getComposition(level, pos);
        float totalPressure = Math.max(1f, comp.totalPressure());
        float fluorineFraction = comp.get(GasRegistry.FLUORINE) / totalPressure;

        if (fluorineFraction > 0.1f) {
            float consumed = Math.min(comp.get(GasRegistry.FLUORINE), 20f * scale);
            comp.add(GasRegistry.FLUORINE,          -consumed);
            comp.add(GasRegistry.HYDROGEN_FLUORIDE,  consumed * 0.8f);
            comp.add(GasRegistry.CARBON_DIOXIDE,     5f * scale);
            GridAtmosphereCompat.setComposition(level, pos, comp);
        } else {
            float o2 = comp.get(GasRegistry.OXYGEN);
            float consumed = Math.min(o2, 30f * scale);
            comp.add(GasRegistry.OXYGEN,          -consumed);
            comp.add(GasRegistry.CARBON_DIOXIDE,   consumed * 0.65f);
            comp.add(GasRegistry.CARBON_MONOXIDE,  consumed * 0.15f);
            comp.add(GasRegistry.SULFUR_DIOXIDE,   8f * scale);
            GridAtmosphereCompat.setComposition(level, pos, comp);
            GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.ASH_CLOUD, 20f * scale);
        }
    }
}
