package fr.atraxterra.dohy.faction;

/**
 * Ce que la faction a debloque : nombre de claims, de homes, delai du home,
 * reduction des degats de raid, bonus d'XP de donjon.
 *
 * <p>Les valeurs de base valent pour une faction neuve ; l'arbre de technologie
 * (chapitre 22b) y ajoute ses recompenses palier par palier.
 *
 * @param claims       chunks revendicables
 * @param homes        F homes ; aucun avant le palier 5 de la branche Globale, comme sur Paladium
 * @param homeDelay    secondes immobile avant la teleportation au home
 * @param raidDamage   part des degats de raid encaissee par les blocs du claim (1 = tout)
 * @param dungeonXp    bonus d'XP de donjon (0,05 = +5 %)
 */
public record FactionPerks(int claims, int homes, int homeDelay, float raidDamage, float dungeonXp) {

    public static final int BASE_CLAIMS = 5;
    public static final int BASE_HOME_DELAY = 10;

    public static FactionPerks of(FactionData data) {
        return new FactionPerks(BASE_CLAIMS, 0, BASE_HOME_DELAY, 1.0f, 0.0f);
    }
}
