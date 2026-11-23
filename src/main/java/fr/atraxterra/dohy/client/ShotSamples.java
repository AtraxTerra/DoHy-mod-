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

    // -- Lot 23i : les pieces livrees aux laboratoires, puis le tour du chapitre ---------------------

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

    /** Le regard d'une camera posee en (x, y des yeux, z) vers un point. */
    private static void look(ServerPlayer player, double x, double eyeY, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - eyeY;
        double dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        teleport(player, x, eyeY - 1.62d, z, yaw, pitch);
    }

    private static fr.atraxterra.dohy.faction.tech.TechConfig.Domain domain(String id) {
        return fr.atraxterra.dohy.faction.tech.TechConfig.domain(id);
    }

    private static final BlockPos LAB = new BlockPos(3000, -60, 2996);
    private static final BlockPos HOPPER = LAB.above();

    private static fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity lab(ServerPlayer player) {
        return player.level().getBlockEntity(LAB) instanceof fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity found
                ? found : null;
    }

    private static int carried(ServerPlayer player, Item item) {
        return fr.atraxterra.dohy.faction.tech.Tech.count(player.getInventory(), item);
    }

    /** Ce que gardent le laboratoire et le joueur, objet par objet. */
    private static void stock(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity found = lab(player);
        if (found == null) {
            log("stock [{}] : pas de laboratoire en {} ({})", label, LAB.toShortString(),
                    BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(LAB).getBlock()));
            return;
        }
        StringBuilder held = new StringBuilder();
        for (Map.Entry<Item, Integer> entry : found.partsByItem().entrySet()) {
            held.append(BuiltInRegistries.ITEM.getKey(entry.getKey()).getPath()).append('=').append(entry.getValue())
                    .append(' ');
        }
        log("stock [{}] : labo {}({} cases libres) ; joueur fer {} cuivre {} or {} terre {} ; etat {} ; energie {} FE",
                label, held, found.freePartSlots(), carried(player, Items.IRON_INGOT), carried(player, Items.COPPER_INGOT),
                carried(player, Items.GOLD_INGOT), carried(player, Items.DIRT), found.status(), found.energyStored());
    }

    private static void state(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.FactionData data = FactionApi.data(player);
        StringBuilder levels = new StringBuilder();
        for (fr.atraxterra.dohy.faction.tech.TechConfig.Domain d : fr.atraxterra.dohy.faction.tech.TechConfig.domains()) {
            levels.append(d.id()).append('=').append(fr.atraxterra.dohy.faction.tech.Tech.level(data, d));
            if (data.techRunning(d.id())) {
                levels.append("(").append(data.techPoints(d.id())).append(" pts)");
            }
            levels.append(' ');
        }
        log("etat [{}] : {}; labos {} ; rythme {} pts/h ; banque {}", label, levels, data.labs(),
                fr.atraxterra.dohy.faction.tech.Tech.rate(player), data.bank());
    }

    private static void useOn(ServerPlayer player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0.0d, 0.5d, 0.0d), Direction.UP, pos, false));
        log("clic droit sur {} avec {} : {} ; en main ensuite {}", pos.toShortString(), stack, result,
                player.getMainHandItem());
    }

    private static void feedEnergy(ServerPlayer player, BlockPos pos, int amount) {
        var handler = net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK.getCapability(
                (ServerLevel) player.level(), pos, null, null, Direction.NORTH);
        if (handler == null) {
            log("energie : pas de capacite sur {}", pos.toShortString());
            return;
        }
        int total = 0;
        for (int i = 0; i < amount / 1000; i++) {
            try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                         net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                total += handler.insert(1000, transaction);
                transaction.commit();
            }
        }
        log("energie versee sur {} : {} FE", pos.toShortString(), total);
    }

    private static void act(ServerPlayer player, MenuController menu, String key, int button) {
        menu.handle(player, new UiNet.Action(menu.id(), "card", key, button));
    }

    /** Les cartes d'une vue dont un texte contient ce mot : titre, lignes et bulle. */
    private static void cards(ServerPlayer player, MenuController menu, String label, String word) {
        MenuView view = menu.view(player);
        for (MenuView.Section section : view.sections()) {
            for (MenuView.Card card : section.cards()) {
                String all = card.title() + " | " + String.join(" / ", card.lines()) + " | "
                        + String.join(" / ", card.tooltip());
                if (word.isEmpty() || all.contains(word)) {
                    log("vue [{}] {} > {} : {} ; action « {} » / « {} »", label, section.title(), card.title(),
                            String.join(" / ", card.lines()), card.action(), card.rightAction());
                    for (String line : card.tooltip()) {
                        if (word.isEmpty() || line.contains(word)) {
                            log("vue [{}]   bulle : {}", label, line);
                        }
                    }
                }
            }
        }
    }

    private static List<fr.atraxterra.dohy.faction.tech.TechConfig.Domain> original;

    /**
     * NTM n'est pas la : ses pieces n'existent pas et le laboratoire ne prendrait rien. Pour l'essai,
     * l'Energie II demande 32 lingots de fer et 16 de cuivre, l'Agriculture II 8 lingots d'or.
     */
    @SuppressWarnings("unchecked")
    private static void testCosts(boolean on) {
        try {
            java.lang.reflect.Field field = fr.atraxterra.dohy.faction.tech.TechConfig.class.getDeclaredField("domains");
            field.setAccessible(true);
            java.lang.reflect.Field parts = fr.atraxterra.dohy.faction.tech.TechConfig.class.getDeclaredField("parts");
            parts.setAccessible(true);
            if (on) {
                original = (List<fr.atraxterra.dohy.faction.tech.TechConfig.Domain>) field.get(null);
                List<fr.atraxterra.dohy.faction.tech.TechConfig.Domain> changed = new ArrayList<>();
                for (fr.atraxterra.dohy.faction.tech.TechConfig.Domain d : original) {
                    if (!d.id().equals("energie") && !d.id().equals("agriculture")) {
                        changed.add(d);
                        continue;
                    }
                    List<fr.atraxterra.dohy.faction.tech.TechConfig.Tier> tiers = new ArrayList<>();
                    for (fr.atraxterra.dohy.faction.tech.TechConfig.Tier t : d.tiers()) {
                        if (t.tier() == 2 && t.cost().isPresent()) {
                            fr.atraxterra.dohy.faction.tech.TechConfig.Cost c = t.cost().get();
                            List<fr.atraxterra.dohy.faction.tech.TechConfig.ItemCost> items = d.id().equals("energie")
                                    ? List.of(new fr.atraxterra.dohy.faction.tech.TechConfig.ItemCost(
                                                    "minecraft:iron_ingot", 32, "lingots de fer"),
                                            new fr.atraxterra.dohy.faction.tech.TechConfig.ItemCost(
                                                    "minecraft:copper_ingot", 16, "lingots de cuivre"))
                                    : List.of(new fr.atraxterra.dohy.faction.tech.TechConfig.ItemCost(
                                            "minecraft:gold_ingot", 8, "lingots d'or"));
                            t = new fr.atraxterra.dohy.faction.tech.TechConfig.Tier(t.tier(), t.name(), t.start(),
                                    t.requires(), t.unlocks(), t.machines(), t.bonuses(),
                                    java.util.Optional.of(new fr.atraxterra.dohy.faction.tech.TechConfig.Cost(c.money(),
                                            c.points(), items)), t.legacy());
                        }
                        tiers.add(t);
                    }
                    changed.add(new fr.atraxterra.dohy.faction.tech.TechConfig.Domain(d.id(), d.name(), d.summary(),
                            d.icons(), d.color(), d.order(), List.copyOf(tiers)));
                }
                field.set(null, List.copyOf(changed));
            } else if (original != null) {
                field.set(null, original);
            }
            parts.set(null, null);
            log("couts d'essai {} : fer piece {} ; or piece {} ; terre piece {} ; plaque d'acier piece {}",
                    on ? "poses" : "retires",
                    fr.atraxterra.dohy.faction.tech.TechConfig.isPart(new ItemStack(Items.IRON_INGOT)),
                    fr.atraxterra.dohy.faction.tech.TechConfig.isPart(new ItemStack(Items.GOLD_INGOT)),
                    fr.atraxterra.dohy.faction.tech.TechConfig.isPart(new ItemStack(Items.DIRT)),
                    fr.atraxterra.dohy.faction.tech.TechConfig.isPart(new ItemStack(Items.IRON_BLOCK)));
        } catch (ReflectiveOperationException error) {
            log("couts d'essai : {}", error.toString());
        }
    }

    /** Par la capacite d'objets : la terre refusee, le fer pris, rien ne sort par le dessous. */
    private static void capability(ServerPlayer player) {
        var handler = net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK.getCapability(
                (ServerLevel) player.level(), LAB, null, null, Direction.DOWN);
        var free = net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK.getCapability(
                (ServerLevel) player.level(), LAB, null, null, null);
        if (handler == null || free == null) {
            log("capacite d'objets : absente (dessous {}, sans face {})", handler, free);
            return;
        }
        int dirt;
        int iron;
        int out;
        int outFree;
        try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                     net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            dirt = handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.DIRT)), 8,
                    transaction);
            iron = handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.IRON_INGOT)),
                    4, transaction);
            transaction.commit();
        }
        try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                     net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            out = 0;
            outFree = 0;
            for (int slot = 0; slot < handler.size(); slot++) {
                var resource = handler.getResource(slot);
                if (resource != null && !resource.isEmpty()) {
                    out += handler.extract(slot, resource, 64, transaction);
                    outFree += free.extract(slot, resource, 64, transaction);
                }
            }
            transaction.commit();
        }
        log("capacite d'objets : {} cases ; terre prise {} ; fer pris {} ; sorti par le dessous {} ; sorti sans face {}",
                handler.size(), dirt, iron, out, outFree);
    }

    /** La sauvegarde du laboratoire, relue dans un laboratoire neuf. */
    private static void roundTrip(ServerPlayer player) {
        try {
            fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity found = lab(player);
            var registries = player.level().registryAccess();
            Object tag = net.minecraft.world.level.block.entity.BlockEntity.class
                    .getMethod("saveWithoutMetadata", net.minecraft.core.HolderLookup.Provider.class)
                    .invoke(found, registries);
            fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity fresh =
                    new fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity(LAB, player.level().getBlockState(LAB));
            net.minecraft.world.level.block.entity.BlockEntity.class
                    .getMethod("loadWithComponents", net.minecraft.world.level.storage.ValueInput.class)
                    .invoke(fresh, net.minecraft.world.level.storage.TagValueInput.create(
                            net.minecraft.util.ProblemReporter.DISCARDING, registries,
                            (net.minecraft.nbt.CompoundTag) tag));
            String text = tag.toString();
            int at = text.indexOf("Items");
            log("aller-retour du laboratoire : cuivre {} -> {} ; fer {} -> {} ; extrait « {} »",
                    found.partCount(Items.COPPER_INGOT), fresh.partCount(Items.COPPER_INGOT),
                    found.partCount(Items.IRON_INGOT), fresh.partCount(Items.IRON_INGOT),
                    at < 0 ? "pas d'Items" : text.substring(at, Math.min(text.length(), at + 160)));
        } catch (ReflectiveOperationException | RuntimeException error) {
            log("aller-retour du laboratoire : {}", error.toString());
        }
    }

    private static int dropped(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemEntity entity : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(LAB).inflate(3.0d))) {
            if (entity.getItem().is(item)) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }

    private static void placeLab(ServerPlayer player) {
        ItemStack stack = new ItemStack(fr.atraxterra.dohy.faction.tech.DoHyResearch.LAB_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(LAB.below()).add(0.0d, 0.5d, 0.0d), Direction.UP, LAB.below(),
                        false));
        log("pose du laboratoire : {} -> {}", result,
                BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(LAB).getBlock()));
    }

    private static void branch(ServerPlayer player, String id) {
        fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE.show(player, domain(id));
    }

    private static void perks(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.tech.TechPerks p = fr.atraxterra.dohy.faction.tech.TechPerks.of(player);
        log("bonus [{}] : labos {} ; paralleles {} ; vitesse +{} % ; energie labo -{} % ; machines -{} % ; agri +{} % ;"
                        + " mineur +{} % ; fermier +{} % argent +{} % xp ; coffre +{} ; largage +{} ; soldats -{} % ;"
                        + " multiplicateur x{}", label, p.labs(), p.parallel(), p.research(),
                p.value("lab_energy"), p.machineEnergy(), p.agriSpeed(), p.minerMoney(), p.farmerMoney(), p.farmerXp(),
                p.vaultSlots(), p.nuclearLoot(), p.soldierHp(),
                String.format(java.util.Locale.ROOT, "%.3f", fr.atraxterra.dohy.faction.tech.Tech.multiplier(player)));
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
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 23i", List.of(), 3);
            log("nation : {} ; claim : {}", refusal == null ? "fondee" : refusal, FactionApi.claim(player));
            FactionApi.creditBank(player, 50_000_000L, "Essai");
            testCosts(true);
            placeLab(player);
            feedEnergy(player, LAB, 100_000);
            player.level().setBlock(HOPPER, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
            if (player.level().getBlockEntity(HOPPER) instanceof HopperBlockEntity hopper) {
                hopper.setItem(0, new ItemStack(Items.IRON_INGOT, 32));
                hopper.setItem(1, new ItemStack(Items.DIRT, 3));
                log("entonnoir pose sur le laboratoire, vers {} : 32 fer et 3 terre",
                        player.level().getBlockState(HOPPER).getValue(HopperBlock.FACING));
            }
            stock(player, "depart");
        }));
        ShotHarness.SHOTS.add(server("a2_entonnoir", 320, player -> {
            stock(player, "apres l'entonnoir");
            if (player.level().getBlockEntity(HOPPER) instanceof HopperBlockEntity hopper) {
                int left = 0;
                for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                    left += hopper.getItem(slot).getCount();
                }
                log("entonnoir : {} objets restent (la terre ne passe pas)", left);
            }
            look(player, 3002.3d, -58.0d, 2998.6d, 3000.5d, -59.2d, 2996.5d);
        }));
        ShotHarness.SHOTS.add(server("a3_ecran_pieces", 40, player -> {
            capability(player);
            stock(player, "apres la capacite");
            teleport(player, 3000.5d, -60.0d, 2998.5d, 180.0f, 30.0f);
            fr.atraxterra.dohy.faction.tech.ResearchLabMenu.open(player, LAB);
            cards(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "pieces", "");
        }));
        ShotHarness.SHOTS.add(server("a4_deposer", 40, player -> {
            player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 16));
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 10));
            player.getInventory().add(new ItemStack(Items.DIRT, 5));
            act(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "deposit", 0);
            stock(player, "apres le depot");
            act(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "deposit", 0);
            UiNet.close(player);
            useOn(player, LAB, new ItemStack(Items.IRON_INGOT, 6));
            useOn(player, LAB, new ItemStack(Items.DIRT, 4));
            stock(player, "apres le clic droit");
            fr.atraxterra.dohy.faction.tech.ResearchLabMenu.open(player, LAB);
            cards(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "depot", "");
        }));
        ShotHarness.SHOTS.add(server("a5_palier_pret", 40, player -> {
            UiNet.close(player);
            op(player, "f techno palier industrie 2");
            log("blocage Energie II (pieces au labo) : « {} »",
                    fr.atraxterra.dohy.faction.tech.Tech.blocker(player, domain("energie")));
            log("blocage Agriculture II (pas d'or) : « {} »",
                    fr.atraxterra.dohy.faction.tech.Tech.blocker(player, domain("agriculture")));
            branch(player, "energie");
            cards(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "energie", "labo");
        }));
        ShotHarness.SHOTS.add(server("a6_lancer", 60, player -> {
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 5));
            stock(player, "avant le lancement");
            act(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "research", 0);
            stock(player, "apres le lancement (fer : 5 sur moi d'abord, puis 27 au labo)");
            state(player, "Energie II lancee");
            branch(player, "energie");
        }));
        ShotHarness.SHOTS.add(server("a7_reprendre", 40, player -> {
            UiNet.close(player);
            fr.atraxterra.dohy.faction.tech.ResearchLabMenu.open(player, LAB);
            act(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "take:minecraft:iron_ingot", 0);
            stock(player, "une pile reprise");
            act(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "deposit", 0);
            act(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "takeall:minecraft:iron_ingot", 1);
            stock(player, "tout repris");
            act(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "take:minecraft:iron_ingot", 0);
            act(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "take:minecraft:stone", 0);
        }));
        ShotHarness.SHOTS.add(server("a8_casser", 60, player -> {
            UiNet.close(player);
            player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 16));
            act(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "deposit", 0);
            fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity found = lab(player);
            if (found != null) {
                found.storePart(new ItemStack(Items.COPPER_INGOT, 16));
            }
            stock(player, "avant de casser");
            roundTrip(player);
            player.level().removeBlock(HOPPER, false);
            log("casser le laboratoire : {} ; au sol cuivre {} ; laboratoire {}", player.gameMode.destroyBlock(LAB),
                    dropped(player, Items.COPPER_INGOT),
                    dropped(player, fr.atraxterra.dohy.faction.tech.DoHyResearch.LAB_ITEM.get()));
            state(player, "laboratoire casse");
            for (ItemEntity entity : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(LAB).inflate(4.0d))) {
                entity.discard();
            }
            placeLab(player);
            feedEnergy(player, LAB, 100_000);
            testCosts(false);
        }));
        // -- Le tour du chapitre : une nation de milieu de partie ------------------------------------------
        ShotHarness.SHOTS.add(server("b0_nation", 60, player -> {
            for (String set : List.of("gouvernement 4", "economie 3", "energie 4", "industrie 5", "chimie 4",
                    "agriculture 3", "nucleaire 3", "defense 3", "espace 2")) {
                op(player, "f techno palier " + set);
            }
            player.setGameMode(GameType.CREATIVE);
            log("lancer Chimie V : « {} » ; Nucleaire IV : « {} »",
                    fr.atraxterra.dohy.faction.tech.Tech.start(player, domain("chimie")),
                    fr.atraxterra.dohy.faction.tech.Tech.start(player, domain("nucleaire")));
            player.setGameMode(GameType.SURVIVAL);
            op(player, "f techno points 21000");
            state(player, "milieu de partie");
            perks(player, "milieu de partie");
        }));
        ShotHarness.SHOTS.add(server("b1_technologies", 40, player ->
                fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE.open(player)));
        String[] order = {"gouvernement", "economie", "energie", "industrie", "chimie", "agriculture", "nucleaire",
                "defense", "espace"};
        for (int i = 0; i < order.length; i++) {
            String id = order[i];
            ShotHarness.SHOTS.add(server("b" + (2 + i) + "_" + id, 40, player -> branch(player, id)));
        }
        ShotHarness.SHOTS.add(server("c0_labo", 60, player -> {
            UiNet.close(player);
            testCosts(true);
            fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity found = lab(player);
            if (found != null) {
                found.storePart(new ItemStack(Items.GOLD_INGOT, 5));
                found.storePart(new ItemStack(Items.IRON_INGOT, 48));
            }
            teleport(player, 3000.5d, -60.0d, 2998.5d, 180.0f, 30.0f);
            fr.atraxterra.dohy.faction.tech.ResearchLabMenu.open(player, LAB);
            cards(player, fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE, "labo plein", "");
        }));
        ShotHarness.SHOTS.add(server("c1_fiche_labo", 40, player -> {
            UiNet.close(player);
            testCosts(false);
            fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player, "laboratoire");
        }));
        ShotHarness.SHOTS.add(server("c2_fiche_techno", 40, player -> {
            UiNet.close(player);
            fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player, "arbre_techno");
        }));
        ShotHarness.SHOTS.add(server("c3_max", 40, player -> {
            UiNet.close(player);
            for (String set : List.of("gouvernement 6", "economie 6", "energie 6", "industrie 6", "chimie 5",
                    "agriculture 5", "nucleaire 6", "defense 6", "espace 6")) {
                op(player, "f techno palier " + set);
            }
            state(player, "tout l'arbre");
            perks(player, "tout l'arbre");
            fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "lot 23i")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            clicks(player);
        }));
    }
}
