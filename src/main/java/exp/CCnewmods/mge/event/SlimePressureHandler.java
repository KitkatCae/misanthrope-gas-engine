package exp.CCnewmods.mge.event;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

/**
 * Pressure-driven Pehkui scale for {@link Slime} and {@link MagmaCube}.
 *
 * <h3>Concept</h3>
 * <p>Slimes are gas-filled gelatinous blobs. Under <em>low</em> pressure the
 * surrounding atmosphere can no longer compress them — they expand, becoming
 * visually larger but physically weaker (their mass is the same; it's just
 * spread over a bigger volume, so any given cross-section has less slime in
 * it). Under <em>high</em> pressure the atmosphere squeezes them down,
 * concentrating their mass and making them smaller but proportionally
 * tougher.</p>
 *
 * <h3>Scale mapping</h3>
 * <pre>
 *   Pressure (mbar)   Visual scale   Health scale (inverse)
 *   ──────────────    ────────────   ──────────────────────
 *   ≥ 1400  (high)     0.6×            1.4×  (compact, tough)
 *    ~1013  (normal)   1.0×            1.0×  (baseline)
 *   ≤  300  (low)      2.0×            0.5×  (bloated, fragile)
 * </pre>
 *
 * <p>Scale is interpolated smoothly across that range. The Pehkui tick-delay
 * ({@link #SCALE_TRANSITION_TICKS}) provides a gradual physical transition
 * rather than a snap change.</p>
 *
 * <h3>Pop mechanic</h3>
 * <p>The vanilla {@code size} NBT tag is repurposed as a <em>structural
 * limit</em> rather than a visual state:</p>
 * <ul>
 *   <li>Size 1 (tiny) — pops if scale exceeds {@link #SIZE_1_MAX_SCALE} or
 *       falls below {@link #SIZE_1_MIN_SCALE}</li>
 *   <li>Size 2 (small) — wider tolerance</li>
 *   <li>Size 3 (large / default max) — standard tolerance</li>
 *   <li>Size 4+ — extremely tough; very hard to pop</li>
 * </ul>
 * <p>A "pop" kills the entity and drops a slimeball (or magma cream) as if
 * it had burst like a water balloon, rather than the normal loot table.</p>
 *
 * <h3>Magma Cubes</h3>
 * <p>Magma Cubes are treated identically to slimes — they are also gas-filled
 * blobs, merely of a fiery variety. No special override needed unless you
 * want differentiated thresholds, which can be done via
 * {@link SlimePressureCompat}.</p>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlimePressureHandler {

    // ── Tick rate ─────────────────────────────────────────────────────────────

    /** How often pressure is sampled per slime. Every 3 s. */
    public static final int TICK_INTERVAL = 60;

    /**
     * Pehkui transition ticks — how many ticks the scale interpolation takes.
     * 40 ticks = 2 s, giving a smooth physical-feeling expansion/contraction.
     */
    public static final int SCALE_TRANSITION_TICKS = 40;

    // ── Pressure reference points (mbar) ─────────────────────────────────────

    /** Nominal sea-level pressure. Scale = 1.0 at this value. */
    public static final float REFERENCE_PRESSURE_MBAR = 1013f;

    /**
     * Pressure at which a slime is at maximum compression (minimum scale).
     * Below this value the compression curve flattens.
     */
    public static final float MAX_COMPRESSION_PRESSURE_MBAR = 1400f;

    /**
     * Pressure at which a slime is at maximum expansion (maximum scale).
     * Above this value the expansion curve flattens.
     */
    public static final float MAX_EXPANSION_PRESSURE_MBAR = 300f;

    // ── Scale range ───────────────────────────────────────────────────────────

    /** Minimum Pehkui visual scale (fully compressed at high pressure). */
    public static final float MIN_VISUAL_SCALE = 0.6f;

    /** Maximum Pehkui visual scale (fully expanded at low pressure). */
    public static final float MAX_VISUAL_SCALE = 2.0f;

    // ── Pop limits by vanilla size state ──────────────────────────────────────

    /**
     * Maximum Pehkui scale before the slime "pops" (bursts outward).
     * Indexed by vanilla size (0 = unused, 1 = tiny, 2 = small, 3 = large, 4 = extra).
     */
    public static final float[] POP_MAX_SCALE = { 0f, 1.3f, 1.6f, 2.0f, 2.6f };

    /**
     * Minimum Pehkui scale before the slime implodes (crushed inward).
     * Tiny slimes can't handle much compression; large ones are more resilient.
     */
    public static final float[] POP_MIN_SCALE = { 0f, 0.75f, 0.65f, 0.55f, 0.40f };

    // ─────────────────────────────────────────────────────────────────────────

    private SlimePressureHandler() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Main tick handler
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Slime slime)) return;

        // Sample on interval — use entity ID to spread load across ticks
        if ((slime.tickCount + slime.getId()) % TICK_INTERVAL != 0) return;

        BlockPos pos = slime.blockPosition();
        float pressure = GridAtmosphereCompat.getTotalPressure(level, pos);

        applyPressureScale(slime, pressure, level, pos);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core scale logic — also called by SlimePressureCompat for modded slimes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies pressure-driven Pehkui scaling to any Slime (or subclass).
     *
     * <p>Modded-slime compat classes should call this directly rather than
     * duplicating the math. Override {@code popMaxScale} / {@code popMinScale}
     * if the mob needs different burst thresholds.</p>
     *
     * @param slime        the entity to scale
     * @param pressure     total atmospheric pressure at the entity's position, mbar
     * @param level        server level
     * @param pos          block position of the entity
     */
    public static void applyPressureScale(Slime slime, float pressure,
                                          ServerLevel level, BlockPos pos) {
        float targetVisualScale = pressureToVisualScale(pressure);
        float targetHealthScale = 1.0f / targetVisualScale; // inverse — stretched = weaker

        // Clamp health scale so it stays in a reasonable game-feel range
        targetHealthScale = Math.max(0.25f, Math.min(2.5f, targetHealthScale));

        // Apply via Pehkui — smooth transition
        setScaleSmooth(slime, ScaleTypes.BASE,   targetVisualScale);
        setScaleSmooth(slime, ScaleTypes.HEALTH, targetHealthScale);

        // Check pop condition
        int vanillaSize = Math.max(1, Math.min(4, slime.getSize()));
        float currentScale = ScaleTypes.BASE.getScaleData(slime).getScale();

        float maxPop = POP_MAX_SCALE[vanillaSize];
        float minPop = POP_MIN_SCALE[vanillaSize];

        if (currentScale > maxPop || currentScale < minPop) {
            popSlime(slime, level, pos);
        }
    }

    /**
     * Variant for modded slimes that aren't {@link Slime} subclasses but
     * behave identically (e.g. wrapped/delegating entities). Pass the
     * vanilla size equivalent and the entity directly.
     */
    public static void applyPressureScaleCustomLimits(
            net.minecraft.world.entity.LivingEntity entity,
            float pressure,
            int vanillaSize,
            float[] popMaxOverride,
            float[] popMinOverride) {

        float targetVisualScale = pressureToVisualScale(pressure);
        float targetHealthScale = Math.max(0.25f, Math.min(2.5f, 1.0f / targetVisualScale));

        setScaleSmooth(entity, ScaleTypes.BASE,   targetVisualScale);
        setScaleSmooth(entity, ScaleTypes.HEALTH, targetHealthScale);

        int sizeIdx = Math.max(1, Math.min(popMaxOverride.length - 1, vanillaSize));
        float currentScale = ScaleTypes.BASE.getScaleData(entity).getScale();

        if (currentScale > popMaxOverride[sizeIdx] || currentScale < popMinOverride[sizeIdx]) {
            popGeneric(entity, (ServerLevel) entity.level(), entity.blockPosition());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Maps atmospheric pressure to a visual scale value.
     *
     * <ul>
     *   <li>At {@link #REFERENCE_PRESSURE_MBAR} → 1.0</li>
     *   <li>Higher pressure → smaller (compressed)</li>
     *   <li>Lower  pressure → larger  (expanded)</li>
     * </ul>
     *
     * Uses a simple linear interpolation across two segments
     * (reference→max-compression and reference→max-expansion) then clamps.
     */
    public static float pressureToVisualScale(float pressure) {
        if (pressure >= REFERENCE_PRESSURE_MBAR) {
            // Compression segment: 1013 mbar → scale 1.0, 1400+ mbar → scale 0.6
            float t = (pressure - REFERENCE_PRESSURE_MBAR)
                    / (MAX_COMPRESSION_PRESSURE_MBAR - REFERENCE_PRESSURE_MBAR);
            t = Math.min(1.0f, t);
            return 1.0f - t * (1.0f - MIN_VISUAL_SCALE);
        } else {
            // Expansion segment: 1013 mbar → scale 1.0, 300 mbar or below → scale 2.0
            float t = (REFERENCE_PRESSURE_MBAR - pressure)
                    / (REFERENCE_PRESSURE_MBAR - MAX_EXPANSION_PRESSURE_MBAR);
            t = Math.min(1.0f, t);
            return 1.0f + t * (MAX_VISUAL_SCALE - 1.0f);
        }
    }

    /** Sets a Pehkui scale type to {@code target} with a smooth transition. */
    public static void setScaleSmooth(net.minecraft.world.entity.LivingEntity entity,
                                      virtuoel.pehkui.api.ScaleType type,
                                      float target) {
        ScaleData data = type.getScaleData(entity);
        // Only update if meaningfully different — avoids spamming sync packets
        if (Math.abs(data.getTargetScale() - target) > 0.005f) {
            data.setScaleTickDelay(SCALE_TRANSITION_TICKS);
            data.setTargetScale(target);
        }
    }

    /**
     * Pops a vanilla {@link Slime}/{@link MagmaCube}, killing it and dropping
     * a burst-appropriate item.
     */
    private static void popSlime(Slime slime, ServerLevel level, BlockPos pos) {
        // Drop a slimeball or magma cream depending on type
        ItemStack drop = (slime instanceof MagmaCube)
                ? new ItemStack(Items.MAGMA_CREAM)
                : new ItemStack(Items.SLIME_BALL);

        level.addFreshEntity(new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));

        level.playSound(null, pos,
                SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 1.2f, 0.6f);

        // Kill without normal loot (suppress drop event via setting health to 0)
        slime.setHealth(0);
        slime.kill();
    }

    /**
     * Generic pop for non-{@link Slime} modded entities that pass through
     * {@link #applyPressureScaleCustomLimits}.
     */
    public static void popGeneric(net.minecraft.world.entity.LivingEntity entity,
                           ServerLevel level, BlockPos pos) {
        level.addFreshEntity(new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(Items.SLIME_BALL)));
        level.playSound(null, pos,
                SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 1.2f, 0.6f);
        entity.setHealth(0);
        entity.kill();
    }
}
