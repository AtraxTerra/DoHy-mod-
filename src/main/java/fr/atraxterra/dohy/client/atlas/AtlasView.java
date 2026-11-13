package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Map;

/**
 * Le dessin d'une vue de l'atlas dans un rectangle de l'ecran : le globe (s'il y
 * en a un), le terrain explore, les pays tenus, les lieux, les points de
 * passage et le joueur. Commun a la minimap et a la grande carte.
 */
final class AtlasView {

    static final Identifier PLAYER = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/gui/atlas/player.png");

    /** Les couleurs des ressources des gisements geants (le reste en gris). */
    private static final Map<String, Integer> RESOURCE = Map.ofEntries(
            Map.entry("fer", 0xFFD8AF93), Map.entry("cuivre", 0xFFE0773A), Map.entry("or", 0xFFFFD700),
            Map.entry("diamant", 0xFF5DECF5), Map.entry("charbon", 0xFF3A3A3A), Map.entry("lignite", 0xFF6B5638),
            Map.entry("petrole", 0xFF101010), Map.entry("gaz", 0xFF9AD0FF), Map.entry("sables bitumineux", 0xFF5A4630),
            Map.entry("uranium", 0xFF6BE04A), Map.entry("thorium", 0xFF3E8E3A), Map.entry("terres rares", 0xFFB06BE0),
            Map.entry("lithium", 0xFFE6E6F0), Map.entry("cobalt", 0xFF3050D0), Map.entry("coltan", 0xFF6A6A80),
            Map.entry("bauxite", 0xFFC0603A), Map.entry("titane", 0xFFB0B8C0), Map.entry("plomb", 0xFF60607A),
            Map.entry("zinc", 0xFFA0B0A8), Map.entry("emeraude", 0xFF20D060), Map.entry("lapis", 0xFF2040C0),
            Map.entry("redstone", 0xFFD02020), Map.entry("quartz", 0xFFF0EAE0), Map.entry("zone irradiee", 0xFFB6FF00),
            Map.entry("base de lancement", 0xFF80C8FF));

    /** Ce que la vue montre. */
    record Layers(boolean holders, boolean marks, boolean waypoints, boolean labels) {
    }

    private AtlasView() {
    }

    static int resourceColor(String resource) {
        return RESOURCE.getOrDefault(resource, 0xFFC8C8C8);
    }

    /**
     * Dessine la vue.
     *
     * @param scale blocs par pixel d'interface
     */
    static void draw(GuiGraphicsExtractor graphics, Font font, AtlasMap map, int x0, int y0, int width, int height,
                     double centerX, double centerZ, double scale, Layers layers, String dimension) {
        graphics.enableScissor(x0, y0, x0 + width, y0 + height);
        graphics.fill(x0, y0, x0 + width, y0 + height, 0xFF101418);
        boolean overworld = "minecraft:overworld".equals(dimension);
        AtlasData.Globe globe = overworld && AtlasGlobe.ready() ? AtlasData.globe() : null;
        if (globe != null) {
            image(graphics, AtlasGlobe.BASE, x0, y0, width, height, centerX, centerZ, scale, globe.minX(),
                    globe.minZ(), globe.width() * globe.cell(), globe.height() * globe.cell(), 0xFFFFFFFF);
        }
        if (map != null) {
            tiles(graphics, scale >= 6.0d ? map.overview : map.fine, x0, y0, width, height, centerX, centerZ, scale);
        }
        if (globe != null && layers.holders()) {
            image(graphics, AtlasGlobe.HOLDERS, x0, y0, width, height, centerX, centerZ, scale, globe.minX(),
                    globe.minZ(), globe.width() * globe.cell(), globe.height() * globe.cell(), 0xFFFFFFFF);
        }
        if (overworld && layers.marks()) {
            marks(graphics, font, x0, y0, width, height, centerX, centerZ, scale, layers.labels());
        }
        if (layers.waypoints()) {
            waypoints(graphics, font, x0, y0, width, height, centerX, centerZ, scale, dimension, layers.labels());
        }
        graphics.disableScissor();
    }

    /** Une image posee sur le monde : son coin et sa taille en blocs. */
    private static void image(GuiGraphicsExtractor graphics, Identifier texture, int x0, int y0, int width, int height,
                              double centerX, double centerZ, double scale, double worldX, double worldZ,
                              double worldWidth, double worldHeight, int tint) {
        int left = screenX(worldX, x0, width, centerX, scale);
        int top = screenY(worldZ, y0, height, centerZ, scale);
        int right = screenX(worldX + worldWidth, x0, width, centerX, scale);
        int bottom = screenY(worldZ + worldHeight, y0, height, centerZ, scale);
        if (right < x0 || bottom < y0 || left > x0 + width || top > y0 + height) {
            return;
        }
        graphics.blit(texture, left, top, right, bottom, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private static void tiles(GuiGraphicsExtractor graphics, AtlasTiles tiles, int x0, int y0, int width,
                              int height, double centerX, double centerZ, double scale) {
        int span = tiles.span();
        double halfWidth = width / 2.0d * scale;
        double halfHeight = height / 2.0d * scale;
        int fromX = (int) Math.floor((centerX - halfWidth) / span);
        int toX = (int) Math.floor((centerX + halfWidth) / span);
        int fromZ = (int) Math.floor((centerZ - halfHeight) / span);
        int toZ = (int) Math.floor((centerZ + halfHeight) / span);
        if ((long) (toX - fromX + 1) * (toZ - fromZ + 1) > 400) {
            return;
        }
        for (int tx = fromX; tx <= toX; tx++) {
            for (int tz = fromZ; tz <= toZ; tz++) {
                AtlasTiles.Tile tile = tiles.tile(tx, tz, false);
                if (tile != null) {
                    image(graphics, tile.id, x0, y0, width, height, centerX, centerZ, scale, (double) tx * span,
                            (double) tz * span, span, span, 0xFFFFFFFF);
                }
            }
        }
    }

    private static void marks(GuiGraphicsExtractor graphics, Font font, int x0, int y0, int width, int height,
                              double centerX, double centerZ, double scale, boolean labels) {
        List<EarthAtlas.Mark> marks = AtlasData.marks();
        for (EarthAtlas.Mark mark : marks) {
            int sx = screenX(mark.x(), x0, width, centerX, scale);
            int sy = screenY(mark.z(), y0, height, centerZ, scale);
            if (sx < x0 - 2 || sy < y0 - 2 || sx > x0 + width + 2 || sy > y0 + height + 2) {
                continue;
            }
            int color = resourceColor(mark.resource());
            int size = mark.kind() >= 10 ? 3 : 2;
            graphics.fill(sx - size - 1, sy - size - 1, sx + size + 1, sy + size + 1, 0xFF000000);
            graphics.fill(sx - size, sy - size, sx + size, sy + size, color);
            if (mark.kind() == 2 || mark.kind() == 3) {
                graphics.fill(sx - 1, sy - 1, sx + 1, sy + 1, 0xFFFFFFFF);
            }
            if (labels && scale <= 4.0d) {
                label(graphics, font, mark.name(), sx, sy + size + 2, 0xFFFFFFFF);
            }
        }
        EarthAtlas.Nations nations = AtlasData.nations();
        if (nations != null && nations.capital() != 0) {
            int sx = screenX(nations.capitalX(), x0, width, centerX, scale);
            int sy = screenY(nations.capitalZ(), y0, height, centerZ, scale);
            graphics.fill(sx - 4, sy - 4, sx + 4, sy + 4, 0xFFFFFFFF);
            graphics.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFFEF3826);
            if (labels) {
                label(graphics, font, "Capitale", sx, sy + 6, 0xFFFFD24A);
            }
        }
    }

    private static void waypoints(GuiGraphicsExtractor graphics, Font font, int x0, int y0, int width, int height,
                                  double centerX, double centerZ, double scale, String dimension, boolean labels) {
        for (Waypoints.Point point : Waypoints.all()) {
            if (!point.dimension().equals(dimension)) {
                continue;
            }
            int sx = screenX(point.x() + 0.5d, x0, width, centerX, scale);
            int sy = screenY(point.z() + 0.5d, y0, height, centerZ, scale);
            if (sx < x0 - 3 || sy < y0 - 3 || sx > x0 + width + 3 || sy > y0 + height + 3) {
                continue;
            }
            diamond(graphics, sx, sy, 4, 0xFF000000);
            diamond(graphics, sx, sy, 3, point.color());
            if (labels) {
                label(graphics, font, point.name(), sx, sy + 5, point.color());
            }
        }
    }

    /** Un losange plein, ligne par ligne. */
    static void diamond(GuiGraphicsExtractor graphics, int x, int y, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
        }
    }

    static void label(GuiGraphicsExtractor graphics, Font font, String text, int centerX, int top, int color) {
        int width = font.width(text);
        graphics.fill(centerX - width / 2 - 2, top - 1, centerX + width / 2 + 2, top + 9, 0x99000000);
        graphics.text(font, Component.literal(text), centerX - width / 2, top, color, false);
    }

    /** La fleche du joueur, tournee selon son regard (le nord en haut de la vue). */
    static void player(GuiGraphicsExtractor graphics, int x, int y, float yaw, int size) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) Math.toRadians(yaw + 180.0f));
        graphics.blit(PLAYER, -size / 2, -size / 2, size / 2, size / 2, 0.0f, 1.0f, 0.0f, 1.0f);
        pose.popMatrix();
    }

    static int screenX(double worldX, int x0, int width, double centerX, double scale) {
        return (int) Math.round(x0 + width / 2.0d + (worldX - centerX) / scale);
    }

    static int screenY(double worldZ, int y0, int height, double centerZ, double scale) {
        return (int) Math.round(y0 + height / 2.0d + (worldZ - centerZ) / scale);
    }

    static double worldX(double screenX, int x0, int width, double centerX, double scale) {
        return centerX + (screenX - x0 - width / 2.0d) * scale;
    }

    static double worldZ(double screenY, int y0, int height, double centerZ, double scale) {
        return centerZ + (screenY - y0 - height / 2.0d) * scale;
    }

    static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
