package fr.atraxterra.dohy.util;

import java.util.Locale;

/**
 * Mise en forme des montants.
 *
 * <p>Le solde est un {@code long} et non un {@code int} : un {@code int}
 * plafonne a 2 147 483 647, ce qui est incompatible avec l'objectif d'atteindre
 * le billion. C'est aussi pour ca que le HUD est dessine par le mod au lieu
 * d'utiliser un objectif de scoreboard vanilla, dont le score est un
 * {@code int}.
 */
public final class MoneyFormat {

    /** Au-dela de Qi on ne depasse pas Long.MAX_VALUE, inutile d'aller plus loin. */
    private static final String[] UNITS = {"k", "M", "B", "T", "Qa", "Qi"};

    /**
     * Seuil de passage a l'unite suivante. On compare a 999.995 et non a 1000
     * pour eviter qu'un arrondi a deux decimales affiche "1000.00k" au lieu de
     * "1.00M".
     */
    private static final double CARRY = 999.995d;

    private MoneyFormat() {
    }

    /** Forme courte : {@code 1.24M}. C'est celle du HUD. */
    public static String shortForm(long amount) {
        if (amount == Long.MIN_VALUE) {
            return "-9.22Qi";
        }
        String sign = amount < 0 ? "-" : "";
        long abs = Math.abs(amount);

        if (abs < 1000L) {
            return sign + abs;
        }

        double value = abs;
        int unit = -1;
        while (value >= CARRY && unit < UNITS.length - 1) {
            value /= 1000d;
            unit++;
        }
        return sign + String.format(Locale.ROOT, "%.2f%s", value, UNITS[unit]);
    }

    /**
     * Forme compacte, trois chiffres au plus et sans zeros inutiles :
     * {@code 28.9k}, {@code 225k}, {@code 1.25M}. Pour les petites etiquettes
     * (cases de la roulette des caisses).
     */
    public static String compact(long amount) {
        if (amount == Long.MIN_VALUE) {
            return shortForm(amount);
        }
        String sign = amount < 0 ? "-" : "";
        long abs = Math.abs(amount);
        if (abs < 1000L) {
            return sign + abs;
        }
        double value = abs;
        int unit = -1;
        while (value >= 999.5d && unit < UNITS.length - 1) {
            value /= 1000d;
            unit++;
        }
        String digits = String.format(Locale.ROOT, value >= 99.95d ? "%.0f" : value >= 9.995d ? "%.1f" : "%.2f",
                value);
        if (digits.indexOf('.') >= 0) {
            digits = digits.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return sign + digits + UNITS[unit];
    }

    /**
     * Forme exacte, groupee par milliers avec une espace fine insecable :
     * {@code 1 240 311}. Utilisee par {@code /money} et les infobulles, pour que
     * la valeur reelle reste consultable malgre l'arrondi du HUD.
     */
    public static String exact(long amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', ' ');
    }

    /** Montant signe, pour les animations de gain : {@code +1.2k}, {@code -340}. */
    public static String delta(long amount) {
        return (amount > 0 ? "+" : "") + shortForm(amount);
    }
}
