package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionEdit;
import fr.atraxterra.dohy.faction.FactionLeague;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.marks.Marks;
import fr.atraxterra.dohy.faction.marks.MarksData;
import fr.atraxterra.dohy.faction.marks.MarksGoal;
import fr.atraxterra.dohy.faction.marks.MarksRound;
import fr.atraxterra.dohy.palamod.boss.BossKind;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran des events de faction (lot 22f) : l'event en cours, A vos marques
 * (lot 26), le calendrier de la semaine aux heures du serveur, et les cinq boss.
 */
public final class FactionEventMenu extends MenuController {

    public static final FactionEventMenu INSTANCE = new FactionEventMenu();

    private static final List<List<String>> ATTACKS = List.of(
            List.of("Mecabras : saisit a 5 blocs, 3 s", "Seisme : tous les 15 % de vie, 50 blocs",
                    "Tic-Tac : engrenage a travers l'armure"),
            List.of("Koudboul : projette qui frappe au contact", "Eruption : 25 blocs, immobilise 5 s",
                    "Belier : charge ; un bloc sur sa route l'etourdit"),
            List.of("Jetsable : tornade a 7 blocs", "Sunami : vague de sable, 25 blocs",
                    "Dechainement : tous les 20 % de vie, zombies"),
            List.of("Stupefreeze : gel a 15 blocs", "Charge : projette a 7 blocs de haut",
                    "Picaglagla : pics de glace, des 2 cibles"),
            List.of("Exploweb : toiles, Wither et cecite", "Morsure : poison II, 10 s",
                    "Captoile : cage de toiles et araignees"));

    private FactionEventMenu() {
        super("faction_events");
    }

    @Override
    public void open(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            FactionEdit.open(player);
            return;
        }
        super.open(player);
    }

    @Override
    protected void back(ServerPlayer player) {
        FactionMenus.Overview.INSTANCE.open(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        ZonedDateTime now = FactionEvents.now();
        List<MenuView.Section> sections = new ArrayList<>();

        EventRun fight = FactionEvents.active();
        if (fight != null) {
            List<Card> running = new ArrayList<>();
            float progress = fight.progress(player.level().getServer());
            Card card = Card.of(new ItemStack(fight.icon()), fight.title(), fight.status(player,
                            player.level().getServer()))
                    .accent(0xFFEF3826).badge("EN COURS")
                    .tooltip(List.of("Depuis " + fight.seconds() / 60 + " min",
                            MoneyFormat.shortForm(fight.kind().elo()) + " ELO selon le rang",
                            "Inventaire garde"));
            if (progress >= 0.0f) {
                card = card.progress(Math.round(progress * 1000.0f), 1000);
            }
            if (FactionEvents.inWarzone(player)) {
                card = card.action("leave|quitter l'arene");
            } else if (!fight.ended()) {
                card = card.action("join|rejoindre l'arene");
            }
            running.add(card);
            boolean boss = fight instanceof BossFight;
            for (EventRun.Rival rival : fight.rivals()) {
                running.add(Card.of(new ItemStack(Items.BANNER.pick(rival.color())), rival.name(), List.of(
                        "Faction rivale", "")).accent(0xFF55545C));
            }
            sections.add(new MenuView.Section("EN COURS", "upgrade", MenuView.GRID, running));
        }

        sections.add(marks(player, now));

        List<Card> calendar = new ArrayList<>();
        ZonedDateTime soonest = null;
        for (FactionEventKind kind : FactionEventKind.values()) {
            ZonedDateTime next = kind.next(now);
            Card card = Card.of(new ItemStack(kind.icon()), kind.label(), List.of(kind.schedule(),
                            kind.ready() ? "Dans " + until(now, next) : "Lot a venir"))
                    .badge(MoneyFormat.shortForm(kind.elo()) + " ELO")
                    .tooltip(List.of(kind.line1(), kind.line2()));
            if (kind.ready()) {
                card = card.accent(0xFFFAC400);
                if (soonest == null || next.isBefore(soonest)) {
                    soonest = next;
                }
            } else {
                card = card.state(MenuView.DISABLED);
            }
            calendar.add(card);
        }
        sections.add(new MenuView.Section("CALENDRIER", "resources", MenuView.GRID, calendar));

        List<Card> bosses = new ArrayList<>();
        BossKind upcoming = FactionEvents.bossOfTheSlot(FactionEventKind.BOSS.next(now));
        for (BossKind kind : BossKind.values()) {
            Card card = Card.of(new ItemStack(Items.WITHER_SKELETON_SKULL), kind.label(), List.of(kind.summary(),
                            Math.round(kind.width()) + " x " + Math.round(kind.height()) + " blocs"))
                    .tooltip(ATTACKS.get(kind.ordinal()))
                    .accent(kind == upcoming ? 0xFFFAC400 : 0xFF55545C);
            bosses.add(kind == upcoming ? card.badge("PROCHAIN") : card);
        }
        sections.add(new MenuView.Section("LES BOSS", "resources", MenuView.GRID, bosses));

        FactionLeague league = FactionLeague.of(data.ranking().league());
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("LIGUE", league.label().toUpperCase(), league.color()),
                new MenuView.Stat("ELO", MoneyFormat.shortForm(data.ranking().elo()), 0xFFFFFFFF),
                new MenuView.Stat("PROCHAIN", fight != null && !fight.ended() ? "EN COURS"
                        : soonest == null ? "-" : until(now, soonest), 0xFFFAC400));
        return new MenuView(id(), "Events de faction", "Heure de Paris", stats, sections, true)
                .withTabs(fr.atraxterra.dohy.faction.FactionMenus.tabs(player, fr.atraxterra.dohy.faction.FactionMenus.EVENTS));
    }

    /** A vos marques : la manche en cours, la prochaine, l'ELO du jour et les objectifs possibles. */
    private static MenuView.Section marks(ServerPlayer player, ZonedDateTime now) {
        List<Card> cards = new ArrayList<>();
        MarksRound round = Marks.active();
        MarksData data = Marks.data(player);
        if (round != null) {
            boolean done = data.done(round);
            long left = Marks.secondsLeft(round);
            List<String> tooltip = new ArrayList<>();
            tooltip.add(round.points() ? "Toutes les actions comptent :" : "Dans " + round.goal().where());
            if (round.points()) {
                tooltip.add(MarksRound.scale());
            }
            tooltip.add("+" + Marks.ELO + " ELO pour ta nation, " + Marks.DAILY_CAP + " par jour au plus");
            cards.add(Card.of(new ItemStack(round.points() ? Items.CLOCK : round.goal().icon()), "A vos marques",
                            List.of(round.objective(), done ? "Objectif atteint" : "Encore " + (left / 60L + 1L)
                                    + " min"))
                    .accent(done ? 0xFF3FB950 : 0xFFEF3826).badge(done ? "ATTEINT" : "EN COURS")
                    .progress(data.count(round), round.target())
                    .tooltip(tooltip));
        }
        cards.add(Card.of(new ItemStack(Items.CLOCK), "Prochaine manche", List.of("De 10 h 30 a 23 h 30",
                        "Dans " + until(now, Marks.next())))
                .accent(0xFFFAC400).badge("+" + Marks.ELO + " ELO")
                .tooltip(List.of("Une manche de 30 min chaque heure", "Une manche sur trois est aux points",
                        "Heure de Paris")));
        long elo = Marks.eloToday(player);
        cards.add(Card.of(new ItemStack(Items.GOLD_INGOT), "Aujourd'hui", List.of(elo + " / " + Marks.DAILY_CAP
                        + " ELO", data.wins() + (data.wins() > 1 ? " manches reussies" : " manche reussie")))
                .accent(0xFFFAC400).progress(elo, Marks.DAILY_CAP)
                .tooltip(List.of("Au plus " + Marks.DAILY_CAP + " ELO par jour", "Remise a zero a minuit")));
        for (MarksGoal goal : MarksGoal.values()) {
            cards.add(Card.of(new ItemStack(goal.icon()), goal.label(), List.of(MoneyFormat.exact(goal.target())
                            + " " + goal.unit(), goal.places()))
                    .accent(round != null && !round.points() && round.goal() == goal ? 0xFFEF3826 : 0xFF55545C)
                    .tooltip(List.of("Dans " + goal.where(),
                            "Aux points : " + goal.points() + " par " + goal.single())));
        }
        cards.add(Card.of(new ItemStack(Items.CLOCK), "Aux points", List.of(MoneyFormat.exact(MarksRound.POINTS_TARGET)
                        + " points", "Toutes les actions"))
                .accent(round != null && round.points() ? 0xFFEF3826 : 0xFF55545C)
                .tooltip(List.of("La deuxieme variante :", MarksRound.scale())));
        return new MenuView.Section("A VOS MARQUES", "resources", MenuView.GRID, cards);
    }

    /** « 3 h 12 », « 25 min », « 2 j 4 h ». */
    static String until(ZonedDateTime now, ZonedDateTime next) {
        long minutes = Math.max(0L, Duration.between(now, next).toMinutes());
        long days = minutes / 1440L;
        long hours = minutes / 60L % 24L;
        if (days > 0) {
            return days + " j " + hours + " h";
        }
        return hours > 0 ? hours + " h " + String.format("%02d", minutes % 60L) : minutes + " min";
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        switch (key) {
            case "join" -> report(player, FactionEvents.join(player), "Tu entres dans l'arene.");
            case "leave" -> report(player, FactionEvents.leave(player), "Tu quittes l'arene.");
            default -> {
            }
        }
    }
}
