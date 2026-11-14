package fr.atraxterra.dohy.faction.absence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ce que le joueur laisse en partant : l'heure, son argent, le contenu des
 * coffres de sa base, les cours du marche, sa ligue et sa derniere notation ;
 * et le dernier rapport d'absence, que {@code /rapport} rouvre.
 *
 * <p>Releve a la deconnexion, et toutes les cinq minutes en jeu : un serveur qui
 * s'arrete net garde ainsi un releve de moins de cinq minutes.
 */
public record AbsenceData(long leftAt, long wallet, long banked, long factionBank, int league, long notedMonday,
                          List<Count> stock, List<Rate> market, Report last) {

    public static final AbsenceData EMPTY = new AbsenceData(0L, 0L, 0L, 0L, 0, 0L, List.of(), List.of(), Report.NONE);

    /** Un objet et sa quantite. */
    public record Count(Identifier item, long count) {
        static final Codec<Count> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Count::item),
                Codec.LONG.fieldOf("count").forGetter(Count::count)
        ).apply(instance, Count::new));
    }

    /** Une ressource du marche international et son cours. */
    public record Rate(Identifier good, double factor) {
        static final Codec<Rate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("good").forGetter(Rate::good),
                Codec.DOUBLE.fieldOf("factor").forGetter(Rate::factor)
        ).apply(instance, Rate::new));
    }

    /** Une carte du rapport : son icone, son titre, ses lignes, la couleur de son lisere, son action. */
    public record Line(Identifier icon, String title, List<String> lines, int color, String action) {
        static final Codec<Line> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("icon").forGetter(Line::icon),
                Codec.STRING.fieldOf("title").forGetter(Line::title),
                Codec.STRING.listOf().optionalFieldOf("lines", List.of()).forGetter(Line::lines),
                Codec.INT.optionalFieldOf("color", 0xFF55545C).forGetter(Line::color),
                Codec.STRING.optionalFieldOf("action", "").forGetter(Line::action)
        ).apply(instance, Line::new));
    }

    /**
     * Le rapport d'une absence.
     *
     * @param built  quand il a ete ecrit
     * @param away   la duree de l'absence, en millisecondes
     * @param ran    le temps pendant lequel le serveur tournait (moins que l'absence s'il s'est arrete)
     * @param kept   les chunks tenus a l'ecriture
     * @param claims les claims de la faction
     * @param boxes  les machines et coffres des chunks tenus
     */
    public record Report(long built, long away, long ran, int kept, int claims, int boxes, List<Count> gained,
                         List<Count> spent, List<Line> money, List<Line> nations, List<Line> world) {

        public static final Report NONE = new Report(0L, 0L, 0L, 0, 0, 0, List.of(), List.of(), List.of(), List.of(),
                List.of());

        static final Codec<Report> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("built").forGetter(Report::built),
                Codec.LONG.fieldOf("away").forGetter(Report::away),
                Codec.LONG.optionalFieldOf("ran", 0L).forGetter(Report::ran),
                Codec.INT.optionalFieldOf("kept", 0).forGetter(Report::kept),
                Codec.INT.optionalFieldOf("claims", 0).forGetter(Report::claims),
                Codec.INT.optionalFieldOf("boxes", 0).forGetter(Report::boxes),
                Count.CODEC.listOf().optionalFieldOf("gained", List.of()).forGetter(Report::gained),
                Count.CODEC.listOf().optionalFieldOf("spent", List.of()).forGetter(Report::spent),
                Line.CODEC.listOf().optionalFieldOf("money", List.of()).forGetter(Report::money),
                Line.CODEC.listOf().optionalFieldOf("nations", List.of()).forGetter(Report::nations),
                Line.CODEC.listOf().optionalFieldOf("world", List.of()).forGetter(Report::world)
        ).apply(instance, Report::new));

        public boolean exists() {
            return built > 0L;
        }
    }

    public static final MapCodec<AbsenceData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("left_at", 0L).forGetter(AbsenceData::leftAt),
            Codec.LONG.optionalFieldOf("wallet", 0L).forGetter(AbsenceData::wallet),
            Codec.LONG.optionalFieldOf("banked", 0L).forGetter(AbsenceData::banked),
            Codec.LONG.optionalFieldOf("faction_bank", 0L).forGetter(AbsenceData::factionBank),
            Codec.INT.optionalFieldOf("league", 0).forGetter(AbsenceData::league),
            Codec.LONG.optionalFieldOf("noted_monday", 0L).forGetter(AbsenceData::notedMonday),
            Count.CODEC.listOf().optionalFieldOf("stock", List.of()).forGetter(AbsenceData::stock),
            Rate.CODEC.listOf().optionalFieldOf("market", List.of()).forGetter(AbsenceData::market),
            Report.CODEC.optionalFieldOf("last", Report.NONE).forGetter(AbsenceData::last)
    ).apply(instance, AbsenceData::new));

    public boolean exists() {
        return leftAt > 0L || last.exists();
    }

    public AbsenceData withLast(Report report) {
        return new AbsenceData(leftAt, wallet, banked, factionBank, league, notedMonday, stock, market, report);
    }

    public Map<Identifier, Long> stockMap() {
        Map<Identifier, Long> map = new HashMap<>();
        for (Count count : stock) {
            map.put(count.item(), count.count());
        }
        return map;
    }

    public Map<Identifier, Double> marketMap() {
        Map<Identifier, Double> map = new HashMap<>();
        for (Rate rate : market) {
            map.put(rate.good(), rate.factor());
        }
        return map;
    }

    static List<Count> counts(Map<Identifier, Long> map) {
        List<Count> list = new ArrayList<>();
        for (Map.Entry<Identifier, Long> entry : map.entrySet()) {
            list.add(new Count(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(list);
    }
}
