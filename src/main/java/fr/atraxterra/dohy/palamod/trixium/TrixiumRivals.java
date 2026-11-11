package fr.atraxterra.dohy.palamod.trixium;

import fr.atraxterra.dohy.faction.event.EventRun;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les joueurs fictifs du concours Trixium.
 *
 * <p>Le classement de Paladium recompensait les 60 premiers. En solo, 59
 * joueurs fictifs deposent au fil de la saison ; avec le joueur, cela fait les
 * 60 places du bareme. Tout se deduit de la saison : leurs noms, leurs factions
 * et l'heure de chacun de leurs depots. Rien n'est sauvegarde, et deux appels
 * au meme instant rendent le meme classement.
 *
 * <p><b>Calibrage</b> (de moi, faute de chiffres de Paladium pour un joueur
 * seul) : le total de fin de saison du rang r vaut a peu pres
 * {@code 2 500 x r^-0,62}, a 15 % pres. La 1re place demande donc environ
 * 2 500 trixium dans le mois, la 10e environ 600, la 30e environ 300. Au
 * rythme du minage (50 trixium par heure de minage pur, chapitre economique),
 * le podium se gagne par un mois tourne vers le trixium, pas en passant.
 */
public final class TrixiumRivals {

    public static final int COUNT = 59;
    private static final double TOP = 2_500.0d;
    private static final double SLOPE = 0.62d;

    /** Des pseudonymes inventes : aucun n'est celui d'un joueur de Paladium. */
    private static final String[] NAMES = {
            "Brisecaillou", "Kaelor", "Lunara", "Taupe_Royale", "Grimfeld", "PiocheDoree", "Mirabel_",
            "Orvain", "Silex42", "Nyxelle", "Hachefer", "Corentin_Mine", "Zephirin", "Bastok", "Eldwen",
            "Galoche", "Ferrailleur", "Ysolde", "Torvik", "Pyrite_", "Mandragore", "Celestin_", "Obsidio",
            "Rouille", "Cassandre_X", "Gribouille", "Volkhan", "Perle_Noire", "Tarragone", "Esteban_M",
            "Lichen", "Quartzou", "Aldaric", "Brindille", "Krovan", "Melusine_", "Charbonnier", "Tybalt_",
            "Faucheuse", "Gravillon", "Isengrin", "Jasper_", "Kobold", "Lysandre", "Morvan", "Nacre",
            "Oriflamme", "Pavlina", "Quillon", "Rhodium", "Sombrelame", "Tisonnier", "Ursule_", "Vespera",
            "Wolfram", "Xandrine", "Yvain_", "Zircon", "Ardoise", "Belladone", "Caillasse", "Dorian_T",
            "Ebene", "Flamberge", "Goupil", "Heliodore", "Ivoire", "Jade_", "Kestrel"};

    private static final Map<Integer, Season> CACHE = new ConcurrentHashMap<>();

    private TrixiumRivals() {
    }

    /** Un joueur fictif : son nom, sa faction (-1 sans faction), et ses depots dates. */
    public record Rival(String name, int faction, long[] times, long[] totals) {

        /** Ce qu'il a depose a cet instant. */
        public long at(long millis) {
            int low = 0;
            int high = times.length - 1;
            int found = -1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (times[mid] <= millis) {
                    found = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return found < 0 ? 0L : totals[found];
        }

        public long finalTotal() {
            return totals.length == 0 ? 0L : totals[totals.length - 1];
        }
    }

    /** Les rivaux d'une saison et les factions fictives qu'ils forment. */
    public record Season(List<Rival> rivals, List<EventRun.Rival> factions) {
    }

    public static Season of(int season) {
        return CACHE.computeIfAbsent(season, TrixiumRivals::build);
    }

    private static Season build(int season) {
        Random random = new Random(0x7121_0000L + season * 7919L);
        List<String> names = new ArrayList<>(List.of(NAMES));
        Collections.shuffle(names, random);

        List<EventRun.Rival> pool = new ArrayList<>(EventRun.rivalPool());
        Collections.shuffle(pool, random);
        List<EventRun.Rival> factions = List.copyOf(pool.subList(0, Math.min(8, pool.size())));

        long start = TrixiumSeason.start(season).toInstant().toEpochMilli();
        long end = TrixiumSeason.end(season).toInstant().toEpochMilli();
        int days = (int) Math.max(1L, Duration.ofMillis(end - start).toDays());

        List<Rival> rivals = new ArrayList<>(COUNT);
        for (int rank = 1; rank <= COUNT; rank++) {
            double target = TOP * Math.pow(rank, -SLOPE) * (0.85d + random.nextDouble() * 0.30d);
            double activity = 0.35d + random.nextDouble() * 0.55d;
            double bias = -0.8d + random.nextDouble() * 1.6d;
            List<Long> times = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (int day = 0; day < days; day++) {
                if (random.nextDouble() >= activity) {
                    continue;
                }
                // Une session le soir, entre 17 h et minuit (5 h + 12 a 19 h apres la bascule).
                long at = start + day * 86_400_000L + (12L + random.nextInt(7)) * 3_600_000L
                        + random.nextInt(60) * 60_000L;
                double weight = (0.5d + random.nextDouble()) * Math.max(0.1d, 1.0d + bias * ((double) day / days - 0.5d));
                times.add(Math.min(at, end - 60_000L));
                weights.add(weight);
            }
            if (times.isEmpty()) {
                times.add(start + (days / 2) * 86_400_000L + 20L * 3_600_000L);
                weights.add(1.0d);
            }
            double sum = 0.0d;
            for (double weight : weights) {
                sum += weight;
            }
            long[] at = new long[times.size()];
            long[] totals = new long[times.size()];
            double running = 0.0d;
            for (int i = 0; i < at.length; i++) {
                running += weights.get(i);
                at[i] = times.get(i);
                totals[i] = Math.round(target * running / sum);
            }
            int faction = random.nextDouble() < 0.2d ? -1 : random.nextInt(factions.size());
            rivals.add(new Rival(names.get((rank - 1) % names.size()), faction, at, totals));
        }
        return new Season(List.copyOf(rivals), factions);
    }
}
