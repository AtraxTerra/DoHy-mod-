package fr.atraxterra.dohy.client.cryo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.cryo.CryoChamberBlock;
import fr.atraxterra.dohy.cryo.CryoChamberBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Le clone dans la cuve : la bete de l'ADN, en suspension au-dessus de
 * l'emetteur, tournant lentement quand la chambre tourne, figee sinon.
 *
 * <p>C'est la maniere du spawner vanilla : un modele de la bete qui n'entre
 * jamais dans le monde, dont on extrait l'etat de rendu. Elle est eclairee par la
 * cuve, pas par le monde. Le verre (couche translucide des blocs) se dessine
 * apres les entites : le givre passe devant elle.
 */
public class CryoChamberRenderer implements BlockEntityRenderer<CryoChamberBlockEntity, CryoChamberRenderer.State> {

    /** Le plancher de la cuve, au-dessus du maitre, en blocs. */
    private static final float FLOOR = 17.0f / 16.0f;
    /** La place dans la cuve : deux blocs et demi de large, deux et demi de haut. */
    private static final float ROOM = 1.9f;
    private static final int LIGHT = 0xF000F0;

    private final EntityRenderDispatcher entities;

    public CryoChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.entities = context.entityRenderer();
    }

    public static final class State extends BlockEntityRenderState {
        EntityRenderState mob;
        float spin;
        float lift;
        float scale = 1.0f;
        float facing;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CryoChamberBlockEntity chamber, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(chamber, state, crumbling);
        Entity mob = chamber.display();
        state.mob = null;
        if (mob == null || chamber.getLevel() == null) {
            return;
        }
        try {
            state.mob = entities.extractEntity(mob, partialTick);
        } catch (RuntimeException failure) {
            // Une bete d'un autre mod qui ne se dessine pas hors du monde : la chambre reste, sans clone.
            fr.atraxterra.dohy.DoHy.LOGGER.warn("Chambre cryogenique : le clone {} ne se dessine pas",
                    mob.getType(), failure);
            chamber.displayFailed();
            state.mob = null;
            return;
        }
        state.mob.lightCoords = LIGHT;
        float time = chamber.getLevel().getGameTime() + partialTick;
        boolean running = chamber.running();
        state.spin = running ? (time * 0.8f) % 360.0f : 0.0f;
        state.lift = running ? 0.18f + (float) Math.sin(time * 0.05f) * 0.06f : 0.04f;
        float size = Math.max(mob.getBbWidth(), mob.getBbHeight());
        state.scale = size > ROOM ? ROOM / size : 1.0f;
        Direction facing = chamber.getBlockState().getValue(CryoChamberBlock.FACING);
        state.facing = 180.0f - facing.toYRot();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.mob == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5f, FLOOR + state.lift, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(state.facing + state.spin));
        pose.scale(state.scale, state.scale, state.scale);
        entities.submit(state.mob, camera, 0.0d, 0.0d, 0.0d, pose, collector);
        pose.popPose();
    }

    /** Toute la chambre : le maitre est au centre du socle. */
    @Override
    public AABB getRenderBoundingBox(CryoChamberBlockEntity chamber) {
        BlockPos pos = chamber.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 2, pos.getY() + 5, pos.getZ() + 2);
    }
}
