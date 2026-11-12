package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
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
 * La notation de la faction : une note sur 100, chaque lundi, et une bourse.
 *
 * <h2>D'ou elle vient</h2>
 *
 * <p>Sur NationsGlory, chaque pays est note toutes les semaines sur plusieurs
 * criteres (activite, gestion, competences, economie, militaire, architecture)
 * et touche le lundi une bourse selon son rang. Seul, il n'y a pas de rang :
 * la note se lit pour elle-meme, et la bourse lui est proportionnelle.
 *
 * <h2>Ce qu'elle ajoute a la ligue</h2>
 *
 * <p>La ligue ({@link FactionLeague}) ne regarde que l'ELO : une semaine sans
 * raid ni event ne rapporte rien. La notation regarde <b>tout ce que le joueur
 * a fait</b> — vendre, monter ses metiers, finir ses contrats, remplir le musee
 * — et paie donc aussi une semaine paisible. Les deux tombent ensemble, le
 * lundi a minuit ({@link FactionWeekly}).
 *
 * <h2>Deux sortes de criteres</h2>
 *
 * <ul>
 *   <li><b>De semaine</b> (activite, economie, contrats, militaire) : ils
 *       repartent de zero le lundi. Leurs compteurs vivent dans les stats de la
 *       faction, sous le prefixe {@code note:}.</li>
 *   <li><b>D'etat</b> (metiers, patrimoine, tresor) : ils se lisent tels quels
 *       au moment de la notation. Ils ne redescendent pas, et donnent a une
 *       partie avancee un socle de points que le debut n'a pas.</li>
 * </ul>
 *
 * <p>L'architecture, notee a la main sur NationsGlory, n'a pas d'equivalent :
 * un programme ne juge pas une construction. Le patrimoine (musee, territoire)
 * tient sa place.
 */
public final class FactionNotation {

    /** Les criteres, dans l'ordre de l'ecran. */
    public enum Criterion {
        ACTIVITY("activity", "Activite"),
        ECONOMY("economy", "Economie"),
        JOBS("jobs", "Metiers"),
        CONTRACTS("contracts", "Contrats"),
        MILITARY("military", "Militaire"),
        HERITAGE("heritage", "Patrimoine"),
        TREASURY("treasury", "Tresor");

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
        lines.add(new Line(Criterion.ACTIVITY,
                cap(activity.max() * days / Math.max(1, activity.fullDays()), activity.max()), activity.max(),
                days + " jour(s) joue(s) sur " + activity.fullDays()));

        long earned = earned(player, data);
        lines.add(new Line(Criterion.ECONOMY, round(config.economy().points(earned)), config.economy().max(),
                MoneyFormat.shortForm(earned) + " $ gagnes"));

        int levels = 0;
        for (Job job : Job.values()) {
            levels += JobApi.level(player, job);
        }
        int maxLevels = Math.max(1, Job.values().length * JobApi.maxLevel());
        lines.add(new Line(Criterion.JOBS, cap(config.jobs().max() * levels / maxLevels, config.jobs().max()),
                config.jobs().max(), levels + " niveaux sur " + maxLevels));

        NotationConfig.Contracts contracts = config.contracts();
        long daily = data.stat(DAILY);
        long weekly = data.stat(WEEKLY);
        long orders = data.stat(ORDERS);
        lines.add(new Line(Criterion.CONTRACTS, cap(daily * contracts.perDaily() + weekly * contracts.perWeekly()
                + orders * contracts.perOrder(), contracts.max()), contracts.max(),
                daily + " du jour, " + weekly + " hebdo, " + orders + " commande(s)"));

        NotationConfig.Military military = config.military();
        int wins = data.ranking().weekWins();
        lines.add(new Line(Criterion.MILITARY, cap(wins * military.perWin(), military.max()), military.max(),
                wins + " victoire(s) ELO"));

        NotationConfig.Heritage heritage = config.heritage();
        int pieces = 0;
        int exposed = 0;
        for (String wing : MuseumApi.wings()) {
            pieces += MuseumApi.expected(wing).size();
            exposed += MuseumApi.donatedIn(player, wing);
        }
        int claims = data.claims().size();
        double museumPart = pieces == 0 ? 0.0d : (double) exposed / pieces;
        double claimPart = Math.min(1.0d, (double) claims / Math.max(1, heritage.fullClaims()));
        lines.add(new Line(Criterion.HERITAGE, cap(heritage.max() * (museumPart + claimPart) / 2.0d, heritage.max()),
                heritage.max(), exposed + " / " + pieces + " au musee, " + claims + " claim(s)"));

        lines.add(new Line(Criterion.TREASURY, round(config.treasury().points(data.bank())), config.treasury().max(),
                MoneyFormat.shortForm(data.bank()) + " $ en banque"));
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

    // -- Le lundi ----------------------------------------------------------------------

    /**
     * Clot la semaine : calcule la note, verse la bourse a la banque de faction,
     * garde le resultat pour l'ecran et remet les compteurs a zero.
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
            long total = Math.round(score.total() * 10.0d);
            data.stats.put(LAST + "total", total);
            data.stats.put(LAST_BOURSE, bourse);
            data.stats.put(LAST_MONDAY, monday);
            data.stats.merge(BEST, total, Math::max);
            data.addStat(COUNT, 1L);
            if (bourse > 0L) {
                data.bank += bourse;
                data.log(bourse, "Bourse de notation " + points(score.total()) + " / " + points(score.max()));
            }
            player.sendSystemMessage(FactionApi.prefix().append(Component.literal("Notation de la semaine : "
                            + points(score.total()) + " / " + points(score.max()) + " (" + score.grade() + ")")
                    .withStyle(ChatFormatting.GOLD)));
            for (Line line : score.lines()) {
                player.sendSystemMessage(Component.literal("  " + line.criterion().label() + " "
                                + points(line.points()) + " / " + points(line.max()) + "  ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(line.measure()).withStyle(ChatFormatting.DARK_GRAY)));
            }
            FactionApi.tell(player, "Bourse : +" + MoneyFormat.exact(bourse) + " $ a la banque de faction.",
                    ChatFormatting.GREEN);
        }
        data.stats.remove(DAYS);
        data.stats.remove(WITHDRAWN);
        data.stats.remove(DAILY);
        data.stats.remove(WEEKLY);
        data.stats.remove(ORDERS);
        data.stats.put(EARNED_START, Economy.of(player).lifetimeEarned() + 1L);
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
