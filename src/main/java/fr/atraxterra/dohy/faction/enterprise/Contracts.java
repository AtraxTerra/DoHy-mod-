package fr.atraxterra.dohy.faction.enterprise;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Les contrats des organisations criminelles (lot 24d). Chaque jour, une organisation qui n'est
 * pas en guerre avec ta nation peut lui proposer de livrer tant d'unites d'un produit, ou tant de
 * caisses : trois propositions au plus a la fois, deux jours pour les accepter, trois pour livrer.
 * On livre depuis les stocks (les colis, sachets et caisses portes sur soi y passent d'abord).
 * Rempli, le contrat paie en argent sale, plus cher que la rue, et l'organisation s'adoucit ; manque,
 * elle s'en souvient.
 */
public final class Contracts {

    static final long OFFER_DAYS = 2L;
    static final long DELIVERY_DAYS = 3L;
    static final int MAX_OFFERS = 3;
    /** Ce qu'un contrat paie au-dessus du prix de gros ; une organisation alliee paie mieux. */
    static final double PREMIUM = 1.35d;
    static final double ALLY_PREMIUM = 0.20d;
    static final double FAILED = 12.0d;
    static final double DONE = 8.0d;

    private Contracts() {
    }

    // -- Chaque jour ------------------------------------------------------------------------------

    static void daily(Payout.World world, Payout.Sim sim, long time, RandomSource random) {
        Holdings holdings = world.holdings;
        Justice justice = holdings.justice;
        for (Contract contract : List.copyOf(justice.contracts)) {
            Syndicate syndicate = contract.from();
            if (contract.phase() == Contract.State.OFFRE && contract.offered() + OFFER_DAYS * Payout.DAY <= time) {
                justice.put(contract.as(Contract.State.EXPIRE));
            } else if (contract.phase() == Contract.State.EN_COURS && contract.deadline() <= time) {
                justice.put(contract.as(Contract.State.ECHOUE));
                if (syndicate != null) {
                    Holdings.Standing standing = holdings.standing(syndicate);
                    holdings.put(standing.with(standing.hostility() + FAILED, standing.state(), standing.until()));
                    sim.events.add(new Payout.Event(time, "Contrat manque avec " + syndicate.label() + " ("
                            + label(contract.key(), contract.units()) + ") : hostilite +" + Math.round(FAILED),
                            0xFFEF3826, "Contrat manque", syndicate.label()));
                }
            }
        }
        int offers = 0;
        for (Contract contract : justice.contracts) {
            if (contract.phase() == Contract.State.OFFRE) {
                offers++;
            }
        }
        List<Syndicate> order = new ArrayList<>(List.of(Syndicate.values()));
        java.util.Collections.shuffle(order, new java.util.Random(random.nextLong()));
        for (Syndicate syndicate : order) {
            if (offers >= MAX_OFFERS) {
                break;
            }
            Holdings.Standing standing = holdings.standing(syndicate);
            if (standing.state() == Holdings.Status.GUERRE || busy(justice, syndicate)) {
                continue;
            }
            double chance = 0.22d + (world.deal(syndicate) ? 0.18d : 0.0d) - (standing.hostility() > 50.0d ? 0.12d : 0.0d);
            if (random.nextDouble() >= chance) {
                continue;
            }
            Contract offer = offer(world, syndicate, time, random, holdings.nextId("k"));
            justice.put(offer);
            offers++;
            sim.events.add(new Payout.Event(time, syndicate.label() + " te propose un contrat : "
                    + label(offer.key(), offer.units()) + " pour " + MoneyFormat.exact(offer.reward())
                    + " $ sales (deux jours pour accepter)", 0xFFFAC400, "Contrat propose",
                    syndicate.label() + " · " + MoneyFormat.exact(offer.reward()) + " $"));
        }
    }

    /** L'organisation a-t-elle deja un contrat propose ou en cours avec ta nation ? */
    static boolean busy(Justice justice, Syndicate syndicate) {
        for (Contract contract : justice.contracts) {
            if (contract.open() && contract.syndicate().equals(syndicate.name())) {
                return true;
            }
        }
        return false;
    }

    /** Ce qu'elle demande : selon sa specialite, un produit ou des caisses, en quantites rondes. */
    static String wanted(Syndicate syndicate, RandomSource random) {
        return switch (syndicate) {
            case DOCKS -> random.nextBoolean() ? Enterprises.CRATES : Line.SYNTHESE.productKey();
            case CONDOR -> Line.COCAINE.productKey();
            case VALCORTE -> random.nextBoolean() ? Line.SYNTHESE.productKey() : Line.COCAINE.productKey();
            case CARAVANE -> Line.CANNABIS.productKey();
            case LOTUS -> random.nextBoolean() ? Enterprises.CRATES : Line.HEROINE.productKey();
            case RECIFS -> Line.values()[random.nextInt(Line.values().length)].productKey();
        };
    }

    static Contract offer(Payout.World world, Syndicate syndicate, long time, RandomSource random, String id) {
        String key = wanted(syndicate, random);
        boolean crates = key.equals(Enterprises.CRATES);
        long units = crates ? 4L * (1 + random.nextInt(4)) : 32L * (1 + random.nextInt(4));
        double premium = PREMIUM + (world.allied(syndicate) ? ALLY_PREMIUM : 0.0d);
        long reward = Math.round(units * unit(key) * premium / 100.0d) * 100L;
        return new Contract(id, syndicate.name(), key, units, 0L, reward, time, 0L, Contract.State.OFFRE.name());
    }

    /** Le prix de gros d'une unite de ce stock. */
    static double unit(String key) {
        if (key.equals(Enterprises.CRATES)) {
            return Enterprises.CRATE_WHOLESALE;
        }
        Line line = line(key);
        return line == null ? 0.0d : line.wholesale();
    }

    static Line line(String key) {
        return key.startsWith("drug:") ? Line.of(key.substring(5)) : null;
    }

    /** « 64 unites de cocaine », « 8 caisses d'armes ». */
    public static String label(String key, long units) {
        if (key.equals(Enterprises.CRATES)) {
            return MoneyFormat.exact(units) + " caisse(s) d'armes";
        }
        Line line = line(key);
        return MoneyFormat.exact(units) + " unite(s) " + (line == null ? "de produit"
                : "de " + line.label().toLowerCase());
    }

    public static ItemStack icon(String key) {
        if (key.equals(Enterprises.CRATES)) {
            return new ItemStack(net.minecraft.world.item.Items.CHEST);
        }
        Line line = line(key);
        return line == null ? new ItemStack(net.minecraft.world.item.Items.PAPER) : line.productIcon();
    }

    // -- Les gestes -------------------------------------------------------------------------------

    public static List<Contract> open(ServerPlayer player) {
        return Enterprises.of(player).justice.open();
    }

    public static String accept(ServerPlayer player, String id) {
        if (!FactionApi.has(player)) {
            return "Il te faut une nation";
        }
        Holdings holdings = Enterprises.of(player);
        Contract contract = holdings.justice.contract(id);
        if (contract == null || contract.phase() != Contract.State.OFFRE) {
            return "Ce contrat n'est plus propose";
        }
        long now = System.currentTimeMillis();
        holdings.justice.put(contract.accepted(now + DELIVERY_DAYS * Payout.DAY));
        Syndicate syndicate = contract.from();
        holdings.log(now, "Contrat accepte avec " + (syndicate == null ? "?" : syndicate.label()) + " : "
                + label(contract.key(), contract.units()) + " sous trois jours");
        return null;
    }

    public static String refuse(ServerPlayer player, String id) {
        Holdings holdings = Enterprises.of(player);
        Contract contract = holdings.justice.contract(id);
        if (contract == null || contract.phase() != Contract.State.OFFRE) {
            return "Ce contrat n'est plus propose";
        }
        holdings.justice.put(contract.as(Contract.State.EXPIRE));
        return null;
    }

    /**
     * Livre ce qu'on peut : d'abord ce que le joueur porte (colis, sachets, caisses de ce produit,
     * jamais plus que ce qui manque), puis les stocks. Le refus (« ! » en tete), ou ce qui s'est passe.
     */
    public static String deliver(ServerPlayer player, String id) {
        if (!FactionApi.has(player)) {
            return "!Il te faut une nation";
        }
        Holdings holdings = Enterprises.of(player);
        Contract contract = holdings.justice.contract(id);
        if (contract == null || contract.phase() != Contract.State.EN_COURS) {
            return "!Ce contrat n'est pas en cours";
        }
        long now = System.currentTimeMillis();
        if (contract.deadline() <= now) {
            return "!L'echeance est passee";
        }
        long missing = contract.missing();
        long carried = fromInventory(player, contract.key(), missing);
        long fromStock = Math.min(missing - carried, holdings.stock(contract.key()));
        if (fromStock > 0L) {
            holdings.add(contract.key(), -fromStock);
        }
        long given = carried + fromStock;
        if (given <= 0L) {
            return "!Rien a livrer : " + label(contract.key(), missing) + " manquent, ni sur toi ni en stock";
        }
        Contract next = contract.plus(given);
        holdings.justice.put(next);
        Syndicate syndicate = contract.from();
        String who = syndicate == null ? "l'organisation" : syndicate.label();
        if (next.phase() == Contract.State.FAIT) {
            holdings.dirty += next.reward();
            if (syndicate != null) {
                Holdings.Standing standing = holdings.standing(syndicate);
                holdings.put(standing.with(standing.hostility() - DONE, standing.state(), standing.until()));
            }
            String text = "Contrat rempli avec " + who + " : " + MoneyFormat.exact(next.reward())
                    + " $ d'argent sale, hostilite -" + Math.round(DONE);
            holdings.log(now, text);
            return text;
        }
        String text = "Livre a " + who + " : " + label(contract.key(), given) + " ; il en manque encore "
                + MoneyFormat.exact(next.missing());
        holdings.log(now, text);
        return text;
    }

    /** Retire de l'inventaire des objets de ce stock, sans depasser {@code needed} unites ; rend les unites. */
    private static long fromInventory(ServerPlayer player, String key, long needed) {
        long taken = 0L;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && taken < needed; slot++) {
            ItemStack stack = inventory.getItem(slot);
            Optional<fr.atraxterra.dohy.underground.Goods.Good> good = fr.atraxterra.dohy.underground.Goods.of(stack);
            if (good.isEmpty() || !good.get().key().equals(key)) {
                continue;
            }
            int per = good.get().units();
            int count = (int) Math.min(stack.getCount(), (needed - taken) / per);
            if (count <= 0) {
                continue;
            }
            stack.shrink(count);
            inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            taken += (long) count * per;
        }
        if (taken > 0L) {
            inventory.setChanged();
        }
        return taken;
    }
}
