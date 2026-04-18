package exp.CCnewmods.mge.fluid;

import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Optional;

/**
 * Exposes an {@link AtmosphereBlockEntity}'s gas composition as an {@link IFluidHandler}.
 *
 * <p>This allows any Create pipe, PneumaticCraft tube, or other Forge fluid-capable
 * device to extract or insert atmospheric gases as virtual fluid stacks.</p>
 *
 * <h3>Tank model</h3>
 * Each gas in the atmosphere composition is a separate "tank" slot. Extraction drains
 * mbar from the corresponding gas. Insertion converts an incoming FluidStack back to
 * a gas and adds it to the composition.
 *
 * <h3>Scale</h3>
 * 1 mB of virtual fluid = 1 mbar of gas partial pressure.
 */
public final class AtmosphereFluidHandler implements IFluidHandler {

    private final AtmosphereBlockEntity atm;
    private static final int MAX_CAPACITY = 2000; // mB per tank slot (2 bar max per gas)

    public AtmosphereFluidHandler(AtmosphereBlockEntity atm) {
        this.atm = atm;
    }

    @Override
    public int getTanks() {
        // One tank per registered gas — but expose only non-zero ones for performance
        return (int) atm.getComposition().getTag().getAllKeys().stream()
                .filter(k -> atm.getComposition().getTag().getFloat(k) > 0f)
                .count();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        var keys = atm.getComposition().getTag().getAllKeys().stream()
                .filter(k -> atm.getComposition().getTag().getFloat(k) > 0f)
                .sorted().toList();
        if (tank >= keys.size()) return FluidStack.EMPTY;

        String key = keys.get(tank);
        int mbar = (int) atm.getComposition().getTag().getFloat(key);
        Optional<Gas> gas = exp.CCnewmods.mge.gas.GasRegistry.get(key);
        if (gas.isEmpty()) return FluidStack.EMPTY;
        return GasFluidRegistry.gasToFluid(gas.get(), mbar).orElse(FluidStack.EMPTY);
    }

    @Override
    public int getTankCapacity(int tank) { return MAX_CAPACITY; }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return GasFluidRegistry.resolveAnyFluidToGas(stack.getFluid()).isPresent();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        Optional<Gas> gasOpt = GasFluidRegistry.resolveAnyFluidToGas(resource.getFluid());
        if (gasOpt.isEmpty()) return 0;

        int amount = resource.getAmount();
        if (action.execute()) {
            GasComposition comp = atm.getComposition();
            comp.add(gasOpt.get(), (float) amount);
            atm.setComposition(comp);
        }
        return amount;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        return drain(resource.getFluid(), resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        // Drain the most abundant gas
        var comp = atm.getComposition();
        String bestKey = null;
        float bestAmt = 0;
        for (String key : comp.getTag().getAllKeys()) {
            float amt = comp.getTag().getFloat(key);
            if (amt > bestAmt) { bestAmt = amt; bestKey = key; }
        }
        if (bestKey == null) return FluidStack.EMPTY;
        Optional<Gas> gas = exp.CCnewmods.mge.gas.GasRegistry.get(bestKey);
        if (gas.isEmpty()) return FluidStack.EMPTY;
        return drain(gas.get(), Math.min(maxDrain, (int) bestAmt), action);
    }

    private FluidStack drain(Fluid fluid, int amount, FluidAction action) {
        Optional<Gas> gasOpt = GasFluidRegistry.resolveAnyFluidToGas(fluid);
        if (gasOpt.isEmpty()) return FluidStack.EMPTY;
        return drain(gasOpt.get(), amount, action);
    }

    private FluidStack drain(Gas gas, int amount, FluidAction action) {
        GasComposition comp = atm.getComposition();
        float have = comp.get(gas);
        int actual = Math.min(amount, (int) have);
        if (actual <= 0) return FluidStack.EMPTY;

        Optional<FluidStack> fs = GasFluidRegistry.gasToFluid(gas, actual);
        if (fs.isEmpty()) return FluidStack.EMPTY;

        if (action.execute()) {
            comp.add(gas, -(float) actual);
            atm.setComposition(comp);
        }
        return fs.get();
    }
}
