package fr.atraxterra.dohy.boost;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;

/**
 * Les boosters d'argent.
 *
 * <h2>Pourquoi si chers et si courts</h2>
 *
 * <p>En solo, un multiplicateur d'argent n'est qu'un changement d'echelle :
 * sans concurrence, « gagner quatre fois plus vite » ne vaut rien en soi. Ce qui
 * rend un booster interessant, c'est qu'il soit un <b>pari sur sa propre
 * preparation</b> — vingt minutes de jeu, soit un sixieme d'une soiree, assez
 * court pour qu'il faille avoir constitue un stock avant de le declencher.
 *
 * <p>Le booster s'applique apres le prix dynamique et le montant booste ne
 * compte pas dans la pression sur le prix. Sans cette regle, le booster
 * accelererait la chute des prix et s'annulerait lui-meme.
 */
public final class Boosters {

    public record Tier(String key, double multiplier, long price) {
    }

    /** Les trois paliers de {@code BALANCE.md}. */
    public static final Tier[] TIERS = {
            new Tier("x2", 2.0d, 2_000_000L),
            new Tier("x3", 3.0d, 25_000_000L),
            new Tier("x4", 4.0d, 300_000_000L),
    };

    /** Duree, en minutes de temps de jeu. */
    public static final long DURATION_MINUTES = 20L;

    /** La duree effective, allongee par la branche Endurance. */
    public static long durationFor(ServerPlayer player) {
        return DURATION_MINUTES + UpgradeApi.bonusInt(player, UpgradeApi.BOOSTER_MINUTES);
    }

    private Boosters() {
    }

    public static BoostData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.BOOSTS);
    }

    public static Tier tier(String key) {
        for (Tier tier : TIERS) {
            if (tier.key().equalsIgnoreCase(key)) {
                return tier;
            }
        }
        return null;
    }

    /** Multiplicateur actif du joueur, ou 1. */
    public static double active(ServerPlayer player) {
        return data(player).booster(ContractApi.data(player).playtimeMinutes());
    }

    /** Minutes de jeu restantes, ou 0. */
    public static long remaining(ServerPlayer player) {
        long now = ContractApi.data(player).playtimeMinutes();
        return Math.max(0L, data(player).boosterUntil() - now);
    }

    /**
     * Achete un booster.
     *
     * @return {@code null} si l'achat a reussi, sinon la raison du refus
     */
    public static String buy(ServerPlayer player, Tier tier) {
        if (active(player) > 1.0d) {
            // Non cumulables : laisser empiler reviendrait a transformer un pari
            // en rente pour qui peut payer plusieurs fois.
            return "Un booster est deja actif (" + remaining(player) + " min restantes).";
        }
        if (!Economy.tryDebit(player, tier.price())) {
            return "Il te faut " + MoneyFormat.exact(tier.price()) + " en liquide.";
        }

        long until = ContractApi.data(player).playtimeMinutes() + durationFor(player);
        data(player).setBooster(tier.multiplier(), until);
        return null;
    }
}
