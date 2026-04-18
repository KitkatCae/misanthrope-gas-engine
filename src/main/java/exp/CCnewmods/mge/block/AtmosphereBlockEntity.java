package exp.CCnewmods.mge.block;

import exp.CCnewmods.mge.fluid.AtmosphereFluidHandler;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for {@link AtmosphereBlock}.
 * Holds both a {@link GasComposition} (gases in mbar) and a
 * {@link ParticulateComposition} (airborne particulates in mg/m³).
 */
public class AtmosphereBlockEntity extends BlockEntity {

    private GasComposition gasComposition;
    private ParticulateComposition particulateComposition;
    private boolean dirty = false;

    // IFluidHandler capability — lazily created, invalidated on chunk unload
    private LazyOptional<IFluidHandler> fluidHandlerCap = LazyOptional.empty();

    public AtmosphereBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(MgeBlockEntities.ATMOSPHERE.get(), pPos, pBlockState);
        this.gasComposition = GasComposition.standard();
        this.particulateComposition = ParticulateComposition.empty();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(
            @NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (!fluidHandlerCap.isPresent()) {
                fluidHandlerCap = LazyOptional.of(() -> new AtmosphereFluidHandler(this));
            }
            return fluidHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidHandlerCap.invalidate();
        fluidHandlerCap = LazyOptional.empty();
    }

    // ── Gas ──────────────────────────────────────────────────────────────────

    public GasComposition getComposition() { return gasComposition; }

    public void setComposition(GasComposition c) {
        this.gasComposition = c;
        markDirtyAndUpdate();
    }

    // ── Particulates ─────────────────────────────────────────────────────────

    public ParticulateComposition getParticulates() { return particulateComposition; }

    public void setParticulates(ParticulateComposition p) {
        this.particulateComposition = p;
        markDirtyAndUpdate();
    }

    // ── Dirty tracking ───────────────────────────────────────────────────────

    public boolean isDirtyForTick() { return dirty; }
    public void clearDirtyFlag() { dirty = false; }

    private void markDirtyAndUpdate() {
        dirty = true;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
        gasComposition.writeTo(pTag);
        particulateComposition.writeTo(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        gasComposition = GasComposition.readFrom(pTag);
        particulateComposition = ParticulateComposition.readFrom(pTag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) { load(tag); }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
