package fr.atraxterra.dohy.client.atlas;

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

    private static final int BAR = 18;
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
        graphics.fill(0, 0, width, height, 0xFF0B0E12);
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
        topBar(graphics);
        bottomBar(graphics, mouseX, mouseY, mapTop, mapHeight, dimension);
    }

    private void topBar(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, BAR, 0xE0101014);
        graphics.text(font, Component.literal("Atlas"), 6, 5, 0xFFFFD24A, false);
        AtlasClient.Options options = AtlasClient.options();
        toggles.clear();
        int x = 50;
        x = toggle("Pays tenus", x, options.holders, () -> options.holders = !options.holders);
        x = toggle("Gisements", x, options.marks, () -> options.marks = !options.marks);
        x = toggle("Points", x, options.waypoints, () -> options.waypoints = !options.waypoints);
        x = toggle("Points dans le monde", x, options.markers, () -> options.markers = !options.markers);
        x = toggle("Minimap", x, options.minimap, () -> options.minimap = !options.minimap);
        toggle("Centrer (C)", x, false, this::recenter);
        for (Toggle toggle : toggles) {
            graphics.fill(toggle.x(), 3, toggle.x() + toggle.width(), BAR - 3, toggle.on() ? 0xFF2E5E36 : 0xFF2B2A33);
            graphics.text(font, Component.literal(toggle.label()), toggle.x() + 4, 5,
                    toggle.on() ? 0xFFFFFFFF : 0xFFB0B0B8, false);
        }
    }

    private int toggle(String label, int x, boolean on, Runnable action) {
        int width = font.width(label) + 8;
        toggles.add(new Toggle(label, x, width, action, on));
        return x + width + 4;
    }

    private void bottomBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int mapTop, int mapHeight,
                           String dimension) {
        int top = height - BAR;
        graphics.fill(0, top, width, height, 0xE0101014);
        StringBuilder text = new StringBuilder();
        if (mouseY >= mapTop && mouseY < mapTop + mapHeight) {
            double wx = AtlasView.worldX(mouseX, 0, width, centerX, scale);
            double wz = AtlasView.worldZ(mouseY, mapTop, mapHeight, centerZ, scale);
            text.append(Math.round(Math.floor(wx))).append(", ").append(Math.round(Math.floor(wz)));
            AtlasData.Globe globe = AtlasData.globe();
            if (globe != null && "minecraft:overworld".equals(dimension)) {
                EarthAtlas.Country country = globe.country(wx, wz);
                if (country != null) {
                    text.append("  ").append(country.name());
                    EarthAtlas.Owner owner = AtlasData.owner(country.id());
                    if (owner != null) {
                        text.append(" (").append(owner.holder()).append(')');
                    }
                }
                EarthAtlas.Mark mark = markAt(mouseX, mouseY, mapTop, mapHeight);
                if (mark != null) {
                    text.append("  |  ").append(mark.name()).append(", ").append(mark.resource());
                }
            }
            Waypoints.Point point = pointAt(mouseX, mouseY, mapTop, mapHeight, dimension);
            if (point != null) {
                text.append("  |  ").append(point.name()).append(" (").append(point.y()).append(')');
            }
        }
        graphics.text(font, Component.literal(text.toString()), 6, top + 5, 0xFFE9E8EE, false);
        String help = "Glisser : bouger  Molette : zoom  Clic droit : point  1 px = "
                + (scale < 1.0d ? String.format(java.util.Locale.ROOT, "%.2f", scale) : String.valueOf((int) scale))
                + " bloc(s)";
        graphics.text(font, Component.literal(help), width - font.width(help) - 6, top + 5, 0xFF8C8B93, false);
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
            for (Toggle toggle : toggles) {
                if (mx >= toggle.x() && mx < toggle.x() + toggle.width()) {
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
