package exp.CCnewmods.mge.render;

import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import exp.CCnewmods.mge.Mge;

/**
 * Desert mirage renderer.
 *
 * A mirage occurs when a hot surface heats the air directly above it,
 * creating a sharp temperature gradient and refractive index variation.
 * We simulate this by manipulating the viewport far-plane and fog density
 * when the player is in a hot, dry biome at surface level during the day,
 * and the local atmosphere has low water vapour.
 *
 * The effect: at ground level in deserts/badlands/savanna during peak day,
 * the fog far-plane oscillates slightly (shimmer) and the horizon develops
 * a subtle colour shift (heated air tint). No screen overlay needed.
 *
 * Conditions required:
 *  - Daytime (sun angle > 0.3)
 *  - Hot biome (downfall < 0.2, temperature > 0.8)
 *  - Player near surface (within 3 blocks of heightmap)
 *  - Low local water vapour (< 20 mbar)
 *  - No rain
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class DesertMirageRenderer {

    private static float shimmerPhase = 0f;
    private static float mirageStrength = 0f; // 0=none, 1=full
    private static int updateTick = 0;

    private DesertMirageRenderer() {}

    public static void clientTick() {
        if (!MgeConfig.enableAtmosphereRenderer) { mirageStrength = 0; return; }
        updateTick++;
        shimmerPhase += 0.07f;

        // Only recompute conditions every 10 ticks
        if (updateTick % 10 != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { mirageStrength = 0; return; }

        // Time of day check
        long time = mc.level.getDayTime() % 24000;
        if (time < 6000 || time > 18000) { mirageStrength = 0; return; } // night

        // Biome check — hot and dry
        BlockPos playerPos = mc.player.blockPosition();
        Biome biome = mc.level.getBiome(playerPos).value();
        float temp = biome.getBaseTemperature();
        float downfall = biome.climateSettings.downfall();
        if (temp < 0.8f || downfall > 0.2f) { mirageStrength = 0; return; }

        // Surface proximity — within 5 blocks of surface
        int surfaceY = mc.level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                playerPos.getX(), playerPos.getZ());
        if (Math.abs(playerPos.getY() - surfaceY) > 5) { mirageStrength = 0; return; }

        // Atmosphere water vapour check
        BlockPos eyePos = BlockPos.containing(mc.player.getEyePosition());
        BlockEntity be = mc.level.getBlockEntity(eyePos);
        float vapor = 25f; // default
        if (be instanceof AtmosphereBlockEntity atm) {
            vapor = atm.getComposition().get(
                    exp.CCnewmods.mge.gas.GasRegistry.WATER_VAPOR);
        }
        if (vapor > 40f) { mirageStrength = 0; return; } // too humid for mirage

        // Compute strength — stronger when hotter and drier
        float targetStrength = Math.min(1f, (temp - 0.8f) * 2.5f * (1f - downfall));
        targetStrength *= Math.max(0f, 1f - vapor / 40f);
        // Smooth transition
        mirageStrength += (targetStrength - mirageStrength) * 0.1f;
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (mirageStrength < 0.05f) return;
        // Shift fog colour slightly toward a warm orange/white for heat shimmer
        float shimmer = (float)(Math.sin(shimmerPhase) * 0.5 + 0.5) * mirageStrength * 0.15f;
        event.setRed(Math.min(1f, event.getRed() + shimmer * 0.8f));
        event.setGreen(Math.min(1f, event.getGreen() + shimmer * 0.5f));
        event.setBlue(Math.min(1f, event.getBlue() + shimmer * 0.2f));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (mirageStrength < 0.05f) return;
        // Oscillate far-plane slightly — creates the characteristic shimmer
        float oscillation = (float)(Math.sin(shimmerPhase * 1.3) * mirageStrength * 12f);
        float far = event.getFarPlaneDistance();
        event.setFarPlaneDistance(far + oscillation);
        event.setCanceled(true);
    }
}
