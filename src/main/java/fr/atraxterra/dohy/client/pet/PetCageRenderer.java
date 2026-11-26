package fr.atraxterra.dohy.client.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.palamod.pet.Familiar;
import fr.atraxterra.dohy.palamod.pet.PalaPets;
import fr.atraxterra.dohy.palamod.pet.PetCageBlock;
import fr.atraxterra.dohy.palamod.pet.PetCageBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * La cage posee : le familier entre, assis sur la paille, ou l'appat qui
 * l'attend ; et, visee de pres, ce qui se passe (appat, palier, temps qui reste).
 */
public class PetCageRenderer implements BlockEntityRenderer<PetCageBlockEntity, PetCageRenderer.State> {

    private static final double LABEL_RANGE = 8.0d;

    private final FamiliarModels models;
    private final ItemModelResolver items;

    public PetCageRenderer(BlockEntityRendererProvider.Context context) {
        this.models = new FamiliarModels(context.entityModelSet());
        this.items = context.itemModelResolver();
    }

    public static final class State extends BlockEntityRenderState {
        float facing;
        Familiar occupant;
        float age;
        final ItemStackRenderState bait = new ItemStackRenderState();
        boolean baited;
        Component line;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PetCageBlockEntity cage, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(cage, state, crumbling);
        Direction facing = cage.getBlockState().hasProperty(PetCageBlock.FACING)
                ? cage.getBlockState().getValue(PetCageBlock.FACING) : Direction.NORTH;
        state.facing = -facing.toYRot();
        long now = cage.getLevel() == null ? 0L : cage.getLevel().getGameTime();
        state.age = now + partialTick;
        state.occupant = Familiar.byId(cage.occupant());
        state.baited = cage.baited(now);
        if (state.baited) {
            items.updateForTopItem(state.bait, new ItemStack(PalaPets.PET_BAIT.get()), ItemDisplayContext.GROUND,
                    cage.getLevel(), null, 0);
        } else {
            state.bait.clear();
        }
        state.line = looked(cage.getBlockPos(), camera) ? line(cage, now) : null;
    }

    /** Ce que dit l'etiquette, selon l'etat de la cage. */
    private static Component line(PetCageBlockEntity cage, long now) {
        Familiar occupant = Familiar.byId(cage.occupant());
        if (occupant != null) {
            String when = now < cage.availableAt()
                    ? "palier " + cage.stage() + "/3 dans " + PetCageBlockEntity.clock(cage.availableAt() - now)
                    : "palier " + cage.stage() + "/3, encore " + PetCageBlockEntity.clock(cage.deadline() - now);
            return Component.literal(occupant.label() + " · " + when).withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        if (cage.baited(now)) {
            return Component.literal("Appat · " + PetCageBlockEntity.clock(cage.baitUntil() - now))
                    .withStyle(ChatFormatting.GREEN);
        }
        return Component.literal("Cage vide").withStyle(ChatFormatting.GRAY);
    }

    private static boolean looked(BlockPos pos, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos)
                && camera.distanceToSqr(Vec3.atCenterOf(pos)) < LABEL_RANGE * LABEL_RANGE;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        if (state.occupant != null) {
            pose.pushPose();
            pose.translate(0.5f, 0.0625f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(state.facing));
            // Le repere des modeles d'entite : retourne, pieds a y = 24 unites.
            pose.scale(-1.0f, -1.0f, 1.0f);
            pose.translate(0.0f, -1.5f, 0.0f);
            models.submit(state.occupant, pose, collector, light, state.age, 0.0f);
            pose.popPose();
        } else if (state.baited) {
            pose.pushPose();
            pose.translate(0.5f, 0.12f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(state.age * 2.0f));
            pose.scale(0.6f, 0.6f, 0.6f);
            state.bait.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (state.line != null) {
            collector.submitNameTag(pose, new Vec3(0.5d, 1.25d, 0.5d), 0, state.line, false, light, camera);
        }
    }

    @Override
    public AABB getRenderBoundingBox(PetCageBlockEntity cage) {
        BlockPos pos = cage.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0d, pos.getY() + 1.6d, pos.getZ() + 1.0d);
    }
}
