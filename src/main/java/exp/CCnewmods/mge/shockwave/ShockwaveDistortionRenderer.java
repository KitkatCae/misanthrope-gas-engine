package exp.CCnewmods.mge.shockwave;

import exp.CCnewmods.mge.Mge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ShockwaveDistortionRenderer {

    private record ClientWave(Vec3 origin, float strength, int maxR,
                               int spawnTick, int[] hold) {}

    private static final List<ClientWave> WAVES = new ArrayList<>();
    private static int clientTick = 0;
    private static float nearMult = 1.0f;

    private ShockwaveDistortionRenderer() {}

    public static void registerWave(Vec3 origin, float strength) {
        int maxR = (int) Math.sqrt(strength / 0.1f) + 1;
        WAVES.add(new ClientWave(origin, strength, maxR, clientTick, new int[]{0}));
    }

    public static void clientTick() {
        clientTick++;
        nearMult = 1.0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        Iterator<ClientWave> it = WAVES.iterator();
        while (it.hasNext()) {
            ClientWave w = it.next();
            int age = clientTick - w.spawnTick();
            if (age > w.maxR()) { it.remove(); continue; }
            double dist = eye.distanceTo(w.origin());
            if (Math.abs(dist - age) < 1.5) {
                float s = w.strength() / Math.max(1, age * age);
                nearMult = Math.max(0.05f, 1f - Math.min(0.95f, s * 0.4f));
                w.hold()[0] = 3;
            } else if (w.hold()[0] > 0) {
                w.hold()[0]--;
                nearMult = Math.min(nearMult, 0.4f);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (nearMult >= 1.0f) return;
        event.setNearPlaneDistance(event.getNearPlaneDistance() * nearMult);
        event.setCanceled(true);
    }
}
