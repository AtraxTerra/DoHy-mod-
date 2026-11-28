package fr.atraxterra.dohy.client.botanist;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.botanist.TallMachineBlock;
import fr.atraxterra.dohy.palamod.botanist.WideMachineBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Une machine du laboratoire du lot 2c, avec son modele de JobsReborn
 * ({@code TileEntityBotanistMachineRenderer}) : l'animation de son etat, depuis qu'elle a commence,
 * le modele reduit a ses blocs et recentre, tourne vers le joueur.
 *
 * <p>Une machine large ({@link WideMachineBlock}) est dessinee depuis sa partie principale, au milieu
 * de ses deux colonnes.
 */
public abstract class LabMachineRenderer<T extends BlockEntity>
        implements BlockEntityRenderer<T, LabMachineRenderer.State> {

    /** L'echelle du modele, et son centre dans le fichier (pixels) : on le ramene au centre des blocs. */
    private final float scale;
    private final float centerX;
    private final float centerZ;
    private final boolean wide;

    protected LabMachineRenderer(float scale, float centerX, float centerZ, boolean wide) {
        this.scale = scale;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.wide = wide;
    }

    public static final class State extends BlockEntityRenderState {
        String clip = "idle";
        float seconds;
        float yaw;
        float offsetX;
        float offsetZ;
    }

    protected abstract BedrockModel model();

    protected abstract Identifier texture();

    /** L'animation de la machine. */
    protected abstract String clip(T machine);

    /** Ou en est l'animation, en secondes ({@code age} en ticks, interpole). */
    protected abstract float seconds(T machine, float age);

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T machine, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(machine, state, crumbling);
        long now = machine.getLevel() == null ? 0L : machine.getLevel().getGameTime();
        state.clip = clip(machine);
        state.seconds = seconds(machine, now + partialTick);
        Direction facing = facing(machine.getBlockState());
        state.yaw = 180.0f - facing.toYRot();
        if (wide) {
            Direction right = WideMachineBlock.toRight(facing);
            state.offsetX = right.getStepX() * 0.5f;
            state.offsetZ = right.getStepZ() * 0.5f;
        } else {
            state.offsetX = 0.0f;
            state.offsetZ = 0.0f;
        }
    }

    private static Direction facing(BlockState state) {
        if (state.hasProperty(WideMachineBlock.FACING)) {
            return state.getValue(WideMachineBlock.FACING);
        }
        if (state.hasProperty(TallMachineBlock.FACING)) {
            return state.getValue(TallMachineBlock.FACING);
        }
        return Direction.NORTH;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BedrockModel model = model();
        if (model == null) {
            return;
        }
        int light = state.lightCoords;
        String clip = state.clip;
        float seconds = state.seconds;
        pose.pushPose();
        pose.translate(0.5f + state.offsetX, 0.0f, 0.5f + state.offsetZ);
        pose.mulPose(Axis.YP.rotationDegrees(state.yaw));
        pose.scale(scale, scale, scale);
        pose.translate(centerX / 16.0f, 0.0f, -centerZ / 16.0f);
        // Le repere des modeles Bedrock du moteur des monstres : retourne, sol a 24 pixels.
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);
        collector.submitCustomGeometry(pose, model.renderType(texture()), (last, buffer) -> {
            model.resetPose();
            model.animate(clip, seconds);
            model.draw(last, buffer, light, OverlayTexture.NO_OVERLAY);
        });
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(T machine) {
        BlockPos pos = machine.getBlockPos();
        return new AABB(pos.getX() - 1.0d, pos.getY(), pos.getZ() - 1.0d, pos.getX() + 2.0d, pos.getY() + 2.2d,
                pos.getZ() + 2.0d);
    }
}
