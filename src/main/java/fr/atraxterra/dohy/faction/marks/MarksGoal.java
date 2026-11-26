package fr.atraxterra.dohy.faction.marks;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Les quatre objectifs d'A vos marques (« Java | A vos marques ») : miner,
 * tuer des creatures, couper du bois, pecher.
 *
 * <p>Sur Paladium, l'event tourne sur les serveurs de minage et de farmland. En
 * solo, ces serveurs sont des mondes : chaque objectif compte la ou il se fait.
 * Miner, dans la Mine et la dimension du Mineur ; tuer et pecher, dans la Farmland
 * (ses creatures du Chasseur, son lac) et la dimension du Mineur ; couper du
 * bois, dans la dimension du Mineur et l'overworld, car la Mine est sous terre
 * et la Farmland ne se casse pas.
 *
 * <p>Le wiki ne donne pas les quantites : elles sont prevues pour un joueur seul
 * en 30 minutes. Dans la manche aux points, chaque action vaut ses points, et les
 * quatre valent autant : 1 000 points, c'est 1 000 blocs, 250 buches, 50
 * creatures ou 25 prises.
 */
public enum MarksGoal {

    MINE("mine", "Miner", "Mine", "blocs", "bloc mine", 1000L, 1L, Items.IRON_PICKAXE,
            List.of("dohy:mine", "palamod:miner"), "la Mine ou la dimension du Mineur", "Mine, Mineur"),
    WOOD("bois", "Couper du bois", "Coupe", "buches", "buche", 250L, 4L, Items.IRON_AXE,
            List.of("minecraft:overworld", "palamod:miner"), "l'overworld ou la dimension du Mineur",
            "Overworld, Mineur"),
    KILL("creatures", "Tuer des creatures", "Tue", "creatures", "creature", 50L, 20L, Items.IRON_SWORD,
            List.of("dohy:farmland", "palamod:miner"), "la Farmland ou la dimension du Mineur",
            "Farmland, Mineur"),
    FISH("peche", "Pecher", "Peche", "prises", "prise", 25L, 40L, Items.FISHING_ROD,
            List.of("dohy:farmland", "palamod:miner"), "la Farmland ou la dimension du Mineur",
            "Farmland, Mineur");

    private final String key;
    private final String label;
    private final String order;
    private final String unit;
    private final String single;
    private final long target;
    private final long points;
    private final Item icon;
    private final List<String> worlds;
    private final String where;
    private final String places;

    MarksGoal(String key, String label, String order, String unit, String single, long target, long points,
              Item icon, List<String> worlds, String where, String places) {
        this.key = key;
        this.label = label;
        this.order = order;
        this.unit = unit;
        this.single = single;
        this.target = target;
        this.points = points;
        this.icon = icon;
        this.worlds = worlds;
        this.where = where;
        this.places = places;
    }

    /** Le mot de la commande d'essai. */
    public String key() {
        return key;
    }

    /** « Miner », « Couper du bois ». */
    public String label() {
        return label;
    }

    /** L'imperatif du message : « Mine 1 000 blocs ». */
    public String order() {
        return order;
    }

    /** « blocs », « buches ». */
    public String unit() {
        return unit;
    }

    /** « bloc mine », « buche » : le bareme de la manche aux points. */
    public String single() {
        return single;
    }

    /** La quantite a atteindre dans une manche a objectif. */
    public long target() {
        return target;
    }

    /** Ce que vaut une action dans la manche aux points. */
    public long points() {
        return points;
    }

    public Item icon() {
        return icon;
    }

    /** Les mondes ou l'objectif compte. */
    public List<String> worlds() {
        return worlds;
    }

    /** « la Mine ou la dimension du Mineur ». */
    public String where() {
        return where;
    }

    /** « Mine, Mineur » : pour les cartes de l'ecran des events. */
    public String places() {
        return places;
    }

    public static MarksGoal byKey(String key) {
        for (MarksGoal goal : values()) {
            if (goal.key.equals(key)) {
                return goal;
            }
        }
        return null;
    }
}
