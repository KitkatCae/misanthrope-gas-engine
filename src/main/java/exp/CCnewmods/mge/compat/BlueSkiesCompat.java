package exp.CCnewmods.mge.compat;

import exp.CCnewmods.mge.Mge;
import exp.CCnewmods.mge.MgeConfig;
import exp.CCnewmods.mge.gas.GasRegistry;
import exp.CCnewmods.mge.grid.compat.GridAtmosphereCompat;
import exp.CCnewmods.mge.particulate.ParticulateType;
import exp.CCnewmods.mge.shockwave.ShockwaveDataPacket;
import exp.CCnewmods.mge.shockwave.ShockwaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

import static exp.CCnewmods.mge.compat.MobAtmosphereUtil.*;

/**
 * Blue Skies compat (modid: blue_skies).
 *
 * All entity IDs confirmed from SkiesEntityTypes.class and loot tables.
 *
 * <ul>
 *   <li><b>frost_spirit / armored_frost_spirit</b> — DRAGON_ICE_CLOUD + ICE_CRYSTALS.</li>
 *   <li><b>polargeist</b> — spectral ice: SOUL_ESSENCE + DRAGON_ICE_CLOUD.</li>
 *   <li><b>jelly_drifter</b> — WATER_VAPOR + H₂S (deep sky jellyfish).</li>
 *   <li><b>gatekeeper</b> — WITHER_MIASMA + SOUL_ESSENCE (dimensional guardian).</li>
 *   <li><b>summoner</b> — SOUL_ESSENCE burst on tick.</li>
 *   <li><b>starlit_crusher</b> — DUST + GRAVEL_DUST + shockwave (heavy golem).</li>
 *   <li><b>venom_spider / diophyde_prowler / infested_swarmer</b> — H₂S + ORGANIC_AEROSOL.</li>
 *   <li><b>spewter</b> — ORGANIC_AEROSOL (bile creature).</li>
 *   <li><b>emberback / charscale_moki</b> — BLAZE_FUME + SO₂ (fire creatures).</li>
 *   <li><b>azulfo / cosmic_fox / crynocerous</b> — mild IONISED_AIR (exotic sky fauna).</li>
 *   <li><b>Projectiles</b>: strange_lightning → plasma channel + shockwave;
 *       venom_bomb → H₂S + ORGANIC_AEROSOL burst;
 *       venom_spit → trace H₂S; decaying_spike → CADAVERINE + H₂S;
 *       fluctuant_sphere → IONISED_AIR; spear → DUST + shockwave.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Mge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlueSkiesCompat {

    public static final String MODID = "blue_skies";
    private static boolean loaded = false;

    private static final int TICK_INTERVAL = 20;
    private static int tick = 0;

    private static final Set<String> VENOM = Set.of(
        "blue_skies:venom_spider", "blue_skies:diophyde_prowler", "blue_skies:infested_swarmer"
    );

    private BlueSkiesCompat() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Blue Skies detected — sky dimension atmosphere emissions active.");
    }

    public static boolean isLoaded() { return loaded; }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (++tick % TICK_INTERVAL != 0) return;

        var entity = event.getEntity();
        String type = entity.getType().toString();
        if (!type.startsWith("blue_skies:")) return;

        BlockPos pos = entity.blockPosition();

        switch (type) {
            case "blue_skies:frost_spirit",
                 "blue_skies:armored_frost_spirit",
                 "blue_skies:armored_frost_spirit_packed",
                 "blue_skies:crynocerous",
                 "blue_skies:crynocerous_packed" -> {
                gasRadius(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 15f, 3);
                partRadius(level, pos, ParticulateType.ICE_CRYSTALS, 18f, 3);
            }
            case "blue_skies:polargeist" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,     8f);
                gas(level, pos, GasRegistry.DRAGON_ICE_CLOUD, 8f);
                part(level, pos, ParticulateType.SOUL_WISPS, 10f);
            }
            case "blue_skies:jelly_drifter" -> {
                for (int dy = 1; dy <= 3; dy++) {
                    gas(level, pos.above(dy), GasRegistry.WATER_VAPOR,    5f / dy);
                    gas(level, pos.above(dy), GasRegistry.HYDROGEN_SULFIDE, 3f / dy);
                }
            }
            case "blue_skies:gatekeeper" -> {
                gas(level, pos, GasRegistry.WITHER_MIASMA, 10f);
                gas(level, pos, GasRegistry.SOUL_ESSENCE,   8f);
                part(level, pos, ParticulateType.SOUL_WISPS, 12f);
            }
            case "blue_skies:summoner" -> {
                gas(level, pos, GasRegistry.SOUL_ESSENCE,   12f);
                part(level, pos, ParticulateType.SOUL_WISPS, 15f);
            }
            case "blue_skies:starlit_crusher",
                 "blue_skies:artificial_golem" -> {
                part(level, pos, ParticulateType.DUST,       15f);
                part(level, pos, ParticulateType.GRAVEL_DUST, 10f);
                ShockwaveHandler.spawn(level, pos, 2f);
                ShockwaveDataPacket.sendToNear(level, entity.position(), 2f, 20f);
            }
            case "blue_skies:emberback",
                 "blue_skies:charscale_moki" -> {
                gas(level, pos, GasRegistry.BLAZE_FUME,     8f);
                gas(level, pos, GasRegistry.SULFUR_DIOXIDE,  4f);
                drain(level, pos, GasRegistry.OXYGEN, 5f);
            }
            case "blue_skies:spewter" ->
                part(level, pos, ParticulateType.ORGANIC_AEROSOL, 8f);
            default -> {
                if (VENOM.contains(type)) {
                    gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 5f);
                    part(level, pos, ParticulateType.ORGANIC_AEROSOL, 6f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        String type = event.getEntity().getType().toString();
        if (!type.startsWith("blue_skies:")) return;

        BlockPos pos = event.getEntity().blockPosition();
        Vec3 vec = event.getEntity().position();

        switch (type) {
            case "blue_skies:gatekeeper" -> {
                gasRadius(level, pos, GasRegistry.WITHER_MIASMA, 40f, 5);
                gasRadius(level, pos, GasRegistry.SOUL_ESSENCE,  25f, 4);
                partRadius(level, pos, ParticulateType.SOUL_WISPS, 50f, 5);
                ShockwaveHandler.spawn(level, pos, 8f);
                ShockwaveDataPacket.sendToNear(level, vec, 8f, 64f);
            }
            case "blue_skies:starlit_crusher" -> {
                partRadius(level, pos, ParticulateType.DUST,        80f, 6);
                partRadius(level, pos, ParticulateType.GRAVEL_DUST, 60f, 5);
                ShockwaveHandler.spawn(level, pos, 9f);
                ShockwaveDataPacket.sendToNear(level, vec, 9f, 72f);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!loaded || !MgeConfig.enableGasEffects) return;
        Entity proj = event.getEntity();
        if (!(proj.level() instanceof ServerLevel level)) return;
        String type = proj.getType().toString();
        if (!type.startsWith("blue_skies:")) return;

        BlockPos pos = BlockPos.containing(proj.position());
        Vec3 vec = proj.position();

        switch (type) {
            case "blue_skies:strange_lightning" -> {
                // Sky lightning — same plasma channel chemistry as vanilla
                var comp = GridAtmosphereCompat.getComposition(level, pos);
                float n2 = comp.get(GasRegistry.NITROGEN);
                float o2 = comp.get(GasRegistry.OXYGEN);
                float reacted = Math.min(n2, o2) * 0.35f;
                comp.add(GasRegistry.NITROGEN,    -reacted);
                comp.add(GasRegistry.OXYGEN,      -reacted);
                comp.add(GasRegistry.NITRIC_OXIDE, reacted * 1.8f);
                comp.add(GasRegistry.IONISED_AIR,  (n2 + o2) * 0.10f);
                comp.add(GasRegistry.OZONE,         o2 * 0.05f);
                GridAtmosphereCompat.setComposition(level, pos, comp);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 12f, 2);
                ShockwaveHandler.spawn(level, pos, 5f);
                ShockwaveDataPacket.sendToNear(level, vec, 5f, 48f);
            }
            case "blue_skies:venom_bomb" -> {
                gasRadius(level, pos, GasRegistry.HYDROGEN_SULFIDE, 18f, 3);
                partRadius(level, pos, ParticulateType.ORGANIC_AEROSOL, 25f, 3);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
            case "blue_skies:venom_spit" ->
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 6f);
            case "blue_skies:decaying_spike" -> {
                gas(level, pos, GasRegistry.CADAVERINE,       8f);
                gas(level, pos, GasRegistry.HYDROGEN_SULFIDE, 5f);
            }
            case "blue_skies:fluctuant_sphere" -> {
                gasRadius(level, pos, GasRegistry.IONISED_AIR, 15f, 2);
                partRadius(level, pos, ParticulateType.IONISED_PARTICLES, 18f, 2);
                ShockwaveHandler.spawn(level, pos, 3f);
                ShockwaveDataPacket.sendToNear(level, vec, 3f, 28f);
            }
            case "blue_skies:spear",
                 "blue_skies:ent_root",
                 "blue_skies:ent_wall" -> {
                partRadius(level, pos, ParticulateType.DUST,       15f, 2);
                ShockwaveHandler.spawn(level, pos, 2f);
                ShockwaveDataPacket.sendToNear(level, vec, 2f, 18f);
            }
        }
    }
}
