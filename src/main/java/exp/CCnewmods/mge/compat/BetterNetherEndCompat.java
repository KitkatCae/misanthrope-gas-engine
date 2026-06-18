package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Better Nether / Better End compat.
 *
 * <ul>
 *   <li><b>Hydrogen Jellyfish</b> — tick: off-gases H₂ into 3 vertical cells above.
 *       Death: 5×5×5 H₂ burst (walking bomb scenario in Nether acetylene atmosphere,
 *       picked up by GasDetonationHandler).</li>
 *   <li><b>Naga</b> — tick: WITHER_MIASMA + SOUL_SMOKE.</li>
 *   <li><b>BN Skull projectile</b> — impact: SOUL_ESSENCE burst.</li>
 *   <li><b>Better End Shadow Walker</b> — tick: ENDER_PARTICULATE + SOUL_ESSENCE.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BetterNetherEndCompat {

    public static final String BN_MODID  = "betternether";
    public static final String BE_MODID  = "betterend";
    private static boolean bnLoaded = false;
    private static boolean beLoaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private BetterNetherEndCompat() {}

    public static void tryLoad() {
        bnLoaded = ModList.get().isLoaded(BN_MODID);
        beLoaded = ModList.get().isLoaded(BE_MODID);
        if (bnLoaded) Mge.LOGGER.info("[MGE] Better Nether detected — jellyfish/naga emissions active.");
        if (beLoaded) Mge.LOGGER.info("[MGE] Better End detected — shadow walker emissions active.");
    }

    public static boolean isLoaded() { return bnLoaded || beLoaded; }

    // ── Tick emissions ────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!isLoaded() || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        BlockPos pos = entity.blockPosition();

        // Better Nether entities
        // Registry IDs confirmed from loot tables + spawn egg:
        // betternether:hydrogen_jellyfish, betternether:naga
        if (bnLoaded && type.startsWith("betternether:")) {

            if (type.equals("betternether:hydrogen_jellyfish")) {
                // Off-gas H₂ upward through 3 vertical cells
                for (int dy = 1; dy <= 3; dy++) {
                    gas(level, pos.above(dy), GasRegistry.HYDROGEN, 8f / dy);
                }

            } else if (type.equals("betternether:naga")) {
                gas(level, pos, GasRegistry.WITHER_MIASMA, 8f);
                gas(level, pos, GasRegistry.SOUL_SMOKE,    6f);
                part(level, pos, ParticulateType.SOUL_WISPS, 8f);
            }
        }

        // Better End entities
        // Registry ID confirmed from EndEntities.class: betterend:shadow_walker
        if (beLoaded && type.startsWith("betterend:")) {
            if (type.equals("betterend:shadow_walker")) {
                gas(level, pos, GasRegistry.ENDER_PARTICULATE, 6f);
                gas(level, pos, GasRegistry.SOUL_ESSENCE, 5f);
                part(level, pos, ParticulateType.SOUL_WISPS, 6f);
            }
        }
    }

    // ── Projectile impacts ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!isLoaded() || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;

        String type = proj.getType().toString();
        BlockPos pos = BlockPos.containing(proj.position());

        // Registry IDs confirmed: betternether:skull, betternether:naga_projectile
        if (bnLoaded && type.startsWith("betternether:")) {
            if (type.equals("betternether:skull") || type.equals("betternether:naga_projectile")) {
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE, 15f, 2);
                gasRadius(level, pos, GasRegistry.SOUL_SMOKE,   10f, 2);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 20f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
            }
        }
    }

    // ── Called from MobDeathAtmosphereHandler ─────────────────────────────────

    /**
     * Hydrogen Jellyfish death — large 5×5×5 H₂ burst.
     * The GasDetonationHandler will detonate this if an ignition source is nearby.
     */
    public static void onHydrogenJellyfishDeath(ServerLevel level, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float falloff = Math.max(0f, 1f - dist / 4f);
                    gas(level, pos.offset(dx, dy, dz), GasRegistry.HYDROGEN, 60f * falloff);
                }
            }
        }
        ShockwaveHandler.spawn(level, pos, 3f);
    }
}
