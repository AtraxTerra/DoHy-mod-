package fr.atraxterra.dohy.palamod.machine;

import net.minecraft.util.StringRepresentable;

/**
 * Les quatre paliers du cobblebreaker.
 *
 * <p>Les cadences viennent de {@code CobbleBreakerUpgrade}, lues au bytecode :
 * 400, 266, 160 et 100 ticks. Les multiplicateurs annonces par le wiki
 * (x1,5 / x2,5 / x4) tombent <b>exactement</b> juste a partir de ces nombres,
 * ce qui les confirme l'un l'autre.
 *
 * <p>C'est un {@link StringRepresentable} parce que le palier est aussi une
 * propriete de l'etat du bloc : la machine change d'apparence quand on
 * l'ameliore, et le joueur voit donc son palier sans ouvrir l'interface. C'est
 * ce qui compense l'absence de barre de progression — voir
 * {@link MachineBlockEntity}.
 */
public enum CobblebreakerTier implements StringRepresentable {

    DEFAULT("default", 400),
    AMETHYST("amethyst", 266),
    TITANE("titane", 160),
    PALADIUM("paladium", 100);

    private final String key;
    private final int workTicks;

    CobblebreakerTier(String key, int workTicks) {
        this.key = key;
        this.workTicks = workTicks;
    }

    public String key() {
        return key;
    }

    /** Ticks necessaires a un cycle complet. */
    public int workTicks() {
        return workTicks;
    }

    /** Cadence par rapport au palier de base, pour l'affichage. */
    public double speedFactor() {
        return (double) DEFAULT.workTicks / workTicks;
    }

    @Override
    public String getSerializedName() {
        return key;
    }

    /** Repli sur le palier de base : une sauvegarde abimee ne doit rien casser. */
    public static CobblebreakerTier byKey(String candidate) {
        for (CobblebreakerTier tier : values()) {
            if (tier.key.equals(candidate)) {
                return tier;
            }
        }
        return DEFAULT;
    }
}
