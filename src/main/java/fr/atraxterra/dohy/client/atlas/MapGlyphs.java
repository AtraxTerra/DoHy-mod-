package fr.atraxterra.dohy.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Les pictogrammes de la carte, generes lisses a la taille exacte ou ils
 * s'affichent : disques, losanges, triangles, etoile, la fleche du joueur, le
 * profil des traits et les coins arrondis de la minimap.
 *
 * <p>Chaque forme est decrite par sa distance signee : le bord est lisse sur un
 * pixel physique, a toutes les tailles. Les textures ont un filtrage lineaire,
 * de sorte qu'une forme tournee (la fleche) reste nette.
 *
 * <p>Les dessins se font dans le repere courant de la pose, en pixels physiques
 * (l'appelant a deja divise par l'unite).
 */
final class MapGlyphs {

    enum Shape {
        DISC, DIAMOND, TRIANGLE, STAR, RING
    }

    /** Une texture dynamique au filtrage lineaire. */
    private static final class Smooth extends DynamicTexture {
        Smooth(String label, NativeImage image) {
            super(() -> label, image);
            this.sampler = MapTexture.linear();
        }
    }

    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static Identifier area;
    private static final int AREA = 256;

    private MapGlyphs() {
    }

    private static Identifier register(String name, NativeImage image) {
        Identifier id = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "atlas_glyph/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new Smooth("dohy atlas " + name, image));
        return id;
    }

    private static int white(double coverage) {
        int a = (int) Math.round(Math.max(0.0d, Math.min(1.0d, coverage)) * 255.0d);
        return a << 24 | 0xFFFFFF;
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    // -- Formes ------------------------------------------------------------------------------------

    /** Une forme blanche de {@code size} pixels de cote, a teinter au dessin. */
    static Identifier shape(Shape shape, int size) {
        int s = Math.max(3, Math.min(256, size));
        return CACHE.computeIfAbsent(shape.name().toLowerCase(java.util.Locale.ROOT) + "_" + s, key -> {
            NativeImage image = new NativeImage(s, s, false);
            double radius = s / 2.0d - 0.5d;
            for (int py = 0; py < s; py++) {
                for (int px = 0; px < s; px++) {
                    double x = (px + 0.5d - s / 2.0d) / radius;
                    double y = (py + 0.5d - s / 2.0d) / radius;
                    double distance = distance(shape, x, y) * radius;
                    image.setPixel(px, py, white(0.5d - distance));
                }
            }
            return register(key, image);
        });
    }

    /** La distance signee (negative dedans), en rayons, de chaque forme. */
    private static double distance(Shape shape, double x, double y) {
        return switch (shape) {
            case DISC -> Math.sqrt(x * x + y * y) - 1.0d;
            case RING -> Math.abs(Math.sqrt(x * x + y * y) - 0.82d) - 0.18d;
            case DIAMOND -> (Math.abs(x) + Math.abs(y) - 1.0d) / Math.sqrt(2.0d);
            case TRIANGLE -> polygon(x, y, TRIANGLE);
            case STAR -> polygon(x, y, STAR);
        };
    }

    private static final double[] TRIANGLE = {0.0d, -1.0d, 0.98d, 0.78d, -0.98d, 0.78d};
    private static final double[] STAR = star();
    /** La fleche de navigation, pointe en haut, encoche a l'arriere. */
    private static final double[] ARROW = {0.0d, -1.0d, 0.74d, 0.86d, 0.0d, 0.44d, -0.74d, 0.86d};

    private static double[] star() {
        double[] points = new double[20];
        for (int i = 0; i < 10; i++) {
            double r = i % 2 == 0 ? 1.0d : 0.46d;
            double a = -Math.PI / 2.0d + i * Math.PI / 5.0d;
            points[i * 2] = Math.cos(a) * r;
            points[i * 2 + 1] = Math.sin(a) * r + 0.08d;
        }
        return points;
    }

    /** La distance signee a un polygone (pair-impair pour le dedans). */
    private static double polygon(double x, double y, double[] points) {
        int n = points.length / 2;
        double best = Double.MAX_VALUE;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = points[j * 2];
            double ay = points[j * 2 + 1];
            double bx = points[i * 2];
            double by = points[i * 2 + 1];
            double dx = bx - ax;
            double dy = by - ay;
            double t = clamp(((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy));
            double ex = x - (ax + t * dx);
            double ey = y - (ay + t * dy);
            best = Math.min(best, ex * ex + ey * ey);
            if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) {
                inside = !inside;
            }
        }
        double d = Math.sqrt(best);
        return inside ? -d : d;
    }

    /**
     * La fleche du joueur, en couleurs : une ombre douce, un filet blanc, le rouge du
     * kit en degrade. Pointe en haut ; on la tourne au dessin.
     */
    static Identifier arrow(int size) {
        int s = Math.max(8, Math.min(256, size));
        return CACHE.computeIfAbsent("arrow_" + s, key -> {
            NativeImage image = new NativeImage(s, s, false);
            double radius = s * 0.36d;
            double outline = Math.max(1.0d, s * 0.055d);
            double blur = Math.max(1.5d, s * 0.09d);
            double offset = s * 0.045d;
            for (int py = 0; py < s; py++) {
                for (int px = 0; px < s; px++) {
                    double cx = px + 0.5d - s / 2.0d;
                    double cy = py + 0.5d - s / 2.0d;
                    double d = polygon(cx / radius, cy / radius, ARROW) * radius;
                    double shadowD = polygon(cx / radius, (cy - offset) / radius, ARROW) * radius;
                    double shadow = 0.5d * Math.pow(clamp(1.0d - shadowD / blur), 2.0d);
                    double edge = clamp(0.5d - d);
                    double fill = clamp(0.5d - (d + outline));
                    double t = clamp((cy / radius + 1.0d) / 2.0d);
                    int top = 0xF2492F;
                    int bottom = 0xC92C1B;
                    int fr = (int) Math.round(((top >> 16) & 0xFF) + (((bottom >> 16) & 0xFF) - ((top >> 16) & 0xFF)) * t);
                    int fg = (int) Math.round(((top >> 8) & 0xFF) + (((bottom >> 8) & 0xFF) - ((top >> 8) & 0xFF)) * t);
                    int fb = (int) Math.round((top & 0xFF) + ((bottom & 0xFF) - (top & 0xFF)) * t);
                    // L'ombre, puis le filet blanc, puis le remplissage par-dessus.
                    double a = shadow;
                    double r = 0;
                    double g = 0;
                    double b = 0;
                    a = edge + a * (1.0d - edge);
                    double whiteShare = a <= 0 ? 0 : edge / a;
                    r = 255.0d * whiteShare;
                    g = 255.0d * whiteShare;
                    b = 255.0d * whiteShare;
                    r = r * (1.0d - fill) + fr * fill;
                    g = g * (1.0d - fill) + fg * fill;
                    b = b * (1.0d - fill) + fb * fill;
                    int alpha = (int) Math.round(clamp(a) * 255.0d);
                    image.setPixel(px, py, alpha << 24 | (int) Math.round(r) << 16 | (int) Math.round(g) << 8
                            | (int) Math.round(b));
                }
            }
            return register(key, image);
        });
    }

    /**
     * Le profil d'un trait de {@code thickness} pixels : une colonne opaque au milieu,
     * un pixel de fondu de chaque cote. La hauteur de la texture est paire.
     */
    static Identifier line(float thickness, int[] height) {
        int quarter = Math.max(2, Math.min(64, Math.round(thickness * 4.0f)));
        double t = quarter / 4.0d;
        int h = 2 * (int) Math.ceil(t / 2.0d + 1.0d);
        height[0] = h;
        return CACHE.computeIfAbsent("line_" + quarter, key -> {
            NativeImage image = new NativeImage(4, h, false);
            for (int py = 0; py < h; py++) {
                double y = Math.abs(py + 0.5d - h / 2.0d);
                int color = white(t / 2.0d + 0.5d - y);
                for (int px = 0; px < 4; px++) {
                    image.setPixel(px, py, color);
                }
            }
            return register(key, image);
        });
    }

    /**
     * Le negatif d'un disque de rayon {@code radius} : opaque hors du cercle. Ses quarts
     * arrondissent les coins de la minimap, peints de la couleur du cadre.
     */
    static Identifier corner(int radius) {
        int r = Math.max(1, Math.min(128, radius));
        return CACHE.computeIfAbsent("corner_" + r, key -> {
            int size = r * 2;
            NativeImage image = new NativeImage(size, size, false);
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    double dx = px + 0.5d - r;
                    double dy = py + 0.5d - r;
                    image.setPixel(px, py, white(Math.sqrt(dx * dx + dy * dy) - r + 0.5d));
                }
            }
            return register(key, image);
        });
    }

    /** Un grand disque lisse avec ses niveaux : les zones (catastrophes, gisements), a toutes les tailles. */
    static Identifier area() {
        if (area == null) {
            NativeImage image = new NativeImage(AREA, AREA, false);
            double radius = AREA / 2.0d - 1.0d;
            for (int py = 0; py < AREA; py++) {
                for (int px = 0; px < AREA; px++) {
                    double dx = px + 0.5d - AREA / 2.0d;
                    double dy = py + 0.5d - AREA / 2.0d;
                    image.setPixel(px, py, white(radius + 0.5d - Math.sqrt(dx * dx + dy * dy)));
                }
            }
            MapTexture texture = new MapTexture("dohy atlas area", AREA, AREA, 9, MapTexture.smooth());
            texture.upload(image, null);
            image.close();
            area = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "atlas_glyph/area");
            Minecraft.getInstance().getTextureManager().register(area, texture);
        }
        return area;
    }

    // -- Dessin (en pixels physiques) ---------------------------------------------------------------

    /** Une forme centree sur (cx, cy), recalee sur le pixel si elle ne tourne pas. */
    static void draw(GuiGraphicsExtractor graphics, Identifier texture, int size, float cx, float cy, int color) {
        int x = Math.round(cx - size / 2.0f);
        int y = Math.round(cy - size / 2.0f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, size, size, size, size, size, size,
                color);
    }

    /** Une forme tournee de {@code angle} radians autour de son centre. */
    static void drawRotated(GuiGraphicsExtractor graphics, Identifier texture, int size, float cx, float cy,
                            float angle, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate(angle);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -size / 2, -size / 2, 0.0f, 0.0f, size, size, size, size,
                size, size, color);
        pose.popMatrix();
    }

    /** Un trait lisse de (x1, y1) a (x2, y2). */
    static void segment(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, float thickness,
                        int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.05f) {
            return;
        }
        int[] height = new int[1];
        Identifier texture = line(thickness, height);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(dy, dx));
        pose.scale(length / 16.0f, 1.0f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, -height[0] / 2, 0.0f, 0.0f, 16, height[0], 4,
                height[0], 4, height[0], color);
        pose.popMatrix();
    }

    /**
     * Un cercle lisse de rayon quelconque, en segments ; seuls ceux qui touchent le
     * rectangle visible se dessinent.
     */
    static void ring(GuiGraphicsExtractor graphics, float cx, float cy, float radius, float thickness, int color,
                     float left, float top, float right, float bottom) {
        if (radius < 1.0f || cx + radius < left || cy + radius < top || cx - radius > right || cy - radius > bottom) {
            return;
        }
        int steps = (int) Math.max(24, Math.min(720, Math.ceil(2.0d * Math.PI * radius / 5.0d)));
        float lastX = cx + radius;
        float lastY = cy;
        float margin = thickness + 2.0f;
        for (int i = 1; i <= steps; i++) {
            double a = i * Math.PI * 2.0d / steps;
            float x = (float) (cx + Math.cos(a) * radius);
            float y = (float) (cy + Math.sin(a) * radius);
            if (Math.max(x, lastX) >= left - margin && Math.min(x, lastX) <= right + margin
                    && Math.max(y, lastY) >= top - margin && Math.min(y, lastY) <= bottom + margin) {
                segment(graphics, lastX, lastY, x, y, thickness, color);
            }
            lastX = x;
            lastY = y;
        }
    }

    /** Le contour lisse d'une ellipse tournee de {@code angle} radians, en segments visibles seulement. */
    static void ellipseRing(GuiGraphicsExtractor graphics, float cx, float cy, float rx, float rz, float angle,
                            float thickness, int color, float left, float top, float right, float bottom) {
        float reach = Math.max(rx, rz);
        if (reach < 1.0f || cx + reach < left || cy + reach < top || cx - reach > right || cy - reach > bottom) {
            return;
        }
        int steps = (int) Math.max(24, Math.min(720, Math.ceil(2.0d * Math.PI * reach / 5.0d)));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        float lastX = (float) (cx + rx * cos);
        float lastY = (float) (cy + rx * sin);
        float margin = thickness + 2.0f;
        for (int i = 1; i <= steps; i++) {
            double a = i * Math.PI * 2.0d / steps;
            double ex = Math.cos(a) * rx;
            double ez = Math.sin(a) * rz;
            float x = (float) (cx + ex * cos - ez * sin);
            float y = (float) (cy + ex * sin + ez * cos);
            if (Math.max(x, lastX) >= left - margin && Math.min(x, lastX) <= right + margin
                    && Math.max(y, lastY) >= top - margin && Math.min(y, lastY) <= bottom + margin) {
                segment(graphics, lastX, lastY, x, y, thickness, color);
            }
            lastX = x;
            lastY = y;
        }
    }

    /** Une ellipse pleine et lisse (une zone), tournee de {@code angle} radians. */
    static void ellipse(GuiGraphicsExtractor graphics, float cx, float cy, float rx, float rz, float angle,
                        int color) {
        if (rx < 0.5f || rz < 0.5f) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        if (angle != 0.0f) {
            pose.rotate(angle);
        }
        pose.scale(rx / (AREA / 2.0f - 1.0f), rz / (AREA / 2.0f - 1.0f));
        graphics.blit(RenderPipelines.GUI_TEXTURED, area(), -AREA / 2, -AREA / 2, 0.0f, 0.0f, AREA, AREA, AREA, AREA,
                AREA, AREA, color);
        pose.popMatrix();
    }

    /** Les quatre coins arrondis d'un rectangle, peints de {@code color} par-dessus son contenu. */
    static void corners(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        Identifier texture = corner(r);
        int size = r * 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, r, r, r, r, size, size, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + width - r, y, r, 0.0f, r, r, r, r, size, size, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y + height - r, 0.0f, r, r, r, r, r, size, size,
                color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + width - r, y + height - r, r, r, r, r, r, r, size,
                size, color);
    }
}
