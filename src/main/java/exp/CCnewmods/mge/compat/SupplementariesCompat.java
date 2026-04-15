package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.util.ChunkIterator;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import net.mehvahdjukaar.supplementaries.common.block.tiles.BellowsBlockTile;

/**
 * Supplementaries Bellows compat.
 * Displaces gas bodily in the facing direction — no O₂ injection.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SupplementariesCompat {

    public static final String SUPP_MODID = "supplementaries";
    private static boolean loaded = false;
    private static final int SCAN_INTERVAL = 4;
    private static int tick = 0;

    private SupplementariesCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(SUPP_MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Supplementaries detected — Bellows atmosphere displacement active.");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!loaded || event.phase != TickEvent.Phase.END) return;
        if (++tick % SCAN_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;
        for (var level : event.getServer().getAllLevels()) tickBellows(level);
    }

    private static void tickBellows(ServerLevel level) {
        ChunkIterator.forEach(level, holder -> {
            var chunk = holder.getTickingChunk();
            if (chunk == null) return;
            chunk.getBlockEntities().forEach((pos, be) -> {
                if (!(be instanceof BellowsBlockTile bellows) || be.isRemoved()) return;
                processBellows(level, bellows, pos);
            });
        });
    }

    private static void processBellows(ServerLevel level, BellowsBlockTile bellows, BlockPos bePos) {
        Direction dir = bellows.getDirection();
        if (dir == null) return;

        float height = bellows.getHeight(1.0f);
        if (height >= 0.35f) return; // not in compression stroke

        float intensity = (0.35f - height) / 0.35f;
        float fraction  = Math.min(0.25f, intensity * 0.3f);

        BlockPos outputPos = bePos.relative(dir);
        BlockPos pushPos   = outputPos.relative(dir);
        BlockPos sourcePos = bePos.relative(dir.getOpposite());

        if (!level.isLoaded(outputPos) || !level.isLoaded(pushPos)) return;
        BlockEntity outBE  = level.getBlockEntity(outputPos);
        BlockEntity pushBE = level.getBlockEntity(pushPos);
        if (!(outBE  instanceof AtmosphereBlockEntity outAtm))  return;
        if (!(pushBE instanceof AtmosphereBlockEntity pushAtm)) return;

        // Push output → pushPos
        transferGas(outAtm.getComposition(), pushAtm.getComposition(), fraction);
        outAtm.setComposition(outAtm.getComposition());
        pushAtm.setComposition(pushAtm.getComposition());

        // Displace particulates
        for (ParticulateType type : ParticulateType.values()) {
            float amt = outAtm.getParticulates().get(type);
            if (amt <= 0f) continue;
            float t = amt * fraction;
            outAtm.getParticulates().add(type, -t);
            pushAtm.getParticulates().add(type, t);
        }
        outAtm.setParticulates(outAtm.getParticulates());
        pushAtm.setParticulates(pushAtm.getParticulates());

        // Pull source → output
        BlockEntity srcBE = level.getBlockEntity(sourcePos);
        if (srcBE instanceof AtmosphereBlockEntity srcAtm && level.isLoaded(sourcePos)) {
            transferGas(srcAtm.getComposition(), outAtm.getComposition(), fraction * 0.5f);
            srcAtm.setComposition(srcAtm.getComposition());
            outAtm.setComposition(outAtm.getComposition());
            Mge.getScheduler(level).enqueue(sourcePos);
        }

        Mge.getScheduler(level).enqueue(outputPos);
        Mge.getScheduler(level).enqueue(pushPos);
    }

    /** Transfer {@code fraction} of each gas in src into dst using raw NBT operations. */
    private static void transferGas(GasComposition src, GasComposition dst, float fraction) {
        var srcTag = src.getTag();
        var dstTag = dst.getTag();
        for (String key : new java.util.ArrayList<>(srcTag.getAllKeys())) {
            float amt      = srcTag.getFloat(key);
            float transfer = amt * fraction;
            if (transfer <= 0f) continue;
            srcTag.putFloat(key, Math.max(0f, amt - transfer));
            dstTag.putFloat(key, dstTag.getFloat(key) + transfer);
        }
    }
}
