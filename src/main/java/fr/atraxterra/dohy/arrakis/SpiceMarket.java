package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.market.HourlyCourse;
import fr.atraxterra.dohy.economy.market.ShopTape;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.ui.view.PriceChart;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Le cours de l'epice, au seul comptoir d'Arrakeen.
 *
 * <p>Le melange n'est pas au catalogue : ni le shop, ni le coffre de vente, ni
 * la vente automatique ne le prennent. Il faut l'apporter a Arrakeen, ou le
 * comptoir le rachete au cours de l'heure. Le reste suit les regles de toute
 * vente : le prix baisse a mesure qu'un joueur en ecoule ({@link DynamicPrice},
 * la pression retombe avec le temps), les boosters et les contrats comptent.
 *
 * <p>Le cours de l'heure est le meme tirage que celui des cereales
 * ({@link HourlyCourse}), propre au melange : une bonne heure pour l'epice n'en
 * est pas forcement une pour le ble.
 */
public final class SpiceMarket {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "melange");

    /** Prix de base d'une unite de melange, avant la pression du marche et le cours. */
    public static final long BASE_PRICE = 250L;
    /** Quantite vendue a laquelle le prix tombe aux deux tiers : une cargaison pleine s'ecoule encore bien. */
    private static final int HALF_SATURATION = 2000;
    private static final double FLOOR = 0.35d;

    private static final PriceEntry ENTRY = new PriceEntry("ARRAKIS", "SPICE", BASE_PRICE, HALF_SATURATION,
            FLOOR, 1.8d, 0L, false);

    private SpiceMarket() {
    }

    /** Le cours de l'heure, en facteur du prix de base. */
    public static double course() {
        return HourlyCourse.factorAt(ID, HourlyCourse.slot());
    }

    /** Le cours en pour cent : 118 pour +18 %. */
    public static int percent() {
        return (int) Math.round(course() * 100.0d);
    }

    public static int minutesLeft() {
        return HourlyCourse.minutesLeft();
    }

    /**
     * La courbe du melange : le prix de base fois le cours de chacune des
     * {@link ShopTape#HOURS} dernieres heures, l'heure en cours au bout, et ce qui
     * s'en est vendu au comptoir. Jamais l'heure suivante : elle est deja tiree.
     */
    public static PriceChart chart(MinecraftServer server) {
        long now = HourlyCourse.slot();
        List<Long> values = new ArrayList<>(ShopTape.HOURS);
        for (int back = ShopTape.HOURS - 1; back >= 0; back--) {
            values.add(Math.max(1L, Math.round(BASE_PRICE * HourlyCourse.factorAt(ID, now - back))));
        }
        return new PriceChart(values, ShopTape.volumes(server, ID), BASE_PRICE, HourlyCourse.periodMinutes(),
                PriceChart.DOLLARS);
    }

    /** Le prix d'une unite pour ce joueur, maintenant. */
    public static long unitPrice(ServerPlayer player) {
        return Math.max(1L, Math.round(DynamicPrice.unitPrice(player, ID, ENTRY) * course()));
    }

    /** Ce que rapporterait {@code quantity} unites, sans rien vendre. */
    public static long quote(ServerPlayer player, int quantity) {
        if (quantity <= 0) {
            return 0L;
        }
        return Math.max(1L, Math.round(DynamicPrice.quote(player, ID, ENTRY, quantity) * course()));
    }

    /** Vend {@code quantity} unites : credite le joueur et rend le paiement. */
    public static long sell(ServerPlayer player, int quantity) {
        if (quantity <= 0) {
            return 0L;
        }
        long payout = Math.max(1L, Math.round(DynamicPrice.sell(player, ID, ENTRY, quantity) * course()));
        Economy.credit(player, payout);
        fr.atraxterra.dohy.palamod.achievement.Achievements.sold(player, payout);
        return payout;
    }
}
