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

/** Temporaire : le lot 24d (lois, police, operations, contrats). */
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


    // -- Le lot 24d ------------------------------------------------------------------------------

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    /** Glisse une operation ou un contrat dans la justice de la nation (essai). */
    private static void inject(ServerPlayer player, Object record) {
        try {
            Object justice = field(holdings(player), "justice");
            java.lang.reflect.Method put = justice.getClass().getDeclaredMethod("put", record.getClass());
            put.setAccessible(true);
            put.invoke(justice, record);
            log("injecte : {}", record);
        } catch (ReflectiveOperationException error) {
            LOG.error("dohy-bug: injection impossible", error);
        }
    }

    private static void setDirty(ServerPlayer player, long amount) {
        try {
            Object holdings = holdings(player);
            java.lang.reflect.Field field = holdings.getClass().getDeclaredField("dirty");
            field.setAccessible(true);
            field.setLong(holdings, amount);
        } catch (ReflectiveOperationException error) {
            LOG.error("dohy-bug: argent sale impossible", error);
        }
    }

    private static List<?> justiceList(ServerPlayer player, String name) {
        try {
            return (List<?>) field(field(holdings(player), "justice"), name);
        } catch (ReflectiveOperationException error) {
            return List.of();
        }
    }

    private static void justice(ServerPlayer player, String label) {
        List<String> laws = new ArrayList<>();
        for (fr.atraxterra.dohy.faction.enterprise.Law.Topic topic
                : fr.atraxterra.dohy.faction.enterprise.Law.Topic.values()) {
            laws.add(topic + "=" + fr.atraxterra.dohy.faction.enterprise.Enterprises.law(player, topic));
        }
        log("justice [{}] : lois {} ; operations {} ; contrats {}", label, laws, justiceList(player, "operations"),
                justiceList(player, "contracts"));
    }

    private static void cadres(ServerPlayer player, String label) {
        List<String> rows = new ArrayList<>();
        for (fr.atraxterra.dohy.faction.org.Cadre cadre : fr.atraxterra.dohy.faction.org.Organisations.cadres(player)) {
            rows.add(cadre.name() + " poste « " + cadre.post() + " » prison " + cadre.jailed() + " loyaute "
                    + Math.round(cadre.loyalty()));
        }
        log("cadres [{}] : {}", label, rows);
    }

    private static void preview(ServerPlayer player, String label) {
        try {
            Class<?> payout = Class.forName("fr.atraxterra.dohy.faction.enterprise.Payout");
            java.lang.reflect.Method method = payout.getDeclaredMethod("preview", ServerPlayer.class);
            method.setAccessible(true);
            log("apercu [{}] : {}", label, method.invoke(null, player));
        } catch (ReflectiveOperationException e) {
            log("apercu [{}] : echec {}", label, e);
        }
    }

    private static void factors(ServerPlayer player, String label) {
        for (fr.atraxterra.dohy.faction.enterprise.Enterprise enterprise : holdings(player).enterprises()) {
            if (enterprise.legal()) {
                continue;
            }
            log("facteurs [{}] {} : risque {} ; {}", label, enterprise.name(), Math.round(
                    fr.atraxterra.dohy.faction.enterprise.Enterprises.risk(player, enterprise)),
                    fr.atraxterra.dohy.faction.enterprise.Enterprises.perHour(player, enterprise));
        }
    }

    private static int country(ServerPlayer player) {
        return fr.atraxterra.dohy.faction.enterprise.Enterprises.here(player).country();
    }

    private static int holderNation(ServerPlayer player) {
        return fr.atraxterra.dohy.earth.EarthNations.holder(player.level().getServer(), country(player)).rival();
    }

    private static void operation(ServerPlayer player, String id, long runsIn, double strength) {
        long now = System.currentTimeMillis();
        inject(player, new fr.atraxterra.dohy.faction.enterprise.Operation(id, holderNation(player),
                List.of(country(player)), now, now + runsIn, "ANNONCEE", strength, false, 0));
    }

    private static void contract(ServerPlayer player, String id, String syndicate, String key, long units,
                                 long reward) {
        inject(player, new fr.atraxterra.dohy.faction.enterprise.Contract(id, syndicate, key, units, 0L, reward,
                System.currentTimeMillis(), 0L, "OFFRE"));
    }

    private static void hub(ServerPlayer player, String key) {
        MenuController hub = menu("faction_enterprises");
        hub.handle(player, new UiNet.Action(hub.id(), "card", key, 0));
    }

    private static void laws(ServerPlayer player, String key) {
        MenuController laws = menu("faction_laws");
        laws.handle(player, new UiNet.Action(laws.id(), "card", key, 0));
    }

    private static void syndicatePage(ServerPlayer player, String syndicate) {
        hub(player, "syndicate:" + syndicate);
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
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 24d", List.of(), 3);
            log("nation : {}", refusal == null ? "fondee" : refusal);
            FactionApi.claim(player);
            FactionApi.creditBank(player, 5_000_000L, "Essai");
            op(player, "f arbre palier global 12");
            for (fr.atraxterra.dohy.faction.org.Cadre candidate
                    : fr.atraxterra.dohy.faction.org.Organisations.candidates(player)) {
                fr.atraxterra.dohy.faction.org.Organisations.hire(player, candidate.id());
            }
            found(player, "PLANTATION", "CANNABIS");
            found(player, "LABORATOIRE", "COCAINE");
            found(player, "RESEAU", null);
            found(player, "ATELIER", null);
            found(player, "TRAFIC", null);
            found(player, "CASINO", null);
            found(player, "RACKET", null);
            found(player, "FACADE", null);
            found(player, "FERME", null);
            fr.atraxterra.dohy.faction.FactionVault.store(player, List.of(new ItemStack(item("minecraft:iron_ingot"),
                    64)));
            log("gerant labo : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.manage(player,
                    id(player, "LABORATOIRE", "COCAINE")));
            log("gerant reseau : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.manage(player,
                    id(player, "RESEAU", null)));
            log("gerant atelier : {}", fr.atraxterra.dohy.faction.enterprise.Enterprises.manage(player,
                    id(player, "ATELIER", null)));
            int nation = holderNation(player);
            log("pays {} tenu par la nation {} ; ses lois {} ; police {}", country(player), nation,
                    fr.atraxterra.dohy.faction.enterprise.Law.of(nation),
                    fr.atraxterra.dohy.faction.enterprise.Law.police(nation));
            for (int index = 0; index < fr.atraxterra.dohy.faction.war.NationTraits.count(); index++) {
                log("lois de {} : {}", fr.atraxterra.dohy.faction.war.NationTraits.of(index).name(),
                        fr.atraxterra.dohy.faction.enterprise.Law.of(index));
            }
            factors(player, "chez une nation");
            cadres(player, "depart");
            estate(player, "depart");
        }));
        // -- la loi d'ailleurs, le catalogue, la fiche, la nation ---------------------------------
        ShotHarness.SHOTS.add(server("b0_lois", 10, player -> openMenu(player, menu("faction_laws"))));
        ShotHarness.SHOTS.add(server("b1_catalogue", 10, player -> openMenu(player, menu("faction_enterprise_found"))));
        ShotHarness.SHOTS.add(server("b2_fiche_labo", 10, player -> hub(player, "open:" + id(player, "LABORATOIRE",
                "COCAINE"))));
        ShotHarness.SHOTS.add(server("b3_nation", 10, player -> {
            MenuController diplomacy = menu("faction_diplomacy");
            diplomacy.handle(player, new UiNet.Action(diplomacy.id(), "card", "nation:" + holderNation(player), 0));
        }));
        // -- une operation annoncee : la menace, la corruption, se mettre au vert -----------------
        ShotHarness.SHOTS.add(server("c0_menace", 10, player -> {
            setDirty(player, 400_000L);
            operation(player, "o901", 12L * 3_600_000L, 1.0d);
            operation(player, "o902", 12L * 3_600_000L, 1.0d);
            justice(player, "deux menaces");
            openMenu(player, menu("faction_enterprises"));
        }));
        ShotHarness.SHOTS.add(server("c1_corruption", 10, player -> {
            fr.atraxterra.dohy.faction.enterprise.Operation first = fr.atraxterra.dohy.faction.enterprise.Police
                    .pending(player).getFirst();
            log("acheter : prix {} chance {} refus {}", fr.atraxterra.dohy.faction.enterprise.Police.bribePrice(player,
                            first), fr.atraxterra.dohy.faction.enterprise.Police.bribeChance(player, first),
                    fr.atraxterra.dohy.faction.enterprise.Police.bribeRefusal(player, first));
            log("acheter : {}", fr.atraxterra.dohy.faction.enterprise.Police.bribe(player, "o901"));
            log("acheter encore : {}", fr.atraxterra.dohy.faction.enterprise.Police.bribe(player, "o901"));
            log("au vert : {}", fr.atraxterra.dohy.faction.enterprise.Police.lieLow(player, "o902"));
            justice(player, "apres corruption");
            estate(player, "au vert");
            openMenu(player, menu("faction_enterprises"));
        }));
        ShotHarness.SHOTS.add(server("c2_frappe", 10, player -> {
            op(player, "f entreprises avancer 20");
            justice(player, "apres 20 h");
            estate(player, "apres 20 h");
            // une operation qui frappe sans qu'on fasse rien : coups de filet, gerants arretes
            op(player, "f entreprises avancer 30");
            operation(player, "o903", 0L, 1.25d);
            op(player, "f entreprises avancer 1");
            justice(player, "operation menee");
            cadres(player, "apres l'operation");
            estate(player, "apres l'operation");
            List<String> journal = holdings(player).journal();
            log("journal : {}", journal.subList(0, Math.min(14, journal.size())));
        }));
        ShotHarness.SHOTS.add(server("c3_prison", 10, player -> {
            fr.atraxterra.dohy.faction.org.Cadre jailed = null;
            for (fr.atraxterra.dohy.faction.org.Cadre cadre : fr.atraxterra.dohy.faction.org.Organisations.cadres(
                    player)) {
                if (cadre.jailed()) {
                    jailed = cadre;
                }
            }
            if (jailed == null) {
                log("personne en prison : on arrete un gerant pour l'essai");
                for (fr.atraxterra.dohy.faction.org.Cadre cadre : fr.atraxterra.dohy.faction.org.Organisations.cadres(
                        player)) {
                    if (!cadre.manages().isEmpty()) {
                        fr.atraxterra.dohy.faction.org.Organisations.arrest(player, cadre.id(),
                                System.currentTimeMillis() + 3L * 86_400_000L, " a l'essai");
                        jailed = cadre;
                        break;
                    }
                }
            }
            if (jailed != null) {
                log("caution de {} : {} $ ; nommer en prison : {} ; gerer en prison : {}", jailed.name(),
                        fr.atraxterra.dohy.faction.org.Organisations.bail(player, jailed),
                        fr.atraxterra.dohy.faction.org.Organisations.appoint(player, jailed.id(),
                                fr.atraxterra.dohy.faction.org.Post.TRESORIER),
                        fr.atraxterra.dohy.faction.org.Organisations.manage(player, jailed.id(), "e1", "essai"));
                MenuController org = menu("faction_organisation");
                org.handle(player, new UiNet.Action(org.id(), "card", "cadre:" + jailed.id(), 0));
            }
            cadres(player, "prison");
        }));
        ShotHarness.SHOTS.add(server("c4_caution", 10, player -> {
            for (fr.atraxterra.dohy.faction.org.Cadre cadre : fr.atraxterra.dohy.faction.org.Organisations.cadres(
                    player)) {
                if (cadre.jailed()) {
                    log("caution payee pour {} : {}", cadre.name(), fr.atraxterra.dohy.faction.org.Organisations
                            .payBail(player, cadre.id()));
                    break;
                }
            }
            cadres(player, "apres la caution");
            UiNet.close(player);
        }));
        // -- tes pays, tes lois -------------------------------------------------------------------
        ShotHarness.SHOTS.add(server("d0_tes_lois", 10, player -> {
            op(player, "f pays donner " + fr.atraxterra.dohy.faction.enterprise.Enterprises.countryName(
                    country(player)));
            log("pays tenu par toi : {}", fr.atraxterra.dohy.earth.EarthNations.heldBy(player));
            laws(player, "law:CANNABIS:up");
            laws(player, "law:CANNABIS:up");
            laws(player, "law:JEU:up");
            laws(player, "law:DROGUES:up");
            laws(player, "law:CONTREBANDE:up");
            laws(player, "law:EXTORSION:down");
            justice(player, "premier vote");
            op(player, "f entreprises avancer 25");
            laws(player, "law:CANNABIS:up");
            laws(player, "law:JEU:up");
            laws(player, "law:DROGUES:up");
            laws(player, "law:CONTREBANDE:up");
            justice(player, "second vote");
            factors(player, "chez toi");
            for (int index = 0; index < fr.atraxterra.dohy.faction.war.NationTraits.count(); index++) {
                log("humeur de {} : lois {} ; diplomatie {}", fr.atraxterra.dohy.faction.war.NationTraits.of(index)
                                .name(), Math.round(fr.atraxterra.dohy.faction.enterprise.Enterprises.lawMood(player,
                                index) * 10.0d) / 10.0d,
                        Math.round(fr.atraxterra.dohy.faction.org.Diplomacy.moodShift(player, index) * 10.0d) / 10.0d);
            }
            openMenu(player, menu("faction_laws"));
        }));
        ShotHarness.SHOTS.add(server("d1_argent_propre", 10, player -> {
            estate(player, "avant 2 h chez toi");
            preview(player, "avant 2 h chez toi");
            op(player, "f entreprises avancer 2");
            estate(player, "2 h chez toi, casino et reseau legaux");
            syndicates(player, "lois votees");
            openMenu(player, menu("faction_enterprises"));
        }));
        // -- l'operation de l'Etat contre une organisation ----------------------------------------
        ShotHarness.SHOTS.add(server("e0_etat", 10, player -> {
            log("regime : {}", fr.atraxterra.dohy.faction.org.Organisations.setRegime(player,
                    fr.atraxterra.dohy.faction.org.Regime.ETAT));
            syndicatePage(player, "CARAVANE");
        }));
        ShotHarness.SHOTS.add(server("e1_operation", 10, player -> {
            fr.atraxterra.dohy.faction.enterprise.Syndicate caravane = fr.atraxterra.dohy.faction.enterprise.Syndicate
                    .CARAVANE;
            log("operation : pays {} chance {} refus {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.heldIn(
                    player, caravane), fr.atraxterra.dohy.faction.enterprise.Syndicates.operationChance(player,
                    caravane), fr.atraxterra.dohy.faction.enterprise.Syndicates.refusal(player, caravane,
                    fr.atraxterra.dohy.faction.enterprise.Syndicates.Action.OPERATION));
            log("operation : {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.operation(player, caravane));
            log("operation encore : {}", fr.atraxterra.dohy.faction.enterprise.Syndicates.operation(player, caravane));
            syndicates(player, "apres l'operation");
            log("humeur apres l'operation : {}", Math.round(fr.atraxterra.dohy.faction.enterprise.Enterprises.lawMood(
                    player, holderNation(player) < 0 ? 0 : holderNation(player)) * 10.0d) / 10.0d);
            syndicatePage(player, "CARAVANE");
        }));
        // -- les contrats ---------------------------------------------------------------------------
        ShotHarness.SHOTS.add(server("f0_contrats", 10, player -> {
            contract(player, "k901", "CONDOR", "drug:COCAINE", 64L, 23_000L);
            contract(player, "k902", "LOTUS", "crates", 8L, 4_800L);
            contract(player, "k903", "CARAVANE", "drug:CANNABIS", 96L, 15_600L);
            contract(player, "k904", "RECIFS", "drug:HEROINE", 64L, 25_000L);
            openMenu(player, menu("faction_enterprises"));
        }));
        ShotHarness.SHOTS.add(server("f1_livrer", 10, player -> {
            log("accepter k901 : {}", fr.atraxterra.dohy.faction.enterprise.Contracts.accept(player, "k901"));
            log("accepter k902 : {}", fr.atraxterra.dohy.faction.enterprise.Contracts.accept(player, "k902"));
            log("refuser k903 : {}", fr.atraxterra.dohy.faction.enterprise.Contracts.refuse(player, "k903"));
            log("accepter k904 (sans heroine) : {}", fr.atraxterra.dohy.faction.enterprise.Contracts.accept(player,
                    "k904"));
            log("accepter k901 encore : {}", fr.atraxterra.dohy.faction.enterprise.Contracts.accept(player, "k901"));
            long dirty = holdings(player).dirty();
            player.getInventory().add(new ItemStack(item("dohy:powder_parcel"), 1));
            player.getInventory().add(new ItemStack(item("dohy:powder_bag"), 3));
            fr.atraxterra.dohy.faction.enterprise.Enterprises.deposit(player, "drug:COCAINE", 40L);
            log("livrer k901 : {}", fr.atraxterra.dohy.faction.enterprise.Contracts.deliver(player, "k901"));
            log("inventaire apres : colis {} sachets {} ; stock cocaine {} ; argent sale {} -> {}",
                    count(player, item("dohy:powder_parcel")), count(player, item("dohy:powder_bag")),
                    holdings(player).stock("drug:COCAINE"), dirty, holdings(player).dirty());
            log("livrer k902 sans caisses : {}", fr.atraxterra.dohy.faction.enterprise.Contracts.deliver(player,
                    "k902"));
            syndicates(player, "contrat rempli");
            justice(player, "contrats");
            syndicatePage(player, "LOTUS");
        }));
        ShotHarness.SHOTS.add(server("f2_echeance", 10, player -> {
            op(player, "f entreprises avancer 73");
            justice(player, "echeance passee");
            for (Object item : justiceList(player, "contracts")) {
                if (item instanceof fr.atraxterra.dohy.faction.enterprise.Contract c && c.id().startsWith("k90")) {
                    log("contrat {} : {}", c.id(), c.state());
                }
            }
            syndicates(player, "contrat manque");
            List<String> journal = holdings(player).journal();
            log("journal : {}", journal.subList(0, Math.min(12, journal.size())));
            openMenu(player, menu("faction_enterprises"));
        }));
        // -- une semaine de plus, pour voir les evenements naturels --------------------------------
        ShotHarness.SHOTS.add(server("g0_semaines", 10, player -> {
            for (int week = 0; week < 3; week++) {
                op(player, "f entreprises avancer 168");
            }
            justice(player, "trois semaines");
            syndicates(player, "trois semaines");
            estate(player, "trois semaines");
            preview(player, "trois semaines");
            List<String> journal = holdings(player).journal();
            log("journal : {}", journal.subList(0, Math.min(20, journal.size())));
            openMenu(player, menu("faction_enterprises"));
        }));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "lot 24d")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            clicks(player);
            justice(player, "apres clics");
        }));
        ShotHarness.SHOTS.add(server("z0_fin", 10, player -> {
            UiNet.close(player);
            FactionApi.disband(player);
            log("fin ; joueur {}", me);
        }));
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
}
