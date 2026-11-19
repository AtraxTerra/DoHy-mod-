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

/** Temporaire : le quatrieme lot des lucky blocks. */
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
        ItemStack held = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP,
                pos.below(), false);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        String drawn = player.level().getBlockEntity(pos) instanceof LuckyBlockEntity lucky ? lucky.event() : "?";
        LOG.info("dohy-shots: [lucky] pose {} {} -> {} evenement « {} »", type, pos, result, drawn);
        if (!(player.level().getBlockEntity(pos) instanceof LuckyBlockEntity)) {
            LOG.info("dohy-shots: [lucky] bloc {} ; entites autour : {}", player.level().getBlockState(pos),
                    player.level().getEntitiesOfClass(Entity.class, new AABB(pos).inflate(1.0d), entity -> true));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
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
        LOG.info("dohy-shots: [lucky4] " + format, args);
    }

    /** Les cases non vides, de from a to (exclu). */
    private static String slots(net.minecraft.world.entity.player.Inventory inventory, int from, int to) {
        StringBuilder out = new StringBuilder();
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                out.append(' ').append(slot).append('=').append(stack.getCount()).append(' ').append(stack.getItem());
            }
        }
        return out.length() == 0 ? " (vide)" : out.toString();
    }

    private static int dirt(net.minecraft.world.entity.player.Inventory inventory) {
        int dirt = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot).is(net.minecraft.world.item.Items.DIRT)) {
                dirt += inventory.getItem(slot).getCount();
            }
        }
        return dirt;
    }

    private static void openInventory() {
        Minecraft.getInstance().gui.setScreen(
                new fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen(Minecraft.getInstance().player));
        cursor(24, 24);
    }

    /** Les objets au sol autour de l'origine, ramasses dans le sac. */
    private static void pickup(ServerPlayer player, int radius) {
        for (ItemEntity drop : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(radius))) {
            player.getInventory().add(drop.getItem().copy());
            drop.discard();
        }
    }

    private static String describe(ItemStack stack) {
        return stack.getCount() + " " + stack.getItem() + " nom=" + stack.getHoverName().getString()
                + " lore=" + stack.get(net.minecraft.core.component.DataComponents.LORE)
                + " donnees=" + stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                + " enchantements=" + stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
    }

    private static BlockPos coords(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(-?\\d+), (-?\\d+), (-?\\d+)").matcher(text);
        return matcher.find() ? new BlockPos(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))) : null;
    }

    private static void waitLoaded(ServerPlayer player) {
        ShotHarness.until = () -> player.level().isLoaded(origin.offset(-24, 0, -24))
                && player.level().isLoaded(origin.offset(24, 0, 24));
    }

    private static <T extends Entity> T nearest(ServerPlayer player, Class<T> type, BlockPos at, double radius) {
        List<T> found = player.level().getEntitiesOfClass(type, new AABB(at).inflate(radius));
        return found.isEmpty() ? null : found.get(0);
    }

    private static void spin(ServerPlayer player) {
        fr.atraxterra.dohy.palamod.lucky.LuckyPass.handle(player, new UiNet.Action(
                fr.atraxterra.dohy.palamod.lucky.LuckyPassView.ID, "spin", "", 0));
        var data = PalaLucky.data(player);
        log("lucky pass : lancer fait, offerts restants {}, gains {}, lancers {}", data.passBonus(), data.passWins(),
                data.passSpins());
    }


    private static final String[] DROPS = {"invisible_human", "fakestuffendium", "balaclava", "calcium", "airmin",
            "poulay", "fakemoney", "palarelou_green"};
    private static final String[] GIVES = {"pig_helmet", "pig_chestplate", "pig_leggings", "pig_boots", "cavehelmet",
            "flashmcqueen"};

    private static void wear(ServerPlayer player, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
        player.setItemSlot(EquipmentSlot.HEAD, head);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, feet);
    }

    private static ItemStack of(net.neoforged.neoforge.registries.DeferredItem<?> item) {
        return new ItemStack(item.get());
    }

    private static String states(net.minecraft.world.level.Level level, BlockPos center) {
        StringBuilder out = new StringBuilder();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(center.offset(dx, 0, dz)).getBlock()).getPath());
            }
        }
        return out.toString();
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            ShotHarness.until = () -> !SpawnApi.building();
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "gamerule doDaylightCycle false");
            player.setGameMode(GameType.CREATIVE);
            origin = new BlockPos(300, -60, 300);
            tp(player, 0.5, 0, 0.5, 0.0f, 10.0f);
            statistics();
        }));
        ShotHarness.SHOTS.add(server("a1_terrain", 100, player -> {
            waitLoaded(player);
            clear(player, 20, 14);
        }));

        // -- J'ai glisse chef : la nappe de slimepads, vue d'en haut --------------------
        ShotHarness.SHOTS.add(server("b1_glisse", 15, player -> {
            clear(player, 20, 14);
            hover(player, false);
            tp(player, 0.5, 0, 0.5, 0.0f, 10.0f);
            BlockPos pos = at(5, 0);
            place(player, LuckyType.PALADIUM, "slime_pad", pos);
            fire(player, pos);
            int pads = count(player.level(), origin, 4, 0, 0,
                    state -> state.is(fr.atraxterra.dohy.palamod.pvp.PalaPvp.SLIMEPAD.get()));
            log("j'ai glisse chef : {} slimepads a hauteur des pieds (attendu 49)", pads);
            hover(player, true);
            tp(player, 0.5, 6, -6.5, 0.0f, 42.0f);
        }));

        // -- Tour de slime : vue de l'interieur, puis de dehors -------------------------
        ShotHarness.SHOTS.add(server("b2_tour_dedans", 30, player -> {
            clear(player, 20, 14);
            hover(player, false);
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            // Sans armure, deux coups du pic (15 points) suffisent : Resistance IV pour survivre a la capture.
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 600, 3));
            tp(player, 0.5, 0, 0.5, 0.0f, -90.0f);
            BlockPos pos = at(4, 3);
            place(player, LuckyType.PALADIUM, "slime_tower", pos);
            fire(player, pos);
            log("tour de slime : joueur a y={} (attendu {}), sante {}", player.getY(), origin.getY() + 1,
                    player.getHealth());
        }));
        ShotHarness.SHOTS.add(server("b3_tour_dehors", 20, player -> {
            int pads = 0;
            for (int dy = 0; dy < 10; dy++) {
                if (player.level().getBlockState(origin.above(dy)).is(
                        fr.atraxterra.dohy.palamod.pvp.PalaPvp.SLIMEPAD.get())) {
                    pads++;
                }
            }
            log("tour de slime : {} slimepads (attendu 10), pic au sommet {}, murs {} ; joueur a y={}, sante {}"
                            + " (moins de 20 : le pic)", pads,
                    player.level().getBlockState(origin.above(10)).is(
                            fr.atraxterra.dohy.palamod.pvp.PalaPvp.PALADIUM_SPIKE.get()),
                    count(player.level(), origin.above(5), 1, 5, 4,
                            state -> state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)),
                    player.getY(), player.getHealth());
            player.setGameMode(GameType.CREATIVE);
            player.setHealth(player.getMaxHealth());
            player.removeAllEffects();
            hover(player, true);
            tp(player, 5.5, 4, -6.5, 35.0f, 8.0f);
        }));

        // -- Les objets : l'inventaire, et ce que chacun fait ---------------------------
        ShotHarness.SHOTS.add(server("c1_objets", 10, player -> {
            clear(player, 20, 14);
            hover(player, false);
            tp(player, 0.5, 0, 0.5, 0.0f, 10.0f);
            player.getInventory().clearContent();
            for (var item : fr.atraxterra.dohy.palamod.lucky.LuckyItems.ORDERED) {
                player.getInventory().add(new ItemStack(item.get(), item.get().getDefaultMaxStackSize()));
            }
            ItemStack money = of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.FAKE_MONEY);
            log("billet : {} ; par {}", describe(money), money.getMaxStackSize());
            ItemStack cave = of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.CAVE_HELMET);
            ItemStack iron = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
            ItemStack titane = new ItemStack(fr.atraxterra.dohy.palamod.PalaItems.TITANE_INGOT.get());
            ItemStack paladium = new ItemStack(fr.atraxterra.dohy.palamod.PalaItems.PALADIUM_INGOT.get());
            log("casque de speleologue : durabilite {} (attendu 300), repare au titane {}", cave.getMaxDamage(),
                    cave.isValidRepairItem(titane));
            log("airmin reparables au fer {} (attendu false) ; cagoule au fer {} (attendu true) ; faux endium au"
                            + " paladium {} (attendu true) ; cochon au titane {} (attendu true)",
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.AIR_MIN_BOOTS).isValidRepairItem(iron),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.BALACLAVA_HELMET).isValidRepairItem(iron),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.FAKE_ENDIUM_HELMET).isValidRepairItem(paladium),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.PIG_HELMET).isValidRepairItem(titane));
            log("brique de lait : {} gorgees ; pomme de vitesse par {} ; epee Couac {}",
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.CALCIUM).getMaxDamage(),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.SPEED_APPLE).getMaxStackSize(),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.COUAC_SWORD).getAttributeModifiers());
        }));
        ShotHarness.SHOTS.add(client("c2_inventaire", 20, ShotSamples::openInventory));
        ShotHarness.SHOTS.add(client("c3_ferme", 5, () -> Minecraft.getInstance().gui.setScreen(null)));

        // -- Les armures portees, vues de face ----------------------------------------
        ShotHarness.SHOTS.add(server("d0_cochon", 5, player -> {
            player.getInventory().clearContent();
            tp(player, 0.5, 0, 0.5, 180.0f, 0.0f);
            wear(player, of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.PIG_HELMET),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.PIG_CHESTPLATE),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.PIG_LEGGINGS),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.PIG_BOOTS));
            log("cochon : armure {} ({} de defense)", player.getArmorValue(), player.getArmorValue());
        }));
        ShotHarness.SHOTS.add(client("d1_cochon_face", 15, () -> camera(CameraType.THIRD_PERSON_FRONT)));
        ShotHarness.SHOTS.add(server("d2_faux_endium", 15, player -> {
            wear(player, of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.FAKE_ENDIUM_HELMET),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.FAKE_ENDIUM_CHESTPLATE),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.FAKE_ENDIUM_LEGGINGS),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.FAKE_ENDIUM_BOOTS));
            log("faux endium : armure {} (le fer : 15)", player.getArmorValue());
        }));
        ShotHarness.SHOTS.add(server("d3_cagoule_invisible", 15, player -> {
            wear(player, of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.BALACLAVA_HELMET),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.INVISIBLE_CHESTPLATE),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.INVISIBLE_LEGGINGS),
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.AIR_MIN_BOOTS));
            log("cagoule, invisible, airmin : armure {}", player.getArmorValue());
        }));
        ShotHarness.SHOTS.add(server("d4_speleo", 15, player -> wear(player,
                of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.CAVE_HELMET), ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY)));
        ShotHarness.SHOTS.add(client("d5_premiere_personne", 5, () -> camera(CameraType.FIRST_PERSON)));

        // -- Le casque de speleologue : le sol transparent ------------------------------
        ShotHarness.SHOTS.add(server("e1_vision", 30, player -> {
            int x = origin.getX();
            int z = origin.getZ();
            command(player, "fill " + (x - 4) + " -63 " + (z - 4) + " " + (x + 4) + " -62 " + (z + 4)
                    + " minecraft:stone");
            command(player, "fill " + (x - 1) + " -62 " + (z - 1) + " " + (x + 1) + " -62 " + (z + 1)
                    + " minecraft:air");
            command(player, "setblock " + x + " -63 " + z + " minecraft:diamond_ore");
            command(player, "setblock " + (x + 1) + " -63 " + z + " minecraft:gold_ore");
            command(player, "setblock " + (x - 1) + " -63 " + (z + 1) + " minecraft:emerald_ore");
            command(player, "setblock " + (x + 1) + " -63 " + (z - 1) + " minecraft:redstone_ore");
            command(player, "setblock " + (x - 1) + " -63 " + (z - 1) + " minecraft:lapis_ore");
            // La poche est dans le noir : une lumiere invisible, pour que la capture montre les minerais.
            command(player, "setblock " + x + " -62 " + z + " minecraft:light[level=15]");
            hover(player, false);
            wear(player, of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.CAVE_HELMET), ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY);
            tp(player, 0.5, 0, 0.5, 0.0f, 90.0f);
            player.setGameMode(GameType.SURVIVAL);
        }));
        ShotHarness.SHOTS.add(server("e2_vision_compte", 5, player -> {
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            log("vision : client{} ; serveur{} ; usure du casque {} / {}", states(Minecraft.getInstance().level,
                    origin.below()), states(player.level(), origin.below()), helmet.getDamageValue(),
                    helmet.getMaxDamage());
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }));
        ShotHarness.SHOTS.add(server("e3_vision_retiree", 15, player -> log("casque retire : client{}",
                states(Minecraft.getInstance().level, origin.below()))));
        ShotHarness.SHOTS.add(server("e4_casque_use", 30, player -> {
            ItemStack worn = of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.CAVE_HELMET);
            worn.setDamageValue(worn.getMaxDamage() - 1);
            player.setItemSlot(EquipmentSlot.HEAD, worn);
        }));
        ShotHarness.SHOTS.add(server("e5_casque_casse", 5, player -> {
            log("casque au bout de ses 300 points : tete {} (attendu vide) ; client{}",
                    player.getItemBySlot(EquipmentSlot.HEAD), states(Minecraft.getInstance().level, origin.below()));
            player.setGameMode(GameType.CREATIVE);
        }));

        // -- Brique de lait, AirMin, pomme de vitesse ----------------------------------
        ShotHarness.SHOTS.add(server("f1_calcium", 5, player -> {
            clear(player, 20, 14);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            ItemStack milk = of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.CALCIUM);
            player.setItemInHand(InteractionHand.MAIN_HAND, milk);
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 600, 0));
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 600, 1));
            ItemStack after = player.getMainHandItem().finishUsingItem(player.level(), player);
            log("brique de lait : effets {} (attendu 0) ; usure {} (attendu 1) ; meme objet {}",
                    player.getActiveEffects().size(), player.getMainHandItem().getDamageValue(),
                    after == player.getMainHandItem());
            var cow = net.minecraft.world.entity.EntityTypes.COW.create(player.level(),
                    net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            cow.snapTo(player.getX() + 1.0d, player.getY(), player.getZ(), 0.0f, 0.0f);
            player.level().addFreshEntity(cow);
            player.attack(cow);
            log("vache frappee : usure {} (attendu 0), sante de la vache {} / {}",
                    player.getMainHandItem().getDamageValue(), cow.getHealth(), cow.getMaxHealth());
            player.getMainHandItem().setDamageValue(9);
            player.getMainHandItem().finishUsingItem(player.level(), player);
            log("dixieme gorgee : main {} (attendu vide)", player.getMainHandItem());
            cow.discard();
            wear(player, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.AIR_MIN_BOOTS));
        }));
        ShotHarness.SHOTS.add(server("f2_airmin_pomme", 5, player -> {
            MobEffectInstance jump = player.getEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST);
            log("airmin : saut {} (attendu amplificateur 2)", jump);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.getFoodData().setFoodLevel(4);
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    of(fr.atraxterra.dohy.palamod.lucky.LuckyItems.SPEED_APPLE));
            player.getMainHandItem().finishUsingItem(player.level(), player);
            log("pomme de vitesse : {} (attendu amplificateur 7, 200 tics) ; faim {}",
                    player.getEffect(net.minecraft.world.effect.MobEffects.SPEED), player.getFoodData().getFoodLevel());
            player.removeAllEffects();
            player.setGameMode(GameType.CREATIVE);
        }));

        // -- Les evenements du findium : ce qu'ils donnent ------------------------------
        ShotHarness.SHOTS.add(server("g1_findium", 20, player -> {
            clear(player, 20, 14);
            player.getInventory().clearContent();
            wear(player, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
            player.setGameMode(GameType.SURVIVAL);
            for (int index = 0; index < GIVES.length; index++) {
                BlockPos pos = at(-6 + 2 * index, -6);
                tp(player, pos.getX() - origin.getX() + 0.5, 0, pos.getZ() - origin.getZ() + 2.5, 180.0f, 10.0f);
                place(player, LuckyType.FINDIUM, GIVES[index], pos);
                fire(player, pos);
            }
            log("objets donnes :{}", slots(player.getInventory(), 0, 36));
            for (int index = 0; index < DROPS.length; index++) {
                BlockPos pos = at(-7 + 2 * index, -2);
                tp(player, pos.getX() - origin.getX() + 0.5, 0, pos.getZ() - origin.getZ() + 2.5, 180.0f, 10.0f);
                place(player, LuckyType.FINDIUM, DROPS[index], pos);
                fire(player, pos);
            }
            StringBuilder dropped = new StringBuilder();
            for (ItemEntity drop : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(12))) {
                dropped.append(' ').append(drop.getItem().getCount()).append(' ').append(drop.getItem().getItem());
            }
            log("objets au sol :{}", dropped);
            log("faux bloc vert : {}", player.level().getBlockState(at(-7 + 2 * 7, -2)));
            player.setGameMode(GameType.CREATIVE);
            hover(player, true);
            tp(player, 0.5, 3, 5.5, 180.0f, 30.0f);
        }));
        ShotHarness.SHOTS.add(server("g2_vert_saute", 15, player -> {
            BlockPos fake = at(-7 + 2 * 7, -2);
            player.setGameMode(GameType.SURVIVAL);
            hover(player, false);
            tp(player, fake.getX() - origin.getX() + 0.5, 0, fake.getZ() - origin.getZ() + 2.5, 180.0f, 30.0f);
            boolean broken = player.gameMode.destroyBlock(fake);
            BlockPos moved = null;
            // Le saut va jusqu'a 21 blocs, sur la surface.
            for (BlockPos probe : BlockPos.betweenClosed(fake.offset(-22, -1, -22), fake.offset(22, 3, 22))) {
                if (player.level().getBlockState(probe).is(
                        fr.atraxterra.dohy.palamod.lucky.PalaLucky.GREEN_PALADIUM_FAKE_BLOCK.get())) {
                    moved = probe.immutable();
                }
            }
            log("faux bloc vert casse ({}) : il a saute en {} -> {}", broken, moved,
                    moved == null ? "?" : player.level().getBlockState(moved));
            player.setGameMode(GameType.CREATIVE);
        }));

        // -- L'ecran, sur les images dessinees -----------------------------------------
        ShotHarness.SHOTS.add(server("h0_ecran_airmin", 30, player -> {
            clear(player, 20, 14);
            hover(player, false);
            tp(player, 0.5, 0, 3.5, 180.0f, 10.0f);
            place(player, LuckyType.FINDIUM, "airmin", at(0, 0));
            place(player, LuckyType.PALADIUM, "slime_pad", at(3, 0));
            place(player, LuckyType.FINDIUM, "flashmcqueen", at(-3, 0));
            LuckySession.open(player, at(0, 0));
        }));
        ShotHarness.SHOTS.add(client("h1_roulette", 4, () -> key(GLFW.GLFW_KEY_SPACE)));
        ShotHarness.SHOTS.add(client("h2_airmin", 100, () -> {
        }));
        ShotHarness.SHOTS.add(client("h3_ferme", 30, () -> Minecraft.getInstance().gui.setScreen(null)));
        ShotHarness.SHOTS.add(server("h4_ecran_turbo", 30, player -> LuckySession.open(player, at(-3, 0))));
        ShotHarness.SHOTS.add(client("h5_roulette", 4, () -> key(GLFW.GLFW_KEY_SPACE)));
        ShotHarness.SHOTS.add(client("h6_turbo", 100, () -> {
        }));
        ShotHarness.SHOTS.add(client("h7_ferme", 30, () -> Minecraft.getInstance().gui.setScreen(null)));
        ShotHarness.SHOTS.add(server("h8_ecran_glisse", 30, player -> LuckySession.open(player, at(3, 0))));
        ShotHarness.SHOTS.add(client("h9_roulette", 4, () -> key(GLFW.GLFW_KEY_SPACE)));
        ShotHarness.SHOTS.add(client("h10_glisse", 100, () -> {
        }));
        ShotHarness.SHOTS.add(client("h11_ferme", 30, () -> Minecraft.getInstance().gui.setScreen(null)));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> log("fin ; ouverts {}", PalaLucky.data(player).opened())));
    }
}
