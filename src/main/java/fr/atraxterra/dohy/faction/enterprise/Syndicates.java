package fr.atraxterra.dohy.faction.enterprise;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.org.Organisations;
import fr.atraxterra.dohy.faction.org.Post;
import fr.atraxterra.dohy.faction.org.Regime;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les six organisations criminelles (lot 24b) face a la nation. Chaque jour, celle d'une region
 * s'echauffe d'autant plus que la nation y tient d'affaires illegales sans s'etre entendue avec
 * elle ; a 75 d'hostilite, c'est la guerre de territoire : sept jours de revenus amputes, de
 * risque en plus et de sabotages. Un accord (14 jours) la calme et la met au service de la
 * nation ; une alliance (30 jours) fait mieux encore, contre une part des gains de la region.
 * En guerre, on paie un tribut, ou l'on tente une riposte.
 */
public final class Syndicates {

    /** A cette hostilite, l'organisation part en guerre de territoire. */
    public static final double WAR_AT = 75.0d;
    /** Au-dela, elle refuse un accord. */
    public static final double ACCORD_MAX = 60.0d;
    /** Au-dela, elle refuse une alliance. */
    public static final double ALLIANCE_MAX = 25.0d;
    static final long WAR_DAYS = 7L;
    static final long ACCORD_DAYS = 14L;
    static final long ALLIANCE_DAYS = 30L;
    static final long WEAKENED_DAYS = 14L;
    static final long RIPOSTE_PRICE = 30_000L;

    /** Ce que la nation peut faire avec une organisation. */
    public enum Action {
        ACCORD, ALLIANCE, BREAK, TRIBUTE, RIPOSTE
    }

    private Syndicates() {
    }

    public static Holdings.Standing standing(ServerPlayer player, Syndicate syndicate) {
        return Enterprises.of(player).standing(syndicate);
    }

    /** L'hostilite vers laquelle elle revient d'elle-meme : 30, moins l'estime que le regime inspire au milieu. */
    public static double baseline(Regime regime) {
        return 30.0d - regime.crime();
    }

    /** Les affaires illegales de la nation dans la region de l'organisation. */
    public static List<Enterprise> inRegion(ServerPlayer player, Syndicate syndicate) {
        MinecraftServer server = player.level().getServer();
        Map<Integer, Region> regions = new HashMap<>();
        List<Enterprise> out = new ArrayList<>();
        for (Enterprise enterprise : Enterprises.of(player).enterprises) {
            if (enterprise.legal()) {
                continue;
            }
            Region region = regions.computeIfAbsent(enterprise.country(), country -> Region.of(server, country));
            if (region == syndicate.region()) {
                out.add(enterprise);
            }
        }
        return out;
    }

    /** La presence de la nation dans la region : la somme des niveaux de ses affaires illegales. */
    public static int presence(ServerPlayer player, Syndicate syndicate) {
        int levels = 0;
        for (Enterprise enterprise : inRegion(player, syndicate)) {
            levels += enterprise.level();
        }
        return levels;
    }

    // -- Chaque jour ------------------------------------------------------------------------------

    static void daily(Payout.World world, Payout.Sim sim, long time, RandomSource random) {
        Holdings holdings = world.holdings;
        double baseline = baseline(world.regime);
        for (Syndicate syndicate : Syndicate.values()) {
            Holdings.Standing standing = holdings.standing(syndicate);
            List<Enterprise> exposed = new ArrayList<>();
            int presence = 0;
            for (Enterprise enterprise : holdings.enterprises) {
                if (enterprise.legal() || enterprise.founded() > time
                        || world.region(enterprise.country()) != syndicate.region()) {
                    continue;
                }
                presence += enterprise.level();
                if (!sim.closed(enterprise, time)) {
                    exposed.add(enterprise);
                }
            }
            boolean weakened = standing.weakened() > time;
            switch (standing.state()) {
                case NEUTRE -> {
                    double heat = presence * 2.0d * (0.5d + syndicate.aggression()) * (weakened ? 0.5d : 1.0d);
                    double hostility = drift(standing.hostility() + heat, baseline, 3.0d);
                    if (hostility >= WAR_AT && presence > 0) {
                        holdings.put(standing.with(hostility, Holdings.Status.GUERRE, time + WAR_DAYS * Payout.DAY));
                        sim.events.add(new Payout.Event(syndicate.label() + " te declare la guerre de territoire en "
                                + syndicate.region().label() + " pour 7 jours", 0xFFEF3826, true));
                    } else {
                        holdings.put(standing.with(hostility, Holdings.Status.NEUTRE, 0L));
                    }
                }
                case ACCORD, ALLIANCE -> {
                    boolean alliance = standing.state() == Holdings.Status.ALLIANCE;
                    double hostility = Math.max(0.0d, standing.hostility() - (alliance ? 3.0d : 2.0d));
                    if (standing.until() <= time) {
                        holdings.put(standing.with(hostility, Holdings.Status.NEUTRE, 0L));
                        sim.events.add(new Payout.Event((alliance ? "L'alliance" : "L'accord") + " avec "
                                + syndicate.label() + " a pris fin : renouvelle-le depuis l'onglet Entreprises",
                                0xFFFAC400, true));
                    } else {
                        holdings.put(standing.with(hostility, standing.state(), standing.until()));
                    }
                }
                case GUERRE -> {
                    if (standing.until() <= time) {
                        holdings.put(standing.with(45.0d, Holdings.Status.NEUTRE, 0L));
                        sim.events.add(new Payout.Event("La guerre de territoire avec " + syndicate.label()
                                + " est finie", 0xFF22E558, false));
                    } else if (!exposed.isEmpty() && random.nextDouble() < 0.4d + 0.3d * syndicate.aggression()) {
                        Enterprise target = exposed.get(random.nextInt(exposed.size()));
                        sim.close(target, time + Payout.CLOSED_SABOTAGE);
                        sim.events.add(new Payout.Event(syndicate.label() + " sabote " + target.name()
                                + Enterprises.place(target) + " : fermee 12 h", 0xFFEF3826, true));
                    }
                }
            }
        }
    }

    /** Rapproche {@code value} de {@code target} d'au plus {@code step}. */
    static double drift(double value, double target, double step) {
        if (value > target) {
            return Math.max(target, value - step);
        }
        return Math.min(target, value + step);
    }

    // -- Les prix ---------------------------------------------------------------------------------

    private static long hundred(double amount) {
        return Math.max(100L, Math.round(amount / 100.0d) * 100L);
    }

    public static long accordPrice(Syndicate syndicate) {
        return hundred(30_000.0d * (0.7d + 0.6d * syndicate.greed()));
    }

    public static long alliancePrice(Syndicate syndicate) {
        return hundred(90_000.0d * (0.7d + 0.6d * syndicate.greed()));
    }

    public static long tributePrice(ServerPlayer player, Syndicate syndicate) {
        return hundred((25_000.0d + 10_000.0d * presence(player, syndicate)) * (0.7d + 0.6d * syndicate.greed()));
    }

    public static long ripostePrice() {
        return RIPOSTE_PRICE;
    }

    /** Les chances d'une riposte : ton general, ton regime, contre son agressivite ; de 10 a 90 %. */
    public static double riposteChance(ServerPlayer player, Syndicate syndicate) {
        Regime regime = Organisations.regime(player);
        double chance = 0.35d + 0.3d * Math.min(1.0d, Organisations.strength(player, Post.GENERAL))
                + (regime == Regime.MILITAIRE ? 0.15d : regime == Regime.CRIMINELLE ? 0.10d : 0.0d)
                - 0.2d * syndicate.aggression();
        return Math.max(0.1d, Math.min(0.9d, chance));
    }

    // -- Les gestes -------------------------------------------------------------------------------

    /** Pourquoi ce geste est impossible aujourd'hui, ou {@code null}. */
    public static String refusal(ServerPlayer player, Syndicate syndicate, Action action) {
        if (!FactionApi.has(player)) {
            return "Il te faut une nation";
        }
        Holdings.Standing standing = standing(player, syndicate);
        Holdings.Status status = standing.state();
        return switch (action) {
            case ACCORD -> status == Holdings.Status.GUERRE ? "En guerre : paie un tribut ou riposte d'abord"
                    : status == Holdings.Status.ALLIANCE ? "Vous etes deja allies"
                    : status == Holdings.Status.NEUTRE && standing.hostility() > ACCORD_MAX
                    ? "Trop hostile pour s'entendre (" + Math.round(standing.hostility()) + " sur "
                    + Math.round(ACCORD_MAX) + " au plus)" : null;
            case ALLIANCE -> status == Holdings.Status.GUERRE ? "En guerre : paie un tribut ou riposte d'abord"
                    : status == Holdings.Status.NEUTRE ? "Une alliance se noue sur un accord en cours"
                    : status == Holdings.Status.ACCORD && standing.hostility() > ALLIANCE_MAX
                    ? "Pas assez de confiance (hostilite " + Math.round(standing.hostility()) + ", "
                    + Math.round(ALLIANCE_MAX) + " au plus)" : null;
            case BREAK -> status == Holdings.Status.ACCORD || status == Holdings.Status.ALLIANCE ? null
                    : "Rien a rompre";
            case TRIBUTE, RIPOSTE -> status == Holdings.Status.GUERRE ? null : "Vous n'etes pas en guerre";
        };
    }

    public static String accord(ServerPlayer player, Syndicate syndicate) {
        String refusal = refusal(player, syndicate, Action.ACCORD);
        if (refusal != null) {
            return refusal;
        }
        Holdings holdings = Enterprises.of(player);
        long price = accordPrice(syndicate);
        String unpaid = Enterprises.pay(player, holdings, price, true, "Accord : " + syndicate.label());
        if (unpaid != null) {
            return unpaid;
        }
        long now = System.currentTimeMillis();
        Holdings.Standing standing = holdings.standing(syndicate);
        long from = standing.state() == Holdings.Status.ACCORD ? Math.max(now, standing.until()) : now;
        holdings.put(standing.with(standing.hostility() - 10.0d, Holdings.Status.ACCORD,
                from + ACCORD_DAYS * Payout.DAY));
        holdings.log(now, "Accord avec " + syndicate.label() + " (14 j)");
        return null;
    }

    public static String alliance(ServerPlayer player, Syndicate syndicate) {
        String refusal = refusal(player, syndicate, Action.ALLIANCE);
        if (refusal != null) {
            return refusal;
        }
        Holdings holdings = Enterprises.of(player);
        long price = alliancePrice(syndicate);
        String unpaid = Enterprises.pay(player, holdings, price, true, "Alliance : " + syndicate.label());
        if (unpaid != null) {
            return unpaid;
        }
        long now = System.currentTimeMillis();
        Holdings.Standing standing = holdings.standing(syndicate);
        long from = standing.state() == Holdings.Status.ALLIANCE ? Math.max(now, standing.until()) : now;
        holdings.put(standing.with(standing.hostility() - 10.0d, Holdings.Status.ALLIANCE,
                from + ALLIANCE_DAYS * Payout.DAY));
        holdings.log(now, "Alliance avec " + syndicate.label() + " (30 j)");
        return null;
    }

    public static String breakDeal(ServerPlayer player, Syndicate syndicate) {
        String refusal = refusal(player, syndicate, Action.BREAK);
        if (refusal != null) {
            return refusal;
        }
        Holdings holdings = Enterprises.of(player);
        Holdings.Standing standing = holdings.standing(syndicate);
        boolean alliance = standing.state() == Holdings.Status.ALLIANCE;
        double anger = (alliance ? 40.0d : 25.0d) * (0.5d + syndicate.honor());
        holdings.put(standing.with(standing.hostility() + anger, Holdings.Status.NEUTRE, 0L));
        holdings.log(System.currentTimeMillis(), "Tu romps " + (alliance ? "l'alliance" : "l'accord") + " avec "
                + syndicate.label() + " : hostilite +" + Math.round(anger));
        return null;
    }

    public static String tribute(ServerPlayer player, Syndicate syndicate) {
        String refusal = refusal(player, syndicate, Action.TRIBUTE);
        if (refusal != null) {
            return refusal;
        }
        Holdings holdings = Enterprises.of(player);
        long price = tributePrice(player, syndicate);
        String unpaid = Enterprises.pay(player, holdings, price, true, "Tribut : " + syndicate.label());
        if (unpaid != null) {
            return unpaid;
        }
        Holdings.Standing standing = holdings.standing(syndicate);
        holdings.put(standing.with(40.0d, Holdings.Status.NEUTRE, 0L));
        holdings.log(System.currentTimeMillis(), "Tribut de " + MoneyFormat.exact(price) + " $ a " + syndicate.label()
                + " : la guerre s'arrete");
        return null;
    }

    /**
     * Une riposte contre l'organisation : reussie, la guerre s'arrete et elle reste affaiblie deux
     * semaines ; ratee, la guerre dure deux jours de plus et une affaire de la region ferme.
     * Rend le refus, ou le resultat en texte, « ! » en tete s'il est mauvais.
     */
    public static String riposte(ServerPlayer player, Syndicate syndicate) {
        String refusal = refusal(player, syndicate, Action.RIPOSTE);
        if (refusal != null) {
            return "!" + refusal;
        }
        Holdings holdings = Enterprises.of(player);
        String unpaid = Enterprises.pay(player, holdings, RIPOSTE_PRICE, true, "Riposte : " + syndicate.label());
        if (unpaid != null) {
            return "!" + unpaid;
        }
        long now = System.currentTimeMillis();
        double chance = riposteChance(player, syndicate);
        Holdings.Standing standing = holdings.standing(syndicate);
        if (player.getRandom().nextDouble() < chance) {
            holdings.put(standing.with(30.0d, Holdings.Status.NEUTRE, 0L).weakenedUntil(now + WEAKENED_DAYS
                    * Payout.DAY));
            String text = "Riposte reussie contre " + syndicate.label() + " : la guerre s'arrete, elle reste "
                    + "affaiblie 14 jours";
            holdings.log(now, text);
            return text;
        }
        holdings.put(standing.with(standing.hostility() + 10.0d, Holdings.Status.GUERRE,
                standing.until() + 2L * Payout.DAY));
        List<Enterprise> exposed = new ArrayList<>();
        for (Enterprise enterprise : inRegion(player, syndicate)) {
            if (!enterprise.closed(now)) {
                exposed.add(enterprise);
            }
        }
        String text = "Riposte ratee contre " + syndicate.label() + " : la guerre dure deux jours de plus";
        if (!exposed.isEmpty()) {
            Enterprise target = exposed.get(player.getRandom().nextInt(exposed.size()));
            holdings.replace(target.closedUntil(now + Payout.CLOSED_SABOTAGE));
            text += ", " + target.name() + " ferme 12 h";
        }
        holdings.log(now, text);
        return "!" + text;
    }
}
