package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.client.pet.PetScreen;
import fr.atraxterra.dohy.client.pet.PetScreenOld;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/** Temporaire : l'ecran du familier, avant et apres son passage dans la coque. */
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

    /** Une commande tapee par le joueur lui-meme. */
    private static void as(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    /** L'ancien ecran, sur la meme vue. */
    private static void old() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof PetScreen screen) {
            minecraft.gui.setScreen(new PetScreenOld(screen.shotView()));
        } else {
            LOG.error("dohy-shots: ECHEC pas d'ecran du familier : " + minecraft.gui.screen());
        }
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "difficulty peaceful");
            command(player, "gamerule doDaylightCycle false");
            command(player, "kill @e[type=minecraft:slime]");
            command(player, "execute as @p run pet admin reset");
            player.setGameMode(GameType.SURVIVAL);
            Economy.set(player, 125_000L);
        }));
        ShotHarness.SHOTS.add(server("p1_sans", 30, player -> {
            UiNet.close(player);
            as(player, "pet");
        }));
        ShotHarness.SHOTS.add(client("p2_sans_avant", 20, ShotSamples::old));
        ShotHarness.SHOTS.add(server("p3_avec", 40, player -> {
            UiNet.close(player);
            command(player, "execute as @p run pet admin tame fox_patronus");
            command(player, "execute as @p run pet admin xp 30000");
            command(player, "execute as @p run pet admin happiness 150");
            as(player, "pet");
        }));
        ShotHarness.SHOTS.add(client("p4_avec_avant", 20, ShotSamples::old));
        ShotHarness.SHOTS.add(server("p5_avec_choix", 30, player -> {
            UiNet.close(player);
            as(player, "pet");
        }));
        ShotHarness.SHOTS.add(client("p6_actives", 20, () -> {
            if (Minecraft.getInstance().gui.screen() instanceof PetScreen screen) {
                screen.shotSelect(2, true);
            }
        }));
        ShotHarness.SHOTS.add(server("p7_hub", 30, player -> {
            UiNet.close(player);
            as(player, "menu");
        }));
    }
}
