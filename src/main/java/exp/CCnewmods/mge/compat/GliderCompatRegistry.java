package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

/**
 * Thin detection layer that unifies glider-state queries across all supported
 * glider mods without creating hard compile dependencies on any of them.
 *
 * <h3>Supported mods</h3>
 * <ul>
 *   <li><b>Any elytra / Caelus-compatible glider</b> (including
 *       {@code createornithopterglider}) — detected via vanilla
 *       {@link LivingEntity#isFallFlying()}, which Caelus's mixin already
 *       extends to cover any item that passes {@code canElytraFly}. No Caelus
 *       API call is needed at runtime; the flag is set by Caelus's mixin before
 *       our event handler runs.</li>
 *   <li><b>VentureCraft Gliders</b> ({@code gliders}) — detected via
 *       {@code GliderUtil.isGlidingWithActiveGlider(LivingEntity)}, looked up
 *       reflectively so the mod remains an optional dependency.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * Call {@link #tryLoad()} once during {@code commonSetup}. After that,
 * {@link #isGliding(LivingEntity)} and {@link #hasGliderEquipped(LivingEntity)}
 * are safe to call every tick with no exceptions.
 */
public final class GliderCompatRegistry {

    // ── mod IDs ───────────────────────────────────────────────────────────────
    public static final String MODID_VC_GLIDERS    = "gliders";
    public static final String MODID_ORNITHOPTER   = "createornithopterglider";
    public static final String MODID_CAELUS        = "caelus";

    // ── state ─────────────────────────────────────────────────────────────────
    private static boolean vcGlidersLoaded      = false;
    private static boolean ornithopterLoaded    = false;

    // Reflective handle for VCGliders — resolved once at startup
    private static java.lang.reflect.Method vcIsGlidingMethod   = null;
    private static java.lang.reflect.Method vcHasEquippedMethod = null;

    private GliderCompatRegistry() {}

    // =========================================================================
    // Init
    // =========================================================================

    public static void tryLoad() {
        if (ModList.get().isLoaded(MODID_VC_GLIDERS)) {
            try {
                Class<?> gliderUtil = Class.forName("net.venturecraft.gliders.util.GliderUtil");
                vcIsGlidingMethod   = gliderUtil.getMethod("isGlidingWithActiveGlider",
                        net.minecraft.world.entity.LivingEntity.class);
                vcHasEquippedMethod = gliderUtil.getMethod("hasGliderEquipped",
                        net.minecraft.world.entity.LivingEntity.class);
                vcGlidersLoaded = true;
                Mge.LOGGER.info("[MGE] VentureCraft Gliders detected — atmospheric glider physics active.");
            } catch (Exception e) {
                Mge.LOGGER.warn("[MGE] VentureCraft Gliders found but reflection failed: {}", e.getMessage());
            }
        }

        if (ModList.get().isLoaded(MODID_ORNITHOPTER)) {
            ornithopterLoaded = true;
            // No reflection needed — ornithopter extends ElytraItem and sets isFallFlying()
            // through the standard Caelus / vanilla path. We just log its presence.
            String caelusNote = ModList.get().isLoaded(MODID_CAELUS)
                    ? "via Caelus isFallFlying() hook"
                    : "via vanilla isFallFlying() — Caelus absent, some items may not register";
            Mge.LOGGER.info("[MGE] Create Ornithopter Glider detected — atmospheric glider physics active ({}).",
                    caelusNote);
        }
    }

    // =========================================================================
    // Runtime detection
    // =========================================================================

    /**
     * Returns {@code true} if this entity is actively gliding right now,
     * by any supported mod or vanilla elytra.
     *
     * <p>Check order:
     * <ol>
     *   <li>Vanilla {@code isFallFlying()} — covers elytra, ornithopter (Caelus),
     *       and any other Caelus-registered item.</li>
     *   <li>VCGliders {@code isGlidingWithActiveGlider()} — separate non-elytra
     *       physics path.</li>
     * </ol>
     */
    public static boolean isGliding(LivingEntity entity) {
        if (entity.isFallFlying()) return true;
        if (vcGlidersLoaded && vcIsGlidingMethod != null) {
            try {
                return Boolean.TRUE.equals(vcIsGlidingMethod.invoke(null, entity));
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Returns {@code true} if this entity has any glider equipped (not necessarily
     * actively gliding). Used for density drag effects that apply even when not
     * in active flight.
     */
    public static boolean hasGliderEquipped(LivingEntity entity) {
        // For elytra/ornithopter: check chest slot for an ElytraItem subtype.
        // This covers vanilla elytra and the ornithopter (which extends ElytraItem).
        var chestStack = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if (chestStack.getItem() instanceof net.minecraft.world.item.ElytraItem) return true;

        // VCGliders uses Curios — check via their utility
        if (vcGlidersLoaded && vcHasEquippedMethod != null) {
            try {
                return Boolean.TRUE.equals(vcHasEquippedMethod.invoke(null, entity));
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Returns {@code true} if at least one glider mod is present. Used to skip
     * the glider branch of the tick handler entirely on setups without any glider mod.
     */
    public static boolean anyGliderModPresent() {
        return vcGlidersLoaded || ornithopterLoaded
                || ModList.get().isLoaded("caelus"); // caelus alone implies elytra extension
    }

    public static boolean isVcGlidersLoaded()   { return vcGlidersLoaded; }
    public static boolean isOrnithopterLoaded() { return ornithopterLoaded; }
}
