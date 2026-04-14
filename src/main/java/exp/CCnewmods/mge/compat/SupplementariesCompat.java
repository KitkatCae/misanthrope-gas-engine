package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
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
 *
 * When a bellows is in its compression stroke it physically displaces the gas in
 * front of it — the entire atmosphere block is pushed N blocks further in the facing
 * direction, proportional to compression intensity. No oxygen is injected; the air
 * that was there is simply moved.
 *
 * Implementation: every SCAN_INTERVAL ticks, scan all loaded block entities. For
 * each blowing BellowsBlockTile, take a fraction of the output block's gas and
 * particulate composition and push it one block further in the facing direction.
 * Simultaneously pull from the block behind the bellows into the output block.
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

        for (var level : event.getServer().getAllLevels()) {
            tickBellows(level);
        }
    }

    private static void tickBellows(ServerLevel level) {
        level.blockEntityList.forEach(be -> {
            if (!(be instanceof BellowsBlockTile bellows) || be.isRemoved()) return;

            Direction dir = bellows.getDirection();
            if (dir == null) return;

            // getHeight() < 0.35 means compression stroke (blowing)
            float height = bellows.getHeight(1.0f);
            if (height >= 0.35f) return;

            float intensity = (0.35f - height) / 0.35f; // 0..1

            BlockPos outputPos = be.getBlockPos().relative(dir);
            BlockPos pushPos   = outputPos.relative(dir);   // one further
            BlockPos sourcePos = be.getBlockPos().relative(dir.getOpposite()); // behind bellows

            if (!level.isLoaded(outputPos) || !level.isLoaded(pushPos)) return;

            BlockEntity outBE  = level.getBlockEntity(outputPos);
            BlockEntity pushBE = level.getBlockEntity(pushPos);
            if (!(outBE instanceof AtmosphereBlockEntity outAtm)) return;
            if (!(pushBE instanceof AtmosphereBlockEntity pushAtm)) return;

            // Fraction of output block's content to displace forward
            float fraction = Math.min(0.25f, intensity * 0.3f);

            // --- Push output → pushPos ---
            var outComp  = outAtm.getComposition();
            var pushComp = pushAtm.getComposition();
            outComp.getTag().getAllKeys().forEach(key -> {
                float amt = outComp.get(key);
                float transfer = amt * fraction;
                outComp.addByKey(key, -transfer);
                pushComp.addByKey(key, transfer);
            });
            outAtm.setComposition(outComp);
            pushAtm.setComposition(pushComp);

            // Displace particulates forward too
            var outParts  = outAtm.getParticulates();
            var pushParts = pushAtm.getParticulates();
            for (ParticulateType type : ParticulateType.values()) {
                float amt = outParts.get(type);
                if (amt <= 0f) continue;
                float transfer = amt * fraction;
                outParts.add(type, -transfer);
                pushParts.add(type, transfer);
            }
            outAtm.setParticulates(outParts);
            pushAtm.setParticulates(pushParts);

            // --- Pull source → output (replace what was pushed) ---
            BlockEntity srcBE = level.getBlockEntity(sourcePos);
            if (srcBE instanceof AtmosphereBlockEntity srcAtm && level.isLoaded(sourcePos)) {
                var srcComp = srcAtm.getComposition();
                srcComp.getTag().getAllKeys().forEach(key -> {
                    float amt = srcComp.get(key);
                    float transfer = amt * fraction * 0.5f; // pull is gentler than push
                    srcComp.addByKey(key, -transfer);
                    outComp.addByKey(key, transfer);
                });
                srcAtm.setComposition(srcComp);
                outAtm.setComposition(outComp);
                Mge.getScheduler(level).enqueue(sourcePos);
            }

            Mge.getScheduler(level).enqueue(outputPos);
            Mge.getScheduler(level).enqueue(pushPos);
        });
    }
}
