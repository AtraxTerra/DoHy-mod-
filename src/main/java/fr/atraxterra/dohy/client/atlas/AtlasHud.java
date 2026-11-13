package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthAtlas;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Le HUD de l'atlas : la minimap en haut a gauche (le nord en haut, ta fleche au
 * milieu, tes coordonnees et le pays dessous) et, dans le monde, les points de
 * passage a leur place, avec leur distance.
 */
final class AtlasHud implements GuiLayer {

    private static final int SIZE = 112;
    private static final int MARGIN = 6;
    private static final double MAX_MARKER_DISTANCE = 6000.0d;

    private boolean loggedFailure;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.level == null || minecraft.options.hideGui
                    || minecraft.gui.screen() instanceof AtlasScreen) {
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
        int x0 = MARGIN;
        int y0 = MARGIN;
        graphics.fill(x0 - 2, y0 - 2, x0 + SIZE + 2, y0 + SIZE + 2, 0xFF15141A);
        graphics.fill(x0 - 1, y0 - 1, x0 + SIZE + 1, y0 + SIZE + 1, 0xFF40404A);
        AtlasView.draw(graphics, minecraft.font, map, x0, y0, SIZE, SIZE, player.getX(), player.getZ(),
                options.minimapScale, new AtlasView.Layers(options.holders, options.marks, options.waypoints, false),
                dimension);
        AtlasView.player(graphics, x0 + SIZE / 2, y0 + SIZE / 2, player.getYRot(), 12);
        graphics.text(minecraft.font, Component.literal("N"), x0 + SIZE / 2 - 2, y0 + 2, 0xFFFFFFFF, true);
        String coordinates = player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
        AtlasView.label(graphics, minecraft.font, coordinates, x0 + SIZE / 2, y0 + SIZE + 4, 0xFFE9E8EE);
        AtlasData.Globe globe = AtlasData.globe();
        if (globe != null && "minecraft:overworld".equals(dimension)) {
            EarthAtlas.Country country = globe.country(player.getX(), player.getZ());
            String where = country == null ? "Eaux internationales" : country.name();
            EarthAtlas.Owner owner = country == null ? null : AtlasData.owner(country.id());
            int color = owner == null ? 0xFFB0B0B8 : owner.kind() == 1 ? AtlasGlobe.MINE : 0xFFB0B0B8;
            AtlasView.label(graphics, minecraft.font, where, x0 + SIZE / 2, y0 + SIZE + 15, color);
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
