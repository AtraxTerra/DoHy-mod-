package fr.atraxterra.dohy.season;

/**
 * Les saisons de la Terre en chiffres, sans Minecraft.
 *
 * <h2>Le calendrier</h2>
 *
 * <p>Une saison par semaine reelle (choix du joueur) : l'annee dure quatre
 * semaines et chaque saison s'ouvre le lundi a 5 h, comme les contrats et le
 * classement. L'annee se lit comme une <b>phase</b> de 0 a 1 ; 0 ouvre l'hiver
 * du nord. Le sud vit la meme annee avec une demi-annee d'ecart : son hiver est
 * l'ete du nord.
 *
 * <h2>Le froid selon la latitude</h2>
 *
 * <p>Le jeu decide de la neige par la temperature d'un biome : en dessous de
 * 0,15, il neige et l'eau gele. Les saisons ajoutent a cette temperature un
 * decalage qui depend de la latitude et du moment de l'annee :
 * <ul>
 *   <li><b>sous les tropiques</b> (moins de 23,44°), aucun : il n'y a pas
 *       d'hiver ;</li>
 *   <li>au-dela, une force qui monte en douceur jusqu'a 65°, ou le plein hiver
 *       retire 0,75 ;</li>
 *   <li>l'ete rechauffe trois fois moins que l'hiver ne refroidit : la toundra
 *       degele, les glaciers tiennent.</li>
 * </ul>
 *
 * <p>Reglage « realiste » (choix du joueur) : en plaine, la neige ne tient
 * qu'au-dela de 50° environ, et quelques jours au coeur de l'hiver ; elle
 * tombe plus bas en montagne, ou le jeu refroidit deja l'air avec l'altitude.
 * Paris garde ses plaines et ses forets vertes, Berlin blanchit un peu,
 * Stockholm et Moscou franchement, la taiga et les Alpes aussi. Les chiffres sont verifies par {@code tools/SeasonMathTest.java}.
 *
 * <p>Cette classe ne depend que de {@code java.lang} : la CI la compile seule
 * pour son test.
 */
public final class SeasonMath {

    /** Les tropiques : en deca, pas d'hiver. */
    public static final double TROPIC = 23.44d;

    /** A partir de cette latitude, l'hiver a toute sa force. */
    public static final double FULL = 65.0d;

    /** Ce que le plein hiver retire a la temperature d'un biome, a pleine force. */
    public static final double WINTER_DROP = 0.75d;

    /** Ce que le plein ete ajoute, en part du froid d'hiver. */
    public static final double SUMMER_SHARE = 0.3d;

    /** En dessous, il neige et l'eau gele : le seuil du jeu. */
    public static final float SNOW_LINE = 0.15f;

    /**
     * Au-dessus, la neige et la glace fondent. L'ecart avec {@link #SNOW_LINE}
     * evite qu'un meme bloc gele et fonde tour a tour a la limite.
     */
    public static final float MELT_LINE = 0.18f;

    /** En plein hiver a pleine force, la part des pousses de cultures qui passent dehors. */
    public static final double WINTER_GROWTH = 0.2d;

    public static final int WINTER = 0;
    public static final int SPRING = 1;
    public static final int SUMMER = 2;
    public static final int AUTUMN = 3;

    private static final String[] NAMES = {"Hiver", "Printemps", "Ete", "Automne"};

    private SeasonMath() {
    }

    /** La phase de l'annee, de 0 (inclus) a 1 (exclu) ; 0 ouvre l'hiver du nord. */
    public static double phase(long now, long anchor, long year) {
        return Math.floorMod(now - anchor, year) / (double) year;
    }

    /** La phase vue d'un hemisphere : le sud a une demi-annee d'ecart. */
    public static double local(double phase, double latitude) {
        double local = latitude >= 0.0d ? phase : phase + 0.5d;
        return local - Math.floor(local);
    }

    /** La saison d'une phase locale : {@link #WINTER}, {@link #SPRING}, {@link #SUMMER} ou {@link #AUTUMN}. */
    public static int season(double local) {
        return Math.max(0, Math.min(3, (int) Math.floor(local * 4.0d)));
    }

    /** Le jour de la saison, de 1 a {@code days}. */
    public static int day(double local, int days) {
        double within = local * 4.0d - season(local);
        return Math.max(1, Math.min(days, (int) Math.floor(within * days) + 1));
    }

    /** La part d'annee qui reste avant la saison suivante. */
    public static double untilNext(double local) {
        return (season(local) + 1) / 4.0d - local;
    }

    /** Le froid de saison : 1 au coeur de l'hiver, -1 au coeur de l'ete, 0 aux mi-saisons. */
    public static double chill(double local) {
        return Math.cos(2.0d * Math.PI * (local - 0.125d));
    }

    /** La force des saisons a une latitude : 0 sous les tropiques, 1 au-dela de 65°. */
    public static double strength(double latitude) {
        double x = (Math.abs(latitude) - TROPIC) / (FULL - TROPIC);
        if (x <= 0.0d) {
            return 0.0d;
        }
        if (x >= 1.0d) {
            return 1.0d;
        }
        return x * x * (3.0d - 2.0d * x);
    }

    /** Le decalage de temperature, en unites du jeu, a ajouter a celle d'un biome. */
    public static double offset(double latitude, double phase) {
        double strength = strength(latitude);
        if (strength <= 0.0d) {
            return 0.0d;
        }
        double chill = chill(local(phase, latitude));
        double drop = WINTER_DROP * strength * chill;
        return chill > 0.0d ? -drop : -drop * SUMMER_SHARE;
    }

    /** La rigueur de l'hiver, de 0 (pas d'hiver) a 1 (coeur de l'hiver, au-dela de 65°). */
    public static double winter(double latitude, double phase) {
        return strength(latitude) * Math.max(0.0d, chill(local(phase, latitude)));
    }

    /** La part des pousses de cultures qui passent, dehors et hors gel. */
    public static double growth(double latitude, double phase) {
        return 1.0d - (1.0d - WINTER_GROWTH) * winter(latitude, phase);
    }

    /** Vrai sous les tropiques : pas d'hiver, une saison seche et une saison des pluies. */
    public static boolean tropical(double latitude) {
        return Math.abs(latitude) < TROPIC;
    }

    /** Sous les tropiques, la saison des pluies : la moitie chaude de l'annee locale. */
    public static boolean wet(double latitude, double phase) {
        return chill(local(phase, latitude)) < 0.0d;
    }

    /** Le nom de la saison, sans accent : « Hiver », « Ete »..., « Saison seche » sous les tropiques. */
    public static String name(double latitude, double phase) {
        if (tropical(latitude)) {
            return wet(latitude, phase) ? "Saison des pluies" : "Saison seche";
        }
        return NAMES[season(local(phase, latitude))];
    }

    /** Le nom d'une saison par son numero. */
    public static String name(int season) {
        return NAMES[Math.max(0, Math.min(3, season))];
    }

    /**
     * Les degres affiches pour une temperature du jeu : 0,15 (la neige) donne
     * -1 °C, une plaine (0,8) 15 °C, un desert (2,0) 45 °C.
     */
    public static int celsius(double temperature) {
        return (int) Math.round(25.0d * temperature - 5.0d);
    }

    /**
     * Le poids d'une saison dans les couleurs : 1 en son milieu, 0 au milieu des
     * saisons voisines. Deux saisons voisines se partagent toujours le tout,
     * donc les teintes glissent de l'une a l'autre sans saut.
     */
    public static double weight(double local, int season) {
        double distance = local - (season * 0.25d + 0.125d);
        distance -= Math.floor(distance + 0.5d);
        double cosine = Math.cos(2.0d * Math.PI * distance);
        return cosine > 0.0d ? cosine * cosine : 0.0d;
    }
}
