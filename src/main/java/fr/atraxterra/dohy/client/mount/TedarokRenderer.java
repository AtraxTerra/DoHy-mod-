package fr.atraxterra.dohy.client.mount;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.mount.Tedarok;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** {@code TedarokGeoRenderer} : une seule texture ; la course quand le cavalier court. */
public class TedarokRenderer extends MobRenderer<Tedarok, DancarokRenderState, TedarokModel> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
            "textures/entity/tedarok.png");

    public TedarokRenderer(EntityRendererProvider.Context context) {
        super(context, new TedarokModel(context.bakeLayer(TedarokModel.LAYER)), 0.8f);
    }

    @Override
    public DancarokRenderState createRenderState() {
        return new DancarokRenderState();
    }

    @Override
    public void extractRenderState(Tedarok mount, DancarokRenderState state, float partialTick) {
        super.extractRenderState(mount, state, partialTick);
        state.idleAnimation.copyFrom(mount.idleAnimation);
        state.running = mount.isRunning();
        state.texture = TEXTURE;
    }

    @Override
    public Identifier getTextureLocation(DancarokRenderState state) {
        return TEXTURE;
    }
}
