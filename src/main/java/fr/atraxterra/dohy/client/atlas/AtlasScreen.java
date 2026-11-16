package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.client.disaster.DisasterClient;
import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.earth.EarthAtlas;
import fr.atraxterra.dohy.earth.disaster.DisasterNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * La grande carte (touche M), dans le style des menus : la carte occupe tout
 * l'ecran, les reglages flottent dans des cartes arrondies. Glisser pour se
 * deplacer, la molette pour un zoom fluide (vers le curseur), clic droit pour
 * poser un point de passage, clic sur un point pour le modifier, C pour revenir
 * sur soi.
 */
public final class AtlasScreen extends Screen {

    private static final double MIN_SCALE = 0.0625d;
    private static final double MAX_SCALE = 128.0d;
    private static final float DESIGN_WIDTH = 608.0f;
    private static final float DESIGN_HEIGHT = 334.0f;
    private static final float MARGIN = 10.0f;
    private static final float PANEL = 132.0f;
    private static final float ROW = 13.3f;
    private static final float CLOSE = 17.3f;
    private static final float INFO = 176.0f;
    /** Les zooms proposes pour la minimap, en blocs par pixel. */
    private static final double[] MINIMAP_ZOOMS = {0.25d, 0.5d, 1.0d, 2.0d, 4.0d};
    private static final String[] MINIMAP_ZOOM_LABELS = {"X4", "X2", "X1", "1/2", "1/4"};

    /** Un rectangle cliquable de l'habillage, en unites de maquette. */
    private record Hit(float x, float y, float width, float height, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + width && my < y + height;
        }
    }

    /** Ce que montre le curseur : un gisement, un point, une catastrophe. */
    private record Focus(String title, String subtitle, int color, ItemStack icon, EarthAtlas.Mark mark,
                         Waypoints.Point point) {
    }

    private double centerX;
    private double centerZ;
    private double scale = 2.0d;
    private double targetScale = 2.0d;
    private boolean anchored;
    private double anchorX;
    private double anchorY;
    private double anchorWorldX;
    private double anchorWorldZ;
    private boolean returning;
    private double lastFrame = -1.0d;
    private float gui = 1.0f;
    private float unit = 1.0f;
    private float designWidth = DESIGN_WIDTH;
    private float designHeight = DESIGN_HEIGHT;
    private final List<Hit> hits = new ArrayList<>();
    /** Les cartes de l'habillage : la carte ne prend pas les clics qui tombent dessus. */
    private final List<float[]> panels = new ArrayList<>();
    private final Map<String, Anim.Value> hovers = new HashMap<>();
    private final double opened = Anim.now();
    private boolean dragging;
    private double pressX;
    private double pressY;

    public AtlasScreen() {
        super(Component.literal("Atlas"));
    }

    /** La grande carte a ce zoom (blocs par pixel d'interface), centree sur le joueur. */
    public AtlasScreen(double scale) {
        this();
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        this.targetScale = this.scale;
    }

    @Override
    protected void init() {
        LocalPlayer player = minecraft.player;
        if (player != null && lastFrame < 0) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
        gui = Math.max(1, minecraft.getWindow().getGuiScale());
        float fit = Math.min(width * 0.96f / DESIGN_WIDTH, height * 0.96f / DESIGN_HEIGHT);
        float physical = (float) Math.floor(fit * gui);
        unit = physical >= 1.0f ? physical : Math.max(0.5f, fit * gui);
        designWidth = width * gui / unit;
        designHeight = height * gui / unit;
    }

    private String dimension() {
        return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
    }

    private AtlasView.Frame frame() {
        return new AtlasView.Frame(0, 0, width, height, centerX, centerZ, scale, gui);
    }

    /** Une unite de maquette, en pixels d'interface. */
    private float design() {
        return unit / gui;
    }

    private double designX(double screenX) {
        return screenX / design();
    }

    private double designY(double screenY) {
        return screenY / design();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, AtlasView.BACKDROP);
    }

    // -- Rendu --------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        animate();
        AtlasMap map = AtlasClient.map();
        if (map != null) {
            map.upload();
        }
        AtlasClient.Options options = AtlasClient.options();
        String dimension = dimension();
        AtlasView.Frame frame = frame();
        float du = design();
        AtlasView.Style style = new AtlasView.Style(5.6f * du, 3.6f * du, 5.0f * du, 0.45f);
        AtlasView.draw(graphics, font, map, frame,
                new AtlasView.Layers(options.holders, options.marks, options.waypoints, true, options.disasters),
                style, dimension);
        double mx = designX(mouseX);
        double my = designY(mouseY);
        boolean overPanel = overPanel(mx, my);
        Focus focus = overPanel ? null : focus(frame, mouseX, mouseY, style, dimension, options);
        UiGraphics ui = new UiGraphics(graphics, font, mouseX, mouseY);
        Paint paint = new Paint(graphics, gui);
        if (focus != null && focus.mark() != null) {
            // Le repere survole, en plus grand, avec son nom.
            float badge = AtlasView.badgeRadius(style, scale) * 1.3f;
            float x = frame.snap(frame.screenX(focus.mark().x()));
            float y = frame.snap(frame.screenY(focus.mark().z()));
            paint.glow(x, y, badge * 2.5f, Paint.alpha(AtlasView.resourceColor(focus.mark().resource()), 0.35f));
            AtlasView.badge(ui, paint, focus.mark(), x, y, badge);
            if (scale > AtlasView.MARK_LABELS) {
                AtlasView.label(ui, paint, focus.mark().name(), x, y + badge + 2.5f * du, style.label(), 0xFFFFFFFF);
            }
        } else if (focus != null && focus.point() != null && scale > AtlasView.POINT_LABELS) {
            Waypoints.Point point = focus.point();
            float x = frame.snap(frame.screenX(point.x() + 0.5d));
            float y = frame.snap(frame.screenY(point.z() + 0.5d));
            AtlasView.label(ui, paint, point.name(), x, y + style.diamond() + 2.5f * du, style.label(), point.color());
        }
        player(graphics, paint, frame, du);
        graphics.nextStratum();
        overlay(graphics, mouseX, mouseY, frame, focus, dimension, options);
    }

    /** Le zoom glisse vers sa cible en gardant le point vise sous le curseur ; le retour sur soi glisse aussi. */
    private void animate() {
        double now = Anim.now();
        double delta = lastFrame < 0 ? 0.0d : Math.min(0.1d, now - lastFrame);
        lastFrame = now;
        if (scale != targetScale) {
            double k = 1.0d - Math.exp(-delta * 16.0d);
            double next = Math.exp(Math.log(scale) + (Math.log(targetScale) - Math.log(scale)) * k);
            if (Math.abs(Math.log(next / targetScale)) < 0.003d) {
                next = targetScale;
            }
            scale = next;
            if (anchored) {
                centerX = anchorWorldX - (anchorX - width / 2.0d) * scale;
                centerZ = anchorWorldZ - (anchorY - height / 2.0d) * scale;
            }
        } else {
            anchored = false;
        }
        if (returning && minecraft.player != null) {
            double k = 1.0d - Math.exp(-delta * 10.0d);
            double tx = minecraft.player.getX();
            double tz = minecraft.player.getZ();
            centerX += (tx - centerX) * k;
            centerZ += (tz - centerZ) * k;
            if (Math.hypot(tx - centerX, tz - centerZ) < scale * 0.3d) {
                centerX = tx;
                centerZ = tz;
                returning = false;
            }
        }
    }

    /** La fleche du joueur et son halo, qui bat doucement. */
    private void player(GuiGraphicsExtractor graphics, Paint paint, AtlasView.Frame frame, float du) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        double x = frame.screenX(player.getX());
        double y = frame.screenY(player.getZ());
        if (!frame.inside(x, y, 20.0d)) {
            return;
        }
        float pulse = Anim.pulse(1.6d);
        paint.glow((float) x, (float) y, (9.0f + 4.0f * pulse) * du, Paint.alpha(Ui.Theme.RED, 0.30f + 0.15f * pulse));
        graphics.nextStratum();
        AtlasView.player(graphics, gui, x, y, player.getYRot(), 13.0f * du);
    }

    // -- L'habillage --------------------------------------------------------------------------------

    private void overlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, AtlasView.Frame frame, Focus focus,
                         String dimension, AtlasClient.Options options) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(design(), design());
        Ui.drawUnit(unit);
        UiGraphics g = new UiGraphics(graphics, font, mouseX, mouseY);
        Paint p = new Paint(graphics, unit);
        double mx = designX(mouseX);
        double my = designY(mouseY);
        hits.clear();
        panels.clear();
        float lift = 4.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.25d)));
        // Un voile en haut et en bas : les cartes se detachent de la carte.
        veil(p, 0.0f, 46.0f, 0.55f, true);
        veil(p, designHeight - 56.0f, 56.0f, 0.6f, false);
        layers(g, p, MARGIN, MARGIN + lift, mx, my, dimension, options);
        close(g, p, designWidth - MARGIN - CLOSE, MARGIN + lift, mx, my);
        position(g, p, designWidth - MARGIN - CLOSE - 4.0f, MARGIN + lift, mx, my);
        float bottom = designHeight - MARGIN - lift;
        info(g, p, frame, focus, MARGIN, bottom, dimension, mouseX, mouseY, mx, my);
        float zoomLeft = zoom(g, p, designWidth - MARGIN, bottom, frame, mx, my);
        hint(g, p, MARGIN + INFO + 8.0f, zoomLeft - 8.0f, bottom);
        pose.popMatrix();
    }

    /** Un degrade du noir vers le transparent, en fines bandes (vers le bas si {@code down}). */
    private void veil(Paint p, float y, float height, float alpha, boolean down) {
        int bands = 28;
        float step = height / bands;
        for (int band = 0; band < bands; band++) {
            float t = (band + 0.5f) / bands;
            float strength = down ? 1.0f - t : t;
            p.rect(0, y + band * step, designWidth, step + 0.01f,
                    Paint.alpha(0xFF000000, alpha * strength * strength));
        }
    }

    private float hover(String key, boolean over) {
        return hovers.computeIfAbsent(key, k -> new Anim.Value(0.0f, 16.0f)).toward(over ? 1.0f : 0.0f);
    }

    private float animated(String key, float target, float initial) {
        return hovers.computeIfAbsent(key, k -> new Anim.Value(initial, 18.0f)).toward(target);
    }

    private boolean overPanel(double mx, double my) {
        for (float[] panel : panels) {
            if (mx >= panel[0] && my >= panel[1] && mx < panel[0] + panel[2] && my < panel[1] + panel[3]) {
                return true;
            }
        }
        return false;
    }

    /** Une carte du kit : ombre, fond, filet. */
    private void card(Paint p, float x, float y, float width, float height) {
        p.shadow(x + 3, y + 5, width - 6, height - 5, 14.0f, Paint.alpha(Ui.Theme.SHADOW, 0.55f));
        p.framed(x, y, width, height, Shell.RADIUS, Ui.Theme.BG2, 0xFF2C2E33, 1);
        panels.add(new float[] {x, y, width, height});
    }

    /** Le panneau des calques et de la minimap. */
    private void layers(UiGraphics g, Paint p, float x, float y, double mx, double my, String dimension,
                        AtlasClient.Options options) {
        float inner = PANEL - 20.0f;
        float height = 38.0f + 14.0f + ROW * 5 + 10.0f + 14.0f + ROW + 2.0f + 2 * 16.7f + 6.0f;
        card(p, x, y, PANEL, height);
        g.textShaded(Ui.TITLE, "ATLAS", x + 10.0f, y + 8.7f, 16.0f, Ui.Theme.RED, Ui.Theme.RED_DD, 1.33f);
        g.text(Ui.BODY, place(dimension), x + 10.0f, y + 27.3f, 5.0f, Ui.Theme.MUTED);
        float rowY = y + 38.0f;
        Shell.section(g, p, x + 10.0f, rowY, inner, "resources", "CALQUES", "");
        rowY += 14.0f;
        rowY = toggle(g, p, x + 10.0f, rowY, inner, "Pays tenus", options.holders,
                () -> options.holders = !options.holders, mx, my, "holders");
        rowY = toggle(g, p, x + 10.0f, rowY, inner, "Gisements et lieux", options.marks,
                () -> options.marks = !options.marks, mx, my, "marks");
        rowY = toggle(g, p, x + 10.0f, rowY, inner, "Points de passage", options.waypoints,
                () -> options.waypoints = !options.waypoints, mx, my, "points");
        rowY = toggle(g, p, x + 10.0f, rowY, inner, "Points dans le monde", options.markers,
                () -> options.markers = !options.markers, mx, my, "markers");
        rowY = toggle(g, p, x + 10.0f, rowY, inner, "Catastrophes", options.disasters,
                () -> options.disasters = !options.disasters, mx, my, "disasters");
        rowY += 10.0f;
        Shell.section(g, p, x + 10.0f, rowY, inner, "craft", "MINIMAP", "");
        rowY += 14.0f;
        rowY = toggle(g, p, x + 10.0f, rowY, inner, "Afficher", options.minimap,
                () -> options.minimap = !options.minimap, mx, my, "minimap");
        rowY += 2.0f;
        String[] sizes = new String[AtlasClient.SIZES.length];
        int size = -1;
        for (int index = 0; index < sizes.length; index++) {
            sizes[index] = String.valueOf(AtlasClient.SIZES[index]);
            if (AtlasClient.SIZES[index] == options.minimapSize) {
                size = index;
            }
        }
        g.text(Ui.PIXEL, "TAILLE", x + 10.0f, rowY + 4.2f, 3.3f, Ui.Theme.MUTED);
        segments(g, p, x + 40.0f, rowY, inner - 30.0f, sizes, size,
                index -> options.minimapSize = AtlasClient.SIZES[index], mx, my, "size");
        rowY += 16.7f;
        int zoom = -1;
        for (int index = 0; index < MINIMAP_ZOOMS.length; index++) {
            if (Math.abs(MINIMAP_ZOOMS[index] - options.minimapScale) < 1.0e-6d) {
                zoom = index;
            }
        }
        g.text(Ui.PIXEL, "ZOOM", x + 10.0f, rowY + 4.2f, 3.3f, Ui.Theme.MUTED);
        segments(g, p, x + 40.0f, rowY, inner - 30.0f, MINIMAP_ZOOM_LABELS, zoom,
                index -> options.minimapScale = MINIMAP_ZOOMS[index], mx, my, "zoom");
    }

    /** Le nom du monde affiche. */
    private static String place(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> AtlasData.globe() != null ? "La Terre \u00B7 carte du monde" : "Le Monde";
            case "minecraft:the_nether" -> "Le Nether";
            case "minecraft:the_end" -> "L'End";
            default -> {
                int colon = dimension.indexOf(':');
                String name = (colon < 0 ? dimension : dimension.substring(colon + 1)).replace('_', ' ');
                yield name.isEmpty() ? "Carte" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        };
    }

    /** Une rangee avec son interrupteur. */
    private float toggle(UiGraphics g, Paint p, float x, float y, float width, String label, boolean on,
                         Runnable action, double mx, double my, String key) {
        boolean over = mx >= x - 3.0f && mx < x + width + 3.0f && my >= y && my < y + ROW;
        float hover = hover("row:" + key, over);
        float knob = animated("knob:" + key, on ? 1.0f : 0.0f, on ? 1.0f : 0.0f);
        if (hover > 0.01f) {
            p.round(x - 3.0f, y, width + 6.0f, ROW, 3.7f, Paint.alpha(0xFFFFFFFF, 0.045f * hover));
        }
        g.text(Ui.BOLD, label, x, y + 4.0f, 5.33f, Paint.mix(Ui.Theme.TEXT2, Ui.Theme.TEXT, Math.max(hover, knob * 0.6f)));
        float switchWidth = 15.3f;
        float switchHeight = 8.6f;
        float sx = x + width - switchWidth;
        float sy = y + (ROW - switchHeight) / 2.0f;
        if (knob > 0.01f) {
            p.glow(sx + switchWidth / 2.0f, sy + switchHeight / 2.0f, 11.0f, Paint.alpha(Ui.Theme.GREEN, 0.22f * knob));
        }
        p.framed(sx, sy, switchWidth, switchHeight, switchHeight / 2.0f, Paint.mix(0xFF1B1C1F, 0xFF2A9A1C, knob),
                Paint.mix(0xFF35373D, 0xFF3FD52C, knob), 1);
        float radius = switchHeight / 2.0f - 1.4f;
        float kx = sx + switchHeight / 2.0f + (switchWidth - switchHeight) * knob;
        p.dot(kx, sy + switchHeight / 2.0f + 0.3f, radius + 0.4f, 0x40000000);
        p.dot(kx, sy + switchHeight / 2.0f, radius, Paint.mix(0xFF8E9198, 0xFFFFFFFF, knob));
        hits.add(new Hit(x - 3.0f, y, width + 6.0f, ROW, () -> {
            UiScreen.click(on ? 0.9f : 1.1f);
            action.run();
            AtlasClient.saveOptions();
        }));
        return y + ROW;
    }

    /** Un choix parmi quelques valeurs : la valeur choisie en rouge, comme l'onglet actif. */
    private void segments(UiGraphics g, Paint p, float x, float y, float width, String[] labels, int selected,
                          IntConsumer action, double mx, double my, String key) {
        float height = 12.0f;
        p.framed(x, y, width, height, 3.7f, Ui.Theme.INSET, 0xFF24262A, 1);
        float step = (width - 2.0f) / labels.length;
        for (int index = 0; index < labels.length; index++) {
            float bx = x + 1.0f + index * step;
            boolean over = mx >= bx && mx < bx + step && my >= y && my < y + height;
            float hover = hover(key + ":" + index, over);
            if (index == selected) {
                p.shadow(bx + 1.5f, y + 3.0f, step - 3.0f, height - 4.0f, 4.0f, Paint.alpha(Ui.Theme.RED, 0.25f));
                p.roundGradient(bx + 0.7f, y + 1.3f, step - 1.4f, height - 2.6f, 2.7f, Ui.Theme.RED_LIGHT,
                        Ui.Theme.RED_D);
            } else if (hover > 0.01f) {
                p.round(bx + 0.7f, y + 1.3f, step - 1.4f, height - 2.6f, 2.7f, Paint.alpha(0xFFFFFFFF, 0.06f * hover));
            }
            g.centered(Ui.WIDE, labels[index], bx + step / 2.0f, y + 4.2f, 3.8f,
                    index == selected ? Ui.Theme.TEXT : Paint.mix(Ui.Theme.MUTED, Ui.Theme.TEXT, hover));
            int chosen = index;
            hits.add(new Hit(bx, y, step, height, () -> {
                UiScreen.click(1.0f);
                action.accept(chosen);
                AtlasClient.saveOptions();
            }));
        }
    }

    /** La croix de fermeture, comme dans les menus. */
    private void close(UiGraphics g, Paint p, float x, float y, double mx, double my) {
        boolean over = mx >= x && my >= y && mx < x + CLOSE && my < y + CLOSE;
        float hover = hover("close", over);
        p.shadow(x + 2, y + 4, CLOSE - 4, CLOSE - 4, 8.0f, Paint.alpha(Ui.Theme.SHADOW, 0.5f));
        p.framed(x, y, CLOSE, CLOSE, 4.7f, Paint.mix(Ui.Theme.CARD, Ui.Theme.RED, hover),
                Paint.mix(0xFF2E3035, Ui.Theme.RED, hover), 1);
        if (hover > 0.05f) {
            p.glow(x + CLOSE / 2, y + CLOSE / 2, 16, Paint.alpha(Ui.Theme.RED, 0.35f * hover));
        }
        g.texture(Ui.CLOSE, Math.round(x + CLOSE / 2 - 3.5f), Math.round(y + CLOSE / 2 - 3.5f), 7, 7, 32, 32);
        panels.add(new float[] {x, y, CLOSE, CLOSE});
        hits.add(new Hit(x, y, CLOSE, CLOSE, () -> {
            UiScreen.click(0.8f);
            onClose();
        }));
    }

    /** La puce de ta position, a gauche de la croix : un clic recentre la carte. */
    private void position(UiGraphics g, Paint p, float right, float y, double mx, double my) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        String value = player.getBlockX() + "  " + player.getBlockY() + "  " + player.getBlockZ();
        float width = Math.max(g.width(Ui.WIDE, value, 6.3f), g.width(Ui.PIXEL, "TA POSITION", 3.3f)) + 13.4f;
        float x = right - width;
        float top = y - 2.0f;
        float height = 21.3f;
        boolean over = mx >= x && my >= top && mx < right && my < top + height;
        float hover = hover("position", over);
        p.shadow(x + 3, top + 5, width - 6, height - 5, 10.0f, Paint.alpha(Ui.Theme.SHADOW, 0.5f));
        p.framed(x, top, width, height, 4.7f, Paint.mix(Ui.Theme.CARD, Ui.Theme.CARD_HOVER, hover),
                Paint.mix(0xFF2E3035, 0xFF45484F, hover), 1);
        g.text(Ui.PIXEL, "TA POSITION", x + 6.7f, top + 5.6f, 3.3f, Ui.Theme.MUTED);
        g.text(Ui.WIDE, value, x + 6.7f, top + 11.4f, 6.3f, Ui.Theme.TEXT);
        panels.add(new float[] {x, top, width, height});
        hits.add(new Hit(x, top, width, height, () -> {
            UiScreen.click(1.0f);
            recenter();
        }));
    }

    /** La carte d'information en bas a gauche : le lieu vise (ou le tien), son pays, ce qui s'y trouve. */
    private void info(UiGraphics g, Paint p, AtlasView.Frame frame, Focus focus, float x, float bottom,
                      String dimension, int mouseX, int mouseY, double mx, double my) {
        boolean cursor = !overPanel(mx, my) && mouseX >= 0 && mouseY >= 0 && mouseX < width && mouseY < height;
        double wx;
        double wz;
        String label;
        if (cursor) {
            wx = frame.worldX(mouseX);
            wz = frame.worldZ(mouseY);
            label = "AU CURSEUR";
        } else if (minecraft.player != null) {
            wx = minecraft.player.getX();
            wz = minecraft.player.getZ();
            label = "TA POSITION";
        } else {
            wx = centerX;
            wz = centerZ;
            label = "CENTRE";
        }
        String coordinates = (long) Math.floor(wx) + "   " + (long) Math.floor(wz);
        String country = null;
        String holder = null;
        int holderColor = 0;
        AtlasData.Globe globe = AtlasData.globe();
        if (globe != null && "minecraft:overworld".equals(dimension)) {
            EarthAtlas.Country found = globe.country(wx, wz);
            country = found == null ? "Eaux internationales" : found.name();
            EarthAtlas.Owner owner = found == null ? null : AtlasData.owner(found.id());
            if (owner != null) {
                holder = owner.holder();
                holderColor = switch (owner.kind()) {
                    case 1 -> AtlasGlobe.MINE;
                    case 2 -> AtlasGlobe.OTHER_PLAYER;
                    default -> AtlasGlobe.NATIONS[Math.floorMod(owner.nation(), AtlasGlobe.NATIONS.length)];
                };
            }
        }
        float height = 30.0f + (country != null ? 10.0f : 0.0f) + (focus != null ? 24.0f : 0.0f);
        float y = bottom - height;
        card(p, x, y, INFO, height);
        g.text(Ui.PIXEL, label, x + 9.0f, y + 8.0f, 3.3f, Ui.Theme.MUTED);
        g.text(Ui.WIDE, coordinates, x + 9.0f, y + 14.3f, 7.0f, Ui.Theme.TEXT);
        float line = y + 26.0f;
        if (country != null) {
            g.text(Ui.BOLD, g.fit(Ui.BOLD, country, 5.33f, INFO - 18.0f - (holder == null ? 0.0f : 52.0f)),
                    x + 9.0f, line, 5.33f, Ui.Theme.TEXT2);
            if (holder != null) {
                Shell.pill(g, p, g.fit(Ui.PIXEL, holder.toUpperCase(Locale.ROOT), 3.3f, 44.0f), x + INFO - 9.0f,
                        line - 1.3f, holderColor, false);
            }
            line += 10.0f;
        }
        if (focus != null) {
            p.rect(x + 9.0f, line - 1.0f, INFO - 18.0f, 0.34f, 0xFF2C2E33);
            float iconX = x + 9.0f;
            float iconY = line + 3.0f;
            p.framed(iconX, iconY, 16.0f, 16.0f, 4.0f, Paint.mix(0xFF1A1B1E, focus.color(), 0.14f),
                    Paint.mix(0xFF2A2C31, focus.color(), 0.45f), 1);
            if (!focus.icon().isEmpty()) {
                g.item(focus.icon(), iconX + 2.3f, iconY + 2.3f, 11.4f, false);
            } else {
                p.dot(iconX + 8.0f, iconY + 8.0f, 3.3f, focus.color());
            }
            float textX = iconX + 21.0f;
            g.text(Ui.BOLD, g.fit(Ui.BOLD, focus.title(), 5.33f, INFO - 39.0f), textX, iconY + 2.0f, 5.33f,
                    Ui.Theme.TEXT);
            g.text(Ui.BODY, g.fit(Ui.BODY, focus.subtitle(), 4.6f, INFO - 39.0f), textX, iconY + 9.7f, 4.6f,
                    Ui.Theme.MUTED);
        }
    }

    /** Les boutons de zoom et l'echelle, en bas a droite ; rend le bord gauche de la carte. */
    private float zoom(UiGraphics g, Paint p, float right, float bottom, AtlasView.Frame frame, double mx, double my) {
        float height = 25.3f;
        float button = 15.3f;
        // L'echelle : une longueur ronde de blocs, entre 28 et 64 unites.
        double blocksPerUnit = scale * design();
        double[] nice = {1, 2, 5};
        double length = 1.0d;
        outer:
        for (double power = 1.0d; power < 1.0e7d; power *= 10.0d) {
            for (double base : nice) {
                if (base * power / blocksPerUnit >= 28.0d) {
                    length = base * power;
                    break outer;
                }
            }
        }
        float bar = (float) (length / blocksPerUnit);
        String text = length >= 1000.0d ? (length % 1000.0d == 0 ? String.valueOf((long) (length / 1000.0d))
                : String.format(Locale.ROOT, "%.1f", length / 1000.0d).replace('.', ',')) + " km"
                : (long) length + " m";
        float barWidth = Math.max(bar, g.width(Ui.BOLD, text, 4.8f)) + 2.0f;
        float width = 8.0f + button + 8.0f + barWidth + 8.0f + button + 6.0f + button + 8.0f;
        float x = right - width;
        float y = bottom - height;
        card(p, x, y, width, height);
        float by = y + (height - button) / 2.0f;
        float cursor = x + 8.0f;
        roundButton(g, p, cursor, by, button, "minus", mx, my, () -> zoomBy(1.6d));
        cursor += button + 8.0f;
        float barLeft = cursor + (barWidth - bar) / 2.0f;
        g.centered(Ui.BOLD, text, cursor + barWidth / 2.0f, y + 5.3f, 4.8f, Ui.Theme.TEXT2);
        float lineY = y + 15.3f;
        p.rect(barLeft, lineY, bar, 1.0f, Ui.Theme.TEXT2);
        p.rect(barLeft, lineY - 2.3f, 1.0f, 3.3f, Ui.Theme.TEXT2);
        p.rect(barLeft + bar - 1.0f, lineY - 2.3f, 1.0f, 3.3f, Ui.Theme.TEXT2);
        cursor += barWidth + 8.0f;
        roundButton(g, p, cursor, by, button, "plus", mx, my, () -> zoomBy(1.0d / 1.6d));
        cursor += button + 6.0f;
        roundButton(g, p, cursor, by, button, "center", mx, my, this::recenter);
        return x;
    }

    /** Un petit bouton rond : moins, plus, ou la mire du recentrage. */
    private void roundButton(UiGraphics g, Paint p, float x, float y, float size, String kind, double mx, double my,
                             Runnable action) {
        boolean over = mx >= x && my >= y && mx < x + size && my < y + size;
        float hover = hover("button:" + kind, over);
        boolean red = "center".equals(kind);
        if (red) {
            p.shadow(x + 1.5f, y + 3.0f, size - 3.0f, size - 3.0f, 5.0f, Paint.alpha(Ui.Theme.RED, 0.25f + 0.2f * hover));
            p.roundGradient(x, y, size, size, size / 2.0f, Paint.mix(Ui.Theme.RED_LIGHT, 0xFFFFFFFF, 0.1f * hover),
                    Ui.Theme.RED_D);
        } else {
            p.framed(x, y, size, size, size / 2.0f, Paint.mix(0xFF1B1C1F, 0xFF26282C, hover),
                    Paint.mix(0xFF3F4248, 0xFF5A5D64, hover), 1);
        }
        float cx = x + size / 2.0f;
        float cy = y + size / 2.0f;
        int color = red ? Ui.Theme.TEXT : Paint.mix(Ui.Theme.TEXT2, Ui.Theme.TEXT, hover);
        switch (kind) {
            case "minus" -> p.rect(cx - 3.0f, cy - 0.5f, 6.0f, 1.0f, color);
            case "plus" -> {
                p.rect(cx - 3.0f, cy - 0.5f, 6.0f, 1.0f, color);
                p.rect(cx - 0.5f, cy - 3.0f, 1.0f, 6.0f, color);
            }
            default -> {
                p.outline(cx - 3.3f, cy - 3.3f, 6.6f, 6.6f, 3.3f, color, Math.max(1, Math.round(unit * 0.34f)));
                p.dot(cx, cy, 1.1f, color);
                p.rect(cx - 0.4f, cy - 5.0f, 0.8f, 2.0f, color);
                p.rect(cx - 0.4f, cy + 3.0f, 0.8f, 2.0f, color);
                p.rect(cx - 5.0f, cy - 0.4f, 2.0f, 0.8f, color);
                p.rect(cx + 3.0f, cy - 0.4f, 2.0f, 0.8f, color);
            }
        }
        hits.add(new Hit(x, y, size, size, () -> {
            UiScreen.click(1.0f);
            action.run();
        }));
    }

    /** Les gestes, en petit au milieu du bas, s'il reste la place. */
    private void hint(UiGraphics g, Paint p, float left, float right, float bottom) {
        String text = "Glisser : se deplacer  \u00B7  Molette : zoom  \u00B7  Clic droit : poser un point"
                + "  \u00B7  C : recentrer";
        float size = 4.6f;
        float width = g.width(Ui.BODY, text, size);
        if (width + 16.0f > right - left) {
            text = "Clic droit : poser un point";
            width = g.width(Ui.BODY, text, size);
            if (width + 16.0f > right - left) {
                return;
            }
        }
        float center = (left + right) / 2.0f;
        float height = 11.3f;
        p.round(center - width / 2.0f - 7.0f, bottom - height, width + 14.0f, height, height / 2.0f, 0xB80E0F12);
        g.text(Ui.BODY, text, center - width / 2.0f, bottom - height + 3.4f, size, Ui.Theme.MUTED);
    }

    // -- Ce que vise le curseur ----------------------------------------------------------------------

    private Focus focus(AtlasView.Frame frame, int mouseX, int mouseY, AtlasView.Style style, String dimension,
                        AtlasClient.Options options) {
        float badge = AtlasView.badgeRadius(style, scale);
        double reach = badge + 1.5d;
        if (options.waypoints) {
            Waypoints.Point point = pointAt(frame, mouseX, mouseY, dimension, style.diamond() + 2.5d);
            if (point != null) {
                return new Focus(point.name(), point.x() + "   " + point.y() + "   " + point.z()
                        + "  \u00B7  clic pour modifier", point.color(), ItemStack.EMPTY, null, point);
            }
        }
        boolean overworld = "minecraft:overworld".equals(dimension);
        if (overworld && options.marks) {
            EarthAtlas.Mark mark = markAt(AtlasView.visibleMarks(frame, badge), frame, mouseX, mouseY, reach);
            if (mark != null) {
                return new Focus(mark.name(), describe(mark), AtlasView.resourceColor(mark.resource()),
                        MarkIcons.icon(mark.resource()), mark, null);
            }
        }
        if (overworld && options.disasters) {
            DisasterNet.Entry disaster = disasterAt(frame.worldX(mouseX), frame.worldZ(mouseY));
            if (disaster != null) {
                String title = switch (disaster.kind()) {
                    case DisasterNet.QUAKE -> "Seisme M" + disaster.level() / 10 + "," + disaster.level() % 10;
                    case DisasterNet.HURRICANE -> disaster.name() + " (cat. " + disaster.level() + ")";
                    case DisasterNet.ERUPTION -> "Eruption";
                    default -> "Mousson";
                };
                return new Focus(title, disaster.place() + "  \u00B7  rayon " + AtlasHud.distance(disaster.radius()),
                        DisasterClient.color(disaster.kind()), ItemStack.EMPTY, null, null);
            }
        }
        return null;
    }

    /** Ce qu'est un repere, en quelques mots. */
    private static String describe(EarthAtlas.Mark mark) {
        String what = switch (mark.kind()) {
            case 0 -> "Gisement";
            case 1 -> "Cheminee";
            case 2 -> "Nappe";
            case 3 -> "Sables";
            case 10 -> "Zone irradiee";
            case 11 -> "Base de lancement";
            case 12 -> "Volcan";
            default -> "Lieu";
        };
        String resource = mark.resource() == null ? "" : mark.resource();
        boolean same = resource.isEmpty() || what.equalsIgnoreCase(resource);
        return same ? what : what + " \u00B7 " + resource;
    }

    /** La catastrophe dont la zone couvre ce point (la plus petite, s'il y en a plusieurs). */
    private static DisasterNet.Entry disasterAt(double x, double z) {
        DisasterNet.Entry best = null;
        for (DisasterNet.Entry entry : DisasterClient.entries()) {
            if (entry.covers(x, z) && (best == null || entry.radius() < best.radius())) {
                best = entry;
            }
        }
        return best;
    }

    private static EarthAtlas.Mark markAt(List<EarthAtlas.Mark> marks, AtlasView.Frame frame, double mouseX,
                                          double mouseY, double reach) {
        EarthAtlas.Mark best = null;
        double bestDistance = reach * reach;
        for (EarthAtlas.Mark mark : marks) {
            double dx = frame.screenX(mark.x()) - mouseX;
            double dy = frame.screenY(mark.z()) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mark;
            }
        }
        return best;
    }

    private static Waypoints.Point pointAt(AtlasView.Frame frame, double mouseX, double mouseY, String dimension,
                                           double reach) {
        Waypoints.Point best = null;
        double bestDistance = reach * reach;
        for (Waypoints.Point point : Waypoints.all()) {
            if (!point.dimension().equals(dimension)) {
                continue;
            }
            double dx = frame.screenX(point.x() + 0.5d) - mouseX;
            double dy = frame.screenY(point.z() + 0.5d) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        return best;
    }

    // -- Les gestes ---------------------------------------------------------------------------------

    private void recenter() {
        returning = minecraft.player != null;
        anchored = false;
    }

    /** Zoome d'un cran autour du centre de l'ecran. */
    private void zoomBy(double factor) {
        anchorAt(width / 2.0d, height / 2.0d);
        targetScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, targetScale * factor));
    }

    private void anchorAt(double screenX, double screenY) {
        AtlasView.Frame frame = frame();
        anchored = true;
        anchorX = screenX;
        anchorY = screenY;
        anchorWorldX = frame.worldX(screenX);
        anchorWorldZ = frame.worldZ(screenY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = designX(event.x());
        double my = designY(event.y());
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (Hit hit : hits) {
                if (hit.contains(mx, my)) {
                    hit.action().run();
                    return true;
                }
            }
        }
        if (overPanel(mx, my)) {
            return true;
        }
        String dimension = dimension();
        AtlasView.Frame frame = frame();
        float du = design();
        Waypoints.Point point = AtlasClient.options().waypoints
                ? pointAt(frame, event.x(), event.y(), dimension, 3.6f * du + 2.5d) : null;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (point != null) {
                UiScreen.click(1.0f);
                minecraft.gui.setScreen(new WaypointScreen(this, point, point.x(), point.y(), point.z(), dimension));
                return true;
            }
            dragging = true;
            pressX = event.x();
            pressY = event.y();
            returning = false;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int x = (int) Math.floor(frame.worldX(event.x()));
            int z = (int) Math.floor(frame.worldZ(event.y()));
            int y = minecraft.player == null ? 64 : minecraft.player.getBlockY();
            if (minecraft.level != null && minecraft.level.hasChunk(x >> 4, z >> 4)) {
                y = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            }
            UiScreen.click(1.2f);
            minecraft.gui.setScreen(new WaypointScreen(this, null, x, y, z, dimension));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            centerX -= dragX * scale;
            centerZ -= dragY * scale;
            if (anchored) {
                anchorWorldX -= dragX * scale;
                anchorWorldZ -= dragY * scale;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0d) {
            return false;
        }
        anchorAt(mouseX, mouseY);
        returning = false;
        double factor = Math.pow(1.25d, -scrollY);
        targetScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, targetScale * factor));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_C) {
            recenter();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_EQUAL || event.key() == GLFW.GLFW_KEY_KP_ADD) {
            zoomBy(1.0d / 1.6d);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_MINUS || event.key() == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoomBy(1.6d);
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
