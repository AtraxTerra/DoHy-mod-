package fr.atraxterra.dohy.economy;

import fr.atraxterra.dohy.DoHyAttachments;
import net.minecraft.server.level.ServerPlayer;

/**
 * La banque : une epargne protegee de la penalite de mort.
 *
 * <h2>A quoi elle sert vraiment</h2>
 *
 * <p>Deux choses. D'abord, les frais de depot sont une friction permanente :
 * c'est un puits a argent qui ne demande aucun contenu a produire. Ensuite, et
 * surtout, elle transforme la penalite de mort en <b>decision</b> plutot qu'en
 * punition — le joueur choisit son exposition au risque avant de sortir, au lieu
 * de subir un prelevement sur lequel il n'a aucune prise.
 */
public final class Bank {

    /** Frais preleves au depot. C'est le sink qui justifie le systeme. */
    public static final double DEPOSIT_FEE = 0.03d;

    /** Interet par jour de temps de jeu. */
    public static final double DAILY_RATE = 0.005d;

    /** Plafond d'interet par jour de temps de jeu. */
    public static final long DAILY_CAP = 50_000L;

    /** Prix d'une charge d'assurance. */
    public static final long INSURANCE_PRICE = 50_000L;

    private Bank() {
    }

    public static long banked(ServerPlayer player) {
        return Economy.of(player).banked();
    }

    /** Frais pour un depot donne. */
    public static long feeFor(long amount) {
        return Math.round(amount * DEPOSIT_FEE);
    }

    /**
     * Depose, frais deduits. Retourne le montant reellement credite sur
     * l'epargne, ou -1 si le solde liquide ne suffit pas.
     */
    public static long deposit(ServerPlayer player, long amount) {
        if (amount <= 0) {
            return -1L;
        }
        PlayerEconomy data = Economy.of(player);
        if (data.balance() < amount) {
            return -1L;
        }

        long fee = feeFor(amount);
        long credited = amount - fee;
        apply(player, data.withBalance(data.balance() - amount)
                .withBanked(data.banked() + credited));
        return credited;
    }

    /** Retire de l'epargne, sans frais. Retourne le montant retire, ou -1. */
    public static long withdraw(ServerPlayer player, long amount) {
        if (amount <= 0) {
            return -1L;
        }
        PlayerEconomy data = Economy.of(player);
        if (data.banked() < amount) {
            return -1L;
        }
        apply(player, data.withBanked(data.banked() - amount)
                .withBalance(data.balance() + amount));
        return amount;
    }

    /**
     * Credite les interets d'un pas de temps donne, en minutes de jeu.
     *
     * <p>Le taux et le plafond sont exprimes par jour de temps de jeu et
     * ramenes au pas : les interets tombent donc de facon continue, sans palier
     * visible, et une soiree de deux heures en rapporte un douzieme.
     */
    public static long accrue(ServerPlayer player, int minutes) {
        PlayerEconomy data = Economy.of(player);
        if (data.banked() <= 0 || minutes <= 0) {
            return 0L;
        }

        double fraction = minutes / (24.0d * 60.0d);
        long interest = Math.round(Math.min(
                data.banked() * DAILY_RATE * fraction,
                DAILY_CAP * fraction));
        if (interest <= 0) {
            return 0L;
        }

        apply(player, data.withBanked(data.banked() + interest));
        return interest;
    }

    /** Achete une charge d'assurance. Retourne false si le solde ne suffit pas. */
    public static boolean buyInsurance(ServerPlayer player) {
        if (!Economy.tryDebit(player, INSURANCE_PRICE)) {
            return false;
        }
        PlayerEconomy data = Economy.of(player);
        apply(player, data.withInsurance(data.insurance() + 1));
        return true;
    }

    public static int insuranceCharges(ServerPlayer player) {
        return Economy.of(player).insurance();
    }

    private static void apply(ServerPlayer player, PlayerEconomy data) {
        player.setData(DoHyAttachments.ECONOMY, data);
        player.syncData(DoHyAttachments.ECONOMY);
    }
}
