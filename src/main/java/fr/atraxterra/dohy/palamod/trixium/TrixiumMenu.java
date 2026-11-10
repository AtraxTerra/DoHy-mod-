package fr.atraxterra.dohy.palamod.trixium;

import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran du concours Trixium ({@code UITrixium} et {@code UITrixiumRanking}) :
 * le depot, la barre des paliers, le classement des joueurs et celui des
 * factions.
 *
 * <p>Paladium deposait par une fenetre a un emplacement ; ici, un clic depose
 * tout le trixium du sac, lingots et blocs.
 */
public final class TrixiumMenu extends MenuController {

    public static final TrixiumMenu INSTANCE = new TrixiumMenu();

    private static final int TOP = 10;
    private static final int GOLD = 0xFFFAC400;
    private static final int TEAL = 0xFF2BC4C4;
    private static final int GREEN = 0xFF22E558;
    private static final int GREY = 0xFF55545C;

    private TrixiumMenu() {
        super("trixium");
    }

    @Override
    public void open(ServerPlayer player) {
        TrixiumContest.catchUp(player);
        super.open(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        TrixiumData data = TrixiumContest.data(player);
        int season = data.season();
        int rank = TrixiumContest.rank(player);
        long carried = TrixiumContest.carried(player);
        List<MenuView.Section> sections = new ArrayList<>();

        // -- Le concours ------------------------------------------------------------
        List<Card> contest = new ArrayList<>();
        Card deposit = Card.of(new ItemStack(PalaItems.TRIXIUM.get()), "Deposer mon trixium", List.of(
                        "Dans ton sac : " + MoneyFormat.exact(carried), "Lingot 1, bloc 9"))
                .tooltip(List.of("Tout le trixium du sac part au concours.",
                        "Depose, il ne se vend plus."));
        contest.add(carried > 0 ? deposit.accent(TEAL).action("deposit").badge("+" + MoneyFormat.shortForm(carried))
                : deposit.state(MenuView.DISABLED));
        List<String> tiers = new ArrayList<>();
        tiers.add("Prime de fin de saison, selon le rang :");
        for (TrixiumContest.Tier tier : TrixiumContest.TIERS) {
            tiers.add(tier.ranks() + " : " + MoneyFormat.exact(tier.money()) + " $");
        }
        tiers.add("Il faut avoir depose au moins un trixium.");
        long prize = data.deposited() > 0 ? TrixiumContest.prize(rank) : 0L;
        contest.add(Card.of(new ItemStack(Items.CLOCK), "Saison de " + TrixiumSeason.label(season), List.of(
                        "Fin dans " + TrixiumSeason.remaining(season),
                        prize > 0 ? "Prime actuelle : " + MoneyFormat.exact(prize) + " $" : "Pas encore classe"))
                .accent(GOLD).tooltip(tiers));
        if (data.lastSeason() > 0) {
            contest.add(Card.of(new ItemStack(Items.FILLED_MAP), "Saison de " + TrixiumSeason.label(data.lastSeason()),
                    List.of(data.lastRank() > 0 ? "Rang final : " + ordinal(data.lastRank()) : "Aucun depot",
                            "Depose : " + MoneyFormat.exact(data.lastDeposit()))).accent(GREY));
        }
        sections.add(new MenuView.Section("CONCOURS", "upgrade", MenuView.GRID, contest));

        // -- La barre des paliers ------------------------------------------------
        List<Card> bar = new ArrayList<>();
        for (int i = 0; i < TrixiumContest.MILESTONES.size(); i++) {
            TrixiumContest.Milestone milestone = TrixiumContest.MILESTONES.get(i);
            boolean reached = data.deposited() >= milestone.threshold();
            boolean claimed = data.hasClaimed(i);
            Card card = Card.of(milestone.icon(), "Palier " + MoneyFormat.exact(milestone.threshold()), List.of(
                            milestone.reward(), Math.min(data.deposited(), milestone.threshold()) + " / "
                                    + milestone.threshold()))
                    .progress(Math.min(data.deposited(), milestone.threshold()), milestone.threshold());
            if (milestone.big()) {
                card = card.badge("EXCLUSIF");
            }
            if (claimed) {
                card = card.state(MenuView.DONE).accent(GREEN);
            } else if (reached) {
                card = card.accent(GOLD).action("claim" + i).badge("RECUPERER");
            } else {
                card = card.accent(milestone.big() ? TEAL : GREY);
            }
            bar.add(card);
        }
        sections.add(new MenuView.Section("PALIERS", "resources", MenuView.GRID, bar));

        // -- Classement des joueurs ---------------------------------------------
        List<TrixiumContest.Entry> players = TrixiumContest.players(player);
        List<Card> ranking = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            TrixiumContest.Entry entry = players.get(i);
            boolean shown = i < TOP || entry.player();
            if (!shown) {
                continue;
            }
            int place = i + 1;
            long reward = TrixiumContest.prize(place);
            Card row = Card.of(new ItemStack(entry.player() ? Items.PLAYER_HEAD : place <= 3 ? Items.GOLD_BLOCK
                            : Items.IRON_BLOCK), place + ". " + entry.name(), List.of(
                            entry.faction().isEmpty() ? "Sans faction" : entry.faction(),
                            MoneyFormat.exact(entry.total()) + " trixium"))
                    .badge(reward > 0 ? MoneyFormat.shortForm(reward) + " $" : "")
                    .accent(entry.player() ? GREEN : place <= 3 ? GOLD : GREY);
            ranking.add(row);
        }
        sections.add(new MenuView.Section("CLASSEMENT DES JOUEURS", "upgrade", MenuView.ROWS, ranking));

        // -- Classement des factions, pour information --------------------------
        List<TrixiumContest.Entry> factions = TrixiumContest.factions(player);
        List<Card> factionRows = new ArrayList<>();
        for (int i = 0; i < factions.size(); i++) {
            TrixiumContest.Entry entry = factions.get(i);
            if (i >= 5 && !entry.player()) {
                continue;
            }
            factionRows.add(Card.of(new ItemStack(Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE)), (i + 1) + ". " + entry.name(), List.of(
                            entry.player() ? "Ta faction" : "Faction fictive",
                            MoneyFormat.exact(entry.total()) + " trixium"))
                    .accent(entry.player() ? GREEN : GREY));
        }
        sections.add(new MenuView.Section("FACTIONS (POUR INFORMATION)", "resources", MenuView.ROWS, factionRows));

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("DEPOSE", MoneyFormat.shortForm(data.deposited()), 0xFFFFFFFF),
                new MenuView.Stat("RANG", data.deposited() > 0 ? rank + " / " + (TrixiumRivals.COUNT + 1) : "-", GOLD),
                new MenuView.Stat("FIN", TrixiumSeason.remaining(season), TEAL));
        return new MenuView(id(), "Concours Trixium", "Saison de " + TrixiumSeason.label(season)
                + " · un mois reel", stats, sections, true);
    }

    static String ordinal(int rank) {
        return rank + (rank == 1 ? "er" : "e");
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.equals("deposit")) {
            long deposited = TrixiumContest.deposit(player);
            report(player, deposited > 0 ? null : "Tu n'as pas de trixium sur toi.",
                    MoneyFormat.exact(deposited) + " trixium deposes.");
            return;
        }
        if (key.startsWith("claim")) {
            int index;
            try {
                index = Integer.parseInt(key.substring(5));
            } catch (NumberFormatException e) {
                return;
            }
            report(player, TrixiumContest.claim(player, index), "Palier recupere : "
                    + TrixiumContest.MILESTONES.get(Math.max(0, Math.min(index, TrixiumContest.MILESTONES.size() - 1)))
                    .reward() + ".");
        }
    }
}
