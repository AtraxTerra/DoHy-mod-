package fr.atraxterra.dohy.auction;

import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** L'ecran du marche : les annonces du jour par rayon, et le renouvellement paye. */
public final class AuctionController extends MenuController {

    public static final AuctionController INSTANCE = new AuctionController();

    private static final int DEAL = 0xFF22E558;
    private static final int FAIR = 0xFF2F5BEA;
    private static final int GREEDY = 0xFFEF3826;

    private AuctionController() {
        super("auction");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        AuctionConfig.Config config = AuctionConfig.config();
        List<AuctionHouse.Listing> listings = AuctionHouse.listings(player);
        long balance = Economy.balance(player);
        int open = 0;
        for (AuctionHouse.Listing listing : listings) {
            if (AuctionHouse.remaining(player, listing) > 0) {
                open++;
            }
        }
        long minutes = ContractApi.minutesUntilDailyReset();
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("LIQUIDE", MoneyFormat.shortForm(balance), 0xFFFFFFFF),
                new MenuView.Stat("ANNONCES", open + " / " + listings.size(), 0xFFFAC400),
                new MenuView.Stat("NOUVELLES DANS", minutes / 60 + " h " + String.format("%02d", minutes % 60),
                        0xFF22E558));

        List<MenuView.Section> sections = new ArrayList<>();
        for (AuctionConfig.Category category : config.categories()) {
            List<Card> cards = new ArrayList<>();
            for (AuctionHouse.Listing listing : listings) {
                if (listing.offer().category().equals(category.id())) {
                    cards.add(card(player, listing, balance));
                }
            }
            if (!cards.isEmpty()) {
                sections.add(new MenuView.Section(category.title(), category.icon(), MenuView.GRID, cards));
            }
        }

        long reroll = AuctionHouse.rerollPrice(player);
        Card refresh = Card.of(new ItemStack(Items.BELL), "Nouvelles annonces", List.of(
                        reroll < 0L ? "Plus rien de neuf aujourd'hui" : MoneyFormat.exact(reroll),
                        "Remplace toutes les annonces"))
                .accent(0xFFFAC400)
                .tooltip(List.of("D'autres vendeurs, d'autres prix",
                        "Le prix double a chaque fois dans la journee",
                        "Les annonces changent seules chaque jour",
                        reroll > balance ? "Il te manque " + MoneyFormat.exact(reroll - balance) : ""));
        refresh = reroll < 0L || reroll > balance ? refresh.state(MenuView.DISABLED)
                : refresh.action("reroll|payer");
        sections.add(new MenuView.Section("VENDEURS", "craft", MenuView.GRID, List.of(refresh)));

        return new MenuView(id(), "Marche", "Ce que d'autres vendent aujourd'hui : stocks limites, prix variables",
                stats, sections, true);
    }

    private static Card card(ServerPlayer player, AuctionHouse.Listing listing, long balance) {
        ItemStack icon = listing.icon();
        int left = AuctionHouse.remaining(player, listing);
        String name = MenuView.text(new ItemStack(listing.item()).getHoverName());
        String title = listing.lot() > 1 ? name + " x" + listing.lot() : name;
        int percent = (int) Math.round((listing.factor() - 1.0d) * 100.0d);
        boolean deal = percent <= -8;
        boolean greedy = percent >= 25;
        List<String> tooltip = new ArrayList<>();
        tooltip.add("Cote : " + MoneyFormat.exact(listing.offer().quote()) + " l'unite");
        if (listing.lot() > 1) {
            tooltip.add("Ici : " + MoneyFormat.exact(listing.price() / listing.lot()) + " l'unite");
        }
        tooltip.add(deal ? "Bonne affaire : " + (-percent) + " % sous la cote"
                : greedy ? "Vendeur gourmand : " + percent + " % au-dessus de la cote"
                : percent == 0 ? "Au prix de la cote"
                : percent > 0 ? percent + " % au-dessus de la cote" : (-percent) + " % sous la cote");
        if (left > 0 && listing.price() > balance) {
            tooltip.add("Il te manque " + MoneyFormat.exact(listing.price() - balance));
        }
        Card card = Card.of(icon, title, List.of(MoneyFormat.exact(listing.price()),
                        listing.seller() + " · " + (left > 0 ? left + " en stock" : "vendu")))
                .accent(deal ? DEAL : greedy ? GREEDY : FAIR)
                .badge(left <= 0 ? "VENDU" : deal ? "-" + (-percent) + " %" : "")
                .tooltip(tooltip);
        if (left <= 0) {
            return card.state(MenuView.DONE);
        }
        if (listing.price() > balance) {
            return card.state(MenuView.DISABLED);
        }
        return card.action("buy:" + listing.index() + "|acheter");
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("buy:")) {
            int index;
            try {
                index = Integer.parseInt(key.substring(4));
            } catch (NumberFormatException error) {
                return;
            }
            List<AuctionHouse.Listing> listings = AuctionHouse.listings(player);
            String name = index >= 0 && index < listings.size()
                    ? new ItemStack(listings.get(index).item()).getHoverName().getString()
                    + (listings.get(index).lot() > 1 ? " x" + listings.get(index).lot() : "") : "";
            report(player, AuctionHouse.buy(player, index), "Achete : " + name + ".");
        } else if (key.equals("reroll")) {
            report(player, AuctionHouse.reroll(player), "De nouveaux vendeurs sont passes.");
        }
    }
}
