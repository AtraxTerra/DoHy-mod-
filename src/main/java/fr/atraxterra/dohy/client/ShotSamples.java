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

/** Temporaire : le lot 23b (les technologies de la nation). */
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


    // -- Les technologies (lot 23b) ------------------------------------------------------------------

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

    private static fr.atraxterra.dohy.faction.tech.TechConfig.Domain domain(String id) {
        return fr.atraxterra.dohy.faction.tech.TechConfig.domain(id);
    }

    /** Le palier de chaque domaine, les recherches en cours, le Gouvernement, le rang, les bonus. */
    private static void state(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.FactionData data = FactionApi.data(player);
        StringBuilder levels = new StringBuilder();
        for (fr.atraxterra.dohy.faction.tech.TechConfig.Domain domain
                : fr.atraxterra.dohy.faction.tech.TechConfig.domains()) {
            levels.append(domain.id()).append('=').append(fr.atraxterra.dohy.faction.tech.Tech.level(data, domain));
            long end = data.techEnd(domain.id());
            if (end > 0L) {
                levels.append("(en cours ").append((end - System.currentTimeMillis()) / 60000L).append(" min)");
            }
            levels.append(' ');
        }
        fr.atraxterra.dohy.faction.tech.TechPerks perks = fr.atraxterra.dohy.faction.tech.TechPerks.of(data);
        log("etat [{}] : {}; paliers {} / {} ; en cours {} (dont recherches {}) ; prete {} ; gouvernement {} ; rang {}"
                        + " ; taxe {} ; bonus {} ; banque {} ; anciennes branches global={} defense={} pillage={}"
                        + " economy={}", label, levels,
                fr.atraxterra.dohy.faction.tech.Tech.researched(data), fr.atraxterra.dohy.faction.tech.Tech.total(),
                fr.atraxterra.dohy.faction.tech.Tech.researching(data), fr.atraxterra.dohy.faction.tech.Tech.running(data),
                fr.atraxterra.dohy.faction.tech.Tech.ready(player),
                fr.atraxterra.dohy.faction.Nation.government(data), fr.atraxterra.dohy.faction.NationRank.of(player),
                String.format(java.util.Locale.ROOT, "%.4f", fr.atraxterra.dohy.faction.NationRank.fee(player)),
                perks.values(), data.bank(), data.tier("global"), data.tier("defense"), data.tier("pillage"),
                data.tier("economy"));
    }

    private static void research(ServerPlayer player, String id) {
        fr.atraxterra.dohy.faction.tech.TechConfig.Domain domain = domain(id);
        String before = fr.atraxterra.dohy.faction.tech.Tech.blocker(player, domain);
        String refusal = fr.atraxterra.dohy.faction.tech.Tech.start(player, domain);
        log("rechercher {} : avant « {} » ; resultat « {} » ; fin dans {} min", id, before, refusal,
                (FactionApi.data(player).techEnd(id) - System.currentTimeMillis()) / 60000L);
    }

    /** Ce que montrent les cartes d'une vue : titres, lignes, bulles (les noms traduits restent en JSON). */
    private static void dump(ServerPlayer player, MenuController menu, String label) {
        MenuView view = menu.view(player);
        log("vue {} [{}] : « {} » / « {} » ; stats {}", menu.id(), label, view.title(), view.subtitle(),
                view.stats());
        for (MenuView.Section section : view.sections()) {
            log("  section « {} » ({} cartes, ancre « {} »)", section.title(), section.cards().size(),
                    section.anchor());
            int shown = 0;
            for (MenuView.Card card : section.cards()) {
                if (shown++ >= 14) {
                    log("    ...");
                    break;
                }
                log("    carte « {} » {} badge « {} » etat {} action « {} » droite « {} »", card.title(), card.lines(),
                        card.badge(), card.state(), card.action(), card.rightAction());
                if (!card.tooltip().isEmpty()) {
                    log("      bulle {}", card.tooltip());
                }
            }
        }
    }

    private static void branch(ServerPlayer player, String id) {
        fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE.show(player, domain(id));
    }

    private static void act(ServerPlayer player, MenuController menu, String key, int button) {
        menu.handle(player, new UiNet.Action(menu.id(), "card", key, button));
    }

    // -- Survoler une carte, pour photographier sa bulle --------------------------------------------

    private static Object any(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found.get(owner);
            } catch (NoSuchFieldException ignored) {
                // la classe du dessus
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object part(Object record, String name) throws ReflectiveOperationException {
        java.lang.reflect.Method accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }

    /** Place la souris au centre de la carte dont le titre commence par {@code title}. */
    private static void hover(String title) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Object screen = minecraft.gui.screen();
            Object content = any(screen, "content");
            List<?> placed = (List<?>) any(content, "placed");
            float scale = (float) any(screen, "scale");
            float originX = (float) any(screen, "originX");
            float originY = (float) any(screen, "originY");
            int baseX = (int) any(content, "x");
            int baseY = (int) any(content, "y");
            for (Object spot : placed) {
                MenuView.Card card = (MenuView.Card) part(spot, "card");
                String text = card.title();
                if (!text.startsWith(title)) {
                    continue;
                }
                float x = baseX + (float) part(spot, "x") + (float) part(spot, "width") / 2.0f;
                float y = baseY + (float) part(spot, "y") + Math.min(12.0f, (float) part(spot, "height") / 2.0f);
                double guiX = originX + x * scale;
                double guiY = originY + y * scale;
                var window = minecraft.getWindow();
                double pixelX = guiX * window.getScreenWidth() / window.getGuiScaledWidth();
                double pixelY = guiY * window.getScreenHeight() / window.getGuiScaledHeight();
                Object mouse = minecraft.mouseHandler;
                boolean set = false;
                for (String[] names : new String[][] {{"xpos", "ypos"}, {"xPos", "yPos"}}) {
                    try {
                        java.lang.reflect.Field fx = mouse.getClass().getDeclaredField(names[0]);
                        java.lang.reflect.Field fy = mouse.getClass().getDeclaredField(names[1]);
                        fx.setAccessible(true);
                        fy.setAccessible(true);
                        fx.setDouble(mouse, pixelX);
                        fy.setDouble(mouse, pixelY);
                        set = true;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        // autre nom
                    }
                }
                if (!set) {
                    StringBuilder fields = new StringBuilder();
                    for (java.lang.reflect.Field f : mouse.getClass().getDeclaredFields()) {
                        fields.append(f.getType().getSimpleName()).append(' ').append(f.getName()).append(", ");
                    }
                    log("survol : champs de la souris introuvables : {}", fields);
                }
                log("survol « {} » : design {} {} -> pixels {} {} ({})", text, x, y, Math.round(pixelX),
                        Math.round(pixelY), set ? "pose" : "rate");
                return;
            }
            log("survol : carte « {} » introuvable parmi {}", title, placed.size());
        } catch (Throwable t) {
            LOG.error("dohy-bug: survol " + title, t);
        }
    }

    private static void unhover() {
        try {
            Object mouse = Minecraft.getInstance().mouseHandler;
            java.lang.reflect.Field fx = mouse.getClass().getDeclaredField("xpos");
            java.lang.reflect.Field fy = mouse.getClass().getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mouse, 2.0d);
            fy.setDouble(mouse, 2.0d);
        } catch (ReflectiveOperationException ignored) {
            // rien a defaire
        }
    }

    private static void codec(ServerPlayer player) {
        fr.atraxterra.dohy.faction.FactionData data = FactionApi.data(player);
        var encoded = fr.atraxterra.dohy.faction.FactionData.MAP_CODEC.codec()
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, data);
        if (encoded.isError()) {
            log("codec : encodage en echec {}", encoded.error());
            return;
        }
        net.minecraft.nbt.Tag tag = encoded.getOrThrow();
        var decoded = fr.atraxterra.dohy.faction.FactionData.MAP_CODEC.codec()
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag);
        if (decoded.isError()) {
            log("codec : decodage en echec {}", decoded.error());
            return;
        }
        fr.atraxterra.dohy.faction.FactionData back = decoded.getOrThrow();
        StringBuilder same = new StringBuilder();
        boolean ok = true;
        for (fr.atraxterra.dohy.faction.tech.TechConfig.Domain domain
                : fr.atraxterra.dohy.faction.tech.TechConfig.domains()) {
            int a = fr.atraxterra.dohy.faction.tech.Tech.level(data, domain);
            int b = fr.atraxterra.dohy.faction.tech.Tech.level(back, domain);
            ok &= a == b && data.techEnd(domain.id()) == back.techEnd(domain.id());
            same.append(domain.id()).append(' ').append(a).append('/').append(b).append(' ');
        }
        String text = tag.toString();
        int at = text.indexOf("tech:");
        log("codec : aller-retour {} ; {}; extrait « {} »", ok ? "identique" : "DIFFERENT", same,
                at < 0 ? "pas de champ tech" : text.substring(at, Math.min(text.length(), at + 160)));
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(8)));
        ShotHarness.SHOTS.add(server("a1_depart", 100, player -> {
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
            log("technologies chargees : {} domaines, {} paliers", fr.atraxterra.dohy.faction.tech.TechConfig.domains()
                    .size(), fr.atraxterra.dohy.faction.tech.Tech.total());
            state(player, "sans nation");
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 23b", List.of(), 3);
            log("nation : {}", refusal == null ? "fondee" : refusal);
            FactionApi.claim(player);
            FactionApi.creditBank(player, 50_000_000L, "Essai");
            state(player, "nation fondee");
            log("prochain palier energie : {} ; machine hbm:machine_press dans {} ; dohy:tractor dans {} ;"
                            + " hbm:plant_flower libre {}",
                    fr.atraxterra.dohy.faction.tech.Tech.next(FactionApi.data(player), domain("energie")).name(),
                    fr.atraxterra.dohy.faction.tech.Tech.tierOf("hbm:machine_press").map(t -> t.name()).orElse("-"),
                    fr.atraxterra.dohy.faction.tech.Tech.tierOf("dohy:tractor").map(t -> t.name()).orElse("-"),
                    fr.atraxterra.dohy.faction.tech.TechConfig.free().stream().anyMatch(
                            p -> fr.atraxterra.dohy.faction.tech.TechConfig.matches(p, "hbm:plant_flower")));
        }));
        ShotHarness.SHOTS.add(server("a2_technologies", 40, player -> {
            openMenu(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE);
            dump(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE, "depart");
        }));
        ShotHarness.SHOTS.add(server("a3_energie", 40, player -> {
            branch(player, "energie");
            dump(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "energie");
        }));
        ShotHarness.SHOTS.add(client("a4_bulle_vapeur", 30, () -> hover("Palier II")));
        ShotHarness.SHOTS.add(server("a5_recherche_lancee", 40, player -> {
            Minecraft.getInstance().execute(ShotSamples::unhover);
            act(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "research", 0);
            state(player, "energie II lancee");
            research(player, "industrie");
        }));
        ShotHarness.SHOTS.add(server("a6_machines_combustion", 40, player ->
                act(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "tier:1", 1)));
        ShotHarness.SHOTS.add(server("a7_en_cours", 40, player -> {
            openMenu(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE);
            dump(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE, "en cours");
        }));
        ShotHarness.SHOTS.add(server("a8_finie", 40, player -> {
            op(player, "f techno finir");
            state(player, "energie II finie");
            branch(player, "energie");
        }));
        ShotHarness.SHOTS.add(server("a9_espace", 40, player -> {
            branch(player, "espace");
            dump(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "espace");
        }));
        ShotHarness.SHOTS.add(server("b0_gouvernement", 40, player -> {
            branch(player, "gouvernement");
            dump(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "gouvernement au depart");
        }));
        ShotHarness.SHOTS.add(client("b1_bulle_fondation", 30, () -> hover("Palier I")));
        ShotHarness.SHOTS.add(server("b2_etapes_globale", 40, player -> {
            Minecraft.getInstance().execute(ShotSamples::unhover);
            act(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "step:global", 0);
            op(player, "f arbre palier global 4");
            state(player, "globale 4");
            branch(player, "gouvernement");
        }));
        ShotHarness.SHOTS.add(server("b3_defense_bloquee", 40, player -> {
            op(player, "f arbre palier defense 6");
            op(player, "f arbre palier pillage 6");
            Tech_complete(player);
            state(player, "defense et pillage 6, industrie I");
            branch(player, "defense");
            dump(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "defense bloquee");
        }));
        ShotHarness.SHOTS.add(server("b4_defense_debloquee", 40, player -> {
            op(player, "f techno palier industrie 3");
            Tech_complete(player);
            state(player, "industrie III");
            branch(player, "defense");
        }));
        ShotHarness.SHOTS.add(server("b5_trois_recherches", 40, player -> {
            fr.atraxterra.dohy.faction.FactionData data = FactionApi.data(player);
            log("taxe avant l'economie : {}", fr.atraxterra.dohy.faction.NationRank.fee(player));
            op(player, "f techno palier economie 6");
            op(player, "f techno palier gouvernement 6");
            Tech_complete(player);
            state(player, "economie et gouvernement VI");
            research(player, "chimie");
            research(player, "agriculture");
            research(player, "nucleaire");
            research(player, "industrie");
            state(player, "trois recherches");
            openMenu(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE);
            dump(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE, "trois recherches");
        }));
        ShotHarness.SHOTS.add(client("b6_bulle_bonus", 30, () -> hover("Gouvernement")));
        ShotHarness.SHOTS.add(server("b7_vue_d_ensemble", 40, player -> {
            Minecraft.getInstance().execute(ShotSamples::unhover);
            openMenu(player, menu("faction"));
            codec(player);
        }));
        ShotHarness.SHOTS.add(server("b8_rechargement", 40, player -> {
            op(player, "dohy reload");
            state(player, "apres /dohy reload");
            op(player, "f techno finir");
            state(player, "tout fini");
            op(player, "f techno voir nucleaire");
        }));
        ShotHarness.SHOTS.add(server("b9_info", 30, player -> {
            UiNet.close(player);
            as(player, "f info");
            op(player, "f techno palier gouvernement 1");
            Tech_complete(player);
            log("rang au gouvernement I : {} ; suivant « {} »", fr.atraxterra.dohy.faction.NationRank.of(player),
                    fr.atraxterra.dohy.faction.Nation.of(player).nextRank());
            op(player, "f techno palier gouvernement 6");
        }));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "lot 23b")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            clicks(player);
            state(player, "apres les clics");
        }));
    }

    private static void Tech_complete(ServerPlayer player) {
        fr.atraxterra.dohy.faction.tech.Tech.complete(player, false);
    }
}
