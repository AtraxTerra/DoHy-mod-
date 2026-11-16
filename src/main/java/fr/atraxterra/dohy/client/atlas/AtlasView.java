package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.disaster.DisasterClient;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.earth.EarthAtlas;
import fr.atraxterra.dohy.earth.disaster.DisasterNet;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Map;

/**
 * Le dessin d'une vue de l'atlas dans un rectangle de l'ecran : le globe (s'il y
 * en a un), le terrain explore, les pays tenus, les gisements, les catastrophes et
 * les points de passage. Commun a la minimap et a la grande carte.
 *
 * <p>Tout se place au pixel physique pres : la carte glisse et zoome en douceur,
 * et ses formes sont lisses ({@link MapGlyphs}). Les gisements et les lieux sont
 * montres par leur objet (un lingot, un bidon de petrole...), dans une pastille.
 */
final class AtlasView {

    /** Le fond d'une carte sans globe ni terrain. */
    static final int BACKDROP = 0xFF0E1013;

    /** Les couleurs des ressources des gisements geants (le reste en gris). */
    private static final Map<String, Integer> RESOURCE = Map.ofEntries(
            Map.entry("fer", 0xFFD8AF93), Map.entry("cuivre", 0xFFE0773A), Map.entry("or", 0xFFFFD700),
            Map.entry("diamant", 0xFF5DECF5), Map.entry("charbon", 0xFF5A5A5A), Map.entry("lignite", 0xFF8B6E44),
            Map.entry("petrole", 0xFF3A3A3A), Map.entry("gaz", 0xFF9AD0FF), Map.entry("sables bitumineux", 0xFF8A7048),
            Map.entry("uranium", 0xFF6BE04A), Map.entry("thorium", 0xFF3E8E3A), Map.entry("terres rares", 0xFFB06BE0),
            Map.entry("lithium", 0xFFE6E6F0), Map.entry("cobalt", 0xFF3050D0), Map.entry("coltan", 0xFF6A6A80),
            Map.entry("bauxite", 0xFFC0603A), Map.entry("titane", 0xFFB0B8C0), Map.entry("plomb", 0xFF60607A),
            Map.entry("zinc", 0xFFA0B0A8), Map.entry("emeraude", 0xFF20D060), Map.entry("lapis", 0xFF2040C0),
            Map.entry("redstone", 0xFFD02020), Map.entry("quartz", 0xFFF0EAE0), Map.entry("zone irradiee", 0xFFB6FF00),
            Map.entry("base de lancement", 0xFF80C8FF), Map.entry("volcan", 0xFFFF5A1F),
            Map.entry("tungstene", 0xFF4A4E58), Map.entry("fluorine", 0xFFB9F2E8), Map.entry("soufre", 0xFFF2D43A),
            Map.entry("salpetre", 0xFFE8E4DA), Map.entry("beryllium", 0xFF7D8A52), Map.entry("amiante", 0xFFCFD3C4));

    /** Ce que la vue montre. */
    record Layers(boolean holders, boolean marks, boolean waypoints, boolean labels, boolean disasters) {
    }

    /**
     * La taille des reperes, en pixels d'interface : le rayon des pastilles des
     * gisements, celui des losanges des points, le corps des etiquettes.
     */
    record Style(float badge, float diamond, float label, float line) {
        static Style minimap() {
            return new Style(4.6f, 3.2f, 4.6f, 0.45f);
        }
    }

    /**
     * Le cadrage d'une vue : son rectangle en pixels d'interface (entiers), le bloc du
     * centre, le zoom en blocs par pixel d'interface et les pixels physiques par pixel
     * d'interface.
     */
    static final class Frame {
        final int x;
        final int y;
        final int width;
        final int height;
        final double centerX;
        final double centerZ;
        final double scale;
        final float unit;

        Frame(int x, int y, int width, int height, double centerX, double centerZ, double scale, float unit) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.scale = scale;
            this.unit = Math.max(1.0f, unit);
        }

        double screenX(double worldX) {
            return x + width / 2.0d + (worldX - centerX) / scale;
        }

        double screenY(double worldZ) {
            return y + height / 2.0d + (worldZ - centerZ) / scale;
        }

        double worldX(double screenX) {
            return centerX + (screenX - x - width / 2.0d) * scale;
        }

        double worldZ(double screenY) {
            return centerZ + (screenY - y - height / 2.0d) * scale;
        }

        /** En pixels physiques. */
        float physX(double worldX) {
            return (float) (screenX(worldX) * unit);
        }

        float physY(double worldZ) {
            return (float) (screenY(worldZ) * unit);
        }

        /** Blocs par pixel physique. */
        double blocks() {
            return scale / unit;
        }

        /** Un point d'interface recale sur le pixel physique le plus proche. */
        float snap(double value) {
            return Math.round(value * unit) / unit;
        }

        boolean inside(double sx, double sy, double margin) {
            return sx >= x - margin && sy >= y - margin && sx <= x + width + margin && sy <= y + height + margin;
        }
    }

    private AtlasView() {
    }

    static int resourceColor(String resource) {
        return RESOURCE.getOrDefault(resource, 0xFFC8C8C8);
    }

    /** Les chargements de dalles depuis le disque permis par image : la carte ne fige jamais. */
    private static final int LOADS_PER_FRAME = 4;
    private static int loads;

    /** Dessine la vue. */
    static void draw(GuiGraphicsExtractor graphics, Font font, AtlasMap map, Frame frame, Layers layers, Style style,
                     String dimension) {
        graphics.enableScissor(frame.x, frame.y, frame.x + frame.width, frame.y + frame.height);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1.0f / frame.unit, 1.0f / frame.unit);
        int left = Math.round(frame.x * frame.unit);
        int top = Math.round(frame.y * frame.unit);
        int right = Math.round((frame.x + frame.width) * frame.unit);
        int bottom = Math.round((frame.y + frame.height) * frame.unit);
        graphics.fill(left, top, right, bottom, BACKDROP);
        grid(graphics, frame, left, top, right, bottom);
        boolean overworld = "minecraft:overworld".equals(dimension);
        AtlasData.Globe globe = overworld && AtlasGlobe.ready() ? AtlasData.globe() : null;
        if (globe != null) {
            GlobeTiles.pump(3);
            globe(graphics, frame, globe, false, left, top, right, bottom);
        }
        loads = 0;
        if (map != null) {
            terrain(graphics, frame, map, left, top, right, bottom);
        }
        if (globe != null && layers.holders()) {
            globe(graphics, frame, globe, true, left, top, right, bottom);
        }
        if (overworld && layers.marks()) {
            footprints(graphics, frame, left, top, right, bottom);
        }
        if (overworld && layers.disasters()) {
            disasterAreas(graphics, frame, style, left, top, right, bottom);
        }
        pose.popMatrix();
        UiGraphics ui = new UiGraphics(graphics, font, 0, 0);
        Paint paint = new Paint(graphics, frame.unit);
        if (overworld && layers.disasters()) {
            disasterCenters(graphics, ui, paint, frame, style, layers.labels());
        }
        if (overworld && layers.marks()) {
            marks(ui, paint, frame, style, layers.labels());
        }
        if (layers.waypoints()) {
            waypoints(graphics, ui, paint, frame, style, dimension, layers.labels());
        }
        graphics.disableScissor();
    }

    // -- Le fond -------------------------------------------------------------------------------------

    /** Un quadrillage discret, visible la ou rien n'est encore connu. */
    private static void grid(GuiGraphicsExtractor graphics, Frame frame, int left, int top, int right, int bottom) {
        double blocks = frame.blocks();
        int step = 16;
        while (step / blocks < 56.0d && step < (1 << 20)) {
            step *= 4;
        }
        int color = 0x0EFFFFFF;
        double fromX = Math.floor(frame.worldX(frame.x) / step) * step;
        for (double wx = fromX; ; wx += step) {
            int px = Math.round(frame.physX(wx));
            if (px > right) {
                break;
            }
            if (px >= left) {
                graphics.fill(px, top, px + 1, bottom, color);
            }
        }
        double fromZ = Math.floor(frame.worldZ(frame.y) / step) * step;
        for (double wz = fromZ; ; wz += step) {
            int py = Math.round(frame.physY(wz));
            if (py > bottom) {
                break;
            }
            if (py >= top) {
                graphics.fill(left, py, right, py + 1, color);
            }
        }
    }

    // -- Le globe ------------------------------------------------------------------------------------

    /**
     * Le globe ou son calque des pays tenus : l'image entiere de loin, les dalles
     * redessinees de pres (l'image agrandie en attendant qu'elles arrivent).
     */
    private static void globe(GuiGraphicsExtractor graphics, Frame frame, AtlasData.Globe globe, boolean holders,
                              int left, int top, int right, int bottom) {
        double cellPixels = globe.cell() / frame.blocks();
        int density = cellPixels >= 64.0d ? 64 : cellPixels >= 16.0d ? 16 : cellPixels >= 4.0d ? 4 : 0;
        Identifier whole = holders ? AtlasGlobe.HOLDERS : AtlasGlobe.BASE;
        if (density == 0 || !holders) {
            region(graphics, frame, globe, whole, 0, 0, globe.width(), globe.height(), left, top, right, bottom);
        }
        if (density == 0) {
            return;
        }
        int cells = GlobeTiles.SIZE / density;
        double span = (double) cells * globe.cell();
        int fromX = Math.max(0, (int) Math.floor((frame.worldX(frame.x) - globe.minX()) / span));
        int toX = Math.min((globe.width() - 1) / cells, (int) Math.floor((frame.worldX(frame.x + frame.width)
                - globe.minX()) / span));
        int fromZ = Math.max(0, (int) Math.floor((frame.worldZ(frame.y) - globe.minZ()) / span));
        int toZ = Math.min((globe.height() - 1) / cells, (int) Math.floor((frame.worldZ(frame.y + frame.height)
                - globe.minZ()) / span));
        if ((long) (toX - fromX + 1) * (toZ - fromZ + 1) > 160) {
            return;
        }
        for (int tz = fromZ; tz <= toZ; tz++) {
            for (int tx = fromX; tx <= toX; tx++) {
                GlobeTiles.Entry entry = GlobeTiles.get(density, tx, tz);
                if (entry == null) {
                    if (holders) {
                        region(graphics, frame, globe, whole, tx * cells, tz * cells, cells, cells, left, top, right,
                                bottom);
                    }
                    continue;
                }
                Identifier texture = holders ? entry.holders() : entry.base();
                if (texture == null) {
                    continue;
                }
                double wx = globe.minX() + tx * span;
                double wz = globe.minZ() + tz * span;
                int x0 = Math.round(frame.physX(wx));
                int y0 = Math.round(frame.physY(wz));
                int x1 = Math.round(frame.physX(wx + span));
                int y1 = Math.round(frame.physY(wz + span));
                graphics.blit(texture, x0, y0, x1, y1, 0.0f, 1.0f, 0.0f, 1.0f);
            }
        }
    }

    /** Une partie de l'image du globe (en cases), limitee a ce qui se voit. */
    private static void region(GuiGraphicsExtractor graphics, Frame frame, AtlasData.Globe globe, Identifier texture,
                               int column, int row, int columns, int rows, int left, int top, int right, int bottom) {
        double wx0 = globe.minX() + (double) column * globe.cell();
        double wz0 = globe.minZ() + (double) row * globe.cell();
        double wx1 = wx0 + (double) Math.min(columns, globe.width() - column) * globe.cell();
        double wz1 = wz0 + (double) Math.min(rows, globe.height() - row) * globe.cell();
        int x0 = Math.max(left, Math.round(frame.physX(wx0)));
        int y0 = Math.max(top, Math.round(frame.physY(wz0)));
        int x1 = Math.min(right, Math.round(frame.physX(wx1)));
        int y1 = Math.min(bottom, Math.round(frame.physY(wz1)));
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        double fullWidth = (double) globe.width() * globe.cell();
        double fullHeight = (double) globe.height() * globe.cell();
        double blocks = frame.blocks();
        float u0 = (float) ((frame.centerX + (x0 - (frame.x + frame.width / 2.0d) * frame.unit) * blocks
                - globe.minX()) / fullWidth);
        float u1 = (float) ((frame.centerX + (x1 - (frame.x + frame.width / 2.0d) * frame.unit) * blocks
                - globe.minX()) / fullWidth);
        float v0 = (float) ((frame.centerZ + (y0 - (frame.y + frame.height / 2.0d) * frame.unit) * blocks
                - globe.minZ()) / fullHeight);
        float v1 = (float) ((frame.centerZ + (y1 - (frame.y + frame.height / 2.0d) * frame.unit) * blocks
                - globe.minZ()) / fullHeight);
        graphics.blit(texture, x0, y0, x1, y1, u0, u1, v0, v1);
    }

    // -- Le terrain explore -------------------------------------------------------------------------

    /**
     * Le niveau de dalles le plus fin qui tient dans sa memoire, avec le niveau au-dessus
     * en dessous (deja charge) le temps que les dalles arrivent.
     */
    private static void terrain(GuiGraphicsExtractor graphics, Frame frame, AtlasMap map, int left, int top,
                                int right, int bottom) {
        AtlasTiles[] levels = {map.fine, map.middle, map.overview};
        double blocks = frame.blocks();
        int chosen = levels.length - 1;
        for (int index = 0; index < levels.length - 1; index++) {
            AtlasTiles level = levels[index];
            if (blocks > level.scale() * 3.0d) {
                continue;
            }
            if (count(frame, level) <= level.budget()) {
                chosen = index;
                break;
            }
        }
        if (chosen < levels.length - 1) {
            level(graphics, frame, levels[chosen + 1], false, left, top, right, bottom);
        }
        level(graphics, frame, levels[chosen], true, left, top, right, bottom);
    }

    private static long count(Frame frame, AtlasTiles level) {
        int span = level.span();
        long columns = (long) Math.floor(frame.worldX(frame.x + frame.width) / span)
                - (long) Math.floor(frame.worldX(frame.x) / span) + 1;
        long rows = (long) Math.floor(frame.worldZ(frame.y + frame.height) / span)
                - (long) Math.floor(frame.worldZ(frame.y) / span) + 1;
        return columns * rows;
    }

    private static void level(GuiGraphicsExtractor graphics, Frame frame, AtlasTiles tiles, boolean load, int left,
                              int top, int right, int bottom) {
        int span = tiles.span();
        int fromX = (int) Math.floor(frame.worldX(frame.x) / span);
        int toX = (int) Math.floor(frame.worldX(frame.x + frame.width) / span);
        int fromZ = (int) Math.floor(frame.worldZ(frame.y) / span);
        int toZ = (int) Math.floor(frame.worldZ(frame.y + frame.height) / span);
        if ((long) (toX - fromX + 1) * (toZ - fromZ + 1) > 400) {
            return;
        }
        for (int tz = fromZ; tz <= toZ; tz++) {
            for (int tx = fromX; tx <= toX; tx++) {
                AtlasTiles.Tile tile = tiles.loaded(tx, tz);
                if (tile == null && load && tiles.unknown(tx, tz)) {
                    if (loads >= LOADS_PER_FRAME) {
                        continue;
                    }
                    loads++;
                    tile = tiles.tile(tx, tz, false);
                }
                if (tile == null) {
                    continue;
                }
                int x0 = Math.round(frame.physX((double) tx * span));
                int y0 = Math.round(frame.physY((double) tz * span));
                int x1 = Math.round(frame.physX((double) (tx + 1) * span));
                int y1 = Math.round(frame.physY((double) (tz + 1) * span));
                if (x1 <= left || y1 <= top || x0 >= right || y0 >= bottom) {
                    continue;
                }
                graphics.blit(tile.id, x0, y0, x1, y1, 0.0f, 1.0f, 0.0f, 1.0f);
            }
        }
    }

    // -- Les zones -----------------------------------------------------------------------------------

    /** L'emprise des gisements et des lieux, des qu'elle se voit : une tache claire et son contour. */
    private static void footprints(GuiGraphicsExtractor graphics, Frame frame, int left, int top, int right,
                                   int bottom) {
        double blocks = frame.blocks();
        float line = Math.max(1.0f, frame.unit * 0.4f);
        for (EarthAtlas.Mark mark : AtlasData.marks()) {
            float rx = (float) (mark.rx() / blocks);
            float rz = (float) (mark.rz() / blocks);
            if (Math.max(rx, rz) < 7.0f * frame.unit) {
                continue;
            }
            float cx = frame.physX(mark.x());
            float cy = frame.physY(mark.z());
            float reach = Math.max(rx, rz);
            if (cx + reach < left || cy + reach < top || cx - reach > right || cy - reach > bottom) {
                continue;
            }
            int color = resourceColor(mark.resource());
            MapGlyphs.ellipse(graphics, cx, cy, rx, rz, mark.angle(), Paint.withAlpha(color, 0x2C));
            MapGlyphs.ellipseRing(graphics, cx, cy, rx, rz, mark.angle(), line, Paint.withAlpha(color, 0x9A),
                    left, top, right, bottom);
        }
    }

    /** Les zones des catastrophes et la route a venir des ouragans. */
    private static void disasterAreas(GuiGraphicsExtractor graphics, Frame frame, Style style, int left, int top,
                                      int right, int bottom) {
        double blocks = frame.blocks();
        float line = Math.max(1.25f, frame.unit * style.line());
        for (DisasterNet.Entry entry : DisasterClient.entries()) {
            int color = DisasterClient.color(entry.kind());
            float cx = frame.physX(entry.x());
            float cy = frame.physY(entry.z());
            float radius = (float) (entry.radius() / blocks);
            if (radius >= 3.0f && cx + radius >= left && cy + radius >= top && cx - radius <= right
                    && cy - radius <= bottom) {
                MapGlyphs.ellipse(graphics, cx, cy, radius, radius, 0.0f, Paint.withAlpha(color, 0x30));
                MapGlyphs.ring(graphics, cx, cy, radius, line, Paint.withAlpha(color, 0xB8), left, top, right,
                        bottom);
            }
            if (entry.kind() == DisasterNet.HURRICANE) {
                int[] track = entry.track();
                float px = cx;
                float py = cy;
                float step = 3.6f * frame.unit;
                int dot = Math.max(3, Math.round(1.3f * frame.unit));
                Identifier disc = MapGlyphs.shape(MapGlyphs.Shape.DISC, dot);
                int trackColor = Paint.withAlpha(color, 0xE6);
                for (int i = 0; i + 1 < track.length; i += 2) {
                    float tx = frame.physX(track[i]);
                    float ty = frame.physY(track[i + 1]);
                    float length = (float) Math.hypot(tx - px, ty - py);
                    int dots = (int) Math.min(4000, Math.floor(length / step));
                    for (int d = 0; d <= dots; d++) {
                        float t = dots == 0 ? 0.0f : d / (float) dots;
                        float x = px + (tx - px) * t;
                        float y = py + (ty - py) * t;
                        if (x >= left - dot && y >= top - dot && x <= right + dot && y <= bottom + dot) {
                            MapGlyphs.draw(graphics, disc, dot, x, y, trackColor);
                        }
                    }
                    px = tx;
                    py = ty;
                }
            }
        }
    }

    /** Le centre des catastrophes : l'oeil des ouragans, un triangle pour les eruptions, un point sinon. */
    private static void disasterCenters(GuiGraphicsExtractor graphics, UiGraphics ui, Paint paint, Frame frame,
                                        Style style, boolean labels) {
        for (DisasterNet.Entry entry : DisasterClient.entries()) {
            int color = DisasterClient.color(entry.kind());
            double sx = frame.screenX(entry.x());
            double sy = frame.screenY(entry.z());
            if (!frame.inside(sx, sy, 24.0d)) {
                continue;
            }
            float x = frame.snap(sx);
            float y = frame.snap(sy);
            if (entry.kind() == DisasterNet.HURRICANE) {
                float eye = (float) Math.max(2.0d, Math.min(60.0d, entry.eye() / frame.scale));
                paint.dot(x, y, eye + 0.75f, 0xFFFFFFFF);
                paint.dot(x, y, eye, 0xFF0B0D12);
            } else if (entry.kind() == DisasterNet.ERUPTION) {
                glyph(graphics, frame, MapGlyphs.Shape.TRIANGLE, x, y - 0.4f, style.badge() * 2.2f, 0xFF15161A);
                glyph(graphics, frame, MapGlyphs.Shape.TRIANGLE, x, y - 0.4f, style.badge() * 1.65f, color);
            } else {
                paint.dot(x, y, style.badge() * 0.62f, 0xFFFFFFFF);
                paint.dot(x, y, style.badge() * 0.46f, color);
            }
            if (labels) {
                String text = switch (entry.kind()) {
                    case DisasterNet.QUAKE -> "Seisme M" + entry.level() / 10 + "," + entry.level() % 10;
                    case DisasterNet.HURRICANE -> entry.name() + " (cat. " + entry.level() + ")";
                    case DisasterNet.ERUPTION -> entry.place();
                    default -> "Mousson";
                };
                label(ui, paint, text, x, y + style.badge() + 2.5f, style.label(), 0xFFFFFFFF);
            }
        }
    }

    // -- Les gisements et les lieux ------------------------------------------------------------------

    /**
     * Les gisements, les zones irradiees, les bases et les volcans : l'objet de leur
     * ressource dans une pastille cerclee de sa couleur. La capitale, une etoile.
     */
    private static void marks(UiGraphics ui, Paint paint, Frame frame, Style style, boolean labels) {
        List<EarthAtlas.Mark> marks = AtlasData.marks();
        float badge = style.badge();
        boolean names = labels && frame.scale <= 4.0d;
        for (EarthAtlas.Mark mark : marks) {
            double sx = frame.screenX(mark.x());
            double sy = frame.screenY(mark.z());
            if (!frame.inside(sx, sy, badge + 2.0d)) {
                continue;
            }
            float x = frame.snap(sx);
            float y = frame.snap(sy);
            badge(ui, paint, mark, x, y, badge);
            if (names) {
                label(ui, paint, mark.name(), x, y + badge + 2.0f, style.label(), 0xFFFFFFFF);
            }
        }
        EarthAtlas.Nations nations = AtlasData.nations();
        if (nations != null && nations.capital() != 0) {
            double sx = frame.screenX(nations.capitalX());
            double sy = frame.screenY(nations.capitalZ());
            if (frame.inside(sx, sy, badge * 2.0d)) {
                float x = frame.snap(sx);
                float y = frame.snap(sy);
                GuiGraphicsExtractor graphics = ui.raw();
                glyph(graphics, frame, MapGlyphs.Shape.STAR, x, y, badge * 2.7f, 0xFFFFFFFF);
                glyph(graphics, frame, MapGlyphs.Shape.STAR, x, y, badge * 2.1f, Ui.Theme.RED);
                if (labels) {
                    label(ui, paint, "Capitale", x, y + badge + 2.0f, style.label(), 0xFFFFD24A);
                }
            }
        }
    }

    /** La pastille d'un repere : son anneau de couleur, le fond sombre, l'objet. */
    static void badge(UiGraphics ui, Paint paint, EarthAtlas.Mark mark, float x, float y, float radius) {
        int color = resourceColor(mark.resource());
        paint.glow(x, y + radius * 0.2f, radius * 1.9f, 0x66000000);
        paint.dot(x, y, radius + 0.7f, Paint.withAlpha(color, 0xF2));
        paint.dot(x, y, radius, 0xF0131417);
        ItemStack icon = MarkIcons.icon(mark.resource());
        if (icon.isEmpty()) {
            paint.dot(x, y, radius * 0.5f, color);
        } else {
            float size = radius * 1.5f;
            ui.item(icon, x - size / 2.0f, y - size / 2.0f, size, false);
        }
    }

    // -- Les points de passage ------------------------------------------------------------------------

    private static void waypoints(GuiGraphicsExtractor graphics, UiGraphics ui, Paint paint, Frame frame,
                                  Style style, String dimension, boolean labels) {
        for (Waypoints.Point point : Waypoints.all()) {
            if (!point.dimension().equals(dimension)) {
                continue;
            }
            double sx = frame.screenX(point.x() + 0.5d);
            double sy = frame.screenY(point.z() + 0.5d);
            if (!frame.inside(sx, sy, style.diamond() + 2.0d)) {
                continue;
            }
            float x = frame.snap(sx);
            float y = frame.snap(sy);
            diamond(graphics, frame.unit, x, y, style.diamond(), point.color());
            if (labels) {
                label(ui, paint, point.name(), x, y + style.diamond() + 2.5f, style.label(), point.color());
            }
        }
    }

    /** Un losange de point de passage : ombre, filet clair, la couleur du point. */
    static void diamond(GuiGraphicsExtractor graphics, float unit, float x, float y, float radius, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1.0f / unit, 1.0f / unit);
        float cx = x * unit;
        float cy = y * unit;
        int outer = Math.max(5, Math.round((radius + 1.0f) * 2.0f * unit));
        int inner = Math.max(3, Math.round(radius * 2.0f * unit));
        int shadow = Math.max(7, Math.round((radius + 2.2f) * 2.0f * unit));
        MapGlyphs.draw(graphics, MapGlyphs.shape(MapGlyphs.Shape.DISC, shadow), shadow, cx, cy + unit * 0.6f,
                0x55000000);
        MapGlyphs.draw(graphics, MapGlyphs.shape(MapGlyphs.Shape.DIAMOND, outer), outer, cx, cy, 0xFFFFFFFF);
        MapGlyphs.draw(graphics, MapGlyphs.shape(MapGlyphs.Shape.DIAMOND, inner), inner, cx, cy, color);
        pose.popMatrix();
    }

    /** Une forme lisse de {@code size} pixels d'interface de cote, centree sur (x, y). */
    static void glyph(GuiGraphicsExtractor graphics, Frame frame, MapGlyphs.Shape shape, float x, float y, float size,
                      int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1.0f / frame.unit, 1.0f / frame.unit);
        int pixels = Math.max(3, Math.round(size * frame.unit));
        MapGlyphs.draw(graphics, MapGlyphs.shape(shape, pixels), pixels, x * frame.unit, y * frame.unit, color);
        pose.popMatrix();
    }

    /** La fleche du joueur, tournee selon son regard (le nord en haut de la vue), de {@code size} pixels d'interface. */
    static void player(GuiGraphicsExtractor graphics, float unit, double x, double y, float yaw, float size) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1.0f / unit, 1.0f / unit);
        int pixels = Math.max(8, Math.round(size * unit));
        MapGlyphs.drawRotated(graphics, MapGlyphs.arrow(pixels), pixels, (float) (x * unit), (float) (y * unit),
                (float) Math.toRadians(yaw + 180.0f), 0xFFFFFFFF);
        pose.popMatrix();
    }

    /** Une etiquette du kit : le texte sur une pilule sombre, centree sous (x, y). */
    static void label(UiGraphics ui, Paint paint, String text, float centerX, float top, float size, int color) {
        float width = ui.width(Ui.BODY, text, size);
        float padX = size * 0.55f;
        float height = size * 1.55f;
        float left = centerX - width / 2.0f - padX;
        paint.round(left, top, width + padX * 2.0f, height, height / 2.0f, 0xD812131A);
        ui.text(Ui.BODY, text, left + padX, top + (height - size) / 2.0f + size * 0.06f, size, color);
    }

    static void logOnce(String message, Throwable error) {
        DoHy.LOGGER.error(message, error);
    }
}
