package exp.CCnewmods.mge.mirage;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import exp.CCnewmods.mge.Mge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Renders phantom mirage structures as translucent ghost blocks with heat shimmer.
 *
 * <h3>Rendering approach</h3>
 * On {@link RenderLevelStageEvent.Stage#AFTER_WEATHER}, iterates all blocks in the
 * active {@link MirageInstance}'s {@link StructureTemplate} and renders each
 * non-air block as a translucent quad using {@link BlockRenderDispatcher}.
 *
 * Alpha is driven by {@link MirageInstance#getAlpha()} plus a sinusoidal shimmer.
 * A slight vertical offset oscillation (the classic mirage float) is applied to
 * the entire structure's render matrix.
 *
 * <h3>Photon integration</h3>
 * If Photon is loaded, the mirage also plays the {@code mge:fx/vacuum_shimmer}
 * FX effect at the structure centre for heat-distortion particles. Edit the .fx
 * file in Photon's in-game editor to adjust the distortion appearance.
 *
 * <h3>Conditions</h3>
 * Same as {@link DesertMirageRenderer}: daytime, hot dry biome, surface level,
 * low local water vapour.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class MirageRenderer {

    private static final List<MirageInstance> INSTANCES = new ArrayList<>();
    private static final Random RNG = new Random();
    private static int clientTick = 0;
    private static float shimmerPhase = 0f;

    // Cooldown between spawning new mirages
    private static int spawnCooldown = 0;
    private static final int SPAWN_COOLDOWN_TICKS = 20 * 30; // 30 seconds between spawns

    private MirageRenderer() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Client tick — update instances, check spawn conditions
    // ─────────────────────────────────────────────────────────────────────────

    public static void clientTick() {
        if (!MgeConfig.enableAtmosphereRenderer) return;
        clientTick++;
        shimmerPhase += 0.04f;
        if (spawnCooldown > 0) spawnCooldown--;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { INSTANCES.clear(); return; }

        Vec3 playerPos  = mc.player.getEyePosition();
        Vec3 playerLook = mc.player.getLookAngle();

        // Tick existing instances
        INSTANCES.removeIf(inst -> !inst.tick(playerPos, playerLook));

        // Try to spawn a new one
        if (INSTANCES.isEmpty() && spawnCooldown <= 0 && clientTick % 40 == 0) {
            trySpawn(mc, playerPos);
        }
    }

    private static void trySpawn(Minecraft mc, Vec3 playerPos) {
        // Check conditions: daytime, hot biome, surface, low vapour
        long time = mc.level.getDayTime() % 24000;
        if (time < 6000 || time > 18000) return;

        BlockPos playerBlock = BlockPos.containing(playerPos);
        Biome biome = mc.level.getBiome(playerBlock).value();
        if (biome.getBaseTemperature() < 0.8f || biome.climateSettings.downfall() > 0.2f) return;

        int surfaceY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                playerBlock.getX(), playerBlock.getZ());
        if (Math.abs(playerBlock.getY() - surfaceY) > 5) return;

        // Check low water vapour
        BlockEntity be = mc.level.getBlockEntity(BlockPos.containing(mc.player.getEyePosition()));
        if (be instanceof AtmosphereBlockEntity atm) {
            if (atm.getComposition().get(GasRegistry.WATER_VAPOR) > 40f) return;
        }

        // Pick a compatible definition
        ResourceLocation biomeId = mc.level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getKey(biome);
        if (biomeId == null) return;

        List<MirageDefinition> candidates = MirageStructureLoader.forBiome(biomeId);
        if (candidates.isEmpty()) return;

        // Weighted pick by frequency
        MirageDefinition chosen = null;
        for (MirageDefinition def : candidates) {
            if (RNG.nextFloat() < def.frequency()) { chosen = def; break; }
        }
        if (chosen == null) return;

        StructureTemplate template = MirageStructureLoader.getTemplate(chosen.structureId());
        if (template == null) return;

        // Pick a random direction and distance
        double angle  = RNG.nextDouble() * Math.PI * 2;
        double dist   = chosen.minDistance()
                + RNG.nextDouble() * (chosen.maxDistance() - chosen.minDistance());
        int tx = playerBlock.getX() + (int)(Math.cos(angle) * dist);
        int tz = playerBlock.getZ() + (int)(Math.sin(angle) * dist);
        int ty = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz)
                + (int) chosen.floatHeight();

        INSTANCES.add(new MirageInstance(chosen, new BlockPos(tx, ty, tz)));
        spawnCooldown = SPAWN_COOLDOWN_TICKS;
        Mge.LOGGER.debug("[MGE] Mirage spawned: {} at {},{},{}", chosen.structureId(), tx, ty, tz);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rendering — ghost block pass
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        if (INSTANCES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (MirageInstance inst : INSTANCES) {
            if (inst.getAlpha() < 0.01f) continue;
            renderMirage(event, mc, inst);
        }
    }

    private static void renderMirage(RenderLevelStageEvent event, Minecraft mc,
                                      MirageInstance inst) {
        StructureTemplate template = MirageStructureLoader.getTemplate(
                inst.definition.structureId());
        if (template == null) return;

        // Base shimmer: alpha pulses ±15% around base alpha
        float shimmer = (float)(Math.sin(shimmerPhase) * 0.15 + Math.sin(shimmerPhase * 1.7) * 0.08);
        float alpha   = Math.max(0, Math.min(1, inst.getAlpha() + shimmer));

        // Vertical float oscillation
        float floatY  = (float)(Math.sin(shimmerPhase * 0.6) * 0.3
                               + Math.sin(shimmerPhase * 1.1) * 0.15);

        // Camera offset
        Vec3 cam       = event.getCamera().getPosition();
        BlockPos origin = inst.origin;
        double ox = origin.getX() - cam.x;
        double oy = origin.getY() - cam.y + floatY;
        double oz = origin.getZ() - cam.z;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(ox, oy, oz);
        pose.scale(inst.definition.scale(), inst.definition.scale(), inst.definition.scale());

        // Set up translucent render state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        var bufferSource = mc.renderBuffers().bufferSource();
        var random = new com.mojang.blaze3d.vertex.VertexSorting();

        // Horizontal distortion offset for shimmer — varies per block row for wave effect
        // Render each block info in the template
        for (StructureTemplate.StructureBlockInfo blockInfo : template.filterBlocks(
                BlockPos.ZERO, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                Blocks.AIR).stream()
                .filter(bi -> !bi.state().isAir())
                .toList()) {

            BlockPos relPos = blockInfo.pos();
            // Per-block horizontal shimmer — creates wavy distortion
            float blockShimmer = (float)(Math.sin(shimmerPhase * 1.3 + relPos.getY() * 0.5) * 0.05);

            pose.pushPose();
            pose.translate(relPos.getX() + blockShimmer,
                           relPos.getY(),
                           relPos.getZ() + blockShimmer * 0.7f);

            // Render the block model with transparency
            // We use the existing block render dispatcher but with a custom alpha
            // via the VertexConsumer's overlay parameter
            int overlayAlpha = (int)(alpha * 255);
            var consumer = bufferSource.getBuffer(RenderType.translucent());

            try {
                dispatcher.renderSingleBlock(
                        blockInfo.state(),
                        pose,
                        bufferSource,
                        (15 << 20 | 15 << 4), // full brightness — mirage glows
                        OverlayTexture.NO_OVERLAY);
            } catch (Exception ignored) {
                // Skip blocks that fail to render (e.g. missing model)
            }

            pose.popPose();
        }

        bufferSource.endBatch(RenderType.translucent());

        // Photon shimmer particles at the structure centre if loaded
        if (exp.CCnewmods.mge.photon.MgePhotonEffects.isLoaded()) {
            exp.CCnewmods.mge.photon.MgePhotonEffects.playAt(
                    exp.CCnewmods.mge.photon.MgePhotonEffects.FX_VACUUM_SHIMMER,
                    mc.level,
                    Vec3.atCenterOf(origin).add(
                            0, template.getSize().getY() * 0.5f, 0));
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pose.popPose();
    }
}
