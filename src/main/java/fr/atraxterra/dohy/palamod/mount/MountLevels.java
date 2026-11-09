package fr.atraxterra.dohy.palamod.mount;

/**
 * Niveaux, PV et cases de la monture ({@code MountUtils}).
 *
 * <p>Le seuil du niveau L est une somme de {@code i^2,5} tronquee pas a pas :
 * {@code acc = (int)(acc + i^2,5)} pour i = 1, 2, ... ; on est au niveau L
 * tant que l'XP reste sous la somme jusqu'a L. Le plafond, lui, est la meme
 * somme <b>a partir de i = 2</b> ({@code getNeededXpForLvl}) jusqu'a 100 :
 * 2 907 301 XP. Les PV et les cases suivent le niveau en proportion de ceux
 * de l'espece au niveau 100 (le Dancarok : 400 PV, 36 cases).
 */
public final class MountLevels {

    public static final int MAX_LEVEL = 100;
    /** {@code CommandMount} : {@code >= 10}, pas {@code > 10} comme le dit le wiki. */
    public static final int SUMMON_LEVEL = 10;
    /** {@code DancarokMount.hp} : PV au niveau 100, soit 4 par niveau. */
    public static final int HEALTH_AT_MAX = 400;
    /** {@code DancarokMount.nbSlots} : cases au niveau 100. */
    public static final int SLOTS_AT_MAX = 36;

    /** {@code SUM[i]} : XP a atteindre pour quitter le niveau i. */
    private static final double[] SUM = new double[MAX_LEVEL + 2];
    /** {@code NEEDED[i]} : {@code getNeededXpForLvl(i)}, somme de 2 a i. */
    private static final double[] NEEDED = new double[MAX_LEVEL + 2];

    static {
        int acc = 0;
        for (int i = 1; i <= MAX_LEVEL + 1; i++) {
            acc = (int) (acc + Math.pow(i, 2.5));
            SUM[i] = acc;
        }
        acc = 0;
        for (int i = 2; i <= MAX_LEVEL + 1; i++) {
            acc = (int) (acc + Math.pow(i, 2.5));
            NEEDED[i] = acc;
        }
    }

    private MountLevels() {
    }

    public static int level(double xp) {
        for (int i = 1; i <= MAX_LEVEL; i++) {
            if (xp < SUM[i]) {
                return i;
            }
        }
        return MAX_LEVEL;
    }

    /** L'XP la plus basse du niveau. */
    public static float minXp(int level) {
        return level <= 1 ? 0.0f : (float) SUM[Math.min(MAX_LEVEL, level) - 1];
    }

    /** {@code incrementXP} : au niveau 100, l'XP est ramenee au plafond. */
    public static float cap(float xp) {
        return level(xp) >= MAX_LEVEL ? (float) NEEDED[MAX_LEVEL] : xp;
    }

    /** Ce que la barre d'XP du menu montre comme maximum : {@code needed(L + 1)}. */
    public static double nextThreshold(int level) {
        return NEEDED[Math.min(MAX_LEVEL, level + 1)];
    }

    /** {@code getHPByXp} : {@code hp x niveau / 100}, en demi-coeurs. */
    public static float maxHealth(int level) {
        return HEALTH_AT_MAX * level / 100.0f;
    }

    /** {@code getNbSlotsByXp} : division entiere, au moins une case. */
    public static int slots(int level) {
        return Math.max(1, SLOTS_AT_MAX * level / 100);
    }
}
