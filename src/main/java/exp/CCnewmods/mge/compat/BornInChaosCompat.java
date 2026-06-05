package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Born in Chaos compat.
 *
 * <p>Modid: {@code born_in_chaos_v1} (confirmed from jar manifest).</p>
 *
 * <ul>
 *   <li><b>Bone undead</b> (decrepit_skeleton, skeleton_demoman, skeleton_thrasher,
 *       bonescaller, bone_imp, door_knight, fallen_chaos_knight, siamese_skeletons,
 *       dread_hound, dire_hound_leader) — tick: SOUL_ESSENCE + H₂S.</li>
 *   <li><b>Boss undead</b> (supreme_bonescaller, krampus, lord_pumpkinhead,
 *       lord_the_headless, sir_pumpkinhead, sir_the_headless, nightmare_stalker,
 *       scarlet_persecutor) — same gases at 3× scale.</li>
 *   <li><b>Spirit types</b> (infernal_spirit, restless_spirit, seared_spirit,
 *       pumpkin_spirit, spiritof_chaos, spirit_guide) — tick: SOUL_ESSENCE.</li>
 *   <li><b>Dark Vortex</b> — tick: ENDER_PARTICULATE + SOUL_ESSENCE, drains O₂.</li>
 *   <li><b>Lifestealer</b> — tick: ORGANIC_AEROSOL + H₂S while moving.</li>
 *   <li><b>Firelight</b> — tick: BLAZE_FUME + SO₂.</li>
 *   <li><b>Death</b>: bosses 40f burst radius 5 + shockwave; regulars 8f radius 2.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BornInChaosCompat {

    public static final String MODID = "born_in_chaos_v1";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    // ── Entity ID sets (all confirmed from BornInChaosV1ModEntities.class) ───

    private static final Set<String> BONE_UNDEAD = Set.of(
        "born_in_chaos_v1:decrepit_skeleton",
        "born_in_chaos_v1:skeleton_demoman",
        "born_in_chaos_v1:skeleton_thrasher",
        "born_in_chaos_v1:skeleton_thrasher_not_despawn",
        "born_in_chaos_v1:bonescaller",
        "born_in_chaos_v1:bonescaller_not_despawn",
        "born_in_chaos_v1:bone_imp",
        "born_in_chaos_v1:bone_imp_minion",
        "born_in_chaos_v1:door_knight",
        "born_in_chaos_v1:door_knight_not_despawn",
        "born_in_chaos_v1:fallen_chaos_knight",
        "born_in_chaos_v1:siamese_skeletons",
        "born_in_chaos_v1:dread_hound",
        "born_in_chaos_v1:dread_hound_not_despawn",
        "born_in_chaos_v1:dire_hound_leader",
        "born_in_chaos_v1:baby_skeleton",
        "born_in_chaos_v1:baby_skeleton_minion"
    );

    private static final Set<String> BOSS_UNDEAD = Set.of(
        "born_in_chaos_v1:supreme_bonescaller",
        "born_in_chaos_v1:supreme_bonescaller_not_despawn",
        "born_in_chaos_v1:krampus",
        "born_in_chaos_v1:lord_pumpkinhead",
        "born_in_chaos_v1:lord_the_headless",
        "born_in_chaos_v1:sir_pumpkinhead",
        "born_in_chaos_v1:sir_the_headless",
        "born_in_chaos_v1:nightmare_stalker",
        "born_in_chaos_v1:scarlet_persecutor",
        "born_in_chaos_v1:pumpkinhead"
    );

    private static final Set<String> SPIRITS = Set.of(
        "born_in_chaos_v1:infernal_spirit",
        "born_in_chaos_v1:restless_spirit",
        "born_in_chaos_v1:seared_spirit",
        "born_in_chaos_v1:seared_spirit_not_despawn",
        "born_in_chaos_v1:pumpkin_spirit",
        "born_in_chaos_v1:spiritof_chaos",
        "born_in_chaos_v1:spirit_guide",
        "born_in_chaos_v1:spirit_guide_assistant"
    );

    private BornInChaosCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Born in Chaos detected — undead atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    // ── Tick emissions ────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("born_in_chaos_v1:")) return;

        BlockPos pos = entity.blockPosition();

        if (BOSS_UNDEAD.contains(type)) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE,     18f);
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE,  12f);
            gas(level, pos, GasRegistry.WITHER_MIASMA,     8f);
            part(level, pos, ParticulateType.SOUL_WISPS,   24f);

        } else if (BONE_UNDEAD.contains(type)) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE,    6f);
            gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 4f);
            part(level, pos, ParticulateType.SOUL_WISPS,  8f);

        } else if (SPIRITS.contains(type)) {
            gas(level, pos, GasRegistry.SOUL_ESSENCE, 10f);
            part(level, pos, ParticulateType.SOUL_WISPS, 12f);

        } else if (type.equals("born_in_chaos_v1:dark_vortex")) {
            gas(level, pos, GasRegistry.ENDER_PARTICULATE, 10f);
            gas(level, pos, GasRegistry.SOUL_ESSENCE,       8f);
            drain(level, pos, GasRegistry.OXYGEN, 12f);
            part(level, pos, ParticulateType.SOUL_WISPS, 10f);

        } else if (type.equals("born_in_chaos_v1:lifestealer")
                || type.equals("born_in_chaos_v1:lifestealer_true_form")) {
            if (entity instanceof LivingEntity le && le.getDeltaMovement().lengthSqr() > 0.01) {
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 8f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 2f);
            }

        } else if (type.equals("born_in_chaos_v1:firelight")
                || type.equals("born_in_chaos_v1:firelight_not_despawn")) {
            gas(level, pos, GasRegistry.BLAZE_FUME, 10f);
            gas(level, pos, GasRegistry.SULFUR_DIOXIDE, 6f);
            drain(level, pos, GasRegistry.OXYGEN, 6f);
        }
    }

    // ── Death bursts ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        String type = event.getEntity().getType().toString();
        if (!type.startsWith("born_in_chaos_v1:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();
        boolean boss = BOSS_UNDEAD.contains(type);

        float baseAmount = boss ? 40f : 8f;
        int radius = boss ? 5 : 2;

        gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,    baseAmount,        radius);
        gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, baseAmount * 0.3f, radius);
        partRadius(level, pos, ParticulateType.SOUL_WISPS,  baseAmount * 1.5f, radius);

        if (boss) {
            ShockwaveHandler.spawn(level, pos, 8f);
            ShockwaveDataPacket.sendToNear(level, vec, 8f, 64f);
        }
    }
}
