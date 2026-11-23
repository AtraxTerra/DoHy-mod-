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

    // -- Les claims sur la carte et la carte revelee ------------------------------------------------

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

    /** Le coin lointain : 600 blocs a l'est, hors de la vue du client, rendu par le serveur seul. */
    private static final int FAR_X = 3600;
    private static final int FAR_Z = 2996;

    private static void claims(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.FactionData data = FactionApi.data(player);
        StringBuilder list = new StringBuilder();
        for (fr.atraxterra.dohy.faction.FactionData.Claim claim : data.claims()) {
            list.append(claim.x()).append(',').append(claim.z()).append(' ');
        }
        log("claims [{}] : {} / {} : {}", label, data.claims().size(), FactionApi.claimCap(player), list);
    }

    /** L'etat des claims et la derniere reponse, cote client (par reflexion : classes du paquet de l'atlas). */
    private static void clientClaims(String label) {
        try {
            Class<?> type = Class.forName("fr.atraxterra.dohy.client.atlas.AtlasClaims");
            java.lang.reflect.Method count = type.getDeclaredMethod("count");
            java.lang.reflect.Method cap = type.getDeclaredMethod("cap");
            java.lang.reflect.Method toast = type.getDeclaredMethod("toast");
            java.lang.reflect.Method known = type.getDeclaredMethod("known");
            count.setAccessible(true);
            cap.setAccessible(true);
            toast.setAccessible(true);
            known.setAccessible(true);
            log("client [{}] : connu {} ; claims {} / {} ; reponse « {} »", label, known.invoke(null),
                    count.invoke(null), cap.invoke(null), toast.invoke(null));
        } catch (ReflectiveOperationException error) {
            log("client [{}] : {}", label, error.toString());
        }
    }

    /** Une couleur d'une dalle revelee du client, au bloc (x, z) ; -1 si la dalle n'est pas la. */
    private static int revealedPixel(String level, int x, int z) {
        try {
            Class<?> client = Class.forName("fr.atraxterra.dohy.client.atlas.AtlasClient");
            java.lang.reflect.Method mapMethod = client.getDeclaredMethod("map");
            mapMethod.setAccessible(true);
            Object map = mapMethod.invoke(null);
            if (map == null) {
                return -2;
            }
            java.lang.reflect.Field field = map.getClass().getDeclaredField(level);
            field.setAccessible(true);
            Object tiles = field.get(map);
            java.lang.reflect.Method scaleMethod = tiles.getClass().getDeclaredMethod("scale");
            scaleMethod.setAccessible(true);
            int scale = (int) scaleMethod.invoke(tiles);
            int px = Math.floorDiv(x, scale);
            int pz = Math.floorDiv(z, scale);
            java.lang.reflect.Method loaded = tiles.getClass().getDeclaredMethod("loaded", int.class, int.class);
            loaded.setAccessible(true);
            Object tile = loaded.invoke(tiles, Math.floorDiv(px, 256), Math.floorDiv(pz, 256));
            if (tile == null) {
                return -1;
            }
            java.lang.reflect.Field image = tile.getClass().getDeclaredField("image");
            image.setAccessible(true);
            com.mojang.blaze3d.platform.NativeImage pixels = (com.mojang.blaze3d.platform.NativeImage) image.get(tile);
            return pixels.getPixel(Math.floorMod(px, 256), Math.floorMod(pz, 256));
        } catch (ReflectiveOperationException | RuntimeException error) {
            log("pixel revele : {}", error.toString());
            return -3;
        }
    }

    private static void request(String dimension, int x1, int z1, int x2, int z2, boolean claim) {
        Minecraft.getInstance().execute(() -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new fr.atraxterra.dohy.faction.ClaimMap.Request(dimension, x1, z1, x2, z2, claim)));
    }

    private static void claimMode(boolean on) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof fr.atraxterra.dohy.client.atlas.AtlasScreen screen) {
            try {
                java.lang.reflect.Field field = screen.getClass().getDeclaredField("claiming");
                field.setAccessible(true);
                field.setBoolean(screen, on);
            } catch (ReflectiveOperationException error) {
                log("mode claim : {}", error.toString());
            }
        }
    }

    private static void regions(ServerPlayer player, String label) {
        java.nio.file.Path root = player.level().getServer().getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT);
        StringBuilder found = new StringBuilder();
        for (String folder : List.of("region", "dimensions/minecraft/overworld/region", "dohy/atlas/minecraft/overworld")) {
            java.nio.file.Path path = root.resolve(folder);
            if (java.nio.file.Files.isDirectory(path)) {
                try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(path)) {
                    found.append(folder).append(" : ").append(files.map(p -> p.getFileName().toString()).sorted()
                            .limit(30).toList()).append(" ; ");
                } catch (java.io.IOException error) {
                    found.append(folder).append(" illisible ; ");
                }
            }
        }
        log("fichiers [{}] : {}", label, found);
    }

    /** 64 chunks du Nether (un vrai relief), generes puis sauves, pour le banc de rendu (le temps par chunk). */
    private static void benchGenerate(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) {
            log("banc : pas de Nether");
            return;
        }
        long started = System.nanoTime();
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                nether.getChunk(cx, cz);
            }
        }
        log("banc : 64 chunks du Nether generes en {} ms", (System.nanoTime() - started) / 1_000_000L);
        op(player, "save-all flush");
    }

    /** Le temps de rendu de cette region par l'atlas du serveur, une fois le fichier ecrit. */
    private static void benchRender(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        java.nio.file.Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("dimensions/minecraft/the_nether/region/r.0.0.mca");
        try {
            log("banc : fichier {} ({} octets)", java.nio.file.Files.isRegularFile(file),
                    java.nio.file.Files.isRegularFile(file) ? java.nio.file.Files.size(file) : -1L);
            Class<?> tiles = Class.forName("fr.atraxterra.dohy.atlas.WorldTiles");
            java.lang.reflect.Method render = tiles.getDeclaredMethod("render", java.nio.file.Path.class, int.class,
                    int.class, boolean.class);
            render.setAccessible(true);
            for (int run = 0; run < 5; run++) {
                long begin = System.nanoTime();
                int[] colors = (int[]) render.invoke(null, file, 0, 0, true);
                long took = (System.nanoTime() - begin) / 1_000L;
                int known = 0;
                for (int color : colors) {
                    known += (color >>> 24) != 0 ? 1 : 0;
                }
                log("banc : rendu {} de la region du Nether en {} ms ; {} pixels connus sur {} ; centre {}", run,
                        took / 1000.0d, known, colors.length, Integer.toHexString(colors[256 * 512 + 256]));
            }
            // L'ancienne lecture, pour comparer : tout le NBT de chaque chunk, par le jeu.
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            for (int run = 0; run < 3; run++) {
                long begin = System.nanoTime();
                int read = 0;
                long raw = 0L;
                for (int index = 0; index < 1024; index++) {
                    int at = index * 4;
                    int location = (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16 | (bytes[at + 2] & 0xFF) << 8
                            | bytes[at + 3] & 0xFF;
                    if (location == 0) {
                        continue;
                    }
                    int offset = (location >>> 8) * 4096;
                    int length = (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16
                            | (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
                    int type = bytes[offset + 4];
                    java.io.InputStream in = new java.io.ByteArrayInputStream(bytes, offset + 5, length - 1);
                    if (type == 2) {
                        in = new java.util.zip.InflaterInputStream(in);
                    } else if (type != 3) {
                        continue;
                    }
                    try (java.io.DataInputStream stream = new java.io.DataInputStream(new java.io.BufferedInputStream(in))) {
                        net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.read(stream,
                                net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                        raw += tag.size();
                        read++;
                    }
                }
                log("banc : ancienne lecture (NBT entier) {} : {} chunks en {} ms ; {} champs racine", run, read,
                        (System.nanoTime() - begin) / 1_000_000.0d, raw);
            }
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException error) {
            log("banc : {}", error.toString());
        }
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(8)));
        ShotHarness.SHOTS.add(server("a1_depart", 60, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule doDaylightCycle false");
            op(player, "gamerule advance_time false");
            op(player, "gamerule doMobSpawning false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty peaceful");
            player.setGameMode(GameType.SURVIVAL);
            teleport(player, 3000.5d, -60.0d, 3000.5d, 180.0f, 20.0f);
            Economy.set(player, 1_000_000L);
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 36));
            String refusal = FactionApi.create(player, "Testeurs", "La carte", List.of(), 3);
            log("nation : {} ; claim ici : {}", refusal == null ? "fondee" : refusal, FactionApi.claim(player));
            op(player, "f techno palier gouvernement 4");
            claims(player, "depart");
        }));
        ShotHarness.SHOTS.add(server("a2_coin_lointain", 200, player -> {
            ServerLevel level = (ServerLevel) player.level();
            int cx = FAR_X >> 4;
            int cz = FAR_Z >> 4;
            int made = 0;
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    level.getChunk(cx + dx, cz + dz);
                    made++;
                }
            }
            int y = -61;
            for (int dx = -96; dx <= 96; dx++) {
                for (int dz = -96; dz <= 96; dz++) {
                    int ax = Math.abs(dx);
                    int az = Math.abs(dz);
                    BlockState state = null;
                    if (Math.max(ax, az) >= 88) {
                        state = Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState();
                    } else if (ax <= 6 || az <= 6) {
                        state = Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.YELLOW).defaultBlockState();
                    } else if (dx > 20 && dx < 70 && dz > 20 && dz < 70) {
                        state = Blocks.WATER.defaultBlockState();
                    } else if (dx < -20 && dx > -70 && dz > 20 && dz < 70) {
                        state = Blocks.SNOW_BLOCK.defaultBlockState();
                    }
                    if (state != null) {
                        level.setBlock(new BlockPos(FAR_X + dx, y, FAR_Z + dz), state, Block.UPDATE_CLIENTS);
                    }
                }
            }
            op(player, "save-all flush");
            log("coin lointain : {} chunks generes autour de {} {} ; motif pose ; monde sauve", made, FAR_X, FAR_Z);
            regions(player, "apres la sauvegarde");
        }));
        ShotHarness.SHOTS.add(client("a3_carte_revelee", 300, () -> Minecraft.getInstance().gui.setScreen(
                new fr.atraxterra.dohy.client.atlas.AtlasScreen(0.75d, FAR_X, FAR_Z))));
        ShotHarness.SHOTS.add(client("a4_carte_large", 240, () -> {
            log("pixel revele fin au centre (jaune attendu) : {}", Integer.toHexString(
                    revealedPixel("revealFine", FAR_X, FAR_Z)));
            log("pixel revele fin dans l'eau : {} ; dans le cadre rouge : {}", Integer.toHexString(
                    revealedPixel("revealFine", FAR_X + 45, FAR_Z + 45)), Integer.toHexString(
                    revealedPixel("revealFine", FAR_X + 92, FAR_Z)));
            Minecraft.getInstance().gui.setScreen(new fr.atraxterra.dohy.client.atlas.AtlasScreen(3.0d, 3300.0d,
                    3000.0d));
        }));
        ShotHarness.SHOTS.add(client("a45_carte_moyenne", 240, () -> Minecraft.getInstance().gui.setScreen(
                new fr.atraxterra.dohy.client.atlas.AtlasScreen(8.0d, 3300.0d, 3000.0d))));
        ShotHarness.SHOTS.add(client("a5_carte_ensemble", 300, () -> {
            log("pixel revele moyen au coin lointain (jaune attendu) : {}", Integer.toHexString(
                    revealedPixel("revealMiddle", FAR_X, FAR_Z)));
            Minecraft.getInstance().gui.setScreen(new fr.atraxterra.dohy.client.atlas.AtlasScreen(24.0d, 3300.0d,
                    3000.0d));
        }));
        ShotHarness.SHOTS.add(server("b0_infos", 20, player -> {
            regions(player, "apres les demandes");
            claims(player, "avant la carte");
        }));
        ShotHarness.SHOTS.add(client("b1_claims_zone", 60, () -> {
            log("pixel revele d'ensemble au coin lointain : {}", Integer.toHexString(
                    revealedPixel("revealOverview", FAR_X, FAR_Z)));
            clientClaims("avant");
            int pcx = 3000 >> 4;
            int pcz = 3000 >> 4;
            request("minecraft:overworld", pcx - 1, pcz - 1, pcx + 1, pcz + 1, true);
        }));
        ShotHarness.SHOTS.add(server("b2_claims_serveur", 20, player -> claims(player, "apres la zone 3x3")));
        ShotHarness.SHOTS.add(client("b3_rendre", 60, () -> {
            clientClaims("apres la zone");
            request("minecraft:overworld", (3000 >> 4) + 1, (3000 >> 4) + 1, (3000 >> 4) + 1, (3000 >> 4) + 1, false);
        }));
        ShotHarness.SHOTS.add(client("b4_trop_loin", 60, () -> {
            clientClaims("apres rendu");
            request("minecraft:overworld", (3000 >> 4) + 70, 3000 >> 4, (3000 >> 4) + 70, 3000 >> 4, true);
        }));
        ShotHarness.SHOTS.add(client("b5_trop_grand", 60, () -> {
            clientClaims("trop loin");
            request("minecraft:overworld", 100, 100, 108, 108, true);
        }));
        ShotHarness.SHOTS.add(client("b6_deja", 60, () -> {
            clientClaims("trop grand");
            request("minecraft:overworld", (3000 >> 4) - 1, (3000 >> 4) - 1, (3000 >> 4), (3000 >> 4), true);
        }));
        ShotHarness.SHOTS.add(client("c0_mode_claim", 80, () -> {
            clientClaims("deja a moi");
            Minecraft.getInstance().gui.setScreen(new fr.atraxterra.dohy.client.atlas.AtlasScreen(0.6d, 3000.0d,
                    3000.0d));
        }));
        ShotHarness.SHOTS.add(client("c1_mode_claim_actif", 60, () -> claimMode(true)));
        ShotHarness.SHOTS.add(server("c2_minimap", 60, player -> {
            claims(player, "fin");
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(null));
        }));
        ShotHarness.SHOTS.add(server("c3_fiche_atlas", 40, player ->
                fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player, "atlas")));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> {
            UiNet.close(player);
            views(player, "carte");
        }));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            clicks(player);
        }));
        ShotHarness.SHOTS.add(server("z0_banc_monde", 200, ShotSamples::benchGenerate));
        ShotHarness.SHOTS.add(server("z1_banc_rendu", 20, ShotSamples::benchRender));
    }
}
