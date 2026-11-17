package fr.atraxterra.dohy.client.arrakis;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.arrakis.SietchDweller;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Un habitant de sietch : le corps humain du modele vanilla, sous une robe du desert et un
 * capuchon (des textures originales). Ceux qui echangent ont chacun la leur ; les autres
 * l'une de quatre.
 */
public class SietchDwellerRenderer extends HumanoidMobRenderer<SietchDweller, SietchDwellerRenderState,
        HumanoidModel<SietchDwellerRenderState>> {

    private static final String[] ROLES = {"water", "artisan", "outfitter", "elder"};
    private static final Identifier[] ROLE_TEXTURES = new Identifier[ROLES.length];
    private static final Identifier[] DWELLER_TEXTURES = new Identifier[SietchDweller.VARIANTS];

    static {
        for (int i = 0; i < ROLES.length; i++) {
            ROLE_TEXTURES[i] = Identifier.fromNamespaceAndPath(DoHy.MOD_ID,
                    "textures/entity/sietch/" + ROLES[i] + ".png");
        }
        for (int i = 0; i < DWELLER_TEXTURES.length; i++) {
            DWELLER_TEXTURES[i] = Identifier.fromNamespaceAndPath(DoHy.MOD_ID,
                    "textures/entity/sietch/dweller_" + i + ".png");
        }
    }

    public SietchDwellerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public SietchDwellerRenderState createRenderState() {
        return new SietchDwellerRenderState();
    }

    @Override
    public void extractRenderState(SietchDweller dweller, SietchDwellerRenderState state, float partialTick) {
        super.extractRenderState(dweller, state, partialTick);
        state.variant = dweller.variant();
        state.role = dweller.role().ordinal();
    }

    @Override
    public Identifier getTextureLocation(SietchDwellerRenderState state) {
        if (state.role >= 0 && state.role < ROLE_TEXTURES.length) {
            return ROLE_TEXTURES[state.role];
        }
        return DWELLER_TEXTURES[Math.floorMod(state.variant, DWELLER_TEXTURES.length)];
    }
}
