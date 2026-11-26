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

    // -- Le patrimoine mondial ------------------------------------------------------------------------

    private static int gx;
    private static int gy;
    private static int gz;

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

    private static BlockPos plaque() {
        return new BlockPos(gx, gy + 1, gz + 2);
    }

    private static String monument(ServerPlayer player) {
        var data = fr.atraxterra.dohy.faction.unesco.Unesco.data(player.level().getServer());
        StringBuilder text = new StringBuilder();
        for (var monument : data.monuments()) {
            text.append(monument.name()).append(" ").append(monument.score()).append("/100 ").append(monument.criteria())
                    .append(" poids ").append(monument.weight()).append(" subvention ").append(monument.grant())
                    .append(" blocs ").append(monument.built()).append(" guide ").append(monument.guide()).append(" ; ");
        }
        var wonder = data.wonder();
        text.append("merveille : ").append(wonder == null ? "aucune" : wonder.name() + " (" + wonder.nation() + ", "
                + (wonder.player() ? "joueur" : "rivale") + ")");
        return text.toString();
    }

    /** Un grand palais de pierre : deux etages, une tour, des fenetres, des meubles. */
    private static void palace(ServerLevel level) {
        int x0 = gx - 8;
        int x1 = gx + 8;
        int z0 = gz - 8;
        int z1 = gz + 8;
        fill(level, x0, gy, z0, x1, gy, z1, Blocks.OAK_PLANKS.defaultBlockState());
        fill(level, x0, gy + 1, z0, x1, gy + 7, z1, Blocks.STONE_BRICKS.defaultBlockState());
        fill(level, x0 + 1, gy + 1, z0 + 1, x1 - 1, gy + 7, z1 - 1, Blocks.AIR.defaultBlockState());
        fill(level, x0 + 1, gy + 4, z0 + 1, x1 - 1, gy + 4, z1 - 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        fill(level, x0 + 2, gy + 4, z0 + 2, x0 + 4, gy + 4, z0 + 4, Blocks.AIR.defaultBlockState());
        fill(level, x0, gy + 8, z0, x1, gy + 8, z1, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            fill(level, x, gy + 2, z0, x, gy + 3, z0, Blocks.GLASS_PANE.defaultBlockState());
            fill(level, x, gy + 2, z1, x, gy + 3, z1, Blocks.GLASS_PANE.defaultBlockState());
            fill(level, x, gy + 5, z0, x, gy + 6, z0, Blocks.GLASS_PANE.defaultBlockState());
            fill(level, x, gy + 5, z1, x, gy + 6, z1, Blocks.GLASS_PANE.defaultBlockState());
        }
        for (int z = z0 + 2; z <= z1 - 2; z += 3) {
            fill(level, x0, gy + 2, z, x0, gy + 3, z, Blocks.GLASS_PANE.defaultBlockState());
            fill(level, x1, gy + 2, z, x1, gy + 3, z, Blocks.GLASS_PANE.defaultBlockState());
        }
        for (int[] corner : new int[][] {{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
            fill(level, corner[0], gy + 1, corner[1], corner[0], gy + 8, corner[1],
                    Blocks.QUARTZ_PILLAR.defaultBlockState());
        }
        // L'entree, au sud.
        fill(level, gx - 1, gy + 1, z1, gx + 1, gy + 3, z1, Blocks.AIR.defaultBlockState());
        fill(level, gx - 2, gy + 1, z1 + 1, gx + 2, gy + 1, z1 + 1, Blocks.STONE_BRICK_STAIRS.defaultBlockState());
        // La tour.
        fill(level, gx - 2, gy + 9, gz - 2, gx + 2, gy + 20, gz + 2, Blocks.STONE_BRICKS.defaultBlockState());
        fill(level, gx - 1, gy + 9, gz - 1, gx + 1, gy + 20, gz + 1, Blocks.AIR.defaultBlockState());
        fill(level, gx - 2, gy + 21, gz - 2, gx + 2, gy + 21, gz + 2, Blocks.STONE_BRICK_WALL.defaultBlockState());
        // Les meubles.
        BlockState[] furniture = {Blocks.CHEST.defaultBlockState(), Blocks.FURNACE.defaultBlockState(),
                Blocks.CRAFTING_TABLE.defaultBlockState(), Blocks.LECTERN.defaultBlockState(),
                Blocks.ANVIL.defaultBlockState(), Blocks.BARREL.defaultBlockState(),
                Blocks.BREWING_STAND.defaultBlockState(), Blocks.ENCHANTING_TABLE.defaultBlockState(),
                Blocks.SMITHING_TABLE.defaultBlockState(), Blocks.LOOM.defaultBlockState()};
        for (int i = 0; i < furniture.length; i++) {
            level.setBlock(new BlockPos(x0 + 1 + i, gy + 1, z0 + 1), furniture[i], 2);
        }
        fill(level, x1 - 1, gy + 1, z0 + 1, x1 - 1, gy + 3, z0 + 6, Blocks.BOOKSHELF.defaultBlockState());
        for (int i = 0; i < 6; i++) {
            level.setBlock(new BlockPos(x0 + 2 + 2 * i, gy + 3, z0 + 8), Blocks.LANTERN.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + 2 + 2 * i, gy + 1, z1 - 1), Blocks.FLOWER_POT.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + 2 + 2 * i, gy + 5, z0 + 8), Blocks.LANTERN.defaultBlockState(), 2);
        }
        fill(level, gx - 3, gy + 1, gz - 3, gx + 3, gy + 1, gz - 1, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState());
        fill(level, x0 + 1, gy + 5, z1 - 3, x0 + 6, gy + 5, z1 - 1, Blocks.OAK_FENCE.defaultBlockState());
        fill(level, x1 - 6, gy + 5, z1 - 2, x1 - 1, gy + 5, z1 - 1, Blocks.OAK_SLAB.defaultBlockState());
        fill(level, x1 - 6, gy + 5, z0 + 1, x1 - 1, gy + 5, z0 + 2, Blocks.SPRUCE_STAIRS.defaultBlockState());
    }

    static void register() {
        ShotHarness.SHOTS.add(client("p0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(8)));
        ShotHarness.SHOTS.add(server("u1_palais", 60, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule advance_time false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty easy");
            player.getInventory().clearContent();
            ServerLevel level = over(player);
            gx = 16 * 40 + 8;
            gz = 16 * 40 + 8;
            level.getChunk(gx >> 4, gz >> 4);
            gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
            fill(level, gx - 30, gy - 1, gz - 30, gx + 30, gy - 1, gz + 30, Blocks.GRASS_BLOCK.defaultBlockState());
            fill(level, gx - 30, gy, gz - 30, gx + 30, gy + 30, gz + 30, Blocks.AIR.defaultBlockState());
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            log("nation : {}", FactionApi.create(player, "Testeurs", "Une nation de test", List.of(), 0));
            player.getInventory().clearContent();
            fr.atraxterra.dohy.faction.FactionTree.setTier(player, fr.atraxterra.dohy.faction.FactionBranch.GLOBAL, 6);
            fr.atraxterra.dohy.faction.FactionTree.setTier(player, fr.atraxterra.dohy.faction.FactionBranch.DEFENSE, 4);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    String result = FactionApi.claimChunk(player, level, (gx >> 4) + dx, (gz >> 4) + dz);
                    if (result != null) {
                        log("claim {} {} : {}", dx, dz, result);
                    }
                }
            }
            palace(level);
            // Une petite cabane a cote, pour le refus.
            fill(level, gx + 25, gy, gz - 3, gx + 28, gy + 2, gz, Blocks.OAK_PLANKS.defaultBlockState());
            BlockState plaqueState = fr.atraxterra.dohy.faction.unesco.DoHyUnesco.PLAQUE.get().defaultBlockState()
                    .setValue(fr.atraxterra.dohy.faction.unesco.UnescoPlaqueBlock.FACING, Direction.SOUTH);
            level.setBlock(plaque(), plaqueState, 3);
            ItemStack named = new ItemStack(fr.atraxterra.dohy.faction.unesco.DoHyUnesco.PLAQUE_ITEM.get());
            named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Le Grand Palais"));
            plaqueState.getBlock().setPlacedBy(level, plaque(), plaqueState, player, named);
            BlockPos shed = new BlockPos(gx + 26, gy + 3, gz - 1);
            level.setBlock(shed, plaqueState, 3);
            plaqueState.getBlock().setPlacedBy(level, shed, plaqueState, player, ItemStack.EMPTY);
            look(player, gx + 0.5d, gy + 9.0d, gz + 30.0d, 180.0f, 12.0f);
        }));
        ShotHarness.SHOTS.add(server("u2_dossier", 30, player -> {
            var menu = fr.atraxterra.dohy.faction.unesco.UnescoMenu.INSTANCE;
            look(player, gx + 26.5d, gy + 3.0d, gz + 1.5d, 180.0f, 10.0f);
            menu.openAt(player, new BlockPos(gx + 26, gy + 3, gz - 1));
            menu.handle(player, new UiNet.Action(menu.id(), "card", "submit", 0));
            log("cabane : {}", monument(player));
            try {
                java.lang.reflect.Field f = fr.atraxterra.dohy.faction.unesco.Unesco.class.getDeclaredField("LAST_SUBMIT");
                f.setAccessible(true);
                ((Map<?, ?>) f.get(null)).clear();
            } catch (Throwable t) {
                LOG.error("dohy-bug: delai", t);
            }
            long bank = FactionApi.data(player).bank();
            look(player, gx + 0.5d, gy + 1.0d, gz + 5.5d, 180.0f, 10.0f);
            menu.openAt(player, plaque());
            menu.handle(player, new UiNet.Action(menu.id(), "card", "submit", 0));
            log("palais : {} ; banque {} -> {}", monument(player), bank, FactionApi.data(player).bank());
        }));
        ShotHarness.SHOTS.add(server("u3_merveille", 20, player -> {
            UiNet.close(player);
            ServerLevel level = over(player);
            Map<String, Integer> terrain = new TreeMap<>();
            for (BlockPos pos : BlockPos.betweenClosed(gx + 10, gy - 6, gz - 17, gx + 42, gy - 1, gz + 15)) {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    terrain.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
                }
            }
            log("terrain sous la cabane : {}", terrain);
            var monuments = fr.atraxterra.dohy.faction.unesco.Unesco.monuments(player);
            if (!monuments.isEmpty()) {
                var menu = fr.atraxterra.dohy.faction.unesco.UnescoMenu.INSTANCE;
                menu.handle(player, new UiNet.Action(menu.id(), "card", "guide:" + monuments.get(0).id(), 1));
            }
            var server = player.level().getServer();
            var data = fr.atraxterra.dohy.faction.unesco.Unesco.data(server);
            try {
                java.lang.reflect.Method elect = fr.atraxterra.dohy.faction.unesco.Unesco.class.getDeclaredMethod(
                        "elect", MinecraftServer.class, fr.atraxterra.dohy.faction.unesco.UnescoData.class, int.class);
                elect.setAccessible(true);
                java.lang.reflect.Method save = fr.atraxterra.dohy.faction.unesco.Unesco.class.getDeclaredMethod(
                        "save", MinecraftServer.class, fr.atraxterra.dohy.faction.unesco.UnescoData.class);
                save.setAccessible(true);
                for (int month = 202611; month <= 202710; month = month % 100 == 12 ? month + 89 : month + 1) {
                    elect.invoke(null, server, data, month);
                    if (data.wonder() != null && data.wonder().player()) {
                        break;
                    }
                }
                save.invoke(null, server, data);
            } catch (Throwable t) {
                LOG.error("dohy-bug: election", t);
            }
            log("apres election : {}", monument(player));
            look(player, gx + 0.5d, gy + 1.0d, gz + 6.5d, 180.0f, 6.0f);
        }));
        ShotHarness.SHOTS.add(server("u4_plaque", 60, player -> {
            log("guide : {}", over(player).getEntities((Entity) null, new AABB(plaque()).inflate(8.0d),
                    e -> e.entityTags().contains("dohy_unesco_guide")).size());
            look(player, gx + 0.5d, gy + 1.0d, gz + 6.5d, 180.0f, 6.0f);
        }));
        ShotHarness.SHOTS.add(server("u5_ecran", 30, player -> {
            openMenu(player, fr.atraxterra.dohy.faction.unesco.UnescoMenu.INSTANCE);
        }));
    }
}
