package fr.atraxterra.dohy.client.pet;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Map;

/**
 * {@code ModelSnake} : la tete et la machoire en avant, huit anneaux de queue qui
 * ondulent en marchant. Les angles sont ceux de {@code setRotationAngles}, ses
 * maladresses comprises (la tete tourne avec le tangage, le premier anneau garde
 * 0,1). Le rendu d'origine reculait le serpent de deux blocs
 * ({@code glTranslated(0, 0, -2)}) pour le centrer sur sa boite.
 */
public class SnakeModel extends EntityModel<LivingEntityRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "snake"), "main");

    private static final float SWING = 0.6662f;
    /** Deux blocs en pixels : le {@code glTranslated(0, 0, -2)} du rendu. */
    private static final float SHIFT = -32.0f;

    private final ModelPart bone;
    private final ModelPart head;
    private final ModelPart[] tails = new ModelPart[8];

    public SnakeModel(ModelPart root) {
        super(root);
        Map<String, ModelPart> parts = SnakeGeometry.index(root);
        this.bone = parts.get("bone");
        this.head = parts.get("head");
        for (int index = 0; index < tails.length; index++) {
            tails[index] = parts.get("tail" + (index + 1));
        }
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        bone.z += SHIFT;
        head.yRot = Mth.cos(pos * SWING + Mth.PI) * speed + state.xRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD + 0.1f;
        tails[0].yRot = 0.1f;
        tails[1].yRot = Mth.cos(pos * SWING * 0.5f) * speed;
        tails[2].yRot = Mth.cos(pos * SWING * 0.5f + Mth.PI) * speed;
        tails[2].xRot = -0.1f;
        tails[3].yRot = Mth.cos(pos * SWING * 0.5f + Mth.PI) * speed;
        tails[3].xRot = -0.1f;
        tails[4].yRot = Mth.cos(pos * SWING) * speed;
        tails[5].yRot = Mth.cos(pos * SWING + Mth.PI) * speed;
        tails[6].yRot = Mth.cos(pos * SWING + Mth.PI) * speed;
        tails[7].yRot = Mth.cos(pos * SWING) * speed;
    }
}
