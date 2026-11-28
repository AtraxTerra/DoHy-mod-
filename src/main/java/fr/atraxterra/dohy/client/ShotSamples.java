package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.client.botanist.CodexScreen;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.botanist.BotanistCodex;
import fr.atraxterra.dohy.palamod.botanist.BotanistPotBlockEntity;
import fr.atraxterra.dohy.palamod.botanist.GeneScannerBlock;
import fr.atraxterra.dohy.palamod.botanist.GeneScannerBlockEntity;
import fr.atraxterra.dohy.palamod.botanist.Mutation;
import fr.atraxterra.dohy.palamod.botanist.NetItem;
import fr.atraxterra.dohy.palamod.botanist.Need;
import fr.atraxterra.dohy.palamod.botanist.Nibbler;
import fr.atraxterra.dohy.palamod.botanist.PalaBotanist;
import fr.atraxterra.dohy.palamod.botanist.PotStage;
import fr.atraxterra.dohy.palamod.botanist.UnknownMutationItem;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;

/** Temporaire : le Botaniste, lot 2b (plantes, mutations, scanner, codex, nibblers). */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    /** Le coin du jardin d'essai, sur le monde plat (herbe a y = -61). */
    private static final int X = 40;
    private static final int Y = -60;
    private static final int Z = 40;

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

    private static void look(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        command(player, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
    }

    // -- Reflexion : poser une plante dans l'etat voulu ------------------------------------------

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
                                              boolean mutated) throws ReflectiveOperationException {
        ServerLevel level = (ServerLevel) player.level();
        level.setBlockAndUpdate(pos, PalaBotanist.POT.get().defaultBlockState());
        BotanistPotBlockEntity pot = (BotanistPotBlockEntity) level.getBlockEntity(pos);
        set(pot, "owner", player.getUUID());
        set(pot, "ownerName", player.getGameProfile().name());
        set(pot, "mutation", mutation);
        set(pot, "stage", stage);
        set(pot, "progress", 0);
        set(pot, "length", 20 * 60 * 15);
        set(pot, "harvestsLeft", 3);
        set(pot, "mutated", mutated);
        set(pot, "active", true);
        call(pot, "sync", new Class<?>[0]);
        return pot;
    }

    private static void scannerAt(ServerPlayer player, BlockPos pos, Direction facing) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState lower = PalaBotanist.GENE_SCANNER.get().defaultBlockState().setValue(GeneScannerBlock.FACING, facing)
                .setValue(GeneScannerBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(pos, lower, Block.UPDATE_CLIENTS);
        level.setBlock(pos.above(), lower.setValue(GeneScannerBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        try {
            set(level.getBlockEntity(pos), "owner", player.getUUID());
            set(level.getBlockEntity(pos), "ownerName", player.getGameProfile().name());
        } catch (ReflectiveOperationException e) {
            LOG.error("dohy-shots: maitre du scanner", e);
        }
    }

    private static GeneScannerBlockEntity scanner(ServerPlayer player) {
        return (GeneScannerBlockEntity) player.level().getBlockEntity(new BlockPos(X + 8, Y, Z + 14));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "difficulty peaceful");
            command(player, "gamerule doDaylightCycle false");
            command(player, "gamerule doMobSpawning false");
            player.setGameMode(GameType.CREATIVE);
            JobApi.grantLevels(player, Job.FARMER, 70);
            command(player, String.format("fill %d %d %d %d %d %d minecraft:air", X - 4, Y, Z - 4, X + 24, Y + 6, Z + 24));
            command(player, String.format("fill %d %d %d %d %d %d minecraft:grass_block", X - 4, Y - 1, Z - 4, X + 24,
                    Y - 1, Z + 24));
            command(player, "clear @a");
        }));
        // Les quinze plantes en fleur, une par pot ; les communes et rares mutees.
        ShotHarness.SHOTS.add(server("b1_quinze_plantes", 60, player -> {
            try {
                Mutation[] all = Mutation.values();
                for (int index = 0; index < all.length; index++) {
                    int column = index % 5;
                    int row = index / 5;
                    pot(player, new BlockPos(X + column * 3, Y, Z + row * 3), all[index], PotStage.BLOOMING,
                            all[index].rarity().ordinal() <= 1 && index % 2 == 0);
                }
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: pots", e);
            }
            look(player, X + 6.0, Y + 6.5, Z - 6.0, 0.0f, 32.0f);
        }));
        ShotHarness.SHOTS.add(server("b2_plantes_proches", 40, player -> look(player, X + 4.5, Y + 2.8, Z - 2.5, -20.0f,
                22.0f)));
        ShotHarness.SHOTS.add(server("b3_plantes_hautes", 40, player -> look(player, X + 7.5, Y + 2.8, Z + 3.5, 10.0f,
                20.0f)));
        // Une plante a ses quatre etats (orchidee de braise), et le pot mute de pres.
        ShotHarness.SHOTS.add(server("b4_etats", 60, player -> {
            try {
                PotStage[] stages = {PotStage.SEED, PotStage.BUD, PotStage.BLOOMING, PotStage.WILTED};
                for (int index = 0; index < 4; index++) {
                    pot(player, new BlockPos(X + 17 + index * 2, Y, Z), Mutation.EMBER_ORCHID, stages[index],
                            index == 2);
                }
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: etats", e);
            }
            look(player, X + 20.5, Y + 2.2, Z - 4.0, 0.0f, 18.0f);
        }));
        // Un besoin de nibblers : l'icone, et les nibblers poses autour.
        ShotHarness.SHOTS.add(server("c1_besoin_nibblers", 60, player -> {
            try {
                BlockPos at = new BlockPos(X + 17, Y, Z + 8);
                BotanistPotBlockEntity pot = pot(player, at, Mutation.JADE_MAW, PotStage.BUD, false);
                set(pot, "need", Need.NIBBLER);
                set(pot, "needCount", 2);
                set(pot, "needLeft", 90 * 20);
                call(pot, "spawnNibblers", new Class<?>[]{ServerLevel.class, int.class, int.class},
                        player.level(), 2, 90 * 20);
                call(pot, "sync", new Class<?>[0]);
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: nibblers", e);
            }
            player.setGameMode(GameType.SPECTATOR);
            look(player, X + 17.5, Y + 3.5, Z + 1.5, 0.0f, 28.0f);
        }));
        ShotHarness.SHOTS.add(server("c2_nibblers_proches", 30, player -> {
            List<Nibbler> nibblers = player.level().getEntitiesOfClass(Nibbler.class, player.getBoundingBox().inflate(30));
            if (!nibblers.isEmpty()) {
                Entity first = nibblers.get(0);
                look(player, first.getX() + 1.2, first.getY() + 0.9, first.getZ() + 1.2, 135.0f, 30.0f);
            }
        }));
        // Le joueur s'approche : ils paniquent (figes, en criant), puis fuient.
        ShotHarness.SHOTS.add(server("c3_nibblers_panique", -18, player -> {
            player.setGameMode(GameType.SURVIVAL);
            List<Nibbler> nibblers = player.level().getEntitiesOfClass(Nibbler.class, player.getBoundingBox().inflate(30));
            if (!nibblers.isEmpty()) {
                Entity first = nibblers.get(0);
                look(player, first.getX() + 2.0, first.getY(), first.getZ() + 2.0, 135.0f, 25.0f);
            }
        }));
        // Le filet : attraper deux nibblers, puis les relacher sur la plante.
        ShotHarness.SHOTS.add(server("c4_filet_plein", 30, player -> {
            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PalaBotanist.NET.get()));
            try {
                Method grab = Nibbler.class.getDeclaredMethod("mobInteract", Player.class, InteractionHand.class);
                grab.setAccessible(true);
                for (Nibbler nibbler : player.level().getEntitiesOfClass(Nibbler.class,
                        player.getBoundingBox().inflate(30))) {
                    grab.invoke(nibbler, player, InteractionHand.MAIN_HAND);
                }
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: attraper", e);
            }
            LOG.info("dohy-shots: filet = {}", NetItem.nibblers(player.getMainHandItem()));
            look(player, X + 17.5, Y + 1.8, Z + 6.0, 0.0f, 30.0f);
        }));
        ShotHarness.SHOTS.add(server("c5_besoin_rempli", 40, player -> {
            try {
                BotanistPotBlockEntity pot = (BotanistPotBlockEntity) player.level()
                        .getBlockEntity(new BlockPos(X + 17, Y, Z + 8));
                Method answer = BotanistPotBlockEntity.class.getDeclaredMethod("answer", ServerPlayer.class,
                        ItemStack.class, InteractionHand.class);
                answer.setAccessible(true);
                answer.invoke(pot, player, player.getMainHandItem(), InteractionHand.MAIN_HAND);
                LOG.info("dohy-shots: besoin apres le filet = {}", pot.need());
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: relacher", e);
            }
        }));
        // Le scanner de Genis, pose et au repos.
        ShotHarness.SHOTS.add(server("d1_scanner", 60, player -> {
            scannerAt(player, new BlockPos(X + 8, Y, Z + 14), Direction.SOUTH);
            look(player, X + 8.5, Y + 1.8, Z + 18.5, 180.0f, 12.0f);
        }));
        ShotHarness.SHOTS.add(server("d2_scanner_cote", 30, player -> look(player, X + 11.0, Y + 2.2, Z + 17.0, 135.0f,
                18.0f)));
        // Une mutation inconnue et du charbon : le scanner attend, la graine posee dessus.
        ShotHarness.SHOTS.add(server("d3_scanner_pret", 40, player -> {
            GeneScannerBlockEntity scanner = scanner(player);
            scanner.setItem(GeneScannerBlockEntity.SLOT_INPUT, UnknownMutationItem.create(Mutation.GLOWBLOOM));
            scanner.setItem(GeneScannerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 12));
            BotanistCodex.discover(player, Mutation.IRONWHEAT);
            BotanistCodex.discover(player, Mutation.MOONFROST);
            look(player, X + 8.5, Y + 1.8, Z + 17.0, 180.0f, 14.0f);
        }));
        ShotHarness.SHOTS.add(server("d4_ecran_pret", 40, player -> player.openMenu(scanner(player))));
        ShotHarness.SHOTS.add(server("d5_ecran_scan", 60, player -> {
            boolean pressed = scanner(player).pressButton(player, GeneScannerBlockEntity.BUTTON_SCAN);
            LOG.info("dohy-shots: scan demande = {}", pressed);
        }));
        ShotHarness.SHOTS.add(server("d6_ecran_scan_avance", 40, player -> {
            try {
                set(scanner(player), "progress", 20 * 25);
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: avance", e);
            }
        }));
        // Le scan vu dans le monde (l'animation etiree), puis le resultat.
        ShotHarness.SHOTS.add(server("d7_scanner_scan_monde", 30, player -> {
            player.closeContainer();
            look(player, X + 8.5, Y + 1.8, Z + 17.0, 180.0f, 14.0f);
        }));
        ShotHarness.SHOTS.add(server("d8_ecran_resultat", 60, player -> {
            GeneScannerBlockEntity scanner = scanner(player);
            try {
                set(scanner, "progress", Math.max(0, scanner.scanDuration() - 10));
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: fin du scan", e);
            }
            player.openMenu(scanner);
        }));
        ShotHarness.SHOTS.add(server("d9_scanner_termine", 40, player -> {
            player.closeContainer();
            look(player, X + 8.5, Y + 1.8, Z + 17.0, 180.0f, 14.0f);
        }));
        // Une graine deja dans le codex : refusee.
        ShotHarness.SHOTS.add(server("dz_ecran_deja_connue", 40, player -> {
            GeneScannerBlockEntity scanner = scanner(player);
            scanner.setItem(GeneScannerBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            scanner.setItem(GeneScannerBlockEntity.SLOT_INPUT, new ItemStack(PalaBotanist.IRONWHEAT_SEED.get()));
            player.openMenu(scanner);
        }));
        // Le codex : quelques plantes decouvertes, puis une inconnue choisie.
        ShotHarness.SHOTS.add(server("e0_decouvertes", 20, player -> {
            player.closeContainer();
            for (Mutation mutation : List.of(Mutation.SAND_CORAL, Mutation.PUFFBLOOM, Mutation.THORNWICK,
                    Mutation.JADE_MAW, Mutation.EMBER_ORCHID)) {
                BotanistCodex.discover(player, mutation);
            }
        }));
        ShotHarness.SHOTS.add(client("e1_codex", 40, () -> Minecraft.getInstance().gui.setScreen(new CodexScreen())));
        ShotHarness.SHOTS.add(client("e2_codex_inconnue", 40, () -> {
            CodexScreen screen = new CodexScreen();
            try {
                Field selected = CodexScreen.class.getDeclaredField("selected");
                selected.setAccessible(true);
                selected.set(screen, Mutation.ARCANIUM);
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: codex", e);
            }
            Minecraft.getInstance().gui.setScreen(screen);
        }));
        ShotHarness.SHOTS.add(client("e3_codex_legendaire", 40, () -> {
            CodexScreen screen = new CodexScreen();
            try {
                Field selected = CodexScreen.class.getDeclaredField("selected");
                selected.setAccessible(true);
                selected.set(screen, Mutation.EMBER_ORCHID);
                Field variant = CodexScreen.class.getDeclaredField("variant");
                variant.setAccessible(true);
                variant.set(screen, 2);
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: codex", e);
            }
            Minecraft.getInstance().gui.setScreen(screen);
        }));
        // La recolte d'une fleur mutee : une mutation inconnue tombe, la plante disparait.
        ShotHarness.SHOTS.add(client("f0_fermer", 10, () -> Minecraft.getInstance().gui.setScreen(null)));
        ShotHarness.SHOTS.add(server("f1_recolte_mutee", 40, player -> {
            try {
                BotanistPotBlockEntity pot = (BotanistPotBlockEntity) player.level().getBlockEntity(new BlockPos(X, Y, Z));
                set(pot, "owner", player.getUUID());
                Method use = BotanistPotBlockEntity.class.getDeclaredMethod("use", ServerPlayer.class, boolean.class);
                use.setAccessible(true);
                use.invoke(pot, player, true);
                LOG.info("dohy-shots: apres recolte, stade = {}", pot.stage());
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: recolte", e);
            }
            look(player, X + 0.5, Y + 2.2, Z - 2.5, 0.0f, 35.0f);
        }));
    }
}
