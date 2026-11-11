package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.contract.ActiveContract;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractDisplay;
import fr.atraxterra.dohy.contract.ContractType;
import fr.atraxterra.dohy.ui.Hub;
import fr.atraxterra.dohy.ui.view.JobDetailView;
import fr.atraxterra.dohy.ui.view.JobsView;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * L'ecran des metiers cote serveur : niveaux, contrats du jour, et les deux
 * actions (ouvrir la fiche d'un metier, revenir au menu).
 */
public final class JobsController {

    private JobsController() {
    }

    public static void open(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        UiNet.show(player, view(player));
    }

    static JobsView view(ServerPlayer player) {
        List<JobsView.JobLine> jobs = new ArrayList<>();
        for (Job job : Job.values()) {
            int level = JobApi.level(player, job);
            jobs.add(new JobsView.JobLine(job.name(), level, JobApi.maxLevel(),
                    (int) Math.round(JobApi.progress(player, job) * 1000), JobApi.xp(player, job),
                    JobApi.xpToReach(Math.min(JobApi.maxLevel(), level + 1))));
        }
        ContractApi.rollIfDue(player);
        List<JobsView.Quest> quests = new ArrayList<>();
        for (ActiveContract contract : ContractApi.data(player).daily()) {
            String goal = MoneyFormat.exact(contract.amount())
                    + (contract.kind() == ContractType.SELL_VALUE ? " $" : "");
            quests.add(new JobsView.Quest(ContractDisplay.label(contract), "Objectif : " + goal,
                    contract.progress(), contract.amount(), ContractApi.rewardFor(player, contract), contract.done()));
        }
        int questBonus = (int) Math.round(ContractApi.rewardBonus(player) * 1000);
        int multiplier = (int) Math.round(UpgradeApi.bonus(player, UpgradeApi.JOB_XP_BONUS) * 1000);
        return new JobsView(jobs, quests, questBonus, multiplier);
    }

    /** La fiche d'un metier ; « retour » ramene a l'ecran des metiers. */
    public static void handleDetail(ServerPlayer player, UiNet.Action action) {
        if ("back".equals(action.verb())) {
            UiNet.show(player, view(player));
        }
    }

    public static void openDetail(ServerPlayer player, Job job) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        UiNet.show(player, detail(player, job));
    }

    static JobDetailView detail(ServerPlayer player, Job job) {
        int level = JobApi.level(player, job);
        int maxLevel = JobApi.maxLevel();
        long into = level >= maxLevel ? 0 : JobApi.xp(player, job) - JobApi.xpToReach(level);
        long need = level >= maxLevel ? 0 : JobApi.xpForLevel(level + 1);

        List<JobDetailView.Row> unlocks = new ArrayList<>();
        for (JobConfig.Unlock unlock : JobGate.unlocksOf(job)) {
            boolean acquired = level >= unlock.level();
            boolean real = !unlock.isTag() && JobConfig.exists(unlock.plainId());
            int state = acquired ? JobDetailView.ACQUIRED : JobDetailView.LOCKED;
            if (!real && !unlock.isTag()) {
                state = JobDetailView.MISSING;
            }
            List<String> lines = new ArrayList<>();
            lines.add(acquired ? "Debloque" : "Il manque " + (unlock.level() - level) + " niveau(x)");
            if (unlock.isTag()) {
                lines.add("Porte sur un groupe : " + unlock.plainId());
            }
            if (!real && !unlock.isTag()) {
                lines.add("Objet pas encore ajoute par le mod");
            }
            unlocks.add(new JobDetailView.Row(icon(unlock.plainId(), acquired ? Items.PAPER : Items.BOOK),
                    MenuView.text(JobApi.name(unlock)), acquired ? "Debloque" : "Verrouille", unlock.level(), state,
                    lines));
        }

        // Les primes de palier : versees ou a venir. « Versee » se lit dans les
        // donnees du joueur et non dans son niveau, pour que la fiche dise ce
        // qui a vraiment ete paye.
        int paid = JobApi.data(player).primeLevel(job);
        List<JobDetailView.Row> primes = new ArrayList<>();
        for (int milestone : JobPrimes.milestones()) {
            boolean received = milestone <= paid;
            long amount = JobPrimes.amount(milestone);
            primes.add(new JobDetailView.Row(new ItemStack(received ? Items.GOLD_INGOT : Items.GOLD_NUGGET),
                    "Prime de " + MoneyFormat.exact(amount), received ? "Versee" : "Au niveau " + milestone,
                    milestone, received ? JobDetailView.ACQUIRED : JobDetailView.LOCKED,
                    List.of("Versee une fois, au passage du niveau " + milestone,
                            "En plus des " + MoneyFormat.exact(JobApi.moneyForLevel(milestone)) + " du niveau")));
        }

        Map<Integer, List<JobConfig.Reward>> byLevel = new TreeMap<>();
        for (JobConfig.Reward reward : JobConfig.config().rewardsOf(job)) {
            byLevel.computeIfAbsent(reward.level(), ignored -> new ArrayList<>()).add(reward);
        }
        List<JobDetailView.Row> rewards = new ArrayList<>();
        byLevel.forEach((rewardLevel, list) -> {
            boolean acquired = level >= rewardLevel;
            ItemStack icon = ItemStack.EMPTY;
            List<String> lines = new ArrayList<>();
            MutableComponent names = Component.empty();
            boolean anyMissing = false;
            for (JobConfig.Reward reward : list) {
                boolean real = JobConfig.exists(reward.id());
                anyMissing |= !real;
                if (real && icon.isEmpty()) {
                    icon = icon(reward.id(), Items.CHEST);
                }
                Component name = Component.literal(reward.count() + "x ").append(JobApi.name(reward));
                if (!names.getSiblings().isEmpty()) {
                    names.append(", ");
                }
                names.append(name);
                lines.add(MenuView.text(name.copy().append((reward.enchantments().isEmpty() ? "" : " (enchante)")
                        + (real ? "" : " — pas encore ajoute"))));
            }
            rewards.add(new JobDetailView.Row(icon.isEmpty() ? new ItemStack(Items.CHEST) : icon,
                    MenuView.text(names), acquired ? "Recu" : "Au niveau " + rewardLevel, rewardLevel,
                    acquired ? JobDetailView.ACQUIRED : anyMissing ? JobDetailView.MISSING : JobDetailView.LOCKED,
                    lines));
        });

        return new JobDetailView(job.name(), level, maxLevel, (int) Math.round(JobApi.progress(player, job) * 1000),
                into, need, JobApi.xp(player, job), level >= maxLevel ? 0 : JobApi.moneyForLevel(level + 1),
                unlocks, primes, rewards);
    }

    private static ItemStack icon(String raw, Item fallback) {
        Identifier id = JobConfig.parse(raw);
        Item item = id == null ? fallback : BuiltInRegistries.ITEM.getOptional(id).orElse(fallback);
        return new ItemStack(item);
    }

    public static void handle(ServerPlayer player, UiNet.Action action) {
        switch (action.verb()) {
            case "open" -> {
                Job job;
                try {
                    job = Job.valueOf(action.argument());
                } catch (IllegalArgumentException unknown) {
                    return;
                }
                UiNet.show(player, detail(player, job));
            }
            case "home" -> {
                UiNet.forget(player);
                Hub.open(player);
            }
            default -> {
            }
        }
    }
}
