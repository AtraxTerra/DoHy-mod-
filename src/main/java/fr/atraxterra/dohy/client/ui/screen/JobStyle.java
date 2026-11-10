package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/** Couleur, nom et image de chaque metier, partages par les ecrans. */
final class JobStyle {

    private JobStyle() {
    }

    static int color(String job) {
        return switch (job) {
            case "FARMER" -> 0xFFFAC400;
            case "HUNTER" -> 0xFF2F5BEA;
            case "ALCHEMIST" -> 0xFFEC4FB5;
            default -> 0xFFEF3826;
        };
    }

    static String name(String job) {
        return switch (job) {
            case "FARMER" -> "FERMIER";
            case "HUNTER" -> "CHASSEUR";
            case "ALCHEMIST" -> "ALCHIMISTE";
            default -> "MINEUR";
        };
    }

    static Identifier texture(String job) {
        return Ui.texture("jobs/" + job.toLowerCase(Locale.ROOT));
    }

    static int darker(int color) {
        int r = ((color >> 16) & 0xFF) * 6 / 10;
        int g = ((color >> 8) & 0xFF) * 6 / 10;
        int b = (color & 0xFF) * 6 / 10;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
