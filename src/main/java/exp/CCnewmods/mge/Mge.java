package exp.CCnewmods.mge;

import com.mojang.logging.LogUtils;
import exp.CCnewmods.mge.compat.BeyondOxygenCompat;
import exp.CCnewmods.mge.compat.ProjectAtmosphereCompat;
import exp.CCnewmods.mge.compat.ThermodynamicaCompat;
import exp.CCnewmods.mge.compat.ColdSweatCompat;
import exp.CCnewmods.mge.compat.WitherStormCompat;
import exp.CCnewmods.mge.compat.SupplementariesCompat;
import exp.CCnewmods.mge.compat.CreateCompat;
import exp.CCnewmods.mge.compat.TfmgCompat;
import exp.CCnewmods.mge.compat.BurntCompat;
import exp.CCnewmods.mge.compat.OreganizedCompat;
import exp.CCnewmods.mge.compat.ChemicaCompat;
import exp.CCnewmods.mge.compat.MisanthropeWorldCompat;
import exp.CCnewmods.mge.compat.PneumaticCraftCompat;
import exp.CCnewmods.mge.compat.IceAndFireCompat;
import exp.CCnewmods.mge.compat.TwilightForestCompat;
import exp.CCnewmods.mge.compat.MowziesCompat;
import exp.CCnewmods.mge.compat.AlexsCavesCompat;
import exp.CCnewmods.mge.compat.AlexsMobsCompat;
import exp.CCnewmods.mge.compat.BetterNetherEndCompat;
import exp.CCnewmods.mge.compat.BornInChaosCompat;
import exp.CCnewmods.mge.compat.BossesMassDestructionCompat;
import exp.CCnewmods.mge.compat.BossesRiseCompat;
import exp.CCnewmods.mge.compat.CrazinessAwakenedCompat;
import exp.CCnewmods.mge.compat.MonsterExpansionCompat;
import exp.CCnewmods.mge.compat.WitherStormMobCompat;
import exp.CCnewmods.mge.compat.LegendaryMonstersCompat;
import exp.CCnewmods.mge.compat.BoxOfHorrorsCompat;
import exp.CCnewmods.mge.compat.IntenseHorrorCompat;
import exp.CCnewmods.mge.compat.RediscoveredCompat;
import exp.CCnewmods.mge.compat.MutantMonstersCompat;
import exp.CCnewmods.mge.compat.NethersExorcismCompat;
import exp.CCnewmods.mge.compat.FDBossesCompat;
import exp.CCnewmods.mge.compat.MoreCrittersCompat;
import exp.CCnewmods.mge.compat.DragonMountsCompat;
import exp.CCnewmods.mge.compat.SandwormModCompat;
import exp.CCnewmods.mge.compat.SaintsDragonsCompat;
import exp.CCnewmods.mge.compat.DrakvyrnCompat;
import exp.CCnewmods.mge.compat.BlueSkiesCompat;
import exp.CCnewmods.mge.compat.TheRavenousCompat;
import exp.CCnewmods.mge.compat.ThreateninglyMobsCompat;
import exp.CCnewmods.mge.compat.RatsCompat;
import exp.CCnewmods.mge.compat.CataclysmCompat;
import exp.CCnewmods.mge.compat.RealmsOfRedemptionCompat;
import exp.CCnewmods.mge.compat.MutantMoreCompat;
import exp.CCnewmods.mge.compat.TerramityCompat;
import exp.CCnewmods.mge.compat.OpposingForceCompat;
import exp.CCnewmods.mge.compat.GliderCompatRegistry;
import exp.CCnewmods.mge.compat.SlimePressureCompat;
import exp.CCnewmods.mge.fluid.GasFluidRegistry;
import exp.CCnewmods.mge.cave.CaveGasAccumulator;
import exp.CCnewmods.mge.permeability.BlockPermeabilityLoader;
import exp.CCnewmods.mge.vacuum.VacuumHandler;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.breathing.ActiveBreathingHandler;
import exp.CCnewmods.mge.breathing.EntityBreathingLoader;
import exp.CCnewmods.mge.dimension.DimensionAtmosphereLoader;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.SectionLoadManager;
import exp.CCnewmods.mge.grid.GridCapabilityHandler;
import exp.CCnewmods.mge.grid.tick.SectionDiffusionTicker;
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

    public Mge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        BLOCKS.register(modBus);
        exp.CCnewmods.mge.sail.MisanthropeSailBlocks.touch();
        GasFluidRegistry.registerAll(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MgeConfig.SPEC);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);
        modBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(GridCapabilityHandler.class);
        MinecraftForge.EVENT_BUS.register(SectionLoadManager.class);
        ShockwaveDataPacket.register();

        // Force gas registry init
        LOGGER.info("[MGE] Initialising — {} gases registered.", GasRegistry.all().size());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Order matters: PA first (provides wind), then Thermo (uses temps),
            // then CS (listens to entity temperatures and calls into Thermo optionally)
            DimensionAtmosphereLoader.INSTANCE.getClass(); // ensure class loads
            EntityBreathingLoader.INSTANCE.getClass();    // ensure class loads
            exp.CCnewmods.mge.event.VanillaMobAtmosphereHandler.class.getName();
            exp.CCnewmods.mge.event.MobDeathAtmosphereHandler.class.getName();
            exp.CCnewmods.mge.spore.SporeGrowthHandler.class.getName();
            CaveGasAccumulator.class.getName();           // ensure class loads
            VacuumHandler.class.getName();
            BlockPermeabilityLoader.INSTANCE.getClass();
            exp.CCnewmods.mge.event.BlockGasReactionHandler.class.getName();
            exp.CCnewmods.mge.event.BreathWeaponHandler.class.getName();
            exp.CCnewmods.mge.mirage.MirageStructureLoader.INSTANCE.getClass();
            ProjectAtmosphereCompat.tryLoad();
            ThermodynamicaCompat.tryLoad();
            ColdSweatCompat.tryLoad();
            BeyondOxygenCompat.tryLoad();
            WitherStormCompat.tryLoad();
            SupplementariesCompat.tryLoad();
            CreateCompat.tryLoad();
            exp.CCnewmods.mge.contraption.MisanthropeContraptionTypes.register();
            TfmgCompat.tryLoad();
            BurntCompat.tryLoad();
            OreganizedCompat.tryLoad();
            MisanthropeWorldCompat.tryLoad();
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient())
                exp.CCnewmods.mge.photon.MgePhotonEffects.tryLoad();
            PneumaticCraftCompat.tryLoad();
            ChemicaCompat.tryLoad();
            IceAndFireCompat.tryLoad();
            TwilightForestCompat.tryLoad();
            MowziesCompat.tryLoad();
            AlexsCavesCompat.tryLoad();
            AlexsMobsCompat.tryLoad();
            BetterNetherEndCompat.tryLoad();
            SlimePressureCompat.tryLoad();
            BornInChaosCompat.tryLoad();
            BossesMassDestructionCompat.tryLoad();
            BossesRiseCompat.tryLoad();
            CrazinessAwakenedCompat.tryLoad();
            MonsterExpansionCompat.tryLoad();
            WitherStormMobCompat.tryLoad();
            LegendaryMonstersCompat.tryLoad();
            BoxOfHorrorsCompat.tryLoad();
            IntenseHorrorCompat.tryLoad();
            RediscoveredCompat.tryLoad();
            MutantMonstersCompat.tryLoad();
            NethersExorcismCompat.tryLoad();
            FDBossesCompat.tryLoad();
            MoreCrittersCompat.tryLoad();
            DragonMountsCompat.tryLoad();
            SandwormModCompat.tryLoad();
            SaintsDragonsCompat.tryLoad();
            DrakvyrnCompat.tryLoad();
            BlueSkiesCompat.tryLoad();
            TheRavenousCompat.tryLoad();
            ThreateninglyMobsCompat.tryLoad();
            RatsCompat.tryLoad();
            CataclysmCompat.tryLoad();
            RealmsOfRedemptionCompat.tryLoad();
            TerramityCompat.tryLoad();
            OpposingForceCompat.tryLoad();
            GliderCompatRegistry.tryLoad();
            LOGGER.info("[MGE] Common setup complete.");
        });
    }

    private void loadComplete(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        // Resolve cross-mod gas↔fluid aliases after all mods have registered their fluids
        event.enqueueWork(GasFluidRegistry::resolveAliases);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.addListener(Mge::onClientTick);
            MinecraftForge.EVENT_BUS.register(
                    exp.CCnewmods.mge.render.DesertMirageRenderer.class);
            MinecraftForge.EVENT_BUS.register(
                    exp.CCnewmods.mge.mirage.MirageRenderer.class);
            LOGGER.info("[MGE] Client setup complete.");
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[MGE] Server starting.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        exp.CCnewmods.mge.breathing.BreathingTracker.clear();
        event.getServer().getAllLevels().forEach(ShockwaveHandler::onLevelUnload);
        LOGGER.info("[MGE] Server stopping — schedulers cleared.");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            // Set thread-local so SectionDiffusionTicker knows which level it is ticking
            SectionDiffusionTicker.currentLevel.set(level);
            SectionDiffusionTicker.currentLevel.remove();
        }
        ActiveBreathingHandler.onServerTick(event, event.getServer());
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (MgeConfig.enableAtmosphereRenderer) AtmosphereRenderer.clientTick();
        exp.CCnewmods.mge.shockwave.ShockwaveDistortionRenderer.clientTick();
        exp.CCnewmods.mge.render.DesertMirageRenderer.clientTick();
        exp.CCnewmods.mge.mirage.MirageRenderer.clientTick();
    }
}
