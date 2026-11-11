package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.museum.MuseumConfig;
import fr.atraxterra.dohy.museum.MuseumMenu;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
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
 * exposee, grisee sinon, avec une ligne qui dit ou la trouver. Montrer le
 * manque, c'est dire quoi chercher.
 */
public final class MuseumController extends MenuController {

    public static final MuseumController INSTANCE = new MuseumController();
    private static final String PATRON = MuseumApi.PATRON;

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

    private static MuseumConfig.Wing section(String wing) {
        return MuseumConfig.config().wing(wing);
    }

    /** L'objet de la carte d'une aile : celui du fichier, sinon sa premiere piece. */
    private static ItemStack icon(MuseumConfig.Wing wing) {
        Item item = wing.icon().flatMap(id -> BuiltInRegistries.ITEM.getOptional(id)).orElse(null);
        if (item == null && !wing.pieces().isEmpty()) {
            item = BuiltInRegistries.ITEM.getOptional(wing.pieces().get(0)).orElse(null);
        }
        return new ItemStack(item == null ? Items.BOOK : item);
    }

    private MenuView wings(ServerPlayer player) {
        List<String> wings = new ArrayList<>(MuseumApi.wings());
        if (!MuseumApi.patronItems().isEmpty()) {
            wings.add(PATRON);
        }
        int expected = 0;
        int donated = 0;
        int complete = 0;
        List<Card> cards = new ArrayList<>();
        for (String key : wings) {
            boolean patron = PATRON.equals(key);
            MuseumConfig.Wing wing = section(key);
            int total = wing.pieces().size();
            int done = MuseumApi.donatedIn(player, key);
            boolean full = done == total;
            if (!patron) {
                expected += total;
                donated += done;
            }
            if (full) {
                complete++;
            }
            List<String> tooltip = new ArrayList<>();
            if (patron) {
                tooltip.add("Optionnel : piece unique, aucune aile ne l'exige");
            }
            wing.rewards().forEach(reward -> tooltip.add((full ? "Acquis : " : "A la cle : ")
                    + MuseumApi.describe(reward)));
            Card card = Card.of(icon(wing), wing.name(), List.of(done + " / " + total
                            + " piece(s)", full ? "Aile complete" : "Bonus a la cle"))
                    .accent(patron ? 0xFFEC4FB5 : 0xFFFAC400).progress(done, total)
                    .action("wing:" + key + "|voir les vitrines").tooltip(tooltip);
            cards.add(full ? card.state(MenuView.DONE) : card);
        }
        cards.add(Card.of(new ItemStack(Items.HOPPER), "Deposer des pieces", List.of("Le guichet de dons",
                        "Un exemplaire preleve, le reste rendu"))
                .accent(0xFF22E558).action("deposit|ouvrir le guichet"));
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("EXPOSEES", donated + " / " + expected, 0xFFFFFFFF),
                new MenuView.Stat("AILES", complete + " / " + wings.size(), 0xFF22E558),
                new MenuView.Stat("PAR DON", "+" + MuseumConfig.config().globalXpPerDonation() + " XP",
                        0xFFFAC400));
        return new MenuView(id(), "Musee", "Des pieces rares seulement ; un don est definitif", stats,
                List.of(new MenuView.Section("AILES", "resources", MenuView.GRID, cards)), true);
    }

    private MenuView wing(ServerPlayer player, String key) {
        MuseumConfig.Wing wing = section(key);
        List<Card> tiles = new ArrayList<>();
        for (Identifier id : wing.pieces()) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
                continue;
            }
            boolean donated = MuseumApi.data(player).has(id);
            String itemName = MenuView.text(new ItemStack(item).getHoverName());
            Card card = Card.of(new ItemStack(item), itemName, List.of(donated ? "Expose" : "Manquant"));
            if (donated) {
                card = card.state(MenuView.DONE);
            } else {
                List<String> tooltip = new ArrayList<>();
                String hint = wing.hints().get(id);
                if (hint != null && !hint.isEmpty()) {
                    tooltip.add(hint);
                }
                tooltip.add("Apporte-en un au guichet");
                card = card.state(MenuView.DISABLED).tooltip(tooltip);
            }
            tiles.add(card);
        }
        int done = MuseumApi.donatedIn(player, key);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("EXPOSEES", done + " / " + wing.pieces().size(), 0xFFFFFFFF));
        return new MenuView(id(), wing.name(), "Les pieces grisees manquent encore", stats,
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
                    Component.literal("Guichet du musee")));
        }
    }
}
