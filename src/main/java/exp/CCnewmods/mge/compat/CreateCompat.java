package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;

/**
 * Create Encased Fan compat.
 *
 * An encased fan's AirCurrent defines a directed flow volume. MGE uses this to:
 * 1. Displace gas bodily in the flow direction — gas in the affected column is
 *    pushed toward the far end of the AirCurrent bounds. Higher fan speed = more
 *    displacement per tick.
 * 2. Disperse accumulated ground-layer heavy gases — if the fan points downward
 *    or horizontally at ground level it breaks up CO₂/radon pooling.
 * 3. Carry particulates with the flow — same displacement applied to particulates.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreateCompat {

    public static final String CREATE_MODID = "create";
    private static boolean loaded = false;
    private static final int SCAN_INTERVAL = 2;
    private static int tick = 0;

    private CreateCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(CREATE_MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Create detected — Encased Fan atmosphere displacement active.");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!loaded || event.phase != TickEvent.Phase.END) return;
        if (++tick % SCAN_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            tickFans(level);
        }
    }

    private static void tickFans(ServerLevel level) {
        level.blockEntityList.forEach(be -> {
            if (!(be instanceof EncasedFanBlockEntity fan) || be.isRemoved()) return;
            var airCurrent = fan.airCurrent;
            if (airCurrent == null) return;

            float speed = Math.abs(fan.getSpeed());
            if (speed < 1f) return;

            Direction flowDir = fan.getAirFlowDirection();
            if (flowDir == null) return;

            // Intensity: fraction of block content displaced per tick
            // Capped — even a max-speed fan shouldn't fully empty a block instantly
            float intensity = Math.min(0.4f, speed / 256f * 0.3f);

            AABB bounds = airCurrent.bounds;
            if (bounds == null) return;

            // Walk positions in the flow direction, displacing each into the next
            int minX = (int) Math.floor(bounds.minX);
            int minY = (int) Math.floor(bounds.minY);
            int minZ = (int) Math.floor(bounds.minZ);
            int maxX = (int) Math.ceil(bounds.maxX);
            int maxY = (int) Math.ceil(bounds.maxY);
            int maxZ = (int) Math.ceil(bounds.maxZ);

            // Iterate in reverse flow direction so we don't double-count
            int dx = flowDir.getStepX(), dy = flowDir.getStepY(), dz = flowDir.getStepZ();
            // Build ordered list of positions along the flow axis
            java.util.List<BlockPos> column = new java.util.ArrayList<>();
            BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)
                .forEach(p -> column.add(p.immutable()));
            // Sort so farthest-from-fan comes first (push outward)
            column.sort((a, b) -> {
                int da = a.getX() * dx + a.getY() * dy + a.getZ() * dz;
                int db = b.getX() * dx + b.getY() * dy + b.getZ() * dz;
                return Integer.compare(db, da); // descending = farthest first
            });

            for (BlockPos pos : column) {
                if (!level.isLoaded(pos)) continue;
                BlockEntity src = level.getBlockEntity(pos);
                if (!(src instanceof AtmosphereBlockEntity srcAtm)) continue;

                BlockPos dst = pos.relative(flowDir);
                if (!level.isLoaded(dst)) continue;
                BlockEntity dstBE = level.getBlockEntity(dst);
                if (!(dstBE instanceof AtmosphereBlockEntity dstAtm)) continue;

                // Displace gas
                var srcComp = srcAtm.getComposition();
                var dstComp = dstAtm.getComposition();
                srcComp.getTag().getAllKeys().forEach(key -> {
                    float amt = srcComp.get(key);
                    float t = amt * intensity;
                    srcComp.addByKey(key, -t);
                    dstComp.addByKey(key, t);
                });
                srcAtm.setComposition(srcComp);
                dstAtm.setComposition(dstComp);

                // Displace particulates
                var srcParts = srcAtm.getParticulates();
                var dstParts = dstAtm.getParticulates();
                for (ParticulateType type : ParticulateType.values()) {
                    float amt = srcParts.get(type);
                    if (amt <= 0f) continue;
                    float t = amt * intensity;
                    srcParts.add(type, -t);
                    dstParts.add(type, t);
                }
                srcAtm.setParticulates(srcParts);
                dstAtm.setParticulates(dstParts);

                Mge.getScheduler(level).enqueue(pos);
                Mge.getScheduler(level).enqueue(dst);
            }
        });
    }
}
