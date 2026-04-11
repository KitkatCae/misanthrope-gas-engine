package exp.CCnewmods.mge.gas;

import net.minecraft.resources.ResourceLocation;

/**
 * A registered gas type. Instances are created and stored in {@link GasRegistry}.
 *
 * <p>Gas amounts in atmosphere NBT are keyed by {@link #id()} string (e.g. {@code "mge:nitrogen"})
 * and valued as {@code float} partial pressures in millibars.</p>
 */
public record Gas(ResourceLocation id, GasProperties properties) {

    /** Convenience: the NBT key string used for this gas in CompoundTag storage. */
    public String nbtKey() {
        return id.toString();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
