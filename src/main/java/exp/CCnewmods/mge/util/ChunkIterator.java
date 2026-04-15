package exp.CCnewmods.mge.util;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * Utility for iterating loaded chunks via the protected {@code ChunkMap.getChunks()} method.
 *
 * <p>{@code getChunks()} is {@code protected} in 1.20.1's {@link ChunkMap} and has no
 * public wrapper. We reflect it once at first use and cache the {@link Method}.</p>
 */
public final class ChunkIterator {

    private static Method getChunksMethod = null;
    private static boolean resolved = false;

    private ChunkIterator() {}

    /**
     * Iterates all currently loaded {@link ChunkHolder}s in the given level.
     * Silently skips if reflection fails.
     */
    @SuppressWarnings("unchecked")
    public static void forEach(ServerLevel level, Consumer<ChunkHolder> action) {
        if (!resolved) resolve();
        if (getChunksMethod == null) return;
        try {
            ChunkMap chunkMap = level.getChunkSource().chunkMap;
            Iterable<ChunkHolder> holders = (Iterable<ChunkHolder>) getChunksMethod.invoke(chunkMap);
            for (ChunkHolder holder : holders) action.accept(holder);
        } catch (Exception ignored) {}
    }

    private static void resolve() {
        resolved = true;
        try {
            Method m = ChunkMap.class.getDeclaredMethod("getChunks");
            m.setAccessible(true);
            getChunksMethod = m;
        } catch (Exception e) {
            // Log once — after this getChunksMethod stays null and forEach is a no-op
            try {
                // Try by return type scan if name changed
                for (Method m : ChunkMap.class.getDeclaredMethods()) {
                    if (Iterable.class.isAssignableFrom(m.getReturnType())
                            && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        getChunksMethod = m;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
