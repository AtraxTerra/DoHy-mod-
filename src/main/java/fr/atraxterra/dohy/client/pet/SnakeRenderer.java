package fr.atraxterra.dohy.client.pet;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.pet.Snake;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** {@code RenderSnake} : la texture de PalaMod, une ombre d'un bloc. */
public class SnakeRenderer extends MobRenderer<Snake, LivingEntityRenderState, SnakeModel> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
            "textures/entity/snake.png");

    public SnakeRenderer(EntityRendererProvider.Context context) {
        super(context, new SnakeModel(context.bakeLayer(SnakeModel.LAYER)), 1.0f);
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
