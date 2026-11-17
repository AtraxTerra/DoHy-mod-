package fr.atraxterra.dohy.nuclear.path;

import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le menu du parcours NTM : a gauche ou en est le joueur, au centre le mode
 * d'emploi de l'etape et son objectif (un clic le valide quand il est atteint),
 * dessous les chapitres, etape par etape. Un clic sur une etape en affiche le
 * mode d'emploi, meme faite ou a venir : le parcours sert aussi d'aide-memoire.
 */
public final class NtmPathMenu extends MenuController {

    public static final NtmPathMenu INSTANCE = new NtmPathMenu();

    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFAC400;
    private static final int MUTED = 0xFF8C8B93;

    /** L'etape affichee par joueur, si ce n'est pas celle en cours. */
    private static final Map<UUID, String> VIEWING = new ConcurrentHashMap<>();

    private NtmPathMenu() {
        super("parcours_ntm");
    }

    /** Ouvre le menu sur l'etape en cours. */
    public static void openFor(ServerPlayer player) {
        VIEWING.remove(player.getUUID());
        INSTANCE.open(player);
    }

    static void forget(UUID player) {
        VIEWING.remove(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        if (!NtmPath.installed()) {
            return message("NTM NEXT absent", List.of(
                    "Le parcours guide les premieres machines de NTM NEXT, de l'enclume au premier reacteur.",
                    "Ce serveur n'a pas NTM NEXT : il n'y a rien a faire ici."));
        }
        List<NtmPath.Located> steps = NtmPath.steps();
        if (steps.isEmpty()) {
            return message("Parcours illisible", List.of(
                    "Le fichier du parcours n'a pas pu etre lu : voir le journal du serveur."));
        }
        NtmPathData data = NtmPath.data(player);
        NtmPath.Located current = NtmPath.current(player);
        NtmPath.Located shown = NtmPath.byId(VIEWING.getOrDefault(player.getUUID(), ""));
        if (shown == null) {
            shown = current;
        }
        int done = NtmPath.doneCount(player);
        int total = steps.size();

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("ETAPES", done + " / " + total, GREEN),
                new MenuView.Stat("CHAPITRE", current == null ? "Termine" : current.chapter().name(), 0xFFFFFFFF),
                new MenuView.Stat("GAGNE", MoneyFormat.shortForm(data.earned()) + " $", GOLD));

        List<MenuView.Section> sections = new ArrayList<>();
        if (shown == null) {
            sections.add(new MenuView.Section("PARCOURS TERMINE", "upgrade", MenuView.TEXT, List.of(
                    Card.of(ItemStack.EMPTY, "De l'enclume a l'uranium enrichi", List.of(
                            "Industrie, petrole, premier reacteur, enrichissement : tu as tout ce qui ouvre la "
                                    + "suite de NTM NEXT.",
                            "- La suite : les reacteurs plus gros (RBMK), la fusion, l'espace. Les fiches NTM de "
                                    + "l'encyclopedie donnent les bases.",
                            "- Ce qui manque s'achete au shop, onglets NTM NEXT (pieces) et Machines NTM.",
                            "! Un clic sur une etape ci-dessous en rouvre le mode d'emploi."))
                            .accent(GREEN))));
        } else {
            sections.add(new MenuView.Section("ETAPE " + shown.number() + " · " + shown.chapter().name()
                    .toUpperCase(Locale.ROOT), "craft", MenuView.TEXT, List.of(
                    Card.of(ItemStack.EMPTY, shown.step().title(), shown.step().text())
                            .accent(shown.chapter().accent()))));
            sections.add(new MenuView.Section("OBJECTIF", "upgrade", MenuView.ROWS,
                    objective(player, data, shown, current)));
        }
        for (NtmPath.Chapter chapter : NtmPath.chapters()) {
            List<Card> cards = new ArrayList<>();
            int chapterDone = 0;
            for (NtmPath.Step step : chapter.steps()) {
                NtmPath.Located located = NtmPath.byId(step.id());
                if (located == null) {
                    continue;
                }
                if (data.done(step.id())) {
                    chapterDone++;
                }
                cards.add(stepCard(data, located, current, shown));
            }
            sections.add(new MenuView.Section(chapter.name().toUpperCase(Locale.ROOT) + "|" + chapterDone + " / "
                    + chapter.steps().size(), "resources", MenuView.GRID, cards));
        }
        return new MenuView(id(), "Parcours NTM", current == null ? "Termine"
                : "Etape " + current.number() + " sur " + total, stats, sections, true)
                .withHero(hero(data, current, shown, done, total));
    }

    /** Le panneau de gauche : l'etape affichee, la barre du parcours, les chapitres. */
    private static MenuView.Hero hero(NtmPathData data, NtmPath.Located current, NtmPath.Located shown, int done,
                                      int total) {
        int accent = shown == null ? GREEN : shown.chapter().accent();
        ItemStack icon = shown == null ? new ItemStack(Items.NETHER_STAR) : shown.step().iconStack();
        String title = shown == null ? "TERMINE" : "ETAPE " + shown.number();
        String badge = shown == null ? "BRAVO" : shown.chapter().name().toUpperCase(Locale.ROOT);
        String legend = done + " / " + total + " ETAPES|" + (total <= 0 ? 100 : done * 100 / total) + " %";
        List<MenuView.Stat> stats = new ArrayList<>();
        stats.add(new MenuView.Stat("GAGNE", MoneyFormat.shortForm(data.earned()) + " $", GOLD));
        stats.add(new MenuView.Stat("RESTE", total == done ? "Aucune"
                : (total - done) + (total - done > 1 ? " etapes" : " etape"), 0xFFFFFFFF));
        if (shown != null) {
            stats.add(new MenuView.Stat("RECOMPENSE", MoneyFormat.shortForm(shown.step().reward().money()) + " $",
                    GOLD));
            stats.add(new MenuView.Stat("STATUT", data.done(shown.step().id()) ? "Validee"
                    : shown == current ? "En cours" : "A venir", data.done(shown.step().id()) ? GREEN : 0xFFFFFFFF));
        }
        List<String> lines = new ArrayList<>();
        lines.add("#CHAPITRES");
        for (NtmPath.Chapter chapter : NtmPath.chapters()) {
            int chapterDone = 0;
            for (NtmPath.Step step : chapter.steps()) {
                if (data.done(step.id())) {
                    chapterDone++;
                }
            }
            lines.add(chapter.name() + " · " + chapterDone + " / " + chapter.steps().size());
        }
        lines.add("#COMMANDE");
        lines.add("/parcours");
        return new MenuView.Hero(icon, "", title, badge, accent, total <= 0 ? 1000 : done * 1000 / total, legend,
                stats, lines);
    }

    /** L'objectif de l'etape affichee : ce qui manque, la recompense, et le bouton quand c'est pret. */
    private static List<Card> objective(ServerPlayer player, NtmPathData data, NtmPath.Located shown,
                                        NtmPath.Located current) {
        NtmPath.Step step = shown.step();
        NtmPath.Goal goal = step.goal();
        List<Card> cards = new ArrayList<>();
        ItemStack icon = goal.items().isEmpty() ? step.iconStack() : NtmPath.stack(goal.items().get(0), 1);
        String reward = MenuView.text(Component.literal("Recompense : ").append(NtmPath.rewardText(step.reward())));
        List<String> tooltip = new ArrayList<>();
        boolean several = goal.items().size() > 1;
        if (goal.near()) {
            tooltip.add((several ? "Une de ces machines" : "La machine") + ", posee a moins de " + NtmPath.radius()
                    + " blocs :");
        } else if (goal.all()) {
            tooltip.add("Un de chaque, dans ton sac :");
        } else {
            tooltip.add(several ? "Au choix, " + goal.count() + " en tout, dans ton sac :"
                    : goal.count() + " dans ton sac :");
        }
        for (Identifier id : goal.items()) {
            tooltip.add(MenuView.text(Component.literal("- ").append(Gui.displayName(id))));
        }

        if (data.done(step.id())) {
            cards.add(Card.of(icon, goal.label(), List.of("Etape validee", reward))
                    .accent(GREEN).badge("FAITE").state(MenuView.DONE).progress(1, 1).tooltip(tooltip));
        } else if (shown != current) {
            cards.add(Card.of(icon, goal.label(), List.of("Valide d'abord l'etape " + current.number() + " : "
                            + current.step().title(), reward))
                    .accent(MUTED).badge("A VENIR").state(MenuView.DISABLED).tooltip(tooltip));
        } else {
            NtmPath.Status status = NtmPath.status(player, step);
            Card card = Card.of(icon, goal.label(), List.of(status.met() ? status.line() + ", c'est pret !"
                            : status.line(), reward))
                    .accent(status.met() ? GREEN : shown.chapter().accent())
                    .badge(status.met() ? "PRET" : MoneyFormat.shortForm(step.reward().money()) + " $")
                    .progress(status.have(), status.need()).tooltip(tooltip);
            cards.add(status.met() ? card.action("valider|valider l'etape") : card);
        }
        if (shown != current && current != null) {
            cards.add(Card.of(current.step().iconStack(), "Etape en cours", List.of(
                            "Etape " + current.number() + " : " + current.step().title()))
                    .accent(current.chapter().accent()).action("courante|revenir a l'etape en cours"));
        }
        return cards;
    }

    private static Card stepCard(NtmPathData data, NtmPath.Located located, NtmPath.Located current,
                                 NtmPath.Located shown) {
        NtmPath.Step step = located.step();
        boolean done = data.done(step.id());
        boolean now = located == current;
        String money = MoneyFormat.shortForm(step.reward().money()) + " $"
                + (step.reward().items().isEmpty() ? "" : " + objets");
        Card card = Card.of(step.iconStack(), step.title(), List.of(step.goal().label(), money))
                .accent(done ? GREEN : now ? located.chapter().accent() : 0xFF3A3C42)
                .action("voir:" + step.id() + "|voir l'etape");
        if (done) {
            card = card.state(MenuView.DONE).badge(located == shown ? "VUE" : "");
        } else if (now) {
            // Pas de pastille : elle couperait le titre. L'etape en cours le dit dans sa ligne.
            card = Card.of(step.iconStack(), step.title(), List.of(step.goal().label(), "En cours · " + money))
                    .accent(located.chapter().accent()).action("voir:" + step.id() + "|voir l'etape");
        } else {
            card = card.state(MenuView.DISABLED).badge(located == shown ? "VUE" : String.valueOf(located.number()));
        }
        return card;
    }

    /**
     * La carte du parcours dans l'ecran des contrats : l'etape en cours, ce qui
     * manque, et un clic qui ouvre le parcours. {@code null} sans NTM NEXT.
     */
    public static Card contractCard(ServerPlayer player) {
        if (!NtmPath.installed() || NtmPath.steps().isEmpty()) {
            return null;
        }
        int total = NtmPath.steps().size();
        NtmPath.Located current = NtmPath.current(player);
        if (current == null) {
            return Card.of(new ItemStack(Items.NETHER_STAR), "Parcours NTM termine", List.of(
                            total + " / " + total + " etapes",
                            MoneyFormat.exact(NtmPath.data(player).earned()) + " $ gagnes"))
                    .accent(GREEN).state(MenuView.DONE).progress(1, 1).action("parcours|ouvrir le parcours");
        }
        NtmPath.Status status = NtmPath.status(player, current.step());
        return Card.of(current.step().iconStack(), "Etape " + current.number() + " / " + total + " : "
                        + current.step().title(), List.of(
                        current.step().goal().label() + " · " + status.line(),
                        status.met() ? "Prete : un clic, et valide-la" : current.chapter().name()))
                .accent(status.met() ? GREEN : current.chapter().accent())
                .badge(status.met() ? "PRET" : MoneyFormat.shortForm(current.step().reward().money()) + " $")
                .progress(status.have(), status.need())
                .action("parcours|ouvrir le parcours");
    }

    /** Le parcours est un contrat de progression : l'hexagone ramene aux contrats. */
    @Override
    protected void back(ServerPlayer player) {
        VIEWING.remove(player.getUUID());
        fr.atraxterra.dohy.ui.menu.ContractsController.INSTANCE.open(player);
    }

    private MenuView message(String title, List<String> lines) {
        return new MenuView(id(), "Parcours NTM", "", List.of(), List.of(
                new MenuView.Section("", "", MenuView.TEXT, List.of(
                        Card.of(ItemStack.EMPTY, title, lines).accent(0xFFEF3826)))), true);
    }

    // -- Les clics ------------------------------------------------------------------------

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if ("valider".equals(key)) {
            String refusal = NtmPath.claim(player);
            if (refusal == null) {
                VIEWING.remove(player.getUUID());
                success(player);
            } else {
                report(player, refusal, "");
            }
        } else if ("courante".equals(key)) {
            VIEWING.remove(player.getUUID());
        } else if (key.startsWith("voir:")) {
            String id = key.substring(5);
            NtmPath.Located current = NtmPath.current(player);
            if (NtmPath.byId(id) == null || current != null && current.step().id().equals(id)) {
                VIEWING.remove(player.getUUID());
            } else {
                VIEWING.put(player.getUUID(), id);
            }
        }
    }
}
