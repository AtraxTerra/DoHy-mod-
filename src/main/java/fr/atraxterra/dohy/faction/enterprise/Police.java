package fr.atraxterra.dohy.faction.enterprise;

import fr.atraxterra.dohy.faction.org.Diplomacy;
import fr.atraxterra.dohy.faction.org.Treaty;
import fr.atraxterra.dohy.faction.war.NationTraits;
import fr.atraxterra.dohy.faction.war.WarData;
import fr.atraxterra.dohy.faction.war.WarStrategy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * La police (lot 24d) : la gravite des descentes, les amendes, et les operations que les nations
 * menent contre les affaires de ta nation dans leurs pays.
 *
 * <p>Une descente n'est plus toujours la meme : un <b>controle</b> (une amende), une
 * <b>descente</b> (amende, fermeture 24 h, saisies), un <b>coup de filet</b> (amende, fermeture
 * 72 h, saisies plus lourdes, et le gerant arrete trois jours). Plus le risque est haut, plus le
 * coup de filet est probable ; une affaire legale n'a que des controles ; une organisation alliee
 * de la region evite le coup de filet.
 *
 * <p>Chaque jour, une nation dont la loi reprouve des affaires de ta nation dans ses pays peut
 * annoncer une operation (une par semaine au plus) : elle frappe douze heures plus tard (vingt-quatre
 * si une organisation alliee t'a prevenu) toutes les affaires visees encore ouvertes. D'ici la, on
 * l'achete (selon son avidite et son honneur) ou l'on se met au vert (les affaires visees ferment
 * jusqu'a six heures apres).
 */
public final class Police {

    static final long JAIL = 3L * Enterprises.DAY_MS;
    static final long CLOSED_HEAVY = 72L * Enterprises.HOUR_MS;
    static final long NOTICE = 12L * Enterprises.HOUR_MS;
    static final long ALLY_NOTICE = 24L * Enterprises.HOUR_MS;
    static final long COOLDOWN = 7L * Enterprises.DAY_MS;
    static final long LIE_LOW_AFTER = 6L * Enterprises.HOUR_MS;

    private Police() {
    }

    /** La gravite d'un passage de la police. */
    public enum Severity {
        CONTROLE("Controle de police"), DESCENTE("Descente de police"), COUP_DE_FILET("Coup de filet");

        private final String label;

        Severity(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * La gravite d'un passage : une affaire legale n'a que des controles ; sinon, plus le risque
     * est haut, plus le coup de filet est probable, et l'operation d'une nation n'est jamais un
     * simple controle.
     */
    static Severity severity(Payout.World world, Enterprise enterprise, double risk, boolean operation,
                             RandomSource random) {
        if (world.law(enterprise) == Law.Status.LEGAL) {
            return Severity.CONTROLE;
        }
        double heavy = Math.max(0.0d, Math.min(0.35d, (risk - 35.0d) / 100.0d)) + (operation ? 0.25d : 0.0d);
        double light = operation ? 0.0d : Math.max(0.10d, Math.min(0.50d, 0.55d - risk / 100.0d));
        Syndicate syndicate = world.syndicate(enterprise);
        boolean shielded = syndicate != null && world.allied(syndicate);
        double roll = random.nextDouble();
        if (roll < heavy) {
            return shielded ? Severity.DESCENTE : Severity.COUP_DE_FILET;
        }
        return roll < heavy + light ? Severity.CONTROLE : Severity.DESCENTE;
    }

    /** L'amende : 2, 5 ou 10 % du capital de l'affaire, entre un plancher et un plafond. */
    static long fine(Enterprise enterprise, Severity severity) {
        double share = switch (severity) {
            case CONTROLE -> 0.02d;
            case DESCENTE -> 0.05d;
            case COUP_DE_FILET -> 0.10d;
        };
        long floor = switch (severity) {
            case CONTROLE -> 1_000L;
            case DESCENTE -> 2_000L;
            case COUP_DE_FILET -> 5_000L;
        };
        long cap = switch (severity) {
            case CONTROLE -> 50_000L;
            case DESCENTE -> 100_000L;
            case COUP_DE_FILET -> 200_000L;
        };
        long amount = Math.round(enterprise.capital() * share / 100.0d) * 100L;
        return Math.max(floor, Math.min(cap, amount));
    }

    // -- Les operations des nations ---------------------------------------------------------------

    /** Une nation a-t-elle deja annonce une operation cette semaine ? */
    static boolean recent(Justice justice, int nation, long time) {
        for (Operation operation : justice.operations) {
            if (operation.nation() == nation && operation.announced() > time - COOLDOWN) {
                return true;
            }
        }
        return false;
    }

    /** Chaque jour : les nations dont la loi reprouve tes affaires peuvent annoncer une operation. */
    static void daily(Payout.World world, Payout.Sim sim, long time, RandomSource random) {
        Holdings holdings = world.holdings;
        Map<Integer, Double> exposure = new HashMap<>();
        Map<Integer, TreeSet<Integer>> countries = new HashMap<>();
        for (Enterprise enterprise : holdings.enterprises) {
            if (enterprise.legal() || enterprise.founded() > time) {
                continue;
            }
            Payout.HolderView holder = world.holder(enterprise.country());
            if (holder.own() || holder.nation() < 0) {
                continue;
            }
            Law.Status law = world.law(enterprise);
            double weight = law == Law.Status.INTERDIT ? 1.0d : law == Law.Status.TOLERE ? 0.4d : 0.0d;
            if (weight <= 0.0d) {
                continue;
            }
            exposure.merge(holder.nation(), weight, Double::sum);
            countries.computeIfAbsent(holder.nation(), nation -> new TreeSet<>()).add(enterprise.country());
        }
        for (Map.Entry<Integer, Double> entry : exposure.entrySet()) {
            int nation = entry.getKey();
            if (recent(holdings.justice, nation, time)) {
                continue;
            }
            Treaty treaty = Diplomacy.treaty(world.player, nation);
            if (treaty != null && treaty.kind() == Treaty.Type.ALLIANCE) {
                continue;
            }
            NationTraits traits = NationTraits.of(nation);
            WarData.Relation relation = WarStrategy.relationOf(world.player, nation);
            double chance = 0.05d * Math.min(4.0d, entry.getValue()) * (0.5d + traits.honor())
                    * (0.5d + relation.hostility() / 100.0d);
            if (treaty != null) {
                chance *= treaty.kind() == Treaty.Type.ACCORD ? 0.5d : 0.75d;
            }
            if (Diplomacy.rival(world.player, nation) || relation.stance() == WarData.Stance.GUERRE) {
                chance *= 1.5d;
            }
            if (random.nextDouble() >= chance) {
                continue;
            }
            List<Integer> targets = List.copyOf(countries.get(nation));
            boolean warned = false;
            for (int country : targets) {
                Syndicate syndicate = Syndicate.in(world.region(country));
                warned |= syndicate != null && world.allied(syndicate);
            }
            long notice = warned ? ALLY_NOTICE : NOTICE;
            Operation operation = new Operation(holdings.nextId("o"), nation, targets, time, time + notice,
                    Operation.State.ANNONCEE.name(), 1.0d, false, 0);
            holdings.justice.put(operation);
            long hours = notice / Enterprises.HOUR_MS;
            sim.events.add(new Payout.Event(time, traits.verb("prepare", "preparent")
                    + " une operation de police contre tes affaires dans " + targets.size() + " de "
                    + (traits.plural() ? "leurs" : "ses") + " pays : elle frappera dans " + hours + " h"
                    + (warned ? " (une organisation alliee t'a prevenu)" : "")
                    + " ; achete-la ou mets-toi au vert depuis l'onglet Entreprises", 0xFFEF3826,
                    "Operation annoncee", traits.name() + " · dans " + hours + " h"));
        }
    }

    /** A son heure, une operation frappe toutes les affaires visees encore ouvertes, plus durement. */
    static void strike(Payout.World world, Payout.Sim sim, long time, List<Enterprise> open, RandomSource random) {
        Justice justice = world.holdings.justice;
        for (Operation operation : List.copyOf(justice.operations)) {
            if (!operation.pending() || operation.runs() > time) {
                continue;
            }
            int hits = 0;
            for (Enterprise enterprise : open) {
                if (enterprise.legal() || !operation.countries().contains(enterprise.country())
                        || sim.raided.contains(enterprise.id()) || sim.closed(enterprise, time)
                        || world.law(enterprise) == Law.Status.LEGAL) {
                    continue;
                }
                double risk = Math.min(95.0d, world.factors(enterprise).risk() * operation.strength());
                Payout.raid(world, sim, enterprise, time, severity(world, enterprise, risk, true, random));
                hits++;
            }
            justice.put(operation.done(hits));
            NationTraits traits = NationTraits.of(operation.nation());
            String text = hits == 0 ? "L'operation de police " + traits.de() + " ne trouve rien : tes affaires visees "
                    + "etaient fermees" : "L'operation de police " + traits.de() + " frappe " + hits + " de tes affaires";
            sim.events.add(new Payout.Event(time, text, hits == 0 ? 0xFF22E558 : 0xFFEF3826,
                    "Operation de police", traits.name() + " : " + hits + " affaire(s) touchee(s)"));
        }
    }

    /** Les affaires qu'une operation frapperait : illegales, dans ses pays, et que la loi n'autorise pas. */
    public static List<Enterprise> targets(ServerPlayer player, Operation operation) {
        Payout.World world = new Payout.World(player, Enterprises.of(player), System.currentTimeMillis());
        List<Enterprise> out = new ArrayList<>();
        for (Enterprise enterprise : world.holdings.enterprises) {
            if (!enterprise.legal() && operation.countries().contains(enterprise.country())
                    && world.law(enterprise) != Law.Status.LEGAL) {
                out.add(enterprise);
            }
        }
        return out;
    }

    public static List<Operation> pending(ServerPlayer player) {
        return Enterprises.of(player).justice.pending();
    }

    // -- Acheter, ou se mettre au vert ------------------------------------------------------------

    private static long hundred(double amount) {
        return Math.max(100L, Math.round(amount / 100.0d) * 100L);
    }

    public static long bribePrice(ServerPlayer player, Operation operation) {
        NationTraits traits = NationTraits.of(operation.nation());
        int targets = Math.max(1, targets(player, operation).size());
        return hundred((20_000.0d + 8_000.0d * targets) * (0.6d + traits.greed()));
    }

    /** Ses chances : l'avidite de la nation contre son honneur, une organisation alliee aide, une rivale non. */
    public static double bribeChance(ServerPlayer player, Operation operation) {
        NationTraits traits = NationTraits.of(operation.nation());
        double chance = 0.25d + 0.6d * traits.greed() - 0.3d * traits.honor();
        Holdings holdings = Enterprises.of(player);
        for (int country : operation.countries()) {
            Syndicate syndicate = Syndicate.in(Region.of(player.level().getServer(), country));
            if (syndicate != null && holdings.standing(syndicate).state() == Holdings.Status.ALLIANCE) {
                chance += 0.2d;
                break;
            }
        }
        if (Diplomacy.rival(player, operation.nation())) {
            chance *= 0.5d;
        }
        return Math.max(0.05d, Math.min(0.9d, chance));
    }

    /** Pourquoi on ne peut plus acheter cette operation, ou {@code null}. */
    public static String bribeRefusal(ServerPlayer player, Operation operation) {
        if (operation == null || !operation.pending()) {
            return "Cette operation n'est plus en attente";
        }
        if (operation.bribed()) {
            return "Elle a deja refuse ton argent";
        }
        if (WarStrategy.relationOf(player, operation.nation()).stance() == WarData.Stance.GUERRE) {
            return "On n'achete pas une nation en guerre avec toi";
        }
        return null;
    }

    /**
     * Tente d'acheter l'operation, en argent sale d'abord : reussi, elle n'a pas lieu ; rate,
     * l'argent est perdu et elle frappera plus fort. Le refus, ou le resultat (« ! » s'il est mauvais).
     */
    public static String bribe(ServerPlayer player, String id) {
        Holdings holdings = Enterprises.of(player);
        Operation operation = holdings.justice.operation(id);
        String refusal = bribeRefusal(player, operation);
        if (refusal != null) {
            return "!" + refusal;
        }
        long price = bribePrice(player, operation);
        double chance = bribeChance(player, operation);
        NationTraits traits = NationTraits.of(operation.nation());
        String unpaid = Enterprises.pay(player, holdings, price, true, "Corruption : " + traits.name());
        if (unpaid != null) {
            return "!" + unpaid;
        }
        long now = System.currentTimeMillis();
        if (player.getRandom().nextDouble() < chance) {
            holdings.justice.put(operation.as(Operation.State.ACHETEE));
            String text = "Pots-de-vin acceptes : l'operation " + traits.de() + " n'aura pas lieu ("
                    + MoneyFormat.exact(price) + " $)";
            holdings.log(now, text);
            return text;
        }
        holdings.justice.put(operation.bribeFailed());
        String text = "Corruption ratee : " + traits.the() + " " + (traits.plural() ? "gardent" : "garde")
                + " ton argent (" + MoneyFormat.exact(price) + " $) et " + (traits.plural() ? "renforcent" : "renforce")
                + " l'operation";
        holdings.log(now, text);
        return "!" + text;
    }

    /** Ferme les affaires visees jusqu'a six heures apres l'operation : elle ne trouvera rien. */
    public static String lieLow(ServerPlayer player, String id) {
        Holdings holdings = Enterprises.of(player);
        Operation operation = holdings.justice.operation(id);
        if (operation == null || !operation.pending()) {
            return "Cette operation n'est plus en attente";
        }
        List<Enterprise> targets = targets(player, operation);
        long until = operation.runs() + LIE_LOW_AFTER;
        for (Enterprise enterprise : targets) {
            holdings.replace(enterprise.closedUntil(Math.max(until, enterprise.closedUntil())));
        }
        holdings.justice.put(operation.as(Operation.State.AU_VERT));
        holdings.log(System.currentTimeMillis(), "Au vert : " + targets.size() + " affaire(s) fermee(s) jusqu'apres "
                + "l'operation " + NationTraits.of(operation.nation()).de());
        return null;
    }
}
