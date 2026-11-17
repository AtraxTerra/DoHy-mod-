package fr.atraxterra.dohy.client.arrakis;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.arrakis.SpiceHarvester;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Le rendu de la moissonneuse : un modele, une texture, et la bouche lue de l'entite. */
public class HarvesterRenderer extends MobRenderer<SpiceHarvester, HarvesterRenderState, HarvesterModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/entity/spice_harvester.png");

    public HarvesterRenderer(EntityRendererProvider.Context context) {
        super(context, new HarvesterModel(context.bakeLayer(HarvesterModel.LAYER)), 1.7f);
    }

    @Override
    public HarvesterRenderState createRenderState() {
        return new HarvesterRenderState();
    }

    @Override
    public void extractRenderState(SpiceHarvester harvester, HarvesterRenderState state, float partialTick) {
        super.extractRenderState(harvester, state, partialTick);
        state.working = harvester.working();
    }

    @Override
    public Identifier getTextureLocation(HarvesterRenderState state) {
        return TEXTURE;
    }
}
