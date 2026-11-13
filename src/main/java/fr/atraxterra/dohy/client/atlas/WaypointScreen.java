package fr.atraxterra.dohy.client.atlas;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Poser ou modifier un point de passage : son nom, sa couleur ; le supprimer. */
final class WaypointScreen extends Screen {

    private final Screen parent;
    private final Waypoints.Point existing;
    private final int x;
    private final int y;
    private final int z;
    private final String dimension;
    private int color;
    private EditBox name;

    WaypointScreen(Screen parent, Waypoints.Point existing, int x, int y, int z, String dimension) {
        super(Component.literal(existing == null ? "Nouveau point" : "Point de passage"));
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
    protected void init() {
        int left = width / 2 - 100;
        int top = height / 2 - 40;
        name = new EditBox(font, left, top, 200, 20, Component.literal("Nom"));
        name.setMaxLength(32);
        name.setValue(existing == null ? "Point " + x + " " + z : existing.name());
        addRenderableWidget(name);
        setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal("Couleur"), button -> nextColor())
                .bounds(left, top + 26, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Enregistrer"), button -> save())
                .bounds(left + 102, top + 26, 98, 20).build());
        if (existing != null) {
            addRenderableWidget(Button.builder(Component.literal("Supprimer"), button -> delete())
                    .bounds(left, top + 50, 98, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Annuler"), button -> onClose())
                .bounds(left + 102, top + 50, 98, 20).build());
    }

    private void nextColor() {
        int index = 0;
        for (int i = 0; i < Waypoints.COLORS.length; i++) {
            if (Waypoints.COLORS[i] == color) {
                index = i;
            }
        }
        color = Waypoints.COLORS[(index + 1) % Waypoints.COLORS.length];
    }

    private void save() {
        String text = name.getValue().isBlank() ? "Point" : name.getValue().trim();
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 100;
        int top = height / 2 - 40;
        graphics.text(font, title, left, top - 24, 0xFFFFD24A, false);
        graphics.text(font, Component.literal(x + ", " + y + ", " + z), left, top - 12, 0xFF8C8B93, false);
        AtlasView.diamond(graphics, left + 200 - 8, top - 16, 5, 0xFF000000);
        AtlasView.diamond(graphics, left + 200 - 8, top - 16, 4, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
