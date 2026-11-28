package fr.atraxterra.dohy.palamod.pet;

import java.util.Locale;

/**
 * Les quetes d'entretien : quatre tirees chaque jour (wiki : s'immerger dans un
 * liquide, nourrir le familier, marcher, passer du temps avec lui, et d'autres).
 * Toutes faites avant minuit (heure de Paris) : le familier garde son bonheur ;
 * sinon il en perd 25. Chacune rapporte de l'XP au familier et un peu de bonheur.
 */
public enum PetMission {

    IMMERSE("S'immerger dans un liquide", "secondes", 30, 300),
    FEED("Donner une friandise a ton familier", "friandise", 1, 300),
    WALK("Marcher avec ton familier", "blocs", 500, 350),
    TIME("Passer du temps avec ton familier", "minutes", 30, 400),
    KILL("Vaincre des monstres", "monstres", 15, 400),
    MINE("Miner des blocs", "blocs", 150, 350),
    HARVEST("Recolter des cultures", "cultures", 40, 350),
    FISH("Pecher des poissons", "poissons", 5, 400),
    JUMP("Sauter", "sauts", 100, 250);

    private final String label;
    private final String unit;
    private final int goal;
    private final int xp;

    PetMission(String label, String unit, int goal, int xp) {
        this.label = label;
        this.unit = unit;
        this.goal = goal;
        this.xp = xp;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public int goal() {
        return goal;
    }

    /** XP de familier a la mission finie. */
    public int xp() {
        return xp;
    }

    public static PetMission byId(String id) {
        for (PetMission mission : values()) {
            if (mission.id().equals(id)) {
                return mission;
            }
        }
        return null;
    }
}
