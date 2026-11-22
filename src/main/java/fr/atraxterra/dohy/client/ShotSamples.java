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

/** Temporaire : le lot 24b (entreprises, argent sale, organisations criminelles). */
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


    // -- Les entreprises -------------------------------------------------------------------------

    private static final String[] PAGES = {"faction", "faction_enterprises", "faction_enterprise_found",
            "faction_enterprise", "faction_enterprise_finance", "faction_enterprise_territory", "faction_syndicate",
            "faction_organisation", "faction_cadre", "faction_diplomacy", "faction_claims", "faction_bank",
            "faction_tree", "faction_events", "faction_ranking"};

    private static MenuController menu(String path) {
        Identifier id = UiNet.id(path);
        for (MenuController menu : Menus.all()) {
            if (menu.id().equals(id)) {
                return menu;
            }
        }
        throw new IllegalStateException("menu inconnu " + path);
    }

    private static void navigation(ServerPlayer player) {
        int fails = 0;
        for (String path : PAGES) {
            MenuController page = menu(path);
            MenuView view = page.view(player);
            String active = view.tabs().stream().filter(MenuView.Tab::active).map(MenuView.Tab::label).findFirst()
                    .orElse("AUCUN");
            List<String> labels = new ArrayList<>();
            for (MenuView.Tab tab : view.tabs()) {
                labels.add(tab.label() + (tab.count().isEmpty() ? "" : "[" + tab.count() + "]"));
            }
            log("page {} : « {} », allume {} ; onglets {}", path, view.title(), active, labels);
            for (MenuView.Tab tab : view.tabs()) {
                page.open(player);
                page.handle(player, new UiNet.Action(page.id(), "card", tab.action(), 0));
                if (!UiNet.isOpen(player, UiNet.id(tab.action().substring(4)))) {
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

    private static fr.atraxterra.dohy.faction.enterprise.Holdings holdings(ServerPlayer player) {
        return fr.atraxterra.dohy.faction.enterprise.Enterprises.of(player);
    }

    private static void estate(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.enterprise.Holdings holdings = holdings(player);
        long now = System.currentTimeMillis();
        List<String> rows = new ArrayList<>();
        for (fr.atraxterra.dohy.faction.enterprise.Enterprise e : holdings.enterprises()) {
            rows.add(e.id() + " " + e.sector() + (e.line().isEmpty() ? "" : "/" + e.line()) + " n" + e.level() + " e"
                    + e.staff() + "/" + e.maxStaff() + (e.closed(now) ? " FERMEE" : "") + " h="
                    + Math.round(fr.atraxterra.dohy.faction.enterprise.Enterprises.perHour(player, e)) + " frais="
                    + Math.round(fr.atraxterra.dohy.faction.enterprise.Enterprises.costPerHour(player, e))
                    + (e.legal() ? "" : " risque=" + Math.round(fr.atraxterra.dohy.faction.enterprise.Enterprises
                    .risk(player, e))) + " gagne=" + e.earned() + " up=" + e.upgrades());
        }
        List<String> stocks = new ArrayList<>();
        for (fr.atraxterra.dohy.faction.enterprise.Line line : fr.atraxterra.dohy.faction.enterprise.Line.values()) {
            long raw = holdings.stock(line.rawKey());
            long product = holdings.stock(line.productKey());
            if (raw > 0 || product > 0) {
                stocks.add(line + " " + raw + "/" + product);
            }
        }
        stocks.add("caisses " + holdings.stock(fr.atraxterra.dohy.faction.enterprise.Enterprises.CRATES));
        fr.atraxterra.dohy.faction.enterprise.Holdings.Report report = holdings.report();
        log("entreprises [{}] : banque {} ; sale {} ; net/h {} ; stocks {} ; dernier versement {} h propre {} sale {} "
                        + "blanchi {} frais {} descentes {} vendu {} perdu {} ; heures {} ; jours {} ; recel {} ; {}",
                label, FactionApi.data(player).bank(), holdings.dirty(),
                fr.atraxterra.dohy.faction.enterprise.Enterprises.netPerHour(player), stocks, report.hours(),
                report.clean(), report.dirty(), report.laundered(), report.costs(), report.raids(), report.sold(),
                report.lost(), holdings.hours().size(), holdings.days().size(),
                fr.atraxterra.dohy.faction.enterprise.Enterprises.fenceBonus(player), rows);
    }

    private static void syndicates(ServerPlayer player, String label) {
        List<String> rows = new ArrayList<>();
        for (fr.atraxterra.dohy.faction.enterprise.Syndicate s : fr.atraxterra.dohy.faction.enterprise.Syndicate
                .values()) {
            fr.atraxterra.dohy.faction.enterprise.Holdings.Standing st =
                    fr.atraxterra.dohy.faction.enterprise.Syndicates.standing(player, s);
            rows.add(s + ":" + st.state() + "/" + Math.round(st.hostility()) + " presence "
                    + fr.atraxterra.dohy.faction.enterprise.Syndicates.presence(player, s));
        }
        log("organisations [{}] : {}", label, rows);
    }

    private static void influence(ServerPlayer player, String label) {
        List<String> rows = new ArrayList<>();
        for (fr.atraxterra.dohy.faction.enterprise.Influence.Reading reading
                : fr.atraxterra.dohy.faction.enterprise.Influence.all(player)) {
            List<String> parts = new ArrayList<>();
            for (fr.atraxterra.dohy.faction.enterprise.Influence.Part part : reading.parts()) {
                parts.add(part.label() + " " + part.value());
            }
            rows.add(fr.atraxterra.dohy.faction.enterprise.Enterprises.countryName(reading.country()) + "="
                    + reading.value() + " " + parts);
        }
        fr.atraxterra.dohy.faction.enterprise.Enterprises.Site site =
                fr.atraxterra.dohy.faction.enterprise.Enterprises.here(player);
        log("influence [{}] : ici {} ({}, lat {}, region {}, refus {}) ; {}", label, site.countryName(), site.country(),
                Math.round(site.latitude() * 10.0d) / 10.0d, site.region(), site.refusal(), rows);
    }

    private static String found(ServerPlayer player, String sector, String line) {
        fr.atraxterra.dohy.faction.enterprise.Sector s = fr.atraxterra.dohy.faction.enterprise.Sector.of(sector);
        fr.atraxterra.dohy.faction.enterprise.Line l = line == null ? null
                : fr.atraxterra.dohy.faction.enterprise.Line.of(line);
        String result = fr.atraxterra.dohy.faction.enterprise.Enterprises.found(player, s, l);
        log("ouvrir {}{} : {}", sector, line == null ? "" : "/" + line, result == null ? "ouverte" : result);
        return result;
    }

    private static String id(ServerPlayer player, String sector, String line) {
        for (fr.atraxterra.dohy.faction.enterprise.Enterprise e : holdings(player).enterprises()) {
            if (e.sector().equals(sector) && (line == null || e.line().equals(line))) {
                return e.id();
            }
        }
        return "?";
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
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 24b", List.of(), 3);
            log("nation : {}", refusal == null ? "fondee" : refusal);
            FactionApi.claim(player);
            FactionApi.creditBank(player, 5_000_000L, "Essai");
            as(player, "f pays capitale");
            log("plafond avant l'arbre : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.cap(player));
            fr.atraxterra.dohy.faction.enterprise.Enterprises.Site site =
                    fr.atraxterra.dohy.faction.enterprise.Enterprises.here(player);
            for (String[] probe : new String[][] {{"USINE", null}, {"RACKET", null}, {"BANQUE", null},
                    {"PLANTATION", "HEROINE"}, {"PLANTATION", "SYNTHESE"}, {"PLANTATION", "COCAINE"},
                    {"PLANTATION", null}}) {
                fr.atraxterra.dohy.faction.enterprise.Sector s = fr.atraxterra.dohy.faction.enterprise.Sector.of(
                        probe[0]);
                fr.atraxterra.dohy.faction.enterprise.Line l = probe[1] == null ? null
                        : fr.atraxterra.dohy.faction.enterprise.Line.of(probe[1]);
                log("refus {}{} : {}", probe[0], probe[1] == null ? "" : "/" + probe[1],
                        fr.atraxterra.dohy.faction.enterprise.Enterprises.refusal(player, s, l, site));
            }
            op(player, "f arbre palier global 12");
            log("plafond apres l'arbre : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.cap(player));
            for (fr.atraxterra.dohy.faction.org.Cadre candidate
                    : fr.atraxterra.dohy.faction.org.Organisations.candidates(player)) {
                fr.atraxterra.dohy.faction.org.Organisations.hire(player, candidate.id());
            }
            influence(player, "depart");
            estate(player, "depart");
        }));
        ShotHarness.SHOTS.add(server("b0_onglet_vide", 0, player -> openMenu(player, menu("faction_enterprises"))));
        ShotHarness.SHOTS.add(server("b1_catalogue", 0, player -> openMenu(player, menu("faction_enterprise_found"))));
        ShotHarness.SHOTS.add(server("c0_ouverture", 20, player -> {
            found(player, "FERME", null);
            found(player, "COMMERCE", null);
            found(player, "TRANSPORT", null);
            influence(player, "trois entreprises");
            found(player, "USINE", null);
            found(player, "PLANTATION", "CANNABIS");
            found(player, "PLANTATION", "COCAINE");
            found(player, "LABORATOIRE", "COCAINE");
            found(player, "LABORATOIRE", "SYNTHESE");
            found(player, "ENTREPOT", null);
            found(player, "RESEAU", null);
            found(player, "FACADE", null);
            found(player, "CASINO", null);
            found(player, "RECEL", null);
            found(player, "ATELIER", null);
            found(player, "TRAFIC", null);
            influence(player, "quinze entreprises");
            found(player, "RACKET", null);
            found(player, "CONTREBANDE", null);
            found(player, "CARRIERE", null);
            fr.atraxterra.dohy.faction.FactionVault.store(player, List.of(new ItemStack(item("minecraft:iron_ingot"),
                    64), new ItemStack(item("minecraft:iron_block"), 2)));
            estate(player, "ouvertes");
            syndicates(player, "ouvertes");
            openMenu(player, menu("faction_enterprises"));
        }));
        ShotHarness.SHOTS.add(server("c1_fiche_labo", 10, player -> {
            MenuController hub = menu("faction_enterprises");
            hub.handle(player, new UiNet.Action(hub.id(), "card", "open:" + id(player, "LABORATOIRE", "COCAINE"), 0));
        }));
        ShotHarness.SHOTS.add(server("d0_heures", 10, player -> {
            op(player, "f entreprises avancer 1");
            estate(player, "1 h");
            op(player, "f entreprises avancer 1");
            estate(player, "2 h");
            op(player, "f entreprises avancer 24");
            estate(player, "26 h");
            syndicates(player, "26 h");
            openMenu(player, menu("faction_enterprises"));
        }));
        ShotHarness.SHOTS.add(server("d1_gestion", 10, player -> {
            String lab = id(player, "LABORATOIRE", "COCAINE");
            String net = id(player, "RESEAU", null);
            String farm = id(player, "FERME", null);
            log("gerant labo : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.manage(player, lab));
            log("gerant reseau : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.manage(player, net));
            log("gerant ferme : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.manage(player, farm));
            log("gerant ferme encore : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.manage(player, farm));
            log("poste tresorier sans cadre libre : {}", fr.atraxterra.dohy.faction.org.Organisations.assign(player,
                    fr.atraxterra.dohy.faction.org.Post.TRESORIER));
            for (fr.atraxterra.dohy.faction.org.Cadre cadre : fr.atraxterra.dohy.faction.org.Organisations.cadres(
                    player)) {
                log("cadre {} : poste « {} » gere « {} »", cadre.name(), cadre.post(), cadre.manages());
            }
            log("niveau labo : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.levelUp(player, lab));
            log("niveau labo : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.levelUp(player, lab));
            log("niveau reseau : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.levelUp(player, net));
            log("amelioration labo : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.upgrade(player, lab,
                    fr.atraxterra.dohy.faction.enterprise.Upgrade.EQUIPEMENT));
            log("amelioration labo encore : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.upgrade(player,
                    lab, fr.atraxterra.dohy.faction.enterprise.Upgrade.EQUIPEMENT));
            log("employes ferme -2 : {} {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.staff(player, farm, -1),
                    fr.atraxterra.dohy.faction.enterprise.Enterprises.staff(player, farm, -1));
            log("employes ferme +5 : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.staff(player, farm, 5));
            log("employes ferme au complet : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.staff(player, farm,
                    1));
            log("renommer : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.rename(player, farm,
                    "Ferme du Zambeze"));
            log("renommer avec accent : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.rename(player, farm,
                    "Fermée"));
            estate(player, "gestion");
            MenuController hub = menu("faction_enterprises");
            hub.handle(player, new UiNet.Action(hub.id(), "card", "open:" + lab, 0));
        }));
        ShotHarness.SHOTS.add(server("e0_organisations", 10, player -> {
            syndicates(player, "avant");
            fr.atraxterra.dohy.faction.enterprise.Syndicate caravane =
                    fr.atraxterra.dohy.faction.enterprise.Syndicate.CARAVANE;
            fr.atraxterra.dohy.faction.enterprise.Syndicate recifs =
                    fr.atraxterra.dohy.faction.enterprise.Syndicate.RECIFS;
            log("alliance sans accord : {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.alliance(player,
                    caravane));
            log("accord caravane : {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.accord(player, caravane));
            log("accord recifs : {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.accord(player, recifs));
            log("tribut sans guerre : {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.tribute(player, caravane));
            syndicates(player, "accords");
            op(player, "f entreprises avancer 24");
            estate(player, "avec les accords");
            log("rompre caravane : {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.breakDeal(player, caravane));
            syndicates(player, "rupture");
            for (int day = 0; day < 6; day++) {
                op(player, "f entreprises avancer 24");
                syndicates(player, "jour " + (day + 1));
            }
            estate(player, "apres la rupture");
            fr.atraxterra.dohy.faction.enterprise.Holdings.Status status =
                    fr.atraxterra.dohy.faction.enterprise.Syndicates.standing(player, caravane).state();
            log("statut caravane : {}", status);
            if (status == fr.atraxterra.dohy.faction.enterprise.Holdings.Status.GUERRE) {
                log("accord en guerre : {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.accord(player,
                        caravane));
                log("riposte : {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.riposte(player, caravane));
                syndicates(player, "riposte");
                if (fr.atraxterra.dohy.faction.enterprise.Syndicates.standing(player, caravane).state()
                        == fr.atraxterra.dohy.faction.enterprise.Holdings.Status.GUERRE) {
                    log("tribut : {} ({} $)", fr.atraxterra.dohy.faction.enterprise.Syndicates.tribute(player,
                            caravane), fr.atraxterra.dohy.faction.enterprise.Syndicates.tributePrice(player, caravane));
                }
                syndicates(player, "fin de guerre");
            }
            MenuController hub = menu("faction_enterprises");
            hub.handle(player, new UiNet.Action(hub.id(), "card", "syndicate:CARAVANE", 0));
        }));
        ShotHarness.SHOTS.add(server("f0_descentes", 10, player -> {
            log("regime etat : {}", fr.atraxterra.dohy.faction.org.Organisations.setRegime(player,
                    fr.atraxterra.dohy.faction.org.Regime.ETAT));
            estate(player, "etat");
            int raids = 0;
            for (int week = 0; week < 4; week++) {
                op(player, "f entreprises avancer 168");
                raids += holdings(player).report().raids();
                estate(player, "semaine " + (week + 1));
            }
            log("descentes en quatre semaines : {}", raids);
            List<String> journal = holdings(player).journal();
            log("journal : {}", journal.subList(0, Math.min(12, journal.size())));
            openMenu(player, menu("faction_enterprise_finance"));
        }));
        ShotHarness.SHOTS.add(server("g0_territoire", 0, player -> openMenu(player,
                menu("faction_enterprise_territory"))));
        ShotHarness.SHOTS.add(server("g1_vue_ensemble", 0, player -> openMenu(player, menu("faction"))));
        ShotHarness.SHOTS.add(server("g2_organisation", 0, player -> openMenu(player, menu("faction_organisation"))));
        ShotHarness.SHOTS.add(server("e1_navigation", 10, ShotSamples::navigation));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "entreprises")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            clicks(player);
            estate(player, "apres clics");
        }));
        ShotHarness.SHOTS.add(server("h2_vues", 10, player -> views(player, "apres clics")));
        ShotHarness.SHOTS.add(server("z0_dissolution", 10, player -> {
            UiNet.close(player);
            FactionApi.disband(player);
            estate(player, "dissoute");
            log("fin ; joueur {}", me);
        }));
    }
}
