package exp.CCnewmods.mge.contraption;

import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

import java.util.HashMap;
import java.util.Map;

/**
 * Drop-in replacement for {@link BearingContraption}, substituted at
 * assembly time by {@code MixinSubstituteWindmillContraption}.
 * <p>
 * Owns three categories of new state beyond its parent:
 * <ol>
 *   <li><b>Burn state</b> ({@link #burnStages} / {@link #burnTicksRemaining})
 *       — per-local-position stage for sail blocks currently on fire. Added in
 *       Part C (burning windmill sub-project).</li>
 *   <li><b>TiC material</b> ({@link #sailMaterials}) — per-local-position
 *       MaterialId captured at assembly time from fin block BEs. Added in
 *       Part D. Package-accessible so {@code MixinBearingContraptionMaterial}
 *       can write into it directly during the assembly tally pass.</li>
 *   <li><b>Melt progress</b> ({@link #meltProgress}) — per-local-position
 *       0.0–1.0 accumulation for metal fin blocks exposed to high ambient
 *       temperature. Added in Part D. At 1.0 the fin's aero contribution
 *       drops to 0; the visual stand-in swap is driven by
 *       {@code WindmillBurnTickMixin}'s sibling mixin (TBD).</li>
 * </ol>
 * All state is fully round-tripped via {@link #readNBT}/{@link #writeNBT}.
 */
public class MisanthropeWindmillContraption extends BearingContraption {

    // ── Burn state (Part C) ───────────────────────────────────────────────────

    private final Map<BlockPos, Integer> burnStages = new HashMap<>();
    private final Map<BlockPos, Integer> burnTicksRemaining = new HashMap<>();

    // ── TiC material per blade (Part D) ──────────────────────────────────────

    /**
     * Maps each fin block's local-contraption {@link BlockPos} to the TiC
     * {@link MaterialId} stored in its BlockEntity at assembly time.
     * <p>
     * Package-accessible intentionally: {@code MixinBearingContraptionMaterial}
     * writes into this map during the assembly tally (the only point where the
     * real BE is live). After assembly, read-only from all other callers.
     * Empty entries mean "no TiC material" (vanilla cloth sail or fin with no
     * material set yet).
     */
    public final Map<BlockPos, MaterialId> sailMaterials = new HashMap<>();

    // ── Melt progress per blade (Part D) ─────────────────────────────────────

    /**
     * Per-local-position melt progress in [0.0, 1.0]. Absent = not melting.
     * Driven by the fin-melt tick driver (Part D, in {@code WindmillBurnTickMixin}
     * or its sibling). At 1.0 the blade's aero contribution to the windmill
     * aggregate is dropped to 0.
     * <p>
     * Uses float accumulation like {@code PhReactivity.strengthFractionAt}'s
     * corrosion convention in MisWorld — same [0,1] vocabulary, same
     * monotonically-increasing-until-threshold semantics.
     */
    private final Map<BlockPos, Float> meltProgress = new HashMap<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    public MisanthropeWindmillContraption() {
        super();
    }

    public MisanthropeWindmillContraption(boolean isWindmill, Direction facing) {
        super(isWindmill, facing);
    }

    // ── Registry identity ─────────────────────────────────────────────────────

    @Override
    public com.simibubi.create.api.contraption.ContraptionType getType() {
        return MisanthropeContraptionTypes.get();
    }

    // ── Lifecycle helpers (called by MixinBearingContraptionMaterial) ─────────

    /**
     * Clears all per-blade maps before a (re-)assembly. Called at HEAD of
     * {@code assemble} by {@code MixinBearingContraptionMaterial}.
     */
    public void clearPerBladeMaps() {
        burnStages.clear();
        burnTicksRemaining.clear();
        sailMaterials.clear();
        meltProgress.clear();
    }

    // ── Burn state API ────────────────────────────────────────────────────────

    public boolean isBurning(BlockPos localPos) {
        return burnStages.containsKey(localPos);
    }

    public int getBurnStage(BlockPos localPos) {
        return burnStages.getOrDefault(localPos, 0);
    }

    public void igniteAt(BlockPos localPos, int ticksUntilNextStage) {
        burnStages.put(localPos, 0);
        burnTicksRemaining.put(localPos, ticksUntilNextStage);
    }

    public void advanceStage(BlockPos localPos, int newStage, int ticksUntilNextStage) {
        burnStages.put(localPos, newStage);
        burnTicksRemaining.put(localPos, ticksUntilNextStage);
    }

    public void clearBurn(BlockPos localPos) {
        burnStages.remove(localPos);
        burnTicksRemaining.remove(localPos);
    }

    public Map<BlockPos, Integer> getBurnStagesView() { return burnStages; }
    public Map<BlockPos, Integer> getBurnTicksRemainingView() { return burnTicksRemaining; }

    // ── TiC material API ──────────────────────────────────────────────────────

    /** Returns the TiC material for this local-position blade, or null. */
    public MaterialId getSailMaterial(BlockPos localPos) {
        return sailMaterials.get(localPos);
    }

    /** Read-only view of all blade materials (local pos → MaterialId). */
    public Map<BlockPos, MaterialId> getSailMaterialsView() {
        return sailMaterials;
    }

    // ── Melt progress API ─────────────────────────────────────────────────────

    public float getMeltProgress(BlockPos localPos) {
        return meltProgress.getOrDefault(localPos, 0f);
    }

    public void addMeltProgress(BlockPos localPos, float delta) {
        float current = meltProgress.getOrDefault(localPos, 0f);
        meltProgress.put(localPos, Math.min(1f, current + delta));
    }

    public boolean isFullyMelted(BlockPos localPos) {
        return meltProgress.getOrDefault(localPos, 0f) >= 1f;
    }

    public void clearMelt(BlockPos localPos) {
        meltProgress.remove(localPos);
    }

    public Map<BlockPos, Float> getMeltProgressView() { return meltProgress; }

    // ── NBT round-trip ────────────────────────────────────────────────────────

    private static final String NBT_BURN_LIST     = "MisanthropeBurnState";
    private static final String NBT_MATERIAL_LIST = "MisanthropeSailMaterials";
    private static final String NBT_MELT_LIST     = "MisanthropeMeltProgress";
    private static final String NBT_POS           = "Pos";
    private static final String NBT_STAGE         = "Stage";
    private static final String NBT_TICKS         = "Ticks";
    private static final String NBT_MATERIAL      = "Material";
    private static final String NBT_MELT          = "Melt";

    @Override
    public void readNBT(Level level, CompoundTag tag, boolean clientPacket) {
        super.readNBT(level, tag, clientPacket);

        burnStages.clear();
        burnTicksRemaining.clear();
        sailMaterials.clear();
        meltProgress.clear();

        // Burn state
        if (tag.contains(NBT_BURN_LIST)) {
            ListTag list = tag.getList(NBT_BURN_LIST, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                BlockPos pos = BlockPos.of(entry.getLong(NBT_POS));
                burnStages.put(pos, entry.getInt(NBT_STAGE));
                burnTicksRemaining.put(pos, entry.getInt(NBT_TICKS));
            }
        }

        // TiC sail materials
        if (tag.contains(NBT_MATERIAL_LIST)) {
            ListTag list = tag.getList(NBT_MATERIAL_LIST, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                BlockPos pos = BlockPos.of(entry.getLong(NBT_POS));
                String matStr = entry.getString(NBT_MATERIAL);
                if (!matStr.isEmpty()) {
                    try {
                        sailMaterials.put(pos, new MaterialId(new ResourceLocation(matStr)));
                    } catch (Exception ignored) {
                        // malformed id — skip silently rather than crash reload
                    }
                }
            }
        }

        // Melt progress
        if (tag.contains(NBT_MELT_LIST)) {
            ListTag list = tag.getList(NBT_MELT_LIST, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                BlockPos pos = BlockPos.of(entry.getLong(NBT_POS));
                meltProgress.put(pos, entry.getFloat(NBT_MELT));
            }
        }
    }

    @Override
    public CompoundTag writeNBT(boolean clientPacket) {
        CompoundTag tag = super.writeNBT(clientPacket);

        // Burn state
        ListTag burnList = new ListTag();
        for (Map.Entry<BlockPos, Integer> e : burnStages.entrySet()) {
            BlockPos pos = e.getKey();
            CompoundTag entry = new CompoundTag();
            entry.putLong(NBT_POS, pos.asLong());
            entry.putInt(NBT_STAGE, e.getValue());
            entry.putInt(NBT_TICKS, burnTicksRemaining.getOrDefault(pos, 0));
            burnList.add(entry);
        }
        tag.put(NBT_BURN_LIST, burnList);

        // TiC sail materials (skip if empty — common case, cloth-only windmill)
        if (!sailMaterials.isEmpty()) {
            ListTag matList = new ListTag();
            for (Map.Entry<BlockPos, MaterialId> e : sailMaterials.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putLong(NBT_POS, e.getKey().asLong());
                entry.putString(NBT_MATERIAL, e.getValue().toString());
                matList.add(entry);
            }
            tag.put(NBT_MATERIAL_LIST, matList);
        }

        // Melt progress (skip if empty — most windmills are never melting)
        if (!meltProgress.isEmpty()) {
            ListTag meltList = new ListTag();
            for (Map.Entry<BlockPos, Float> e : meltProgress.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putLong(NBT_POS, e.getKey().asLong());
                entry.putFloat(NBT_MELT, e.getValue());
                meltList.add(entry);
            }
            tag.put(NBT_MELT_LIST, meltList);
        }

        return tag;
    }
}
