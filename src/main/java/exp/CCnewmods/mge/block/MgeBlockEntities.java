package exp.CCnewmods.mge.block;

import exp.CCnewmods.mge.Mge;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MgeBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Mge.MODID);

    public static final RegistryObject<BlockEntityType<AtmosphereBlockEntity>> ATMOSPHERE =
            BLOCK_ENTITIES.register("atmosphere", () ->
                    BlockEntityType.Builder
                            .of(AtmosphereBlockEntity::new, Mge.ATMOSPHERE_BLOCK.get())
                            .build(null));

    private MgeBlockEntities() {}
}
