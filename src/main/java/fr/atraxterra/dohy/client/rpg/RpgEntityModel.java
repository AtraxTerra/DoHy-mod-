package fr.atraxterra.dohy.client.rpg;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Le modele d'un monstre de donjon. Il est partage par tous les monstres du
 * meme modele : sa pose est refaite a chaque dessin, d'apres l'image
 * ({@link RpgFrame}) que porte l'etat de rendu du monstre dessine.
 */
public class RpgEntityModel extends EntityModel<LivingEntityRenderState> {

    public RpgEntityModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        RpgFrame frame = state.getRenderData(RpgClient.FRAME);
        if (frame != null) {
            frame.pose();
        }
    }
}
