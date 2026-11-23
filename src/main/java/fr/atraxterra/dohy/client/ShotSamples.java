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

/** Temporaire : le chanvre de NTM NEXT. */
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

    // -- Le chanvre de NTM NEXT ---------------------------------------------------------------------

    private static int hx;
    private static int hy;
    private static int hz;

    private static ServerPlayer player() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null || me == null ? null : server.getPlayerList().getPlayer(me);
    }

    private static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static Map<String, Integer> drops(ServerLevel level, BlockPos at, double radius) {
        Map<String, Integer> found = new TreeMap<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(radius))) {
            found.merge(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(),
                    item.getItem().getCount(), Integer::sum);
            item.discard();
        }
        return found;
    }

    private static boolean tagged(ItemStack stack, String tag) {
        return stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                Identifier.parse(tag)));
    }

    private static long farmerXp(ServerPlayer player) {
        return fr.atraxterra.dohy.job.JobApi.data(player).xpOf(fr.atraxterra.dohy.job.Job.FARMER);
    }

    /** Un chanvre haut sur de l'herbe, le pied en {@code pos}. */
    private static void tall(ServerLevel level, BlockPos pos) {
        Block block = BuiltInRegistries.BLOCK.getValue(fr.atraxterra.dohy.agri.Hemp.TALL);
        level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        net.minecraft.world.level.block.DoublePlantBlock.placeAt(level, block.defaultBlockState(), pos, 3);
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(6)));
        ShotHarness.SHOTS.add(server("a1_depart", 40, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule advance_time false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty peaceful");
            player.setGameMode(GameType.SURVIVAL);
            // Le joueur arrive dans la dimension du spawn, ou rien ne se casse : le champ va dans l'Overworld.
            ServerLevel level = ((ServerLevel) player.level()).getServer().overworld();
            hx = 200;
            hz = 200;
            level.getChunk(hx >> 4, hz >> 4);
            hy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hx, hz);
            for (int x = -8; x <= 44; x++) {
                for (int z = -4; z <= 31; z++) {
                    level.setBlock(new BlockPos(hx + x, hy - 1, hz + z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int y = 0; y < 6; y++) {
                        level.setBlock(new BlockPos(hx + x, hy + y, hz + z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            player.teleport(new TeleportTransition(level, new Vec3(hx + 2.5d, hy, hz - 1.5d), Vec3.ZERO, -10.0f, 18.0f,
                    TeleportTransition.DO_NOTHING));
            log("chanvre : NTM {} ; fleur {} ; haut {} ; sol {} {} {} ; dimension {}",
                    BuiltInRegistries.BLOCK.getOptional(fr.atraxterra.dohy.agri.Hemp.TALL).isPresent(),
                    fr.atraxterra.dohy.agri.Hemp.flower(),
                    BuiltInRegistries.BLOCK.getOptional(fr.atraxterra.dohy.agri.Hemp.TALL).orElse(null),
                    hx, hy, hz, player.level().dimension().identifier());
            for (String item : List.of("dohy:hemp_fiber", "dohy:hemp_seeds", "dohy:hemp_canvas", "hbm:plant_flower_weed")) {
                var entry = fr.atraxterra.dohy.economy.price.Prices.book().entry(Identifier.parse(item)).orElse(null);
                log("prix {} : {}", item, entry == null ? "aucun" : "vente " + entry.basePrice() + " achat "
                        + entry.buyPrice() + " (" + entry.category() + ")");
            }
            Set<String> recipes = new TreeSet<>();
            for (var holder : level.getServer().getRecipeManager().getRecipes()) {
                String key = holder.id().identifier().toString();
                if (key.contains("hemp")) {
                    recipes.add(key);
                }
            }
            log("recettes du chanvre : {}", recipes);
            ItemStack seeds = new ItemStack(fr.atraxterra.dohy.agri.DoHyAgri.HEMP_SEEDS.get());
            ItemStack fiber = new ItemStack(fr.atraxterra.dohy.agri.DoHyAgri.HEMP_FIBER.get());
            log("tags des graines : poules {} perroquets {} c:seeds {} fourrage {} cereales {} ; fibre fourrage {}",
                    tagged(seeds, "minecraft:chicken_food"), tagged(seeds, "minecraft:parrot_food"),
                    tagged(seeds, "c:seeds"), tagged(seeds, "dohy:fodder"), tagged(seeds, "dohy:cereals"),
                    tagged(fiber, "dohy:fodder"));
        }));
        ShotHarness.SHOTS.add(server("b1_champ", 80, player -> {
            ServerLevel level = (ServerLevel) player.level();
            for (int i = 0; i < 6; i++) {
                tall(level, new BlockPos(hx + i, hy, hz + 3));
            }
            Block flower = BuiltInRegistries.BLOCK.getValue(fr.atraxterra.dohy.agri.Hemp.FLOWER);
            for (int i = 0; i < 3; i++) {
                level.setBlock(new BlockPos(hx + i, hy - 1, hz + 5), Blocks.FARMLAND.defaultBlockState(), 3);
                level.setBlock(new BlockPos(hx + i, hy, hz + 5), flower.defaultBlockState(), 3);
            }
            log("champ : pied {} couronne {} fleur {}", id(level.getBlockState(new BlockPos(hx, hy, hz + 3))),
                    id(level.getBlockState(new BlockPos(hx, hy + 1, hz + 3))),
                    id(level.getBlockState(new BlockPos(hx, hy, hz + 5))));
            player.teleport(new TeleportTransition(level, new Vec3(hx + 2.5d, hy, hz - 1.5d), Vec3.ZERO, -10.0f, 18.0f,
                    TeleportTransition.DO_NOTHING));
        }));
        ShotHarness.SHOTS.add(server("b2_recolte_couronne", 20, player -> {
            ServerLevel level = (ServerLevel) player.level();
            drops(level, new BlockPos(hx, hy, hz + 3), 6.0d);
            for (int plant = 0; plant < 4; plant++) {
                BlockPos foot = new BlockPos(hx + plant, hy, hz + 3);
                long before = farmerXp(player);
                boolean broken = player.gameMode.destroyBlock(foot.above());
                log("couronne {} cassee {} : recolte {} ; reste {} ; XP Fermier +{}", plant, broken,
                        drops(level, foot, 1.5d), id(level.getBlockState(foot)), farmerXp(player) - before);
            }
        }));
        ShotHarness.SHOTS.add(server("b3_recolte_pied", 20, player -> {
            ServerLevel level = (ServerLevel) player.level();
            for (int plant = 4; plant < 6; plant++) {
                BlockPos foot = new BlockPos(hx + plant, hy, hz + 3);
                long before = farmerXp(player);
                boolean broken = player.gameMode.destroyBlock(foot);
                log("pied {} casse {} : recolte {} ; reste {} / {} ; XP Fermier +{}", plant, broken,
                        drops(level, foot, 1.5d), id(level.getBlockState(foot)), id(level.getBlockState(foot.above())),
                        farmerXp(player) - before);
            }
        }));
        ShotHarness.SHOTS.add(server("b4_herbe", 20, player -> {
            ServerLevel level = (ServerLevel) player.level();
            Map<String, Integer> found = new TreeMap<>();
            int broken = 0;
            for (int x = 0; x < 30; x++) {
                for (int z = 0; z < 30; z++) {
                    BlockPos pos = new BlockPos(hx + 12 + x, hy, hz + z);
                    level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), 3);
                    if (player.gameMode.destroyBlock(pos)) {
                        broken++;
                    }
                    drops(level, pos, 0.9d).forEach((key, count) -> found.merge(key, count, Integer::sum));
                }
            }
            log("herbe : {} touffes cassees (climat {}) : {}", broken,
                    fr.atraxterra.dohy.agri.Climate.at(level, new BlockPos(hx + 12, hy, hz)), found);
        }));
        ShotHarness.SHOTS.add(server("b5_fourrage", 20, player -> {
            ServerLevel level = (ServerLevel) player.level();
            BlockPos biogen = new BlockPos(hx - 3, hy, hz);
            BlockPos trough = new BlockPos(hx - 3, hy, hz + 2);
            BlockPos silo = new BlockPos(hx - 6, hy, hz + 2);
            level.setBlock(biogen, fr.atraxterra.dohy.agri.DoHyAgri.BIOGENERATOR.get().defaultBlockState(), 3);
            level.setBlock(trough, fr.atraxterra.dohy.agri.DoHyAgri.TROUGH.get().defaultBlockState(), 3);
            level.setBlock(silo, fr.atraxterra.dohy.agri.DoHyAgri.SILO.get().defaultBlockState(), 3);
            for (BlockPos pos : List.of(biogen, trough, silo)) {
                ItemStack stack = new ItemStack(fr.atraxterra.dohy.agri.DoHyAgri.HEMP_SEEDS.get(), 16);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                InteractionResult result = level.getBlockState(pos).useItemOn(stack, level, player,
                        InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                log("graines sur {} : {} ; reste en main {}", id(level.getBlockState(pos)), result,
                        player.getMainHandItem().getCount());
            }
            if (level.getBlockEntity(biogen) instanceof fr.atraxterra.dohy.agri.AgriMachineBlockEntity machine) {
                log("biogenerateur : accepte les graines {} ; {}", machine.accepts(
                        new ItemStack(fr.atraxterra.dohy.agri.DoHyAgri.HEMP_SEEDS.get())), machine.fuelReport());
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var chicken = net.minecraft.world.entity.EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
            if (chicken != null) {
                log("poule : les graines de chanvre sont sa nourriture {}",
                        chicken.isFood(new ItemStack(fr.atraxterra.dohy.agri.DoHyAgri.HEMP_SEEDS.get())));
            }
        }));
        ShotHarness.SHOTS.add(server("c1_sac", 40, player -> {
            player.getInventory().clearContent();
            String[] items = {"hbm:plant_flower_weed", "dohy:hemp_fiber", "dohy:hemp_seeds", "dohy:hemp_canvas",
                    "minecraft:string", "hbm:plant_item_rope", "minecraft:paper", "minecraft:painting"};
            for (int slot = 0; slot < items.length; slot++) {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(items[slot]));
                player.getInventory().setItem(slot, new ItemStack(item, slot < 4 ? 32 : 4));
            }
            ServerLevel level = (ServerLevel) player.level();
            player.teleport(new TeleportTransition(level, new Vec3(hx + 2.5d, hy, hz + 0.5d), Vec3.ZERO, 0.0f, 35.0f,
                    TeleportTransition.DO_NOTHING));
        }));
        ShotHarness.SHOTS.add(client("c2_noms", 10, () -> {
            for (String key : List.of("block.hbm.plant_flower_weed", "block.hbm.plant_tall_weed", "item.hbm.plant_item_rope",
                    "item.dohy.hemp_fiber", "item.dohy.hemp_seeds", "item.dohy.hemp_canvas")) {
                log("nom {} : {}", key, net.minecraft.client.resources.language.I18n.get(key));
            }
        }));
        ShotHarness.SHOTS.add(server("c3_fiche", 40, player -> fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player,
                "chanvre")));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> {
            UiNet.close(player);
            views(player, "chanvre");
        }));
    }
}
