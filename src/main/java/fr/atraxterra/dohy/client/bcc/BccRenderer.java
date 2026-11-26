package fr.atraxterra.dohy.client.bcc;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atraxterra.dohy.faction.bcc.BccBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Le bloc affiche, quand on le regarde, a qui il est et combien d'intrus rodent a 200 blocs. */
public class BccRenderer implements BlockEntityRenderer<BccBlockEntity, BccRenderer.State> {

    public BccRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static final class State extends BlockEntityRenderState {
        Component owner;
        Component watch;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BccBlockEntity bcc, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(bcc, state, crumbling);
        Minecraft minecraft = Minecraft.getInstance();
        boolean looked = minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(bcc.getBlockPos())
                && camera.distanceToSqr(Vec3.atCenterOf(bcc.getBlockPos())) < 12.0d * 12.0d;
        if (!looked) {
            state.owner = null;
            state.watch = null;
            return;
        }
        String who = bcc.rival().isEmpty() ? bcc.ownerName()
                : bcc.rival().startsWith("Les ") ? "les " + bcc.rival().substring(4) : "les " + bcc.rival();
        state.owner = Component.literal("Base Claim Controller · " + who).withStyle(ChatFormatting.GOLD);
        int intruders = bcc.intruders();
        state.watch = Component.literal(intruders == 0 ? "Personne a 200 blocs" : intruders + " intrus a 200 blocs")
                .withStyle(intruders == 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.owner != null) {
            collector.submitNameTag(pose, new Vec3(0.5d, 1.5d, 0.5d), -10, state.owner, false, state.lightCoords,
                    camera);
            collector.submitNameTag(pose, new Vec3(0.5d, 1.5d, 0.5d), 0, state.watch, false, state.lightCoords,
                    camera);
        }
    }

    @Override
    public AABB getRenderBoundingBox(BccBlockEntity bcc) {
        BlockPos pos = bcc.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0d, pos.getY() + 2.0d, pos.getZ() + 1.0d);
    }
}
