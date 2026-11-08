package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.golem.GuardianGolem;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;

/**
 * {@code RenderGolem} : le {@code ModelGolem} de GuardianGolem est une copie du
 * golem de fer de 1.7.10, sur la meme texture de 128 x 128. Le modele vanilla
 * de 26.2 le reprend tel quel ; seuls les bras ne balancent qu'en frappant,
 * comme dans l'original. Ni fissures ni fleur.
 */
public class GuardianGolemRenderer extends MobRenderer<GuardianGolem, IronGolemRenderState, IronGolemModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "textures/entity/guardian_golem.png");

    public GuardianGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new IronGolemModel(context.bakeLayer(ModelLayers.IRON_GOLEM)), 0.5f);
    }

    @Override
    public IronGolemRenderState createRenderState() {
        return new IronGolemRenderState();
    }

    @Override
    public void extractRenderState(GuardianGolem golem, IronGolemRenderState state, float partialTick) {
        super.extractRenderState(golem, state, partialTick);
        state.attackTicksRemaining = golem.attackAnimationTick() > 0 ? golem.attackAnimationTick() - partialTick : 0.0f;
        state.offerFlowerTick = 0;
    }

    @Override
    public Identifier getTextureLocation(IronGolemRenderState state) {
        return TEXTURE;
    }
}
