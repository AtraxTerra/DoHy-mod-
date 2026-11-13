package fr.atraxterra.dohy.season;

/**
 * Le climat de saison du moment, tel que le lit le mixin de temperature des
 * biomes ({@code BiomeTemperatureMixin}).
 *
 * <h2>Deux contextes</h2>
 *
 * <p>Le serveur connait la carte de la Terre ({@code EarthData}) ; un client
 * branche sur un serveur dedie ne la connait que par le paquet
 * {@link SeasonSync}. Chacun a donc son contexte : {@link #SERVER} et
 * {@link #CLIENT}. En solo les deux existent et disent la meme chose ; le
 * serveur passe en premier.
 *
 * <h2>Une lecture qui ne coute rien</h2>
 *
 * <p>La temperature d'un biome est demandee des milliers de fois par tick, sur
 * le fil du serveur, ceux de la generation et celui du rendu. Le decalage de
 * saison ne depend que de la latitude, donc du z : il est precalcule par bande
 * de 16 blocs dans une table, refaite toutes les quelques secondes et
 * remplacee d'un seul coup (champ {@code volatile}). Lire, c'est un test de
 * bornes et une case de tableau.
 *
 * <p>Hors de l'emprise de la carte — et donc dans les autres dimensions, dont
 * les arenes et les donjons pres de l'origine, sous les tropiques de la carte —
 * le decalage vaut zero.
 */
public final class SeasonClimate {

    /** Le contexte du serveur : la carte est chargee. */
    public static final SeasonClimate SERVER = new SeasonClimate();

    /** Le contexte du client : la carte a ete recue. */
    public static final SeasonClimate CLIENT = new SeasonClimate();

    /** Une bande de la table : 16 blocs de z. */
    private static final int BAND_SHIFT = 4;

    /**
     * La carte : sa projection et son emprise en blocs.
     *
     * @param miller          projection de Miller (sinon equirectangulaire)
     * @param blocksPerDegree blocs par degre
     * @param equatorZ        le z de l'equateur
     * @param dimension       la dimension de la carte, {@code minecraft:overworld}
     */
    public record Frame(boolean miller, double blocksPerDegree, double equatorZ, int minX, int maxX, int minZ,
                        int maxZ, String dimension) {

        /** La latitude d'un z : l'inverse de la projection, le nord vers les z negatifs (comme EarthData). */
        public double latitude(double z) {
            double projected = (equatorZ - z) / blocksPerDegree;
            if (!miller) {
                return Math.max(-90.0d, Math.min(90.0d, projected));
            }
            double radians = Math.toRadians(projected);
            return Math.toDegrees(2.5d * Math.atan(Math.exp(0.8d * radians)) - 0.625d * Math.PI);
        }

        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    /** Le calendrier : l'instant ou s'ouvre un hiver du nord, et la duree de l'annee. */
    public record Clock(long anchor, long year) {
        public double phase(long now) {
            return SeasonMath.phase(now, anchor, year);
        }
    }

    /**
     * Ce que lisent le mixin et les couleurs.
     *
     * @param offsets   le decalage de temperature de chaque bande
     * @param strengths la force des couleurs de saison de chaque bande, positive au nord, negative au sud
     */
    private record Table(Frame frame, float[] offsets, float[] strengths, double phase) {

        float offset(int x, int z) {
            if (!frame.contains(x, z)) {
                return 0.0f;
            }
            return offsets[(z - frame.minZ()) >> BAND_SHIFT];
        }

        float strength(int x, int z) {
            if (!frame.contains(x, z)) {
                return 0.0f;
            }
            return strengths[(z - frame.minZ()) >> BAND_SHIFT];
        }
    }

    private volatile Frame frame;
    private volatile Clock clock;
    private volatile long skew;
    private volatile Table table;
    private volatile boolean dimension = true;

    private SeasonClimate() {
    }

    /**
     * Met en service : la carte, le calendrier, et l'ecart d'horloge avec le
     * serveur (zero cote serveur ; cote client, de quoi lire l'heure du serveur).
     */
    public void configure(Frame frame, Clock clock, long skew) {
        this.frame = frame;
        this.clock = clock;
        this.skew = skew;
        refresh();
    }

    /** Hors service : plus de saison dans ce contexte. */
    public void clear() {
        table = null;
        frame = null;
        clock = null;
        skew = 0L;
        dimension = true;
    }

    /** Vrai si les saisons tournent dans ce contexte. */
    public boolean active() {
        return table != null;
    }

    public Frame frame() {
        return frame;
    }

    public Clock clock() {
        return clock;
    }

    /** L'heure du serveur, en millisecondes. */
    public long now() {
        return System.currentTimeMillis() + skew;
    }

    /** La phase de l'annee en ce moment, 0 si les saisons ne tournent pas. */
    public double phase() {
        Clock current = clock;
        return current == null ? 0.0d : current.phase(now());
    }

    /** La phase qui a servi a la derniere table. */
    public double tablePhase() {
        Table current = table;
        return current == null ? 0.0d : current.phase();
    }

    /** Cote client : la dimension affichee est-elle celle de la carte ? Sinon, pas de saison a l'ecran. */
    public void dimension(boolean onMap) {
        dimension = onMap;
    }

    /** Refait la table pour l'instant present. */
    public void refresh() {
        Frame current = frame;
        Clock calendar = clock;
        if (current == null || calendar == null) {
            table = null;
            return;
        }
        double phase = calendar.phase(now());
        int bands = ((current.maxZ() - current.minZ()) >> BAND_SHIFT) + 1;
        float[] offsets = new float[bands];
        float[] strengths = new float[bands];
        for (int band = 0; band < bands; band++) {
            double latitude = current.latitude(current.minZ() + (band << BAND_SHIFT) + 8);
            offsets[band] = (float) SeasonMath.offset(latitude, phase);
            float strength = (float) SeasonMath.colorStrength(latitude);
            strengths[band] = latitude >= 0.0d ? strength : -strength;
        }
        table = new Table(current, offsets, strengths, phase);
    }

    /** Le decalage a ajouter a la temperature d'un biome en (x, z). */
    public static float offset(int x, int z) {
        Table current = SERVER.table;
        if (current == null) {
            current = CLIENT.table;
            if (current == null || !CLIENT.dimension) {
                return 0.0f;
            }
        }
        return current.offset(x, z);
    }

    /**
     * La force des couleurs de saison en (x, z), signee : positive au nord,
     * negative au sud ; zero hors de la carte ou si ce contexte ne tourne pas.
     */
    public float strength(int x, int z) {
        Table current = table;
        if (current == null || this == CLIENT && !dimension) {
            return 0.0f;
        }
        return current.strength(x, z);
    }
}
