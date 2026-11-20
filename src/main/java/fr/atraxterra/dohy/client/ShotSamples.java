package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.farmbot.DoHyFarmBots;
import fr.atraxterra.dohy.farmbot.FarmBot;
import fr.atraxterra.dohy.farmbot.FarmBotMenu;
import fr.atraxterra.dohy.farmbot.FarmBots;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.menu.BoutiqueController;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : les bots de farming. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    /** Sur le sol du monde plat, loin du spawn : (300, -60, 300). */
    private static BlockPos origin;
    private static volatile FarmBot current;
    private static long farmerXp;
    private static long firstRound;

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

    private static BlockPos at(int dx, int dy, int dz) {
        return origin.offset(dx, dy, dz);
    }

    private static String xyz(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void set(ServerPlayer player, int dx, int dy, int dz, String block) {
        command(player, "setblock " + xyz(at(dx, dy, dz)) + " " + block);
    }

    private static void fill(ServerPlayer player, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        command(player, "fill " + xyz(at(x1, y1, z1)) + " " + xyz(at(x2, y2, z2)) + " " + block);
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
            LOG.warn("dohy-shots: [bots] souris non deplacee", e);
        }
    }

    private static void log(String format, Object... args) {
        LOG.info("dohy-shots: [bots] " + format, args);
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

    /** Le clic droit du joueur sur une face de bloc, par le vrai chemin du serveur. */
    private static InteractionResult click(ServerPlayer player, BlockPos pos, Direction face) {
        ItemStack held = player.getMainHandItem();
        String name = held.isEmpty() ? "main vide" : BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5d), face, pos, false);
        InteractionResult result = player.gameMode.useItemOn(player, player.level(), held, InteractionHand.MAIN_HAND,
                hit);
        log("clic droit{} sur {} ({}) avec {} -> {}", player.isShiftKeyDown() ? " accroupi" : "", xyz(pos),
                BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).getPath(), name, result);
        return result;
    }

    /** Le clic droit du joueur sur une entite : Player.interactOn, quelle que soit sa signature. */
    private static Object interact(ServerPlayer player, Entity entity) {
        for (Method method : Player.class.getMethods()) {
            if (!method.getName().equals("interactOn")) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (Entity.class.isAssignableFrom(types[i])) {
                    args[i] = entity;
                } else if (types[i] == InteractionHand.class) {
                    args[i] = InteractionHand.MAIN_HAND;
                } else if (types[i] == Vec3.class) {
                    args[i] = entity.position().add(0.0d, 0.6d, 0.0d);
                }
            }
            try {
                Object result = method.invoke(player, args);
                log("interactOn {} -> {}", java.util.Arrays.toString(types), result);
                return result;
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: ECHEC interactOn", e);
                return null;
            }
        }
        log("interactOn introuvable");
        return null;
    }

    private static int slotOf(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** Met en main la pile de cet objet (la retire de sa case). */
    private static ItemStack hold(ServerPlayer player, Item item) {
        int slot = slotOf(player, item);
        if (slot < 0) {
            log("rien a tenir : {}", BuiltInRegistries.ITEM.getKey(item));
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        int selected = player.getInventory().getSelectedSlot();
        if (slot != selected) {
            ItemStack old = player.getInventory().getItem(selected);
            player.getInventory().setItem(selected, stack);
            player.getInventory().setItem(slot, old);
        }
        return player.getMainHandItem();
    }

    /** Range ce qui est en main dans une autre case, et laisse la main vide. */
    private static void emptyHand(ServerPlayer player) {
        int selected = player.getInventory().getSelectedSlot();
        ItemStack held = player.getInventory().getItem(selected);
        if (held.isEmpty()) {
            return;
        }
        for (int slot = 0; slot < 36; slot++) {
            if (slot != selected && player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, held);
                player.getInventory().setItem(selected, ItemStack.EMPTY);
                return;
            }
        }
    }

    private static FarmBot findBot(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        List<FarmBot> bots = level.getEntitiesOfClass(FarmBot.class, new AABB(origin.getX() - 20, origin.getY() - 10,
                origin.getZ() - 20, origin.getX() + 30, origin.getY() + 10, origin.getZ() + 30));
        log("bots autour : {}", bots.size());
        return bots.isEmpty() ? null : bots.get(0);
    }

    private static String contents(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return "pas un contenant";
        }
        Map<String, Integer> totals = new TreeMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
            }
        }
        return totals.toString();
    }

    private static String bag(FarmBot bot) {
        Map<String, Integer> totals = new TreeMap<>();
        for (int slot = 0; slot < bot.inventory().getContainerSize(); slot++) {
            ItemStack stack = bot.inventory().getItem(slot);
            if (!stack.isEmpty()) {
                totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
            }
        }
        return totals.toString();
    }

    /** Le champ : cultures mures, jeunes (replantees), cases vides ; et la terre labouree restante. */
    private static String field(ServerLevel level) {
        int ripe = 0;
        int young = 0;
        int empty = 0;
        int farmland = 0;
        for (int dx = 2; dx <= 10; dx++) {
            for (int dz = 2; dz <= 10; dz++) {
                if (dx == 6 && dz == 6) {
                    continue;
                }
                BlockState state = level.getBlockState(at(dx, 0, dz));
                if (state.getBlock() instanceof CropBlock crop) {
                    if (crop.isMaxAge(state)) {
                        ripe++;
                    } else {
                        young++;
                    }
                } else {
                    empty++;
                }
                if (level.getBlockState(at(dx, -1, dz)).is(Blocks.FARMLAND)) {
                    farmland++;
                }
            }
        }
        return "mures " + ripe + ", jeunes " + young + ", vides " + empty + ", terre labouree " + farmland + " / 80";
    }

    private static String block(ServerLevel level, int dx, int dy, int dz) {
        BlockState state = level.getBlockState(at(dx, dy, dz));
        String text = state.toString();
        return text.replace("Block{minecraft:", "").replace("}", "");
    }

    private static void report(ServerPlayer player, String when) {
        FarmBot bot = current;
        ServerLevel level = (ServerLevel) player.level();
        if (bot == null) {
            log("{} : pas de bot", when);
            return;
        }
        log("{} : etat {} recoltes {} a recolter {} sac {} / 27 {} zone {} coffres {} pause {} vivant {}", when,
                bot.status(), bot.harvested(), bot.ripe(), bot.usedSlots(), bag(bot),
                bot.zone() == null ? "aucune" : bot.zone().label() + " " + xyz(bot.zone().min()) + " -> "
                        + xyz(bot.zone().max()), bot.chests().size(), bot.paused(), bot.isAlive());
        log("{} : champ {}", when, field(level));
        log("{} : extras melon {} / {} citrouille {} / {} verrue {} baies {} {} canne {} {} {} | {} {} cacao {}", when,
                block(level, 13, 0, 2), block(level, 12, 0, 2), block(level, 13, 0, 3), block(level, 12, 0, 3),
                block(level, 12, 0, 5), block(level, 13, 0, 6), block(level, 13, 0, 7), block(level, 12, 0, 9),
                block(level, 12, 1, 9), block(level, 12, 2, 9), block(level, 13, 0, 8), block(level, 13, 1, 8),
                block(level, 13, 0, 10));
        log("{} : citrouille de deco {}", when, block(level, 13, 0, 4));
        log("{} : coffre {} ; baril {}", when, contents(level, at(4, 0, 12)), contents(level, at(8, 0, 12)));
        log("{} : XP de Fermier {} (+{} depuis le depart), argent {}", when, JobApi.xp(player, Job.FARMER),
                JobApi.xp(player, Job.FARMER) - farmerXp, Economy.balance(player));
    }

    private static void menu(ServerPlayer player, String key) {
        FarmBotMenu.INSTANCE.handle(player, new UiNet.Action(Identifier.parse("dohy:farm_bot"), "card", key, 0));
        log("menu : {}", key);
    }

    private static void inventory(ServerPlayer player, String when) {
        List<String> items = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                items.add(slot + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + "x" + stack.getCount());
            }
        }
        log("{} : inventaire {}", when, items);
    }

    private static void build(ServerPlayer player) {
        // Le champ : 9 x 9 de terre labouree, l'eau au milieu.
        fill(player, -2, -1, -6, 16, 6, 16, "minecraft:air");
        fill(player, -2, -1, -6, 16, -1, 16, "minecraft:grass_block");
        fill(player, 2, -1, 2, 10, -1, 10, "minecraft:farmland[moisture=7]");
        fill(player, 2, 0, 2, 10, 0, 10, "minecraft:wheat[age=7]");
        fill(player, 4, 0, 2, 4, 0, 10, "minecraft:carrots[age=7]");
        fill(player, 5, 0, 2, 5, 0, 10, "minecraft:potatoes[age=7]");
        fill(player, 7, 0, 2, 7, 0, 10, "minecraft:beetroots[age=3]");
        set(player, 6, 0, 6, "minecraft:air");
        set(player, 6, -1, 6, "minecraft:water");
        // Au bord : pasteque et citrouille sur leur tige, verrue, baies, canne a sucre, cacao.
        set(player, 12, -1, 2, "minecraft:farmland[moisture=7]");
        set(player, 13, 0, 2, "minecraft:melon");
        set(player, 12, 0, 2, "minecraft:attached_melon_stem[facing=east]");
        set(player, 12, -1, 3, "minecraft:farmland[moisture=7]");
        set(player, 13, 0, 3, "minecraft:pumpkin");
        set(player, 12, 0, 3, "minecraft:attached_pumpkin_stem[facing=east]");
        // une citrouille posee en deco, sans tige : il doit la laisser
        set(player, 13, 0, 4, "minecraft:pumpkin");
        set(player, 12, -1, 5, "minecraft:soul_sand");
        set(player, 12, 0, 5, "minecraft:nether_wart[age=3]");
        set(player, 13, 0, 6, "minecraft:sweet_berry_bush[age=3]");
        set(player, 13, 0, 7, "minecraft:sweet_berry_bush[age=3]");
        set(player, 12, -1, 8, "minecraft:water");
        set(player, 12, 0, 9, "minecraft:sugar_cane");
        set(player, 12, 1, 9, "minecraft:sugar_cane");
        set(player, 12, 2, 9, "minecraft:sugar_cane");
        set(player, 13, 0, 8, "minecraft:sugar_cane");
        set(player, 13, 1, 8, "minecraft:sugar_cane");
        set(player, 14, 0, 10, "minecraft:jungle_log");
        set(player, 14, 1, 10, "minecraft:jungle_log");
        set(player, 13, 0, 10, "minecraft:cocoa[age=2,facing=east]");
        // Les coffres, hors du champ ; une plateforme de verre pour regarder.
        set(player, 4, 0, 12, "minecraft:chest[facing=north]");
        set(player, 8, 0, 12, "minecraft:barrel[facing=up]");
        set(player, 7, 4, -4, "minecraft:glass");
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 100, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "gamerule doDaylightCycle false");
            command(player, "gamerule advance_time false");
            command(player, "gamerule doMobSpawning false");
            command(player, "gamerule spawn_mobs false");
            command(player, "gamerule randomTickSpeed 0");
            command(player, "gamerule random_tick_speed 0");
            command(player, "difficulty peaceful");
            command(player, "kill @e[type=minecraft:slime]");
            player.setGameMode(GameType.SURVIVAL);
            origin = new BlockPos(300, -60, 300);
            tp(player, 6.5, 0, -5.5, 0.0f, 12.0f);
            command(player, "forceload add " + (origin.getX() - 8) + " " + (origin.getZ() - 8) + " "
                    + (origin.getX() + 24) + " " + (origin.getZ() + 24));
        }));
        // Les chunks du champ sont charges : on peut le construire.
        ShotHarness.SHOTS.add(server("a0b_champ", 60, player -> {
            build(player);
            command(player, "kill @e[type=minecraft:item]");
            Economy.set(player, 50_000_000L);
            player.getInventory().clearContent();
            farmerXp = JobApi.xp(player, Job.FARMER);
            log("depart : argent {} XP de Fermier {}", Economy.balance(player), farmerXp);
            log("depart : champ {}", field((ServerLevel) player.level()));
            ServerLevel level = (ServerLevel) player.level();
            log("depart : extras melon {} citrouille {} verrue {} baies {} canne {} {} cacao {} coffre {} baril {}",
                    block(level, 13, 0, 2), block(level, 13, 0, 3), block(level, 12, 0, 5), block(level, 13, 0, 6),
                    block(level, 12, 2, 9), block(level, 13, 1, 8), block(level, 13, 0, 10), block(level, 4, 0, 12),
                    block(level, 8, 0, 12));
        }));
        ShotHarness.SHOTS.add(server("a1_boutique", 30, BoutiqueController.INSTANCE::open));
        ShotHarness.SHOTS.add(client("a2_boutique", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(client("a3_boutique", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(client("a4_boutique", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(server("b1_achat_pose", 40, player -> {
            UiNet.close(player);
            long before = Economy.balance(player);
            String refusal = FarmBots.buyBot(player);
            log("achat : refus {} ; argent {} -> {} (-{}) ; bots sur lui {}", refusal, before,
                    Economy.balance(player), before - Economy.balance(player), FarmBots.carried(player));
            inventory(player, "apres l'achat");
            hold(player, DoHyFarmBots.FARM_BOT_ITEM.get());
            click(player, at(6, -1, -1), Direction.UP);
            current = findBot(player);
            FarmBot bot = current;
            ItemStack remote = player.getInventory().getItem(slotOf(player, DoHyFarmBots.BOT_REMOTE.get()));
            UUID piloted = remote.get(DoHyFarmBots.REMOTE_BOT.get());
            log("pose : bot {} a {} proprietaire {} ; telecommande reliee {} ; bots sur lui {} ; poses {}",
                    bot != null, bot == null ? "-" : xyz(bot.blockPosition()), bot == null ? "-" : bot.ownerName(),
                    bot != null && bot.getUUID().equals(piloted), FarmBots.carried(player), FarmBots.of(player).size());
            report(player, "apres la pose");
        }));
        ShotHarness.SHOTS.add(server("b2_bot_proche", 230, player -> tp(player, 6.5, 0, -3.4, 0.0f, 6.0f)));
        ShotHarness.SHOTS.add(server("c1_zone_coffres", 40, player -> {
            hold(player, DoHyFarmBots.BOT_REMOTE.get());
            click(player, at(1, -1, 1), Direction.UP);
            click(player, at(14, -1, 11), Direction.UP);
            player.setShiftKeyDown(true);
            click(player, at(4, 0, 12), Direction.UP);
            click(player, at(8, 0, 12), Direction.UP);
            click(player, at(1, -1, 13), Direction.UP);
            player.setShiftKeyDown(false);
            report(player, "zone et coffres");
            tp(player, 7.5, 5, -3.5, 0.0f, 30.0f);
        }));
        ShotHarness.SHOTS.add(server("c2_travail", 160, player -> report(player, "au travail")));
        ShotHarness.SHOTS.add(server("c3_menu_travail", 30, player -> {
            report(player, "au travail, plus tard");
            FarmBot bot = current;
            if (bot != null) {
                tp(player, bot.getX() - origin.getX(), 0, bot.getZ() - origin.getZ() - 2.0d, 0.0f, 10.0f);
                emptyHand(player);
                interact(player, bot);
            }
        }));
        ShotHarness.SHOTS.add(server("c3b_retour", 60, player -> {
            UiNet.close(player);
            tp(player, 7.5, 5, -3.5, 0.0f, 30.0f);
            hold(player, DoHyFarmBots.BOT_REMOTE.get());
        }));
        ShotHarness.SHOTS.add(server("c4_fini", 20, player -> {
            ShotHarness.until = () -> current != null && current.harvested() >= 88
                    && current.status() == FarmBot.Status.WAITING && current.usedSlots() == 0;
            report(player, "avant la fin du premier tour");
        }));
        ShotHarness.SHOTS.add(server("c5_bilan", 20, player -> {
            report(player, "premier tour fini");
            firstRound = current == null ? 0 : current.harvested();
        }));
        ShotHarness.SHOTS.add(client("c6_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("c7_repousse", 60, player -> {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(null));
            // Ce qui a ete replante pousse d'un coup : le bot doit refaire un tour.
            fill(player, 2, 0, 2, 10, 0, 10, "minecraft:wheat[age=7] replace minecraft:wheat");
            fill(player, 2, 0, 2, 10, 0, 10, "minecraft:carrots[age=7] replace minecraft:carrots");
            fill(player, 2, 0, 2, 10, 0, 10, "minecraft:potatoes[age=7] replace minecraft:potatoes");
            fill(player, 2, 0, 2, 10, 0, 10, "minecraft:beetroots[age=3] replace minecraft:beetroots");
            log("repousse : champ {}", field((ServerLevel) player.level()));
        }));
        ShotHarness.SHOTS.add(server("c8_second_tour", 20, player -> {
            ShotHarness.until = () -> current != null && current.harvested() >= firstRound + 78
                    && current.status() == FarmBot.Status.WAITING && current.usedSlots() == 0;
            report(player, "second tour en cours");
        }));
        ShotHarness.SHOTS.add(server("d1_menu", 40, player -> {
            report(player, "second tour fini");
            FarmBot bot = current;
            if (bot != null) {
                tp(player, bot.getX() - origin.getX(), 0, bot.getZ() - origin.getZ() - 3.0d, 0.0f, 10.0f);
                emptyHand(player);
                interact(player, bot);
            }
        }));
        ShotHarness.SHOTS.add(client("d2_menu_bas", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(server("d3_coffre", 30, player -> {
            UiNet.close(player);
            ServerLevel level = (ServerLevel) player.level();
            BlockPos chest = at(4, 0, 12);
            MenuProvider provider = level.getBlockState(chest).getMenuProvider(level, chest);
            log("coffre : menu {}", provider != null);
            if (provider != null) {
                player.openMenu(provider);
            }
        }));
        ShotHarness.SHOTS.add(server("e1_pause", 40, player -> {
            player.closeContainer();
            FarmBot bot = current;
            if (bot != null) {
                interact(player, bot);
                menu(player, "pause");
                log("pause : {} ({})", bot.paused(), bot.status());
            }
        }));
        ShotHarness.SHOTS.add(server("f1_ranger", 30, player -> {
            menu(player, "pause");
            log("reprise : pause {}", current == null ? "-" : current.paused());
            menu(player, "pickup");
            inventory(player, "apres le rangement");
            int slot = slotOf(player, DoHyFarmBots.FARM_BOT_ITEM.get());
            if (slot >= 0) {
                ItemStack item = player.getInventory().getItem(slot);
                List<String> lines = new ArrayList<>();
                for (Component line : item.getTooltipLines(Item.TooltipContext.of(player.level()), player,
                        TooltipFlag.NORMAL)) {
                    lines.add(line.getString());
                }
                log("bot range : reglage {} ; bulle {}", item.get(DoHyFarmBots.SETUP.get()), lines);
            }
            log("bot range : poses {} vivant {}", FarmBots.of(player).size(), current != null && current.isAlive());
        }));
        ShotHarness.SHOTS.add(server("f2_reposer", 40, player -> {
            UiNet.close(player);
            hold(player, DoHyFarmBots.FARM_BOT_ITEM.get());
            click(player, at(6, -1, -1), Direction.UP);
            current = findBot(player);
            report(player, "repose");
            tp(player, 7.5, 5, -3.5, 0.0f, 30.0f);
            hold(player, DoHyFarmBots.BOT_REMOTE.get());
        }));
        ShotHarness.SHOTS.add(server("g1_limite", 30, player -> {
            List<String> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                long before = Economy.balance(player);
                String refusal = FarmBots.buyBot(player);
                results.add((refusal == null ? "achete" : refusal) + " (-" + (before - Economy.balance(player)) + ")");
            }
            log("limite : {} ; poses {} sur lui {}", results, FarmBots.of(player).size(), FarmBots.carried(player));
            as(player, "bots");
        }));
        ShotHarness.SHOTS.add(client("g2_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(client("h1_inventaire", 20, () -> {
            Minecraft.getInstance().gui.setScreen(
                    new fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen(Minecraft.getInstance().player));
            cursor(24, 24);
        }));
        ShotHarness.SHOTS.add(server("i1_fiche", 30, player ->
                fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player, "bots_farming")));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> report(player, "fin")));
    }
}
