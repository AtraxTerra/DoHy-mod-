package fr.atraxterra.dohy.palamod.dungeon;

/**
 * Les six types de monstres des donjons (« Ennemis » du wiki), communs aux
 * quatre donjons. Le wiki n'en donne que le role ; les chiffres sont ceux de
 * DoHy, calibres pour l'equipement en endium au palier 100.
 *
 * <ul>
 *   <li>{@code health} : points de vie au palier 1, multiplies ensuite ;</li>
 *   <li>{@code from} : premier palier ou le type apparait — assassin et tank
 *       sont des ajouts de la V12, on les introduit plus tard ;</li>
 *   <li>{@code weight} : poids dans le tirage d'une vague.</li>
 * </ul>
 */
public enum MobKind {

    /** Fonce sur le joueur. A Manelios, se demultiplie a sa mort. */
    MELEE(22.0d, 1, 34),
    /** Ne fait aucun degat : renforce les monstres, gene les joueurs. Immobile. */
    MAGE(30.0d, 10, 8),
    /** Tire en gardant ses distances. */
    RANGED(20.0d, 1, 22),
    /** Vole au-dessus des joueurs et tire. */
    FLYING(20.0d, 5, 10),
    /** Bondit sur le joueur des qu'il est a portee. */
    ASSASSIN(18.0d, 10, 14),
    /** Tres resistant. */
    TANK(60.0d, 20, 12);

    private final double health;
    private final int from;
    private final int weight;

    MobKind(double health, int from, int weight) {
        this.health = health;
        this.from = from;
        this.weight = weight;
    }

    public double health() {
        return health;
    }

    public int from() {
        return from;
    }

    public int weight() {
        return weight;
    }
}
