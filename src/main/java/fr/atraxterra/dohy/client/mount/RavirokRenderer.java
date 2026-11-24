package fr.atraxterra.dohy.client.mount;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.mount.Ravirok;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** {@code RavirokGeoRenderer} : une seule texture. */
public class RavirokRenderer extends MobRenderer<Ravirok, DancarokRenderState, RavirokModel> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
            "textures/entity/ravirok.png");

    public RavirokRenderer(EntityRendererProvider.Context context) {
        super(context, new RavirokModel(context.bakeLayer(RavirokModel.LAYER)), 0.9f);
    }

    @Override
    public DancarokRenderState createRenderState() {
        return new DancarokRenderState();
    }

    @Override
    public void extractRenderState(Ravirok mount, DancarokRenderState state, float partialTick) {
        super.extractRenderState(mount, state, partialTick);
        state.idleAnimation.copyFrom(mount.idleAnimation);
        state.flyAnimation.copyFrom(mount.flyAnimation);
        state.flying = mount.isVehicle() && !mount.onGround();
        state.texture = TEXTURE;
    }

    @Override
    public Identifier getTextureLocation(DancarokRenderState state) {
        return TEXTURE;
    }
}
