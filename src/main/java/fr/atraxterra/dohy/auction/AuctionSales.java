package fr.atraxterra.dohy.auction;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.ui.Toasts;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * L'hotel des ventes, cote vendeur : ce que le joueur met en vente, et les
 * acheteurs fictifs qui le prennent.
 *
 * <p>En solo il n'y a personne en face. Des acheteurs fictifs cherchent les
 * memes raretes que celles que les vendeurs fictifs proposent (la reserve de
 * {@link AuctionConfig}) : runes, essences, equipement de donjon, pierres
 * legendaires, cles... Ce que le shop rachete n'en fait pas partie : il se vend
 * au shop. Ce qui se fabrique surtout avec des matieres du shop non plus
 * (« buyers » a faux dans la reserve) : le vendre ici reviendrait a vendre ces
 * matieres sans que leur prix baisse.
 *
 * <p><b>Le prix et le temps.</b> Le prix conseille est la cote de l'objet fois
 * le facteur des acheteurs (0,6), fois la quantite et l'usure. Le joueur choisit
 * entre trois prix ; moins c'est cher, plus vite ca part : la duree de vente
 * suit une loi exponentielle de moyenne propre a chaque prix. Elle est tiree a
 * la mise en vente, et l'heure ou l'objet part est gardee : la vente se fait en
 * temps reel, partie fermee comprise, et se regle a la connexion suivante. Ce
 * qui n'est pas parti au bout de 48 heures attend que le joueur le recupere.
 *
 * <p><b>Pas de boucle d'argent.</b> Le prix le plus haut vaut 0,6 x 1,15 =
 * 0,69 fois la cote. Rien de ce que les acheteurs prennent ne s'achete moins
 * cher que 0,70 fois sa cote : 0,70 au mieux chez les vendeurs fictifs, 0,75
 * pour une cle en promotion, son prix plein a la boutique. Acheter pour revendre
 * perd donc toujours, sauf l'objet vole au marche noir (quelques pieces par jour,
 * et un grief contre la faction) : un ecart borne, pas une machine.
 */
@EventBusSubscriber
public final class AuctionSales {

    private static final long HOUR = 3_600_000L;
    /** Une vente reussie reste affichee une journee, puis s'efface. */
    private static final long SOLD_SHOWN = 24L * HOUR;
    private static final Random RANDOM = new Random();

    /** La cote de chaque objet de la reserve, recalculee quand la reserve change. */
    private static AuctionConfig.Config indexed;
    private static Map<Item, Long> quotes = Map.of();

    private static int ticks;

    private AuctionSales() {
    }

    public static AuctionConfig.Buyers buyers() {
        return AuctionConfig.config().buyers();
    }

    // -- Ce que les acheteurs prennent ----------------------------------------------------

    /** La cote d'un objet que les acheteurs cherchent (la plus haute de la reserve), 0 sinon. */
    public static long quote(Item item) {
        AuctionConfig.Config config = AuctionConfig.config();
        if (config != indexed) {
            Map<Item, Long> next = new HashMap<>();
            for (AuctionConfig.Offer offer : config.offers()) {
                if (!offer.buyers()) {
                    continue;
                }
                Optional<Item> found = BuiltInRegistries.ITEM.getOptional(offer.item());
                found.ifPresent(value -> next.merge(value, offer.quote(), Math::max));
            }
            quotes = next;
            indexed = config;
        }
        return quotes.getOrDefault(item, 0L);
    }

    /** Le shop rachete-t-il cet objet ? Il se vend alors au shop, pas ici. */
    public static boolean shopBuys(ItemStack stack) {
        return Prices.book().entry(stack).map(PriceEntry::sellable).orElse(false);
    }

    /** Pourquoi les acheteurs n'en veulent pas, ou {@code null} s'ils le prennent. */
    public static String refusal(ItemStack stack) {
        if (stack.isEmpty()) {
            return "Il n'y a plus rien a cet emplacement.";
        }
        if (shopBuys(stack)) {
            return "Le shop rachete cet objet : vends-le dans l'onglet Vendre.";
        }
        if (quote(stack.getItem()) <= 0L) {
            return "Personne ne cherche cet objet a l'hotel des ventes.";
        }
        return null;
    }

    public static boolean accepts(ItemStack stack) {
        return refusal(stack) == null;
    }

    /** Le prix conseille d'une pile : la cote, fois le facteur des acheteurs, la quantite et l'usure. */
    public static long recommended(ItemStack stack) {
        long quote = quote(stack.getItem());
        if (quote <= 0L || stack.isEmpty()) {
            return 0L;
        }
        double wear = 1.0d;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            wear = Math.max(0.1d, 1.0d - stack.getDamageValue() / (double) stack.getMaxDamage());
        }
        return round(quote * buyers().factor() * stack.getCount() * wear);
    }

    /** Le prix d'une pile a l'un des prix proposes. */
    public static long price(ItemStack stack, AuctionConfig.Preset preset) {
        return round(recommended(stack) * preset.factor());
    }

    /** Un prix lisible : trois chiffres significatifs, comme les vendeurs. */
    static long round(double amount) {
        if (amount < 1000.0d) {
            return Math.max(1L, Math.round(amount));
        }
        double scale = Math.pow(10.0d, Math.floor(Math.log10(amount)) - 2.0d);
        return Math.round(amount / scale) * (long) scale;
    }

    /** La chance qu'un objet a ce prix trouve preneur avant de revenir. */
    public static double chance(AuctionConfig.Preset preset) {
        return 1.0d - Math.exp(-buyers().hours() / Math.max(0.01d, preset.hours()));
    }

    /** « Part en ~20 min », « Part en ~3 h », « ~1 jour, pas garanti ». */
    public static String estimate(AuctionConfig.Preset preset) {
        double hours = preset.hours();
        String when;
        if (hours < 1.0d) {
            when = "Part en ~" + Math.max(5L, Math.round(hours * 60.0d / 5.0d) * 5L) + " min";
        } else if (hours < 20.0d) {
            when = "Part en ~" + Math.round(hours) + " h";
        } else {
            long days = Math.max(1L, Math.round(hours / 24.0d));
            when = "~" + days + (days > 1 ? " jours" : " jour");
        }
        return chance(preset) < 0.95d ? when + ", pas garanti" : when;
    }

    // -- Les annonces du joueur -----------------------------------------------------------

    public static List<AuctionData.Sale> sales(ServerPlayer player) {
        return AuctionHouse.data(player).sales();
    }

    /** Les emplacements pris : les annonces en cours et les invendus pas encore recuperes. */
    public static int used(ServerPlayer player) {
        int used = 0;
        for (AuctionData.Sale sale : sales(player)) {
            if (sale.state() != AuctionData.Sale.SOLD) {
                used++;
            }
        }
        return used;
    }

    public static int free(ServerPlayer player) {
        return Math.max(0, buyers().slots() - used(player));
    }

    /** Les invendus qui attendent le joueur. */
    public static int unsold(ServerPlayer player) {
        int count = 0;
        for (AuctionData.Sale sale : sales(player)) {
            if (sale.state() == AuctionData.Sale.UNSOLD) {
                count++;
            }
        }
        return count;
    }

    /** Le sac du joueur, sans l'armure portee ni la seconde main : ce qu'on peut mettre en vente. */
    public static NonNullList<ItemStack> bag(ServerPlayer player) {
        return player.getInventory().getNonEquipmentItems();
    }

    /**
     * Met en vente la pile d'un emplacement du sac, a l'un des prix proposes.
     * Retourne la raison du refus, ou {@code null} si l'annonce est posee.
     */
    public static String list(ServerPlayer player, int slot, String presetId) {
        AuctionConfig.Preset preset = buyers().preset(presetId);
        if (preset == null) {
            return "Ce prix n'existe pas.";
        }
        NonNullList<ItemStack> bag = bag(player);
        if (slot < 0 || slot >= bag.size()) {
            return "Il n'y a plus rien a cet emplacement.";
        }
        ItemStack stack = bag.get(slot);
        String refusal = refusal(stack);
        if (refusal != null) {
            return refusal;
        }
        if (free(player) <= 0) {
            return "Tes " + buyers().slots() + " emplacements sont pris : attends une vente ou retire une annonce.";
        }
        long price = price(stack, preset);
        ItemStack listed = stack.copy();
        stack.setCount(0);
        long now = System.currentTimeMillis();
        // Le temps qu'il faut pour trouver preneur : une loi exponentielle de moyenne
        // celle du prix choisi. Au-dela de la duree de l'annonce, personne ne viendra.
        double hours = -preset.hours() * Math.log(1.0d - RANDOM.nextDouble());
        long sellAt = hours > buyers().hours() ? -1L : now + Math.round(hours * HOUR);
        AuctionData.Sale sale = AuctionHouse.data(player).add(listed, price, preset.id(), now, sellAt);
        DoHy.LOGGER.info("Hotel des ventes : {} met en vente {} x{} a {} ({}), annonce {}",
                player.getName().getString(), BuiltInRegistries.ITEM.getKey(listed.getItem()), listed.getCount(),
                price, preset.id(), sale.id());
        return null;
    }

    /** Retire une annonce en cours : l'objet revient dans le sac. */
    public static String cancel(ServerPlayer player, long id) {
        AuctionData data = AuctionHouse.data(player);
        AuctionData.Sale sale = data.sale(id);
        if (sale == null || sale.state() != AuctionData.Sale.ACTIVE) {
            return "Cette annonce n'est plus en vente.";
        }
        data.remove(id);
        give(player, sale.stack());
        return null;
    }

    /** Recupere un invendu. */
    public static String claim(ServerPlayer player, long id) {
        AuctionData data = AuctionHouse.data(player);
        AuctionData.Sale sale = data.sale(id);
        if (sale == null || sale.state() != AuctionData.Sale.UNSOLD) {
            return "Il n'y a rien a recuperer.";
        }
        data.remove(id);
        give(player, sale.stack());
        return null;
    }

    /** Efface une vente reussie de la liste. */
    public static void clear(ServerPlayer player, long id) {
        AuctionData data = AuctionHouse.data(player);
        AuctionData.Sale sale = data.sale(id);
        if (sale != null && sale.state() == AuctionData.Sale.SOLD) {
            data.remove(id);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        // add() vide la pile de ce qu'il range : ce qui reste n'a pas trouve de place.
        player.getInventory().add(copy);
        if (!copy.isEmpty()) {
            player.drop(copy, false);
        }
    }

    // -- Les acheteurs passent ------------------------------------------------------------

    /**
     * Regle les annonces dont l'heure est venue : l'argent de ce qui est parti,
     * le retour de ce qui n'a pas trouve preneur. {@code now} force toutes les
     * annonces en cours a partir tout de suite (commande de test).
     *
     * @return le nombre d'annonces reglees
     */
    public static int settle(ServerPlayer player, boolean now) {
        AuctionData data = player.getData(fr.atraxterra.dohy.DoHyAttachments.AUCTION);
        List<AuctionData.Sale> sales = data.sales();
        if (sales.isEmpty()) {
            return 0;
        }
        long clock = System.currentTimeMillis();
        long expiry = Math.round(buyers().hours() * HOUR);
        int settled = 0;
        long earned = 0L;
        int sold = 0;
        int returned = 0;
        AuctionData.Sale first = null;
        for (AuctionData.Sale sale : sales) {
            if (sale.state() == AuctionData.Sale.SOLD) {
                long soldAt = sale.sellAt() > 0L ? sale.sellAt() : sale.listedAt();
                if (clock - soldAt > SOLD_SHOWN) {
                    data.remove(sale.id());
                }
                continue;
            }
            if (sale.state() != AuctionData.Sale.ACTIVE) {
                continue;
            }
            boolean due = now || sale.sellAt() >= 0L && clock >= sale.sellAt();
            if (due) {
                AuctionData.Sale done = (now ? sale.withSellAt(clock) : sale).withState(AuctionData.Sale.SOLD);
                data.replace(done);
                Economy.credit(player, sale.price());
                fr.atraxterra.dohy.palamod.achievement.Achievements.sold(player, sale.price());
                earned += sale.price();
                sold++;
                settled++;
                if (first == null) {
                    first = sale;
                }
                DoHy.LOGGER.info("Hotel des ventes : annonce {} de {} vendue {}", sale.id(),
                        player.getName().getString(), sale.price());
            } else if (clock - sale.listedAt() >= expiry) {
                data.replace(sale.withState(AuctionData.Sale.UNSOLD));
                returned++;
                settled++;
            }
        }
        if (sold > 0) {
            // Le nom de l'objet part en composant : le client le traduit dans sa langue.
            Component message = sold > 1
                    ? Component.literal("Hotel des ventes : " + sold + " ventes, +" + MoneyFormat.exact(earned) + " $.")
                    : Component.literal("Hotel des ventes : ").append(name(first))
                            .append(" vendu, +" + MoneyFormat.exact(earned) + " $.");
            player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GREEN));
            Toasts.send(player, first.stack().copyWithCount(1), "auction", sold > 1 ? sold + " ventes a l'hotel des ventes"
                    : "Vendu a l'hotel des ventes", "+" + MoneyFormat.exact(earned) + " $", 0xFF22E558, "auction");
        }
        if (returned > 0) {
            player.sendSystemMessage(Component.literal(returned > 1
                            ? "Hotel des ventes : " + returned + " objets n'ont pas trouve preneur, recupere-les."
                            : "Hotel des ventes : un objet n'a pas trouve preneur, recupere-le.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return settled;
    }

    private static Component name(AuctionData.Sale sale) {
        Component name = sale.stack().getHoverName();
        return sale.stack().getCount() > 1 ? Component.empty().append(name).append(" x" + sale.stack().getCount())
                : name;
    }

    /** Les acheteurs passent toutes les cinq secondes pour les joueurs en ligne. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < 100) {
            return;
        }
        ticks = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            settle(player, false);
        }
    }

    /** Ce qui s'est vendu partie fermee se regle a la connexion. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            settle(player, false);
        }
    }
}
