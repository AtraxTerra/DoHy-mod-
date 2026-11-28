package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/** Temporaire : les notifications du jeu au style du kit. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();

    private static TutorialToast tutorial;

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

    /** Une pause sans action : la notification precedente repart avant la suivante. */
    private static void pause(int index) {
        ShotHarness.SHOTS.add(client("p" + index + "_pause", 90, () -> {
        }));
    }

    private static ToastManager toasts() {
        return Minecraft.getInstance().getToastManager();
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "difficulty peaceful");
            player.setGameMode(GameType.SURVIVAL);
        }));
        ShotHarness.SHOTS.add(server("t1_progres", 40,
                player -> command(player, "advancement grant @a only minecraft:story/mine_stone")));
        pause(1);
        ShotHarness.SHOTS.add(server("t2_objectif", 40,
                player -> command(player, "advancement grant @a only minecraft:end/respawn_dragon")));
        pause(2);
        ShotHarness.SHOTS.add(server("t3_defi", 40,
                player -> command(player, "advancement grant @a only minecraft:adventure/adventuring_time")));
        pause(3);
        ShotHarness.SHOTS.add(server("t4_recette", 40,
                player -> command(player, "recipe give @a minecraft:diamond_pickaxe")));
        pause(4);
        ShotHarness.SHOTS.add(client("t5_tutoriel", 40, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            tutorial = new TutorialToast(minecraft.font, TutorialToast.Icons.MOVEMENT_KEYS,
                    Component.translatable("tutorial.move.title", Component.literal("Z"), Component.literal("Q"),
                            Component.literal("S"), Component.literal("D")),
                    Component.translatable("tutorial.move.description", Component.literal("Espace")), true);
            toasts().addToast(tutorial);
            tutorial.updateProgress(0.45f);
        }));
        pause(5);
        ShotHarness.SHOTS.add(client("t6_systeme", 40, () -> {
            tutorial.hide();
            SystemToast.add(toasts(), SystemToast.SystemToastId.WORLD_BACKUP,
                    Component.translatable("selectWorld.edit.backupCreated", "shots"),
                    Component.translatable("selectWorld.edit.backupSize", 12));
        }));
        pause(6);
        ShotHarness.SHOTS.add(client("t7_echec", 40, () -> SystemToast.add(toasts(),
                SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                Component.translatable("resourcePack.load_fail"),
                Component.literal("Le pack exemple n'a pas pu etre charge."))));
        pause(7);
        ShotHarness.SHOTS.add(server("t8_pile", 60, player -> {
            command(player, "advancement grant @a only minecraft:story/smelt_iron");
            command(player, "recipe give @a minecraft:golden_apple");
            command(player, "advancement grant @a only minecraft:story/enter_the_nether");
        }));
        pause(8);
        ShotHarness.SHOTS.add(client("t9_sur_inventaire", 40, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gui.setScreen(new KitInventoryScreen(minecraft.player));
        }));
        ShotHarness.SHOTS.add(server("t10_sur_inventaire_progres", 40,
                player -> command(player, "advancement grant @a only minecraft:story/iron_tools")));
    }
}
