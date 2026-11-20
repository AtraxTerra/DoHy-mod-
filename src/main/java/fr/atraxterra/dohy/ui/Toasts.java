package fr.atraxterra.dohy.ui;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les notifications en bas a droite de l'ecran : contrat termine, gain d'XP d'un
 * metier, montee de niveau, succes valide.
 *
 * <p>L'XP d'un metier s'additionne tant que la notification est encore a l'ecran :
 * miner vingt blocs d'affilee donne une seule notification qui monte, pas vingt.
 */
@EventBusSubscriber
public final class Toasts {

    /** Le temps pendant lequel les gains d'XP d'un metier s'additionnent. */
    private static final long XP_WINDOW = 4_000L;
    private static final Map<UUID, Map<Job, long[]>> XP = new ConcurrentHashMap<>();

    private Toasts() {
    }

    public static void send(ServerPlayer player, ItemStack icon, String key, String title, String line, int accent,
                            String group) {
        UiNet.toast(player, new UiNet.Toast(icon, key, title, line, accent, 4_500, group));
    }

    public static int jobColor(Job job) {
        return switch (job) {
            case MINER -> 0xFFEF3926;
            case FARMER -> 0xFFFAC400;
            case HUNTER -> 0xFF2F5BEA;
            case ALCHEMIST -> 0xFFEC4FB5;
        };
    }

    public static ItemStack jobIcon(Job job) {
        return new ItemStack(switch (job) {
            case MINER -> Items.IRON_PICKAXE;
            case FARMER -> Items.WHEAT;
            case HUNTER -> Items.BOW;
            case ALCHEMIST -> Items.BLAZE_POWDER;
        });
    }

    /** « 38 059 » : les milliers separes par une espace. */
    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    /**
     * Un gain d'XP de metier : additionne aux gains recents, puis la notification du
     * metier est envoyee (ou remplacee).
     */
    public static void jobXp(ServerPlayer player, Job job, long gained, boolean levelUp) {
        long now = System.currentTimeMillis();
        long[] running = XP.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(Job.class))
                .computeIfAbsent(job, key -> new long[2]);
        if (now - running[1] > XP_WINDOW) {
            running[0] = 0L;
        }
        running[0] += gained;
        running[1] = now;
        int level = JobApi.progressLevel(player, job);
        String rank = fr.atraxterra.dohy.job.JobRebirth.rank(player, job);
        String line;
        if (level >= JobApi.maxLevel()) {
            line = "Niveau maximum";
        } else {
            long into = JobApi.xp(player, job) - JobApi.xpToReach(level);
            long need = JobApi.xpToReach(level + 1) - JobApi.xpToReach(level);
            line = number(Math.max(0L, into)) + " / " + number(Math.max(1L, need));
        }
        send(player, jobIcon(job), job.display().toUpperCase(Locale.ROOT) + (rank.isEmpty() ? "" : " " + rank)
                        + " · NIVEAU " + level,
                "+" + number(running[0]) + " XP", line, jobColor(job), "xp_" + job.key());
        if (levelUp) {
            send(player, jobIcon(job), "NIVEAU SUPERIEUR", fr.atraxterra.dohy.job.JobRebirth.title(player, job)
                            + " niveau " + level,
                    "Ouvre les metiers pour tes deblocages", jobColor(job), "level_" + job.key());
        }
    }

    /** Oublie un joueur qui se deconnecte. */
    public static void forget(ServerPlayer player) {
        XP.remove(player.getUUID());
    }

    /** A la reconnexion, la barre laterale doit recevoir ses pastilles a nouveau. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player);
            UiNet.forgetNav(player);
        }
    }
}
