package fr.atraxterra.dohy.client.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atraxterra.dohy.palamod.pet.Familiar;
import fr.atraxterra.dohy.palamod.pet.PalaPets;
import fr.atraxterra.dohy.palamod.pet.PetLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Le familier sur l'epaule droite de son maitre (wiki) : les terrestres y sont
 * assis, comme le perroquet ({@code ParrotOnShoulderLayer}) ; le volant flotte a
 * cote, en ondulant. Visible de tous, sauf si son maitre l'a cache.
 */
public class PetShoulderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private final FamiliarModels models;

    public PetShoulderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, FamiliarModels models) {
        super(parent);
        this.models = models;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot,
                       float xRot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || state.isInvisible) {
            return;
        }
        Entity entity = minecraft.level.getEntity(state.id);
        if (entity == null) {
            return;
        }
        PetLook look = entity.getData(PalaPets.LOOK.get());
        Familiar familiar = look.familiar();
        if (!look.shown() || familiar == null) {
            return;
        }
        pose.pushPose();
        if (familiar.flying()) {
            // A cote de l'epaule droite, un peu au-dessus, en ondulant.
            float bob = Mth.sin(state.ageInTicks * 0.1f) * 0.06f;
            pose.translate(-0.62f, (state.isCrouching ? -1.25f : -1.45f) + bob, 0.05f);
            pose.scale(0.8f, 0.8f, 0.8f);
        } else {
            pose.translate(-0.4f, state.isCrouching ? -1.3f : -1.5f, 0.0f);
        }
        models.submit(familiar, pose, collector, light, state.ageInTicks,
                Math.min(1.0f, state.walkAnimationSpeed));
        pose.popPose();
    }
}
