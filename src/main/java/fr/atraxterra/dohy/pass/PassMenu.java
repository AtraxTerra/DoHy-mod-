package fr.atraxterra.dohy.pass;

import fr.atraxterra.dohy.contract.ContractDisplay;
import fr.atraxterra.dohy.contract.ContractType;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran du pass : le niveau et l'XP, les 35 recompenses (a recuperer d'un
 * clic), la relique du mois, et les quetes du jour et du mois.
 */
public final class PassMenu extends MenuController {

    public static final PassMenu INSTANCE = new PassMenu();

    private static final int GOLD = 0xFFFAC400;

    private PassMenu() {
        super("pass");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        PassApi.rollIfDue(player);
        PassConfig.Config config = PassConfig.config();
        PassData data = PassApi.data(player);
        int season = data.isEmpty() ? PassSeason.current() : data.season();
        PassConfig.Theme theme = PassApi.theme(season);
        int accent = theme == null ? 0xFFEC4FB5 : theme.color();
        int level = PassApi.level(data);
        long into = PassApi.xpIntoLevel(data);
        Relic relic = PassApi.relic(season);

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("NIVEAU", level + " / " + config.levels(), level >= config.levels() ? 0xFF22E558
                        : 0xFFFFFFFF),
                new MenuView.Stat("XP", into < 0 ? "MAX" : MoneyFormat.exact(into) + " / "
                        + MoneyFormat.exact(config.xpPerLevel()), 0xFF55E5FF),
                new MenuView.Stat("FIN", PassSeason.remaining(season).toUpperCase(), 0xFFFFFFFF));

        List<MenuView.Section> sections = new ArrayList<>();
        if (config.themes().isEmpty()) {
            sections.add(new MenuView.Section("", "", MenuView.TEXT, List.of(Card.of(ItemStack.EMPTY,
                    "Pass indisponible", List.of("Le fichier du pass n'a pas pu etre lu : voir le journal du serveur."))
                    .accent(0xFFEF3826))));
            return new MenuView(id(), "Pass", "", stats, sections, true);
        }

        // La relique, et le bouton de tout recuperer.
        List<Card> top = new ArrayList<>();
        top.add(Card.of(DoHyPass.relic(relic, season), relic.title(), List.of("Niveau " + config.levels()
                        + " · exclusive", "Main secondaire : " + relic.effectName() + " I"))
                .accent(accent).state(data.claimed(config.levels()) ? MenuView.DONE : MenuView.NORMAL)
                .badge(level >= config.levels() ? "OBTENUE" : ""));
        int claimable = PassApi.claimable(player);
        top.add(Card.of(new ItemStack(Items.CHEST), "Tout recuperer", List.of(
                        claimable > 0 ? claimable + " recompense(s) en attente" : "Rien en attente",
                        "Monte de niveau avec les quetes"))
                .accent(GOLD).state(claimable > 0 ? MenuView.NORMAL : MenuView.DISABLED)
                .badge(claimable > 0 ? "+" + claimable : "").action(claimable > 0 ? "all|tout recuperer" : ""));
        sections.add(new MenuView.Section(theme == null ? "CE MOIS" : theme.name().toUpperCase(), "upgrade",
                MenuView.GRID, top));

        // Les 35 niveaux.
        List<Card> tiles = new ArrayList<>();
        for (int at = 1; at <= config.levels(); at++) {
            PassConfig.Reward reward = config.reward(at);
            if (reward == null) {
                continue;
            }
            boolean claimed = data.claimed(at);
            boolean reached = at <= level;
            List<String> lines = new ArrayList<>(PassApi.describe(reward, season));
            lines.add(claimed ? "Recuperee" : reached ? "A recuperer" : "Niveau " + at + " a atteindre");
            Card tile = Card.of(PassApi.icon(reward, season), "Niveau " + at, lines).badge(String.valueOf(at));
            if (claimed) {
                tile = tile.state(MenuView.DONE);
            } else if (reached) {
                tile = tile.accent(GOLD).action("claim:" + at + "|recuperer");
            } else {
                tile = tile.state(MenuView.DISABLED);
            }
            tiles.add(tile);
        }
        sections.add(new MenuView.Section("RECOMPENSES", "resources", MenuView.TILES, tiles));

        sections.add(new MenuView.Section("QUETES DU JOUR · NOUVELLES DANS " + PassSeason.untilTomorrow().toUpperCase(),
                "craft", MenuView.ROWS, quests(data.daily(), accent)));
        sections.add(new MenuView.Section("QUETES DU MOIS", "armor", MenuView.ROWS, quests(data.monthly(), accent)));

        String subtitle = (theme == null ? "" : theme.name() + (theme.tagline().isEmpty() ? "" : " · " + theme.tagline()));
        return new MenuView(id(), "Pass " + PassSeason.ofMonth(season), subtitle, stats, sections, true);
    }

    private static List<Card> quests(List<PassQuest> quests, int accent) {
        List<Card> cards = new ArrayList<>();
        for (PassQuest quest : quests) {
            boolean money = quest.kind() == ContractType.SELL_VALUE;
            String count = MoneyFormat.exact(quest.progress()) + " / " + MoneyFormat.exact(quest.amount())
                    + (money ? " $" : quest.kind() == ContractType.PLAYTIME ? " min" : "");
            cards.add(Card.of(new ItemStack(ContractDisplay.icon(quest.asContract())),
                            ContractDisplay.label(quest.asContract()),
                            List.of(quest.done() ? count + " · terminee" : count))
                    .accent(accent).badge("+" + MoneyFormat.exact(quest.xp()) + " XP")
                    .progress(quest.progress(), quest.amount())
                    .state(quest.done() ? MenuView.DONE : MenuView.NORMAL));
        }
        if (cards.isEmpty()) {
            cards.add(Card.of(new ItemStack(Items.PAPER), "Aucune quete", List.of("Elles arrivent a la connexion"))
                    .accent(0xFF55545C));
        }
        return cards;
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("claim:")) {
            try {
                String refusal = PassApi.claim(player, Integer.parseInt(key.substring(6)));
                if (refusal == null) {
                    success(player);
                } else {
                    report(player, refusal, null);
                }
            } catch (NumberFormatException ignored) {
                failure(player);
            }
        } else if ("all".equals(key)) {
            int count = PassApi.claimAll(player);
            if (count > 0) {
                report(player, null, count + " recompense(s) recuperee(s).");
            } else {
                failure(player);
            }
        }
    }
}
