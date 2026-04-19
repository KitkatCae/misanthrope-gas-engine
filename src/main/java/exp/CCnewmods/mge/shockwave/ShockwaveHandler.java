package exp.CCnewmods.mge.shockwave;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.vacuum.VacuumHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        });
        ACTIVE.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private static void tickWave(ServerLevel level, ShockwaveFront wave) {
        int r = wave.currentRadius;
        float strength = wave.currentStrength();
        float disp = Math.min(0.8f, BASE_DISPLACEMENT * strength);

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

    private static void processShellBlock(ServerLevel level, ShockwaveFront wave,
                                           BlockPos shellPos, int dx, int dy, int dz,
                                           float disp) {
        BlockEntity be = level.getBlockEntity(shellPos);
        if (!(be instanceof AtmosphereBlockEntity srcAtm)) return;

        // Attenuate in vacuum
        float dispFrac = VacuumHandler.isVacuum(level, shellPos) ? disp * 0.3f : disp;

        BlockPos outPos = shellPos.offset(
                (int) Math.signum(dx), (int) Math.signum(dy), (int) Math.signum(dz));
        if (!level.isLoaded(outPos)) return;
        BlockEntity outBE = level.getBlockEntity(outPos);
        if (!(outBE instanceof AtmosphereBlockEntity dstAtm)) return;

        // Transfer gas
        var srcTag = srcAtm.getComposition().getTag();
        var dstTag = dstAtm.getComposition().getTag();
        for (String key : new ArrayList<>(srcTag.getAllKeys())) {
            float amt = srcTag.getFloat(key);
            float t = amt * dispFrac;
            if (t <= 0) continue;
            srcTag.putFloat(key, Math.max(0, amt - t));
            dstTag.putFloat(key, dstTag.getFloat(key) + t);
        }
        srcAtm.setComposition(srcAtm.getComposition());
        dstAtm.setComposition(dstAtm.getComposition());

        // Transfer particulates
        for (ParticulateType type : ParticulateType.values()) {
            float amt = srcAtm.getParticulates().get(type);
            if (amt <= 0) continue;
            float t = amt * dispFrac;
            srcAtm.getParticulates().add(type, -t);
            dstAtm.getParticulates().add(type, t);
        }
        srcAtm.setParticulates(srcAtm.getParticulates());
        dstAtm.setParticulates(dstAtm.getParticulates());

        Mge.getScheduler(level).enqueue(shellPos);
        Mge.getScheduler(level).enqueue(outPos);
    }
}
