package fr.atraxterra.dohy.contract;

/**
 * Les objectifs qu'un contrat peut demander.
 *
 * <p>Un type inconnu du mod rend le contrat inerte plutot que de faire echouer
 * le chargement : ajouter un type dans {@code contracts.json} avant de l'avoir
 * implemente ne doit pas casser la partie.
 */
public enum ContractType {
    MINE_BLOCK,
    KILL_ENTITY,
    HARVEST,
    FISH,
    SMELT,
    SELL_VALUE,
    TRAVEL,
    PLACE_BLOCK,
    COLLECTION_TIER,
    /** Gagner des niveaux de metier, tous metiers confondus. */
    JOB_LEVELS,
    /** Produire a une machine : moudre une farine, cuire un pain. La cible est l'objet produit, ou son tag. */
    PRODUCE,
    /** Jouer : une unite par minute de jeu, serveur en marche. */
    PLAYTIME,
    UNKNOWN;

    public static ContractType byName(String raw) {
        // Ancien nom, d'avant le remplacement des skills par les metiers : les
        // contrats deja en cours le portent encore dans la sauvegarde.
        if ("SKILL_LEVELS".equalsIgnoreCase(raw)) {
            return JOB_LEVELS;
        }
        try {
            return valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException error) {
            return UNKNOWN;
        }
    }
}
