package exp.CCnewmods.mge.photon;

import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import exp.CCnewmods.mge.Mge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

/**
 * Plays Photon particle effects for MGE events.
 *
 * <h3>Effect files</h3>
 * All FX definitions live in {@code assets/mge/fx/} as NBT {@code .fx} files.
 * They can be edited using Photon's in-game editor (the {@code /photon} command)
 * or by hand-editing the NBT. To add a new effect:
 * <ol>
 *   <li>Create {@code assets/mge/fx/my_effect.fx} (copy an existing one as base)</li>
 *   <li>Register a {@code ResourceLocation} constant here</li>
 *   <li>Call {@link #play} from the relevant event handler</li>
 * </ol>
 *
 * <h3>Currently registered effects</h3>
 * <ul>
 *   <li>{@code mge:fx/detonation} — gas explosion flash, fireball, radial smoke</li>
 *   <li>{@code mge:fx/shockwave_ring} — expanding refractive ring at wave front</li>
 *   <li>{@code mge:fx/coal_dust_explosion} — brown-black dust cloud burst</li>
 *   <li>{@code mge:fx/vacuum_shimmer} — heat-shimmer distortion on vacuum boundary</li>
 *   <li>{@code mge:fx/gas_leak} — coloured gas jet from pressurised container</li>
 * </ul>
 *
 * All effect files are shipped as empty stubs that produce no visual output.
 * Use Photon's in-game editor to design the actual effects and save them
 * back to the resource pack. This way effects can be tuned without recompiling.
 */
@OnlyIn(Dist.CLIENT)
public final class MgePhotonEffects {

    public static final String PHOTON_MODID = "photon";
    private static boolean loaded = false;

    public static final ResourceLocation FX_DETONATION        = rl("detonation");
    public static final ResourceLocation FX_SHOCKWAVE_RING    = rl("shockwave_ring");
    public static final ResourceLocation FX_COAL_DUST         = rl("coal_dust_explosion");
    public static final ResourceLocation FX_VACUUM_SHIMMER    = rl("vacuum_shimmer");
    public static final ResourceLocation FX_GAS_LEAK          = rl("gas_leak");
    public static final ResourceLocation FX_ACID_RAIN         = rl("acid_rain");

    private MgePhotonEffects() {}

    public static void tryLoad() {
        if (!ModList.get().isLoaded(PHOTON_MODID)) return;
        loaded = true;
        Mge.LOGGER.info("[MGE] Photon detected — visual effects active.");
    }

    public static boolean isLoaded() { return loaded; }

    /**
     * Plays an FX effect at a block position on the client.
     * Safe to call from the render/client tick — no-ops if Photon isn't loaded.
     *
     * @param fxId  one of the {@code FX_*} constants above
     * @param level the client level
     * @param pos   block position to play at
     */
    public static void play(ResourceLocation fxId, Level level, BlockPos pos) {
        if (!loaded) return;
        FX fx = FXHelper.getFX(fxId);
        if (fx == null) return;
        BlockEffect effect = new BlockEffect(fx, level, pos);
        effect.start();
    }

    /**
     * Plays an FX effect at an arbitrary world position.
     */
    public static void playAt(ResourceLocation fxId, Level level, Vec3 pos) {
        play(fxId, level, BlockPos.containing(pos));
    }

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(Mge.MODID, "fx/" + path);
    }
}
