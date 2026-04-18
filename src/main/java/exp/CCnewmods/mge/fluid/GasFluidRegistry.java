package exp.CCnewmods.mge.fluid;

import com.simibubi.create.content.fluids.VirtualFluid;
import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers one {@link VirtualFluid} per MGE gas so that any Create-compatible
 * pipe/tank system can interact with atmospheric gases as fluid stacks.
 *
 * <h3>Conversion scale</h3>
 * 1 mB of virtual fluid = 1 mbar of partial pressure.
 *
 * <h3>Cross-mod aliasing</h3>
 * When Chemica or PNC registers a fluid for the same gas, the alias table
 * redirects extraction to that fluid so pipes see one unified type.
 * MGE's own VirtualFluids are the fallback when no alias exists.
 */
public final class GasFluidRegistry {

    public static final DeferredRegister<Fluid>     FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, Mge.MODID);
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, Mge.MODID);

    // gas RL → our RegistryObject<VirtualFluid>
    private static final Map<ResourceLocation, RegistryObject<VirtualFluid>> MGE_FLUIDS
            = new LinkedHashMap<>();

    // gas RL → preferred fluid RL (may be from another mod, resolved at load-complete)
    private static final Map<ResourceLocation, ResourceLocation> PREFERRED_FLUID
            = new ConcurrentHashMap<>();

    // Known cross-mod aliases: gas path → ordered list of candidate fluid RLs
    private static final Map<String, List<ResourceLocation>> KNOWN_ALIASES
            = new LinkedHashMap<>();

    static {
        alias("oxygen",           "chemica:oxygen",         "pneumaticcraft:oxygen");
        alias("nitrogen",         "chemica:nitrogen",        "pneumaticcraft:nitrogen");
        alias("carbon_dioxide",   "tfmg:carbon_dioxide");
        alias("carbon_monoxide",  "chemica:carbon_monoxide");
        alias("methane",          "chemica:methane");
        alias("argon",            "chemica:argon");
        alias("ammonia",          "chemica:ammonia");
        alias("chlorine",         "chemica:chlorine");
        alias("fluorine",         "chemica:fluorine");
        alias("hydrogen",         "pneumaticcraft:hydrogen");
        alias("nitrous_oxide",    "chemica:nitrous_oxide");
        alias("nitrogen_dioxide", "chemica:nitrogen_dioxide");
        alias("water_vapor",      "chemica:steam",           "minecraft:water");
        alias("helium",           "chemica:helium");
        alias("ethane",           "chemica:ethane");
        alias("propane",          "chemica:propene");
    }

    private static void alias(String gasPath, String... fluids) {
        List<ResourceLocation> rls = new ArrayList<>();
        for (String f : fluids) {
            String[] p = f.split(":", 2);
            rls.add(new ResourceLocation(p[0], p[1]));
        }
        KNOWN_ALIASES.put(gasPath, rls);
    }

    private GasFluidRegistry() {}

    /**
     * Registers VirtualFluids for all MGE gases. Call from {@link Mge} constructor
     * before the event bus runs.
     */
    public static void registerAll(IEventBus bus) {
        for (Gas gas : GasRegistry.all()) {
            ResourceLocation gasId  = gas.id();
            String fluidPath        = "gas_" + gasId.getPath();

            // Register FluidType first
            RegistryObject<FluidType> typeRO = FLUID_TYPES.register(fluidPath,
                    () -> new MgeGasFluidType(gas, FluidType.Properties.create()));

            // Then register the VirtualFluid referencing the type supplier
            // RegistryObject holder so the Properties supplier can reference itself
            @SuppressWarnings("unchecked")
            RegistryObject<VirtualFluid>[] holder = new RegistryObject[1];
            holder[0] = FLUIDS.register(fluidPath, () -> {
                ForgeFlowingFluid.Properties props = new ForgeFlowingFluid.Properties(
                        typeRO,
                        () -> holder[0].get(),
                        () -> holder[0].get());
                return VirtualFluid.createSource(props);
            });
            RegistryObject<VirtualFluid> fluidRO = holder[0];

            MGE_FLUIDS.put(gasId, fluidRO);
        }

        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Alias resolution — called in FMLLoadCompleteEvent after all mods register
    // ─────────────────────────────────────────────────────────────────────────

    public static void resolveAliases() {
        KNOWN_ALIASES.forEach((gasPath, candidates) -> {
            ResourceLocation gasId = new ResourceLocation(Mge.MODID, gasPath);
            for (ResourceLocation candidate : candidates) {
                if (ForgeRegistries.FLUIDS.containsKey(candidate)) {
                    PREFERRED_FLUID.put(gasId, candidate);
                    Mge.LOGGER.debug("[MGE] Gas fluid alias: {} → {}", gasId, candidate);
                    return;
                }
            }
            // Fallback: use MGE's own VirtualFluid
            PREFERRED_FLUID.put(gasId,
                    new ResourceLocation(Mge.MODID, "gas_" + gasPath));
        });

        // Also register fallback for gases with no alias entry
        for (Map.Entry<ResourceLocation, RegistryObject<VirtualFluid>> e : MGE_FLUIDS.entrySet()) {
            PREFERRED_FLUID.computeIfAbsent(e.getKey(),
                    k -> new ResourceLocation(Mge.MODID,
                            "gas_" + k.getPath()));
        }

        Mge.LOGGER.info("[MGE] Gas fluid aliases resolved ({} gases).", PREFERRED_FLUID.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns a FluidStack for {@code mbar} mB of the given gas, using the preferred fluid. */
    public static Optional<FluidStack> gasToFluid(Gas gas, int mbar) {
        if (mbar <= 0) return Optional.empty();
        ResourceLocation gasId   = gas.id();
        ResourceLocation fluidId = PREFERRED_FLUID.getOrDefault(gasId,
                new ResourceLocation(Mge.MODID, "gas_" + gasId.getPath()));
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid == null) return Optional.empty();
        return Optional.of(new FluidStack(fluid, mbar));
    }

    /** Resolves a Fluid back to an MGE Gas via the alias table or name matching. */
    public static Optional<Gas> fluidToGas(Fluid fluid) {
        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
        if (fluidId == null) return Optional.empty();

        // Inverse lookup through preferred map
        for (Map.Entry<ResourceLocation, ResourceLocation> e : PREFERRED_FLUID.entrySet()) {
            if (e.getValue().equals(fluidId)) {
                return GasRegistry.get(e.getKey().toString());
            }
        }

        // MGE own virtual fluid by naming convention
        if (fluidId.getNamespace().equals(Mge.MODID)
                && fluidId.getPath().startsWith("gas_")) {
            return GasRegistry.get(Mge.MODID + ":"
                    + fluidId.getPath().substring(4));
        }

        return Optional.empty();
    }

    /**
     * Resolves any fluid (from any mod) to an MGE gas using path-name matching
     * as a last resort. Used when a pipe inserts a fluid into an AtmosphereBlockEntity.
     */
    public static Optional<Gas> resolveAnyFluidToGas(Fluid fluid) {
        Optional<Gas> direct = fluidToGas(fluid);
        if (direct.isPresent()) return direct;

        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
        if (fluidId == null) return Optional.empty();

        String path = fluidId.getPath()
                .replaceAll("_source$|_flowing$", "")
                .toLowerCase(Locale.ROOT);

        return GasRegistry.all().stream()
                .filter(g -> g.id().getPath().equals(path))
                .findFirst();
    }

    public static Map<ResourceLocation, RegistryObject<VirtualFluid>> getMgeFluids() {
        return Collections.unmodifiableMap(MGE_FLUIDS);
    }
}
