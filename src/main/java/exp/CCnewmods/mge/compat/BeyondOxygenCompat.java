package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.gas.GasRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.sierravanguard.beyond_oxygen.registry.BODimensions;
import com.sierravanguard.beyond_oxygen.utils.SpaceSuitHandler;

/**
 * Compatibility bridge for Beyond Oxygen 0.12.x.
 *
 * <h3>What Beyond Oxygen does:</h3>
 * <p>BO handles breathability at the <em>dimension</em> level. Its
 * {@code livingBreatheDeny} fires first (NORMAL priority) and sets
 * {@code canBreathe=false} for the whole dimension if the dimension is in its
 * unbreathable tag. Its {@code livingBreathAllow} then re-enables breathing
 * if the entity is inside a sealed hermetic area (bubble generator, pressurised
 * hull, etc.).</p>
 *
 * <h3>What MGE adds on top:</h3>
 * <p>MGE overrides at the <em>block</em> level. At {@link EventPriority#HIGH} —
 * after BO's NORMAL-priority deny, before BO's NORMAL-priority allow — MGE
 * samples the atmosphere block entity at the entity's eye position and:</p>
 * <ul>
 *   <li>If O₂ ≥ {@link MgeConfig#o2BreathableThresholdMbar}: sets
 *       {@code canBreathe=true} and {@code canRefillAir=true}, overriding
 *       BO's dimension-wide block.</li>
 *   <li>If O₂ &lt; threshold AND the dimension is not already marked unbreathable
 *       by BO (meaning it's a normally breathable dimension with a local anoxic
 *       pocket): sets {@code canBreathe=false} and drains air.</li>
 *   <li>If the entity is wearing a closed BO spacesuit with oxygen: skip MGE
 *       interference entirely — the suit handles it.</li>
 * </ul>
 *
 * <p>This creates a coherent two-layer system: BO provides dimension-wide defaults
 * and equipment (spacesuits, tanks, hermetic areas), MGE provides dynamic
 * per-block gas composition that can override both directions.</p>
 *
 * <p>Registered only when Beyond Oxygen is loaded. Call {@link #tryLoad()} during
 * {@code FMLCommonSetupEvent}.</p>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BeyondOxygenCompat {

    public static final String BO_MODID = "beyond_oxygen";
    private static boolean loaded = false;

    private BeyondOxygenCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(BO_MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Beyond Oxygen detected — per-block O₂ breathability override active.");
    }

    public static boolean isLoaded() { return loaded; }

    // -------------------------------------------------------------------------
    // LivingBreatheEvent — HIGH priority so we fire after BO's NORMAL deny,
    // but the ordering relative to BO's allow handler needs care. BO's allow
    // fires at NORMAL too; we use HIGH so we run before it, meaning:
    // BO deny (NORMAL) → MGE override (HIGH) → BO allow (NORMAL, lowest)
    //
    // Forge processes HIGH before NORMAL for the same event, so our HIGH fires
    // BEFORE BO's NORMAL allow. That's fine: if we set canBreathe=true, BO's
    // allow will see it's already true and either skip or redundantly confirm.
    // If we set canBreathe=false, BO's allow can still re-enable it if the
    // entity is in a hermetic area — which is correct behaviour (sealed room
    // beats local anoxic atmosphere).
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingBreathe(LivingBreatheEvent event) {
        if (!loaded) return;
        if (!MgeConfig.enableGasEffects) return;

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // If entity is wearing a functional closed BO spacesuit, don't interfere —
        // the suit's internal oxygen supply is already handling breathability
        if (SpaceSuitHandler.isWearingFullSuit(entity)
                && SpaceSuitHandler.isHelmetClosed(entity)) return;

        // Sample atmosphere at eye position
        BlockPos eyePos = BlockPos.containing(entity.getEyePosition());
        BlockEntity be = entity.level().getBlockEntity(eyePos);

        if (!(be instanceof AtmosphereBlockEntity atm)) {
            // Not in an atmosphere block — fall through to BO's logic
            return;
        }

        float o2 = atm.getComposition().get(GasRegistry.OXYGEN);
        boolean mgeBreathable = o2 >= MgeConfig.o2BreathableThresholdMbar;
        boolean boDimensionUnbreathable = BODimensions.isUnbreathable(entity.level());

        if (mgeBreathable) {
            // Local O₂ is sufficient — override BO's dimension-wide block
            event.setCanBreathe(true);
            event.setCanRefillAir(true);
        } else if (!boDimensionUnbreathable) {
            // Breathable dimension but local O₂ depleted (fire, explosion, sealed room leak)
            // MGE creates a local anoxic hazard that BO wouldn't otherwise detect
            event.setCanBreathe(false);
            // Drain air at standard rate — vanilla air supply goes to zero then drown damage
            event.setConsumeAirAmount(Math.min(5, entity.getAirSupply()));
        }
        // If boDimensionUnbreathable && !mgeBreathable: BO already handled it, don't double-penalise
    }
}
