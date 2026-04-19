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

public final class ShockwaveDataPacket {
    private static final String VER = "1";
    public static SimpleChannel CHANNEL;

    public final Vec3 origin;
    public final float strength;

    public ShockwaveDataPacket(Vec3 origin, float strength) {
        this.origin = origin; this.strength = strength;
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
        b.writeDouble(p.origin.x); b.writeDouble(p.origin.y); b.writeDouble(p.origin.z);
        b.writeFloat(p.strength);
    }
    private static ShockwaveDataPacket decode(FriendlyByteBuf b) {
        return new ShockwaveDataPacket(new Vec3(b.readDouble(),b.readDouble(),b.readDouble()), b.readFloat());
    }
    private static void handle(ShockwaveDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(p));
        ctx.get().setPacketHandled(true);
    }
    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ShockwaveDataPacket p) {
        ShockwaveDistortionRenderer.registerWave(p.origin, p.strength);
    }

    public static void sendToNear(ServerLevel level, Vec3 origin, float strength, double radius) {
        CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(
                        origin.x, origin.y, origin.z, radius * radius,
                        level.dimension())),
                new ShockwaveDataPacket(origin, strength));
    }
}
