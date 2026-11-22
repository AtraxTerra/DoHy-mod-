package fr.atraxterra.dohy.faction.enterprise;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.org.Cadre;
import fr.atraxterra.dohy.faction.org.Organisations;
import fr.atraxterra.dohy.faction.org.Regime;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.PriceChart;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'onglet Entreprises de la nation (lot 24b) : le tableau de bord des affaires, chaque
 * entreprise et sa fiche, le catalogue de ce qu'on peut ouvrir la ou l'on est, les comptes,
 * l'influence pays par pays et les six organisations criminelles.
 */
public final class EnterpriseMenus {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.of("Europe/Paris"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] WEEK = {"Lun.", "Mar.", "Mer.", "Jeu.", "Ven.", "Sam.", "Dim."};

    private EnterpriseMenus() {
    }

    /** Une page des affaires : sans nation, on la fonde d'abord ; l'hexagone revient au tableau de bord. */
    private abstract static class Page extends MenuController {
        Page(String path) {
            super(path);
        }

        @Override
        public void open(ServerPlayer player) {
            if (!FactionApi.has(player)) {
                FactionMenus.Overview.INSTANCE.open(player);
                return;
            }
            super.open(player);
        }

        @Override
        protected void back(ServerPlayer player) {
            Hub.INSTANCE.open(player);
        }

        static List<MenuView.Tab> tabs(ServerPlayer player) {
            return FactionMenus.tabs(player, FactionMenus.ENTERPRISES);
        }
    }

    static void answer(ServerPlayer player, String refusal, String success) {
        if (refusal != null) {
            FactionApi.tell(player, refusal + ".", ChatFormatting.RED);
        } else if (!success.isEmpty()) {
            FactionApi.tell(player, success, ChatFormatting.GREEN);
        }
    }

    // -- Petits outils ----------------------------------------------------------------------------

    static String perHour(double amount) {
        long value = Math.round(amount);
        return (value >= 0 ? "+" : "-") + MoneyFormat.exact(Math.abs(value)) + " $ / h";
    }

    static int riskColor(double risk) {
        return risk >= 50.0d ? 0xFFEF3826 : risk >= 25.0d ? 0xFFFAC400 : 0xFF22E558;
    }

    static int influenceColor(int influence) {
        return influence >= 50 ? 0xFF22E558 : influence >= 20 ? 0xFFFAC400 : 0xFF9AA0A6;
    }

    static ItemStack icon(Enterprise enterprise) {
        Line line = enterprise.drug();
        if (line != null) {
            return enterprise.kind().role() == Sector.Role.GROW ? line.rawIcon() : line.productIcon();
        }
        return enterprise.kind().icon();
    }

    static String title(Sector sector, Line line) {
        return line == null ? sector.label() : sector.shortLabel() + " (" + line.label().toLowerCase() + ")";
    }

    static List<Card> journal(Holdings holdings, int max) {
        List<Card> cards = new ArrayList<>();
        for (String line : holdings.journal()) {
            int bar = line.indexOf('|');
            if (bar < 0) {
                continue;
            }
            long at;
            try {
                at = Long.parseLong(line.substring(0, bar));
            } catch (NumberFormatException error) {
                continue;
            }
            cards.add(Card.of(new ItemStack(Items.PAPER), line.substring(bar + 1), List.of(
                    WHEN.format(Instant.ofEpochMilli(at)))));
            if (cards.size() >= max) {
                break;
            }
        }
        return cards;
    }

    /** « 376 $ / h », sans signe : une estimation. */
    static String rate(double amount) {
        return MoneyFormat.exact(Math.round(amount)) + " $ / h";
    }

    /** Ce qu'une entreprise de ce secteur ferait en une heure, au niveau 1, ici. */
    static String estimate(Sector sector, Line line, Regime regime, int influence) {
        double factor = (sector.legal() ? regime.legal() : regime.illegal()) * Influence.revenue(sector, influence);
        return switch (sector.role()) {
            case REVENUE -> "~" + rate(sector.base() * factor);
            case GROW -> line == null ? "" : "~" + Math.round(line.yield() * factor) + " "
                    + line.rawLabel().toLowerCase() + " / h";
            case LAB -> "~" + Math.round(sector.base() * factor) + " unites / h";
            case STORE -> "+" + Math.round(sector.base()) + " places par stock";
            case SELL -> "~" + Math.round(sector.base() * factor) + " unites vendues / h";
            case ARMS -> "~" + Math.round(sector.base() * factor) + " caisses / h";
            case ARMS_SELL -> "~" + Math.round(sector.base() * factor) + " caisses vendues / h";
            case SMUGGLE, CASINO, FENCE -> "~" + rate(sector.base() * factor) + " sales";
            case RACKET -> "~" + rate(sector.base() * factor * influence / 50.0d) + " sales";
            case LAUNDER -> "Blanchit ~" + MoneyFormat.exact(Math.round(sector.base() * factor)) + " $ / h";
        };
    }

    /** Le risque de depart d'une affaire de ce secteur, ici, au niveau 1. */
    static long risk(Sector sector, Line line, Regime regime, int influence) {
        if (sector.legal()) {
            return 0L;
        }
        double risk = (sector.risk() + (line == null ? 0.0d : line.risk()) + 0.5d * sector.staff()) * regime.risk()
                * Influence.risk(influence);
        return Math.round(Math.max(0.0d, Math.min(95.0d, risk)));
    }

    // -- Le tableau de bord -----------------------------------------------------------------------

    public static final class Hub extends Page {
        public static final Hub INSTANCE = new Hub();

        private Hub() {
            super("faction_enterprises");
        }

        @Override
        protected void back(ServerPlayer player) {
            FactionMenus.Overview.INSTANCE.open(player);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Holdings holdings = Enterprises.of(player);
            long now = System.currentTimeMillis();
            Payout.Preview preview = Payout.preview(player);
            Payout.World world = new Payout.World(player, holdings, now);
            List<Enterprise> enterprises = holdings.enterprises();

            double riskSum = 0.0d;
            int illegal = 0;
            long stocked = 0L;
            for (Enterprise enterprise : enterprises) {
                if (!enterprise.legal()) {
                    riskSum += world.factors(enterprise).risk();
                    illegal++;
                }
            }
            for (Map.Entry<String, Long> entry : holdings.stock.entrySet()) {
                stocked += entry.getValue();
            }
            double risk = illegal == 0 ? 0.0d : riskSum / illegal;

            List<Card> board = new ArrayList<>();
            board.add(Card.of(new ItemStack(Items.EMERALD), "Revenus propres", List.of(perHour(preview.clean()),
                            "Vers la banque nationale"))
                    .accent(0xFF22E558).action("finance|voir les comptes"));
            board.add(Card.of(new ItemStack(Items.REDSTONE), "Argent sale", List.of(perHour(preview.dirty()),
                            "Solde : " + MoneyFormat.exact(holdings.dirty()) + " $"))
                    .accent(0xFFEC4FB5).action("finance|voir les comptes")
                    .tooltip(List.of("Ce que rapportent les affaires illegales", "A blanchir avant d'aller a la banque",
                            "Il paie d'abord tes affaires illegales et tes accords")));
            board.add(Card.of(new ItemStack(Items.PAPER), "Frais", List.of(perHour(-preview.costs()),
                            "Loyers, materiel, salaires"))
                    .accent(0xFFEF3826).action("finance|voir les comptes"));
            List<String> washing = new ArrayList<>();
            washing.add(perHour(preview.laundered()) + " propres");
            washing.add(preview.laundered() <= 0.0d && holdings.dirty() > 0L ? "Ouvre une societe ecran"
                    : "Commission : " + MoneyFormat.exact(Math.round(preview.fees())) + " $ / h");
            board.add(Card.of(new ItemStack(Items.WATER_BUCKET), "Blanchiment", washing).accent(0xFF27C4E8)
                    .action("finance|voir les comptes"));
            board.add(Card.of(new ItemStack(Items.BARREL), "Stocks", List.of(MoneyFormat.exact(stocked) + " unite(s)",
                            MoneyFormat.exact(world.capacity) + " places par produit"))
                    .accent(0xFFA0522D).action("finance|voir les comptes"));
            long next = holdings.paidUntil() <= 0L ? 0L : Math.max(60_000L, holdings.paidUntil() + Enterprises.HOUR_MS
                    - now);
            Holdings.Report report = holdings.report();
            board.add(Card.of(new ItemStack(Items.CLOCK), "Prochain versement", List.of(
                            enterprises.isEmpty() ? "Aucune entreprise" : "Dans " + Organisations.duration(next),
                            report.time() <= 0L ? "Chaque heure, meme hors ligne" : "Dernier : "
                                    + MoneyFormat.delta(report.clean() + report.laundered() - report.costs()) + " $ ("
                                    + report.hours() + " h)"))
                    .accent(0xFF9AA0A6));

            List<Card> owned = new ArrayList<>();
            for (Enterprise enterprise : enterprises) {
                Payout.Factors factors = world.factors(enterprise);
                double earned = preview.earned().getOrDefault(enterprise.id(), 0.0d);
                boolean closed = enterprise.closed(now);
                List<String> lines = new ArrayList<>();
                lines.add(title(enterprise.kind(), enterprise.drug()) + " · niveau " + enterprise.level() + " · "
                        + Enterprises.countryName(enterprise.country()));
                String second = closed ? "Fermee encore " + Organisations.duration(enterprise.closedUntil() - now)
                        : (earned > 0.0d ? perHour(earned) + (enterprise.legal() ? "" : " sales") : "Ne vend rien")
                        + " · frais " + MoneyFormat.exact(Math.round(factors.cost())) + " $";
                if (!enterprise.legal() && !closed) {
                    second += " · risque " + Math.round(factors.risk()) + " %";
                }
                lines.add(second);
                Card card = Card.of(icon(enterprise), enterprise.name(), lines).accent(closed ? 0xFF55545C
                                : enterprise.kind().color())
                        .badge(closed ? "FERMEE" : enterprise.legal() ? "" : "ILLEGALE")
                        .action("open:" + enterprise.id() + "|gerer");
                card = enterprise.legal() ? card.progress(enterprise.level(), Enterprise.MAX_LEVEL)
                        : card.progress(Math.round(factors.risk()), 100L);
                owned.add(card);
            }
            if (owned.isEmpty()) {
                owned.add(Card.of(new ItemStack(Items.OAK_SAPLING), "Aucune entreprise", List.of(
                                "Va dans un pays de la carte et ouvres-en une",
                                "Elles rapportent chaque heure, meme hors ligne"))
                        .state(MenuView.DISABLED));
            }

            Enterprises.Site site = Enterprises.here(player);
            int here = Influence.value(player, site.country());
            List<Card> open = new ArrayList<>();
            Card found = Card.of(new ItemStack(Items.WRITABLE_BOOK), "Ouvrir une entreprise", List.of(
                    "Ici : " + site.countryName() + (site.region() == null ? "" : " · " + site.region().label()),
                    site.refusal() != null ? site.refusal() : "Influence " + here + " · " + enterprises.size() + " / "
                            + Enterprises.cap(player) + " entreprises"));
            open.add(site.refusal() != null ? found.state(MenuView.DISABLED) : found.accent(0xFF22E558)
                    .action("catalog|choisir"));
            List<Influence.Reading> readings = Influence.all(player);
            open.add(Card.of(new ItemStack(Items.MAP), "Territoire et influence", List.of(
                            readings.size() + " pays ou ta nation compte",
                            readings.isEmpty() ? "Tiens des pays, claim, ouvre des affaires"
                                    : "Le plus fort : " + Enterprises.countryName(readings.get(0).country()) + " "
                                    + readings.get(0).value()))
                    .accent(0xFFFAC400).action("territory|ouvrir"));
            open.add(Card.of(new ItemStack(Items.BOOK), "Comptes", List.of("Les 48 dernieres heures",
                            "Les 7 derniers jours"))
                    .accent(0xFF27C4E8).action("finance|ouvrir"));

            List<Card> syndicates = new ArrayList<>();
            for (Syndicate syndicate : Syndicate.values()) {
                Holdings.Standing standing = holdings.standing(syndicate);
                Holdings.Status status = standing.state();
                syndicates.add(Card.of(syndicate.icon(), syndicate.label(), List.of(syndicate.region().label(),
                                status.label() + " · hostilite " + Math.round(standing.hostility())))
                        .accent(syndicate.color()).progress(Math.round(standing.hostility()), 100L)
                        .badge(switch (status) {
                            case NEUTRE -> "";
                            case ACCORD -> "ACCORD";
                            case ALLIANCE -> "ALLIEE";
                            case GUERRE -> "GUERRE";
                        })
                        .action("syndicate:" + syndicate.name() + "|voir"));
            }

            List<MenuView.Stat> stats = new ArrayList<>();
            stats.add(new MenuView.Stat("NET / H", MoneyFormat.delta(Math.round(preview.net())) + " $",
                    preview.net() >= 0.0d ? 0xFF22E558 : 0xFFEF3826));
            stats.add(new MenuView.Stat("ARGENT SALE", MoneyFormat.shortForm(holdings.dirty()) + " $", 0xFFEC4FB5));
            stats.add(new MenuView.Stat("RISQUE", illegal == 0 ? "-" : Math.round(risk) + " %", riskColor(risk)));

            List<MenuView.Section> sections = new ArrayList<>();
            sections.add(new MenuView.Section("TABLEAU DE BORD", "resources", MenuView.GRID, board));
            sections.add(new MenuView.Section("TES ENTREPRISES|" + enterprises.size() + " / "
                    + Enterprises.cap(player), "craft", MenuView.ROWS, owned));
            sections.add(new MenuView.Section("OUVRIR ET GERER", "upgrade", MenuView.GRID, open));
            sections.add(new MenuView.Section("ORGANISATIONS CRIMINELLES", "armor", MenuView.GRID, syndicates));
            List<Card> journal = journal(holdings, 6);
            if (!journal.isEmpty()) {
                sections.add(new MenuView.Section("JOURNAL", "resources", MenuView.AGENDA, journal));
            }
            return new MenuView(id(), "Entreprises", "Affaires legales et illegales, revenus, risque et organisations",
                    stats, sections, true).withTabs(tabs(player));
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (FactionMenus.opened(player, key)) {
                return;
            }
            if (key.startsWith("open:")) {
                Detail.INSTANCE.show(player, key.substring(5));
            } else if (key.equals("catalog")) {
                Catalog.INSTANCE.open(player);
            } else if (key.equals("territory")) {
                Territory.INSTANCE.open(player);
            } else if (key.equals("finance")) {
                Finance.INSTANCE.open(player);
            } else if (key.startsWith("syndicate:")) {
                Syndicate syndicate = Syndicate.of(key.substring(10));
                if (syndicate != null) {
                    SyndicatePage.INSTANCE.show(player, syndicate);
                }
            }
        }
    }

    // -- Le catalogue -----------------------------------------------------------------------------

    public static final class Catalog extends Page {
        public static final Catalog INSTANCE = new Catalog();

        private Catalog() {
            super("faction_enterprise_found");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Enterprises.Site site = Enterprises.here(player);
            int influence = Influence.value(player, site.country());
            Regime regime = Organisations.regime(player);
            List<Card> legal = new ArrayList<>();
            List<Card> illegal = new ArrayList<>();
            for (Sector sector : Sector.values()) {
                List<Line> lines = new ArrayList<>();
                if (sector.lined()) {
                    for (Line line : Line.values()) {
                        if (sector.role() != Sector.Role.GROW || line.grows()) {
                            lines.add(line);
                        }
                    }
                } else {
                    lines.add(null);
                }
                for (Line line : lines) {
                    (sector.legal() ? legal : illegal).add(offer(player, site, sector, line, regime, influence));
                }
            }
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("ICI", site.countryName().toUpperCase(), 0xFFFFFFFF),
                    new MenuView.Stat("INFLUENCE", String.valueOf(influence), influenceColor(influence)),
                    new MenuView.Stat("ENTREPRISES", Enterprises.of(player).enterprises().size() + " / "
                            + Enterprises.cap(player), 0xFFFAC400));
            List<MenuView.Section> sections = new ArrayList<>();
            if (site.refusal() != null) {
                sections.add(new MenuView.Section("ICI", "craft", MenuView.GRID, List.of(Card.of(
                        new ItemStack(Items.BARRIER), "Impossible d'ouvrir ici", List.of(site.refusal()))
                        .state(MenuView.DISABLED))));
            }
            sections.add(new MenuView.Section("LEGALES|argent propre, a la banque nationale", "resources",
                    MenuView.GRID, legal));
            sections.add(new MenuView.Section("ILLEGALES|payees en argent sale d'abord", "armor", MenuView.GRID,
                    illegal));
            return new MenuView(id(), "Ouvrir une entreprise", "Ce que tu peux ouvrir la ou tu te tiens, et ce que "
                    + "ca rapporte", stats, sections, true).withTabs(tabs(player));
        }

        private static Card offer(ServerPlayer player, Enterprises.Site site, Sector sector, Line line, Regime regime,
                                  int influence) {
            String refusal = Enterprises.refusal(player, sector, line, site);
            long risk = risk(sector, line, regime, influence);
            ItemStack icon = line == null ? sector.icon()
                    : sector.role() == Sector.Role.GROW ? line.rawIcon() : line.productIcon();
            List<String> lines = new ArrayList<>();
            lines.add(MoneyFormat.exact(sector.price()) + " $ · " + estimate(sector, line, regime, influence));
            String detail = line == null ? sector.summary() : sector.role() == Sector.Role.GROW ? line.climate()
                    : line.rawLabel().toLowerCase() + " > " + line.productLabel().toLowerCase();
            lines.add(refusal != null ? refusal : sector.legal() ? sector.summary() : "Risque ~" + risk + " % · "
                    + detail);
            List<String> tooltip = new ArrayList<>();
            tooltip.add(sector.summary());
            if (line != null) {
                tooltip.add(line.rawLabel() + " > " + line.productLabel() + " : rue " + line.street() + " $ l'unite");
            }
            tooltip.add("Frais ~" + MoneyFormat.exact(Math.round(sector.cost() + Enterprises.WAGE * sector.staff()))
                    + " $ / h, " + sector.staff() + " employe(s)");
            Card card = Card.of(icon, title(sector, line), lines).accent(sector.color()).tooltip(tooltip);
            if (refusal != null) {
                return card.state(MenuView.DISABLED);
            }
            String key = "found:" + sector.name() + (line == null ? "" : ":" + line.name());
            return card.action(key + "|ouvrir").confirm("Ouvrir : " + title(sector, line) + " ?|"
                    + MoneyFormat.exact(sector.price()) + " $ " + (sector.legal() ? "depuis la banque nationale"
                    : "en argent sale d'abord, puis la banque") + ", a " + site.countryName());
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (!key.startsWith("found:")) {
                return;
            }
            String[] parts = key.substring(6).split(":");
            Sector sector = Sector.of(parts[0]);
            Line line = parts.length > 1 ? Line.of(parts[1]) : null;
            if (sector == null) {
                return;
            }
            String refusal = Enterprises.found(player, sector, line);
            answer(player, refusal, title(sector, line) + " ouvre ses portes.");
            if (refusal == null) {
                Hub.INSTANCE.open(player);
            }
        }
    }

    // -- La fiche d'une entreprise ----------------------------------------------------------------

    public static final class Detail extends Page {
        public static final Detail INSTANCE = new Detail();
        private final Map<UUID, String> selected = new ConcurrentHashMap<>();

        private Detail() {
            super("faction_enterprise");
        }

        public void show(ServerPlayer player, String id) {
            selected.put(player.getUUID(), id);
            open(player);
        }

        private Enterprise enterprise(ServerPlayer player) {
            String id = selected.get(player.getUUID());
            if (id == null) {
                List<Enterprise> all = Enterprises.of(player).enterprises();
                return all.isEmpty() ? null : all.get(0);
            }
            return Enterprises.find(player, id);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Enterprise enterprise = enterprise(player);
            if (enterprise == null) {
                return new MenuView(id(), "Entreprise", "Elle n'existe plus", List.of(), List.of(
                        new MenuView.Section("ENTREPRISE", "craft", MenuView.GRID, List.of(Card.of(
                                new ItemStack(Items.BARRIER), "Fermee", List.of("Cette entreprise n'existe plus"))
                                .state(MenuView.DISABLED)))), true).withTabs(tabs(player));
            }
            Holdings holdings = Enterprises.of(player);
            long now = System.currentTimeMillis();
            Payout.World world = new Payout.World(player, holdings, now);
            Payout.Factors factors = world.factors(enterprise);
            Payout.Preview preview = Payout.preview(player);
            double earned = preview.earned().getOrDefault(enterprise.id(), 0.0d);
            Sector sector = enterprise.kind();
            Line line = enterprise.drug();
            boolean closed = enterprise.closed(now);

            List<Card> activity = new ArrayList<>();
            activity.add(doing(enterprise, sector, line, factors, earned, holdings, world));
            activity.add(Card.of(new ItemStack(Items.PAPER), "Frais", List.of(perHour(-factors.cost()),
                            enterprise.staff() + " employe(s) a " + Math.round(Enterprises.WAGE) + " $ / h"))
                    .accent(0xFFEF3826)
                    .tooltip(List.of("Loyer, entretien et materiel, plus les salaires",
                            "Le Tresorier les reduit jusqu'a 20 %", "Fermee, elle coute moitie moins")));
            Influence.Reading reading = world.influence.read(enterprise.country());
            Region region = world.region(enterprise.country());
            Syndicate syndicate = Syndicate.in(region);
            activity.add(Card.of(new ItemStack(Items.MAP), Enterprises.countryName(enterprise.country()), List.of(
                            "Influence " + reading.value() + (region == null ? "" : " · " + region.label()),
                            syndicate == null ? "Hors des territoires du crime" : "Territoire " + syndicate.of()))
                    .accent(influenceColor(reading.value())).progress(reading.value(), 100L)
                    .action("territory|voir l'influence"));
            activity.add(Card.of(new ItemStack(closed ? Items.IRON_BARS : Items.TORCH),
                            closed ? "Fermee" : "Ouverte", List.of(
                            closed ? "Rouvre dans " + Organisations.duration(enterprise.closedUntil() - now)
                                    : "Depuis le " + WHEN.format(Instant.ofEpochMilli(enterprise.founded())),
                            "A rapporte " + MoneyFormat.exact(enterprise.earned()) + " $ en tout"))
                    .accent(closed ? 0xFFEF3826 : 0xFF22E558)
                    .tooltip(List.of("Capital investi : " + MoneyFormat.exact(enterprise.capital()) + " $",
                            "Renommer : /f entreprises renommer " + enterprise.id() + " <nom>")));

            List<Card> manage = new ArrayList<>();
            if (enterprise.level() >= Enterprise.MAX_LEVEL) {
                manage.add(Card.of(new ItemStack(Items.NETHER_STAR), "Niveau maximal", List.of("Niveau 5",
                        "Production " + Payout.times(Enterprises.OUTPUT[4]))).state(MenuView.DONE));
            } else {
                long price = Enterprises.levelPrice(enterprise);
                int next = enterprise.level() + 1;
                manage.add(Card.of(new ItemStack(Items.EXPERIENCE_BOTTLE), "Passer au niveau " + next, List.of(
                                MoneyFormat.exact(price) + " $" + (enterprise.legal() ? "" : " (argent sale d'abord)"),
                                "Production " + Payout.times(Enterprises.OUTPUT[next - 1]) + ", frais "
                                        + Payout.times(Enterprises.COSTS[next - 1])))
                        .accent(0xFFFAC400).action("level|agrandir").confirm("Passer " + enterprise.name()
                                + " au niveau " + next + " ?|" + MoneyFormat.exact(price) + " $"));
            }
            manage.add(Card.of(new ItemStack(Items.PLAYER_HEAD), "Employes " + enterprise.staff() + " / "
                            + enterprise.maxStaff(), List.of("Clic : embaucher · clic droit : renvoyer",
                            "Chacun : " + Math.round(Enterprises.WAGE) + " $ / h"
                                    + (enterprise.legal() ? "" : ", +0,5 de risque")))
                    .accent(0xFF27C4E8).progress(enterprise.staff(), Math.max(1, enterprise.maxStaff()))
                    .action("staff+|embaucher").right("staff-|renvoyer"));
            Optional<Cadre> manager = Enterprises.manager(player, enterprise);
            if (manager.isPresent()) {
                Cadre cadre = manager.get();
                double strength = Enterprises.managerStrength(cadre);
                manage.add(Card.of(new ItemStack(Items.GOLDEN_HELMET), "Gerant : " + cadre.name(), List.of(
                                cadre.rank().label() + " · talent " + cadre.skill() + " / 5",
                                Payout.percent(1.0d + 0.2d * strength) + " de production"
                                        + (enterprise.legal() ? "" : ", " + Payout.percent(1.0d - 0.2d
                                        * Math.min(1.0d, strength)) + " de risque")))
                        .accent(0xFFFAC400).action("unmanage|le liberer"));
            } else {
                Optional<Cadre> best = Organisations.bestFree(player);
                Card card = Card.of(new ItemStack(Items.LEATHER_HELMET), "Aucun gerant", List.of(
                        best.map(cadre -> "Confier a " + cadre.name()).orElse("Aucun cadre libre"),
                        "Un cadre de l'onglet Organisation"));
                manage.add(best.isPresent() ? card.accent(0xFF22E558).action("manage|nommer")
                        : card.state(MenuView.DISABLED));
            }
            long refund = Enterprises.refund(enterprise);
            manage.add(Card.of(new ItemStack(Items.BARRIER), "Fermer", List.of("Rend " + MoneyFormat.exact(refund)
                            + " $" + (enterprise.legal() ? "" : " sales"), "40 % de ce qui y a ete investi"))
                    .accent(0xFFEF3826).action("close|fermer").confirm("!Fermer " + enterprise.name() + " ?|Elle "
                            + "disparait ; il te reste " + MoneyFormat.exact(refund) + " $"));

            List<Card> upgrades = new ArrayList<>();
            for (Upgrade upgrade : Upgrade.values()) {
                boolean has = enterprise.has(upgrade);
                long price = upgrade.price(sector);
                Card card = Card.of(upgrade.icon(), upgrade.label(sector), List.of(upgrade.effect(sector),
                        has ? "Achetee" : MoneyFormat.exact(price) + " $")).accent(0xFF8B5CF6);
                upgrades.add(has ? card.state(MenuView.DONE) : card.action("upgrade:" + upgrade.name() + "|acheter")
                        .confirm(upgrade.label(sector) + " pour " + enterprise.name() + " ?|"
                                + MoneyFormat.exact(price) + " $"));
            }

            List<String> notes = new ArrayList<>();
            for (String note : factors.notes()) {
                notes.add("- " + note);
            }
            if (!enterprise.legal()) {
                notes.add("! Risque " + Math.round(factors.risk()) + " % : chaque heure, une descente peut la fermer 24 h "
                        + "et saisir une part des stocks et de l'argent sale");
            }

            List<MenuView.Stat> stats = new ArrayList<>();
            stats.add(new MenuView.Stat("NIVEAU", enterprise.level() + " / " + Enterprise.MAX_LEVEL, 0xFFFAC400));
            stats.add(switch (sector.role()) {
                case GROW -> new MenuView.Stat("PRODUCTION", "~" + Math.round(line == null ? 0.0d
                        : line.yield() * factors.output()) + " U/H", 0xFF22E558);
                case LAB, ARMS -> new MenuView.Stat("PRODUCTION", "~" + Math.round(sector.base() * factors.output())
                        + (sector.role() == Sector.Role.ARMS ? " CAISSES/H" : " U/H"), 0xFFEC4FB5);
                case STORE -> new MenuView.Stat("PLACE", "+" + MoneyFormat.shortForm(Math.round(sector.base()
                        * enterprise.level() * (enterprise.has(Upgrade.EQUIPEMENT) ? 1.25d : 1.0d))), 0xFFA0522D);
                default -> new MenuView.Stat("PAR HEURE", MoneyFormat.delta(Math.round(earned)) + " $",
                        enterprise.legal() ? 0xFF22E558 : 0xFFEC4FB5);
            });
            stats.add(enterprise.legal() ? new MenuView.Stat("FRAIS", "-" + MoneyFormat.shortForm(Math.round(
                    factors.cost())) + " $", 0xFFEF3826) : new MenuView.Stat("RISQUE", Math.round(factors.risk())
                    + " %", riskColor(factors.risk())));
            List<MenuView.Section> sections = new ArrayList<>();
            sections.add(new MenuView.Section("ACTIVITE", "resources", MenuView.GRID, activity));
            sections.add(new MenuView.Section("GESTION", "craft", MenuView.GRID, manage));
            sections.add(new MenuView.Section("AMELIORATIONS", "upgrade", MenuView.GRID, upgrades));
            sections.add(new MenuView.Section("CE QUI COMPTE", "resources", MenuView.TEXT, List.of(
                    Card.of(ItemStack.EMPTY, "", notes))));
            return new MenuView(id(), enterprise.name(), title(sector, line) + " · " + sector.summary(), stats,
                    sections, true).withTabs(tabs(player));
        }

        /** La carte de ce que fait l'entreprise chaque heure. */
        private static Card doing(Enterprise enterprise, Sector sector, Line line, Payout.Factors factors,
                                  double earned, Holdings holdings, Payout.World world) {
            double output = factors.output();
            List<String> lines = new ArrayList<>();
            String title;
            switch (sector.role()) {
                case REVENUE -> {
                    title = "Rapporte";
                    lines.add(perHour(earned));
                    lines.add("Argent propre, a la banque");
                }
                case GROW -> {
                    title = "Fait pousser";
                    lines.add("~" + Math.round(line == null ? 0.0d : line.yield() * output) + " "
                            + (line == null ? "" : line.rawLabel().toLowerCase()) + " / h");
                    lines.add("Stock : " + (line == null ? 0 : holdings.stock(line.rawKey())) + " / "
                            + world.capacity);
                }
                case LAB -> {
                    title = "Transforme";
                    lines.add("~" + Math.round(sector.base() * output) + " unites / h");
                    lines.add(line == null ? "" : line.rawLabel() + " " + holdings.stock(line.rawKey()) + " · "
                            + line.productLabel().toLowerCase() + " " + holdings.stock(line.productKey()));
                }
                case STORE -> {
                    title = "Garde";
                    lines.add("+" + Math.round(sector.base() * enterprise.level()
                            * (enterprise.has(Upgrade.EQUIPEMENT) ? 1.25d : 1.0d)) + " places par stock");
                    lines.add("Saisies deux fois moindres");
                }
                case SELL -> {
                    title = "Ecoule";
                    lines.add("~" + Math.round(sector.base() * output) + " unites / h");
                    lines.add(perHour(earned) + " sales");
                }
                case ARMS -> {
                    title = "Monte";
                    lines.add("~" + Math.round(sector.base() * output) + " caisses / h");
                    lines.add("Fer du coffre : " + world.iron + " (2 par caisse)");
                }
                case ARMS_SELL -> {
                    title = "Vend";
                    lines.add("~" + Math.round(sector.base() * output) + " caisses / h");
                    lines.add(perHour(earned) + " sales" + (world.atWar ? " (guerre : +40 %)" : ""));
                }
                case LAUNDER -> {
                    title = "Blanchit";
                    lines.add("~" + MoneyFormat.exact(Math.round(sector.base() * output)) + " $ / h");
                    lines.add("Commission " + Math.round(Enterprises.facadeFee(enterprise) * 100.0d) + " %, "
                            + perHour(earned) + " propres");
                }
                default -> {
                    title = "Rapporte";
                    lines.add(perHour(earned) + " sales");
                    lines.add(sector.summary());
                }
            }
            return Card.of(icon(enterprise), title, lines).accent(sector.color());
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            Enterprise enterprise = enterprise(player);
            if (enterprise == null) {
                return;
            }
            String id = enterprise.id();
            switch (key) {
                case "level" -> answer(player, Enterprises.levelUp(player, id), enterprise.name() + " grandit.");
                case "staff+" -> answer(player, Enterprises.staff(player, id, 1), "");
                case "staff-" -> answer(player, Enterprises.staff(player, id, -1), "");
                case "manage" -> answer(player, Enterprises.manage(player, id), "Un gerant prend la tete de "
                        + enterprise.name() + ".");
                case "unmanage" -> answer(player, Enterprises.unmanage(player, id), "");
                case "territory" -> Territory.INSTANCE.open(player);
                case "close" -> {
                    String refusal = Enterprises.close(player, id);
                    answer(player, refusal, enterprise.name() + " ferme ses portes.");
                    if (refusal == null) {
                        Hub.INSTANCE.open(player);
                    }
                }
                default -> {
                    if (key.startsWith("upgrade:")) {
                        Upgrade upgrade = Upgrade.of(key.substring(8));
                        if (upgrade != null) {
                            answer(player, Enterprises.upgrade(player, id, upgrade), upgrade.label(enterprise.kind())
                                    + " : fait.");
                        }
                    }
                }
            }
        }
    }

    // -- Les comptes ------------------------------------------------------------------------------

    public static final class Finance extends Page {
        public static final Finance INSTANCE = new Finance();

        private Finance() {
            super("faction_enterprise_finance");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Holdings holdings = Enterprises.of(player);
            long now = System.currentTimeMillis();
            Payout.Preview preview = Payout.preview(player);
            Payout.World world = new Payout.World(player, holdings, now);
            List<MenuView.Section> sections = new ArrayList<>();

            List<Long> hours = holdings.hours();
            if (!hours.isEmpty()) {
                long sum = 0L;
                for (long value : hours) {
                    sum += value;
                }
                Card chart = Card.of(new ItemStack(Items.GOLD_INGOT), "Revenus bruts par heure", List.of(
                                hours.size() + " heure(s) : " + MoneyFormat.exact(sum) + " $, propres et sales",
                                "Les barres : les frais de chaque heure"))
                        .accent(0xFFFAC400).chart(new PriceChart(hours, holdings.hourCosts(), 0L, 60,
                                PriceChart.DOLLARS | PriceChart.VOLUME_DOLLARS));
                sections.add(new MenuView.Section("48 DERNIERES HEURES", "resources", MenuView.CHART, List.of(chart)));
            }

            List<Card> days = new ArrayList<>();
            long weekNet = 0L;
            long weekLaundered = 0L;
            List<Holdings.Day> book = new ArrayList<>(holdings.days());
            java.util.Collections.reverse(book);
            for (Holdings.Day day : book) {
                LocalDate date = LocalDate.ofEpochDay(day.day());
                long net = day.clean() + day.laundered() - day.costs();
                weekNet += net;
                weekLaundered += day.laundered();
                days.add(Card.of(new ItemStack(net >= 0L ? Items.EMERALD : Items.REDSTONE),
                                WEEK[date.getDayOfWeek().getValue() - 1] + " " + DAY.format(date) + " : "
                                        + MoneyFormat.delta(net) + " $ a la banque",
                                List.of("+" + MoneyFormat.exact(day.clean()) + " propres · +" + MoneyFormat.exact(
                                                day.dirty()) + " sales · -" + MoneyFormat.exact(day.costs()) + " de frais",
                                        "Blanchi " + MoneyFormat.exact(day.laundered()) + " $" + (day.seized() > 0L
                                                ? " · saisi " + MoneyFormat.exact(day.seized()) + " $" : "")))
                        .accent(net >= 0L ? 0xFF22E558 : 0xFFEF3826));
            }
            if (days.isEmpty()) {
                days.add(Card.of(new ItemStack(Items.PAPER), "Aucun compte encore", List.of(
                        "Chaque heure pleine s'inscrit ici", "Une semaine est gardee")).state(MenuView.DISABLED));
            }

            List<Card> each = new ArrayList<>();
            for (Enterprise enterprise : holdings.enterprises()) {
                double earned = preview.earned().getOrDefault(enterprise.id(), 0.0d);
                Payout.Factors factors = world.factors(enterprise);
                each.add(Card.of(icon(enterprise), enterprise.name(), List.of(
                                perHour(earned) + (enterprise.legal() ? " propres" : " sales"),
                                "Frais " + MoneyFormat.exact(Math.round(factors.cost())) + " $ / h · net "
                                        + MoneyFormat.delta(Math.round(earned - factors.cost())) + " $"))
                        .accent(enterprise.kind().color()).action("open:" + enterprise.id() + "|gerer"));
            }

            List<Card> dirty = new ArrayList<>();
            dirty.add(Card.of(new ItemStack(Items.REDSTONE), "Argent sale", List.of(
                            MoneyFormat.exact(holdings.dirty()) + " $",
                            "+" + MoneyFormat.exact(Math.round(preview.dirty())) + " $ / h"))
                    .accent(0xFFEC4FB5)
                    .tooltip(List.of("Il paie d'abord tes affaires illegales, tes accords et tes tributs",
                            "Une descente en saisit 15 %, 250 000 $ au plus",
                            "Beaucoup d'argent qui dort attire la police : jusqu'a +15 de risque")));
            dirty.add(Card.of(new ItemStack(Items.WATER_BUCKET), "Blanchi par heure", List.of(
                            "+" + MoneyFormat.exact(Math.round(preview.laundered())) + " $ propres",
                            "Commission " + MoneyFormat.exact(Math.round(preview.fees())) + " $"))
                    .accent(0xFF27C4E8));
            dirty.add(Card.of(new ItemStack(Items.HOPPER), "Achats aux fournisseurs", List.of(
                            MoneyFormat.exact(Math.round(preview.supplies())) + " $ sales / h",
                            "Matiere premiere, pieces, produits"))
                    .accent(0xFF9AA0A6));
            dirty.add(Card.of(new ItemStack(Items.BARREL), "Ventes et pertes", List.of(
                            "~" + Math.round(preview.sold()) + " unite(s) vendue(s) / h",
                            Math.round(preview.lost()) > 0 ? "~" + Math.round(preview.lost()) + " perdue(s) faute de place"
                                    : "Rien ne se perd"))
                    .accent(0xFFA0522D));
            List<Card> stocks = new ArrayList<>();
            for (Line line : Line.values()) {
                long raw = holdings.stock(line.rawKey());
                long product = holdings.stock(line.productKey());
                if (raw > 0L || product > 0L) {
                    stocks.add(Card.of(line.productIcon(), line.label(), List.of(line.rawLabel() + " : " + raw,
                                    line.productLabel() + " : " + product + " / " + world.capacity))
                            .accent(0xFFEC4FB5).progress(product, Math.max(1L, world.capacity)));
                }
            }
            long crates = holdings.stock(Enterprises.CRATES);
            if (crates > 0L) {
                stocks.add(Card.of(new ItemStack(Items.CHEST), "Caisses d'armes", List.of(crates + " / "
                                + world.capacity, "Vendues par tes trafics"))
                        .accent(0xFF9AA0A6).progress(crates, Math.max(1L, world.capacity)));
            }

            // sortir des stocks des objets (lot 24c) : des colis, des sachets, des caisses scellees
            List<Card> out = new ArrayList<>();
            for (Line line : Line.values()) {
                long product = holdings.stock(line.productKey());
                ItemStack parcel = new ItemStack(fr.atraxterra.dohy.underground.Goods.parcel(line));
                ItemStack bag = new ItemStack(fr.atraxterra.dohy.underground.Goods.bag(line));
                if (product >= fr.atraxterra.dohy.underground.Goods.PARCEL) {
                    out.add(Card.of(parcel, MenuView.text(parcel.getHoverName()), List.of(
                                    fr.atraxterra.dohy.underground.Goods.PARCEL + " unites de " + line.productLabel()
                                            .toLowerCase(), product + " en stock"))
                            .accent(0xFFA0522D).action("withdraw:parcel:" + line.name() + "|sortir un colis"));
                }
                if (product >= fr.atraxterra.dohy.underground.Goods.BAG) {
                    out.add(Card.of(bag, MenuView.text(bag.getHoverName()), List.of(
                                    fr.atraxterra.dohy.underground.Goods.BAG + " unites de " + line.productLabel()
                                            .toLowerCase(), product + " en stock"))
                            .accent(0xFFEC4FB5).action("withdraw:bag:" + line.name() + "|sortir un sachet"));
                }
            }
            if (crates > 0L) {
                ItemStack crate = new ItemStack(fr.atraxterra.dohy.underground.DoHyUnderground.SEALED_CRATE.get());
                out.add(Card.of(crate, MenuView.text(crate.getHoverName()), List.of("Une caisse d'armes",
                        crates + " en stock")).accent(0xFF9AA0A6).action("withdraw:crate|sortir une caisse"));
            }

            // les fournisseurs (lot 24c) : des bidons et des graines, payes en argent sale d'abord
            List<Card> suppliers = new ArrayList<>();
            long cans = precursors(world);
            suppliers.add(Card.of(new ItemStack(fr.atraxterra.dohy.underground.DoHyUnderground.PRECURSOR_CAN.get(), 16),
                            "16 bidons de precurseurs", List.of(MoneyFormat.exact(cans) + " $",
                                    world.deal(Syndicate.RECIFS) ? "Prix du Cartel des Recifs" : "Marche gris, sans accord"))
                    .accent(0xFF27C4E8).action("buy:precursors|acheter")
                    .confirm("Acheter 16 bidons de precurseurs ?|" + MoneyFormat.exact(cans)
                            + " $, en argent sale d'abord"));
            for (Line line : Line.values()) {
                if (!line.grows()) {
                    continue;
                }
                long price = seeds(world);
                ItemStack seed = new ItemStack(fr.atraxterra.dohy.underground.Goods.seed(line), 8);
                suppliers.add(Card.of(seed, "8 " + MenuView.text(seed.getHoverName()).toLowerCase(), List.of(
                                MoneyFormat.exact(price) + " $", line.climate()))
                        .accent(0xFF22E558).action("buy:seeds:" + line.name() + "|acheter")
                        .confirm("Acheter 8 graines ?|" + MoneyFormat.exact(price) + " $, en argent sale d'abord"));
            }

            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("SEMAINE", MoneyFormat.delta(weekNet) + " $", weekNet >= 0L ? 0xFF22E558
                            : 0xFFEF3826),
                    new MenuView.Stat("BLANCHI", MoneyFormat.shortForm(weekLaundered) + " $", 0xFF27C4E8),
                    new MenuView.Stat("ARGENT SALE", MoneyFormat.shortForm(holdings.dirty()) + " $", 0xFFEC4FB5));
            sections.add(new MenuView.Section("ARGENT SALE ET STOCKS", "armor", MenuView.GRID, dirty));
            if (!stocks.isEmpty()) {
                sections.add(new MenuView.Section("STOCKS", "resources", MenuView.GRID, stocks));
            }
            if (!out.isEmpty()) {
                sections.add(new MenuView.Section("SORTIR DES STOCKS|en objets", "craft", MenuView.GRID, out));
            }
            sections.add(new MenuView.Section("FOURNISSEURS|argent sale d'abord", "upgrade", MenuView.GRID,
                    suppliers));
            sections.add(new MenuView.Section("7 DERNIERS JOURS", "craft", MenuView.ROWS, days));
            if (!each.isEmpty()) {
                sections.add(new MenuView.Section("PAR ENTREPRISE, LA PROCHAINE HEURE", "resources", MenuView.ROWS,
                        each));
            }
            return new MenuView(id(), "Comptes des entreprises", "Ce qui rentre, ce qui sort, ce qui se blanchit",
                    stats, sections, true).withTabs(tabs(player));
        }

        /** Seize bidons de precurseurs : au prix du Cartel des Recifs avec un accord, du marche gris sinon. */
        static long precursors(Payout.World world) {
            return 16L * Enterprises.rawPrice(world, Line.SYNTHESE);
        }

        /** Huit graines : 150 $ piece, 110 $ avec un accord avec le Cartel des Recifs. */
        static long seeds(Payout.World world) {
            return 8L * (world.deal(Syndicate.RECIFS) ? 110L : 150L);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (key.startsWith("open:")) {
                Detail.INSTANCE.show(player, key.substring(5));
            } else if (key.startsWith("withdraw:")) {
                String[] parts = key.split(":");
                net.minecraft.world.item.Item item = null;
                if (parts.length == 2 && parts[1].equals("crate")) {
                    item = fr.atraxterra.dohy.underground.DoHyUnderground.SEALED_CRATE.get();
                } else if (parts.length == 3 && Line.of(parts[2]) != null) {
                    Line line = Line.of(parts[2]);
                    item = parts[1].equals("parcel") ? fr.atraxterra.dohy.underground.Goods.parcel(line)
                            : fr.atraxterra.dohy.underground.Goods.bag(line);
                }
                if (item != null) {
                    answer(player, fr.atraxterra.dohy.underground.Goods.withdraw(player, item), "");
                }
            } else if (key.startsWith("buy:")) {
                Holdings holdings = Enterprises.of(player);
                Payout.World world = new Payout.World(player, holdings, System.currentTimeMillis());
                if (key.equals("buy:precursors")) {
                    long price = precursors(world);
                    String refusal = Enterprises.pay(player, holdings, price, true, "Fournisseur : precurseurs");
                    if (refusal == null) {
                        fr.atraxterra.dohy.agri.AgriInv.give(player,
                                fr.atraxterra.dohy.underground.DoHyUnderground.PRECURSOR_CAN.get(), 16);
                    }
                    answer(player, refusal, "16 bidons de precurseurs livres.");
                } else if (key.startsWith("buy:seeds:")) {
                    Line line = Line.of(key.substring(10));
                    if (line != null && line.grows()) {
                        long price = seeds(world);
                        String refusal = Enterprises.pay(player, holdings, price, true, "Fournisseur : graines");
                        if (refusal == null) {
                            fr.atraxterra.dohy.agri.AgriInv.give(player, fr.atraxterra.dohy.underground.Goods.seed(line),
                                    8);
                        }
                        answer(player, refusal, "8 graines livrees.");
                    }
                }
            }
        }
    }

    // -- Le territoire ----------------------------------------------------------------------------

    public static final class Territory extends Page {
        public static final Territory INSTANCE = new Territory();

        private Territory() {
            super("faction_enterprise_territory");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Holdings holdings = Enterprises.of(player);
            Enterprises.Site site = Enterprises.here(player);
            List<Influence.Reading> readings = Influence.all(player);
            List<Card> here = new ArrayList<>();
            if (site.country() > 0) {
                Influence.Reading reading = Influence.of(player, site.country());
                here.add(card(player, holdings, reading).badge("ICI"));
            } else {
                here.add(Card.of(new ItemStack(Items.COMPASS), site.countryName(), List.of(
                        site.refusal() != null ? site.refusal() : "Aucun pays sous tes pieds",
                        "L'influence se mesure pays par pays")).state(MenuView.DISABLED));
            }
            List<Card> countries = new ArrayList<>();
            int best = 0;
            for (Influence.Reading reading : readings) {
                countries.add(card(player, holdings, reading));
                best = Math.max(best, reading.value());
            }
            if (countries.isEmpty()) {
                countries.add(Card.of(new ItemStack(Items.MAP), "Aucun pays", List.of(
                        "Tiens des pays, pose des claims, ouvre des entreprises",
                        "L'influence fait les revenus et baisse le risque")).state(MenuView.DISABLED));
            }
            List<String> rules = List.of(
                    "- Tenir le pays : +40 ; ta capitale : +15",
                    "- Tes claims : +1 chacun, 15 au plus ; tes entreprises : +3 par niveau, 30 au plus",
                    "- Un traite avec la nation qui le tient : pacte +3, accord +6, alliance +10",
                    "- Une rivale declaree : -10 ; une guerre avec elle : -15",
                    "- L'organisation criminelle de la region : -10 en guerre, +5 alliee",
                    "! Revenus de 80 % (influence 0) a 140 % (100) ; risque de 120 % a 60 %");
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("PAYS", String.valueOf(readings.size()), 0xFFFFFFFF),
                    new MenuView.Stat("LE PLUS FORT", String.valueOf(best), influenceColor(best)));
            List<MenuView.Section> sections = new ArrayList<>();
            sections.add(new MenuView.Section("ICI", "craft", MenuView.ROWS, here));
            sections.add(new MenuView.Section("TES PAYS", "resources", MenuView.ROWS, countries));
            sections.add(new MenuView.Section("CE QUI FAIT L'INFLUENCE", "resources", MenuView.TEXT, List.of(
                    Card.of(ItemStack.EMPTY, "", rules))));
            return new MenuView(id(), "Territoire et influence", "Ou ta nation compte, et pourquoi", stats, sections,
                    true).withTabs(tabs(player));
        }

        private static Card card(ServerPlayer player, Holdings holdings, Influence.Reading reading) {
            int count = 0;
            for (Enterprise enterprise : holdings.enterprises()) {
                if (enterprise.country() == reading.country()) {
                    count++;
                }
            }
            StringBuilder parts = new StringBuilder();
            for (Influence.Part part : reading.parts()) {
                if (!parts.isEmpty()) {
                    parts.append(" · ");
                }
                parts.append(part.label()).append(' ').append(part.value() > 0 ? "+" : "").append(part.value());
            }
            Region region = Region.of(player.level().getServer(), reading.country());
            return Card.of(new ItemStack(Items.MAP), Enterprises.countryName(reading.country()) + " · influence "
                            + reading.value(), List.of(
                            fr.atraxterra.dohy.earth.EarthNations.holderName(player, reading.country())
                                    + (region == null ? "" : " · " + region.label()) + " · " + count + " entreprise(s)",
                            parts.isEmpty() ? "Aucune presence" : parts.toString()))
                    .accent(influenceColor(reading.value())).progress(reading.value(), 100L);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
        }
    }

    // -- Une organisation criminelle --------------------------------------------------------------

    public static final class SyndicatePage extends Page {
        public static final SyndicatePage INSTANCE = new SyndicatePage();
        private final Map<UUID, Syndicate> selected = new ConcurrentHashMap<>();

        private SyndicatePage() {
            super("faction_syndicate");
        }

        public void show(ServerPlayer player, Syndicate syndicate) {
            selected.put(player.getUUID(), syndicate);
            open(player);
        }

        private Syndicate syndicate(ServerPlayer player) {
            return selected.getOrDefault(player.getUUID(), Syndicate.DOCKS);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Syndicate syndicate = syndicate(player);
            Holdings.Standing standing = Syndicates.standing(player, syndicate);
            Holdings.Status status = standing.state();
            long now = System.currentTimeMillis();
            List<Enterprise> mine = Syndicates.inRegion(player, syndicate);
            int presence = 0;
            for (Enterprise enterprise : mine) {
                presence += enterprise.level();
            }

            List<Card> relation = new ArrayList<>();
            String until = standing.until() > now ? " · encore " + Organisations.duration(standing.until() - now) : "";
            relation.add(Card.of(syndicate.icon(), status.label(), List.of(syndicate.region().label() + until,
                            standing.weakened() > now ? "Affaiblie encore " + Organisations.duration(
                                    standing.weakened() - now) : "Specialite : " + syndicate.specialty().name()
                                    .toLowerCase()))
                    .accent(syndicate.color()));
            relation.add(Card.of(new ItemStack(Items.WITHER_ROSE), "Hostilite " + Math.round(standing.hostility()),
                            List.of("Guerre a " + Math.round(Syndicates.WAR_AT) + ", accord sous "
                                    + Math.round(Syndicates.ACCORD_MAX), "Revient d'elle-meme vers "
                                    + Math.round(Syndicates.baseline(Organisations.regime(player)))))
                    .accent(riskColor(standing.hostility())).progress(Math.round(standing.hostility()), 100L));

            List<Card> acts = new ArrayList<>();
            acts.add(act(player, syndicate, Syndicates.Action.ACCORD, new ItemStack(Items.WRITABLE_BOOK),
                    status == Holdings.Status.ACCORD ? "Renouveler l'accord" : "Accord (14 j)",
                    MoneyFormat.exact(Syndicates.accordPrice(syndicate)) + " $ · hostilite -10",
                    "Sa specialite te sert, plus de malus sur son territoire", "accord"));
            acts.add(act(player, syndicate, Syndicates.Action.ALLIANCE, new ItemStack(Items.GOLDEN_APPLE),
                    status == Holdings.Status.ALLIANCE ? "Renouveler l'alliance" : "Alliance (30 j)",
                    MoneyFormat.exact(Syndicates.alliancePrice(syndicate)) + " $ · sur un accord",
                    "+15 % sur son territoire, moins de risque, elle prend 5 %", "alliance"));
            acts.add(act(player, syndicate, Syndicates.Action.BREAK, new ItemStack(Items.BARRIER), "Rompre",
                    "Son hostilite monte d'un coup", "Elle n'oublie pas", "break"));
            if (status == Holdings.Status.GUERRE) {
                acts.add(act(player, syndicate, Syndicates.Action.TRIBUTE, new ItemStack(Items.GOLD_BLOCK), "Tribut",
                        MoneyFormat.exact(Syndicates.tributePrice(player, syndicate)) + " $",
                        "La guerre s'arrete, hostilite 40", "tribute"));
                acts.add(act(player, syndicate, Syndicates.Action.RIPOSTE, new ItemStack(Items.IRON_SWORD), "Riposte",
                        MoneyFormat.exact(Syndicates.ripostePrice()) + " $ · " + Math.round(Syndicates.riposteChance(
                                player, syndicate) * 100.0d) + " % de chances",
                        "Reussie : elle est affaiblie 14 jours", "riposte"));
            }

            List<String> text = new ArrayList<>();
            text.add("- " + syndicate.specialty().effect());
            text.add("- Neutre : -10 % sur tes affaires illegales de " + syndicate.region().label()
                    + ", et son hostilite monte chaque jour selon ta presence");
            text.add("- En guerre : -40 % sur ce territoire, +15 de risque, et des sabotages");
            text.add("! Agressivite " + Math.round(syndicate.aggression() * 100.0d) + " %, avidite "
                    + Math.round(syndicate.greed() * 100.0d) + " %, rancune " + Math.round(syndicate.honor() * 100.0d)
                    + " %");

            List<Card> affairs = new ArrayList<>();
            for (Enterprise enterprise : mine) {
                boolean closed = enterprise.closed(now);
                affairs.add(Card.of(icon(enterprise), enterprise.name(), List.of(title(enterprise.kind(),
                                enterprise.drug()) + " · niveau " + enterprise.level(), closed ? "Fermee encore "
                                + Organisations.duration(enterprise.closedUntil() - now) : Enterprises.countryName(
                                enterprise.country())))
                        .accent(closed ? 0xFF55545C : enterprise.kind().color())
                        .action("open:" + enterprise.id() + "|gerer"));
            }
            if (affairs.isEmpty()) {
                affairs.add(Card.of(new ItemStack(Items.OAK_SAPLING), "Aucune affaire illegale ici", List.of(
                        "Elle ne s'occupe pas de toi", "tant que tu restes hors de " + syndicate.region().label()))
                        .state(MenuView.DISABLED));
            }

            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("STATUT", status.label().toUpperCase(), syndicate.color()),
                    new MenuView.Stat("HOSTILITE", Math.round(standing.hostility()) + " / 100",
                            riskColor(standing.hostility())),
                    new MenuView.Stat("PRESENCE", String.valueOf(presence), 0xFFFFFFFF));
            List<MenuView.Section> sections = new ArrayList<>();
            sections.add(new MenuView.Section("RELATION", "resources", MenuView.GRID, relation));
            sections.add(new MenuView.Section("GESTES|payes en argent sale d'abord", "craft", MenuView.GRID, acts));
            sections.add(new MenuView.Section("CE QU'ELLE PESE", "resources", MenuView.TEXT, List.of(
                    Card.of(ItemStack.EMPTY, "", text))));
            sections.add(new MenuView.Section("TES AFFAIRES SUR SON TERRITOIRE", "armor", MenuView.ROWS, affairs));
            return new MenuView(id(), syndicate.label(), "Organisation criminelle · " + syndicate.region().label(),
                    stats, sections, true).withTabs(tabs(player));
        }

        private static Card act(ServerPlayer player, Syndicate syndicate, Syndicates.Action action, ItemStack icon,
                                String title, String price, String effect, String key) {
            String refusal = Syndicates.refusal(player, syndicate, action);
            Card card = Card.of(icon, title, List.of(price, refusal != null ? refusal : effect))
                    .accent(action == Syndicates.Action.BREAK || action == Syndicates.Action.RIPOSTE ? 0xFFEF3826
                            : 0xFF22E558);
            if (refusal != null) {
                return card.state(MenuView.DISABLED);
            }
            String question = switch (action) {
                case BREAK -> "!Rompre avec " + syndicate.label() + " ?|Son hostilite monte d'un coup";
                case RIPOSTE -> "!Riposter contre " + syndicate.label() + " ?|" + price;
                default -> title + " avec " + syndicate.label() + " ?|" + price;
            };
            return card.action(key + "|" + title.toLowerCase()).confirm(question);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            Syndicate syndicate = syndicate(player);
            switch (key) {
                case "accord" -> answer(player, Syndicates.accord(player, syndicate), "Accord conclu avec "
                        + syndicate.label() + ".");
                case "alliance" -> answer(player, Syndicates.alliance(player, syndicate), "Alliance conclue avec "
                        + syndicate.label() + ".");
                case "break" -> answer(player, Syndicates.breakDeal(player, syndicate), "Tu romps avec "
                        + syndicate.label() + ".");
                case "tribute" -> answer(player, Syndicates.tribute(player, syndicate), "Tribut paye : la guerre "
                        + "s'arrete.");
                case "riposte" -> {
                    String result = Syndicates.riposte(player, syndicate);
                    if (result.startsWith("!")) {
                        FactionApi.tell(player, result.substring(1) + ".", ChatFormatting.RED);
                    } else {
                        FactionApi.tell(player, result + ".", ChatFormatting.GREEN);
                    }
                }
                default -> {
                    if (key.startsWith("open:")) {
                        Detail.INSTANCE.show(player, key.substring(5));
                    }
                }
            }
        }
    }
}
