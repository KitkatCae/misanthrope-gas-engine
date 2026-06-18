package exp.CCnewmods.mge.event;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Listens to Forge world events and mutates atmosphere block gas and particulate
 * compositions accordingly.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldEventHandler {

    private WorldEventHandler() {}

    // ── Block placement ───────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockState placed = event.getPlacedBlock();
        if (placed.getBlock() instanceof FireBlock || placed.getBlock() instanceof CampfireBlock) {
            mutateFire(level, event.getPos(), 10f);
        }

        EnvironmentGrid.enqueueWithNeighbours(level, event.getPos());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.DUST, 20f);

        // Coal ore and coal blocks emit coal dust when mined
        var blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                .getKey(event.getState().getBlock());
        if (blockId != null) {
            String blockPath = blockId.getPath();
            if (blockPath.contains("coal") && (blockPath.contains("ore")
                    || blockPath.equals("coal_block") || blockPath.equals("coal"))) {
                GridAtmosphereCompat.addParticulate(level, pos,         ParticulateType.COAL_DUST, 80f);
                GridAtmosphereCompat.addParticulate(level, pos.above(), ParticulateType.COAL_DUST, 40f);
                EnvironmentGrid.enqueue(level, pos.above());
            }
        }
        EnvironmentGrid.enqueueWithNeighbours(level, pos);
    }

    // ── Explosions ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;

        net.minecraft.world.phys.Vec3 centre = event.getExplosion().getPosition();
        BlockPos centrePos = BlockPos.containing(centre);
        float baseRadius;
        try {
            java.lang.reflect.Field f = net.minecraft.world.level.Explosion.class.getDeclaredField("radius");
            f.setAccessible(true);
            baseRadius = f.getFloat(event.getExplosion());
        } catch (Exception e) { baseRadius = 4f; }

        // Check atmosphere at centre for amplification / vacuum suppression
        {
            var centreComp = GridAtmosphereCompat.getComposition(level, centrePos);
            float pressure = centreComp.totalPressure();
            if (pressure < exp.CCnewmods.mge.vacuum.VacuumHandler.VACUUM_THRESHOLD_MBAR) {
                affected.subList(affected.size() / 2, affected.size()).clear();
            } else {
                float amp = checkAtmosphericAmplification(centreComp);
                if (amp > 1f) {
                    exp.CCnewmods.mge.shockwave.ShockwaveHandler.spawn(
                            level, centrePos, baseRadius * amp * 2f);
                }
            }
        }

        // Mutate atmosphere in all affected blocks
        for (BlockPos pos : affected) {
            float o2 = GridAtmosphereCompat.getGas(level, pos, GasRegistry.OXYGEN);
            float consumed = Math.min(o2, 50f);
            GridAtmosphereCompat.addGas(level, pos, GasRegistry.OXYGEN,         -consumed);
            GridAtmosphereCompat.addGas(level, pos, GasRegistry.CARBON_DIOXIDE,  consumed * 0.6f);
            GridAtmosphereCompat.addGas(level, pos, GasRegistry.CARBON_MONOXIDE, consumed * 0.2f);
            GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SMOKE_AEROSOL, 120f);
            GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SOOT,           40f);
            GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.DUST,            30f);
            EnvironmentGrid.enqueue(level, pos);
        }

        // Shockwave from every explosion
        exp.CCnewmods.mge.shockwave.ShockwaveHandler.spawn(level, centrePos, baseRadius * 1.5f);
    }

    private static float checkAtmosphericAmplification(GasComposition comp) {
        float total = comp.totalPressure();
        if (total <= 0) return 1f;
        float oxidiser = comp.get(GasRegistry.OXYGEN);
        for (String key : comp.getTag().getAllKeys()) {
            exp.CCnewmods.mge.gas.Gas g = exp.CCnewmods.mge.gas.GasRegistry.get(key).orElse(null);
            if (g != null && g.properties().hasReactivity(exp.CCnewmods.mge.gas.ReactivityFlag.OXIDISER))
                oxidiser += comp.get(key);
        }
        if (oxidiser / total < 0.16f) return 1f;
        float maxAmp = 1f;
        for (String key : comp.getTag().getAllKeys()) {
            exp.CCnewmods.mge.gas.Gas gas = exp.CCnewmods.mge.gas.GasRegistry.get(key).orElse(null);
            if (gas == null || !gas.properties().isFlammable()) continue;
            float frac = comp.get(key) / total;
            if (frac >= gas.properties().lowerExplosiveLimit()
                    && frac <= gas.properties().upperExplosiveLimit()) {
                float amp = 1f + frac * 5f;
                if (amp > maxAmp) maxAmp = amp;
            }
        }
        return maxAmp;
    }

    // ── Nether portal ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onNetherPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        for (int dy = 0; dy <= 4; dy++) injectNetherGases(level, pos.above(dy));
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    /** Fire combustion: consumes the dominant oxidiser, produces appropriate products. */
    public static void mutateFire(ServerLevel level, BlockPos pos, float intensity) {
        var comp = GridAtmosphereCompat.getComposition(level, pos);
        float totalPressure = Math.max(1f, comp.totalPressure());
        float fluorineFraction = comp.get(GasRegistry.FLUORINE) / totalPressure;
        float consumed;

        if (fluorineFraction > 0.1f) {
            // Fluorine atmosphere — combustion produces HF instead of CO₂
            consumed = Math.min(comp.get(GasRegistry.FLUORINE), intensity * 2f);
            comp.add(GasRegistry.FLUORINE,         -consumed);
            comp.add(GasRegistry.HYDROGEN_FLUORIDE, consumed * 0.8f);
            comp.add(GasRegistry.CARBON_DIOXIDE,    intensity * 0.05f);
        } else {
            float o2 = comp.get(GasRegistry.OXYGEN);
            consumed = Math.min(o2, intensity);
            comp.add(GasRegistry.OXYGEN,         -consumed);
            comp.add(GasRegistry.CARBON_DIOXIDE,  consumed * 0.7f);
            comp.add(GasRegistry.CARBON_MONOXIDE, consumed * 0.15f);
        }
        GridAtmosphereCompat.setComposition(level, pos, comp);
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SOOT,          consumed * 1.5f);
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SMOKE_AEROSOL, consumed * 3f);
        EnvironmentGrid.enqueue(level, pos);
    }

    /** Inject Nether-characteristic gases and soul dust near a portal. */
    public static void injectNetherGases(ServerLevel level, BlockPos pos) {
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.SULFUR_DIOXIDE,  15f);
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.BLAZE_FUME,       8f);
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.SOUL_SMOKE,       5f);
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.CARBON_MONOXIDE, 10f);
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SOUL_DUST,          8f);
        GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.NETHER_QUARTZ_DUST, 5f);
        EnvironmentGrid.enqueue(level, pos);
    }

    /** Water vapour injection (called by compat layers on lava/water contact, etc.). */
    public static void injectWaterVapour(ServerLevel level, BlockPos pos, float mbar) {
        GridAtmosphereCompat.addGas(level, pos, GasRegistry.WATER_VAPOR, mbar);
    }

    /** General gas injection — for compat layers and future extensions. */
    public static void injectGas(ServerLevel level, BlockPos pos,
                                 exp.CCnewmods.mge.gas.Gas gas, float mbar) {
        GridAtmosphereCompat.addGas(level, pos, gas, mbar);
    }

    /** General particulate injection — for compat layers and future extensions. */
    public static void injectParticulate(ServerLevel level, BlockPos pos,
                                         ParticulateType type, float mgM3) {
        GridAtmosphereCompat.addParticulate(level, pos, type, mgM3);
        EnvironmentGrid.enqueue(level, pos);
    }
}
