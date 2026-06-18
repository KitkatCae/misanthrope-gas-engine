package exp.CCnewmods.mge.shockwave;

import exp.CCnewmods.mge.Mge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Client-bound packet carrying shockwave visual state for the
 * post-process distortion shader.
 *
 * <p>Three message kinds share one wire format, distinguished by
 * {@link #kind}:
 * <ul>
 *   <li>{@link Kind#SPAWN} — sent once when a wave is created
 *       ({@code ShockwaveHandler.spawn}).</li>
 *   <li>{@link Kind#UPDATE} — sent every {@code UPDATE_INTERVAL} ticks
 *       while the wave is alive, carrying current radius/strength/
 *       temperature/particulate buckets as they accumulate.</li>
 *   <li>{@link Kind#DEATH} — sent once when the wave dies, so the client
 *       removes it immediately instead of waiting on a timeout.</li>
 * </ul>
 *
 * <p>SPAWN and UPDATE carry the same payload shape; DEATH only needs the
 * wave ID. {@link #waveId} is included on every message so the client can
 * tell which active visual wave an UPDATE/DEATH belongs to.
 *
 * <p>Particulate buckets are sent as raw mg/m³ floats (see
 * {@link ParticulateBucket}); the client shader normalizes them by a fixed
 * max-concentration constant rather than the server sending a pre-normalized
 * 0–1 fraction.
 */
public final class ShockwaveDataPacket {
    private static final String VER = "2";
    public static SimpleChannel CHANNEL;

    public enum Kind { SPAWN, UPDATE, DEATH }

    public final Kind kind;
    public final long waveId;
    public final Vec3 origin;
    public final float radius;
    public final float strength;
    public final float temperatureC;
    /** [FINE, HEAVY, ASH, EXOTIC] mg/m³, see {@link ParticulateBucket}. */
    public final float[] particulateBuckets;

    public ShockwaveDataPacket(Kind kind, long waveId, Vec3 origin, float radius,
                                float strength, float temperatureC, float[] particulateBuckets) {
        this.kind = kind;
        this.waveId = waveId;
        this.origin = origin;
        this.radius = radius;
        this.strength = strength;
        this.temperatureC = temperatureC;
        this.particulateBuckets = particulateBuckets;
    }

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Mge.MODID, "shockwave"),
                () -> VER, VER::equals, VER::equals);
        CHANNEL.registerMessage(0, ShockwaveDataPacket.class,
                ShockwaveDataPacket::encode, ShockwaveDataPacket::decode,
                ShockwaveDataPacket::handle);
    }

    private static void encode(ShockwaveDataPacket p, FriendlyByteBuf b) {
        b.writeEnum(p.kind);
        b.writeLong(p.waveId);
        b.writeDouble(p.origin.x); b.writeDouble(p.origin.y); b.writeDouble(p.origin.z);
        b.writeFloat(p.radius);
        b.writeFloat(p.strength);
        b.writeFloat(p.temperatureC);
        // DEATH packets don't need bucket data — the wave's gone either way —
        // but we still write 4 zeros to keep decode() unconditional and simple.
        float[] buckets = p.particulateBuckets != null
                ? p.particulateBuckets : new float[ParticulateBucket.values().length];
        for (int i = 0; i < ParticulateBucket.values().length; i++) {
            b.writeFloat(i < buckets.length ? buckets[i] : 0f);
        }
    }

    private static ShockwaveDataPacket decode(FriendlyByteBuf b) {
        Kind kind = b.readEnum(Kind.class);
        long waveId = b.readLong();
        Vec3 origin = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        float radius = b.readFloat();
        float strength = b.readFloat();
        float temperatureC = b.readFloat();
        float[] buckets = new float[ParticulateBucket.values().length];
        for (int i = 0; i < buckets.length; i++) buckets[i] = b.readFloat();
        return new ShockwaveDataPacket(kind, waveId, origin, radius, strength, temperatureC, buckets);
    }

    private static void handle(ShockwaveDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(p));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ShockwaveDataPacket p) {
        switch (p.kind) {
            case SPAWN, UPDATE -> ShockwaveDistortionRenderer.updateWave(
                    p.waveId, p.origin, p.radius, p.strength, p.temperatureC, p.particulateBuckets);
            case DEATH -> ShockwaveDistortionRenderer.removeWave(p.waveId);
        }
    }

    // ── Send helpers ─────────────────────────────────────────────────────

    /**
     * Builds and sends a SPAWN or UPDATE packet from a live {@link ShockwaveFront}.
     * Used both for the initial spawn packet and every periodic update — see
     * {@code ShockwaveHandler.spawn} and {@code ShockwaveHandler.onServerTick}.
     */
    public static void sendUpdateToNear(ServerLevel level, ShockwaveFront wave, double radius) {
        Kind kind = wave.currentRadius <= 0f ? Kind.SPAWN : Kind.UPDATE;
        send(level, Vec3.atCenterOf(wave.origin), radius,
                new ShockwaveDataPacket(kind, wave.waveId, Vec3.atCenterOf(wave.origin),
                        wave.currentRadius, wave.strength(), wave.spawnTemperatureC,
                        wave.particulateBuckets()));
    }

    /** Sends a DEATH packet so the client removes this wave immediately. */
    public static void sendDeathToNear(ServerLevel level, ShockwaveFront wave, double radius) {
        send(level, Vec3.atCenterOf(wave.origin), radius,
                new ShockwaveDataPacket(Kind.DEATH, wave.waveId, Vec3.atCenterOf(wave.origin),
                        wave.currentRadius, 0f, wave.spawnTemperatureC, null));
    }

    private static void send(ServerLevel level, Vec3 origin, double radius, ShockwaveDataPacket packet) {
        CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(
                        origin.x, origin.y, origin.z, radius * radius,
                        level.dimension())),
                packet);
    }
}
