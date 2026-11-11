package fr.atraxterra.dohy.auction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Ce que le joueur a fait au marche aujourd'hui : ce qu'il a achete de chaque
 * annonce, et combien de fois il a paye pour de nouvelles annonces. Les
 * annonces elles-memes ne sont pas gardees : elles se recalculent du jour, du
 * monde et de ce compteur ({@link AuctionHouse}).
 */
public final class AuctionData {

    public static final MapCodec<AuctionData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(data -> data.day),
            Codec.INT.optionalFieldOf("rerolls", 0).forGetter(data -> data.rerolls),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bought", Map.of())
                    .forGetter(data -> {
                        Map<String, Integer> out = new HashMap<>();
                        data.bought.forEach((index, count) -> out.put(String.valueOf(index), count));
                        return out;
                    })
    ).apply(instance, AuctionData::new));

    private long day = -1L;
    private int rerolls;
    private final Map<Integer, Integer> bought = new HashMap<>();

    public AuctionData() {
    }

    private AuctionData(long day, int rerolls, Map<String, Integer> bought) {
        this.day = day;
        this.rerolls = rerolls;
        bought.forEach((index, count) -> {
            try {
                this.bought.put(Integer.parseInt(index), count);
            } catch (NumberFormatException ignored) {
                // Une cle illisible : l'annonce repart avec son stock plein.
            }
        });
    }

    public boolean isEmpty() {
        return day < 0L;
    }

    /** Passe au jour donne : un nouveau jour efface les achats et les renouvellements. */
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
}
