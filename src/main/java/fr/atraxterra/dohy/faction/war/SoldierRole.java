package fr.atraxterra.dohy.faction.war;

/**
 * Les roles d'une armee en campagne.
 *
 * <p>Chacun porte ce qui le fait reconnaitre de loin : la banniere de sa
 * nation sur la tete du commandant, une caisse de TNT sous le bras du sapeur,
 * un coffre dans les mains du tresorier (tout en or), un casque d'or, une
 * pioche et une echelle pour l'ingenieur, une tunique blanche pour le
 * medecin. Le reste de la troupe porte le cuir teint aux couleurs de la
 * nation.
 *
 * <ul>
 *   <li><b>Fantassin</b> : la masse de l'assaut, il tient la breche et le coffre ;</li>
 *   <li><b>Archer</b> : il couvre les travaux depuis l'arriere, garde ses distances ;</li>
 *   <li><b>Sapeur</b> : il pose les charges contre tes murs ;</li>
 *   <li><b>Ingenieur</b> : il creuse, pose les echelles, jette les ponts ;</li>
 *   <li><b>Medecin</b> : il soigne les blesses a la potion ;</li>
 *   <li><b>Commandant</b> : il dirige depuis le camp, pres de l'etendard — sa
 *       chute brise le moral et la coordination ;</li>
 *   <li><b>Tresorier</b> : il vient vider ta banque quand le coffre est pris,
 *       et repart avec. Le tuer rend l'argent.</li>
 * </ul>
 */
public enum SoldierRole {
    FANTASSIN("Fantassin", 26.0d, 4.0d, 0.27d, 6),
    ARCHER("Archer", 20.0d, 2.0d, 0.27d, 5),
    SAPEUR("Sapeur", 22.0d, 2.0d, 0.30d, 9),
    INGENIEUR("Ingenieur", 24.0d, 3.0d, 0.27d, 9),
    MEDECIN("Medecin", 20.0d, 1.0d, 0.28d, 8),
    COMMANDANT("Commandant", 60.0d, 7.0d, 0.25d, 30),
    TRESORIER("Tresorier", 30.0d, 1.0d, 0.30d, 20);

    private final String label;
    private final double health;
    private final double damage;
    private final double speed;
    /** Le coup que sa chute porte au moral de l'armee, en points. */
    private final int weight;

    SoldierRole(String label, double health, double damage, double speed, int weight) {
        this.label = label;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.weight = weight;
    }

    public static SoldierRole of(int index) {
        SoldierRole[] all = values();
        return index >= 0 && index < all.length ? all[index] : FANTASSIN;
    }

    public String label() {
        return label;
    }

    public double health() {
        return health;
    }

    public double damage() {
        return damage;
    }

    public double speed() {
        return speed;
    }

    public int weight() {
        return weight;
    }

    /** Ceux qui se battent au corps a corps quand on les attaque. */
    public boolean fighter() {
        return this == FANTASSIN || this == COMMANDANT || this == INGENIEUR;
    }

    /** Ceux qui fuient le joueur plutot que de l'affronter. */
    public boolean shy() {
        return this == SAPEUR || this == MEDECIN || this == TRESORIER;
    }

    /** Ceux qui travaillent aux obstacles. */
    public boolean worker() {
        return this == SAPEUR || this == INGENIEUR;
    }
}
