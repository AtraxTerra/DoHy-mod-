package fr.atraxterra.dohy.faction.absence;

import com.mojang.brigadier.arguments.StringArgumentType;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionLeague;
import fr.atraxterra.dohy.faction.FactionNotation;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.event.FactionEventKind;
import fr.atraxterra.dohy.faction.market.WorldMarket;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le rapport d'absence : en revenant apres plus d'une demi-heure, le joueur
 * trouve un ecran de ce qui s'est passe sans lui — ce que sa base a produit et
 * consomme, l'argent gagne, ce qu'ont fait les nations, et le reste du monde.
 *
 * <p>Le releve de depart ({@link AbsenceData}) est pris a la deconnexion et
 * toutes les cinq minutes en jeu. A la connexion, le rapport s'ecrit trois
 * secondes plus tard, une fois les rattrapages de la connexion passes (interets,
 * lundis de la ligue et de la notation) : il les compte. {@code /rapport} le
 * rouvre ; {@code /f charge} dit ce que la base tient, et l'eteint ou la rallume.
 */
@EventBusSubscriber
public final class AbsenceReport {

    /** En deca d'une demi-heure, pas de rapport. */
    static final long WORTH = 30L * 60_000L;
    /** Le rapport s'ecrit trois secondes apres la connexion. */
    private static final int DELAY = 60;
    private static final int TILES = 13;
    private static final String[] DAYS = {"lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim."};

    /** Ce que le rattrapage de la connexion a credite : interets, prix revenus. */
    private record CatchUp(long interest, int restored) {
    }

    private static final Map<UUID, Integer> WAITING = new HashMap<>();
    private static final Map<UUID, CatchUp> CAUGHT_UP = new HashMap<>();
    private static final Map<UUID, Integer> SIGNATURES = new HashMap<>();

    private AbsenceReport() {
    }

    // -- Les evenements -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onTickets(RegisterTicketControllersEvent event) {
        event.register(KeptBases.CONTROLLER);
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        KeptBases.start(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        KeptBases.stop();
        WAITING.clear();
        CAUGHT_UP.clear();
        SIGNATURES.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WAITING.put(player.getUUID(), DELAY);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WAITING.remove(player.getUUID());
            CAUGHT_UP.remove(player.getUUID());
            SIGNATURES.remove(player.getUUID());
            KeptBases.refresh(player);
            snapshot(player);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (!WAITING.isEmpty()) {
            for (UUID uuid : List.copyOf(WAITING.keySet())) {
                int left = WAITING.merge(uuid, -1, Integer::sum);
                if (left <= 0) {
                    WAITING.remove(uuid);
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        arrive(player);
                    }
                }
            }
        }
        // un claim pose ou rendu : la base suit dans les cinq secondes
        if (tick % 100 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (WAITING.containsKey(player.getUUID())) {
                    continue;
                }
                int signature = KeptBases.signature(FactionApi.data(player));
                Integer before = SIGNATURES.put(player.getUUID(), signature);
                if (before == null || before != signature) {
                    KeptBases.refresh(player);
                }
            }
        }
        if (tick % 1200 == 600) {
            KeptBases.reconcile(server);
        }
        // toutes les cinq minutes : les machines recomptees, et un releve si le serveur s'arretait net
        if (tick % 6000 == 3000) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!WAITING.containsKey(player.getUUID())) {
                    KeptBases.refresh(player);
                    snapshot(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rapport").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            AbsenceData data = player.getData(Factions.ABSENCE.get());
            if (!data.last().exists()) {
                player.sendSystemMessage(Component.literal("Pas encore de rapport : il s'ecrit quand tu reviens apres "
                        + "plus d'une demi-heure d'absence.").withStyle(ChatFormatting.GRAY));
                return 0;
            }
            AbsenceMenu.INSTANCE.open(player);
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("f").then(Commands.literal("charge")
                .executes(context -> status(context.getSource().getPlayerOrException()))
                .then(Commands.argument("etat", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("on");
                            builder.suggest("off");
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String state = StringArgumentType.getString(context, "etat");
                            if (!state.equals("on") && !state.equals("off")) {
                                player.sendSystemMessage(Component.literal("/f charge on ou /f charge off")
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            KeptBases.toggle(player, state.equals("on"));
                            return status(player);
                        }))));
    }

    /** Ce que la base tient, dans le chat. */
    private static int status(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            FactionApi.tell(player, "Fonde d'abord ta faction : tes claims tourneront sans toi.", ChatFormatting.RED);
            return 0;
        }
        KeptBases.Base base = KeptBases.refresh(player);
        if (base == null) {
            FactionApi.tell(player, "Aucun claim : revendique des chunks, ils tourneront sans toi.",
                    ChatFormatting.GRAY);
            return 0;
        }
        if (!base.enabled()) {
            FactionApi.tell(player, "Ta base est eteinte : rien ne tourne sans toi. /f charge on pour la rallumer.",
                    ChatFormatting.GOLD);
            return 1;
        }
        int claims = base.spots().size();
        FactionApi.tell(player, "Ta base tourne sans toi : " + base.kept() + " chunk(s) tenus sur " + claims
                + " claim(s), " + base.boxes() + " machines et coffres.", ChatFormatting.GREEN);
        if (claims > KeptBases.CAP) {
            FactionApi.tell(player, "Au-dela de " + KeptBases.CAP + " chunks, ceux qui ont le plus de machines et de "
                    + "coffres.", ChatFormatting.GRAY);
        }
        FactionApi.tell(player, "/f charge off pour l'eteindre, /rapport pour le dernier rapport d'absence.",
                ChatFormatting.GRAY);
        return 1;
    }

    // -- Le releve de depart --------------------------------------------------------------------

    /** Note ce que le joueur laisse : l'heure, l'argent, les coffres de la base, les cours. */
    static void snapshot(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        PlayerEconomy economy = Economy.of(player);
        FactionData faction = FactionApi.data(player);
        Map<Identifier, Long> stock = BaseStock.scan(server, player.getUUID());
        List<AbsenceData.Rate> market = new ArrayList<>();
        for (Identifier good : WorldMarket.goods()) {
            market.add(new AbsenceData.Rate(good, WorldMarket.factor(server, good)));
        }
        AbsenceData before = player.getData(Factions.ABSENCE.get());
        player.setData(Factions.ABSENCE.get(), new AbsenceData(System.currentTimeMillis(), economy.balance(),
                economy.banked(), faction.exists() ? faction.bank() : 0L,
                faction.exists() ? faction.ranking().league() : 0,
                faction.exists() ? FactionNotation.lastMonday(faction) : 0L,
                AbsenceData.counts(stock), List.copyOf(market), before.last()));
    }

    /**
     * Le rattrapage d'argent de la connexion, passe par {@code OfflineCatchUp} :
     * vrai si le rapport d'absence le prend (alors le chat n'en dit rien).
     */
    public static boolean catchUp(ServerPlayer player, long interest, int restored) {
        AbsenceData data = player.getData(Factions.ABSENCE.get());
        if (data.leftAt() <= 0L || System.currentTimeMillis() - data.leftAt() < WORTH) {
            return false;
        }
        CAUGHT_UP.put(player.getUUID(), new CatchUp(interest, restored));
        return true;
    }

    // -- Le retour ----------------------------------------------------------------------------

    static void arrive(ServerPlayer player) {
        AbsenceData data = player.getData(Factions.ABSENCE.get());
        CatchUp caught = CAUGHT_UP.remove(player.getUUID());
        SIGNATURES.put(player.getUUID(), KeptBases.signature(FactionApi.data(player)));
        KeptBases.Base base = KeptBases.refresh(player);
        long now = System.currentTimeMillis();
        if (data.leftAt() <= 0L || now - data.leftAt() < WORTH) {
            return;
        }
        AbsenceData.Report report = build(player, data, base, caught == null ? new CatchUp(0L, 0) : caught, now);
        player.setData(Factions.ABSENCE.get(), data.withLast(report));
        DoHy.LOGGER.info("Rapport d'absence de {} : {} d'absence, {} objets produits, {} consommes, {} chunks tenus",
                player.getGameProfile().name(), duration(report.away()), total(report.gained()),
                total(report.spent()), report.kept());
        player.sendSystemMessage(Component.literal("Pendant ton absence (" + duration(report.away()) + ") : ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(summary(report)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" [rapport]").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent.RunCommand("/rapport")))));
        if (player.containerMenu == player.inventoryMenu) {
            AbsenceMenu.INSTANCE.open(player);
        }
    }

    private static String summary(AbsenceData.Report report) {
        long gained = total(report.gained());
        if (report.kept() <= 0) {
            return "ta base ne tournait pas.";
        }
        return gained > 0 ? "ta base a produit " + MoneyFormat.exact(gained) + " objet(s)."
                : "ta base a tourne, sans rien ranger de plus.";
    }

    static long total(List<AbsenceData.Count> counts) {
        long sum = 0L;
        for (AbsenceData.Count count : counts) {
            sum += count.count();
        }
        return sum;
    }

    /** Ecrit le rapport : production, argent, nations, monde. */
    static AbsenceData.Report build(ServerPlayer player, AbsenceData data, KeptBases.Base base, CatchUp caught,
                                    long now) {
        MinecraftServer server = player.level().getServer();
        long away = now - data.leftAt();
        FactionData faction = FactionApi.data(player);

        // -- ce que la base a produit et consomme
        Map<Identifier, Long> before = data.stockMap();
        Map<Identifier, Long> after = BaseStock.scan(server, player.getUUID());
        Set<Identifier> items = new HashSet<>(before.keySet());
        items.addAll(after.keySet());
        List<AbsenceData.Count> gained = new ArrayList<>();
        List<AbsenceData.Count> spent = new ArrayList<>();
        for (Identifier item : items) {
            long delta = after.getOrDefault(item, 0L) - before.getOrDefault(item, 0L);
            if (delta > 0) {
                gained.add(new AbsenceData.Count(item, delta));
            } else if (delta < 0) {
                spent.add(new AbsenceData.Count(item, -delta));
            }
        }
        gained.sort(Comparator.comparingLong(AbsenceData.Count::count).reversed());
        spent.sort(Comparator.comparingLong(AbsenceData.Count::count).reversed());

        // -- l'argent
        List<AbsenceData.Line> money = new ArrayList<>();
        PlayerEconomy economy = Economy.of(player);
        long banked = economy.banked() - data.banked();
        if (caught.interest() > 0 || banked != 0) {
            List<String> lines = new ArrayList<>();
            if (caught.interest() > 0) {
                lines.add("+" + MoneyFormat.exact(caught.interest()) + " $ d'interets");
            } else {
                lines.add(signed(banked) + " $");
            }
            lines.add("Epargne : " + MoneyFormat.shortForm(economy.banked()) + " $");
            money.add(new AbsenceData.Line(id(Items.GOLD_INGOT), "Epargne", lines, 0xFFFAC400, ""));
        }
        long wallet = economy.balance() - data.wallet();
        if (wallet != 0) {
            money.add(new AbsenceData.Line(id(Items.GOLD_NUGGET), "Liquide", List.of(signed(wallet) + " $",
                    "Solde : " + MoneyFormat.shortForm(economy.balance()) + " $"), 0xFFFFFFFF, ""));
        }
        if (faction.exists()) {
            long bank = faction.bank() - data.factionBank();
            List<String> lines = new ArrayList<>();
            for (FactionData.BankEntry entry : faction.history()) {
                if (entry.time() <= data.leftAt() || lines.size() >= 2) {
                    break;
                }
                lines.add(entry.label() + " " + signed(entry.amount()) + " $");
            }
            if (bank != 0 || !lines.isEmpty()) {
                lines.add(0, signed(bank) + " $");
                money.add(new AbsenceData.Line(id(Items.EMERALD), "Banque de faction", lines, 0xFF55FF55, "bank"));
            }
        }
        if (caught.restored() > 0) {
            money.add(new AbsenceData.Line(id(Items.PAPER), "Prix de vente", List.of(caught.restored()
                    + " objet(s) revenus", "au plein tarif"), 0xFFAAAAAA, ""));
        }

        // -- les nations
        List<AbsenceData.Line> nations = new ArrayList<>();
        List<AbsenceJournal.Entry> moves = AbsenceJournal.since(server, AbsenceJournal.NATIONS, data.leftAt());
        Map<Identifier, Double> rates = data.marketMap();
        List<Identifier> goods = new ArrayList<>(WorldMarket.goods());
        goods.removeIf(good -> !rates.containsKey(good));
        goods.sort(Comparator.comparingDouble((Identifier good) ->
                -Math.abs(WorldMarket.factor(server, good) - rates.get(good))));
        List<String> marketLines = new ArrayList<>();
        marketLines.add(moves.size() + " gros lot(s) des nations");
        if (!moves.isEmpty()) {
            marketLines.add(clip(moves.get(0).text(), 40));
        }
        nations.add(new AbsenceData.Line(id(Items.EMERALD_BLOCK), "Marche international", marketLines, 0xFF55FFFF,
                "market"));
        for (int i = 0; i < Math.min(3, goods.size()); i++) {
            Identifier good = goods.get(i);
            double from = rates.get(good);
            double to = WorldMarket.factor(server, good);
            if (Math.abs(to - from) < 0.01d) {
                break;
            }
            // le nom traduit chez le joueur : un serveur dedie ne connait que l'anglais
            Item item = BuiltInRegistries.ITEM.getOptional(good).orElse(Items.PAPER);
            nations.add(new AbsenceData.Line(good, MenuView.text(new ItemStack(item).getHoverName()), List.of(
                    (to > from ? "En hausse : " : "En baisse : ") + WorldMarket.percent(from) + " > "
                            + WorldMarket.percent(to)), to > from ? 0xFF55FF55 : 0xFFFF5555, "market"));
        }
        if (faction.exists()) {
            long monday = FactionNotation.lastMonday(faction);
            if (monday != 0L && monday != data.notedMonday() && FactionNotation.hasLast(faction)) {
                int rank = FactionNotation.lastRank(faction);
                nations.add(new AbsenceData.Line(id(Items.WRITABLE_BOOK), "Notation de la semaine", List.of(
                        FactionNotation.points(FactionNotation.lastTotal(faction)) + " / 100"
                                + (rank > 0 ? ", " + FactionNotation.ordinal(rank) : ""),
                        "Bourse : " + MoneyFormat.shortForm(FactionNotation.lastBourse(faction)) + " $"),
                        0xFFFFAA00, ""));
            }
            int league = faction.ranking().league();
            if (league != data.league()) {
                nations.add(new AbsenceData.Line(id(Items.GOLDEN_HELMET), "Ligue", List.of(
                        FactionLeague.of(data.league()).label() + " > " + FactionLeague.of(league).label()),
                        league > data.league() ? 0xFF55FF55 : 0xFFFF5555, ""));
            }
        }
        nations.add(events(data.leftAt(), now));

        // -- le monde
        List<AbsenceData.Line> world = new ArrayList<>();
        for (AbsenceJournal.Entry entry : AbsenceJournal.since(server, AbsenceJournal.WORLD, data.leftAt())) {
            if (world.size() >= 8) {
                break;
            }
            world.add(new AbsenceData.Line(entry.icon(), entry.text(), List.of(ago(now - entry.time())), 0xFF55545C,
                    ""));
        }

        int kept = base == null || !base.enabled() ? 0 : base.kept();
        int claims = base == null ? 0 : base.spots().size();
        int boxes = base == null ? 0 : base.boxes();
        // un serveur arrete (ou une partie solo fermee) pendant l'absence : la base n'a tourne que depuis le demarrage
        long ran = Math.max(0L, Math.min(away, now - KeptBases.started()));
        return new AbsenceData.Report(now, away, ran, kept, claims, boxes, List.copyOf(gained.subList(0,
                Math.min(gained.size(), TILES * 3))), List.copyOf(spent.subList(0, Math.min(spent.size(), TILES))),
                List.copyOf(money), List.copyOf(nations), List.copyOf(world));
    }

    /** Les events de faction passes sans le joueur, et le prochain. */
    private static AbsenceData.Line events(long since, long now) {
        ZonedDateTime from = ZonedDateTime.ofInstant(Instant.ofEpochMilli(since), RealClock.zone()).plusMinutes(1);
        ZonedDateTime until = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), RealClock.zone());
        List<String> missed = new ArrayList<>();
        int count = 0;
        ZonedDateTime next = null;
        FactionEventKind nextKind = null;
        for (FactionEventKind kind : FactionEventKind.values()) {
            if (!kind.ready()) {
                continue;
            }
            ZonedDateTime at = kind.next(from);
            int guard = 0;
            while (at.isBefore(until) && guard++ < 64) {
                count++;
                if (missed.size() < 2) {
                    missed.add(kind.label() + " " + when(at, until));
                }
                at = kind.next(at.plusMinutes(1));
            }
            ZonedDateTime upcoming = kind.next(until);
            if (next == null || upcoming.isBefore(next)) {
                next = upcoming;
                nextKind = kind;
            }
        }
        List<String> lines = new ArrayList<>();
        if (count == 0) {
            lines.add("Aucun event manque");
        } else {
            lines.add(count + " manque(s) : " + String.join(", ", missed) + (count > missed.size() ? "..." : ""));
        }
        if (nextKind != null) {
            lines.add("Prochain : " + nextKind.label() + " " + when(next, until));
        }
        return new AbsenceData.Line(id(nextKind == null ? Items.CLOCK : nextKind.icon()), "Events de faction", lines,
                0xFFFFAA00, "");
    }

    /** « 18 h », « mer. 21 h 30 » : le jour seulement s'il n'est pas aujourd'hui. */
    private static String when(ZonedDateTime at, ZonedDateTime now) {
        String hour = at.getHour() + " h" + (at.getMinute() == 0 ? "" : " " + String.format(Locale.ROOT, "%02d",
                at.getMinute()));
        if (at.toLocalDate().equals(now.toLocalDate())) {
            return hour;
        }
        return DAYS[at.getDayOfWeek().getValue() - 1] + " " + hour;
    }

    // -- Les mots -------------------------------------------------------------------------------

    static Identifier id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    private static String signed(long amount) {
        return (amount >= 0 ? "+" : "-") + MoneyFormat.exact(Math.abs(amount));
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    /** « 9 h 12 min », « 2 j 3 h », « 45 min ». */
    static String duration(long millis) {
        long minutes = Math.max(0L, millis / 60_000L);
        long hours = minutes / 60L;
        if (hours >= 24L) {
            return hours / 24L + " j " + hours % 24L + " h";
        }
        if (hours > 0L) {
            return hours + " h " + minutes % 60L + " min";
        }
        return minutes + " min";
    }

    /** « il y a 3 h », « il y a 12 min ». */
    static String ago(long millis) {
        return "il y a " + duration(millis);
    }
}
