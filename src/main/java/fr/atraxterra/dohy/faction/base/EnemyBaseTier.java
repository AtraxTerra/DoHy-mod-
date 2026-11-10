package fr.atraxterra.dohy.faction.base;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Les trois tailles de base ennemie (lot 22g). Toutes sont des places fortes
 * a la Paladium — douve, enceinte a couches avec de l'eau prise dans les murs,
 * tours, porterie, donjon a couches sur ses six faces —, de plus en plus
 * grandes et epaisses :
 * <ul>
 *   <li><b>avant-poste</b> (41 x 41) : pierre et eau, un donjon d'obsidienne ;</li>
 *   <li><b>base</b> (65 x 65) : obsidienne, obsidienne renforcee et piegee,
 *       spikes, deux casernes a spawner, une balise sur le donjon ;</li>
 *   <li><b>forteresse</b> (97 x 97) : six couches d'enceinte dont deux d'eau,
 *       douve de Fake Water, tranchee de lave, quatre casernes, donjon a sept
 *       couches (enclumes en paladium, obsidiennes a wither et anti-aggro).</li>
 * </ul>
 *
 * @param half         demi-cote de l'emprise, en blocs (tours comprises)
 * @param claimRadius  rayon des claims, en chunks, autour du chunk central :
 *                     l'emprise plus deux chunks de glacis, ou rien ne se pose —
 *                     la TNT arrive en dynamite, au canon ou par wither
 * @param clearHeight  hauteur degagee au-dessus du sol de la base
 * @param elo          ELO gagne en la pillant
 * @param money        argent verse a la banque de faction
 * @param summary      la taille et l'allure, en une ligne
 * @param contents     ce qu'elle cache, en une ligne
 * @param tools        ce qu'il faudra pour la piller
 */
public enum EnemyBaseTier {
    OUTPOST(1, "avant_poste", "Avant-poste", 20, 3, 14, 150L, 25_000L, Items.STONE_BRICKS,
            "41 x 41 : douve, enceinte, 4 tours", "Murs a eau, donjon d'obsidienne, 4 coffres, 10 gardes",
            "TNT, seau extrapole, TNT en endium"),
    BASE(2, "base", "Base", 32, 4, 20, 500L, 120_000L, Items.OBSIDIAN,
            "65 x 65 : obsidienne, spikes, casernes", "Donjon a 5 couches, balise, 6 coffres, 2 spawners, 18 gardes",
            "Seau extrapole, TNT en endium, withers"),
    FORTRESS(3, "forteresse", "Forteresse", 48, 5, 28, 1_500L, 500_000L, Items.CRYING_OBSIDIAN,
            "97 x 97 : place forte, lave, 4 casernes", "Enceinte et donjon a 6-7 couches, enclumes, 11 coffres, 30 gardes",
            "Withers ameliores, TNT en endium, seau extrapole");

    private final int level;
    private final String key;
    private final String label;
    private final int half;
    private final int claimRadius;
    private final int clearHeight;
    private final long elo;
    private final long money;
    private final Item icon;
    private final String summary;
    private final String contents;
    private final String tools;

    EnemyBaseTier(int level, String key, String label, int half, int claimRadius, int clearHeight, long elo, long money,
                  Item icon, String summary, String contents, String tools) {
        this.level = level;
        this.key = key;
        this.label = label;
        this.half = half;
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
