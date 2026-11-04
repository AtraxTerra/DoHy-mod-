package fr.atraxterra.dohy.economy.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Pression de vente accumulee par le joueur, item par item.
 *
 * <p>C'est la memoire du marche : plus on a vendu de blé, moins le blé vaut. La
 * valeur est un reel et non un entier parce qu'elle decroit multiplicativement
 * (voir {@link MarketDecay}), et qu'un entier se figerait a zero ou ne
 * descendrait jamais.
 *
 * <p>Mutable, comme {@code PlacedBlockData} : une vente et un pas de
 * decroissance modifient cette table en permanence, recreer l'objet a chaque
 * fois serait du gaspillage.
 */
public final class PlayerMarket {

    /**
     * En dessous de ce seuil, l'entree est supprimee. Sans cette purge, la table
     * grossirait indefiniment avec des valeurs infinitesimales sans effet sur
     * les prix.
     */
    private static final double PRUNE_BELOW = 0.5d;

    public static final MapCodec<PlayerMarket> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE)
                    .optionalFieldOf("sold", Map.of())
                    .forGetter(market -> Map.copyOf(market.sold))
    ).apply(instance, PlayerMarket::new));

    private final Map<Identifier, Double> sold;

    public PlayerMarket() {
        this.sold = new HashMap<>();
    }

    private PlayerMarket(Map<Identifier, Double> sold) {
        this.sold = new HashMap<>(sold);
    }

    /** Pression accumulee pour cet item. Zero si jamais vendu. */
    public double soldOf(Identifier id) {
        return sold.getOrDefault(id, 0.0d);
    }

    /** Enregistre une vente. */
    public void record(Identifier id, int quantity) {
        if (quantity <= 0) {
            return;
        }
        sold.merge(id, (double) quantity, Double::sum);
    }

    /**
     * Applique un pas de decroissance a toutes les entrees et purge les
     * residus. Retourne le nombre d'entrees restantes.
     */
    public int decay(double keepFactor) {
        sold.replaceAll((id, value) -> value * keepFactor);
        sold.values().removeIf(value -> value < PRUNE_BELOW);
        return sold.size();
    }

    public int trackedItems() {
        return sold.size();
    }

    public boolean isEmpty() {
        return sold.isEmpty();
    }
}
