package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.client.ui.screen.MenuScreen;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.botanist.BotanistLab;
import fr.atraxterra.dohy.palamod.botanist.BotanistPotBlockEntity;
import fr.atraxterra.dohy.palamod.botanist.CentrifugeBlockEntity;
import fr.atraxterra.dohy.palamod.botanist.LabControlMenu;
import fr.atraxterra.dohy.palamod.botanist.Mutation;
import fr.atraxterra.dohy.palamod.botanist.Need;
import fr.atraxterra.dohy.palamod.botanist.PalaBotanist;
import fr.atraxterra.dohy.palamod.botanist.PotStage;
import fr.atraxterra.dohy.palamod.botanist.SeedReserve;
import fr.atraxterra.dohy.palamod.botanist.SeedReserveBlock;
import fr.atraxterra.dohy.palamod.botanist.TallMachineBlock;
import fr.atraxterra.dohy.palamod.botanist.WideMachineBlock;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Temporaire : le Botaniste, lot 2c (centrifugeuse, reserve de graines, centre de controle). */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    /** Le coin du laboratoire d'essai, sur le monde plat (herbe a y = -61). */
    private static final int X = 40;
    private static final int Y = -60;
    private static final int Z = 40;
    private static final BlockPos CENTRIFUGE = new BlockPos(X + 2, Y, Z + 4);
    private static final BlockPos RESERVE = new BlockPos(X + 6, Y, Z + 4);
    private static final BlockPos CONTROL = new BlockPos(X + 11, Y, Z + 4);

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

    /** Une capture d'un menu ouvert par le serveur ; la souris va ou on le dit, une fois l'ecran ouvert. */
    private static ShotHarness.Shot serverMenu(String name, int extra, Class<?> screen, double mouseX, double mouseY,
                                               Consumer<ServerPlayer> action) {
        ShotHarness.Shot base = server(name, extra, action);
        return new ShotHarness.Shot(name, () -> {
            net.minecraft.client.gui.screens.Screen opened = base.screen().get();
            // Un ecran qui s'ouvre recentre la souris : on la replace a chaque tick pendant une seconde.
            int[] parked = {0};
            ShotHarness.until = () -> {
                if (screen.isInstance(Minecraft.getInstance().gui.screen())) {
                    cursor(mouseX, mouseY);
                    return ++parked[0] >= 20;
                }
                return false;
            };
            return opened;
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

    private static void look(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        command(player, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
    }

    /** Place la souris (en pixels de la fenetre), pour qu'aucune bulle ne cache l'ecran, ou pour en montrer une. */
    private static void cursor(double x, double y) {
        var handler = Minecraft.getInstance().mouseHandler;
        try {
            var xpos = handler.getClass().getDeclaredField("xpos");
            var ypos = handler.getClass().getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(handler, x);
            ypos.setDouble(handler, y);
        } catch (ReflectiveOperationException e) {
            LOG.warn("dohy-shots: souris non deplacee", e);
        }
    }

    // -- Reflexion ---------------------------------------------------------------------------------

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static BotanistPotBlockEntity pot(ServerPlayer player, BlockPos pos, Mutation mutation, PotStage stage,
                                              boolean mutated, int permille) throws ReflectiveOperationException {
        ServerLevel level = (ServerLevel) player.level();
        level.setBlockAndUpdate(pos, PalaBotanist.POT.get().defaultBlockState());
        BotanistPotBlockEntity pot = (BotanistPotBlockEntity) level.getBlockEntity(pos);
        int length = 20 * 60 * 15;
        set(pot, "owner", player.getUUID());
        set(pot, "ownerName", player.getGameProfile().name());
        set(pot, "mutation", mutation);
        set(pot, "stage", stage);
        set(pot, "progress", length * permille / 1000);
        set(pot, "length", length);
        set(pot, "harvestsLeft", 3);
        set(pot, "mutated", mutated);
        set(pot, "active", true);
        call(pot, "sync", new Class<?>[0]);
        return pot;
    }

    private static void need(BotanistPotBlockEntity pot, Need need, int count) throws ReflectiveOperationException {
        set(pot, "need", need);
        set(pot, "needCount", count);
        set(pot, "needLeft", Need.TIMEOUT);
        call(pot, "sync", new Class<?>[0]);
    }

    /** Pose une machine comme un joueur : la partie principale, puis les autres ({@code setPlacedBy}). */
    private static void place(ServerPlayer player, Block block, BlockPos pos, BlockState state) {
        ServerLevel level = (ServerLevel) player.level();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        block.setPlacedBy(level, pos, state, player, new ItemStack(block.asItem()));
    }

    private static CentrifugeBlockEntity centrifuge(ServerPlayer player) {
        return (CentrifugeBlockEntity) player.level().getBlockEntity(CENTRIFUGE);
    }

    private static void openReserve(ServerPlayer player) {
        try {
            Method open = SeedReserveBlock.class.getDeclaredMethod("open", ServerPlayer.class, BlockEntity.class);
            open.setAccessible(true);
            open.invoke(PalaBotanist.SEED_RESERVE_BLOCK.get(), player, player.level().getBlockEntity(RESERVE));
        } catch (ReflectiveOperationException e) {
            LOG.error("dohy-shots: reserve", e);
        }
    }

    private static void module(ServerPlayer player, String key) {
        LabControlMenu.INSTANCE.handle(player, new UiNet.Action(LabControlMenu.INSTANCE.id(), "card", "module:" + key, 0));
    }

    /** Le panneau des machines (262 x 236) au centre ; l'interface en x4 sur 1920 x 1080 : gauche 109, haut 17. */
    private static double px(int panelX) {
        return (109 + panelX) * 4.0d;
    }

    private static double py(int panelY) {
        return (17 + panelY) * 4.0d;
    }

    private static void seeds(ServerPlayer player, Mutation mutation, int count) {
        player.getInventory().add(new ItemStack(PalaBotanist.MUTATION_SEEDS.get(mutation).get(), count));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "difficulty peaceful");
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            JobApi.grantLevels(player, Job.FARMER, 70);
            Economy.credit(player, 2_000_000L);
            command(player, "clear @a");
            // D'abord au laboratoire : ses chunks se chargent avant qu'on y pose quoi que ce soit.
            look(player, X + 7.5, Y + 2.6, Z + 11.5, 180.0f, 14.0f);
        }));
        ShotHarness.SHOTS.add(server("a1_terrain", 20, player -> {
            command(player, String.format("fill %d %d %d %d %d %d minecraft:air", X - 4, Y, Z - 4, X + 24, Y + 6, Z + 24));
            command(player, String.format("fill %d %d %d %d %d %d minecraft:grass_block", X - 4, Y - 1, Z - 4, X + 24,
                    Y - 1, Z + 24));
        }));
        // Les trois machines, posees face au sud comme par un joueur.
        ShotHarness.SHOTS.add(server("b1_machines", 60, player -> {
            Block centrifuge = PalaBotanist.MUTATION_CENTRIFUGE.get();
            place(player, centrifuge, CENTRIFUGE, centrifuge.defaultBlockState()
                    .setValue(TallMachineBlock.FACING, Direction.SOUTH));
            Block reserve = PalaBotanist.SEED_RESERVE_BLOCK.get();
            place(player, reserve, RESERVE, reserve.defaultBlockState().setValue(WideMachineBlock.FACING,
                    Direction.SOUTH));
            Block control = PalaBotanist.LAB_CONTROL.get();
            place(player, control, CONTROL, control.defaultBlockState().setValue(WideMachineBlock.FACING,
                    Direction.SOUTH));
            look(player, X + 7.5, Y + 2.6, Z + 11.5, 180.0f, 14.0f);
        }));
        ShotHarness.SHOTS.add(server("b2_centrifugeuse", 30, player -> look(player, X + 2.5, Y + 0.6, Z + 7.6, 180.0f,
                8.0f)));
        ShotHarness.SHOTS.add(server("b3_reserve", 30, player -> look(player, X + 7.0, Y + 0.7, Z + 8.2, 180.0f,
                6.0f)));
        ShotHarness.SHOTS.add(server("b4_centre", 30, player -> look(player, X + 12.0, Y + 0.7, Z + 8.2, 180.0f,
                6.0f)));
        ShotHarness.SHOTS.add(server("b5_trois_quarts", 30, player -> look(player, X + 13.5, Y + 2.2, Z + 10.5,
                150.0f, 14.0f)));
        ShotHarness.SHOTS.add(server("b6_dos", 30, player -> look(player, X + 7.5, Y + 2.2, Z - 3.0, 0.0f, 14.0f)));
        // Un autre sens : la centrifugeuse et le centre de controle face a l'est.
        ShotHarness.SHOTS.add(server("b7_face_est", 60, player -> {
            Block centrifuge = PalaBotanist.MUTATION_CENTRIFUGE.get();
            place(player, centrifuge, new BlockPos(X + 18, Y, Z + 2), centrifuge.defaultBlockState()
                    .setValue(TallMachineBlock.FACING, Direction.EAST));
            Block control = PalaBotanist.LAB_CONTROL.get();
            place(player, control, new BlockPos(X + 18, Y, Z + 6), control.defaultBlockState()
                    .setValue(WideMachineBlock.FACING, Direction.EAST));
            look(player, X + 24.0, Y + 2.0, Z + 5.0, 90.0f, 12.0f);
        }));

        // -- La centrifugeuse ---------------------------------------------------------------------
        // Le menu se ferme a plus de huit blocs : on se place devant la machine d'abord.
        ShotHarness.SHOTS.add(serverMenu("c1_ecran_vide", 40, AbstractScreens.CONTAINER, 4.0d, 4.0d, player -> {
            command(player, "time set noon");
            look(player, X + 2.5, Y + 0.6, Z + 7.6, 180.0f, 8.0f);
            player.openMenu(centrifuge(player));
        }));
        ShotHarness.SHOTS.add(serverMenu("c2_ecran_pret", 40, AbstractScreens.CONTAINER, 4.0d, 4.0d, player -> {
            CentrifugeBlockEntity machine = centrifuge(player);
            machine.setItem(CentrifugeBlockEntity.SLOT_SEED, new ItemStack(PalaBotanist.MUTATION_SEEDS
                    .get(Mutation.GLOWBLOOM).get()));
            machine.setItem(CentrifugeBlockEntity.FIRST_ITEM, new ItemStack(Items.DIAMOND, 3));
            machine.setItem(CentrifugeBlockEntity.FIRST_ITEM + 1, new ItemStack(PalaItems.PALADIUM_INGOT.get(), 5));
            machine.setItem(CentrifugeBlockEntity.FIRST_ITEM + 2, new ItemStack(Items.EMERALD, 2));
            machine.setItem(CentrifugeBlockEntity.FIRST_ITEM + 4, new ItemStack(Items.REDSTONE, 16));
            machine.setItem(CentrifugeBlockEntity.FIRST_ITEM + 5, new ItemStack(Items.BONE_MEAL, 8));
            player.openMenu(machine);
        }));
        // La bulle « Objets acceptes », sur le coeur.
        ShotHarness.SHOTS.add(client("c3_bulle_objets", 20, () -> cursor(px(154), py(71))));
        ShotHarness.SHOTS.add(serverMenu("c4_ecran_centrifugation", 30, AbstractScreens.CONTAINER, 4.0d, 4.0d,
                player -> {
                    boolean pressed = centrifuge(player).pressButton(player, CentrifugeBlockEntity.BUTTON_START);
                    LOG.info("dohy-shots: centrifugation demandee = {}", pressed);
                }));
        ShotHarness.SHOTS.add(server("c5_monde_centrifugation", 20, player -> {
            player.closeContainer();
            look(player, X + 2.5, Y + 0.6, Z + 7.6, 180.0f, 8.0f);
        }));
        ShotHarness.SHOTS.add(server("c6_monde_fin", 0, player -> {
            try {
                set(centrifuge(player), "progress", CentrifugeBlockEntity.DURATION - 5);
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: fin", e);
            }
        }));
        ShotHarness.SHOTS.add(serverMenu("c7_ecran_resultat", 40, AbstractScreens.CONTAINER, 4.0d, 4.0d, player -> {
            LOG.info("dohy-shots: resultat = {}", centrifuge(player).getItem(CentrifugeBlockEntity.SLOT_RESULT));
            player.openMenu(centrifuge(player));
        }));
        // Cinq pepites d'endium organique : 100 %, la graine boostee.
        ShotHarness.SHOTS.add(serverMenu("c8_ecran_boost", 40, AbstractScreens.CONTAINER, 4.0d, 4.0d, player -> {
            CentrifugeBlockEntity machine = centrifuge(player);
            machine.setItem(CentrifugeBlockEntity.SLOT_RESULT, ItemStack.EMPTY);
            machine.setItem(CentrifugeBlockEntity.SLOT_SEED, new ItemStack(PalaBotanist.MUTATION_SEEDS
                    .get(Mutation.SUNBLOOM).get(), 2));
            for (int slot = 0; slot < CentrifugeBlockEntity.ITEMS; slot++) {
                machine.setItem(CentrifugeBlockEntity.FIRST_ITEM + slot, new ItemStack(PalaBotanist.ORGANIC_ENDIUM.get(),
                        4));
            }
            player.openMenu(machine);
        }));
        ShotHarness.SHOTS.add(server("c9_monde_boost", 40, player -> {
            centrifuge(player).pressButton(player, CentrifugeBlockEntity.BUTTON_START);
            player.closeContainer();
            look(player, X + 2.5, Y + 0.6, Z + 7.6, 180.0f, 8.0f);
        }));
        ShotHarness.SHOTS.add(server("ca_monde_boost_fin", 0, player -> {
            try {
                set(centrifuge(player), "progress", CentrifugeBlockEntity.DURATION - 5);
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: fin boostee", e);
            }
        }));
        ShotHarness.SHOTS.add(serverMenu("cb_ecran_boost_resultat", 40, AbstractScreens.CONTAINER, 4.0d, 4.0d,
                player -> player.openMenu(centrifuge(player))));
        // L'onglet BOOST : « Bientot disponible ».
        ShotHarness.SHOTS.add(client("cc_bulle_boost", 20, () -> cursor(px(233), py(18))));

        // -- La reserve de graines -----------------------------------------------------------------
        ShotHarness.SHOTS.add(serverMenu("d1_reserve_vide", 40, AbstractScreens.CONTAINER, 4.0d, 4.0d, player -> {
            player.closeContainer();
            command(player, "time set noon");
            look(player, X + 7.0, Y + 0.7, Z + 8.2, 180.0f, 6.0f);
            openReserve(player);
        }));
        ShotHarness.SHOTS.add(serverMenu("d2_reserve_remplie", 60, AbstractScreens.CONTAINER, 4.0d, 4.0d, player -> {
            player.closeContainer();
            SeedReserve.Storage storage = new SeedReserve.Storage(player, RESERVE);
            storage.setItem(Mutation.IRONWHEAT.ordinal(), new ItemStack(PalaBotanist.MUTATION_SEEDS
                    .get(Mutation.IRONWHEAT).get(), 10));
            storage.setItem(Mutation.GLOWBLOOM.ordinal(), new ItemStack(PalaBotanist.MUTATION_SEEDS
                    .get(Mutation.GLOWBLOOM).get(), 3));
            storage.setItem(Mutation.MOONFROST.ordinal(), new ItemStack(PalaBotanist.MUTATION_SEEDS
                    .get(Mutation.MOONFROST).get(), 7));
            storage.setItem(Mutation.AURELYS.ordinal(), new ItemStack(PalaBotanist.MUTATION_SEEDS
                    .get(Mutation.AURELYS).get(), 1));
            storage.setItem(Mutation.EMBER_ORCHID.ordinal(), new ItemStack(PalaBotanist.MUTATION_SEEDS
                    .get(Mutation.EMBER_ORCHID).get(), 2));
            seeds(player, Mutation.SAND_CORAL, 12);
            seeds(player, Mutation.ARCANIUM, 4);
            LOG.info("dohy-shots: reserve = {}", SeedReserve.counts(player));
            openReserve(player);
        }));
        // La bulle d'une case : la graine de ble de fer, sa recompense deja recue.
        ShotHarness.SHOTS.add(client("d3_bulle_case", 20, () -> cursor(px(75), py(56))));
        ShotHarness.SHOTS.add(client("d4_bulle_case_vide", 20, () -> cursor(px(103), py(56))));
        ShotHarness.SHOTS.add(server("d5_reserve_monde", 30, player -> {
            player.closeContainer();
            look(player, X + 7.0, Y + 0.7, Z + 8.2, 180.0f, 6.0f);
        }));

        // -- Le centre de controle ------------------------------------------------------------------
        // Des pots des trois climats : en fleur, mutes, en bourgeon avec un besoin.
        ShotHarness.SHOTS.add(server("e0_pots", 60, player -> {
            command(player, "time set noon");
            try {
                pot(player, new BlockPos(X + 1, Y, Z + 14), Mutation.IRONWHEAT, PotStage.BLOOMING, false, 300);
                pot(player, new BlockPos(X + 3, Y, Z + 14), Mutation.GLOWBLOOM, PotStage.BLOOMING, true, 600);
                need(pot(player, new BlockPos(X + 5, Y, Z + 14), Mutation.PUFFBLOOM, PotStage.BUD, false, 450),
                        Need.WATER, 1);
                pot(player, new BlockPos(X + 7, Y, Z + 14), Mutation.JADE_MAW, PotStage.SEED, false, 150);
                pot(player, new BlockPos(X + 9, Y, Z + 14), Mutation.MOONFROST, PotStage.BUD, false, 700);
                need(pot(player, new BlockPos(X + 11, Y, Z + 14), Mutation.SONGLILY, PotStage.BUD, false, 250),
                        Need.MUSIC, 1);
                pot(player, new BlockPos(X + 13, Y, Z + 14), Mutation.SAND_CORAL, PotStage.WILTED, false, 100);
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: pots", e);
            }
            look(player, X + 7.0, Y + 3.0, Z + 20.0, 180.0f, 22.0f);
        }));
        ShotHarness.SHOTS.add(serverMenu("e1_centre_normal", 60, MenuScreen.class, 4.0d, 4.0d, player -> {
            // A portee du centre : ses actions verifient la distance.
            look(player, X + 12.0, Y + 0.7, Z + 8.2, 180.0f, 6.0f);
            LabControlMenu.INSTANCE.openAt(player, CONTROL);
        }));
        ShotHarness.SHOTS.add(serverMenu("e2_centre_glacial", 40, MenuScreen.class, 4.0d, 4.0d,
                player -> module(player, "glacial")));
        ShotHarness.SHOTS.add(serverMenu("e3_centre_desert", 40, MenuScreen.class, 4.0d, 4.0d,
                player -> module(player, "desert")));
        // Le boost du jour, puis la premiere amelioration (en construction).
        ShotHarness.SHOTS.add(serverMenu("e4_centre_boost", 40, MenuScreen.class, 4.0d, 4.0d, player -> {
            player.getInventory().add(new ItemStack(Items.BONE_MEAL, 64));
            module(player, "normal");
            LOG.info("dohy-shots: boost = {}", BotanistLab.boost(player, BotanistLab.Module.NORMAL));
            module(player, "normal");
        }));
        ShotHarness.SHOTS.add(serverMenu("e5_centre_amelioration", 40, MenuScreen.class, 4.0d, 4.0d, player -> {
            LOG.info("dohy-shots: amelioration = {}", BotanistLab.upgrade(player, BotanistLab.Module.NORMAL));
            module(player, "normal");
        }));
        ShotHarness.SHOTS.add(serverMenu("e6_centre_glacial_cout", 40, MenuScreen.class, 4.0d, 4.0d,
                player -> module(player, "glacial")));
        // Dans le monde : l'alerte (besoins en attente), puis la construction.
        ShotHarness.SHOTS.add(client("e7_fermer", 10, () -> Minecraft.getInstance().gui.setScreen(null)));
        ShotHarness.SHOTS.add(server("e8_centre_alerte", 60, player -> {
            command(player, "time set noon");
            look(player, X + 12.0, Y + 0.7, Z + 8.2, 180.0f, 6.0f);
        }));
        ShotHarness.SHOTS.add(server("e9_centre_construction", 80, player -> {
            try {
                for (int x : new int[]{X + 5, X + 11}) {
                    BotanistPotBlockEntity pot = (BotanistPotBlockEntity) player.level()
                            .getBlockEntity(new BlockPos(x, Y, Z + 14));
                    call(pot, "clearNeed", new Class<?>[0]);
                    call(pot, "sync", new Class<?>[0]);
                }
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: besoins", e);
            }
        }));
        ShotHarness.SHOTS.add(server("ea_centre_construction_bis", 40, player -> look(player, X + 13.0, Y + 1.6,
                Z + 7.0, 160.0f, 16.0f)));
    }

    /** Les classes d'ecran attendues. */
    private static final class AbstractScreens {
        static final Class<?> CONTAINER = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class;
    }
}
