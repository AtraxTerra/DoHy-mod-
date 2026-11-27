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

    // -- Les trains de Create Fly (port NeoForge) et de Steam 'n' Rails ---------------------------------

    private static int gx;
    private static int gy;
    private static int gz;

    /** Une ligne d'essai : une voie droite nord-sud, trois gares, un train assemble a la premiere. */
    private static final class Line {
        final String label;
        final int dx;
        final String track;
        final int stationSide;
        final List<BlockPos> stations = new ArrayList<>();
        UUID train;

        Line(String label, int dx, String track, int stationSide) {
            this.label = label;
            this.dx = dx;
            this.track = track;
            this.stationSide = stationSide;
        }

        int x() {
            return gx + dx;
        }
    }

    private static final Line CREATE = new Line("create", 0, "create:track", 1);
    private static final Line WIDE = new Line("snr large", 24, "railways:track_create_andesite_wide", 2);

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

    /** Un bloc par son identifiant, ou null s'il n'existe pas (mod absent). */
    private static Block block(String id) {
        Identifier key = Identifier.parse(id);
        return BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.getValue(key) : null;
    }

    private static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Une voie droite nord-sud : la forme ZO de Create, que les voies de Steam 'n' Rails reprennent. */
    private static BlockState straight(Block track) {
        BlockState state = track.defaultBlockState();
        if (state.hasProperty(com.zurrtum.create.content.trains.track.TrackBlock.SHAPE)) {
            state = state.setValue(com.zurrtum.create.content.trains.track.TrackBlock.SHAPE,
                    com.zurrtum.create.content.trains.track.TrackShape.ZO);
        }
        return state;
    }

    /** Une gare posee comme un joueur : clic sur la voie, puis clic sur le sol a cote. */
    private static BlockPos station(ServerPlayer player, Line line, int z, String name) {
        ServerLevel level = over(player);
        int x = line.x();
        BlockPos track = new BlockPos(x, gy, z);
        // Le joueur regarde vers +Z : la gare vise ce sens de la voie.
        look(player, x + 2.5d, gy + 1.0d, z - 2.5d, 0.0f, 30.0f);
        ItemStack item = new ItemStack(com.zurrtum.create.AllItems.TRACK_STATION);
        InteractionResult select = click(player, item, track, Direction.UP);
        BlockPos ground = new BlockPos(x + line.stationSide, gy - 1, z);
        InteractionResult place = click(player, item, ground, Direction.UP);
        BlockPos pos = ground.above();
        if (level.getBlockEntity(pos) instanceof com.zurrtum.create.content.trains.station.StationBlockEntity be) {
            boolean renamed = be.updateName(name);
            var global = be.getStation();
            log("[{}] gare {} : selection {}, pose {}, bloc {}, voie valide {}, gare globale {}, renommee {}", line.label, name,
                    select, place, id(level.getBlockState(pos)), be.edgePoint.hasValidTrack(),
                    global == null ? "aucune" : global.name, renamed);
        } else {
            log("[{}] gare {} : selection {}, pose {}, bloc {} (pas de gare)", line.label, name, select, place,
                    id(level.getBlockState(pos)));
        }
        line.stations.add(pos);
        return pos;
    }

    private static com.zurrtum.create.content.trains.station.StationBlockEntity stationAt(ServerPlayer player, Line line, int index) {
        return (com.zurrtum.create.content.trains.station.StationBlockEntity) over(player).getBlockEntity(line.stations.get(index));
    }

    private static com.zurrtum.create.content.trains.entity.Train train(Line line) {
        return line.train == null ? null : com.zurrtum.create.Create.RAILWAYS.trains.get(line.train);
    }

    private static String describe(com.zurrtum.create.content.trains.entity.Train train) {
        if (train == null) {
            return "aucun train";
        }
        var entity = train.carriages.isEmpty() ? null : train.carriages.get(0).anyAvailableEntity();
        var current = train.getCurrentStation();
        var destination = train.navigation.destination;
        String bogeys = train.carriages.isEmpty() ? "?" : BuiltInRegistries.BLOCK.getKey(train.carriages.get(0).leadingBogey().type).toString();
        return String.format(java.util.Locale.ROOT, "%s : %d voiture(s), bogie %s, conducteur av %s ar %s, vitesse %.3f, cible %.3f, position %s, en gare %s, destination %s, deraille %s",
                train.name.getString(), train.carriages.size(), bogeys, train.hasForwardConductor(), train.hasBackwardConductor(),
                train.speed, train.targetSpeed,
                entity == null ? "?" : String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", entity.getX(), entity.getY(), entity.getZ()),
                current == null ? "non" : current.name, destination == null ? "aucune" : destination.name, train.derailed);
    }

    /** Voie, gares, train, depart, trajet et arrivee d'une ligne : les etapes {prefix}1 a {prefix}7. */
    private static void trainSteps(String prefix, Line line) {
        ShotHarness.SHOTS.add(server(prefix + "1_voie", 60, player -> {
            ServerLevel level = over(player);
            Block track = block(line.track);
            if (track == null) {
                log("[{}] voie : {} absente, ligne ignoree", line.label, line.track);
                return;
            }
            int x = line.x();
            fill(level, x - 8, gy - 1, gz - 44, x + 8, gy - 1, gz + 48, Blocks.STONE_BRICKS.defaultBlockState());
            fill(level, x - 8, gy, gz - 44, x + 8, gy + 12, gz + 48, Blocks.AIR.defaultBlockState());
            BlockState rail = straight(track);
            for (int z = gz - 40; z <= gz + 44; z++) {
                level.setBlock(new BlockPos(x, gy, z), rail, 3);
            }
            log("[{}] voie : {} blocs de {} a z {}..{}", line.label, 85, id(rail), gz - 40, gz + 44);
            look(player, x + 10.5d, gy + 8.0d, gz, 90.0f, 30.0f);
        }));
        ShotHarness.SHOTS.add(server(prefix + "2_gares", 40, player -> {
            if (block(line.track) == null) {
                return;
            }
            log("[{}] reseaux de voie : {}", line.label, com.zurrtum.create.Create.RAILWAYS.trackNetworks.size());
            station(player, line, gz + 4, "Gare A");
            station(player, line, gz + 38, "Gare B");
            station(player, line, gz - 30, "Gare C");
            look(player, line.x() + 10.5d, gy + 6.0d, gz + 4.5d, 90.0f, 20.0f);
        }));
        ShotHarness.SHOTS.add(server(prefix + "3_train", 60, player -> {
            if (line.stations.size() < 3) {
                return;
            }
            ServerLevel level = over(player);
            String[] names = {"Gare A", "Gare B", "Gare C"};
            for (int i = 0; i < line.stations.size(); i++) {
                var be = stationAt(player, line, i);
                boolean renamed = be.updateName(names[i]);
                log("[{}] gare {} : gare globale {}, renommee {}", line.label, names[i],
                        be.getStation() == null ? "aucune" : be.getStation().name, renamed);
            }
            var a = stationAt(player, line, 0);
            boolean assembling = a.enterAssemblyMode(player);
            Direction dir = a.getAssemblyDirection();
            BlockPos target = a.edgePoint.getGlobalPosition();
            log("[{}] assemblage : {}, sens {}, voie visee {}", line.label, assembling, dir, target);
            if (dir == null) {
                return;
            }
            BlockPos bogeyTrack = target.relative(dir, 1);
            InteractionResult casing = click(player, new ItemStack(com.zurrtum.create.AllItems.RAILWAY_CASING, 4), bogeyTrack, Direction.UP);
            BlockPos bogey = bogeyTrack.above();
            log("[{}] bogie : clic {}, bloc {}", line.label, casing, id(level.getBlockState(bogey)));
            BlockPos controls = bogey.above();
            // Create compte comme commandes avant celles qui regardent dans le sens d'assemblage.
            level.setBlock(controls, com.zurrtum.create.AllBlocks.TRAIN_CONTROLS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, dir), 3);
            // Sans conducteur, Create ne cherche aucun chemin : un blaze derriere les commandes conduit.
            BlockPos driver = controls.relative(dir);
            level.setBlock(driver, com.zurrtum.create.AllBlocks.BLAZE_BURNER.defaultBlockState()
                    .setValue(com.zurrtum.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
                            com.zurrtum.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SMOULDERING), 3);
            log("[{}] conducteur : {}", line.label, id(level.getBlockState(driver)));
            BlockPos low = new BlockPos(Math.min(bogey.getX(), driver.getX()), bogey.getY(), Math.min(bogey.getZ(), driver.getZ()));
            BlockPos high = new BlockPos(Math.max(bogey.getX(), driver.getX()), controls.getY(), Math.max(bogey.getZ(), driver.getZ()));
            level.addFreshEntity(new com.zurrtum.create.content.contraptions.glue.SuperGlueEntity(level,
                    new AABB(Vec3.atLowerCornerOf(low), Vec3.atLowerCornerOf(high.offset(1, 1, 1)))));
            a.refreshAssemblyInfo();
            a.assemble(player.getUUID());
            // Comme le bouton de l'ecran de gare : un train assemble fait quitter le mode assemblage
            // (une gare en assemblage barre la voie a la recherche de chemin).
            var present = a.getStation() == null ? null : a.getStation().getPresentTrain();
            line.train = present == null ? null : present.id;
            boolean exited = present != null && a.exitAssemblyMode();
            log("[{}] apres assemblage : {} train(s) en tout ; {} ; erreur {} ; fin du mode assemblage {}", line.label,
                    com.zurrtum.create.Create.RAILWAYS.trains.size(), describe(train(line)),
                    a.lastException == null ? "aucune" : a.lastException.component.getString(), exited);
            look(player, line.x() + 8.5d, gy + 4.0d, bogey.getZ() + 0.5d, 90.0f, 15.0f);
        }));
        ShotHarness.SHOTS.add(server(prefix + "4_depart", 40, player -> {
            var train = train(line);
            if (train == null) {
                log("[{}] depart : pas de train", line.label);
                return;
            }
            for (int index : new int[]{1, 2}) {
                var station = stationAt(player, line, index).getStation();
                if (station == null) {
                    continue;
                }
                var path = train.navigation.findPathTo(station, Double.MAX_VALUE);
                log("[{}] chemin vers {} : {}", line.label, station.name, path == null ? "aucun" : "trouve");
                if (path != null) {
                    double cost = train.navigation.startNavigation(path);
                    log("[{}] navigation vers {} : cout {}", line.label, station.name, cost);
                    break;
                }
            }
            log("[{}] depart : {}", line.label, describe(train));
            look(player, line.x() + 9.5d, gy + 5.0d, gz + 4.5d, 90.0f, 20.0f);
        }));
        ShotHarness.SHOTS.add(server(prefix + "5_route", 40, player -> {
            var train = train(line);
            log("[{}] en route : {}", line.label, describe(train));
            var entity = train == null || train.carriages.isEmpty() ? null : train.carriages.get(0).anyAvailableEntity();
            double z = entity == null ? gz + 10 : entity.getZ();
            look(player, line.x() + 9.5d, gy + 5.0d, z, 90.0f, 20.0f);
        }));
        ShotHarness.SHOTS.add(server(prefix + "6_arrivee", 200, player -> log("[{}] plus tard : {}", line.label, describe(train(line)))));
        ShotHarness.SHOTS.add(server(prefix + "7_gare", 40, player -> {
            var train = train(line);
            log("[{}] arrivee : {}", line.label, describe(train));
            var entity = train == null || train.carriages.isEmpty() ? null : train.carriages.get(0).anyAvailableEntity();
            double z = entity == null ? gz + 38 : entity.getZ();
            look(player, line.x() + 9.5d, gy + 5.0d, z, 90.0f, 20.0f);
        }));
    }

    // -- Steam 'n' Rails : vitrine ---------------------------------------------------------------------

    private static final String[] SNR_TRACKS = {"track_oak", "track_spruce", "track_birch", "track_jungle", "track_acacia",
            "track_dark_oak", "track_mangrove", "track_cherry", "track_bamboo", "track_stripped_bamboo", "track_crimson",
            "track_warped", "track_blackstone", "track_ender", "track_tieless", "track_phantom", "track_monorail",
            "track_oak_narrow", "track_oak_wide", "track_create_andesite_narrow", "track_create_andesite_wide"};

    private static final String[] SNR_BLOCKS = {"semaphore", "track_switch_andesite", "track_switch_brass", "track_coupler",
            "buffer", "big_buffer", "small_buffer", "buffer_narrow", "buffer_wide", "headstock", "link_and_pin",
            "copycat_headstock", "fuel_tank", "portable_fuel_interface", "conductor_whistle", "conductor_vent",
            "smokestack_caboosestyle", "smokestack_coalburner", "smokestack_diesel", "smokestack_long",
            "smokestack_oilburner", "smokestack_streamlined", "smokestack_woodburner", "handcar",
            "singleaxle_bogey", "doubleaxle_bogey", "tripleaxle_bogey", "medium_bogey", "large_create_styled_0_10_0",
            "narrow_small_bogey", "wide_doubleaxle_bogey", "mono_bogey",
            "black_riveted_locometal", "black_plated_locometal", "black_locometal_boiler", "red_locometal_boiler",
            "brass_wrapped_locometal_boiler", "black_locometal_smokebox", "black_locometal_vent",
            "hazard_stripes_chevron_on_black"};

    private static void snrSteps() {
        ShotHarness.SHOTS.add(server("r1_voies_snr", 60, player -> {
            ServerLevel level = over(player);
            if (block("railways:track_oak") == null) {
                log("snr : Steam 'n' Rails absent, vitrine ignoree");
                return;
            }
            int x0 = gx + 44;
            fill(level, x0 - 4, gy - 1, gz - 12, x0 + 3 * SNR_TRACKS.length + 2, gy - 1, gz + 40, Blocks.STONE_BRICKS.defaultBlockState());
            List<String> missing = new ArrayList<>();
            for (int i = 0; i < SNR_TRACKS.length; i++) {
                Block track = block("railways:" + SNR_TRACKS[i]);
                if (track == null) {
                    missing.add(SNR_TRACKS[i]);
                    continue;
                }
                BlockState rail = straight(track);
                for (int z = gz - 8; z <= gz + 8; z++) {
                    level.setBlock(new BlockPos(x0 + 3 * i, gy, z), rail, 3);
                }
            }
            log("snr : {} voies posees, absentes {}", SNR_TRACKS.length - missing.size(), missing);
            look(player, x0 + 1.5d * SNR_TRACKS.length, gy + 18.0d, gz - 24.0d, 0.0f, 38.0f);
        }));
        ShotHarness.SHOTS.add(server("r2_blocs_snr", 60, player -> {
            ServerLevel level = over(player);
            if (block("railways:track_oak") == null) {
                return;
            }
            int x0 = gx + 44;
            int z0 = gz + 18;
            List<String> missing = new ArrayList<>();
            for (int i = 0; i < SNR_BLOCKS.length; i++) {
                Block b = block("railways:" + SNR_BLOCKS[i]);
                if (b == null) {
                    missing.add(SNR_BLOCKS[i]);
                    continue;
                }
                BlockPos pos = new BlockPos(x0 + 3 * (i % 10), gy, z0 + 4 * (i / 10));
                level.setBlock(pos, b.defaultBlockState(), 2);
                String placed = id(level.getBlockState(pos));
                if (!placed.equals("railways:" + SNR_BLOCKS[i])) {
                    missing.add(SNR_BLOCKS[i] + " -> " + placed);
                }
            }
            log("snr : {} blocs poses, problemes {}", SNR_BLOCKS.length - missing.size(), missing);
            look(player, x0 + 13.5d, gy + 9.0d, z0 - 7.0d, 0.0f, 35.0f);
        }));
        ShotHarness.SHOTS.add(server("r3_conducteur_snr", 60, player -> {
            if (block("railways:track_oak") == null) {
                return;
            }
            Identifier key = Identifier.parse("railways:conductor");
            boolean known = BuiltInRegistries.ENTITY_TYPE.containsKey(key);
            int x0 = gx + 44;
            int z0 = gz + 34;
            op(player, "summon railways:conductor " + (x0 + 2) + " " + gy + " " + z0);
            ServerLevel level = over(player);
            int found = level.getEntities((Entity) null, new AABB(x0 - 2, gy - 1, z0 - 3, x0 + 6, gy + 3, z0 + 3),
                    e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(key)).size();
            log("snr : conducteur {} , {} present(s)", known ? "enregistre" : "inconnu", found);
            look(player, x0 + 2.5d, gy + 1.6d, z0 - 3.5d, 0.0f, 10.0f);
        }));
        ShotHarness.SHOTS.add(server("r4_objets_snr", 40, player -> {
            if (block("railways:track_oak") == null) {
                return;
            }
            ServerLevel level = over(player);
            // Barre d'objets : modeles d'objet enveloppes (tetes copycat, buffer teint en rouge, pichets).
            String[] items = {"copycat_headstock", "copycat_headstock_knuckle_coupler", "wooden_headstock_buffer",
                    "small_buffer", "small_buffer", "buffer", "link_and_pin", "red_paint_pitcher", "blue_paint_pitcher"};
            List<String> missing = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                Identifier key = Identifier.parse("railways:" + items[i]);
                if (!BuiltInRegistries.ITEM.containsKey(key)) {
                    missing.add(items[i]);
                    continue;
                }
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(key));
                if (i == 3 && block("railways:small_buffer") instanceof net.minecraft.world.level.block.EntityBlock entityBlock) {
                    Block b = block("railways:small_buffer");
                    var be = entityBlock.newBlockEntity(BlockPos.ZERO, b.defaultBlockState());
                    if (be != null) {
                        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                        tag.putInt("Color", 14);
                        stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                                net.minecraft.world.item.component.TypedEntityData.<net.minecraft.world.level.block.entity.BlockEntityType<?>>of(be.getType(), tag));
                    }
                }
                player.getInventory().setItem(i, stack);
            }
            log("snr : barre d'objets, absents {}", missing);
            // Trois reservoirs de Create, remplis a l'etape suivante (le reservoir se forme au premier tick).
            int x0 = gx + 44;
            int z0 = gz + 44;
            for (int i = 0; i < 3; i++) {
                level.setBlock(new BlockPos(x0 + 3 * i, gy, z0), com.zurrtum.create.AllBlocks.FLUID_TANK.defaultBlockState(), 3);
            }
            look(player, x0 + 3.5d, gy + 1.6d, z0 - 4.5d, 0.0f, 10.0f);
        }));
        ShotHarness.SHOTS.add(server("r5_peinture_snr", 60, player -> {
            if (block("railways:track_oak") == null) {
                return;
            }
            ServerLevel level = over(player);
            net.minecraft.world.level.material.Fluid paint = BuiltInRegistries.FLUID.getValue(Identifier.parse("railways:paint"));
            String[] colors = {"red", "blue", null};
            int x0 = gx + 44;
            int z0 = gz + 44;
            for (int i = 0; i < 3; i++) {
                BlockPos pos = new BlockPos(x0 + 3 * i, gy, z0);
                var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, pos, null);
                if (handler == null) {
                    log("snr : reservoir {} sans capacite fluide ({})", i, id(level.getBlockState(pos)));
                    continue;
                }
                net.minecraft.core.component.DataComponentPatch patch = net.minecraft.core.component.DataComponentPatch.EMPTY;
                if (colors[i] != null) {
                    net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                    tag.putString("Color", colors[i]);
                    patch = net.minecraft.core.component.DataComponentPatch.builder()
                            .set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag))
                            .build();
                }
                var resource = net.neoforged.neoforge.transfer.fluid.FluidResource.of(new net.neoforged.neoforge.fluids.FluidStack(paint, 1000, patch));
                int inserted;
                try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    inserted = handler.insert(resource, 8000, tx);
                    tx.commit();
                }
                log("snr : reservoir {} peinture {} : {} mB inseres", i, colors[i] == null ? "sans couleur" : colors[i], inserted);
            }
            look(player, x0 + 3.5d, gy + 1.6d, z0 - 4.5d, 0.0f, 10.0f);
        }));
    }

    // -- Trackwright (port NeoForge) : voie droite a travers un mur, tunnel creuse, annulation ---------------

    private static int kx() {
        return gx + 150;
    }

    /** Un mur de pierre 5 x 4 en travers d'une ligne nord-sud, de z0 a z0 + 3. */
    private static void wall(ServerLevel level, int x, int z0) {
        fill(level, x - 2, gy, z0, x + 2, gy + 3, z0 + 3, Blocks.STONE.defaultBlockState());
    }

    private static int countStone(ServerLevel level, int x, int z0) {
        int stone = 0;
        for (int y = gy; y <= gy + 3; y++) {
            for (int z = z0; z <= z0 + 3; z++) {
                if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) {
                    stone++;
                }
            }
        }
        return stone;
    }

    private static String trackLine(ServerLevel level, int x, int z0, int z1) {
        int tracks = 0;
        int connections = 0;
        for (int z = z0; z <= z1; z++) {
            BlockPos pos = new BlockPos(x, gy, z);
            if (level.getBlockState(pos).getBlock() instanceof com.zurrtum.create.content.trains.track.ITrackBlock) {
                tracks++;
            }
            if (level.getBlockEntity(pos) instanceof com.zurrtum.create.content.trains.track.TrackBlockEntity tbe) {
                connections += tbe.getConnections().size();
            }
        }
        return tracks + " bloc(s) de voie, " + connections + " raccord(s) courbe(s)";
    }

    /**
     * Pose de voie comme un joueur : un premier bloc de voie au sol, un clic dessus pour le choisir comme
     * depart (l'objet brille), puis un clic au sol 16 blocs plus loin vers +Z.
     */
    private static void layTrack(ServerPlayer player, int x, int z0, String label) {
        ServerLevel level = over(player);
        look(player, x + 0.5d, gy + 1.0d, z0 - 1.5d, 0.0f, 35.0f);
        ItemStack track = new ItemStack(com.zurrtum.create.AllItems.TRACK, 64);
        InteractionResult first = click(player, track, new BlockPos(x, gy - 1, z0), Direction.UP);
        InteractionResult start = click(player, player.getMainHandItem(), new BlockPos(x, gy, z0), Direction.UP);
        ItemStack held = player.getMainHandItem();
        boolean selected = held.has(com.zurrtum.create.AllDataComponents.TRACK_CONNECTING_FROM);
        InteractionResult end = click(player, held, new BlockPos(x, gy - 1, z0 + 16), Direction.UP);
        log("[trackwright {}] premier bloc {}, depart {} (selection {}), arrivee {} : {}", label, id(level.getBlockState(new BlockPos(x, gy, z0))),
                start.getClass().getSimpleName(), selected, end.getClass().getSimpleName(), trackLine(level, x, z0, z0 + 16));
    }

    private static void trackwrightSteps() {
        ShotHarness.SHOTS.add(server("k1_murs_tw", 40, player -> {
            ServerLevel level = over(player);
            boolean loaded = net.neoforged.fml.ModList.get().isLoaded("trackwright");
            log("trackwright : {}", loaded ? "charge" : "absent");
            if (!loaded) {
                return;
            }
            int x = kx();
            fill(level, x - 6, gy - 1, gz - 4, x + 14, gy - 1, gz + 22, Blocks.STONE_BRICKS.defaultBlockState());
            fill(level, x - 6, gy, gz - 4, x + 14, gy + 8, gz + 22, Blocks.AIR.defaultBlockState());
            wall(level, x, gz + 6);
            wall(level, x + 8, gz + 6);
            log("trackwright : murs poses, pierre {} et {}", countStone(level, x, gz + 6), countStone(level, x + 8, gz + 6));
            look(player, x + 4.5d, gy + 9.0d, gz - 6.0d, 0.0f, 40.0f);
        }));
        ShotHarness.SHOTS.add(server("k2_voie_mur_tw", 60, player -> {
            if (!net.neoforged.fml.ModList.get().isLoaded("trackwright")) {
                return;
            }
            ServerLevel level = over(player);
            int x = kx();
            // Main gauche vide : voie droite a travers le mur sans le creuser (straightThroughBlocks).
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            layTrack(player, x, gz, "a travers");
            log("trackwright : mur intact {}", countStone(level, x, gz + 6));
            look(player, x + 6.5d, gy + 4.0d, gz + 2.0d, 60.0f, 20.0f);
        }));
        ShotHarness.SHOTS.add(server("k3_tunnel_tw", 60, player -> {
            if (!net.neoforged.fml.ModList.get().isLoaded("trackwright")) {
                return;
            }
            ServerLevel level = over(player);
            int x = kx() + 8;
            // Une pioche dans l'autre main : la voie creuse son tunnel (boreThroughBlocks).
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            layTrack(player, x, gz, "tunnel");
            log("trackwright : pierre restante dans le mur {}", countStone(level, x, gz + 6));
            look(player, x + 0.5d, gy + 2.0d, gz + 2.0d, 0.0f, 10.0f);
        }));
        ShotHarness.SHOTS.add(server("k4_annuler_tw", 60, player -> {
            if (!net.neoforged.fml.ModList.get().isLoaded("trackwright")) {
                return;
            }
            ServerLevel level = over(player);
            int x = kx() + 8;
            op(player, "trackwright undo");
            log("trackwright : apres annulation, {} ; pierre {}", trackLine(level, x, gz, gz + 16), countStone(level, x, gz + 6));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            look(player, x + 0.5d, gy + 2.0d, gz + 2.0d, 0.0f, 10.0f);
        }));
    }

    static void register() {
        ShotHarness.SHOTS.add(client("p0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(8)));
        ShotHarness.SHOTS.add(server("p1_monde", 20, player -> {
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
            log("monde : sol a y {}, Steam 'n' Rails {}", gy, block("railways:track_oak") == null ? "absent" : "present");
        }));
        trainSteps("t", CREATE);
        trainSteps("w", WIDE);
        snrSteps();
        trackwrightSteps();
        ShotHarness.SHOTS.add(server("z_sauvegarde", 20, player -> {
            var server = player.level().getServer();
            server.saveEverything(false, true, true);
            java.nio.file.Path data = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
            try (var files = java.nio.file.Files.list(data)) {
                files.filter(f -> f.getFileName().toString().contains("create") || f.getFileName().toString().contains("railways"))
                        .forEach(f -> log("sauvegarde : {} ({} octets)", f.getFileName(), f.toFile().length()));
            } catch (Throwable t) {
                LOG.error("dohy-bug: sauvegarde", t);
            }
        }));
    }
}
