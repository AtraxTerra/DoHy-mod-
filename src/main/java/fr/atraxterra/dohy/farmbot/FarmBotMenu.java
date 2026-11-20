package fr.atraxterra.dohy.farmbot;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le menu d'un bot de farming, clic droit dessus : ce qu'il fait, sa zone, ses coffres, son sac, et
 * de quoi le mettre en pause, le faire vider, reprendre sa recolte ou le ranger.
 */
public final class FarmBotMenu extends MenuController {

    public static final FarmBotMenu INSTANCE = new FarmBotMenu();

    private static final double REACH = 12.0d;
    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFAC400;
    private static final int RED = 0xFFE5484D;
    private static final int BLUE = 0xFF27C4E8;
    private static final int GREY = 0xFF55545C;

    private final Map<UUID, UUID> open = new ConcurrentHashMap<>();

    private FarmBotMenu() {
        super("farm_bot");
    }

    void openFor(ServerPlayer player, FarmBot bot) {
        open.put(player.getUUID(), bot.getUUID());
        open(player);
    }

    /** Le bot ouvert par ce joueur, s'il est encore la, a lui et a portee. */
    private FarmBot bot(ServerPlayer player) {
        UUID id = open.get(player.getUUID());
        if (id == null || !(player.level() instanceof ServerLevel level)
                || !(level.getEntity(id) instanceof FarmBot bot) || !bot.isAlive() || !bot.ownedBy(player)
                || bot.distanceToSqr(player) > REACH * REACH) {
            return null;
        }
        return bot;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        FarmBot bot = bot(player);
        if (bot == null) {
            return new MenuView(id(), "Bot de farming", "Approche-toi du bot et fais clic droit dessus.", List.of(),
                    List.of(), true);
        }
        FarmBot.Status status = bot.status();
        int used = bot.usedSlots();
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("ETAT", status.label().toUpperCase(Locale.ROOT), status.color()),
                new MenuView.Stat("RECOLTES", MoneyFormat.exact(bot.harvested()), 0xFFFFFFFF),
                new MenuView.Stat("SAC", used + " / " + FarmBot.SLOTS, used >= FarmBot.SLOTS ? RED : 0xFFFFFFFF));
        List<MenuView.Section> sections = new ArrayList<>();
        sections.add(new MenuView.Section("SA ZONE", "resources", MenuView.GRID, zone(player, bot)));
        sections.add(new MenuView.Section("SES COFFRES|" + bot.chests().size() + " / " + FarmBot.MAX_CHESTS, "craft",
                MenuView.GRID, chests(player, bot)));
        List<Card> bag = bag(bot);
        if (!bag.isEmpty()) {
            sections.add(new MenuView.Section("SON SAC", "armor", MenuView.TILES, bag));
        }
        sections.add(new MenuView.Section("ACTIONS", "upgrade", MenuView.GRID, actions(bot)));
        return new MenuView(id(), "Bot de farming", subtitle(bot), stats, sections, true);
    }

    private static String subtitle(FarmBot bot) {
        if (bot.zone() == null) {
            return "Telecommande en main : clic droit sur deux coins du champ, il le cultivera.";
        }
        if (bot.chests().isEmpty()) {
            return "Lie-lui un coffre : telecommande en main, accroupi + clic droit sur le coffre.";
        }
        return "Il recolte ce qui est mur, replante, et vide son sac dans ses coffres. A " + bot.ownerName()
                + " : l'XP de Fermier aussi.";
    }

    private static List<Card> zone(ServerPlayer player, FarmBot bot) {
        List<Card> cards = new ArrayList<>();
        BotZone zone = bot.zone();
        if (zone == null) {
            cards.add(Card.of(new ItemStack(Items.FARMLAND), "Pas de zone", List.of("Telecommande : clic droit sur",
                            "deux coins du champ"))
                    .accent(GOLD).tooltip(List.of("Jusqu'a " + BotZone.MAX_SIDE + " x " + BotZone.MAX_SIDE + " blocs",
                            "A moins de " + FarmBot.ZONE_RANGE + " blocs du bot")));
        } else {
            BlockPos min = zone.min();
            BlockPos max = zone.max();
            cards.add(Card.of(new ItemStack(Items.FARMLAND), zone.label(), List.of(
                            "De " + min.getX() + " " + min.getZ() + " a " + max.getX() + " " + max.getZ(),
                            "Clic droit : effacer la zone"))
                    .accent(GREEN).right("zone_clear|effacer la zone")
                    .tooltip(List.of("Pour la changer : telecommande en main,", "clic droit sur deux nouveaux coins")));
            cards.add(Card.of(new ItemStack(Items.WHEAT), bot.ripe() + " a recolter", List.of(
                            "Cultures mures de la zone", "Recomptees toutes les 3 s"))
                    .accent(bot.ripe() > 0 ? GREEN : GREY)
                    .tooltip(List.of("Ble, carottes, pommes de terre, betteraves,", "cultures du PalaMod et de"
                            + " l'agriculture,", "verrues, cacao, pasteques, citrouilles,", "canne a sucre, baies")));
        }
        boolean hasRemote = remote(player) != null;
        Card remote = Card.of(new ItemStack(DoHyFarmBots.BOT_REMOTE.get()), "Telecommande", List.of(
                        hasRemote ? "Clic : la relier a ce bot" : MoneyFormat.exact(DoHyFarmBots.REMOTE_PRICE) + " $",
                        hasRemote ? "Zone et coffres se reglent avec" : "Clic : en acheter une"))
                .accent(BLUE);
        cards.add(remote.action(hasRemote ? "remote|relier la telecommande" : "remote_buy|acheter"));
        return cards;
    }

    private static List<Card> chests(ServerPlayer player, FarmBot bot) {
        List<Card> cards = new ArrayList<>();
        List<BlockPos> chests = bot.chests();
        for (int index = 0; index < chests.size(); index++) {
            BlockPos pos = chests.get(index);
            Item icon = player.level().getBlockState(pos).getBlock().asItem();
            boolean full = bot.chestFull(pos);
            int distance = (int) Math.round(Math.sqrt(bot.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d,
                    pos.getZ() + 0.5d)));
            cards.add(Card.of(new ItemStack(icon == Items.AIR ? Items.CHEST : icon), "Coffre " + (index + 1), List.of(
                            pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                            full ? "Plein" : "A " + distance + " blocs  ·  clic droit : delier"))
                    .accent(full ? RED : GREEN).badge(full ? "PLEIN" : "")
                    .right("unlink:" + index + "|delier ce coffre"));
        }
        if (chests.size() < FarmBot.MAX_CHESTS) {
            cards.add(Card.of(new ItemStack(Items.CHEST), chests.isEmpty() ? "Aucun coffre" : "Lier un coffre", List.of(
                            "Telecommande en main, accroupi", "+ clic droit sur le coffre"))
                    .accent(chests.isEmpty() ? GOLD : GREY)
                    .tooltip(List.of("Coffre, baril, tiroir, silo... tout ce", "qui garde des objets, a moins de "
                            + FarmBot.CHEST_RANGE + " blocs", "de la zone. Le plus proche d'abord ;",
                            "plein, il passe au suivant.")));
        }
        return cards;
    }

    /** Le sac du bot : une vignette par objet, le total en pastille. */
    private static List<Card> bag(FarmBot bot) {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (int slot = 0; slot < bot.inventory().getContainerSize(); slot++) {
            ItemStack stack = bot.inventory().getItem(slot);
            if (!stack.isEmpty()) {
                totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        List<Card> tiles = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : totals.entrySet()) {
            ItemStack icon = new ItemStack(entry.getKey());
            tiles.add(Card.of(icon, MenuView.text(icon.getHoverName()), List.of("x" + entry.getValue()))
                    .badge(String.valueOf(entry.getValue())));
        }
        return tiles;
    }

    private static List<Card> actions(FarmBot bot) {
        List<Card> cards = new ArrayList<>();
        cards.add(Card.of(new ItemStack(Items.DYE.pick(bot.paused() ? DyeColor.LIME : DyeColor.GRAY)),
                        bot.paused() ? "Reprendre" : "Mettre en pause", List.of(
                                bot.paused() ? "Il se remet au travail" : "Il s'arrete ou il est", ""))
                .accent(bot.paused() ? GREEN : GOLD)
                .action("pause|" + (bot.paused() ? "reprendre" : "mettre en pause")));
        boolean carrying = bot.usedSlots() > 0;
        Card store = Card.of(new ItemStack(Items.HOPPER), "Vider maintenant", List.of("Il va porter son sac",
                "a ses coffres")).accent(BLUE);
        cards.add(carrying && !bot.chests().isEmpty() ? store.action("store|vider dans les coffres")
                : store.state(MenuView.DISABLED));
        Card take = Card.of(new ItemStack(Items.BUNDLE), "Tout reprendre", List.of("Son sac passe dans",
                "le tien")).accent(BLUE);
        cards.add(carrying ? take.action("take|tout reprendre") : take.state(MenuView.DISABLED));
        cards.add(Card.of(new ItemStack(DoHyFarmBots.FARM_BOT_ITEM.get()), "Ranger le bot", List.of(
                        "Il revient dans ton sac", "avec sa zone et ses coffres"))
                .accent(RED).action("pickup|ranger le bot")
                .confirm("Ranger le bot ?|Il revient dans ton inventaire avec sa zone, ses coffres et son sac."));
        return cards;
    }

    /** La telecommande en main, sinon la premiere du sac. */
    private static ItemStack remote(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof BotRemoteItem) {
            return held;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BotRemoteItem) {
                return stack;
            }
        }
        return null;
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        FarmBot bot = bot(player);
        if (bot == null) {
            return;
        }
        if (key.startsWith("unlink:")) {
            try {
                bot.unlink(Integer.parseInt(key.substring(7)));
                success(player);
            } catch (NumberFormatException malformed) {
                failure(player);
            }
            return;
        }
        switch (key) {
            case "pause" -> {
                bot.setPaused(!bot.paused());
                success(player);
            }
            case "store" -> {
                bot.storeNow();
                success(player);
            }
            case "take" -> {
                for (int slot = 0; slot < bot.inventory().getContainerSize(); slot++) {
                    ItemStack stack = bot.inventory().removeItemNoUpdate(slot);
                    if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                }
                bot.inventory().setChanged();
                success(player);
            }
            case "zone_clear" -> {
                bot.setZone(null);
                success(player);
            }
            case "remote" -> {
                ItemStack remote = remote(player);
                if (remote != null) {
                    BotRemoteItem.select(player, remote, bot);
                }
            }
            case "remote_buy" -> {
                if (!Economy.tryDebit(player, DoHyFarmBots.REMOTE_PRICE)) {
                    report(player, "Il te faut " + MoneyFormat.exact(DoHyFarmBots.REMOTE_PRICE) + " $ en liquide.", "");
                    return;
                }
                ItemStack remote = new ItemStack(DoHyFarmBots.BOT_REMOTE.get());
                BotRemoteItem.select(player, remote, bot);
                if (!player.getInventory().add(remote)) {
                    player.drop(remote, false);
                }
            }
            case "pickup" -> {
                UiNet.close(player);
                bot.pickUp(player);
                player.sendSystemMessage(Component.literal("Bot range dans ton inventaire.")
                        .withStyle(ChatFormatting.GREEN));
            }
            default -> {
            }
        }
    }
}
