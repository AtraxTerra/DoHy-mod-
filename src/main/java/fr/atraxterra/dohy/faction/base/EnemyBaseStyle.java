package fr.atraxterra.dohy.faction.base;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Le genre d'une base ennemie (refonte militaire du 2026-11-13) : ce qu'elle
 * abrite entre ses murs, et la forme de sa salle forte. Les tailles
 * ({@link EnemyBaseTier}) fixent l'enceinte ; le genre fixe les quartiers, les
 * couleurs du beton et la facon dont on atteint les coffres. Avec NTM NEXT,
 * toutes ont des tourelles, des mines, des barbeles et des projecteurs.
 *
 * @param label    son nom, avec son article (« un silo de missiles »)
 * @param summary  ce qu'on y voit, en une ligne
 * @param vault    ou sont les coffres, en une ligne
 */
public enum EnemyBaseStyle {
    /** Des puits de lancement sous leurs trappes, du carburant, un poste de tir enterre. */
    SILO("silo", "Silo de missiles", "un silo de missiles", Items.FIREWORK_ROCKET,
            "Puits de lancement sous trappe, depot de carburant, poste de tir, groupe electrogene",
            "Poste de tir enterre sous la grande trappe du silo : il faut creuser"),
    /** Des casemates a tourelle, des tranchees, des sacs de sable et du barbele. */
    BUNKER("bunker", "Bunker", "un bunker", Items.IRON_DOOR,
            "Casemates a tourelle, tranchees, sacs de sable, barbeles, depot de munitions",
            "Poste de commandement enterre sous le blockhaus : il faut creuser"),
    /** Des miradors, des casernes, un garage de blindes, la place d'armes a l'etoile rouge. */
    SOVIET("sovietique", "Forteresse sovietique", "une forteresse sovietique", Items.BANNER.red(),
            "Miradors a projecteur, casernes, garage de blindes, place d'armes a l'etoile rouge",
            "Donjon de beton au centre, tourelles sur le toit, les coffres au niveau du sol"),
    /** Un grand radar, des radomes, un mat d'antennes, un heliport. */
    RADAR("radar", "Base radar", "une base radar", Items.COMPASS,
            "Grand radar, radomes, mat d'antennes, heliport, salle de controle",
            "Salle des operations sous le grand radar, au niveau du sol");

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

    /** Les cles des genres, pour l'aide de la commande : « silo, bunker, sovietique, radar ». */
    public static String keys() {
        StringBuilder out = new StringBuilder();
        for (EnemyBaseStyle style : values()) {
            out.append(out.isEmpty() ? "" : ", ").append(style.key);
        }
        return out.toString();
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
