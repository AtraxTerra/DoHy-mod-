package fr.atraxterra.dohy.faction.base;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Les trois tailles de base ennemie (lot 22g, refonte du 2026-11-11). La
 * taille fixe l'enceinte ; le genre ({@link EnemyBaseStyle}) fixe ce qu'il y a
 * dedans. De l'exterieur vers le centre :
 * <ul>
 *   <li><b>avant-poste</b> (61 x 61) : une enceinte a trois couches, quatre
 *       miradors, sept quartiers, une salle forte a trois couches ;</li>
 *   <li><b>base</b> (93 x 93) : enceinte a quatre couches et sept miradors,
 *       puis une <b>seconde enceinte</b> autour de la salle forte a cinq
 *       couches ; douze quartiers ;</li>
 *   <li><b>forteresse</b> (129 x 129) : enceinte a six couches dont deux
 *       d'eau, onze miradors, seconde enceinte a quatre couches, fosse de lave,
 *       salle forte a sept couches ; seize quartiers.</li>
 * </ul>
 * Avec NTM NEXT, chaque taille a ses tourelles (de 4 a 15, plus celles des
 * casemates), un champ de mines dans le glacis et du barbele.
 *
 * @param half         demi-cote de l'emprise, en blocs (douve comprise)
 * @param wall         rayon de la face exterieure de l'enceinte
 * @param claimRadius  rayon des claims, en chunks, autour du chunk central :
 *                     l'emprise plus deux chunks de glacis, ou rien ne se pose —
 *                     la TNT arrive en dynamite, au canon ou par wither
 * @param clearHeight  hauteur degagee au-dessus du sol de la base
 * @param elo          ELO gagne en la pillant
 * @param money        argent verse a la banque de faction
 * @param summary      la taille et l'allure, en une ligne
 * @param contents     ce qui la defend, en une ligne
 * @param tools        ce qu'il faudra pour la piller
 */
public enum EnemyBaseTier {
    OUTPOST(1, "avant_poste", "Avant-poste", 30, 23, 4, 18, 150L, 40_000L, Items.STONE_BRICKS,
            "61 x 61 : fosse, enceinte a 3 couches, 4 miradors",
            "7 quartiers, salle forte a 3 couches, 16 gardes, tourelles et mines NTM",
            "TNT, seau extrapole, TNT en endium"),
    BASE(2, "base", "Base", 46, 38, 5, 26, 500L, 200_000L, Items.OBSIDIAN,
            "93 x 93 : deux enceintes, 7 miradors, barbeles",
            "12 quartiers, salle forte a 5 couches, 30 gardes, tourelles et mines NTM",
            "Seau extrapole, TNT en endium, withers"),
    FORTRESS(3, "forteresse", "Forteresse", 64, 54, 6, 36, 1_500L, 800_000L, Items.CRYING_OBSIDIAN,
            "129 x 129 : deux enceintes, 11 miradors, lave",
            "16 quartiers, salle forte a 7 couches, 50 gardes, tourelles et mines NTM",
            "Withers ameliores, TNT en endium, seau extrapole");

    private final int level;
    private final String key;
    private final String label;
    private final int half;
    private final int wall;
    private final int claimRadius;
    private final int clearHeight;
    private final long elo;
    private final long money;
    private final Item icon;
    private final String summary;
    private final String contents;
    private final String tools;

    EnemyBaseTier(int level, String key, String label, int half, int wall, int claimRadius, int clearHeight, long elo,
                  long money, Item icon, String summary, String contents, String tools) {
        this.level = level;
        this.key = key;
        this.label = label;
        this.half = half;
        this.wall = wall;
        this.claimRadius = claimRadius;
        this.clearHeight = clearHeight;
        this.elo = elo;
        this.money = money;
        this.icon = icon;
        this.summary = summary;
        this.contents = contents;
        this.tools = tools;
    }

    public static EnemyBaseTier byLevel(int level) {
        for (EnemyBaseTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return OUTPOST;
    }

    public int level() {
        return level;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int half() {
        return half;
    }

    public int wall() {
        return wall;
    }

    public int claimRadius() {
        return claimRadius;
    }

    public int clearHeight() {
        return clearHeight;
    }

    public long elo() {
        return elo;
    }

    public long money() {
        return money;
    }

    public Item icon() {
        return icon;
    }

    public String summary() {
        return summary;
    }

    public String contents() {
        return contents;
    }

    public String tools() {
        return tools;
    }

    /** La table de butin des coffres : {@code palamod:chests/enemy_<cle>}. */
    public String lootTable() {
        return "chests/enemy_" + key;
    }
}
