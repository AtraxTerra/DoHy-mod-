package fr.atraxterra.dohy.client.farmbot;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Le bot de farming : un petit robot sur chenilles, coiffe d'un chapeau de paille, une houe a la
 * main droite. Dessine pour DoHy ; la texture (64 x 64) est faite par {@code tools/farm_bot_assets.py},
 * qui reprend les memes boites.
 *
 * <p>En marchant, il balance les bras ; quand il recolte, la main droite donne un coup de houe.
 */
public class FarmBotModel extends EntityModel<FarmBotRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "farm_bot"), "main");

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart body;

    public FarmBotModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.body = root.getChild("body");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("tracks", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0f, -3.0f, -4.0f, 8.0f, 3.0f, 8.0f), PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 11).addBox(-4.0f, -10.0f, -3.0f, 8.0f, 7.0f, 6.0f), PartPose.offset(0.0f, 24.0f, 0.0f));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(28, 11).addBox(-3.5f, -5.0f, -3.0f, 7.0f, 5.0f, 6.0f), PartPose.offset(0.0f, 14.0f, 0.0f));
        head.addOrReplaceChild("brim", CubeListBuilder.create()
                .texOffs(0, 24).addBox(-5.5f, -6.0f, -5.0f, 11.0f, 1.0f, 10.0f), PartPose.ZERO);
        head.addOrReplaceChild("crown", CubeListBuilder.create()
                .texOffs(0, 35).addBox(-3.5f, -8.0f, -3.0f, 7.0f, 2.0f, 6.0f), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(42, 24).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f), PartPose.offset(-5.0f, 15.0f, 0.0f));
        rightArm.addOrReplaceChild("hoe_handle", CubeListBuilder.create()
                .texOffs(26, 35).addBox(-0.5f, 5.0f, -6.0f, 1.0f, 1.0f, 7.0f), PartPose.ZERO);
        rightArm.addOrReplaceChild("hoe_blade", CubeListBuilder.create()
                .texOffs(42, 32).addBox(-0.5f, 5.0f, -7.0f, 1.0f, 3.0f, 1.0f), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(50, 24).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f), PartPose.offset(5.0f, 15.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(FarmBotRenderState state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        float walk = Mth.cos(state.walkAnimationPos * 0.6662f) * 0.8f * state.walkAnimationSpeed;
        rightArm.xRot = walk;
        leftArm.xRot = -walk;
        // Le coup de houe : le bras se leve puis retombe.
        if (state.swing > 0.0f) {
            rightArm.xRot = -Mth.sin(state.swing * Mth.PI) * 1.4f;
        }
        // Un leger tremblement du moteur, a l'arret comme en marche.
        float hum = Mth.sin(state.ageInTicks * 0.9f) * 0.12f;
        body.y += hum;
        head.y += hum;
        rightArm.y += hum;
        leftArm.y += hum;
    }
}
