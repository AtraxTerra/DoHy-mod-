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

/** Temporaire : le lot 24a (regime, cadres PNJ, diplomatie). */
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


    // -- L'organisation --------------------------------------------------------------------------

    private static final String[] PAGES = {"faction", "faction_organisation", "faction_cadre", "faction_settings",
            "faction_diplomacy", "faction_diplomacy_nation", "guerre", "faction_enemy_bases", "faction_claims",
            "faction_tree", "faction_bank", "faction_events", "faction_ranking"};

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

    private static void state(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.org.Organisation org = fr.atraxterra.dohy.faction.org.Organisations.of(player);
        List<String> cadres = new ArrayList<>();
        for (fr.atraxterra.dohy.faction.org.Cadre cadre : org.cadres()) {
            cadres.add(cadre.id() + " " + cadre.name() + " " + cadre.rank() + " talent " + cadre.skill() + " "
                    + cadre.character() + " poste « " + cadre.post() + " » loyaute " + Math.round(cadre.loyalty())
                    + " salaire " + fr.atraxterra.dohy.faction.org.Organisations.salary(player, cadre));
        }
        List<String> posts = new ArrayList<>();
        for (fr.atraxterra.dohy.faction.org.Post post : fr.atraxterra.dohy.faction.org.Post.values()) {
            posts.add(post + "=" + Math.round(fr.atraxterra.dohy.faction.org.Organisations.strength(player, post)
                    * 100.0d) + "%");
        }
        log("etat [{}] : regime {} ; banque {} ; dette {} ; paie/j {} ; plafond {} ; postes {} ; cadres {}", label,
                org.regime(), FactionApi.data(player).bank(), org.debt(),
                fr.atraxterra.dohy.faction.org.Organisations.payroll(player),
                fr.atraxterra.dohy.faction.org.Organisations.cap(player), posts, cadres);
    }

    private static void diplomacy(ServerPlayer player, String label) {
        List<String> rows = new ArrayList<>();
        for (int nation = 0; nation < fr.atraxterra.dohy.faction.war.NationTraits.count(); nation++) {
            fr.atraxterra.dohy.faction.war.WarData.Relation relation =
                    fr.atraxterra.dohy.faction.war.WarStrategy.relationOf(player, nation);
            fr.atraxterra.dohy.faction.org.Treaty treaty = fr.atraxterra.dohy.faction.org.Diplomacy.treaty(player,
                    nation);
            rows.add(nation + ":" + Math.round(relation.hostility()) + "/" + relation.stance()
                    + (treaty == null ? "" : "/" + treaty.type())
                    + (fr.atraxterra.dohy.faction.org.Diplomacy.rival(player, nation) ? "/RIVALE" : "")
                    + " shift " + Math.round(fr.atraxterra.dohy.faction.org.Diplomacy.moodShift(player, nation))
                    + (fr.atraxterra.dohy.faction.org.Diplomacy.protects(player, nation) ? " PROTEGE" : ""));
        }
        log("diplomatie [{}] : taxe {} ; reduction {} ; places {} ; {}", label,
                fr.atraxterra.dohy.faction.NationRank.fee(player),
                fr.atraxterra.dohy.faction.org.Diplomacy.taxCut(player),
                fr.atraxterra.dohy.faction.org.Diplomacy.slots(player), rows);
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
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 24a", List.of(), 3);
            log("nation : {}", refusal == null ? "fondee" : refusal);
            FactionApi.claim(player);
            FactionApi.creditBank(player, 2_000_000L, "Essai");
            as(player, "f pays capitale");
            java.util.Map<String, Integer> traits = new java.util.TreeMap<>();
            for (int round = 0; round < 12; round++) {
                for (fr.atraxterra.dohy.faction.org.Cadre candidate
                        : fr.atraxterra.dohy.faction.org.Organisations.candidates(player)) {
                    traits.merge(candidate.trait(), 1, Integer::sum);
                }
                op(player, "f organisation avancer 24");
            }
            log("caracteres de 36 candidats : {}", traits);
            state(player, "depart");
        }));
        ShotHarness.SHOTS.add(server("b0_organisation_vide", 0, player -> openMenu(player,
                menu("faction_organisation"))));
        ShotHarness.SHOTS.add(server("b1_embauche", 20, player -> {
            for (fr.atraxterra.dohy.faction.org.Cadre candidate
                    : fr.atraxterra.dohy.faction.org.Organisations.candidates(player)) {
                log("embauche {} : {}", candidate.name(), fr.atraxterra.dohy.faction.org.Organisations.hire(player,
                        candidate.id()));
            }
            log("embauche d'un quatrieme : {}", fr.atraxterra.dohy.faction.org.Organisations.hire(player, "inconnu"));
            state(player, "embauches");
            List<fr.atraxterra.dohy.faction.org.Cadre> cadres = fr.atraxterra.dohy.faction.org.Organisations.cadres(
                    player);
            if (!cadres.isEmpty()) {
                String first = cadres.get(0).id();
                log("promotion trop tot : {}", fr.atraxterra.dohy.faction.org.Organisations.promote(player, first));
                for (int round = 0; round < 5; round++) {
                    op(player, "f organisation avancer 100");
                    for (fr.atraxterra.dohy.faction.org.Cadre cadre
                            : fr.atraxterra.dohy.faction.org.Organisations.cadres(player)) {
                        String result = fr.atraxterra.dohy.faction.org.Organisations.promote(player, cadre.id());
                        log("promotion {} : {}", cadre.name(), result == null ? "faite" : result);
                    }
                }
            }
            for (fr.atraxterra.dohy.faction.org.Post post : fr.atraxterra.dohy.faction.org.Post.values()) {
                log("nommer {} : {}", post, fr.atraxterra.dohy.faction.org.Organisations.assign(player, post));
            }
            log("regime criminel : {}", fr.atraxterra.dohy.faction.org.Organisations.setRegime(player,
                    fr.atraxterra.dohy.faction.org.Regime.CRIMINELLE));
            log("regime etat aussitot : {}", fr.atraxterra.dohy.faction.org.Organisations.setRegime(player,
                    fr.atraxterra.dohy.faction.org.Regime.ETAT));
            state(player, "postes");
            openMenu(player, menu("faction_organisation"));
        }));
        ShotHarness.SHOTS.add(server("b2_cadre", 0, player -> {
            List<fr.atraxterra.dohy.faction.org.Cadre> cadres = fr.atraxterra.dohy.faction.org.Organisations.cadres(
                    player);
            MenuController org = menu("faction_organisation");
            if (!cadres.isEmpty()) {
                org.handle(player, new UiNet.Action(org.id(), "card", "cadre:" + cadres.get(0).id(), 0));
            }
        }));
        ShotHarness.SHOTS.add(server("c0_diplomatie", 0, player -> {
            op(player, "guerre admin hostilite 1 10");
            op(player, "guerre admin hostilite 2 30");
            op(player, "guerre admin hostilite 3 80");
            diplomacy(player, "avant");
            openMenu(player, menu("faction_diplomacy"));
        }));
        ShotHarness.SHOTS.add(server("c1_traites", 10, player -> {
            fr.atraxterra.dohy.faction.org.Treaty.Type pacte = fr.atraxterra.dohy.faction.org.Treaty.Type.PACTE;
            fr.atraxterra.dohy.faction.org.Treaty.Type accord = fr.atraxterra.dohy.faction.org.Treaty.Type.ACCORD;
            fr.atraxterra.dohy.faction.org.Treaty.Type alliance = fr.atraxterra.dohy.faction.org.Treaty.Type.ALLIANCE;
            log("alliance sans pacte (0) : {}", fr.atraxterra.dohy.faction.org.Diplomacy.propose(player, 0, alliance));
            log("pacte (0) : {}", fr.atraxterra.dohy.faction.org.Diplomacy.propose(player, 0, pacte));
            log("accord (0) en plus : {}", fr.atraxterra.dohy.faction.org.Diplomacy.propose(player, 0, accord));
            log("alliance (0) : {}", fr.atraxterra.dohy.faction.org.Diplomacy.propose(player, 0, alliance));
            log("accord (1) : {}", fr.atraxterra.dohy.faction.org.Diplomacy.propose(player, 1, accord));
            log("pacte (2, hostile) : {}", fr.atraxterra.dohy.faction.org.Diplomacy.propose(player, 2, pacte));
            log("renouvelle accord (1) : {}", fr.atraxterra.dohy.faction.org.Diplomacy.propose(player, 1, accord));
            diplomacy(player, "traites");
            MenuController hub = menu("faction_diplomacy");
            hub.handle(player, new UiNet.Action(hub.id(), "card", "nation:0", 0));
        }));
        ShotHarness.SHOTS.add(server("c2_nation_hostile", 0, player -> {
            MenuController hub = menu("faction_diplomacy");
            hub.handle(player, new UiNet.Action(hub.id(), "card", "nation:2", 0));
        }));
        ShotHarness.SHOTS.add(server("c3_rivalite", 10, player -> {
            log("rivale (1, sous accord) : {}", fr.atraxterra.dohy.faction.org.Diplomacy.toggleRival(player, 1));
            log("pacte avec une rivale : {}", fr.atraxterra.dohy.faction.org.Diplomacy.propose(player, 1,
                    fr.atraxterra.dohy.faction.org.Treaty.Type.PACTE));
            log("bonus de conquete (1) : {}", fr.atraxterra.dohy.faction.org.Diplomacy.conquestBonus(player, 1));
            log("rompre l'alliance (0) : {}", fr.atraxterra.dohy.faction.org.Diplomacy.breakTreaty(player, 0));
            diplomacy(player, "rivalite et trahison");
            openMenu(player, menu("faction_diplomacy"));
        }));
        ShotHarness.SHOTS.add(server("d0_paie", 10, player -> {
            state(player, "avant paie");
            op(player, "f organisation avancer 30");
            state(player, "apres 30 h");
            long bank = FactionApi.data(player).bank();
            FactionApi.payFromBank(player, bank, "Essai : banque videe");
            op(player, "f organisation avancer 48");
            state(player, "48 h sans argent");
            op(player, "f organisation avancer 168");
            op(player, "f organisation avancer 168");
            state(player, "deux semaines sans argent");
            FactionApi.creditBank(player, 1_000_000L, "Essai");
            op(player, "f organisation avancer 2");
            state(player, "renfloue");
            openMenu(player, menu("faction_organisation"));
        }));
        ShotHarness.SHOTS.add(server("e0_vue_ensemble", 0, player -> openMenu(player, menu("faction"))));
        ShotHarness.SHOTS.add(server("e1_navigation", 10, ShotSamples::navigation));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "organisation")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            teleport(player, 3000.5d, Y, 3000.5d, 180.0f, 20.0f);
            clicks(player);
            state(player, "apres clics");
        }));
        ShotHarness.SHOTS.add(server("h2_vues", 10, player -> views(player, "apres clics")));
        ShotHarness.SHOTS.add(server("z0_dissolution", 10, player -> {
            UiNet.close(player);
            FactionApi.disband(player);
            state(player, "dissoute");
            log("fin ; joueur {}", me);
        }));
    }
}
