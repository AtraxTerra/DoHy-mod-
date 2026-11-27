package fr.atraxterra.dohy.client.wither;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.wither.WitherReceptacleBlock;
import fr.atraxterra.dohy.palamod.wither.WitherReceptacleBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Le receptacle a wither, avec le modele de PalaWither (son
 * {@code BlockWitherReceptacleRenderer}) : la texture eteinte, et l'allumee, aux
 * veines d'or, tant qu'une invocation est en cours. La dalle inclinee du modele
 * fait face a qui l'a pose.
 */
public class WitherReceptacleRenderer
        implements BlockEntityRenderer<WitherReceptacleBlockEntity, WitherReceptacleRenderer.State> {

    public WitherReceptacleRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static final class State extends BlockEntityRenderState {
        boolean active;
        float yaw;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WitherReceptacleBlockEntity receptacle, State state, float partialTick,
                                   Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(receptacle, state, crumbling);
        state.active = receptacle.summoning() != null;
        BlockState block = receptacle.getBlockState();
        Direction facing = block.hasProperty(WitherReceptacleBlock.FACING)
                ? block.getValue(WitherReceptacleBlock.FACING) : Direction.NORTH;
        // La dalle est du cote +x du modele, a l'ouest une fois retourne : on la tourne vers l'avant.
        state.yaw = 90.0f - facing.toYRot();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BedrockModel model = WitherModels.receptacle();
        if (model == null) {
            return;
        }
        Identifier texture = state.active ? WitherModels.RECEPTACLE_ON : WitherModels.RECEPTACLE_OFF;
        int light = state.lightCoords;
        pose.pushPose();
        pose.translate(0.5f, 0.0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(state.yaw));
        // Le repere des modeles Bedrock du moteur des monstres : retourne, sol a 24 pixels.
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);
        collector.submitCustomGeometry(pose, model.renderType(texture), (last, buffer) -> {
            model.resetPose();
            model.draw(last, buffer, light, OverlayTexture.NO_OVERLAY);
        });
        pose.popPose();
    }
}
