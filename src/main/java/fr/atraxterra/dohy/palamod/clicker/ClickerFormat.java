package fr.atraxterra.dohy.palamod.clicker;

import java.util.Locale;

/**
 * L'ecriture des ClicCoins : un clicker va de l'unite au million de milliards.
 *
 * <p>Sous le million, le nombre entier, par groupes de trois chiffres. Au-dela,
 * trois chiffres significatifs et l'unite francaise : M (million), Md
 * (milliard), B (billion, mille milliards), Bd (billiard), T (trillion).
 */
public final class ClickerFormat {

    private static final String[] UNITS = {"M", "Md", "B", "Bd", "T", "Td"};

    private ClickerFormat() {
    }

    public static String compact(double value) {
        if (value < 0.0d) {
            return "-" + compact(-value);
        }
        if (value < 1_000_000.0d) {
            return grouped((long) Math.floor(value));
        }
        int unit = -1;
        double scaled = value;
        while (scaled >= 1_000.0d && unit < UNITS.length - 1) {
            scaled /= 1_000.0d;
            unit++;
            if (unit == 0) {
                // Le premier pas saute les milliers : 1 000 000 s'ecrit 1 M.
                scaled /= 1_000.0d;
            }
        }
        String digits = scaled >= 100.0d ? String.format(Locale.ROOT, "%.0f", Math.floor(scaled))
                : scaled >= 10.0d ? String.format(Locale.ROOT, "%.1f", Math.floor(scaled * 10.0d) / 10.0d)
                : String.format(Locale.ROOT, "%.2f", Math.floor(scaled * 100.0d) / 100.0d);
        return digits.replace('.', ',') + " " + UNITS[unit];
    }

    /** Une production par seconde : deux decimales tant qu'elle est petite. */
    public static String rate(double value) {
        if (value < 100.0d) {
            return String.format(Locale.ROOT, "%.2f", value).replace('.', ',');
        }
        return compact(value);
    }

    private static String grouped(long value) {
        String digits = Long.toString(value);
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < digits.length(); index++) {
            if (index > 0 && (digits.length() - index) % 3 == 0) {
                out.append(' ');
            }
            out.append(digits.charAt(index));
        }
        return out.toString();
    }
}
