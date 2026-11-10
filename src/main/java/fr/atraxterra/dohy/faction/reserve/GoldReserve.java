package fr.atraxterra.dohy.faction.reserve;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * La reserve d'or de la faction (lot 22e), gardee sur le joueur : ou est le
 * cube, sa taille, l'or liquide qu'il contenait au dernier releve, et les
 * investissements en cours.
 *
 * @param dimension    la dimension du cube
 * @param origin       son coin bas (x, y, z minimaux)
 * @param size         son cote : 5, 6 ou 7 (palier 1, 2 ou 3) ; 0 sans reserve
 * @param gold         les blocs d'or liquide en son coeur
 * @param investments  les placements en cours
 */
public record GoldReserve(String dimension, BlockPos origin, int size, int gold, List<Investment> investments) {

    public static final GoldReserve EMPTY = new GoldReserve("", BlockPos.ZERO, 0, 0, List.of());

    /**
     * Un placement : l'argent sorti de la banque, sa date, sa duree en jours
     * et son rendement en pour mille (multiplicateur d'interets compris, fige
     * au placement).
     */
    public record Investment(long amount, long start, int days, int permille) {

        public static final Codec<Investment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("amount").forGetter(Investment::amount),
                Codec.LONG.fieldOf("start").forGetter(Investment::start),
                Codec.INT.fieldOf("days").forGetter(Investment::days),
                Codec.INT.fieldOf("permille").forGetter(Investment::permille)
        ).apply(instance, Investment::new));

        public long end() {
            return start + days * 86_400_000L;
        }

        public boolean ripe(long now) {
            return now >= end();
        }

        /** Ce que le placement rend a l'echeance : la mise et ses interets. */
        public long payout() {
            return amount + amount * permille / 1000L;
        }
    }

    public static final MapCodec<GoldReserve> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(GoldReserve::dimension),
            BlockPos.CODEC.optionalFieldOf("origin", BlockPos.ZERO).forGetter(GoldReserve::origin),
            Codec.INT.optionalFieldOf("size", 0).forGetter(GoldReserve::size),
            Codec.INT.optionalFieldOf("gold", 0).forGetter(GoldReserve::gold),
            Investment.CODEC.listOf().optionalFieldOf("investments", List.of()).forGetter(GoldReserve::investments)
    ).apply(instance, GoldReserve::new));

    public boolean exists() {
        return size > 0;
    }

    /** Le palier : 1 pour un cube de 5, 2 pour 6, 3 pour 7. */
    public int tier() {
        return size == 0 ? 0 : size - 4;
    }

    public long invested() {
        long total = 0L;
        for (Investment investment : investments) {
            total += investment.amount();
        }
        return total;
    }

    public boolean contains(String dim, BlockPos pos) {
        return exists() && dimension.equals(dim)
                && pos.getX() >= origin.getX() && pos.getX() < origin.getX() + size
                && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + size
                && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + size;
    }

    public GoldReserve withGold(int count) {
        return new GoldReserve(dimension, origin, size, count, investments);
    }

    public GoldReserve with(Investment investment) {
        List<Investment> list = new ArrayList<>(investments);
        list.add(investment);
        return new GoldReserve(dimension, origin, size, gold, List.copyOf(list));
    }

    public GoldReserve without(int index) {
        List<Investment> list = new ArrayList<>(investments);
        list.remove(index);
        return new GoldReserve(dimension, origin, size, gold, List.copyOf(list));
    }
}
