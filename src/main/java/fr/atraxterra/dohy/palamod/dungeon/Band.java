package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;

/**
 * Les quatre couleurs de paliers du wiki (« Jaune », « Orange », « Rouge »,
 * « Noire ») : vingt-cinq paliers chacune, la difficulte montant d'une couleur
 * a l'autre. Elles nomment la tranche dans les ecrans et colorent les noms des
 * monstres.
 */
public enum Band {

    YELLOW("Jaune", ChatFormatting.YELLOW),
    ORANGE("Orange", ChatFormatting.GOLD),
    RED("Rouge", ChatFormatting.RED),
    BLACK("Noire", ChatFormatting.DARK_GRAY);

    private final String label;
    private final ChatFormatting color;

    Band(String label, ChatFormatting color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public ChatFormatting color() {
        return color;
    }

    public static Band of(int palier) {
        if (palier <= 25) {
            return YELLOW;
        }
        if (palier <= 50) {
            return ORANGE;
        }
        return palier <= 75 ? RED : BLACK;
    }
}
