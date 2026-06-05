package exp.CCnewmods.mge.grid;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.dimension.DimensionAtmosphereLoader;
import exp.CCnewmods.mge.dimension.DimensionAtmosphereProfile;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.section.EnvironmentSection;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Forge event handler that:
 *   1. Registers the {@link EnvironmentChunkData} capability.
 *   2. Attaches an {@link EnvironmentChunkData} instance to every {@link LevelChunk}.
 *   3. Saves and loads section data alongside chunk NBT.
 *   4. Initialises new sections from the dimension atmosphere profile on load.
 *   5. Informs {@link SectionLoadManager} when chunks load and unload.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GridCapabilityHandler {

    private static final ResourceLocation CAP_ID =
            new ResourceLocation(Mge.MODID, "environment");

    private GridCapabilityHandler() {}

    // ── Capability registration (mod bus) ─────────────────────────────────────

    @Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {
        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(EnvironmentChunkData.class);
        }
    }

    // ── Attachment (forge bus) ─────────────────────────────────────────────────

    @SubscribeEvent
    public static void onAttachChunkCapabilities(AttachCapabilitiesEvent<LevelChunk> event) {
        LevelChunk chunk = event.getObject();
        if (chunk.getLevel() == null) return;
        // Create's schematic preview uses a fake level whose dimension() returns null.
        // Attaching the gas grid to such chunks is meaningless, so bail out early.
        if (chunk.getLevel().dimension() == null) return;

        ResourceLocation dimId = chunk.getLevel().dimension().location();
        EnvironmentChunkData data = new EnvironmentChunkData(chunk, dimId);

        event.addCapability(CAP_ID, new ICapabilityProvider() {
            private final LazyOptional<EnvironmentChunkData> opt = LazyOptional.of(() -> data);

            @Nonnull @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap,
                                                      @Nullable Direction side) {
                return cap == EnvironmentChunkData.CAP ? opt.cast() : LazyOptional.empty();
            }
        });
    }

    // ── Save / Load ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onChunkDataSave(ChunkDataEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        chunk.getCapability(EnvironmentChunkData.CAP).ifPresent(data -> {
            if (!data.isDirty()) return;
            CompoundTag tag = data.serializeNBT();
            if (!tag.isEmpty()) {
                event.getData().put(CAP_ID.toString(), tag);
            }
        });
    }

    @SubscribeEvent
    public static void onChunkDataLoad(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        CompoundTag envTag = event.getData().getCompound(CAP_ID.toString());
        if (envTag.isEmpty()) return;
        chunk.getCapability(EnvironmentChunkData.CAP).ifPresent(data -> {
            data.deserializeNBT(envTag);
        });
    }

    // ── Chunk load/unload ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        chunk.getCapability(EnvironmentChunkData.CAP).ifPresent(data -> {
            // Initialise unallocated sections from dimension profile
            ResourceLocation dimId = level.dimension().location();
            DimensionAtmosphereProfile profile = DimensionAtmosphereLoader.get(dimId);
            if (profile == null) profile = DimensionAtmosphereLoader.STANDARD_OVERWORLD_FALLBACK;

            // Notify the section load manager — it will handle tick priority and
            // Unloaded Activity catch-up simulation
            SectionLoadManager.onChunkLoad(level, chunk, data);
        });
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        SectionLoadManager.onChunkUnload(level, chunk);
    }
}
