package exp.CCnewmods.mge.contraption;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import exp.CCnewmods.mge.Mge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Registers MGE's custom {@link ContraptionType}(s) into Create's own
 * extensible {@code CreateBuiltInRegistries.CONTRAPTION_TYPE} vanilla
 * {@link Registry}.
 * <p>
 * ── Verified against {@code AllContraptionTypes}'s own private
 * {@code register(String, Supplier)} helper (decompiled via bytecode, not
 * guessed) ───────────────────────────────────────────────────────────────
 * <p>
 * Two distinct steps are required, in this order:
 * <ol>
 *   <li>{@code new ContraptionType(factorySupplier)} — the constructor
 *       internally calls {@code CONTRAPTION_TYPE.createIntrusiveHolder(this)}
 *       and stores the resulting (still-unbound) {@code Holder.Reference}
 *       on itself. This does NOT key-register the entry — "intrusive
 *       holder" just means the holder object exists and is reachable from
 *       the {@code ContraptionType} instance before the registry knows its
 *       {@link ResourceLocation}, which is what lets
 *       {@code ContraptionType.is(TagKey)} work via that holder later.</li>
 *   <li>{@code Registry.registerForHolder(Registry, ResourceLocation, Object)}
 *       — the actual keyed registration, reusing the intrusive holder
 *       created in step 1 rather than creating a second one (this is why
 *       it's {@code registerForHolder} and not the more commonly-seen plain
 *       {@code Registry.register}, which would create its own holder and
 *       leave the {@code ContraptionType}'s internal one orphaned/unbound).
 *       Create's own {@code AllContraptionTypes.register} does exactly
 *       this, plus a put into its own legacy-name lookup map, which MGE has
 *       no equivalent of and doesn't need — {@link MisanthropeWindmillContraption}
 *       always writes its own type's string name via {@code getType()} at
 *       save time, no legacy/migration name required.</li>
 * </ol>
 * <p>
 * Call {@link #register()} once, during {@code FMLCommonSetupEvent} (matches
 * the timing of MGE's other cross-mod setup in {@code Mge}'s
 * {@code commonSetup} — see that method's established
 * {@code event.enqueueWork(() -> ...)} pattern). Common setup is safe
 * because {@code CreateBuiltInRegistries.CONTRAPTION_TYPE} is populated by
 * {@code AllContraptionTypes}'s static initializer, which runs at class-load
 * time well before any mod's common setup phase — Create's own nine types
 * are guaranteed to already exist in the registry by the time MGE adds its
 * tenth, though ordering against Create's own types doesn't actually matter
 * here since this doesn't reference any of them by registry lookup.
 */
public final class MisanthropeContraptionTypes {

    private MisanthropeContraptionTypes() {
    }

    /**
     * Held directly as the constructed {@link ContraptionType} instance —
     * not a {@code Holder.Reference} wrapper — matching how
     * {@code BearingContraption.getType()} returns the unwrapped
     * {@code ContraptionType} via {@code AllContraptionTypes.BEARING.value()}
     * (confirmed via bytecode: {@code Holder.Reference.value()} unwraps it).
     * {@code null} until {@link #register()} runs; see that method's doc
     * comment for why the null window is never actually observed at
     * runtime in practice.
     */
    public static ContraptionType MISANTHROPE_WINDMILL_TYPE;

    public static ContraptionType get() {
        return MISANTHROPE_WINDMILL_TYPE;
    }

    /**
     * Constructs and registers MGE's custom windmill {@link ContraptionType}.
     * See class doc comment for why both the constructor call AND the
     * separate {@code registerForHolder} call are required — skipping
     * either one leaves the registry in a broken state (constructor alone:
     * unreachable by {@link ContraptionType#fromType(String)}'s registry
     * lookup; {@code registerForHolder} alone, without first constructing
     * a {@code ContraptionType}: not possible, since the object being
     * registered must already exist).
     * <p>
     * Self-guarded behind {@code ModList.isLoaded("create")}, mirroring
     * {@code CreateCompat.tryLoad()}'s established convention elsewhere in
     * this codebase — MGE declares Create as a {@code mandatory = false}
     * dependency in {@code mods.toml}, so any code path that touches Create
     * classes directly (this one does, via {@link ContraptionType} and
     * {@link CreateBuiltInRegistries}) needs its own runtime check rather
     * than assuming Create is present. A no-op call when Create is absent
     * is silent and intentional — there's nothing for a windmill
     * contraption type to do in a pack that doesn't have windmills.
     * <p>
     * Not idempotency-guarded beyond the Create-presence check — calling
     * this twice (with Create present) is a programming error and should
     * throw loudly via vanilla's own duplicate-registration check, not be
     * silently swallowed.
     */
    public static void register() {
        if (!ModList.get().isLoaded("create")) return;

        MISANTHROPE_WINDMILL_TYPE = new ContraptionType(MisanthropeWindmillContraption::new);

        Registry.registerForHolder(
                CreateBuiltInRegistries.CONTRAPTION_TYPE,
                new ResourceLocation(Mge.MODID, "misanthrope_windmill"),
                MISANTHROPE_WINDMILL_TYPE
        );
    }
}
