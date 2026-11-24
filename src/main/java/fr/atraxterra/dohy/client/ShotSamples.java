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

/** Temporaire : les PNJ du spawn. */
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

    // -- Les PNJ du spawn, a la charte de Paladium ---------------------------------------------------------------

    private static final int FLOOR = 200;
    private static final int PAD_Z = 78;

    private static ServerPlayer player() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null || me == null ? null : server.getPlayerList().getPlayer(me);
    }

    private static ServerLevel spawnLevel(ServerPlayer player) {
        return player.level().getServer().getLevel(fr.atraxterra.dohy.spawn.DoHySpawn.DIMENSION);
    }

    /** Cache ou montre l'interface (F1), par reflexion : 26.2 l'a sortie des options. */
    private static void hud(boolean visible) {
        Object hud = Minecraft.getInstance().gui.hud;
        try {
            for (java.lang.reflect.Method m : hud.getClass().getMethods()) {
                if (m.getName().equals("setHidden") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == boolean.class) {
                    m.invoke(hud, !visible);
                    return;
                }
            }
            for (Class<?> c = hud.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.getType() == boolean.class && f.getName().toLowerCase().contains("hid")) {
                        f.setAccessible(true);
                        f.setBoolean(hud, !visible);
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            LOG.error("dohy-bug: bascule d'interface", t);
        }
    }

    /** Une vue du spawn : le joueur pose la, en spectateur pour ne pas tomber. */
    private static void view(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        ServerLevel level = spawnLevel(player);
        player.setGameMode(GameType.SPECTATOR);
        player.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                TeleportTransition.DO_NOTHING));
    }

    private static List<fr.atraxterra.dohy.spawn.SpawnNpc> npcs(ServerPlayer player) {
        ServerLevel level = spawnLevel(player);
        return level.getEntitiesOfClass(fr.atraxterra.dohy.spawn.SpawnNpc.class,
                new AABB(-140, 100, -140, 140, 320, 180), e -> true);
    }

    private static String block(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
    }

    /** Chaque PNJ : son role, son poste, sa place, et ce qui l'entoure (pieds, tete, sol, enseigne). */
    private static void census(ServerPlayer player, String label) {
        ServerLevel level = spawnLevel(player);
        List<fr.atraxterra.dohy.spawn.SpawnNpc> found = npcs(player);
        Map<String, Integer> roles = new TreeMap<>();
        for (fr.atraxterra.dohy.spawn.SpawnNpc npc : found) {
            roles.merge(npc.role().id(), 1, Integer::sum);
            BlockPos post = npc.post();
            String feet = block(level, post);
            String head = block(level, post.above());
            String floor = block(level, post.below());
            boolean sturdy = level.getBlockState(post.below()).isFaceSturdy(level, post.below(), Direction.UP);
            String sign = "";
            for (int dy = 2; dy <= 6; dy++) {
                for (int dz = -1; dz <= 0; dz++) {
                    BlockPos at = post.offset(0, dy, dz);
                    if (level.getBlockEntity(at) instanceof fr.atraxterra.dohy.spawn.HologramBlockEntity hologram) {
                        sign += "[" + hologram.text() + " +" + dy + (dz != 0 ? " z" + dz : "") + "]";
                    }
                }
            }
            log("PNJ [{}] {} « {} » poste {} pos {} {} {} yaw {} pieds={} tete={} sol={} (plein {}) tourne={} enseignes {}",
                    label, npc.role().id(), npc.getCustomName() == null ? "?" : npc.getCustomName().getString(),
                    post.toShortString(), String.format("%.2f", npc.getX()), String.format("%.2f", npc.getY()),
                    String.format("%.2f", npc.getZ()), Math.round(npc.getYHeadRot()), feet, head, floor, sturdy,
                    level.isPositionEntityTicking(post), sign.isEmpty() ? "aucune" : sign);
        }
        log("PNJ [{}] : {} en tout, par role {}", label, found.size(), roles);
    }

    @SuppressWarnings("unchecked")
    private static String openView(ServerPlayer player) {
        try {
            java.lang.reflect.Field field = UiNet.class.getDeclaredField("OPEN");
            field.setAccessible(true);
            Object id = ((Map<UUID, Object>) field.get(null)).get(player.getUUID());
            return id == null ? "aucune" : id.toString();
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }

    private static void inventory(ServerPlayer player, String label) {
        Map<String, Integer> items = new TreeMap<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        log("sac [{}] : {} ; argent {}", label, items, Economy.balance(player));
    }

    private static void claims(ServerPlayer player, String label) {
        log("kits pris [{}] : {} ; prets {} ; pastille {}", label,
                player.getData(fr.atraxterra.dohy.kits.DoHyKits.CLAIMS.get()).taken().keySet(),
                fr.atraxterra.dohy.kits.PlayerKits.readyCount(player),
                fr.atraxterra.dohy.ui.NavBadges.of(player).badges().get("kits"));
    }

    private static void claim(ServerPlayer player, String kit, String expect) {
        String refusal = fr.atraxterra.dohy.kits.PlayerKits.claim(player, kit);
        log("kit {} : {} (attendu : {})", kit, refusal == null ? "DONNE" : "refus « " + refusal + " »", expect);
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> {
            Minecraft.getInstance().options.renderDistance().set(8);
            // comme le serveur du joueur : deux chunks de simulation
            Minecraft.getInstance().options.simulationDistance().set(2);
        }));
        ShotHarness.SHOTS.add(server("a1_depart", 40, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule advance_time false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty peaceful");
            log("kits charges : {}", fr.atraxterra.dohy.kits.PlayerKits.kits().stream()
                    .map(k -> k.id() + "(" + k.stacks().size() + " piles, " + k.delay() + ")").toList());
            inventory(player, "a la connexion");
            claims(player, "a la connexion");
            fr.atraxterra.dohy.spawn.SpawnApi.enter(player);
            ShotHarness.until = () -> {
                ServerPlayer p = player();
                return p != null && !fr.atraxterra.dohy.spawn.SpawnApi.building()
                        && fr.atraxterra.dohy.spawn.SpawnApi.inSpawn(p);
            };
        }));
        ShotHarness.SHOTS.add(server("a2_postes", 120, player -> {
            player.setGameMode(GameType.SURVIVAL);
            view(player, 0.5d, FLOOR + 1.0d, 30.5d, 180.0f, 0.0f);
        }));
        ShotHarness.SHOTS.add(server("a3_recensement", 20, player -> census(player, "centre")));
        // -- les captures
        ShotHarness.SHOTS.add(server("b1_arrivee_intendant", 100, player -> {
            player.setGameMode(GameType.SURVIVAL);
            view(player, 0.5d, FLOOR + 1.0d, PAD_Z + 0.5d, 180.0f, 0.0f);
            player.setGameMode(GameType.SURVIVAL);
        }));
        ShotHarness.SHOTS.add(server("b2_intendant_de_pres", 60,
                player -> view(player, -1.0d, FLOOR + 1.6d, PAD_Z + 0.5d, 120.0f, 8.0f)));
        ShotHarness.SHOTS.add(server("b3_passeur_pilote", 60,
                player -> view(player, 0.5d, FLOOR + 1.6d, PAD_Z - 1.5d, 0.0f, 6.0f)));
        ShotHarness.SHOTS.add(server("b4_nef_commerce", 120,
                player -> view(player, 20.5d, FLOOR + 1.8d, -12.0d, 180.0f, 4.0f)));
        ShotHarness.SHOTS.add(server("b5_nef_carriere", 80,
                player -> view(player, -19.5d, FLOOR + 1.8d, -12.0d, 180.0f, 4.0f)));
        ShotHarness.SHOTS.add(server("b6_banquier_de_pres", 60,
                player -> view(player, -12.5d, FLOOR + 2.0d, -16.3d, 180.0f, 10.0f)));
        ShotHarness.SHOTS.add(server("b7_portail", 80,
                player -> view(player, 0.5d, FLOOR + 2.0d, 1.5d, 180.0f, 2.0f)));
        ShotHarness.SHOTS.add(server("b8_sortie", 60,
                player -> view(player, 0.5d, FLOOR + 2.0d, -4.0d, 0.0f, 6.0f)));
        ShotHarness.SHOTS.add(server("b9_marche_noir", 80,
                player -> view(player, -1.5d, FLOOR - 5.4d, 45.5d, -90.0f, 6.0f)));
        ShotHarness.SHOTS.add(server("c1_autel", 100,
                player -> view(player, -15.5d, FLOOR + 2.4d, 22.5d, 90.0f, 2.0f)));
        ShotHarness.SHOTS.add(server("c2_spawn_de_haut", 140,
                player -> view(player, 0.5d, FLOOR + 30.0d, 120.5d, 180.0f, 22.0f)));
        ShotHarness.SHOTS.add(server("c3_recensement_fin", 20, player -> {
            view(player, 0.5d, FLOOR + 1.0d, 30.5d, 180.0f, 0.0f);
            census(player, "fin des vues");
        }));
    }
}
