package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.job.JobRebirth;
import fr.atraxterra.dohy.job.JobsMenu;
import fr.atraxterra.dohy.ui.menu.HubController;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/** Temporaire : la renaissance des metiers. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static long firstCycle;

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

    private static void log(String format, Object... args) {
        LOG.info("dohy-shots: [renaissance] " + format, args);
    }

    private static void scroll(double amount) {
        try {
            var screen = Minecraft.getInstance().gui.screen();
            var field = screen.getClass().getDeclaredField("content");
            field.setAccessible(true);
            Object content = field.get(screen);
            var method = content.getClass().getMethod("mouseScrolled", double.class, double.class, double.class);
            method.setAccessible(true);
            method.invoke(content, 0.0, 0.0, amount);
        } catch (Throwable t) {
            LOG.error("dohy-shots: ECHEC defilement", t);
        }
    }

    private static void openChat() {
        try {
            Screen chat = null;
            for (var constructor : net.minecraft.client.gui.screens.ChatScreen.class.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 1 && types[0] == String.class) {
                    chat = (Screen) constructor.newInstance("");
                } else if (types.length == 2 && types[0] == String.class && types[1] == boolean.class) {
                    chat = (Screen) constructor.newInstance("", false);
                }
            }
            if (chat != null) {
                Minecraft.getInstance().gui.setScreen(chat);
            }
        } catch (Throwable t) {
            LOG.error("dohy-shots: ECHEC chat", t);
        }
    }

    private static void state(ServerPlayer player, String when) {
        Job job = Job.FARMER;
        log("{} : Fermier tour {} deblocage {} renaissances {} bonus {} % XP {} ; argent {} ; meets(50) {}"
                        + " meets(100) {}", when, JobApi.progressLevel(player, job), JobApi.level(player, job),
                JobApi.rebirths(player, job), JobRebirth.percent(player, job), JobApi.xp(player, job),
                Economy.balance(player), JobApi.meets(player, job, 50), JobApi.meets(player, job, 100));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "difficulty peaceful");
            command(player, "kill @e[type=minecraft:slime]");
            player.setGameMode(GameType.SURVIVAL);
            Economy.set(player, 0L);
            player.getInventory().clearContent();
            log("refus au niveau 0 : {}", JobRebirth.refusal(player, Job.FARMER));
            state(player, "depart");
        }));
        ShotHarness.SHOTS.add(server("a1_page_niveau0", 30, player -> JobsMenu.INSTANCE.open(player, Job.FARMER)));
        ShotHarness.SHOTS.add(server("b1_niveau100", 40, player -> {
            UiNet.close(player);
            long before = Economy.balance(player);
            JobApi.grantLevels(player, Job.FARMER, 100);
            firstCycle = Economy.balance(player) - before;
            log("premier tour jusqu'au 100 : +{} $", firstCycle);
            // de l'XP au-dela du maximum, a reporter sur le tour suivant
            long xp = JobApi.xp(player, Job.FARMER);
            JobApi.grant(player, Job.FARMER, 60_000L);
            log("XP au-dela du maximum : {} -> {}", xp, JobApi.xp(player, Job.FARMER));
            state(player, "au niveau 100");
            player.getInventory().clearContent();
            JobsMenu.INSTANCE.open(player, Job.FARMER);
        }));
        ShotHarness.SHOTS.add(server("c1_renaissance", 40, player -> {
            long before = Economy.balance(player);
            JobsMenu.INSTANCE.handle(player, new UiNet.Action(Identifier.parse("dohy:jobs"), "card", "rebirth", 0));
            log("renaissance : argent des niveaux reportes +{} $", Economy.balance(player) - before);
            state(player, "apres la renaissance");
            UiNet.close(player);
        }));
        ShotHarness.SHOTS.add(server("c2_bonus", 30, player -> {
            long xp = JobApi.xp(player, Job.FARMER);
            JobApi.grant(player, Job.FARMER, 1_000L);
            log("1 000 XP accordes : {} recus (+10 % attendu)", JobApi.xp(player, Job.FARMER) - xp);
            int level = JobApi.progressLevel(player, Job.FARMER);
            long before = Economy.balance(player);
            JobApi.grantLevels(player, Job.FARMER, 1);
            log("niveau {} -> {} : +{} $ (base {} $, attendu {} $)", level, JobApi.progressLevel(player, Job.FARMER),
                    Economy.balance(player) - before, JobApi.moneyForLevel(level + 1),
                    Math.round(JobApi.moneyForLevel(level + 1) * 1.1d));
            state(player, "bonus");
            JobsMenu.INSTANCE.open(player, Job.FARMER);
        }));
        ShotHarness.SHOTS.add(client("c3_page_bas", 20, () -> scroll(-60.0)));
        ShotHarness.SHOTS.add(server("d1_accueil", 30, player -> {
            UiNet.close(player);
            HubController.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("e1_second_tour", 40, player -> {
            UiNet.close(player);
            long before = Economy.balance(player);
            JobApi.grantLevels(player, Job.FARMER, 100);
            long second = Economy.balance(player) - before;
            log("second tour jusqu'au 100 : +{} $ (premier tour {} $)", second, firstCycle);
            as(player, "metiers renaissance fermier");
            state(player, "apres la seconde renaissance");
            as(player, "metiers renaissance fermier");
            JobApi.grant(player, Job.FARMER, 500L);
        }));
        ShotHarness.SHOTS.add(client("e2_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("f1_metiers", 30, player -> JobsMenu.INSTANCE.open(player, Job.FARMER)));
        ShotHarness.SHOTS.add(server("g1_fiche", 30, player -> {
            UiNet.close(player);
            fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player, "renaissance");
        }));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> state(player, "fin")));
    }
}
