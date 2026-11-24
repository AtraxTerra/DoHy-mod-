package fr.atraxterra.dohy.faction.garrison;

/**
 * Les soldats qu'une caserne recrute : les memes metiers que les soldats des nations rivales
 * ({@link fr.atraxterra.dohy.faction.war.SoldierRole}), ceux qui servent a defendre.
 *
 * <p>Les chiffres de combat sont ceux des soldats ennemis (vie, degats, vitesse) ; le recrutement
 * et la solde sont propres a la garnison ({@code BALANCE.md}, la garnison).
 */
public enum GarrisonRole {
    FANTASSIN("Fantassin", "Fantassins", 26.0d, 4.0d, 0.27d, 20_000L, 2_000L, 0,
            "Epee et bouclier."),
    ARCHER("Archer", "Archers", 20.0d, 2.0d, 0.27d, 25_000L, 2_500L, 0,
            "Tire a l'arc."),
    MEDECIN("Medecin", "Medecins", 20.0d, 1.0d, 0.28d, 35_000L, 3_500L, 0,
            "Soigne la garnison, et toi."),
    CAPITAINE("Capitaine", "Capitaine", 60.0d, 7.0d, 0.25d, 80_000L, 8_000L, 1,
            "Force et Resistance aux soldats proches.");

    private final String label;
    private final String plural;
    private final double health;
    private final double damage;
    private final double speed;
    private final long cost;
    private final long pay;
    /** Le nombre qu'une garnison peut en avoir ; 0 : pas de limite propre. */
    private final int max;
    private final String description;

    GarrisonRole(String label, String plural, double health, double damage, double speed, long cost, long pay,
                 int max, String description) {
        this.label = label;
        this.plural = plural;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.cost = cost;
        this.pay = pay;
        this.max = max;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String plural() {
        return plural;
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

    /** Le prix du recrutement, pris dans la banque de faction. */
    public long cost() {
        return cost;
    }

    /** La solde d'un jour reel. */
    public long pay() {
        return pay;
    }

    public int max() {
        return max;
    }

    public String description() {
        return description;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static GarrisonRole byId(String id) {
        for (GarrisonRole role : values()) {
            if (role.id().equals(id)) {
                return role;
            }
        }
        return FANTASSIN;
    }
}
