package fr.atraxterra.dohy.guide;

import fr.atraxterra.dohy.contract.ActiveContract;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionLeague;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.war.WarData;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.job.JobConfig;
import fr.atraxterra.dohy.job.JobGate;
import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.palamod.achievement.Achievements;
import fr.atraxterra.dohy.palamod.dungeon.Rpg;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.upgrade.UpgradeConfig;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * La progression du joueur, montree sur la fiche qui la nomme
 * ({@code progress} dans l'encyclopedie) : des chiffres dans le cartouche, et
 * parfois des cartes. Une cle inconnue ne montre rien.
 */
public final class EncyclopediaProgress {

    /** Ce qu'une fiche montre : les chiffres du cartouche, et les cartes de « ta progression ». */
    public record Result(List<MenuView.Stat> stats, List<Card> cards) {
        static final Result NONE = new Result(List.of(), List.of());
    }

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF22E558;
    private static final int MUTED = 0xFF8C8B93;

    private EncyclopediaProgress() {
    }

    public static Result of(ServerPlayer player, String key) {
        if (key == null || key.isEmpty()) {
            return Result.NONE;
        }
        try {
            if (key.startsWith("metier:")) {
                return job(player, key.substring(7));
            }
            return switch (key) {
                case "argent" -> money(player);
                case "metiers" -> jobs(player);
                case "contrats" -> contracts(player);
                case "musee" -> museum(player);
                case "succes" -> achievements(player);
                case "rpg" -> rpg(player);
                case "faction" -> faction(player);
                case "guerre" -> war(player);
                case "terre" -> earth(player);
                case "ameliorations" -> upgrades(player);
                default -> Result.NONE;
            };
        } catch (RuntimeException error) {
            // Une progression qui casse ne doit pas fermer la fiche.
            fr.atraxterra.dohy.DoHy.LOGGER.warn("Encyclopedie : progression « {} » illisible", key, error);
            return Result.NONE;
        }
    }

    private static Result money(ServerPlayer player) {
        return new Result(List.of(
                new MenuView.Stat("LIQUIDE", MoneyFormat.shortForm(Economy.balance(player)), WHITE),
                new MenuView.Stat("EPARGNE", MoneyFormat.shortForm(Bank.banked(player)), WHITE),
                new MenuView.Stat("PATRIMOINE", MoneyFormat.shortForm(Economy.of(player).netWorth()), GREEN)),
                List.of());
    }

    private static Result jobs(ServerPlayer player) {
        List<Card> cards = new ArrayList<>();
        for (Job job : Job.values()) {
            cards.add(jobCard(player, job));
        }
        return new Result(List.of(new MenuView.Stat("NIVEAUX CUMULES", String.valueOf(JobApi.totalLevels(player)),
                GREEN)), cards);
    }

    private static Card jobCard(ServerPlayer player, Job job) {
        int level = JobApi.level(player, job);
        double progress = JobApi.progress(player, job);
        return Card.of(jobIcon(job), job.display(), List.of("Niveau " + level + " / " + JobApi.maxLevel(),
                        level >= JobApi.maxLevel() ? "Au maximum" : Math.round(progress * 100) + " % du suivant"))
                .accent(0xFF2F5BEA).progress(Math.round(progress * 1000), 1000)
                .state(level >= JobApi.maxLevel() ? MenuView.DONE : MenuView.NORMAL);
    }

    private static ItemStack jobIcon(Job job) {
        return new ItemStack(switch (job) {
            case MINER -> Items.IRON_PICKAXE;
            case HUNTER -> Items.IRON_SWORD;
            case FARMER -> Items.IRON_HOE;
            case ALCHEMIST -> Items.BREWING_STAND;
        });
    }

    private static Result job(ServerPlayer player, String key) {
        Job job = null;
        for (Job candidate : Job.values()) {
            if (candidate.key().equals(key) || candidate.display().equalsIgnoreCase(key)) {
                job = candidate;
            }
        }
        if (job == null) {
            return Result.NONE;
        }
        int unlocked = 0;
        List<JobConfig.Unlock> next = new ArrayList<>();
        List<JobConfig.Unlock> all = JobGate.unlocksOf(job);
        for (JobConfig.Unlock unlock : all) {
            if (JobGate.allows(player, unlock)) {
                unlocked++;
            } else if (next.size() < 4) {
                next.add(unlock);
            }
        }
        List<Card> cards = new ArrayList<>();
        cards.add(jobCard(player, job));
        for (JobConfig.Unlock unlock : next) {
            cards.add(Card.of(new ItemStack(Items.PAPER), MenuView.text(JobApi.name(unlock)),
                    List.of("Debloque au niveau " + unlock.level())).accent(0xFF55545C).state(MenuView.DISABLED));
        }
        return new Result(List.of(
                new MenuView.Stat("NIVEAU", JobApi.level(player, job) + " / " + JobApi.maxLevel(), GREEN),
                new MenuView.Stat("DEBLOCAGES", unlocked + " / " + all.size(), WHITE)), cards);
    }

    private static Result contracts(ServerPlayer player) {
        int left = 0;
        for (ActiveContract contract : ContractApi.data(player).daily()) {
            if (!contract.done()) {
                left++;
            }
        }
        return new Result(List.of(
                new MenuView.Stat("QUOTIDIENS", left == 0 ? "FINIS" : left + " A FAIRE", left == 0 ? GREEN : WHITE),
                new MenuView.Stat("NIVEAU GLOBAL", String.valueOf(ContractApi.globalLevel(player)), GREEN)),
                List.of());
    }

    private static Result museum(ServerPlayer player) {
        int expected = 0;
        int donated = 0;
        int wings = 0;
        for (String wing : MuseumApi.wings()) {
            expected += MuseumApi.expected(wing).size();
            donated += MuseumApi.donatedIn(player, wing);
            if (MuseumApi.isComplete(player, wing)) {
                wings++;
            }
        }
        return new Result(List.of(
                new MenuView.Stat("PIECES", donated + " / " + expected, WHITE),
                new MenuView.Stat("AILES COMPLETES", wings + " / " + MuseumApi.wings().size(), GREEN)), List.of());
    }

    private static Result achievements(ServerPlayer player) {
        int done = Achievements.data(player).doneCount();
        return new Result(List.of(new MenuView.Stat("VALIDES", done + " / " + Achievements.total(), GREEN)),
                List.of());
    }

    private static Result rpg(ServerPlayer player) {
        int level = Rpg.level(Rpg.data(player).xp());
        int points = Rpg.available(player);
        return new Result(List.of(
                new MenuView.Stat("NIVEAU RPG", String.valueOf(level), GREEN),
                new MenuView.Stat("POINTS", String.valueOf(points), points > 0 ? 0xFFFAC400 : WHITE)), List.of());
    }

    private static Result faction(ServerPlayer player) {
        FactionData faction = FactionApi.data(player);
        if (!faction.exists()) {
            return new Result(List.of(new MenuView.Stat("FACTION", "AUCUNE", MUTED)), List.of());
        }
        FactionLeague league = FactionLeague.of(faction.ranking().league());
        return new Result(List.of(
                new MenuView.Stat("ELO", MoneyFormat.shortForm(faction.ranking().elo()), GREEN),
                new MenuView.Stat("LIGUE", league.label().toUpperCase(), league.color()),
                new MenuView.Stat("CLAIMS", String.valueOf(faction.claims().size()), WHITE)), List.of());
    }

    private static Result war(ServerPlayer player) {
        WarData data = player.getData(Factions.WAR.get());
        int hostile = 0;
        for (WarData.Relation relation : data.relations()) {
            if (relation.stance() == WarData.Stance.MENACE || relation.stance() == WarData.Stance.GUERRE) {
                hostile++;
            }
        }
        String conflict = data.conflict().map(relation -> relation.traits().name().toUpperCase()).orElse("AUCUNE");
        return new Result(List.of(
                new MenuView.Stat("HOSTILES", hostile + " / " + fr.atraxterra.dohy.faction.war.NationTraits.count(),
                        hostile > 0 ? 0xFFEF3826 : WHITE),
                new MenuView.Stat("GUERRE", conflict, data.conflict().isPresent() ? 0xFFEF3826 : MUTED)), List.of());
    }

    private static Result earth(ServerPlayer player) {
        if (!fr.atraxterra.dohy.earth.EarthNations.active()) {
            return new Result(List.of(new MenuView.Stat("CARTE", "MONDE ORDINAIRE", MUTED)), List.of());
        }
        int held = fr.atraxterra.dohy.earth.EarthNations.heldBy(player).size();
        return new Result(List.of(new MenuView.Stat("PAYS TENUS", String.valueOf(held), GREEN)), List.of());
    }

    private static Result upgrades(ServerPlayer player) {
        int done = UpgradeApi.data(player).total();
        int total = UpgradeConfig.config().totalLevels();
        return new Result(List.of(new MenuView.Stat("PALIERS", done + " / " + total, GREEN)), List.of());
    }
}
