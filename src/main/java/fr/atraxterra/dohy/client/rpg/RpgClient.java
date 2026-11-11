package fr.atraxterra.dohy.client.rpg;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeon;
import fr.atraxterra.dohy.palamod.dungeon.RpgSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Le rendu des monstres de donjon : un monstre vanilla qui porte une
 * apparence ({@link PalaDungeon#SKIN}) est dessine avec son modele de
 * Paladium a la place du sien.
 *
 * <p>En deux temps, comme tout rendu de la 26 : a l'extraction, on calcule
 * l'image du monstre ({@link RpgFrame}) et on la range dans son etat de
 * rendu ; au dessin, on annule le rendu vanilla (corps, armure, objets en
 * main) et on soumet le modele. Le nom au-dessus de la tete, que le rendu
 * annule aurait affiche, est remis, plus haut si le modele est plus grand.
 * L'ombre et la boite de collision restent celles du monstre vanilla.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class RpgClient {

    static final ContextKey<RpgFrame> FRAME = new ContextKey<>(Identifier.fromNamespaceAndPath("palarpg", "frame"));

    private static final Map<LivingEntity, RpgAnimator> ANIMATORS = new WeakHashMap<>();

    private RpgClient() {
    }

    @SubscribeEvent
    public static void onReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath("palarpg", "models"),
                (ResourceManagerReloadListener) manager -> {
                    synchronized (ANIMATORS) {
                        RpgModels.clear();
                        ANIMATORS.clear();
                    }
                });
    }

    @SubscribeEvent
    public static void onModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {
        }, RpgClient::extract);
    }

    /**
     * Sous verrou : certains moteurs de rendu (Crankshaft) extraient les etats
     * des creatures sur plusieurs fils a la fois, et ni le cache des modeles ni
     * la table des animateurs ne le supportent.
     */
    private static void extract(LivingEntity entity, LivingEntityRenderState state) {
        RpgSkin skin = entity.getExistingDataOrNull(PalaDungeon.SKIN.get());
        if (skin == null || skin.isNone()) {
            return;
        }
        synchronized (ANIMATORS) {
            RpgModels.Entry entry = RpgModels.get(skin.model());
            if (entry == null) {
                return;
            }
            RpgAnimator animator = ANIMATORS.computeIfAbsent(entity, key -> new RpgAnimator());
            state.setRenderData(FRAME,
                    animator.frame(entity, skin, entry, state.partialTick, state.walkAnimationSpeed));
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLivingEvent.Pre<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        RpgFrame frame = state.getRenderData(FRAME);
        if (frame == null) {
            return;
        }
        event.setCanceled(true);
        if (state.isInvisible) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        float scale = frame.scale();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - state.bodyRot));
        pose.scale(-scale, -scale, scale);
        pose.translate(0.0f, -1.501f, 0.0f);
        collector.submitModel(frame.entry().model(), state, pose, frame.texture(), state.lightCoords,
                OverlayTexture.pack(0.0f, state.hasRedOverlay), state.outlineColor, null);
        pose.popPose();

        if (state.nameTag != null) {
            CameraRenderState camera = Minecraft.getInstance().gameRenderer.gameRenderState()
                    .levelRenderState.cameraRenderState;
            double above = frame.entry().height() * scale + 0.3d;
            Vec3 attachment = state.nameTagAttachment;
            if (attachment == null || attachment.y < above) {
                attachment = new Vec3(0.0d, above, 0.0d);
            }
            collector.submitNameTag(pose, attachment, 0, state.nameTag, !state.isDiscrete, state.lightCoords, camera);
        }
    }
}
