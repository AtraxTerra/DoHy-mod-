package fr.atraxterra.dohy.client.ui.vanilla;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.mine.MineService;
import fr.atraxterra.dohy.util.RealClock;

import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Les pieces communes de l'ecran titre, du menu pause et de la liste des joueurs :
 * le logo, les boutons larges, les pastilles d'information, l'heure du serveur.
 *
 * <p>Comme les conteneurs, ces ecrans se dessinent au pixel d'interface (une unite)
 * et suivent l'echelle d'interface choisie dans les options, comme les ecrans du
 * jeu qu'ils remplacent.
 */
public final class MenuStyle {

    /** Le bouton principal (rouge), les boutons pleins et le bouton fantome (quitter). */
    public static final int PRIMARY = 0;
    public static final int SOLID = 1;
    public static final int GHOST = 2;

    private MenuStyle() {
    }

    /** Le logo : BERSKAR en police de titre, rouge sur son ombre portee. */
    public static void logo(UiGraphics g, float x, float y, float size) {
        g.textShaded(Ui.LOGO, "BERSKAR", x, y, size, Ui.Theme.RED, Ui.Theme.RED_DD, Math.max(1.0f, size * 0.07f));
    }

    public static float logoWidth(UiGraphics g, float size) {
        return g.width(Ui.LOGO, "BERSKAR", size);
    }

    /**
     * Un bouton large : le libelle a gauche, une touche ou une precision a droite.
     * {@code hover} va de 0 a 1.
     */
    public static void button(UiGraphics g, Paint p, float x, float y, float width, float height, String label,
                              String hint, int kind, float hover, boolean enabled, float textSize) {
        float radius = 3.0f;
        float textY = y + (height - textSize) / 2.0f + 0.4f;
        int text = Ui.Theme.TEXT;
        switch (kind) {
            case PRIMARY -> {
                p.shadow(x + 3, y + 4, width - 6, height - 4, 8, Paint.alpha(Ui.Theme.RED, 0.30f + 0.20f * hover));
                p.roundGradient(x, y, width, height, radius, Paint.mix(Ui.Theme.RED_LIGHT, 0xFFFFFFFF, 0.10f * hover),
                        Ui.Theme.RED_D);
                p.rect(x + radius, y + 0.3f, width - 2 * radius, 0.3f, 0x40FFFFFF);
            }
            case GHOST -> {
                p.round(x, y, width, height, radius, Paint.mix(0x30101113, 0xB0222427, hover));
                p.outline(x, y, width, height, radius, Paint.mix(0xFF3B3E45, 0xFF5A5E66, hover), 1);
                text = hover > 0.5f ? Ui.Theme.TEXT : Ui.Theme.TEXT2;
            }
            default -> {
                p.framed(x, y, width, height, radius, Paint.mix(0xF02A2C31, 0xF036393F, hover),
                        Paint.mix(0xFF3B3E45, 0xFF52555C, hover), 1);
            }
        }
        if (!enabled) {
            text = Ui.Theme.DIM;
        }
        g.text(Ui.WIDE, label, x + 6.0f, textY, textSize, text);
        if (hint != null && !hint.isEmpty()) {
            g.right(Ui.PIXEL, hint, x + width - 6.0f, y + (height - 3.3f) / 2.0f + 0.3f, 3.3f,
                    kind == PRIMARY ? 0xCCFFFFFF : Ui.Theme.MUTED);
        }
    }

    /** Une pastille d'information : un petit intitule, une valeur en couleur. */
    public static void chip(UiGraphics g, Paint p, float x, float y, float width, float height, String label,
                            String value, int color) {
        p.framed(x, y, width, height, 3.0f, 0xFF1C1D21, 0xFF2A2C31, 1);
        g.text(Ui.PIXEL, label, x + 5.0f, y + 3.2f, 3.3f, Ui.Theme.DIM);
        g.text(Ui.WIDE, g.fit(Ui.WIDE, value, 4.5f, width - 10.0f), x + 5.0f, y + height - 7.5f, 4.5f, color);
    }

    /** La ville du fuseau du serveur : « PARIS ». */
    public static String city() {
        String id = RealClock.zone().getId();
        int slash = id.lastIndexOf('/');
        return ContainerStyle.plain(slash >= 0 ? id.substring(slash + 1) : id).replace('_', ' ')
                .toUpperCase(Locale.ROOT);
    }

    /** L'heure du serveur : « 18 H 24 ». */
    public static String clock() {
        ZonedDateTime now = ZonedDateTime.now(RealClock.zone());
        return now.getHour() + " H " + String.format(Locale.ROOT, "%02d", now.getMinute());
    }

    /** L'heure de la prochaine remise a neuf de la Mine : « 20 H ». */
    public static String mineReset() {
        return MineService.nextResetHour() + " H";
    }
}
