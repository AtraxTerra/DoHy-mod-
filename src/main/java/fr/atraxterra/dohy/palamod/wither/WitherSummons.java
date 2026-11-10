package fr.atraxterra.dohy.palamod.wither;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Qui a invoque ce wither. Une piece jointe sur l'entite, posee par le
 * receptacle et la TNT a wither, valable aussi sur le wither vanilla : c'est a
 * l'invocateur que reviennent les compteurs de l'arbre de faction
 * (obsidiennes cassees par un wither, regeneration au receptacle).
 */
public final class WitherSummons {

    private WitherSummons() {
    }

    public static void mark(Entity wither, ServerPlayer player) {
        if (player != null) {
            wither.setData(PalaWithers.SUMMONER.get(), player.getUUID().toString());
        }
    }

    static void copy(Entity from, Entity to) {
        String summoner = from.getData(PalaWithers.SUMMONER.get());
        if (!summoner.isEmpty()) {
            to.setData(PalaWithers.SUMMONER.get(), summoner);
        }
    }

    /** L'invocateur s'il est connecte, sinon {@code null}. */
    public static ServerPlayer summoner(Entity wither) {
        if (!(wither.level() instanceof ServerLevel level)) {
            return null;
        }
        String summoner = wither.getData(PalaWithers.SUMMONER.get());
        if (summoner.isEmpty()) {
            return null;
        }
        try {
            return level.getServer().getPlayerList().getPlayer(UUID.fromString(summoner));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** Vrai si ce joueur a invoque ce wither, ou si personne ne l'a invoque. */
    public static boolean controls(Entity wither, ServerPlayer player) {
        String summoner = wither.getData(PalaWithers.SUMMONER.get());
        return summoner.isEmpty() || summoner.equals(player.getUUID().toString());
    }
}
