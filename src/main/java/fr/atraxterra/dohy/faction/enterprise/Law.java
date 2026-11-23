package fr.atraxterra.dohy.faction.enterprise;

import fr.atraxterra.dohy.faction.war.NationTraits;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Les lois des pays (lot 24d) : chaque activite illegale y est legale, toleree ou interdite.
 * Dans les pays que tient ta nation, c'est toi qui fais la loi, la meme pour tous tes pays ;
 * ailleurs vaut celle de la nation qui tient le pays, selon son caractere ; hors de toute nation,
 * la loi commune.
 *
 * <p>Ce que la loi change pour une affaire : interdite, rien (le risque de toujours) ; toleree,
 * la police ferme a moitie les yeux (risque x0,6) contre des pots-de-vin (frais +15 %) ; legale,
 * de simples controles (risque x0,3), et ce qu'elle gagne devient de l'argent propre, mais une
 * concurrence au grand jour et les taxes en retirent un cinquieme.
 */
public final class Law {

    /** Ce que gagne une affaire legale, propre, par rapport a ce qu'elle gagnait sale. */
    public static final double LEGAL_REVENUE = 0.80d;
    /** Une loi change au plus une fois par jour. */
    public static final long COOLDOWN = Enterprises.DAY_MS;

    private Law() {
    }

    /** Le statut d'une activite dans un pays, du plus strict au plus libre. */
    public enum Status {
        INTERDIT("Interdite", 0xFFEF3826, 1.00d, 1.00d),
        TOLERE("Toleree", 0xFFFAC400, 0.60d, 1.15d),
        LEGAL("Legale", 0xFF22E558, 0.30d, 1.00d);

        private final String label;
        private final int color;
        private final double risk;
        private final double cost;

        Status(String label, int color, double risk, double cost) {
            this.label = label;
            this.color = color;
            this.risk = risk;
            this.cost = cost;
        }

        public String label() {
            return label;
        }

        public int color() {
            return color;
        }

        /** Ce que la loi fait au risque d'une affaire. */
        public double risk() {
            return risk;
        }

        /** Ce qu'elle fait a ses frais : les pots-de-vin d'une activite toleree. */
        public double cost() {
            return cost;
        }

        public static Status of(String name) {
            for (Status status : values()) {
                if (status.name().equals(name)) {
                    return status;
                }
            }
            return null;
        }
    }

    /**
     * Les sujets de loi, chacun pour une famille d'affaires illegales.
     *
     * @param legalAt   a partir de ce laxisme, une nation la rend legale (jamais pour les sujets
     *                  qui ne peuvent pas l'etre)
     * @param toleratedAt a partir de ce laxisme, elle la tolere
     */
    public enum Topic {
        CANNABIS("Cannabis", "Plantations et laboratoires de cannabis", true, Status.INTERDIT, 0.66d, 0.42d,
                () -> Items.FERN),
        DROGUES("Drogues dures", "Coca, pavot, synthese, reseaux de rue, entrepots", true, Status.INTERDIT, 0.66d,
                0.42d, () -> Items.SUGAR),
        ARMES("Armes", "Ateliers et trafics d'armes", true, Status.INTERDIT, 0.60d, 0.50d, () -> Items.CROSSBOW),
        JEU("Jeux d'argent", "Casinos", true, Status.TOLERE, 0.66d, 0.50d, () -> Items.GOLD_NUGGET),
        CONTREBANDE("Contrebande", "Routes de contrebande", false, Status.TOLERE, 2.0d, 0.50d,
                () -> Items.COMPASS),
        EXTORSION("Racket et recel", "Racket, recel", false, Status.INTERDIT, 2.0d, 0.45d, () -> Items.IRON_BARS),
        BLANCHIMENT("Blanchiment", "Societes ecrans", false, Status.TOLERE, 2.0d, 0.50d, () -> Items.WATER_BUCKET);

        private final String label;
        private final String scope;
        private final boolean legalizable;
        private final Status common;
        private final double legalAt;
        private final double toleratedAt;
        private final Supplier<Item> icon;

        Topic(String label, String scope, boolean legalizable, Status common, double legalAt, double toleratedAt,
              Supplier<Item> icon) {
            this.label = label;
            this.scope = scope;
            this.legalizable = legalizable;
            this.common = common;
            this.legalAt = legalAt;
            this.toleratedAt = toleratedAt;
            this.icon = icon;
        }

        public String label() {
            return label;
        }

        /** Les affaires qu'elle regit. */
        public String scope() {
            return scope;
        }

        /** Peut-elle etre legale ? Le racket, la contrebande et le blanchiment, jamais. */
        public boolean legalizable() {
            return legalizable;
        }

        /** La loi commune, hors de toute nation : ce que ta nation applique tant qu'elle n'a rien vote. */
        public Status common() {
            return common;
        }

        public ItemStack icon() {
            return new ItemStack(icon.get());
        }

        /** Le statut suivant vers plus de liberte, {@code null} au bout. */
        public Status looser(Status status) {
            return switch (status) {
                case INTERDIT -> Status.TOLERE;
                case TOLERE -> legalizable ? Status.LEGAL : null;
                case LEGAL -> null;
            };
        }

        /** Le statut suivant vers plus de rigueur, {@code null} au bout. */
        public Status stricter(Status status) {
            return switch (status) {
                case LEGAL -> Status.TOLERE;
                case TOLERE -> Status.INTERDIT;
                case INTERDIT -> null;
            };
        }

        public static Topic of(String name) {
            for (Topic topic : values()) {
                if (topic.name().equals(name)) {
                    return topic;
                }
            }
            return null;
        }
    }

    /** Le sujet de loi d'une affaire illegale, {@code null} pour une affaire legale. */
    public static Topic topic(Sector sector, Line line) {
        if (sector.legal()) {
            return null;
        }
        return switch (sector.role()) {
            case GROW, LAB -> line == Line.CANNABIS ? Topic.CANNABIS : Topic.DROGUES;
            case SELL, STORE -> Topic.DROGUES;
            case ARMS, ARMS_SELL -> Topic.ARMES;
            case CASINO -> Topic.JEU;
            case SMUGGLE -> Topic.CONTREBANDE;
            case RACKET, FENCE -> Topic.EXTORSION;
            case LAUNDER -> Topic.BLANCHIMENT;
            case REVENUE -> null;
        };
    }

    public static Topic topic(Enterprise enterprise) {
        return topic(enterprise.kind(), enterprise.drug());
    }

    // -- Les lois des douze nations ---------------------------------------------------------------

    /**
     * Le laxisme d'une nation sur un sujet, de 0 a 1 environ : l'honneur la rend stricte,
     * l'avidite et la ruse coulantes, l'agressivite lui fait aimer les armes.
     */
    static double laxity(NationTraits traits, Topic topic) {
        double loose = 1.0d - traits.honor();
        return switch (topic) {
            case CANNABIS -> 0.20d + 0.45d * loose + 0.25d * traits.greed();
            case DROGUES -> 0.05d + 0.50d * loose + 0.15d * traits.greed();
            case ARMES -> 0.60d * traits.aggression() + 0.20d * traits.greed() - 0.15d * traits.caution()
                    + 0.10d * loose;
            case JEU -> 0.25d + 0.50d * traits.greed() + 0.10d * loose;
            case CONTREBANDE -> 0.30d * traits.greed() + 0.35d * traits.cunning() + 0.15d * loose;
            case EXTORSION -> 0.55d * loose + 0.10d * traits.aggression();
            case BLANCHIMENT -> 0.45d * traits.cunning() + 0.20d * traits.greed() + 0.15d * loose;
        };
    }

    /** La loi d'une nation sur un sujet. */
    public static Status of(int nation, Topic topic) {
        if (nation < 0 || nation >= NationTraits.count()) {
            return topic.common();
        }
        double lax = laxity(NationTraits.of(nation), topic);
        if (topic.legalizable() && lax >= topic.legalAt) {
            return Status.LEGAL;
        }
        return lax >= topic.toleratedAt ? Status.TOLERE : Status.INTERDIT;
    }

    /** Toutes les lois d'une nation. */
    public static Map<Topic, Status> of(int nation) {
        Map<Topic, Status> laws = new EnumMap<>(Topic.class);
        for (Topic topic : Topic.values()) {
            laws.put(topic, of(nation, topic));
        }
        return laws;
    }

    /** Le zele de sa police : de x0,88 (sans honneur) a x1,25 (le plus d'honneur). */
    public static double police(int nation) {
        if (nation < 0 || nation >= NationTraits.count()) {
            return 1.0d;
        }
        return 0.8d + 0.5d * NationTraits.of(nation).honor();
    }

    /** « Cannabis legal · drogues interdites · ... » en quelques mots, pour une carte. */
    public static String summary(Map<Topic, Status> laws) {
        StringBuilder text = new StringBuilder();
        for (Topic topic : new Topic[] {Topic.CANNABIS, Topic.DROGUES, Topic.ARMES, Topic.JEU}) {
            if (!text.isEmpty()) {
                text.append(" · ");
            }
            text.append(shortLabel(topic)).append(' ').append(mark(laws.get(topic)));
        }
        return text.toString();
    }

    private static String shortLabel(Topic topic) {
        return switch (topic) {
            case CANNABIS -> "cannabis";
            case DROGUES -> "drogues";
            case ARMES -> "armes";
            case JEU -> "jeu";
            case CONTREBANDE -> "contrebande";
            case EXTORSION -> "racket";
            case BLANCHIMENT -> "blanchiment";
        };
    }

    private static String mark(Status status) {
        return status == null ? "?" : switch (status) {
            case INTERDIT -> "non";
            case TOLERE -> "tolere";
            case LEGAL -> "oui";
        };
    }
}
