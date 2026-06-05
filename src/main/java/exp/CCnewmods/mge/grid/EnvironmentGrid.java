package exp.CCnewmods.mge.grid;

import exp.CCnewmods.mge.dimension.DimensionAtmosphereLoader;
import exp.CCnewmods.mge.dimension.DimensionAtmosphereProfile;
import exp.CCnewmods.mge.gas.Gas;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.gas.GasComposition;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.section.EnvironmentSection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;

/**
 * Primary public API for the MGE parallel environment grid.
 *
 * All gas composition and temperature reads/writes for the world go through
 * here.  Thread-safe for reads; writes should happen on the server thread.
 *
 * ── Usage ─────────────────────────────────────────────────────────────────────
 *
 *   // Read a single gas
 *   float o2 = EnvironmentGrid.getGas(level, pos, GasRegistry.OXYGEN);
 *
 *   // Read full composition
 *   GasComposition comp = EnvironmentGrid.getComposition(level, pos);
 *
 *   // Write
 *   EnvironmentGrid.addGas(level, pos, GasRegistry.CARBON_DIOXIDE, 15f);
 *
 *   // Read temperature (NaN = no explicit value, caller falls back to ColdSweat)
 *   float temp = EnvironmentGrid.getTemperature(level, pos);
 *   if (Float.isNaN(temp)) temp = (float) MisTemperatureAPI.getAmbientCelsius(level, pos);
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 * The underlying float[] arrays are written to on the server tick thread.
 * Client-side rendering reads are safe because rendering runs on the render
 * thread after the server tick completes (in single-player) or reads a
 * client-side copy of chunk data (in multiplayer).
 */
public final class EnvironmentGrid {

    private EnvironmentGrid() {}

    // ── Section access ────────────────────────────────────────────────────────

    /**
     * Returns the EnvironmentSection for the given world position, creating it
     * if the section hasn't been used before.  Returns null if the chunk is not
     * loaded or the position is out of world bounds.
     */
    @Nullable
    public static EnvironmentSection getOrCreateSection(Level level, BlockPos pos) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return null;
        return chunk.getCapability(EnvironmentChunkData.CAP)
                .map(data -> data.getOrCreate(pos.getY()))
                .orElse(null);
    }

    /**
     * Returns the EnvironmentSection for the given world position without
     * allocating.  Returns null if the section has never been written to
     * (i.e. it is entirely at dimension defaults).
     */
    @Nullable
    public static EnvironmentSection getSection(Level level, BlockPos pos) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return null;
        return chunk.getCapability(EnvironmentChunkData.CAP)
                .map(data -> data.get(pos.getY()))
                .orElse(null);
    }

    /**
     * Returns the EnvironmentChunkData capability for a chunk.
     */
    @Nullable
    public static EnvironmentChunkData getChunkData(Level level, BlockPos pos) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return null;
        return chunk.getCapability(EnvironmentChunkData.CAP).orElse(null);
    }

    // ── Gas reads ─────────────────────────────────────────────────────────────

    /**
     * Get the partial pressure of a gas at the given world position (mbar).
     * Returns the dimension profile default if the position is untracked.
     */
    public static float getGas(Level level, BlockPos pos, Gas gas) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return defaultGas(level, gas);
        return chunk.getCapability(EnvironmentChunkData.CAP)
                .map(data -> data.getGas(gas, pos))
                .orElse(defaultGas(level, gas));
    }

    /**
     * Get the full GasComposition at a world position.
     * Relatively expensive — prefer getGas() for single-gas queries.
     */
    public static GasComposition getComposition(Level level, BlockPos pos) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return defaultComposition(level);
        return chunk.getCapability(EnvironmentChunkData.CAP)
                .map(data -> data.getComposition(pos))
                .orElse(defaultComposition(level));
    }

    // ── Gas writes ────────────────────────────────────────────────────────────

    public static void setGas(Level level, BlockPos pos, Gas gas, float mbar) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return;
        chunk.getCapability(EnvironmentChunkData.CAP)
                .ifPresent(data -> data.setGas(gas, pos, mbar));
        enqueue(level, pos);
    }

    public static void addGas(Level level, BlockPos pos, Gas gas, float deltaMbar) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return;
        chunk.getCapability(EnvironmentChunkData.CAP)
                .ifPresent(data -> data.addGas(gas, pos, deltaMbar));
        enqueue(level, pos);
    }

    public static void setComposition(Level level, BlockPos pos, GasComposition comp) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return;
        chunk.getCapability(EnvironmentChunkData.CAP)
                .ifPresent(data -> data.setComposition(pos, comp));
        enqueue(level, pos);
    }

    // ── Temperature reads ─────────────────────────────────────────────────────

    /**
     * Get the explicit temperature in °C at a world position.
     * Returns {@link EnvironmentSection#AMBIENT_SENTINEL} (Float.NaN) if no
     * explicit temperature has been set — callers should fall back to
     * ColdSweat / Thermodynamica / biome ambient.
     */
    public static float getTemperature(Level level, BlockPos pos) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return EnvironmentSection.AMBIENT_SENTINEL;
        return chunk.getCapability(EnvironmentChunkData.CAP)
                .map(data -> data.getTemperature(pos))
                .orElse(EnvironmentSection.AMBIENT_SENTINEL);
    }

    // ── Temperature writes ────────────────────────────────────────────────────

    public static void setTemperature(Level level, BlockPos pos, float celsius) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return;
        chunk.getCapability(EnvironmentChunkData.CAP)
                .ifPresent(data -> data.setTemperature(pos, celsius));
    }

    // ── Particulate reads ─────────────────────────────────────────────────────

    public static float getParticulate(Level level, BlockPos pos, ParticulateType type) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return 0f;
        return chunk.getCapability(EnvironmentChunkData.CAP)
                .map(data -> data.getParticulate(type, pos))
                .orElse(0f);
    }

    public static ParticulateComposition getParticulates(Level level, BlockPos pos) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return ParticulateComposition.empty();
        return chunk.getCapability(EnvironmentChunkData.CAP)
                .map(data -> data.getParticulates(pos))
                .orElse(ParticulateComposition.empty());
    }

    // ── Particulate writes ────────────────────────────────────────────────────

    public static void addParticulate(Level level, BlockPos pos, ParticulateType type, float mgM3) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return;
        chunk.getCapability(EnvironmentChunkData.CAP)
                .ifPresent(data -> data.addParticulate(type, pos, mgM3));
    }

    public static void setParticulates(Level level, BlockPos pos, ParticulateComposition comp) {
        LevelChunk chunk = getChunkIfLoaded(level, pos);
        if (chunk == null) return;
        chunk.getCapability(EnvironmentChunkData.CAP)
                .ifPresent(data -> data.setParticulates(pos, comp));
    }

    // ── Dirty queue ───────────────────────────────────────────────────────────

    /**
     * Enqueue a position for the diffusion ticker.
     * All gas writes should call this — the ticker will process and propagate.
     */
    public static void enqueue(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            SectionLoadManager.getScheduler(sl).enqueue(pos);
        }
    }

    public static void enqueueWithNeighbours(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            SectionLoadManager.getScheduler(sl).enqueueWithNeighbours(pos);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Nullable
    private static LevelChunk getChunkIfLoaded(Level level, BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        if (!level.hasChunk(cp.x, cp.z)) return null;
        return level.getChunk(cp.x, cp.z);
    }

    private static float defaultGas(Level level, Gas gas) {
        ResourceLocation dimId = level.dimension().location();
        DimensionAtmosphereProfile profile = DimensionAtmosphereLoader.get(dimId);
        if (profile == null) profile = DimensionAtmosphereLoader.STANDARD_OVERWORLD_FALLBACK;
        Float val = profile.gases.get(gas.id().toString());
        return val != null ? val : 0f;
    }

    private static GasComposition defaultComposition(Level level) {
        ResourceLocation dimId = level.dimension().location();
        DimensionAtmosphereProfile profile = DimensionAtmosphereLoader.get(dimId);
        if (profile == null) profile = DimensionAtmosphereLoader.STANDARD_OVERWORLD_FALLBACK;
        return profile.createGasComposition();
    }
}
