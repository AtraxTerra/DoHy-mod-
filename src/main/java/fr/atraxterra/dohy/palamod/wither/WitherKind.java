package fr.atraxterra.dohy.palamod.wither;

import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/**
 * Les withers du pillage (module PalaWither, wiki « Réceptacle à Wither »).
 *
 * <p>Chacun s'invoque au receptacle avec sa gemme, apres un temps
 * d'invocation propre. Sur Paladium, un wither sert a percer la base d'une
 * faction ennemie ; seul en jeu, il n'y a pas de faction ennemie, donc deux
 * familles :
 * <ul>
 *   <li><b>les withers a combattre</b> — de base, amusant, baby : le wither
 *       vanilla et ses variantes, hostiles a tout ce qui vit, toi compris ;</li>
 *   <li><b>les withers allies</b> — passif, assoiffe, predateur, supreme :
 *       la ou Paladium les disait « passifs » ou « non agressifs envers sa
 *       faction », ils n'attaquent ici ni toi ni les joueurs, seulement les
 *       monstres, et cassent ce qu'ils touchent. Des engins de siege.</li>
 * </ul>
 *
 * @param summonTicks  temps d'invocation au receptacle (wiki : 5 s a 20 min)
 * @param tint         teinte du rendu sur la texture vanilla (0 : aucune)
 * @param allied       n'attaque que les monstres
 * @param pinned       ne se deplace pas seul (assoiffe : il faut le pousser ;
 *                     predateur, supreme : au laser de ciblage)
 * @param alchemist    niveau d'Alchimiste (sur 100) pour utiliser la gemme
 */
public enum WitherKind {
    BASE("paladium_wither", "wither_gem", 10 * 20, 300.0d, 1.0d, 0, false, false, 0),
    FUNNY("funny_wither", "funny_wither_gem", 5 * 20, 300.0d, 1.0d, 0, false, false, 0),
    BABY("baby_wither", "baby_wither_gem", 5 * 20, 150.0d, 0.5d, 0xFFE8D8FF, false, false, 0),
    PASSIVE("passive_wither", "passive_wither_gem", 10 * 20, 300.0d, 1.0d, 0xFFC8F2D0, true, false, 0),
    THIRSTY("thirsty_wither", "thirsty_wither_gem", 5 * 60 * 20, 450.0d, 1.1d, 0xFF8FC2FF, true, true, 70),
    PREDATOR("predator_wither", "predator_wither_gem", 10 * 60 * 20, 600.0d, 1.2d, 0xFFFF8A7A, true, true, 90),
    SUPREME("supreme_wither", "supreme_wither_gem", 20 * 60 * 20, 1000.0d, 1.4d, 0xFFFFD45A, true, true, 100);

    private final String id;
    private final String gem;
    private final int summonTicks;
    private final double maxHealth;
    private final double scale;
    private final int tint;
    private final boolean allied;
    private final boolean pinned;
    private final int alchemist;

    WitherKind(String id, String gem, int summonTicks, double maxHealth, double scale, int tint, boolean allied,
               boolean pinned, int alchemist) {
        this.id = id;
        this.gem = gem;
        this.summonTicks = summonTicks;
        this.maxHealth = maxHealth;
        this.scale = scale;
        this.tint = tint;
        this.allied = allied;
        this.pinned = pinned;
        this.alchemist = alchemist;
    }

    public String id() {
        return id;
    }

    public String gem() {
        return gem;
    }

    public int summonTicks() {
        return summonTicks;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public double scale() {
        return scale;
    }

    public int tint() {
        return tint;
    }

    public boolean allied() {
        return allied;
    }

    public boolean pinned() {
        return pinned;
    }

    public int alchemist() {
        return alchemist;
    }

    /** Le laser de ciblage le guide : predateur et supreme. */
    public boolean directed() {
        return this == PREDATOR || this == SUPREME;
    }

    /** Selle et canne a peche : ni l'assoiffe, ni le predateur, ni le supreme. */
    public boolean saddleable() {
        return !pinned;
    }

    /** La fleche de mi-vie : ni le predateur, ni le supreme. */
    public boolean halvable() {
        return !directed();
    }

    /** La cle de l'arbre de faction qui debloque son invocation. */
    public String unlock() {
        return this == BASE ? "use:wither" : "use:" + id;
    }

    /** Le compteur de l'arbre alimente par chaque invocation. */
    public String counter() {
        return this == BASE ? "spawn:wither" : "spawn:" + id;
    }

    public Supplier<? extends EntityType<PalaWither>> type() {
        return PalaWithers.type(this);
    }

    public static WitherKind of(EntityType<?> type) {
        for (WitherKind kind : values()) {
            if (PalaWithers.type(kind).get() == type) {
                return kind;
            }
        }
        return BASE;
    }
}
