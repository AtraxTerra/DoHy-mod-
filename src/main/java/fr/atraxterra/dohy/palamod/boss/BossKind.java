package fr.atraxterra.dohy.palamod.boss;

import net.minecraft.world.BossEvent;

/**
 * Les cinq boss de l'event Boss de Paladium (wiki « Java | Boss »).
 *
 * <p>Taille et vitesse viennent des fichiers {@code assets/palamod/boss/*.json}
 * et des classes {@code Entity*} du PalaMod : la boite de collision d'origine
 * ({@code getEntityWidth/Height}) multipliee par {@code sizeMultiplier}, qui
 * agrandit aussi le rendu. Beep Boop et Azhur sont donc des geants de onze et
 * dix blocs.
 *
 * <p><b>Solo.</b> Sur Paladium, 6 500 points de vie se partagent entre des
 * dizaines de joueurs en armure de paladium. Ici :
 * <ul>
 *   <li>1 000 points de vie ({@link EventBoss#SOLO_HEALTH}) ;</li>
 *   <li>les degats des attaques sont ceux du wiki divises par quatre
 *       ({@link EventBoss#DAMAGE_SCALE}) : la charge de Tobalt, 100 sur le
 *       wiki, fait 25 avant armure ;</li>
 *   <li>la vitesse du json multipliee par 0,7 : les valeurs de 1.7.10 sont
 *       celles d'un zombie (0,23) poussees a 0,3–0,5, trop vives pour des
 *       colosses dans une arene.</li>
 * </ul>
 */
public enum BossKind {

    BEEP_BOOP("beep_boop", "Beep Boop", 2.0f, 3.7f, 3.0f, 0.4d, 10.0d, 80, BossEvent.BossBarColor.WHITE,
            "Robot minier cyclope, venu d'une casse"),
    TOBALT("tobalt", "Tobalt", 5.5f, 4.2f, 1.0f, 0.3d, 12.0d, 100, BossEvent.BossBarColor.RED,
            "Tortue geante, carapace de volcan"),
    AZHUR("azhur", "Azhur", 2.0f, 5.0f, 2.0f, 0.3d, 16.0d, 80, BossEvent.BossBarColor.YELLOW,
            "Djinn de sable qui flotte"),
    MORSULA("morsula", "Morsula", 2.0f, 3.7f, 1.0f, 0.25d, 10.0d, 80, BossEvent.BossBarColor.BLUE,
            "Morse bleu aux pics de glace"),
    ARACHNA("arachna", "Arachna", 4.0f, 3.0f, 1.0f, 0.5d, 15.0d, 100, BossEvent.BossBarColor.GREEN,
            "Araignee radioactive");

    private final String id;
    private final String label;
    private final float width;
    private final float height;
    private final float scale;
    private final double speed;
    private final double attackDamage;
    private final int cooldown;
    private final BossEvent.BossBarColor color;
    private final String summary;

    BossKind(String id, String label, float width, float height, float scale, double speed, double attackDamage,
             int cooldown, BossEvent.BossBarColor color, String summary) {
        this.id = id;
        this.label = label;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.speed = speed;
        this.attackDamage = attackDamage;
        this.cooldown = cooldown;
        this.color = color;
        this.summary = summary;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Largeur de la boite de collision, en blocs, deja multipliee par {@link #scale()}. */
    public float width() {
        return width * scale;
    }

    public float height() {
        return height * scale;
    }

    /** Le {@code sizeMultiplier} du json : l'echelle du rendu. */
    public float scale() {
        return scale;
    }

    public double speed() {
        return speed * 0.7d;
    }

    /** Le coup de poing ordinaire : {@code attackDamage} du json, aux trois quarts. */
    public double attackDamage() {
        return attackDamage * 0.75d;
    }

    /** Ticks entre deux attaques speciales : 4 ou 5 s, comme le wiki. */
    public int cooldown() {
        return cooldown;
    }

    public BossEvent.BossBarColor color() {
        return color;
    }

    public String summary() {
        return summary;
    }

    /** Les epines du wiki : tous sauf Tobalt, a 50 % puis 25 % de vie. */
    public boolean thornsBelowHalf() {
        return this != TOBALT;
    }

    public static BossKind byId(String id) {
        for (BossKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
