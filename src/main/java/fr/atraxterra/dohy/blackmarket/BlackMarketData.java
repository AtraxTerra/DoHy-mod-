package fr.atraxterra.dohy.blackmarket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Ce que le joueur a fait chez le receleur aujourd'hui : les lots achetes de
 * chaque offre, les lots sans etiquette, les pieces revendues de chaque
 * demande. L'arrivage lui-meme n'est pas garde : il se recalcule du jour, du
 * monde et du joueur ({@link BlackMarket}).
 */
public final class BlackMarketData {

    public static final MapCodec<BlackMarketData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(data -> data.day),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bought", Map.of())
                    .forGetter(data -> keys(data.bought)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("sold", Map.of())
                    .forGetter(data -> keys(data.sold)),
            Codec.INT.optionalFieldOf("lots", 0).forGetter(data -> data.lots)
    ).apply(instance, BlackMarketData::new));

    private long day = -1L;
    private final Map<Integer, Integer> bought = new HashMap<>();
    private final Map<Integer, Integer> sold = new HashMap<>();
    private int lots;

    public BlackMarketData() {
    }

    private BlackMarketData(long day, Map<String, Integer> bought, Map<String, Integer> sold, int lots) {
        this.day = day;
        read(bought, this.bought);
        read(sold, this.sold);
        this.lots = lots;
    }

    private static Map<String, Integer> keys(Map<Integer, Integer> map) {
        Map<String, Integer> out = new HashMap<>();
        map.forEach((index, count) -> out.put(String.valueOf(index), count));
        return out;
    }

    private static void read(Map<String, Integer> from, Map<Integer, Integer> into) {
        from.forEach((index, count) -> {
            try {
                into.put(Integer.parseInt(index), count);
            } catch (NumberFormatException ignored) {
                // une cle illisible : l'offre repart pleine
            }
        });
    }

    public boolean isEmpty() {
        return day < 0L;
    }

    /** Passe au jour donne : un nouveau jour, un nouvel arrivage. */
    void sync(long today) {
        if (day != today) {
            day = today;
            bought.clear();
            sold.clear();
            lots = 0;
        }
    }

    int bought(int index) {
        return bought.getOrDefault(index, 0);
    }

    void buy(int index) {
        bought.merge(index, 1, Integer::sum);
    }

    int sold(int index) {
        return sold.getOrDefault(index, 0);
    }

    void sell(int index, int count) {
        sold.merge(index, count, Integer::sum);
    }

    int lots() {
        return lots;
    }

    void lot() {
        lots++;
    }
}
