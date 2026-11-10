package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.base.EnemyBase;
import fr.atraxterra.dohy.faction.base.EnemyBaseMenu;
import fr.atraxterra.dohy.faction.base.EnemyBases;
import fr.atraxterra.dohy.faction.event.BossFight;
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
 * Les onglets de la faction, sur le kit (MenuView) : la page de la faction, la
 * banque, les claims, le classement et les parametres. Sur Paladium, huit
 * onglets ; seul, les membres, l'annuaire et les grades n'ont pas d'objet.
 */
public final class FactionMenus {

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
            FactionData data = FactionApi.data(player);
            FactionPerks perks = FactionPerks.of(data);
            FactionLeague league = FactionLeague.of(data.ranking().league());
            List<String> identity = new ArrayList<>();
            if (!data.description().isEmpty()) {
                identity.add(data.description());
            }
            if (!data.motd().isEmpty()) {
                identity.add("MOTD : " + data.motd());
            }
            identity.add("Tags : " + (data.tags().isEmpty() ? "aucun" : String.join(", ", data.tags())));
            List<Card> faction = List.of(
                    Card.of(banner(data), data.name(), List.of("Fondee le " + date(data.created()),
                                    data.tags().isEmpty() ? "Faction solo" : String.join(" · ", data.tags())))
                            .accent(0xFFEF3826).tooltip(identity),
                    Card.of(new ItemStack(Items.GRASS_BLOCK), "Claims", List.of(
                                    data.claims().size() + " / " + perks.claims() + " chunks",
                                    "Ta base, a l'abri du monde"))
                            .accent(0xFF22E558).progress(data.claims().size(), perks.claims())
                            .action("claims|gerer les claims"),
                    Card.of(new ItemStack(Items.GOLD_INGOT), "Banque de faction", List.of(
                                    MoneyFormat.exact(data.bank()) + " $", "Paie l'arbre de technologie"))
                            .accent(0xFFFAC400).action("bank|ouvrir la banque"),
                    Card.of(leagueIcon(league), "Ligue " + league.label(), List.of(
                                    MoneyFormat.exact(data.ranking().elo()) + " ELO",
                                    "Semaine : +" + MoneyFormat.exact(data.ranking().weekElo())))
                            .accent(league.color()).action("ranking|voir le classement"));

            List<String> homeLines = new ArrayList<>();
            if (perks.homes() <= 0) {
                homeLines.add("Verrouille");
                homeLines.add("Palier 5 de la branche Globale");
            } else {
                homeLines.add(data.homes().size() + " / " + perks.homes() + " pose(s)");
                homeLines.add("Delai : " + perks.homeDelay() + " s immobile");
            }
            Card home = Card.of(new ItemStack(Items.BED.pick(DyeColor.RED)), "F home", homeLines).accent(0xFF2F5BEA);
            home = perks.homes() <= 0 ? home.state(MenuView.DISABLED)
                    : home.action("home|y aller").right("sethome|le poser ici");
            List<Card> progression = List.of(
                    treeCard(player, data),
                    home,
                    enemyBaseCard(player),
                    reserveCard(player),
                    eventsCard(),
                    Card.of(new ItemStack(Items.COMPARATOR), "Parametres", List.of("Nom, description, MOTD",
                            "Tags, blason, dissolution")).accent(0xFF55545C).action("settings|ouvrir"));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("LIGUE", league.label().toUpperCase(), league.color()),
                    new MenuView.Stat("ELO", MoneyFormat.shortForm(data.ranking().elo()), 0xFFFFFFFF),
                    new MenuView.Stat("BANQUE", MoneyFormat.shortForm(data.bank()), 0xFFFAC400));
            return new MenuView(id(), data.name(), data.description().isEmpty() ? "Ta faction" : data.description(),
                    stats, List.of(new MenuView.Section("TA FACTION", "resources", MenuView.GRID, faction),
                    new MenuView.Section("PROGRESSION", "upgrade", MenuView.GRID, progression)), true);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            switch (key) {
                case "claims" -> Claims.INSTANCE.open(player);
                case "bank" -> Bank.INSTANCE.open(player);
                case "ranking" -> Ranking.INSTANCE.open(player);
                case "settings" -> Settings.INSTANCE.open(player);
                case "tree" -> FactionTreeMenus.Tree.INSTANCE.open(player);
                case "bases" -> EnemyBaseMenu.INSTANCE.open(player);
                case "reserve" -> ReserveMenu.INSTANCE.open(player);
                case "events" -> FactionEventMenu.INSTANCE.open(player);
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
        BossFight fight = FactionEvents.active();
        if (fight != null && !fight.ended()) {
            return Card.of(new ItemStack(Items.WITHER_SKELETON_SKULL), "Events de faction", List.of(
                            "Boss : " + fight.kind().label(), "En cours, rejoins l'arene"))
                    .accent(0xFFEF3826).badge("EN COURS").action("events|ouvrir");
        }
        ZonedDateTime now = FactionEvents.now();
        ZonedDateTime next = FactionEventKind.BOSS.next(now);
        return Card.of(new ItemStack(Items.DRAGON_EGG), "Events de faction", List.of(
                        "Prochain : Boss, " + String.format("%d h %02d", next.getHour(), next.getMinute()),
                        "Egg Hunt, End, KOTH, Totem a venir"))
                .accent(0xFFFAC400).action("events|ouvrir");
    }

    private static Card reserveCard(ServerPlayer player) {
        GoldReserve reserve = GoldReserves.of(player);
        long now = System.currentTimeMillis();
        boolean ripe = reserve.investments().stream().anyMatch(investment -> investment.ripe(now));
        List<String> lines = reserve.exists()
                ? List.of("Palier " + reserve.tier() + " · " + reserve.investments().size() + " placement(s)",
                        MoneyFormat.shortForm(reserve.invested()) + " $ places")
                : List.of("Places l'argent de la banque,", "recolte les interets");
        return Card.of(new ItemStack(Items.GOLD_BLOCK), "Reserve d'or", lines).accent(0xFFFAC400)
                .badge(ripe ? "PRET" : "").action("reserve|ouvrir");
    }

    /** Les bases ennemies a piller (lot 22g) : la cible en cours, ou l'invitation a en reperer une. */
    private static Card enemyBaseCard(ServerPlayer player) {
        EnemyBase base = EnemyBases.of(player);
        List<String> lines = base.active()
                ? List.of("Cible : les " + base.name(), EnemyBases.where(player, base))
                : EnemyBases.pending(player) ? List.of("Reperage en cours", "Patience...")
                : List.of("Une faction a piller,", "a plus de 9 000 blocs");
        return Card.of(new ItemStack(Items.TNT), "Bases ennemies", lines).accent(0xFFEF3826)
                .badge(base.active() ? "!" : "").action("bases|ouvrir");
    }

    private static Card treeCard(ServerPlayer player, FactionData data) {
        int done = 0;
        int total = 0;
        int running = 0;
        boolean ready = false;
        for (FactionBranch branch : FactionBranch.values()) {
            done += data.tier(branch.key());
            total += FactionTree.tiers(branch).size();
            if (data.upgradeEnd(branch.key()) > 0L) {
                running++;
            } else if (FactionTree.blocker(player, branch) == null) {
                ready = true;
            }
        }
        return Card.of(new ItemStack(Items.ENCHANTING_TABLE), "Arbre de technologie", List.of(
                        "Paliers " + done + " / " + total,
                        running > 0 ? running + " amelioration(s) en cours" : ready ? "Un palier est pret" : "4 branches"))
                .accent(0xFFEC4FB5).progress(done, total).badge(ready ? "!" : "").action("tree|ouvrir l'arbre");
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
                history.add(Card.of(new ItemStack(Items.PAPER), "Aucun mouvement", List.of("Les depots, retraits",
                        "et recompenses s'inscrivent ici")).state(MenuView.DISABLED));
            }
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("BANQUE DE FACTION", MoneyFormat.shortForm(data.bank()), 0xFFFAC400),
                    new MenuView.Stat("TON ARGENT", MoneyFormat.shortForm(purse), 0xFFFFFFFF));
            return new MenuView(id(), "Banque de faction", "L'arbre de technologie se paie d'ici", stats, List.of(
                    new MenuView.Section("DEPOSER", "resources", MenuView.GRID, deposit),
                    new MenuView.Section("RETIRER", "upgrade", MenuView.GRID, withdraw),
                    new MenuView.Section("HISTORIQUE", "craft", MenuView.ROWS, history)), true);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (key.startsWith("deposit:")) {
                long amount = Long.parseLong(key.substring(8));
                report(player, FactionApi.deposit(player, amount), "+" + MoneyFormat.exact(amount)
                        + " $ a la banque de faction.");
            } else if (key.startsWith("withdraw:")) {
                long amount = Long.parseLong(key.substring(9));
                report(player, FactionApi.withdraw(player, amount), "-" + MoneyFormat.exact(amount)
                        + " $ de la banque de faction.");
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
                        "Faction " + owner.faction())).state(MenuView.DISABLED));
            } else if (!FactionApi.claimable(player.level())) {
                actions.add(Card.of(new ItemStack(Items.BARRIER), "Pas ici", List.of(hereLine,
                        "Monde, Nether ou End seulement")).state(MenuView.DISABLED));
            } else {
                Card claim = Card.of(new ItemStack(Items.GRASS_BLOCK), "Revendiquer ce chunk", List.of(hereLine,
                        data.claims().size() + " / " + perks.claims() + " utilises"));
                actions.add(data.claims().size() < perks.claims() ? claim.accent(0xFF22E558).action("claim|revendiquer")
                        : claim.state(MenuView.DISABLED).tooltip(List.of("Plus de claim disponible")));
            }
            boolean armed = armed(player, "unclaimall");
            actions.add(Card.of(new ItemStack(Items.TNT), armed ? "Confirmer ?" : "Tout rendre", List.of(
                            data.claims().size() + " claim(s)", armed ? "Clique encore pour confirmer" : "Deux clics"))
                    .accent(0xFFEF3826).action("unclaimall|tout rendre"));
            actions.add(Card.of(new ItemStack(Items.SHIELD), "Protection", List.of("Ni explosions de monstres,",
                            "ni endermen, ni intrus"))
                    .accent(0xFF2F5BEA).tooltip(List.of("Tes explosions (TNT, dynamite) cassent ton claim ;",
                            "celles des creepers, ghasts et withers sauvages, non.",
                            "Les autres joueurs n'y cassent, n'y posent et n'y ouvrent rien.",
                            "Chaque claim peut refuser l'apparition des monstres.")));
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
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("CLAIMS", data.claims().size() + " / " + perks.claims(), 0xFF22E558),
                    new MenuView.Stat("ICI", owner.ownedBy(player.getUUID()) ? "A TOI" : owner.claimed() ? "PRIS"
                            : "LIBRE", owner.claimed() ? 0xFFEF3826 : 0xFFFFFFFF));
            return new MenuView(id(), "Claims", "Clic : monstres oui/non · clic droit : rendre", stats, List.of(
                    new MenuView.Section("ICI", "resources", MenuView.GRID, actions),
                    new MenuView.Section("TES CLAIMS", "upgrade", MenuView.ROWS, claims)), true);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            FactionData data = FactionApi.data(player);
            if ("claim".equals(key)) {
                report(player, FactionApi.claim(player), "Chunk revendique.");
            } else if ("unclaim".equals(key)) {
                report(player, FactionApi.unclaimHere(player), "Chunk rendu.");
            } else if ("unclaimall".equals(key)) {
                if (confirmed(player, "unclaimall")) {
                    int count = FactionApi.unclaimAll(player);
                    report(player, null, count + " claim(s) rendu(s).");
                }
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
            List<Card> week = List.of(
                    Card.of(new ItemStack(Items.CLOCK), "Lundi minuit", List.of(
                            "Dans " + duration(FactionWeekly.untilReset()), "Recalcul et recompenses"))
                            .accent(0xFFFAC400),
                    Card.of(new ItemStack(Items.DYE.pick(active ? DyeColor.LIME : DyeColor.GRAY)), "Cette semaine", List.of(
                                    ranking.weekWins() + " victoire(s) ELO", "+" + MoneyFormat.exact(ranking.weekElo())
                                            + " ELO"))
                            .accent(active ? 0xFF22E558 : 0xFFEF3826)
                            .tooltip(List.of("Sans victoire dans la semaine, la faction",
                                    "redescend d'une ligue et ne touche rien.")),
                    Card.of(new ItemStack(Items.GOLD_BLOCK), "Recompense", List.of(
                            MoneyFormat.exact(current.reward()) + " $", active ? "A la banque lundi" : "Gagne un event"))
                            .accent(0xFFFAC400));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("LIGUE", current.label().toUpperCase(), current.color()),
                    new MenuView.Stat("ELO", MoneyFormat.shortForm(ranking.elo()), 0xFFFFFFFF),
                    new MenuView.Stat("SEMAINE", "+" + MoneyFormat.shortForm(ranking.weekElo()), 0xFF22E558));
            return new MenuView(id(), "Classement", "Les events et les raids rapportent l'ELO", stats, List.of(
                    new MenuView.Section("LIGUES", "upgrade", MenuView.GRID, leagues),
                    new MenuView.Section("SEMAINE", "resources", MenuView.GRID, week)), true);
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
        public MenuView view(ServerPlayer player) {
            FactionData data = FactionApi.data(player);
            boolean disband = armed(player, "disband");
            FactionPerks perks = FactionPerks.of(data);
            Card custom = Card.of(data.bannerItem().isEmpty() ? new ItemStack(Items.FLOWER_BANNER_PATTERN)
                    : data.bannerItem(), "Blason personnalise", perks.customBanner()
                    ? List.of("Clic : copier la banniere tenue", "Clic droit : retirer")
                    : List.of("Verrouille", "Palier 4 de la branche Globale"));
            custom = perks.customBanner() ? custom.accent(0xFFEC4FB5).action("custom|copier la banniere en main")
                    .right("uncustom|retirer le blason personnalise") : custom.state(MenuView.DISABLED);
            List<Card> cards = List.of(custom,
                    Card.of(new ItemStack(Items.WRITABLE_BOOK), "Modifier", List.of("Nom, description, MOTD",
                            "Tags et blason")).accent(0xFF2F5BEA).action("edit|modifier"),
                    Card.of(banner(data.banner()), "Blason", List.of("Clic : couleur suivante",
                            "Clic droit : precedente")).accent(0xFFFAC400)
                            .action("banner:1|couleur suivante").right("banner:-1|couleur precedente"),
                    Card.of(new ItemStack(Items.SKELETON_SKULL), disband ? "Confirmer ?" : "Dissoudre", List.of(
                                    disband ? "Clique encore pour confirmer" : "Deux clics",
                                    "Claims, banque et arbre perdus"))
                            .accent(0xFFEF3826).action("disband|dissoudre la faction"));
            return new MenuView(id(), "Parametres", "Ta faction, ton nom", List.of(), List.of(
                    new MenuView.Section("FACTION", "craft", MenuView.GRID, cards)), true);
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
            } else if ("disband".equals(key) && confirmed(player, "disband")) {
                FactionApi.disband(player);
                UiNet.close(player);
                player.sendSystemMessage(FactionApi.prefix().append(net.minecraft.network.chat.Component.literal(
                        "Tu peux en fonder une nouvelle avec /f create.").withStyle(ChatFormatting.GRAY)));
            }
        }
    }
}
