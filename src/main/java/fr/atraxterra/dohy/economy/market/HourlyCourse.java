package fr.atraxterra.dohy.economy.market;

import fr.atraxterra.dohy.economy.EconomyConfig;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import net.minecraft.resources.Identifier;

/**
 * Le cours de l'heure.
 *
 * <p>Les objets de certaines categories du catalogue — l'agriculture — n'ont
 * pas un prix fixe : chaque heure reelle, chacun tire un facteur entre un
 * minimum et un maximum, et ce facteur multiplie son prix de vente <b>et</b>
 * son prix d'achat. Le ble peut valoir cher pendant que le seigle est bas ;
 * une heure plus tard c'est l'inverse.
 *
 * <h2>Pourquoi un tirage et pas un etat</h2>
 *
 * <p>Le facteur est une fonction pure de l'objet et de l'heure : rien a
 * sauvegarder, rien a rattraper apres une absence, et le cours est le meme vu
 * du shop, de {@code /worth}, du coffre de vente et de la vente automatique.
 * Le sel du datapack distingue deux mondes.
 *
 * <h2>Pourquoi le meme facteur a l'achat</h2>
 *
 * <p>Un cours qui ne toucherait que la vente laisserait le prix d'achat fixe
 * comme repere, et l'ecart entre les deux — la marge d'achat de 4 — est ce qui
 * interdit d'acheter pour revendre. Applique des deux cotes, l'ecart reste le
 * meme a chaque heure ; entre deux heures, le pire cas est d'acheter au
 * minimum et de revendre au maximum, soit {@code max / (min x 4)}, loin sous 1
 * avec les valeurs livrees. {@code tools/check_agri_prices.py} verifie la meme
 * chose a travers les recettes.
 *
 * <p>Le tirage est la moyenne de deux tirages uniformes : les cours moyens sont
 * frequents, les extremes rares, et une bonne heure se remarque.
 */
public final class HourlyCourse {

    private HourlyCourse() {
    }

    /** Vrai si cet objet suit un cours. */
    public static boolean follows(PriceEntry entry) {
        for (String category : EconomyConfig.course().categories()) {
            if (category.equalsIgnoreCase(entry.category())) {
                return true;
            }
        }
        return false;
    }

    /** Le facteur de l'heure courante pour cet objet ; 1 s'il ne suit pas de cours. */
    public static double factor(Identifier id, PriceEntry entry) {
        return follows(entry) ? factorAt(id, slot()) : 1.0d;
    }

    /** Le cours en pour cent du prix de base (118 pour +18 %) ; 0 si l'objet n'en suit pas. */
    public static int percent(Identifier id, PriceEntry entry) {
        return follows(entry) ? (int) Math.round(factorAt(id, slot()) * 100.0d) : 0;
    }

    /** Le facteur a une periode donnee : c'est la variante testable. */
    public static double factorAt(Identifier id, long slot) {
        EconomyConfig.Course course = EconomyConfig.course();
        double low = Math.min(course.min(), course.max());
        double high = Math.max(course.min(), course.max());
        long seed = mix(mix(course.salt() ^ 0x9E3779B97F4A7C15L) ^ mix(id.toString().hashCode()) ^ mix(slot));
        double first = unit(seed);
        double second = unit(mix(seed + 0x632BE59BD9B4E019L));
        return low + (high - low) * (first + second) / 2.0d;
    }

    /** Le numero de la periode courante. */
    public static long slot() {
        return slotAt(System.currentTimeMillis());
    }

    static long slotAt(long epochMillis) {
        return Math.floorDiv(epochMillis, periodMillis());
    }

    /** Minutes restantes avant le prochain cours, au moins 1. */
    public static int minutesLeft() {
        long period = periodMillis();
        long left = period - Math.floorMod(System.currentTimeMillis(), period);
        return (int) Math.max(1L, (left + 59_999L) / 60_000L);
    }

    private static long periodMillis() {
        return Math.max(1, EconomyConfig.course().periodMinutes()) * 60_000L;
    }

    /** SplitMix64 : un melange dont deux entrees voisines sortent sans rapport. */
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53d;
    }
}
