package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.widget.CloseButton;
import fr.atraxterra.dohy.client.ui.widget.TextField;
import fr.atraxterra.dohy.client.ui.widget.UiButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Poser ou modifier un point de passage, dans les panneaux du kit : son nom, sa couleur ; le supprimer. */
final class WaypointScreen extends UiScreen {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 150;
    private static final int SWATCH = 16;

    private final Screen parent;
    private final Waypoints.Point existing;
    private final int x;
    private final int y;
    private final int z;
    private final String dimension;
    private int color;
    private TextField name;

    WaypointScreen(Screen parent, Waypoints.Point existing, int x, int y, int z, String dimension) {
        super(Component.literal("Point de passage"), WIDTH, HEIGHT);
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
    protected void build() {
        String kept = name == null ? (existing == null ? "Point " + x + " " + z : existing.name()) : name.value();
        add(new CloseButton(WIDTH - 26, 16, this::onClose));
        name = new TextField(22, 62, WIDTH - 44, 16, "Nom du point", 32, text -> {
        });
        name.search = false;
        name.textSize = 5.5f;
        add(name.withValue(kept));
        for (int index = 0; index < Waypoints.COLORS.length; index++) {
            add(new Swatch(Waypoints.COLORS[index], 22 + index * (SWATCH + 6), 96));
        }
        add(new UiButton(WIDTH - 22 - 90, HEIGHT - 32, 90, 18, "ENREGISTRER", UiButton.Style.GREEN, this::save));
        add(new UiButton(WIDTH - 22 - 90 - 66, HEIGHT - 32, 60, 18, "ANNULER", UiButton.Style.GRAY, this::onClose));
        if (existing != null) {
            add(new UiButton(22, HEIGHT - 32, 70, 18, "SUPPRIMER", UiButton.Style.RED, this::delete));
        }
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

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        g.panel(0, 0, WIDTH, HEIGHT);
        g.textShaded(Ui.TITLE, existing == null ? "NOUVEAU POINT" : "POINT DE PASSAGE", 22, 16, 14, Ui.RED,
                Ui.RED_SHADE, 1.0f);
        g.section(22, 44, "section_craft", "NOM", WIDTH - 44);
        g.text(Ui.PIXEL, x + "  " + y + "  " + z, 22, 54, 4.5f, Ui.MUTED);
        g.section(22, 86, "section_resources", "COULEUR", WIDTH - 44);
    }

    /** Une case de couleur : un clic la choisit, la case choisie a un filet blanc. */
    private final class Swatch extends UiElement {
        private final int value;

        Swatch(int value, int x, int y) {
            super(x, y, SWATCH, SWATCH);
            this.value = value;
        }

        @Override
        public void render(UiGraphics graphics, double mouseX, double mouseY) {
            boolean chosen = value == color;
            graphics.rect(x, y, width, height, chosen ? Ui.WHITE : contains(mouseX, mouseY) ? 0xFF8C8B93 : Ui.OUTER);
            graphics.rect(x + 2, y + 2, width - 4, height - 4, value);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            UiScreen.click(1.2f);
            color = value;
            return true;
        }
    }
}
