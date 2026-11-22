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

/** Temporaire : le lot 23a de la nation (huit onglets, pages de detail, rang, HUD). */
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


    // -- La nation -------------------------------------------------------------------------------

    private static final String[] TABS = {"faction", "faction_claims", "faction_tree", "faction_bank", "guerre",
            "faction_events", "faction_ranking", "faction_settings"};
    private static final String[] DETAILS = {"faction_nations", "faction_mines", "faction_branch", "faction_reserve",
            "faction_world_market", "faction_enemy_bases", "faction_notation"};

    private static MenuController menu(String path) {
        Identifier id = UiNet.id(path);
        for (MenuController menu : Menus.all()) {
            if (menu.id().equals(id)) {
                return menu;
            }
        }
        throw new IllegalStateException("menu inconnu " + path);
    }

    /** L'onglet allume d'une page, puis chaque onglet clique depuis elle, puis l'hexagone. */
    private static void navigation(ServerPlayer player) {
        List<String> pages = new ArrayList<>(List.of(TABS));
        pages.addAll(List.of(DETAILS));
        int fails = 0;
        for (String path : pages) {
            MenuController page = menu(path);
            MenuView view = page.view(player);
            String active = view.tabs().stream().filter(MenuView.Tab::active).map(MenuView.Tab::label).findFirst()
                    .orElse("AUCUN");
            log("page {} : « {} », {} onglets, allume {}", path, view.title(), view.tabs().size(), active);
            for (MenuView.Tab tab : view.tabs()) {
                page.open(player);
                page.handle(player, new UiNet.Action(page.id(), "card", tab.action(), 0));
                Identifier target = UiNet.id(tab.action().substring(4));
                if (!UiNet.isOpen(player, target)) {
                    fails++;
                    log("ECHEC onglet {} depuis {}", tab.label(), path);
                }
            }
            page.open(player);
            page.handle(player, new UiNet.Action(page.id(), "back", "", 0));
            String back = "?";
            for (MenuController other : Menus.all()) {
                if (UiNet.isOpen(player, other.id())) {
                    back = other.id().getPath();
                }
            }
            log("retour depuis {} : {}", path, back);
        }
        log("navigation : {} echec(s)", fails);
        UiNet.close(player);
    }

    private static void rank(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.Nation nation = fr.atraxterra.dohy.faction.Nation.of(player);
        log("rang [{}] : {} ; titre « {} » ; {} pays ; capitale {} ; suivant « {} » ; taxe {}", label, nation.rank(),
                nation.title(), nation.countries().size(), nation.capital(), nation.nextRank(),
                fr.atraxterra.dohy.faction.NationRank.fee(player));
        for (int tier = 0; tier <= 6; tier++) {
            log("sans carte, Gouvernement {} : {}", tier, fr.atraxterra.dohy.faction.NationRank.byGovernment(tier));
        }
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
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
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 36));
        }));
        ShotHarness.SHOTS.add(server("a1_hub_sans_nation", 20, player -> openMenu(player,
                fr.atraxterra.dohy.ui.menu.HubController.INSTANCE)));
        ShotHarness.SHOTS.add(server("a2_fonder", 20, player -> openMenu(player, menu("faction"))));
        ShotHarness.SHOTS.add(server("b0_fondation", 60, player -> {
            UiNet.close(player);
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 23a", List.of(), 3);
            log("nation : {}", refusal == null ? "fondee" : refusal);
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
            rank(player, "fondee");
            as(player, "f pays capitale");
            rank(player, "capitale");
        }));
        for (int i = 0; i < TABS.length; i++) {
            String path = TABS[i];
            ShotHarness.SHOTS.add(server("c" + i + "_" + path, 0, player -> openMenu(player, menu(path))));
        }
        for (int i = 0; i < DETAILS.length; i++) {
            String path = DETAILS[i];
            ShotHarness.SHOTS.add(server("d" + i + "_" + path, 0, player -> openMenu(player, menu(path))));
        }
        ShotHarness.SHOTS.add(server("e0_navigation", 10, ShotSamples::navigation));
        ShotHarness.SHOTS.add(server("f0_etat", 30, player -> {
            op(player, "f pays donner ZMB");
            rank(player, "un pays");
            openMenu(player, menu("faction"));
        }));
        ShotHarness.SHOTS.add(server("f1_royaume", 30, player -> {
            for (String code : List.of("ZWE", "BWA", "MOZ", "AGO", "NAM")) {
                op(player, "f pays donner " + code);
            }
            rank(player, "six pays");
            openMenu(player, menu("faction"));
        }));
        ShotHarness.SHOTS.add(server("f2_territoire", 0, player -> openMenu(player, menu("faction_claims"))));
        ShotHarness.SHOTS.add(server("f3_hub", 0, player -> openMenu(player,
                fr.atraxterra.dohy.ui.menu.HubController.INSTANCE)));
        ShotHarness.SHOTS.add(server("g0_hud_info", 60, player -> {
            UiNet.close(player);
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            as(player, "nation info");
        }));
        ShotHarness.SHOTS.add(client("g1_liste", 30, () -> Minecraft.getInstance().options.keyPlayerList.setDown(true)));
        ShotHarness.SHOTS.add(client("g2_fin_liste", 5, () -> Minecraft.getInstance().options.keyPlayerList
                .setDown(false)));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "nation")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            clicks(player);
        }));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> {
            UiNet.close(player);
            log("fin ; joueur {}", me);
        }));
    }
}
