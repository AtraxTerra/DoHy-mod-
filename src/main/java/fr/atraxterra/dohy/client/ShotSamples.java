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

    // -- Les trains de Create Fly (port NeoForge) -------------------------------------------------------

    private static int gx;
    private static int gy;
    private static int gz;
    private static final List<BlockPos> STATIONS = new ArrayList<>();

    private static ServerLevel over(ServerPlayer player) {
        return ((ServerLevel) player.level()).getServer().overworld();
    }

    private static void look(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        player.teleport(new TeleportTransition(over(player), new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                TeleportTransition.DO_NOTHING));
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.setYRot(yaw);
                Minecraft.getInstance().player.setXRot(pitch);
            }
        });
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    level.setBlock(new BlockPos(x, y, z), state, 2);
                }
            }
        }
    }

    private static InteractionResult click(ServerPlayer player, ItemStack stack, BlockPos pos, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5d, face.getStepY() * 0.5d, face.getStepZ() * 0.5d);
        return player.gameMode.useItemOn(player, over(player), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, pos, false));
    }

    /** Une gare posee comme un joueur : clic sur la voie, puis clic sur le sol a cote. */
    private static BlockPos station(ServerPlayer player, int z, String name) {
        ServerLevel level = over(player);
        BlockPos track = new BlockPos(gx, gy, z);
        // Le joueur regarde vers +Z : la gare vise ce sens de la voie.
        look(player, gx + 2.5d, gy + 1.0d, z - 2.5d, 0.0f, 30.0f);
        ItemStack item = new ItemStack(com.zurrtum.create.AllItems.TRACK_STATION);
        InteractionResult select = click(player, item, track, Direction.UP);
        BlockPos ground = new BlockPos(gx + 1, gy - 1, z);
        InteractionResult place = click(player, item, ground, Direction.UP);
        BlockPos pos = ground.above();
        String state = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        if (level.getBlockEntity(pos) instanceof com.zurrtum.create.content.trains.station.StationBlockEntity be) {
            boolean renamed = be.updateName(name);
            var global = be.getStation();
            log("gare {} : selection {}, pose {}, bloc {}, voie valide {}, gare globale {}, renommee {}", name, select, place,
                    state, be.edgePoint.hasValidTrack(), global == null ? "aucune" : global.name, renamed);
        } else {
            log("gare {} : selection {}, pose {}, bloc {} (pas de gare)", name, select, place, state);
        }
        STATIONS.add(pos);
        return pos;
    }

    private static com.zurrtum.create.content.trains.station.StationBlockEntity stationAt(ServerPlayer player, int index) {
        return (com.zurrtum.create.content.trains.station.StationBlockEntity) over(player).getBlockEntity(STATIONS.get(index));
    }

    private static com.zurrtum.create.content.trains.entity.Train train() {
        for (var train : com.zurrtum.create.Create.RAILWAYS.trains.values()) {
            return train;
        }
        return null;
    }

    private static String describe(com.zurrtum.create.content.trains.entity.Train train) {
        if (train == null) {
            return "aucun train";
        }
        var entity = train.carriages.isEmpty() ? null : train.carriages.get(0).anyAvailableEntity();
        var current = train.getCurrentStation();
        var destination = train.navigation.destination;
        return String.format(java.util.Locale.ROOT, "%s : %d voiture(s), vitesse %.3f, cible %.3f, position %s, en gare %s, destination %s, deraille %s",
                train.name.getString(), train.carriages.size(), train.speed, train.targetSpeed,
                entity == null ? "?" : String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", entity.getX(), entity.getY(), entity.getZ()),
                current == null ? "non" : current.name, destination == null ? "aucune" : destination.name, train.derailed);
    }

    static void register() {
        ShotHarness.SHOTS.add(client("p0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(8)));
        ShotHarness.SHOTS.add(server("t1_voie", 60, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule advance_time false");
            op(player, "gamerule spawn_mobs false");
            op(player, "gamemode creative");
            ServerLevel level = over(player);
            gx = 16 * 40 + 8;
            gz = 16 * 40 + 8;
            level.getChunk(gx >> 4, gz >> 4);
            gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
            fill(level, gx - 8, gy - 1, gz - 44, gx + 8, gy - 1, gz + 48, Blocks.STONE_BRICKS.defaultBlockState());
            fill(level, gx - 8, gy, gz - 44, gx + 8, gy + 12, gz + 48, Blocks.AIR.defaultBlockState());
            BlockState rail = com.zurrtum.create.AllBlocks.TRACK.defaultBlockState()
                    .setValue(com.zurrtum.create.content.trains.track.TrackBlock.SHAPE,
                            com.zurrtum.create.content.trains.track.TrackShape.ZO);
            for (int z = gz - 40; z <= gz + 44; z++) {
                level.setBlock(new BlockPos(gx, gy, z), rail, 3);
            }
            log("voie : {} blocs de {} a z {}..{}", 85, BuiltInRegistries.BLOCK.getKey(rail.getBlock()), gz - 40, gz + 44);
            look(player, gx + 10.5d, gy + 8.0d, gz, 90.0f, 30.0f);
        }));
        ShotHarness.SHOTS.add(server("t2_gares", 40, player -> {
            var graphs = com.zurrtum.create.Create.RAILWAYS.trackNetworks;
            log("reseaux de voie : {}", graphs.size());
            station(player, gz + 4, "Gare A");
            station(player, gz + 38, "Gare B");
            station(player, gz - 30, "Gare C");
            look(player, gx + 10.5d, gy + 6.0d, gz + 4.5d, 90.0f, 20.0f);
        }));
        ShotHarness.SHOTS.add(server("t3_train", 60, player -> {
            ServerLevel level = over(player);
            var a = stationAt(player, 0);
            boolean assembling = a.enterAssemblyMode(player);
            Direction dir = a.getAssemblyDirection();
            BlockPos target = a.edgePoint.getGlobalPosition();
            log("assemblage : {}, sens {}, voie visee {}", assembling, dir, target);
            if (dir == null) {
                return;
            }
            BlockPos bogeyTrack = target.relative(dir, 1);
            InteractionResult casing = click(player, new ItemStack(com.zurrtum.create.AllItems.RAILWAY_CASING, 4), bogeyTrack, Direction.UP);
            BlockPos bogey = bogeyTrack.above();
            log("bogie : clic {}, bloc {}", casing, BuiltInRegistries.BLOCK.getKey(level.getBlockState(bogey).getBlock()));
            BlockPos controls = bogey.above();
            // Les commandes regardent le conducteur : le train avance a l'oppose.
            level.setBlock(controls, com.zurrtum.create.AllBlocks.TRAIN_CONTROLS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, dir.getOpposite()), 3);
            level.addFreshEntity(new com.zurrtum.create.content.contraptions.glue.SuperGlueEntity(level,
                    new AABB(Vec3.atLowerCornerOf(bogey), Vec3.atLowerCornerOf(controls.offset(1, 1, 1)))));
            a.refreshAssemblyInfo();
            a.assemble(player.getUUID());
            log("apres assemblage : {} train(s) ; {}", com.zurrtum.create.Create.RAILWAYS.trains.size(), describe(train()));
            look(player, gx + 8.5d, gy + 4.0d, bogey.getZ() + 0.5d, 90.0f, 15.0f);
        }));
        ShotHarness.SHOTS.add(server("t4_depart", 40, player -> {
            var train = train();
            if (train == null) {
                log("depart : pas de train");
                return;
            }
            String chosen = null;
            for (int index : new int[]{1, 2}) {
                var station = stationAt(player, index).getStation();
                if (station == null) {
                    continue;
                }
                var path = train.navigation.findPathTo(station, Double.MAX_VALUE);
                log("chemin vers {} : {}", station.name, path == null ? "aucun" : "trouve");
                if (path != null) {
                    double cost = train.navigation.startNavigation(path);
                    chosen = station.name;
                    log("navigation vers {} : cout {}", chosen, cost);
                    break;
                }
            }
            log("depart : {}", describe(train));
            look(player, gx + 9.5d, gy + 5.0d, gz + 4.5d, 90.0f, 20.0f);
        }));
        ShotHarness.SHOTS.add(server("t5_route", 40, player -> {
            var train = train();
            log("en route : {}", describe(train));
            var entity = train == null || train.carriages.isEmpty() ? null : train.carriages.get(0).anyAvailableEntity();
            double z = entity == null ? gz + 10 : entity.getZ();
            look(player, gx + 9.5d, gy + 5.0d, z, 90.0f, 20.0f);
        }));
        ShotHarness.SHOTS.add(server("t6_arrivee", 200, player -> {
            log("plus tard : {}", describe(train()));
        }));
        ShotHarness.SHOTS.add(server("t7_gare", 40, player -> {
            var train = train();
            log("arrivee : {}", describe(train));
            var entity = train == null || train.carriages.isEmpty() ? null : train.carriages.get(0).anyAvailableEntity();
            double z = entity == null ? gz + 38 : entity.getZ();
            look(player, gx + 9.5d, gy + 5.0d, z, 90.0f, 20.0f);
            var server = player.level().getServer();
            server.saveEverything(false, true, true);
            java.nio.file.Path data = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
            try (var files = java.nio.file.Files.list(data)) {
                files.filter(f -> f.getFileName().toString().contains("create"))
                        .forEach(f -> log("sauvegarde : {} ({} octets)", f.getFileName(), f.toFile().length()));
            } catch (Throwable t) {
                LOG.error("dohy-bug: sauvegarde", t);
            }
        }));
    }
}
