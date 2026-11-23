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


    // -- Chimie et Agriculture (lot 23f) ----------------------------------------------------------------

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

    private static final BlockPos MILL = new BlockPos(2998, -60, 2996);
    private static final BlockPos OVEN = new BlockPos(3000, -60, 2996);
    private static final BlockPos TRADING = new BlockPos(3002, -60, 2996);

    private static String at(ServerPlayer player, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).toString();
    }

    private static void useOn(ServerPlayer player, BlockPos ground, ItemStack stack, String label) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0.0d, 0.5d, 0.0d), Direction.UP, ground, false));
        log("{} : {} sur {} -> {} ; au-dessus {}", label, BuiltInRegistries.ITEM.getKey(stack.getItem()),
                ground.toShortString(), result, at(player, ground.above()));
    }

    private static void open(ServerPlayer player, BlockPos pos, String label) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult result = player.gameMode.useItemOn(player, player.level(), ItemStack.EMPTY,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
        log("ouvrir {} : {}", label, result);
    }

    private static void lock(ServerPlayer player, String id) {
        fr.atraxterra.dohy.faction.tech.TechGate.Lock found = fr.atraxterra.dohy.faction.tech.TechGate.of(id);
        log("verrou {} : {} ; permis : {}", id, found == null ? "aucun" : found.label(),
                fr.atraxterra.dohy.faction.tech.TechGate.allows(player, found));
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


    private static Entity find(ServerPlayer player, String type) {
        for (Entity entity : ((ServerLevel) player.level()).getAllEntities()) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(type)) {
                return entity;
            }
        }
        return null;
    }

    private static double speedOf(Entity entity) {
        if (entity instanceof LivingEntity living) {
            var speed = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            return speed == null ? -1.0d : speed.getBaseValue();
        }
        return -1.0d;
    }

    /** Ce que disent les bonus de l'Agriculture, et ce qu'en font le Fermier, les automates, le tracteur et le bot. */
    private static void bonuses(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.tech.TechPerks perks = fr.atraxterra.dohy.faction.tech.TechPerks.of(player);
        log("{} : farmer_xp {} %, agri_speed {} %, farmer_money {} %, oil_fields {} %", label, perks.farmerXp(),
                perks.agriSpeed(), perks.farmerMoney(), perks.oilFields());
        for (String type : List.of("farmer_xp", "agri_speed", "farmer_money", "nuclear_loot")) {
            log("  en attente {} : '{}'", type, fr.atraxterra.dohy.faction.tech.TechPerks.pending(type));
        }
        long before = fr.atraxterra.dohy.job.JobApi.xp(player, fr.atraxterra.dohy.job.Job.FARMER);
        fr.atraxterra.dohy.job.JobApi.grant(player, fr.atraxterra.dohy.job.Job.FARMER, 1000L, false);
        long gained = fr.atraxterra.dohy.job.JobApi.xp(player, fr.atraxterra.dohy.job.Job.FARMER) - before;
        long minerBefore = fr.atraxterra.dohy.job.JobApi.xp(player, fr.atraxterra.dohy.job.Job.MINER);
        fr.atraxterra.dohy.job.JobApi.grant(player, fr.atraxterra.dohy.job.Job.MINER, 1000L, false);
        long minerGained = fr.atraxterra.dohy.job.JobApi.xp(player, fr.atraxterra.dohy.job.Job.MINER) - minerBefore;
        log("  1000 XP de Fermier donnent {} ; 1000 XP de Mineur donnent {}", gained, minerGained);
        log("  Fermier niveau 10 : {} $, prime {} $ ; Mineur niveau 10 : {} $",
                fr.atraxterra.dohy.job.JobApi.levelMoney(player, fr.atraxterra.dohy.job.Job.FARMER, 10),
                fr.atraxterra.dohy.job.JobPrimes.amount(player, fr.atraxterra.dohy.job.Job.FARMER, 10),
                fr.atraxterra.dohy.job.JobApi.levelMoney(player, fr.atraxterra.dohy.job.Job.MINER, 10));
        if (player.level().getBlockEntity(MILL) instanceof fr.atraxterra.dohy.agri.AgriMachineBlockEntity mill) {
            log("  moulin : vitesse +{} %, une farine en {} ticks (10 de base), {} FE", mill.speedBonus(),
                    mill.duration(10), mill.cost(120));
        }
        if (player.level().getBlockEntity(OVEN) instanceof fr.atraxterra.dohy.agri.AgriMachineBlockEntity oven) {
            log("  machine a pain : vitesse +{} %, une fournee en {} ticks (40 de base)", oven.speedBonus(),
                    oven.duration(40));
        }
        Entity tractor = find(player, "dohy:tractor");
        log("  tracteur : vitesse {} (0.2 de base), conducteur {}", tractor == null ? -1.0d : speedOf(tractor),
                tractor == null ? "aucun" : tractor.getFirstPassenger());
        Entity bot = find(player, "dohy:farm_bot");
        log("  bot de farming : vitesse {} (0.27 de base)", bot == null ? -1.0d : speedOf(bot));
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(8)));
        ShotHarness.SHOTS.add(server("a1_verrous", 60, player -> {
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
            for (String id : List.of("hbm:fluid_pipe", "hbm:fluid_duct_box_copper_0", "hbm:fluid_duct_gauge",
                    "hbm:fluid_counter_valve", "hbm:machine_fluidtank", "hbm:machine_drain", "hbm:pump_electric",
                    "hbm:machine_well", "hbm:machine_pumpjack", "hbm:machine_electrolyser", "hbm:machine_refinery",
                    "hbm:machine_pyrooven", "hbmspace:hydrobay", "hbmspace:algae_film", "dohy:flour_mill")) {
                lock(player, id);
            }
            for (String domain : List.of("chimie", "agriculture")) {
                fr.atraxterra.dohy.faction.tech.TechConfig.Domain d = fr.atraxterra.dohy.faction.tech.TechConfig.domain(domain);
                for (fr.atraxterra.dohy.faction.tech.TechConfig.Tier tier : d.tiers()) {
                    log("palier {} : requiert {} ; {} machines ; cout {}", tier.title(d), tier.requires(),
                            tier.machines().size(), tier.cost().map(c -> c.items().toString()).orElse("d'office"));
                }
            }
            bonuses(player, "sans nation");
        }));
        ShotHarness.SHOTS.add(server("a2_machines", 60, player -> {
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 36));
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 23f", List.of(), 3);
            log("nation : {}", refusal == null ? "fondee" : refusal);
            log("claim : {}", FactionApi.claim(player));
            player.setGameMode(GameType.CREATIVE);
            useOn(player, MILL.below(), new ItemStack(item("dohy:flour_mill")), "moulin");
            useOn(player, OVEN.below(), new ItemStack(item("dohy:bread_machine")), "machine a pain");
            useOn(player, new BlockPos(3000, -61, 2992), new ItemStack(item("dohy:tractor")), "tracteur");
            useOn(player, new BlockPos(2996, -61, 2992), new ItemStack(item("dohy:farm_bot")), "bot de farming");
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            feedEnergy(player, MILL, 20000);
            feedEnergy(player, OVEN, 20000);
            Entity tractor = find(player, "dohy:tractor");
            log("monter dans le tracteur : {}", tractor != null && player.startRiding(tractor, true, true));
        }));
        ShotHarness.SHOTS.add(server("a3_agriculture_1", 40, player -> bonuses(player, "Agriculture I")));
        ShotHarness.SHOTS.add(server("a4_agriculture_2", 40, player -> {
            op(player, "f techno palier agriculture 2");
            fr.atraxterra.dohy.faction.tech.Tech.complete(player, false);
        }));
        ShotHarness.SHOTS.add(server("a5_agriculture_3", 40, player -> {
            bonuses(player, "Agriculture II");
            op(player, "f techno palier agriculture 3");
            fr.atraxterra.dohy.faction.tech.Tech.complete(player, false);
        }));
        ShotHarness.SHOTS.add(server("a6_agriculture_4", 40, player -> {
            bonuses(player, "Agriculture III");
            op(player, "f techno palier agriculture 4");
            fr.atraxterra.dohy.faction.tech.Tech.complete(player, false);
        }));
        ShotHarness.SHOTS.add(server("a7_agriculture_5", 40, player -> {
            bonuses(player, "Agriculture IV");
            op(player, "f techno palier agriculture 5");
            op(player, "f techno palier chimie 4");
            fr.atraxterra.dohy.faction.tech.Tech.complete(player, false);
        }));
        ShotHarness.SHOTS.add(server("a8_bonus_hauts", 40, player -> {
            bonuses(player, "Agriculture V, Chimie IV");
            player.stopRiding();
            fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE.show(player,
                    fr.atraxterra.dohy.faction.tech.TechConfig.domain("chimie"));
        }));
        ShotHarness.SHOTS.add(server("a9_agriculture", 40, player ->
                fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE.show(player,
                        fr.atraxterra.dohy.faction.tech.TechConfig.domain("agriculture"))));
        ShotHarness.SHOTS.add(server("b0_espace", 40, player ->
                fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE.show(player,
                        fr.atraxterra.dohy.faction.tech.TechConfig.domain("espace"))));
        ShotHarness.SHOTS.add(server("b1_technologies", 40, player ->
                fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE.open(player)));
        ShotHarness.SHOTS.add(server("b2_moulin", 40, player -> {
            UiNet.close(player);
            teleport(player, 3000.5d, -60.0d, 3000.5d, 180.0f, 25.0f);
            open(player, MILL, "moulin");
        }));
        ShotHarness.SHOTS.add(server("b3_rechargement", 40, player -> {
            UiNet.close(player);
            op(player, "dohy reload");
            bonuses(player, "apres /dohy reload");
        }));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "lot 23f")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            clicks(player);
        }));
    }
}
