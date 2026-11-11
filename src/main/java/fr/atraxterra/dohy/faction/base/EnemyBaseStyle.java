package fr.atraxterra.dohy.faction.base;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Le genre d'une base ennemie : ce qu'elle abrite entre ses murs, et la forme
 * de sa salle forte. Les tailles ({@link EnemyBaseTier}) fixent l'enceinte ;
 * le genre fixe les quartiers et la facon dont on atteint les coffres.
 *
 * @param label    son nom, avec son article (« une citadelle »)
 * @param summary  ce qu'on y voit, en une ligne
 * @param vault    ou sont les coffres, en une ligne
 */
public enum EnemyBaseStyle {
    /** La place forte d'avant la refonte, en plus grand : casernes, spikes, armurerie. */
    CITADEL("citadelle", "Citadelle", "une citadelle", Items.CRYING_OBSIDIAN,
            "Casernes, cours a spikes, armurerie, poudriere",
            "Donjon a couches au centre, les coffres au niveau du sol"),
    /** Une base de farm : champs sous totem, serres, silos, etable. */
    FARM("ferme", "Domaine agricole", "un domaine agricole", Items.WHEAT,
            "Champs des quatre cultures, totems, serres, silos",
            "Cave a couches sous la grande grange : il faut creuser"),
    /** Une base de stockage : des halles de coffres, et un coffre-fort a cellules. */
    STORAGE("entrepot", "Entrepot", "un entrepot", Items.CHEST,
            "Halles de coffres, silos, reserve d'explosifs",
            "Bunker a neuf cellules : seule celle du milieu compte"),
    /** Une base de machines : cobblebreakers, crushers, grinder, boite a golem, forge. */
    FACTORY("usine", "Usine", "une usine", Items.BLAST_FURNACE,
            "Cobblebreakers, crushers, grinder, boite a golem, forge",
            "Bunker enterre sous la centrale"),
    /** Une demonstration de force : pyramides a balise, trophees, autel a wither. */
    SHOWCASE("vitrine", "Demonstration de force", "une demonstration de force", Items.BEACON,
            "Pyramides a balise, galerie de trophees, autel a wither",
            "Salle forte en haut d'une ziggourat");

    private final String key;
    private final String title;
    private final String label;
    private final Item icon;
    private final String summary;
    private final String vault;

    EnemyBaseStyle(String key, String title, String label, Item icon, String summary, String vault) {
        this.key = key;
        this.title = title;
        this.label = label;
        this.icon = icon;
        this.summary = summary;
        this.vault = vault;
    }

    public static EnemyBaseStyle byKey(String key) {
        for (EnemyBaseStyle style : values()) {
            if (style.key.equals(key)) {
                return style;
            }
        }
        return null;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public String label() {
        return label;
    }

    public Item icon() {
        return icon;
    }

    public String summary() {
        return summary;
    }

    public String vault() {
        return vault;
    }
}
