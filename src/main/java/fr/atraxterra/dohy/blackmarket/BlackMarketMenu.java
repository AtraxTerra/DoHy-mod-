package fr.atraxterra.dohy.blackmarket;

import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.war.NationTraits;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran du receleur : la marchandise volee, la contrebande, le lot sans
 * etiquette, et ce qu'il rachete aujourd'hui.
 */
public final class BlackMarketMenu extends MenuController {

    public static final BlackMarketMenu INSTANCE = new BlackMarketMenu();

    private static final int PURPLE = 0xFF9B6BFF;
    private static final int RED = 0xFFEF3826;
    private static final int GOLD = 0xFFFAC400;
    private static final int GREEN = 0xFF22E558;

    private BlackMarketMenu() {
        super("marche_noir");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        BlackMarketConfig.Config config = BlackMarketConfig.config();
        long balance = Economy.balance(player);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("LIQUIDE", MoneyFormat.shortForm(balance), 0xFFFFFFFF),
                new MenuView.Stat("ARRIVAGE", "DANS " + minutes(ContractApi.minutesUntilDailyReset()), 0xFF8C8B93));

        List<Card> stolen = new ArrayList<>();
        List<Card> contraband = new ArrayList<>();
        boolean faction = FactionApi.has(player);
        for (BlackMarket.Listing listing : BlackMarket.listings(player)) {
            int left = BlackMarket.remaining(player, listing);
            String name = new ItemStack(listing.item()).getHoverName().getString();
            List<String> lines = new ArrayList<>();
            lines.add(listing.lot() + " x · " + MoneyFormat.exact(listing.price()) + " $");
            if (listing.stolen()) {
                lines.add("Vole " + BlackMarket.fromNation(listing.nation()));
            } else {
                lines.add(listing.offer().jobXp() > 0 ? MoneyFormat.exact(listing.offer().jobXp()) + " XP de metier"
                        : "Introuvable ailleurs");
            }
            List<String> tooltip = new ArrayList<>();
            tooltip.add(listing.lot() + " x " + name);
            tooltip.add(MoneyFormat.exact(listing.price()) + " $ le lot · " + left + " lot(s) aujourd'hui");
            tooltip.add("Valeur boutique : " + MoneyFormat.exact(listing.offer().value() * listing.lot()) + " $");
            if (listing.stolen()) {
                NationTraits traits = NationTraits.of(listing.nation());
                tooltip.add(faction ? traits.name() + " : un grief de plus si tu achetes"
                        : "Sans faction, personne ne remonte jusqu'a toi");
            }
            Card card = Card.of(listing.icon(), name, lines)
                    .accent(listing.stolen() ? RED : PURPLE)
                    .badge(left > 0 ? "x" + left : "VENDU")
                    .tooltip(tooltip);
            if (left <= 0) {
                card = card.state(MenuView.DISABLED);
            } else if (balance < listing.price()) {
                card = card.state(MenuView.DISABLED).action("buy:" + listing.index() + "|acheter");
            } else {
                card = card.action("buy:" + listing.index() + "|acheter");
            }
            (listing.stolen() ? stolen : contraband).add(card);
        }

        List<Card> lot = new ArrayList<>();
        BlackMarketConfig.Mystery mystery = config.mystery();
        if (mystery.price() > 0L && !mystery.outcomes().isEmpty()) {
            int left = BlackMarket.lotsLeft(player);
            Card card = Card.of(new ItemStack(Items.BARREL), "Lot sans etiquette", List.of(
                            MoneyFormat.exact(mystery.price()) + " $",
                            "De la ferraille... ou le gros lot"))
                    .accent(GOLD).badge(left > 0 ? "x" + left : "VENDU")
                    .tooltip(List.of("Un prix fixe, un contenu au hasard",
                            "Parfois de l'or, parfois du vent",
                            left + " lot(s) aujourd'hui"));
            lot.add(left <= 0 ? card.state(MenuView.DISABLED) : card.action("lot|ouvrir un lot"));
        }

        List<Card> buyback = new ArrayList<>();
        for (BlackMarket.Wanted wanted : BlackMarket.wanted(player)) {
            int left = BlackMarket.leftToSell(player, wanted);
            String name = new ItemStack(wanted.item()).getHoverName().getString();
            int held = 0;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.getItem() == wanted.item()) {
                    held += stack.getCount();
                }
            }
            Card card = Card.of(new ItemStack(wanted.item()), name, List.of(
                            MoneyFormat.exact(wanted.unit()) + " $ la piece, usure deduite",
                            left > 0 ? "Encore " + left + " aujourd'hui · " + held + " dans ton sac"
                                    : "Il en a assez pour aujourd'hui"))
                    .accent(GREEN).badge(left > 0 ? "x" + left : "OK");
            buyback.add(left <= 0 || held == 0 ? card.state(MenuView.DISABLED)
                    : card.action("sell:" + wanted.index() + "|revendre"));
        }

        List<MenuView.Section> sections = new ArrayList<>();
        sections.add(new MenuView.Section("MARCHANDISE VOLEE", "melt", MenuView.GRID, stolen));
        sections.add(new MenuView.Section("CONTREBANDE", "fuel", MenuView.GRID, contraband));
        if (!lot.isEmpty()) {
            sections.add(new MenuView.Section("LOT SANS ETIQUETTE", "resources", MenuView.GRID, lot));
        }
        sections.add(new MenuView.Section("LE RECELEUR RACHETE", "upgrade", MenuView.ROWS, buyback));
        String quote = BlackMarket.quote(player);
        return new MenuView(id(), "Marche noir", quote.isEmpty() ? "Le receleur" : "Le receleur · " + quote, stats,
                sections, false);
    }

    private static String minutes(long minutes) {
        long hours = minutes / 60L;
        return hours > 0 ? hours + " H " + String.format("%02d", minutes % 60L) : minutes + " MIN";
    }

    @Override
    protected void back(ServerPlayer player) {
        fr.atraxterra.dohy.ui.view.UiNet.close(player);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("buy:")) {
            try {
                String refusal = BlackMarket.buy(player, Integer.parseInt(key.substring(4)));
                if (refusal == null) {
                    success(player);
                } else {
                    report(player, refusal, null);
                }
            } catch (NumberFormatException ignored) {
                failure(player);
            }
        } else if ("lot".equals(key)) {
            String result = BlackMarket.mystery(player);
            if (result.startsWith("!")) {
                report(player, result.substring(1), null);
            } else {
                success(player);
                player.sendSystemMessage(Component.literal("[Receleur] ").withStyle(ChatFormatting.DARK_PURPLE)
                        .append(Component.literal(result).withStyle(ChatFormatting.GOLD)));
            }
        } else if (key.startsWith("sell:")) {
            try {
                String refusal = BlackMarket.sell(player, Integer.parseInt(key.substring(5)));
                if (refusal == null) {
                    success(player);
                } else {
                    report(player, refusal, null);
                }
            } catch (NumberFormatException ignored) {
                failure(player);
            }
        }
    }
}
