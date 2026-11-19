package fr.atraxterra.dohy.auction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ce que le joueur fait a l'hotel des ventes.
 *
 * <p>Le jour : ce qu'il a achete de chaque annonce et combien de fois il a paye
 * pour de nouvelles annonces. Les annonces des vendeurs ne sont pas gardees :
 * elles se recalculent du jour, du monde et de ce compteur ({@link AuctionHouse}).
 *
 * <p>Ses propres ventes, elles, sont gardees d'un jour a l'autre : l'objet mis en
 * vente, son prix, et l'heure (reelle) ou un acheteur le prendra
 * ({@link AuctionSales}).
 */
public final class AuctionData {

    /** Un objet qui ne se relit plus (retire du jeu) : l'annonce reste, vide. */
    private static final Codec<ItemStack> LENIENT_STACK = ItemStack.OPTIONAL_CODEC.orElse(ItemStack.EMPTY);

    /**
     * Une annonce du joueur.
     *
     * @param price    ce qu'il recoit quand l'objet part
     * @param preset   le prix choisi ({@link AuctionConfig.Preset#id()})
     * @param listedAt l'heure de la mise en vente, en millisecondes
     * @param sellAt   l'heure ou un acheteur le prend, -1 s'il n'en viendra pas
     */
    public record Sale(long id, ItemStack stack, long price, String preset, long listedAt, long sellAt, int state) {

        public static final int ACTIVE = 0;
        public static final int SOLD = 1;
        public static final int UNSOLD = 2;

        static final Codec<Sale> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("id").forGetter(Sale::id),
                LENIENT_STACK.fieldOf("stack").forGetter(Sale::stack),
                Codec.LONG.fieldOf("price").forGetter(Sale::price),
                Codec.STRING.optionalFieldOf("preset", "").forGetter(Sale::preset),
                Codec.LONG.fieldOf("listed_at").forGetter(Sale::listedAt),
                Codec.LONG.optionalFieldOf("sell_at", -1L).forGetter(Sale::sellAt),
                Codec.INT.optionalFieldOf("state", ACTIVE).forGetter(Sale::state)
        ).apply(instance, Sale::new));

        Sale withState(int value) {
            return new Sale(id, stack, price, preset, listedAt, sellAt, value);
        }

        Sale withSellAt(long value) {
            return new Sale(id, stack, price, preset, listedAt, value, state);
        }
    }

    public static final MapCodec<AuctionData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(data -> data.day),
            Codec.INT.optionalFieldOf("rerolls", 0).forGetter(data -> data.rerolls),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bought", Map.of())
                    .forGetter(data -> {
                        Map<String, Integer> out = new HashMap<>();
                        data.bought.forEach((index, count) -> out.put(String.valueOf(index), count));
                        return out;
                    }),
            Sale.CODEC.listOf().optionalFieldOf("sales", List.of()).forGetter(data -> List.copyOf(data.sales)),
            Codec.LONG.optionalFieldOf("next_sale", 1L).forGetter(data -> data.nextSale)
    ).apply(instance, AuctionData::new));

    private long day = -1L;
    private int rerolls;
    private final Map<Integer, Integer> bought = new HashMap<>();
    private final List<Sale> sales = new ArrayList<>();
    private long nextSale = 1L;

    public AuctionData() {
    }

    private AuctionData(long day, int rerolls, Map<String, Integer> bought, List<Sale> sales, long nextSale) {
        this.day = day;
        this.rerolls = rerolls;
        bought.forEach((index, count) -> {
            try {
                this.bought.put(Integer.parseInt(index), count);
            } catch (NumberFormatException ignored) {
                // Une cle illisible : l'annonce repart avec son stock plein.
            }
        });
        this.sales.addAll(sales);
        this.nextSale = Math.max(1L, nextSale);
        for (Sale sale : sales) {
            this.nextSale = Math.max(this.nextSale, sale.id() + 1L);
        }
    }

    /** Rien a garder : ni journee entamee, ni annonce du joueur. */
    public boolean isEmpty() {
        return day < 0L && sales.isEmpty();
    }

    /** Passe au jour donne : un nouveau jour efface les achats et les renouvellements, pas les ventes. */
    void sync(long today) {
        if (day != today) {
            day = today;
            rerolls = 0;
            bought.clear();
        }
    }

    int rerolls() {
        return rerolls;
    }

    void reroll() {
        rerolls++;
        bought.clear();
    }

    int bought(int index) {
        return bought.getOrDefault(index, 0);
    }

    void buy(int index) {
        bought.merge(index, 1, Integer::sum);
    }

    // -- Les ventes du joueur ----------------------------------------------------------

    /** Les annonces du joueur, de la plus ancienne a la plus recente. */
    public List<Sale> sales() {
        return List.copyOf(sales);
    }

    Sale sale(long id) {
        for (Sale sale : sales) {
            if (sale.id() == id) {
                return sale;
            }
        }
        return null;
    }

    Sale add(ItemStack stack, long price, String preset, long listedAt, long sellAt) {
        Sale sale = new Sale(nextSale++, stack, price, preset, listedAt, sellAt, Sale.ACTIVE);
        sales.add(sale);
        return sale;
    }

    void replace(Sale sale) {
        for (int index = 0; index < sales.size(); index++) {
            if (sales.get(index).id() == sale.id()) {
                sales.set(index, sale);
                return;
            }
        }
    }

    void remove(long id) {
        sales.removeIf(sale -> sale.id() == id);
    }
}
