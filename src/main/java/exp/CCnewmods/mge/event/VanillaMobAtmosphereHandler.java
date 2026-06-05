package exp.CCnewmods.mge.event;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.warden.Warden;
import cz.maxtechnik.ntrials.entity.WindChargeProjectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Atmospheric effects for vanilla mobs that need custom chemical profiles beyond
 * what {@link BreathWeaponHandler} already covers.
 *
 * <h3>Covered entities</h3>
 * <ul>
 *   <li><b>Warden</b> — passive sonic-frequency vibration off-gassing; sonic boom attack
 *       creates a directional near-vacuum channel along the attack vector.</li>
 *   <li><b>Breeze / Wind Charge</b> — wind-charge impact creates a rotational vortex
 *       pattern of gas displacement rather than a simple radial burst.</li>
 *   <li><b>Lightning bolt</b> — plasma channel flash-vaporises N₂/O₂ into NO and O₃;
 *       the resulting micro-vacuum draws surrounding air inward with a shockwave.</li>
 *   <li><b>Wither Boss</b> — continuous wither miasma + soul smoke tick emission;
 *       skull impacts handled by {@link BreathWeaponHandler}; death burst handled
 *       by {@link MobDeathAtmosphereHandler}.</li>
 *   <li><b>Wither Skeleton</b> — passive wither miasma aura while alive.</li>
 *   <li><b>Skeleton</b> — faint bone-dust particulate every few seconds (decaying
 *       undead off-gassing).</li>
 * </ul>
 *
 * <h3>Dependencies on previous-session additions</h3>
 * The following constants must exist in GasRegistry / ParticulateType — they are
 * added in the companion session files that are not yet merged into the base zip:
 * {@code GasRegistry.SOUL_ESSENCE}, {@code GasRegistry.IONISED_AIR},
 * {@code ParticulateType.SOUL_WISPS}, {@code ParticulateType.IONISED_PARTICLES}.
 * If those files are not yet present, temporarily replace references with
 * {@code GasRegistry.SOUL_SMOKE} / {@code GasRegistry.OZONE} /
 * {@code ParticulateType.SOUL_DUST} / {@code ParticulateType.REDSTONE_DUST}
 * as placeholders until the merge.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaMobAtmosphereHandler {

    // ── Tick intervals (in server ticks) ──────────────────────────────────────
    private static final int WARDEN_TICK_INTERVAL      = 40; // every 2 s
    private static final int WITHER_TICK_INTERVAL      = 20; // every 1 s
    private static final int WITHER_SKEL_TICK_INTERVAL = 60; // every 3 s
    private static final int SKELETON_TICK_INTERVAL    = 80; // every 4 s

    private static int tickCounter = 0;

    private VanillaMobAtmosphereHandler() {}

    // ─────────────────────────────────────────────────────────────────────────
    // LivingUpdateEvent — passive tick emissions
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        tickCounter++;
        var entity = event.getEntity();
        BlockPos pos = entity.blockPosition();

        // ── Warden ───────────────────────────────────────────────────────────
        if (entity instanceof Warden warden) {
            if (tickCounter % WARDEN_TICK_INTERVAL != 0) return;

            // Warden's continuous sonic output pressurises nearby air with
            // micro-vibrations, slowly displacing gases outward and ionising
            // small quantities of air into ozone and nitric oxide.
            GridAtmosphereCompat.addGas(level, pos, GasRegistry.OZONE,      1.5f);
            GridAtmosphereCompat.addGas(level, pos, GasRegistry.NITRIC_OXIDE, 0.8f);
            // IONISED_AIR — added in session-2 GasRegistry additions
            GridAtmosphereCompat.addGas(level, pos, GasRegistry.IONISED_AIR, 2.0f);
            // Micro-particulate from the sonic vibration field
            GridAtmosphereCompat.addParticulate(level, pos,
                    ParticulateType.IONISED_PARTICLES, 3.0f);
            EnvironmentGrid.enqueue(level, pos);
        }

        // ── Wither Boss ──────────────────────────────────────────────────────
        else if (entity instanceof WitherBoss) {
            if (tickCounter % WITHER_TICK_INTERVAL != 0) return;

            // Three skulls, three emission points
            for (int head = -1; head <= 1; head++) {
                BlockPos headPos = pos.offset(head, 1, 0);
                GridAtmosphereCompat.addGas(level, headPos,
                        GasRegistry.WITHER_MIASMA, 8.0f);
                GridAtmosphereCompat.addGas(level, headPos,
                        GasRegistry.SOUL_SMOKE,    5.0f);
                GridAtmosphereCompat.addParticulate(level, headPos,
                        ParticulateType.SOUL_WISPS, 6.0f);
                EnvironmentGrid.enqueue(level, headPos);
            }
            // Core body also drains ambient O₂ (necromantic consumption)
            float o2 = GridAtmosphereCompat.getGas(level, pos, GasRegistry.OXYGEN);
            if (o2 > 20f) {
                GridAtmosphereCompat.addGas(level, pos, GasRegistry.OXYGEN, -4f);
                GridAtmosphereCompat.addGas(level, pos, GasRegistry.SOUL_ESSENCE, 2f);
            }
        }

        // ── Wither Skeleton ──────────────────────────────────────────────────
        else if (entity instanceof WitherSkeleton) {
            if (tickCounter % WITHER_SKEL_TICK_INTERVAL != 0) return;

            GridAtmosphereCompat.addGas(level, pos, GasRegistry.WITHER_MIASMA, 3.0f);
            GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.SOUL_DUST, 2.0f);
            EnvironmentGrid.enqueue(level, pos);
        }

        // ── Skeleton ─────────────────────────────────────────────────────────
        else if (entity instanceof Skeleton) {
            if (tickCounter % SKELETON_TICK_INTERVAL != 0) return;

            // Dry bone dust from an ambulatory skeleton — very faint
            GridAtmosphereCompat.addParticulate(level, pos, ParticulateType.DUST, 1.5f);
            EnvironmentGrid.enqueue(level, pos);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LivingDeathEvent — death bursts
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        var entity = event.getEntity();
        BlockPos pos = entity.blockPosition();
        Vec3 centre = entity.position();

        // Wither Boss death — massive release; handled here alongside the
        // explosion the game already triggers.
        if (entity instanceof WitherBoss) {
            // Enormous wither miasma + soul smoke burst; shockwave is handled
            // by the vanilla explosion event in WorldEventHandler, so we only
            // add the chemical signature here.
            injectRadius(level, pos, 10, (p, dist) -> {
                float falloff = 1f - dist / 10f;
                GridAtmosphereCompat.addGas(level, p, GasRegistry.WITHER_MIASMA,
                        120f * falloff);
                GridAtmosphereCompat.addGas(level, p, GasRegistry.SOUL_SMOKE,
                        80f  * falloff);
                GridAtmosphereCompat.addGas(level, p, GasRegistry.SOUL_ESSENCE,
                        50f  * falloff);
                GridAtmosphereCompat.addParticulate(level, p,
                        ParticulateType.SOUL_WISPS, 60f * falloff);
                GridAtmosphereCompat.addParticulate(level, p,
                        ParticulateType.SOUL_DUST,  40f * falloff);
                EnvironmentGrid.enqueue(level, p);
            });
            ShockwaveHandler.spawn(level, pos, 18f);
            ShockwaveDataPacket.sendToNear(level, centre, 18f, 200f);
        }

        // Wither Skeleton death — moderate miasma puff
        else if (entity instanceof WitherSkeleton) {
            injectRadius(level, pos, 3, (p, dist) -> {
                float falloff = 1f - dist / 3f;
                GridAtmosphereCompat.addGas(level, p, GasRegistry.WITHER_MIASMA,
                        20f * falloff);
                GridAtmosphereCompat.addParticulate(level, p,
                        ParticulateType.SOUL_WISPS, 12f * falloff);
                EnvironmentGrid.enqueue(level, p);
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sonic Boom — Warden attack
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called from the Forge {@code ProjectileImpactEvent} subscriber for the
     * Warden's internal sonic boom projectile, OR from a dedicated Mixin into
     * {@code Warden#performSonicBoom} if the projectile approach doesn't work
     * cleanly in 1.20.1.
     *
     * <p>The sonic boom creates a narrow directional near-vacuum channel along
     * the attack vector (the air is violently displaced outward), with a dense
     * shockwave ring at the edge and ozone/NO ionisation throughout.</p>
     *
     * @param level    server level
     * @param origin   Warden eye position (source of the boom)
     * @param target   target entity centre position
     */
    public static void onWardenSonicBoom(ServerLevel level, BlockPos origin, Vec3 target) {
        if (!MgeConfig.enableGasEffects) return;

        Vec3 src = Vec3.atCenterOf(origin);
        Vec3 dir = target.subtract(src).normalize();
        double channelLength = src.distanceTo(target) + 3.0;

        // Walk the boom channel and evacuate gas, leaving ionisation behind
        for (double t = 0; t <= channelLength; t += 0.75) {
            Vec3 p = src.add(dir.scale(t));
            BlockPos cell = BlockPos.containing(p);

            // Partial vacuum — displace N₂ and O₂ radially (simulate by
            // simply removing a fraction; diffusion restores equilibrium)
            var comp = GridAtmosphereCompat.getComposition(level, cell);
            float n2    = comp.get(GasRegistry.NITROGEN);
            float o2    = comp.get(GasRegistry.OXYGEN);
            float vacFrac = (float) (0.65 - t / channelLength * 0.30); // deepest at source
            comp.add(GasRegistry.NITROGEN, -n2 * vacFrac);
            comp.add(GasRegistry.OXYGEN,   -o2 * vacFrac);
            // Ionisation products along the beam path
            comp.add(GasRegistry.OZONE,       o2 * vacFrac * 0.12f);
            comp.add(GasRegistry.NITRIC_OXIDE, n2 * vacFrac * 0.08f);
            comp.add(GasRegistry.IONISED_AIR,  (n2 + o2) * vacFrac * 0.20f);
            GridAtmosphereCompat.setComposition(level, cell, comp);
            GridAtmosphereCompat.addParticulate(level, cell,
                    ParticulateType.IONISED_PARTICLES, 8f * (float)(1 - t / channelLength));
            EnvironmentGrid.enqueue(level, cell);
        }

        // Shockwave ring at the target — the displaced air slams back
        BlockPos targetCell = BlockPos.containing(target);
        ShockwaveHandler.spawn(level, targetCell, 6f);
        ShockwaveDataPacket.sendToNear(level, target, 6f, 60f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ProjectileImpactEvent — Wind Charge (Breeze)
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        if (!(proj instanceof WindChargeProjectile)) return;

        BlockPos impactPos = BlockPos.containing(proj.position());
        Vec3 impactVec = proj.position();

        // Wind Charge creates a rotational vortex — not a simple radial burst.
        // We approximate this by injecting a CCW spiral pattern of gas displacement:
        // cells along the vortex perimeter are evacuated slightly, and the centre
        // is left at ambient with a sharp shockwave ring.
        applyVortexDisplacement(level, impactPos, impactVec, 5);

        // Central cell: sharp pressure spike then release (the "eye" of the vortex)
        GridAtmosphereCompat.addGas(level, impactPos, GasRegistry.OZONE, 3f);
        GridAtmosphereCompat.addGas(level, impactPos, GasRegistry.IONISED_AIR, 4f);
        GridAtmosphereCompat.addParticulate(level, impactPos,
                ParticulateType.IONISED_PARTICLES, 5f);
        EnvironmentGrid.enqueue(level, impactPos);

        ShockwaveHandler.spawn(level, impactPos, 4.5f);
        ShockwaveDataPacket.sendToNear(level, impactVec, 4.5f, 48f);
    }

    /**
     * Applies a rotational vortex gas displacement pattern in the horizontal
     * plane centred on {@code centre}. Cells at radius {@code r} are partially
     * evacuated (air is centrifugally displaced outward), cells just outside
     * that ring receive a compensatory pressure pulse, and the pattern
     * transitions smoothly via linear falloff.
     */
    private static void applyVortexDisplacement(ServerLevel level,
                                                BlockPos centre,
                                                Vec3 centreVec,
                                                int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist < 1f || dist > radius) continue;

                BlockPos cell = centre.offset(dx, 0, dz);
                float innerRing = (float) (radius - 1);
                float outerRing = (float) (radius + 1);

                if (dist < innerRing) {
                    // Interior: centrifugal evacuation
                    float vacFrac = 0.25f * (1f - dist / innerRing);
                    var comp = GridAtmosphereCompat.getComposition(level, cell);
                    float n2 = comp.get(GasRegistry.NITROGEN);
                    float o2 = comp.get(GasRegistry.OXYGEN);
                    comp.add(GasRegistry.NITROGEN, -n2 * vacFrac);
                    comp.add(GasRegistry.OXYGEN,   -o2 * vacFrac);
                    GridAtmosphereCompat.setComposition(level, cell, comp);
                } else {
                    // Outer ring: pressure pulse as displaced air converges
                    float excess = 0.15f * (1f - (dist - innerRing) / (outerRing - innerRing));
                    GridAtmosphereCompat.addGas(level, cell, GasRegistry.NITROGEN,
                            GridAtmosphereCompat.getGas(level, cell, GasRegistry.NITROGEN) * excess);
                    GridAtmosphereCompat.addGas(level, cell, GasRegistry.OXYGEN,
                            GridAtmosphereCompat.getGas(level, cell, GasRegistry.OXYGEN) * excess);
                }
                EnvironmentGrid.enqueue(level, cell);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lightning bolt — plasma channel
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLightningStrike(EntityStruckByLightningEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        BlockPos strikePos = event.getLightning().blockPosition();
        Vec3 strikeVec = event.getLightning().position();

        // The lightning bolt flash-vaporises a narrow column of air into a
        // plasma channel.  Along the bolt path (surface down to Y+5 above strike)
        // the gases are partially replaced by reaction products; the surrounding
        // air then rushes in, creating a micro-vacuum with a shockwave.

        int strikeY = strikePos.getY();
        for (int dy = 0; dy <= 12; dy++) {
            BlockPos cell = strikePos.above(dy);
            if (!level.getBlockState(cell).isAir()) continue; // skip solid blocks

            var comp = GridAtmosphereCompat.getComposition(level, cell);
            float n2 = comp.get(GasRegistry.NITROGEN);
            float o2 = comp.get(GasRegistry.OXYGEN);

            // N₂ + O₂ → 2 NO  (lightning fixation)
            float reacted = Math.min(n2, o2) * 0.35f;
            comp.add(GasRegistry.NITROGEN,    -reacted);
            comp.add(GasRegistry.OXYGEN,      -reacted);
            comp.add(GasRegistry.NITRIC_OXIDE, reacted * 1.8f);
            // Some O₃ from O₂ dissociation + recombination
            comp.add(GasRegistry.OZONE,        o2 * 0.05f);
            // IONISED_AIR — superheated plasma residue that cools to metastable form
            comp.add(GasRegistry.IONISED_AIR,  (n2 + o2) * 0.10f);

            // Micro-vacuum from the rapid heating/expansion then cooling
            float totalBefore = comp.get(GasRegistry.NITROGEN) + comp.get(GasRegistry.OXYGEN)
                    + n2 - reacted + o2 - reacted; // rough estimate
            // Actual vacuum effect: remove a fraction of remaining gas
            // (the plasma channel has much lower effective pressure in the bolt instant)
            float vacFrac = 0.20f * (1f - (float) dy / 12f);
            comp.add(GasRegistry.NITROGEN, -comp.get(GasRegistry.NITROGEN) * vacFrac);
            comp.add(GasRegistry.OXYGEN,   -comp.get(GasRegistry.OXYGEN)   * vacFrac);

            GridAtmosphereCompat.setComposition(level, cell, comp);
            GridAtmosphereCompat.addParticulate(level, cell,
                    ParticulateType.IONISED_PARTICLES, 6f * (1f - (float) dy / 12f));
            EnvironmentGrid.enqueue(level, cell);
        }

        // Surround the strike point — NO₂ forms as NO reacts with remaining O₂
        // in the seconds after the strike; approximated as immediate deposition
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 3) continue;
                BlockPos cell = strikePos.offset(dx, 0, dz);
                GridAtmosphereCompat.addGas(level, cell,
                        GasRegistry.NITROGEN_DIOXIDE, 2.5f);
                GridAtmosphereCompat.addGas(level, cell,
                        GasRegistry.OZONE, 1.5f);
                EnvironmentGrid.enqueue(level, cell);
            }
        }

        // Shockwave — the thunder crack is the air rushing back into the vacuum
        ShockwaveHandler.spawn(level, strikePos, 5f);
        ShockwaveDataPacket.sendToNear(level, strikeVec, 5f, 64f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper — radial injection with distance-aware callback
    // ─────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface RadialCallback {
        void apply(BlockPos pos, float distanceFraction);
    }

    private static void injectRadius(ServerLevel level, BlockPos centre,
                                     int radius, RadialCallback callback) {
        float r = radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > r) continue;
                    callback.apply(centre.offset(dx, dy, dz), dist / r);
                }
            }
        }
    }
}