package fr.atraxterra.dohy.client.palette;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.palette.CrateLayout;
import fr.atraxterra.dohy.palette.PaletteBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Les caisses d'un groupe de palettes, dessinees par son controleur (le coin
 * nord-ouest) sur toute l'emprise du groupe.
 *
 * <p>Chaque caisse est une boite texturee depuis une seule image
 * ({@code textures/entity/palette/caisses.png}, 128 x 64, deux texels par
 * pixel de modele) : caisse longue d'obus, boite metallique de munitions,
 * caisse geante de missiles. Une caisse geante qui court sur plusieurs
 * palettes est decoupee en autant de troncons, chacun avec sa texture de flanc
 * et de dessus — le troncon du milieu porte le marquage. Les dessins sont
 * propres a DoHy (voir {@code tools/dohy_assets.py}).
 *
 * <p>Le controleur n'a que l'occupation par famille ; la pile se recalcule ici
 * ({@link CrateLayout}), comme le serveur l'a calculee.
 */
public class PaletteRenderer implements BlockEntityRenderer<PaletteBlockEntity, PaletteRenderer.State> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(DoHy.MOD_ID,
            "textures/entity/palette/caisses.png");
    private static final float ATLAS_W = 128.0f;
    private static final float ATLAS_H = 64.0f;

    /** Une face de caisse dans l'image : u0, v0, u1, v1 en texels. */
    private record Face(float u0, float v0, float u1, float v1) {
    }

    /** Les faces d'une famille : flanc, flanc marque, dessus, dessus marque, bout. */
    private record Skin(Face side, Face sideMarked, Face top, Face topMarked, Face end) {
    }

    private static final Skin[] SKINS = {
        // obus : la caisse longue
        new Skin(new Face(0, 0, 28, 10), new Face(0, 0, 28, 10), new Face(0, 10, 28, 23),
                new Face(0, 10, 28, 23), new Face(28, 0, 41, 10)),
        // munitions : la boite metallique
        new Skin(new Face(0, 24, 13, 33), new Face(0, 24, 13, 33), new Face(13, 24, 26, 32),
                new Face(13, 24, 26, 32), new Face(26, 24, 34, 33)),
        // missiles : la caisse geante
        new Skin(new Face(0, 34, 32, 48), new Face(32, 34, 64, 48), new Face(64, 0, 96, 28),
                new Face(96, 0, 128, 28), new Face(64, 34, 92, 48)),
    };

    public PaletteRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** L'etat de rendu : les caisses du groupe, vides pour une palette qui n'est pas controleur. */
    public static final class State extends BlockEntityRenderState {
        List<CrateLayout.Crate> crates = List.of();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PaletteBlockEntity pallet, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(pallet, state, crumbling);
        state.crates = pallet.isController() ? pallet.clientLayout().crates() : List.of();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        List<CrateLayout.Crate> crates = state.crates;
        if (crates.isEmpty()) {
            return;
        }
        int light = state.lightCoords;
        RenderType type = RenderTypes.entityCutout(TEXTURE);
        collector.submitCustomGeometry(pose, type, (last, buffer) -> {
            for (CrateLayout.Crate crate : crates) {
                drawCrate(last, buffer, crate, light);
            }
        });
    }

    /** Le groupe entier, et la hauteur de trois etages de caisses geantes. */
    @Override
    public AABB getRenderBoundingBox(PaletteBlockEntity pallet) {
        BlockPos pos = pallet.getBlockPos();
        int w = pallet.isController() ? pallet.groupW() : 1;
        int d = pallet.isController() ? pallet.groupD() : 1;
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + w, pos.getY() + 1.6d, pos.getZ() + d);
    }

    // -- Une caisse ------------------------------------------------------------------------

    private static void drawCrate(PoseStack.Pose pose, VertexConsumer buffer, CrateLayout.Crate crate, int light) {
        Skin skin = SKINS[Math.max(0, Math.min(SKINS.length - 1, crate.family()))];
        float x0 = crate.x0() / 16.0f;
        float y0 = crate.y0() / 16.0f;
        float z0 = crate.z0() / 16.0f;
        float x1 = crate.x1() / 16.0f;
        float y1 = crate.y1() / 16.0f;
        float z1 = crate.z1() / 16.0f;
        int segments = Math.max(1, crate.segments());
        int marked = segments / 2;
        for (int k = 0; k < segments; k++) {
            Face side = k == marked ? skin.sideMarked() : skin.side();
            Face top = k == marked ? skin.topMarked() : skin.top();
            if (crate.alongX()) {
                float a = x0 + (x1 - x0) * k / segments;
                float b = x0 + (x1 - x0) * (k + 1) / segments;
                quad(pose, buffer, a, y1, z1, a, y0, z1, b, y0, z1, b, y1, z1, side, 0, 0, 1, light);
                quad(pose, buffer, b, y1, z0, b, y0, z0, a, y0, z0, a, y1, z0, side, 0, 0, -1, light);
                quad(pose, buffer, a, y1, z0, a, y1, z1, b, y1, z1, b, y1, z0, top, 0, 1, 0, light);
                quad(pose, buffer, a, y0, z1, a, y0, z0, b, y0, z0, b, y0, z1, top, 0, -1, 0, light);
            } else {
                float a = z0 + (z1 - z0) * k / segments;
                float b = z0 + (z1 - z0) * (k + 1) / segments;
                quad(pose, buffer, x1, y1, b, x1, y0, b, x1, y0, a, x1, y1, a, side, 1, 0, 0, light);
                quad(pose, buffer, x0, y1, a, x0, y0, a, x0, y0, b, x0, y1, b, side, -1, 0, 0, light);
                quad(pose, buffer, x0, y1, a, x1, y1, a, x1, y1, b, x0, y1, b, top, 0, 1, 0, light);
                quad(pose, buffer, x1, y0, a, x0, y0, a, x0, y0, b, x1, y0, b, top, 0, -1, 0, light);
            }
        }
        Face end = skin.end();
        if (crate.alongX()) {
            quad(pose, buffer, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, end, 1, 0, 0, light);
            quad(pose, buffer, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, end, -1, 0, 0, light);
        } else {
            quad(pose, buffer, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, end, 0, 0, 1, light);
            quad(pose, buffer, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, end, 0, 0, -1, light);
        }
    }

    /** Un quadrilatere : a en haut a gauche de la texture, b en bas a gauche, c en bas a droite, d en haut a droite. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             Face face, float nx, float ny, float nz, int light) {
        float u0 = face.u0() / ATLAS_W;
        float v0 = face.v0() / ATLAS_H;
        float u1 = face.u1() / ATLAS_W;
        float v1 = face.v1() / ATLAS_H;
        vertex(pose, buffer, ax, ay, az, u0, v0, nx, ny, nz, light);
        vertex(pose, buffer, bx, by, bz, u0, v1, nx, ny, nz, light);
        vertex(pose, buffer, cx, cy, cz, u1, v1, nx, ny, nz, light);
        vertex(pose, buffer, dx, dy, dz, u1, v0, nx, ny, nz, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u,
                               float v, float nx, float ny, float nz, int light) {
        buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }
}
