package exp.CCnewmods.mge.render;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side renderer that modifies fog colour and density based on the gas and
 * particulate composition of the atmosphere block at the camera's eye position.
 *
 * <p>Blends gas tints (weighted by concentration × alpha) and particulate tints
 * (weighted by mg/m³ × alpha) into a single RGBA fog colour. Opaque particulates
 * (smoke, sand, ash) reduce far-plane distance aggressively; invisible gases (N₂, O₂)
 * have no visual effect. Ice crystals add a cold blue-white tint and slightly
 * reduce visibility.</p>
 *
 * <p>The colour cache is refreshed every {@link #UPDATE_INTERVAL_TICKS} client ticks
 * (not every frame) to keep overhead negligible.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AtmosphereRenderer {

    private static final int UPDATE_INTERVAL_TICKS = 5;

    // Cached per-frame values — updated from block entity, applied every render frame
    private static float cachedR = 0f;
    private static float cachedG = 0f;
    private static float cachedB = 0f;
    /** 0 = clear air (no tint, no fog change). 1 = fully opaque. */
    private static float cachedA = 0f;
    /** Additional visibility reduction from particulates (0–1). Tightens far plane. */
    private static float cachedParticulateOpacity = 0f;

    private static int tickCounter = 0;

    private AtmosphereRenderer() {}

    // -------------------------------------------------------------------------
    // Client tick — refresh cache
    // -------------------------------------------------------------------------

    public static void clientTick() {
        if (++tickCounter % UPDATE_INTERVAL_TICKS != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            cachedR = cachedG = cachedB = cachedA = cachedParticulateOpacity = 0f;
            return;
        }

        BlockPos eyePos = BlockPos.containing(mc.player.getEyePosition());
        BlockGetter level = mc.level;
        BlockEntity be = level.getBlockEntity(eyePos);

        if (!(be instanceof AtmosphereBlockEntity atm)) {
            cachedR = cachedG = cachedB = cachedA = cachedParticulateOpacity = 0f;
            return;
        }

        blendColours(atm.getComposition(), atm.getParticulates());
    }

    // -------------------------------------------------------------------------
    // Colour blending
    // -------------------------------------------------------------------------

    private static void blendColours(GasComposition gases, ParticulateComposition particulates) {
        float blendR = 0f, blendG = 0f, blendB = 0f;
        float totalGasWeight = 0f;
        float totalPartWeight = 0f;
        float partOpacity = 0f;

        float totalPressure = gases.totalPressure();

        // ── Gas tints ──────────────────────────────────────────────────────────
        if (totalPressure > 0f) {
            for (String key : gases.getTag().getAllKeys()) {
                Gas gas = GasRegistry.get(key).orElse(null);
                if (gas == null) continue;
                int argb  = gas.properties().colorARGB();
                int alpha = (argb >> 24) & 0xFF;
                if (alpha == 0) continue;

                float conc   = gases.get(key) / totalPressure;
                float weight = conc * (alpha / 255f);

                blendR += ((argb >> 16) & 0xFF) * weight;
                blendG += ((argb >> 8)  & 0xFF) * weight;
                blendB += ( argb        & 0xFF) * weight;
                totalGasWeight += weight;
            }
        }

        // ── Particulate tints ─────────────────────────────────────────────────
        // Particulates contribute strongly to opacity. Reference scale:
        //   50 mg/m³ PM2.5 = moderate haze; 500 mg/m³ = severe opacity.
        for (ParticulateType type : ParticulateType.values()) {
            float mgM3 = particulates.get(type);
            if (mgM3 <= 0f) continue;

            int argb  = type.colorARGB;
            int alpha = (argb >> 24) & 0xFF;
            if (alpha == 0) continue;

            // Opacity grows logarithmically with concentration — realistic Beer-Lambert
            float opacity = (float) Math.min(1.0, Math.log1p(mgM3 / 50.0) / Math.log1p(10.0));
            float weight  = opacity * (alpha / 255f);

            blendR += ((argb >> 16) & 0xFF) * weight;
            blendG += ((argb >> 8)  & 0xFF) * weight;
            blendB += ( argb        & 0xFF) * weight;
            totalPartWeight += weight;
            partOpacity = Math.max(partOpacity, opacity * (alpha / 255f));
        }

        float totalWeight = totalGasWeight + totalPartWeight;

        if (totalWeight > 0f) {
            cachedR = blendR / (totalWeight * 255f);
            cachedG = blendG / (totalWeight * 255f);
            cachedB = blendB / (totalWeight * 255f);
            cachedA = Math.min(1f, totalWeight);
        } else {
            cachedR = cachedG = cachedB = cachedA = 0f;
        }

        cachedParticulateOpacity = partOpacity;
    }

    // -------------------------------------------------------------------------
    // Forge render events
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (cachedA <= 0f) return;
        float t = cachedA;
        event.setRed(  lerp(event.getRed(),   cachedR, t));
        event.setGreen(lerp(event.getGreen(), cachedG, t));
        event.setBlue( lerp(event.getBlue(),  cachedB, t));
    }

    @SubscribeEvent
    public static void onFogDensity(ViewportEvent.RenderFog event) {
        // Gas opacity tightens fog moderately
        if (cachedA > 0.3f) {
            float gasFactor = 1.0f - (cachedA - 0.3f) * 0.8f;
            event.setFarPlaneDistance(event.getFarPlaneDistance() * Math.max(0.2f, gasFactor));
        }

        // Particulate opacity tightens fog much more aggressively
        // Sand/ash at 500 mg/m³ should reduce visibility to near zero
        if (cachedParticulateOpacity > 0.05f) {
            float partFactor = 1.0f - cachedParticulateOpacity * 0.95f;
            float newFar     = event.getFarPlaneDistance() * Math.max(0.05f, partFactor);
            float newNear    = event.getNearPlaneDistance() * Math.max(0.1f, 1.0f - cachedParticulateOpacity * 0.5f);
            event.setFarPlaneDistance(newFar);
            event.setNearPlaneDistance(newNear);
            event.setCanceled(true); // override vanilla fog calculation entirely
        } else if (cachedA > 0.3f) {
            event.setCanceled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Util
    // -------------------------------------------------------------------------

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
