package fr.atraxterra.dohy.palamod.trixium;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Le concours Trixium d'un joueur, garde sur lui.
 *
 * @param season      la saison de {@link #deposited} et {@link #claimed}
 *                    ({@link TrixiumSeason#id}) ; 0 avant le premier depot
 * @param deposited   le trixium depose cette saison (lingot 1, bloc 9)
 * @param claimed     les paliers recuperes cette saison, un bit par palier
 * @param lifetime    tout le trixium depose depuis toujours (les succes le lisent)
 * @param lastSeason  la derniere saison close, 0 pour aucune
 * @param lastRank    son rang final, 0 sans depot
 * @param lastDeposit son depot final
 */
public record TrixiumData(int season, long deposited, long claimed, long lifetime,
                          int lastSeason, int lastRank, long lastDeposit) {

    public static final TrixiumData EMPTY = new TrixiumData(0, 0L, 0L, 0L, 0, 0, 0L);

    public static final MapCodec<TrixiumData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("season", 0).forGetter(TrixiumData::season),
            Codec.LONG.optionalFieldOf("deposited", 0L).forGetter(TrixiumData::deposited),
            Codec.LONG.optionalFieldOf("claimed", 0L).forGetter(TrixiumData::claimed),
            Codec.LONG.optionalFieldOf("lifetime", 0L).forGetter(TrixiumData::lifetime),
            Codec.INT.optionalFieldOf("last_season", 0).forGetter(TrixiumData::lastSeason),
            Codec.INT.optionalFieldOf("last_rank", 0).forGetter(TrixiumData::lastRank),
            Codec.LONG.optionalFieldOf("last_deposit", 0L).forGetter(TrixiumData::lastDeposit)
    ).apply(instance, TrixiumData::new));

    public boolean hasClaimed(int milestone) {
        return (claimed >>> milestone & 1L) != 0L;
    }

    public TrixiumData withClaimed(int milestone) {
        return new TrixiumData(season, deposited, claimed | 1L << milestone, lifetime, lastSeason, lastRank,
                lastDeposit);
    }

    public TrixiumData plus(long amount) {
        return new TrixiumData(season, deposited + amount, claimed, lifetime + amount, lastSeason, lastRank,
                lastDeposit);
    }

    /** Une nouvelle saison : le depot et les paliers repartent de zero, le bilan de l'ancienne est garde. */
    public TrixiumData next(int newSeason, int closedSeason, int rank, long finalDeposit) {
        return new TrixiumData(newSeason, 0L, 0L, lifetime, closedSeason, rank, finalDeposit);
    }
}
