package fr.atraxterra.dohy.faction.enterprise;

import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthNations;
import net.minecraft.server.MinecraftServer;

/**
 * Les six grandes regions du monde (lot 24b) : chacune a son organisation criminelle. Un pays
 * appartient a la region de son coeur, d'apres sa latitude et sa longitude : un decoupage de
 * jeu, large, pas une carte politique.
 */
public enum Region {
    AMERIQUE_NORD("Amerique du Nord"),
    AMERIQUE_SUD("Amerique du Sud"),
    EUROPE("Europe"),
    AFRIQUE("Afrique et Moyen-Orient"),
    ASIE("Asie"),
    OCEANIE("Oceanie");

    private final String label;

    Region(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** La region d'un point du globe, ou {@code null} dans les glaces du sud. */
    public static Region at(double latitude, double longitude) {
        if (latitude < -60.0d) {
            return null;
        }
        if (longitude < -140.0d && latitude < 30.0d) {
            return OCEANIE;
        }
        if (longitude < -30.0d) {
            return latitude >= 12.0d ? AMERIQUE_NORD : AMERIQUE_SUD;
        }
        if (longitude < 60.0d) {
            return latitude >= 35.0d ? EUROPE : AFRIQUE;
        }
        if (latitude < 0.0d && longitude >= 140.0d || latitude < -10.0d) {
            return OCEANIE;
        }
        return ASIE;
    }

    /** La region d'un pays de la carte de la Terre, ou {@code null} hors de la carte. */
    public static Region of(MinecraftServer server, int country) {
        EarthData earth = EarthData.get();
        if (earth == null || server == null || country <= 0) {
            return null;
        }
        int[] heart = EarthNations.heart(server, country);
        if (heart == null) {
            return null;
        }
        return at(earth.latitude(heart[1]), earth.longitude(heart[0]));
    }

    public static Region of(String name) {
        for (Region region : values()) {
            if (region.name().equals(name)) {
                return region;
            }
        }
        return null;
    }
}
