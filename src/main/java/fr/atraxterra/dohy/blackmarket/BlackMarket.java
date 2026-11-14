package fr.atraxterra.dohy.blackmarket;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.war.NationTraits;
import fr.atraxterra.dohy.faction.war.WarStrategy;
import fr.atraxterra.dohy.palamod.dungeon.JobBottleItem;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeon;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Le marche noir : le receleur du spawn, sous le pont, toujours ouvert.
 *
 * <p>Chaque jour (bascule de 5 h des contrats), un autre arrivage : de la
 * marchandise volee aux nations rivales, bon marche mais qui donne un grief a
 * la nation volee ; de la contrebande, qu'on ne trouve nulle part ailleurs ;
 * un lot sans etiquette ; et trois objets qu'il rachete, quelques pieces par
 * jour, ce que le shop refuse.
 *
 * <p>Comme le marche, <b>l'arrivage n'est pas sauvegarde</b> : il se recalcule
 * a l'identique de la graine du monde, du jour et du joueur. Seuls les achats
 * et les ventes du jour sont gardes ({@link BlackMarketData}).
 */
public final class BlackMarket {

    private BlackMarket() {
    }

    /**
     * Une offre du jour.
     *
     * @param stolen vole a une nation ({@code nation}), sinon de la contrebande
     * @param lot    combien d'objets par achat
     * @param stock  combien de lots aujourd'hui
     * @param price  le prix d'un lot
     */
    public record Listing(int index, boolean stolen, BlackMarketConfig.Offer offer, Item item, int lot, int stock,
                          long price, int nation) {

        public ItemStack icon() {
            return stack(Math.min(lot, item.getDefaultMaxStackSize()));
        }

        ItemStack stack(int count) {
            ItemStack stack = new ItemStack(item, count);
            if (offer.jobXp() > 0 && item instanceof JobBottleItem) {
                stack.set(PalaDungeon.JOB_XP.get(), offer.jobXp());
            }
            return stack;
        }
    }

    /**
     * Ce que le receleur rachete aujourd'hui.
     *
     * @param cap  combien de pieces, au plus, aujourd'hui
     * @param unit ce qu'il paie une piece intacte
     */
    public record Wanted(int index, BlackMarketConfig.Demand demand, Item item, int cap, long unit) {
    }

    public static BlackMarketData data(ServerPlayer player) {
        BlackMarketData data = player.getData(DoHyAttachments.BLACK_MARKET.get());
        data.sync(ContractApi.today());
        return data;
    }

    private static Random random(ServerPlayer player, long salt) {
        long seed = player.level().getServer().overworld().getSeed();
        seed = seed * 6364136223846793005L + ContractApi.today();
        seed = seed * 6364136223846793005L + player.getUUID().getLeastSignificantBits();
        seed = seed * 6364136223846793005L + salt;
        return new Random(seed);
    }

    /** Les offres du jour : la marchandise volee, puis la contrebande. */
    public static List<Listing> listings(ServerPlayer player) {
        BlackMarketConfig.Config config = BlackMarketConfig.config();
        Random random = random(player, 1L);
        List<Listing> out = new ArrayList<>();
        shelf(config.stolen(), true, random, out);
        shelf(config.contraband(), false, random, out);
        return out;
    }

    private static void shelf(BlackMarketConfig.Shelf shelf, boolean stolen, Random random, List<Listing> out) {
        List<BlackMarketConfig.Offer> pool = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        for (BlackMarketConfig.Offer offer : shelf.offers()) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(offer.item());
            if (offer.weight() > 0 && item.isPresent()) {
                pool.add(offer);
                items.add(item.get());
            }
        }
        for (int pick = 0; pick < shelf.count() && !pool.isEmpty(); pick++) {
            int total = 0;
            for (BlackMarketConfig.Offer offer : pool) {
                total += offer.weight();
            }
            int roll = random.nextInt(total);
            int chosen = 0;
            while (roll >= pool.get(chosen).weight()) {
                roll -= pool.get(chosen).weight();
                chosen++;
            }
            BlackMarketConfig.Offer offer = pool.remove(chosen);
            Item item = items.remove(chosen);
            int lot = Math.max(1, offer.lot().pick(random));
            int stock = Math.max(1, offer.stock().pick(random));
            long price = round(offer.value() * (double) lot * shelf.factor().roll(random));
            int nation = stolen ? random.nextInt(NationTraits.count()) : -1;
            out.add(new Listing(out.size(), stolen, offer, item, lot, stock, price, nation));
        }
    }

    /** Ce que le receleur rachete aujourd'hui. */
    public static List<Wanted> wanted(ServerPlayer player) {
        BlackMarketConfig.Buyback buyback = BlackMarketConfig.config().buyback();
        Random random = random(player, 2L);
        List<BlackMarketConfig.Demand> pool = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        for (BlackMarketConfig.Demand demand : buyback.demands()) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(demand.item());
            if (demand.weight() > 0 && item.isPresent()) {
                pool.add(demand);
                items.add(item.get());
            }
        }
        List<Wanted> out = new ArrayList<>();
        for (int pick = 0; pick < buyback.count() && !pool.isEmpty(); pick++) {
            int total = 0;
            for (BlackMarketConfig.Demand demand : pool) {
                total += demand.weight();
            }
            int roll = random.nextInt(total);
            int chosen = 0;
            while (roll >= pool.get(chosen).weight()) {
                roll -= pool.get(chosen).weight();
                chosen++;
            }
            BlackMarketConfig.Demand demand = pool.remove(chosen);
            Item item = items.remove(chosen);
            int cap = Math.max(1, demand.cap().pick(random));
            long unit = round(demand.value() * buyback.factor().roll(random));
            out.add(new Wanted(out.size(), demand, item, cap, unit));
        }
        return out;
    }

    /** La phrase du jour du receleur. */
    public static String quote(ServerPlayer player) {
        List<String> quotes = BlackMarketConfig.config().quotes();
        return quotes.isEmpty() ? "" : quotes.get(random(player, 3L).nextInt(quotes.size()));
    }

    /** « aux Corbeaux », « a Nordheim ». */
    public static String fromNation(int nation) {
        NationTraits traits = NationTraits.of(nation);
        return traits.plural() ? "aux " + traits.name().substring(4) : "a " + traits.name();
    }

    public static int remaining(ServerPlayer player, Listing listing) {
        return Math.max(0, listing.stock() - data(player).bought(listing.index()));
    }

    public static int lotsLeft(ServerPlayer player) {
        return Math.max(0, BlackMarketConfig.config().mystery().stock() - data(player).lots());
    }

    public static int leftToSell(ServerPlayer player, Wanted wanted) {
        return Math.max(0, wanted.cap() - data(player).sold(wanted.index()));
    }

    // -- Les gestes -----------------------------------------------------------------------

    /** Achete un lot d'une offre. La raison du refus, ou {@code null}. */
    public static String buy(ServerPlayer player, int index) {
        List<Listing> listings = listings(player);
        if (index < 0 || index >= listings.size()) {
            return "Cette offre n'existe plus.";
        }
        Listing listing = listings.get(index);
        if (remaining(player, listing) <= 0) {
            return "Plus rien de ca aujourd'hui.";
        }
        if (!Economy.tryDebit(player, listing.price())) {
            return "Il te faut " + MoneyFormat.exact(listing.price()) + " $ en liquide.";
        }
        data(player).buy(index);
        give(player, listing.stack(listing.lot()));
        String name = new ItemStack(listing.item()).getHoverName().getString();
        if (listing.stolen() && listing.nation() >= 0 && FactionApi.has(player)) {
            WarStrategy.grieve(player, listing.nation(), "recel : " + name, BlackMarketConfig.config().grief());
            player.sendSystemMessage(Component.literal("[Receleur] ").withStyle(ChatFormatting.DARK_PURPLE)
                    .append(Component.literal(NationTraits.of(listing.nation()).verb("finira", "finiront")
                            + " par savoir ou est passee sa marchandise.").withStyle(ChatFormatting.GRAY)));
        }
        DoHy.LOGGER.info("Marche noir : {} achete {} x{} pour {}", player.getGameProfile().name(),
                listing.offer().item(), listing.lot(), listing.price());
        return null;
    }

    /** Achete un lot sans etiquette. Ce qu'il contenait, ou la raison du refus (prefixee de {@code !}). */
    public static String mystery(ServerPlayer player) {
        BlackMarketConfig.Mystery mystery = BlackMarketConfig.config().mystery();
        if (mystery.outcomes().isEmpty() || mystery.price() <= 0L) {
            return "!Pas de lot aujourd'hui.";
        }
        if (lotsLeft(player) <= 0) {
            return "!Plus de lot sans etiquette aujourd'hui. Reviens demain.";
        }
        if (!Economy.tryDebit(player, mystery.price())) {
            return "!Il te faut " + MoneyFormat.exact(mystery.price()) + " $ en liquide.";
        }
        data(player).lot();
        int total = 0;
        for (BlackMarketConfig.Outcome outcome : mystery.outcomes()) {
            total += Math.max(0, outcome.weight());
        }
        int roll = player.getRandom().nextInt(Math.max(1, total));
        BlackMarketConfig.Outcome chosen = mystery.outcomes().get(0);
        for (BlackMarketConfig.Outcome outcome : mystery.outcomes()) {
            roll -= Math.max(0, outcome.weight());
            if (roll < 0) {
                chosen = outcome;
                break;
            }
        }
        List<String> names = new ArrayList<>();
        for (BlackMarketConfig.Stack spec : chosen.items()) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(spec.item());
            if (item.isEmpty()) {
                continue;
            }
            ItemStack stack = new ItemStack(item.get(), Math.max(1, spec.count()));
            names.add(stack.getCount() + " " + stack.getHoverName().getString());
            give(player, stack);
        }
        DoHy.LOGGER.info("Marche noir : {} ouvre un lot sans etiquette : {}", player.getGameProfile().name(),
                chosen.label());
        return chosen.label() + " : " + String.join(", ", names) + ".";
    }

    /** Revend au receleur ce qu'il demande, autant qu'il en prend encore. La raison du refus, ou {@code null}. */
    public static String sell(ServerPlayer player, int index) {
        List<Wanted> wanted = wanted(player);
        if (index < 0 || index >= wanted.size()) {
            return "Il ne rachete plus ca.";
        }
        Wanted demand = wanted.get(index);
        int left = leftToSell(player, demand);
        if (left <= 0) {
            return "Il en a assez pour aujourd'hui.";
        }
        var inventory = player.getInventory();
        long paid = 0L;
        int taken = 0;
        // Le sac seulement (les 36 cases) : pas l'armure portee ni la main secondaire.
        for (int slot = 0; slot < 36 && taken < left; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != demand.item()) {
                continue;
            }
            double wear = stack.isDamageableItem() && stack.getMaxDamage() > 0
                    ? Math.max(0.1d, 1.0d - (double) stack.getDamageValue() / stack.getMaxDamage()) : 1.0d;
            int count = Math.min(left - taken, stack.getCount());
            paid += Math.round(demand.unit() * wear) * count;
            taken += count;
            stack.shrink(count);
        }
        String name = new ItemStack(demand.item()).getHoverName().getString();
        if (taken == 0) {
            return "Tu n'as pas de " + name + " dans ton sac.";
        }
        data(player).sell(index, taken);
        Economy.credit(player, paid);
        player.sendSystemMessage(Component.literal("[Receleur] ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(taken + " " + name + " : +" + MoneyFormat.exact(paid) + " $")
                        .withStyle(ChatFormatting.GREEN)));
        DoHy.LOGGER.info("Marche noir : {} revend {} x{} pour {}", player.getGameProfile().name(),
                demand.demand().item(), taken, paid);
        return null;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        int left = stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (left > 0) {
            ItemStack part = stack.copyWithCount(Math.min(max, left));
            left -= part.getCount();
            player.getInventory().add(part);
            if (!part.isEmpty()) {
                player.drop(part, false);
            }
        }
    }

    /** Un prix de receleur : trois chiffres significatifs. */
    static long round(double amount) {
        if (amount < 1000.0d) {
            return Math.max(1L, Math.round(amount));
        }
        double scale = Math.pow(10.0d, Math.floor(Math.log10(amount)) - 2.0d);
        return Math.round(amount / scale) * (long) scale;
    }
}
