package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.earth.EarthAtlas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * La grande carte (touche M) : glisser pour se deplacer, la molette pour le
 * zoom, clic droit pour poser un point de passage, clic sur un point pour le
 * modifier, C pour revenir sur soi. En haut, les calques a afficher.
 */
public final class AtlasScreen extends Screen {

    private static final int BAR = 22;
    private static final double MIN_SCALE = 0.125d;
    private static final double MAX_SCALE = 128.0d;

    private record Toggle(String label, int x, int width, Runnable action, boolean on) {
    }

    private double centerX;
    private double centerZ;
    private double scale = 2.0d;
    private final List<Toggle> toggles = new ArrayList<>();

    public AtlasScreen() {
        super(Component.literal("Atlas"));
    }

    /** La grande carte a ce zoom (blocs par pixel), centree sur le joueur. */
    public AtlasScreen(double scale) {
        this();
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    @Override
    protected void init() {
        LocalPlayer player = minecraft.player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
    }

    private String dimension() {
        return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, Ui.INSET);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AtlasMap map = AtlasClient.map();
        if (map != null) {
            map.upload();
        }
        AtlasClient.Options options = AtlasClient.options();
        String dimension = dimension();
        int mapTop = BAR;
        int mapHeight = height - BAR * 2;
        AtlasView.draw(graphics, font, map, 0, mapTop, width, mapHeight, centerX, centerZ, scale,
                new AtlasView.Layers(options.holders, options.marks, options.waypoints, true), dimension);
        LocalPlayer player = minecraft.player;
        if (player != null) {
            int px = AtlasView.screenX(player.getX(), 0, width, centerX, scale);
            int py = AtlasView.screenY(player.getZ(), mapTop, mapHeight, centerZ, scale);
            if (py > mapTop && py < mapTop + mapHeight) {
                AtlasView.player(graphics, px, py, player.getYRot(), 14);
            }
        }
        topBar(graphics, mouseX, mouseY);
        bottomBar(graphics, mouseX, mouseY, mapTop, mapHeight, dimension);
    }

    /** La barre du haut, dans le style des panneaux du kit : le titre, les calques, la croix. */
    private void topBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiGraphics ui = new UiGraphics(graphics, font, mouseX, mouseY);
        ui.rect(0, 0, width, BAR, Ui.OUTER);
        ui.rect(0, 0, width, BAR - 2, Ui.BG);
        ui.rect(0, BAR - 3, width, 1, Ui.FRAME);
        ui.textShaded(Ui.TITLE, "ATLAS", 8, 5, 11.0f, Ui.RED, Ui.RED_SHADE, 1.0f);
        AtlasClient.Options options = AtlasClient.options();
        toggles.clear();
        int x = 8 + Math.round(ui.width(Ui.TITLE, "ATLAS", 11.0f)) + 12;
        x = toggle(ui, "PAYS TENUS", x, options.holders, () -> options.holders = !options.holders);
        x = toggle(ui, "GISEMENTS", x, options.marks, () -> options.marks = !options.marks);
        x = toggle(ui, "POINTS", x, options.waypoints, () -> options.waypoints = !options.waypoints);
        x = toggle(ui, "POINTS EN JEU", x, options.markers, () -> options.markers = !options.markers);
        x = toggle(ui, "MINIMAP", x, options.minimap, () -> options.minimap = !options.minimap);
        toggle(ui, "CENTRER (C)", x, false, this::recenter);
        for (Toggle toggle : toggles) {
            boolean hovered = mouseY >= 4 && mouseY < 18 && mouseX >= toggle.x() && mouseX < toggle.x() + toggle.width();
            if (toggle.on()) {
                ui.button(toggle.x(), 4, toggle.width(), 14, Ui.GREEN_DARK, 0xFF094A1C, hovered);
            } else {
                ui.button(toggle.x(), 4, toggle.width(), 14, Ui.GRAY_BUTTON, Ui.GRAY_BUTTON_DARK, hovered);
            }
            ui.centered(Ui.WIDE, toggle.label(), toggle.x() + toggle.width() / 2.0f, 8, 4.5f,
                    toggle.on() ? Ui.WHITE : Ui.TEXT);
        }
        // la croix rouge du coin, comme les autres ecrans du kit
        int close = width - 18;
        boolean hovered = mouseX >= close && mouseX < close + 12 && mouseY >= 5 && mouseY < 17;
        ui.rect(close, 6, 12, 11, Ui.RED_DARK);
        ui.rect(close, 5, 12, 10, hovered ? UiGraphics.lighten(Ui.RED) : Ui.RED);
        ui.texture(Ui.CLOSE, close + 2, 6, 8, 8, 32, 32);
    }

    private int toggle(UiGraphics ui, String label, int x, boolean on, Runnable action) {
        int width = Math.round(ui.width(Ui.WIDE, label, 4.5f)) + 10;
        toggles.add(new Toggle(label, x, width, action, on));
        return x + width + 4;
    }

    private void bottomBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int mapTop, int mapHeight,
                           String dimension) {
        UiGraphics ui = new UiGraphics(graphics, font, mouseX, mouseY);
        int top = height - BAR;
        ui.rect(0, top, width, BAR, Ui.OUTER);
        ui.rect(0, top + 2, width, BAR - 2, Ui.BG);
        ui.rect(0, top + 2, width, 1, Ui.FRAME);
        StringBuilder text = new StringBuilder();
        if (mouseY >= mapTop && mouseY < mapTop + mapHeight) {
            double wx = AtlasView.worldX(mouseX, 0, width, centerX, scale);
            double wz = AtlasView.worldZ(mouseY, mapTop, mapHeight, centerZ, scale);
            text.append(Math.round(Math.floor(wx))).append(", ").append(Math.round(Math.floor(wz)));
            AtlasData.Globe globe = AtlasData.globe();
            if (globe != null && "minecraft:overworld".equals(dimension)) {
                EarthAtlas.Country country = globe.country(wx, wz);
                if (country != null) {
                    text.append("   ").append(country.name());
                    EarthAtlas.Owner owner = AtlasData.owner(country.id());
                    if (owner != null) {
                        text.append(" (").append(owner.holder()).append(')');
                    }
                }
                EarthAtlas.Mark mark = markAt(mouseX, mouseY, mapTop, mapHeight);
                if (mark != null) {
                    text.append("   |   ").append(mark.name()).append(", ").append(mark.resource());
                }
            }
            Waypoints.Point point = pointAt(mouseX, mouseY, mapTop, mapHeight, dimension);
            if (point != null) {
                text.append("   |   ").append(point.name()).append(" (").append(point.y()).append(')');
            }
        }
        ui.text(Ui.BODY, text.toString(), 8, top + 8, 6.5f, Ui.TEXT);
        String help = "Glisser : bouger   Molette : zoom   Clic droit : point   1 px = "
                + (scale < 1.0d ? String.format(java.util.Locale.ROOT, "%.2f", scale) : String.valueOf((int) scale))
                + " bloc(s)";
        ui.right(Ui.BODY, help, width - 8, top + 8, 6.0f, Ui.MUTED);
    }

    private EarthAtlas.Mark markAt(double mouseX, double mouseY, int mapTop, int mapHeight) {
        EarthAtlas.Mark best = null;
        double bestDistance = 36.0d;
        for (EarthAtlas.Mark mark : AtlasData.marks()) {
            double dx = AtlasView.screenX(mark.x(), 0, width, centerX, scale) - mouseX;
            double dy = AtlasView.screenY(mark.z(), mapTop, mapHeight, centerZ, scale) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mark;
            }
        }
        return best;
    }

    private Waypoints.Point pointAt(double mouseX, double mouseY, int mapTop, int mapHeight, String dimension) {
        Waypoints.Point best = null;
        double bestDistance = 36.0d;
        for (Waypoints.Point point : Waypoints.all()) {
            if (!point.dimension().equals(dimension)) {
                continue;
            }
            double dx = AtlasView.screenX(point.x() + 0.5d, 0, width, centerX, scale) - mouseX;
            double dy = AtlasView.screenY(point.z() + 0.5d, mapTop, mapHeight, centerZ, scale) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        return best;
    }

    private void recenter() {
        if (minecraft.player != null) {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (my < BAR) {
            if (mx >= width - 18 && mx < width - 6) {
                UiScreen.click(0.8f);
                onClose();
                return true;
            }
            for (Toggle toggle : toggles) {
                if (mx >= toggle.x() && mx < toggle.x() + toggle.width()) {
                    UiScreen.click(1.0f);
                    toggle.action().run();
                    AtlasClient.saveOptions();
                    return true;
                }
            }
            return true;
        }
        int mapTop = BAR;
        int mapHeight = height - BAR * 2;
        if (my >= mapTop + mapHeight) {
            return true;
        }
        String dimension = dimension();
        Waypoints.Point point = pointAt(mx, my, mapTop, mapHeight, dimension);
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && point != null) {
            minecraft.gui.setScreen(new WaypointScreen(this, point, point.x(), point.y(), point.z(), dimension));
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int x = (int) Math.floor(AtlasView.worldX(mx, 0, width, centerX, scale));
            int z = (int) Math.floor(AtlasView.worldZ(my, mapTop, mapHeight, centerZ, scale));
            int y = minecraft.player == null ? 64 : minecraft.player.getBlockY();
            if (minecraft.level != null && minecraft.level.hasChunk(x >> 4, z >> 4)) {
                y = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            }
            minecraft.gui.setScreen(new WaypointScreen(this, null, x, y, z, dimension));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.y() >= BAR) {
            centerX -= dragX * scale;
            centerZ -= dragY * scale;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mapTop = BAR;
        int mapHeight = height - BAR * 2;
        double wx = AtlasView.worldX(mouseX, 0, width, centerX, scale);
        double wz = AtlasView.worldZ(mouseY, mapTop, mapHeight, centerZ, scale);
        double next = scrollY > 0 ? scale / 1.25d : scale * 1.25d;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, next));
        centerX = wx - (mouseX - width / 2.0d) * scale;
        centerZ = wz - (mouseY - mapTop - mapHeight / 2.0d) * scale;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_C) {
            recenter();
            return true;
        }
        if (AtlasClient.atlasKey() != null && AtlasClient.atlasKey().matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
