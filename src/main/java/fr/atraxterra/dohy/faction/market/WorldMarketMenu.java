package fr.atraxterra.dohy.faction.market;

import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionEdit;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.NationRank;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran du marche international : le cours de chaque ressource, son stock
 * mondial, et les depeches des gros lots des nations. Clic : vendre tout ce
 * qu'on porte ; clic droit : acheter une pile.
 */
public final class WorldMarketMenu extends MenuController {

    public static final WorldMarketMenu INSTANCE = new WorldMarketMenu();

    private WorldMarketMenu() {
        super("faction_world_market");
    }

    @Override
    public void open(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            FactionEdit.open(player);
            return;
        }
        super.open(player);
    }

    @Override
    protected void back(ServerPlayer player) {
        FactionMenus.Overview.INSTANCE.open(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        NationRank rank = NationRank.of(player);
        List<Card> goods = new ArrayList<>();
        for (Identifier id : WorldMarket.goods()) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER);
            double factor = WorldMarket.factor(server, id);
            long base = Prices.book().entry(id).map(entry -> entry.basePrice()).orElse(1L);
            int have = WorldMarket.count(player, item);
            int room = WorldMarket.room(server, id);
            int available = WorldMarket.available(server, id);
            long stock = Math.round(WorldMarket.stock(server, id));
            int pile = Math.min(item.getDefaultMaxStackSize(), available);
            List<String> tip = new ArrayList<>();
            tip.add("Stock mondial : " + MoneyFormat.exact(stock) + " (reference "
                    + MoneyFormat.exact(Math.round(WorldMarket.reference(id))) + ")");
            tip.add("Prix de base : " + MoneyFormat.exact(base) + " $ · boutique : "
                    + MoneyFormat.exact(Prices.book().entry(id).map(entry -> entry.buyPrice()).orElse(0L)) + " $");
            if (have > 0 && room > 0) {
                int sold = Math.min(have, room);
                tip.add("Clic : vendre " + MoneyFormat.exact(sold) + " pour "
                        + MoneyFormat.exact(WorldMarket.sellQuote(player, id, sold)) + " $");
            } else {
                tip.add(room <= 0 ? "Marche sature : il n'achete plus" : "Clic : vendre ce que tu portes (rien)");
            }
            tip.add(pile > 0 ? "Clic droit : acheter " + pile + " pour "
                    + MoneyFormat.exact(WorldMarket.buyQuote(server, id, pile)) + " $" : "Penurie : rien a acheter");
            Card card = Card.of(new ItemStack(item), WorldMarket.name(id), List.of(
                            "Vente " + MoneyFormat.exact(WorldMarket.sellUnit(player, id)) + " $ · achat "
                                    + MoneyFormat.exact(WorldMarket.buyUnit(server, id)) + " $",
                            "Cours " + WorldMarket.percent(factor) + (have > 0 ? " · tu en as "
                                    + MoneyFormat.exact(have) : "")))
                    .accent(factor >= 1.15d ? 0xFF22E558 : factor <= 0.85d ? 0xFFEF3826 : 0xFF55545C)
                    .progress(Math.round(stock - WorldMarket.minStock(id)),
                            Math.round(WorldMarket.maxStock(id) - WorldMarket.minStock(id)))
                    .tooltip(tip).action("sell:" + id + "|vendre tout").right("buy:" + id + "|acheter une pile");
            if (room <= 0) {
                card = card.badge("SATURE");
            } else if (available <= 0) {
                card = card.badge("PENURIE");
            } else if (factor >= 1.15d) {
                card = card.badge("CHER");
            } else if (factor <= 0.85d) {
                card = card.badge("BRADE");
            }
            goods.add(card);
        }
        if (goods.isEmpty()) {
            goods.add(Card.of(new ItemStack(Items.BARRIER), "Marche ferme", List.of("Aucune ressource cotee", ""))
                    .state(MenuView.DISABLED));
        }
        List<Card> news = new ArrayList<>();
        for (String line : WorldMarket.news(server)) {
            int cut = line.indexOf(" : ");
            news.add(Card.of(new ItemStack(Items.PAPER), cut > 0 ? line.substring(0, cut) : line,
                    List.of(cut > 0 ? capital(line.substring(cut + 3)) : "", "")).accent(0xFF27C4E8));
        }
        if (news.isEmpty()) {
            news.add(Card.of(new ItemStack(Items.PAPER), "Pas encore de depeche", List.of(
                    "Une nation bouge un gros lot", "toutes les dix minutes de jeu")).state(MenuView.DISABLED));
        }
        NationRank next = rank.next();
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("RANG", rank.label().toUpperCase(), rank.color()),
                new MenuView.Stat("TAXE", Math.round(rank.fee() * 100.0d) + " %", 0xFFFAC400),
                new MenuView.Stat("VOLUME", MoneyFormat.shortForm(WorldMarket.volume(player)), 0xFF22E558));
        return new MenuView(id(), "Marche international", next == null
                ? "Le cours suit le stock mondial : vends quand le monde en manque"
                : "Taxe de " + Math.round(next.fee() * 100.0d) + " % au rang " + next.label() + " ("
                + next.countries() + " pays)", stats, List.of(
                new MenuView.Section("COURS MONDIAUX", "resources", MenuView.GRID, goods),
                new MenuView.Section("DEPECHES", "upgrade", MenuView.GRID, news)), true);
    }

    /** Le marche annonce lui-meme ses echanges : ici, seulement le son, ou le refus. */
    private static void done(ServerPlayer player, String refusal) {
        if (refusal == null) {
            success(player);
        } else {
            report(player, refusal, "");
        }
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("sell:")) {
            done(player, WorldMarket.sell(player, Identifier.parse(key.substring(5)), Integer.MAX_VALUE));
        } else if (key.startsWith("buy:")) {
            Identifier id = Identifier.parse(key.substring(4));
            int pile = BuiltInRegistries.ITEM.getOptional(id).map(Item::getDefaultMaxStackSize).orElse(64);
            done(player, WorldMarket.buy(player, id, pile));
        }
    }
}
