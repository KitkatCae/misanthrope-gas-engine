package exp.CCnewmods.mge.shockwave;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.Map;

/**
 * Drives the shockwave post-process shader every frame.
 *
 * <p>Replaces the old fog-plane hack entirely. For each active wave (see
 * {@link ShockwaveDistortionRenderer#activeWaves()}), runs:
 * <ol>
 *   <li>{@code shockwave_distort} — additive screen-space refraction +
 *       thermal/exotic rim-glow, one pass per wave.</li>
 *   <li>{@code shockwave_desaturate} — desaturation/whitening near the
 *       shell driven by ash/fine particulate load, also one pass per wave,
 *       run AFTER every wave's distort pass has been applied. See
 *       shockwave_desaturate.fsh's header comment for why this can't be a
 *       single combined multi-wave pass (Mojang's Uniform wrapper caps at
 *       4 components — no true GLSL arrays through this JSON-driven path).</li>
 * </ol>
 *
 * <p>Hooked on {@code RenderLevelStageEvent.Stage.AFTER_PARTICLES} — after
 * the world, entities, and particles are all drawn, but before the GUI.
 * That's the right point to sample the (almost) fully composed scene.
 *
 * <p>Both real camera matrices ({@code InvViewProjMat} for unprojecting
 * depth, {@code RealViewProjMat} for forward-projecting the refraction
 * sample point) are built fresh every frame from the event's own
 * {@code getProjectionMatrix()} plus the camera's rotation — post-process
 * program JSONs do NOT get a usable camera view/projection automatically,
 * unlike core (block/entity) shaders.
 *
 * <p>Implements {@link ResourceManagerReloadListener} and registers itself
 * via {@code RegisterClientReloadListenersEvent} so both {@link EffectInstance}s
 * are properly disposed and reloaded on F3+T resource pack reloads, rather
 * than holding onto stale GL program handles from before the reload.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ShockwavePostProcessor implements ResourceManagerReloadListener {

    private static EffectInstance distortShader;
    private static EffectInstance desaturateShader;

    /** Ping-pong scratch target — same size as the main target, holds the
     *  result of each pass so the next pass can read it as its source. */
    private static RenderTarget scratchTarget;

    private static boolean loadFailed = false;

    private ShockwavePostProcessor() {}

    private static final ShockwavePostProcessor INSTANCE = new ShockwavePostProcessor();

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(INSTANCE);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // Dispose old GL program handles before rebuilding — EffectInstance
        // implements AutoCloseable (close() releases the linked program),
        // so we don't leak shader objects across repeated F3+T reloads.
        if (distortShader != null) { distortShader.close(); distortShader = null; }
        if (desaturateShader != null) { desaturateShader.close(); desaturateShader = null; }
        loadFailed = false;
        load(resourceManager);
    }

    private static void load(ResourceManager resourceManager) {
        try {
            distortShader = new EffectInstance(resourceManager, "shockwave_distort");
            desaturateShader = new EffectInstance(resourceManager, "shockwave_desaturate");
        } catch (IOException e) {
            Mge.LOGGER.error("[MGE] Failed to load shockwave shaders — distortion effect disabled.", e);
            loadFailed = true;
        }
    }

    private static void ensureLoaded() {
        // Fallback for the (normally unreachable) case where a wave is
        // active before the first RegisterClientReloadListenersEvent-driven
        // load has happened — the explicit reload-listener path above is
        // the primary loading mechanism now, this just guards against
        // calling order surprises.
        if (distortShader != null || loadFailed) return;
        load(Minecraft.getInstance().getResourceManager());
    }

    private static void ensureScratchTarget(int width, int height) {
        if (scratchTarget != null
                && scratchTarget.width == width && scratchTarget.height == height) return;
        if (scratchTarget != null) scratchTarget.destroyBuffers();
        scratchTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!MgeConfig.enableGasEffects) return; // shares the master gas-effects toggle

        Map<Long, ShockwaveDistortionRenderer.ClientWave> waves = ShockwaveDistortionRenderer.activeWaves();
        if (waves.isEmpty()) return;

        ensureLoaded();
        if (loadFailed) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        RenderTarget mainTarget = mc.getMainRenderTarget();
        ensureScratchTarget(mainTarget.width, mainTarget.height);
        // The scratch target needs the SAME depth buffer as the main target
        // for every pass — depth never changes between passes, only color
        // does, and we need real scene depth for the world-position
        // reconstruction every single pass.
        scratchTarget.copyDepthFrom(mainTarget);

        Vector3f camPos = toVector3f(event.getCamera().getPosition());
        Matrix4f viewRotation = new Matrix4f().rotation(event.getCamera().rotation());
        Matrix4f realProj = event.getProjectionMatrix();
        Matrix4f realViewProj = new Matrix4f(realProj).mul(viewRotation);
        Matrix4f invViewProj = new Matrix4f(realViewProj).invert();

        float time = (mc.level.getGameTime() % 20) / 20.0f
                + mc.getPartialTick() / 20.0f; // loops every real-world second-ish

        RenderTarget readFrom = mainTarget;
        RenderTarget writeTo = scratchTarget;

        // ── Pass 1: additive refraction + rim-glow, once per wave ──────
        for (var wave : waves.values()) {
            runDistortPass(wave, readFrom, writeTo, camPos, invViewProj, realViewProj, time);
            RenderTarget tmp = readFrom; readFrom = writeTo; writeTo = tmp;
        }

        // ── Pass 2: desaturation/whitening, once per wave ───────────────
        int desatLimit = MgeConfig.shockwaveMaxDesaturationWaves;
        int desatCount = 0;
        for (var wave : waves.values()) {
            if (desatCount++ >= desatLimit) break;
            runDesaturatePass(wave, readFrom, writeTo, camPos, invViewProj, time);
            RenderTarget tmp = readFrom; readFrom = writeTo; writeTo = tmp;
        }

        // If the final result ended up in the scratch target, blit it back
        // onto the main target — every subsequent stage (GUI etc.) reads
        // from the main target, not our scratch one.
        if (readFrom == scratchTarget) {
            mainTarget.bindWrite(false);
            blitColorOnly(scratchTarget, mainTarget);
        }

        mainTarget.bindWrite(false);
    }

    private static void runDistortPass(ShockwaveDistortionRenderer.ClientWave wave,
                                        RenderTarget src, RenderTarget dst,
                                        Vector3f camPos, Matrix4f invViewProj,
                                        Matrix4f realViewProj, float time) {
        Vector3f originRel = toVector3f(wave.origin()).sub(camPos);

        RenderSystem.depthMask(false);

        distortShader.setSampler("DiffuseSampler", src::getColorTextureId);
        distortShader.setSampler("DiffuseDepthSampler", src::getDepthTextureId);
        distortShader.safeGetUniform("ProjMat").set(orthoMatrixFor(dst));
        distortShader.safeGetUniform("InvViewProjMat").set(invViewProj);
        distortShader.safeGetUniform("RealViewProjMat").set(realViewProj);
        distortShader.safeGetUniform("OutSize").set((float) dst.width, (float) dst.height);
        distortShader.safeGetUniform("Time").set(time);
        distortShader.safeGetUniform("WaveOriginRel").set(originRel);
        distortShader.safeGetUniform("WaveRadius").set(wave.radius());
        distortShader.safeGetUniform("WaveStrength").set(wave.strength());
        distortShader.safeGetUniform("WaveTempC").set(wave.temperatureC());
        float[] b = wave.particulateBuckets();
        distortShader.safeGetUniform("WaveBuckets").set(b[0], b[1], b[2], b[3]);

        // The previous pass's color is what we're refracting; carry it
        // forward unmodified outside the wave's shell band by first
        // copying src -> dst, then additively blending the distortion
        // shader's delta on top (its fragment shader outputs vec4(0)
        // outside the shell, so additive blending leaves the copy intact).
        blitColorOnly(src, dst);

        dst.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        distortShader.apply();
        drawFullscreenQuad(dst);
        distortShader.clear();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static void runDesaturatePass(ShockwaveDistortionRenderer.ClientWave wave,
                                           RenderTarget src, RenderTarget dst,
                                           Vector3f camPos, Matrix4f invViewProj, float time) {
        Vector3f originRel = toVector3f(wave.origin()).sub(camPos);

        RenderSystem.depthMask(false);

        desaturateShader.setSampler("DiffuseSampler", src::getColorTextureId);
        desaturateShader.setSampler("DiffuseDepthSampler", src::getDepthTextureId);
        desaturateShader.safeGetUniform("ProjMat").set(orthoMatrixFor(dst));
        desaturateShader.safeGetUniform("InvViewProjMat").set(invViewProj);
        desaturateShader.safeGetUniform("OutSize").set((float) dst.width, (float) dst.height);
        desaturateShader.safeGetUniform("Time").set(time);
        desaturateShader.safeGetUniform("WaveOriginRel").set(originRel);
        desaturateShader.safeGetUniform("WaveRadius").set(wave.radius());
        float[] b = wave.particulateBuckets();
        desaturateShader.safeGetUniform("WaveBuckets").set(b[0], b[1], b[2], b[3]);

        // This shader samples src itself (DiffuseSampler) and computes the
        // final color directly, so it's a straight overwrite — no blending,
        // no pre-copy needed like the additive pass above.
        dst.bindWrite(true);
        RenderSystem.disableBlend();
        desaturateShader.apply();
        drawFullscreenQuad(dst);
        desaturateShader.clear();
        RenderSystem.depthMask(true);
    }

    /** Copies only the color attachment from src to dst, leaving dst's
     *  depth buffer (already shared via copyDepthFrom) untouched.
     *
     *  Explicitly disables blending first: {@code blitToScreen} draws its
     *  source through an internal blit shader rather than doing a raw
     *  framebuffer copy (confirmed via decompile — RenderTarget has a
     *  {@code blitShader} field), and later MC versions actually rename
     *  this method to {@code blitAndBlendToScreen}, implying it can blend
     *  against whatever's already in the destination depending on current
     *  GL state. We want a guaranteed clean overwrite here, not a blend
     *  that depends on whatever blend mode happened to be active. */
    private static void blitColorOnly(RenderTarget src, RenderTarget dst) {
        RenderSystem.disableBlend();
        src.bindRead();
        dst.bindWrite(false);
        src.blitToScreen(dst.width, dst.height, false);
    }

    /** Draws a single fullscreen-quad triangle-strip — same vertex layout
     *  every post shader in this mod's pipeline expects (see shockwave.vsh).
     *
     *  Mirrors exactly what {@code PostPass.process()} does internally
     *  (confirmed via decompile) since we're driving {@code EffectInstance}
     *  directly rather than through the static {@code PostChain}/{@code PostPass}
     *  pipeline — that pipeline doesn't support dynamic per-frame uniforms
     *  for an arbitrary number of waves the way we need here. */
    private static void drawFullscreenQuad(RenderTarget target) {
        var tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        var buffer = tesselator.getBuilder();
        buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION);
        buffer.vertex(0.0,            0.0,             0.0).endVertex();
        buffer.vertex(target.width,   0.0,             0.0).endVertex();
        buffer.vertex(target.width,   target.height,   0.0).endVertex();
        buffer.vertex(0.0,            target.height,   0.0).endVertex();
        com.mojang.blaze3d.vertex.BufferUploader.draw(buffer.end());
    }

    /** Builds the orthographic projection matrix matching the target's
     *  pixel dimensions — same construction {@code PostPass} uses for its
     *  {@code ProjMat} uniform, sized fresh per-target since our ping-pong
     *  scratch target and the main target may have been created/resized
     *  independently. */
    private static Matrix4f orthoMatrixFor(RenderTarget target) {
        return new Matrix4f().setOrtho(0.0f, target.width, target.height, 0.0f, 0.1f, 1000.0f);
    }

    private static Vector3f toVector3f(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        ShockwaveDistortionRenderer.clear();
    }
}
