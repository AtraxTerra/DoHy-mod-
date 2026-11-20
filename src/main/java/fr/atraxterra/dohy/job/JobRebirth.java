package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * La renaissance d'un metier (demandee le 2026-11-20) : au niveau maximum, le metier repart de
 * zero, pour toujours un peu plus fort.
 *
 * <p><b>Ce qui reste</b> (choix du joueur) : tout ce que le metier a debloque — outils, machines,
 * recettes, zones, sources d'XP. Les verrous lisent {@link JobApi#level}, qui vaut le maximum des
 * qu'un metier a connu une renaissance ; la progression se lit dans {@link JobApi#progressLevel}.
 *
 * <p><b>Ce que ca rapporte</b> (choix du joueur) : +{@value #PERCENT} % d'XP et d'argent de ce
 * metier par renaissance, sans limite de nombre. Les recompenses des niveaux (argent, primes,
 * objets) se repaient en entier en remontant : le palier des primes repart de zero avec l'XP.
 *
 * <p>L'XP gagnee au-dela du maximum n'est pas perdue : elle compte des le premier niveau du
 * nouveau tour, recompenses comprises.
 */
public final class JobRebirth {

    /** Le bonus d'une renaissance sur l'XP et l'argent de son metier. */
    public static final double BONUS = 0.10d;
    /** Le meme, en pourcentage, pour les textes. */
    public static final int PERCENT = 10;

    private JobRebirth() {
    }

    /** Le rang affiche a cote du metier : « R2 », vide sans renaissance. */
    public static String rank(ServerPlayer player, Job job) {
        int count = JobApi.rebirths(player, job);
        return count > 0 ? "R" + count : "";
    }

    /** « Fermier », ou « Fermier R2 » apres deux renaissances. */
    public static String title(ServerPlayer player, Job job) {
        String rank = rank(player, job);
        return rank.isEmpty() ? job.display() : job.display() + " " + rank;
    }

    /** Le bonus total des renaissances de ce metier, en pourcentage. */
    public static long percent(ServerPlayer player, Job job) {
        return (long) JobApi.rebirths(player, job) * PERCENT;
    }

    /** Ce qui empeche la renaissance de ce metier ; {@code null} si elle peut avoir lieu. */
    public static String refusal(ServerPlayer player, Job job) {
        int level = JobApi.progressLevel(player, job);
        int max = JobApi.maxLevel();
        if (level < max) {
            return job.display() + " niveau " + level + " : la renaissance se fait au niveau " + max;
        }
        return null;
    }

    /** Fait renaitre ce metier ; la raison du refus, ou {@code null}. */
    public static String rebirth(ServerPlayer player, Job job) {
        String refusal = refusal(player, job);
        if (refusal != null) {
            return refusal;
        }
        JobData data = JobApi.data(player);
        long excess = Math.max(0L, data.xpOf(job) - JobApi.xpToReach(JobApi.maxLevel()));
        data.rebirth(job);
        int count = data.rebirths(job);
        long percent = (long) count * PERCENT;

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(job.display())
                .withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Renaissance " + count
                + " · +" + percent + " % d'XP et d'argent").withStyle(ChatFormatting.YELLOW)));
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0d, player.getZ(),
                    60, 0.6d, 1.0d, 0.6d, 0.4d);
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.2f);
        }
        player.sendSystemMessage(Component.literal("[Metiers] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(job.display() + " : renaissance " + count + ". Retour au niveau 0, tes"
                        + " deblocages restent ; +" + percent + " % d'XP et d'argent sur ce metier, et chaque niveau"
                        + " repaie sa recompense.").withStyle(ChatFormatting.YELLOW)));
        player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Metiers] ")
                .withStyle(ChatFormatting.GOLD).append(Component.literal(player.getName().getString()
                        + " fait renaitre son metier de " + job.display() + " (renaissance " + count + ").")
                        .withStyle(ChatFormatting.WHITE)), false);
        DoHy.LOGGER.info("Metiers : {} fait renaitre {} (renaissance {}, {} XP reportee)",
                player.getScoreboardName(), job.key(), count, excess);
        if (excess > 0L) {
            player.sendSystemMessage(Component.literal("  XP gagnee au-dela du maximum, reportee : "
                    + MoneyFormat.exact(excess)).withStyle(ChatFormatting.GRAY));
            JobApi.apply(player, job, excess);
        }
        return null;
    }
}
