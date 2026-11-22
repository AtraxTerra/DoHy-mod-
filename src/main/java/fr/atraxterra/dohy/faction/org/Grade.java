package fr.atraxterra.dohy.faction.org;

/**
 * La hierarchie interne de la nation (lot 24a) : cinq grades, de la recrue au haut cadre. Le
 * grade fixe le salaire, les postes qu'un cadre peut tenir et la force de ce qu'il y fait.
 *
 * @param label  le nom du grade
 * @param salary le salaire de base, en $ par jour (le talent le module)
 * @param days   les jours de service a ce grade avant de pouvoir monter au suivant
 */
public enum Grade {
    RECRUE("Recrue", 150L, 0),
    MEMBRE("Membre", 300L, 1),
    LIEUTENANT("Lieutenant", 600L, 2),
    CAPITAINE("Capitaine", 1_200L, 3),
    CADRE("Haut cadre", 2_500L, 4);

    private final String label;
    private final long salary;
    private final int days;

    Grade(String label, long salary, int days) {
        this.label = label;
        this.salary = salary;
        this.days = days;
    }

    public String label() {
        return label;
    }

    public long salary() {
        return salary;
    }

    /** Les jours a servir a ce grade avant la promotion. */
    public int days() {
        return days;
    }

    /** Le grade au-dessus, ou {@code null} au sommet. */
    public Grade next() {
        int next = ordinal() + 1;
        return next < values().length ? values()[next] : null;
    }

    public static Grade of(int index) {
        Grade[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, index))];
    }
}
