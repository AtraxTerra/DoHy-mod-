package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La notation de la nation : une note sur 100, chaque lundi, un classement
 * face aux douze nations rivales, et ses recompenses.
 *
 * <h2>D'ou elle vient</h2>
 *
 * <p>Sur NationsGlory, chaque pays est note toutes les semaines et paye le
 * lundi selon son rang. Ici le rang existe aussi : les douze nations rivales
 * ont chacune leur note de la semaine, et la faction du joueur se classe parmi
 * elles.
 *
 * <h2>Cinq criteres</h2>
 *
 * <ul>
 *   <li><b>Activite</b> (20) : les jours joues et les contrats termines.</li>
 *   <li><b>Economie</b> (20) : l'argent gagne dans la semaine.</li>
 *   <li><b>Commerce</b> (15) : le volume echange au marche international.</li>
 *   <li><b>Militaire</b> (20) : les victoires ELO.</li>
 *   <li><b>Territoire</b> (25) : la capitale, les pays tenus, les claims, le musee.</li>
 * </ul>
 *
 * <p>Les quatre premiers repartent de zero le lundi ; le territoire se lit tel
 * qu'il est. Les metiers et le tresor, qui ne faisaient que monter, ne comptent
 * plus : la note dit ce que la nation a fait cette semaine et ce qu'elle tient.
 *
 * <h2>Les nations rivales</h2>
 *
 * <p>La note d'une nation se tire de la graine du monde et de la semaine, entre
 * 38 et 80 : 38 a 72 au hasard, plus 8 points selon la part de ses pays
 * d'origine qu'elle tient encore — lui prendre ses pays la fait reculer.
 *
 * <h2>Les recompenses</h2>
 *
 * <p>La bourse (a la banque de faction), des points de conquete sur le pays
 * vise, et pour le podium des lots de ressources ; le premier gagne un claim.
 */
public final class FactionNotation {

    /** Les criteres, dans l'ordre de l'ecran. */
    public enum Criterion {
        ACTIVITY("activity", "Activite"),
        ECONOMY("economy", "Economie"),
        TRADE("trade", "Commerce"),
        MILITARY("military", "Militaire"),
        TERRITORY("territory", "Territoire");

        private final String key;
        private final String label;

        Criterion(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }
    }

    /** Une ligne de la note : les points obtenus, le plafond, et ce qui a ete mesure. */
    public record Line(Criterion criterion, double points, double max, String measure) {
    }

    /** Une note complete. */
    public record Score(List<Line> lines) {
        public double total() {
            double total = 0.0d;
            for (Line line : lines) {
                total += line.points();
            }
            return round(total);
        }

        public double max() {
            double max = 0.0d;
            for (Line line : lines) {
                max += line.max();
            }
            return max;
        }

        public long bourse() {
            return Math.round(total() * NotationConfig.config().boursePerPoint());
        }

        public String grade() {
            return NotationConfig.config().grade(total());
        }
    }

    // -- Compteurs de semaine, dans les stats de la faction -----------------------

    private static final String PREFIX = "note:";
    /** Les jours joues : un bit par jour de la semaine, lundi = bit 0. */
    private static final String DAYS = PREFIX + "days";
    /** Les gains cumules du joueur au debut de la semaine, plus un : 0 veut dire « pas encore releve ». */
    private static final String EARNED_START = PREFIX + "earned_start";
    private static final String WITHDRAWN = PREFIX + "withdrawn";
    public static final String DAILY = PREFIX + "daily";
    public static final String WEEKLY = PREFIX + "weekly";
    public static final String ORDERS = PREFIX + "orders";

    // -- La derniere notation, gardee pour l'ecran --------------------------------

    private static final String LAST = PREFIX + "last:";
    private static final String LAST_MONDAY = LAST + "monday";
    private static final String LAST_BOURSE = LAST + "bourse";
    private static final String BEST = PREFIX + "best";
    private static final String COUNT = PREFIX + "count";
    private static final String LAST_RANK = LAST + "rank";
    /** Les claims gagnes en finissant premier ; ils restent. */
    public static final String PRESTIGE_CLAIMS = "prestige:claims";

    private FactionNotation() {
    }

    private static double round(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    /** Affiche une note : « 12 » ou « 12,5 ». */
    public static String points(double value) {
        double rounded = round(value);
        return rounded == Math.rint(rounded)
                ? String.valueOf((long) rounded)
                : String.format(Locale.ROOT, "%.1f", rounded).replace('.', ',');
    }

    // -- Ce que les autres systemes signalent --------------------------------------

    /** Un contrat termine, une commande livree : {@link #DAILY}, {@link #WEEKLY} ou {@link #ORDERS}. */
    public static void count(ServerPlayer player, String key) {
        FactionData data = FactionApi.data(player);
        if (data.exists()) {
            data.addStat(key, 1L);
        }
    }

    /** Un retrait de la banque de faction : de l'argent deplace, pas gagne. */
    static void withdrawn(FactionData data, long amount) {
        data.addStat(WITHDRAWN, amount);
    }

    /** Marque le jour comme joue, et releve les gains de depart si la semaine n'en a pas. */
    static void touch(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return;
        }
        int bit = 1 << (LocalDate.now(FactionWeekly.zone()).getDayOfWeek().getValue() - 1);
        long days = data.stat(DAYS);
        if ((days & bit) == 0L) {
            data.stats.put(DAYS, days | bit);
        }
        if (data.stat(EARNED_START) == 0L) {
            data.stats.put(EARNED_START, Economy.of(player).lifetimeEarned() + 1L);
        }
    }

    // -- La note ---------------------------------------------------------------------

    /** La note de la semaine en cours, telle qu'elle tomberait maintenant. */
    public static Score current(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        NotationConfig.Config config = NotationConfig.config();
        List<Line> lines = new ArrayList<>();

        int days = Long.bitCount(data.stat(DAYS) & 0x7FL);
        NotationConfig.Activity activity = config.activity();
        long daily = data.stat(DAILY);
        long weekly = data.stat(WEEKLY);
        long orders = data.stat(ORDERS);
        double played = Math.min(activity.max(), activity.max() * days / Math.max(1, activity.fullDays()));
        double contracts = Math.min(activity.contractsMax(), daily * activity.perDaily()
                + weekly * activity.perWeekly() + orders * activity.perOrder());
        lines.add(new Line(Criterion.ACTIVITY, cap(played + contracts, activity.total()), activity.total(),
                days + " jour(s), " + (daily + weekly + orders) + " contrat(s)"));

        long earned = earned(player, data);
        lines.add(new Line(Criterion.ECONOMY, round(config.economy().points(earned)), config.economy().max(),
                MoneyFormat.shortForm(earned) + " $ gagnes"));

        long volume = data.stat(fr.atraxterra.dohy.faction.market.WorldMarket.VOLUME);
        lines.add(new Line(Criterion.TRADE, round(config.trade().points(volume)), config.trade().max(),
                MoneyFormat.shortForm(volume) + " $ echanges"));

        NotationConfig.Military military = config.military();
        int wins = data.ranking().weekWins();
        lines.add(new Line(Criterion.MILITARY, cap(wins * military.perWin(), military.max()), military.max(),
                wins + " victoire(s) ELO"));

        NotationConfig.Territory territory = config.territory();
        int pieces = 0;
        int exposed = 0;
        for (String wing : MuseumApi.wings()) {
            pieces += MuseumApi.expected(wing).size();
            exposed += MuseumApi.donatedIn(player, wing);
        }
        int claims = data.claims().size();
        double museumPart = pieces == 0 ? 0.0d : (double) exposed / pieces;
        double claimPart = Math.min(1.0d, (double) claims / Math.max(1, territory.fullClaims()));
        double points;
        String measure;
        if (EarthNations.active()) {
            int countries = EarthNations.heldBy(player).size();
            boolean capital = EarthNations.nation(player).capital() != 0;
            points = (capital ? territory.capital() : 0.0d)
                    + territory.countries() * Math.min(1.0d, Math.sqrt((double) countries
                    / Math.max(1, territory.fullCountries())))
                    + territory.claims() * claimPart + territory.museum() * museumPart;
            measure = countries + " pays, " + claims + " claim(s)" + (capital ? ", capitale" : ", sans capitale");
        } else {
            // sans carte de la Terre, les points de la capitale et des pays vont aux claims et au musee
            double spare = (territory.capital() + territory.countries()) / 2.0d;
            points = (territory.claims() + spare) * claimPart + (territory.museum() + spare) * museumPart;
            measure = claims + " claim(s), " + exposed + " / " + pieces + " au musee";
        }
        lines.add(new Line(Criterion.TERRITORY, cap(points, territory.max()), territory.max(), measure));
        return new Score(List.copyOf(lines));
    }

    private static double cap(double value, double max) {
        return round(Math.max(0.0d, Math.min(max, value)));
    }

    /** L'argent gagne depuis le debut de la semaine, retraits de la banque de faction deduits. */
    private static long earned(ServerPlayer player, FactionData data) {
        long start = data.stat(EARNED_START);
        if (start == 0L) {
            return 0L;
        }
        return Math.max(0L, Economy.of(player).lifetimeEarned() - (start - 1L) - data.stat(WITHDRAWN));
    }

    // -- Le classement face aux nations ------------------------------------------------------

    /** Une ligne du classement : une nation rivale, ou la faction du joueur. */
    public record Standing(String name, double score, int color, boolean mine) {
    }

    /** Le lundi qui clora la semaine en cours, en jour epoch. */
    private static long nextMonday() {
        LocalDate today = LocalDate.now(FactionWeekly.zone());
        return today.plusDays(8 - today.getDayOfWeek().getValue()).toEpochDay();
    }

    /** La note d'une nation rivale pour la semaine close ce lundi. */
    private static double rivalScore(long seed, long monday, int index, double share) {
        java.util.Random random = new java.util.Random(seed ^ monday * 0x9E37_79B9L ^ index * 0x5851_F42DL);
        random.nextDouble();
        return round(38.0d + 34.0d * random.nextDouble() + 8.0d * share);
    }

    /** Le classement de la semaine close ce lundi, du premier au dernier, avec cette note pour le joueur. */
    public static List<Standing> standings(ServerPlayer player, double mine, long monday) {
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        long seed = server.overworld().getSeed();
        double[] shares = EarthNations.rivalShares(server);
        List<Standing> out = new ArrayList<>();
        List<fr.atraxterra.dohy.faction.event.EventRun.Rival> pool =
                fr.atraxterra.dohy.faction.event.EventRun.rivalPool();
        for (int i = 0; i < pool.size(); i++) {
            out.add(new Standing(pool.get(i).name(), rivalScore(seed, monday, i, shares[i]),
                    0xFF000000 | pool.get(i).color().getTextColor(), false));
        }
        out.add(new Standing(FactionApi.data(player).name(), round(mine), 0xFFFAC400, true));
        // a note egale, le joueur passe devant
        out.sort((a, b) -> a.score() != b.score() ? Double.compare(b.score(), a.score())
                : Boolean.compare(b.mine(), a.mine()));
        return out;
    }

    /** Le classement tel qu'il tomberait maintenant. */
    public static List<Standing> standings(ServerPlayer player) {
        return standings(player, current(player).total(), nextMonday());
    }

    /** Le rang du joueur dans ce classement : 1 pour le premier. */
    public static int rank(List<Standing> standings) {
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).mine()) {
                return i + 1;
            }
        }
        return standings.size();
    }

    /** « 1er », « 2e ». */
    public static String ordinal(int rank) {
        return rank == 1 ? "1er" : rank + "e";
    }

    // -- Le lundi ----------------------------------------------------------------------

    /**
     * Clot la semaine : calcule la note et le rang, verse la bourse a la banque
     * de faction, donne les recompenses, garde le resultat pour l'ecran et
     * remet les compteurs a zero.
     *
     * <p>Appele par {@link FactionWeekly} <b>avant</b> qu'il ne remette les
     * victoires de la semaine a zero : le critere militaire les lit.
     *
     * @param monday le lundi qui ouvre la nouvelle semaine, en jour epoch
     * @param played faux pour un lundi rattrape apres une semaine d'absence : rien n'a ete fait, rien n'est note
     */
    static void close(ServerPlayer player, long monday, boolean played) {
        FactionData data = FactionApi.data(player);
        if (played) {
            Score score = current(player);
            long bourse = score.bourse();
            for (Line line : score.lines()) {
                data.stats.put(LAST + line.criterion().key(), Math.round(line.points() * 10.0d));
            }
            List<Standing> standings = standings(player, score.total(), monday);
            int rank = rank(standings);
            long total = Math.round(score.total() * 10.0d);
            data.stats.put(LAST + "total", total);
            data.stats.put(LAST_BOURSE, bourse);
            data.stats.put(LAST_MONDAY, monday);
            data.stats.put(LAST_RANK, (long) rank);
            data.stats.merge(BEST, total, Math::max);
            data.addStat(COUNT, 1L);
            if (bourse > 0L) {
                data.bank += bourse;
                data.log(bourse, "Bourse de notation " + points(score.total()) + " / " + points(score.max()));
            }
            player.sendSystemMessage(FactionApi.prefix().append(Component.literal("Notation de la semaine : "
                            + points(score.total()) + " / " + points(score.max()) + " (" + score.grade() + "), "
                            + ordinal(rank) + " sur " + standings.size())
                    .withStyle(ChatFormatting.GOLD)));
            for (Line line : score.lines()) {
                player.sendSystemMessage(Component.literal("  " + line.criterion().label() + " "
                                + points(line.points()) + " / " + points(line.max()) + "  ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(line.measure()).withStyle(ChatFormatting.DARK_GRAY)));
            }
            for (int i = 0; i < Math.min(3, standings.size()); i++) {
                Standing standing = standings.get(i);
                player.sendSystemMessage(Component.literal("  " + ordinal(i + 1) + " " + standing.name() + " · "
                        + points(standing.score())).withStyle(standing.mine() ? ChatFormatting.YELLOW
                        : ChatFormatting.GRAY));
            }
            FactionApi.tell(player, "Bourse : +" + MoneyFormat.exact(bourse) + " $ a la banque de faction.",
                    ChatFormatting.GREEN);
            reward(player, data, score, rank);
        }
        data.stats.remove(DAYS);
        data.stats.remove(WITHDRAWN);
        data.stats.remove(DAILY);
        data.stats.remove(WEEKLY);
        data.stats.remove(ORDERS);
        data.stats.remove(fr.atraxterra.dohy.faction.market.WorldMarket.VOLUME);
        data.stats.put(EARNED_START, Economy.of(player).lifetimeEarned() + 1L);
    }

    /** Les points de conquete que vaudrait cette note a ce rang. */
    public static long conquestPoints(double total, int rank) {
        NotationConfig.Rewards rewards = NotationConfig.config().rewards();
        return Math.round(total * rewards.conquestPerPoint() * rewards.multiplier(rank));
    }

    /** Ce que la semaine rapporte en plus de la bourse : conquete, lots du podium, claim du premier. */
    private static void reward(ServerPlayer player, FactionData data, Score score, int rank) {
        NotationConfig.Rewards rewards = NotationConfig.config().rewards();
        EarthNations.award(player, conquestPoints(score.total(), rank), "Notation");
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < rewards.lots(rank); i++) {
            net.minecraft.world.item.ItemStack lot = fr.atraxterra.dohy.faction.market.WorldMarket.lot(random,
                    rewards.lotValue());
            if (lot.isEmpty()) {
                continue;
            }
            String name = lot.getCount() + " x " + lot.getHoverName().getString();
            if (!player.getInventory().add(lot)) {
                player.drop(lot, false);
            }
            FactionApi.tell(player, "Podium (" + ordinal(rank) + ") : un lot de " + name + ".", ChatFormatting.GREEN);
        }
        if (rank == 1 && data.stat(PRESTIGE_CLAIMS) < rewards.prestigeClaims()) {
            data.addStat(PRESTIGE_CLAIMS, 1L);
            FactionApi.tell(player, "Premiere du classement : +1 claim (" + data.stat(PRESTIGE_CLAIMS) + " / "
                    + rewards.prestigeClaims() + ").", ChatFormatting.GOLD);
        }
    }

    // -- La derniere notation -------------------------------------------------------------

    /** Vrai si une semaine a deja ete notee. */
    public static boolean hasLast(FactionData data) {
        return data.stat(LAST_MONDAY) != 0L;
    }

    public static double lastPoints(FactionData data, Criterion criterion) {
        return data.stat(LAST + criterion.key()) / 10.0d;
    }

    public static double lastTotal(FactionData data) {
        return data.stat(LAST + "total") / 10.0d;
    }

    /** Le rang de la derniere semaine notee ; 0 si elle date d'avant le classement. */
    public static int lastRank(FactionData data) {
        return (int) data.stat(LAST_RANK);
    }

    public static long lastBourse(FactionData data) {
        return data.stat(LAST_BOURSE);
    }

    /** Le lundi qui a clos la derniere semaine notee, en jour epoch. */
    public static long lastMonday(FactionData data) {
        return data.stat(LAST_MONDAY);
    }

    public static double best(FactionData data) {
        return data.stat(BEST) / 10.0d;
    }

    public static long count(FactionData data) {
        return data.stat(COUNT);
    }
}
