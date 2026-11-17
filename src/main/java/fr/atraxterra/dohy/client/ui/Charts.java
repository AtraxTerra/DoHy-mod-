package fr.atraxterra.dohy.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import fr.atraxterra.dohy.ui.view.PriceChart;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les courbes de prix du kit, comme a la bourse.
 *
 * <p>Une courbe se rasterise une seule fois, en logiciel, a la taille exacte ou elle
 * s'affiche (en pixels physiques) : un trait adouci, un halo, et l'aire en degrade
 * sous la ligne. Le resultat est un masque blanc garde en cache, que chaque image
 * teinte de la couleur de la tendance d'un seul trait. Par-dessus, les formes du kit
 * posent les reperes, les volumes, les etiquettes et le reticule sous la souris.
 */
public final class Charts {

    /** La hausse. */
    public static final int UP = 0xFF22E558;
    /** La baisse. */
    public static final int DOWN = 0xFFF2492F;
    /** Un prix qui ne bouge pas. */
    public static final int FLAT = 0xFF7F95B0;

    /** L'aire sous la ligne, au plus haut de la courbe. */
    private static final float AREA = 0.30f;
    /** Le halo du trait, a son coeur. */
    private static final float HALO = 0.42f;
    private static final int CACHE_SIZE = 48;
    private static int serial;

    private record Key(int width, int height, int half, List<Integer> points) {
    }

    /** Les masques deja rasterises, les plus anciens rendus a la carte graphique. */
    private static final Map<Key, Identifier> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Identifier> eldest) {
            if (size() > CACHE_SIZE) {
                Paint.release(eldest.getValue());
                return true;
            }
            return false;
        }
    };

    private Charts() {
    }

    // -- Lire une courbe ------------------------------------------------------------------

    /** La couleur de la tendance : vert si la courbe monte, rouge si elle baisse, bleu-gris si elle est plate. */
    public static int trend(PriceChart chart) {
        return chart.flat() || chart.change() == 0 ? FLAT : chart.change() > 0 ? UP : DOWN;
    }

    /** Une valeur de la courbe : « 1.2k $ » pour un prix, « +12 % » pour un cours. */
    public static String value(PriceChart chart, long value) {
        if (chart.scale() == PriceChart.PERMILLE) {
            long percent = Math.round((value - 1000L) / 10.0d);
            return (percent > 0 ? "+" : "") + percent + " %";
        }
        return MoneyFormat.shortForm(value) + " $";
    }

    /** La variation du premier au dernier point : « +12,4 % ». */
    public static String change(PriceChart chart) {
        int change = chart.change();
        int abs = Math.abs(change);
        return (change > 0 ? "+" : change < 0 ? "-" : "") + abs / 100 + "," + (abs % 100) / 10 + " %";
    }

    /** La duree couverte, en capitales : « 24 H », « 8 H », « 40 MIN ». */
    public static String span(int minutes) {
        if (minutes < 60) {
            return minutes + " MIN";
        }
        return minutes % 60 == 0 ? minutes / 60 + " H" : minutes / 60 + " H " + minutes % 60;
    }

    /** « maintenant », « il y a 40 min », « il y a 3 h », « il y a 2 h 10 ». */
    public static String ago(int minutes) {
        if (minutes <= 0) {
            return "maintenant";
        }
        if (minutes < 60) {
            return "il y a " + minutes + " min";
        }
        return "il y a " + minutes / 60 + " h" + (minutes % 60 == 0 ? "" : " " + minutes % 60);
    }

    // -- Dessiner ---------------------------------------------------------------------------

    /**
     * Une courbe miniature, pour une carte : la ligne, son aire, le repere en
     * pointilles et le dernier point. {@code reveal} la deroule de gauche a droite.
     */
    public static void spark(UiGraphics g, PriceChart chart, float x, float y, float width, float height,
                             float reveal) {
        if (chart.size() == 0) {
            return;
        }
        Paint p = g.paint();
        Plot plot = plot(p, chart, x, y, width, height, 0.0f, 0.9f, 1.4f);
        int color = trend(chart);
        if (plot.referenceY >= y && plot.referenceY <= y + height) {
            p.dashes(x, plot.referenceY, width, 1.0f, 1.3f, 0x30FFFFFF);
        }
        plot.draw(p, color, reveal);
        if (reveal >= 0.999f) {
            float lastX = plot.guiX(plot.last());
            float lastY = plot.guiY(plot.last());
            p.glow(lastX, lastY, 4.0f, Paint.alpha(color, 0.6f));
            p.dot(lastX, lastY, 1.1f, Paint.mix(color, 0xFFFFFFFF, 0.35f));
        }
    }

    /**
     * La courbe complete : grille et prix a droite, volumes en bas, repere en
     * pointilles, la ligne et son aire, le dernier point qui bat, les heures dessous,
     * et sous la souris un reticule avec la valeur et l'heure du point.
     */
    public static void full(UiGraphics g, PriceChart chart, float x, float y, float width, float height,
                            double mouseX, double mouseY, float reveal) {
        if (chart.size() == 0) {
            return;
        }
        Paint p = g.paint();
        float labels = 22.0f;
        float axis = 7.0f;
        float plotWidth = width - labels;
        float plotHeight = height - axis;
        boolean volumes = chart.volumes().size() == chart.size() && chart.traded() > 0;
        float volumeHeight = volumes ? Math.min(16.0f, plotHeight * 0.24f) : 0.0f;
        float lineHeight = plotHeight - volumeHeight - (volumes ? 2.0f : 0.0f);
        int color = trend(chart);
        int n = chart.size();

        // Le point sous la souris, s'il y en a un.
        Plot plot = plot(p, chart, x, y, plotWidth, lineHeight, 3.0f, 1.15f, 1.6f);
        int hovered = -1;
        boolean inside = mouseX >= x && mouseX < x + plotWidth && mouseY >= y && mouseY < y + plotHeight;
        if (inside && n > 1) {
            hovered = Math.round((float) ((mouseX - x) / plotWidth * (n - 1)));
            hovered = Math.max(0, Math.min(n - 1, hovered));
        }
        // L'etiquette de droite (le dernier prix, ou celui du point survole) cache les paliers qu'elle touche.
        float tagY = hovered >= 0 ? plot.guiY(hovered) : reveal >= 0.999f ? plot.guiY(plot.last()) : Float.NaN;

        // Le cadre et la grille : trois paliers, leur prix a droite.
        p.rect(x, y + plotHeight, plotWidth, 0.34f, 0xFF2C2E33);
        for (int step = 0; step < 3; step++) {
            double value = plot.high - (plot.high - plot.low) * step / 2.0d;
            float gridY = plot.guiYOf(value);
            p.dashes(x, gridY, plotWidth, 0.7f, 1.4f, 0x22FFFFFF);
            if (!(Math.abs(gridY - tagY) < 5.0f)) {
                g.text(Ui.PIXEL, value(chart, Math.round(value)), x + plotWidth + 2.7f, gridY - 1.4f, 3.0f,
                        Ui.Theme.DIM);
            }
        }

        // Les volumes, une barre par point, de la couleur du pas.
        if (volumes) {
            long top = 1L;
            for (long volume : chart.volumes()) {
                top = Math.max(top, volume);
            }
            float pitch = plotWidth / Math.max(1, n);
            float bar = Math.max(0.6f, pitch * 0.62f);
            float base = y + plotHeight;
            for (int index = 0; index < n; index++) {
                long volume = chart.volumes().get(index);
                if (volume <= 0L || index > (n - 1) * reveal + 0.5f) {
                    continue;
                }
                float barHeight = Math.max(0.7f, volumeHeight * (float) Math.sqrt(volume / (double) top));
                boolean up = index == 0 || chart.values().get(index) >= chart.values().get(index - 1);
                float centre = plot.guiX(index);
                p.rect(centre - bar / 2.0f, base - barHeight, bar, barHeight,
                        Paint.alpha(up ? UP : DOWN, index == hovered ? 0.75f : 0.32f));
            }
        }

        // Le repere : le prix de base, ou le cours de reference.
        boolean reference = chart.reference() > 0 && plot.referenceY >= y && plot.referenceY <= y + lineHeight;
        if (reference) {
            p.dashes(x, plot.referenceY, plotWidth, 1.3f, 1.3f, 0x40FFFFFF);
        }

        plot.draw(p, color, reveal);

        // Le nom du repere, sur un fond : la courbe passe souvent dessous.
        if (reference) {
            String name = chart.scale() == PriceChart.PERMILLE ? "REFERENCE" : "BASE";
            float nameWidth = g.width(Ui.PIXEL, name, 2.7f) + 3.4f;
            p.round(x + 0.6f, plot.referenceY - 5.4f, nameWidth, 4.4f, 1.2f, 0xD016171A);
            g.text(Ui.PIXEL, name, x + 2.3f, plot.referenceY - 4.5f, 2.7f, 0x99FFFFFF);
        }

        // Le dernier point : le prix de maintenant, qui bat doucement.
        float lastX = plot.guiX(plot.last());
        float lastY = plot.guiY(plot.last());
        if (reveal >= 0.999f) {
            float pulse = Anim.pulse(1.4d);
            p.glow(lastX, lastY, 6.0f + 3.0f * pulse, Paint.alpha(color, 0.35f + 0.25f * pulse));
            p.dot(lastX, lastY, 1.6f, color);
            p.dot(lastX, lastY, 0.7f, 0xFFFFFFFF);
            // l'etiquette du dernier prix, accrochee a l'echelle de droite
            if (hovered < 0) {
                tag(g, p, value(chart, chart.last()), x + plotWidth + 1.3f, lastY, color);
            }
        }

        // Les heures.
        if (hovered < 0) {
            g.text(Ui.PIXEL, "-" + span(chart.span()), x, y + plotHeight + 2.3f, 3.0f, Ui.Theme.DIM);
            if (chart.span() >= 120) {
                g.centered(Ui.PIXEL, "-" + span(chart.span() / 2), x + plotWidth / 2.0f, y + plotHeight + 2.3f, 3.0f,
                        Ui.Theme.DIM);
            }
            g.right(Ui.PIXEL, "MAINTENANT", x + plotWidth, y + plotHeight + 2.3f, 3.0f, Ui.Theme.DIM);
        }

        // Le reticule.
        if (hovered >= 0) {
            float pointX = plot.guiX(hovered);
            float pointY = plot.guiY(hovered);
            p.dashesV(pointX, y, plotHeight, 1.0f, 1.0f, 0x66FFFFFF);
            p.dashes(pointX, pointY, x + plotWidth - pointX, 1.0f, 1.0f, 0x44FFFFFF);
            p.glow(pointX, pointY, 6.0f, Paint.alpha(color, 0.5f));
            p.dot(pointX, pointY, 1.8f, 0xFFFFFFFF);
            p.dot(pointX, pointY, 1.1f, color);
            tag(g, p, value(chart, chart.values().get(hovered)), x + plotWidth + 1.3f, pointY, color);
            int minutes = (n - 1 - hovered) * chart.step();
            String when = ago(minutes).toUpperCase(java.util.Locale.ROOT);
            if (volumes && chart.volumes().get(hovered) > 0L) {
                when += " · " + MoneyFormat.shortForm(chart.volumes().get(hovered))
                        + (chart.volumeInDollars() ? " $" : " ECH.");
            }
            float tagWidth = g.width(Ui.PIXEL, when, 3.0f) + 5.4f;
            float tagX = Math.max(x, Math.min(x + plotWidth - tagWidth, pointX - tagWidth / 2.0f));
            p.round(tagX, y + plotHeight + 0.7f, tagWidth, 5.6f, 1.6f, 0xFF2A2C31);
            g.text(Ui.PIXEL, when, tagX + 2.7f, y + plotHeight + 2.0f, 3.0f, Ui.Theme.TEXT);
        }
    }

    /** Une etiquette de prix accrochee a l'echelle de droite, a la hauteur {@code centreY}. */
    private static void tag(UiGraphics g, Paint p, String text, float left, float centreY, int color) {
        float tagWidth = g.width(Ui.PIXEL, text, 3.0f) + 4.0f;
        p.round(left, centreY - 2.8f, tagWidth, 5.6f, 1.4f, color);
        g.text(Ui.PIXEL, text, left + 2.0f, centreY - 1.4f, 3.0f, 0xFF101114);
    }

    // -- La rasterisation ---------------------------------------------------------------------

    /** Une courbe posee dans un rectangle : ses points en pixels physiques, et de quoi la dessiner. */
    private static final class Plot {
        final float unit;
        final int left;
        final int top;
        final int width;
        final int height;
        final float[] xs;
        final float[] ys;
        final float half;
        final float pad;
        final long low;
        final long high;
        final float referenceY;

        Plot(float unit, int left, int top, int width, int height, float[] xs, float[] ys, float half, float pad,
             long low, long high, long reference) {
            this.unit = unit;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.xs = xs;
            this.ys = ys;
            this.half = half;
            this.pad = pad;
            this.low = low;
            this.high = high;
            this.referenceY = reference > 0 ? guiYOf(reference) : Float.NaN;
        }

        /** Le dernier point trace : le prix de maintenant. */
        int last() {
            return xs.length - 1;
        }

        float guiX(int index) {
            return (left + xs[Math.min(index, xs.length - 1)]) / unit;
        }

        float guiY(int index) {
            return (top + ys[Math.min(index, ys.length - 1)]) / unit;
        }

        /** La hauteur d'une valeur, en unites. */
        float guiYOf(double value) {
            double t = (value - low) / (double) (high - low);
            return (float) (top + pad + (height - 2.0f * pad) * (1.0d - t)) / unit;
        }

        /** L'aire et le halo de la couleur de la tendance, puis le trait, un peu plus clair. */
        void draw(Paint p, int color, float reveal) {
            if (width <= 0 || height <= 0) {
                return;
            }
            Identifier masks = masks(xs, ys, width, height, half);
            int shown = Math.max(0, Math.min(width, Math.round(width * Math.max(0.0f, Math.min(1.0f, reveal)))));
            p.mask(masks, left, top, shown, height, height, width, height * 2, color);
            p.mask(masks, left, top, shown, height, 0, width, height * 2, Paint.mix(color, 0xFFFFFFFF, 0.18f));
        }
    }

    /**
     * Pose la courbe dans le rectangle : l'echelle couvre ses points (et le repere
     * s'il est proche), avec une marge ; une courbe plate se centre.
     *
     * @param inset     marge haute et basse, en unites
     * @param thickness epaisseur du trait, en unites
     * @param minimum   epaisseur minimale du trait, en pixels physiques
     */
    private static Plot plot(Paint p, PriceChart chart, float x, float y, float width, float height, float inset,
                             float thickness, float minimum) {
        int left = p.pixels(x);
        int top = p.pixels(y);
        int w = Math.max(1, p.pixels(x + width) - left);
        int h = Math.max(1, p.pixels(y + height) - top);
        float unit = p.unit();
        long low = chart.low();
        long high = chart.high();
        long reference = chart.reference();
        if (reference > 0 && high > low) {
            long range = high - low;
            if (reference < low && low - reference <= range / 2) {
                low = reference;
            } else if (reference > high && reference - high <= range / 2) {
                high = reference;
            }
        }
        if (high == low) {
            long pad = Math.max(1L, Math.round(Math.abs(high) * 0.08d));
            low -= pad;
            high += pad;
        }
        float half = Math.max(minimum, thickness * unit) / 2.0f;
        float margin = half + 1.0f;
        float pad = inset * unit + margin;
        int n = chart.size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int index = 0; index < n; index++) {
            xs[index] = n == 1 ? w / 2.0f : margin + (w - 2.0f * margin) * index / (n - 1.0f);
            double t = (chart.values().get(index) - low) / (double) (high - low);
            ys[index] = (float) (pad + (h - 2.0f * pad) * (1.0d - t));
        }
        if (n == 1) {
            // un seul releve : une ligne plate de bord a bord
            float[] wideX = {margin, w - margin};
            float[] wideY = {ys[0], ys[0]};
            return new Plot(unit, left, top, w, h, wideX, wideY, half, pad, low, high, chart.reference());
        }
        return new Plot(unit, left, top, w, h, xs, ys, half, pad, low, high, chart.reference());
    }

    /** Les masques d'une courbe, rasterises une fois : le trait en haut, le halo et l'aire en bas. */
    private static Identifier masks(float[] xs, float[] ys, int width, int height, float half) {
        List<Integer> points = new ArrayList<>(xs.length * 2);
        for (int index = 0; index < xs.length; index++) {
            points.add(Math.round(xs[index] * 4.0f));
            points.add(Math.round(ys[index] * 4.0f));
        }
        Key key = new Key(width, height, Math.round(half * 8.0f), points);
        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        NativeImage image = new NativeImage(width, height * 2, true);
        raster(image, xs, ys, width, height, half);
        Identifier id = Paint.registerGenerated("chart_" + serial++, image);
        CACHE.put(key, id);
        return id;
    }

    /**
     * Le trait : la couverture d'un trait rond d'epaisseur {@code 2 x half}, mesuree a
     * la distance de chaque pixel a la ligne brisee. L'aire : de la ligne jusqu'en bas,
     * un degrade ancre au plus haut point de la courbe. Le halo s'y ajoute autour du
     * trait. Tout en blanc : la couleur vient a l'affichage.
     */
    private static void raster(NativeImage image, float[] xs, float[] ys, int width, int height, float half) {
        int n = xs.length;
        float glow = half * 3.5f + 2.0f;
        float peak = Float.MAX_VALUE;
        for (float y : ys) {
            peak = Math.min(peak, y);
        }
        float depth = Math.max(1.0f, height - peak);
        float[] lower = new float[height];
        for (int px = 0; px < width; px++) {
            float cx = px + 0.5f;
            java.util.Arrays.fill(lower, 0.0f);
            // L'aire, entre le premier et le dernier point.
            if (cx >= xs[0] && cx <= xs[n - 1]) {
                float line = lineAt(xs, ys, cx);
                for (int py = Math.max(0, (int) Math.floor(line)); py < height; py++) {
                    float cover = Math.min(1.0f, Math.max(0.0f, py + 1.0f - line));
                    float t = Math.min(1.0f, Math.max(0.0f, (py + 0.5f - peak) / depth));
                    lower[py] = AREA * cover * (float) Math.pow(1.0f - t, 1.6d);
                }
            }
            // Le trait et son halo, sur les rangees que les segments voisins touchent.
            float low = Float.MAX_VALUE;
            float high = -Float.MAX_VALUE;
            for (int index = 0; index < n - 1; index++) {
                if (xs[index + 1] < cx - glow || xs[index] > cx + glow) {
                    continue;
                }
                low = Math.min(low, Math.min(ys[index], ys[index + 1]));
                high = Math.max(high, Math.max(ys[index], ys[index + 1]));
            }
            if (low <= high) {
                int from = Math.max(0, (int) Math.floor(low - glow));
                int to = Math.min(height - 1, (int) Math.ceil(high + glow));
                for (int py = from; py <= to; py++) {
                    float cy = py + 0.5f;
                    float distance = Float.MAX_VALUE;
                    for (int index = 0; index < n - 1; index++) {
                        if (xs[index + 1] < cx - glow || xs[index] > cx + glow) {
                            continue;
                        }
                        distance = Math.min(distance, distance(cx, cy, xs[index], ys[index], xs[index + 1],
                                ys[index + 1]));
                    }
                    float stroke = Math.min(1.0f, Math.max(0.0f, half + 0.5f - distance));
                    if (stroke > 0.0f) {
                        image.setPixel(px, py, white(stroke));
                    }
                    float halo = Math.max(0.0f, 1.0f - distance / glow);
                    halo = HALO * halo * halo;
                    lower[py] = 1.0f - (1.0f - lower[py]) * (1.0f - halo);
                }
            }
            for (int py = 0; py < height; py++) {
                if (lower[py] > 0.002f) {
                    image.setPixel(px, height + py, white(lower[py]));
                }
            }
        }
    }

    /** La hauteur de la ligne brisee a l'abscisse {@code x}. */
    private static float lineAt(float[] xs, float[] ys, float x) {
        for (int index = 0; index < xs.length - 1; index++) {
            if (x <= xs[index + 1]) {
                float span = xs[index + 1] - xs[index];
                float t = span < 1.0e-4f ? 1.0f : (x - xs[index]) / span;
                return ys[index] + (ys[index + 1] - ys[index]) * Math.max(0.0f, Math.min(1.0f, t));
            }
        }
        return ys[ys.length - 1];
    }

    private static float distance(float px, float py, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = dx * dx + dy * dy;
        float t = length < 1.0e-6f ? 0.0f : Math.max(0.0f, Math.min(1.0f, ((px - x0) * dx + (py - y0) * dy) / length));
        float ex = x0 + t * dx - px;
        float ey = y0 + t * dy - py;
        return (float) Math.sqrt(ex * ex + ey * ey);
    }

    private static int white(float alpha) {
        int a = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
        return (a << 24) | 0xFFFFFF;
    }
}
