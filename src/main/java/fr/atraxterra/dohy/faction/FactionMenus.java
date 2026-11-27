package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.base.EnemyBase;
import fr.atraxterra.dohy.faction.base.EnemyBaseMenu;
import fr.atraxterra.dohy.faction.base.EnemyBases;
import fr.atraxterra.dohy.faction.event.EventRun;
import fr.atraxterra.dohy.faction.event.FactionEventKind;
import fr.atraxterra.dohy.faction.event.FactionEventMenu;
import fr.atraxterra.dohy.faction.event.FactionEvents;
import fr.atraxterra.dohy.faction.reserve.GoldReserve;
import fr.atraxterra.dohy.faction.reserve.GoldReserves;
import fr.atraxterra.dohy.faction.reserve.ReserveMenu;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * « Ma nation » (lots 23a et 24a) : la faction et le pays ne font plus qu'un, sous huit onglets —
 * vue d'ensemble, territoire, organisation, diplomatie, technologies, economie, events,
 * classement. Les pages de detail (pays a prendre, mines, cadre, parametres, fiche d'une nation,
 * guerre, bases ennemies, branche de l'arbre, reserve d'or, marche international, notation)
 * gardent la rangee, sur l'onglet dont elles dependent.
 */
public final class FactionMenus {

    /** Les chemins des huit onglets ; une page de detail passe celui de son onglet. */
    public static final String OVERVIEW = "faction";
    public static final String TERRITORY = "faction_claims";
    public static final String ORGANISATION = "faction_organisation";
    public static final String DIPLOMACY = "faction_diplomacy";
    public static final String ENTERPRISES = "faction_enterprises";
    public static final String TECHNOLOGY = "faction_tree";
    public static final String ECONOMY = "faction_bank";
    public static final String WAR = "guerre";
    public static final String EVENTS = "faction_events";
    public static final String RANKING = "faction_ranking";
    public static final String SETTINGS = "faction_settings";

    private static final DyeColor[] COLORS = DyeColor.values();

    private static final Item[] LEAGUE_ICONS = {Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT,
            Items.PRISMARINE_CRYSTALS, Items.DIAMOND};

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    /** Les actions en deux clics (dissoudre, tout rendre) armees par joueur, avec leur fin. */
    private static final Map<UUID, Map<String, Long>> ARMED = new ConcurrentHashMap<>();

    private FactionMenus() {
    }

    public static ItemStack banner(int color) {
        return new ItemStack(Items.BANNER.pick(COLORS[Math.floorMod(color, COLORS.length)]));
    }

    /** Le blason de la faction : la banniere personnalisee si elle est posee, sinon la couleur. */
    public static ItemStack banner(FactionData data) {
        ItemStack custom = data.bannerItem();
        return custom.isEmpty() ? banner(data.banner()) : custom;
    }

    static ItemStack leagueIcon(FactionLeague league) {
        return new ItemStack(LEAGUE_ICONS[league.ordinal()]);
    }

    static String date(long millis) {
        return DATE.format(Instant.ofEpochMilli(millis).atZone(FactionWeekly.zone()));
    }

    static String duration(Duration duration) {
        long minutes = Math.max(0L, duration.toMinutes());
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        return days > 0 ? days + " j " + hours + " h" : hours + " h " + minutes % 60 + " min";
    }

    /** Vrai au second clic dans les 5 secondes ; le premier arme seulement. */
    static boolean confirmed(ServerPlayer player, String key) {
        Map<String, Long> armed = ARMED.computeIfAbsent(player.getUUID(), uuid -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long until = armed.get(key);
        if (until != null && until > now) {
            armed.remove(key);
            return true;
        }
        armed.put(key, now + 5000L);
        return false;
    }

    static boolean armed(ServerPlayer player, String key) {
        Long until = ARMED.getOrDefault(player.getUUID(), Map.of()).get(key);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * La rangee des neuf onglets de la nation : un clic change de page sans repasser par la vue
     * d'ensemble. Les compteurs disent ce qui attend. {@code active} est le chemin de l'onglet a
     * allumer : celui de la page, ou celui dont depend la page de detail.
     */
    public static List<MenuView.Tab> tabs(ServerPlayer player, String active) {
        Nation nation = Nation.of(player);
        int done = nation.researched();
        int total = nation.researchTotal();
        EventRun fight = FactionEvents.active();
        boolean live = fight != null && !fight.ended();
        List<MenuView.Tab> tabs = new ArrayList<>();
        fr.atraxterra.dohy.faction.org.Organisation org = fr.atraxterra.dohy.faction.org.Organisations.of(player);
        String war = warBadge(nation);
        int treaties = fr.atraxterra.dohy.faction.org.Diplomacy.treaties(player).size();
        tabs.add(tab("Vue d'ensemble", OVERVIEW, active, ""));
        tabs.add(tab("Territoire", TERRITORY, active, nation.claims() + "/" + nation.claimCap()));
        tabs.add(tab("Organisation", ORGANISATION, active, org.debt() > 0L ? "DETTE"
                : org.cadres().size() + "/" + fr.atraxterra.dohy.faction.org.Organisations.cap(player)));
        tabs.add(tab("Diplomatie", DIPLOMACY, active, !war.isEmpty() ? war : treaties > 0 ? String.valueOf(treaties)
                : ""));
        tabs.add(tab("Technologies", TECHNOLOGY, active, total <= 0 ? "" : done * 100 / total + " %"));
        tabs.add(tab("Entreprises", ENTERPRISES, active,
                fr.atraxterra.dohy.faction.enterprise.Enterprises.badge(player)));
        tabs.add(tab("Economie", ECONOMY, active, nation.reserveRipe() ? "PRET" : ""));
        tabs.add(tab("Events", EVENTS, active, live ? "EN COURS" : ""));
        tabs.add(tab("Classement", RANKING, active, nation.league().label().toUpperCase()));
        return tabs;
    }

    /** Le compteur de l'onglet Guerre : l'assaut en cours, la guerre, l'ultimatum, ou rien. */
    public static String warBadge(Nation nation) {
        if (nation.besieged()) {
            return "ASSAUT";
        }
        return nation.conflict().map(relation -> relation.stance() == fr.atraxterra.dohy.faction.war.WarData.Stance
                .GUERRE ? "GUERRE" : "ULTIMATUM").orElse("");
    }

    private static MenuView.Tab tab(String label, String path, String active, String count) {
        return new MenuView.Tab(label, "tab:" + path, path.equals(active), count);
    }

    /** Les derniers mouvements de la banque, en agenda : quand, quoi, combien. */
    private static List<Card> activity(FactionData data) {
        List<FactionData.BankEntry> history = data.history();
        List<Card> cards = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(FactionWeekly.zone());
        for (int index = history.size() - 1; index >= 0 && cards.size() < 3; index--) {
            FactionData.BankEntry entry = history.get(index);
            ZonedDateTime at = Instant.ofEpochMilli(entry.time()).atZone(FactionWeekly.zone());
            long days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate());
            String when = days == 0 ? at.getHour() + " H" : days == 1 ? "HIER " + at.getHour() + " H"
                    : days < 7 ? DAYS[at.getDayOfWeek().getValue() - 1] + " " + at.getHour() + " H"
                    : at.getDayOfMonth() + "/" + at.getMonthValue();
            boolean in = entry.amount() >= 0;
            cards.add(Card.of(new ItemStack(in ? Items.GOLD_INGOT : Items.GOLD_NUGGET),
                            entry.label().isEmpty() ? (in ? "Depot" : "Retrait") : entry.label(),
                            List.of((in ? "+" : "") + MoneyFormat.exact(entry.amount()) + " $"))
                    .badge(when).accent(in ? 0xFF22E558 : 0xFFFAC400));
        }
        return cards;
    }

    private static final String[] DAYS = {"LUN", "MAR", "MER", "JEU", "VEN", "SAM", "DIM"};

    /** Un onglet de la faction : sans faction, l'ecran de creation ; l'hexagone revient a la page. */
    private abstract static class Tab extends MenuController {
        Tab(String path) {
            super(path);
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
            Overview.INSTANCE.open(player);
        }
    }

    // -- La page de la faction --------------------------------------------------

    public static final class Overview extends Tab {
        public static final Overview INSTANCE = new Overview();

        private Overview() {
            super("faction");
        }

        @Override
        protected void back(ServerPlayer player) {
            fr.atraxterra.dohy.ui.Hub.open(player);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Nation nation = Nation.of(player);
            FactionData data = nation.data();
            FactionPerks perks = FactionPerks.of(data);
            FactionLeague league = nation.league();
            NationRank rank = nation.rank();
            boolean earth = nation.earth();
            List<String> identity = new ArrayList<>();
            if (!data.description().isEmpty()) {
                identity.add(data.description());
            }
            if (!data.motd().isEmpty()) {
                identity.add("MOTD : " + data.motd());
            }
            identity.add("Tags : " + (data.tags().isEmpty() ? "aucun" : String.join(", ", data.tags())));
            String next = nation.nextRank();
            identity.add("Rang : " + rank.label() + (next.isEmpty() ? "" : " ; " + next));
            identity.add("Taxe du marche international : " + Math.round(NationRank.fee(player) * 100.0d) + " %");
            List<String> territory = new ArrayList<>();
            territory.add(nation.claims() + " / " + nation.claimCap() + " claims");
            if (earth) {
                int countries = nation.countries().size();
                territory.add(nation.capital() == 0 ? "Pas encore de capitale" : countries + " pays · capitale "
                        + fr.atraxterra.dohy.earth.EarthNations.countryName(nation.capital()));
            } else {
                territory.add("Monde, Nether et End");
            }
            List<String> treasury = new ArrayList<>();
            treasury.add(MoneyFormat.exact(nation.bank()) + " $");
            FactionVault vault = nation.vault();
            if (!vault.isEmpty()) {
                treasury.add("Coffre " + vault.used() + " / " + FactionVault.capacity(player));
            } else if (nation.mines() > 0) {
                treasury.add("+" + MoneyFormat.exact(nation.minesPerHour()) + " $ / h des mines");
            }
            List<Card> faction = List.of(
                    Card.of(nation.banner(), data.name(), List.of(rank.label() + " · fondee le "
                                    + date(data.created()),
                                    data.tags().isEmpty() ? "Nation solo" : String.join(" · ", data.tags())))
                            .accent(rank.color()).tooltip(identity),
                    Card.of(new ItemStack(Items.GRASS_BLOCK), "Territoire", territory)
                            .accent(0xFF22E558).progress(nation.claims(), nation.claimCap())
                            .action("claims|ouvrir le territoire"),
                    Card.of(new ItemStack(Items.GOLD_INGOT), "Banque nationale", treasury)
                            .accent(0xFFFAC400).action("bank|ouvrir l'economie"),
                    Card.of(leagueIcon(league), "Ligue " + league.label(), List.of(
                                    MoneyFormat.exact(data.ranking().elo()) + " ELO",
                                    "+" + MoneyFormat.exact(data.ranking().weekElo()) + " cette semaine"))
                            .accent(league.color()).action("ranking|voir le classement"));

            List<String> homeLines = new ArrayList<>();
            if (perks.homes() <= 0) {
                homeLines.add("Verrouille");
                homeLines.add("Etape Globale 5 (Gouvernement)");
            } else {
                homeLines.add(data.homes().size() + " / " + perks.homes() + " pose(s)");
                homeLines.add("Delai : " + perks.homeDelay() + " s immobile");
            }
            Card home = Card.of(new ItemStack(Items.BED.pick(DyeColor.RED)), "F home", homeLines).accent(0xFF2F5BEA);
            home = perks.homes() <= 0 ? home.state(MenuView.DISABLED)
                    : home.action("home|y aller").right("sethome|le poser ici");
            List<Card> progression = new ArrayList<>(List.of(
                    organisationCard(player),
                    diplomacyCard(player),
                    enterpriseCard(player),
                    treeCard(nation),
                    warCard(nation),
                    eventsCard(),
                    home,
                    enemyBaseCard(player),
                    reserveCard(player),
                    marketCard(player),
                    notationCard(player)));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("RANG", rank.label().toUpperCase(), rank.color()),
                    new MenuView.Stat("ELO", MoneyFormat.shortForm(data.ranking().elo()), 0xFFFFFFFF),
                    new MenuView.Stat("BANQUE", MoneyFormat.shortForm(data.bank()), 0xFFFAC400));
            List<MenuView.Section> sections = new ArrayList<>();
            sections.add(new MenuView.Section("TA NATION", "resources", MenuView.GRID, faction));
            if (earth) {
                // la capitale, les pays et la cible : la faction est une nation, tout tient sur une page
                List<Card> lands = new ArrayList<>(fr.atraxterra.dohy.earth.NationMenu.ownCards(player));
                lands.add(Card.of(new ItemStack(Items.COMPASS), "Voisins", List.of(
                                "Pays", ""))
                        .accent(0xFFFAC400).action("nations|ouvrir"));
                if (fr.atraxterra.dohy.earth.mine.EarthMines.active()) {
                    int mines = fr.atraxterra.dohy.earth.mine.EarthMines.of(player).size();
                    long hourly = fr.atraxterra.dohy.earth.mine.EarthMines.moneyPerHour(player);
                    lands.add(Card.of(new ItemStack(Items.IRON_PICKAXE), "Mines", List.of(
                                    mines == 0 ? "Aucune pour l'instant" : mines + " mine" + (mines > 1 ? "s" : ""),
                                    mines == 0 ? "" : "+" + MoneyFormat.exact(hourly) + " $ / h"))
                            .accent(0xFFFAC400).badge(mines > 0 ? String.valueOf(mines) : "")
                            .action("mines|ouvrir"));
                }
                sections.add(new MenuView.Section("TERRITOIRE", "craft", MenuView.GRID, lands));
            }
            sections.add(new MenuView.Section("EN COURS", "upgrade", MenuView.GRID, progression));
            List<Card> activity = activity(data);
            if (!activity.isEmpty()) {
                sections.add(new MenuView.Section("ACTIVITE RECENTE", "craft", MenuView.AGENDA, activity));
            }
            return new MenuView(id(), nation.title(), "Ma nation", stats, sections, true)
                    .withTabs(tabs(player, OVERVIEW));
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (opened(player, key)) {
                return;
            }
            switch (key) {
                case "home" -> {
                    String refusal = FactionHomes.go(player, 0);
                    if (refusal != null) {
                        report(player, refusal, "");
                    } else {
                        UiNet.close(player);
                    }
                }
                case "sethome" -> report(player, FactionHomes.set(player, 0), "Home pose ici.");
                default -> {
                }
            }
        }
    }

    /** La reserve d'or (lot 22e) : ce qui est place, ou comment en batir une. */
    /** La carte des events : l'event en cours, sinon le prochain depart. */
    private static Card eventsCard() {
        EventRun fight = FactionEvents.active();
        if (fight != null && !fight.ended()) {
            return Card.of(new ItemStack(fight.icon()), "Events de faction", List.of(
                            fight.title(), "En cours, rejoins l'arene"))
                    .accent(0xFFEF3826).badge("EN COURS").action("events|ouvrir");
        }
        ZonedDateTime now = FactionEvents.now();
        FactionEventKind soonest = null;
        ZonedDateTime next = null;
        for (FactionEventKind kind : FactionEventKind.values()) {
            ZonedDateTime start = kind.next(now);
            if (kind.ready() && (next == null || start.isBefore(next))) {
                soonest = kind;
                next = start;
            }
        }
        String day = next.toLocalDate().equals(now.toLocalDate()) ? "" : next.getDayOfMonth() + "/"
                + next.getMonthValue() + " ";
        return Card.of(new ItemStack(Items.DRAGON_EGG), "Events de faction", List.of(
                        soonest.label() + ", " + day
                                + String.format("%d h %02d", next.getHour(), next.getMinute())))
                .accent(0xFFFAC400).action("events|ouvrir");
    }

    private static Card reserveCard(ServerPlayer player) {
        GoldReserve reserve = GoldReserves.of(player);
        long now = System.currentTimeMillis();
        boolean ripe = reserve.investments().stream().anyMatch(investment -> investment.ripe(now));
        List<String> lines = reserve.exists()
                ? List.of("Palier " + reserve.tier() + " · " + reserve.investments().size() + " placement(s)",
                        MoneyFormat.shortForm(reserve.invested()) + " $ places")
                : List.of("Place l'argent de la banque");
        return Card.of(new ItemStack(Items.GOLD_BLOCK), "Reserve d'or", lines).accent(0xFFFAC400)
                .badge(ripe ? "PRET" : "").action("reserve|ouvrir");
    }

    /** Les bases ennemies a piller (lot 22g) : la cible en cours, ou l'invitation a en reperer une. */
    public static Card enemyBaseCard(ServerPlayer player) {
        EnemyBase base = EnemyBases.of(player);
        List<String> lines = base.active()
                ? List.of("Cible : les " + base.name(), EnemyBases.where(player, base))
                : EnemyBases.pending(player) ? List.of("Reperage en cours", "Patience...")
                : List.of("A plus de 9 000 blocs");
        return Card.of(new ItemStack(Items.TNT), "Bases ennemies", lines).accent(0xFFEF3826)
                .badge(base.active() ? "!" : "").action("bases|ouvrir");
    }

    private static Card treeCard(Nation nation) {
        int done = nation.researched();
        int total = nation.researchTotal();
        int running = nation.researching();
        boolean ready = running == 0 && nation.researchReady();
        List<String> treeLines = new ArrayList<>();
        treeLines.add("Paliers " + done + " / " + total);
        if (running > 0) {
            treeLines.add(running + (running > 1 ? " recherches en cours" : " recherche en cours"));
        } else if (ready) {
            treeLines.add("Une recherche est prete");
        }
        return Card.of(new ItemStack(Items.ENCHANTING_TABLE), "Technologies", treeLines)
                .accent(0xFFEC4FB5).progress(done, total).badge(ready ? "!" : "").action("tree|ouvrir les technologies");
    }

    /** La guerre des nations en une carte : l'assaut, la guerre ou l'ultimatum, sinon les hostiles. */
    private static Card warCard(Nation nation) {
        if (nation.besieged()) {
            return Card.of(new ItemStack(Items.IRON_SWORD), "Guerre des nations", List.of("Assaut en cours",
                            "Defends ta base"))
                    .accent(0xFFEF3826).badge("ASSAUT").action("war|ouvrir");
        }
        java.util.Optional<fr.atraxterra.dohy.faction.war.WarData.Relation> conflict = nation.conflict();
        if (conflict.isPresent()) {
            fr.atraxterra.dohy.faction.war.WarData.Relation relation = conflict.get();
            boolean war = relation.stance() == fr.atraxterra.dohy.faction.war.WarData.Stance.GUERRE;
            List<String> lines = war
                    ? List.of("Contre " + relation.traits().the(), relation.slot() > 0L ? "Assaut fixe"
                            : relation.declared() ? "Rase sa place forte" : "Fixe l'heure de l'assaut")
                    : List.of("Ultimatum " + relation.traits().de(), "Paie, negocie ou refuse");
            return Card.of(new ItemStack(war ? Items.IRON_SWORD : Items.WRITABLE_BOOK), "Guerre des nations", lines)
                    .accent(war ? 0xFFEF3826 : 0xFFE8590C).badge(war ? "GUERRE" : "!").action("war|ouvrir");
        }
        int hostiles = nation.hostiles();
        return Card.of(new ItemStack(Items.SHIELD), "Guerre des nations", List.of("En paix",
                        hostiles == 0 ? "Aucune nation hostile" : hostiles + " nation(s) hostile(s)"))
                .accent(hostiles > 0 ? 0xFFE8590C : 0xFF55545C).action("war|ouvrir");
    }

    /** Le regime et les cadres de la nation (lot 24a). */
    private static Card organisationCard(ServerPlayer player) {
        fr.atraxterra.dohy.faction.org.Organisation org = fr.atraxterra.dohy.faction.org.Organisations.of(player);
        fr.atraxterra.dohy.faction.org.Regime regime = org.regime();
        int vacant = 0;
        for (fr.atraxterra.dohy.faction.org.Post post : fr.atraxterra.dohy.faction.org.Post.values()) {
            if (fr.atraxterra.dohy.faction.org.Organisations.holder(player, post).isEmpty()) {
                vacant++;
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(regime.label());
        lines.add(org.debt() > 0L ? "Salaires impayes : " + MoneyFormat.exact(org.debt()) + " $"
                : org.cadres().size() + " cadre(s), " + vacant + " poste(s) vacant(s)");
        return Card.of(regime.icon(), "Organisation", lines).accent(regime.color())
                .badge(org.debt() > 0L ? "DETTE" : "").action("organisation|ouvrir");
    }

    /** Les traites et rivalites (lot 24a). */
    private static Card diplomacyCard(ServerPlayer player) {
        int treaties = fr.atraxterra.dohy.faction.org.Diplomacy.treaties(player).size();
        int rivals = fr.atraxterra.dohy.faction.org.Organisations.of(player).rivals().size();
        return Card.of(new ItemStack(Items.WRITABLE_BOOK), "Diplomatie", List.of(
                        treaties + " traite(s) sur " + fr.atraxterra.dohy.faction.org.Diplomacy.slots(player),
                        rivals == 0 ? "Aucune rivale declaree" : rivals + " rivale(s) declaree(s)"))
                .accent(0xFF27C4E8).action("diplomacy|ouvrir");
    }

    /** Les entreprises, l'argent sale et le net de l'heure (lot 24b). */
    private static Card enterpriseCard(ServerPlayer player) {
        fr.atraxterra.dohy.faction.enterprise.Holdings holdings =
                fr.atraxterra.dohy.faction.enterprise.Enterprises.of(player);
        int count = holdings.enterprises().size();
        String badge = fr.atraxterra.dohy.faction.enterprise.Enterprises.badge(player);
        List<String> lines = new ArrayList<>();
        if (count == 0) {
            lines.add("Aucune pour l'instant");
            lines.add("Legales ou non, elles rapportent");
        } else {
            long net = fr.atraxterra.dohy.faction.enterprise.Enterprises.netPerHour(player);
            lines.add(count + " / " + fr.atraxterra.dohy.faction.enterprise.Enterprises.cap(player)
                    + " · net " + MoneyFormat.delta(net) + " $ / h");
            lines.add("Argent sale : " + MoneyFormat.shortForm(holdings.dirty()) + " $");
        }
        return Card.of(new ItemStack(Items.EMERALD_BLOCK), "Entreprises", lines).accent(0xFF22E558)
                .badge(badge.equals("GUERRE") || badge.equals("FERMEE") ? badge : "")
                .action("enterprises|ouvrir");
    }

    /** Le marche international : la taxe que paie ton rang. */
    private static Card marketCard(ServerPlayer player) {
        return Card.of(new ItemStack(Items.CHEST), "Marche international", List.of(
                        "Taxe " + Math.round(NationRank.fee(player) * 100.0d) + " %", "Selon ton rang"))
                .accent(0xFF27C4E8).action("market|ouvrir");
    }

    /**
     * Les cartes qui menent a une autre page de la nation, et celles qui posent la capitale ou
     * renoncent a la cible, d'ou qu'on clique : vrai si la cle en etait une.
     */
    public static boolean opened(ServerPlayer player, String key) {
        if (fr.atraxterra.dohy.earth.NationMenu.act(player, key)) {
            return true;
        }
        MenuController target = switch (key) {
            case "market" -> fr.atraxterra.dohy.faction.market.WorldMarketMenu.INSTANCE;
            case "claims" -> Claims.INSTANCE;
            case "bank" -> Bank.INSTANCE;
            case "ranking" -> Ranking.INSTANCE;
            case "notation" -> Notation.INSTANCE;
            case "settings" -> Settings.INSTANCE;
            case "tree" -> FactionTreeMenus.Tree.INSTANCE;
            case "bases" -> EnemyBaseMenu.INSTANCE;
            case "reserve" -> ReserveMenu.INSTANCE;
            case "events" -> FactionEventMenu.INSTANCE;
            case "nations" -> fr.atraxterra.dohy.earth.NationMenu.INSTANCE;
            case "mines" -> fr.atraxterra.dohy.earth.mine.MineMenu.INSTANCE;
            case "war" -> fr.atraxterra.dohy.faction.war.WarMenu.INSTANCE;
            case "organisation" -> fr.atraxterra.dohy.faction.org.OrgMenus.Org.INSTANCE;
            case "diplomacy" -> fr.atraxterra.dohy.faction.org.DiplomacyMenus.Hub.INSTANCE;
            case "enterprises" -> fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Hub.INSTANCE;
            case "influence" -> fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Territory.INSTANCE;
            case "enterprise_finance" -> fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Finance.INSTANCE;
            case "laws" -> fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Laws.INSTANCE;
            default -> null;
        };
        if (target == null) {
            return false;
        }
        target.open(player);
        return true;
    }

    // -- Banque -----------------------------------------------------------------

    public static final class Bank extends Tab {
        public static final Bank INSTANCE = new Bank();
        private static final long[] AMOUNTS = {1_000L, 10_000L, 100_000L, 1_000_000L};
        private static final Item[] DEPOSIT_ICONS = {Items.IRON_NUGGET, Items.IRON_INGOT, Items.IRON_BLOCK,
                Items.NETHERITE_INGOT};
        private static final Item[] WITHDRAW_ICONS = {Items.GOLD_NUGGET, Items.GOLD_INGOT, Items.GOLD_BLOCK,
                Items.NETHERITE_BLOCK};

        private Bank() {
            super("faction_bank");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            FactionData data = FactionApi.data(player);
            long purse = Economy.balance(player);
            List<Card> deposit = new ArrayList<>();
            List<Card> withdraw = new ArrayList<>();
            for (int index = 0; index < AMOUNTS.length; index++) {
                long amount = AMOUNTS[index];
                Card in = Card.of(new ItemStack(DEPOSIT_ICONS[index]), "Deposer " + MoneyFormat.shortForm(amount),
                        List.of(MoneyFormat.exact(amount) + " $", "De ton argent"));
                deposit.add(purse >= amount ? in.accent(0xFF22E558).action("deposit:" + amount + "|deposer")
                        : in.state(MenuView.DISABLED));
                Card out = Card.of(new ItemStack(WITHDRAW_ICONS[index]), "Retirer " + MoneyFormat.shortForm(amount),
                        List.of(MoneyFormat.exact(amount) + " $", "Vers ton argent"));
                withdraw.add(data.bank() >= amount ? out.accent(0xFFFAC400).action("withdraw:" + amount + "|retirer")
                        : out.state(MenuView.DISABLED));
            }
            List<Card> history = new ArrayList<>();
            for (FactionData.BankEntry entry : data.history()) {
                if (history.size() >= 12) {
                    break;
                }
                boolean in = entry.amount() >= 0;
                history.add(Card.of(new ItemStack(in ? Items.EMERALD : Items.REDSTONE),
                        (in ? "+" : "") + MoneyFormat.exact(entry.amount()) + " $", List.of(entry.label(),
                                TIME.format(Instant.ofEpochMilli(entry.time()).atZone(FactionWeekly.zone()))))
                        .accent(in ? 0xFF22E558 : 0xFFEF3826));
            }
            if (history.isEmpty()) {
                history.add(Card.of(new ItemStack(Items.PAPER), "Aucun mouvement", List.of("", ""))
                        .state(MenuView.DISABLED));
            }
            FactionVault vault = FactionVault.of(player);
            List<MenuView.Stat> stats = new ArrayList<>(List.of(
                    new MenuView.Stat("BANQUE NATIONALE", MoneyFormat.shortForm(data.bank()), 0xFFFAC400),
                    new MenuView.Stat("TON ARGENT", MoneyFormat.shortForm(purse), 0xFFFFFFFF)));
            // la reserve d'or, le marche international et les mines : le reste du tresor de la nation
            List<Card> treasury = new ArrayList<>(List.of(reserveCard(player), marketCard(player)));
            fr.atraxterra.dohy.faction.enterprise.Holdings holdings =
                    fr.atraxterra.dohy.faction.enterprise.Enterprises.of(player);
            treasury.add(Card.of(new ItemStack(Items.EMERALD_BLOCK), "Entreprises", List.of(
                            holdings.enterprises().isEmpty() ? "Aucune pour l'instant"
                                    : "Net " + MoneyFormat.delta(fr.atraxterra.dohy.faction.enterprise.Enterprises
                                    .netPerHour(player)) + " $ / h",
                            "Argent sale : " + MoneyFormat.shortForm(holdings.dirty()) + " $"))
                    .accent(0xFF22E558).action("enterprise_finance|voir les comptes"));
            Nation nation = Nation.of(player);
            if (fr.atraxterra.dohy.earth.mine.EarthMines.active()) {
                int mines = nation.mines();
                treasury.add(Card.of(new ItemStack(Items.IRON_PICKAXE), "Mines de la Terre", List.of(
                                mines == 0 ? "Aucune pour l'instant" : mines + " mine" + (mines > 1 ? "s" : ""),
                                mines == 0 ? "" : "+" + MoneyFormat.exact(nation.minesPerHour())
                                        + " $ / h"))
                        .accent(0xFFFAC400).badge(mines > 0 ? String.valueOf(mines) : "").action("mines|ouvrir"));
            }
            List<MenuView.Section> sections = new ArrayList<>(List.of(
                    new MenuView.Section("DEPOSER", "resources", MenuView.GRID, deposit),
                    new MenuView.Section("RETIRER", "upgrade", MenuView.GRID, withdraw),
                    new MenuView.Section("TRESOR", "resources", MenuView.GRID, treasury)));
            if (fr.atraxterra.dohy.earth.mine.EarthMines.active() || !vault.isEmpty()) {
                int capacity = FactionVault.capacity(player);
                stats.add(new MenuView.Stat("COFFRE", vault.used() + " / " + capacity,
                        vault.used() >= capacity ? 0xFFEF3826 : 0xFFFFFFFF));
                sections.add(new MenuView.Section("COFFRE DE LA BANQUE|" + vault.used() + " / " + capacity,
                        "armor", MenuView.TILES, vaultCards(vault)));
            }
            sections.add(new MenuView.Section("HISTORIQUE", "craft", MenuView.ROWS, history));
            return new MenuView(id(), "Economie", "", stats, sections, true).withTabs(tabs(player, ECONOMY));
        }

        /** Le coffre de la banque : une vignette par pile, un clic la prend ; une carte pour tout prendre. */
        private static List<Card> vaultCards(FactionVault vault) {
            List<Card> cards = new ArrayList<>();
            if (vault.isEmpty()) {
                cards.add(Card.of(new ItemStack(Items.CHEST), "Vide", List.of("Les minerais des mines", ""))
                        .state(MenuView.DISABLED));
                return cards;
            }
            cards.add(Card.of(new ItemStack(Items.HOPPER), "Tout prendre", List.of(vault.count() + " objets"))
                    .accent(0xFF22E558).badge("TOUT").action("vault_all|tout prendre"));
            for (int index = 0; index < vault.items().size(); index++) {
                ItemStack stack = vault.items().get(index);
                cards.add(Card.of(stack.copyWithCount(1), MenuView.text(stack.getHoverName()),
                                List.of("x" + stack.getCount()))
                        .badge(String.valueOf(stack.getCount())).action("vault:" + index + "|prendre"));
            }
            return cards;
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (opened(player, key)) {
                return;
            }
            if (key.startsWith("deposit:")) {
                long amount = Long.parseLong(key.substring(8));
                report(player, FactionApi.deposit(player, amount), "+" + MoneyFormat.exact(amount)
                        + " $ a la banque nationale.");
            } else if (key.startsWith("withdraw:")) {
                long amount = Long.parseLong(key.substring(9));
                report(player, FactionApi.withdraw(player, amount), "-" + MoneyFormat.exact(amount)
                        + " $ de la banque nationale.");
            } else if (key.equals("vault_all")) {
                int moved = FactionVault.takeAll(player);
                report(player, moved > 0 ? null : "Ton inventaire est plein", "");
            } else if (key.startsWith("vault:")) {
                try {
                    int moved = FactionVault.take(player, Integer.parseInt(key.substring(6)));
                    report(player, moved > 0 ? null : "Ton inventaire est plein", "");
                } catch (NumberFormatException malformed) {
                    failure(player);
                }
            }
        }
    }

    // -- Claims -----------------------------------------------------------------

    public static final class Claims extends Tab {
        public static final Claims INSTANCE = new Claims();

        private Claims() {
            super("faction_claims");
        }

        private static String dimensionName(String dimension) {
            return switch (dimension) {
                case "minecraft:overworld" -> "Monde";
                case "minecraft:the_nether" -> "Nether";
                case "minecraft:the_end" -> "End";
                default -> dimension;
            };
        }

        @Override
        public MenuView view(ServerPlayer player) {
            FactionData data = FactionApi.data(player);
            FactionPerks perks = FactionPerks.of(data);
            ChunkPos here = player.chunkPosition();
            ClaimOwner owner = FactionApi.claimAt(player.level(), player.blockPosition());
            List<Card> actions = new ArrayList<>();
            String hereLine = "Chunk " + here.x() + ", " + here.z();
            if (owner.ownedBy(player.getUUID())) {
                actions.add(Card.of(new ItemStack(Items.SHEARS), "Rendre ce chunk", List.of(hereLine, "Il est a toi"))
                        .accent(0xFFEF3826).action("unclaim|rendre ce chunk"));
            } else if (owner.claimed()) {
                actions.add(Card.of(new ItemStack(Items.BARRIER), "Chunk pris", List.of(hereLine,
                        owner.label())).state(MenuView.DISABLED));
            } else if (!FactionApi.claimable(player.level())) {
                actions.add(Card.of(new ItemStack(Items.BARRIER), "Pas ici", List.of(hereLine,
                        "Monde, Nether ou End seulement")).state(MenuView.DISABLED));
            } else {
                Card claim = Card.of(new ItemStack(Items.GRASS_BLOCK), "Revendiquer ce chunk", List.of(hereLine,
                        data.claims().size() + " / " + FactionApi.claimCap(player) + " utilises"));
                actions.add(data.claims().size() < FactionApi.claimCap(player) ? claim.accent(0xFF22E558).action("claim|revendiquer")
                        : claim.state(MenuView.DISABLED).tooltip(List.of("Plus de claim disponible")));
            }
            actions.add(Card.of(new ItemStack(Items.TNT), "Tout rendre", List.of(
                            data.claims().size() + " claim(s)", "Avec confirmation"))
                    .accent(0xFFEF3826).action("unclaimall|tout rendre")
                    .confirm("!Rendre tous les claims ?|" + data.claims().size() + " chunk(s)"));
            actions.add(Card.of(new ItemStack(Items.SHIELD), "Protection", List.of("Explosions, endermen,",
                            "intrus"))
                    .accent(0xFF2F5BEA));
            List<Card> claims = new ArrayList<>();
            List<FactionData.Claim> list = data.claims();
            for (int index = 0; index < list.size(); index++) {
                FactionData.Claim claim = list.get(index);
                claims.add(Card.of(new ItemStack(claim.monsters() ? Items.ZOMBIE_HEAD : Items.GRASS_BLOCK),
                                dimensionName(claim.dimension()) + " · chunk " + claim.x() + ", " + claim.z(), List.of(
                                        "Blocs " + (claim.x() << 4) + " a " + ((claim.x() << 4) + 15) + ", "
                                                + (claim.z() << 4) + " a " + ((claim.z() << 4) + 15),
                                        claim.monsters() ? "Monstres : oui" : "Monstres : non"))
                        .accent(claim.monsters() ? 0xFF55545C : 0xFF22E558)
                        .action("toggle:" + index + "|basculer les monstres").right("unclaim:" + index + "|rendre"));
            }
            if (claims.isEmpty()) {
                claims.add(Card.of(new ItemStack(Items.MAP), "Aucun claim", List.of("Revendique le chunk",
                        "ou tu te tiens")).state(MenuView.DISABLED));
            }
            Nation nation = Nation.of(player);
            List<MenuView.Stat> stats = new ArrayList<>();
            stats.add(new MenuView.Stat("CLAIMS", data.claims().size() + " / " + FactionApi.claimCap(player),
                    0xFF22E558));
            List<MenuView.Section> sections = new ArrayList<>();
            if (nation.earth()) {
                // la capitale, les pays et la cible, puis les pages de la carte de la Terre
                List<Card> lands = new ArrayList<>(fr.atraxterra.dohy.earth.NationMenu.ownCards(player));
                lands.add(Card.of(new ItemStack(Items.COMPASS), "Pays a prendre", List.of(
                                "Les voisins", ""))
                        .accent(0xFFFAC400).action("nations|ouvrir"));
                if (fr.atraxterra.dohy.earth.mine.EarthMines.active()) {
                    int mines = nation.mines();
                    lands.add(Card.of(new ItemStack(Items.IRON_PICKAXE), "Mines de la Terre", List.of(
                                    mines == 0 ? "Aucune pour l'instant" : mines + " mine" + (mines > 1 ? "s" : ""),
                                    mines == 0 ? "" : "+" + MoneyFormat.exact(
                                            nation.minesPerHour()) + " $ / h"))
                            .accent(0xFFFAC400).badge(mines > 0 ? String.valueOf(mines) : "")
                            .action("mines|ouvrir"));
                }
                // l'influence pays par pays (lot 24b) : ce qui fait les revenus des entreprises
                lands.add(Card.of(new ItemStack(Items.MAP), "Influence", List.of("Pays par pays",
                                "Revenus et risque des entreprises"))
                        .accent(0xFF22E558).action("influence|ouvrir"));
                sections.add(new MenuView.Section("TES TERRES", "craft", MenuView.GRID, lands));
                stats.add(new MenuView.Stat("PAYS", String.valueOf(nation.countries().size()), 0xFFFAC400));
            }
            stats.add(new MenuView.Stat("ICI", owner.ownedBy(player.getUUID()) ? "A TOI" : owner.claimed() ? "PRIS"
                    : "LIBRE", owner.claimed() ? 0xFFEF3826 : 0xFFFFFFFF));
            sections.add(new MenuView.Section("ICI", "resources", MenuView.GRID, actions));
            sections.add(new MenuView.Section("TES CLAIMS", "upgrade", MenuView.ROWS, claims));
            return new MenuView(id(), "Territoire", nation.earth() ? "Tes claims, ta capitale et tes pays"
                    : "Tes claims", stats, sections, true).withTabs(tabs(player, TERRITORY));
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (opened(player, key)) {
                return;
            }
            FactionData data = FactionApi.data(player);
            if ("claim".equals(key)) {
                report(player, FactionApi.claim(player), "Chunk revendique.");
            } else if ("unclaim".equals(key)) {
                report(player, FactionApi.unclaimHere(player), "Chunk rendu.");
            } else if ("unclaimall".equals(key)) {
                // La fenetre de confirmation a deja pose la question.
                int count = FactionApi.unclaimAll(player);
                report(player, null, count + " claim(s) rendu(s).");
            } else if (key.startsWith("toggle:")) {
                int index = Integer.parseInt(key.substring(7));
                if (index < data.claims().size()) {
                    boolean monsters = FactionApi.toggleMonsters(player, index);
                    report(player, null, monsters ? "Les monstres apparaissent dans ce claim."
                            : "Plus de monstres dans ce claim.");
                }
            } else if (key.startsWith("unclaim:")) {
                int index = Integer.parseInt(key.substring(8));
                if (index < data.claims().size()) {
                    FactionData.Claim claim = data.claims().get(index);
                    report(player, FactionApi.unclaim(player, claim.dimension(), claim.x(), claim.z()), "Chunk rendu.");
                }
            }
        }
    }

    // -- Classement -------------------------------------------------------------

    public static final class Ranking extends Tab {
        public static final Ranking INSTANCE = new Ranking();

        private Ranking() {
            super("faction_ranking");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            FactionData data = FactionApi.data(player);
            FactionData.Ranking ranking = data.ranking();
            FactionLeague current = FactionLeague.of(ranking.league());
            List<Card> leagues = new ArrayList<>();
            for (FactionLeague league : FactionLeague.values()) {
                Card card = Card.of(leagueIcon(league), league.label(), List.of(
                        "Des " + MoneyFormat.exact(league.threshold()) + " ELO",
                        "Lundi : +" + MoneyFormat.shortForm(league.reward()) + " $"));
                if (league == current) {
                    FactionLeague next = league.next();
                    card = card.accent(league.color()).badge("ICI");
                    if (next != null) {
                        card = card.progress(ranking.elo() - league.threshold(), next.threshold() - league.threshold());
                    }
                } else if (league.ordinal() < current.ordinal()) {
                    card = card.state(MenuView.DONE);
                } else {
                    card = card.state(MenuView.DISABLED);
                }
                leagues.add(card);
            }
            boolean active = ranking.weekWins() > 0;
            List<Card> week = new ArrayList<>(List.of(
                    Card.of(new ItemStack(Items.CLOCK), "Lundi minuit", List.of(
                            "Dans " + duration(FactionWeekly.untilReset()), "Recalcul et recompenses"))
                            .accent(0xFFFAC400),
                    Card.of(new ItemStack(Items.DYE.pick(active ? DyeColor.LIME : DyeColor.GRAY)), "Cette semaine", List.of(
                                    ranking.weekWins() + " victoire(s) ELO", "+" + MoneyFormat.exact(ranking.weekElo())
                                            + " ELO"))
                            .accent(active ? 0xFF22E558 : 0xFFEF3826)
                            .tooltip(List.of("Sans victoire : une ligue de moins, sans recompense")),
                    Card.of(new ItemStack(Items.GOLD_BLOCK), "Recompense", List.of(
                            MoneyFormat.exact(current.reward()) + " $", active ? "A la banque lundi" : "Gagne un event"))
                            .accent(0xFFFAC400)));
            // la note de la semaine face aux douze nations : sa page detaille les cinq criteres
            week.add(notationCard(player));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("LIGUE", current.label().toUpperCase(), current.color()),
                    new MenuView.Stat("ELO", MoneyFormat.shortForm(ranking.elo()), 0xFFFFFFFF),
                    new MenuView.Stat("SEMAINE", "+" + MoneyFormat.shortForm(ranking.weekElo()), 0xFF22E558));
            return new MenuView(id(), "Classement", "", stats, List.of(
                    new MenuView.Section("LIGUES", "upgrade", MenuView.GRID, leagues),
                    new MenuView.Section("SEMAINE", "resources", MenuView.GRID, week)), true)
                    .withTabs(tabs(player, RANKING));
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            opened(player, key);
        }
    }

    // -- Notation ---------------------------------------------------------------

    private static final Item[] CRITERION_ICONS = {Items.CLOCK, Items.EMERALD, Items.CHEST, Items.IRON_SWORD,
            Items.FILLED_MAP};

    /** La carte de la page de faction : la note en cours et la bourse qu'elle vaudrait. */
    private static Card notationCard(ServerPlayer player) {
        FactionNotation.Score score = FactionNotation.current(player);
        List<FactionNotation.Standing> standings = FactionNotation.standings(player);
        int rank = FactionNotation.rank(standings);
        return Card.of(new ItemStack(Items.WRITABLE_BOOK), "Notation " + score.grade(), List.of(
                        FactionNotation.points(score.total()) + " / " + FactionNotation.points(score.max())
                                + " · " + FactionNotation.ordinal(rank) + " sur " + standings.size(),
                        "Lundi : +" + MoneyFormat.shortForm(score.bourse()) + " $"))
                .accent(0xFF8F5BEA).progress(Math.round(score.total()), Math.round(score.max()))
                .action("notation|voir la notation");
    }

    /** La notation : la semaine en cours critere par critere, et la derniere semaine notee. */
    public static final class Notation extends Tab {
        public static final Notation INSTANCE = new Notation();

        private static final List<List<String>> HELP = List.of(
                List.of("Jours joues (5 suffisent), contrats"),
                List.of("Ventes, primes, contrats, butins"),
                List.of("Marche international"),
                List.of("Victoires ELO de la semaine"),
                List.of("Capitale, pays, claims, musee"));

        private Notation() {
            super("faction_notation");
        }

        @Override
        protected void back(ServerPlayer player) {
            Ranking.INSTANCE.open(player);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            FactionData data = FactionApi.data(player);
            FactionNotation.Score score = FactionNotation.current(player);
            List<Card> week = new ArrayList<>();
            for (FactionNotation.Line line : score.lines()) {
                int index = line.criterion().ordinal();
                boolean full = line.points() >= line.max();
                Card card = Card.of(new ItemStack(CRITERION_ICONS[index]), line.criterion().label(), List.of(
                                FactionNotation.points(line.points()) + " / " + FactionNotation.points(line.max()),
                                line.measure()))
                        .accent(0xFF8F5BEA).progress(Math.round(line.points() * 10.0d), Math.round(line.max() * 10.0d))
                        .tooltip(HELP.get(index));
                week.add(full ? card.state(MenuView.DONE) : card);
            }

            List<Card> last = new ArrayList<>();
            if (FactionNotation.hasLast(data)) {
                List<String> detail = new ArrayList<>();
                for (FactionNotation.Criterion criterion : FactionNotation.Criterion.values()) {
                    detail.add(criterion.label() + " : " + FactionNotation.points(
                            FactionNotation.lastPoints(data, criterion)));
                }
                double total = FactionNotation.lastTotal(data);
                detail.add("Close le " + DATE.format(java.time.LocalDate.ofEpochDay(
                        FactionNotation.lastMonday(data))));
                last.add(Card.of(new ItemStack(Items.WRITTEN_BOOK), "Derniere semaine",
                        List.of(FactionNotation.points(total) + " / " + FactionNotation.points(score.max()) + "  ·  "
                                        + NotationConfig.config().grade(total)
                                        + (FactionNotation.lastRank(data) > 0 ? "  ·  "
                                        + FactionNotation.ordinal(FactionNotation.lastRank(data)) : ""),
                                "Bourse : +" + MoneyFormat.exact(FactionNotation.lastBourse(data)) + " $"))
                        .accent(0xFFFAC400).tooltip(detail));
                last.add(Card.of(new ItemStack(Items.NETHER_STAR), "Meilleure semaine", List.of(
                                FactionNotation.points(FactionNotation.best(data)) + " / "
                                        + FactionNotation.points(score.max()),
                                FactionNotation.count(data) + " semaine(s) notee(s)"))
                        .accent(0xFF27C4E8));
            } else {
                last.add(Card.of(new ItemStack(Items.BOOK), "Pas encore notee", List.of(
                        "La premiere notation tombe lundi", "avec sa bourse")).state(MenuView.DISABLED));
            }
            last.add(Card.of(new ItemStack(Items.CLOCK), "Lundi minuit", List.of(
                            "Dans " + duration(FactionWeekly.untilReset()), "Note et bourse a la banque nationale"))
                    .accent(0xFFFAC400));

            // le classement face aux douze nations, et ce que vaudrait le rang
            List<FactionNotation.Standing> standings = FactionNotation.standings(player);
            int rank = FactionNotation.rank(standings);
            NotationConfig.Rewards rewards = NotationConfig.config().rewards();
            List<Card> table = new ArrayList<>();
            for (int i = 0; i < standings.size(); i++) {
                FactionNotation.Standing standing = standings.get(i);
                Card card = Card.of(standing.mine() ? banner(data) : new ItemStack(i < 3 ? Items.GOLD_INGOT
                                : Items.IRON_INGOT), FactionNotation.ordinal(i + 1) + " " + standing.name(),
                                List.of(FactionNotation.points(standing.score()) + " / "
                                        + FactionNotation.points(score.max()), standing.mine() ? "Ta nation"
                                        : "Nation rivale"))
                        .accent(standing.color()).progress(Math.round(standing.score()), Math.round(score.max()));
                table.add(standing.mine() ? card.badge("TOI") : card);
            }
            List<String> gains = new ArrayList<>();
            gains.add("Bourse : +" + MoneyFormat.exact(score.bourse()) + " $ a la banque nationale");
            gains.add("Conquete : +" + MoneyFormat.exact(FactionNotation.conquestPoints(score.total(), rank))
                    + " points sur ton pays vise");
            gains.add("Podium : 3, 2 ou 1 lot(s) de ressources (" + MoneyFormat.shortForm(rewards.lotValue())
                    + " $ piece)");
            gains.add("Premiere place : +1 claim (au plus " + rewards.prestigeClaims() + ")");
            last.add(0, Card.of(new ItemStack(Items.CHEST), "Recompenses", List.of(
                            FactionNotation.ordinal(rank) + " : +" + MoneyFormat.shortForm(score.bourse()) + " $",
                            "+" + MoneyFormat.exact(FactionNotation.conquestPoints(score.total(), rank))
                                    + " points de conquete" + (rewards.lots(rank) > 0 ? ", " + rewards.lots(rank)
                                    + " lot(s)" : "")))
                    .accent(0xFF22E558).tooltip(gains));

            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("NOTE", FactionNotation.points(score.total()) + " / "
                            + FactionNotation.points(score.max()), 0xFFFFFFFF),
                    new MenuView.Stat("RANG", FactionNotation.ordinal(rank).toUpperCase() + " / " + standings.size(),
                            0xFF8F5BEA),
                    new MenuView.Stat("BOURSE", MoneyFormat.shortForm(score.bourse()), 0xFFFAC400));
            return new MenuView(id(), "Notation", "Une note chaque lundi", stats,
                    List.of(new MenuView.Section("CETTE SEMAINE", "resources", MenuView.GRID, week),
                            new MenuView.Section("BILAN", "upgrade", MenuView.GRID, last),
                            new MenuView.Section("CLASSEMENT DES NATIONS", "craft", MenuView.GRID, table)), true)
                    .withTabs(tabs(player, RANKING));
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
        }
    }

    // -- Parametres -------------------------------------------------------------

    public static final class Settings extends Tab {
        public static final Settings INSTANCE = new Settings();

        private Settings() {
            super("faction_settings");
        }

        @Override
        protected void back(ServerPlayer player) {
            fr.atraxterra.dohy.faction.org.OrgMenus.Org.INSTANCE.open(player);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            FactionData data = FactionApi.data(player);
            FactionPerks perks = FactionPerks.of(data);
            Card custom = Card.of(data.bannerItem().isEmpty() ? new ItemStack(Items.FLOWER_BANNER_PATTERN)
                    : data.bannerItem(), "Blason personnalise", perks.customBanner()
                    ? List.of("Clic : copier la banniere tenue", "Clic droit : retirer")
                    : List.of("Verrouille", "Etape Globale 4 (Gouvernement)"));
            custom = perks.customBanner() ? custom.accent(0xFFEC4FB5).action("custom|copier la banniere en main")
                    .right("uncustom|retirer le blason personnalise") : custom.state(MenuView.DISABLED);
            List<Card> cards = List.of(custom,
                    Card.of(new ItemStack(Items.WRITABLE_BOOK), "Modifier", List.of("Nom, description, MOTD",
                            "Tags et blason")).accent(0xFF2F5BEA).action("edit|modifier"),
                    Card.of(banner(data.banner()), "Blason", List.of("Clic : couleur suivante",
                            "Clic droit : precedente")).accent(0xFFFAC400)
                            .action("banner:1|couleur suivante").right("banner:-1|couleur precedente"),
                    Card.of(new ItemStack(Items.SKELETON_SKULL), "Dissoudre", List.of(
                                    "Avec confirmation", "Claims, banque et arbre perdus"))
                            .accent(0xFFEF3826).action("disband|dissoudre la nation")
                            .confirm("!Dissoudre " + data.name() + " ?|Claims, banque et arbre sont perdus pour de bon"));
            return new MenuView(id(), "Parametres", "", List.of(), List.of(
                    new MenuView.Section("NATION", "craft", MenuView.GRID, cards)), true)
                    .withTabs(tabs(player, ORGANISATION));
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            FactionData data = FactionApi.data(player);
            if ("edit".equals(key)) {
                FactionEdit.open(player);
            } else if (key.startsWith("banner:")) {
                int step = Integer.parseInt(key.substring(7));
                FactionApi.edit(player, data.name(), data.description(), data.motd(), data.tags(), data.banner() + step);
                success(player);
            } else if ("custom".equals(key)) {
                report(player, FactionApi.setCustomBanner(player, player.getMainHandItem()), "Blason personnalise pose.");
            } else if ("uncustom".equals(key)) {
                report(player, FactionApi.setCustomBanner(player, ItemStack.EMPTY), "Blason personnalise retire.");
            } else if ("disband".equals(key)) {
                FactionApi.disband(player);
                UiNet.close(player);
                player.sendSystemMessage(FactionApi.prefix().append(net.minecraft.network.chat.Component.literal(
                        "Tu peux en fonder une nouvelle avec /f create.").withStyle(ChatFormatting.GRAY)));
            }
        }
    }
}
