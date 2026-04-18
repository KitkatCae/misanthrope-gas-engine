package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.util.ChunkIterator;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * PneumaticCraft: Repressurized compat.
 *
 * <h3>Pressure leaks → atmosphere</h3>
 * When a PNC machine's {@code IAirHandlerMachine.getSideLeaking()} is non-null,
 * compressed air is venting into the surrounding atmosphere. We inject a proportional
 * amount of nitrogen + oxygen (atmospheric ratio, ~78/21) into the atmosphere block
 * on the leaking face. Higher pressure = more injection per tick.
 *
 * <h3>Critical pressure bursts</h3>
 * When a machine exceeds its critical pressure, it should explode. We hook this by
 * checking {@code getPressure() >= getCriticalPressure()} and triggering
 * {@link exp.CCnewmods.mge.cave.GasDetonationHandler}-style logic: violent
 * decompression, shrapnel smoke, and a pressure wave of nitrogen into surrounding blocks.
 *
 * <h3>Safety venting</h3>
 * PNC machines can have safety venting enabled on a face. We treat this as a
 * controlled leak — smaller injection, no damage.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PneumaticCraftCompat {

    public static final String PNC_MODID = "pneumaticcraft";
    private static boolean loaded = false;
    private static final int SCAN_INTERVAL = 5;
    private static int tick = 0;

    // mB of air per bar of overpressure per tick injected into atmosphere
    private static final float LEAK_RATE_MB_PER_BAR = 8f;
    private static final float BURST_NITROGEN  = 200f;
    private static final float BURST_OXYGEN    =  55f;

    private PneumaticCraftCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(PNC_MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] PneumaticCraft: Repressurized detected — pressure leak atmosphere injection active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!loaded || event.phase != TickEvent.Phase.END) return;
        if (++tick % SCAN_INTERVAL != 0) return;
        if (!MgeConfig.enableGasEffects) return;
        for (ServerLevel level : event.getServer().getAllLevels()) tickPNC(level);
    }

    private static void tickPNC(ServerLevel level) {
        ChunkIterator.forEach(level, holder -> {
            var chunk = holder.getTickingChunk();
            if (chunk == null) return;
            chunk.getBlockEntities().forEach((pos, be) -> {
                if (be.isRemoved()) return;
                be.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY)
                  .ifPresent(handler -> processMachine(level, pos, be, handler));
            });
        });
    }

    private static void processMachine(ServerLevel level, BlockPos pos,
                                        BlockEntity be, IAirHandlerMachine handler) {
        float pressure = handler.getPressure();
        float critical = handler.getCriticalPressure();
        float danger   = handler.getDangerPressure();

        // ── Critical burst ──────────────────────────────────────────────────
        if (pressure >= critical && critical > 0f) {
            triggerBurst(level, pos, pressure);
            return;
        }

        // ── Active leak ─────────────────────────────────────────────────────
        Direction leakFace = handler.getSideLeaking();
        if (leakFace == null) return;

        float overpressure = Math.max(0f, pressure - 1.0f); // above atmospheric
        float injectAmount = overpressure * LEAK_RATE_MB_PER_BAR;
        if (injectAmount < 0.1f) return;

        BlockPos targetPos = pos.relative(leakFace);
        if (!level.isLoaded(targetPos)) return;
        BlockEntity targetBE = level.getBlockEntity(targetPos);
        if (!(targetBE instanceof AtmosphereBlockEntity atm)) return;

        // Inject air as N₂/O₂ mixture (78/21 ratio)
        GasComposition comp = atm.getComposition();
        comp.add(GasRegistry.NITROGEN, injectAmount * 0.78f);
        comp.add(GasRegistry.OXYGEN,   injectAmount * 0.21f);
        atm.setComposition(comp);
        Mge.getScheduler(level).enqueue(targetPos);

        // Danger pressure — also add some oil mist / aerosol from compressor
        if (pressure >= danger) {
            var parts = atm.getParticulates();
            parts.add(ParticulateType.SMOKE_AEROSOL, injectAmount * 0.5f);
            atm.setParticulates(parts);
        }
    }

    private static void triggerBurst(ServerLevel level, BlockPos pos, float pressure) {
        // Violent decompression — push N₂/O₂ into all 6 neighbouring blocks
        float energy = Math.min(pressure * 50f, 500f);
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.relative(dir);
            if (!level.isLoaded(neighbour)) continue;
            BlockEntity be = level.getBlockEntity(neighbour);
            if (!(be instanceof AtmosphereBlockEntity atm)) continue;

            GasComposition comp = atm.getComposition();
            comp.add(GasRegistry.NITROGEN, BURST_NITROGEN);
            comp.add(GasRegistry.OXYGEN,   BURST_OXYGEN);
            atm.setComposition(comp);

            var parts = atm.getParticulates();
            parts.add(ParticulateType.SMOKE_AEROSOL, 80f);
            atm.setParticulates(parts);
            Mge.getScheduler(level).enqueue(neighbour);
        }

        // Small explosion for the decompression event
        var centre = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        level.explode(null, centre.x, centre.y, centre.z,
                Math.min(4f, pressure),
                net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);

        Mge.LOGGER.debug("[MGE] PNC critical burst at {} pressure={}", pos, pressure);
    }
}
