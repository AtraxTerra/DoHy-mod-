package fr.atraxterra.dohy.economy;

import fr.atraxterra.dohy.DoHyAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Seul point d'acces au solde.
 *
 * <p>Tout passe par ici, pour une raison : la synchronisation vers le client est
 * facile a oublier, et un solde modifie sans synchronisation donne un HUD qui
 * ment. Centraliser la mutation rend l'oubli impossible.
 */
public final class Economy {

    private Economy() {
    }

    public static PlayerEconomy of(Player player) {
        return player.getData(DoHyAttachments.ECONOMY);
    }

    public static long balance(Player player) {
        return of(player).balance();
    }

    /** Credite et synchronise. Retourne le nouveau solde. */
    public static long credit(ServerPlayer player, long amount) {
        return apply(player, of(player).credit(amount));
    }

    /** Debite et synchronise, sans jamais passer sous zero. Retourne le nouveau solde. */
    public static long debit(ServerPlayer player, long amount) {
        return apply(player, of(player).debit(amount));
    }

    /**
     * Debite seulement si le solde suffit. Retourne {@code false} sans rien
     * modifier sinon — c'est la methode a utiliser pour tout achat.
     */
    public static boolean tryDebit(ServerPlayer player, long amount) {
        PlayerEconomy data = of(player);
        if (!data.canAfford(amount)) {
            return false;
        }
        apply(player, data.debit(amount));
        return true;
    }

    /**
     * Fixe le solde sans toucher aux gains cumules. Reserve aux commandes
     * d'administration : utiliser {@link #credit} pour un gain reel, sinon la
     * penalite de mort, qui s'indexe sur les gains cumules, serait faussee.
     */
    public static long set(ServerPlayer player, long amount) {
        return apply(player, of(player).withBalance(Math.max(0L, amount)));
    }

    public static boolean isHudHidden(Player player) {
        return of(player).hudHidden();
    }

    public static boolean toggleHud(ServerPlayer player) {
        PlayerEconomy data = of(player);
        boolean hidden = !data.hudHidden();
        apply(player, data.withHudHidden(hidden));
        return hidden;
    }

    private static long apply(ServerPlayer player, PlayerEconomy data) {
        player.setData(DoHyAttachments.ECONOMY, data);
        player.syncData(DoHyAttachments.ECONOMY);
        return data.balance();
    }
}
