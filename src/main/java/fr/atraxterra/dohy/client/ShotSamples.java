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

/** Temporaire : le lot 24e (la farmland du Chasseur). */
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

    // -- La farmland --------------------------------------------------------------------------------

    private static ServerLevel farmland(ServerPlayer player) {
        return fr.atraxterra.dohy.farmland.Farmland.level(player.level().getServer());
    }

    /** Une vue : le joueur pose dans la farmland, en vol, tourne comme il faut. */
    private static void farm(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        ServerLevel level = farmland(player);
        player.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                TeleportTransition.DO_NOTHING));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.setYRot(yaw);
                Minecraft.getInstance().player.setXRot(pitch);
            }
        });
    }

    private static Object call(String type, String method, Class<?>[] types, Object... args) {
        try {
            java.lang.reflect.Method found = Class.forName(type).getDeclaredMethod(method, types);
            found.setAccessible(true);
            return found.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            log("reflexion {}.{} : {}", type, method, e);
            return null;
        }
    }

    private static Object stat(String type, String name) {
        try {
            java.lang.reflect.Field found = Class.forName(type).getDeclaredField(name);
            found.setAccessible(true);
            return found.get(null);
        } catch (ReflectiveOperationException e) {
            return "?" + e;
        }
    }

    private static void census(ServerPlayer player, String label) {
        ServerLevel level = farmland(player);
        Map<String, Integer> types = new TreeMap<>();
        int burning = 0;
        int undead = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!entity.entityTags().contains(fr.atraxterra.dohy.farmland.FarmlandSpawner.TAG)) {
                continue;
            }
            types.merge(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(), 1, Integer::sum);
            String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
            if (path.equals("zombie") || path.equals("skeleton")) {
                undead++;
                if (entity.isOnFire()) {
                    burning++;
                }
            }
        }
        log("creatures [{}] : reserves {} ; especes {} ; morts-vivants {} dont {} en feu ; temps {}", label,
                fr.atraxterra.dohy.farmland.FarmlandSpawner.census(level), types, undead, burning,
                level.getGameTime());
    }

    private static void where(ServerPlayer player, String label) {
        log("joueur [{}] : {} a {} {} {} ; coin {}", label, player.level().dimension().identifier(),
                Math.round(player.getX()), Math.round(player.getY()), Math.round(player.getZ()),
                fr.atraxterra.dohy.farmland.Farmland.inFarmland(player)
                        ? fr.atraxterra.dohy.farmland.Farmland.zoneOf(player) : "-");
    }

    private static void blocks(ServerPlayer player) {
        ServerLevel level = farmland(player);
        int[][] probes = {{0, 0}, {-30, -30}, {46, -42}, {20, -74}, {54, 6}, {55, 6}, {30, 40}, {-26, 30},
                {-2, 18}, {-60, 20}, {-49, 52}, {-62, 62}, {61, 53}, {62, 62}, {0, 98}, {0, 103}, {0, 106}};
        for (int[] probe : probes) {
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    probe[0], probe[1]);
            BlockState below = level.getBlockState(new BlockPos(probe[0], top - 1, probe[1]));
            log("sol {} {} : coin {} ; dessus y={} {} ; biome {}", probe[0], probe[1],
                    fr.atraxterra.dohy.farmland.FarmlandMap.zone(probe[0], probe[1]), top - 1,
                    BuiltInRegistries.BLOCK.getKey(below.getBlock()), level.getBiome(new BlockPos(probe[0], G, probe[1]))
                            .unwrapKey().map(key -> key.identifier().toString()).orElse("?"));
        }
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> {
            Minecraft.getInstance().options.renderDistance().set(16);
        }));
        ShotHarness.SHOTS.add(server("a1_depart", 60, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule doDaylightCycle false");
            op(player, "gamerule advance_time false");
            op(player, "gamerule doMobSpawning true");
            op(player, "gamerule spawn_mobs true");
            op(player, "difficulty normal");
            player.setGameMode(GameType.CREATIVE);
            where(player, "depart");
            fr.atraxterra.dohy.farmland.Farmland.enter(player);
            log("farmland : construction en cours {}", fr.atraxterra.dohy.farmland.Farmland.building());
            ShotHarness.until = () -> Minecraft.getInstance().level != null && Minecraft.getInstance().level
                    .dimension() == fr.atraxterra.dohy.farmland.Farmland.DIMENSION;
        }));
        ShotHarness.SHOTS.add(server("b0_camp", 120, player -> {
            where(player, "arrivee");
            log("farmland : prete {} ; blocs inconnus {}", fr.atraxterra.dohy.farmland.Farmland.ready(farmland(player)),
                    stat("fr.atraxterra.dohy.farmland.B", "MISSING"));
            blocks(player);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }));
        ShotHarness.SHOTS.add(server("b1_vue_du_ciel", 160, player -> farm(player, 0.5d, G + 150, 0.5d, 180.0f,
                90.0f)));
        ShotHarness.SHOTS.add(server("b2_vue_du_sud", 120, player -> farm(player, 0.5d, G + 45, 70.5d, 180.0f,
                28.0f)));
        ShotHarness.SHOTS.add(server("b3_foret", 120, player -> {
            census(player, "apres les vues");
            farm(player, -14.5d, G + 9, -12.5d, 135.0f, 12.0f);
        }));
        ShotHarness.SHOTS.add(server("b4_hameau", 120, player -> farm(player, 28.5d, G + 9, -24.5d, -140.0f, 14.0f)));
        ShotHarness.SHOTS.add(server("b5_champignons", 100, player -> farm(player, 12.5d, G + 8, -58.5d, -160.0f,
                18.0f)));
        ShotHarness.SHOTS.add(server("b6_collines", 120, player -> farm(player, 16.5d, G + 13, -4.5d, -78.0f, 16.0f)));
        ShotHarness.SHOTS.add(server("b7_grotte", 80, player -> farm(player, 50.5d, G + 1.2d, 6.5d, -90.0f, 5.0f)));
        ShotHarness.SHOTS.add(server("b8_jungle", 120, player -> farm(player, 12.5d, G + 9, 22.5d, -40.0f, 12.0f)));
        ShotHarness.SHOTS.add(server("b9_lac", 120, player -> farm(player, -4.5d, G + 9, 6.5d, 45.0f, 16.0f)));
        ShotHarness.SHOTS.add(server("c0_marais", 120, player -> farm(player, -36.5d, G + 9, 8.5d, 30.0f, 14.0f)));
        ShotHarness.SHOTS.add(server("c1_nether", 140, player -> farm(player, -38.5d, G + 12, 34.5d, 50.0f, 16.0f)));
        ShotHarness.SHOTS.add(server("c2_end", 140, player -> farm(player, 38.5d, G + 13, 34.5d, -40.0f, 14.0f)));
        ShotHarness.SHOTS.add(server("c3_creatures_foret", 400, player -> {
            census(player, "apres le tour");
            farm(player, -38.5d, G + 4, -38.5d, 135.0f, 8.0f);
        }));
        ShotHarness.SHOTS.add(server("c4_creatures_nether", 400, player -> {
            census(player, "dans la foret");
            farm(player, -48.5d, G + 8, 34.5d, 30.0f, 10.0f);
        }));
        ShotHarness.SHOTS.add(server("c5_creatures_prairie", 400, player -> {
            census(player, "au Nether");
            farm(player, 30.5d, G + 6, -30.5d, -150.0f, 10.0f);
        }));
        // les regles, en survie
        ShotHarness.SHOTS.add(server("d0_regles", 40, player -> {
            census(player, "dans la prairie");
            ServerLevel level = farmland(player);
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            fr.atraxterra.dohy.farmland.Farmland.leave(player);
            fr.atraxterra.dohy.farmland.Farmland.enter(player);
            where(player, "retour au camp");
            BlockPos ground = new BlockPos(3, G, 3);
            BlockState before = level.getBlockState(ground);
            boolean broken = player.gameMode.destroyBlock(ground);
            log("casser {} : {} ; reste {}", BuiltInRegistries.BLOCK.getKey(before.getBlock()), broken,
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(ground).getBlock()));
            BlockPos blast = new BlockPos(20, G + 1, -30);
            BlockState blastBefore = level.getBlockState(blast.below());
            level.explode(null, blast.getX() + 0.5d, blast.getY(), blast.getZ() + 0.5d, 4.0f,
                    net.minecraft.world.level.Level.ExplosionInteraction.TNT);
            log("explosion : sol {} -> {}", BuiltInRegistries.BLOCK.getKey(blastBefore.getBlock()),
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(blast.below()).getBlock()));
            player.getFoodData().setFoodLevel(12);
            log("faim mise a 12 : {}", player.getFoodData().getFoodLevel());
            as(player, "f");
            as(player, "nation");
            log("commande de faction : {} ; /spawn : {}", fr.atraxterra.dohy.farmland.FarmlandEvents
                    .factionCommand("f lois"), fr.atraxterra.dohy.farmland.FarmlandEvents.factionCommand("spawn"));
            // une creature venue d'ailleurs que du Chasseur : refusee
            Entity stray = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:pig")).create(level,
                    EntitySpawnReason.NATURAL);
            if (stray instanceof net.minecraft.world.entity.Mob mob) {
                mob.snapTo(4.5d, G + 1, 4.5d, 0.0f, 0.0f);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL,
                        null);
                log("cochon naturel : ajoute {}", level.addFreshEntity(mob));
            }
        }));
        ShotHarness.SHOTS.add(server("d1_faim", 40, player -> {
            log("faim apres un tick : {} (gardee) ; saturation {}", player.getFoodData().getFoodLevel(),
                    player.getFoodData().getSaturationLevel());
            player.causeFoodExhaustion(40.0f);
        }));
        ShotHarness.SHOTS.add(server("d2_duree_de_vie", 40, player -> {
            log("faim apres l'effort : {}", player.getFoodData().getFoodLevel());
            ServerLevel level = farmland(player);
            fr.atraxterra.dohy.farmland.FarmlandSpawner.Pool meadow = null;
            for (fr.atraxterra.dohy.farmland.FarmlandSpawner.Pool pool : fr.atraxterra.dohy.farmland.FarmlandSpawner
                    .POOLS) {
                if (pool.name().equals("prairie")) {
                    meadow = pool;
                }
            }
            player.setGameMode(GameType.CREATIVE);
            farm(player, 30.5d, G + 4, -30.5d, -150.0f, 10.0f);
            net.minecraft.world.entity.Mob old = null;
            net.minecraft.world.entity.Mob hit = null;
            for (int i = 0; i < 40 && (old == null || hit == null); i++) {
                net.minecraft.world.entity.Mob mob = fr.atraxterra.dohy.farmland.FarmlandSpawner.spawn(level, meadow,
                        player, level.getRandom());
                if (mob == null) {
                    continue;
                }
                if (old == null) {
                    old = mob;
                } else {
                    hit = mob;
                }
            }
            long now = level.getGameTime();
            if (old != null && hit != null) {
                old.getPersistentData().putLong("dohy_farmland_born", now - 12_001L);
                hit.getPersistentData().putLong("dohy_farmland_born", now - 12_001L);
                hit.hurtServer(level, player.damageSources().playerAttack(player), 0.5f);
                call("fr.atraxterra.dohy.farmland.FarmlandSpawner", "sweep", new Class<?>[] {ServerLevel.class}, level);
                log("duree de vie : {} oublie(e) retire(e) {} ; {} frappe(e) retire(e) {} (marque {})",
                        BuiltInRegistries.ENTITY_TYPE.getKey(old.getType()).getPath(), old.isRemoved(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(hit.getType()).getPath(), hit.isRemoved(),
                        hit.entityTags().contains("dohy_farmland_hit"));
            } else {
                log("duree de vie : pas assez de betes ({} / {})", old, hit);
            }
        }));
        ShotHarness.SHOTS.add(server("d3_bords", 60, player -> {
            farm(player, 0.5d, G + 30, 108.5d, 0.0f, 0.0f);
            where(player, "au-dela des falaises");
        }));
        ShotHarness.SHOTS.add(server("d4_menu", 20, player -> {
            where(player, "apres le bord");
            openMenu(player, fr.atraxterra.dohy.farmland.FarmlandMenu.INSTANCE);
        }));
        ShotHarness.SHOTS.add(server("d5_retour", 60, player -> {
            UiNet.close(player);
            fr.atraxterra.dohy.farmland.Farmland.leave(player);
            where(player, "borne RETOUR");
        }));
        ShotHarness.SHOTS.add(server("d6_portail", 20, player -> openMenu(player,
                fr.atraxterra.dohy.spawn.SpawnPortals.INSTANCE)));
        ShotHarness.SHOTS.add(server("d7_grade", 80, player -> {
            UiNet.close(player);
            as(player, "farmland");
            where(player, "/farmland sans grade");
            op(player, "dohy grade " + player.getName().getString() + " heros");
            as(player, "farmland");
            where(player, "/farmland au grade Heros");
        }));
        ShotHarness.SHOTS.add(server("d8_spawn", 60, player -> {
            where(player, "avant /spawn");
            as(player, "spawn");
            where(player, "apres /spawn");
        }));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "lot 24e")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            clicks(player);
            where(player, "apres les clics");
        }));
    }
}
