package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.block.AtmosphereBlockEntity;
import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Compat for Oreganized and its Carcinogenius addon.
 *
 * Lead ore/blocks (Oreganized, modid "oreganized"):
 *   - Mining emits LEAD_DUST into the atmosphere at the break position.
 *   - Random ticks on lead ore blocks emit small amounts continuously.
 *   - When LEAD_DUST exceeds threshold, applies oreganized:stunning effect
 *     via reflection-safe registry lookup. Skips entities protected by
 *     LeadProtections.isProtected().
 *
 * Asbestos ore/block (Carcinogenius addon, registered under "oreganized" namespace):
 *   - Mining emits ASBESTOS_FIBER.
 *   - Random ticks emit very small amounts (fibres release naturally from ore).
 *   - When ASBESTOS_FIBER exceeds threshold, applies
 *     oreganized_carcinogenius:lung_damage effect.
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OreganizedCompat {

    public static final String OREG_MODID   = "oreganized";
    public static final String CARCI_MODID  = "oreganized_carcinogenius";

    private static boolean oregLoaded   = false;
    private static boolean carciLoaded  = false;

    // Resolved via registry at load time — null if mod absent
    private static MobEffect stunnedEffect     = null;
    private static MobEffect lungDamageEffect  = null;

    // Block class name signatures for reflection-safe detection
    private static final String LEAD_ORE_CLASS  = "galena.oreganized.content.block.LeadOreBlock";
    private static final String ASBESTOS_CLASS  = "galena.oreganized.carcinogenius.content.block.AsbestosBlock";

    private OreganizedCompat() {}

    public static void tryLoad() {
        oregLoaded  = ModList.get().isLoaded(OREG_MODID);
        carciLoaded = ModList.get().isLoaded(CARCI_MODID);

        if (!oregLoaded && !carciLoaded) return;

        if (oregLoaded) {
            stunnedEffect = ForgeRegistries.MOB_EFFECTS
                    .getValue(new net.minecraft.resources.ResourceLocation("oreganized", "stunning"));
            if (stunnedEffect != null)
                Mge.LOGGER.info("[MGE] Oreganized detected — lead dust atmosphere effects active.");
        }

        if (carciLoaded) {
            lungDamageEffect = ForgeRegistries.MOB_EFFECTS
                    .getValue(new net.minecraft.resources.ResourceLocation(
                            "oreganized_carcinogenius", "lung_damage"));
            if (lungDamageEffect != null)
                Mge.LOGGER.info("[MGE] Oreganized Carcinogenius detected — asbestos fibre effects active.");
        }
    }

    public static boolean isOregLoaded()  { return oregLoaded; }
    public static boolean isCarciLoaded() { return carciLoaded; }

    // -------------------------------------------------------------------------
    // Block break — inject dust on mining
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        String blockClass = event.getState().getBlock().getClass().getName();
        BlockPos pos = event.getPos();

        if (oregLoaded && blockClass.equals(LEAD_ORE_CLASS)) {
            injectParticulate(level, pos.above(), ParticulateType.LEAD_DUST, 120f);
            injectParticulate(level, pos,         ParticulateType.LEAD_DUST,  60f);
        }

        if (carciLoaded && blockClass.equals(ASBESTOS_CLASS)) {
            // Asbestos fibres are lighter and spread more
            injectParticulate(level, pos.above(), ParticulateType.ASBESTOS_FIBER, 80f);
            injectParticulate(level, pos,         ParticulateType.ASBESTOS_FIBER, 40f);
            injectParticulate(level, pos.above(2),ParticulateType.ASBESTOS_FIBER, 25f);
        }
    }

    // -------------------------------------------------------------------------
    // Random tick — continuous low-level emission
    // -------------------------------------------------------------------------

    /** Called from MixinRandomTick (via ActiveBreathingHandler hook) if block matches. */
    public static void onOreRandomTick(BlockState state, ServerLevel level, BlockPos pos) {
        if (!MgeConfig.enableGasEffects) return;
        String cls = state.getBlock().getClass().getName();

        if (oregLoaded && cls.equals(LEAD_ORE_CLASS)) {
            injectParticulate(level, pos.above(), ParticulateType.LEAD_DUST, 3f);
        }
        if (carciLoaded && cls.equals(ASBESTOS_CLASS)) {
            injectParticulate(level, pos.above(), ParticulateType.ASBESTOS_FIBER, 1.5f);
        }
    }

    // -------------------------------------------------------------------------
    // Effect application — called from PlayerGasEffectHandler
    // -------------------------------------------------------------------------

    /**
     * Override the default particulate effect for LEAD_DUST and ASBESTOS_FIBER.
     * Returns true if this compat handled the effect (suppresses the default handler).
     */
    public static boolean applyOverrideEffect(LivingEntity entity,
                                               ParticulateType type, int amplifier) {
        if (type == ParticulateType.LEAD_DUST && stunnedEffect != null) {
            // Check LeadProtections.isProtected via reflection-safe try/catch
            if (!isLeadProtected(entity)) {
                entity.addEffect(new MobEffectInstance(stunnedEffect,
                        60, Math.min(amplifier, 3), false, true));
            }
            return true;
        }
        if (type == ParticulateType.ASBESTOS_FIBER && lungDamageEffect != null) {
            entity.addEffect(new MobEffectInstance(lungDamageEffect,
                    100, amplifier, false, true));
            return true;
        }
        return false;
    }

    private static boolean isLeadProtected(LivingEntity entity) {
        try {
            Class<?> lp = Class.forName("galena.oreganized.api.LeadProtections");
            java.lang.reflect.Method m = lp.getMethod("isProtected", LivingEntity.class);
            return (boolean) m.invoke(null, entity);
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void injectParticulate(ServerLevel level, BlockPos pos,
                                           ParticulateType type, float amount) {
        if (!level.isLoaded(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AtmosphereBlockEntity atm)) return;
        ParticulateComposition parts = atm.getParticulates();
        parts.add(type, amount);
        atm.setParticulates(parts);
        Mge.getScheduler(level).enqueue(pos);
    }
}
