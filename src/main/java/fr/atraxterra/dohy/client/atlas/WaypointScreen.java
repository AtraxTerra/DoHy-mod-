package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Buttons;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.client.ui.widget.SearchBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Poser ou modifier un point de passage, dans une carte du kit par-dessus la
 * carte du monde : son nom, sa couleur ; le supprimer.
 */
final class WaypointScreen extends UiScreen {

    private static final int WIDTH = 236;
    private static final int HEIGHT = 154;
    /** La carte au milieu d'une maquette de la taille des menus : meme echelle qu'eux. */
    private static final int LEFT = (Shell.WIDTH - WIDTH) / 2;
    private static final int TOP = (Shell.HEIGHT - HEIGHT) / 2;
    private static final float CLOSE = 17.3f;
    private static final float SWATCH = 6.3f;

    private final Screen parent;
    private final Waypoints.Point existing;
    private final int x;
    private final int y;
    private final int z;
    private final String dimension;
    private final double opened = Anim.now();
    private final Anim.Value closeHover = new Anim.Value(0.0f, 16.0f);
    private int color;
    private SearchBox name;

    WaypointScreen(Screen parent, Waypoints.Point existing, int x, int y, int z, String dimension) {
        super(Component.literal("Point de passage"), Shell.WIDTH, Shell.HEIGHT);
        this.parent = parent;
        this.existing = existing;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.color = existing == null ? Waypoints.COLORS[(int) (Math.abs((long) x * 31 + z) % Waypoints.COLORS.length)]
                : existing.color();
    }

    @Override
    protected float lift() {
        return 5.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.22d)));
    }

    @Override
    protected void build() {
        String kept = name == null ? (existing == null ? "Point " + x + " " + z : existing.name()) : name.value();
        name = add(SearchBox.form(LEFT + 16, TOP + 62, WIDTH - 32, 15, "Nom du point", 32, kept));
        add(new Swatches());
        add(new Actions());
    }

    private void save() {
        String text = name.value().isBlank() ? "Point" : name.value().trim();
        Waypoints.Point point = new Waypoints.Point(text, x, y, z, color, dimension,
                existing != null && existing.death() && text.equals(existing.name()));
        if (existing == null) {
            Waypoints.add(point);
        } else {
            Waypoints.replace(existing, point);
        }
        onClose();
    }

    private void delete() {
        Waypoints.remove(existing);
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    /** La carte du monde reste visible derriere, assombrie. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (parent != null) {
            parent.extractRenderState(graphics, -1, -1, partialTick);
            graphics.nextStratum();
        }
        graphics.fill(0, 0, width, height, 0x99000000);
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        Paint p = g.paint();
        p.shadow(LEFT + 4, TOP + 8, WIDTH - 8, HEIGHT - 8, 22, Paint.alpha(Ui.Theme.SHADOW, 0.6f));
        p.framed(LEFT, TOP, WIDTH, HEIGHT, Shell.RADIUS, Ui.Theme.BG2, 0xFF2C2E33, 1);
        g.textShaded(Ui.TITLE, existing == null ? "NOUVEAU POINT" : "POINT DE PASSAGE", LEFT + 16.0f, TOP + 12.0f,
                14.7f, Ui.Theme.RED, Ui.Theme.RED_DD, 1.33f);
        g.text(Ui.BODY, x + "   " + y + "   " + z, LEFT + 16.0f, TOP + 30.3f, 5.0f, Ui.Theme.MUTED);
        // La croix, comme dans les menus.
        float closeX = closeX();
        float closeY = TOP + 11.0f;
        boolean over = mouseX >= closeX && mouseY >= closeY && mouseX < closeX + CLOSE && mouseY < closeY + CLOSE;
        float hover = closeHover.toward(over ? 1.0f : 0.0f);
        p.framed(closeX, closeY, CLOSE, CLOSE, 4.7f, Paint.mix(Ui.Theme.CARD, Ui.Theme.RED, hover),
                Paint.mix(0xFF2E3035, Ui.Theme.RED, hover), 1);
        if (hover > 0.05f) {
            p.glow(closeX + CLOSE / 2, closeY + CLOSE / 2, 16, Paint.alpha(Ui.Theme.RED, 0.35f * hover));
        }
        g.texture(Ui.CLOSE, Math.round(closeX + CLOSE / 2 - 3.5f), Math.round(closeY + CLOSE / 2 - 3.5f), 7, 7, 32,
                32);
        Shell.section(g, p, LEFT + 16.0f, TOP + 47.0f, WIDTH - 32.0f, "craft", "NOM", "");
        Shell.section(g, p, LEFT + 16.0f, TOP + 86.0f, WIDTH - 32.0f, "resources", "COULEUR", "");
    }

    private static float closeX() {
        return LEFT + WIDTH - 12.0f - CLOSE;
    }

    @Override
    protected boolean backgroundClicked(double mouseX, double mouseY, int button) {
        float closeX = closeX();
        float closeY = TOP + 11.0f;
        if (button == 0 && mouseX >= closeX && mouseY >= closeY && mouseX < closeX + CLOSE
                && mouseY < closeY + CLOSE) {
            UiScreen.click(0.8f);
            onClose();
            return true;
        }
        if (button == 0 && (mouseX < LEFT || mouseY < TOP || mouseX >= LEFT + WIDTH || mouseY >= TOP + HEIGHT)) {
            // Un clic hors de la carte la ferme, comme on referme une bulle.
            onClose();
            return true;
        }
        return false;
    }

    /** Les couleurs : des pastilles rondes, la choisie cerclee de blanc. */
    private final class Swatches extends UiElement {
        private final Anim.Value[] hovers = new Anim.Value[Waypoints.COLORS.length];

        Swatches() {
            super(LEFT + 16, TOP + 98, WIDTH - 32, 16);
            for (int index = 0; index < hovers.length; index++) {
                hovers[index] = new Anim.Value(0.0f, 16.0f);
            }
        }

        private float centerX(int index) {
            return x + SWATCH + 1.0f + index * (SWATCH * 2.0f + 6.0f);
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            Paint p = g.paint();
            float cy = y + height / 2.0f;
            for (int index = 0; index < Waypoints.COLORS.length; index++) {
                int value = Waypoints.COLORS[index];
                float cx = centerX(index);
                boolean over = Math.hypot(mouseX - cx, mouseY - cy) <= SWATCH + 1.5f;
                float hover = hovers[index].toward(over ? 1.0f : 0.0f);
                boolean chosen = value == color;
                if (chosen) {
                    p.glow(cx, cy, SWATCH * 2.4f, Paint.alpha(value, 0.45f));
                    p.dot(cx, cy, SWATCH + 1.6f, 0xFFFFFFFF);
                    p.dot(cx, cy, SWATCH + 0.6f, Ui.Theme.BG2);
                } else if (hover > 0.01f) {
                    p.dot(cx, cy, SWATCH + 1.2f, Paint.alpha(0xFFFFFFFF, 0.35f * hover));
                    p.dot(cx, cy, SWATCH + 0.5f, Ui.Theme.BG2);
                }
                p.dot(cx, cy, SWATCH - (chosen ? 0.6f : 0.0f), value);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            float cy = y + height / 2.0f;
            for (int index = 0; index < Waypoints.COLORS.length; index++) {
                if (Math.hypot(mouseX - centerX(index), mouseY - cy) <= SWATCH + 2.0f) {
                    UiScreen.click(1.2f);
                    color = Waypoints.COLORS[index];
                    return true;
                }
            }
            return false;
        }
    }

    /** Les boutons du bas : supprimer a gauche (si le point existe), annuler et enregistrer a droite. */
    private final class Actions extends UiElement {
        private static final float CANCEL = 56.0f;
        private static final float SAVE = 80.0f;
        private static final float DELETE = 64.0f;
        private final Anim.Value cancelHover = new Anim.Value(0.0f, 16.0f);
        private final Anim.Value saveHover = new Anim.Value(0.0f, 16.0f);
        private final Anim.Value deleteHover = new Anim.Value(0.0f, 16.0f);

        Actions() {
            super(LEFT + 16, TOP + HEIGHT - 30, WIDTH - 32, 17);
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            Paint p = g.paint();
            float right = x + width;
            float saveX = right - SAVE;
            float cancelX = saveX - 4.0f - CANCEL;
            boolean inRow = mouseY >= y && mouseY < y + height;
            Buttons.draw(g, p, cancelX, y, CANCEL, height, "ANNULER", Buttons.GHOST,
                    cancelHover.toward(inRow && mouseX >= cancelX && mouseX < cancelX + CANCEL ? 1.0f : 0.0f), true,
                    5.3f);
            Buttons.draw(g, p, saveX, y, SAVE, height, "ENREGISTRER", Buttons.GREEN,
                    saveHover.toward(inRow && mouseX >= saveX && mouseX < right ? 1.0f : 0.0f), true, 5.3f);
            if (existing != null) {
                Buttons.draw(g, p, x, y, DELETE, height, "SUPPRIMER", Buttons.RED,
                        deleteHover.toward(inRow && mouseX >= x && mouseX < x + DELETE ? 1.0f : 0.0f), true, 5.3f);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || mouseY < y || mouseY >= y + height) {
                return false;
            }
            float right = x + width;
            float saveX = right - SAVE;
            float cancelX = saveX - 4.0f - CANCEL;
            if (mouseX >= saveX && mouseX < right) {
                UiScreen.click(1.1f);
                save();
                return true;
            }
            if (mouseX >= cancelX && mouseX < cancelX + CANCEL) {
                UiScreen.click(0.9f);
                onClose();
                return true;
            }
            if (existing != null && mouseX >= x && mouseX < x + DELETE) {
                UiScreen.click(0.8f);
                delete();
                return true;
            }
            return false;
        }
    }
}
