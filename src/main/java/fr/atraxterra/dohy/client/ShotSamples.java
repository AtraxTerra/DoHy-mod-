package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.auction.AuctionController;
import fr.atraxterra.dohy.auction.AuctionData;
import fr.atraxterra.dohy.auction.AuctionHouse;
import fr.atraxterra.dohy.auction.AuctionSales;
import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.shop.SellController;
import fr.atraxterra.dohy.shop.ShopController;
import fr.atraxterra.dohy.spawn.SpawnApi;
import fr.atraxterra.dohy.ui.menu.BoutiqueController;
import fr.atraxterra.dohy.ui.menu.HubController;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/** Temporaire : le commerce a quatre onglets et l'hotel des ventes. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    /** Sur le sol du monde plat, loin du spawn : (300, -60, 300). */
    private static BlockPos origin;

    private ShotSamples() {
    }

    private static ShotHarness.Shot server(String name, int extra, Consumer<ServerPlayer> action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    action.accept(server.getPlayerList().getPlayers().get(0));
                } catch (Throwable t) {
                    LOG.error("dohy-shots: ECHEC serveur " + name, t);
                }
            });
            return null;
        });
    }

    private static ShotHarness.Shot client(String name, int extra, Runnable action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            try {
                action.run();
            } catch (Throwable t) {
                LOG.error("dohy-shots: ECHEC client " + name, t);
            }
            return null;
        });
    }

    private static void command(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static void tp(ServerPlayer player, double dx, double dy, double dz, float yaw, float pitch) {
        command(player, String.format(java.util.Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f",
                player.getName().getString(), origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, yaw,
                pitch));
    }

    /** Place la souris, en pixels de la fenetre, la ou le jeu la lit. */
    private static void cursor(double x, double y) {
        var handler = Minecraft.getInstance().mouseHandler;
        try {
            var xpos = handler.getClass().getDeclaredField("xpos");
            var ypos = handler.getClass().getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(handler, x);
            ypos.setDouble(handler, y);
        } catch (ReflectiveOperationException e) {
            LOG.warn("dohy-shots: [commerce] souris non deplacee", e);
        }
    }

    private static void log(String format, Object... args) {
        LOG.info("dohy-shots: [commerce] " + format, args);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow();
    }

    private static int slotOf(ServerPlayer player, String id) {
        NonNullList<ItemStack> bag = AuctionSales.bag(player);
        Item wanted = item(id);
        for (int slot = 0; slot < bag.size(); slot++) {
            if (bag.get(slot).is(wanted)) {
                return slot;
            }
        }
        return -1;
    }

    /** Tape une recherche dans l'onglet Acheter : la boite de recherche et son retour. */
    @SuppressWarnings("unchecked")
    private static void search(String text) throws ReflectiveOperationException {
        var screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof fr.atraxterra.dohy.client.ui.screen.ShopScreen)) {
            log("pas d'onglet Acheter ouvert : {}", screen == null ? "aucun" : screen.getClass().getSimpleName());
            return;
        }
        var elements = fr.atraxterra.dohy.client.ui.UiScreen.class.getDeclaredField("elements");
        elements.setAccessible(true);
        for (Object element : (List<Object>) elements.get(screen)) {
            if (element instanceof fr.atraxterra.dohy.client.ui.widget.SearchBox box) {
                box.withValue(text);
                var change = fr.atraxterra.dohy.client.ui.widget.TextField.class.getDeclaredField("onChange");
                change.setAccessible(true);
                ((Consumer<String>) change.get(box)).accept(text);
                log("recherche « {} »", text);
            }
        }
    }

    private static void action(ServerPlayer player, String card) {
        AuctionController.INSTANCE.handle(player, new UiNet.Action(AuctionController.INSTANCE.id(), "card", card, 0));
    }

    private static String sales(ServerPlayer player) {
        StringBuilder out = new StringBuilder();
        for (AuctionData.Sale sale : AuctionSales.sales(player)) {
            out.append(" [").append(sale.id()).append(' ').append(BuiltInRegistries.ITEM.getKey(sale.stack().getItem()))
                    .append(" x").append(sale.stack().getCount()).append(' ').append(sale.price()).append(" $ ")
                    .append(sale.state() == AuctionData.Sale.ACTIVE ? "en vente"
                            : sale.state() == AuctionData.Sale.SOLD ? "vendu" : "invendu").append(']');
        }
        return out.length() == 0 ? " (aucune)" : out.toString();
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            ShotHarness.until = () -> !SpawnApi.building();
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "gamerule doDaylightCycle false");
            command(player, "gamerule doMobSpawning false");
            player.setGameMode(GameType.SURVIVAL);
            origin = new BlockPos(300, -60, 300);
            tp(player, 0.5, 0, 0.5, 0.0f, 10.0f);
        }));
        ShotHarness.SHOTS.add(server("a1_sac", 20, player -> {
            Economy.set(player, 80_000_000L);
            var inventory = player.getInventory();
            inventory.clearContent();
            String[][] items = {
                    {"minecraft:wheat", "64"}, {"minecraft:cobblestone", "64"}, {"minecraft:cobblestone", "64"},
                    {"palamod:paladium_ingot", "32"}, {"minecraft:gold_ingot", "16"}, {"minecraft:carrot", "48"},
                    {"palamod:legendary_stone_fortune", "1"}, {"palamod:rune_common", "1"},
                    {"palamod:rune_fragment_common", "5"}, {"palamod:talikus_reinforced_sword", "1"},
                    {"palamod:crate_key_paladium", "2"}, {"palamod:capture_stone", "2"},
                    {"palamod:forest_essence", "3"}};
            int slot = 9;
            for (String[] entry : items) {
                inventory.setItem(slot++, new ItemStack(item(entry[0]), Integer.parseInt(entry[1])));
            }
            // La vente du ble, de la pierre, du paladium et des carottes est ouverte ; pas celle de l'or.
            for (String id : new String[] {"minecraft:wheat", "minecraft:cobblestone", "palamod:paladium_ingot",
                    "minecraft:carrot"}) {
                CollectionApi.record(player, Identifier.parse(id), 20_000);
            }
            ItemStack stone = new ItemStack(item("palamod:legendary_stone_fortune"));
            ItemStack keys = new ItemStack(item("palamod:crate_key_paladium"), 2);
            log("argent {} ; pierre : cote {} conseille {} ; 2 cles : conseille {} ; refus pierre de capture : {} ;"
                            + " refus ble : {}", Economy.balance(player), AuctionSales.quote(stone.getItem()),
                    AuctionSales.recommended(stone), AuctionSales.recommended(keys),
                    AuctionSales.refusal(new ItemStack(item("palamod:capture_stone"))),
                    AuctionSales.refusal(new ItemStack(item("minecraft:wheat"))));
        }));
        ShotHarness.SHOTS.add(server("b1_acheter", 30, ShopController::open));
        ShotHarness.SHOTS.add(client("b2_recherche", 15, () -> {
            cursor(10, 10);
            try {
                search("cle");
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: ECHEC recherche", e);
            }
        }));
        ShotHarness.SHOTS.add(client("b3_introuvable", 10, () -> {
            try {
                search("zzzz");
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: ECHEC recherche", e);
            }
        }));
        ShotHarness.SHOTS.add(server("c1_vendre", 25, player -> SellController.INSTANCE.open(player)));
        ShotHarness.SHOTS.add(server("d1_boutique", 25, player -> BoutiqueController.INSTANCE.open(player)));
        ShotHarness.SHOTS.add(server("e1_hdv", 30, player -> {
            // Trois annonces : une vendue, une en cours, une invendue.
            log("fragments : {}", AuctionSales.list(player, slotOf(player, "palamod:rune_fragment_common"), "quick"));
            log("regle : {}", AuctionSales.settle(player, true));
            log("epee : {}", AuctionSales.list(player, slotOf(player, "palamod:talikus_reinforced_sword"), "fair"));
            log("cles : {}", AuctionSales.list(player, slotOf(player, "palamod:crate_key_paladium"), "high"));
            try {
                // L'annonce des cles vieillit de deux jours : elle doit revenir invendue.
                AuctionData data = AuctionHouse.data(player);
                var replace = AuctionData.class.getDeclaredMethod("replace", AuctionData.Sale.class);
                replace.setAccessible(true);
                for (AuctionData.Sale sale : data.sales()) {
                    if (sale.stack().is(item("palamod:crate_key_paladium"))) {
                        replace.invoke(data, new AuctionData.Sale(sale.id(), sale.stack(), sale.price(), sale.preset(),
                                sale.listedAt() - 49L * 3_600_000L, -1L, AuctionData.Sale.ACTIVE));
                    }
                }
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: ECHEC vieillissement", e);
            }
            AuctionController.INSTANCE.open(player);
            log("ventes :{} ; emplacements libres {}", sales(player), AuctionSales.free(player));
        }));
        ShotHarness.SHOTS.add(server("e2_hdv_objet", 20, player -> action(player, "pick")));
        ShotHarness.SHOTS.add(server("e3_hdv_prix", 20, player -> action(player, "pick:"
                + slotOf(player, "palamod:legendary_stone_fortune"))));
        ShotHarness.SHOTS.add(server("e4_hdv_mis", 20, player -> {
            action(player, "sell:" + slotOf(player, "palamod:legendary_stone_fortune") + ":fair");
            log("apres la mise en vente :{}", sales(player));
        }));
        ShotHarness.SHOTS.add(server("e5_hdv_vendu", 20, player -> {
            long before = Economy.balance(player);
            int done = AuctionSales.settle(player, true);
            AuctionController.INSTANCE.open(player);
            log("vente forcee : {} reglee(s), argent {} -> {} ;{}", done, before, Economy.balance(player),
                    sales(player));
        }));
        ShotHarness.SHOTS.add(server("e6_hdv_recupere", 15, player -> {
            for (AuctionData.Sale sale : AuctionSales.sales(player)) {
                if (sale.state() == AuctionData.Sale.UNSOLD) {
                    action(player, "claim:" + sale.id());
                }
            }
            log("apres la recuperation :{} ; cles dans le sac : {}", sales(player),
                    slotOf(player, "palamod:crate_key_paladium") >= 0);
        }));
        ShotHarness.SHOTS.add(server("f1_menu", 25, player -> HubController.INSTANCE.open(player)));
        ShotHarness.SHOTS.add(client("f2_inventaire", 15, () -> {
            Minecraft.getInstance().gui.setScreen(
                    new fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen(Minecraft.getInstance().player));
            cursor(24, 24);
        }));
        ShotHarness.SHOTS.add(client("f3_fermer", 5, () -> Minecraft.getInstance().gui.setScreen(null)));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> LOG.info("dohy-shots: [fin] {}",
                List.of(player.getName().getString()))));
    }
}
