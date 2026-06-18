package exp.CCnewmods.mge.shockwave;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.compat.MisWorldBridge;
import exp.CCnewmods.mge.compat.MisanthropeWorldCompat;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.vacuum.VacuumHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShockwaveHandler {

    private static final Map<ServerLevel, List<ShockwaveFront>> ACTIVE
            = new ConcurrentHashMap<>();
    private static final float BASE_DISPLACEMENT = 0.35f;
    private static final float IMPULSE_PER_STRENGTH = 0.8f;

    /** How far (in blocks) the client packet propagates — must cover the
     *  largest wave's maxRadius so distant players still see the shader. */
    private static final double PACKET_RADIUS = 96.0;

    /** Ticks between periodic client update packets for a live wave. */
    private static final int UPDATE_INTERVAL = 4;

    private ShockwaveHandler() {}

    public static void spawn(ServerLevel level, BlockPos origin, float strength) {
        if (strength < 0.5f) return;

        // Sample ambient temperature once at spawn and seed the accumulator
        // from whatever particulates were already airborne at the origin —
        // both per the design: the wave "has starting debris and picks up
        // more as it travels."
        float spawnTempC = MisanthropeWorldCompat.getCelsiusAt(level, origin, 20.0f);
        ShockwaveFront wave = new ShockwaveFront(origin, strength, spawnTempC);
        seedParticulates(level, origin, wave);

        ACTIVE.computeIfAbsent(level, k -> Collections.synchronizedList(new ArrayList<>()))
              .add(wave);
        level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS, Math.min(2f, strength * 0.5f),
                0.8f + level.getRandom().nextFloat() * 0.4f);

        // Initial packet uses the same construction path as periodic
        // updates (see onServerTick) so there's only one place that builds
        // a ShockwaveDataPacket from a ShockwaveFront.
        ShockwaveDataPacket.sendUpdateToNear(level, wave, PACKET_RADIUS);
    }

    /** Seeds a freshly-spawned wave's accumulator from the origin block's
     *  existing particulate composition, so the wave starts out carrying
     *  whatever dust/smoke/etc was already in the air at ground zero. */
    private static void seedParticulates(ServerLevel level, BlockPos origin, ShockwaveFront wave) {
        var originParts = GridAtmosphereCompat.getParticulates(level, origin);
        for (ParticulateType type : ParticulateType.values()) {
            float amt = originParts.get(type);
            if (amt > 0f) wave.accumulatedParticulates.add(type, amt);
        }
    }

    /** Called on server stopping (see {@code Mge.onServerStopping}) so
     *  shockwave state doesn't leak across a server restart. */
    public static void onLevelUnload(ServerLevel level) { ACTIVE.remove(level); }

    /** Smoke particle threshold (mg/m³ of ash) below which we don't bother
     *  spawning anything — keeps very mild waves from kicking up smoke. */
    private static final float SMOKE_ASH_THRESHOLD = 15.0f;
    /** mg/m³ of ash at which smoke spawning hits its max density per call. */
    private static final float SMOKE_ASH_SATURATION = 250.0f;
    private static final int   MAX_SMOKE_PER_CALL = 12;

    /**
     * Spawns vanilla smoke particles in a ring around the wave's current
     * shell, density scaled by how much ash/soot the wave has accumulated.
     * Server-side via {@code ServerLevel.sendParticles} so every observing
     * player sees the same particles rather than each client inventing its
     * own — keeps this consistent with how the rest of MGE's visual
     * feedback (sounds, etc.) is already server-authoritative.
     */
    private static void spawnDustSmoke(ServerLevel level, ShockwaveFront wave) {
        float ashAmt = wave.particulateBuckets()[ParticulateBucket.ASH.ordinal()];
        if (ashAmt < SMOKE_ASH_THRESHOLD) return;

        float saturation = Math.min(1.0f,
                (ashAmt - SMOKE_ASH_THRESHOLD) / (SMOKE_ASH_SATURATION - SMOKE_ASH_THRESHOLD));
        int count = Math.max(1, Math.round(saturation * MAX_SMOKE_PER_CALL));

        double radius = wave.currentRadius;
        if (radius < 1.0) return;

        var rand = level.getRandom();
        for (int i = 0; i < count; i++) {
            // Random point on the current shell sphere (not just a flat
            // ring) so smoke appears above, below, and around the blast,
            // not just at the player's eye-level slice through it.
            double theta = rand.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * rand.nextDouble() - 1.0);
            double x = wave.origin.getX() + 0.5 + radius * Math.sin(phi) * Math.cos(theta);
            double y = wave.origin.getY() + 0.5 + radius * Math.cos(phi);
            double z = wave.origin.getZ() + 0.5 + radius * Math.sin(phi) * Math.sin(theta);

            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, y, z, 1,
                    0.1, 0.05, 0.1, 0.01);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ACTIVE.forEach((level, waves) -> {
            waves.removeIf(w -> {
                tickWave(level, w);
                if (w.dead && !w.isSolidPropagation) {
                    // Tell the client this wave is gone now rather than
                    // leaving it to guess from a stale last-known radius.
                    ShockwaveDataPacket.sendDeathToNear(level, w, PACKET_RADIUS);
                }
                return w.dead;
            });

            // Periodic client updates: only for waves the shader actually
            // visualizes (solid-propagation fronts travel through rock and
            // have no above-ground screen-space distortion to show).
            for (ShockwaveFront wave : waves) {
                if (wave.isSolidPropagation) continue;
                if (++wave.ticksSinceLastUpdate < UPDATE_INTERVAL) continue;
                wave.ticksSinceLastUpdate = 0;
                ShockwaveDataPacket.sendUpdateToNear(level, wave, PACKET_RADIUS);
                spawnDustSmoke(level, wave);
            }

            // Spawn secondary solid-propagation fronts accumulated this tick
            List<ShockwaveFront> toAdd = new ArrayList<>();
            for (ShockwaveFront wave : waves) {
                if (!wave.isSolidPropagation
                        && wave.transmittedStrength >= ShockwaveFront.MIN_PROPAGATION_THRESHOLD) {
                    // Carry the parent wave's temperature into the secondary
                    // front rather than resetting to ambient — heat doesn't
                    // vanish just because the wave is now travelling through
                    // solid rock.
                    toAdd.add(new ShockwaveFront(wave.origin, wave.transmittedStrength,
                            true, wave.spawnTemperatureC));
                    wave.transmittedStrength = 0f;
                }
            }
            waves.addAll(toAdd);
        });
        ACTIVE.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private static void tickWave(ServerLevel level, ShockwaveFront wave) {
        // Fix 1: cast currentRadius (float) to int for loop bounds
        int r = (int) wave.currentRadius;
        // Fix 2: use strength() not currentStrength()
        float strength = wave.strength();
        float disp = Math.min(0.8f, BASE_DISPLACEMENT * strength);

        // Solid-propagation fronts only inject stress — skip gas/entity/particulate
        if (wave.isSolidPropagation) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) continue;
                        BlockPos shellPos = wave.origin.offset(dx, dy, dz);
                        if (!level.isLoaded(shellPos)) continue;
                        if (!level.isEmptyBlock(shellPos)) {
                            processSolidBlock(level, wave, shellPos, strength);
                        }
                    }
                }
            }
            wave.advance();
            return;
        }

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) continue;
                    BlockPos shellPos = wave.origin.offset(dx, dy, dz);
                    if (!level.isLoaded(shellPos)) continue;
                    processShellBlock(level, wave, shellPos, dx, dy, dz, disp);
                }
            }
        }

        // Entity impulses
        if (strength >= 0.3f) {
            Vec3 originVec = Vec3.atCenterOf(wave.origin);
            level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(wave.origin).inflate(r + 1.5), entity -> {
                        double dist = entity.position().distanceTo(originVec);
                        return dist >= r - 0.5 && dist <= r + 0.5;
                    }).forEach(entity -> {
                Vec3 impulse = entity.position().subtract(originVec).normalize()
                        .scale(strength * IMPULSE_PER_STRENGTH);
                entity.setDeltaMovement(entity.getDeltaMovement().add(impulse));
                entity.hurtMarked = true;
                if (strength > 2f)
                    entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0));
            });
        }

        wave.advance();
    }

    /**
     * Called by processShellBlock when the target position is a solid block.
     * Injects structural stress into Misanthrope Core (via MisWorldBridge) and
     * accumulates transmitted strength for secondary front spawning.
     *
     * @param shellStrength effective wave strength at this shell position
     */
    private static void processSolidBlock(ServerLevel level, ShockwaveFront wave,
                                          BlockPos pos, float shellStrength) {
        BlockState state = level.getBlockState(pos);

        float absorption     = MisWorldBridge.getShockwaveAbsorption(state);
        float amplification  = MisWorldBridge.getShockwaveAmplification(state);
        float effective      = shellStrength * amplification * (1f - absorption);

        // Inject stress — MisWorldBridge no-ops if Misanthrope Core not loaded
        if (effective > 0.01f) {
            MisWorldBridge.injectShockwaveStress(level, pos, effective);
        }

        // Compute transmission through the block
        // Harder blocks (higher blast resistance) transmit less
        float blastRes       = (float) state.getExplosionResistance(level, pos, null);
        float transmission   = shellStrength * (1f - Math.min(1f, blastRes / 600f));

        if (!wave.isSolidPropagation) {
            // Only accumulate on primary fronts — avoid chain reaction of secondaries
            wave.transmittedStrength = Math.max(wave.transmittedStrength, transmission);
        }
    }

    private static void processShellBlock(ServerLevel level, ShockwaveFront wave,
                                           BlockPos shellPos, int dx, int dy, int dz,
                                           float disp) {
        // Attenuate in vacuum
        float dispFrac = VacuumHandler.isVacuum(level, shellPos) ? disp * 0.3f : disp;

        BlockPos outPos = shellPos.offset(
                (int) Math.signum(dx), (int) Math.signum(dy), (int) Math.signum(dz));
        if (!level.isLoaded(outPos)) return;

        // Transfer gas via grid
        var srcComp = GridAtmosphereCompat.getComposition(level, shellPos);
        var dstComp = GridAtmosphereCompat.getComposition(level, outPos);
        for (exp.CCnewmods.mge.gas.Gas gas : exp.CCnewmods.mge.gas.GasRegistry.all()) {
            float amt = srcComp.get(gas);
            float t = amt * dispFrac;
            if (t <= 0) continue;
            srcComp.add(gas, -t);
            dstComp.add(gas, t);
        }
        GridAtmosphereCompat.setComposition(level, shellPos, srcComp);
        GridAtmosphereCompat.setComposition(level, outPos,   dstComp);

        // Transfer particulates
        for (ParticulateType type : ParticulateType.values()) {
            var srcParts = GridAtmosphereCompat.getParticulates(level, shellPos);
            float amt = srcParts.get(type);
            if (amt <= 0) continue;
            float t = amt * dispFrac;
            GridAtmosphereCompat.addParticulate(level, shellPos, type, -t);
            GridAtmosphereCompat.addParticulate(level, outPos,   type,  t);

            // Feed the wave's running accumulator from the same physical
            // transfer already happening here — this is the "picks up more
            // as it travels" half of the design; the wave's accumulator
            // grows by exactly what it actually pushed outward, not an
            // invented separate sampling pass.
            if (!wave.isSolidPropagation) {
                wave.accumulatedParticulates.add(type, t);
            }
        }

        // Fix 3: use correct local variable names (shellPos, disp→strength)
        if (!level.isEmptyBlock(shellPos)) {
            processSolidBlock(level, wave, shellPos, disp);
        }

        EnvironmentGrid.enqueue(level, shellPos);
        EnvironmentGrid.enqueue(level, outPos);
    }
}
