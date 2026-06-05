package exp.CCnewmods.mge.render;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.photon.MgePhotonEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side renderer that modifies fog colour and density based on the gas
 * and particulate composition at the camera's eye position.
 *
 * ── Data source ───────────────────────────────────────────────────────────────
 * Reads directly from {@link EnvironmentGrid} — no block entity lookup needed.
 * The grid's client-side chunk capability is populated by the normal chunk sync
 * packet, same as any other Forge chunk capability.
 *
 * ── Visual model ──────────────────────────────────────────────────────────────
 * Gas tints:        each gas with a non-zero colorARGB contributes a weighted
 *                   tint proportional to (partial_pressure / total_pressure) × alpha.
 *                   Invisible gases (N₂, O₂, Ar) have alpha=0 and contribute nothing.
 *
 * Particulate fog:  opacity grows logarithmically with mg/m³ (Beer-Lambert).
 *                   50 mg/m³ = light haze, 500 mg/m³ = near-zero visibility.
 *                   Opaque types (ASH_CLOUD, SMOKE_AEROSOL) reduce far plane hard.
 *
 * Photon particles: when Photon is loaded, visible particulate types above their
 *                   visual threshold spawn looping Photon FX at the eye position.
 *                   Each type has its own FX file in assets/mge/fx/particulate/.
 *                   FX are played at most once per UPDATE_INTERVAL_TICKS per type
 *                   to avoid spam.
 *
 * ── Performance ───────────────────────────────────────────────────────────────
 * The cache is refreshed every {@link #UPDATE_INTERVAL_TICKS} client ticks.
 * Grid reads are array lookups — no NBT, no block entity, negligible cost.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AtmosphereRenderer {

    private static final int UPDATE_INTERVAL_TICKS = 5;

    // ── Fog colour cache ──────────────────────────────────────────────────────

    private static float cachedR = 0f;
    private static float cachedG = 0f;
    private static float cachedB = 0f;
    /** 0 = clear air (no tint). 1 = fully saturated tint. */
    private static float cachedA = 0f;
    /** Visibility reduction from opaque particulates [0,1]. */
    private static float cachedParticulateOpacity = 0f;

    // ── Photon particulate FX tracking ───────────────────────────────────────

    /**
     * Per-ParticulateType threshold above which the Photon FX for that type fires.
     * All types below this in mg/m³ skip FX to avoid trivial low-level effects.
     */
    private static final float PHOTON_PARTICULATE_THRESHOLD = 30f;

    /** Tracks which particulate FX were playing last update tick. */
    private static final boolean[] lastFxActive = new boolean[ParticulateType.values().length];

    // Photon FX resource locations for each significant particulate type.
    // Stub .fx files must exist at assets/mge/fx/particulate/<name>.fx
    private static final ResourceLocation[] PARTICULATE_FX;
    static {
        ParticulateType[] types = ParticulateType.values();
        PARTICULATE_FX = new ResourceLocation[types.length];
        for (int i = 0; i < types.length; i++) {
            PARTICULATE_FX[i] = new ResourceLocation(Mge.MODID,
                    "fx/particulate/" + types[i].id);
        }
    }

    private static int tickCounter = 0;

    private AtmosphereRenderer() {}

    // ── Client tick ───────────────────────────────────────────────────────────

    public static void clientTick() {
        if (++tickCounter % UPDATE_INTERVAL_TICKS != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        BlockPos eyePos = BlockPos.containing(mc.player.getEyePosition());
        Level level = mc.level;

        // Read gas composition from the grid — returns dimension default if
        // the section has never been written to (i.e. clean standard air).
        GasComposition gases = EnvironmentGrid.getComposition(level, eyePos);

        // Particulates are still stored in AtmosphereBlockEntity for now (Phase 1).
        // Once particulates are migrated to EnvironmentSection, replace this lookup.
        ParticulateComposition particulates = getParticulatesLegacy(level, eyePos);

        blendColours(gases, particulates);
        tickPhotonEffects(level, eyePos, particulates);
    }

    // ── Colour blending ───────────────────────────────────────────────────────

    private static void blendColours(GasComposition gases, ParticulateComposition particulates) {
        float blendR = 0f, blendG = 0f, blendB = 0f;
        float totalWeight = 0f;
        float partOpacity = 0f;

        float totalPressure = gases.totalPressure();

        // ── Gas tints ─────────────────────────────────────────────────────────
        if (totalPressure > 0f) {
            for (Gas gas : GasRegistry.all()) {
                int argb  = gas.properties().colorARGB();
                int alpha = (argb >> 24) & 0xFF;
                if (alpha == 0) continue; // invisible gas (N₂, O₂, Ar...)

                float mbar   = gases.get(gas);
                if (mbar <= 0f) continue;

                float conc   = mbar / totalPressure;
                float weight = conc * (alpha / 255f);

                blendR       += ((argb >> 16) & 0xFF) * weight;
                blendG       += ((argb >>  8) & 0xFF) * weight;
                blendB       += ( argb        & 0xFF) * weight;
                totalWeight  += weight;
            }
        }

        // ── Particulate tints ─────────────────────────────────────────────────
        // Beer-Lambert opacity: logarithmic with concentration.
        // 50 mg/m³ = light haze; 500 mg/m³ = severe.
        for (ParticulateType type : ParticulateType.values()) {
            float mgM3 = particulates.get(type);
            if (mgM3 <= 0f) continue;

            int argb  = type.colorARGB;
            int alpha = (argb >> 24) & 0xFF;
            if (alpha == 0) continue;

            float opacity = (float) Math.min(1.0, Math.log1p(mgM3 / 50.0) / Math.log1p(10.0));
            float weight  = opacity * (alpha / 255f);

            blendR      += ((argb >> 16) & 0xFF) * weight;
            blendG      += ((argb >>  8) & 0xFF) * weight;
            blendB      += ( argb        & 0xFF) * weight;
            totalWeight += weight;
            partOpacity  = Math.max(partOpacity, opacity * (alpha / 255f));
        }

        if (totalWeight > 0f) {
            cachedR = blendR / (totalWeight * 255f);
            cachedG = blendG / (totalWeight * 255f);
            cachedB = blendB / (totalWeight * 255f);
            // Alpha is total visual weight, capped — clean air = 0, toxic soup = ~1
            cachedA = Math.min(1f, totalWeight);
        } else {
            reset();
            return;
        }

        cachedParticulateOpacity = partOpacity;
    }

    // ── Photon particulate effects ────────────────────────────────────────────

    /**
     * For each particulate type above threshold, plays or continues its Photon FX.
     * FX are looping (Photon handles restart) — we just ensure play() is called
     * once per update interval when the type is active.
     */
    private static void tickPhotonEffects(Level level, BlockPos eyePos,
                                           ParticulateComposition particulates) {
        if (!MgePhotonEffects.isLoaded()) return;

        ParticulateType[] types = ParticulateType.values();
        for (int i = 0; i < types.length; i++) {
            ParticulateType type  = types[i];
            float mgM3 = particulates.get(type);
            boolean shouldBeActive = mgM3 >= PHOTON_PARTICULATE_THRESHOLD;

            if (shouldBeActive && !lastFxActive[i]) {
                // Concentration just crossed threshold — start the FX
                MgePhotonEffects.play(PARTICULATE_FX[i], level, eyePos);
            }
            // If it was already active, Photon's loop handles continuation.
            // If it just went below threshold, Photon's FX will expire naturally.
            lastFxActive[i] = shouldBeActive;
        }

        // Smoke aerosol above serious threshold: also play detonation smoke FX
        float smokeLevel = particulates.get(ParticulateType.SMOKE_AEROSOL);
        if (smokeLevel > 200f) {
            MgePhotonEffects.play(MgePhotonEffects.FX_COAL_DUST, level, eyePos);
        }
    }

    // ── Forge render events ───────────────────────────────────────────────────

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (cachedA <= 0.02f) return; // skip if negligible tint
        float t = cachedA;
        event.setRed(  lerp(event.getRed(),   cachedR, t));
        event.setGreen(lerp(event.getGreen(), cachedG, t));
        event.setBlue( lerp(event.getBlue(),  cachedB, t));
    }

    @SubscribeEvent
    public static void onFogDensity(ViewportEvent.RenderFog event) {
        boolean cancelled = false;

        // Gas opacity: moderate fog tightening
        if (cachedA > 0.1f) {
            float gasFactor = 1.0f - (cachedA - 0.1f) * 0.7f;
            event.setFarPlaneDistance(event.getFarPlaneDistance() * Math.max(0.15f, gasFactor));
            cancelled = true;
        }

        // Particulate opacity: aggressive fog tightening — sand/ash kills visibility
        if (cachedParticulateOpacity > 0.05f) {
            float partFactor = 1.0f - cachedParticulateOpacity * 0.95f;
            float newFar  = event.getFarPlaneDistance() * Math.max(0.05f, partFactor);
            float newNear = event.getNearPlaneDistance()
                    * Math.max(0.1f, 1.0f - cachedParticulateOpacity * 0.5f);
            event.setFarPlaneDistance(newFar);
            event.setNearPlaneDistance(newNear);
            cancelled = true;
        }

        if (cancelled) event.setCanceled(true);
    }

    // ── Particulate legacy bridge ─────────────────────────────────────────────

    /**
     * Reads particulates from AtmosphereBlockEntity until Phase 2 migration
     * moves particulates into EnvironmentSection.
     * Returns empty composition if no atmosphere block is present.
     */
    private static ParticulateComposition getParticulatesLegacy(Level level, BlockPos pos) {
        return EnvironmentGrid.getParticulates(level, pos);
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private static void reset() {
        cachedR = cachedG = cachedB = cachedA = cachedParticulateOpacity = 0f;
        java.util.Arrays.fill(lastFxActive, false);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
