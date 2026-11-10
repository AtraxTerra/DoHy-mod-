package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.museum.MuseumConfig;
import fr.atraxterra.dohy.museum.MuseumMenu;
import fr.atraxterra.dohy.shop.ShopCategory;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le musee : les ailes, puis les vitrines d'une aile — la piece si elle est
 * exposee, grisee sinon. Montrer le manque, c'est dire quoi chercher.
 */
public final class MuseumController extends MenuController {

    public static final MuseumController INSTANCE = new MuseumController();
    private static final String PATRON = "patron";

    private final Map<UUID, String> open = new ConcurrentHashMap<>();

    private MuseumController() {
        super("museum");
    }

    @Override
    public void open(ServerPlayer player) {
        open.remove(player.getUUID());
        super.open(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        String wing = open.get(player.getUUID());
        return wing == null ? wings(player) : wing(player, wing);
    }

    private static List<Identifier> contents(String wing) {
        return PATRON.equals(wing) ? MuseumApi.patronItems() : MuseumApi.expected(wing);
    }

    private static MuseumConfig.Section section(String wing) {
        return PATRON.equals(wing) ? MuseumConfig.config().patron() : MuseumConfig.config().section(wing);
    }

    private static String name(String wing) {
        String display = section(wing).display();
        return display.isEmpty() ? wing : display;
    }

    private static Item icon(String wing) {
        if (PATRON.equals(wing)) {
            return Items.NETHER_STAR;
        }
        ShopCategory category = ShopCategory.byKey(wing);
        return category == null ? Items.BOOK : category.icon();
    }

    private static int donatedAmong(ServerPlayer player, List<Identifier> ids) {
        int count = 0;
        for (Identifier id : ids) {
            if (MuseumApi.data(player).has(id)) {
                count++;
            }
        }
        return count;
    }

    private MenuView wings(ServerPlayer player) {
        List<String> wings = new ArrayList<>(MuseumApi.categories());
        if (!MuseumApi.patronItems().isEmpty()) {
            wings.add(PATRON);
        }
        int expected = 0;
        int donated = 0;
        int complete = 0;
        List<Card> cards = new ArrayList<>();
        for (String wing : wings) {
            boolean patron = PATRON.equals(wing);
            List<Identifier> ids = contents(wing);
            int done = donatedAmong(player, ids);
            boolean full = !ids.isEmpty() && done == ids.size();
            if (!patron) {
                expected += ids.size();
                donated += done;
            }
            if (full) {
                complete++;
            }
            List<String> tooltip = new ArrayList<>();
            if (patron) {
                tooltip.add("Optionnel : pieces uniques, aucune aile ne les exige");
            }
            section(wing).rewards().forEach(reward -> tooltip.add((full ? "Acquis : " : "A la cle : ")
                    + MuseumApi.describe(reward)));
            Card card = Card.of(new ItemStack(icon(wing)), name(wing), List.of(done + " / " + ids.size()
                            + " piece(s)", full ? "Aile complete" : "Bonus a la cle"))
                    .accent(patron ? 0xFFEC4FB5 : 0xFFFAC400).progress(done, ids.size())
                    .action("wing:" + wing + "|voir les vitrines").tooltip(tooltip);
            cards.add(full ? card.state(MenuView.DONE) : card);
        }
        cards.add(Card.of(new ItemStack(Items.HOPPER), "Deposer des pieces", List.of("Le guichet de dons",
                        "Un exemplaire preleve, le reste rendu"))
                .accent(0xFF22E558).action("deposit|ouvrir le guichet"));
        long value = 0;
        for (Identifier id : MuseumApi.data(player).all()) {
            PriceEntry entry = Prices.book().entry(id).orElse(null);
            if (entry != null) {
                value += entry.basePrice();
            }
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("EXPOSEES", donated + " / " + expected, 0xFFFFFFFF),
                new MenuView.Stat("AILES", complete + " / " + wings.size(), 0xFF22E558),
                new MenuView.Stat("VALEUR", MoneyFormat.shortForm(value), 0xFFFAC400));
        return new MenuView(id(), "Musee", "Un don est definitif ; les bonus d'aile sont permanents", stats,
                List.of(new MenuView.Section("AILES", "resources", MenuView.GRID, cards)), true);
    }

    private MenuView wing(ServerPlayer player, String wing) {
        List<Identifier> ids = contents(wing);
        List<Card> tiles = new ArrayList<>();
        for (Identifier id : ids) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
                continue;
            }
            boolean donated = MuseumApi.data(player).has(id);
            PriceEntry entry = Prices.book().entry(id).orElse(null);
            String itemName = new ItemStack(item).getHoverName().getString();
            Card card = Card.of(new ItemStack(item), itemName, List.of(donated ? "Expose" : "Manquant"));
            if (donated) {
                card = card.state(MenuView.DONE).tooltip(entry == null ? List.of()
                        : List.of("Valeur " + MoneyFormat.exact(entry.basePrice())));
            } else {
                card = card.state(MenuView.DISABLED).tooltip(List.of("Apporte-en un au guichet"));
            }
            tiles.add(card);
        }
        int done = donatedAmong(player, ids);
        List<MenuView.Stat> stats = List.of(new MenuView.Stat("EXPOSEES", done + " / " + ids.size(), 0xFFFFFFFF));
        return new MenuView(id(), name(wing), "Les pieces grisees manquent encore", stats,
                List.of(new MenuView.Section("", "", MenuView.TILES, tiles)), true);
    }

    @Override
    protected void back(ServerPlayer player) {
        if (open.remove(player.getUUID()) != null) {
            super.open(player);
        } else {
            super.back(player);
        }
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("wing:")) {
            open.put(player.getUUID(), key.substring(5));
        } else if ("deposit".equals(key)) {
            UiNet.forget(player);
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, viewer) -> new MuseumMenu(containerId, inventory, player),
                    Component.literal("Musee — depose, ferme pour exposer")));
        }
    }
}
