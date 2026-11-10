package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionEdit;
import fr.atraxterra.dohy.faction.FactionLeague;
import fr.atraxterra.dohy.faction.FactionMenus;
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
 * L'ecran des events de faction (lot 22f) : l'event en cours, le calendrier de
 * la semaine aux heures du serveur, et les cinq boss.
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
                            MoneyFormat.shortForm(fight.kind().elo()) + " ELO a la fin, selon le rang",
                            "Dans l'arene, tu gardes ton inventaire."));
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
                        "Faction rivale", "Deux combattants a la fois")).accent(0xFF55545C)
                        .tooltip(boss ? List.of("Ils frappent le boss et lui prennent du classement.",
                                "Ils ne t'attaquent que si tu les frappes.")
                                : List.of("Ils se battent entre eux et contre toi.",
                                "Ils visent la zone, le totem, ou la zone de depot.")));
            }
            sections.add(new MenuView.Section("EN COURS", "upgrade", MenuView.GRID, running));
        }

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
        return new MenuView(id(), "Events de faction", "Aux heures de Paladium, heure du serveur", stats,
                sections, true);
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
