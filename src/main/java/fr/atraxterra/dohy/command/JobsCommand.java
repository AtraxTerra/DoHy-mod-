package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.job.JobConfig;
import fr.atraxterra.dohy.job.JobGate;
import fr.atraxterra.dohy.job.JobProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /metiers} — niveaux, deblocages, recompenses, et l'audit de la table.
 *
 * <p>Depuis que les metiers ont remplace les skills, c'est la seule commande de
 * progression : {@code /skills} redirige ici pour les habitues.
 *
 * <p>{@code audit} merite sa place a cote des deux autres : la table des
 * deblocages nomme des items que les chapitres suivants n'ont pas encore
 * ajoutes, et un verrou qui porte sur un identifiant inexistant ne verrouille
 * rien. C'est exactement l'outil que {@code /dohy scan} est pour les prix,
 * et pour la meme raison — un trou silencieux dans une table d'equilibrage ne
 * se voit qu'en jeu, trop tard.
 */
@EventBusSubscriber
public final class JobsCommand {

    private static final int BAR_WIDTH = 20;

    private JobsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build("metiers"));
        // Alias anglais : le code et le datapack parlent anglais, autant que la
        // commande reponde aux deux.
        event.getDispatcher().register(build("jobs"));
        // L'ancienne commande des skills : elle ouvre les metiers plutot que de
        // repondre « commande inconnue » a ceux qui l'ont dans les doigts.
        event.getDispatcher().register(build("skills"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .executes(ctx -> open(ctx.getSource()))
                .then(Commands.literal("list")
                        .executes(ctx -> summary(ctx.getSource())))
                .then(Commands.literal("audit")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> audit(ctx.getSource())));

        for (Job job : Job.values()) {
            root = root.then(Commands.literal(job.key())
                    .executes(ctx -> detail(ctx.getSource(), job)));
        }
        return root;
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        fr.atraxterra.dohy.job.JobsController.open(source.getPlayerOrException());
        return 1;
    }

    private static int summary(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int maxLevel = JobApi.maxLevel();

        source.sendSuccess(() -> Component.literal("Metiers")
                .withStyle(ChatFormatting.GOLD), false);

        for (Job job : Job.values()) {
            int level = JobApi.level(player, job);
            List<JobConfig.Unlock> unlocks = JobGate.unlocksOf(job);
            int acquired = 0;
            for (JobConfig.Unlock unlock : unlocks) {
                if (JobGate.allows(player, unlock)) {
                    acquired++;
                }
            }

            JobConfig.Unlock next = JobGate.next(player, job);
            Component tail = next == null
                    ? Component.literal("  tout debloque").withStyle(ChatFormatting.GREEN)
                    : Component.literal("  prochain : ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(JobApi.name(next).copy().withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" (niv. " + next.level() + ")")
                                    .withStyle(ChatFormatting.DARK_GRAY));

            int finalAcquired = acquired;
            source.sendSuccess(() -> Component.literal("  " + pad(job.display()) + " ")
                    .withStyle(level >= maxLevel ? ChatFormatting.AQUA : ChatFormatting.WHITE)
                    .append(Component.literal(String.format("%3d", level))
                            .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" " + bar(JobApi.progress(player, job)) + " ")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(finalAcquired + "/" + unlocks.size())
                            .withStyle(ChatFormatting.GRAY))
                    .append(tail), false);
        }

        source.sendSuccess(() -> Component.literal(
                        "  Perk tous les " + JobProgression.config().curve().perkEvery()
                                + " niveaux, objets aux paliers. Detail : /metiers <metier>.")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return Job.values().length;
    }

    private static int detail(CommandSourceStack source, Job job) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int level = JobApi.level(player, job);
        List<JobConfig.Unlock> unlocks = JobGate.unlocksOf(job);

        source.sendSuccess(() -> Component.literal(
                        job.display() + " — niveau " + level + " / " + JobApi.maxLevel())
                .withStyle(ChatFormatting.GOLD), false);

        if (unlocks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  aucun deblocage declare")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 0;
        }

        for (JobConfig.Unlock unlock : unlocks) {
            boolean acquired = level >= unlock.level();
            boolean real = unlock.isTag() || JobConfig.exists(unlock.plainId());
            source.sendSuccess(() -> Component.literal("  " + (acquired ? "✔ " : "✖ "))
                    .withStyle(acquired ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(Component.literal(String.format("%3d  ", unlock.level()))
                            .withStyle(ChatFormatting.GOLD))
                    .append(JobApi.name(unlock).copy()
                            .withStyle(acquired ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                    .append(real
                            ? Component.empty()
                            : Component.literal("  (item absent)")
                                    .withStyle(ChatFormatting.DARK_RED)), false);
        }

        List<JobConfig.Reward> rewards = JobConfig.config().rewardsOf(job);
        if (!rewards.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  Recompenses en objets")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            for (JobConfig.Reward reward : rewards) {
                boolean received = level >= reward.level();
                boolean real = JobConfig.exists(reward.id());
                source.sendSuccess(() -> Component.literal("  " + (received ? "✔ " : "· "))
                        .withStyle(received ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                        .append(Component.literal(String.format("%3d  ", reward.level()))
                                .withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(reward.count() + "x ")
                                .withStyle(ChatFormatting.WHITE))
                        .append(JobApi.name(reward).copy()
                                .withStyle(received ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                        .append(real
                                ? Component.empty()
                                : Component.literal("  (item absent)")
                                        .withStyle(ChatFormatting.DARK_RED)), false);
            }
        }
        return unlocks.size();
    }

    /**
     * L'audit : ce que la table promet et que le mod ne livre pas.
     *
     * <p>Sortie volontairement muette quand tout va bien — un audit qui parle
     * toujours finit par ne plus etre lu.
     */
    private static int audit(CommandSourceStack source) {
        List<JobConfig.Unlock> unlocks = JobConfig.config().unlocks();
        List<String> missing = new java.util.ArrayList<>();
        List<String> badJob = new java.util.ArrayList<>();

        for (JobConfig.Unlock unlock : unlocks) {
            if (Job.byKey(unlock.job()) == null) {
                badJob.add(unlock.id() + " -> metier « " + unlock.job() + " »");
            } else if (!unlock.isTag() && !JobConfig.exists(unlock.plainId())) {
                missing.add(unlock.id());
            }
        }

        java.util.TreeSet<String> missingRewards = new java.util.TreeSet<>();
        for (JobConfig.Reward reward : JobConfig.config().rewards()) {
            if (Job.byKey(reward.job()) == null) {
                badJob.add(reward.id() + " -> metier « " + reward.job() + " »");
            } else if (!JobConfig.exists(reward.id())) {
                missingRewards.add(reward.id());
            }
        }

        source.sendSuccess(() -> Component.literal("Audit des metiers — "
                        + unlocks.size() + " deblocages, "
                        + JobConfig.config().rewards().size() + " recompenses declares")
                .withStyle(ChatFormatting.GOLD), false);

        if (badJob.isEmpty() && missing.isEmpty() && missingRewards.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  tout est branche")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        badJob.forEach(line -> source.sendSuccess(() ->
                Component.literal("  metier inconnu : " + line)
                        .withStyle(ChatFormatting.RED), false));

        if (!missing.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  " + missing.size()
                            + " verrou(s) sans item enregistre, donc sans effet :")
                    .withStyle(ChatFormatting.YELLOW), false);
            missing.forEach(id -> source.sendSuccess(() ->
                    Component.literal("    " + id).withStyle(ChatFormatting.DARK_GRAY), false));
        }
        if (!missingRewards.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  " + missingRewards.size()
                            + " objet(s) de recompense non enregistre(s), donc non donne(s) :")
                    .withStyle(ChatFormatting.YELLOW), false);
            missingRewards.forEach(id -> source.sendSuccess(() ->
                    Component.literal("    " + id).withStyle(ChatFormatting.DARK_GRAY), false));
        }
        return badJob.size() + missing.size() + missingRewards.size();
    }

    private static String bar(double ratio) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * BAR_WIDTH);
        return "▏".repeat(Math.max(0, filled)) + "·".repeat(Math.max(0, BAR_WIDTH - filled));
    }

    private static String pad(String text) {
        return text.length() >= 12 ? text : text + " ".repeat(12 - text.length());
    }
}
