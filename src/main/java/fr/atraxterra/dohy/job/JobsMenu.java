package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.palamod.defense.Amulets;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.JobDetailView;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La page Metiers : un onglet par metier, la fiche du metier a gauche, et a droite ce
 * qu'il debloque, ses primes, ses recompenses et ce qui rapporte de l'XP — une seule
 * page, parcourue par la rangee « Aller a », au lieu d'onglets dans des onglets.
 */
public final class JobsMenu extends MenuController {

    public static final JobsMenu INSTANCE = new JobsMenu();

    /** Le metier affiche, par joueur : la page rouvre sur le dernier regarde. */
    private static final Map<UUID, Job> SHOWN = new ConcurrentHashMap<>();

    private JobsMenu() {
        super("jobs");
    }

    /** Ouvre la page sur un metier. */
    public void open(ServerPlayer player, Job job) {
        SHOWN.put(player.getUUID(), job);
        open(player);
    }

    /** Le dernier metier regarde, sinon le plus avance. */
    private static Job shown(ServerPlayer player) {
        Job job = SHOWN.get(player.getUUID());
        if (job != null) {
            return job;
        }
        Job best = Job.MINER;
        for (Job candidate : Job.values()) {
            if (standing(player, candidate) > standing(player, best)) {
                best = candidate;
            }
        }
        return best;
    }

    /** Ou en est un metier, renaissances comprises : cent niveaux par renaissance. */
    private static long standing(ServerPlayer player, Job job) {
        return (long) JobApi.rebirths(player, job) * JobApi.maxLevel() + JobApi.progressLevel(player, job);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.equals("rebirth")) {
            report(player, JobRebirth.rebirth(player, shown(player)), "");
            return;
        }
        if (key.startsWith("job:")) {
            try {
                SHOWN.put(player.getUUID(), Job.valueOf(key.substring(4)));
            } catch (IllegalArgumentException unknown) {
                // un nom venu du client : on l'ignore
            }
        }
    }

    @Override
    public MenuView view(ServerPlayer player) {
        Job job = shown(player);
        JobDetailView detail = JobsController.detail(player, job);
        int color = color(job);

        // Les onglets, un par metier, et les chiffres de tous les metiers.
        List<MenuView.Tab> tabs = new ArrayList<>();
        int levels = 0;
        int unlocked = 0;
        int unlockTotal = 0;
        for (Job other : Job.values()) {
            int level = JobApi.progressLevel(player, other);
            int unlockedLevel = JobApi.level(player, other);
            for (JobConfig.Unlock unlock : JobGate.unlocksOf(other)) {
                unlockTotal++;
                if (unlockedLevel >= unlock.level()) {
                    unlocked++;
                }
            }
            String rank = JobRebirth.rank(player, other);
            tabs.add(new MenuView.Tab(other.display(), "job:" + other.name(), other == job,
                    (rank.isEmpty() ? "" : rank + " ") + "NIV. " + level));
        }
        levels = JobApi.totalLevels(player);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("NIVEAUX CUMULES", Integer.toString(levels), 0xFFFFFFFF),
                new MenuView.Stat("DEBLOCAGES", unlocked + " / " + unlockTotal, 0xFF22E558));

        // La fiche, a gauche.
        int unlocksDone = done(detail.unlocks());
        int primesDone = done(detail.primes());
        boolean max = detail.level() >= detail.maxLevel();
        String legend = max ? "NIVEAU MAXIMUM|100 %"
                : MoneyFormat.exact(detail.xpInto()) + " / " + MoneyFormat.exact(detail.xpNeed()) + " XP|"
                + detail.progress() / 10 + " %";
        List<String> lines = new ArrayList<>();
        long bonus = Math.round((UpgradeApi.bonus(player, UpgradeApi.JOB_XP_BONUS) + Amulets.xpBonus(player, job)
                + JobApi.rebirthBonus(player, job)) * 100.0d);
        if (bonus > 0) {
            lines.add("#TES BONUS");
            lines.add("+" + bonus + " % d'XP sur chaque gain");
        }
        int rebirths = JobApi.rebirths(player, job);
        if (rebirths > 0) {
            lines.add("#RENAISSANCE " + rebirths);
            lines.add("+" + JobRebirth.percent(player, job) + " % d'XP et d'argent ; deblocages gardes");
        }
        MenuView.Hero hero = new MenuView.Hero(icon(job), "jobs/" + job.name().toLowerCase(Locale.ROOT),
                job.display().toUpperCase(Locale.ROOT), "NIVEAU " + detail.level()
                + (rebirths > 0 ? " \u00B7 R" + rebirths : ""), color, detail.progress(), legend,
                List.of(new MenuView.Stat("XP TOTALE", MoneyFormat.exact(detail.totalXp()), 0xFFFFFFFF),
                        new MenuView.Stat("PROCHAIN NIVEAU",
                                detail.nextMoney() > 0 ? "+" + MoneyFormat.shortForm(detail.nextMoney()) + " $" : "-",
                                0xFFFAC400),
                        new MenuView.Stat("DEBLOCAGES", unlocksDone + " / " + detail.unlocks().size(), 0xFF22E558),
                        new MenuView.Stat("PRIMES", primesDone + " / " + detail.primes().size(), 0xFFFAC400)),
                lines);

        List<MenuView.Section> sections = new ArrayList<>();
        sections.add(new MenuView.Section("RENAISSANCE" + (rebirths > 0 ? "|" + rebirths : ""), "upgrade",
                MenuView.GRID, rebirth(player, job, detail, color)));
        String unlockCount = unlocksDone + " / " + detail.unlocks().size();
        sections.add(new MenuView.Section("PROCHAINS DEBLOCAGES|" + unlockCount, "upgrade", MenuView.GRID,
                next(detail, color, JobApi.level(player, job)))
                .anchor("Deblocages|" + unlocksDone + "/" + detail.unlocks().size()));
        sections.add(new MenuView.Section("TOUS LES DEBLOCAGES", "", MenuView.ROWS,
                rows(detail.unlocks(), detail.level(), color, true)));
        sections.add(new MenuView.Section("PRIMES|" + primesDone + " / " + detail.primes().size(), "resources",
                MenuView.ROWS, rows(detail.primes(), detail.level(), 0xFFFAC400, true))
                .anchor("Primes|" + primesDone + "/" + detail.primes().size()));
        int rewardsDone = done(detail.rewards());
        sections.add(new MenuView.Section("RECOMPENSES|" + rewardsDone + " / " + detail.rewards().size(), "armor",
                MenuView.ROWS, rows(detail.rewards(), detail.level(), color, false))
                .anchor("Recompenses|" + rewardsDone + "/" + detail.rewards().size()));
        sections.addAll(xp(detail, color));

        return new MenuView(id(), "Metiers", "", stats, sections, false, tabs)
                .withHero(hero).withFlags(MenuView.ANCHORS);
    }

    /**
     * La renaissance : au maximum, la carte qui la lance (avec confirmation) ; avant, ce qu'il
     * reste a monter. Apres une premiere, une carte dit le bonus acquis.
     */
    private static List<Card> rebirth(ServerPlayer player, Job job, JobDetailView detail, int color) {
        List<Card> cards = new ArrayList<>();
        int rebirths = JobApi.rebirths(player, job);
        int next = rebirths + 1;
        long after = (long) next * JobRebirth.PERCENT;
        boolean max = detail.level() >= detail.maxLevel();
        List<String> tooltip = List.of("Deblocages gardes",
                "+" + JobRebirth.PERCENT + " % d'XP et d'argent, pour toujours");
        Card card = Card.of(new ItemStack(Items.TOTEM_OF_UNDYING), "Renaissance " + next, List.of(
                        max ? "Retour au niveau 0" : "Au niveau " + detail.maxLevel(),
                        max ? "Bonus total : +" + after + " %" : "Encore " + (detail.maxLevel() - detail.level())
                                + " niveaux"))
                .accent(0xFFFAC400).tooltip(tooltip);
        cards.add(max ? card.action("rebirth|renaitre").confirm("!Faire renaitre le " + job.display().toLowerCase(
                        Locale.ROOT) + " ?|Retour au niveau 0")
                : card.state(MenuView.DISABLED).progress(detail.level(), detail.maxLevel()));
        if (rebirths > 0) {
            cards.add(Card.of(new ItemStack(Items.NETHER_STAR), "Renaissance " + rebirths, List.of(
                            "+" + JobRebirth.percent(player, job) + " % d'XP et d'argent", "Deblocages gardes"))
                    .accent(color).badge("R" + rebirths).state(MenuView.DONE));
        }
        return cards;
    }

    /**
     * Les prochains deblocages : le dernier obtenu, puis les deux suivants, chacun avec
     * sa barre vers son niveau (le niveau de deblocage, garde par les renaissances). Au niveau
     * maximum, les trois derniers.
     */
    private static List<Card> next(JobDetailView detail, int color, int unlockedLevel) {
        List<JobDetailView.Row> sorted = new ArrayList<>(detail.unlocks());
        sorted.sort(Comparator.comparingInt(JobDetailView.Row::level));
        int firstLocked = sorted.size();
        for (int index = 0; index < sorted.size(); index++) {
            if (sorted.get(index).state() != JobDetailView.ACQUIRED) {
                firstLocked = index;
                break;
            }
        }
        int start = Math.max(0, Math.min(firstLocked - 1, sorted.size() - 3));
        List<Card> cards = new ArrayList<>();
        for (int index = start; index < Math.min(sorted.size(), start + 3); index++) {
            JobDetailView.Row row = sorted.get(index);
            boolean acquired = row.state() == JobDetailView.ACQUIRED;
            Card card = Card.of(row.icon(), row.title(), List.of("Niveau " + row.level())).accent(color)
                    .tooltip(without(row.lines(), "Niveau " + row.level()));
            if (acquired) {
                card = card.state(MenuView.DONE);
            } else {
                card = card.state(MenuView.DISABLED).badge(Integer.toString(row.level()))
                        .progress(unlockedLevel, row.level());
            }
            cards.add(card);
        }
        return cards;
    }

    /** Une liste de lignes (deblocages, primes, recompenses) : acquis en vert, le reste verrouille. */
    private static List<Card> rows(List<JobDetailView.Row> rows, int level, int color, boolean unlocks) {
        List<Card> cards = new ArrayList<>();
        for (JobDetailView.Row row : rows) {
            boolean acquired = row.state() == JobDetailView.ACQUIRED;
            String line;
            if (acquired) {
                line = row.detail();
            } else if (row.state() == JobDetailView.MISSING) {
                line = "Pas encore dans le mod";
            } else if (unlocks) {
                line = "Encore " + Math.max(1, row.level() - level) + " niveau" + (row.level() - level > 1 ? "x" : "");
            } else {
                line = row.detail();
            }
            cards.add(Card.of(row.icon(), row.title(), List.of(line)).accent(color)
                    .badge("NIV. " + row.level())
                    .state(acquired ? MenuView.DONE : MenuView.DISABLED)
                    .tooltip(without(row.lines(), line)));
        }
        return cards;
    }

    /** Les lignes de bulle, sans celle deja ecrite sur la carte. */
    private static List<String> without(List<String> lines, String shown) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (!line.equals(shown) && !line.equals("Debloque")) {
                out.add(line);
            }
        }
        return out;
    }

    /**
     * Ce qui rapporte de l'XP : une section par titre du guide (« FONTE », « BLOCS
     * MINES »...), la premiere portant l'ancre et le bonus du joueur.
     */
    private static List<MenuView.Section> xp(JobDetailView detail, int color) {
        List<MenuView.Section> sections = new ArrayList<>();
        int open = 0;
        int total = 0;
        for (JobDetailView.Row row : detail.sources()) {
            if (row.state() != JobDetailView.HEADER) {
                total++;
                if (row.state() == JobDetailView.ACQUIRED) {
                    open++;
                }
            }
        }
        String title = null;
        List<Card> cards = new ArrayList<>();
        int sectionOpen = 0;
        boolean first = true;
        for (JobDetailView.Row row : detail.sources()) {
            if (row.state() == JobDetailView.HEADER) {
                if (title != null && !cards.isEmpty()) {
                    sections.add(xpSection(title, cards, sectionOpen, first, open, total));
                    first = false;
                }
                title = row.title() + (row.detail().isEmpty() ? "" : " · " + row.detail());
                cards = new ArrayList<>();
                sectionOpen = 0;
                continue;
            }
            boolean acquired = row.state() == JobDetailView.ACQUIRED;
            if (acquired) {
                sectionOpen++;
            }
            cards.add(Card.of(row.icon(), row.title(), List.of(row.detail())).accent(color)
                    .badge("NIV. " + row.level())
                    .state(acquired ? MenuView.DONE : MenuView.DISABLED)
                    .tooltip(without(row.lines(), row.detail())));
        }
        if (title != null && !cards.isEmpty()) {
            sections.add(xpSection(title, cards, sectionOpen, first, open, total));
        }
        return sections;
    }

    private static MenuView.Section xpSection(String title, List<Card> cards, int sectionOpen, boolean first,
                                              int open, int total) {
        MenuView.Section section = new MenuView.Section((first ? "XP PAR ACTION · " : "") + title + "|"
                + sectionOpen + " / " + cards.size(), first ? "melt" : "", MenuView.ROWS, cards);
        return first ? section.anchor("XP par action|" + open + "/" + total) : section;
    }

    private static int done(List<JobDetailView.Row> rows) {
        int count = 0;
        for (JobDetailView.Row row : rows) {
            if (row.state() == JobDetailView.ACQUIRED) {
                count++;
            }
        }
        return count;
    }

    static int color(Job job) {
        return switch (job) {
            case MINER -> 0xFFEF3926;
            case FARMER -> 0xFFFAC400;
            case HUNTER -> 0xFF2F5BEA;
            case ALCHEMIST -> 0xFFEC4FB5;
        };
    }

    private static ItemStack icon(Job job) {
        return new ItemStack(switch (job) {
            case MINER -> Items.IRON_PICKAXE;
            case FARMER -> Items.WHEAT;
            case HUNTER -> Items.BOW;
            case ALCHEMIST -> Items.BLAZE_POWDER;
        });
    }
}
