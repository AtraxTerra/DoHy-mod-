package fr.atraxterra.dohy.economy.price;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Tarif d'un item, telle que le datapack le decrit.
 *
 * <p>Ces valeurs ne sont pas ecrites a la main : elles sont generees par
 * {@code tools/gen_prices.py} depuis {@code data/rates.csv}, ou chaque item est
 * decrit par son taux d'obtention horaire. Le prix en decoule :
 *
 * <pre>base_price = TARGET_RATE[tier] / rate_per_hour * MONEY_MULTIPLIER</pre>
 *
 * <p>Consequence : deux items du meme tier rapportent le meme montant a
 * l'heure, et rééquilibrer toute l'economie revient a changer un seul
 * multiplicateur. Voir {@code docs/BALANCE.md}.
 */
public record PriceEntry(
        String category,
        String tier,
        long basePrice,
        int halfSaturation,
        double floorPct,
        double ceilingPct,
        long buyPrice,
        boolean sellable
) {

    public static final Codec<PriceEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("category").forGetter(PriceEntry::category),
            Codec.STRING.fieldOf("tier").forGetter(PriceEntry::tier),
            Codec.LONG.fieldOf("base_price").forGetter(PriceEntry::basePrice),
            Codec.INT.optionalFieldOf("s0", 1000).forGetter(PriceEntry::halfSaturation),
            Codec.DOUBLE.optionalFieldOf("floor_pct", 0.15d).forGetter(PriceEntry::floorPct),
            Codec.DOUBLE.optionalFieldOf("ceiling_pct", 1.8d).forGetter(PriceEntry::ceilingPct),
            Codec.LONG.optionalFieldOf("buy_price", 0L).forGetter(PriceEntry::buyPrice),
            Codec.BOOL.optionalFieldOf("sellable", true).forGetter(PriceEntry::sellable)
    ).apply(instance, PriceEntry::new));

    /** Prix plancher absolu, en pieces. */
    public long floorPrice() {
        return Math.max(1L, Math.round(basePrice * floorPct));
    }

    /** Prix plafond absolu, en pieces. */
    public long ceilingPrice() {
        return Math.round(basePrice * ceilingPct);
    }
}
