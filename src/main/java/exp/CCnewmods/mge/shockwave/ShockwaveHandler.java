package exp.CCnewmods.mge.shockwave;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.compat.MisCoreBridge;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.vacuum.VacuumHandler;
import net.minecraft.core.BlockPos;
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

    private ShockwaveHandler() {}

    public static void spawn(ServerLevel level, BlockPos origin, float strength) {
        if (strength < 0.5f) return;
        ACTIVE.computeIfAbsent(level, k -> Collections.synchronizedList(new ArrayList<>()))
              .add(new ShockwaveFront(origin, strength));
        level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS, Math.min(2f, strength * 0.5f),
                0.8f + level.getRandom().nextFloat() * 0.4f);
    }

    public static void onLevelUnload(ServerLevel level) { ACTIVE.remove(level); }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ACTIVE.forEach((level, waves) -> {
            waves.removeIf(w -> { tickWave(level, w); return w.dead; });

            // Spawn secondary solid-propagation fronts accumulated this tick
            List<ShockwaveFront> toAdd = new ArrayList<>();
            for (ShockwaveFront wave : waves) {
                if (!wave.isSolidPropagation
                        && wave.transmittedStrength >= ShockwaveFront.MIN_PROPAGATION_THRESHOLD) {
                    toAdd.add(new ShockwaveFront(wave.origin, wave.transmittedStrength, true));
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
     * Injects structural stress into Misanthrope Core (via MisCoreBridge) and
     * accumulates transmitted strength for secondary front spawning.
     *
     * @param shellStrength effective wave strength at this shell position
     */
    private static void processSolidBlock(ServerLevel level, ShockwaveFront wave,
                                          BlockPos pos, float shellStrength) {
        BlockState state = level.getBlockState(pos);

        float absorption     = MisCoreBridge.getShockwaveAbsorption(state);
        float amplification  = MisCoreBridge.getShockwaveAmplification(state);
        float effective      = shellStrength * amplification * (1f - absorption);

        // Inject stress — MisCoreBridge no-ops if Misanthrope Core not loaded
        if (effective > 0.01f) {
            MisCoreBridge.injectShockwaveStress(level, pos, effective);
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
        }

        // Fix 3: use correct local variable names (shellPos, disp→strength)
        if (!level.isEmptyBlock(shellPos)) {
            processSolidBlock(level, wave, shellPos, disp);
        }

        EnvironmentGrid.enqueue(level, shellPos);
        EnvironmentGrid.enqueue(level, outPos);
    }
}
