package fr.atraxterra.dohy.client.pet;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.pet.Snail;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** {@code RenderSnail} : la texture de PalaMod, une ombre d'un bloc. */
public class SnailRenderer extends MobRenderer<Snail, LivingEntityRenderState, SnailModel> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
            "textures/entity/snail.png");

    public SnailRenderer(EntityRendererProvider.Context context) {
        super(context, new SnailModel(context.bakeLayer(SnailModel.LAYER)), 1.0f);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
