package exp.CCnewmods.mge.fluid;

import exp.CCnewmods.mge.gas.Gas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidType;
import net.minecraft.network.chat.Component;

/**
 * FluidType for an MGE virtual gas fluid. Carries the source gas for display and conversion.
 */
public final class MgeGasFluidType extends FluidType {

    private final Gas gas;

    public MgeGasFluidType(Gas gas, Properties properties) {
        super(properties
                .lightLevel(0)
                .density((int) Math.round(gas.properties().densityRatioToAir() * 1000))
                .viscosity(100)
                .temperature(293));
        this.gas = gas;
    }

    public Gas getGas() { return gas; }

    @Override
    public Component getDescription() {
        return Component.translatable("gas." + gas.id().toString().replace(":", "."));
    }
}
