package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.auction.AuctionSales;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.NationRank;
import fr.atraxterra.dohy.grade.GradesController;
import fr.atraxterra.dohy.grade.Grades;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.storage.EnderExtra;
import fr.atraxterra.dohy.palamod.storage.PalaStorage;
import fr.atraxterra.dohy.shop.ShopController;
import fr.atraxterra.dohy.spawn.SpawnApi;
import fr.atraxterra.dohy.ui.menu.BoutiqueController;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/** Temporaire : les grades, Premium et leurs avantages. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    /** Sur le sol du monde plat, loin du spawn : (300, -60, 300). */
    private static BlockPos origin;
    private static int xpLevelBeforeDeath;

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

    /** Une commande tapee par le joueur lui-meme. */
    private static void as(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    private static void tp(ServerPlayer player, double dx, double dy, double dz, float yaw, float pitch) {
        command(player, String.format(java.util.Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f",
                player.getName().getString(), origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, yaw,
                pitch));
    }

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
            LOG.warn("dohy-shots: [grades] souris non deplacee", e);
        }
    }

    private static void log(String format, Object... args) {
        LOG.info("dohy-shots: [grades] " + format, args);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow();
    }

    /** Fait defiler le contenu du menu ouvert. */
    private static void scroll(double amount) {
        try {
            var screen = Minecraft.getInstance().gui.screen();
            var field = screen.getClass().getDeclaredField("content");
            field.setAccessible(true);
            Object content = field.get(screen);
            var method = content.getClass().getMethod("mouseScrolled", double.class, double.class, double.class);
            method.setAccessible(true);
            method.invoke(content, 0.0, 0.0, amount);
        } catch (Throwable t) {
            LOG.error("dohy-shots: ECHEC defilement", t);
        }
    }

    /** Ouvre l'ecran du chat, pour voir les messages recus. */
    private static void openChat() {
        try {
            Screen chat = null;
            for (var constructor : net.minecraft.client.gui.screens.ChatScreen.class.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 1 && types[0] == String.class) {
                    chat = (Screen) constructor.newInstance("");
                } else if (types.length == 2 && types[0] == String.class && types[1] == boolean.class) {
                    chat = (Screen) constructor.newInstance("", false);
                }
            }
            if (chat != null) {
                Minecraft.getInstance().gui.setScreen(chat);
            } else {
                log("pas de constructeur d'ecran de chat");
            }
        } catch (Throwable t) {
            LOG.error("dohy-shots: ECHEC chat", t);
        }
    }

    /** Tape une recherche dans l'onglet Acheter. */
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

    private static void state(ServerPlayer player, String when) {
        log("{} : grade {} premium {} j argent {} homes {} rtp {} ender {} rangees XP metier +{} pass +{} classique"
                        + " +{} taxe-{} annonces {} xp gardee {} tp direct {}", when, Grades.grade(player),
                Grades.premiumDays(player), Economy.balance(player), Grades.homes(player), Grades.rtpLeft(player),
                Grades.enderRows(player), Grades.jobXpBonus(player), Grades.passXpBonus(player),
                Grades.vanillaXpBonus(player), Grades.marketTaxCut(player), AuctionSales.slots(player),
                Grades.keepsXp(player), Grades.instantTeleport(player));
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
            Economy.set(player, 1_500_000_000L);
            player.getInventory().clearContent();
            state(player, "depart");
        }));
        ShotHarness.SHOTS.add(server("a1_boutique", 30, BoutiqueController.INSTANCE::open));
        ShotHarness.SHOTS.add(server("a2_page", 30, GradesController.INSTANCE::open));
        ShotHarness.SHOTS.add(client("a3_page_commandes", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(client("a4_page_tableau", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(server("b1_heros", 30, player -> {
            GradesController.handle(player, "buy:heros");
            state(player, "apres Heros");
            GradesController.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("b2_divinite", 30, player -> {
            GradesController.handle(player, "buy:divinite");
            GradesController.handle(player, "premium");
            state(player, "apres Divinite et Premium");
            GradesController.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(client("b3_commandes", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(server("c1_boutique_achete", 30, player -> {
            long before = JobApi.xp(player, Job.MINER);
            JobApi.grant(player, Job.MINER, 1000L);
            log("XP de metier : 1000 accordes, {} recus", JobApi.xp(player, Job.MINER) - before);
            int total = player.totalExperience;
            player.giveExperiencePoints(100);
            log("XP classique : 100 accordes, {} recus", player.totalExperience - total);
            log("taxe du marche international : {}", NationRank.fee(player));
            BoutiqueController.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("d1_ender", 30, player -> {
            var ender = player.getEnderChestInventory();
            ender.setItem(0, new ItemStack(item("minecraft:diamond"), 12));
            ender.setItem(4, new ItemStack(item("palamod:paladium_ingot"), 64));
            NonNullList<ItemStack> extra = NonNullList.withSize(EnderExtra.MAX_SLOTS, ItemStack.EMPTY);
            extra.set(2, new ItemStack(item("minecraft:emerald"), 20));
            extra.set(30, new ItemStack(item("minecraft:golden_apple"), 3));
            player.setData(PalaStorage.ENDER_EXTRA.get(), new EnderExtra(extra));
            fr.atraxterra.dohy.ui.view.UiNet.close(player);
            as(player, "ec");
        }));
        ShotHarness.SHOTS.add(client("d2_fermer", 10, () -> Minecraft.getInstance().gui.setScreen(null)));
        ShotHarness.SHOTS.add(server("e1_homes", 20, player -> {
            as(player, "sethome base");
            tp(player, 40.5, 0, 0.5, 90.0f, 10.0f);
            as(player, "sethome mine");
            as(player, "homes");
            as(player, "home base");
            as(player, "feed");
            as(player, "deathlocate");
        }));
        ShotHarness.SHOTS.add(client("e2_chat", 40, () -> {
            var connection = Minecraft.getInstance().player.connection;
            connection.sendChat("&aSalut &l:coeur: &6tout va bien :etoile: :epee: :soleil:");
            openChat();
            cursor(10, 10);
        }));
        ShotHarness.SHOTS.add(client("e3_tab", 20, () -> {
            Minecraft.getInstance().gui.setScreen(null);
            Minecraft.getInstance().options.keyPlayerList.setDown(true);
        }));
        ShotHarness.SHOTS.add(server("f1_condense", 20, player -> {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().options.keyPlayerList.setDown(false));
            var inventory = player.getInventory();
            inventory.setItem(9, new ItemStack(item("minecraft:iron_ingot"), 64));
            inventory.setItem(20, new ItemStack(item("minecraft:gold_nugget"), 30));
            inventory.setItem(14, new ItemStack(item("minecraft:raw_copper"), 20));
            inventory.setItem(30, new ItemStack(item("minecraft:wheat"), 9));
            inventory.setItem(27, new ItemStack(item("minecraft:cobblestone"), 40));
            inventory.setItem(12, new ItemStack(item("minecraft:cobblestone"), 30));
            inventory.setItem(33, new ItemStack(item("minecraft:dirt"), 12));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("minecraft:raw_iron"), 16));
            as(player, "furnace");
            as(player, "condense");
            as(player, "rangement");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("minecraft:carved_pumpkin")));
            as(player, "hat");
            as(player, "nv");
            as(player, "rtp");
            log("rtp restants {} ; tete {}", Grades.rtpLeft(player),
                    BuiltInRegistries.ITEM.getKey(player.getItemBySlot(
                            net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()));
        }));
        ShotHarness.SHOTS.add(client("f2_inventaire", 20, () -> {
            Minecraft.getInstance().gui.setScreen(
                    new fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen(Minecraft.getInstance().player));
            cursor(24, 24);
        }));
        ShotHarness.SHOTS.add(client("f3_chat", 15, () -> {
            Minecraft.getInstance().gui.setScreen(null);
            openChat();
        }));
        ShotHarness.SHOTS.add(server("g1_mort", 40, player -> {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(null));
            player.setExperienceLevels(30);
            xpLevelBeforeDeath = player.experienceLevel;
            log("avant la mort : niveau {}", xpLevelBeforeDeath);
            command(player, "kill " + player.getName().getString());
        }));
        ShotHarness.SHOTS.add(client("g2_reapparition", 40, () -> {
            try {
                var local = Minecraft.getInstance().player;
                local.getClass().getMethod("respawn").invoke(local);
            } catch (Throwable t) {
                LOG.error("dohy-shots: ECHEC reapparition", t);
            }
        }));
        ShotHarness.SHOTS.add(server("g3_xp", 20, player -> {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(null));
            log("apres la mort : niveau {} (avant {}), vivant {}", player.experienceLevel, xpLevelBeforeDeath,
                    player.isAlive());
        }));
        ShotHarness.SHOTS.add(server("h1_acheter", 30, ShopController::open));
        ShotHarness.SHOTS.add(client("h2_recherche", 20, () -> {
            cursor(10, 10);
            try {
                search("grade");
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: ECHEC recherche", e);
            }
        }));
        ShotHarness.SHOTS.add(server("i1_fiche", 30, player ->
                fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player, "grades")));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> LOG.info("dohy-shots: [fin] {}",
                List.of(player.getName().getString()))));
    }
}
