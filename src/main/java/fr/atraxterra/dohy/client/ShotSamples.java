package fr.atraxterra.dohy.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : chasse aux bugs (menus, objets, blocs, entites, commandes). */
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

    private static void items() {
        Minecraft minecraft = Minecraft.getInstance();
        Item.TooltipContext context = Item.TooltipContext.of(minecraft.level);
        int total = 0;
        int broken = 0;
        Map<String, Integer> exoticCount = new TreeMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!OWN.contains(id.getNamespace())) {
                continue;
            }
            total++;
            try {
                ItemStack stack = new ItemStack(item);
                Set<String> keys = new TreeSet<>();
                missing(stack.getHoverName(), keys);
                List<String> flags = new ArrayList<>();
                Set<Character> exotic = new TreeSet<>();
                inspect(stack.getHoverName().getString(), flags, exotic);
                for (TooltipFlag flag : new TooltipFlag[] {TooltipFlag.NORMAL, TooltipFlag.ADVANCED}) {
                    for (Component line : stack.getTooltipLines(context, minecraft.player, flag)) {
                        missing(line, keys);
                        inspect(line.getString(), flags, exotic);
                    }
                }
                if (!keys.isEmpty() || !flags.isEmpty()) {
                    broken++;
                    log("objet {} « {} » : cles {} ; signes {}", id, stack.getHoverName().getString(), keys, flags);
                }
                for (char c : exotic) {
                    exoticCount.merge(c + " (U+" + Integer.toHexString(c) + ")", 1, Integer::sum);
                }
            } catch (Throwable t) {
                broken++;
                LOG.error("dohy-bug: ECHEC infobulle " + id, t);
            }
        }
        log("objets : {} verifies, {} a revoir ; caracteres hors ASCII dans les infobulles : {}", total, broken,
                exoticCount);
    }

    // -- Les menus -------------------------------------------------------------------------------

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

    private static void placeAll(ServerPlayer player) {
        UiNet.close(player);
        player.setGameMode(GameType.CREATIVE);
        ServerLevel level = player.level().getServer().overworld();
        List<Block> blocks = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!OWN.contains(id.getNamespace())) {
                continue;
            }
            if (block instanceof LiquidBlock || RISKY_BLOCK.matcher(id.getPath()).find()) {
                skipped.add(id.toString());
                continue;
            }
            blocks.add(block);
        }
        blocks.sort((a, b) -> BuiltInRegistries.BLOCK.getKey(a).toString()
                .compareTo(BuiltInRegistries.BLOCK.getKey(b).toString()));
        side = (int) Math.ceil(Math.sqrt(blocks.size()));
        teleport(player, BX + side * 1.5d, Y + 30.0d, BZ + side * 1.5d, 180.0f, 90.0f);
        int index = 0;
        for (Block block : blocks) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            BlockPos pos = new BlockPos(BX + (index % side) * 3, Y, BZ + (index / side) * 3);
            index++;
            try {
                level.setBlock(pos, block.defaultBlockState(), 3);
                PLACED.put(pos, id);
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC pose " + id, t);
            }
        }
        StringBuilder layout = new StringBuilder();
        int row = -1;
        for (Map.Entry<BlockPos, String> entry : PLACED.entrySet()) {
            int r = (entry.getKey().getZ() - BZ) / 3;
            if (r != row) {
                if (layout.length() > 0) {
                    log("rangee {} : {}", row, layout);
                }
                layout.setLength(0);
                row = r;
            }
            layout.append(entry.getValue()).append(' ');
        }
        log("rangee {} : {}", row, layout);
        log("blocs poses : {} (cote {}), ecartes : {}", PLACED.size(), side, skipped);
    }

    private static void viewBlocks(ServerPlayer player, int quadrant) {
        player.setGameMode(GameType.SPECTATOR);
        double width = side * 3.0d;
        double cx = BX + width * (quadrant % 2 == 0 ? 0.25d : 0.75d);
        double cz = BZ + width * (quadrant < 2 ? 0.25d : 0.75d);
        // Vue plongeante a 60 degres, de face (vers le nord) : le dessus et la face sud de chaque bloc.
        teleport(player, cx, Y + 22.0d, cz + 13.0d, 180.0f, 58.0f);
    }

    private static void breakAll(ServerPlayer player) {
        player.setGameMode(GameType.CREATIVE);
        ServerLevel level = player.level().getServer().overworld();
        teleport(player, BX + side * 1.5d, Y + 6.0d, BZ + side * 1.5d, 180.0f, 90.0f);
        List<String> gone = new ArrayList<>();
        List<String> empty = new ArrayList<>();
        List<String> stayed = new ArrayList<>();
        Map<String, Integer> other = new TreeMap<>();
        for (Map.Entry<BlockPos, String> entry : PLACED.entrySet()) {
            BlockPos pos = entry.getKey();
            String id = entry.getValue();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                gone.add(id);
                continue;
            }
            String now = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (!now.equals(id)) {
                other.merge(id + "->" + now, 1, Integer::sum);
            }
            try {
                level.destroyBlock(pos, true);
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC casse " + id, t);
                continue;
            }
            if (!level.getBlockState(pos).isAir()) {
                stayed.add(id + " (" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()) + ")");
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.2d));
            if (drops.isEmpty()) {
                empty.add(id);
            }
            for (ItemEntity drop : drops) {
                drop.discard();
            }
        }
        log("casse : {} blocs ; disparus avant la casse : {} ; changes : {} ; restes apres la casse : {}",
                PLACED.size(), gone, other, stayed);
        log("casse sans butin ({}) : {}", empty.size(), empty);
    }

    // -- Les entites -----------------------------------------------------------------------------

    private static void spawnAll(ServerPlayer player) {
        ServerLevel level = player.level().getServer().overworld();
        player.setGameMode(GameType.SPECTATOR);
        List<EntityType<?>> types = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!OWN.contains(id.getNamespace())) {
                continue;
            }
            if (RISKY_ENTITY.matcher(id.getPath()).find()) {
                skipped.add(id.toString());
                continue;
            }
            types.add(type);
        }
        int count = (int) Math.ceil(Math.sqrt(Math.max(1, types.size())));
        teleport(player, EX + count * 4.0d, Y + 26.0d, EZ + count * 8.0d + 14.0d, 180.0f, 50.0f);
        int index = 0;
        List<String> none = new ArrayList<>();
        for (EntityType<?> type : types) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            BlockPos pos = new BlockPos(EX + (index % count) * 8, Y, EZ + (index / count) * 8);
            index++;
            try {
                Entity entity = type.spawn(level, pos, EntitySpawnReason.COMMAND);
                if (entity == null) {
                    none.add(id);
                } else {
                    SPAWNED.add(entity);
                }
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC apparition " + id, t);
            }
        }
        log("entites : {} apparues, non creees {}, ecartees {}", SPAWNED.size(), none, skipped);
    }

    private static void killAll(ServerPlayer player) {
        ServerLevel level = player.level().getServer().overworld();
        List<String> vanished = new ArrayList<>();
        List<String> undying = new ArrayList<>();
        for (Entity entity : SPAWNED) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (entity.isRemoved()) {
                vanished.add(id);
                continue;
            }
            try {
                if (entity instanceof LivingEntity living) {
                    living.hurtServer(level, player.damageSources().playerAttack(player), 1.0e6f);
                    if (living.isAlive()) {
                        undying.add(id + " (" + Math.round(living.getHealth()) + " pv)");
                        entity.discard();
                    }
                } else {
                    entity.discard();
                }
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC mort " + id, t);
            }
        }
        log("entites : disparues d'elles-memes {} ; survivent a 1e6 degats {}", vanished, undying);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class,
                new AABB(EX - 20, Y - 10, EZ - 20, EX + 200, Y + 40, EZ + 200))) {
            drop.discard();
        }
    }

    // -- Les commandes ---------------------------------------------------------------------------

    private static final Set<String> SAFE = Set.of("aide", "help", "info", "infos", "liste", "list", "etat", "stats",
            "top", "classement", "voir", "carte", "solde", "banque", "rang", "membres", "prix", "statut", "status",
            "menu", "journal", "historique", "saison", "progression", "niveau", "niveaux", "recompenses", "audit");
    private static final Set<String> NEVER = Set.of("rtp", "stop", "dohy_lucky_reponse");

    private static boolean ours(CommandNode<CommandSourceStack> node, int depth) {
        if (node.getCommand() != null && node.getCommand().getClass().getName().startsWith("fr.atraxterra")) {
            return true;
        }
        if (depth > 3) {
            return false;
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (ours(child, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static void commands(ServerPlayer player) {
        UiNet.close(player);
        player.setGameMode(GameType.SURVIVAL);
        teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
        MinecraftServer server = player.level().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        List<String> lines = new ArrayList<>();
        for (CommandNode<CommandSourceStack> root : dispatcher.getRoot().getChildren()) {
            if (!(root instanceof LiteralCommandNode) || NEVER.contains(root.getName()) || !ours(root, 0)) {
                continue;
            }
            if (root.getCommand() != null) {
                lines.add(root.getName());
            }
            for (CommandNode<CommandSourceStack> child : root.getChildren()) {
                if (child instanceof LiteralCommandNode && child.getCommand() != null && SAFE.contains(child.getName())) {
                    lines.add(root.getName() + " " + child.getName());
                }
            }
        }
        int ok = 0;
        for (String line : lines) {
            try {
                dispatcher.execute(line, player.createCommandSourceStack());
                ok++;
            } catch (CommandSyntaxException e) {
                log("commande /{} : {}", line, e.getMessage());
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC commande /" + line, t);
            }
            UiNet.close(player);
        }
        log("commandes : {} / {} sans exception : {}", ok, lines.size(), lines);
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
            op(player, "difficulty easy");
            player.setGameMode(GameType.SURVIVAL);
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            Economy.set(player, 1_000_000L);
            log("depart : {} menus", Menus.all().size());
        }));
        ShotHarness.SHOTS.add(client("a1_objets", 10, ShotSamples::items));
        ShotHarness.SHOTS.add(server("a2_vues", 10, player -> {
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            views(player, "seul");
        }));
        int index = 0;
        for (MenuController menu : Menus.all()) {
            String name = String.format("m%02d_%s", index++, menu.id().getPath().replace('/', '_'));
            ShotHarness.SHOTS.add(server(name, 0, player -> openMenu(player, menu)));
        }
        ShotHarness.SHOTS.add(server("b0_faction", 60, player -> {
            UiNet.close(player);
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            String refusal = FactionApi.create(player, "Testeurs", "Chasse aux bugs", List.of(), 3);
            log("faction : {}", refusal == null ? "fondee" : refusal);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    teleport(player, 3000.5d + dx * 16, Y, 3000.5d + dz * 16, 180.0f, 20.0f);
                    String claim = FactionApi.claim(player);
                    if (claim != null) {
                        log("claim {} {} : {}", dx, dz, claim);
                    }
                }
            }
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            FactionApi.creditBank(player, 400_000L, "Essai");
            as(player, "f pays capitale");
            as(player, "guerre coffre");
        }));
        ShotHarness.SHOTS.add(server("b1_vues", 10, player -> views(player, "faction")));
        index = 0;
        for (MenuController menu : Menus.all()) {
            int number = index++;
            if (factionMenu(menu)) {
                String name = String.format("n%02d_%s", number, menu.id().getPath().replace('/', '_'));
                ShotHarness.SHOTS.add(server(name, 0, player -> openMenu(player, menu)));
            }
        }
        ShotHarness.SHOTS.add(server("c0_blocs", 200, ShotSamples::placeAll));
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int q = quadrant;
            ShotHarness.SHOTS.add(server("c" + (q + 1) + "_blocs", 60, player -> viewBlocks(player, q)));
        }
        ShotHarness.SHOTS.add(server("c5_casse", 40, ShotSamples::breakAll));
        ShotHarness.SHOTS.add(server("d0_entites", 200, ShotSamples::spawnAll));
        ShotHarness.SHOTS.add(server("d1_entites", 40, player -> log("entites apres 10 s : {} vivantes",
                SPAWNED.stream().filter(e -> !e.isRemoved()).count())));
        ShotHarness.SHOTS.add(server("d2_mort", 40, ShotSamples::killAll));
        ShotHarness.SHOTS.add(server("e0_commandes", 60, ShotSamples::commands));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> {
            UiNet.close(player);
            log("fin de la chasse ; joueur {}", me);
        }));
    }
}
