package fr.atraxterra.dohy.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Volcania : le rendu du dragon de l'Ender, avec la texture du PalaMod.
 *
 * <p>Le rendu vanilla ecrit sa texture en dur dans {@code submit}. On reprend ici
 * son {@code submit}, relu au bytecode — meme rotation tiree de l'historique de
 * vol (lacet a 7 images, tangage entre la 5e et la 10e), memes translations —
 * avec notre texture. L'extraction de l'etat (battement d'ailes, historique)
 * reste celle de la classe mere. Ni rayons de mort ni yeux lumineux : la
 * texture de Volcania porte ses propres yeux.
 */
public class VolcaniaRenderer extends EnderDragonRenderer {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
            "textures/entity/egghunt/volcania.png");

    private final EnderDragonModel model;

    public VolcaniaRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new EnderDragonModel(context.bakeLayer(ModelLayers.ENDER_DRAGON));
    }

    @Override
    public void submit(EnderDragonRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        pose.pushPose();
        float yaw = state.getHistoricalPos(7).yRot();
        float pitch = (float) (state.getHistoricalPos(5).y() - state.getHistoricalPos(10).y());
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch * 10.0f));
        pose.translate(0.0f, 0.0f, 1.0f);
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);
        int overlay = OverlayTexture.pack(0.0f, state.hasRedOverlay);
        collector.submitModel(model, state, pose, TEXTURE, state.lightCoords, overlay, state.outlineColor, null);
        pose.popPose();
    }
}
