package exp.CCnewmods.mge;

import com.mojang.logging.LogUtils;
import exp.CCnewmods.mge.block.AtmosphereBlock;
import exp.CCnewmods.mge.block.MgeBlockEntities;
import exp.CCnewmods.mge.compat.BeyondOxygenCompat;
import exp.CCnewmods.mge.compat.ColdSweatCompat;
import exp.CCnewmods.mge.compat.WitherStormCompat;
import exp.CCnewmods.mge.breathing.ActiveBreathingHandler;
import exp.CCnewmods.mge.breathing.EntityBreathingLoader;
import exp.CCnewmods.mge.dimension.DimensionAtmosphereLoader;
import exp.CCnewmods.mge.compat.ProjectAtmosphereCompat;
import exp.CCnewmods.mge.compat.ThermodynamicaCompat;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.propagation.AtmosphereTickScheduler;
import exp.CCnewmods.mge.render.AtmosphereRenderer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Mod(Mge.MODID)
public class Mge {

    public static final String MODID = "mge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    public static final RegistryObject<AtmosphereBlock> ATMOSPHERE_BLOCK =
            BLOCKS.register("atmosphere", AtmosphereBlock::new);

    private static final Map<ServerLevel, AtmosphereTickScheduler> SCHEDULERS = new HashMap<>();

    public static AtmosphereTickScheduler getScheduler(ServerLevel level) {
        return SCHEDULERS.computeIfAbsent(level, AtmosphereTickScheduler::new);
    }

    public Mge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        BLOCKS.register(modBus);
        MgeBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MgeConfig.SPEC);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);

        // Force gas registry init
        LOGGER.info("[MGE] Initialising — {} gases registered.", GasRegistry.all().size());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Order matters: PA first (provides wind), then Thermo (uses temps),
            // then CS (listens to entity temperatures and calls into Thermo optionally)
            DimensionAtmosphereLoader.INSTANCE.getClass(); // ensure class loads
            EntityBreathingLoader.INSTANCE.getClass();    // ensure class loads
            ProjectAtmosphereCompat.tryLoad();
            ThermodynamicaCompat.tryLoad();
            ColdSweatCompat.tryLoad();
            BeyondOxygenCompat.tryLoad();
            WitherStormCompat.tryLoad();
            LOGGER.info("[MGE] Common setup complete.");
        });
    }

    private void clientSetup(FMLClientSetupEvent event) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.addListener(Mge::onClientTick);
            LOGGER.info("[MGE] Client setup complete.");
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[MGE] Server starting.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        SCHEDULERS.clear();
        exp.CCnewmods.mge.breathing.BreathingTracker.clear();
        LOGGER.info("[MGE] Server stopping — schedulers cleared.");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            getScheduler(level).tick();
        }
        ActiveBreathingHandler.onServerTick(event, event.getServer());
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (MgeConfig.enableAtmosphereRenderer) AtmosphereRenderer.clientTick();
    }
}
