package exp.CCnewmods.mge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Callback bridge allowing MGE's shockwave system to notify structural
 * physics about solid-block stress injection, without MGE having any
 * compile dependency on the mod providing that physics.
 *
 * <p><b>Migration note:</b> structural stress and collapse physics were
 * moved from Misanthrope Core to Misanthrope World (modid
 * {@code misanthrope_world}) in pack version 2.0.6. Misanthrope Core itself
 * still exists with a different scope and is no longer the registrant here.
 * The interface and class name are kept as-is to avoid unnecessary API
 * churn for this bridge; only the registering mod changed.
 *
 * <p>Misanthrope World registers a {@link StructuralAdapter} implementation
 * ({@code exp.CCnewmods.misanthrope_world.physics.structural.ShockwaveStressAdapter})
 * during {@code FMLLoadCompleteEvent}. Until then all calls are no-ops. If
 * Misanthrope World is not loaded, the adapter is never registered and the
 * bridge stays inert forever.
 *
 * <p>Thread safety: the adapter field is written once at startup and only
 * read after that, so volatile is sufficient.
 */
public final class MisWorldBridge {

    private MisWorldBridge() {}

    /**
     * Implemented by Misanthrope World. Registered once at startup.
     */
    public interface StructuralAdapter {
        /**
         * Returns the fraction of incoming shockwave energy this block absorbs
         * (0 = none absorbed, transmits fully; 1 = fully absorbed).
         */
        float getShockwaveAbsorption(BlockState state);

        /**
         * Returns a multiplier applied to incoming shockwave strength for this
         * block. Values > 1 focus/amplify (hollow blocks); < 1 attenuate beyond
         * absorption.
         */
        float getShockwaveAmplification(BlockState state);

        /**
         * Injects shockwave-derived structural stress into Misanthrope World's
         * {@code StructuralStressField} at the given position.
         *
         * @param strength effective shockwave strength at this block (0–1 scale)
         */
        void injectShockwaveStress(ServerLevel level, BlockPos pos, float strength);

        /**
         * Injects torsional shear stress from a rotating kinetic source (Create
         * shaft, high-RPM bearing) into {@code StructuralStressField}.
         *
         * @param torqueNm estimated torque in Newton-metres (game-scaled)
         */
        void injectKineticStress(ServerLevel level, BlockPos pos, float torqueNm);
    }

    @Nullable
    private static volatile StructuralAdapter adapter = null;

    /** Called by Misanthrope World on {@code FMLLoadCompleteEvent}. */
    public static void register(StructuralAdapter a) {
        adapter = a;
    }

    // ── Null-safe delegating accessors ────────────────────────────────────────

    public static float getShockwaveAbsorption(BlockState state) {
        StructuralAdapter a = adapter;
        return a != null ? a.getShockwaveAbsorption(state) : 0f;
    }

    public static float getShockwaveAmplification(BlockState state) {
        StructuralAdapter a = adapter;
        return a != null ? a.getShockwaveAmplification(state) : 1f;
    }

    public static void injectShockwaveStress(ServerLevel level, BlockPos pos, float strength) {
        StructuralAdapter a = adapter;
        if (a != null) a.injectShockwaveStress(level, pos, strength);
    }

    public static void injectKineticStress(ServerLevel level, BlockPos pos, float torqueNm) {
        StructuralAdapter a = adapter;
        if (a != null) a.injectKineticStress(level, pos, torqueNm);
    }

    public static boolean isRegistered() {
        return adapter != null;
    }
}
