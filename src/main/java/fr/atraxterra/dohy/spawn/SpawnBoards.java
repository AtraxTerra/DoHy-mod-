package fr.atraxterra.dohy.spawn;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.dungeon.Dungeon;
import fr.atraxterra.dohy.palamod.dungeon.DungeonManager;
import fr.atraxterra.dohy.palamod.dungeon.Rpg;
import fr.atraxterra.dohy.palamod.trixium.TrixiumContest;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Les deux tableaux de l'atrium : le classement du concours de trixium (le
 * joueur parmi ses rivaux) et son profil. Ce sont des textes flottants dont
 * le contenu est recalcule de temps en temps, tant qu'il y a quelqu'un au
 * spawn ; ils ne sont reecrits que s'ils ont change.
 *
 * <p>Le mod se joue seul : un classement « des plus riches » n'aurait qu'une
 * ligne. Le concours de trixium, lui, a deja ses rivaux.
 */
final class SpawnBoards {

    private static final int EVERY = 100;
    private static final int TOP = 5;
    private static int ticks;
    private static boolean failed;

    private SpawnBoards() {
    }

    static void tick(MinecraftServer server) {
        if (ticks++ % EVERY != 0 || SpawnApi.building()) {
            return;
        }
        ServerLevel level = server.getLevel(DoHySpawn.DIMENSION);
        if (level == null || level.players().isEmpty()) {
            return;
        }
        ServerPlayer viewer = level.players().get(0);
        try {
            write(level, SpawnBuilder.BOARD_CONTEST, contest(viewer), 0xFFFFFF);
            write(level, SpawnBuilder.BOARD_PROFILE, profile(viewer), 0xFFFFFF);
        } catch (RuntimeException error) {
            if (!failed) {
                failed = true;
                DoHy.LOGGER.error("Spawn : les tableaux de l'atrium n'ont pas pu etre mis a jour", error);
            }
        }
    }

    private static void write(ServerLevel level, BlockPos pos, String text, int color) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof HologramBlockEntity board
                && !text.equals(board.text())) {
            board.set(text, color, SpawnBuilder.BOARD_SCALE);
        }
    }

    /** Les cinq premiers du concours, puis le joueur s'il n'en est pas. */
    static String contest(ServerPlayer player) {
        List<TrixiumContest.Entry> entries = TrixiumContest.players(player);
        StringBuilder text = new StringBuilder();
        int own = -1;
        for (int index = 0; index < entries.size(); index++) {
            TrixiumContest.Entry entry = entries.get(index);
            if (entry.player()) {
                own = index;
            }
            if (index < TOP) {
                text.append(index + 1).append(". ").append(entry.player() ? "> " + entry.name() + " <" : entry.name())
                        .append("  ").append(MoneyFormat.exact(entry.total())).append('\n');
            }
        }
        if (own >= TOP) {
            TrixiumContest.Entry entry = entries.get(own);
            text.append("...\n").append(own + 1).append(". > ").append(entry.name()).append(" <  ")
                    .append(MoneyFormat.exact(entry.total()));
        }
        return text.toString().strip();
    }

    static String profile(ServerPlayer player) {
        StringBuilder paliers = new StringBuilder();
        for (Dungeon dungeon : Dungeon.values()) {
            paliers.append(paliers.isEmpty() ? "" : " / ").append(DungeonManager.cleared(player, dungeon));
        }
        return player.getName().getString() + '\n'
                + "Patrimoine  " + MoneyFormat.shortForm(Economy.of(player).netWorth()) + '\n'
                + "Niveau global  " + ContractApi.globalLevel(player) + '\n'
                + "Metiers  " + JobApi.totalLevels(player) + " niveaux\n"
                + "RPG  niveau " + Rpg.level(Rpg.data(player).xp()) + '\n'
                + "Donjons  " + paliers + " paliers\n"
                + "Serie du jour  " + SpawnDaily.streak(player) + " jour(s)";
    }
}
