package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.season.ClientSeasons;
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

/**
 * Le HUD de l'atlas : la minimap en haut a gauche (le nord en haut, ta fleche au
 * milieu, tes coordonnees et le pays dessous) et, dans le monde, les points de
 * passage a leur place, avec leur distance.
 */
final class AtlasHud implements GuiLayer {

    private static final int MARGIN = 6;
    private static final double MAX_MARKER_DISTANCE = 6000.0d;

    private boolean loggedFailure;

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
            if (options.markers) {
                markers(graphics, minecraft, dimension);
            }
            if (options.minimap) {
                minimap(graphics, minecraft, player, map, options, dimension);
            }
        } catch (Throwable error) {
            if (!loggedFailure) {
                loggedFailure = true;
                DoHy.LOGGER.error("L'atlas a echoue, son HUD est desactive pour cette session", error);
            }
        }
    }

    private static void minimap(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, AtlasMap map,
                                AtlasClient.Options options, String dimension) {
        int size = Math.max(48, Math.min(160, options.minimapSize));
        int x0 = MARGIN + 3;
        int y0 = MARGIN + 3;
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        // le cadre des panneaux du kit : bord sombre aux coins crantes, filet gris
        ui.rect(x0 - 3, y0 - 6, size + 6, size + 12, Ui.OUTER);
        ui.rect(x0 - 6, y0 - 3, size + 12, size + 6, Ui.OUTER);
        ui.border(x0 - 2, y0 - 2, size + 4, size + 4, 2, Ui.FRAME);
        AtlasView.draw(graphics, minecraft.font, map, x0, y0, size, size, player.getX(), player.getZ(),
                options.minimapScale, new AtlasView.Layers(options.holders, options.marks, options.waypoints, false),
                dimension);
        AtlasView.player(graphics, x0 + size / 2, y0 + size / 2, player.getYRot(), size >= 80 ? 12 : 9);
        ui.centered(Ui.WIDE, "N", x0 + size / 2.0f, y0 + 2, 5.0f, Ui.WHITE);
        int panelTop = y0 + size + 5;
        AtlasData.Globe globe = AtlasData.globe();
        String where = null;
        int color = Ui.MUTED;
        if (globe != null && "minecraft:overworld".equals(dimension)) {
            EarthAtlas.Country country = globe.country(player.getX(), player.getZ());
            where = country == null ? "Eaux internationales" : country.name();
            EarthAtlas.Owner owner = country == null ? null : AtlasData.owner(country.id());
            color = owner != null && owner.kind() == 1 ? AtlasGlobe.MINE : Ui.TEXT;
        }
        // la saison et la temperature du lieu, sous le pays
        ClientSeasons.Line season = where == null ? null
                : ClientSeasons.line(minecraft.level, player.blockPosition());
        int panelHeight = 12 + (where == null ? 0 : 9) + (season == null ? 0 : 9);
        ui.rect(x0 - 3, panelTop, size + 6, panelHeight, Ui.OUTER);
        ui.rect(x0 - 2, panelTop + 1, size + 4, panelHeight - 2, Ui.BG);
        String coordinates = player.getBlockX() + "  " + player.getBlockY() + "  " + player.getBlockZ();
        ui.centered(Ui.PIXEL, ui.fit(Ui.PIXEL, coordinates, 5.0f, size + 2), x0 + size / 2.0f, panelTop + 3, 5.0f,
                Ui.TEXT);
        if (where != null) {
            ui.centered(Ui.BOLD, ui.fit(Ui.BOLD, where, 5.5f, size + 2), x0 + size / 2.0f, panelTop + 11, 5.5f, color);
        }
        if (season != null) {
            ui.centered(Ui.BOLD, ui.fit(Ui.BOLD, season.text(), 5.0f, size + 2), x0 + size / 2.0f, panelTop + 20,
                    5.0f, season.color());
        }
    }

    /** Les points de passage dans le monde : un losange, le nom et la distance, la ou ils sont a l'ecran. */
    private static void markers(GuiGraphicsExtractor graphics, Minecraft minecraft, String dimension) {
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
            int sx = (int) Math.round(width / 2.0d + right / depth / tangent * (height / 2.0d));
            int sy = (int) Math.round(height / 2.0d - up / depth / tangent * (height / 2.0d));
            if (sx < 0 || sy < 0 || sx > width || sy > height) {
                continue;
            }
            AtlasView.diamond(graphics, sx, sy, 4, 0xFF000000);
            AtlasView.diamond(graphics, sx, sy, 3, point.color());
            AtlasView.label(graphics, minecraft.font, point.name() + " (" + Math.round(distance) + " m)", sx, sy + 6,
                    point.color());
        }
    }
}
