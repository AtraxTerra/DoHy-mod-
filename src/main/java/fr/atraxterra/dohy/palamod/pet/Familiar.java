package fr.atraxterra.dohy.palamod.pet;

import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * Les familiers qu'une cage peut attirer : les vingt et un de Paladium, avec
 * leurs modeles, leurs animations et leurs textures du jar PalaPet 7.12.0
 * (repris par {@code tools/palapet_assets.py}, credit dans le README).
 *
 * <p>Comme dans l'original, l'apparence ne change rien aux competences. Le jar
 * ne donne ni leurs noms (ils viennent du serveur de Paladium) ni leur rarete :
 * les noms sont traduits de leur identifiant, et le tirage est pondere, les
 * familiers d'evenement plus rares que les animaux de tous les jours.
 *
 * <p>Les trois premiers familiers du mod (Pyrolynx, Mordillon, Feu-follet) se
 * lisent encore dans les sauvegardes : ils deviennent le renard patronus, le
 * chien et la fee de l'hiver ({@link #byId}).
 */
public enum Familiar {

    DOG("Chien", false, 10),
    CAT("Chat", false, 10),
    RABBIT("Lapin", false, 10),
    PENGUIN("Pingouin", false, 10),
    CHAMELEON("Cameleon", false, 10),
    BLOBFISH("Poisson-blob", false, 10),
    MINI_GOLEM("Mini-golem", false, 10),
    DRAGON("Dragon", false, 5),
    KAPIO_KOI("Carpe koi", true, 5),
    ARTY("Arty", false, 5),
    ZOMBIE_HAND("Main de zombie", false, 5),
    NIMBRIA_HAND("Main de Nimbria", false, 5),
    REINDEER("Renne", false, 5),
    FOX_PATRONUS("Renard patronus", false, 2),
    UFO("OVNI", true, 2),
    SAHARIA_BAT("Chauve-souris de Saharia", true, 2),
    TALIKUS_MOSQUITO("Moustique de Talikus", true, 2),
    FENG_UANG("Feng Uang", true, 2),
    WINTER_FAIRY("Fee de l'hiver", true, 2),
    BUBBLE_WITHER("Wither bulle", true, 2),
    ENDER_DRAGON("Dragon de l'End", false, 1);

    private final String label;
    private final boolean flying;
    private final int weight;

    Familiar(String label, boolean flying, int weight) {
        this.label = label;
        this.flying = flying;
        this.weight = weight;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }

    /** Un volant flotte pres de l'epaule au lieu de s'y asseoir. */
    public boolean flying() {
        return flying;
    }

    /** Son poids au tirage de la cage. */
    public int weight() {
        return weight;
    }

    /** Un familier tire au sort, selon les poids. */
    public static Familiar pick(RandomSource random) {
        int total = 0;
        for (Familiar familiar : values()) {
            total += familiar.weight;
        }
        int roll = random.nextInt(total);
        for (Familiar familiar : values()) {
            roll -= familiar.weight;
            if (roll < 0) {
                return familiar;
            }
        }
        return DOG;
    }

    public static Familiar byId(String id) {
        if (id == null) {
            return null;
        }
        for (Familiar familiar : values()) {
            if (familiar.id().equals(id)) {
                return familiar;
            }
        }
        // Les familiers propres au mod avant PalaPet.
        return switch (id) {
            case "ember_lynx" -> FOX_PATRONUS;
            case "pup" -> DOG;
            case "wisp" -> WINTER_FAIRY;
            default -> null;
        };
    }
}
