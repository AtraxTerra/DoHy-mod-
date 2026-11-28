package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.client.ui.screen.MenuScreen;
import fr.atraxterra.dohy.palamod.dungeon.Dungeon;
import fr.atraxterra.dohy.palamod.dungeon.DungeonManager;
import fr.atraxterra.dohy.palamod.dungeon.DungeonMenus;
import fr.atraxterra.dohy.palamod.dungeon.DungeonMode;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeon;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/** Temporaire : l'Aventurier (conquete d'un palier, note, ecran des paliers). */
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

    /** Un menu ouvert par le serveur ; la souris est replacee pendant une seconde (l'ecran la recentre). */
    private static ShotHarness.Shot serverMenu(String name, int extra, Consumer<ServerPlayer> action) {
        ShotHarness.Shot base = server(name, extra, action);
        return new ShotHarness.Shot(name, () -> {
            net.minecraft.client.gui.screens.Screen opened = base.screen().get();
            int[] parked = {0};
            ShotHarness.until = () -> {
                if (Minecraft.getInstance().gui.screen() instanceof MenuScreen) {
                    cursor(4.0d, 4.0d);
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

    /** Lance un palier sans cle ({@code /donjon admin lancer}). */
    private static void launch(ServerPlayer player, int palier) {
        try {
            Method start = DungeonManager.class.getDeclaredMethod("start", ServerPlayer.class, Dungeon.class,
                    int.class, InteractionHand.class, DungeonMode.class, boolean.class);
            start.setAccessible(true);
            Object started = start.invoke(null, player, Dungeon.TALIKUS, palier, InteractionHand.MAIN_HAND,
                    DungeonMode.NORMAL, true);
            LOG.info("dohy-shots: palier {} lance = {}", palier, started);
        } catch (ReflectiveOperationException e) {
            LOG.error("dohy-shots: lancer", e);
        }
    }

    /** La porte de sortie : le palier reussi, tel quel. */
    private static void succeed(ServerPlayer player) {
        try {
            // La partie est d'une classe du paquet des donjons : tout passe par la reflexion.
            Object run = DungeonManager.class.getMethod("runOf", ServerPlayer.class).invoke(null, player);
            if (run == null) {
                LOG.error("dohy-shots: pas de partie en cours");
                return;
            }
            Method succeed = run.getClass().getDeclaredMethod("succeed");
            succeed.setAccessible(true);
            succeed.invoke(run);
            LOG.info("dohy-shots: conquis = {}", DungeonManager.conquered(player, Dungeon.TALIKUS));
        } catch (ReflectiveOperationException e) {
            LOG.error("dohy-shots: reussite", e);
        }
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 60, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "difficulty peaceful");
            player.setGameMode(GameType.CREATIVE);
            // Talikus : douze paliers deja reussis en Normal, trois en Facile.
            Map<String, Integer> progress = new HashMap<>();
            progress.put(DungeonMode.NORMAL.progressKey(Dungeon.TALIKUS), 12);
            progress.put(DungeonMode.FACILE.progressKey(Dungeon.TALIKUS), 3);
            player.setData(PalaDungeon.PROGRESS.get(), progress);
        }));
        ShotHarness.SHOTS.add(serverMenu("b1_paliers", 40,
                player -> DungeonMenus.openPaliers(player, Dungeon.TALIKUS, InteractionHand.MAIN_HAND)));
        ShotHarness.SHOTS.add(client("c0_fermer", 10, () -> Minecraft.getInstance().gui.setScreen(null)));
        // Le palier 13 : jamais reussi, il se conquiert.
        ShotHarness.SHOTS.add(server("c1_lancer", 200, player -> launch(player, 13)));
        ShotHarness.SHOTS.add(server("c2_conquete", -10, ShotSamples::succeed));
        ShotHarness.SHOTS.add(server("c3_conquete_chat", 40, player -> {
        }));
        // Le meme palier rejoue : reussi, plus conquis.
        ShotHarness.SHOTS.add(server("c4_relancer", 200, player -> launch(player, 13)));
        ShotHarness.SHOTS.add(server("c5_reussite", -10, ShotSamples::succeed));
        ShotHarness.SHOTS.add(serverMenu("d1_paliers_apres", 40,
                player -> DungeonMenus.openPaliers(player, Dungeon.TALIKUS, InteractionHand.MAIN_HAND)));
    }
}
