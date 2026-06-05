package exp.CCnewmods.mge.fluid;

import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.EnvironmentGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Exposes the gas composition at a world position as an {@link IFluidHandler}.
 *
 * Reads from and writes to {@link EnvironmentGrid} directly — no block entity
 * required.  One instance is created per position that needs fluid-handler
 * access (e.g. a duct vent, a gas collector block, or a Create pipe connection).
 *
 * ── Tank model ────────────────────────────────────────────────────────────────
 * Each gas present above {@link #MIN_TANK_MBAR} becomes a numbered tank slot.
 * Tanks are ordered by partial pressure descending so the most abundant gas is
 * always tank 0 — this makes automated pipes preferentially extract the
 * dominant gas without needing a filter.
 *
 * Gases below {@link #MIN_TANK_MBAR} are not exposed as tanks: they're below
 * the noise floor and exporting trace amounts would be physically nonsensical.
 *
 * ── Scale ─────────────────────────────────────────────────────────────────────
 * 1 mB of virtual fluid = 1 mbar of partial pressure.
 * MAX_CAPACITY per tank = 2000 mB (2 bar), matching atmospheric oxygen.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 * All writes go through EnvironmentGrid which enqueues the position for the
 * diffusion ticker.  getTanks() / getFluidInTank() re-snapshot the grid each
 * call — no stale cached state.
 */
public final class AtmosphereFluidHandler implements IFluidHandler {

    private static final int   MAX_CAPACITY = 2000;
    private static final float MIN_TANK_MBAR = 1.0f; // below this, don't expose as a tank

    private final ServerLevel level;
    private final BlockPos    pos;

    public AtmosphereFluidHandler(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos   = pos;
    }

    // ── Tank enumeration ──────────────────────────────────────────────────────

    /**
     * Builds the current ordered tank list from the live grid composition.
     * Called at the start of every IFluidHandler method — cheap because
     * EnvironmentGrid.getComposition() is an array read with no allocation
     * for gases that haven't been written to.
     */
    private List<TankEntry> buildTanks() {
        GasComposition comp = EnvironmentGrid.getComposition(level, pos);
        List<TankEntry> tanks = new ArrayList<>();
        for (Gas gas : GasRegistry.all()) {
            float mbar = comp.get(gas);
            if (mbar >= MIN_TANK_MBAR) tanks.add(new TankEntry(gas, mbar));
        }
        // Sort descending by partial pressure — most abundant gas is tank 0
        tanks.sort((a, b) -> Float.compare(b.mbar(), a.mbar()));
        return tanks;
    }

    private record TankEntry(Gas gas, float mbar) {}

    @Override
    public int getTanks() {
        return buildTanks().size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        List<TankEntry> tanks = buildTanks();
        if (tank < 0 || tank >= tanks.size()) return FluidStack.EMPTY;
        TankEntry entry = tanks.get(tank);
        return GasFluidRegistry.gasToFluid(entry.gas(), (int) entry.mbar())
                .orElse(FluidStack.EMPTY);
    }

    @Override
    public int getTankCapacity(int tank) {
        return MAX_CAPACITY;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return GasFluidRegistry.resolveAnyFluidToGas(stack.getFluid()).isPresent();
    }

    // ── Fill ──────────────────────────────────────────────────────────────────

    /**
     * Accepts a gas-fluid from an external machine and injects it into the
     * atmosphere at this position.  Used by pipes pushing gas into the world.
     */
    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        Optional<Gas> gasOpt = GasFluidRegistry.resolveAnyFluidToGas(resource.getFluid());
        if (gasOpt.isEmpty()) return 0;

        int amount = resource.getAmount();
        if (action.execute()) {
            EnvironmentGrid.addGas(level, pos, gasOpt.get(), (float) amount);
        }
        return amount;
    }

    // ── Drain ─────────────────────────────────────────────────────────────────

    /**
     * Extracts a specific gas-fluid from the atmosphere.
     * Used by collectors and pipes pulling gas out of the world.
     */
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        Optional<Gas> gasOpt = GasFluidRegistry.resolveAnyFluidToGas(resource.getFluid());
        if (gasOpt.isEmpty()) return FluidStack.EMPTY;
        return drainGas(gasOpt.get(), resource.getAmount(), action);
    }

    /**
     * Extracts the most abundant gas from the atmosphere — used by unfiltered pipes.
     */
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        List<TankEntry> tanks = buildTanks();
        if (tanks.isEmpty()) return FluidStack.EMPTY;
        TankEntry best = tanks.get(0); // already sorted descending
        return drainGas(best.gas(), Math.min(maxDrain, (int) best.mbar()), action);
    }

    private FluidStack drainGas(Gas gas, int amount, FluidAction action) {
        float have = EnvironmentGrid.getGas(level, pos, gas);
        int actual = Math.min(amount, (int) have);
        if (actual <= 0) return FluidStack.EMPTY;

        Optional<FluidStack> fs = GasFluidRegistry.gasToFluid(gas, actual);
        if (fs.isEmpty()) return FluidStack.EMPTY;

        if (action.execute()) {
            EnvironmentGrid.addGas(level, pos, gas, -(float) actual);
        }
        return fs.get();
    }
}
