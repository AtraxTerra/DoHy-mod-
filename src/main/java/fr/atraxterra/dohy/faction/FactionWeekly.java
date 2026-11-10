package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Le lundi minuit de Paladium : la ligue se recalcule et les recompenses
 * tombent dans la banque de faction.
 *
 * <p>Sur Paladium, une faction sans aucune victoire ELO dans la semaine descend
 * d'une ligue et ne touche rien. Ici, pareil, a l'heure du serveur. Un joueur
 * absent plusieurs lundis les rattrape a sa connexion, un par un : seule la
 * premiere semaine peut compter des victoires.
 */
@EventBusSubscriber
public final class FactionWeekly {

    /** Lundis rattrapes au plus : un an. */
    private static final int MAX_CATCH_UP = 52;

    private FactionWeekly() {
    }

    public static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    /** Le lundi de la semaine en cours, en jour epoch. */
    public static long currentMonday() {
        return LocalDate.now(zone()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
    }

    /** Le temps jusqu'au prochain lundi minuit. */
    public static Duration untilReset() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        ZonedDateTime next = LocalDate.now(zone()).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(zone());
        return Duration.between(now, next);
    }

    public static void process(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return;
        }
        long monday = currentMonday();
        FactionData.Ranking ranking = data.ranking;
        if (ranking.lastMonday() == 0L) {
            data.ranking = new FactionData.Ranking(ranking.elo(), ranking.weekElo(), ranking.weekWins(),
                    ranking.league(), monday);
            return;
        }
        int weeks = (int) Math.min(MAX_CATCH_UP, (monday - ranking.lastMonday()) / 7);
        if (weeks <= 0) {
            return;
        }
        for (int week = 0; week < weeks; week++) {
            FactionData.Ranking current = data.ranking;
            FactionLeague league = FactionLeague.of(current.league());
            if (current.weekWins() > 0) {
                data.bank += league.reward();
                data.log(league.reward(), "Recompense de ligue " + league.label());
                FactionApi.tell(player, "Semaine close en ligue " + league.label() + " : +"
                        + MoneyFormat.exact(league.reward()) + " $ a la banque de faction.", ChatFormatting.GREEN);
                data.ranking = new FactionData.Ranking(current.elo(), 0L, 0, league.ordinal(), current.lastMonday());
            } else if (league.ordinal() > 0) {
                // Une semaine sans victoire : une marche plus bas, et l'ELO ramene au seuil de la ligue.
                FactionLeague lower = FactionLeague.of(league.ordinal() - 1);
                long elo = Math.min(current.elo(), lower.threshold());
                data.ranking = new FactionData.Ranking(elo, 0L, 0, lower.ordinal(), current.lastMonday());
                FactionApi.tell(player, "Une semaine sans victoire : ta faction redescend en ligue " + lower.label()
                        + ".", ChatFormatting.RED);
            } else {
                data.ranking = new FactionData.Ranking(current.elo(), 0L, 0, 0, current.lastMonday());
            }
        }
        FactionData.Ranking done = data.ranking;
        data.ranking = new FactionData.Ranking(done.elo(), done.weekElo(), done.weekWins(), done.league(), monday);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            process(player);
            FactionData data = FactionApi.data(player);
            if (data.exists() && !data.motd().isEmpty()) {
                FactionApi.tell(player, data.motd(), ChatFormatting.WHITE);
            }
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 1200 == 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                process(player);
            }
        }
        FactionHomes.tick(event.getServer());
    }
}
