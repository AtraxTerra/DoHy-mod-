package fr.atraxterra.dohy.client.ui;

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
 * Le moteur de dessin du kit, dans l'esprit de Zephyr-UI : coins arrondis lisses,
 * degrades, halos et ombres douces.
 *
 * <p>Tout se calcule en pixels physiques : une forme de la maquette tombe sur des
 * pixels entiers, et ses coins sont des quarts de disque deja lisses, generes a la
 * taille exacte ou ils s'affichent. Rien n'est donc etire ni flou, a toutes les
 * echelles d'interface.
 *
 * <p><b>Le moteur Zephyr</b> ({@link Zephyr}) dessine les formes au GPU : coins
 * arrondis, anneaux, cadres, degrades, ombres et halos sont des quads dont le shader
 * calcule le bord, sans texture, et les formes voisines partagent un seul appel de
 * dessin. Un degrade arrondi est un element, plus une bande par ligne de pixels.
 *
 * <p>Sans le moteur (shaders refuses par la carte graphique), les images sont des
 * textures dynamiques, generees une fois et gardees : disques par rayon, halo radial,
 * rampes de degrade. La coche, la croix et la fleche restent des textures dans les deux cas.
 */
public final class Paint {

    private static final Map<Integer, Identifier> DISCS = new HashMap<>();
    private static final Map<Integer, Identifier> CHECKS = new HashMap<>();
    private static final Map<Integer, Identifier> CROSSES = new HashMap<>();
    private static final Map<Long, Identifier> ARROWS = new HashMap<>();
    private static final Map<Integer, Identifier> RINGS = new HashMap<>();
    private static Identifier glow;
    private static Identifier rampVertical;
    private static Identifier rampHorizontal;
    private static final int GLOW_SIZE = 128;
    private static final int RAMP = 256;
    /** Le rayon interieur d'un anneau, en 512e du rayon (voir zephyr_rounded.fsh). */
    private static final int RING_STEPS = 512;

    private final GuiGraphicsExtractor graphics;
    /** Le nombre de pixels physiques par unite du repere courant. */
    private final float unit;

    public Paint(GuiGraphicsExtractor graphics, float unit) {
        this.graphics = graphics;
        this.unit = Math.max(0.25f, unit);
    }

    public float unit() {
        return unit;
    }

    private int p(float value) {
        return Math.round(value * unit);
    }

    /** Une coordonnee du repere courant en pixels physiques, arrondie comme les formes. */
    public int pixels(float value) {
        return p(value);
    }

    /** Passe en pixels physiques le temps d'un dessin. */
    private void begin() {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1.0f / unit, 1.0f / unit);
    }

    private void end() {
        graphics.pose().popMatrix();
    }

    // -- Couleurs ------------------------------------------------------------------

    public static int alpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int from, int to, float t) {
        float c = Math.max(0.0f, Math.min(1.0f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * c);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * c);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * c);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * c);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // -- Formes ----------------------------------------------------------------------

    public void rect(float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        int x0 = p(x);
        int y0 = p(y);
        if (Zephyr.active()) {
            ZephyrShape.builder(Zephyr.ROUNDED).quad(x0, y0, p(x + width), p(y + height), 0.0f, 0.0f, 0.0f, 0.0f,
                    color).submit(graphics);
        } else {
            graphics.fill(x0, y0, p(x + width), p(y + height), color);
        }
        end();
    }

    /** Un rectangle aux coins arrondis, au bord lisse. */
    public void round(float x, float y, float width, float height, float radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        roundPx(p(x), p(y), p(x + width) - p(x), p(y + height) - p(y), p(radius), color);
        end();
    }

    private void roundPx(int x, int y, int w, int h, int r, int color) {
        if (Zephyr.active()) {
            ZephyrShape.Builder shape = ZephyrShape.builder(Zephyr.ROUNDED);
            roundShape(shape, x, y, w, h, r, color, color);
            shape.submit(graphics);
            return;
        }
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (radius <= 0) {
            graphics.fill(x, y, x + w, y + h, color);
            return;
        }
        graphics.fill(x + radius, y, x + w - radius, y + h, color);
        graphics.fill(x, y + radius, x + radius, y + h - radius, color);
        graphics.fill(x + w - radius, y + radius, x + w, y + h - radius, color);
        Identifier disc = disc(radius);
        int size = radius * 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, disc, x, y, 0.0f, 0.0f, radius, radius, radius, radius, size, size,
                color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, disc, x + w - radius, y, radius, 0.0f, radius, radius, radius, radius,
                size, size, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, disc, x, y + h - radius, 0.0f, radius, radius, radius, radius, radius,
                size, size, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, disc, x + w - radius, y + h - radius, radius, radius, radius, radius,
                radius, radius, size, size, color);
    }

    /**
     * Un contour arrondi seul, de {@code line} pixels physiques : l'interieur reste tel
     * quel (un bouton fantome sur le panorama).
     */
    public void outline(float x, float y, float width, float height, float radius, int color, int line) {
        if (width <= 0 || height <= 0 || line <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        int x0 = p(x);
        int y0 = p(y);
        int w = p(x + width) - x0;
        int h = p(y + height) - y0;
        int r = Math.max(line, Math.min(p(radius), Math.min(w, h) / 2));
        if (Zephyr.active()) {
            ZephyrShape.Builder shape = ZephyrShape.builder(Zephyr.ROUNDED);
            ringShape(shape, x0, y0, w, h, r, line, color);
            shape.submit(graphics);
            end();
            return;
        }
        graphics.fill(x0 + r, y0, x0 + w - r, y0 + line, color);
        graphics.fill(x0 + r, y0 + h - line, x0 + w - r, y0 + h, color);
        graphics.fill(x0, y0 + r, x0 + line, y0 + h - r, color);
        graphics.fill(x0 + w - line, y0 + r, x0 + w, y0 + h - r, color);
        Identifier ring = ring(r, line);
        int size = r * 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ring, x0, y0, 0.0f, 0.0f, r, r, r, r, size, size, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ring, x0 + w - r, y0, r, 0.0f, r, r, r, r, size, size, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ring, x0, y0 + h - r, 0.0f, r, r, r, r, r, size, size, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ring, x0 + w - r, y0 + h - r, r, r, r, r, r, r, size, size,
                color);
        end();
    }

    /**
     * Un cadre arrondi : le filet de {@code line} pixels physiques, puis le fond. Le
     * fond doit etre opaque, sinon le filet se voit au travers.
     */
    public void framed(float x, float y, float width, float height, float radius, int fill, int border, int line) {
        if (width <= 0 || height <= 0) {
            return;
        }
        begin();
        int x0 = p(x);
        int y0 = p(y);
        int w = p(x + width) - x0;
        int h = p(y + height) - y0;
        int r = p(radius);
        if (Zephyr.active()) {
            // Le filet puis le fond, dans une seule forme : un seul element, dessine dans cet ordre.
            ZephyrShape.Builder shape = ZephyrShape.builder(Zephyr.ROUNDED);
            roundShape(shape, x0, y0, w, h, r, border, border);
            roundShape(shape, x0 + line, y0 + line, w - 2 * line, h - 2 * line, Math.max(0, r - line), fill, fill);
            shape.submit(graphics);
            end();
            return;
        }
        roundPx(x0, y0, w, h, r, border);
        roundPx(x0 + line, y0 + line, w - 2 * line, h - 2 * line, Math.max(0, r - line), fill);
        end();
    }

    /** Un degrade vertical dans un rectangle arrondi : du haut vers le bas. */
    public void roundGradient(float x, float y, float width, float height, float radius, int top, int bottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        begin();
        int x0 = p(x);
        int y0 = p(y);
        int w = p(x + width) - x0;
        int h = p(y + height) - y0;
        int r = Math.max(0, Math.min(p(radius), Math.min(w, h) / 2));
        if (Zephyr.active()) {
            // Le degrade est porte par les couleurs des sommets : une forme, plus une bande par ligne.
            ZephyrShape.Builder shape = ZephyrShape.builder(Zephyr.ROUNDED);
            roundShape(shape, x0, y0, w, h, r, top, bottom);
            shape.submit(graphics);
            end();
            return;
        }
        // Le bord lisse vient de la forme pleine, de la couleur du milieu.
        roundPx(x0, y0, w, h, r, mix(top, bottom, 0.5f));
        for (int row = 0; row < h; row++) {
            int inset = 0;
            if (row < r) {
                double dy = r - row - 0.5d;
                inset = (int) Math.ceil(r - Math.sqrt(Math.max(0.0d, r * (double) r - dy * dy)));
            } else if (row >= h - r) {
                double dy = row - (h - r) + 0.5d;
                inset = (int) Math.ceil(r - Math.sqrt(Math.max(0.0d, r * (double) r - dy * dy)));
            }
            inset += r > 0 ? 1 : 0;
            if (w - 2 * inset <= 0) {
                continue;
            }
            float t = h <= 1 ? 0.0f : row / (float) (h - 1);
            graphics.fill(x0 + inset, y0 + row, x0 + w - inset, y0 + row + 1, mix(top, bottom, t));
        }
        end();
    }

    /** Un degrade vertical dans un rectangle droit. */
    public void gradient(float x, float y, float width, float height, int top, int bottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        begin();
        int x0 = p(x);
        int y0 = p(y);
        int w = p(x + width) - x0;
        int h = p(y + height) - y0;
        if (Zephyr.active()) {
            ZephyrShape.builder(Zephyr.ROUNDED).quad(x0, y0, x0 + w, y0 + h, 0.0f, 0.0f, 0.0f, 0.0f, top, top,
                    bottom, bottom).submit(graphics);
            end();
            return;
        }
        graphics.fill(x0, y0, x0 + w, y0 + h, bottom);
        graphics.blit(RenderPipelines.GUI_TEXTURED, rampVertical(), x0, y0, 0.0f, 0.0f, w, h, 1, RAMP, 1, RAMP, top);
        end();
    }

    /** Un degrade horizontal dans un rectangle droit : de la gauche vers la droite. */
    public void gradientH(float x, float y, float width, float height, int left, int right) {
        if (width <= 0 || height <= 0) {
            return;
        }
        begin();
        int x0 = p(x);
        int y0 = p(y);
        int w = p(x + width) - x0;
        int h = p(y + height) - y0;
        if (Zephyr.active()) {
            ZephyrShape.builder(Zephyr.ROUNDED).quad(x0, y0, x0 + w, y0 + h, 0.0f, 0.0f, 0.0f, 0.0f, left, right,
                    right, left).submit(graphics);
            end();
            return;
        }
        graphics.fill(x0, y0, x0 + w, y0 + h, right);
        graphics.blit(RenderPipelines.GUI_TEXTURED, rampHorizontal(), x0, y0, 0.0f, 0.0f, w, h, RAMP, 1, RAMP, 1, left);
        end();
    }

    /** Un halo radial centre sur (cx, cy), de {@code radius} unites. */
    public void glow(float cx, float cy, float radius, int color) {
        if (radius <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        int size = Math.max(2, p(radius * 2));
        int left = p(cx) - size / 2;
        int top = p(cy) - size / 2;
        if (Zephyr.active()) {
            // Un quad, l'UV de -1 a 1 : la distance au centre, en rayons.
            ZephyrShape.builder(Zephyr.SHADOW).quad(left, top, left + size, top + size, -1.0f, -1.0f, 1.0f, 1.0f,
                    color).submit(graphics);
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, glow(), left, top, 0.0f, 0.0f, size, size, GLOW_SIZE,
                    GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, color);
        }
        end();
    }

    /**
     * Une ombre douce autour d'un rectangle : le halo coupe en neuf, les coins en
     * quarts, les bords etires, le milieu plein.
     */
    public void shadow(float x, float y, float width, float height, float spread, int color) {
        if (width <= 0 || height <= 0 || spread <= 0) {
            return;
        }
        begin();
        int s = Math.max(1, p(spread));
        int x0 = p(x) - s;
        int y0 = p(y) - s;
        int w = p(x + width) - p(x) + 2 * s;
        int h = p(y + height) - p(y) + 2 * s;
        int innerW = Math.max(0, w - 2 * s);
        int innerH = Math.max(0, h - 2 * s);
        if (Zephyr.active()) {
            shadowShape(x0, y0, w, h, s, innerW, innerH, color);
            end();
            return;
        }
        int half = GLOW_SIZE / 2;
        Identifier texture = glow();
        // Coins.
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0, 0.0f, 0.0f, s, s, half, half, GLOW_SIZE, GLOW_SIZE,
                color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0 + w - s, y0, half, 0.0f, s, s, half, half, GLOW_SIZE,
                GLOW_SIZE, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0 + h - s, 0.0f, half, s, s, half, half, GLOW_SIZE,
                GLOW_SIZE, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0 + w - s, y0 + h - s, half, half, s, s, half, half,
                GLOW_SIZE, GLOW_SIZE, color);
        // Bords : la colonne et la rangee du milieu du halo, etirees.
        if (innerW > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0 + s, y0, half - 1, 0.0f, innerW, s, 1, half,
                    GLOW_SIZE, GLOW_SIZE, color);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0 + s, y0 + h - s, half - 1, half, innerW, s, 1, half,
                    GLOW_SIZE, GLOW_SIZE, color);
        }
        if (innerH > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0 + s, 0.0f, half - 1, s, innerH, half, 1,
                    GLOW_SIZE, GLOW_SIZE, color);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0 + w - s, y0 + s, half, half - 1, s, innerH, half, 1,
                    GLOW_SIZE, GLOW_SIZE, color);
        }
        if (innerW > 0 && innerH > 0) {
            graphics.fill(x0 + s, y0 + s, x0 + s + innerW, y0 + s + innerH, color);
        }
        end();
    }

    /** Un disque plein (pastille, voyant). */
    public void dot(float cx, float cy, float radius, int color) {
        round(cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /** Une coche de {@code size} unites de cote, centree sur (cx, cy) : les polices n'en ont pas. */
    public void check(float cx, float cy, float size, int color) {
        if (size <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        int s = Math.max(4, p(size));
        graphics.blit(RenderPipelines.GUI_TEXTURED, check(s), p(cx) - s / 2, p(cy) - s / 2, 0.0f, 0.0f, s, s, s, s, s,
                s, color);
        end();
    }

    /** Une croix de {@code size} unites de cote, centree sur (cx, cy) : un refus, une erreur. */
    public void cross(float cx, float cy, float size, int color) {
        if (size <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        int s = Math.max(4, p(size));
        graphics.blit(RenderPipelines.GUI_TEXTURED, cross(s), p(cx) - s / 2, p(cy) - s / 2, 0.0f, 0.0f, s, s, s, s, s,
                s, color);
        end();
    }

    /**
     * Une fleche vers la droite (trait et pointe arrondis) dans le rectangle donne,
     * dont la hauteur fixe l'epaisseur du trait : les polices n'en ont pas non plus.
     */
    public void arrow(float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        int x0 = p(x);
        int y0 = p(y);
        int w = Math.max(4, p(x + width) - x0);
        int h = Math.max(4, p(y + height) - y0);
        graphics.blit(RenderPipelines.GUI_TEXTURED, arrow(w, h), x0, y0, 0.0f, 0.0f, w, h, w, h, w, h, color);
        end();
    }

    /**
     * Une texture generee a la taille exacte ou elle s'affiche, en pixels physiques :
     * la region {@code (0, v, width, height)} posee en {@code (px, py)}, teintee de
     * {@code color}. C'est ainsi que les courbes, rasterisees une fois, se dessinent
     * d'un seul trait a chaque image.
     */
    public void mask(Identifier texture, int px, int py, int width, int height, int v, int textureWidth,
                     int textureHeight, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, px, py, 0.0f, v, width, height, width, height,
                textureWidth, textureHeight, color);
        end();
    }

    /**
     * Une image posee sur un rectangle du repere courant, en pixels physiques : la region
     * {@code (u, v, regionWidth, regionHeight)} d'une texture de {@code textureWidth} x
     * {@code textureHeight}, etiree au rectangle et teintee de {@code color}.
     */
    public void image(Identifier texture, float x, float y, float width, float height, float u, float v,
                      int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        if (width <= 0 || height <= 0 || regionWidth <= 0 || regionHeight <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        int x0 = p(x);
        int y0 = p(y);
        int w = Math.max(1, p(x + width) - x0);
        int h = Math.max(1, p(y + height) - y0);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0, u, v, w, h, regionWidth, regionHeight,
                textureWidth, textureHeight, color);
        end();
    }

    /** Un trait horizontal en pointilles, d'un pixel physique d'epaisseur. */
    public void dashes(float x, float y, float width, float dash, float gap, int color) {
        if (width <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        int y0 = p(y);
        int x1 = p(x + width);
        int on = Math.max(1, p(dash));
        int off = Math.max(1, p(gap));
        if (Zephyr.active()) {
            // Tous les traits dans une forme : un element au lieu d'un par trait.
            ZephyrShape.Builder shape = ZephyrShape.builder(Zephyr.ROUNDED);
            for (int cursor = p(x); cursor < x1; cursor += on + off) {
                shape.quad(cursor, y0, Math.min(x1, cursor + on), y0 + 1, 0.0f, 0.0f, 0.0f, 0.0f, color);
            }
            shape.submit(graphics);
            end();
            return;
        }
        for (int cursor = p(x); cursor < x1; cursor += on + off) {
            graphics.fill(cursor, y0, Math.min(x1, cursor + on), y0 + 1, color);
        }
        end();
    }

    /** Un trait vertical en pointilles, d'un pixel physique d'epaisseur. */
    public void dashesV(float x, float y, float height, float dash, float gap, int color) {
        if (height <= 0 || (color >>> 24) == 0) {
            return;
        }
        begin();
        int x0 = p(x);
        int y1 = p(y + height);
        int on = Math.max(1, p(dash));
        int off = Math.max(1, p(gap));
        if (Zephyr.active()) {
            ZephyrShape.Builder shape = ZephyrShape.builder(Zephyr.ROUNDED);
            for (int cursor = p(y); cursor < y1; cursor += on + off) {
                shape.quad(x0, cursor, x0 + 1, Math.min(y1, cursor + on), 0.0f, 0.0f, 0.0f, 0.0f, color);
            }
            shape.submit(graphics);
            end();
            return;
        }
        for (int cursor = p(y); cursor < y1; cursor += on + off) {
            graphics.fill(x0, cursor, x0 + 1, Math.min(y1, cursor + on), color);
        }
        end();
    }

    // -- Formes du moteur Zephyr --------------------------------------------------------

    /**
     * Un rectangle arrondi plein, en pixels physiques, degrade de {@code top} a
     * {@code bottom} : quatre coins (l'UV de 1 au bord exterieur a 0 au coin interieur)
     * et trois bandes pleines (l'UV nul). Les couleurs des sommets suivent la hauteur.
     */
    private static void roundShape(ZephyrShape.Builder shape, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (radius <= 0) {
            shape.quad(x, y, x + w, y + h, 0.0f, 0.0f, 0.0f, 0.0f, top, top, bottom, bottom);
            return;
        }
        int upper = top == bottom ? top : mix(top, bottom, radius / (float) h);
        int lower = top == bottom ? top : mix(top, bottom, (h - radius) / (float) h);
        int right = x + w - radius;
        int below = y + h - radius;
        shape.quad(x, y, x + radius, y + radius, 1.0f, 1.0f, 0.0f, 0.0f, top, top, upper, upper);
        shape.quad(right, y, x + w, y + radius, 0.0f, 1.0f, 1.0f, 0.0f, top, top, upper, upper);
        shape.quad(x, below, x + radius, y + h, 1.0f, 0.0f, 0.0f, 1.0f, lower, lower, bottom, bottom);
        shape.quad(right, below, x + w, y + h, 0.0f, 0.0f, 1.0f, 1.0f, lower, lower, bottom, bottom);
        shape.quad(x + radius, y, right, y + radius, 0.0f, 0.0f, 0.0f, 0.0f, top, top, upper, upper);
        shape.quad(x, y + radius, x + w, below, 0.0f, 0.0f, 0.0f, 0.0f, upper, upper, lower, lower);
        shape.quad(x + radius, below, right, y + h, 0.0f, 0.0f, 0.0f, 0.0f, lower, lower, bottom, bottom);
    }

    /**
     * Un contour arrondi de {@code line} pixels physiques : quatre quarts d'anneau (le
     * rayon interieur porte par la partie entiere de l'UV) et quatre bandes.
     */
    private static void ringShape(ZephyrShape.Builder shape, int x, int y, int w, int h, int r, int line, int color) {
        float inner = Math.max(0, r - line) / (float) r;
        float n = 4.0f * Math.round(inner * RING_STEPS);
        int right = x + w - r;
        int below = y + h - r;
        shape.quad(x, y, x + r, y + r, 1.0f + n, 1.0f + n, n, n, color);
        shape.quad(right, y, x + w, y + r, n, 1.0f + n, 1.0f + n, n, color);
        shape.quad(x, below, x + r, y + h, 1.0f + n, n, n, 1.0f + n, color);
        shape.quad(right, below, x + w, y + h, n, n, 1.0f + n, 1.0f + n, color);
        shape.quad(x + r, y, right, y + line, 0.0f, 0.0f, 0.0f, 0.0f, color);
        shape.quad(x + r, y + h - line, right, y + h, 0.0f, 0.0f, 0.0f, 0.0f, color);
        shape.quad(x, y + r, x + line, below, 0.0f, 0.0f, 0.0f, 0.0f, color);
        shape.quad(x + w - line, y + r, x + w, below, 0.0f, 0.0f, 0.0f, 0.0f, color);
    }

    /**
     * L'ombre douce coupee en neuf : les coins mesurent depuis leur coin interieur, les
     * bords depuis leur bord interieur, le milieu est plein.
     */
    private void shadowShape(int x0, int y0, int w, int h, int s, int innerW, int innerH, int color) {
        ZephyrShape.Builder shape = ZephyrShape.builder(Zephyr.SHADOW);
        int right = x0 + w - s;
        int below = y0 + h - s;
        shape.quad(x0, y0, x0 + s, y0 + s, 1.0f, 1.0f, 0.0f, 0.0f, color);
        shape.quad(right, y0, x0 + w, y0 + s, 0.0f, 1.0f, 1.0f, 0.0f, color);
        shape.quad(x0, below, x0 + s, y0 + h, 1.0f, 0.0f, 0.0f, 1.0f, color);
        shape.quad(right, below, x0 + w, y0 + h, 0.0f, 0.0f, 1.0f, 1.0f, color);
        if (innerW > 0) {
            shape.quad(x0 + s, y0, x0 + s + innerW, y0 + s, 0.0f, 1.0f, 0.0f, 0.0f, color);
            shape.quad(x0 + s, below, x0 + s + innerW, y0 + h, 0.0f, 0.0f, 0.0f, 1.0f, color);
        }
        if (innerH > 0) {
            shape.quad(x0, y0 + s, x0 + s, y0 + s + innerH, 1.0f, 0.0f, 0.0f, 0.0f, color);
            shape.quad(right, y0 + s, x0 + w, y0 + s + innerH, 0.0f, 0.0f, 1.0f, 0.0f, color);
        }
        if (innerW > 0 && innerH > 0) {
            shape.quad(x0 + s, y0 + s, x0 + s + innerW, y0 + s + innerH, 0.0f, 0.0f, 0.0f, 0.0f, color);
        }
        shape.submit(graphics);
    }

    // -- Textures generees ------------------------------------------------------------

    /**
     * Enregistre une image generee hors du kit (une courbe) ; a rendre par
     * {@link #release} quand elle ne sert plus, sinon la carte graphique la garde.
     */
    public static Identifier registerGenerated(String name, NativeImage image) {
        return register(name, image);
    }

    public static void release(Identifier id) {
        Minecraft.getInstance().getTextureManager().release(id);
    }

    private static Identifier register(String name, NativeImage image) {
        Identifier id = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "kit2/" + name);
        DynamicTexture texture = new DynamicTexture(() -> "dohy kit " + name, image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }

    private static int white(float alpha) {
        int a = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
        return (a << 24) | 0xFFFFFF;
    }

    /** Un disque blanc de rayon {@code radius} pixels, au bord lisse (4 x 4 echantillons par pixel). */
    private static Identifier disc(int radius) {
        int r = Math.min(radius, 160);
        return DISCS.computeIfAbsent(r, key -> {
            int size = key * 2;
            NativeImage image = new NativeImage(size, size, true);
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    int inside = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            double dx = px + (sx + 0.5d) / 4.0d - key;
                            double dy = py + (sy + 0.5d) / 4.0d - key;
                            if (dx * dx + dy * dy <= key * (double) key) {
                                inside++;
                            }
                        }
                    }
                    image.setPixel(px, py, white(inside / 16.0f));
                }
            }
            return register("disc_" + key, image);
        });
    }

    /** Un anneau blanc de rayon {@code radius} et d'epaisseur {@code line} pixels, au bord lisse. */
    private static Identifier ring(int radius, int line) {
        int r = Math.min(radius, 160);
        int l = Math.min(line, r);
        return RINGS.computeIfAbsent(r * 1000 + l, key -> {
            int size = r * 2;
            double outer = r * (double) r;
            double inner = (r - l) * (double) (r - l);
            NativeImage image = new NativeImage(size, size, true);
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    int inside = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            double dx = px + (sx + 0.5d) / 4.0d - r;
                            double dy = py + (sy + 0.5d) / 4.0d - r;
                            double d = dx * dx + dy * dy;
                            if (d <= outer && d >= inner) {
                                inside++;
                            }
                        }
                    }
                    image.setPixel(px, py, white(inside / 16.0f));
                }
            }
            return register("ring_" + r + "_" + l, image);
        });
    }

    /** Une coche blanche de {@code size} pixels, au trait lisse. */
    private static Identifier check(int size) {
        int key = Math.min(size, 128);
        return CHECKS.computeIfAbsent(key, value -> {
            NativeImage image = new NativeImage(value, value, true);
            double stroke = 0.075d;
            for (int py = 0; py < value; py++) {
                for (int px = 0; px < value; px++) {
                    int inside = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            double x = (px + (sx + 0.5d) / 4.0d) / value;
                            double y = (py + (sy + 0.5d) / 4.0d) / value;
                            double d = Math.min(segment(x, y, 0.20d, 0.53d, 0.41d, 0.74d),
                                    segment(x, y, 0.41d, 0.74d, 0.80d, 0.29d));
                            if (d <= stroke) {
                                inside++;
                            }
                        }
                    }
                    image.setPixel(px, py, white(inside / 16.0f));
                }
            }
            return register("check_" + value, image);
        });
    }

    /** Une croix blanche de {@code size} pixels, aux traits lisses et arrondis. */
    private static Identifier cross(int size) {
        int key = Math.min(size, 128);
        return CROSSES.computeIfAbsent(key, value -> {
            NativeImage image = new NativeImage(value, value, true);
            double stroke = 0.085d;
            for (int py = 0; py < value; py++) {
                for (int px = 0; px < value; px++) {
                    int inside = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            double x = (px + (sx + 0.5d) / 4.0d) / value;
                            double y = (py + (sy + 0.5d) / 4.0d) / value;
                            double d = Math.min(segment(x, y, 0.24d, 0.24d, 0.76d, 0.76d),
                                    segment(x, y, 0.76d, 0.24d, 0.24d, 0.76d));
                            if (d <= stroke) {
                                inside++;
                            }
                        }
                    }
                    image.setPixel(px, py, white(inside / 16.0f));
                }
            }
            return register("cross_" + value, image);
        });
    }

    /**
     * Une fleche blanche de {@code width} x {@code height} pixels : le trait du milieu
     * et les deux branches de la pointe, au bout arrondi, comme la fleche des maquettes.
     */
    private static Identifier arrow(int width, int height) {
        int w = Math.min(width, 256);
        int h = Math.min(height, 128);
        long key = ((long) w << 32) | h;
        return ARROWS.computeIfAbsent(key, value -> {
            NativeImage image = new NativeImage(w, h, true);
            // En unites de hauteur : demi-epaisseur du trait, puis la pointe.
            double half = 0.093d;
            double length = (double) w / h;
            double tipX = length - half;
            double armX = tipX - 0.52d;
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    int inside = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            double ux = (px + (sx + 0.5d) / 4.0d) / h;
                            double uy = (py + (sy + 0.5d) / 4.0d) / h;
                            double d = Math.min(segment(ux, uy, half, 0.5d, tipX - 0.04d, 0.5d),
                                    Math.min(segment(ux, uy, armX, half, tipX, 0.5d),
                                            segment(ux, uy, armX, 1.0d - half, tipX, 0.5d)));
                            if (d <= half) {
                                inside++;
                            }
                        }
                    }
                    image.setPixel(px, py, white(inside / 16.0f));
                }
            }
            return register("arrow_" + w + "x" + h, image);
        });
    }

    /** La distance du point (x, y) au segment (ax, ay) - (bx, by). */
    private static double segment(double x, double y, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double t = Math.max(0.0d, Math.min(1.0d, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
        double ex = x - (ax + t * dx);
        double ey = y - (ay + t * dy);
        return Math.sqrt(ex * ex + ey * ey);
    }

    private static Identifier glow() {
        if (glow == null) {
            NativeImage image = new NativeImage(GLOW_SIZE, GLOW_SIZE, true);
            double center = GLOW_SIZE / 2.0d;
            for (int py = 0; py < GLOW_SIZE; py++) {
                for (int px = 0; px < GLOW_SIZE; px++) {
                    double d = Math.sqrt(Math.pow(px + 0.5d - center, 2) + Math.pow(py + 0.5d - center, 2)) / center;
                    double t = Math.max(0.0d, 1.0d - d);
                    image.setPixel(px, py, white((float) (t * t * (3.0d - 2.0d * t))));
                }
            }
            glow = register("glow", image);
        }
        return glow;
    }

    private static Identifier rampVertical() {
        if (rampVertical == null) {
            NativeImage image = new NativeImage(1, RAMP, true);
            for (int py = 0; py < RAMP; py++) {
                image.setPixel(0, py, white(1.0f - (py + 0.5f) / RAMP));
            }
            rampVertical = register("ramp_v", image);
        }
        return rampVertical;
    }

    private static Identifier rampHorizontal() {
        if (rampHorizontal == null) {
            NativeImage image = new NativeImage(RAMP, 1, true);
            for (int px = 0; px < RAMP; px++) {
                image.setPixel(px, 0, white(1.0f - (px + 0.5f) / RAMP));
            }
            rampHorizontal = register("ramp_h", image);
        }
        return rampHorizontal;
    }
}
