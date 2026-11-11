package fr.atraxterra.dohy.palamod.achievement;

import fr.atraxterra.dohy.palamod.achievement.Achievement.Category;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ecran des succes ({@code /achievements} sur Paladium, ici {@code /succes}) :
 * les six categories, puis les succes de celle choisie, avec leurs etapes en
 * infobulle.
 */
public final class AchievementMenu extends MenuController {

    public static final AchievementMenu INSTANCE = new AchievementMenu();

    private static final int GOLD = 0xFFFAC400;
    private static final int GREEN = 0xFF22E558;
    private static final int GREY = 0xFF55545C;
    private static final int[] COLORS = {0xFF2F5BEA, 0xFFFAC400, 0xFF22E558, 0xFFEF3826, 0xFFEC4FB5, 0xFF2BC4C4};
    /** Des objets et non des piles : pas d'ItemStack a l'initialisation de la classe. */
    private static final net.minecraft.world.item.Item[] ICONS = {Items.WOODEN_PICKAXE, Items.GOLD_INGOT,
            Items.IRON_PICKAXE, Items.TNT, Items.SHIELD, Items.NETHER_STAR};

    /** La categorie ouverte par joueur ; absente : la vue d'ensemble. */
    private static final Map<UUID, Category> OPEN = new ConcurrentHashMap<>();

    private AchievementMenu() {
        super("achievements");
    }

    /** Ouvre la vue d'ensemble. */
    @Override
    public void open(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        Achievements.check(player);
        super.open(player);
    }

    @Override
    protected void back(ServerPlayer player) {
        if (OPEN.remove(player.getUUID()) != null) {
            super.open(player);
            return;
        }
        super.back(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        Category category = OPEN.get(player.getUUID());
        return category == null ? overview(player) : category(player, category);
    }

    private MenuView overview(ServerPlayer player) {
        AchievementData data = Achievements.data(player);
        List<Card> cards = new ArrayList<>();
        int done = 0;
        int total = 0;
        long earned = 0L;
        for (Category category : Category.values()) {
            List<Achievement> list = AchievementCatalogue.of(category);
            int ok = 0;
            for (Achievement achievement : list) {
                if (data.isDone(achievement.id())) {
                    ok++;
                    earned += achievement.reward();
                }
            }
            done += ok;
            total += list.size();
            cards.add(Card.of(new ItemStack(ICONS[category.ordinal()]), category.label(), List.of(
                            ok + " / " + list.size() + " succes", ok == list.size() ? "Categorie terminee" : "Voir le detail"))
                    .accent(COLORS[category.ordinal()]).progress(ok, list.size()).action("cat" + category.ordinal())
                    .state(ok == list.size() && !list.isEmpty() ? MenuView.DONE : MenuView.NORMAL));
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("SUCCES", done + " / " + total, 0xFFFFFFFF),
                new MenuView.Stat("GAGNE", MoneyFormat.shortForm(earned) + " $", GOLD),
                new MenuView.Stat("AVANCEMENT", percent(done, total), GREEN));
        return new MenuView(id(), "Succes", "Les succes de PalaMod et de PalaJobs", stats,
                List.of(new MenuView.Section("CATEGORIES", "upgrade", MenuView.GRID, cards)), true);
    }

    private MenuView category(ServerPlayer player, Category category) {
        AchievementData data = Achievements.data(player);
        List<Card> pending = new ArrayList<>();
        List<Card> finished = new ArrayList<>();
        int ok = 0;
        List<Achievement> list = AchievementCatalogue.of(category);
        for (Achievement achievement : list) {
            boolean done = data.isDone(achievement.id());
            List<String> tooltip = new ArrayList<>();
            long progressDone = 0L;
            long progressGoal = 0L;
            for (Achievement.Step step : achievement.steps()) {
                long value = step.of(player);
                progressDone += Math.min(value, step.goal()) * 1000L / Math.max(1L, step.goal());
                progressGoal += 1000L;
                tooltip.add((value >= step.goal() ? "[x] " : "[ ] ") + step.label()
                        + (step.goal() > 1 ? " : " + MoneyFormat.exact(value) + " / " + MoneyFormat.exact(step.goal()) : ""));
            }
            tooltip.add("Recompense : " + MoneyFormat.exact(achievement.reward()) + " $");
            String line = achievement.steps().size() > 1
                    ? "Etapes " + achievement.stepsDone(player) + " / " + achievement.steps().size()
                    : percent(progressDone, progressGoal);
            Card card = Card.of(achievement.icon().get(), achievement.name(), List.of(achievement.description(),
                            done ? "Valide" : line))
                    .badge(MoneyFormat.shortForm(achievement.reward()) + " $")
                    .tooltip(tooltip);
            if (done) {
                ok++;
                finished.add(card.state(MenuView.DONE).accent(GREEN));
            } else {
                pending.add(card.accent(GREY).progress(progressDone, progressGoal));
            }
        }
        List<MenuView.Section> sections = new ArrayList<>();
        if (!pending.isEmpty()) {
            sections.add(new MenuView.Section("A FAIRE", "upgrade", MenuView.GRID, pending));
        }
        if (!finished.isEmpty()) {
            sections.add(new MenuView.Section("VALIDES", "resources", MenuView.GRID, finished));
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("SUCCES", ok + " / " + list.size(), 0xFFFFFFFF),
                new MenuView.Stat("AVANCEMENT", percent(ok, list.size()), GREEN));
        return new MenuView(id(), "Succes · " + category.label(), "Passe la souris sur un succes pour ses etapes",
                stats, sections, true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("cat")) {
            try {
                int index = Integer.parseInt(key.substring(3));
                if (index >= 0 && index < Category.values().length) {
                    OPEN.put(player.getUUID(), Category.values()[index]);
                }
            } catch (NumberFormatException e) {
                // Une cle inconnue : rien.
            }
        }
    }
}
