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

/** Temporaire : le lot 24c (objets, postes en relief, liens Create et NTM NEXT). */
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

    // -- Les objets ------------------------------------------------------------------------------


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

    private static boolean factionMenu(MenuController menu) {
        String type = menu.getClass().getName();
        return type.contains(".faction.") || type.contains(".earth.");
    }

    // -- Les blocs -------------------------------------------------------------------------------

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

    // -- Les commandes ---------------------------------------------------------------------------


    // -- Le lot 24c ------------------------------------------------------------------------------

    private static final int SZ = 2994;
    private static final BlockPos LAB = new BlockPos(2997, Y, SZ);
    private static final BlockPos TABLE = new BlockPos(2999, Y, SZ);
    private static final BlockPos BENCH = new BlockPos(3001, Y, SZ);
    private static final BlockPos SAFE = new BlockPos(3003, Y, SZ);
    private static final BlockPos RACK = new BlockPos(3005, Y, SZ);
    private static final BlockPos RACK_EMPTY = new BlockPos(2999, Y, 2991);
    private static final BlockPos RACK_DRY = new BlockPos(3001, Y, 2991);
    private static final BlockPos CROPS = new BlockPos(3003, Y, 2991);
    private static final BlockPos AUTO = new BlockPos(3009, Y, 2999);
    private static final BlockPos WORK_RACK = new BlockPos(3012, Y, 2999);
    private static final BlockPos FIELD = new BlockPos(3009, Y, 3003);

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

    private static fr.atraxterra.dohy.faction.enterprise.Holdings holdings(ServerPlayer player) {
        return fr.atraxterra.dohy.faction.enterprise.Enterprises.of(player);
    }

    private static void stocks(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.enterprise.Holdings holdings = holdings(player);
        List<String> rows = new ArrayList<>();
        for (fr.atraxterra.dohy.faction.enterprise.Line line : fr.atraxterra.dohy.faction.enterprise.Line.values()) {
            rows.add(line + " " + holdings.stock(line.rawKey()) + "/" + holdings.stock(line.productKey()));
        }
        rows.add("caisses " + holdings.stock(fr.atraxterra.dohy.faction.enterprise.Enterprises.CRATES));
        log("stocks [{}] : {} ; sale {} ; place {}", label, rows, holdings.dirty(),
                fr.atraxterra.dohy.faction.enterprise.Enterprises.capacity(player));
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static String contents(net.minecraft.world.Container container) {
        List<String> parts = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                parts.add(stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
        return parts.isEmpty() ? "vide" : String.join(", ", parts);
    }

    private static String inventory(ServerPlayer player, String... ids) {
        List<String> parts = new ArrayList<>();
        for (String id : ids) {
            parts.add(id + "=" + count(player, item(id)));
        }
        return String.join(" ", parts);
    }

    private static void give(ServerPlayer player, String id, int count) {
        ItemStack stack = new ItemStack(item(id), count);
        if (!player.getInventory().add(stack)) {
            log("inventaire plein pour {}", id);
        }
    }

    private static BlockState facing(Block block, Direction direction) {
        BlockState state = block.defaultBlockState();
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            if (property.getName().equals("facing")) {
                @SuppressWarnings("unchecked")
                net.minecraft.world.level.block.state.properties.Property<Direction> typed =
                        (net.minecraft.world.level.block.state.properties.Property<Direction>) property;
                return state.setValue(typed, direction);
            }
        }
        return state;
    }

    private static void stash(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState safe = facing(DoHyUnderground.STASH_CHEST.get(), Direction.SOUTH);
        level.setBlock(pos, safe, 3);
        DoHyUnderground.STASH_CHEST.get().setPlacedBy(level, pos, safe, player,
                new ItemStack(DoHyUnderground.STASH_CHEST_ITEM.get()));
    }

    private static void showroom(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        level.setBlock(LAB, facing(DoHyUnderground.CLANDESTINE_LAB.get(), Direction.SOUTH), 3);
        level.setBlock(TABLE, facing(DoHyUnderground.PACKAGING_TABLE.get(), Direction.SOUTH), 3);
        level.setBlock(BENCH, facing(DoHyUnderground.ARMS_WORKBENCH.get(), Direction.SOUTH), 3);
        stash(player, SAFE);
        level.setBlock(RACK, DoHyUnderground.DRYING_RACK.get().defaultBlockState()
                .setValue(fr.atraxterra.dohy.underground.DryingRackBlock.STAGE, 1), 3);
        level.setBlock(RACK_EMPTY, DoHyUnderground.DRYING_RACK.get().defaultBlockState(), 3);
        level.setBlock(RACK_DRY, DoHyUnderground.DRYING_RACK.get().defaultBlockState()
                .setValue(fr.atraxterra.dohy.underground.DryingRackBlock.STAGE, 2), 3);
        List<fr.atraxterra.dohy.underground.DrugCropBlock> crops = List.of(DoHyUnderground.CANNABIS_CROP.get(),
                DoHyUnderground.COCA_CROP.get(), DoHyUnderground.POPPY_CROP.get());
        for (int i = 0; i < crops.size(); i++) {
            BlockPos crop = CROPS.east(i);
            level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            level.setBlock(crop, crops.get(i).getStateForAge(7), 3);
        }
        for (BlockPos pos : List.of(LAB, TABLE, BENCH, SAFE, RACK, RACK_EMPTY, RACK_DRY)) {
            BlockState state = level.getBlockState(pos);
            log("pose {} : {} ; forme {} ; plein {}", pos.toShortString(), state,
                    state.getShape(level, pos).bounds(), state.isCollisionShapeFullBlock(level, pos));
        }
    }

    /** Le joueur (spectateur) regarde le point (tx, ty, tz) depuis (cx, cy, cz) : cy est la hauteur des pieds. */
    private static void look(ServerPlayer player, double cx, double cy, double cz, double tx, double ty, double tz) {
        double dx = tx - cx;
        double dy = ty - (cy + 1.62d);
        double dz = tz - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        teleport(player, cx, cy, cz, yaw, pitch);
    }

    private static ShotHarness.Shot view(String name, double dx, double dy, double dz, BlockPos target) {
        return server(name, 40, player -> look(player, target.getX() + 0.5d + dx, target.getY() + dy,
                target.getZ() + 0.5d + dz, target.getX() + 0.5d, target.getY() + 0.45d, target.getZ() + 0.5d));
    }

    private static void hideGui(boolean hide) {
        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            List<Object> owners = new ArrayList<>();
            owners.add(minecraft.options);
            owners.add(minecraft.gui);
            owners.add(minecraft);
            for (Object owner : owners) {
                for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                    for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                        String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                        if (!name.contains("hide") || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                            continue;
                        }
                        try {
                            field.setAccessible(true);
                            if (field.getType() == boolean.class) {
                                field.setBoolean(owner, hide);
                                LOG.info("dohy-bug: interface {} par {}.{}", hide ? "cachee" : "montree",
                                        type.getSimpleName(), field.getName());
                                return;
                            }
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            LOG.info("dohy-bug: champ {} : {}", field.getName(), error.toString());
                        }
                    }
                }
            }
            LOG.info("dohy-bug: aucun drapeau pour cacher l'interface");
        });
    }

    private static void useOn(ServerPlayer player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        log("clic droit sur {} avec {} : {} ; etat {}", pos.toShortString(), stack, result,
                player.level().getBlockState(pos));
    }

    private static void craft(ServerPlayer player, fr.atraxterra.dohy.underground.WorkstationBlock.Kind kind,
                              BlockPos pos, String key) {
        fr.atraxterra.dohy.underground.WorkstationMenus.Station station =
                fr.atraxterra.dohy.underground.WorkstationMenus.of(kind);
        station.openAt(player, pos);
        station.handle(player, new UiNet.Action(station.id(), "card", key, 0));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 100, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule doDaylightCycle false");
            op(player, "gamerule advance_time false");
            op(player, "gamerule doMobSpawning false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty peaceful");
            player.setGameMode(GameType.SURVIVAL);
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            Economy.set(player, 1_000_000L);
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 36));
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 24c", List.of(), 3);
            log("nation : {}", refusal == null ? "fondee" : refusal);
            FactionApi.claim(player);
            FactionApi.creditBank(player, 5_000_000L, "Essai");
            log("mods : create {} ; hbm {} ; jei {}", net.neoforged.fml.ModList.get().isLoaded("create"),
                    net.neoforged.fml.ModList.get().isLoaded("hbm"), net.neoforged.fml.ModList.get().isLoaded("jei"));
            showroom(player);
            stocks(player, "depart");
        }));
        // -- les postes en relief, sans interface -------------------------------------------------
        ShotHarness.SHOTS.add(server("b0_vitrine", 60, player -> {
            player.setGameMode(GameType.SPECTATOR);
            hideGui(true);
            look(player, 3001.0d, -57.6d, 3000.2d, 3001.0d, -59.6d, 2993.0d);
        }));
        ShotHarness.SHOTS.add(view("b1_labo", 1.35d, 0.75d, 1.55d, LAB));
        ShotHarness.SHOTS.add(view("b2_labo_gauche", -1.45d, 0.75d, 1.5d, LAB));
        ShotHarness.SHOTS.add(view("b3_table", 1.35d, 0.75d, 1.55d, TABLE));
        ShotHarness.SHOTS.add(view("b4_etabli", 1.35d, 0.75d, 1.55d, BENCH));
        ShotHarness.SHOTS.add(view("b5_etabli_gauche", -1.45d, 0.75d, 1.5d, BENCH));
        ShotHarness.SHOTS.add(view("b6_planque", 1.4d, 0.45d, 1.6d, SAFE));
        ShotHarness.SHOTS.add(view("b7_sechoir", 1.45d, 0.75d, 1.6d, RACK));
        ShotHarness.SHOTS.add(server("b8_sechoirs_plantes", 40, player -> look(player, 3002.0d, -58.0d, 2995.6d,
                3002.0d, -59.6d, 2991.5d)));
        ShotHarness.SHOTS.add(server("b9_derriere", 40, player -> look(player, 3000.0d, -57.8d, 2988.2d, 3000.0d,
                -59.6d, 2994.5d)));
        ShotHarness.SHOTS.add(server("b10_barre", 40, player -> {
            hideGui(false);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            String[] bar = {"dohy:clandestine_lab", "dohy:packaging_table", "dohy:arms_workbench", "dohy:stash_chest",
                    "dohy:drying_rack", "dohy:powder_bag", "dohy:herb_parcel", "dohy:sealed_crate",
                    "dohy:incomplete_sealed_crate"};
            for (int slot = 0; slot < bar.length; slot++) {
                player.getInventory().setItem(slot, new ItemStack(item(bar[slot])));
            }
            teleport(player, 3001.0d, Y, 2999.5d, 180.0f, 30.0f);
        }));
        // -- les postes, a l'usage -----------------------------------------------------------------
        ShotHarness.SHOTS.add(server("c0_labo", 20, player -> {
            player.getInventory().clearContent();
            teleport(player, 2999.5d, Y, 2997.5d, 160.0f, 25.0f);
            player.removeAllEffects();
            give(player, "dohy:coca_leaf", 8);
            give(player, "dohy:precursor_can", 6);
            give(player, "dohy:cannabis_leaf", 8);
            craft(player, fr.atraxterra.dohy.underground.WorkstationBlock.Kind.LAB, LAB, "one:powder");
            log("labo sans protection : {} ; nausee {} poison {}", inventory(player, "dohy:cocaine_powder",
                    "dohy:coca_leaf", "dohy:precursor_can"), player.hasEffect(MobEffects.NAUSEA),
                    player.hasEffect(MobEffects.POISON));
            player.removeAllEffects();
            Item mask = item("hbm:gas_mask");
            Item suit = item("hbm:hazmat_plate");
            boolean ntm = mask != Items.AIR && suit != Items.AIR;
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ntm ? mask : DoHyUnderground.GAS_MASK.get()));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ntm ? suit : DoHyUnderground.HAZMAT_SUIT.get()));
            craft(player, fr.atraxterra.dohy.underground.WorkstationBlock.Kind.LAB, LAB, "one:powder");
            log("labo avec {} : {} ; nausee {} poison {}", ntm ? "les protections de NTM" : "les protections du mod",
                    inventory(player, "dohy:cocaine_powder", "dohy:coca_leaf", "dohy:precursor_can"),
                    player.hasEffect(MobEffects.NAUSEA), player.hasEffect(MobEffects.POISON));
            craft(player, fr.atraxterra.dohy.underground.WorkstationBlock.Kind.LAB, LAB, "many:herb");
            craft(player, fr.atraxterra.dohy.underground.WorkstationBlock.Kind.LAB, LAB, "one:pills");
            log("labo : {}", inventory(player, "dohy:dried_herb", "dohy:synthetic_pills", "dohy:cannabis_leaf",
                    "dohy:precursor_can"));
            fr.atraxterra.dohy.underground.WorkstationMenus.of(
                    fr.atraxterra.dohy.underground.WorkstationBlock.Kind.LAB).openAt(player, LAB);
        }));
        ShotHarness.SHOTS.add(server("c1_table", 20, player -> {
            give(player, "dohy:cocaine_powder", 2);
            give(player, "dohy:empty_bag", 4);
            give(player, "dohy:precision_scale", 1);
            give(player, "minecraft:paper", 2);
            craft(player, fr.atraxterra.dohy.underground.WorkstationBlock.Kind.PACKAGING, TABLE, "many:powder_bag");
            log("table, sachets : {}", inventory(player, "dohy:powder_bag", "dohy:cocaine_powder", "dohy:empty_bag"));
            give(player, "dohy:powder_bag", 7);
            craft(player, fr.atraxterra.dohy.underground.WorkstationBlock.Kind.PACKAGING, TABLE, "one:powder_parcel");
            log("table, colis : {}", inventory(player, "dohy:powder_parcel", "dohy:powder_bag", "minecraft:paper"));
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(DoHyUnderground.PRECISION_SCALE.get())) {
                    log("balance : {} / {} uses", stack.getDamageValue(), stack.getMaxDamage());
                }
            }
            give(player, "dohy:synthetic_pills", 4);
            fr.atraxterra.dohy.underground.WorkstationMenus.of(
                    fr.atraxterra.dohy.underground.WorkstationBlock.Kind.PACKAGING).openAt(player, TABLE);
        }));
        ShotHarness.SHOTS.add(server("c2_etabli", 20, player -> {
            give(player, "minecraft:iron_ingot", 11);
            craft(player, fr.atraxterra.dohy.underground.WorkstationBlock.Kind.ARMS, BENCH, "many:parts");
            craft(player, fr.atraxterra.dohy.underground.WorkstationBlock.Kind.ARMS, BENCH, "one:crate");
            log("etabli : {}", inventory(player, "dohy:machined_parts", "dohy:sealed_crate", "minecraft:iron_ingot"));
            fr.atraxterra.dohy.underground.WorkstationMenus.of(
                    fr.atraxterra.dohy.underground.WorkstationBlock.Kind.ARMS).openAt(player, BENCH);
        }));
        // -- le sechoir, les graines, la planque ouverte aux machines -----------------------------
        ShotHarness.SHOTS.add(server("d0_machines", 200, player -> {
            UiNet.close(player);
            ServerLevel level = (ServerLevel) player.level();
            teleport(player, 3010.5d, Y, 3002.5d, 160.0f, 30.0f);
            level.setBlock(WORK_RACK, DoHyUnderground.DRYING_RACK.get().defaultBlockState(), 3);
            give(player, "dohy:cannabis_leaf", 4);
            useOn(player, WORK_RACK, new ItemStack(DoHyUnderground.CANNABIS_LEAF.get(), 4));
            for (int i = 0; i < 3; i++) {
                level.setBlock(FIELD.east(i).below(), Blocks.FARMLAND.defaultBlockState(), 3);
            }
            useOn(player, FIELD.below(), new ItemStack(DoHyUnderground.CANNABIS_SEEDS.get(), 1));
            useOn(player, FIELD.east(1).below(), new ItemStack(DoHyUnderground.COCA_SEEDS.get(), 1));
            useOn(player, FIELD.east(2).below(), new ItemStack(DoHyUnderground.POPPY_SEEDS.get(), 1));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            stash(player, AUTO);
            level.setBlock(AUTO.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING,
                    Direction.DOWN), 3);
            level.setBlock(AUTO.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING,
                    Direction.DOWN), 3);
            if (level.getBlockEntity(AUTO.above()) instanceof HopperBlockEntity hopper) {
                hopper.setItem(0, new ItemStack(DoHyUnderground.POWDER_BAG.get(), 8));
                hopper.setItem(1, new ItemStack(Items.DIRT, 4));
                hopper.setItem(2, new ItemStack(DoHyUnderground.HERB_PARCEL.get(), 1));
            }
            if (level.getBlockEntity(AUTO) instanceof fr.atraxterra.dohy.underground.StashChestBlockEntity box) {
                box.setItem(26, new ItemStack(Items.COBBLESTONE, 5));
                log("planque posee : proprietaire {} ; contenu {}", box.ownerName(), contents(box));
            }
            stocks(player, "avant les machines");
        }));
        ShotHarness.SHOTS.add(server("d1_entonnoirs", 40, player -> {
            ServerLevel level = (ServerLevel) player.level();
            if (level.getBlockEntity(AUTO.above()) instanceof HopperBlockEntity above) {
                log("entonnoir du dessus : {}", contents(above));
            }
            if (level.getBlockEntity(AUTO.below()) instanceof HopperBlockEntity below) {
                log("entonnoir du dessous (ne doit rien tirer) : {}", contents(below));
            }
            if (level.getBlockEntity(AUTO) instanceof fr.atraxterra.dohy.underground.StashChestBlockEntity box) {
                log("planque apres 10 s : {}", contents(box));
            }
            stocks(player, "10 s");
            look(player, AUTO.getX() + 2.4d, Y + 0.6d, AUTO.getZ() + 2.6d, AUTO.getX() + 0.5d, Y + 0.6d,
                    AUTO.getZ() + 0.5d);
        }));
        ShotHarness.SHOTS.add(server("d2_versement", 1300, player -> log("attente du versement automatique")));
        ShotHarness.SHOTS.add(server("d3_apres_versement", 20, player -> {
            ServerLevel level = (ServerLevel) player.level();
            if (level.getBlockEntity(AUTO) instanceof fr.atraxterra.dohy.underground.StashChestBlockEntity box) {
                log("planque apres une minute : {}", contents(box));
            }
            if (level.getBlockEntity(AUTO.below()) instanceof HopperBlockEntity below) {
                log("entonnoir du dessous : {}", contents(below));
            }
            stocks(player, "apres le versement automatique");
            log("sechoir apres 75 s : {}", level.getBlockState(WORK_RACK));
            for (int i = 0; i < 3; i++) {
                log("champ {} : {}", i, level.getBlockState(FIELD.east(i)));
            }
        }));
        ShotHarness.SHOTS.add(server("d4_sechage", 1150, player -> log("attente du sechage")));
        ShotHarness.SHOTS.add(server("d5_recolte", 20, player -> {
            ServerLevel level = (ServerLevel) player.level();
            log("sechoir apres 2 min : {}", level.getBlockState(WORK_RACK));
            int before = count(player, DoHyUnderground.DRIED_HERB.get());
            useOn(player, WORK_RACK, ItemStack.EMPTY);
            log("herbe sechee recoltee : {}", count(player, DoHyUnderground.DRIED_HERB.get()) - before);
            List<String> journal = holdings(player).journal();
            log("journal : {}", journal.subList(0, Math.min(6, journal.size())));
        }));
        // -- les comptes, puis le tour de toutes les vues -----------------------------------------
        ShotHarness.SHOTS.add(server("e0_comptes", 20, player -> openMenu(player, menu("faction_enterprise_finance"))));
        ShotHarness.SHOTS.add(server("e1_sortir", 20, player -> {
            MenuController finance = menu("faction_enterprise_finance");
            finance.handle(player, new UiNet.Action(finance.id(), "card", "withdraw:bag:COCAINE", 0));
            finance.handle(player, new UiNet.Action(finance.id(), "card", "withdraw:parcel:CANNABIS", 0));
            finance.handle(player, new UiNet.Action(finance.id(), "card", "buy:precursors", 0));
            log("sortis et achetes : {}", inventory(player, "dohy:powder_bag", "dohy:herb_parcel",
                    "dohy:precursor_can"));
            stocks(player, "apres sortie");
        }));
        // -- JEI : les recettes de Create pour les objets du milieu --------------------------------
        ShotHarness.SHOTS.add(client("f0_jei_poudre", 60, () -> jei("dohy:cocaine_powder")));
        ShotHarness.SHOTS.add(client("f1_jei_sachet", 60, () -> jei("dohy:powder_bag")));
        ShotHarness.SHOTS.add(client("f2_jei_caisse", 60, () -> jei("dohy:sealed_crate")));
        ShotHarness.SHOTS.add(client("f3_jei_bidon", 60, () -> jei("dohy:precursor_can")));
        ShotHarness.SHOTS.add(client("f4_jei_pilules", 60, () -> jei("dohy:synthetic_pills")));
        ShotHarness.SHOTS.add(client("f9_jei_ferme", 20, ShotJei::close));
        ShotHarness.SHOTS.add(server("g0_vues", 10, player -> views(player, "lot 24c")));
        ShotHarness.SHOTS.add(server("g1_clics", 100, player -> {
            UiNet.close(player);
            teleport(player, 2999.5d, Y, 2997.5d, 180.0f, 20.0f);
            clicks(player);
            stocks(player, "apres clics");
        }));
        ShotHarness.SHOTS.add(server("z0_fin", 10, player -> {
            UiNet.close(player);
            FactionApi.disband(player);
            log("fin ; joueur {}", me);
        }));
    }

    private static void jei(String id) {
        Object runtime = ShotJei.runtime();
        if (runtime == null) {
            LOG.info("dohy-bug: JEI absent ou pas pret pour {}", id);
            return;
        }
        try {
            ShotJei.showOutput(runtime, item(id));
            LOG.info("dohy-bug: JEI ouvert sur {} : {}", id, Minecraft.getInstance().gui.screen() == null ? "aucun ecran"
                    : Minecraft.getInstance().gui.screen().getClass().getName());
        } catch (Throwable error) {
            LOG.error("dohy-bug: JEI " + id, error);
        }
    }
}
