package fr.atraxterra.dohy.client.hunter;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Map;

/**
 * Un age de l'elephant ({@code ModelElephant}). Le modele d'origine porte les
 * trois ages, chacun sous sa racine et sur sa propre taille de texture
 * ({@code bone} en 64, {@code boneA} en 128, {@code boneB} en 160) : ce sont ici
 * trois couches, une par age ({@link ElephantGeometry}).
 *
 * <p>L'animation est celle de {@code setRotationAngles} : les pattes avant et
 * arriere en opposition, {@code cos(marche) x amplitude}, sans le facteur
 * 0,6662 du vanilla ; la tete suit le regard, sans limite.
 */
public class ElephantModel extends EntityModel<ElephantRenderState> {

    public static final ModelLayerLocation CHILD = layer("child");
    public static final ModelLayerLocation TEEN = layer("teen");
    public static final ModelLayerLocation ADULT = layer("adult");

    private final ModelPart head;
    private final ModelPart armRight;
    private final ModelPart armLeft;
    private final ModelPart legRight;
    private final ModelPart legLeft;

    private ElephantModel(ModelPart root, Map<String, ModelPart> parts, String suffix) {
        super(root);
        this.head = parts.get("head" + suffix);
        this.armRight = parts.get("armRight" + suffix);
        this.armLeft = parts.get("armLeft" + suffix);
        this.legRight = parts.get("legRight" + suffix);
        this.legLeft = parts.get("legLeft" + suffix);
    }

    private static ModelLayerLocation layer(String age) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "elephant"), age);
    }

    public static ElephantModel child(ModelPart root) {
        return new ElephantModel(root, ElephantGeometry.indexBone(root), "");
    }

    public static ElephantModel teen(ModelPart root) {
        return new ElephantModel(root, ElephantGeometry.indexBoneA(root), "A");
    }

    public static ElephantModel adult(ModelPart root) {
        return new ElephantModel(root, ElephantGeometry.indexBoneB(root), "B");
    }

    @Override
    public void setupAnim(ElephantRenderState state) {
        super.setupAnim(state);
        float swing = Mth.cos(state.walkAnimationPos) * state.walkAnimationSpeed;
        legRight.xRot = swing;
        armLeft.xRot = swing;
        armRight.xRot = -swing;
        legLeft.xRot = -swing;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
    }
}
