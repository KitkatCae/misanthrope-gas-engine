package exp.CCnewmods.mge.mixin.coldsweat;

import com.momosoftworks.coldsweat.data.biome_modifier.AddSpawnsBiomeModifier;
import com.momosoftworks.coldsweat.util.serialization.RegistryHelper;
import exp.CCnewmods.mge.Mge;
import net.minecraft.core.RegistryAccess;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Fixes Cold Sweat crash during ServerAboutToStartEvent / biome modifier application:
 * <p>
 * NullPointerException: Cannot invoke "RegistryAccess.registryOrThrow()"
 * because "registryAccess" is null
 * at ConfigHelper.parseRegistryItems(ConfigHelper.java:58)
 * at AddSpawnsBiomeModifier.modify(AddSpawnsBiomeModifier.java:26)
 * <p>
 * Root cause: AddSpawnsBiomeModifier.modify() calls
 * RegistryHelper.getRegistryAccess()
 * which reads the static field RegistryHelper.REGISTRY_ACCESS.
 * Cold Sweat populates this field lazily (on first world load or server tick),
 * but biome modifiers run during ServerLifecycleHooks.handleServerAboutToStart()
 * before that initialisation, leaving the field null.
 * <p>
 * Fix: inject at HEAD of modify(). If REGISTRY_ACCESS is null, we populate it
 * from ServerLifecycleHooks.getCurrentServer().registryAccess() — which IS
 * available at this point since the server has been constructed. We do this via
 * reflection because the field is package-private in Cold Sweat's source.
 * <p>
 * This is a one-time bootstrap: once we write the field the first time,
 * Cold Sweat's own code will maintain it from that point on.
 */
@Mixin(value = AddSpawnsBiomeModifier.class, remap = false)
public abstract class ColdSweatRegistryAccessMixin {

    private static Field REGISTRY_ACCESS_FIELD = null;
    private static boolean fieldLookupAttempted = false;

    @Inject(method = "modify", at = @At("HEAD"), remap = false)
    private void misanthrope$ensureRegistryAccess(
            net.minecraft.core.Holder biome, BiomeModifier.Phase phase,
            ModifiableBiomeInfo.BiomeInfo.Builder builder,
            CallbackInfo ci) {

        // Fast path: if Cold Sweat already populated the field, nothing to do.
        if (RegistryHelper.getRegistryAccess() != null) return;

        // Lazy field lookup — only attempt once.
        if (!fieldLookupAttempted) {
            fieldLookupAttempted = true;
            try {
                Field f = RegistryHelper.class.getDeclaredField("REGISTRY_ACCESS");
                f.setAccessible(true);
                REGISTRY_ACCESS_FIELD = f;
            } catch (NoSuchFieldException e) {
                Mge.LOGGER.error(
                        "[Misanthrope] ColdSweatRegistryAccessMixin: could not find " +
                                "RegistryHelper.REGISTRY_ACCESS field — Cold Sweat biome modifier " +
                                "crash will NOT be prevented. Error: {}", e.getMessage());
            }
        }

        if (REGISTRY_ACCESS_FIELD == null) return;

        // Get a valid RegistryAccess from the current server.
        // ServerLifecycleHooks.getCurrentServer() is non-null during biome modifier
        // application since the server has been constructed by this point.
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            Mge.LOGGER.warn(
                    "[Misanthrope] ColdSweatRegistryAccessMixin: server is null during " +
                            "biome modifier application — cannot bootstrap RegistryAccess.");
            return;
        }

        try {
            RegistryAccess registryAccess = server.registryAccess();
            REGISTRY_ACCESS_FIELD.set(null, registryAccess);
            Mge.LOGGER.info(
                    "[Misanthrope] Bootstrapped Cold Sweat RegistryHelper.REGISTRY_ACCESS " +
                            "before biome modifier application to prevent NPE.");
        } catch (IllegalAccessException e) {
            Mge.LOGGER.error(
                    "[Misanthrope] ColdSweatRegistryAccessMixin: failed to set " +
                            "REGISTRY_ACCESS: {}", e.getMessage());
        }
    }
}
