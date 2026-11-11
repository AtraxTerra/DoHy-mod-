package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Les primes de palier : de l'argent tous les cinq niveaux de metier.
 *
 * <p>Elles ont remplace les perks le 2026-11-11. Un palier ne donne plus un
 * attribut, une chance de double drop ou un bonus d'XP : il paie
 * {@code prime_factor} fois l'argent de son niveau, en plus de cet argent.
 *
 * <h2>Payee une fois, rattrapee si besoin</h2>
 *
 * <p>{@link JobData} retient le dernier palier paye par metier. Le paiement
 * verse tous les paliers atteints au-dela de celui-la, ce qui couvre trois cas
 * avec la meme regle : la montee de niveau normale, plusieurs paliers franchis
 * d'un coup, et le joueur qui avait deja ses perks avant le changement, paye a
 * sa premiere connexion pour les paliers qu'il avait passes. Un reequilibrage
 * de la courbe qui fait redescendre des niveaux ne repaie rien.
 */
@EventBusSubscriber
public final class JobPrimes {

    private JobPrimes() {
    }

    /**
     * Le rattrapage a la connexion. La migration des anciens skills passe avant
     * ({@code EventPriority.HIGHEST}) : les niveaux lus ici sont les bons.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long total = 0;
        int count = 0;
        for (Job job : Job.values()) {
            List<Integer> due = due(player, job, JobApi.level(player, job));
            for (int level : due) {
                total += pay(player, job, level);
            }
            count += due.size();
        }
        if (count > 0) {
            player.sendSystemMessage(Component.literal("Les bonus de metier sont devenus des primes : ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("+" + MoneyFormat.exact(total)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" pour " + count + " palier(s) deja franchi(s)")
                            .withStyle(ChatFormatting.GRAY)));
            DoHy.LOGGER.info("Primes de metier rattrapees pour {} : {} paliers, {}",
                    player.getScoreboardName(), count, total);
        }
    }

    /**
     * Verse la prime d'un niveau qui vient d'etre atteint, s'il est un palier
     * et qu'elle n'a pas deja ete versee. Appele a chaque montee de niveau.
     */
    static void onLevel(ServerPlayer player, Job job, int level) {
        for (int due : due(player, job, level)) {
            long amount = pay(player, job, due);
            player.sendSystemMessage(Component.literal("  Prime de palier : ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("+" + MoneyFormat.exact(amount)).withStyle(ChatFormatting.GREEN)));
        }
    }

    /** Les paliers atteints jusqu'a {@code level} et pas encore payes. */
    private static List<Integer> due(ServerPlayer player, Job job, int level) {
        List<Integer> due = new ArrayList<>();
        int paid = JobApi.data(player).primeLevel(job);
        for (int milestone : milestones()) {
            if (milestone > paid && milestone <= level) {
                due.add(milestone);
            }
        }
        return due;
    }

    private static long pay(ServerPlayer player, Job job, int level) {
        long amount = amount(level);
        // Le palier est note avant le credit : une exception dans le credit
        // laisserait une prime perdue, jamais une prime payee deux fois.
        JobApi.data(player).markPrime(job, level);
        if (amount > 0) {
            Economy.credit(player, amount);
        }
        return amount;
    }

    /** Les niveaux qui portent une prime : 5, 10, ... jusqu'au niveau maximum. */
    public static List<Integer> milestones() {
        JobProgression.Curve curve = JobProgression.config().curve();
        List<Integer> levels = new ArrayList<>();
        if (curve.primeEvery() <= 0) {
            return levels;
        }
        for (int level = curve.primeEvery(); level <= curve.maxLevel(); level += curve.primeEvery()) {
            levels.add(level);
        }
        return levels;
    }

    /**
     * La prime d'un palier : {@code prime_factor} fois l'argent du niveau,
     * arrondie a deux chiffres significatifs comme les prix du shop.
     */
    public static long amount(int level) {
        double raw = JobProgression.config().curve().primeFactor() * JobApi.moneyForLevel(level);
        return roundSignificant(raw);
    }

    private static long roundSignificant(double value) {
        if (value <= 0) {
            return 0L;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)) - 1);
        return Math.max(1L, Math.round(Math.round(value / magnitude) * magnitude));
    }
}
