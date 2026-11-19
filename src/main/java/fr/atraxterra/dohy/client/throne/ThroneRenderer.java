package fr.atraxterra.dohy.client.throne;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.palamod.throne.ThroneBlock;
import fr.atraxterra.dohy.palamod.throne.ThroneBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Le trone pose : {@code TileEntityMinerThroneRenderer} et ses freres.
 *
 * <p>Comme l'original : le modele d'entite a 1,5 bloc du sol, tourne selon
 * l'orientation, retourne (y vers le bas), et le nom du proprietaire en rouge a
 * 3,2 blocs, tourne vers la camera. Le nom ne s'affiche qu'a moins de 24 blocs.
 */
public class ThroneRenderer implements BlockEntityRenderer<ThroneBlockEntity, ThroneRenderer.State> {

    private static final double NAME_RANGE = 24.0d;

    private final ModelPart root;

    public ThroneRenderer(BlockEntityRendererProvider.Context context) {
        this.root = context.bakeLayer(ThroneGeometry.LAYER);
    }

    public static final class State extends BlockEntityRenderState {
        Identifier texture;
        float facing;
        Component owner;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ThroneBlockEntity throne, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(throne, state, crumbling);
        Identifier block = BuiltInRegistries.BLOCK.getKey(throne.getBlockState().getBlock());
        state.texture = Identifier.fromNamespaceAndPath(block.getNamespace(),
                "textures/block/" + block.getPath() + ".png");
        Direction facing = throne.getBlockState().hasProperty(ThroneBlock.FACING)
                ? throne.getBlockState().getValue(ThroneBlock.FACING) : Direction.NORTH;
        state.facing = 180.0f - facing.toYRot();
        String owner = throne.owner();
        state.owner = owner.isEmpty() || camera.distanceToSqr(Vec3.atCenterOf(throne.getBlockPos()))
                > NAME_RANGE * NAME_RANGE ? null : Component.literal(owner).withStyle(ChatFormatting.RED);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        pose.pushPose();
        pose.translate(0.5f, 1.5f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(state.facing));
        // Le demi-tour autour de z du rendu de 1.7.10 : x et y s'inversent.
        pose.scale(-1.0f, -1.0f, 1.0f);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(state.texture), (last, buffer) -> {
            PoseStack stack = new PoseStack();
            stack.last().set(last);
            root.render(stack, buffer, light, OverlayTexture.NO_OVERLAY);
        });
        pose.popPose();
        if (state.owner != null) {
            // Le nom se pose 0,5 bloc au-dessus du point d'attache : 3,2 blocs, comme l'original.
            collector.submitNameTag(pose, new Vec3(0.5d, 2.7d, 0.5d), 0, state.owner, false, light, camera);
        }
    }

    /** Le trone deborde de son bloc : un bloc de chaque cote, quatre de haut avec le nom. */
    @Override
    public AABB getRenderBoundingBox(ThroneBlockEntity throne) {
        BlockPos pos = throne.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 2, pos.getY() + 4, pos.getZ() + 2);
    }
}
