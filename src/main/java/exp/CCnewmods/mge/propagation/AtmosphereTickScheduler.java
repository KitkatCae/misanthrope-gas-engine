package exp.CCnewmods.mge.propagation;

import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.wind.WindProviderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Processes atmosphere block diffusion and particulate settling on a per-server-tick budget.
 *
 * <p>Maintains a dirty queue of {@link BlockPos} entries. Each tick, up to
 * {@link #MAX_BLOCKS_PER_TICK} are dequeued and processed:
 * <ol>
 *   <li>Gas diffusion — passive equalisation toward lower-concentration neighbours,
 *       weighted by density and wind.</li>
 *   <li>Particulate settling — gravitational settling downward, counteracted by wind loft.
 *       Heavy particulates (sand, gravel) fall fast; light ones (spores, pollen) linger.</li>
 * </ol>
 */
public final class AtmosphereTickScheduler {

    public static final int MAX_BLOCKS_PER_TICK = 512;
    private static final float GAS_DIFFUSION_RATE   = 0.02f;
    private static final float GAS_PRUNE_THRESHOLD  = 0.0001f;
    private static final float PART_PRUNE_THRESHOLD = 0.01f;

    private final ServerLevel level;
    private final Queue<BlockPos> dirtyQueue = new ConcurrentLinkedQueue<>();
    private final Set<Long> queued = Collections.synchronizedSet(new HashSet<>());

    public AtmosphereTickScheduler(ServerLevel level) {
        this.level = level;
    }

    // -------------------------------------------------------------------------
    // Enqueue
    // -------------------------------------------------------------------------

    public void enqueue(BlockPos pos) {
        if (queued.add(pos.asLong())) dirtyQueue.add(pos.immutable());
    }

    public void enqueueWithNeighbours(BlockPos pos) {
        enqueue(pos);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    enqueue(pos.offset(dx, dy, dz));
                }
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    public void tick() {
        int processed = 0;
        while (processed < MAX_BLOCKS_PER_TICK) {
            BlockPos pos = dirtyQueue.poll();
            if (pos == null) break;
            queued.remove(pos.asLong());
            if (!level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof AtmosphereBlockEntity atm)) continue;
            diffuseGas(pos, atm);
            settleParticulates(pos, atm);
            processed++;
        }
    }

    // -------------------------------------------------------------------------
    // Gas diffusion
    // -------------------------------------------------------------------------

    private void diffuseGas(BlockPos pos, AtmosphereBlockEntity source) {
        GasComposition srcComp = source.getComposition();
        if (srcComp.isEmpty()) return;

        Vec3 wind = WindProviderManager.getWind(level, pos);
        float windX = (float) wind.x, windY = (float) wind.y, windZ = (float) wind.z;

        record Neighbour(BlockPos pos, AtmosphereBlockEntity entity, int dx, int dy, int dz) {}
        List<Neighbour> neighbours = new ArrayList<>(26);

        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos nPos = pos.offset(dx, dy, dz);
                    if (!level.isLoaded(nPos)) continue;
                    BlockEntity nbe = level.getBlockEntity(nPos);
                    if (!(nbe instanceof AtmosphereBlockEntity nAtm)) continue;
                    neighbours.add(new Neighbour(nPos, nAtm, dx, dy, dz));
                }

        if (neighbours.isEmpty()) return;
        boolean sourceChanged = false;

        for (String key : new ArrayList<>(srcComp.getTag().getAllKeys())) {
            float srcMbar = srcComp.get(key);
            if (srcMbar <= 0f) continue;
            Gas gas = GasRegistry.get(key).orElse(null);
            if (gas == null) continue;

            double density = gas.properties().densityRatioToAir();
            float windSens = gas.properties().windSensitivity();
            float totalTransferWeight = 0f;
            float[] weights = new float[neighbours.size()];

            for (int i = 0; i < neighbours.size(); i++) {
                Neighbour n = neighbours.get(i);
                if (n.entity().getComposition().get(key) >= srcMbar) { weights[i] = 0f; continue; }
                float distSq = n.dx() * n.dx() + n.dy() * n.dy() + n.dz() * n.dz();
                float w = 1.0f / distSq;
                if (n.dy() < 0) w *= (float) Math.min(2.0, density);
                else if (n.dy() > 0) w *= (float) Math.max(0.1, 2.0 - density);
                float offsetLen = (float) Math.sqrt(distSq);
                float windDot = (n.dx() * windX + n.dy() * windY + n.dz() * windZ) / offsetLen;
                w *= (1.0f + windSens * Math.max(0f, windDot));
                weights[i] = w;
                totalTransferWeight += w;
            }

            if (totalTransferWeight <= 0f) continue;
            float totalTransfer = srcMbar * GAS_DIFFUSION_RATE;

            for (int i = 0; i < neighbours.size(); i++) {
                if (weights[i] <= 0f) continue;
                float transfer = totalTransfer * (weights[i] / totalTransferWeight);
                AtmosphereBlockEntity nAtm = neighbours.get(i).entity();
                nAtm.getComposition().add(gas, transfer);
                nAtm.setComposition(nAtm.getComposition());
                enqueue(neighbours.get(i).pos());
            }
            srcComp.add(gas, -totalTransfer);
            sourceChanged = true;
        }

        if (sourceChanged) {
            srcComp.prune(GAS_PRUNE_THRESHOLD);
            source.setComposition(srcComp);
        }
    }

    // -------------------------------------------------------------------------
    // Particulate settling
    // -------------------------------------------------------------------------

    /**
     * Applies gravitational settling to particulates in the source block.
     * Settled amounts are transferred to the block directly below (if it is
     * also an atmosphere block). Wind loft is sampled once per block per settle tick.
     */
    private void settleParticulates(BlockPos pos, AtmosphereBlockEntity source) {
        ParticulateComposition parts = source.getParticulates();
        if (parts.isEmpty()) return;

        Vec3 wind = WindProviderManager.getWind(level, pos);
        float windSpeed = (float) wind.horizontalDistance();

        // Also diffuse particulates laterally using wind direction
        diffuseParticulates(pos, source, parts, wind, windSpeed);

        // Settle downward
        float settled = parts.applySettling(windSpeed);
        parts.prune(PART_PRUNE_THRESHOLD);
        source.setParticulates(parts);

        if (settled > 0f) {
            BlockPos below = pos.below();
            if (level.isLoaded(below)) {
                BlockEntity nbe = level.getBlockEntity(below);
                if (nbe instanceof AtmosphereBlockEntity belowAtm) {
                    // Distribute settled amount back down, split across all particulate types
                    // proportionally (applySettling removed proportionally, so we re-add scaled)
                    for (ParticulateType type : ParticulateType.values()) {
                        float amt = belowAtm.getParticulates().get(type);
                        // We deposited `settled` total; apportion by what each type contributed
                        // Simple approach: re-add whatever was removed from each type
                    }
                    // Simpler: just add the total settled as DUST to the block below
                    // and let individual tracking handle itself via the per-type settle in applySettling
                    // (applySettling already modified `parts` in place; below receives net settled)
                    ParticulateComposition belowParts = belowAtm.getParticulates();
                    // Re-distribute: settled mass goes below proportional to each type's fraction
                    ParticulateComposition snapshot = source.getParticulates().copy();
                    float totalBefore = snapshot.totalConcentration() + settled;
                    if (totalBefore > 0f) {
                        for (ParticulateType type : ParticulateType.values()) {
                            float original = snapshot.get(type);
                            if (original <= 0f) continue;
                            float fraction = original / totalBefore;
                            belowParts.add(type, settled * fraction);
                        }
                    }
                    belowAtm.setParticulates(belowParts);
                    enqueue(below);
                }
            }
        }
    }

    /**
     * Horizontal wind dispersal of particulates — moves fractions to downwind neighbours
     * on the same Y level.
     */
    private void diffuseParticulates(BlockPos pos, AtmosphereBlockEntity source,
                                      ParticulateComposition parts, Vec3 wind, float windSpeed) {
        if (windSpeed < 0.1f) return; // no meaningful wind — skip lateral dispersal

        float windX = (float) wind.x;
        float windZ = (float) wind.z;

        // Only consider the 8 horizontal face/edge neighbours (same Y)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos nPos = pos.offset(dx, 0, dz);
                if (!level.isLoaded(nPos)) continue;
                BlockEntity nbe = level.getBlockEntity(nPos);
                if (!(nbe instanceof AtmosphereBlockEntity nAtm)) continue;

                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                float windDot = (dx * windX + dz * windZ) / dist;
                if (windDot <= 0f) continue; // only downwind neighbours

                ParticulateComposition nParts = nAtm.getParticulates();
                boolean changed = false;

                for (ParticulateType type : ParticulateType.values()) {
                    float amount = parts.get(type);
                    if (amount <= 0f) continue;
                    float transfer = amount * windDot * type.windSensitivity * windSpeed * 0.005f;
                    transfer = Math.min(transfer, amount * 0.1f); // cap at 10% per tick
                    if (transfer < PART_PRUNE_THRESHOLD) continue;
                    parts.add(type, -transfer);
                    nParts.add(type, transfer);
                    changed = true;
                }

                if (changed) {
                    nAtm.setParticulates(nParts);
                    enqueue(nPos);
                }
            }
        }
    }

    public int queueSize() { return dirtyQueue.size(); }
}
