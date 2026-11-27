package fr.atraxterra.dohy.client.mount;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;

/**
 * Le Ravirok, converti de son fichier Bedrock par {@code tools/bedrock_model.py}
 * ({@link RavirokGeometry}). En vol, {@code fly} en boucle ; au sol, la marche
 * et l'attente ({@code predicateMovement} de {@code EntityRavirokMount}).
 */
public class RavirokModel extends EntityModel<DancarokRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "ravirok"), "main");

    private final ModelPart head;
    private final KeyframeAnimation idle;
    private final KeyframeAnimation walk;
    private final KeyframeAnimation fly;

    public RavirokModel(ModelPart root) {
        super(root);
        this.head = root.getChild("All").getChild("body").getChild("head");
        this.idle = RavirokGeometry.IDLE.bake(root);
        this.walk = RavirokGeometry.WALK.bake(root);
        this.fly = RavirokGeometry.FLY.bake(root);
    }

    @Override
    public void setupAnim(DancarokRenderState state) {
        super.setupAnim(state);
        this.head.yRot += Math.clamp(state.yRot, -30.0f, 30.0f) * ((float) Math.PI / 180.0f);
        this.head.xRot += Math.clamp(state.xRot, -20.0f, 20.0f) * ((float) Math.PI / 180.0f);
        if (state.flying) {
            this.fly.apply(state.flyAnimation, state.ageInTicks);
        } else {
            this.walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0f, 2.5f);
            this.idle.apply(state.idleAnimation, state.ageInTicks);
        }
    }
}
