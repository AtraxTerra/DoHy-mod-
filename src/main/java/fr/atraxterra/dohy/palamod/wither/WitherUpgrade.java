package fr.atraxterra.dohy.palamod.wither;

/**
 * Les ameliorations de wither (wiki : une par wither, une heure chacune).
 *
 * <ul>
 *   <li><b>Discretion</b> ({@code FURTIVE_WITHER_UPGRADE}) : plus de barre de
 *       vie, plus un bruit ;</li>
 *   <li><b>Resistance</b> : les degats qui ne viennent pas d'un joueur sont
 *       divises par deux ;</li>
 *   <li><b>Visqueuse</b> ({@code SLIMY_WITHER_UPGRADE}) : les blocs a base de
 *       slime ne l'arretent plus — il les casse sans les declencher, et chacun
 *       lui rend 20 points de vie.</li>
 * </ul>
 * Ni discretion ni resistance sur le wither supreme, qu'on ne tue pas.
 */
public enum WitherUpgrade {
    STEALTH("stealth_upgrade"),
    RESISTANCE("wither_resistance_upgrade"),
    SLIME("wither_slime_upgrade");

    /** Une heure de jeu. */
    public static final int DURATION = 60 * 60 * 20;
    /** Ce que rend chaque bloc de slime casse par un wither visqueux. */
    public static final float SLIME_HEAL = 20.0f;

    private final String id;

    WitherUpgrade(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean fits(WitherKind kind) {
        return kind != WitherKind.SUPREME || this == SLIME;
    }

    public static WitherUpgrade byId(String id) {
        for (WitherUpgrade upgrade : values()) {
            if (upgrade.id.equals(id)) {
                return upgrade;
            }
        }
        return null;
    }
}
