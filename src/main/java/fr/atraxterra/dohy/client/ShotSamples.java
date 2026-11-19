package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.lucky.LuckyBlockEntity;
import fr.atraxterra.dohy.palamod.lucky.LuckyEvent;
import fr.atraxterra.dohy.palamod.lucky.LuckyEvents;
import fr.atraxterra.dohy.palamod.lucky.LuckyPass;
import fr.atraxterra.dohy.palamod.lucky.LuckyBlockView;
import fr.atraxterra.dohy.palamod.lucky.LuckySession;
import fr.atraxterra.dohy.palamod.lucky.LuckyType;
import fr.atraxterra.dohy.palamod.lucky.PalaLucky;
import fr.atraxterra.dohy.spawn.SpawnApi;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/** Temporaire : le cinquieme lot des lucky blocks (le noir et les lucky blocks du mois). */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    /** Sur le sol du monde plat, loin du spawn : (300, -60, 300). */
    private static BlockPos origin;

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
                    LOG.error("dohy-shots: ECHEC serveur " + name, t);
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
                LOG.error("dohy-shots: ECHEC client " + name, t);
            }
            return null;
        });
    }

    private static void command(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    /** Une position absolue : x et z au centre du bloc donne par rapport a l'origine. */
    private static void tp(ServerPlayer player, double dx, double dy, double dz, float yaw, float pitch) {
        command(player, String.format(java.util.Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f",
                player.getName().getString(), origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, yaw,
                pitch));
    }

    /** En creatif, reste en l'air la ou on l'a mis : pour les vues d'en haut. */
    private static void hover(ServerPlayer player, boolean on) {
        player.getAbilities().flying = on;
        player.onUpdateAbilities();
    }

    private static void camera(CameraType type) {
        Minecraft.getInstance().options.setCameraType(type);
    }

    private static void key(int key) {
        var screen = Minecraft.getInstance().gui.screen();
        LOG.info("dohy-shots: [lucky] touche {} sur {}", key, screen == null ? "aucun ecran" : screen.getClass()
                .getSimpleName());
        if (screen != null) {
            screen.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, 0));
        }
    }

    /**
     * Place la souris, en pixels de la fenetre. GLFW ignore le mouvement qu'il provoque lui-meme
     * (X11) : on ecrit directement la position que lit le jeu.
     */
    private static void cursor(double x, double y) {
        var handler = Minecraft.getInstance().mouseHandler;
        try {
            var xpos = handler.getClass().getDeclaredField("xpos");
            var ypos = handler.getClass().getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(handler, x);
            ypos.setDouble(handler, y);
            LOG.info("dohy-shots: [lucky] souris a {} {}", x, y);
        } catch (ReflectiveOperationException e) {
            LOG.warn("dohy-shots: [lucky] souris non deplacee", e);
        }
    }

    /** Un clic gauche, en pixels de la fenetre, sur l'ecran ouvert. */
    private static void click(double x, double y) {
        var screen = Minecraft.getInstance().gui.screen();
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        if (screen != null) {
            boolean used = screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x / scale, y / scale,
                    new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
            LOG.info("dohy-shots: [lucky] clic {} {} (echelle {}) sur {} -> {}", x, y, scale,
                    screen.getClass().getSimpleName(), used);
        }
    }

    private static BlockPos at(int dx, int dz) {
        return origin.offset(dx, 0, dz);
    }

    /** Pose un lucky block comme un joueur, avec un evenement deja tire si on le veut. */
    private static void place(ServerPlayer player, LuckyType type, String event, BlockPos pos) {
        ItemStack stack = type.stack(1);
        if (event != null) {
            stack.set(PalaLucky.EVENT.get(), event);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP,
                pos.below(), false);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        String drawn = player.level().getBlockEntity(pos) instanceof LuckyBlockEntity lucky ? lucky.event() : "?";
        LOG.info("dohy-shots: [lucky] pose {} {} -> {} evenement « {} »", type, pos, result, drawn);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Ouvre un lucky block d un coup, comme l ecran : « Ouvrir », puis la fermeture (joueur a portee). */
    private static void fire(ServerPlayer player, BlockPos pos) {
        LuckySession.open(player, pos);
        LuckySession.handle(player, new UiNet.Action(LuckyBlockView.ID, "open", "", 0));
        LuckySession.handle(player, new UiNet.Action(LuckyBlockView.ID, "use", "", 0));
        UiNet.close(player);
        LOG.info("dohy-shots: [lucky] ouvert {} -> bloc {}", pos, player.level().getBlockState(pos));
    }

    /** Un sol neuf, de l'air au-dessus et plus aucune entite autour de l'origine. */
    private static void clear(ServerPlayer player, int radius, int height) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        command(player, "fill " + (x - radius) + " " + (y - 1) + " " + (z - radius) + " " + (x + radius) + " "
                + (y - 1) + " " + (z + radius) + " minecraft:polished_andesite");
        command(player, "fill " + (x - radius) + " " + y + " " + (z - radius) + " " + (x + radius) + " "
                + (y + height) + " " + (z + radius) + " minecraft:air");
        List<Entity> around = player.level().getEntitiesOfClass(Entity.class, new AABB(origin).inflate(40),
                entity -> !(entity instanceof Player));
        around.forEach(Entity::discard);
    }

    /** Le tirage, vingt mille fois par type : la part des bons et les evenements les plus frequents. */
    private static void statistics() {
        RandomSource random = RandomSource.create(42L);
        for (LuckyType type : LuckyType.values()) {
            Map<String, Integer> counts = new HashMap<>();
            int good = 0;
            int draws = 20000;
            for (int index = 0; index < draws; index++) {
                LuckyEvent event = LuckyEvents.draw(type, random, false);
                counts.merge(event.id(), 1, Integer::sum);
                if (!event.bad()) {
                    good++;
                }
            }
            StringBuilder top = new StringBuilder();
            counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6).forEach(entry ->
                    top.append(String.format(java.util.Locale.ROOT, " %s %.1f%% (attendu %.1f%%)", entry.getKey(),
                            100.0 * entry.getValue() / draws,
                            100.0 * LuckyEvents.chance(LuckyEvents.get(entry.getKey()), type))));
            LOG.info("dohy-shots: [lucky] {} : {} evenements, bons {} %,{}", type, LuckyEvents.pool(type).size(),
                    String.format(java.util.Locale.ROOT, "%.1f", 100.0 * good / draws), top);
        }
        try {
            var draw = LuckyPass.class.getDeclaredMethod("draw", RandomSource.class);
            draw.setAccessible(true);
            int[] sizes = new int[4];
            Map<String, Integer> kinds = new HashMap<>();
            for (int index = 0; index < 20000; index++) {
                @SuppressWarnings("unchecked")
                List<Integer> wins = (List<Integer>) draw.invoke(null, random);
                int won = 0;
                for (int win : wins) {
                    if (win >= 0) {
                        won++;
                        kinds.merge(LuckyType.of(win).name(), 1, Integer::sum);
                    }
                }
                sizes[won]++;
            }
            LOG.info("dohy-shots: [lucky] pass sur 20000 lancers : 0 bloc {}, 1 bloc {}, 2 blocs {}, 3 blocs {} ;"
                    + " types {}", sizes[0], sizes[1], sizes[2], sizes[3], kinds);
        } catch (ReflectiveOperationException e) {
            LOG.error("dohy-shots: ECHEC tirage du pass", e);
        }
    }


    private static void effectsOff(ServerPlayer player) {
        player.removeAllEffects();
    }

    private static int count(ServerLevel level, BlockPos center, int radius, int down, int up,
                             java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> test) {
        int found = 0;
        for (BlockPos probe : BlockPos.betweenClosed(center.offset(-radius, -down, -radius),
                center.offset(radius, up, radius))) {
            if (test.test(level.getBlockState(probe))) {
                found++;
            }
        }
        return found;
    }


    private static void log(String format, Object... args) {
        LOG.info("dohy-shots: [lucky5] " + format, args);
    }

    private static void openInventory() {
        Minecraft.getInstance().gui.setScreen(
                new fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen(Minecraft.getInstance().player));
        cursor(24, 24);
    }

    private static void set(ServerPlayer player, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        player.level().setBlock(pos, state, 3);
    }

    private static net.minecraft.world.level.block.state.BlockState facing(net.minecraft.world.level.block.Block block) {
        var state = block.defaultBlockState();
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                    Direction.SOUTH);
        }
        return state;
    }

    /** Un lucky block du type, pose et ouvert a l'ecran (la machine a sous). */
    private static void screen(String name, LuckyType type, String event) {
        ShotHarness.SHOTS.add(server(name, 60, player -> {
            clear(player, 20, 12);
            tp(player, 0.5, 0, 2.5, 180.0f, 20.0f);
            BlockPos pos = at(0, -2);
            place(player, type, event, pos);
            LuckySession.open(player, pos);
        }));
        ShotHarness.SHOTS.add(server(name + "_fermer", 10, player -> UiNet.close(player)));
    }

    /** Un evenement ouvert devant le joueur, la capture au bout de {@code extra} tics. */
    private static void event(String name, int extra, LuckyType type, String event, float pitch) {
        ShotHarness.SHOTS.add(server(name, extra, player -> {
            clear(player, 20, 12);
            effectsOff(player);
            hover(player, false);
            tp(player, 0.5, 0, 4.5, 180.0f, pitch);
            BlockPos pos = at(0, 0);
            place(player, type, event, pos);
            fire(player, pos);
            log("{} : ouvert", event);
        }));
    }

    /**
     * Les PNJ apparaissent au hasard autour du joueur : on ramene celui de l'evenement devant lui, tourne vers
     * lui, et on pousse les autres derriere la camera.
     */
    private static void npcInFront(ServerPlayer player, String name) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        command(player, String.format(java.util.Locale.ROOT,
                "execute positioned %d %d %d run tp @e[type=minecraft:villager,distance=..12,name=!\"%s\"] %d %d %d",
                x, y, z + 4, name, x, y, z + 40));
        command(player, String.format(java.util.Locale.ROOT,
                "execute positioned %d %d %d run tp @e[type=minecraft:villager,distance=..12,name=\"%s\"] %.2f %d %.2f"
                        + " facing entity %s eyes", x, y, z + 4, name, x + 0.5d, y, z + 2.0d,
                player.getName().getString()));
        log("pnj {} ramene devant le joueur", name);
    }

    private static String slotsOf(net.minecraft.world.entity.player.Inventory inventory) {
        StringBuilder out = new StringBuilder();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                out.append(' ').append(slot).append('=').append(stack.getCount()).append(' ').append(stack.getItem());
            }
        }
        return out.length() == 0 ? " (vide)" : out.toString();
    }

    private static Object season(String field) {
        try {
            return ((java.util.function.Supplier<?>) fr.atraxterra.dohy.palamod.lucky.SeasonItems.class.getField(field)
                    .get(null)).get();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            ShotHarness.until = () -> !SpawnApi.building();
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "gamerule doDaylightCycle false");
            command(player, "gamerule doMobSpawning false");
            player.setGameMode(GameType.CREATIVE);
            origin = new BlockPos(300, -60, 300);
            tp(player, 0.5, 0, 0.5, 0.0f, 10.0f);
        }));
        ShotHarness.SHOTS.add(server("a1_terrain", 100, player -> {
            ShotHarness.until = () -> player.level().isLoaded(origin.offset(-24, 0, -24))
                    && player.level().isLoaded(origin.offset(24, 0, 24));
            clear(player, 20, 12);
        }));
        ShotHarness.SHOTS.add(server("a2_tirages", 10, player -> {
            statistics();
            log("{} evenements en tout", LuckyEvents.count());
            for (LuckyType type : LuckyType.values()) {
                if (type.seasonal()) {
                    log("{} : {} evenements", type, LuckyEvents.pool(type).size());
                }
            }
        }));
        screen("b1_ecran_halloween", LuckyType.HALLOWEEN, "bouh");
        screen("b2_ecran_noel", LuckyType.CHRISTMAS, "present");
        screen("b3_ecran_noir", LuckyType.BLACK, "tchu");
        screen("b4_ecran_septembre", LuckyType.SEPTEMBER, "dream_job");
        screen("b5_ecran_novembre", LuckyType.NOVEMBER, "november_sandstorm");
        ShotHarness.SHOTS.add(server("c1_blocs", 40, player -> {
            clear(player, 20, 12);
            hover(player, true);
            var faces = fr.atraxterra.dohy.palamod.lucky.SeasonItems.pumpkinFaces();
            for (int index = 0; index < faces.size(); index++) {
                set(player, at(index - 6, -2), facing(faces.get(index).get()));
            }
            String[] decor = {"INFINITY_PIE", "CHRISTMAS_TREE", "CHRISTMAS_MOCKUP", "WHISTLE"};
            for (int index = 0; index < decor.length; index++) {
                set(player, at(3 + index, -2), ((net.minecraft.world.level.block.Block) season(decor[index]))
                        .defaultBlockState());
            }
            int column = -6;
            for (LuckyType type : LuckyType.values()) {
                if (type.seasonal()) {
                    set(player, at(column++, -4), PalaLucky.block(type).get().defaultBlockState());
                }
            }
            tp(player, 0.5, 2.2, 5.5, 180.0f, 22.0f);
        }));
        ShotHarness.SHOTS.add(server("c2_objets", 10, player -> {
            var inventory = player.getInventory();
            inventory.clearContent();
            String[] ids = {"butcher_knife", "job_offer", "trade_file", "daemon_amulet", "pumpkin_shear", "spider_gloves",
                    "witch_broom", "halloween_stone", "infinity_pie", "pumpkin_face_3", "chocolate_milk", "christmas_axe",
                    "christmas_stone", "multicolor_gift", "present", "present_bag", "wish_card", "christmas_tree",
                    "christmas_mockup", "whistle", "water_pistol", "supersonic_boots", "weather_missile", "extending_arm",
                    "alchemist_flask", "ghost_cape", "quicksand"};
            for (int index = 0; index < ids.length; index++) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("palamod", ids[index]));
                if (item.isEmpty()) {
                    log("objet absent : {}", ids[index]);
                    continue;
                }
                inventory.setItem(9 + index, new ItemStack(item.get()));
            }
            log("objets poses dans l'inventaire : {}", ids.length);
        }));
        ShotHarness.SHOTS.add(client("c3_inventaire", 15, ShotSamples::openInventory));
        ShotHarness.SHOTS.add(client("c4_fermer", 5, () -> Minecraft.getInstance().gui.setScreen(null)));
        event("d1_trouilles", 40, LuckyType.HALLOWEEN, "sixtrouilles", 15.0f);
        ShotHarness.SHOTS.add(server("d3_anglais", 25, player -> {
            command(player, "kill @e[type=minecraft:armor_stand]");
            clear(player, 20, 12);
            hover(player, false);
            tp(player, 0.5, 0, 4.5, 180.0f, 10.0f);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.STONE));
            player.getInventory().setSelectedSlot(0);
            player.addEffect(new MobEffectInstance(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.ENGLISH, 600, 0));
            log("anglais : effet {}", player.hasEffect(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.ENGLISH));
        }));
        ShotHarness.SHOTS.add(client("d4_anglais_langue", 5, () -> log("anglais : block.minecraft.stone = {}",
                net.minecraft.locale.Language.getInstance().getOrDefault("block.minecraft.stone"))));
        ShotHarness.SHOTS.add(server("d6_fin_anglais", 25, player -> player.removeEffect(
                fr.atraxterra.dohy.palamod.lucky.SeasonEffects.ENGLISH)));
        ShotHarness.SHOTS.add(client("d7_francais", 5, () -> log("francais : block.minecraft.stone = {}",
                net.minecraft.locale.Language.getInstance().getOrDefault("block.minecraft.stone"))));
        event("e4_neige", 40, LuckyType.CHRISTMAS, "snow_army", 15.0f);
        ShotHarness.SHOTS.add(server("e4b_neige_haut", 20, player -> {
            hover(player, true);
            tp(player, 0.5, 7, 12.5, 180.0f, 35.0f);
        }));
        ShotHarness.SHOTS.add(server("e5_paladium", 120, player -> {
            clear(player, 20, 12);
            effectsOff(player);
            hover(player, false);
            tp(player, 0.5, 0, 0.5, 0.0f, 0.0f);
            BlockPos pos = at(0, 2);
            place(player, LuckyType.BLACK, "partoutladium", pos);
            fire(player, pos);
        }));
        ShotHarness.SHOTS.add(server("e6_paladium_haut", 20, player -> {
            hover(player, true);
            tp(player, 0.5, 12, 0.5, 0.0f, 90.0f);
            log("paladium : {} minerais", count((ServerLevel) player.level(), origin, 9, 4, 3,
                    state -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()
                            .equals("paladium_ore")));
        }));
        event("e7_noescroc", 10, LuckyType.CHRISTMAS, "noescroc_father", 10.0f);
        ShotHarness.SHOTS.add(server("e7b_noescroc_devant", 20, player -> npcInFront(player, "Pere Noel")));
        event("e8_jack", 10, LuckyType.HALLOWEEN, "small_candies", 10.0f);
        ShotHarness.SHOTS.add(server("e8b_jack_devant", 20, player -> npcInFront(player, "Jack-o-lantern")));
        ShotHarness.SHOTS.add(server("f1_cadeaux", 10, player -> {
            var inventory = player.getInventory();
            inventory.clearContent();
            inventory.setItem(0, new ItemStack((net.minecraft.world.item.Item) season("PRESENT_BAG")));
            inventory.setSelectedSlot(0);
            inventory.getItem(0).use(player.level(), player, InteractionHand.MAIN_HAND);
            log("sac de cadeaux ouvert :{}", slotsOf(inventory));
            for (int slot = 0; slot < 36; slot++) {
                if (inventory.getItem(slot).is((net.minecraft.world.item.Item) season("PRESENT"))) {
                    inventory.setSelectedSlot(slot);
                    inventory.getItem(slot).use(player.level(), player, InteractionHand.MAIN_HAND);
                    break;
                }
            }
            log("cadeau ouvert :{}", slotsOf(inventory));
        }));
        ShotHarness.SHOTS.add(server("z1_capsule", 30, player -> {
            clear(player, 20, 12);
            effectsOff(player);
            hover(player, false);
            player.getInventory().clearContent();
            tp(player, 0.5, 0, 0.5, 180.0f, 10.0f);
            BlockPos pos = at(0, -2);
            place(player, LuckyType.SEPTEMBER, "dream_job", pos);
            fire(player, pos);
        }));
        ShotHarness.SHOTS.add(server("z2_lune", 360, player -> log("lune : joueur a y = {}", player.getY())));
        ShotHarness.SHOTS.add(client("z3_lune_dos", 20, () -> camera(CameraType.THIRD_PERSON_BACK)));
        ShotHarness.SHOTS.add(client("z5_camera", 5, () -> camera(CameraType.FIRST_PERSON)));
        ShotHarness.SHOTS.add(server("d2_coffre", 40, player -> {
            clear(player, 20, 12);
            hover(player, false);
            effectsOff(player);
            tp(player, 0.5, 0, 2.5, 180.0f, 5.0f);
            BlockPos pos = at(0, 0);
            place(player, LuckyType.SEPTEMBER, "september_chest", pos);
            fire(player, pos);
        }));
        ShotHarness.SHOTS.add(server("d2b_coffre_ouest", 10, player -> tp(player, 0.5, 0, 2.5, 90.0f, 5.0f)));
        ShotHarness.SHOTS.add(server("d2c_coffre_est", 10, player -> tp(player, 0.5, 0, 2.5, 270.0f, 0.0f)));
        ShotHarness.SHOTS.add(server("d2d_coffre_sud", 10, player -> tp(player, 0.5, 0, 2.5, 0.0f, 20.0f)));
        ShotHarness.SHOTS.add(server("e1_boules", 30, player -> {
            clear(player, 20, 12);
            effectsOff(player);
            hover(player, false);
            tp(player, 0.5, 0, 0.5, 180.0f, 15.0f);
            BlockPos pos = at(0, -2);
            place(player, LuckyType.CHRISTMAS, "drank_too_much", pos);
            fire(player, pos);
            player.removeEffect(net.minecraft.world.effect.MobEffects.NAUSEA);
        }));
        ShotHarness.SHOTS.add(client("e2_boules_dos", 20, () -> camera(CameraType.THIRD_PERSON_BACK)));
        ShotHarness.SHOTS.add(client("e3_camera", 5, () -> camera(CameraType.FIRST_PERSON)));
        ShotHarness.SHOTS.add(server("z4_fin", 10, player -> LOG.info("dohy-shots: [fin] {}",
                List.of(player.getName().getString()))));
    }
}
