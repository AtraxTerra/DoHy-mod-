package fr.atraxterra.dohy.client.pet;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** {@code ModelSnail} : les yeux se balancent doucement ({@code setRotationAngles}). */
public class SnailModel extends EntityModel<LivingEntityRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "snail"), "main");

    private final ModelPart eyes;

    public SnailModel(ModelPart root) {
        super(root);
        this.eyes = SnailGeometry.index(root).get("eyes");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        eyes.xRot = Mth.cos(state.ageInTicks * 0.6662f / 8.0f) / 8.0f + 0.5236f;
    }
}
