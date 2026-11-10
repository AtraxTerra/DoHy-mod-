package fr.atraxterra.dohy.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La faction d'un joueur : en solo, une faction a un seul membre, son chef.
 *
 * <p>Portee par le joueur (piece jointe conservee a la mort). Les claims sont
 * aussi notes sur les chunks eux-memes ({@link ClaimOwner}) : la protection doit
 * repondre sans charger le joueur, et la liste des claims de la faction ne doit
 * pas parcourir le monde.
 *
 * <p>Mutable : la banque, les claims et l'ELO bougent souvent, et NeoForge
 * sauvegarde la piece jointe avec le joueur.
 */
public final class FactionData {

    /** Une ligne de l'historique de la banque. */
    public record BankEntry(long time, long amount, String label) {
        static final Codec<BankEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("time").forGetter(BankEntry::time),
                Codec.LONG.fieldOf("amount").forGetter(BankEntry::amount),
                Codec.STRING.optionalFieldOf("label", "").forGetter(BankEntry::label)
        ).apply(instance, BankEntry::new));
    }

    /** Un chunk revendique, et s'il laisse apparaitre les monstres. */
    public record Claim(String dimension, int x, int z, boolean monsters) {
        static final Codec<Claim> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(Claim::dimension),
                Codec.INT.fieldOf("x").forGetter(Claim::x),
                Codec.INT.fieldOf("z").forGetter(Claim::z),
                Codec.BOOL.optionalFieldOf("monsters", true).forGetter(Claim::monsters)
        ).apply(instance, Claim::new));

        public boolean is(String dim, int chunkX, int chunkZ) {
            return x == chunkX && z == chunkZ && dimension.equals(dim);
        }
    }

    /** Un F home. */
    public record Home(String dimension, double x, double y, double z, float yaw) {
        static final Codec<Home> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(Home::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(Home::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Home::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Home::z),
                Codec.FLOAT.optionalFieldOf("yaw", 0.0f).forGetter(Home::yaw)
        ).apply(instance, Home::new));
    }

    /**
     * Le classement : ELO total, ELO et victoires de la semaine, ligue, et le
     * lundi deja traite (jour epoch).
     */
    public record Ranking(long elo, long weekElo, int weekWins, int league, long lastMonday) {
        static final Ranking EMPTY = new Ranking(0L, 0L, 0, 0, 0L);
        static final Codec<Ranking> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("elo", 0L).forGetter(Ranking::elo),
                Codec.LONG.optionalFieldOf("week_elo", 0L).forGetter(Ranking::weekElo),
                Codec.INT.optionalFieldOf("week_wins", 0).forGetter(Ranking::weekWins),
                Codec.INT.optionalFieldOf("league", 0).forGetter(Ranking::league),
                Codec.LONG.optionalFieldOf("last_monday", 0L).forGetter(Ranking::lastMonday)
        ).apply(instance, Ranking::new));
    }

    public static final MapCodec<FactionData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(data -> data.name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(data -> data.description),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(data -> List.copyOf(data.tags)),
            Codec.INT.optionalFieldOf("banner", 14).forGetter(data -> data.banner),
            Codec.STRING.optionalFieldOf("motd", "").forGetter(data -> data.motd),
            Codec.LONG.optionalFieldOf("created", 0L).forGetter(data -> data.created),
            Codec.LONG.optionalFieldOf("bank", 0L).forGetter(data -> data.bank),
            BankEntry.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(data -> List.copyOf(data.history)),
            Claim.CODEC.listOf().optionalFieldOf("claims", List.of()).forGetter(data -> List.copyOf(data.claims)),
            Home.CODEC.listOf().optionalFieldOf("homes", List.of()).forGetter(data -> List.copyOf(data.homes)),
            Ranking.CODEC.optionalFieldOf("ranking", Ranking.EMPTY).forGetter(data -> data.ranking),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("tiers", Map.of())
                    .forGetter(data -> Map.copyOf(data.tiers)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("upgrades", Map.of())
                    .forGetter(data -> Map.copyOf(data.upgrades)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("stats", Map.of())
                    .forGetter(data -> Map.copyOf(data.stats))
    ).apply(instance, FactionData::new));

    /** Lignes d'historique gardees. */
    static final int HISTORY = 30;

    String name;
    String description;
    final List<String> tags;
    int banner;
    String motd;
    long created;
    long bank;
    final List<BankEntry> history;
    final List<Claim> claims;
    final List<Home> homes;
    Ranking ranking;
    /** Paliers acquis par branche de l'arbre (chapitre 22b). */
    final Map<String, Integer> tiers;
    /** Palier en cours d'amelioration par branche : sa fin, en millisecondes epoch. */
    final Map<String, Long> upgrades;
    /** Compteurs des conditions de l'arbre : blocs parcourus, obsidienne cassee... */
    final Map<String, Long> stats;

    public FactionData() {
        this("", "", List.of(), 14, "", 0L, 0L, List.of(), List.of(), List.of(), Ranking.EMPTY, Map.of(), Map.of(),
                Map.of());
    }

    private FactionData(String name, String description, List<String> tags, int banner, String motd, long created,
                        long bank, List<BankEntry> history, List<Claim> claims, List<Home> homes, Ranking ranking,
                        Map<String, Integer> tiers, Map<String, Long> upgrades, Map<String, Long> stats) {
        this.name = name;
        this.description = description;
        this.tags = new ArrayList<>(tags);
        this.banner = banner;
        this.motd = motd;
        this.created = created;
        this.bank = bank;
        this.history = new ArrayList<>(history);
        this.claims = new ArrayList<>(claims);
        this.homes = new ArrayList<>(homes);
        this.ranking = ranking;
        this.tiers = new HashMap<>(tiers);
        this.upgrades = new HashMap<>(upgrades);
        this.stats = new HashMap<>(stats);
    }

    public boolean exists() {
        return !name.isEmpty();
    }

    public boolean isEmpty() {
        return !exists() && stats.isEmpty();
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<String> tags() {
        return List.copyOf(tags);
    }

    public int banner() {
        return banner;
    }

    public String motd() {
        return motd;
    }

    public long created() {
        return created;
    }

    public long bank() {
        return bank;
    }

    public List<BankEntry> history() {
        return List.copyOf(history);
    }

    public List<Claim> claims() {
        return List.copyOf(claims);
    }

    public List<Home> homes() {
        return List.copyOf(homes);
    }

    public Ranking ranking() {
        return ranking;
    }

    public int tier(String branch) {
        return tiers.getOrDefault(branch, 0);
    }

    public long upgradeEnd(String branch) {
        return upgrades.getOrDefault(branch, 0L);
    }

    public long stat(String key) {
        return stats.getOrDefault(key, 0L);
    }

    void addStat(String key, long amount) {
        stats.merge(key, amount, Long::sum);
    }

    void log(long amount, String label) {
        history.add(0, new BankEntry(System.currentTimeMillis(), amount, label));
        while (history.size() > HISTORY) {
            history.remove(history.size() - 1);
        }
    }

    /** Remet a neuf : la faction est dissoute. Les compteurs de l'arbre restent, ils disent ce que le joueur a fait. */
    void clear() {
        name = "";
        description = "";
        tags.clear();
        banner = 14;
        motd = "";
        created = 0L;
        bank = 0L;
        history.clear();
        claims.clear();
        homes.clear();
        ranking = Ranking.EMPTY;
        tiers.clear();
        upgrades.clear();
    }
}
