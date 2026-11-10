package fr.atraxterra.dohy.faction.base;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Les trois tailles de base ennemie (lot 22g), du plus facile au plus dur a
 * piller. Chacune demande les outils d'un moment de l'arbre de pillage :
 * <ul>
 *   <li><b>avant-poste</b> : un bunker de pierre, la TNT et la dynamite
 *       suffisent ;</li>
 *   <li><b>base</b> : une enceinte et un bunker d'obsidienne, une douve de
 *       Fake Water — il faut la TNT en endium ou un wither ;</li>
 *   <li><b>forteresse</b> : quatre tours, un champ de spikes, une double douve,
 *       un bunker a trois couches (obsidienne renforcee, obsidienne a wither,
 *       obsidienne piegee).</li>
 * </ul>
 *
 * @param half         demi-cote de l'emprise, en blocs (tours comprises)
 * @param claimRadius  rayon des claims, en chunks, autour du chunk central
 * @param clearHeight  hauteur degagee au-dessus du sol de la base
 * @param elo          ELO gagne en la pillant
 * @param money        argent verse a la banque de faction
 */
public enum EnemyBaseTier {
    OUTPOST(1, "avant_poste", "Avant-poste", 6, 0, 9, 100L, 15_000L, Items.COBBLESTONE,
            "Bunker de pierre, 2 coffres, 3 gardes", "TNT ou dynamite"),
    BASE(2, "base", "Base", 12, 1, 11, 300L, 75_000L, Items.OBSIDIAN,
            "Enceinte d'obsidienne, douve, 4 coffres, 6 gardes", "TNT en endium ou wither"),
    FORTRESS(3, "forteresse", "Forteresse", 22, 2, 16, 1_000L, 300_000L, Items.CRYING_OBSIDIAN,
            "Tours, spikes, bunker a 3 couches, 7 coffres, 14 gardes", "Withers et TNT en endium");

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
    private final String tools;

    EnemyBaseTier(int level, String key, String label, int half, int claimRadius, int clearHeight, long elo, long money,
                  Item icon, String summary, String tools) {
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

    public String tools() {
        return tools;
    }

    /** La table de butin des coffres : {@code palamod:chests/enemy_<cle>}. */
    public String lootTable() {
        return "chests/enemy_" + key;
    }
}
