package exp.CCnewmods.mge.particulate;

/**
 * Airborne particulate matter types tracked in atmosphere NBT alongside gases.
 *
 * <p>Particulates differ from gases in key ways:
 * <ul>
 *   <li>They are measured in mg/m³ (milligrams per cubic metre) rather than mbar.</li>
 *   <li>They settle over time — gravity slowly removes them downward unless wind keeps them aloft.</li>
 *   <li>They can coat surfaces, clog filters, and cause mechanical damage in compat contexts.</li>
 *   <li>They interact with moisture: some absorb water vapour, some dissolve, some clump.</li>
 * </ul>
 *
 * <p>NBT key format: {@code "mge:particulate.<id>"}, e.g. {@code "mge:particulate.sand"}.</p>
 */
public enum ParticulateType {

    // ── Mineral / geological ─────────────────────────────────────────────────

    /** Fine quartz and feldspar grains. Settles quickly, abrades lungs. */
    SAND("sand",
            0xFFD580AA,  // warm tan, semi-opaque
            SettleBehaviour.FAST,
            /*toxicMgM3=*/ 500f, ToxicEffect.MINING_FATIGUE,
            /*windSens=*/ 0.9f),

    /** Finer than sand — hangs longer, penetrates deeper into airways. */
    DUST("dust",
            0xBBAA9977,
            SettleBehaviour.MEDIUM,
            300f, ToxicEffect.MINING_FATIGUE,
            1.1f),

    /** Desert / Badlands fine red-orange silt. */
    RED_SAND_DUST("red_sand_dust",
            0xBBCC6633,
            SettleBehaviour.MEDIUM,
            300f, ToxicEffect.MINING_FATIGUE,
            1.0f),

    /** Volcanic ash — very fine, abrasive, long hang time. */
    VOLCANIC_ASH("volcanic_ash",
            0xCC666666,
            SettleBehaviour.SLOW,
            200f, ToxicEffect.SUFFOCATION,
            0.7f),

    /**
     * Dense combustion ash cloud — from explosions, fires, industrial burning.
     * Heavier than volcanic ash, settles faster, very high visual opacity.
     */
    ASH_CLOUD("ash_cloud",
            0xDD333333,
            SettleBehaviour.MEDIUM,
            150f, ToxicEffect.SUFFOCATION,
            0.6f),

    /** Ground gravel particles. Coarse, settles almost instantly. */
    GRAVEL_DUST("gravel_dust",
            0x99888888,
            SettleBehaviour.INSTANT,
            800f, ToxicEffect.MINING_FATIGUE,
            0.4f),

    // ── Combustion / industrial ───────────────────────────────────────────────

    /** Wood/coal combustion soot. Sticky, coats surfaces, carcinogenic. */
    SOOT("soot",
            0xDD111111,
            SettleBehaviour.SLOW,
            150f, ToxicEffect.WITHER,
            0.8f),

    /** Mixed combustion aerosol — opacity-heavy. Produced by fire events. */
    SMOKE_AEROSOL("smoke_aerosol",
            0xAA444444,
            SettleBehaviour.VERY_SLOW,
            100f, ToxicEffect.WITHER,
            1.0f),

    /** Iron/steel oxide flakes. Heavy, settles fast, mildly toxic. */
    RUST_PARTICLES("rust_particles",
            0x99AA4422,
            SettleBehaviour.FAST,
            600f, ToxicEffect.NAUSEA,
            0.5f),

    /** Fine iron powder — flammable when concentrated. */
    IRON_FILINGS("iron_filings",
            0x88AAAAAA,
            SettleBehaviour.FAST,
            400f, ToxicEffect.NAUSEA,
            0.4f),

    /** Concrete/cement fine particles. */
    CEMENT_DUST("cement_dust",
            0xAABBBBBB,
            SettleBehaviour.MEDIUM,
            350f, ToxicEffect.MINING_FATIGUE,
            0.7f),

    // ── Biological / organic ─────────────────────────────────────────────────

    /**
     * Ophiocordyceps humanus — the zombie-ant fungus adapted to human hosts.
     * Microscopic spores released by infected corpses and fungal blooms.
     * At low concentration: nausea, disorientation. At high concentration:
     * wither (CNS invasion). Spores are near-invisible individually but form
     * a faint sickly-green haze in dense clouds. Extremely slow to settle —
     * they remain suspended for minutes in still air.
     */
    OPHIOCORDYCEPS_HUMANUS("ophiocordyceps_humanus",
            0x7722AA44,   // faint sickly green
            SettleBehaviour.VERY_SLOW,
            40f, ToxicEffect.NAUSEA,
            1.5f),

    /**
     * Brown mushroom spores — emitted by Blocks.BROWN_MUSHROOM and
     * BROWN_MUSHROOM_BLOCK on random tick. Mildly disorienting; contributes to
     * fungal growth in the atmosphere grid at sufficient concentration.
     * Warm earthy-brown tint.
     */
    BROWN_MUSHROOM_SPORES("brown_mushroom_spores",
            0x88996633,
            SettleBehaviour.VERY_SLOW,
            80f, ToxicEffect.NAUSEA,
            1.4f),

    /**
     * Red mushroom spores — emitted by Blocks.RED_MUSHROOM and RED_MUSHROOM_BLOCK.
     * Slightly more toxic than brown; high concentration causes poison.
     */
    RED_MUSHROOM_SPORES("red_mushroom_spores",
            0x88CC3322,
            SettleBehaviour.VERY_SLOW,
            50f, ToxicEffect.NAUSEA,
            1.4f),

    /**
     * Crimson spores — emitted by Crimson Fungus and Crimson Nylium in the Nether.
     * Denser and heavier than Overworld spores; deep red-orange haze.
     * Contributes to crimson fungus growth in the grid.
     */
    CRIMSON_SPORES("crimson_spores",
            0xAA992222,
            SettleBehaviour.SLOW,           // heavier nether spores settle faster
            60f, ToxicEffect.NAUSEA,
            1.1f),

    /**
     * Warped spores — emitted by Warped Fungus and Warped Nylium.
     * Mildly disorienting, faint cyan-teal haze. Lighter than crimson spores.
     * Contributes to warped fungus growth in the grid.
     */
    WARPED_SPORES("warped_spores",
            0x8822CCAA,
            SettleBehaviour.VERY_SLOW,
            70f, ToxicEffect.NAUSEA,
            1.3f),

    /** Pollen from flowering plants. Seasonal, mild irritant. */
    POLLEN("pollen",
            0x66FFEE44,
            SettleBehaviour.VERY_SLOW,
            200f, ToxicEffect.NAUSEA,
            1.5f),

    /** Dried plant fibres and leaf fragments. */
    PLANT_DEBRIS("plant_debris",
            0x55886633,
            SettleBehaviour.MEDIUM,
            1000f, ToxicEffect.NONE,
            1.1f),

    // ── Ice / moisture ────────────────────────────────────────────────────────

    /** Tiny ice crystals — reduces visibility, very cold. */
    ICE_CRYSTALS("ice_crystals",
            0x88CCEEFF,
            SettleBehaviour.SLOW,
            /*non-toxic, but causes cold*/0f, ToxicEffect.NONE,
            1.2f),

    /** Fog droplets — liquid water in suspension. */
    WATER_DROPLETS("water_droplets",
            0x66DDEEFF,
            SettleBehaviour.VERY_SLOW,
            0f, ToxicEffect.NONE,
            1.4f),

    // ── Magical / otherworldly ────────────────────────────────────────────────

    /** Glowstone dust fragments — luminescent, mildly toxic. */
    GLOWSTONE_DUST("glowstone_dust",
            0x88FFDD44,
            SettleBehaviour.SLOW,
            150f, ToxicEffect.NAUSEA,
            0.9f),

    /** Nether quartz fine particles. */
    NETHER_QUARTZ_DUST("nether_quartz_dust",
            0x88FFEEDD,
            SettleBehaviour.MEDIUM,
            300f, ToxicEffect.NAUSEA,
            0.7f),

    /** Redstone fine particles. Mildly radioactive/energetic. */
    REDSTONE_DUST("redstone_dust",
            0x99FF2222,
            SettleBehaviour.MEDIUM,
            200f, ToxicEffect.WITHER,
            0.8f),

    /** Chorus plant spores from End dimension. */
    CHORUS_SPORES("chorus_spores",
            0x88AA66CC,
            SettleBehaviour.VERY_SLOW,
            100f, ToxicEffect.LEVITATION,
            1.2f),

    /** Soul sand fine particles. Draining. */
    SOUL_DUST("soul_dust",
            0x88336688,
            SettleBehaviour.SLOW,
            100f, ToxicEffect.SLOWNESS,
            0.8f),

    /**
     * Coal dust — produced by mining coal ore/blocks and by coal combustion.
     * Causes respiratory impairment at concentration. Explosive above ~150 mg/m³
     * with an ignition source present (handled by GasDetonationHandler).
     */
    COAL_DUST("coal_dust",
            0xBB111111,
            SettleBehaviour.SLOW,
            80f, ToxicEffect.MINING_FATIGUE,
            0.5f),

    /**
     * Asbestos fibres — produced by mining/disturbing asbestos ore or block.
     * Requires Oreganized Carcinogenius. Applies lung_damage effect via compat.
     * Low settle rate — fibres remain suspended in air for a long time.
     */
    ASBESTOS_FIBER("asbestos_fiber",
            0xAAC8C8B0,
            SettleBehaviour.VERY_SLOW,
            15f, ToxicEffect.SUFFOCATION,  // lung_damage applied via OreganizedCompat override
            1.2f),

    /**
     * Lead dust — produced by mining lead ore, breaking lead blocks, and firing lead bolts.
     * Requires Oreganized. Applies stunning effect via compat. Heavier than air, settles fast.
     */
    LEAD_DUST("lead_dust",
            0xAAA0A8A0,
            SettleBehaviour.FAST,
            40f, ToxicEffect.SLOWNESS,     // stunning applied via OreganizedCompat override
            0.3f),

    /**
     * Organic aerosol — fine droplets and particulates from biological matter,
     * cooking, corpse decay, and swamp environments. Causes nausea at high concentration.
     */
    ORGANIC_AEROSOL("organic_aerosol",
            0x66886644,
            SettleBehaviour.VERY_SLOW,
            200f, ToxicEffect.NAUSEA,
            1.0f),

    // ── Magical / combat ──────────────────────────────────────────────────────

    /**
     * Ice Crystal Shards — jagged macro-scale ice fragments expelled by ice dragon
     * breath and Frostmaw attacks. Larger than ICE_CRYSTALS; settles faster and
     * causes direct laceration damage at high concentration. Very cold to the touch.
     */
    ICE_CRYSTAL_SHARDS("ice_crystal_shards",
            0xBBAADDFF,
            SettleBehaviour.FAST,
            50f, ToxicEffect.SUFFOCATION,  // direct impact damage handled in compat
            0.7f),

    /**
     * Soul Wisps — luminescent spectral particulate released by the death or
     * corruption of undead and ghost-type entities. Mildly disorienting.
     * Extremely buoyant — rises rather than settles; treated as VERY_SLOW settle
     * but with upward bias in the diffusion ticker.
     */
    SOUL_WISPS("soul_wisps",
            0x8844CCDD,
            SettleBehaviour.VERY_SLOW,
            80f, ToxicEffect.WITHER,
            1.3f),

    /**
     * Ionised Particles — energised charged particulates produced alongside
     * IONISED_AIR by lightning, sonic booms, and plasma events. Faint blue-white
     * luminescence. Settles slowly; mildly disrupts electronics (compat flavour).
     */
    IONISED_PARTICLES("ionised_particles",
            0x7777AAFF,
            SettleBehaviour.SLOW,
            120f, ToxicEffect.WEAKNESS,
            1.1f),

    /**
     * Spore Cluster — a dense clump of mixed fungal spores, larger than individual
     * spore gases. Produced when large mushroom blocks break or spore gas
     * concentration reaches saturation. Settles at medium rate.
     */
    SPORE_CLUSTER("spore_cluster",
            0x88AAAA44,
            SettleBehaviour.MEDIUM,
            100f, ToxicEffect.NAUSEA,
            0.9f),

    /**
     * Pyrotheum Dust — radioactive superheavy Group 14 element in fine dust form.
     * Gold-to-sulfur coloured. Radioactive decay continuously ionises surrounding
     * air into plasma, heating the environment to ~2200°C ambient output.
     * The ecological foundation of blaze biology (blazing blood is pyrotheum-buffered).
     * At high concentration causes fire damage and radiation sickness (Wither).
     * Thermally buoyant — settle is very slow. Co-emits IONISED_AIR as secondary effect.
     */
    PYROTHEUM_DUST("pyrotheum_dust",
            0xCCFFAA00,
            SettleBehaviour.VERY_SLOW,
            30f, ToxicEffect.WITHER,
            1.5f);

    // ── Data ──────────────────────────────────────────────────────────────────

    /** NBT sub-key, e.g. {@code "sand"} → full key {@code "mge:particulate.sand"}. */
    public final String id;

    /** ARGB colour tint for the renderer. Alpha encodes base opacity per mg/m³. */
    public final int colorARGB;

    /** How quickly this particulate settles downward per diffusion tick. */
    public final SettleBehaviour settle;

    /** Concentration in mg/m³ above which toxic effects begin. 0 = non-toxic. */
    public final float toxicThresholdMgM3;

    /** The toxic effect applied when threshold is exceeded. */
    public final ToxicEffect toxicEffect;

    /** Wind sensitivity multiplier — high = stays aloft in wind. */
    public final float windSensitivity;

    ParticulateType(String id, int colorARGB, SettleBehaviour settle,
                    float toxicThresholdMgM3, ToxicEffect toxicEffect, float windSensitivity) {
        this.id = id;
        this.colorARGB = colorARGB;
        this.settle = settle;
        this.toxicThresholdMgM3 = toxicThresholdMgM3;
        this.toxicEffect = toxicEffect;
        this.windSensitivity = windSensitivity;
    }

    /** Full NBT key used in the atmosphere CompoundTag. */
    public String nbtKey() {
        return "mge:particulate." + id;
    }

    public boolean isToxic() {
        return toxicThresholdMgM3 > 0f && toxicEffect != ToxicEffect.NONE;
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * How many mg/m³ settle per diffusion tick due to gravity.
     * Wind counteracts settling proportionally to wind speed × windSensitivity.
     */
    public enum SettleBehaviour {
        INSTANT(50f),    // gravel, heavy debris
        FAST(10f),       // sand, rust
        MEDIUM(3f),      // dust, cement
        SLOW(1f),        // soot, ash, ice crystals
        VERY_SLOW(0.2f); // spores, pollen, aerosols

        public final float mgM3PerTick;
        SettleBehaviour(float rate) { this.mgM3PerTick = rate; }
    }

    /**
     * Re-uses gas ToxicEffect semantics for particulates.
     * Imported as a standalone enum to avoid a cross-package dependency cycle.
     */
    public enum ToxicEffect {
        NONE, MINING_FATIGUE, NAUSEA, WITHER, SUFFOCATION, LEVITATION, SLOWNESS, WEAKNESS
    }
}
