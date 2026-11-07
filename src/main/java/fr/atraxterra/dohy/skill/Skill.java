package fr.atraxterra.dohy.skill;

/**
 * Les huit skills.
 *
 * <p>La cle correspond a celle de {@code skills.json}. Un skill inconnu du
 * fichier n'a simplement aucune source d'XP : il reste a zero plutot que de
 * faire echouer le chargement.
 */
public enum Skill {

    MINING("mining", "Minage"),
    COMBAT("combat", "Combat"),
    FARMING("farming", "Agriculture"),
    FORAGING("foraging", "Bucheronnage"),
    FISHING("fishing", "Peche"),
    SMITHING("smithing", "Forge"),
    BUILDING("building", "Construction"),
    NUCLEAR("nuclear", "Nucleaire"),

    /**
     * Alchimie.
     *
     * <p>Ajoute par le chapitre des metiers : les trois autres metiers de
     * Paladium tombaient deja sur un skill existant ({@code mining},
     * {@code combat}, {@code farming}), l'Alchimiste n'avait pas d'equivalent.
     * Le creer plutot que de detourner {@code smithing} evite qu'un seul skill
     * porte deux progressions sans rapport.
     */
    ALCHEMY("alchemy", "Alchimie");

    private final String key;
    private final String display;

    Skill(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public static Skill byKey(String key) {
        for (Skill skill : values()) {
            if (skill.key.equalsIgnoreCase(key)) {
                return skill;
            }
        }
        return null;
    }
}
