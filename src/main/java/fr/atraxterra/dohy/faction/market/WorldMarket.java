package fr.atraxterra.dohy.faction.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.market.MarketDecay;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.NationRank;
import fr.atraxterra.dohy.faction.event.EventRun;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Le marche international : un stock mondial par ressource, et un cours qui le suit.
 *
 * <h2>Le cours</h2>
 *
 * <p>Chaque ressource a un stock de reference (quatre fois la demi-saturation
 * de sa fiche de prix). Le cours vaut {@code (reference / stock) ^ 0,6} : 1 au
 * stock de reference, plus haut quand le monde en manque, plus bas quand il en
 * regorge. Vendre gonfle le stock et fait baisser le cours ; acheter fait
 * l'inverse. Chaque echange est paye a l'integrale du cours sur la quantite :
 * vendre mille lingots d'un coup ou en dix fois rapporte la meme somme.
 *
 * <h2>Les gros stocks des nations</h2>
 *
 * <p>Toutes les dix minutes de jeu, une des douze nations rivales achete ou
 * vend un gros lot d'une ressource (15 a 40 % de son stock de reference) : le
 * cours bouge d'un coup, et l'annonce passe dans le chat. Entre deux, le stock
 * revient vers sa reference au rythme de l'oubli du marche du joueur (10 % de
 * l'ecart par vingt minutes de jeu).
 *
 * <h2>Pas de boucle d'argent</h2>
 *
 * <ul>
 *   <li>La vente est <b>plafonnee</b> : le cours ne descend pas sous 0,4, et a
 *       ce stock le marche est sature — il n'achete plus rien.</li>
 *   <li>Le cours ne monte pas au-dela de 2 : on vend au plus a deux fois le
 *       prix de base, taxe deduite, quand la boutique vend a quatre fois.</li>
 *   <li>On achete a 2,8 fois le cours, jamais sous 2,6 fois le prix de base :
 *       plus cher que toute revente (au marche comme a la boutique, jour de
 *       demande compris), moins cher que la boutique tant que le stock est la.</li>
 * </ul>
 */
@EventBusSubscriber
public final class WorldMarket {

    /** Les ressources cotees ; celles qui n'ont pas de fiche de prix sont ignorees. */
    private static final String[] GOODS = {
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot", "minecraft:coal",
            "minecraft:diamond", "minecraft:emerald", "minecraft:redstone", "minecraft:lapis_lazuli",
            "minecraft:quartz", "minecraft:obsidian", "minecraft:oak_log", "minecraft:wheat", "minecraft:potato",
            "minecraft:carrot", "minecraft:sugar_cane", "minecraft:leather", "minecraft:bone", "minecraft:string",
            "minecraft:gunpowder", "minecraft:slime_ball", "minecraft:ender_pearl", "minecraft:blaze_rod",
            "palamod:amethyst_ingot", "palamod:titane_ingot", "palamod:paladium_ingot"};

    private static final double K = 0.6d;
    public static final double MIN_FACTOR = 0.4d;
    public static final double MAX_FACTOR = 2.0d;
    /** Le stock ou le cours touche son plancher : le marche est sature. */
    private static final double MAX_STOCK = Math.pow(MIN_FACTOR, -1.0d / K);
    /** Le stock ou le cours touche son plafond : la penurie, plus rien a acheter. */
    private static final double MIN_STOCK = Math.pow(MAX_FACTOR, -1.0d / K);
    private static final double REFERENCE_PER_S0 = 4.0d;
    private static final double BUY_MARKUP = 2.8d;
    private static final double BUY_FLOOR = 2.6d;
    /** Un gros lot d'une nation toutes les dix minutes de jeu. */
    private static final int MOVE_MINUTES = 10;
    private static final int NEWS_KEPT = 6;
    public static final String VOLUME = "note:trade";

    private static int ticks;
    private static final Random RANDOM = new Random();

    private WorldMarket() {
    }

    // -- L'etat, sur le monde principal ---------------------------------------------------

    public record Stock(Identifier id, double amount) {
        static final Codec<Stock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Stock::id),
                Codec.DOUBLE.fieldOf("amount").forGetter(Stock::amount)
        ).apply(instance, Stock::new));
    }

    /** Les stocks qui s'ecartent de leur reference, les dernieres depeches, et les minutes depuis le dernier lot. */
    public record State(List<Stock> stocks, List<String> news, int minutes) {
        public static final State EMPTY = new State(List.of(), List.of(), 0);
        public static final MapCodec<State> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Stock.CODEC.listOf().optionalFieldOf("stocks", List.of()).forGetter(State::stocks),
                Codec.STRING.listOf().optionalFieldOf("news", List.of()).forGetter(State::news),
                Codec.INT.optionalFieldOf("minutes", 0).forGetter(State::minutes)
        ).apply(instance, State::new));

        public boolean exists() {
            return !stocks.isEmpty() || !news.isEmpty();
        }
    }

    private static State state(MinecraftServer server) {
        return server.overworld().getData(Factions.WORLD_MARKET.get());
    }

    private static void save(MinecraftServer server, Map<Identifier, Double> stocks, List<String> news, int minutes) {
        List<Stock> list = new ArrayList<>();
        for (Map.Entry<Identifier, Double> entry : stocks.entrySet()) {
            double reference = reference(entry.getKey());
            if (reference > 0 && Math.abs(entry.getValue() - reference) > reference * 0.002d) {
                list.add(new Stock(entry.getKey(), entry.getValue()));
            }
        }
        ServerLevel level = server.overworld();
        level.setData(Factions.WORLD_MARKET.get(), new State(List.copyOf(list), List.copyOf(news), minutes));
    }

    private static Map<Identifier, Double> stocks(MinecraftServer server) {
        Map<Identifier, Double> map = new HashMap<>();
        for (Stock stock : state(server).stocks()) {
            map.put(stock.id(), stock.amount());
        }
        return map;
    }

    // -- Lire le marche ---------------------------------------------------------------------

    /** Les ressources cotees dans cette partie. */
    public static List<Identifier> goods() {
        List<Identifier> out = new ArrayList<>();
        for (String name : GOODS) {
            Identifier id = Identifier.parse(name);
            if (entry(id) != null && BuiltInRegistries.ITEM.getOptional(id).isPresent()) {
                out.add(id);
            }
        }
        return out;
    }

    private static PriceEntry entry(Identifier id) {
        return Prices.book().entry(id).filter(PriceEntry::sellable).orElse(null);
    }

    public static boolean traded(Identifier id) {
        for (String name : GOODS) {
            if (name.equals(id.toString())) {
                return entry(id) != null;
            }
        }
        return false;
    }

    /** Le stock de reference : celui ou le cours vaut 1. */
    public static double reference(Identifier id) {
        PriceEntry entry = entry(id);
        return entry == null ? 0.0d : Math.max(1, entry.halfSaturation()) * REFERENCE_PER_S0;
    }

    public static double stock(MinecraftServer server, Identifier id) {
        for (Stock stock : state(server).stocks()) {
            if (stock.id().equals(id)) {
                return stock.amount();
            }
        }
        return reference(id);
    }

    /** Le cours : 1 au stock de reference, entre 0,4 (sature) et 2 (penurie). */
    public static double factor(MinecraftServer server, Identifier id) {
        return factorAt(stock(server, id), reference(id));
    }

    private static double factorAt(double stock, double reference) {
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, Math.pow(reference / Math.max(1.0d, stock), K)));
    }

    public static double maxStock(Identifier id) {
        return reference(id) * MAX_STOCK;
    }

    public static double minStock(Identifier id) {
        return reference(id) * MIN_STOCK;
    }

    /** Ce que touche le joueur pour une unite vendue maintenant, taxe deduite. */
    public static long sellUnit(ServerPlayer player, Identifier id) {
        PriceEntry entry = entry(id);
        return entry == null ? 0L : Math.max(1L, Math.round(entry.basePrice() * factor(player.level().getServer(), id)
                * (1.0d - NationRank.of(player).fee())));
    }

    /** Ce que paie le joueur pour une unite achetee maintenant. */
    public static long buyUnit(MinecraftServer server, Identifier id) {
        PriceEntry entry = entry(id);
        return entry == null ? 0L : Math.max(1L, Math.round(entry.basePrice()
                * Math.max(BUY_FLOOR, BUY_MARKUP * factor(server, id))));
    }

    /** La quantite que le marche peut encore absorber avant d'etre sature. */
    public static int room(MinecraftServer server, Identifier id) {
        return (int) Math.max(0.0d, Math.floor(maxStock(id) - stock(server, id)));
    }

    /** La quantite qu'on peut encore acheter avant la penurie. */
    public static int available(MinecraftServer server, Identifier id) {
        return (int) Math.max(0.0d, Math.floor(stock(server, id) - minStock(id)));
    }

    /** Le prix de base fois l'integrale du cours entre deux stocks. */
    private static double integral(double base, double reference, double from, double to) {
        return base * Math.pow(reference, K) * (Math.pow(to, 1.0d - K) - Math.pow(from, 1.0d - K)) / (1.0d - K);
    }

    /** Ce que rapporterait la vente de cette quantite, taxe deduite ; 0 si le marche ne peut pas tout prendre. */
    public static long sellQuote(ServerPlayer player, Identifier id, int quantity) {
        PriceEntry entry = entry(id);
        MinecraftServer server = player.level().getServer();
        if (entry == null || quantity <= 0 || quantity > room(server, id)) {
            return 0L;
        }
        double stock = stock(server, id);
        return Math.max(1L, Math.round(integral(entry.basePrice(), reference(id), stock, stock + quantity)
                * (1.0d - NationRank.of(player).fee())));
    }

    /** Ce que couterait l'achat de cette quantite ; 0 si le stock n'y suffit pas. */
    public static long buyQuote(MinecraftServer server, Identifier id, int quantity) {
        PriceEntry entry = entry(id);
        if (entry == null || quantity <= 0 || quantity > available(server, id)) {
            return 0L;
        }
        double stock = stock(server, id);
        double reference = reference(id);
        // sous ce stock le cours depasse le plancher d'achat : la partie haute se paie a l'integrale
        double knee = reference * Math.pow(BUY_FLOOR / BUY_MARKUP, -1.0d / K);
        double low = stock - quantity;
        double total = 0.0d;
        if (low < knee) {
            total += BUY_MARKUP * integral(entry.basePrice(), reference, low, Math.min(stock, knee));
        }
        if (stock > knee) {
            total += BUY_FLOOR * entry.basePrice() * (stock - Math.max(low, knee));
        }
        return Math.max(1L, Math.round(total));
    }

    // -- Echanger ---------------------------------------------------------------------------

    private static String precheck(ServerPlayer player, Identifier id) {
        if (!FactionApi.has(player)) {
            return "Le marche international est ouvert aux factions : fonde la tienne";
        }
        if (!traded(id)) {
            return "Cette ressource n'est pas cotee au marche international";
        }
        return null;
    }

    public static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (plain(stack, item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Un objet ordinaire : ni renomme, ni enchante, ni abime. */
    private static boolean plain(ItemStack stack, Item item) {
        return stack.is(item) && stack.getComponentsPatch().isEmpty();
    }

    /**
     * Vend au plus {@code quantity} de cette ressource, prise dans l'inventaire :
     * ce que le joueur a, et ce que le marche peut encore absorber.
     *
     * @return un refus, ou {@code null} : la vente est faite et annoncee au joueur
     */
    public static String sell(ServerPlayer player, Identifier id, int quantity) {
        String refusal = precheck(player, id);
        if (refusal != null) {
            return refusal;
        }
        MinecraftServer server = player.level().getServer();
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow();
        int have = count(player, item);
        if (have <= 0) {
            return "Tu n'en as pas dans ton inventaire";
        }
        int room = room(server, id);
        if (room <= 0) {
            return "Le marche est sature : plus personne n'en achete pour l'instant";
        }
        int sold = Math.min(Math.min(quantity, have), room);
        long payout = sellQuote(player, id, sold);
        int left = sold;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (plain(stack, item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        Map<Identifier, Double> stocks = stocks(server);
        stocks.put(id, stock(server, id) + sold);
        State state = state(server);
        save(server, stocks, state.news(), state.minutes());
        Economy.credit(player, payout);
        FactionApi.stat(player, VOLUME, payout);
        FactionApi.tell(player, "Vendu : " + MoneyFormat.exact(sold) + " x " + name(id) + " pour "
                + MoneyFormat.exact(payout) + " $ (taxe " + Math.round(NationRank.of(player).fee() * 100.0d)
                + " %). Cours : " + percent(factor(server, id)) + "."
                + (sold < Math.min(quantity, have) ? " Le marche est sature." : ""), ChatFormatting.GREEN);
        return null;
    }

    /** Achete cette quantite, au plus ce que le stock mondial permet et ce que le joueur peut payer. */
    public static String buy(ServerPlayer player, Identifier id, int quantity) {
        String refusal = precheck(player, id);
        if (refusal != null) {
            return refusal;
        }
        MinecraftServer server = player.level().getServer();
        int available = available(server, id);
        if (available <= 0) {
            return "Penurie mondiale : plus rien a acheter pour l'instant";
        }
        int bought = Math.min(quantity, available);
        long cost = buyQuote(server, id, bought);
        if (!Economy.tryDebit(player, cost)) {
            return "Il te faut " + MoneyFormat.exact(cost) + " $ pour " + MoneyFormat.exact(bought) + " x " + name(id);
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow();
        int left = bought;
        while (left > 0) {
            ItemStack stack = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
            left -= stack.getCount();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        Map<Identifier, Double> stocks = stocks(server);
        stocks.put(id, stock(server, id) - bought);
        State state = state(server);
        save(server, stocks, state.news(), state.minutes());
        FactionApi.stat(player, VOLUME, cost);
        FactionApi.tell(player, "Achete : " + MoneyFormat.exact(bought) + " x " + name(id) + " pour "
                + MoneyFormat.exact(cost) + " $. Cours : " + percent(factor(server, id)) + ".", ChatFormatting.GREEN);
        return null;
    }

    public static String name(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id).map(item -> new ItemStack(item).getHoverName().getString())
                .orElse(id.getPath());
    }

    /** « +18 % », « -32 % » : l'ecart du cours a la reference. */
    public static String percent(double factor) {
        long value = Math.round((factor - 1.0d) * 100.0d);
        return (value >= 0 ? "+" : "") + value + " %";
    }

    public static List<String> news(MinecraftServer server) {
        return state(server).news();
    }

    /** Le volume echange par la faction cette semaine, en dollars. */
    public static long volume(ServerPlayer player) {
        return FactionApi.data(player).stat(VOLUME);
    }

    // -- La vie du marche -------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // le marche vit aussi quand personne n'est la : le rapport d'absence raconte ce qu'ont fait les nations
        if (++ticks < 20 * 60) {
            return;
        }
        ticks = 0;
        minute(server);
    }

    /** Une minute de jeu : les stocks reviennent vers leur reference, et parfois une nation bouge un gros lot. */
    static void minute(MinecraftServer server) {
        State state = state(server);
        Map<Identifier, Double> stocks = stocks(server);
        double keep = MarketDecay.keepPerMinute();
        for (Map.Entry<Identifier, Double> entry : stocks.entrySet()) {
            double reference = reference(entry.getKey());
            entry.setValue(reference + (entry.getValue() - reference) * keep);
        }
        int minutes = state.minutes() + 1;
        List<String> news = new ArrayList<>(state.news());
        save(server, stocks, news, minutes >= MOVE_MINUTES ? 0 : minutes);
        if (minutes >= MOVE_MINUTES) {
            move(server, RANDOM);
        }
    }

    /**
     * Le gros lot d'une nation : elle achete ou vend 15 a 40 % du stock de
     * reference d'une ressource. Une nation vend plutot ce dont le monde
     * manque, et achete plutot ce dont il regorge : le marche ne reste pas
     * colle a une borne.
     */
    public static String move(MinecraftServer server, Random random) {
        List<Identifier> goods = goods();
        if (goods.isEmpty()) {
            return null;
        }
        Identifier id = goods.get(random.nextInt(goods.size()));
        double reference = reference(id);
        double before = stock(server, id);
        double beforeFactor = factorAt(before, reference);
        boolean sells = random.nextDouble() < 0.5d + 0.35d * Math.signum(beforeFactor - 1.0d)
                * Math.min(1.0d, Math.abs(beforeFactor - 1.0d) * 3.0d);
        double lot = reference * (0.15d + 0.25d * random.nextDouble());
        double after = sells ? Math.min(reference * MAX_STOCK, before + lot)
                : Math.max(reference * MIN_STOCK, before - lot);
        long moved = Math.round(Math.abs(after - before));
        if (moved <= 0) {
            return null;
        }
        Map<Identifier, Double> stocks = stocks(server);
        stocks.put(id, after);
        EventRun.Rival nation = EventRun.rivalPool().get(random.nextInt(EventRun.rivalPool().size()));
        double factor = factorAt(after, reference);
        PriceEntry entry = entry(id);
        boolean plural = nation.name().startsWith("Les ");
        String line = nation.name() + (sells ? (plural ? " vendent " : " vend ") : (plural ? " achetent " : " achete "))
                + MoneyFormat.exact(moved) + " x " + name(id)
                + " : le cours " + (sells ? "tombe" : "monte") + " a " + percent(factor) + " ("
                + MoneyFormat.exact(Math.round(entry.basePrice() * factor)) + " $)";
        List<String> news = new ArrayList<>(state(server).news());
        news.add(0, line);
        while (news.size() > NEWS_KEPT) {
            news.remove(news.size() - 1);
        }
        save(server, stocks, news, 0);
        fr.atraxterra.dohy.faction.absence.AbsenceJournal.post(server,
                fr.atraxterra.dohy.faction.absence.AbsenceJournal.NATIONS, line,
                BuiltInRegistries.ITEM.getOptional(id).orElse(net.minecraft.world.item.Items.PAPER));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (FactionApi.has(player)) {
                player.sendSystemMessage(Component.literal("[Marche] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(line + ".").withStyle(ChatFormatting.GRAY)));
            }
        }
        DoHy.LOGGER.debug("Marche international : {}", line);
        return line;
    }

    /** Un lot de recompense : pour {@code value} dollars au prix de base d'une ressource tiree au sort. */
    public static ItemStack lot(Random random, long value) {
        List<Identifier> goods = goods();
        if (goods.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier id = goods.get(random.nextInt(goods.size()));
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow();
        int count = (int) Math.max(1L, Math.min(item.getDefaultMaxStackSize() * 9L, value / Math.max(1L,
                entry(id).basePrice())));
        return new ItemStack(item, count);
    }
}
