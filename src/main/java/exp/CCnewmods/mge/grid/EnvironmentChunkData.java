package exp.CCnewmods.mge.grid;

import exp.CCnewmods.mge.dimension.DimensionAtmosphereLoader;
import exp.CCnewmods.mge.dimension.DimensionAtmosphereProfile;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.section.EnvironmentSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Forge capability attached to every loaded {@link net.minecraft.world.level.chunk.LevelChunk}.
 *
 * Stores one {@link EnvironmentSection} per 16-block Y slice of the chunk column.
 * Section count = (worldHeight / 16), matching Minecraft's {@link net.minecraft.world.level.chunk.LevelChunkSection} geometry exactly.
 *
 * ── Attachment ────────────────────────────────────────────────────────────────
 * Attached in {@link exp.CCnewmods.mge.grid.GridCapabilityHandler} via
 * {@link net.minecraftforge.event.AttachCapabilitiesEvent}.
 *
 * ── Access ────────────────────────────────────────────────────────────────────
 * Use {@link EnvironmentGrid#getSection(net.minecraft.world.level.Level, BlockPos)}
 * for the primary public API.  Direct capability access via
 * {@code chunk.getCapability(EnvironmentChunkData.CAP)}.
 *
 * ── Section indexing ──────────────────────────────────────────────────────────
 * sectionIndex = (worldY - minBuildHeight) >> 4
 * sectionBottomY = minBuildHeight + sectionIndex * 16
 */
public final class EnvironmentChunkData implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<EnvironmentChunkData> CAP =
            CapabilityManager.get(new CapabilityToken<>() {});

    private final LazyOptional<EnvironmentChunkData> self = LazyOptional.of(() -> this);

    // ── Section storage ───────────────────────────────────────────────────────

    private final EnvironmentSection[] sections;
    private final int minBuildHeight;
    private final int sectionCount;
    @Nullable private DimensionAtmosphereProfile profile;

    // ── Constructor ───────────────────────────────────────────────────────────

    public EnvironmentChunkData(LevelHeightAccessor heightAccessor,
                                @Nullable ResourceLocation dimensionId) {
        this.minBuildHeight = heightAccessor.getMinBuildHeight();
        this.sectionCount   = heightAccessor.getSectionsCount();
        this.sections       = new EnvironmentSection[sectionCount];
        this.profile        = dimensionId != null
                ? DimensionAtmosphereLoader.get(dimensionId) : null;
        if (this.profile == null)
            this.profile = DimensionAtmosphereLoader.STANDARD_OVERWORLD_FALLBACK;
    }

    // ── Section access ────────────────────────────────────────────────────────

    /**
     * Returns the section for the given world Y, creating it lazily if needed.
     */
    public EnvironmentSection getOrCreate(int worldY) {
        int idx = sectionIndex(worldY);
        if (idx < 0 || idx >= sectionCount) return null;
        if (sections[idx] == null) {
            int bottomY = minBuildHeight + idx * 16;
            sections[idx] = new EnvironmentSection(bottomY);
            sections[idx].setDimensionProfile(profile);
        }
        return sections[idx];
    }

    /**
     * Returns the section for the given world Y, or null if not yet created.
     * Does NOT allocate. Use for read-only queries where null → return default.
     */
    @Nullable
    public EnvironmentSection get(int worldY) {
        int idx = sectionIndex(worldY);
        if (idx < 0 || idx >= sectionCount) return null;
        return sections[idx];
    }

    /** Returns section by index (0 = bottom of world). */
    @Nullable
    public EnvironmentSection getByIndex(int idx) {
        if (idx < 0 || idx >= sectionCount) return null;
        return sections[idx];
    }

    public int sectionCount() { return sectionCount; }

    // ── Convenience gas/temp reads ────────────────────────────────────────────

    /**
     * Get the partial pressure of a gas at the given world position.
     * Returns the dimension default if the section hasn't been allocated yet.
     */
    public float getGas(Gas gas, BlockPos pos) {
        EnvironmentSection sec = get(pos.getY());
        if (sec == null) return defaultGas(gas);
        return sec.getGas(gas, pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15);
    }

    /**
     * Get the full GasComposition at a world position.
     * Constructs a new GasComposition; do not call on the hot path.
     */
    public GasComposition getComposition(BlockPos pos) {
        EnvironmentSection sec = get(pos.getY());
        if (sec == null) return defaultComposition();
        return sec.getComposition(pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15);
    }

    /**
     * Get temperature in °C at a world position.
     * Returns {@link EnvironmentSection#AMBIENT_SENTINEL} (NaN) if no explicit value.
     */
    public float getTemperature(BlockPos pos) {
        EnvironmentSection sec = get(pos.getY());
        if (sec == null) return EnvironmentSection.AMBIENT_SENTINEL;
        return sec.getTemperature(pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15);
    }

    // ── Convenience gas/temp writes ───────────────────────────────────────────

    public void setGas(Gas gas, BlockPos pos, float mbar) {
        EnvironmentSection sec = getOrCreate(pos.getY());
        if (sec == null) return;
        sec.setGas(gas, pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15, mbar);
    }

    public void addGas(Gas gas, BlockPos pos, float deltaMbar) {
        EnvironmentSection sec = getOrCreate(pos.getY());
        if (sec == null) return;
        sec.addGas(gas, pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15, deltaMbar);
    }

    public void setComposition(BlockPos pos, GasComposition comp) {
        EnvironmentSection sec = getOrCreate(pos.getY());
        if (sec == null) return;
        sec.setComposition(pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15, comp);
    }

    public void setTemperature(BlockPos pos, float celsius) {
        EnvironmentSection sec = getOrCreate(pos.getY());
        if (sec == null) return;
        sec.setTemperature(pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15, celsius);
    }

    // ── Convenience particulate reads/writes ──────────────────────────────────

    public float getParticulate(ParticulateType type, BlockPos pos) {
        EnvironmentSection sec = get(pos.getY());
        if (sec == null) return 0f;
        return sec.getParticulate(type, pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15);
    }

    public ParticulateComposition getParticulates(BlockPos pos) {
        EnvironmentSection sec = get(pos.getY());
        if (sec == null) return ParticulateComposition.empty();
        return sec.getParticulates(pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15);
    }

    public void addParticulate(ParticulateType type, BlockPos pos, float mgM3) {
        EnvironmentSection sec = getOrCreate(pos.getY());
        if (sec == null) return;
        sec.addParticulate(type, pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15, mgM3);
    }

    public void setParticulates(BlockPos pos, ParticulateComposition comp) {
        EnvironmentSection sec = getOrCreate(pos.getY());
        if (sec == null) return;
        sec.setParticulates(pos.getX() & 15, localY(pos.getY()), pos.getZ() & 15, comp);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int sectionIndex(int worldY) {
        return (worldY - minBuildHeight) >> 4;
    }

    private int localY(int worldY) {
        return (worldY - minBuildHeight) & 15;
    }

    private float defaultGas(Gas gas) {
        if (profile == null) return GasRegistry.standardAtmosphere().getOrDefault(gas, 0f);
        Float val = profile.gases.get(gas.id().toString());
        return val != null ? val : 0f;
    }

    private GasComposition defaultComposition() {
        return profile != null ? profile.createGasComposition() : GasComposition.standard();
    }

    // ── Dirty tracking ────────────────────────────────────────────────────────

    /**
     * Returns true if any section in this chunk has unsaved changes.
     * Called by chunk serialisation to decide whether to include env data.
     */
    public boolean isDirty() {
        for (EnvironmentSection sec : sections) {
            if (sec != null && sec.dirty) return true; // includes particulate dirty
        }
        return false;
    }

    // ── Capability ────────────────────────────────────────────────────────────

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap,
                                             @Nullable Direction side) {
        return cap == CAP ? self.cast() : LazyOptional.empty();
    }

    public void invalidateCaps() { self.invalidate(); }

    // ── NBT serialisation ─────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < sections.length; i++) {
            EnvironmentSection sec = sections[i];
            if (sec == null) continue;
            if (!sec.hasAnyGasData() && !sec.hasAnyTempData() && !sec.hasAnyParticulateData()) continue;
            CompoundTag secTag = sec.save();
            secTag.putInt("SectionIdx", i);
            list.add(secTag);
        }
        if (!list.isEmpty()) root.put("Sections", list);
        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag root) {
        if (!root.contains("Sections", Tag.TAG_LIST)) return;
        ListTag list = root.getList("Sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag secTag = list.getCompound(i);
            int idx = secTag.getInt("SectionIdx");
            if (idx < 0 || idx >= sectionCount) continue;
            int bottomY = minBuildHeight + idx * 16;
            sections[idx] = EnvironmentSection.load(secTag, bottomY, profile);
        }
    }
}
