package fr.atraxterra.dohy.faction.absence;

import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.market.WorldMarketMenu;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran du rapport d'absence : en haut, la duree et la base ; puis ce que la
 * base a produit et consomme (en vignettes, avec les quantites), l'argent, les
 * nations et le monde. Le marche et la banque de faction s'ouvrent d'un clic.
 */
public final class AbsenceMenu extends MenuController {

    public static final AbsenceMenu INSTANCE = new AbsenceMenu();

    private AbsenceMenu() {
        super("absence");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        AbsenceData.Report report = player.getData(Factions.ABSENCE.get()).last();
        long gained = AbsenceReport.total(report.gained());
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("ABSENCE", AbsenceReport.duration(report.away())
                        .toUpperCase(java.util.Locale.ROOT), 0xFFFFFFFF),
                new MenuView.Stat("BASE", report.kept() + " / " + KeptBases.CAP, report.kept() > 0 ? 0xFF55FF55
                        : 0xFFAAAAAA),
                new MenuView.Stat("PRODUIT", "+" + MoneyFormat.compact(gained), 0xFFFAC400));

        List<MenuView.Section> sections = new ArrayList<>();
        // -- la base
        List<Card> produced = new ArrayList<>();
        for (AbsenceData.Count count : report.gained()) {
            produced.add(tile(count, "+", 0xFF2E7D32));
        }
        if (produced.isEmpty()) {
            produced.add(Card.of(new ItemStack(report.kept() > 0 ? Items.CHEST : Items.BARRIER),
                    report.kept() > 0 ? "Rien de plus dans tes coffres" : "Ta base ne tournait pas",
                    report.kept() > 0 ? List.of("Machines, fours et champs", "de tes claims tournaient")
                            : List.of("Revendique des chunks :", "ils tourneront sans toi"))
                    .tooltip(List.of(report.kept() + " chunk(s) tenus, " + report.boxes() + " machines et coffres")));
            sections.add(new MenuView.Section("TA BASE", "resources", MenuView.GRID, produced));
        } else {
            sections.add(new MenuView.Section("CE QUE TA BASE A PRODUIT", "resources", MenuView.TILES, produced));
        }
        if (!report.spent().isEmpty()) {
            List<Card> spent = new ArrayList<>();
            for (AbsenceData.Count count : report.spent()) {
                spent.add(tile(count, "-", 0xFF8A2A22));
            }
            sections.add(new MenuView.Section("CE QU'ELLE A CONSOMME", "craft", MenuView.TILES, spent));
        }
        // -- l'argent, les nations, le monde
        if (!report.money().isEmpty()) {
            sections.add(new MenuView.Section("L'ARGENT", "upgrade", MenuView.GRID, cards(report.money())));
        }
        if (!report.nations().isEmpty()) {
            sections.add(new MenuView.Section("LES NATIONS", "craft", MenuView.GRID, cards(report.nations())));
        }
        if (!report.world().isEmpty()) {
            sections.add(new MenuView.Section("LE MONDE", "resources", MenuView.ROWS, cards(report.world())));
        }
        String subtitle = report.kept() > 0 && report.ran() < report.away() - 60_000L
                ? "Serveur arrete une partie du temps : ta base a tourne " + AbsenceReport.duration(report.ran())
                : report.claims() > report.kept() && report.kept() > 0
                ? report.kept() + " chunks tenus sur " + report.claims() + " claims : les plus actifs"
                : report.kept() > 0 ? "Tes " + report.kept() + " claims tournaient sans toi"
                : "/f charge : ce que ta base tient quand tu n'es pas la";
        return new MenuView(id(), "Pendant ton absence", subtitle, stats, sections, true);
    }

    private static Card tile(AbsenceData.Count count, String sign, int accent) {
        Item item = BuiltInRegistries.ITEM.getOptional(count.item()).orElse(Items.PAPER);
        ItemStack stack = new ItemStack(item);
        return Card.of(stack, MenuView.text(stack.getHoverName()), List.of(sign + MoneyFormat.exact(count.count())))
                .badge(sign + MoneyFormat.compact(count.count()))
                .accent(accent);
    }

    private static List<Card> cards(List<AbsenceData.Line> lines) {
        List<Card> cards = new ArrayList<>();
        for (AbsenceData.Line line : lines) {
            Item item = BuiltInRegistries.ITEM.getOptional(line.icon()).orElse(Items.PAPER);
            Card card = Card.of(new ItemStack(item), line.title(), line.lines()).accent(line.color());
            if (!line.action().isEmpty()) {
                card = card.action(line.action());
            }
            cards.add(card);
        }
        return cards;
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        switch (key) {
            case "market" -> WorldMarketMenu.INSTANCE.open(player);
            case "bank" -> FactionMenus.Bank.INSTANCE.open(player);
            default -> {
            }
        }
    }
}
