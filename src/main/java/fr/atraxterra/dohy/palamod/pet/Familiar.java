package fr.atraxterra.dohy.palamod.pet;

import java.util.Locale;

/**
 * Les trois familiers qu'une cage peut attirer.
 *
 * <p>Paladium en a trois aussi, mais leurs modeles et leurs noms ne sont pas dans
 * le jar de PalaMod : ceux-ci sont des creatures propres au mod, dessinees pour
 * lui. Comme dans l'original, l'apparence ne change rien aux competences ; les
 * terrestres s'assoient sur l'epaule droite, le volant flotte a cote.
 */
public enum Familiar {

    /** Un petit lynx gris cendre, aux pointes d'oreilles et de queue couleur braise. */
    EMBER_LYNX("Pyrolynx", false),
    /** Un chiot brun et creme, foulard rouge au cou. */
    PUP("Mordillon", false),
    /** Une flammeche bleue a petite frimousse, qui flotte en ondulant. */
    WISP("Feu-follet", true);

    private final String label;
    private final boolean flying;

    Familiar(String label, boolean flying) {
        this.label = label;
        this.flying = flying;
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

    public static Familiar byId(String id) {
        for (Familiar familiar : values()) {
            if (familiar.id().equals(id)) {
                return familiar;
            }
        }
        return null;
    }
}
