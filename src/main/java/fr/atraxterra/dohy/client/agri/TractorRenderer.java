package fr.atraxterra.dohy.client.agri;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.agri.Tractor;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Le rendu du tracteur : un modele, une texture, et l'outil attele lu de l'entite. */
public class TractorRenderer extends MobRenderer<Tractor, TractorRenderState, TractorModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/entity/tractor.png");

    public TractorRenderer(EntityRendererProvider.Context context) {
        super(context, new TractorModel(context.bakeLayer(TractorModel.LAYER)), 1.1f);
    }

    @Override
    public TractorRenderState createRenderState() {
        return new TractorRenderState();
    }

    @Override
    public void extractRenderState(Tractor tractor, TractorRenderState state, float partialTick) {
        super.extractRenderState(tractor, state, partialTick);
        state.tool = tractor.tool();
        state.working = tractor.working();
    }

    @Override
    public Identifier getTextureLocation(TractorRenderState state) {
        return TEXTURE;
    }
}
