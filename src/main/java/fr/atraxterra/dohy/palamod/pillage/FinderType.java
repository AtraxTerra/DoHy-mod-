package fr.atraxterra.dohy.palamod.pillage;

/**
 * Les detecteurs ({@code ItemUnclaimFinder}, {@code ItemSpawnerFinder}).
 *
 * <p>Sur Paladium, l'Unclaim Finder montre la part de « tile entities »
 * (coffres, fours, machines) des chunks autour du joueur : une base non claim
 * se trahit par son pourcentage. Ici, ce sont les conteneurs — coffres de
 * structures, de donjons, de bases a piller — qui se trahissent, dans un carre
 * de chunks centre sur le joueur : 1, 9, 25 ou 49 chunks selon la couleur. Le
 * bleu indique en plus la direction du plus proche.
 *
 * <p>Le Spawner Finder cherche les spawners, sur 9 chunks.
 *
 * @param radius rayon du carre, en chunks : 0 pour le seul chunk du joueur
 * @param color  couleur de la jauge
 */
public enum FinderType {
    GREEN("unclaim_finder", 0, 0xFF22E558, false, false),
    ORANGE("unclaim_finder_orange", 1, 0xFFF39A1E, false, false),
    RED("unclaim_finder_red", 2, 0xFFEF3826, false, false),
    BLUE("unclaim_finder_blue", 3, 0xFF3FA9F5, true, false),
    SPAWNER("spawner_finder", 1, 0xFFB45CF0, false, true);

    /** 180 000 points uses de 20 par seconde en 1.7.10 : deux heures et demie tenu en main. */
    public static final int DURABILITY = 9_000;

    /** Un pourcentage de 10 par conteneur trouve, plafonne a 100. */
    public static final int PERCENT_PER_FIND = 10;

    private final String id;
    private final int radius;
    private final int color;
    private final boolean direction;
    private final boolean spawners;

    FinderType(String id, int radius, int color, boolean direction, boolean spawners) {
        this.id = id;
        this.radius = radius;
        this.color = color;
        this.direction = direction;
        this.spawners = spawners;
    }

    public String id() {
        return id;
    }

    public int radius() {
        return radius;
    }

    public int chunks() {
        return (2 * radius + 1) * (2 * radius + 1);
    }

    public int color() {
        return color;
    }

    public boolean direction() {
        return direction;
    }

    public boolean spawners() {
        return spawners;
    }
}
