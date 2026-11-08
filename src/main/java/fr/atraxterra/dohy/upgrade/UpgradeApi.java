package fr.atraxterra.dohy.upgrade;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Les ameliorations permanentes.
 *
 * <h2>Le trou qu'elles bouchent</h2>
 *
 * <p>Tous les autres puits a argent du mod sont consommables ou ponctuels : un
 * booster se depense, une caisse est un pari, une assurance se consomme, et
 * l'autosell a 500 M est un achat <b>unique</b>. Passe ce point, l'argent
 * n'avait plus de destination : on continuait a en gagner sans que ca change
 * quoi que ce soit. C'est comme ca que meurt une economie de jeu.
 *
 * <p>Ces ameliorations sont definitives et de plus en plus cheres — tout monter
 * represente une centaine de milliards, bien au-dela de l'arc de quatre mois.
 * C'est volontaire : c'est l'objectif qui reste quand tout le reste est fait.
 *
 * <h2>Pourquoi les effets sont nommes ici</h2>
 *
 * <p>{@link #EFFECTS} liste les effets que le code lit reellement, et le
 * chargement du datapack previent pour tout effet absent de cette liste. C'est
 * le meme filet que pour les perks de metier, pose apres avoir decouvert que
 * trente-trois perks etaient declares et inertes. Une amelioration payee
 * plusieurs milliards doit faire quelque chose.
 */
public final class UpgradeApi {

    public static final String SHOP_DISCOUNT = "shop_discount";
    public static final String AUTOSELL_INTERVAL_CUT = "autosell_interval_cut";
    public static final String BANK_CAP_MULTIPLIER = "bank_cap_multiplier";
    public static final String DAILY_CONTRACT_SLOTS = "daily_contract_slots";
    public static final String BOOSTER_MINUTES = "booster_minutes";
    public static final String FLOOR_BONUS = "floor_bonus";
    public static final String JOB_XP_BONUS = "job_xp_bonus";
    public static final String DEATH_PENALTY_CUT = "death_penalty_cut";

    /** Les effets effectivement lus quelque part dans le code. */
    private static final Set<String> EFFECTS = Set.of(
            SHOP_DISCOUNT, AUTOSELL_INTERVAL_CUT, BANK_CAP_MULTIPLIER,
            DAILY_CONTRACT_SLOTS, BOOSTER_MINUTES, FLOOR_BONUS,
            JOB_XP_BONUS, DEATH_PENALTY_CUT);

    private UpgradeApi() {
    }

    static boolean isKnownEffect(String effect) {
        return EFFECTS.contains(effect);
    }

    public static UpgradeData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.UPGRADES.get());
    }

    public static int levelOf(ServerPlayer player, String id) {
        return data(player).levelOf(id);
    }

    /**
     * L'amplitude cumulee d'un effet pour ce joueur.
     *
     * <p>Somme sur toutes les branches qui portent cet effet, ce qui autorise
     * plusieurs branches a alimenter le meme effet sans rien changer au code
     * appelant.
     */
    public static double bonus(ServerPlayer player, String effect) {
        UpgradeData data = data(player);
        double total = 0.0d;
        for (var entry : UpgradeConfig.config().upgrades().entrySet()) {
            if (entry.getValue().effect().equals(effect)) {
                total += entry.getValue().amountAt(data.levelOf(entry.getKey()));
            }
        }
        return total;
    }

    /** Variante entiere, pour les effets qui comptent des rangees ou des minutes. */
    public static int bonusInt(ServerPlayer player, String effect) {
        return (int) Math.round(bonus(player, effect));
    }

    /**
     * Achete le palier suivant d'une branche.
     *
     * @return {@code null} si l'achat a reussi, sinon la raison du refus
     */
    public static String buy(ServerPlayer player, String id) {
        UpgradeConfig.Upgrade upgrade = UpgradeConfig.config().get(id).orElse(null);
        if (upgrade == null) {
            return "Amelioration inconnue.";
        }
        UpgradeData data = data(player);
        int current = data.levelOf(id);
        var price = upgrade.priceOf(current);
        if (price.isEmpty()) {
            return upgrade.label() + " est deja au maximum.";
        }
        if (!Economy.tryDebit(player, price.get())) {
            return "Il te manque " + MoneyFormat.exact(price.get() - Economy.balance(player)) + ".";
        }

        data.set(id, current + 1);
        // Les ameliorations ne sont jamais recalculees ailleurs : la seule
        // mutation passe par ici, donc la synchronisation aussi.
        player.setData(DoHyAttachments.UPGRADES.get(), data);
        return null;
    }
}
