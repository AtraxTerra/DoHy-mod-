package fr.atraxterra.dohy.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.underground.DoHyUnderground;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.menu.Menus;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.UiNet;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : les grandes machines de l agriculture. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<String> OWN = Set.of("dohy", "palamod", "palarpg");
    private static final Pattern RISKY_BLOCK = Pattern.compile(
            "tnt|dynamite|bomb|nuke|explos|lucky|portal|_fire|^fire|lava|landmine|detonat");
    private static final Pattern RISKY_ENTITY = Pattern.compile("missile|nuke|bomb|tnt|grenade|rocket|explos|shell"
            + "|bullet|arrow|projectile|warhead|strike|meteor|bolt|spear|fireball|ball|dynamite|potion|orb|laser|beam"
            + "|shot|lucky|firework|flare|dart|knife|shuriken|snowball|egg|pearl|trident|harpoon|hook|bobber");
    private static final Pattern GLITCH = Pattern.compile(
            "(?i)\\bnull\\b|\\bNaN\\b|Infinity|%[sdf]|\\{\\}|\\$\\{|\\b(item|block|entity|tooltip|message|gui|menu)"
                    + "\\.(dohy|palamod|palarpg)\\.[a-z0-9_.]+");
    private static final Pattern TRANSLATE = Pattern.compile("\"translate\":\"([^\"]+)\"");
    private static final String ALLOWED = "«»—·×";

    private static final int BX = 3200;
    private static final int BZ = 3200;
    private static final int EX = 3200;
    private static final int EZ = 3420;
    private static final int Y = -60;

    private static volatile UUID me;
    private static final int G = fr.atraxterra.dohy.farmland.FarmlandMap.G;
    private static final Map<BlockPos, String> PLACED = new LinkedHashMap<>();
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static int side;

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
                    LOG.error("dohy-bug: ECHEC etape " + name, t);
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
                LOG.error("dohy-bug: ECHEC client " + name, t);
            }
            return null;
        });
    }

    private static void op(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player)
                .withPosition(player.position()).withLevel((ServerLevel) player.level()), command);
    }

    private static void as(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    private static void log(String format, Object... args) {
        LOG.info("dohy-bug: " + format, args);
    }

    // -- Les textes ------------------------------------------------------------------------------

    /** Les cles de traduction sans texte, dans un composant et ses enfants. */
    private static void missing(Component component, Set<String> out) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (!Language.getInstance().has(translatable.getKey()) && translatable.getFallback() == null) {
                out.add(translatable.getKey());
            }
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component inner) {
                    missing(inner, out);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            missing(sibling, out);
        }
    }

    /** Les signes d'un texte casse : « null », NaN, %s, cle brute, et les caracteres hors ASCII. */
    private static void inspect(String text, List<String> flags, Set<Character> exotic) {
        if (text == null) {
            flags.add("texte null");
            return;
        }
        if (!text.isEmpty() && text.charAt(0) == MenuView.TRANSLATED) {
            Matcher keys = TRANSLATE.matcher(text);
            while (keys.find()) {
                if (!Language.getInstance().has(keys.group(1))) {
                    flags.add("cle sans traduction " + keys.group(1));
                }
            }
            return;
        }
        Matcher glitch = GLITCH.matcher(text);
        if (glitch.find()) {
            flags.add("'" + glitch.group() + "' dans « " + (text.length() > 90 ? text.substring(0, 90) + "..." : text)
                    + " »");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127 && ALLOWED.indexOf(c) < 0) {
                exotic.add(c);
            }
        }
    }

    private static void views(ServerPlayer player, String label) {
        MinecraftServer server = player.level().getServer();
        int ok = 0;
        for (MenuController menu : Menus.all()) {
            String id = menu.id().toString();
            try {
                MenuView view = menu.view(player);
                if (view == null) {
                    log("vue {} [{}] : NULL", id, label);
                    continue;
                }
                List<String> flags = new ArrayList<>();
                Set<Character> exotic = new TreeSet<>();
                view.map(text -> {
                    inspect(text, flags, exotic);
                    return text == null ? "" : text;
                });
                int cards = 0;
                int tall = 0;
                for (MenuView.Section section : view.sections()) {
                    cards += section.cards().size();
                    for (MenuView.Card card : section.cards()) {
                        if (card.lines().size() > 3) {
                            tall++;
                            log("carte haute {} [{}] section « {} » (mise en page {}) : « {} » {} lignes {}", id, label,
                                    section.title(), section.layout(), card.title(), card.lines().size(), card.lines());
                        }
                    }
                }
                RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                UiNet.Show.STREAM_CODEC.encode(buffer, new UiNet.Show(view));
                int size = buffer.readableBytes();
                UiNet.Show.STREAM_CODEC.decode(buffer);
                buffer.release();
                ok++;
                log("vue {} [{}] : « {} » {} sections, {} cartes{}, {} octets{}{}", id, label, view.title(),
                        view.sections().size(), cards, tall > 0 ? " (" + tall + " a plus de 3 lignes)" : "", size,
                        flags.isEmpty() ? "" : " ; SIGNES " + flags, exotic.isEmpty() ? "" : " ; hors ASCII " + exotic);
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC vue " + id + " [" + label + "]", t);
            }
        }
        log("vues [{}] : {} / {} sans exception", label, ok, Menus.all().size());
    }

    private static void openMenu(ServerPlayer player, MenuController menu) {
        try {
            menu.open(player);
        } catch (Throwable t) {
            LOG.error("dohy-bug: ECHEC ouverture " + menu.id(), t);
        }
    }

    private static final Pattern RISKY_ACTION = Pattern.compile(
            "(?i)dissou|quitt|supprim|abandon|reset|reinit|disband|leave|delete|detrui|exil|renonc|bannir|kick|liquid");

    private static String key(String action) {
        int bar = action.indexOf('|');
        return bar < 0 ? action : action.substring(0, bar);
    }

    private static void clicks(ServerPlayer player) {
        int total = 0;
        int failed = 0;
        int skipped = 0;
        for (MenuController menu : Menus.all()) {
            MenuView view;
            try {
                view = menu.view(player);
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC vue avant clics " + menu.id(), t);
                continue;
            }
            List<String[]> actions = new ArrayList<>();
            for (MenuView.Section section : view.sections()) {
                for (MenuView.Card card : section.cards()) {
                    String text = card.title() + " " + card.confirm() + " " + card.action() + " " + card.rightAction();
                    if (RISKY_ACTION.matcher(text).find()) {
                        skipped++;
                        continue;
                    }
                    // Comme le client : ce qui suit la barre est le libelle de l'action, pas sa cle.
                    if (!card.action().isEmpty()) {
                        actions.add(new String[] {key(card.action()), "0"});
                        actions.add(new String[] {key(card.action()), "2"});
                    }
                    if (!card.rightAction().isEmpty()) {
                        actions.add(new String[] {key(card.rightAction()), "1"});
                    }
                }
            }
            int done = 0;
            for (String[] action : actions) {
                total++;
                try {
                    menu.handle(player, new UiNet.Action(menu.id(), "card", action[0], Integer.parseInt(action[1])));
                    done++;
                } catch (Throwable t) {
                    failed++;
                    LOG.error("dohy-bug: ECHEC clic " + menu.id() + " « " + action[0] + " » bouton " + action[1], t);
                }
            }
            UiNet.close(player);
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
            log("clics {} : {} / {} sans exception", menu.id(), done, actions.size());
        }
        log("clics : {} actions, {} en echec, {} cartes ecartees", total, failed, skipped);
    }

    private static MenuController menu(String path) {
        Identifier id = UiNet.id(path);
        for (MenuController menu : Menus.all()) {
            if (menu.id().equals(id)) {
                return menu;
            }
        }
        throw new IllegalStateException("menu inconnu " + path);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }


    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    // -- Les grandes machines de l'agriculture ----------------------------------------------------

    private static int gx;
    private static int gy;
    private static int gz;

    /** Cache ou montre l'interface (F1), par reflexion : 26.2 l'a sortie des options. */
    private static void hud(boolean visible) {
        Object hud = Minecraft.getInstance().gui.hud;
        try {
            for (java.lang.reflect.Method m : hud.getClass().getMethods()) {
                if (m.getName().equals("setHidden") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == boolean.class) {
                    m.invoke(hud, !visible);
                    return;
                }
            }
            for (Class<?> c = hud.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.getType() == boolean.class && f.getName().toLowerCase().contains("hid")) {
                        f.setAccessible(true);
                        f.setBoolean(hud, !visible);
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            LOG.error("dohy-bug: bascule d'interface", t);
        }
    }

    private static ServerLevel over(ServerPlayer player) {
        return ((ServerLevel) player.level()).getServer().overworld();
    }

    private static void eye(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        player.setGameMode(GameType.SPECTATOR);
        player.teleport(new TeleportTransition(over(player), new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                TeleportTransition.DO_NOTHING));
    }

    /** Pose une machine comme un joueur : l'objet en main, un clic sur le sol, le regard vers le sud. */
    private static String place(ServerPlayer player, Item item, BlockPos target) {
        ServerLevel level = over(player);
        player.setGameMode(GameType.CREATIVE);
        player.teleport(new TeleportTransition(level, new Vec3(target.getX() + 0.5d, target.getY(), target.getZ() - 4.5d),
                Vec3.ZERO, 0.0f, 30.0f, TeleportTransition.DO_NOTHING));
        player.setYRot(0.0f);
        player.setYHeadRot(0.0f);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos ground = target.below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0d, 0.5d, 0.0d), Direction.UP, ground, false);
        InteractionResult result = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
        return result.getClass().getSimpleName();
    }

    /** L'etat d'une machine posee : la commande, deployee ou non, et ses parties presentes. */
    private static void census(ServerPlayer player, fr.atraxterra.dohy.agri.machine.BigMachine machine, BlockPos pos,
                               String label) {
        ServerLevel level = over(player);
        BlockState state = level.getBlockState(pos);
        boolean formed = state.hasProperty(fr.atraxterra.dohy.agri.machine.BigMachines.FORMED)
                && state.getValue(fr.atraxterra.dohy.agri.machine.BigMachines.FORMED);
        Direction facing = state.hasProperty(fr.atraxterra.dohy.agri.machine.BigMachines.FACING)
                ? state.getValue(fr.atraxterra.dohy.agri.machine.BigMachines.FACING) : Direction.NORTH;
        int parts = 0;
        int cells = 0;
        for (fr.atraxterra.dohy.agri.machine.BigMachine.Cell cell : machine.cells()) {
            if (machine.isController(cell.x(), cell.y(), cell.z())) {
                continue;
            }
            cells++;
            if (level.getBlockState(machine.toWorld(pos, facing, cell.x(), cell.y(), cell.z())).is(machine.part())) {
                parts++;
            }
        }
        log("machine {} [{}] : {} ; deployee {} ; facing {} ; parties {} / {} ; lit {}", machine.id(), label,
                BuiltInRegistries.BLOCK.getKey(state.getBlock()), formed, facing, parts, cells,
                state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                        ? state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) : "-");
    }

    private static BlockPos at(int dx, int dz) {
        return new BlockPos(gx + dx, gy, gz + dz);
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(8)));
        ShotHarness.SHOTS.add(server("a1_terrain", 60, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule advance_time false");
            op(player, "gamerule advance_weather false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty peaceful");
            player.getInventory().clearContent();
            ServerLevel level = over(player);
            gx = 300;
            gz = 300;
            level.getChunk(gx >> 4, gz >> 4);
            gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
            for (int x = -12; x <= 36; x++) {
                for (int z = -16; z <= 12; z++) {
                    level.setBlock(new BlockPos(gx + x, gy - 1, gz + z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int y = 0; y < 8; y++) {
                        level.setBlock(new BlockPos(gx + x, gy + y, gz + z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            eye(player, gx + 10.5d, gy + 6.0d, gz - 14.0d, 0.0f, 12.0f);
            log("terrain : sol {} {} {}", gx, gy, gz);
        }));
        ShotHarness.SHOTS.add(server("a2_pose", 80, player -> {
            ServerLevel level = over(player);
            log("pose moulin : {}", place(player, fr.atraxterra.dohy.agri.DoHyAgri.FLOUR_MILL_ITEM.get(), at(0, 0)));
            log("pose biogenerateur : {}", place(player, fr.atraxterra.dohy.agri.DoHyAgri.BIOGENERATOR_ITEM.get(), at(3, 0)));
            log("pose machine a pain : {}", place(player, fr.atraxterra.dohy.agri.DoHyAgri.BREAD_MACHINE_ITEM.get(), at(6, 0)));
            log("pose silo : {}", place(player, fr.atraxterra.dohy.agri.DoHyAgri.SILO_ITEM.get(), at(11, 0)));
            log("pose machine a OGM : {}", place(player, fr.atraxterra.dohy.agri.DoHyAgri.GMO_MACHINE_ITEM.get(), at(15, 0)));
            log("pose pompe : {}", place(player, fr.atraxterra.dohy.agri.DoHyAgri.FUEL_PUMP_ITEM.get(), at(19, 0)));
            // un silo sans la place : un bloc de pierre dans sa construction
            level.setBlock(at(26, 1).above(2), Blocks.STONE.defaultBlockState(), 3);
            log("pose silo sans place : {} -> {}", place(player, fr.atraxterra.dohy.agri.DoHyAgri.SILO_ITEM.get(), at(26, 0)),
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(at(26, 0)).getBlock()));
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.FLOUR_MILL, at(0, 0), "pose");
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.BIOGENERATOR, at(3, 0), "pose");
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.BREAD_MACHINE, at(6, 0), "pose");
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.SILO, at(11, 0), "pose");
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.GMO_MACHINE, at(15, 0), "pose");
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.FUEL_PUMP, at(19, 0), "pose");
            // le biogenerateur brule du fourrage : il alimente le moulin et la machine a pain qui le touchent
            if (level.getBlockEntity(at(3, 0)) instanceof fr.atraxterra.dohy.agri.AgriMachineBlockEntity bio) {
                log("biogenerateur : {} grains pris", bio.addFuel(64));
            }
            // du grain par une partie du silo (son toit) : la capacite renvoie a la commande
            BlockPos roof = at(11, 0).above(4);
            net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> handler =
                    level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, roof, Direction.UP);
            int moved = 0;
            if (handler != null) {
                try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                             net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    moved = handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.WHEAT)),
                            48, tx);
                    tx.commit();
                }
            }
            log("silo par le toit : partie {} ; capacite {} ; {} ble entre ; silo {}", BuiltInRegistries.BLOCK.getKey(
                    level.getBlockState(roof).getBlock()), handler != null, moved,
                    level.getBlockEntity(at(11, 0)) instanceof fr.atraxterra.dohy.agri.SiloBlockEntity silo ? silo.total() : -1);
            eye(player, gx + 10.5d, gy + 7.0d, gz - 15.0d, 0.0f, 14.0f);
        }));
        ShotHarness.SHOTS.add(client("a3_sans_interface", 10, () -> hud(false)));
        ShotHarness.SHOTS.add(server("b1_ensemble", 120, player -> {
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.FLOUR_MILL, at(0, 0), "en marche");
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.BIOGENERATOR, at(3, 0), "en marche");
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.BREAD_MACHINE, at(6, 0), "en marche");
            eye(player, gx + 10.5d, gy + 9.0d, gz - 17.0d, 0.0f, 18.0f);
        }));
        ShotHarness.SHOTS.add(server("b2_ferme_de_biais", 60,
                player -> eye(player, gx - 8.0d, gy + 7.0d, gz - 10.0d, -58.0f, 20.0f)));
        ShotHarness.SHOTS.add(server("b3_moulin", 40, player -> eye(player, gx - 5.0d, gy + 5.0d, gz - 6.0d, -40.0f, 19.0f)));
        ShotHarness.SHOTS.add(server("b4_four", 40, player -> eye(player, gx + 11.0d, gy + 4.0d, gz - 5.5d, 37.0f, 18.0f)));
        ShotHarness.SHOTS.add(server("b5_biogenerateur", 40,
                player -> eye(player, gx + 3.5d, gy + 3.5d, gz - 5.5d, 0.0f, 18.0f)));
        ShotHarness.SHOTS.add(server("b6_silo", 40, player -> eye(player, gx + 16.0d, gy + 3.0d, gz - 7.5d, 29.0f, 3.0f)));
        ShotHarness.SHOTS.add(server("b7_ogm", 40, player -> eye(player, gx + 19.0d, gy + 4.5d, gz - 4.5d, 35.0f, 26.0f)));
        ShotHarness.SHOTS.add(server("b8_pompe", 40, player -> eye(player, gx + 23.5d, gy + 3.0d, gz - 3.0d, 41.0f, 16.0f)));
        ShotHarness.SHOTS.add(server("b9_de_dos", 60, player -> eye(player, gx + 10.5d, gy + 8.0d, gz + 14.0d, 180.0f, 16.0f)));
        ShotHarness.SHOTS.add(server("c1_casse", 40, player -> {
            ServerLevel level = over(player);
            // une partie de la pompe cassee : la pompe tombe en objet, toutes ses parties partent
            player.setGameMode(GameType.SURVIVAL);
            BlockPos part = at(20, 1).above(2);
            String before = BuiltInRegistries.BLOCK.getKey(level.getBlockState(part).getBlock()).toString();
            boolean broken = player.gameMode.destroyBlock(part);
            List<String> drops = new ArrayList<>();
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at(19, 0)).inflate(4.0d))) {
                drops.add(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()) + " x" + item.getItem().getCount());
                item.discard();
            }
            census(player, fr.atraxterra.dohy.agri.machine.BigMachine.FUEL_PUMP, at(19, 0), "apres casse");
            log("casse d'une partie de la pompe ({}) : {} ; tombe {} ; commande {}", before, broken, drops,
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(at(19, 0)).getBlock()));
            eye(player, gx + 10.5d, gy + 7.0d, gz - 15.0d, 0.0f, 14.0f);
        }));
        ShotHarness.SHOTS.add(client("c2_objets", 20, () -> {
            hud(true);
        }));
        ShotHarness.SHOTS.add(server("c3_sac", 30, player -> {
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            Item[] items = {fr.atraxterra.dohy.agri.DoHyAgri.SILO_ITEM.get(), fr.atraxterra.dohy.agri.DoHyAgri.FLOUR_MILL_ITEM.get(),
                    fr.atraxterra.dohy.agri.DoHyAgri.BREAD_MACHINE_ITEM.get(), fr.atraxterra.dohy.agri.DoHyAgri.BIOGENERATOR_ITEM.get(),
                    fr.atraxterra.dohy.agri.DoHyAgri.GMO_MACHINE_ITEM.get(), fr.atraxterra.dohy.agri.DoHyAgri.FUEL_PUMP_ITEM.get()};
            for (int i = 0; i < items.length; i++) {
                player.getInventory().setItem(i, new ItemStack(items[i]));
            }
            player.teleport(new TeleportTransition(over(player), new Vec3(gx + 10.5d, gy, gz - 12.0d), Vec3.ZERO, 0.0f, 10.0f,
                    TeleportTransition.DO_NOTHING));
        }));
    }
}
