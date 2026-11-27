package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.client.ui.Zephyr;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.grade.GradesController;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobsMenu;
import fr.atraxterra.dohy.shop.ShopController;
import fr.atraxterra.dohy.ui.menu.BoutiqueController;
import fr.atraxterra.dohy.ui.menu.HubController;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/** Temporaire : le moteur Zephyr, puis les memes ecrans sur les textures, pour comparer. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();

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

    private static void screens(String prefix) {
        ShotHarness.SHOTS.add(server(prefix + "_1_hub", 30, player -> HubController.INSTANCE.open(player)));
        ShotHarness.SHOTS.add(server(prefix + "_2_metiers", 30, player -> JobsMenu.INSTANCE.open(player, Job.FARMER)));
        ShotHarness.SHOTS.add(server(prefix + "_3_shop", 30, ShopController::open));
        ShotHarness.SHOTS.add(server(prefix + "_4_boutique", 30, player -> BoutiqueController.INSTANCE.open(player)));
        ShotHarness.SHOTS.add(server(prefix + "_5_grades", 30, player -> GradesController.INSTANCE.open(player)));
        ShotHarness.SHOTS.add(server(prefix + "_6_hud", 30, UiNet::close));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "difficulty peaceful");
            command(player, "gamerule doDaylightCycle false");
            command(player, "kill @e[type=minecraft:slime]");
            player.setGameMode(GameType.SURVIVAL);
            Economy.set(player, 125_000L);
        }));
        ShotHarness.SHOTS.add(client("a1_moteur", 10,
                () -> LOG.info("dohy-shots: moteur Zephyr actif : {}", Zephyr.active())));
        screens("on");
        ShotHarness.SHOTS.add(client("off_0_bascule", 10, () -> {
            Zephyr.force(true);
            LOG.info("dohy-shots: moteur Zephyr coupe : {}", !Zephyr.active());
        }));
        screens("off");
    }
}
