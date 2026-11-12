package fr.atraxterra.dohy.earth;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le saut : le joueur est lache au-dessus d'une terre tiree au hasard sur la
 * carte, et descend en parachute.
 *
 * <p>Le point vient de la grille des pays, une case de terre parmi toutes ;
 * comme la grille et la carte ne s'accordent pas au bloc pres le long des
 * cotes, le sol est verifie avant de sauter, et un point qui donne sur l'eau
 * est retire. Le parachute est une chute lente, et la premiere reception ne
 * fait aucun degat.
 */
public final class EarthJump {

    private static final int ATTEMPTS = 12;
    /** Hauteur du lacher au-dessus du sol. */
    private static final int DROP = 96;
    private static final int PARACHUTE_TICKS = 20 * 90;

    /** Le temps de quitter le sol d'ou l'on saute : avant, « au sol » ne veut pas dire « arrive ». */
    private static final int TAKE_OFF_TICKS = 40;

    /** Ceux qui descendent, et le tick de leur lacher : leur prochaine chute ne blesse pas. */
    private static final Map<UUID, Long> FALLING = new ConcurrentHashMap<>();

    private EarthJump() {
    }

    /** Vrai si le joueur a ete lache au-dessus de la carte. */
    public static boolean jump(ServerPlayer player) {
        EarthData earth = EarthData.get();
        if (earth == null) {
            return false;
        }
        ServerLevel level = player.level().getServer().getLevel(earth.dimension());
        if (level == null) {
            return false;
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int[] spot = earth.randomLand(player.getRandom());
            if (spot == null) {
                return false;
            }
            int x = spot[0];
            int z = spot[1];
            if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))) {
                continue;
            }
            // charge le chunk vise : il faut son sol pour savoir d'ou lacher
            level.getChunk(x >> 4, z >> 4);
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockState ground = level.getBlockState(new BlockPos(x, surface - 1, z));
            if (surface <= level.getMinY() + 1 || !ground.getFluidState().isEmpty()) {
                continue;
            }
            drop(player, level, earth, x, surface, z);
            return true;
        }
        return false;
    }

    private static void drop(ServerPlayer player, ServerLevel level, EarthData earth, int x, int surface, int z) {
        int height = Math.min(level.getMaxY() - 4, surface + DROP);
        player.closeContainer();
        player.stopRiding();
        player.fallDistance = 0.0f;
        FALLING.put(player.getUUID(), level.getGameTime());
        player.teleport(new TeleportTransition(level, new Vec3(x + 0.5d, height, z + 0.5d), Vec3.ZERO,
                player.getYRot(), 30.0f, TeleportTransition.DO_NOTHING));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, PARACHUTE_TICKS, 0, false, false, true));
        EarthData.Country country = earth.countryAt(x, z);
        String name = country == null ? "Terre inconnue" : country.name();
        EarthEvents.entered(player, country);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal(name).withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("Parachutage  ·  " + earth.coordinates(x, z)).withStyle(ChatFormatting.GRAY)));
    }

    /** Vrai une fois : ce joueur descend d'un saut, sa reception ne doit pas le blesser. */
    static boolean protects(ServerPlayer player) {
        return FALLING.remove(player.getUUID()) != null;
    }

    /** Le joueur a touche le sol ou l'eau : son saut est fini. */
    static void landed(ServerPlayer player) {
        Long start = FALLING.get(player.getUUID());
        if (start != null && player.level().getGameTime() - start > TAKE_OFF_TICKS) {
            FALLING.remove(player.getUUID());
        }
    }

    static void forget(UUID player) {
        FALLING.remove(player);
    }

    static void clear() {
        FALLING.clear();
    }
}
