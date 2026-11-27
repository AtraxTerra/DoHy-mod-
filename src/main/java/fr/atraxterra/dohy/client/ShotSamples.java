package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.client.atlas.AtlasScreen;
import fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen;
import fr.atraxterra.dohy.client.ui.vanilla.KitPauseScreen;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/** Temporaire : le tour des ecrans, avant leur epuration. */
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

    private static void typed(String name, String command) {
        ShotHarness.SHOTS.add(server(name, 30, player -> {
            UiNet.close(player);
            as(player, command);
        }));
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
        ShotHarness.SHOTS.add(client("b1_pause", 20, () -> Minecraft.getInstance().gui.setScreen(new KitPauseScreen())));
        ShotHarness.SHOTS.add(client("b2_inventaire", 20,
                () -> Minecraft.getInstance().gui.setScreen(new KitInventoryScreen(Minecraft.getInstance().player))));
        ShotHarness.SHOTS.add(client("b3_atlas", 60, () -> Minecraft.getInstance().gui.setScreen(new AtlasScreen())));
        ShotHarness.SHOTS.add(client("b4_ferme", 10, () -> Minecraft.getInstance().gui.setScreen(null)));
        typed("c01_pet", "pet");
        typed("c02_profil", "profil");
        typed("c03_luckypass", "luckypass");
        typed("c04_pass", "pass");
        typed("c05_rapport", "rapport");
        typed("c06_clicker", "clicker");
        typed("c07_casino", "casino");
        typed("c08_calendrier", "calendrier");
        typed("c09_bank", "bank");
        typed("c10_museum", "museum");
        typed("c11_collections", "collections");
        typed("c12_contracts", "contracts");
        typed("c13_trixium", "trixium");
        typed("c14_faction", "f");
        typed("c15_entreprises", "entreprises");
        typed("c16_mines", "mines");
        typed("c17_bots", "bots");
        typed("c18_kit", "kit");
        typed("c19_boutique", "boutique");
        typed("c20_hdv", "hdv");
        typed("c21_unesco", "unesco");
        typed("c22_quetes", "quetes");
        typed("c23_donjon", "donjon");
        typed("c24_techno", "techno");
    }
}
