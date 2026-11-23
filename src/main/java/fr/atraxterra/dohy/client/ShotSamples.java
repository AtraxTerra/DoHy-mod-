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

/** Temporaire : le lot 23d (les verrous de technologie). */
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


    // -- Les verrous de technologie (lot 23d) --------------------------------------------------------

    private static void teleport(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        ServerLevel world = player.level().getServer().overworld();
        world.getChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
        player.teleport(new TeleportTransition(world, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                TeleportTransition.DO_NOTHING));
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.setYRot(yaw);
                Minecraft.getInstance().player.setXRot(pitch);
            }
        });
    }

    private static final BlockPos SILO = new BlockPos(3000, -60, 2996);
    private static final BlockPos TROUGH = new BlockPos(2998, -60, 2996);
    private static final BlockPos MILL = new BlockPos(3002, -60, 2996);
    private static final BlockPos TRACTOR_SPOT = new BlockPos(3000, -60, 2992);

    private static String at(ServerPlayer player, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).toString();
    }

    private static void useOn(ServerPlayer player, BlockPos ground, ItemStack stack, String label) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        int before = stack.getCount();
        InteractionResult result = player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0.0d, 0.5d, 0.0d), Direction.UP, ground, false));
        log("{} : {} sur {} -> {} ; au-dessus {} ; en main {} (avant {})", label,
                BuiltInRegistries.ITEM.getKey(stack.getItem()), ground.toShortString(), result,
                at(player, ground.above()), player.getMainHandItem().getCount(), before);
    }

    private static void lock(String id) {
        fr.atraxterra.dohy.faction.tech.TechGate.Lock found = fr.atraxterra.dohy.faction.tech.TechGate.of(id);
        log("verrou {} : {}", id, found == null ? "aucun" : found.label());
    }

    private static int tractors(ServerPlayer player) {
        int count = 0;
        for (Entity entity : ((ServerLevel) player.level()).getAllEntities()) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("dohy:tractor")) {
                count++;
            }
        }
        return count;
    }

    /** Les lignes d'infobulle, comme le client les affiche. */
    private static void tooltip(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        Item found = item(id);
        ItemStack stack = new ItemStack(found);
        List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player,
                TooltipFlag.NORMAL);
        List<String> text = new ArrayList<>();
        for (Component line : lines) {
            text.add(line.getString());
        }
        log("infobulle {} : {}", id, text);
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(8)));
        ShotHarness.SHOTS.add(server("a1_sans_nation", 60, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule doDaylightCycle false");
            op(player, "gamerule advance_time false");
            op(player, "gamerule doMobSpawning false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty peaceful");
            player.setGameMode(GameType.SURVIVAL);
            teleport(player, 3000.5d, -60.0d, 3000.5d, 180.0f, 25.0f);
            Economy.set(player, 1_000_000L);
            log("verrous indexes : {}", fr.atraxterra.dohy.faction.tech.TechGate.size());
            for (String id : List.of("dohy:silo", "dohy:trough", "dohy:tractor", "dohy:farm_bot", "dohy:bot_remote",
                    "dohy:research_lab", "dohy:flour_mill", "dohy:gmo_machine", "minecraft:furnace",
                    "hbm:machine_press", "hbm:plant_flower")) {
                lock(id);
            }
            useOn(player, SILO.below(), new ItemStack(item("dohy:silo")), "silo sans nation");
            useOn(player, TROUGH.below(), new ItemStack(item("dohy:trough")), "mangeoire sans nation");
        }));
        ShotHarness.SHOTS.add(server("a2_refus", 60, player -> {
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 36));
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 23d", List.of(), 3);
            log("nation : {}", refusal == null ? "fondee" : refusal);
            log("claim : {}", FactionApi.claim(player));
            useOn(player, SILO.below(), new ItemStack(item("dohy:silo")), "silo a l'Agriculture I");
            useOn(player, MILL.below(), new ItemStack(item("dohy:flour_mill")), "moulin a l'Agriculture I");
            int before = tractors(player);
            useOn(player, TRACTOR_SPOT.below(), new ItemStack(item("dohy:tractor")), "tracteur a l'Agriculture I");
            log("tracteurs : {} -> {}", before, tractors(player));
            useOn(player, new BlockPos(2996, -61, 2996), new ItemStack(item("dohy:research_lab")),
                    "laboratoire (d'office)");
        }));
        ShotHarness.SHOTS.add(client("a3_infobulles", 20, () -> {
            for (String id : List.of("dohy:silo", "dohy:trough", "dohy:tractor", "dohy:farm_bot", "dohy:research_lab",
                    "dohy:bot_remote", "dohy:flour_mill", "minecraft:furnace")) {
                tooltip(id);
            }
        }));
        ShotHarness.SHOTS.add(server("a4_creatif", 40, player -> {
            player.setGameMode(GameType.CREATIVE);
            useOn(player, MILL.below(), new ItemStack(item("dohy:flour_mill")), "moulin en creatif");
            player.setGameMode(GameType.SURVIVAL);
            op(player, "f techno palier agriculture 2");
            fr.atraxterra.dohy.faction.tech.Tech.complete(player, false);
            useOn(player, SILO.below(), new ItemStack(item("dohy:silo")), "silo a l'Agriculture II");
            int before = tractors(player);
            useOn(player, TRACTOR_SPOT.below(), new ItemStack(item("dohy:tractor")), "tracteur a l'Agriculture II");
            log("tracteurs : {} -> {}", before, tractors(player));
        }));
        ShotHarness.SHOTS.add(client("a5_infobulles_apres", 60, () -> {
            for (String id : List.of("dohy:silo", "dohy:tractor", "dohy:flour_mill")) {
                tooltip(id);
            }
        }));
        ShotHarness.SHOTS.add(server("a6_machines", 60, player -> {
            op(player, "f techno palier agriculture 1");
            fr.atraxterra.dohy.faction.tech.Tech.complete(player, false);
            log("silo deja pose, Agriculture redescendue a I : {}", at(player, SILO));
            InteractionResult open = player.gameMode.useItemOn(player, player.level(), ItemStack.EMPTY,
                    InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(SILO), Direction.SOUTH, SILO, false));
            log("ouvrir le silo deja pose : {}", open);
            UiNet.close(player);
            log("casser le silo : {}", player.gameMode.destroyBlock(SILO));
            useOn(player, SILO.below(), new ItemStack(item("dohy:silo")), "silo repose a l'Agriculture I");
            teleport(player, 3000.5d, -60.0d, 3000.5d, 180.0f, 25.0f);
        }));
        ShotHarness.SHOTS.add(server("a7_agriculture", 40, player ->
                fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE.show(player,
                        fr.atraxterra.dohy.faction.tech.TechConfig.domain("agriculture"))));
        ShotHarness.SHOTS.add(server("a8_rechargement", 40, player -> {
            UiNet.close(player);
            op(player, "dohy reload");
            log("verrous indexes apres /dohy reload : {}", fr.atraxterra.dohy.faction.tech.TechGate.size());
            useOn(player, SILO.below(), new ItemStack(item("dohy:silo")), "silo apres rechargement");
        }));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "lot 23d")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            clicks(player);
        }));
    }
}
