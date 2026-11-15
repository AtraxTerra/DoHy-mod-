package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.contract.ActiveContract;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractData;
import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.job.JobConfig;
import fr.atraxterra.dohy.job.JobGate;
import fr.atraxterra.dohy.job.JobsController;
import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.achievement.AchievementMenu;
import fr.atraxterra.dohy.palamod.achievement.Achievements;
import fr.atraxterra.dohy.palamod.dungeon.Rpg;
import fr.atraxterra.dohy.palamod.dungeon.RpgMenu;
import fr.atraxterra.dohy.palamod.trixium.TrixiumContest;
import fr.atraxterra.dohy.palamod.trixium.TrixiumData;
import fr.atraxterra.dohy.palamod.trixium.TrixiumMenu;
import fr.atraxterra.dohy.palamod.trixium.TrixiumRivals;
import fr.atraxterra.dohy.shop.SellChestMenu;
import fr.atraxterra.dohy.shop.ShopController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.upgrade.UpgradeConfig;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Le menu principal : une carte par systeme, avec le chiffre qui dit s'il faut
 * y aller (contrats restants, epargne, pieces manquantes au musee).
 */
public final class HubController extends MenuController {

    public static final HubController INSTANCE = new HubController();

    private HubController() {
        super("hub");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("LIQUIDE", MoneyFormat.shortForm(Economy.balance(player)), 0xFFFFFFFF),
                new MenuView.Stat("PATRIMOINE", MoneyFormat.shortForm(Economy.of(player).netWorth()), 0xFFFFFFFF),
                new MenuView.Stat("NIVEAU GLOBAL", String.valueOf(ContractApi.globalLevel(player)), 0xFF22E558));

        // A faire maintenant : ce qui attend une action du joueur.
        ContractData contracts = ContractApi.data(player);
        int left = 0;
        int dailies = contracts.daily().size();
        for (ActiveContract contract : contracts.daily()) {
            if (!contract.done()) {
                left++;
            }
        }
        Card contractCard = Card.of(new ItemStack(Items.WRITABLE_BOOK), "Contrats du jour", List.of(
                        left == 0 ? "Tout est fait" : left + (left > 1 ? " restants" : " restant"), "",
                        (dailies - left) + " / " + dailies))
                .accent(0xFF2F5BEA).action("contracts").progress(dailies - left, dailies)
                .badge(left == 0 ? "" : String.valueOf(left)).state(left == 0 ? MenuView.DONE : MenuView.NORMAL);
        int rpgLevel = Rpg.level(Rpg.data(player).xp());
        long rpgInto = Rpg.xpInLevel(Rpg.data(player).xp());
        long rpgNeed = Rpg.xpToNext(rpgLevel);
        int points = Rpg.available(player);
        Card rpgCard = Card.of(new ItemStack(Items.TOTEM_OF_UNDYING), "Profil RPG", List.of("Niveau " + rpgLevel, "",
                        MoneyFormat.shortForm(rpgInto) + " / " + MoneyFormat.shortForm(rpgNeed) + " XP"))
                .accent(0xFFF3445D).action("rpg").progress(rpgInto, Math.max(1, rpgNeed))
                .badge(points > 0 ? "+" + points : "");
        List<Card> todo = List.of(contractCard, passCard(player), altarCard(player), rpgCard);
        int actionable = (left > 0 ? 1 : 0) + (points > 0 ? 1 : 0);
        for (Card card : todo) {
            if (card != contractCard && card != rpgCard && !card.badge().isEmpty()) {
                actionable++;
            }
        }

        // Les metiers : le niveau, la barre, le prochain deblocage.
        List<Card> jobs = new java.util.ArrayList<>();
        for (Job job : Job.values()) {
            int level = JobApi.level(player, job);
            JobConfig.Unlock next = fr.atraxterra.dohy.job.JobGate.next(player, job);
            String line;
            if (next == null) {
                line = "Tout est debloque";
            } else {
                Component name = next.label().<Component>map(Component::literal).orElseGet(() ->
                        fr.atraxterra.dohy.ui.Gui.displayName(net.minecraft.resources.Identifier.parse(next.id())));
                line = MenuView.text(Component.empty().append(name).append(" \u00B7 niv. " + next.level()));
            }
            double progress = level >= JobApi.maxLevel() ? 1.0d : JobApi.progress(player, job);
            jobs.add(Card.of(new ItemStack(jobIcon(job)), job.display(), List.of(line))
                    .accent(jobColor(job)).badge("NIV. " + level).action("jobs")
                    .progress(Math.round(progress * 1000.0d), 1000));
        }

        // L'agenda : les prochaines heures qui comptent.
        List<Card> agenda = new java.util.ArrayList<>();
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(fr.atraxterra.dohy.util.RealClock.zone());
        long mineMinutes = fr.atraxterra.dohy.mine.MineService.minutesUntilReset();
        agenda.add(Card.of(new ItemStack(Items.IRON_PICKAXE), "Remise de la Mine", List.of("Dans " + until(mineMinutes)))
                .badge(now.plusMinutes(mineMinutes).getHour() + " H").accent(0xFF2BC4C4));
        java.time.ZonedDateTime eventNow = fr.atraxterra.dohy.faction.event.FactionEvents.now();
        fr.atraxterra.dohy.faction.event.FactionEventKind soonest = null;
        java.time.ZonedDateTime soonestTime = null;
        for (fr.atraxterra.dohy.faction.event.FactionEventKind kind
                : fr.atraxterra.dohy.faction.event.FactionEventKind.values()) {
            if (!kind.ready()) {
                continue;
            }
            java.time.ZonedDateTime at = kind.next(eventNow);
            if (soonestTime == null || at.isBefore(soonestTime)) {
                soonest = kind;
                soonestTime = at;
            }
        }
        if (soonest != null) {
            agenda.add(Card.of(new ItemStack(soonest.icon()), soonest.label(),
                            List.of(MoneyFormat.shortForm(soonest.elo()) + " ELO"))
                    .badge(day(soonestTime, eventNow) + soonestTime.getHour() + " H").accent(0xFFFAC400));
        }
        long contractMinutes = ContractApi.minutesUntilDailyReset();
        agenda.add(Card.of(new ItemStack(Items.WRITABLE_BOOK), "Nouveaux contrats", List.of())
                .badge(now.plusMinutes(contractMinutes).getHour() + " H").accent(0xFF2F5BEA));
        TrixiumContest.catchUp(player);
        TrixiumData trixium = TrixiumContest.data(player);
        long daysLeft = daysLeftInMonth();
        agenda.add(Card.of(new ItemStack(PalaItems.TRIXIUM.get()), "Fin du concours Trixium", List.of(
                        trixium.deposited() > 0 ? TrixiumContest.rank(player) + "e / " + (TrixiumRivals.COUNT + 1)
                                : "Pas encore de depot"))
                .badge(daysLeft + " J").accent(0xFFEC4FB5));

        // L'economie : l'argent qui travaille.
        double booster = Boosters.active(player);
        long boosterLeft = Boosters.remaining(player);
        int listings = 0;
        try {
            listings = fr.atraxterra.dohy.auction.AuctionHouse.listings(player).size();
        } catch (RuntimeException ignored) {
            // le marche n'est pas encore pret
        }
        List<Card> economy = List.of(
                Card.of(new ItemStack(Items.CHEST_MINECART), "Banque", List.of(
                                MoneyFormat.shortForm(Bank.banked(player)),
                                "+" + MoneyFormat.shortForm(Bank.perDay(player)) + " / jour"))
                        .accent(0xFFFAC400).action("bank"),
                Card.of(new ItemStack(Items.GOLD_BLOCK), booster > 1 ? "Booster x" + (long) booster : "Boutique",
                                List.of(booster > 1 ? "Encore " + until(boosterLeft) : "Aucun booster actif"))
                        .accent(0xFFFAC400).action("boutique").badge(booster > 1 ? "ACTIF" : "")
                        .state(booster > 1 ? MenuView.DONE : MenuView.NORMAL),
                Card.of(new ItemStack(Items.MAP), "Marche", List.of(listings > 0 ? listings + " annonces"
                                : "Les annonces du jour"))
                        .accent(0xFFFAC400).action("market"),
                clickerCard(player));

        return new MenuView(id(), "Accueil", "", stats, List.of(
                new MenuView.Section("A FAIRE|" + actionable, "upgrade", MenuView.GRID, todo),
                new MenuView.Section("TES METIERS", "resources", MenuView.ROWS | MenuView.HALF, jobs),
                new MenuView.Section("AGENDA", "craft", MenuView.AGENDA | MenuView.HALF, agenda),
                new MenuView.Section("ECONOMIE", "resources", MenuView.GRID, economy)), false);
    }

    private static net.minecraft.world.item.Item jobIcon(Job job) {
        return switch (job) {
            case MINER -> Items.IRON_PICKAXE;
            case FARMER -> Items.WHEAT;
            case HUNTER -> Items.BOW;
            case ALCHEMIST -> Items.BLAZE_POWDER;
        };
    }

    private static int jobColor(Job job) {
        return switch (job) {
            case MINER -> 0xFFEF3926;
            case FARMER -> 0xFFFAC400;
            case HUNTER -> 0xFF2F5BEA;
            case ALCHEMIST -> 0xFFEC4FB5;
        };
    }

    /** « 1 h 36 », « 25 min », « 2 j 4 h ». */
    private static String until(long minutes) {
        long days = minutes / 1440L;
        long hours = minutes / 60L % 24L;
        if (days > 0) {
            return days + " j " + hours + " h";
        }
        if (hours > 0) {
            return hours + " h " + String.format(java.util.Locale.ROOT, "%02d", minutes % 60L);
        }
        return Math.max(1L, minutes) + " min";
    }

    /** Le jour en trois lettres s'il n'est pas aujourd'hui (« MAR »). */
    private static String day(java.time.ZonedDateTime at, java.time.ZonedDateTime now) {
        if (at.toLocalDate().equals(now.toLocalDate())) {
            return "";
        }
        return switch (at.getDayOfWeek()) {
            case MONDAY -> "LUN ";
            case TUESDAY -> "MAR ";
            case WEDNESDAY -> "MER ";
            case THURSDAY -> "JEU ";
            case FRIDAY -> "VEN ";
            case SATURDAY -> "SAM ";
            case SUNDAY -> "DIM ";
        };
    }

    /** Les jours restants avant la fin du mois (pass, concours). */
    private static long daysLeftInMonth() {
        java.time.ZonedDateTime now = fr.atraxterra.dohy.pass.PassSeason.now();
        java.time.ZonedDateTime end = now.withDayOfMonth(1).toLocalDate().plusMonths(1).atStartOfDay(now.getZone());
        return Math.max(0L, java.time.Duration.between(now, end).toDays());
    }

    /** Le clicker : ses ClicCoins et sa production, s'il a deja servi. */
    private static Card clickerCard(ServerPlayer player) {
        var data = player.getExistingDataOrNull(fr.atraxterra.dohy.DoHyAttachments.CLICKER.get());
        boolean started = data != null && !data.isEmpty();
        return Card.of(new ItemStack(Items.COOKIE), "Clicker", started ? List.of(
                        fr.atraxterra.dohy.palamod.clicker.ClickerFormat.compact(data.points()) + " ClicCoins",
                        "+" + fr.atraxterra.dohy.palamod.clicker.ClickerFormat.rate(data.rps()) + " / s")
                        : List.of("Nourris les namuus"))
                .accent(0xFFFAC400).action("clicker");
    }

    /** Les succes : combien sont valides. */
    private static Card achievementCard(ServerPlayer player) {
        int done = Achievements.data(player).doneCount();
        int total = Achievements.total();
        return Card.of(new ItemStack(Items.GOLDEN_APPLE), "Succes", List.of(done + " / " + total + " valides",
                        "Une prime par succes"))
                .accent(0xFFFAC400).action("achievements").progress(done, total);
    }

    /** Le pass du mois : le niveau, et les recompenses a recuperer. */
    /** Les boss de metier : combien attendent dans l'arene, combien sont en route. */
    private static Card altarCard(ServerPlayer player) {
        int ready = 0;
        int coming = 0;
        for (fr.atraxterra.dohy.job.Job job : fr.atraxterra.dohy.job.Job.values()) {
            switch (fr.atraxterra.dohy.jobboss.JobBossApi.state(player, job)) {
                case READY -> ready++;
                case COMING -> coming++;
                default -> {
                }
            }
        }
        String readyJob = "";
        for (fr.atraxterra.dohy.job.Job job : fr.atraxterra.dohy.job.Job.values()) {
            if (fr.atraxterra.dohy.jobboss.JobBossApi.state(player, job)
                    == fr.atraxterra.dohy.jobboss.JobBossApi.State.READY) {
                readyJob = job.display();
                break;
            }
        }
        return Card.of(new ItemStack(Items.IRON_SWORD), "Boss de metier", List.of(
                        ready > 0 ? "Gardien du " + readyJob : coming > 0 ? coming + " en route" : "Une offrande a l'autel"))
                .accent(0xFFFF6F00).action("altar").badge(ready > 0 ? "PRET" : "");
    }

    private static Card passCard(ServerPlayer player) {
        fr.atraxterra.dohy.pass.PassApi.rollIfDue(player);
        fr.atraxterra.dohy.pass.PassData data = fr.atraxterra.dohy.pass.PassApi.data(player);
        int season = data.isEmpty() ? fr.atraxterra.dohy.pass.PassSeason.current() : data.season();
        int level = fr.atraxterra.dohy.pass.PassApi.level(data);
        int levels = fr.atraxterra.dohy.pass.PassConfig.config().levels();
        int claimable = fr.atraxterra.dohy.pass.PassApi.claimable(player);
        fr.atraxterra.dohy.pass.Relic relic = fr.atraxterra.dohy.pass.PassApi.relic(season);
        return Card.of(fr.atraxterra.dohy.pass.DoHyPass.relic(relic, season),
                        "Pass " + fr.atraxterra.dohy.pass.PassSeason.ofMonth(season), List.of(
                        claimable > 0 ? claimable + (claimable > 1 ? " recompenses" : " recompense") : "Niveau " + level,
                        "", "NIVEAU " + level + " / " + levels + "|" + daysLeftInMonth() + " J"))
                .accent(0xFFEC4FB5).action("pass").progress(level, levels)
                .badge(claimable > 0 ? "+" + claimable : "");
    }

    /** Le concours Trixium : le rang du mois, et les paliers a recuperer. */
    private static Card trixiumCard(ServerPlayer player) {
        TrixiumContest.catchUp(player);
        TrixiumData data = TrixiumContest.data(player);
        int claimable = TrixiumContest.claimable(player);
        return Card.of(new ItemStack(PalaItems.TRIXIUM.get()), "Concours Trixium", List.of(
                        data.deposited() > 0 ? "Rang " + TrixiumContest.rank(player) + " / "
                                + (TrixiumRivals.COUNT + 1) : "Pas encore de depot",
                        MoneyFormat.shortForm(data.deposited()) + " depose(s) ce mois"))
                .accent(0xFF2BC4C4).action("trixium").badge(claimable > 0 ? "+" + claimable : "");
    }

    private static Card factionCard(ServerPlayer player) {
        fr.atraxterra.dohy.faction.FactionData faction = fr.atraxterra.dohy.faction.FactionApi.data(player);
        if (!faction.exists()) {
            return Card.of(new ItemStack(Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE)), "Faction", List.of("Fonde la tienne",
                    fr.atraxterra.dohy.faction.FactionApi.CREATION_COST + " lingots de paladium"))
                    .accent(0xFFEC4FB5).action("faction");
        }
        fr.atraxterra.dohy.faction.FactionLeague league =
                fr.atraxterra.dohy.faction.FactionLeague.of(faction.ranking().league());
        return Card.of(fr.atraxterra.dohy.faction.FactionMenus.banner(faction), faction.name(), List.of(
                        "Ligue " + league.label(), MoneyFormat.shortForm(faction.ranking().elo()) + " ELO"))
                .accent(league.color()).action("faction");
    }

    /** Les menus que les boutons de l'inventaire et la barre laterale peuvent ouvrir. */
    private static final java.util.Set<String> INVENTORY_TARGETS =
            java.util.Set.of("shop", "contracts", "bank", "faction", "jobs", "trixium", "clicker", "encyclopedia",
                    "pass", "sell", "boutique", "market", "collections", "museum", "altar", "achievements", "upgrades",
                    "rpg", "plans");

    /**
     * Ouvre le menu demande par un bouton de l'inventaire ({@code InventoryNav}).
     *
     * <p>{@code hub} ouvre ce menu-ci ; les autres passent par les memes cartes
     * que le menu principal, donc par les memes regles. Un nom hors de la liste
     * ne fait rien : il vient du client.
     */
    public static void openTarget(ServerPlayer player, String target) {
        if ("hub".equals(target)) {
            INSTANCE.open(player);
        } else if (INVENTORY_TARGETS.contains(target)) {
            INSTANCE.card(player, target, 0);
        }
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        switch (key) {
            case "encyclopedia" -> fr.atraxterra.dohy.guide.EncyclopediaMenu.openIndex(player);
            case "pass" -> fr.atraxterra.dohy.pass.PassMenu.INSTANCE.open(player);
            case "altar" -> fr.atraxterra.dohy.jobboss.JobBossMenu.INSTANCE.openFromAfar(player);
            case "plans" -> fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player, "plans");
            case "faction" -> fr.atraxterra.dohy.faction.FactionMenus.Overview.INSTANCE.open(player);
            case "trixium" -> TrixiumMenu.INSTANCE.open(player);
            case "achievements" -> AchievementMenu.INSTANCE.open(player);
            case "spawn" -> {
                UiNet.close(player);
                fr.atraxterra.dohy.spawn.SpawnApi.toggle(player);
            }
            case "shop" -> ShopController.open(player);
            case "sell" -> {
                UiNet.forget(player);
                player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, viewer) -> new SellChestMenu(containerId, inventory, player),
                        Component.literal("Coffre de vente")));
            }
            case "boutique" -> BoutiqueController.INSTANCE.open(player);
            case "market" -> fr.atraxterra.dohy.auction.AuctionController.INSTANCE.open(player);
            case "bank" -> BankController.INSTANCE.open(player);
            case "clicker" -> fr.atraxterra.dohy.palamod.clicker.Clicker.open(player);
            case "contracts" -> ContractsController.INSTANCE.open(player);
            case "jobs" -> JobsController.open(player);
            case "collections" -> CollectionsController.INSTANCE.open(player);
            case "museum" -> MuseumController.INSTANCE.open(player);
            case "upgrades" -> UpgradesController.INSTANCE.open(player);
            case "rpg" -> {
                UiNet.forget(player);
                RpgMenu.INSTANCE.open(player);
            }
            default -> {
            }
        }
    }
}
