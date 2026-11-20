package fr.atraxterra.dohy.client.farmbot;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.farmbot.FarmBot;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Le rendu du bot de farming : un modele, une texture, et son coup de houe lu de l'entite. */
public class FarmBotRenderer extends MobRenderer<FarmBot, FarmBotRenderState, FarmBotModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/entity/farm_bot.png");

    public FarmBotRenderer(EntityRendererProvider.Context context) {
        super(context, new FarmBotModel(context.bakeLayer(FarmBotModel.LAYER)), 0.4f);
    }

    @Override
    public FarmBotRenderState createRenderState() {
        return new FarmBotRenderState();
    }

    @Override
    public void extractRenderState(FarmBot bot, FarmBotRenderState state, float partialTick) {
        super.extractRenderState(bot, state, partialTick);
        state.swing = bot.getAttackAnim(partialTick);
    }

    @Override
    public Identifier getTextureLocation(FarmBotRenderState state) {
        return TEXTURE;
    }
}
