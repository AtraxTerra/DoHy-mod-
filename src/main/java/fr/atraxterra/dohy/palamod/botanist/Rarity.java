package fr.atraxterra.dohy.palamod.botanist;

/**
 * La rarete d'une plante du laboratoire ({@code BotanistMutationRarity} de JobsReborn :
 * commune, rare, epique, legendaire).
 *
 * <p>Dans JobsReborn, le niveau, la chance de muter et l'XP de decouverte sont dans la
 * configuration de chaque mutation ; ici ils suivent la rarete (voir {@code docs/BALANCE.md}) :
 * <ul>
 *   <li>le niveau de Fermier pour la planter, et pour qu'elle sorte d'une mutation ;</li>
 *   <li>la chance qu'une floraison de cette plante mute vers la rarete suivante ;</li>
 *   <li>l'XP de Fermier de sa decouverte au scanner ;</li>
 *   <li>les nibblers que reclame un besoin.</li>
 * </ul>
 */
public enum Rarity {
    COMMON("common", "Commune", "Communes", 10, 0.08d, 150, 0, 0xFFB4B4C0),
    RARE("rare", "Rare", "Rares", 25, 0.05d, 300, 1, 0xFF3F9BFF),
    EPIC("epic", "Epique", "Epiques", 45, 0.03d, 800, 2, 0xFFB45CF0),
    LEGENDARY("legendary", "Legendaire", "Legendaires", 70, 0.0d, 2000, 3, 0xFFF5A623);

    private final String key;
    private final String label;
    private final String plural;
    private final int level;
    private final double chance;
    private final int discoveryXp;
    private final int nibblers;
    private final int color;

    Rarity(String key, String label, String plural, int level, double chance, int discoveryXp, int nibblers,
           int color) {
        this.key = key;
        this.label = label;
        this.plural = plural;
        this.level = level;
        this.chance = chance;
        this.discoveryXp = discoveryXp;
        this.nibblers = nibblers;
        this.color = color;
    }

    /** La cle des textes ({@code tooltip.palamod.botanist.rarity.<key>}). */
    public String key() {
        return key;
    }

    /** « Commune », sans accent (police du jeu). */
    public String label() {
        return label;
    }

    /** « Communes » : la categorie du codex. */
    public String plural() {
        return plural;
    }

    /** Le niveau de Fermier pour la planter, et pour qu'elle sorte d'une mutation. */
    public int level() {
        return level;
    }

    /** La chance qu'une floraison mute (0 : la plante est au bout de sa lignee). */
    public double chance() {
        return chance;
    }

    /** L'XP de Fermier de sa decouverte au scanner. */
    public int discoveryXp() {
        return discoveryXp;
    }

    /** Les nibblers que reclame un besoin de cette plante (0 : elle n'en reclame pas). */
    public int nibblers() {
        return nibblers;
    }

    /** La couleur de la rarete dans le codex et les bulles (ARGB). */
    public int color() {
        return color;
    }
}
