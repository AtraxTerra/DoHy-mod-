package fr.atraxterra.dohy.agri;

/** Ce qu'un tracteur peut tirer : un outil a la fois, large de trois blocs. */
public enum TractorTool {
    NONE("none", "Aucun outil"),
    /** Retourne la terre et l'herbe en terre labouree. */
    PLOW("plow", "Charrue"),
    /** Seme sur la terre labouree les semences chargees dans le tracteur. */
    SEEDER("seeder", "Semoir"),
    /** Epand l'engrais charge sur les cultures. */
    SPREADER("spreader", "Epandeur"),
    /** Moissonne les cereales mures : le grain reste dans le tracteur jusqu'au silo. */
    HARVESTER("harvester", "Moissonneuse");

    private final String key;
    private final String label;

    TractorTool(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static TractorTool byId(int id) {
        TractorTool[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
