package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.disaster.DisasterClient;
import fr.atraxterra.dohy.client.season.ClientSeasons;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.earth.EarthAtlas;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Le HUD de l'atlas : la minimap en haut a gauche, une carte du kit aux coins
 * arrondis (le nord en haut, ta fleche au milieu, tes coordonnees, le pays, la
 * saison et les alertes dessous) et, dans le monde, les points de passage a leur
 * place, avec leur distance.
 */
final class AtlasHud implements GuiLayer {

    private static final int MARGIN = 6;
    private static final int PAD = 3;
    private static final float CARD_RADIUS = 6.0f;
    private static final float MAP_RADIUS = 4.0f;
    private static final double MAX_MARKER_DISTANCE = 6000.0d;

    private boolean loggedFailure;

    /** Une ligne de la carte sous la minimap. */
    private record Line(String text, float size, int color, boolean bold, float height) {
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.level == null || minecraft.gui.screen() instanceof AtlasScreen) {
                return;
            }
            AtlasMap map = AtlasClient.map();
            if (map != null) {
                map.upload();
            }
            AtlasClient.Options options = AtlasClient.options();
            String dimension = minecraft.level.dimension().identifier().toString();
            float unit = Math.max(1, minecraft.getWindow().getGuiScale());
            Ui.drawUnit(unit);
            if (options.markers) {
                markers(graphics, minecraft, dimension, unit);
            }
            if (options.minimap) {
                minimap(graphics, minecraft, player, map, options, dimension, unit);
            }
        } catch (Throwable error) {
            if (!loggedFailure) {
                loggedFailure = true;
                DoHy.LOGGER.error("L'atlas a echoue, son HUD est desactive pour cette session", error);
            }
        }
    }

    private static void minimap(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, AtlasMap map,
                                AtlasClient.Options options, String dimension, float unit) {
        int size = Math.max(48, Math.min(160, options.minimapSize));
        int cardX = MARGIN;
        int cardY = MARGIN;
        int cardWidth = size + PAD * 2;
        int mapX = cardX + PAD;
        int mapY = cardY + PAD;
        List<Line> lines = lines(minecraft, player, dimension, size);
        float info = 3.5f;
        for (Line line : lines) {
            info += line.height();
        }
        float cardHeight = PAD + size + info + 2.5f;
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        Paint paint = new Paint(graphics, unit);
        paint.shadow(cardX + 2, cardY + 3, cardWidth - 4, cardHeight - 3, 6.0f, Paint.alpha(Ui.Theme.SHADOW, 0.5f));
        paint.framed(cardX, cardY, cardWidth, cardHeight, CARD_RADIUS, Ui.Theme.BG2, Ui.Theme.EDGE, 1);
        AtlasView.Frame frame = new AtlasView.Frame(mapX, mapY, size, size, player.getX(), player.getZ(),
                options.minimapScale, unit);
        AtlasView.draw(graphics, minecraft.font, map, frame,
                new AtlasView.Layers(options.holders, options.marks, options.waypoints, false, options.disasters,
                        options.claims, false),
                AtlasView.Style.minimap(), dimension);
        graphics.nextStratum();
        // Les coins arrondis de la carte, peints du fond de la carte, et un filet sombre.
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1.0f / unit, 1.0f / unit);
        MapGlyphs.corners(graphics, Math.round(mapX * unit), Math.round(mapY * unit), Math.round(size * unit),
                Math.round(size * unit), Math.round(MAP_RADIUS * unit), Ui.Theme.BG2);
        pose.popMatrix();
        paint.outline(mapX, mapY, size, size, MAP_RADIUS, 0x8A000000, 1);
        AtlasView.player(graphics, unit, mapX + size / 2.0d, mapY + size / 2.0d, player.getYRot(),
                size >= 80 ? 11.0f : 9.0f);
        // Le nord : une pastille en haut de la carte.
        float north = mapX + size / 2.0f;
        paint.round(north - 4.2f, mapY + 1.8f, 8.4f, 7.2f, 3.6f, 0xD80E0F12);
        ui.centered(Ui.WIDE, "N", north + 0.15f, mapY + 3.3f, 4.2f, Ui.Theme.RED_LIGHT);
        // Les lignes sous la carte.
        float y = mapY + size + 3.5f;
        for (Line line : lines) {
            ui.text(line.bold() ? Ui.BOLD : Ui.BODY, line.text(), mapX + 1.0f, y, line.size(), line.color());
            y += line.height();
        }
    }

    /** Les coordonnees, le pays, la saison et l'alerte du moment, coupees a la largeur de la carte. */
    private static List<Line> lines(Minecraft minecraft, LocalPlayer player, String dimension, int width) {
        List<Line> lines = new ArrayList<>();
        String coordinates = player.getBlockX() + "   " + player.getBlockY() + "   " + player.getBlockZ();
        lines.add(new Line(fit(minecraft, Ui.BOLD, coordinates, 5.0f, width - 2), 5.0f, Ui.Theme.TEXT, true, 7.0f));
        AtlasData.Globe globe = AtlasData.globe();
        if (globe == null || !"minecraft:overworld".equals(dimension)) {
            return lines;
        }
        EarthAtlas.Country country = globe.country(player.getX(), player.getZ());
        String where = country == null ? "Eaux internationales" : country.name();
        EarthAtlas.Owner owner = country == null ? null : AtlasData.owner(country.id());
        int color = owner != null && owner.kind() == 1 ? AtlasGlobe.MINE : country == null ? Ui.Theme.MUTED
                : Ui.Theme.TEXT2;
        lines.add(new Line(fit(minecraft, Ui.BOLD, where, 5.3f, width - 2), 5.3f, color, true, 7.2f));
        ClientSeasons.Line season = ClientSeasons.line(minecraft.level, player.blockPosition());
        if (season != null) {
            lines.add(new Line(fit(minecraft, Ui.BODY, season.text(), 4.8f, width - 2), 4.8f, season.color(), false,
                    6.6f));
        }
        DisasterClient.Alert alert = DisasterClient.alert(player.getX(), player.getZ());
        if (alert != null) {
            // Clignote doucement : une seconde allumee, un quart de seconde attenuee.
            boolean dim = (System.currentTimeMillis() / 250L) % 5L == 4L;
            lines.add(new Line(fit(minecraft, Ui.BOLD, alert.text(), 4.8f, width - 2), 4.8f,
                    dim ? Ui.Theme.MUTED : alert.color(), true, 6.6f));
        }
        return lines;
    }

    private static String fit(Minecraft minecraft, net.minecraft.network.chat.FontDescription face, String text,
                              float size, float width) {
        if (UiGraphics.measure(minecraft.font, face, text, size) <= width) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && UiGraphics.measure(minecraft.font, face, cut + "...", size) > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    /** Les points de passage dans le monde : un losange, le nom et la distance, la ou ils sont a l'ecran. */
    private static void markers(GuiGraphicsExtractor graphics, Minecraft minecraft, String dimension, float unit) {
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());
        // le repere de la camera : devant, a droite, en haut
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        // en haut = a droite x devant
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        double tangent = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2.0d);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        Paint paint = new Paint(graphics, unit);
        for (Waypoints.Point point : Waypoints.all()) {
            if (!point.dimension().equals(dimension)) {
                continue;
            }
            double dx = point.x() + 0.5d - eye.x;
            double dy = point.y() + 1.0d - eye.y;
            double dz = point.z() + 0.5d - eye.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > MAX_MARKER_DISTANCE || distance < 2.0d) {
                continue;
            }
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth < 0.5d) {
                continue;
            }
            double right = dx * rx + dz * rz;
            double up = dx * ux + dy * uy + dz * uz;
            double sx = width / 2.0d + right / depth / tangent * (height / 2.0d);
            double sy = height / 2.0d - up / depth / tangent * (height / 2.0d);
            if (sx < 0 || sy < 0 || sx > width || sy > height) {
                continue;
            }
            float x = Math.round(sx * unit) / unit;
            float y = Math.round(sy * unit) / unit;
            AtlasView.diamond(graphics, unit, x, y, 3.4f, point.color());
            AtlasView.label(ui, paint, point.name() + "  \u00B7  " + distance(distance), x, y + 6.0f, 4.8f,
                    point.color());
        }
    }

    /** Une distance lisible : en metres, puis en kilometres. */
    static String distance(double blocks) {
        if (blocks < 1000.0d) {
            return Math.round(blocks) + " m";
        }
        double km = blocks / 1000.0d;
        return (km < 10.0d ? String.format(java.util.Locale.ROOT, "%.1f", km).replace('.', ',')
                : String.valueOf(Math.round(km))) + " km";
    }
}
