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
    SKILL_LEVELS,
    UNKNOWN;

    public static ContractType byName(String raw) {
        try {
            return valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException error) {
            return UNKNOWN;
        }
    }
}
