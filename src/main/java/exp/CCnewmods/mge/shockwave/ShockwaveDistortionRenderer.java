package exp.CCnewmods.mge.shockwave;

import exp.CCnewmods.mge.Mge;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side bookkeeping for active shockwave visuals.
 *
 * <p><b>This used to be a fog-plane hack</b> (narrowing the near clip plane
 * as a wave passed through the camera, faked via {@code ViewportEvent.RenderFog}).
 * That's gone entirely — replaced by a real post-process refraction shader.
 * See {@link ShockwavePostProcessor}, which does the actual GL work every
 * frame; this class is now just the live wave-state table that the
 * post-processor reads from and {@link ShockwaveDataPacket} writes to.
 *
 * <p>Waves are keyed by {@code waveId} (assigned server-side, see
 * {@code ShockwaveFront.waveId}) rather than by list position, since
 * UPDATE packets need to update a specific existing wave's state in place
 * rather than spawning duplicates.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ShockwaveDistortionRenderer {

    /**
     * One active wave's last-known visual state, as reported by the server.
     * {@code origin} is the absolute world position — camera-relative
     * conversion happens fresh every frame in {@link ShockwavePostProcessor},
     * since the camera moves every frame but the wave's world position
     * (as last reported) does not.
     */
    public record ClientWave(long waveId, Vec3 origin, float radius,
                              float strength, float temperatureC,
                              float[] particulateBuckets) {}

    // LinkedHashMap preserves spawn order, which keeps the per-wave pass
    // sequencing deterministic frame-to-frame (matters for the desaturation
    // pass's "imperfect compounding" — see shockwave_desaturate.fsh notes —
    // since a stable order means at least consistent, not flickering,
    // results when waves overlap).
    private static final Map<Long, ClientWave> WAVES = new LinkedHashMap<>();

    private ShockwaveDistortionRenderer() {}

    /**
     * Called once per client tick from {@code Mge.onClientTick}.
     *
     * <p>The old fog-plane hack used this to age waves and recompute the
     * near-plane multiplier every tick — none of that applies anymore,
     * the post-processor (see {@link ShockwavePostProcessor}) does the
     * actual per-frame work driven by server packets instead.
     *
     * <p>What's left here is a safety-net staleness prune: if a wave never
     * receives a periodic UPDATE for an unreasonably long time (e.g. a
     * DEATH packet was somehow missed, or the world was left mid-session
     * in an unusual way), drop it client-side rather than leaving a
     * permanently stuck visual.
     */
    private static final long STALE_TICKS = 200; // 10 seconds at 20 TPS
    private static final Map<Long, Long> LAST_SEEN_TICK = new LinkedHashMap<>();
    private static long clientTickCounter = 0;

    public static void clientTick() {
        clientTickCounter++;
        for (long id : WAVES.keySet()) {
            LAST_SEEN_TICK.putIfAbsent(id, clientTickCounter);
        }
        LAST_SEEN_TICK.keySet().removeIf(id -> !WAVES.containsKey(id));

        var stale = new java.util.ArrayList<Long>();
        for (var entry : LAST_SEEN_TICK.entrySet()) {
            if (clientTickCounter - entry.getValue() > STALE_TICKS) stale.add(entry.getKey());
        }
        for (long id : stale) {
            WAVES.remove(id);
            LAST_SEEN_TICK.remove(id);
        }
    }

    /** Called by {@link ShockwaveDataPacket} on SPAWN/UPDATE. */
    public static void updateWave(long waveId, Vec3 origin, float radius,
                                   float strength, float temperatureC,
                                   float[] particulateBuckets) {
        WAVES.put(waveId, new ClientWave(waveId, origin, radius, strength,
                temperatureC, particulateBuckets));
        LAST_SEEN_TICK.put(waveId, clientTickCounter);
    }

    /** Called by {@link ShockwaveDataPacket} on DEATH. */
    public static void removeWave(long waveId) {
        WAVES.remove(waveId);
    }

    /** Read-only snapshot for {@link ShockwavePostProcessor} to render from. */
    public static Map<Long, ClientWave> activeWaves() {
        return WAVES;
    }

    /** Called on world unload / disconnect so stale waves don't carry over
     *  into a new world/session. */
    public static void clear() {
        WAVES.clear();
        LAST_SEEN_TICK.clear();
    }
}
