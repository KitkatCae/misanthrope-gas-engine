package exp.CCnewmods.mge.shockwave;

import exp.CCnewmods.mge.particulate.ParticulateComposition;
import exp.CCnewmods.mge.particulate.ParticulateType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Collapses all 29 {@link ParticulateType} values into 4 coarse visual
 * buckets for the shockwave shader. Sending all 29 raw types over the
 * network every frame isn't worth it — the shader only needs broad strokes:
 * how much fine haze, how much heavy debris, how much dark ash/smoke, and
 * how much glowing exotic matter the wave is carrying.
 *
 * <p>Grouping rationale:
 * <ul>
 *   <li>{@link #FINE} — light, slow-settling, low-opacity. Drives haze/
 *       desaturation without much visible "debris."</li>
 *   <li>{@link #HEAVY} — fast-settling, coarse, debris-like. Drives the
 *       look of physical chunks flying outward rather than haze.</li>
 *   <li>{@link #ASH} — dark, opaque, combustion-associated. Drives smoke
 *       particle spawning and the strongest desaturation/whitening.</li>
 *   <li>{@link #EXOTIC} — luminescent or energetically unusual matter.
 *       Drives its own glow independent of ambient temperature (e.g.
 *       pyrotheum dust is intensely hot regardless of surrounding air).</li>
 * </ul>
 */
public enum ParticulateBucket {
    FINE,
    HEAVY,
    ASH,
    EXOTIC;

    private static final Map<ParticulateType, ParticulateBucket> MAP = new EnumMap<>(ParticulateType.class);

    static {
        // ── FINE ──────────────────────────────────────────────────────────
        put(ParticulateType.DUST, FINE);
        put(ParticulateType.RED_SAND_DUST, FINE);
        put(ParticulateType.CEMENT_DUST, FINE);
        put(ParticulateType.NETHER_QUARTZ_DUST, FINE);
        put(ParticulateType.POLLEN, FINE);
        put(ParticulateType.BROWN_MUSHROOM_SPORES, FINE);
        put(ParticulateType.RED_MUSHROOM_SPORES, FINE);
        put(ParticulateType.WARPED_SPORES, FINE);
        put(ParticulateType.CRIMSON_SPORES, FINE);
        put(ParticulateType.OPHIOCORDYCEPS_HUMANUS, FINE);
        put(ParticulateType.SPORE_CLUSTER, FINE);
        put(ParticulateType.CHORUS_SPORES, FINE);
        put(ParticulateType.ORGANIC_AEROSOL, FINE);
        put(ParticulateType.ICE_CRYSTALS, FINE);
        put(ParticulateType.WATER_DROPLETS, FINE);

        // ── HEAVY ─────────────────────────────────────────────────────────
        put(ParticulateType.SAND, HEAVY);
        put(ParticulateType.GRAVEL_DUST, HEAVY);
        put(ParticulateType.RUST_PARTICLES, HEAVY);
        put(ParticulateType.IRON_FILINGS, HEAVY);
        put(ParticulateType.PLANT_DEBRIS, HEAVY);
        put(ParticulateType.GLOWSTONE_DUST, HEAVY);
        put(ParticulateType.REDSTONE_DUST, HEAVY);
        put(ParticulateType.SOUL_DUST, HEAVY);
        put(ParticulateType.COAL_DUST, HEAVY);
        put(ParticulateType.ASBESTOS_FIBER, HEAVY);
        put(ParticulateType.LEAD_DUST, HEAVY);
        put(ParticulateType.ICE_CRYSTAL_SHARDS, HEAVY);

        // ── ASH ───────────────────────────────────────────────────────────
        put(ParticulateType.VOLCANIC_ASH, ASH);
        put(ParticulateType.ASH_CLOUD, ASH);
        put(ParticulateType.SOOT, ASH);
        put(ParticulateType.SMOKE_AEROSOL, ASH);

        // ── EXOTIC ────────────────────────────────────────────────────────
        put(ParticulateType.PYROTHEUM_DUST, EXOTIC);
        put(ParticulateType.IONISED_PARTICLES, EXOTIC);
        put(ParticulateType.SOUL_WISPS, EXOTIC);

        // Safety net: any ParticulateType added later without an explicit
        // mapping falls back to FINE rather than throwing or silently
        // vanishing from the shader's awareness.
        for (ParticulateType t : ParticulateType.values()) {
            MAP.putIfAbsent(t, FINE);
        }
    }

    private static void put(ParticulateType type, ParticulateBucket bucket) {
        MAP.put(type, bucket);
    }

    public static ParticulateBucket of(ParticulateType type) {
        return MAP.getOrDefault(type, FINE);
    }

    /**
     * Sums a full {@link ParticulateComposition} into 4 bucket totals
     * (mg/m³), indexed {@code [FINE, HEAVY, ASH, EXOTIC]} via {@link #ordinal()}.
     */
    public static float[] sumBuckets(ParticulateComposition comp) {
        float[] totals = new float[ParticulateBucket.values().length];
        for (ParticulateType type : ParticulateType.values()) {
            float amt = comp.get(type);
            if (amt <= 0f) continue;
            totals[of(type).ordinal()] += amt;
        }
        return totals;
    }
}
