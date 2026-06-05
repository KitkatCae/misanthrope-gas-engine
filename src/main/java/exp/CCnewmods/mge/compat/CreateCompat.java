package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.util.ChunkIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;

/**
 * Create Encased Fan compat — displaces atmosphere in the fan's air current volume.
 *
 * We cast the block entity to {@link IAirCurrentSource} (an interface with no Ponder
 * dependency) rather than the concrete {@code EncasedFanBlockEntity} to avoid pulling
 * in the SmartBlockEntity → VirtualBlockEntity (Ponder) class hierarchy at compile time.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreateCompat {

    public static final String CREATE_MODID = "create";
    private static boolean loaded = false;
    private static Class<?> encasedFanClass = null;
    private static final int SCAN_INTERVAL = 2;
    private static int tick = 0;

    private CreateCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(CREATE_MODID)) return;
        try {
            encasedFanClass = Class.forName(
                "com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity");
        } catch (ClassNotFoundException e) {
            Mge.LOGGER.warn("[MGE] Create: EncasedFanBlockEntity not found — {}", e.getMessage());
            return;
        }
        loaded = true;
        Mge.LOGGER.info("[MGE] Create detected — Encased Fan atmosphere displacement active.");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!loaded || event.phase != TickEvent.Phase.END) return;
        if (++tick % SCAN_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;
        for (ServerLevel level : event.getServer().getAllLevels()) tickFans(level);
    }

    private static void tickFans(ServerLevel level) {
        ChunkIterator.forEach(level, holder -> {
            var chunk = holder.getTickingChunk();
            if (chunk == null) return;
            chunk.getBlockEntities().forEach((pos, be) -> {
                // Check via reflected class to avoid compile-time hierarchy resolution
                if (!encasedFanClass.isInstance(be) || be.isRemoved()) return;
                if (!(be instanceof IAirCurrentSource fan)) return;
                processFan(level, fan, pos);
            });
        });
    }

    private static void processFan(ServerLevel level, IAirCurrentSource fan, BlockPos fanPos) {
        AirCurrent airCurrent = fan.getAirCurrent();
        if (airCurrent == null) return;

        float speed = Math.abs(fan.getSpeed());
        if (speed < 1f) return;

        Direction flowDir = fan.getAirFlowDirection();
        if (flowDir == null) return;

        float intensity = Math.min(0.4f, speed / 256f * 0.3f);

        AABB bounds = airCurrent.bounds;
        if (bounds == null) return;

        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX);
        int maxY = (int) Math.ceil(bounds.maxY);
        int maxZ = (int) Math.ceil(bounds.maxZ);

        int dx = flowDir.getStepX(), dy = flowDir.getStepY(), dz = flowDir.getStepZ();

        java.util.List<BlockPos> column = new java.util.ArrayList<>();
        BlockPos.betweenClosedStream(minX, minY, minZ, maxX, maxY, maxZ)
                .forEach(p -> column.add(p.immutable()));
        column.sort((a, b) -> {
            int da = a.getX() * dx + a.getY() * dy + a.getZ() * dz;
            int db = b.getX() * dx + b.getY() * dy + b.getZ() * dz;
            return Integer.compare(db, da);
        });

        for (BlockPos pos : column) {
            if (!level.isLoaded(pos)) continue;
            BlockPos dst = pos.relative(flowDir);
            if (!level.isLoaded(dst)) continue;
            // Transfer gas between grid cells
            var srcComp = GridAtmosphereCompat.getComposition(level, pos);
            var dstComp = GridAtmosphereCompat.getComposition(level, dst);
            transferGas(srcComp, dstComp, intensity);
            GridAtmosphereCompat.setComposition(level, pos, srcComp);
            GridAtmosphereCompat.setComposition(level, dst, dstComp);
            // Particulates via legacy
            for (ParticulateType type : ParticulateType.values()) {
                var srcParts = GridAtmosphereCompat.getParticulates(level, pos);
                float amt = srcParts.get(type);
                if (amt <= 0f) continue;
                float t = amt * intensity;
                GridAtmosphereCompat.addParticulate(level, pos, type, -t);
                GridAtmosphereCompat.addParticulate(level, dst, type, t);
            }
            EnvironmentGrid.enqueue(level, pos);
            EnvironmentGrid.enqueue(level, dst);
        }
    }

    private static void transferGas(GasComposition src, GasComposition dst, float fraction) {
        var srcTag = src.getTag();
        var dstTag = dst.getTag();
        for (String key : new java.util.ArrayList<>(srcTag.getAllKeys())) {
            float amt = srcTag.getFloat(key);
            float transfer = amt * fraction;
            if (transfer <= 0f) continue;
            srcTag.putFloat(key, Math.max(0f, amt - transfer));
            dstTag.putFloat(key, dstTag.getFloat(key) + transfer);
        }
    }
}
