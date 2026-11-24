package fr.atraxterra.dohy.client.miner;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Le gardien des cristaux sur sa geometrie d'origine ({@link MinerBossGeometry}). Les mouvements
 * reprennent {@code setRotationAngles2} de {@code MinerBossModel}, relu au bytecode :
 *
 * <ul>
 *   <li>etourdi : dos voute, tete penchee, bras ballants ;</li>
 *   <li>frappe au sol (5 ticks) : le dos plonge et le bras leve (ou les deux) s'abat ;</li>
 *   <li>coup de poing (10 ticks) : le bras droit tendu balaie, le dos tourne ;</li>
 *   <li>sinon la tete suit le regard ; bras leves (l'annonce d'une attaque) ou balancier de la
 *       marche, comme le biped de 1.7.10.</li>
 * </ul>
 * Les rotations de 1.7.10 restaient d'une image a l'autre ; ici chaque image repart de la pose de
 * repos. Le bras droit, a la marche, se balance comme le gauche : l'original ecrasait son angle de
 * balancier (une faute de frappe, {@code rotateAngleX = 0} au lieu de {@code rotateAngleZ}).
 */
public class MinerBossModel extends EntityModel<MinerBossRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "miner_boss"), "main");

    private static final float RAISED = 3.57792f;
    private static final float SLAM = 2.26892f;
    private static final float FOREARM = -0.959931f;
    private static final float WALK = 0.6662f;

    private final ModelPart back;
    private final ModelPart head;
    private final ModelPart armRightUtil;
    private final ModelPart armRight3;
    private final ModelPart armLeftUtil;
    private final ModelPart armLeft3;
    private final ModelPart legLeft;
    private final ModelPart legLeft2;
    private final ModelPart legRight;
    private final ModelPart legRight2;

    public MinerBossModel(ModelPart root) {
        super(root);
        ModelPart bone = root.getChild("bone");
        this.back = bone.getChild("body").getChild("back");
        this.head = back.getChild("head");
        this.armRightUtil = back.getChild("arm_right_util");
        this.armRight3 = armRightUtil.getChild("arm_right").getChild("arm_right2").getChild("arm_right3");
        this.armLeftUtil = back.getChild("arm_left_util");
        this.armLeft3 = armLeftUtil.getChild("arm_left").getChild("arm_left2").getChild("arm_left3");
        this.legLeft = bone.getChild("leg_left");
        this.legLeft2 = legLeft.getChild("leg_left2");
        this.legRight = bone.getChild("leg_right");
        this.legRight2 = legRight.getChild("leg_right2");
    }

    @Override
    public void setupAnim(MinerBossRenderState state) {
        super.setupAnim(state);
        if (state.stunned) {
            back.xRot = 1.0472f;
            head.zRot = 0.523599f;
            head.xRot = -0.523599f;
            armRightUtil.xRot = -1.309f;
            armLeftUtil.xRot = -1.309f;
            armRight3.xRot = FOREARM;
            armLeft3.xRot = FOREARM;
            return;
        }
        if (state.strikingGround) {
            float progress = state.strikeProgress / 5.0f;
            back.xRot = 0.1745f + progress * 0.8727f;
            boolean left = state.leftArmRaised && !state.rightArmRaised;
            boolean right = state.rightArmRaised && !state.leftArmRaised;
            if (!right) {
                armLeftUtil.xRot = RAISED + progress * SLAM;
                armLeft3.xRot = progress * FOREARM;
            }
            if (!left) {
                armRightUtil.xRot = RAISED + progress * SLAM;
                armRight3.xRot = progress * FOREARM;
            }
            return;
        }
        if (state.punching) {
            float progress = state.punchProgress / 10.0f;
            armRightUtil.xRot = -Mth.HALF_PI;
            armRightUtil.yRot = 0.73303807f - progress * 14.451327f;
            armRightUtil.zRot = 0.523599f;
            back.yRot = -progress * 0.3054326f;
            armLeftUtil.zRot = -progress * 0.349066f;
            return;
        }
        head.yRot = state.yRot * Mth.DEG_TO_RAD / 2.0f;
        head.xRot = 0.3927f + state.xRot * Mth.DEG_TO_RAD;
        boolean right = state.rightArmRaised;
        boolean left = state.leftArmRaised;
        if (state.raised) {
            back.xRot = -0.30543f;
            right = true;
            left = true;
        } else {
            back.xRot = 0.1745f;
        }
        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        if (left) {
            armLeftUtil.xRot = RAISED;
            armLeftUtil.zRot = Mth.cos(state.ageInTicks * WALK / 3.0f) / 6.0f;
        } else {
            armLeftUtil.xRot = Mth.cos(pos * WALK) * speed;
            armLeft3.xRot = -Mth.cos(pos * WALK) * speed;
            armLeftUtil.zRot = 0.0f;
        }
        if (right) {
            armRightUtil.xRot = RAISED;
            armRightUtil.zRot = -Mth.cos(state.ageInTicks * WALK / 3.0f) / 6.0f;
        } else {
            armRightUtil.xRot = Mth.cos(pos * WALK + Mth.PI) * speed;
            armRight3.xRot = -Mth.cos(pos * WALK + Mth.PI) * speed;
            armRightUtil.zRot = 0.0f;
        }
        legLeft.xRot = -0.3054f - Mth.cos(pos) * speed;
        legRight.xRot = -0.3054f + Mth.cos(pos) * speed;
        legRight2.xRot = 0.3054f - Mth.cos(pos) * speed;
        legLeft2.xRot = 0.3054f + Mth.cos(pos) * speed;
    }
}
