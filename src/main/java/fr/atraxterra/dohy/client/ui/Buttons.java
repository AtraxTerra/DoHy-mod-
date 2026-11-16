package fr.atraxterra.dohy.client.ui;

/**
 * Les boutons du kit, dessines avec {@link Paint} : plein vert (acheter, confirmer),
 * plein rouge (vendre, supprimer), ou fantome (annuler, quantites).
 */
public final class Buttons {

    public static final int GREEN = 0;
    public static final int RED = 1;
    public static final int GHOST = 2;
    /** Fantome mais allume : l'option choisie d'une rangee. */
    public static final int GHOST_ON = 3;

    private Buttons() {
    }

    /**
     * Un bouton : {@code hover} de 0 a 1 (anime par l'appelant). Eteint, il reste
     * lisible mais gris.
     */
    public static void draw(UiGraphics g, Paint p, float x, float y, float width, float height, String label,
                            int kind, float hover, boolean enabled, float textSize) {
        float radius = Math.min(4.0f, height / 2.0f);
        if (!enabled) {
            p.framed(x, y, width, height, radius, 0xFF1B1C1F, 0xFF2A2C31, 1);
            g.centered(Ui.WIDE, label, x + width / 2.0f, y + (height - textSize) / 2.0f + 0.3f, textSize,
                    Ui.Theme.DIM);
            return;
        }
        switch (kind) {
            case GREEN, RED -> {
                int top = kind == GREEN ? 0xFF3FD52C : Ui.Theme.RED_LIGHT;
                int bottom = kind == GREEN ? 0xFF24A015 : Ui.Theme.RED_D;
                int glow = kind == GREEN ? Ui.Theme.GREEN : Ui.Theme.RED;
                p.shadow(x + 3, y + 5, width - 6, height - 5, 7, Paint.alpha(glow, 0.28f + 0.17f * hover));
                p.roundGradient(x, y, width, height, radius, Paint.mix(top, 0xFFFFFFFF, 0.10f * hover), bottom);
                p.rect(x + radius, y + 0.34f, width - 2 * radius, 0.34f, 0x40FFFFFF);
                g.centered(Ui.WIDE, label, x + width / 2.0f, y + (height - textSize) / 2.0f + 0.3f, textSize,
                        Ui.Theme.TEXT);
            }
            default -> {
                boolean on = kind == GHOST_ON;
                int border = on ? 0xFF6A6D75 : Paint.mix(0xFF3F4248, 0xFF5A5D64, hover);
                int fill = on ? 0xFF2E3036 : Paint.mix(0xFF1B1C1F, 0xFF26282C, hover);
                p.framed(x, y, width, height, radius, fill, border, 1);
                g.centered(Ui.WIDE, label, x + width / 2.0f, y + (height - textSize) / 2.0f + 0.3f, textSize,
                        on ? Ui.Theme.TEXT : Ui.Theme.TEXT2);
            }
        }
    }

    /** Un champ de saisie arrondi, creux. */
    public static void field(Paint p, float x, float y, float width, float height, boolean focused) {
        p.framed(x, y, width, height, Math.min(4.0f, height / 2.0f), focused ? 0xFF1E1F23 : 0xFF161719,
                focused ? 0xFF55585F : 0xFF2A2C31, 1);
    }
}
