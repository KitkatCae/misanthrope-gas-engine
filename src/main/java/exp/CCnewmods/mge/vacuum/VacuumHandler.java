package exp.CCnewmods.mge.vacuum;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages vacuum regions — atmosphere blocks whose total pressure has fallen
 * below {@link #VACUUM_THRESHOLD_MBAR}.
 *
 * <h3>Suction field</h3>
 * Each vacuum block exerts a pulling force on nearby entities and items,
 * proportional to {@code (1013 - pressure) / 1013} and decaying with
 * inverse-square distance from the vacuum block centre. Connected vacuum
 * regions are tracked — a larger connected vacuum pulls with greater total force
 * since each block in the region contributes independently.
 *
 * <h3>Block pulling</h3>
 * Weak blocks adjacent to a deep vacuum (< {@link #HARD_VACUUM_THRESHOLD_MBAR})
 * can be pulled in. Only blocks tagged {@code #mge:vacuum_pullable} or with
 * destroy speed ≤ 2.0 (sand, gravel, leaves, carpet, doors) are eligible.
 *
 * <h3>Effects on entities inside vacuum</h3>
 * Suffocation, armour stress, ear-pop, fluid boiling in adjacent water.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VacuumHandler {

    public static final float VACUUM_THRESHOLD_MBAR      = 50f;
    public static final float HARD_VACUUM_THRESHOLD_MBAR = 10f;
    private static final float STD_PRESSURE              = 1013.25f;

    private static final int EFFECT_INTERVAL   = 20;  // ticks between entity effect checks
    private static final int SUCTION_INTERVAL  = 2;   // ticks between suction field updates
    private static final float SUCTION_RADIUS  = 8f;  // blocks
    private static final float MAX_PULL_FORCE  = 2.5f;// blocks/tick at zero distance
    private static final float ARMOUR_DMG_CHANCE = 0.05f;
    private static final float BLOCK_PULL_DESTROY_SPEED = 2.0f;

    private static int effectTick   = 0;
    private static int suctionTick  = 0;

    private VacuumHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!MgeConfig.enableGasEffects) return;

        effectTick++;
        suctionTick++;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (suctionTick % SUCTION_INTERVAL == 0) tickSuction(level);
            if (effectTick % EFFECT_INTERVAL == 0)   tickEffects(level);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Suction field — pull entities and items toward vacuum
    // ─────────────────────────────────────────────────────────────────────────

    private static void tickSuction(ServerLevel level) {
        exp.CCnewmods.mge.util.ChunkIterator.forEach(level, holder -> {
            var chunk = holder.getTickingChunk();
            if (chunk == null) return;

            chunk.getBlockEntities().forEach((pos, be) -> {
                if (!(be instanceof AtmosphereBlockEntity atm)) return;
                float pressure = atm.getComposition().totalPressure();
                if (pressure >= VACUUM_THRESHOLD_MBAR) return;

                float strength = (STD_PRESSURE - pressure) / STD_PRESSURE;
                Vec3 centre    = Vec3.atCenterOf(pos);

                // Pull entities
                AABB box = new AABB(pos).inflate(SUCTION_RADIUS);
                level.getEntitiesOfClass(LivingEntity.class, box).forEach(entity -> {
                    applyPull(entity, centre, strength);
                });
                // Pull item entities
                level.getEntitiesOfClass(ItemEntity.class, box).forEach(item -> {
                    applyPull(item, centre, strength);
                });

                // Pull weak adjacent blocks into vacuum
                if (pressure < HARD_VACUUM_THRESHOLD_MBAR) {
                    tryPullBlocks(level, pos, strength);
                }
            });
        });
    }

    private static void applyPull(net.minecraft.world.entity.Entity entity,
                                   Vec3 vacuum, float strength) {
        Vec3 toVacuum = vacuum.subtract(entity.position());
        double distSq = toVacuum.lengthSqr();
        if (distSq < 0.01) return;
        double dist = Math.sqrt(distSq);
        // Force = strength * MAX_PULL / dist² (inverse square), capped
        double force = Math.min(MAX_PULL_FORCE, strength * 4.0 / distSq);
        Vec3 impulse = toVacuum.normalize().scale(force * SUCTION_INTERVAL / 20.0);
        entity.setDeltaMovement(entity.getDeltaMovement().add(impulse));
    }

    private static void tryPullBlocks(ServerLevel level, BlockPos vacuumPos, float strength) {
        if (level.getRandom().nextFloat() > strength * 0.02f) return; // rare event

        for (BlockPos n : new BlockPos[]{
                vacuumPos.above(), vacuumPos.below(),
                vacuumPos.north(), vacuumPos.south(),
                vacuumPos.east(), vacuumPos.west()}) {
            if (!level.isLoaded(n)) continue;
            var state = level.getBlockState(n);
            if (state.isAir()) continue;
            if (state.hasBlockEntity()) continue;

            float speed = state.getDestroySpeed(level, n);
            if (speed < 0 || speed > BLOCK_PULL_DESTROY_SPEED) continue; // unbreakable or too hard

            // Destroy and let the drop fall into the vacuum (suction will pull it in)
            level.destroyBlock(n, true);
            break; // one block per vacuum per check
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity effects — suffocation, armour stress, fluid boiling
    // ─────────────────────────────────────────────────────────────────────────

    private static void tickEffects(ServerLevel level) {
        exp.CCnewmods.mge.util.ChunkIterator.forEach(level, holder -> {
            var chunk = holder.getTickingChunk();
            if (chunk == null) return;

            chunk.getBlockEntities().forEach((pos, be) -> {
                if (!(be instanceof AtmosphereBlockEntity atm)) return;
                float pressure = atm.getComposition().totalPressure();
                if (pressure >= VACUUM_THRESHOLD_MBAR) return;

                boolean hard = pressure < HARD_VACUUM_THRESHOLD_MBAR;

                // Entities inside this block
                level.getEntitiesOfClass(LivingEntity.class, new AABB(pos))
                     .forEach(entity -> applyVacuumEffects(level, pos, entity, hard));

                // Water boiling
                boilAdjacentFluids(level, pos, atm, pressure);
            });
        });
    }

    private static void applyVacuumEffects(ServerLevel level, BlockPos pos,
                                            LivingEntity entity, boolean hard) {
        // Suffocation
        int airDrain = hard ? 10 : 5;
        entity.setAirSupply(entity.getAirSupply() - airDrain);
        if (entity.getAirSupply() <= 0) {
            entity.hurt(entity.damageSources().drown(), hard ? 3f : 1.5f);
            entity.setAirSupply(0);
        }

        // Armour stress
        if (level.getRandom().nextFloat() < ARMOUR_DMG_CHANCE) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
                var armour = entity.getItemBySlot(slot);
                if (!armour.isEmpty() && armour.isDamageableItem())
                    armour.hurtAndBreak(1, entity, e -> e.broadcastBreakEvent(slot));
            }
        }

        // Ear-pop on entry
        if (entity.getAirSupply() == entity.getMaxAirSupply() - airDrain) {
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE,
                    SoundSource.AMBIENT, 0.3f, 1.8f);
        }
    }

    private static void boilAdjacentFluids(ServerLevel level, BlockPos pos,
                                            AtmosphereBlockEntity atm, float pressure) {
        if (pressure > VACUUM_THRESHOLD_MBAR * 0.5f) return;
        float boilChance = (1f - pressure / VACUUM_THRESHOLD_MBAR) * 0.02f;
        for (BlockPos n : new BlockPos[]{
                pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (!level.isLoaded(n)) continue;
            var state = level.getBlockState(n);
            if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
                if (level.getRandom().nextFloat() < boilChance) {
                    level.setBlock(n, Blocks.AIR.defaultBlockState(), 3);
                    atm.getComposition().add(GasRegistry.WATER_VAPOR, 15f);
                    atm.setComposition(atm.getComposition());
                    Mge.getScheduler(level).enqueue(pos);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean isVacuum(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof AtmosphereBlockEntity atm
                && atm.getComposition().totalPressure() < VACUUM_THRESHOLD_MBAR;
    }

    public static float getPressureRatio(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return 1.0f;
        return atm.getComposition().totalPressure() / STD_PRESSURE;
    }
}
