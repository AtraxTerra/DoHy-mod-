package fr.atraxterra.dohy.economy.market;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.contract.Bonuses;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractType;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Le prix de vente reel, pression du marche comprise.
 *
 * <p>Le multiplicateur suit
 *
 * <pre>f(vendu) = ( S0 / (S0 + vendu) ) ^ 0.6</pre>
 *
 * borne par le plancher du tier. Sans plancher, le grind finirait litteralement
 * gratuit et le joueur se sentirait vole ; sans exposant inferieur a 1, le prix
 * s'effondrerait trop vite pour qu'une ferme serve a quelque chose.
 *
 * <h2>Pourquoi une integrale et pas une simple multiplication</h2>
 *
 * <p>Vendre 10 000 blés d'un coup ne doit pas rapporter 10 000 fois le prix
 * d'avant-vente : ce serait exactement l'inverse de l'effet recherche, puisqu'il
 * suffirait de tout stocker puis de tout ecouler en une fois pour ignorer la
 * pression. Le paiement est donc l'<b>integrale</b> du prix unitaire sur la
 * quantite vendue :
 *
 * <pre>
 * paiement = base · S0^k/(1-k) · [ (S0+fin)^(1-k) - (S0+debut)^(1-k) ]
 * </pre>
 *
 * <p>Au-dela du point ou le plancher est atteint, le prix est constant et la
 * partie restante est payee lineairement. L'arrondi n'intervient qu'une fois, a
 * la fin : arrondir chaque unite ferait perdre jusqu'a 12 % sur les items bon
 * marche au plancher, ou le prix unitaire tombe entre deux entiers.
 */
public final class DynamicPrice {

    /** Exposant de la courbe. Voir {@code docs/BALANCE.md}. */
    private static final double K = 0.6d;

    private DynamicPrice() {
    }

    public static PlayerMarket market(ServerPlayer player) {
        return player.getData(DoHyAttachments.MARKET);
    }

    /**
     * Borne dure du plancher, en fraction du prix de base.
     *
     * <p>Le plancher du tier, le bonus de collection et celui de prestige
     * s'additionnent, et rien n'empeche la somme de depasser 1 : un `RARE`
     * (plancher 50 %) a collection complete et prestige maximal atteint 90 %.
     * A ce niveau le frein des prix dynamiques n'existe plus — la dix-millieme
     * unite vendue rapporte encore le plein tarif, ce qui est exactement ce que
     * le systeme doit empecher.
     *
     * <p>La borne etait a 95 %, c'est-a-dire nulle part : elle laissait passer
     * precisement le cas qu'elle devait couvrir. A 75 %, un prix peut toujours
     * perdre un quart de sa valeur sous la pression, quelle que soit la
     * progression du joueur et quelles que soient les valeurs du datapack.
     */
    private static final double MAX_FLOOR = 0.75d;

    /**
     * Plancher effectif : celui du tier, releve par les paliers de collection
     * franchis et par le prestige, puis borne.
     *
     * <p>Un `FARM` a fond atteint 55 %, un `FOUND` 75 %, un `RARE` touche la
     * borne.
     */
    public static double effectiveFloor(ServerPlayer player, Identifier id, PriceEntry entry) {
        return Math.min(MAX_FLOOR, entry.floorPct()
                + CollectionApi.floorBonus(player, id, entry)
                + CollectionApi.prestigeFloorBonus(player, id)
                + UpgradeApi.bonus(player, UpgradeApi.FLOOR_BONUS));
    }

    /**
     * Demi-saturation effective : celle du tier, elargie par le prestige.
     *
     * <p>C'est le levier du prestige. Elargir S0 recule le point ou le prix
     * s'effondre, sans jamais depasser le prix de base : le gain est reel et
     * l'economie ne peut pas gonfler.
     */
    public static double effectiveS0(ServerPlayer player, Identifier id, PriceEntry entry) {
        return Math.max(1, entry.halfSaturation()) * CollectionApi.s0Multiplier(player, id);
    }

    /** Multiplicateur courant, entre le plancher effectif et le plafond du tier. */
    public static double multiplier(ServerPlayer player, Identifier id, PriceEntry entry) {
        return multiplierAt(market(player).soldOf(id), entry,
                effectiveFloor(player, id, entry), effectiveS0(player, id, entry));
    }

    static double multiplierAt(double sold, PriceEntry entry, double floor, double s0) {
        double raw = Math.pow(s0 / (s0 + Math.max(0, sold)), K);
        // Le plafond ne mord pas encore : la formule ne depasse jamais 1. Il
        // existe pour la demande du jour (phase 8), qui multipliera ce facteur.
        return Math.min(entry.ceilingPct(), Math.max(floor, raw));
    }

    /**
     * Prix unitaire affiche, arrondi. Jamais zero.
     *
     * <p>Le cours de l'heure ({@link HourlyCourse}) multiplie la pression du
     * marche : les deux sont independants, l'un vient de ce que le joueur a
     * vendu, l'autre de l'heure qu'il est.
     */
    public static long unitPrice(ServerPlayer player, Identifier id, PriceEntry entry) {
        return Math.max(1L, Math.round(entry.basePrice() * multiplier(player, id, entry)
                * HourlyCourse.factor(id, entry)));
    }

    /**
     * Ce que rapporterait la vente de {@code quantity} exemplaires, sans rien
     * enregistrer. Utilise par {@code /worth} et par les apercus du shop.
     */
    public static long quote(ServerPlayer player, Identifier id, PriceEntry entry, int quantity) {
        if (quantity <= 0) {
            return 0L;
        }
        double start = market(player).soldOf(id);
        long base = Math.max(1L, Math.round(payoutBetween(start, start + quantity, entry,
                effectiveFloor(player, id, entry), effectiveS0(player, id, entry))
                * HourlyCourse.factor(id, entry)));
        return Math.max(1L, Math.round(base * Bonuses.preview(player, id)));
    }

    /**
     * Vend : calcule le paiement et enregistre la pression correspondante.
     *
     * <p>C'est le seul chemin par lequel une vente doit passer, afin qu'aucune
     * ne puisse rapporter de l'argent sans peser sur le prix.
     */
    public static long sell(ServerPlayer player, Identifier id, PriceEntry entry, int quantity) {
        if (quantity <= 0) {
            return 0L;
        }
        PlayerMarket market = market(player);
        double start = market.soldOf(id);
        long base = Math.max(1L, Math.round(payoutBetween(start, start + quantity, entry,
                effectiveFloor(player, id, entry), effectiveS0(player, id, entry))
                * HourlyCourse.factor(id, entry)));

        // La pression enregistree est celle de la quantite reellement vendue :
        // un bonus ne doit pas accelerer la chute du prix, sinon il s'annule.
        market.record(id, quantity);

        double multiplier = Bonuses.consume(player, id);
        long payout = Math.max(1L, Math.round(base * multiplier));
        Bonuses.report(player, multiplier, payout);

        ContractApi.progress(player, ContractType.SELL_VALUE, target -> true, payout);
        return payout;
    }

    /**
     * Integrale du prix unitaire entre deux niveaux de pression.
     *
     * @param from pression avant la vente
     * @param to   pression apres la vente
     */
    private static double payoutBetween(double from, double to, PriceEntry entry,
                                        double floor, double s0) {
        double base = entry.basePrice();
        double breakpoint = floorBreakpoint(s0, floor);

        double total = 0.0d;

        // Partie sur la courbe, jusqu'au point ou le plancher est atteint.
        double curveFrom = Math.min(from, breakpoint);
        double curveTo = Math.min(to, breakpoint);
        if (curveTo > curveFrom) {
            total += base * Math.pow(s0, K) / (1 - K)
                    * (Math.pow(s0 + curveTo, 1 - K) - Math.pow(s0 + curveFrom, 1 - K));
        }

        // Partie au plancher, a prix constant.
        if (to > breakpoint) {
            total += base * floor * (to - Math.max(from, breakpoint));
        }

        return total;
    }

    /**
     * Quantite vendue a partir de laquelle le multiplicateur atteint le
     * plancher : {@code (S0/(S0+x))^k = plancher}.
     */
    private static double floorBreakpoint(double s0, double floor) {
        if (floor <= 0 || floor >= 1) {
            return Double.MAX_VALUE;
        }
        return s0 * (Math.pow(floor, -1 / K) - 1);
    }
}
