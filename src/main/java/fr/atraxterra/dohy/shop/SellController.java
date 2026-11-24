package fr.atraxterra.dohy.shop;

import fr.atraxterra.dohy.auction.AuctionController;
import fr.atraxterra.dohy.auction.AuctionSales;
import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.market.HourlyCourse;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L'onglet Vendre du commerce : ce que le sac contient, et ou chaque chose se vend.
 *
 * <p>Ce que le shop rachete, ligne par ligne, avec ce que ca rapporte maintenant ;
 * un bouton pour tout vendre d'un coup. Ce dont le prix change chaque heure (le
 * cours de l'heure) a sa propre section, en cartes avec la courbe des dernieres
 * heures : c'est la que choisir l'heure de vendre compte. Ce que le shop refuse
 * mais que l'hotel des ventes prend, avec un raccourci vers la mise en vente. Le
 * coffre de vente reste la, pour deposer une fournee a la main.
 */
public final class SellController extends MenuController {

    public static final SellController INSTANCE = new SellController();

    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFAC400;
    private static final int BLUE = 0xFF2F5BEA;

    private SellController() {
        super("sell");
    }

    /** Une sorte d'objet du sac que le shop rachete. */
    private record Line(Item item, Identifier id, PriceEntry entry, int count) {
    }

    @Override
    public MenuView view(ServerPlayer player) {
        long balance = Economy.balance(player);
        List<Line> lines = shopLines(player);
        Map<Item, Integer> auction = auctionKinds(player);

        long total = 0L;
        int open = 0;
        List<Card> rows = new ArrayList<>();
        List<Card> moving = new ArrayList<>();
        long slot = HourlyCourse.slot();
        for (Line line : lines) {
            boolean locked = !CollectionApi.isUnlocked(player, line.id(), line.entry());
            if (locked) {
                long missing = CollectionApi.missingToUnlock(player, line.id(), line.entry());
                long collected = CollectionApi.countOf(player, line.id());
                rows.add(Card.of(new ItemStack(line.item()), name(line.item()), List.of(
                                "x" + line.count() + " · vente pas encore ouverte",
                                "Encore " + MoneyFormat.exact(missing) + " a collecter"))
                        .state(MenuView.DISABLED).progress(collected, collected + missing));
                continue;
            }
            long unit = DynamicPrice.unitPrice(player, line.id(), line.entry());
            long payout = DynamicPrice.quote(player, line.id(), line.entry(), line.count());
            int percent = (int) Math.round(100.0d * unit / Math.max(1L, line.entry().basePrice()));
            total += payout;
            open++;
            List<String> tooltip = new ArrayList<>();
            if (percent < 100) {
                tooltip.add("Prix bas : il remonte avec le temps");
            }
            boolean hourly = HourlyCourse.follows(line.entry());
            if (hourly) {
                int course = HourlyCourse.percent(line.id(), line.entry()) - 100;
                tooltip.add("Cours : " + (course > 0 ? "+" : "") + course + " % (encore "
                        + HourlyCourse.minutesLeft() + " min)");
            }
            Card row = Card.of(new ItemStack(line.item()), name(line.item()), List.of(
                            "x" + line.count() + " · " + money(unit) + " piece",
                            "Total : " + money(payout)))
                    .accent(GREEN).tooltip(tooltip)
                    .action("item:" + line.id() + "|vendre les " + line.count());
            if (percent < 95) {
                row = row.badge("PRIX BAS");
            }
            if (hourly) {
                // Un prix qui change chaque heure : une carte avec la courbe des dernieres heures.
                moving.add(row.chart(ShopController.chart(player, line.id(), line.entry(), 0.0d, slot, false)));
            } else {
                rows.add(row);
            }
        }

        List<Card> quick = new ArrayList<>();
        Card all = Card.of(new ItemStack(Items.EMERALD), "Tout vendre", List.of(
                        open > 0 ? money(total) : "Rien a vendre",
                        open > 0 ? open + (open > 1 ? " sortes d'objets" : " sorte d'objet") : ""))
                .accent(GREEN)
                .tooltip(List.of("Sauf l'armure portee et la seconde main"));
        quick.add(open > 0 ? all.action("all|tout vendre").confirm("Tout vendre pour " + money(total) + " ?|"
                + open + (open > 1 ? " sortes d'objets" : " sorte d'objet") + " du sac") : all.state(MenuView.DISABLED));
        quick.add(Card.of(new ItemStack(Items.CHEST), "Coffre de vente", List.of("Depose, ferme : vendu",
                        "/sell coffre"))
                .accent(GOLD).action("chest|ouvrir"));

        List<Card> rare = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : auction.entrySet()) {
            ItemStack sample = new ItemStack(entry.getKey(), 1);
            long each = AuctionSales.recommended(sample);
            rare.add(Card.of(new ItemStack(entry.getKey()), name(entry.getKey()), List.of(
                            "x" + entry.getValue() + " · " + money(each) + " piece conseille",
                            "Hotel des ventes"))
                    .accent(BLUE)
                    .action("auction:" + BuiltInRegistries.ITEM.getKey(entry.getKey()) + "|mettre en vente"));
        }

        List<MenuView.Section> sections = new ArrayList<>();
        sections.add(new MenuView.Section("VENDRE D'UN COUP", "resources", MenuView.GRID, quick));
        if (!moving.isEmpty()) {
            sections.add(new MenuView.Section("COURS DE L'HEURE|" + moving.size(), "upgrade",
                    MenuView.GRID, moving));
        }
        if (!rows.isEmpty()) {
            sections.add(new MenuView.Section("CE QUE LE SHOP RACHETE|" + rows.size(), "upgrade", MenuView.ROWS, rows));
        }
        if (!rare.isEmpty()) {
            sections.add(new MenuView.Section("A L'HOTEL DES VENTES|" + rare.size(), "armor", MenuView.GRID, rare));
        }
        if (rows.isEmpty() && moving.isEmpty() && rare.isEmpty()) {
            sections.add(new MenuView.Section("TON SAC", "craft", MenuView.TEXT, List.of(Card.of(ItemStack.EMPTY, "",
                    List.of("Rien a vendre.")))));
        }
        List<MenuView.Stat> stats = List.of(new MenuView.Stat("TON ARGENT", money(balance), 0xFFFFFFFF));
        return new MenuView(id(), Commerce.TITLE, Commerce.Tab.SELL.subtitle(), stats, sections, false)
                .withTabs(Commerce.tabs(player, Commerce.Tab.SELL));
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (Commerce.openTab(player, key)) {
            return;
        }
        if (key.equals("all")) {
            sellAll(player);
        } else if (key.equals("chest")) {
            openChest(player);
        } else if (key.startsWith("item:")) {
            Identifier id = Identifier.tryParse(key.substring(5));
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            PriceEntry entry = id == null ? null : Prices.book().entry(id).orElse(null);
            if (item != null && entry != null) {
                ShopController.sell(player, id, item, entry, Integer.MAX_VALUE);
            }
        } else if (key.startsWith("auction:")) {
            Identifier id = Identifier.tryParse(key.substring(8));
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null) {
                AuctionController.INSTANCE.openPrice(player, item);
            }
        }
    }

    /** Le coffre de vente : un vrai conteneur, hors des vues. */
    public static void openChest(ServerPlayer player) {
        UiNet.forget(player);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new SellChestMenu(containerId, inventory, player),
                Component.literal("Coffre de vente")));
    }

    /**
     * Vend tout ce que le shop rachete dans le sac, vente ouverte. Les piles d'un
     * meme objet sont tarifees ensemble : le prix dynamique est une integrale sur
     * la quantite.
     */
    static void sellAll(ServerPlayer player) {
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        Map<Item, Integer> batch = new LinkedHashMap<>();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            PriceEntry entry = Prices.book().entry(stack).orElse(null);
            if (entry == null || !entry.sellable()
                    || !CollectionApi.isUnlocked(player, BuiltInRegistries.ITEM.getKey(stack.getItem()), entry)) {
                continue;
            }
            batch.merge(stack.getItem(), stack.getCount(), Integer::sum);
            stack.setCount(0);
        }
        long payout = 0L;
        int sold = 0;
        for (Map.Entry<Item, Integer> line : batch.entrySet()) {
            PriceEntry entry = Prices.book().entry(line.getKey()).orElse(null);
            if (entry == null) {
                continue;
            }
            payout += DynamicPrice.sell(player, BuiltInRegistries.ITEM.getKey(line.getKey()), entry, line.getValue());
            sold += line.getValue();
        }
        if (sold == 0) {
            failure(player);
            player.sendSystemMessage(Component.literal("Rien a vendre.").withStyle(ChatFormatting.GRAY));
            return;
        }
        long balance = Economy.credit(player, payout);
        fr.atraxterra.dohy.palamod.achievement.Achievements.sold(player, payout);
        success(player);
        player.sendSystemMessage(Component.literal("Vendu " + sold + " objets (" + batch.size() + " sortes) pour ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(money(payout)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    /** Ce que le shop rachete dans le sac, par sorte d'objet, les plus rentables d'abord. */
    private static List<Line> shopLines(ServerPlayer player) {
        Map<Item, Line> lines = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            PriceEntry entry = Prices.book().entry(stack).orElse(null);
            if (entry == null || !entry.sellable()) {
                continue;
            }
            Line known = lines.get(stack.getItem());
            lines.put(stack.getItem(), new Line(stack.getItem(), BuiltInRegistries.ITEM.getKey(stack.getItem()), entry,
                    (known == null ? 0 : known.count()) + stack.getCount()));
        }
        List<Line> out = new ArrayList<>(lines.values());
        out.sort(Comparator.comparingLong((Line line) -> -line.entry().basePrice() * (long) line.count()));
        return out;
    }

    /** Ce que l'hotel des ventes prend dans le sac, par sorte d'objet, avec la quantite. */
    private static Map<Item, Integer> auctionKinds(ServerPlayer player) {
        Map<Item, Integer> kinds = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && AuctionSales.accepts(stack)) {
                kinds.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return kinds;
    }

    private static String name(Item item) {
        return MenuView.text(new ItemStack(item).getHoverName());
    }

    private static String money(long amount) {
        return Commerce.money(amount);
    }
}
