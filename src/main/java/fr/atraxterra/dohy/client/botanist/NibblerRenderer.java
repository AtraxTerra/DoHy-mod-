package fr.atraxterra.dohy.client.botanist;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.botanist.Nibbler;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Le nibbler, avec le modele de JobsReborn ({@code RenderBotanistNibbler}) et ses quatre
 * animations : il panique (se fige en criant), marche, ou attend ; a l'arret, il grignote de temps
 * en temps ({@code nibble}).
 */
public class NibblerRenderer extends EntityRenderer<Nibbler, NibblerRenderer.State> {

    /** Il grignote deux secondes toutes les dix, decale d'un nibbler a l'autre. */
    private static final float NIBBLE_EVERY = 10.0f;

    public NibblerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.2f;
    }

    public static class State extends EntityRenderState {
        float bodyRot;
        float walkSpeed;
        boolean panicking;
        int seed;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Nibbler nibbler, State state, float partialTick) {
        super.extractRenderState(nibbler, state, partialTick);
        state.bodyRot = Mth.rotLerp(partialTick, nibbler.yBodyRotO, nibbler.yBodyRot);
        state.walkSpeed = nibbler.walkAnimation.speed(partialTick);
        state.panicking = nibbler.panicking();
        state.seed = nibbler.getId();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BedrockModel model = BotanistModels.nibbler();
        if (model != null && !state.isInvisible) {
            float seconds = state.ageInTicks / 20.0f;
            String clip;
            float clipSeconds = seconds;
            if (state.panicking) {
                clip = "panic";
            } else if (state.walkSpeed > 0.05f) {
                clip = "walk";
            } else {
                float cycle = (seconds + (state.seed % 17)) % NIBBLE_EVERY;
                clip = cycle < model.length("nibble") ? "nibble" : "idle";
                clipSeconds = clip.equals("nibble") ? cycle : seconds;
            }
            String playing = clip;
            float at = clipSeconds;
            int light = state.lightCoords;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - state.bodyRot));
            // Le repere des modeles Bedrock du moteur des monstres : retourne, sol a 24 pixels.
            pose.scale(-1.0f, -1.0f, 1.0f);
            pose.translate(0.0f, -1.501f, 0.0f);
            collector.submitCustomGeometry(pose, model.renderType(BotanistModels.NIBBLER_TEXTURE), (last, buffer) -> {
                model.resetPose();
                model.animate(playing, at);
                model.draw(last, buffer, light, OverlayTexture.NO_OVERLAY);
            });
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }
}
