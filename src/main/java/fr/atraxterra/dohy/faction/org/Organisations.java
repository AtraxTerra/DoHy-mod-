package fr.atraxterra.dohy.faction.org;

import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.Nation;
import fr.atraxterra.dohy.faction.war.Sieges;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Le regime et les cadres de la nation (lot 24a). Seul, la hierarchie se tient avec des PNJ :
 * on les recrute parmi les candidats du jour, on les paie chaque heure depuis la banque
 * nationale (meme hors ligne, une semaine au plus), on les promeut, on leur confie un poste.
 * Impayes, ils perdent patience ; a zero de loyaute, ils s'en vont.
 */
public final class Organisations {

    public static final long DAY_MS = 86_400_000L;
    static final long HOUR_MS = 3_600_000L;
    static final int MAX_HOURS = 168;
    /** Un regime ne change qu'une fois par semaine (le premier choix est libre). */
    static final long REGIME_MS = 7L * DAY_MS;
    static final int CANDIDATES = 3;
    /** Sans promotion depuis deux semaines, l'ambitieux s'impatiente. */
    static final long AMBITION_MS = 14L * DAY_MS;

    private static final String[] FIRST = {"Ines", "Malik", "Sofia", "Hugo", "Leila", "Marco", "Nadia", "Yanis",
            "Clara", "Viktor", "Amara", "Theo", "Elena", "Karim", "Lina", "Oscar", "Maya", "Diego", "Sasha", "Jules",
            "Rosa", "Anton", "Zoe", "Bastien", "Irina", "Moussa", "Alba", "Kenji", "Lucia", "Tarek"};
    private static final String[] LAST = {"Morel", "Duval", "Ferrand", "Okafor", "Varga", "Lindqvist", "Benali",
            "Castillo", "Moreau", "Novak", "Sato", "Rossi", "Mbaye", "Keller", "Petrov", "Laurent", "Haddad",
            "Ortega", "Fontaine", "Kowalski", "Diallo", "Brunet", "Marchetti", "Andersen", "Leclerc", "Navarro",
            "Garnier", "Yilmaz", "Rousseau", "Abadi"};

    private Organisations() {
    }

    public static Organisation of(ServerPlayer player) {
        return player.getData(Factions.ORGANISATION.get());
    }

    public static Regime regime(ServerPlayer player) {
        return FactionApi.has(player) ? of(player).regime() : Regime.INDEPENDANT;
    }

    // -- Le regime ------------------------------------------------------------------------------

    /** Quand le regime pourra changer (ms) ; 0 : tout de suite. */
    public static long regimeReady(ServerPlayer player) {
        Organisation org = of(player);
        return org.regimeSince == 0L ? 0L : org.regimeSince + REGIME_MS;
    }

    public static String setRegime(ServerPlayer player, Regime regime) {
        if (!FactionApi.has(player)) {
            return "Fonde d'abord ta nation";
        }
        Organisation org = of(player);
        long now = System.currentTimeMillis();
        if (org.regime == regime && org.regimeSince > 0L) {
            return "C'est deja ton regime";
        }
        long ready = regimeReady(player);
        if (now < ready) {
            return "Un regime ne change qu'une fois par semaine : encore " + duration(ready - now);
        }
        org.regime = regime;
        org.regimeSince = now;
        org.log(now, "Regime : " + regime.label());
        return null;
    }

    // -- Les cadres -----------------------------------------------------------------------------

    /** Combien de cadres la nation peut employer : 3, et un de plus par palier du Gouvernement. */
    public static int cap(ServerPlayer player) {
        return 3 + Nation.government(FactionApi.data(player));
    }

    public static List<Cadre> cadres(ServerPlayer player) {
        return of(player).cadres();
    }

    /** Les candidats du jour : trois, renouveles toutes les 24 heures. */
    public static List<Cadre> candidates(ServerPlayer player) {
        Organisation org = of(player);
        long now = System.currentTimeMillis();
        if (org.candidates.isEmpty() || now - org.candidatesAt >= DAY_MS) {
            refresh(player, org, now);
        }
        return org.candidates();
    }

    static void refresh(ServerPlayer player, Organisation org, long now) {
        org.candidates.clear();
        RandomSource random = player.getRandom();
        for (int index = 0; index < CANDIDATES; index++) {
            org.candidates.add(candidate(org, random, now, index));
        }
        org.candidatesAt = now;
    }

    private static Cadre candidate(Organisation org, RandomSource random, long now, int index) {
        double roll = random.nextDouble();
        int grade = roll < 0.6d ? 0 : roll < 0.9d ? 1 : 2;
        int skill = 1 + Math.min(4, (int) Math.floor(Math.pow(random.nextDouble(), 1.4d) * 5.0d));
        Cadre.Trait[] traits = Cadre.Trait.values();
        Cadre.Trait trait = traits[random.nextInt(traits.length)];
        String name = FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
        double loyalty = 55.0d + random.nextInt(21);
        return new Cadre("k" + index + "_" + (now / 1000L % 100_000L), name, grade, skill, trait.name(), "", 0L, 0L,
                loyalty);
    }

    /** Le salaire d'un cadre, en $ par jour : son grade, son talent, moins ce que le tresorier economise. */
    public static long salary(ServerPlayer player, Cadre cadre) {
        double base = cadre.rank().salary() * (0.8d + 0.1d * cadre.skill());
        double cut = 0.20d * strength(player, Post.TRESORIER);
        return Math.max(10L, Math.round(base * (1.0d - cut) / 10.0d) * 10L);
    }

    /** Les salaires de tous les cadres, par jour. */
    public static long payroll(ServerPlayer player) {
        long total = 0L;
        for (Cadre cadre : of(player).cadres) {
            total += salary(player, cadre);
        }
        return total;
    }

    /** La prime d'embauche : deux jours de salaire. */
    public static long signing(ServerPlayer player, Cadre cadre) {
        return round(2L * salary(player, cadre));
    }

    public static String hire(ServerPlayer player, String id) {
        if (!FactionApi.has(player)) {
            return "Fonde d'abord ta nation";
        }
        Organisation org = of(player);
        Cadre found = null;
        for (Cadre candidate : candidates(player)) {
            if (candidate.id().equals(id)) {
                found = candidate;
            }
        }
        if (found == null) {
            return "Ce candidat n'est plus disponible";
        }
        if (org.cadres.size() >= cap(player)) {
            return "Ta nation emploie deja " + org.cadres.size() + " cadres : le Gouvernement en permettra plus";
        }
        long price = signing(player, found);
        if (!FactionApi.payFromBank(player, price, "Embauche de " + found.name())) {
            return "Il faut " + MoneyFormat.exact(price) + " $ a la banque nationale pour la prime d'embauche";
        }
        long now = System.currentTimeMillis();
        Cadre hired = new Cadre(org.nextId(), found.name(), found.grade(), found.skill(), found.trait(), "", now, now,
                found.loyalty());
        org.cadres.add(hired);
        org.candidates.removeIf(candidate -> candidate.id().equals(id));
        if (org.paidUntil == 0L) {
            org.paidUntil = now;
        }
        org.log(now, hired.label() + " rejoint la nation");
        return null;
    }

    public static String fire(ServerPlayer player, String id) {
        Organisation org = of(player);
        Cadre cadre = org.cadre(id);
        if (cadre == null) {
            return "Ce cadre n'est plus dans ta nation";
        }
        org.cadres.remove(cadre);
        org.log(System.currentTimeMillis(), cadre.label() + " est renvoye");
        return null;
    }

    /** Monter d'un grade coute une semaine du nouveau salaire. */
    public static long promotionPrice(ServerPlayer player, Cadre cadre) {
        Grade next = cadre.rank().next();
        if (next == null) {
            return 0L;
        }
        Cadre up = new Cadre(cadre.id(), cadre.name(), next.ordinal(), cadre.skill(), cadre.trait(), cadre.post(),
                cadre.hired(), cadre.promoted(), cadre.loyalty());
        return round(7L * salary(player, up));
    }

    public static String promote(ServerPlayer player, String id) {
        Organisation org = of(player);
        Cadre cadre = org.cadre(id);
        if (cadre == null) {
            return "Ce cadre n'est plus dans ta nation";
        }
        Grade next = cadre.rank().next();
        if (next == null) {
            return cadre.name() + " est deja au sommet";
        }
        long now = System.currentTimeMillis();
        if (now < cadre.promotable()) {
            return cadre.name() + " doit encore servir " + duration(cadre.promotable() - now) + " comme "
                    + cadre.rank().label();
        }
        long price = promotionPrice(player, cadre);
        if (!FactionApi.payFromBank(player, price, "Promotion de " + cadre.name())) {
            return "Il faut " + MoneyFormat.exact(price) + " $ a la banque nationale";
        }
        Cadre up = cadre.promotedAt(now);
        org.replace(up);
        org.log(now, cadre.name() + " devient " + up.rank().label());
        return null;
    }

    public static Optional<Cadre> holder(ServerPlayer player, Post post) {
        for (Cadre cadre : of(player).cadres) {
            if (post.name().equals(cadre.post())) {
                return Optional.of(cadre);
            }
        }
        return Optional.empty();
    }

    /** Confie le poste au meilleur cadre libre qui a le grade : grade, puis talent, puis loyaute. */
    public static String assign(ServerPlayer player, Post post) {
        Organisation org = of(player);
        if (holder(player, post).isPresent()) {
            return "Le poste de " + post.label() + " est deja tenu";
        }
        Cadre best = org.cadres.stream()
                .filter(cadre -> cadre.post().isEmpty() && cadre.grade() >= post.grade().ordinal())
                .max(Comparator.comparingInt(Cadre::grade).thenComparingInt(Cadre::skill)
                        .thenComparingDouble(Cadre::loyalty))
                .orElse(null);
        if (best == null) {
            return "Aucun cadre libre n'a le grade de " + post.grade().label() + " pour ce poste";
        }
        org.replace(best.withPost(post.name()));
        org.log(System.currentTimeMillis(), best.name() + " devient " + post.label());
        return null;
    }

    /** Confie le poste a ce cadre ; celui qui le tenait redevient libre. */
    public static String appoint(ServerPlayer player, String id, Post post) {
        Organisation org = of(player);
        Cadre cadre = org.cadre(id);
        if (cadre == null) {
            return "Ce cadre n'est plus dans ta nation";
        }
        if (cadre.grade() < post.grade().ordinal()) {
            return post.label() + " : il faut le grade de " + post.grade().label();
        }
        if (post.name().equals(cadre.post())) {
            return cadre.name() + " tient deja ce poste";
        }
        Optional<Cadre> previous = holder(player, post);
        previous.ifPresent(other -> org.replace(other.withPost("")));
        org.replace(org.cadre(id).withPost(post.name()));
        org.log(System.currentTimeMillis(), cadre.name() + " devient " + post.label()
                + previous.map(other -> " a la place de " + other.name()).orElse(""));
        return null;
    }

    public static String vacate(ServerPlayer player, Post post) {
        Organisation org = of(player);
        Optional<Cadre> holder = holder(player, post);
        if (holder.isEmpty()) {
            return "Personne ne tient ce poste";
        }
        org.replace(holder.get().withPost(""));
        org.log(System.currentTimeMillis(), holder.get().name() + " quitte le poste de " + post.label());
        return null;
    }

    /**
     * Ce que vaut le poste, de 0 (vacant) a un peu plus de 1 : le talent et le grade du cadre,
     * moitie moins s'il n'est plus loyal, un peu plus avec un bras droit.
     */
    public static double strength(ServerPlayer player, Post post) {
        if (!FactionApi.has(player)) {
            return 0.0d;
        }
        Optional<Cadre> holder = holder(player, post);
        if (holder.isEmpty()) {
            return 0.0d;
        }
        Cadre cadre = holder.get();
        double value = (0.5d + 0.1d * cadre.skill()) * (1.0d + 0.1d * (cadre.grade() - post.grade().ordinal()));
        if (cadre.loyalty() < 30.0d) {
            value *= 0.5d;
        }
        if (post != Post.BRAS_DROIT) {
            value *= 1.0d + 0.15d * strength(player, Post.BRAS_DROIT);
        }
        return Math.max(0.0d, Math.min(1.25d, value));
    }

    // -- La paie --------------------------------------------------------------------------------

    /**
     * Regle les salaires depuis la derniere paie, heure par heure pleine, une semaine au plus,
     * et fait bouger la loyaute. {@code login} : il revient, le message dit ce qui s'est passe.
     */
    public static void payroll(ServerPlayer player, boolean login) {
        if (!FactionApi.has(player)) {
            return;
        }
        Organisation org = of(player);
        long now = System.currentTimeMillis();
        if (org.cadres.isEmpty() || org.paidUntil == 0L) {
            org.paidUntil = now;
            return;
        }
        long elapsed = now - org.paidUntil;
        if (elapsed < HOUR_MS) {
            return;
        }
        long whole = elapsed / HOUR_MS;
        long hours = Math.min(whole, MAX_HOURS);
        org.paidUntil = whole > MAX_HOURS ? now - elapsed % HOUR_MS : org.paidUntil + whole * HOUR_MS;
        long wages = 0L;
        for (Cadre cadre : org.cadres) {
            wages += salary(player, cadre) * hours / 24L;
        }
        long due = wages + org.debt;
        long paid = 0L;
        if (due > 0L && !Sieges.besieged(player.getUUID())) {
            paid = Math.min(due, Math.max(0L, FactionApi.data(player).bank()));
            if (paid > 0L && !FactionApi.payFromBank(player, paid, "Salaires des cadres (" + hours + " h)")) {
                paid = 0L;
            }
        }
        org.debt = due - paid;
        boolean settled = org.debt == 0L;
        double days = hours / 24.0d;
        boolean right = holder(player, Post.BRAS_DROIT).isPresent();
        List<Cadre> leaving = new ArrayList<>();
        for (Cadre cadre : List.copyOf(org.cadres)) {
            double delta = settled ? 0.5d * days : -5.0d * days;
            switch (cadre.character()) {
                case FIDELE -> delta *= settled ? 2.0d : 1.0d;
                case MERCENAIRE -> delta *= settled ? 1.0d : 2.0d;
                case PRUDENT -> delta *= 0.5d;
                case AMBITIEUX -> {
                    if (now - Math.max(cadre.hired(), cadre.promoted()) > AMBITION_MS) {
                        delta -= 0.5d * days;
                    }
                }
            }
            if (right && delta < 0.0d && !Post.BRAS_DROIT.name().equals(cadre.post())) {
                delta *= 0.5d;
            }
            Cadre next = cadre.withLoyalty(cadre.loyalty() + delta);
            org.replace(next);
            if (next.loyalty() <= 0.0d) {
                leaving.add(next);
            }
        }
        for (Cadre cadre : leaving) {
            org.cadres.remove(cadre);
            org.log(now, cadre.label() + " quitte la nation, faute de paie");
            FactionApi.tell(player, cadre.label() + " quitte ta nation : ses salaires n'etaient plus payes.",
                    ChatFormatting.RED);
        }
        if (login && hours >= 1L && wages > 0L) {
            FactionApi.tell(player, "Pendant ton absence (" + hours + " h), tes cadres ont touche "
                    + MoneyFormat.exact(paid) + " $" + (settled ? "." : " ; il manque " + MoneyFormat.exact(org.debt)
                    + " $ a la banque nationale, leur loyaute baisse."), settled ? ChatFormatting.GREEN
                    : ChatFormatting.YELLOW);
        } else if (!settled) {
            FactionApi.tell(player, "Salaires impayes : il manque " + MoneyFormat.exact(org.debt)
                    + " $ a la banque nationale. Tes cadres perdent patience.", ChatFormatting.YELLOW);
        }
    }

    /**
     * Pour les operateurs : fait comme si {@code hours} heures avaient passe (la paie, le
     * service des cadres, les candidats, le regime, les traites), puis regle la paie.
     */
    public static void advance(ServerPlayer player, int hours) {
        Organisation org = of(player);
        long shift = hours * HOUR_MS;
        if (org.paidUntil > 0L) {
            org.paidUntil -= shift;
        }
        if (org.candidatesAt > 0L) {
            org.candidatesAt -= shift;
        }
        if (org.regimeSince > 0L) {
            org.regimeSince -= shift;
        }
        for (Cadre cadre : List.copyOf(org.cadres)) {
            org.replace(new Cadre(cadre.id(), cadre.name(), cadre.grade(), cadre.skill(), cadre.trait(), cadre.post(),
                    cadre.hired() - shift, cadre.promoted() - shift, cadre.loyalty()));
        }
        for (int index = 0; index < org.treaties.size(); index++) {
            Treaty treaty = org.treaties.get(index);
            org.treaties.set(index, new Treaty(treaty.nation(), treaty.type(), treaty.since() - shift,
                    treaty.until() - shift));
        }
        payroll(player, true);
        FactionApi.tell(player, hours + " h passent pour ton organisation.", ChatFormatting.GRAY);
    }

    /** La nation est dissoute : ses cadres partent, ses traites tombent. */
    public static void disbanded(ServerPlayer player) {
        of(player).clear();
    }

    // -- Petits outils --------------------------------------------------------------------------

    static long round(long amount) {
        return Math.max(100L, (amount + 50L) / 100L * 100L);
    }

    /** « 3 j 4 h », « 5 h », « 12 min » */
    public static String duration(long ms) {
        long minutes = Math.max(1L, ms / 60_000L);
        long days = minutes / 1440L;
        long hours = minutes % 1440L / 60L;
        if (days > 0L) {
            return days + " j" + (hours > 0L ? " " + hours + " h" : "");
        }
        if (hours > 0L) {
            return hours + " h";
        }
        return minutes + " min";
    }
}
