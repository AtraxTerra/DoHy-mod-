package fr.atraxterra.dohy.faction.org;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.war.NationTraits;
import fr.atraxterra.dohy.faction.war.WarData;
import fr.atraxterra.dohy.faction.war.WarStrategy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * La diplomatie de ta nation avec les douze nations rivales (lot 24a) : des traites signes
 * (pacte de non-agression, accord commercial, alliance) et des rivalites declarees. Elle pese
 * sur l'IA de guerre : un pacte ou une alliance empeche l'ultimatum et la guerre et retire la
 * nation des coalitions ; chaque traite, le regime et le general changent l'humeur qu'elle
 * tend a avoir envers toi. Les accords et alliances baissent la taxe du marche international.
 *
 * <p>Un traite par nation. L'alliance demande un pacte ou un accord deja en cours, qu'elle
 * remplace. Rompre un pacte ou une alliance est une trahison, que la nation n'oublie pas.
 */
public final class Diplomacy {

    /** La taxe du marche international ne descend pas sous ce plancher, traites compris. */
    public static final double TAX_FLOOR = 0.02d;
    private static final double TAX_CUT_MAX = 0.04d;

    private Diplomacy() {
    }

    // -- Les traites ----------------------------------------------------------------------------

    /** Les traites en cours ; ceux qui ont expire tombent ici, avec une ligne au journal. */
    public static List<Treaty> treaties(ServerPlayer player) {
        Organisation org = Organisations.of(player);
        long now = System.currentTimeMillis();
        List<Treaty> active = new ArrayList<>();
        for (Treaty treaty : List.copyOf(org.treaties)) {
            if (treaty.active(now)) {
                active.add(treaty);
            } else {
                org.treaties.remove(treaty);
                Treaty.Type type = treaty.kind();
                org.log(now, (type == null ? "Un traite" : type.label()) + " avec " + NationTraits.of(treaty.nation())
                        .name() + " a expire");
            }
        }
        return active;
    }

    /** Le traite en cours avec cette nation, ou {@code null}. */
    public static Treaty treaty(ServerPlayer player, int nation) {
        if (!FactionApi.has(player)) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (Treaty treaty : Organisations.of(player).treaties) {
            if (treaty.nation() == nation && treaty.active(now)) {
                return treaty;
            }
        }
        return null;
    }

    /** Un pacte ou une alliance la lie a toi : ni ultimatum, ni guerre, ni coalition contre toi. */
    public static boolean protects(ServerPlayer player, int nation) {
        Treaty treaty = treaty(player, nation);
        return treaty != null && (treaty.kind() == Treaty.Type.PACTE || treaty.kind() == Treaty.Type.ALLIANCE);
    }

    /** Combien de traites la nation peut tenir : deux, un de plus avec un diplomate, deux avec un haut cadre. */
    public static int slots(ServerPlayer player) {
        int slots = 2;
        java.util.Optional<Cadre> diplomat = Organisations.holder(player, Post.DIPLOMATE);
        if (diplomat.isPresent()) {
            slots += diplomat.get().rank() == Grade.CADRE ? 2 : 1;
        }
        return slots;
    }

    /** Le cadeau d'usage a la signature : le prix du traite, selon l'avidite de la nation et ton diplomate. */
    public static long price(ServerPlayer player, int nation, Treaty.Type type) {
        NationTraits traits = NationTraits.of(nation);
        double cut = 0.30d * Math.min(1.0d, Organisations.strength(player, Post.DIPLOMATE));
        double price = type.price() * (0.7d + 0.6d * traits.greed()) * (1.0d - cut);
        return Organisations.round(Math.round(price));
    }

    /** L'hostilite au-dela de laquelle elle refuse ce traite. */
    public static double threshold(ServerPlayer player, int nation, Treaty.Type type) {
        NationTraits traits = NationTraits.of(nation);
        double limit = type.threshold() + 10.0d * Math.min(1.0d, Organisations.strength(player, Post.DIPLOMATE));
        Regime regime = Organisations.regime(player);
        if (regime == Regime.ETAT && type != Treaty.Type.PACTE) {
            limit += 5.0d;
        } else if (regime == Regime.CRIMINELLE) {
            limit -= 10.0d * traits.honor();
        } else if (regime == Regime.MILITAIRE && type == Treaty.Type.PACTE) {
            limit += 5.0d;
        }
        return Math.max(5.0d, limit);
    }

    /** Pourquoi cette nation refuserait ce traite aujourd'hui ; {@code null} si elle signe. */
    public static String refusal(ServerPlayer player, int nation, Treaty.Type type) {
        if (!FactionApi.has(player)) {
            return "Fonde d'abord ta nation";
        }
        if (nation < 0 || nation >= NationTraits.count()) {
            return "Nation inconnue";
        }
        NationTraits traits = NationTraits.of(nation);
        WarData.Relation relation = WarStrategy.relationOf(player, nation);
        if (relation.stance() == WarData.Stance.MENACE || relation.stance() == WarData.Stance.GUERRE) {
            return traits.name() + " ne signe rien en pleine crise : regle d'abord l'ultimatum ou la guerre";
        }
        if (rival(player, nation)) {
            return "Tu as declare " + traits.the() + " rivale : renonce d'abord a la rivalite";
        }
        Treaty current = treaty(player, nation);
        if (current != null && current.kind() == type) {
            return null;
        }
        if (type == Treaty.Type.ALLIANCE && current == null) {
            return "Une alliance se noue sur un pacte ou un accord deja en cours";
        }
        if (current != null && type != Treaty.Type.ALLIANCE) {
            return (current.kind() == Treaty.Type.ALLIANCE ? "Une " : "Un ") + current.kind().label().toLowerCase()
                    + " vous lie deja";
        }
        if (current == null && treaties(player).size() >= slots(player)) {
            return "Ta nation tient deja " + slots(player) + " traites : un diplomate en permet plus";
        }
        double limit = threshold(player, nation, type);
        if (relation.hostility() > limit) {
            return traits.name() + " est trop hostile (" + Math.round(relation.hostility()) + ", il faut "
                    + Math.round(limit) + " au plus)";
        }
        return null;
    }

    /**
     * Propose le traite : la nation signe si elle n'est pas trop hostile, et le cadeau d'usage
     * sort de la banque nationale. Le meme traite deja en cours se renouvelle depuis son echeance.
     */
    public static String propose(ServerPlayer player, int nation, Treaty.Type type) {
        String refusal = refusal(player, nation, type);
        if (refusal != null) {
            return refusal;
        }
        NationTraits traits = NationTraits.of(nation);
        long price = price(player, nation, type);
        if (!FactionApi.payFromBank(player, price, type.label() + " avec " + traits.name())) {
            return "Il faut " + MoneyFormat.exact(price) + " $ a la banque nationale pour le cadeau d'usage";
        }
        Organisation org = Organisations.of(player);
        long now = System.currentTimeMillis();
        Treaty current = treaty(player, nation);
        boolean renewal = current != null && current.kind() == type;
        org.treaties.removeIf(treaty -> treaty.nation() == nation);
        long start = renewal ? current.until() : now;
        org.treaties.add(new Treaty(nation, type.name(), renewal ? current.since() : now, start + type.duration()));
        WarStrategy.soothe(player, nation, renewal ? 2.0d : 5.0d, (renewal ? "renouvelle " : "signe ")
                + type.label().toLowerCase());
        org.log(now, (renewal ? "Renouvele : " : "Signe : ") + type.label() + " avec " + traits.name());
        WarStrategy.speak(player, nation, renewal ? "Nous prolongeons notre " + type.label().toLowerCase() + "."
                : switch (type) {
                    case PACTE -> "Paix entre nous. Nous ne leverons pas l'epee contre toi.";
                    case ACCORD -> "Nos marchands commerceront avec les tiens.";
                    case ALLIANCE -> "Desormais, ce qui te menace nous menace.";
                }, ChatFormatting.GREEN);
        return null;
    }

    /** Rompt le traite : un accord tombe sans suite ; un pacte ou une alliance rompus sont une trahison. */
    public static String breakTreaty(ServerPlayer player, int nation) {
        Treaty current = treaty(player, nation);
        if (current == null) {
            return "Aucun traite ne vous lie";
        }
        Organisation org = Organisations.of(player);
        long now = System.currentTimeMillis();
        org.treaties.removeIf(treaty -> treaty.nation() == nation);
        NationTraits traits = NationTraits.of(nation);
        org.log(now, "Rompu : " + current.kind().label() + " avec " + traits.name());
        if (current.kind() != Treaty.Type.ACCORD) {
            boolean alliance = current.kind() == Treaty.Type.ALLIANCE;
            WarStrategy.grieve(player, nation, "trahison", alliance ? 12.0d : 8.0d);
            WarStrategy.provoke(player, nation, alliance ? 20.0d : 12.0d, "trahison");
            WarStrategy.speak(player, nation, "Tu romps ta parole. Nous nous en souviendrons.", ChatFormatting.RED);
        } else {
            WarStrategy.speak(player, nation, "Soit. Nos marchands rentrent chez eux.", ChatFormatting.GRAY);
        }
        return null;
    }

    // -- Les rivalites --------------------------------------------------------------------------

    public static boolean rival(ServerPlayer player, int nation) {
        return FactionApi.has(player) && Organisations.of(player).rivals.contains(nation);
    }

    /**
     * Declare la nation rivale, ou y renonce. La rivalite rompt le traite en cours, la fache, et
     * rapporte un quart de points de conquete en plus sur ses pays.
     */
    public static String toggleRival(ServerPlayer player, int nation) {
        if (!FactionApi.has(player)) {
            return "Fonde d'abord ta nation";
        }
        if (nation < 0 || nation >= NationTraits.count()) {
            return "Nation inconnue";
        }
        Organisation org = Organisations.of(player);
        long now = System.currentTimeMillis();
        NationTraits traits = NationTraits.of(nation);
        if (org.rivals.contains(nation)) {
            org.rivals.remove(Integer.valueOf(nation));
            org.log(now, "Plus de rivalite avec " + traits.name());
            return null;
        }
        if (treaty(player, nation) != null) {
            breakTreaty(player, nation);
        }
        org.rivals.add(nation);
        WarStrategy.grieve(player, nation, "rivalite", 6.0d);
        WarStrategy.provoke(player, nation, 10.0d, "rivalite declaree");
        org.log(now, "Rivalite declaree a " + traits.name());
        WarStrategy.speak(player, nation, "Tu nous defies ? Nous relevons le defi.", ChatFormatting.RED);
        return null;
    }

    /** Les points de conquete pris a une rivale declaree comptent un quart de plus. */
    public static double conquestBonus(ServerPlayer player, int nation) {
        return nation >= 0 && rival(player, nation) ? 1.25d : 1.0d;
    }

    // -- Ce que cela change ---------------------------------------------------------------------

    /**
     * Ce que la diplomatie ajoute a l'humeur qu'une nation tend a avoir envers toi : les traites
     * et le regime la calment (ou l'irritent), la rivalite l'echauffe, le general et le regime
     * la dissuadent. Negatif : elle tend a etre moins hostile.
     */
    public static double moodShift(ServerPlayer player, int nation) {
        if (!FactionApi.has(player) || nation < 0 || nation >= NationTraits.count()) {
            return 0.0d;
        }
        NationTraits traits = NationTraits.of(nation);
        Regime regime = Organisations.regime(player);
        double shift = -regime.trust() * (0.5d + traits.honor());
        shift -= regime.fear() * (0.5d + traits.caution());
        shift -= 10.0d * Math.min(1.0d, Organisations.strength(player, Post.GENERAL)) * (0.5d + traits.caution());
        Treaty treaty = treaty(player, nation);
        if (treaty != null) {
            shift -= treaty.kind().trust();
        }
        if (rival(player, nation)) {
            shift += 12.0d;
        }
        return shift;
    }

    /** Ce que les traites retirent a la taxe du marche international, 4 points au plus. */
    public static double taxCut(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            return 0.0d;
        }
        double cut = 0.0d;
        for (Treaty treaty : treaties(player)) {
            cut += treaty.kind().tax();
        }
        return Math.min(TAX_CUT_MAX, cut);
    }
}
