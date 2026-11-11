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
    /** La lumiere maximale : un os lumineux ne s'assombrit pas la nuit. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final RpgGeometry.Node tree;
    private final boolean glows;

    public RpgEntityModel(RpgGeometry.Built built) {
        super(built.root());
        this.tree = built.tree();
        this.glows = built.glows();
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        RpgFrame frame = state.getRenderData(RpgClient.FRAME);
        if (frame != null) {
            frame.pose();
        }
    }

    /** Le modele a des os lumineux : il lui faut la seconde passe ({@link #drawGlow}). */
    public boolean glows() {
        return glows;
    }

    /** Dessine le corps du modele dans sa pose du moment. */
    public void draw(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
        PoseStack stack = new PoseStack();
        stack.last().set(pose);
        draw(tree, stack, buffer, light, overlay, new Vector3f(), false, false);
    }

    /**
     * Dessine les os lumineux (rayons, faisceaux, eclats des boxes), pour un
     * rendu translucide. Deux differences avec le corps :
     * <ul>
     *   <li>pleine lumiere, quelle que soit celle du lieu ;</li>
     *   <li>les faces qui tournent le dos a la camera sont sautees. Les rayons
     *       de Paladium sont une boite translucide dans une coque retournee
     *       (taille negative) d'une couleur claire : sans ce tri, la coque
     *       cache le rayon ; avec, on n'en voit que le fond, derriere lui.</li>
     * </ul>
     */
    public void drawGlow(PoseStack.Pose pose, VertexConsumer buffer, int overlay) {
        PoseStack stack = new PoseStack();
        stack.last().set(pose);
        draw(tree, stack, buffer, FULL_BRIGHT, overlay, new Vector3f(), true, false);
    }

    /**
     * {@code ModelPart.render} et {@code Cube.compile}, a la lettre.
     *
     * @param glowPass on dessine les os lumineux (vrai) ou les autres (faux)
     * @param inGlow   on est sous un os lumineux
     */
    private static void draw(RpgGeometry.Node node, PoseStack stack, VertexConsumer buffer, int light, int overlay,
                             Vector3f scratch, boolean glowPass, boolean inGlow) {
        ModelPart part = node.part();
        if (!part.visible || (node.cubes().length == 0 && node.children().length == 0)) {
            return;
        }
        inGlow |= node.glow();
        stack.pushPose();
        part.translateAndRotate(stack);
        if (!part.skipDraw && inGlow == glowPass) {
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
                    ModelPart.Vertex[] vertices = polygon.vertices();
                    if (glowPass) {
                        // La camera est a l'origine : une face dont la normale
                        // s'eloigne d'elle lui tourne le dos.
                        Vector3f first = matrix.transformPosition(vertices[0].worldX(), vertices[0].worldY(),
                                vertices[0].worldZ(), scratch);
                        if (first.x() * nx + first.y() * ny + first.z() * nz >= 0.0f) {
                            continue;
                        }
                    }
                    for (ModelPart.Vertex vertex : vertices) {
                        Vector3f at = matrix.transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(),
                                scratch);
                        buffer.addVertex(at.x(), at.y(), at.z(), WHITE, vertex.u(), vertex.v(), overlay, light,
                                nx, ny, nz);
                    }
                }
            }
        }
        for (RpgGeometry.Node child : node.children()) {
            draw(child, stack, buffer, light, overlay, scratch, glowPass, inGlow);
        }
        stack.popPose();
    }
}
