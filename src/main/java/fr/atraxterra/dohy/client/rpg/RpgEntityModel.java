package fr.atraxterra.dohy.client.rpg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Le modele d'un monstre de donjon. Il est partage par tous les monstres du
 * meme modele : sa pose est refaite a chaque dessin, d'apres l'image
 * ({@link RpgFrame}) du monstre dessine.
 *
 * <p><b>Il se dessine lui-meme</b> ({@link #draw}), face par face, au lieu de
 * passer par {@code ModelPart.render}. Les modeles de Paladium donnent une
 * zone de texture a chaque face ; on les pose en remplacant les faces des
 * boites vanilla ({@code Cube.polygons}). Or Sodium garde, a la construction
 * de chaque boite, sa propre copie depliee « en croix » et dessine celle-la :
 * les faces remplacees etaient ignorees, et les textures sortaient melangees.
 * En ecrivant les sommets nous-memes, le resultat ne depend plus de qui
 * accelere le rendu des boites.
 */
public class RpgEntityModel extends EntityModel<LivingEntityRenderState> {

    private static final int WHITE = -1;

    private final RpgGeometry.Node tree;

    public RpgEntityModel(RpgGeometry.Built built) {
        super(built.root());
        this.tree = built.tree();
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        RpgFrame frame = state.getRenderData(RpgClient.FRAME);
        if (frame != null) {
            frame.pose();
        }
    }

    /** Dessine le modele dans sa pose du moment. */
    public void draw(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
        PoseStack stack = new PoseStack();
        stack.last().set(pose);
        draw(tree, stack, buffer, light, overlay, new Vector3f());
    }

    /** {@code ModelPart.render} et {@code Cube.compile}, a la lettre. */
    private static void draw(RpgGeometry.Node node, PoseStack stack, VertexConsumer buffer, int light, int overlay,
                             Vector3f scratch) {
        ModelPart part = node.part();
        if (!part.visible || (node.cubes().length == 0 && node.children().length == 0)) {
            return;
        }
        stack.pushPose();
        part.translateAndRotate(stack);
        if (!part.skipDraw) {
            PoseStack.Pose pose = stack.last();
            Matrix4f matrix = pose.pose();
            for (ModelPart.Cube cube : node.cubes()) {
                for (ModelPart.Polygon polygon : cube.polygons) {
                    if (polygon == null) {
                        continue;
                    }
                    Vector3f normal = pose.transformNormal(polygon.normal(), scratch);
                    float nx = normal.x();
                    float ny = normal.y();
                    float nz = normal.z();
                    for (ModelPart.Vertex vertex : polygon.vertices()) {
                        Vector3f at = matrix.transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(),
                                scratch);
                        buffer.addVertex(at.x(), at.y(), at.z(), WHITE, vertex.u(), vertex.v(), overlay, light,
                                nx, ny, nz);
                    }
                }
            }
        }
        for (RpgGeometry.Node child : node.children()) {
            draw(child, stack, buffer, light, overlay, scratch);
        }
        stack.popPose();
    }
}
