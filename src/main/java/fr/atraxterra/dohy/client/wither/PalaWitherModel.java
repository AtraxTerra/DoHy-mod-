package fr.atraxterra.dohy.client.wither;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.wither.WitherBossModel;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.util.Mth;

/**
 * Le modele d'un wither du pillage : celui du vanilla (le squelette de
 * {@link PalaWitherGeometry} en garde les noms, il l'anime donc pareil), plus
 * les bras du supreme, qui se balancent lentement sous lui.
 */
public class PalaWitherModel extends WitherBossModel {

    static final float ARM_PITCH = -0.2F;
    static final float ARM_SPREAD = 0.12F;

    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public PalaWitherModel(ModelPart root, boolean armed) {
        super(root);
        this.rightArm = armed ? root.getChild("right_arm") : null;
        this.leftArm = armed ? root.getChild("left_arm") : null;
    }

    @Override
    public void setupAnim(WitherRenderState state) {
        super.setupAnim(state);
        if (rightArm == null) {
            return;
        }
        float sway = Mth.cos(state.ageInTicks * 0.08F) * 0.1F;
        rightArm.xRot = ARM_PITCH + sway;
        leftArm.xRot = ARM_PITCH - sway;
        float spread = Mth.sin(state.ageInTicks * 0.05F) * 0.04F;
        rightArm.zRot = ARM_SPREAD + spread;
        leftArm.zRot = -ARM_SPREAD - spread;
    }
}
