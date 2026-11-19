package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le catalogue des evenements portes et le tirage ({@code LuckyEvents},
 * {@code PLuckyBlock.events} et {@code luckypassEvents}).
 *
 * <p>Le tirage de PalaMod : un evenement du type du bloc, au poids
 * {@code 1 / rarete}. Avec le Lucky Pass, les evenements bons pesent 1,25 fois
 * plus ({@code PLuckyBlock$2}). Le Lucky Pass est toujours actif ici : le bonus
 * s'applique a tous les tirages.
 *
 * <p>Tous les evenements ne sont pas encore portes. Pour que la part des bons et
 * des mauvais reste celle de Paladium, le bonus des bons est recalcule sur ce qui
 * existe : avec les 111 evenements du lucky block en paladium, bonus compris,
 * 51,48 % des tirages sont bons ; avec les 130 du findium, 44,38 %. Chaque lucky
 * block saisonnier a sa propre part. Les chiffres viennent de
 * {@code tools/lucky_assets.py --stats}. A chaque lot porte, le calcul suit.
 */
public final class LuckyEvents {

    /** La part de bons au lucky block en paladium, bonus du Lucky Pass compris. */
    private static final double PALADIUM_GOOD = 0.5148d;
    /** La part de bons au lucky block en findium, bonus du Lucky Pass compris. */
    private static final double FINDIUM_GOOD = 0.4438d;
    /** Le bonus du Lucky Pass, quand il n'y a rien a equilibrer (l'endium n'a que des bons). */
    private static final double PASS_BONUS = 1.25d;

    private static Map<String, LuckyEvent> events;

    private LuckyEvents() {
    }

    /** Tous les evenements portes, dans l'ordre du catalogue de PalaMod. */
    public static synchronized Map<String, LuckyEvent> all() {
        if (events == null) {
            Catalog catalog = new Catalog();
            GoodEvents.register(catalog);
            BadEvents.register(catalog);
            FindiumEvents.register(catalog);
            EffectEvents.register(catalog);
            TrickEvents.register(catalog);
            FindiumTricks.register(catalog);
            ContentEvents.register(catalog);
            FindiumContent.register(catalog);
            LuckyItemEvents.register(catalog);
            ClassicContent.register(catalog);
            MarchEvents.register(catalog);
            EasterEvents.register(catalog);
            MayEvents.register(catalog);
            JuneEvents.register(catalog);
            JulyEvents.register(catalog);
            AugustEvents.register(catalog);
            SeptemberEvents.register(catalog);
            HalloweenEvents.register(catalog);
            events = Collections.unmodifiableMap(catalog.map);
        }
        return events;
    }

    /** L'evenement de ce nom, ou {@code null}. */
    public static LuckyEvent get(String id) {
        return id == null ? null : all().get(id);
    }

    /** Les evenements qui peuvent sortir de ce lucky block. */
    public static List<LuckyEvent> pool(LuckyType type) {
        List<LuckyEvent> pool = new ArrayList<>();
        for (LuckyEvent event : all().values()) {
            if (event.types().contains(type)) {
                pool.add(event);
            }
        }
        return pool;
    }

    /** Le nombre d'evenements differents, tous lucky blocks confondus. */
    public static int count() {
        return all().size();
    }

    /**
     * Le multiplicateur des bons evenements de ce type, pour que leur part soit
     * celle de Paladium.
     */
    static double goodScale(LuckyType type) {
        double good = 0.0d;
        double bad = 0.0d;
        for (LuckyEvent event : pool(type)) {
            if (event.bad()) {
                bad += event.weight();
            } else {
                good += event.weight();
            }
        }
        double target = switch (type) {
            case PALADIUM -> PALADIUM_GOOD;
            case FINDIUM -> FINDIUM_GOOD;
            case ENDIUM, HALLOWEEN -> 1.0d;
            // Les lucky blocks saisonniers, sur tous leurs evenements de PalaMod (--stats).
            case BLACK -> 0.9001d;
            case MARCH -> 0.7772d;
            case EASTER -> 0.6174d;
            case MAY -> 0.6886d;
            case JUNE -> 0.5406d;
            case JULY -> 0.7340d;
            case AUGUST -> 0.6217d;
            case SEPTEMBER -> 0.7064d;
            case NOVEMBER -> 0.3733d;
            case CHRISTMAS -> 0.7406d;
        };
        if (good <= 0.0d || bad <= 0.0d || target >= 1.0d) {
            return PASS_BONUS;
        }
        return target * bad / ((1.0d - target) * good);
    }

    /** La chance de cet evenement dans ce lucky block, entre 0 et 1. */
    public static double chance(LuckyEvent event, LuckyType type) {
        double scale = goodScale(type);
        double total = 0.0d;
        for (LuckyEvent other : pool(type)) {
            total += other.weight() * (other.bad() ? 1.0d : scale);
        }
        return total <= 0.0d || !event.types().contains(type) ? 0.0d
                : event.weight() * (event.bad() ? 1.0d : scale) / total;
    }

    /**
     * Tire un evenement. {@code chained} : le bloc vient de « Quelle dinguerie ! »,
     * qui ne peut pas en ressortir (sans quoi une chaine de lucky blocks en endium
     * ne finirait jamais).
     */
    public static LuckyEvent draw(LuckyType type, RandomSource random, boolean chained) {
        List<LuckyEvent> pool = pool(type);
        if (chained) {
            pool.removeIf(event -> event.id().equals(GoodEvents.WOW_BG));
        }
        double scale = goodScale(type);
        double total = 0.0d;
        for (LuckyEvent event : pool) {
            total += event.weight() * (event.bad() ? 1.0d : scale);
        }
        double roll = random.nextDouble() * total;
        for (LuckyEvent event : pool) {
            roll -= event.weight() * (event.bad() ? 1.0d : scale);
            if (roll < 0.0d) {
                return event;
            }
        }
        return pool.isEmpty() ? null : pool.get(pool.size() - 1);
    }

    /** L'inscription des evenements, par les trois classes qui les portent. */
    public static final class Catalog {
        private final Map<String, LuckyEvent> map = new LinkedHashMap<>();

        private Catalog() {
        }

        /**
         * Un evenement des lucky blocks classiques : il sort du paladium, et de
         * l'endium s'il est bon (le constructeur par defaut de {@code LuckyEvents}).
         */
        public void classic(String id, String name, String icon, boolean bad, int rarity, LuckyEvent.Action action) {
            add(new LuckyEvent(id, name, icon, bad, rarity, bad ? EnumSet.of(LuckyType.PALADIUM)
                    : EnumSet.of(LuckyType.PALADIUM, LuckyType.ENDIUM), action));
        }

        /** Un evenement du lucky block en findium, et de lui seul. */
        public void findium(String id, String name, String icon, boolean bad, int rarity, LuckyEvent.Action action) {
            add(new LuckyEvent(id, name, icon, bad, rarity, EnumSet.of(LuckyType.FINDIUM), action));
        }

        /** Un evenement d'un lucky block saisonnier (le noir, ceux des mois), et de lui seul. */
        public void season(LuckyType type, String id, String name, String icon, boolean bad, int rarity,
                           LuckyEvent.Action action) {
            add(new LuckyEvent(id, name, icon, bad, rarity, EnumSet.of(type), action));
        }

        private void add(LuckyEvent event) {
            if (map.put(event.id(), event) != null) {
                throw new IllegalStateException("evenement en double : " + event.id());
            }
        }
    }
}
