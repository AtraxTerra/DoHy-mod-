package fr.atraxterra.dohy.client.arrakis;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.arrakis.SpiceHarvester;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Le rendu de la moissonneuse : un modele, une texture, la bouche lue de
 * l'entite, et ses lumieres — phares, feux, gyrophare, hublots ou l'epice luit —
 * en pleine lumiere, meme la nuit.
 */
public class HarvesterRenderer extends MobRenderer<SpiceHarvester, HarvesterRenderState, HarvesterModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/entity/spice_harvester.png");
    private static final Identifier GLOW =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/entity/spice_harvester_glow.png");

    public HarvesterRenderer(EntityRendererProvider.Context context) {
        super(context, new HarvesterModel(context.bakeLayer(HarvesterModel.LAYER)), 1.7f);
        addLayer(new Lights(this));
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

    /** Les lumieres de la machine, dans leur propre texture. */
    private static final class Lights extends EyesLayer<HarvesterRenderState, HarvesterModel> {

        private final RenderType type;

        Lights(RenderLayerParent<HarvesterRenderState, HarvesterModel> parent) {
            super(parent);
            this.type = RenderTypes.eyes(GLOW);
        }

        @Override
        public RenderType renderType() {
            return type;
        }
    }
}
