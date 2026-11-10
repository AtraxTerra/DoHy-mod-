package fr.atraxterra.dohy.faction;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Les quatre branches de l'arbre de technologie de Paladium. */
public enum FactionBranch {
    GLOBAL("global", "Globale", "Claims, homes, XP de donjon", 0xFF2F5BEA),
    PILLAGE("pillage", "Pillage", "Dynamites, withers, arc", 0xFFEF3826),
    DEFENSE("defense", "Defense", "Obsidiennes, spikes, claims", 0xFF22E558),
    ECONOMY("economy", "Economie", "Reserve d'or et interets", 0xFFFAC400);

    private final String key;
    private final String label;
    private final String summary;
    private final int color;

    FactionBranch(String key, String label, String summary, int color) {
        this.key = key;
        this.label = label;
        this.summary = summary;
        this.color = color;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String summary() {
        return summary;
    }

    public int color() {
        return color;
    }

    public Item icon() {
        return switch (this) {
            case GLOBAL -> Items.COMPASS;
            case PILLAGE -> Items.TNT;
            case DEFENSE -> Items.OBSIDIAN;
            case ECONOMY -> Items.GOLD_BLOCK;
        };
    }

    public static FactionBranch byKey(String key) {
        for (FactionBranch branch : values()) {
            if (branch.key.equals(key)) {
                return branch;
            }
        }
        return null;
    }
}
