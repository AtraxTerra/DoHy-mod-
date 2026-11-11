package fr.atraxterra.dohy.auction;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Le marche : l'hotel des ventes de Paladium, pour un joueur seul.
 *
 * <p>Dans Paladium, {@code /market} liste ce que les autres joueurs vendent.
 * Ici, des vendeurs fictifs deposent chaque jour une douzaine d'annonces,
 * tirees de la reserve ({@link AuctionConfig}) : quelques-unes par rayon,
 * chacune avec un lot, un stock et un prix qui tourne autour de la cote de
 * l'objet — une bonne affaire de temps en temps, un vendeur gourmand aussi.
 * On n'y vend rien : la vente reste au shop et aux contrats.
 *
 * <p><b>Les annonces ne sont pas sauvegardees.</b> Elles se recalculent, a
 * l'identique, de la graine du monde, du jour (celui des contrats, avec sa
 * bascule) et du nombre de renouvellements payes. Seuls les achats du jour
 * sont gardes ({@link AuctionData}).
 */
public final class AuctionHouse {

    private AuctionHouse() {
    }

    /**
     * Une annonce du jour.
     *
     * @param index  son rang dans la liste du jour : la cle de ses achats
     * @param lot    combien d'objets on recoit par achat
     * @param stock  combien de lots le vendeur propose
     * @param price  le prix d'un lot
     * @param factor ce prix, en part de la cote
     */
    public record Listing(int index, AuctionConfig.Offer offer, Item item, int lot, int stock, long price,
                          String seller, double factor) {

        public ItemStack icon() {
            return new ItemStack(item, Math.min(lot, item.getDefaultMaxStackSize()));
        }
    }

    public static AuctionData data(ServerPlayer player) {
        AuctionData data = player.getData(DoHyAttachments.AUCTION);
        data.sync(ContractApi.today());
        return data;
    }

    /** Les annonces du jour, dans l'ordre des rayons. */
    public static List<Listing> listings(ServerPlayer player) {
        AuctionConfig.Config config = AuctionConfig.config();
        AuctionData data = data(player);
        long seed = player.level().getServer().overworld().getSeed();
        seed = seed * 6364136223846793005L + ContractApi.today();
        seed = seed * 6364136223846793005L + data.rerolls();
        seed = seed * 6364136223846793005L + player.getUUID().getLeastSignificantBits();
        Random random = new Random(seed);
        List<Listing> listings = new ArrayList<>();
        for (AuctionConfig.Category category : config.categories()) {
            // Les offres dont l'objet existe dans cette partie.
            List<AuctionConfig.Offer> pool = new ArrayList<>();
            List<Item> items = new ArrayList<>();
            for (AuctionConfig.Offer offer : config.offers()) {
                if (!offer.category().equals(category.id()) || offer.weight() <= 0) {
                    continue;
                }
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(offer.item());
                if (item.isPresent()) {
                    pool.add(offer);
                    items.add(item.get());
                }
            }
            for (int pick = 0; pick < category.count() && !pool.isEmpty(); pick++) {
                int total = 0;
                for (AuctionConfig.Offer offer : pool) {
                    total += offer.weight();
                }
                int roll = random.nextInt(total);
                int chosen = 0;
                while (roll >= pool.get(chosen).weight()) {
                    roll -= pool.get(chosen).weight();
                    chosen++;
                }
                AuctionConfig.Offer offer = pool.remove(chosen);
                Item item = items.remove(chosen);
                int lot = Math.max(1, offer.lot().pick(random));
                int stock = Math.max(1, offer.stock().pick(random));
                double factor = factor(config, offer, random);
                long price = round(offer.quote() * (double) lot * factor);
                String seller = config.sellers().get(random.nextInt(config.sellers().size()));
                listings.add(new Listing(listings.size(), offer, item, lot, stock, price, seller,
                        price / (offer.quote() * (double) lot)));
            }
        }
        return listings;
    }

    private static double factor(AuctionConfig.Config config, AuctionConfig.Offer offer, Random random) {
        if (offer.factor().isPresent()) {
            return offer.factor().get().roll(random);
        }
        int total = 0;
        for (AuctionConfig.Pricing pricing : config.prices()) {
            total += Math.max(0, pricing.chance());
        }
        if (total <= 0) {
            return 1.0d;
        }
        int roll = random.nextInt(total);
        for (AuctionConfig.Pricing pricing : config.prices()) {
            roll -= Math.max(0, pricing.chance());
            if (roll < 0) {
                return pricing.factor().roll(random);
            }
        }
        return 1.0d;
    }

    /** Un prix d'annonce : trois chiffres significatifs, comme un vendeur l'ecrirait. */
    private static long round(double amount) {
        if (amount < 1000.0d) {
            return Math.max(1L, Math.round(amount));
        }
        double scale = Math.pow(10.0d, Math.floor(Math.log10(amount)) - 2.0d);
        return Math.round(amount / scale) * (long) scale;
    }

    public static int remaining(ServerPlayer player, Listing listing) {
        return Math.max(0, listing.stock() - data(player).bought(listing.index()));
    }

    /** Achete un lot d'une annonce. Retourne la raison du refus, ou {@code null} si reussi. */
    public static String buy(ServerPlayer player, int index) {
        List<Listing> listings = listings(player);
        if (index < 0 || index >= listings.size()) {
            return "Cette annonce n'existe plus.";
        }
        Listing listing = listings.get(index);
        if (remaining(player, listing) <= 0) {
            return "Tout est vendu.";
        }
        if (!Economy.tryDebit(player, listing.price())) {
            return "Il te faut " + MoneyFormat.exact(listing.price()) + " en liquide.";
        }
        data(player).buy(index);
        int left = listing.lot();
        int max = Math.max(1, listing.item().getDefaultMaxStackSize());
        while (left > 0) {
            ItemStack stack = new ItemStack(listing.item(), Math.min(max, left));
            left -= stack.getCount();
            // add() vide la pile de ce qu'il range : ce qui reste n'a pas trouve de place.
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        DoHy.LOGGER.info("Marche : {} achete {} x{} a {} pour {}", player.getName().getString(),
                listing.offer().item(), listing.lot(), listing.seller(), listing.price());
        return null;
    }

    /** Le prix du prochain renouvellement paye, ou -1 s'il n'y en a plus aujourd'hui. */
    public static long rerollPrice(ServerPlayer player) {
        AuctionConfig.Config config = AuctionConfig.config();
        int done = data(player).rerolls();
        return done >= config.maxRerolls() ? -1L : config.perReroll() << done;
    }

    /** Paie pour de nouvelles annonces. Retourne la raison du refus, ou {@code null} si reussi. */
    public static String reroll(ServerPlayer player) {
        long price = rerollPrice(player);
        if (price < 0L) {
            return "Les vendeurs n'ont plus rien de neuf aujourd'hui.";
        }
        if (!Economy.tryDebit(player, price)) {
            return "Il te faut " + MoneyFormat.exact(price) + " en liquide.";
        }
        data(player).reroll();
        return null;
    }
}
