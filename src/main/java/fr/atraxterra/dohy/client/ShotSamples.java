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

/** Temporaire : les couleurs des saisons (avant). */
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

    // -- Les couleurs des saisons ------------------------------------------------------------------

    private static int sx;
    private static int sy;
    private static int sz;
    private static String lastShot = "";

    /** Le z d'une latitude sur la carte des saisons (la latitude baisse quand z monte). */
    private static int zAt(double latitude) {
        var frame = fr.atraxterra.dohy.season.SeasonClimate.SERVER.frame();
        long low = frame.minZ();
        long high = frame.maxZ();
        while (low < high) {
            long mid = Math.floorDiv(low + high, 2L);
            if (frame.latitude(mid) > latitude) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return (int) low;
    }

    private static BlockState leaves(Block block) {
        return block.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    }

    /** Un arbre a la main si la feature n'a rien pose. */
    private static void handTree(ServerLevel level, int x, int y, int z, Block log, Block leaf, int height) {
        BlockState leafState = leaves(leaf);
        for (int dy = height - 3; dy <= height; dy++) {
            int radius = dy >= height - 1 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && (radius == 2 || dy == height)) {
                        continue;
                    }
                    level.setBlock(new BlockPos(x + dx, y + dy, z + dz), leafState, 2);
                }
            }
        }
        for (int dy = 0; dy < height; dy++) {
            level.setBlock(new BlockPos(x, y + dy, z), log.defaultBlockState(), 2);
        }
    }

    private static void tree(ServerPlayer player, ServerLevel level, String feature, int x, int y, int z, Block log,
                             Block leaf, int height) {
        op(player, "place feature minecraft:" + feature + " " + x + " " + y + " " + z);
        if (!level.getBlockState(new BlockPos(x, y + 1, z)).is(log)) {
            handTree(level, x, y, z, log, leaf, height);
            log("arbre {} en {} {} {} : a la main", feature, x, y, z);
        }
    }

    /** Un bout de foret : sol d'herbe, arbres de cinq especes, herbes et fougeres. */
    private static void scene(ServerPlayer player, ServerLevel level, int x0, int z0, String biome, boolean tropics) {
        level.getChunk(x0 >> 4, z0 >> 4);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
        for (int x = -24; x <= 24; x++) {
            for (int z = -12; z <= 36; z++) {
                level.setBlock(new BlockPos(x0 + x, y - 1, z0 + z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy < 14; dy++) {
                    level.setBlock(new BlockPos(x0 + x, y + dy, z0 + z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        op(player, "fillbiome " + (x0 - 32) + " " + (y - 8) + " " + (z0 - 16) + " " + (x0 + 32) + " " + (y + 40) + " "
                + (z0 + 44) + " " + biome);
        if (tropics) {
            int[][] spots = {{-14, 9}, {-5, 13}, {5, 9}, {14, 14}, {-9, 22}, {2, 24}, {11, 26}};
            String[] kinds = {"acacia", "oak", "acacia", "jungle_tree", "acacia", "oak", "acacia"};
            for (int i = 0; i < spots.length; i++) {
                Block log = kinds[i].equals("acacia") ? Blocks.ACACIA_LOG : kinds[i].equals("oak") ? Blocks.OAK_LOG
                        : Blocks.JUNGLE_LOG;
                Block leaf = kinds[i].equals("acacia") ? Blocks.ACACIA_LEAVES : kinds[i].equals("oak") ? Blocks.OAK_LEAVES
                        : Blocks.JUNGLE_LEAVES;
                tree(player, level, kinds[i], x0 + spots[i][0], y, z0 + spots[i][1], log, leaf, 5);
            }
        } else {
            int[][] spots = {{-15, 8}, {-8, 10}, {-1, 8}, {7, 9}, {15, 8}, {-12, 17}, {-4, 18}, {4, 17}, {12, 18},
                    {-16, 26}, {-7, 27}, {1, 26}, {9, 27}, {17, 26}};
            String[] kinds = {"oak", "birch", "fancy_oak", "dark_oak", "spruce", "birch", "oak", "birch", "oak",
                    "dark_oak", "oak", "spruce", "birch", "oak"};
            for (int i = 0; i < spots.length; i++) {
                Block log;
                Block leaf;
                switch (kinds[i]) {
                    case "birch" -> {
                        log = Blocks.BIRCH_LOG;
                        leaf = Blocks.BIRCH_LEAVES;
                    }
                    case "dark_oak" -> {
                        log = Blocks.DARK_OAK_LOG;
                        leaf = Blocks.DARK_OAK_LEAVES;
                    }
                    case "spruce" -> {
                        log = Blocks.SPRUCE_LOG;
                        leaf = Blocks.SPRUCE_LEAVES;
                    }
                    default -> {
                        log = Blocks.OAK_LOG;
                        leaf = Blocks.OAK_LEAVES;
                    }
                }
                tree(player, level, kinds[i], x0 + spots[i][0], y, z0 + spots[i][1], log, leaf, 6);
            }
        }
        java.util.Random random = new java.util.Random(47L);
        Block litter = BuiltInRegistries.BLOCK.getOptional(Identifier.parse("minecraft:leaf_litter")).orElse(null);
        for (int i = 0; i < 260; i++) {
            int x = x0 - 22 + random.nextInt(45);
            int z = z0 - 4 + random.nextInt(36);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            int roll = random.nextInt(10);
            if (roll < 6) {
                level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), 2);
            } else if (roll < 8) {
                level.setBlock(pos, Blocks.FERN.defaultBlockState(), 2);
            } else if (roll < 9 || litter == null) {
                net.minecraft.world.level.block.DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(),
                        pos, 2);
            } else {
                level.setBlock(pos, litter.defaultBlockState(), 2);
            }
        }
        sx = x0;
        sy = y;
        sz = z0;
    }

    private static void camera(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        // spectateur : ni chute (la camera reste en l'air), ni main, ni barre d'objets
        player.setGameMode(GameType.SPECTATOR);
        player.teleport(new TeleportTransition(level, new Vec3(sx + 0.5d, sy + 6.0d, sz - 12.0d), Vec3.ZERO, 0.0f, 8.0f,
                TeleportTransition.DO_NOTHING));
    }

    /** Ce que le client peint pour le bout de foret, au palier affiche (celui de l'etape precedente). */
    private static void paint(String label) {
        BlockPos pos = new BlockPos(sx, sy, sz + 10);
        var now = fr.atraxterra.dohy.client.season.ClientSeasons.now(pos);
        StringBuilder groves = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            groves.append(String.format("%06X ", fr.atraxterra.dohy.client.season.SeasonColors.foliage(0x59AE30,
                    pos.offset(i * 4, 0, 0)) & 0xFFFFFF));
        }
        log("teintes {} : {} ; herbe {} ; feuillage {}", label, now == null ? "hors saison" : now.name() + " j" + now.day(),
                String.format("%06X", fr.atraxterra.dohy.client.season.SeasonColors.grass(0x79C05A, pos) & 0xFFFFFF),
                groves.toString().trim());
    }

    private static void season(ServerPlayer player, double target) {
        op(player, "saison admin remettre");
        double now = fr.atraxterra.dohy.season.SeasonClimate.SERVER.phase();
        double days = (((target - now) % 1.0d) + 1.0d) % 1.0d * 28.0d;
        op(player, String.format(java.util.Locale.ROOT, "saison admin avancer %.4f", days));
        double phase = fr.atraxterra.dohy.season.SeasonClimate.SERVER.phase();
        log("saison : visee {} obtenue {} ({} au nord)", target, String.format(java.util.Locale.ROOT, "%.4f", phase),
                fr.atraxterra.dohy.season.SeasonMath.name(45.0d, phase));
    }

    private static ShotHarness.Shot at(String name, double phase) {
        return server(name, 110, player -> {
            paint(lastShot);
            lastShot = name;
            season(player, phase);
            camera(player);
        });
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> {
            Minecraft.getInstance().options.renderDistance().set(6);
        }));
        ShotHarness.SHOTS.add(server("a1_foret", 120, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule advance_time false");
            op(player, "gamerule advance_weather false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty peaceful");
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            ServerLevel level = ((ServerLevel) player.level()).getServer().overworld();
            int z = zAt(47.0d);
            log("carte : 47 N en z {}, latitude lue {}", z, fr.atraxterra.dohy.season.SeasonClimate.SERVER.frame()
                    .latitude(z));
            player.teleport(new TeleportTransition(level, new Vec3(0.5d, 0.0d, z + 0.5d), Vec3.ZERO, 0.0f, 10.0f,
                    TeleportTransition.DO_NOTHING));
            scene(player, level, 0, z, "minecraft:forest", false);
            camera(player);
        }));
        ShotHarness.SHOTS.add(at("s1_hiver", 0.125d));
        ShotHarness.SHOTS.add(at("s2_fin_hiver", 0.225d));
        ShotHarness.SHOTS.add(at("s3_debut_printemps", 0.29d));
        ShotHarness.SHOTS.add(at("s4_printemps", 0.375d));
        ShotHarness.SHOTS.add(at("s5_ete", 0.625d));
        ShotHarness.SHOTS.add(at("s6_fin_ete", 0.72d));
        ShotHarness.SHOTS.add(at("s7_debut_automne", 0.785d));
        ShotHarness.SHOTS.add(at("s8_automne", 0.875d));
        ShotHarness.SHOTS.add(at("s9_fin_automne", 0.96d));
        ShotHarness.SHOTS.add(server("t1_tropiques_seche", 140, player -> {
            paint(lastShot);
            lastShot = "t1_tropiques_seche";
            ServerLevel level = (ServerLevel) player.level();
            int z = zAt(14.0d);
            player.teleport(new TeleportTransition(level, new Vec3(0.5d, 0.0d, z + 0.5d), Vec3.ZERO, 0.0f, 10.0f,
                    TeleportTransition.DO_NOTHING));
            scene(player, level, 0, z, "minecraft:savanna", true);
            season(player, 0.125d);
            camera(player);
        }));
        ShotHarness.SHOTS.add(at("t2_tropiques_pluies", 0.625d));
        ShotHarness.SHOTS.add(server("z_fin", 20, player -> {
            paint(lastShot);
            op(player, "saison admin remettre");
        }));
    }
}
