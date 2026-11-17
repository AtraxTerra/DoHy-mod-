package fr.atraxterra.dohy.ui.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Une courbe de prix, comme a la bourse : des points a pas fixe, du plus ancien au
 * plus recent, et le volume echange pendant chacun de ces pas.
 *
 * <p>Le dernier point est toujours le prix de maintenant. Une courbe ne montre que
 * le passe : un cours tire d'avance (celui de l'heure, voir {@code HourlyCourse})
 * se lirait sinon avant qu'il tombe.
 *
 * @param values    les points, du plus ancien au plus recent
 * @param volumes   le volume echange sur chaque pas, aligne sur {@code values} ; vide pour aucun
 * @param reference un repere horizontal (le prix de base, un cours de 1000), 0 pour aucun
 * @param step      la duree d'un pas, en minutes
 * @param unit      {@link #DOLLARS} ou {@link #PERMILLE}
 */
public record PriceChart(List<Long> values, List<Long> volumes, long reference, int step, int unit) {

    /** Des prix en dollars. */
    public static final int DOLLARS = 0;
    /** Un cours en pour mille de la reference : 1000 pour le prix de base. */
    public static final int PERMILLE = 1;

    public static final StreamCodec<RegistryFriendlyByteBuf, PriceChart> STREAM_CODEC = StreamCodec.of(
            (buffer, chart) -> {
                longs(buffer, chart.values());
                longs(buffer, chart.volumes());
                buffer.writeVarLong(chart.reference());
                buffer.writeVarInt(chart.step());
                buffer.writeVarInt(chart.unit());
            },
            buffer -> new PriceChart(longs(buffer), longs(buffer), buffer.readVarLong(), buffer.readVarInt(),
                    buffer.readVarInt()));

    /** Une courbe absente s'ecrit comme un booleen faux : la plupart des cartes n'en ont pas. */
    public static final StreamCodec<RegistryFriendlyByteBuf, PriceChart> OPTIONAL_STREAM_CODEC = StreamCodec.of(
            (buffer, chart) -> {
                buffer.writeBoolean(chart != null);
                if (chart != null) {
                    STREAM_CODEC.encode(buffer, chart);
                }
            },
            buffer -> buffer.readBoolean() ? STREAM_CODEC.decode(buffer) : null);

    public PriceChart {
        values = List.copyOf(values);
        volumes = List.copyOf(volumes);
    }

    public int size() {
        return values.size();
    }

    public long first() {
        return values.isEmpty() ? 0L : values.getFirst();
    }

    public long last() {
        return values.isEmpty() ? 0L : values.getLast();
    }

    public long high() {
        long high = Long.MIN_VALUE;
        for (long value : values) {
            high = Math.max(high, value);
        }
        return values.isEmpty() ? 0L : high;
    }

    public long low() {
        long low = Long.MAX_VALUE;
        for (long value : values) {
            low = Math.min(low, value);
        }
        return values.isEmpty() ? 0L : low;
    }

    /** Le volume total de la periode. */
    public long traded() {
        long total = 0L;
        for (long volume : volumes) {
            total += volume;
        }
        return total;
    }

    /** La variation du premier au dernier point, en pour dix mille : 1240 pour +12,4 %. */
    public int change() {
        long first = first();
        if (first <= 0L) {
            return 0;
        }
        return (int) Math.round((last() - first) * 10_000.0d / first);
    }

    /** Vrai si la courbe ne bouge pas : un prix fixe. */
    public boolean flat() {
        return high() == low();
    }

    /** La duree couverte, en minutes. */
    public int span() {
        return Math.max(0, size() - 1) * step;
    }

    private static void longs(RegistryFriendlyByteBuf buffer, List<Long> values) {
        buffer.writeVarInt(values.size());
        for (long value : values) {
            buffer.writeVarLong(value);
        }
    }

    private static List<Long> longs(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), 4096);
        List<Long> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(buffer.readVarLong());
        }
        return values;
    }
}
