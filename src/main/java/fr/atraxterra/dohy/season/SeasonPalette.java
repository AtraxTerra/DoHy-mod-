package fr.atraxterra.dohy.season;

/**
 * Les teintes des saisons en chiffres, sans Minecraft : ce que visent l'herbe,
 * le feuillage et les bouleaux a chaque moment de l'annee, et avec quelle force.
 *
 * <h2>Douze temps de l'annee</h2>
 *
 * <p>Chaque saison a un debut, un coeur et une fin, comme dans Serene Seasons :
 * douze temps, dont les teintes se fondent de l'un a l'autre (une courbe douce
 * entre deux temps voisins, sans saut). L'annee de l'herbe et des arbres :
 * <ul>
 *   <li><b>hiver</b> : les dernieres feuilles mortes, brunes, puis des arbres nus,
 *       gris brun ; l'herbe pale, olive et seche ;</li>
 *   <li><b>printemps</b> : les bourgeons d'un vert pale, puis un vert tendre et
 *       vif, puis un vert plein ; l'herbe reverdit franchement ;</li>
 *   <li><b>ete</b> : un vert profond au debut, les couleurs du jeu au coeur, puis
 *       l'herbe jaunit et le feuillage tourne a l'olive ;</li>
 *   <li><b>automne</b> : le feuillage s'embrase, une couleur par bosquet (rouge,
 *       orange, ambre, jaune, roux, cuivre), puis brunit et se fane ; l'herbe
 *       tourne a la paille puis a l'olive terne.</li>
 * </ul>
 *
 * <p>Les bosquets ne tournent pas tous en meme temps : chacun est en avance ou
 * en retard d'au plus {@link #SPREAD} d'annee (un jour environ), en
 * {@link #LEVELS} niveaux. Au debut de l'automne, des arbres rougis cotoient
 * des arbres encore verts ; au printemps, certains bourgeonnent plus tot.
 *
 * <p>Les bouleaux ont leur propre annee : vert pale au printemps, or a
 * l'automne. Les sapins n'en ont pas : les resineux restent verts.
 *
 * <h2>Sous les tropiques</h2>
 *
 * <p>Pas d'hiver, mais une saison seche et une saison des pluies : l'herbe dore
 * et le feuillage s'eteint au coeur de la saison seche ; tout reverdit, plus
 * vif que le jeu, au coeur des pluies. La force suit la latitude
 * ({@link SeasonMath#tropicsColorStrength}) : la foret de l'equateur reste verte.
 *
 * <p>Cette classe ne depend que de {@code java.lang} et de {@link SeasonMath} :
 * la CI la compile seule pour son test ({@code tools/SeasonPaletteTest.java}).
 */
public final class SeasonPalette {

    /**
     * Un temps de l'annee.
     *
     * @param grass        teinte visee par l'herbe
     * @param grassForce   part de cette teinte dans l'herbe, de 0 (le jeu) a 1
     * @param green        teinte visee par le feuillage hors couleurs d'automne
     * @param autumn       part des couleurs d'automne du bosquet dans le feuillage
     * @param wither       part du brun des feuilles mortes, par-dessus
     * @param foliageForce part de la teinte visee dans le feuillage
     * @param birch        teinte visee par les bouleaux
     * @param birchForce   part de cette teinte dans les bouleaux
     */
    public record Key(int grass, double grassForce, int green, double autumn, double wither, double foliageForce,
                      int birch, double birchForce) {
    }

    /** Les douze temps, du debut de l'hiver a la fin de l'automne ; le temps k regne en (k + 0,5) / 12. */
    private static final Key[] YEAR = {
            // hiver : feuilles mortes puis arbres nus, herbe pale
            new Key(0xA49A72, 0.80d, 0x7E6E55, 0.10d, 0.90d, 0.86d, 0x9A8456, 0.85d),
            new Key(0xA8A584, 0.84d, 0x786C5C, 0.0d, 0.0d, 0.88d, 0x8C8470, 0.85d),
            new Key(0xA09E76, 0.76d, 0x7A6E58, 0.0d, 0.0d, 0.84d, 0x908A6C, 0.80d),
            // printemps : bourgeons, vert tendre, vert plein
            new Key(0x9CC456, 0.55d, 0xA6D86A, 0.0d, 0.0d, 0.62d, 0xB6DE78, 0.65d),
            new Key(0x76DE48, 0.55d, 0x82E445, 0.0d, 0.0d, 0.58d, 0x9EE05A, 0.55d),
            new Key(0x5ED040, 0.42d, 0x5CCB36, 0.0d, 0.0d, 0.45d, 0x88CF4C, 0.35d),
            // ete : vert profond, les couleurs du jeu, l'herbe qui jaunit
            new Key(0x4CB83A, 0.30d, 0x3FA22C, 0.0d, 0.0d, 0.30d, 0x78B04A, 0.15d),
            new Key(0x8CB848, 0.12d, 0x5E9E2C, 0.0d, 0.0d, 0.10d, 0x80A755, 0.0d),
            new Key(0xBCB04A, 0.48d, 0x7C9A30, 0.0d, 0.0d, 0.30d, 0xA2B04A, 0.30d),
            // automne : le feuillage s'embrase, puis brunit
            new Key(0xC6AA50, 0.58d, 0x93A23A, 0.70d, 0.0d, 0.85d, 0xE6C02E, 0.75d),
            new Key(0xCA9C48, 0.70d, 0x93A23A, 1.0d, 0.06d, 0.95d, 0xF2B526, 0.92d),
            new Key(0xA88A5A, 0.78d, 0x93A23A, 0.85d, 0.60d, 0.92d, 0xBC8C3A, 0.88d),
    };

    /** Les feuillages d'automne, un par bosquet : rouge, orange, ambre, jaune, roux, cuivre. */
    public static final int[] AUTUMN = {0xD2381E, 0xE8641C, 0xF0A02A, 0xE0C437, 0xB4452A, 0xC9762E};

    /** Le brun des feuilles mortes. */
    public static final int WITHER = 0x8B5A2E;

    /** Niveaux d'avance des bosquets. */
    public static final int LEVELS = 8;

    /** L'avance ou le retard le plus grand d'un bosquet, en part d'annee (un peu plus d'un jour). */
    public static final double SPREAD = 0.04d;

    // Sous les tropiques : coeur de la saison seche, coeur de la saison des pluies.
    private static final int DRY_GRASS = 0xC9B262;
    private static final double DRY_GRASS_FORCE = 0.62d;
    private static final int DRY_FOLIAGE = 0x9AA244;
    private static final double DRY_FOLIAGE_FORCE = 0.36d;
    private static final int WET_GRASS = 0x52CC3E;
    private static final double WET_GRASS_FORCE = 0.40d;
    private static final int WET_FOLIAGE = 0x38B832;
    private static final double WET_FOLIAGE_FORCE = 0.32d;

    private SeasonPalette() {
    }

    /** Nombre de temps dans l'annee. */
    public static int keys() {
        return YEAR.length;
    }

    /** Le temps numero {@code index} (0 : debut de l'hiver). */
    public static Key key(int index) {
        return YEAR[Math.floorMod(index, YEAR.length)];
    }

    /** L'avance d'un niveau de bosquet, de -{@link #SPREAD} (tardif) a +{@link #SPREAD} (precoce). */
    public static double precocity(int level) {
        return SPREAD * (2.0d * level / (LEVELS - 1) - 1.0d);
    }

    /**
     * Les teintes a une phase locale (0 : debut de l'hiver de l'hemisphere) : entre
     * les deux temps qui l'encadrent, par une courbe douce. Toute phase est admise,
     * l'annee boucle.
     */
    public static Key at(double local) {
        double position = local * YEAR.length - 0.5d;
        double floor = Math.floor(position);
        double fraction = position - floor;
        double t = fraction * fraction * (3.0d - 2.0d * fraction);
        int index = (int) floor;
        Key a = key(index);
        Key b = key(index + 1);
        return new Key(mix(a.grass(), b.grass(), t), lerp(a.grassForce(), b.grassForce(), t),
                mix(a.green(), b.green(), t), lerp(a.autumn(), b.autumn(), t), lerp(a.wither(), b.wither(), t),
                lerp(a.foliageForce(), b.foliageForce(), t), mix(a.birch(), b.birch(), t),
                lerp(a.birchForce(), b.birchForce(), t));
    }

    /** La teinte visee par le feuillage d'un bosquet de couleur d'automne {@code shade}, a un temps. */
    public static int foliageTarget(Key key, int shade) {
        int color = key.autumn() <= 0.0d ? key.green() : mix(key.green(), AUTUMN[Math.floorMod(shade, AUTUMN.length)],
                key.autumn());
        return key.wither() <= 0.0d ? color : mix(color, WITHER, key.wither());
    }

    /**
     * Sous les tropiques, a une phase locale.
     *
     * @param grass        teinte visee par l'herbe
     * @param grassForce   sa part, avant la force de la latitude
     * @param foliage      teinte visee par le feuillage (bouleaux compris)
     * @param foliageForce sa part, avant la force de la latitude
     */
    public record Tropics(int grass, double grassForce, int foliage, double foliageForce) {
    }

    /** La saison seche a son coeur au milieu de l'hiver local, la saison des pluies au milieu de l'ete. */
    public static Tropics tropics(double local) {
        double chill = SeasonMath.chill(local - Math.floor(local));
        if (chill >= 0.0d) {
            return new Tropics(DRY_GRASS, DRY_GRASS_FORCE * chill, DRY_FOLIAGE, DRY_FOLIAGE_FORCE * chill);
        }
        return new Tropics(WET_GRASS, WET_GRASS_FORCE * -chill, WET_FOLIAGE, WET_FOLIAGE_FORCE * -chill);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Un melange de deux couleurs RGB, {@code share} de la seconde. */
    public static int mix(int first, int second, double share) {
        double keep = 1.0d - share;
        int red = (int) Math.round(((first >> 16) & 0xFF) * keep + ((second >> 16) & 0xFF) * share);
        int green = (int) Math.round(((first >> 8) & 0xFF) * keep + ((second >> 8) & 0xFF) * share);
        int blue = (int) Math.round((first & 0xFF) * keep + (second & 0xFF) * share);
        return red << 16 | green << 8 | blue;
    }

    /**
     * Une couleur du jeu tiree vers une teinte : {@code amount} de la teinte, au plus
     * tout ; l'octet de transparence de {@code base} est garde.
     */
    public static int blend(int base, int target, double amount) {
        if (amount <= 0.0d) {
            return base;
        }
        return base & 0xFF000000 | mix(base & 0xFFFFFF, target & 0xFFFFFF, Math.min(1.0d, amount));
    }
}
