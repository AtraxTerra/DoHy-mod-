package fr.atraxterra.dohy.client.mount;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;

/**
 * Le dancarok. Mount 6.1.0 le dessine avec Lindworm, la bibliotheque Bedrock
 * maison de Paladium ; ici la geometrie et les six animations sont converties
 * une fois pour toutes en code vanilla par {@code tools/bedrock_model.py}
 * ({@link DancarokGeometry}), ce qui evite une dependance a GeckoLib.
 *
 * <p>Comme Mount, on joue la marche (sauvage) ou la course (invoque) en
 * mouvement, l'attente a l'arret, et le coup de corne quand il est donne.
 * {@code attack} et {@code death} sont converties mais jamais jouees, comme
 * dans l'original.
 */
public class DancarokModel extends EntityModel<DancarokRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "dancarok"), "main");

    private final ModelPart head;
    private final KeyframeAnimation walk;
    private final KeyframeAnimation run;
    private final KeyframeAnimation idle;
    private final KeyframeAnimation special;

    public DancarokModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.walk = DancarokGeometry.WALKING.bake(root);
        this.run = DancarokGeometry.RUN.bake(root);
        this.idle = DancarokGeometry.IDLE.bake(root);
        this.special = DancarokGeometry.SPECIAL.bake(root);
    }

    @Override
    public void setupAnim(DancarokRenderState state) {
        super.setupAnim(state);
        // La tete suit le regard, dans les limites d'un animal massif.
        this.head.yRot += Math.clamp(state.yRot, -30.0f, 30.0f) * ((float) Math.PI / 180.0f);
        this.head.xRot += Math.clamp(state.xRot, -15.0f, 15.0f) * ((float) Math.PI / 180.0f);
        if (state.running) {
            this.run.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.5f, 2.0f);
        } else {
            this.walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0f, 2.5f);
        }
        this.idle.apply(state.idleAnimation, state.ageInTicks);
        this.special.apply(state.specialAnimation, state.ageInTicks);
    }
}
