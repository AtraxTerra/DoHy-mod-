package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Les statistiques d'une rune, sur la rune ou sur la piece ou elle est fusionnee.
 *
 * <p>Une ligne vaut {@code base x rarete x (1 + 0,5 (tier - 1 + bonus))} :
 * la base de la statistique ({@link RpgStat#runeBase}), le multiplicateur de
 * la rarete, le tier de la rune, et le bonus de la ligne — gagne a
 * l'amelioration quand la rune sacrifiee porte la meme statistique (wiki :
 * « la rune fusionne les statistiques qu'elle partage »).
 *
 * @param tier  1 a 4
 */
public record RuneData(RuneRarity rarity, int tier, List<Line> lines) {

    public static final int MAX_TIER = 4;

    public record Line(RpgStat stat, int bonus) {
        public static final Codec<Line> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                RpgStat.CODEC.fieldOf("stat").forGetter(Line::stat),
                Codec.INT.optionalFieldOf("bonus", 0).forGetter(Line::bonus)
        ).apply(instance, Line::new));
    }

    public static final Codec<RuneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RuneRarity.CODEC.fieldOf("rarity").forGetter(RuneData::rarity),
            Codec.intRange(1, MAX_TIER).fieldOf("tier").forGetter(RuneData::tier),
            Line.CODEC.listOf().fieldOf("lines").forGetter(RuneData::lines)
    ).apply(instance, RuneData::new));

    public static final StreamCodec<ByteBuf, RuneData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /** Une rune neuve : tier 1, une ou deux statistiques differentes. */
    public static RuneData roll(RuneRarity rarity, RandomSource random) {
        int count = rarity == RuneRarity.COMMON && random.nextBoolean() ? 1 : 2;
        List<RpgStat> pool = new ArrayList<>(List.of(RpgStat.values()));
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            lines.add(new Line(pool.remove(random.nextInt(pool.size())), 0));
        }
        return new RuneData(rarity, 1, List.copyOf(lines));
    }

    public float value(Line line) {
        return line.stat().runeBase() * rarity.multiplier() * (1.0f + 0.5f * (tier - 1 + line.bonus()));
    }

    /** Ce que la rune donne d'une statistique. */
    public float value(RpgStat stat) {
        float total = 0.0f;
        for (Line line : lines) {
            if (line.stat() == stat) {
                total += value(line);
            }
        }
        return total;
    }

    public boolean has(RpgStat stat) {
        return lines.stream().anyMatch(line -> line.stat() == stat);
    }

    private int bonus(RpgStat stat) {
        return lines.stream().filter(line -> line.stat() == stat).mapToInt(Line::bonus).max().orElse(0);
    }

    /** Peut-elle absorber {@code other} a l'enclume ? Meme rarete, meme tier, pas au maximum. */
    public boolean canUpgradeWith(RuneData other) {
        return other.rarity == rarity && other.tier == tier && tier < MAX_TIER;
    }

    /**
     * L'amelioration (wiki : « +1 tier, en sacrifiant une rune du meme tier ;
     * la rune fusionne les statistiques qu'elle partage ») : les lignes qu'elle
     * partage avec la rune sacrifiee gagnent un bonus, les autres restent.
     */
    public RuneData upgradedWith(RuneData other) {
        List<Line> merged = new ArrayList<>();
        for (Line line : lines) {
            merged.add(other.has(line.stat())
                    ? new Line(line.stat(), Math.max(line.bonus(), other.bonus(line.stat())) + 1)
                    : line);
        }
        return new RuneData(rarity, tier + 1, List.copyOf(merged));
    }

    /** Le cout de l'amelioration vers le tier suivant (wiki : 10, 15, 20 niveaux). */
    public int upgradeCost() {
        return switch (tier + 1) {
            case 2 -> 10;
            case 3 -> 15;
            default -> 20;
        };
    }
}
