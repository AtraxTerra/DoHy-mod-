package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.DoHyAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Le passage des skills aux metiers, une fois par joueur.
 *
 * <p>Les sauvegardes d'avant le remplacement portent l'XP dans la piece jointe
 * {@code dohy:skills}, sous les cles {@code mining}, {@code combat},
 * {@code farming} et {@code alchemy}. A la premiere connexion, elle est versee
 * dans {@code dohy:jobs} — minage vers Mineur, combat vers Chasseur, agriculture
 * vers Fermier, alchimie vers Alchimiste — puis supprimee. L'XP des cinq skills
 * retires est abandonnee : c'est la decision prise au remplacement.
 *
 * <p><b>Les recompenses en objets des paliers deja franchis</b> sont donnees a
 * ce moment-la. L'argent de ces niveaux a deja ete verse par les skills ; les
 * objets, eux, n'existaient pas. Sans ce rattrapage, un joueur deja Mineur 60
 * n'aurait jamais les lingots des paliers 10 a 60, alors qu'un nouveau venu
 * les recevrait.
 *
 * <p>Priorite la plus haute : les perks se recalculent a la connexion, et ils
 * doivent lire les niveaux migres, pas des niveaux a zero.
 */
@EventBusSubscriber
public final class JobMigration {

    private JobMigration() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            migrate(player);
        }
    }

    static void migrate(ServerPlayer player) {
        if (!player.hasData(DoHyAttachments.LEGACY_SKILLS.get())) {
            return;
        }
        JobData legacy = player.getData(DoHyAttachments.LEGACY_SKILLS);
        player.removeData(DoHyAttachments.LEGACY_SKILLS.get());
        if (legacy.isEmpty()) {
            return;
        }

        JobData jobs = JobApi.data(player);
        int[] before = new int[Job.values().length];
        for (Job job : Job.values()) {
            before[job.ordinal()] = JobApi.level(player, job);
        }
        jobs.absorb(legacy);

        player.sendSystemMessage(Component.literal("Les skills sont devenus des metiers.")
                .withStyle(ChatFormatting.GOLD));
        for (Job job : Job.values()) {
            int level = JobApi.level(player, job);
            if (level <= 0) {
                continue;
            }
            player.sendSystemMessage(Component.literal("  " + job.display() + " niveau " + level)
                    .withStyle(ChatFormatting.YELLOW));
            for (int reached = before[job.ordinal()] + 1; reached <= level; reached++) {
                JobApi.giveItems(player, job, reached);
            }
        }
        DoHy.LOGGER.info("Skills de {} convertis en metiers", player.getScoreboardName());
    }
}
