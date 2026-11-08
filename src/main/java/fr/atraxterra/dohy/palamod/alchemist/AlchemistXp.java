package fr.atraxterra.dohy.palamod.alchemist;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.job.JobConfig;
import fr.atraxterra.dohy.job.JobGate;
import fr.atraxterra.dohy.palamod.miner.MinerXpEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ItemLike;

/**
 * L'XP d'Alchimiste et les verrous lus depuis le code du metier.
 *
 * <p>Les gains de PalaJobs sont convertis a progression egale
 * ({@link MinerXpEvents#convert}) et, comme dans {@code incrementStats}, rien
 * n'est donne sous le niveau minimal de l'objectif.
 */
public final class AlchemistXp {

    private AlchemistXp() {
    }

    /** {@code buildValue(xp, niveau)} : l'XP de PalaJobs, a partir de {@code minLevel} (niveaux d'ici). */
    public static void grant(ServerPlayer player, double palajobsXp, int minLevel) {
        if (palajobsXp <= 0.0d || JobApi.level(player, Job.ALCHEMIST) < minLevel) {
            return;
        }
        long xp = MinerXpEvents.convert(player, palajobsXp);
        if (xp > 0) {
            JobApi.grant(player, Job.ALCHEMIST, xp);
        }
    }

    /**
     * Le joueur peut-il se servir de cet objet, d'apres {@code data/jobs.json} ?
     * C'est le {@code canUseCraft} / {@code canUseUseItem} des machines de
     * l'Alchimiste. Le creatif passe, comme pour tous les verrous.
     */
    public static boolean allows(ServerPlayer player, ItemLike item) {
        if (player.isCreative()) {
            return true;
        }
        JobConfig.Unlock unlock = JobGate.of(item.asItem());
        return unlock == null || JobGate.allows(player, unlock);
    }

    /** Le refus du verrou, dans la barre d'action. */
    public static void refuse(ServerPlayer player, ItemLike item) {
        JobConfig.Unlock unlock = JobGate.of(item.asItem());
        if (unlock != null) {
            actionBar(player, JobApi.refusal(player, unlock));
        }
    }

    public static void actionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }
}
