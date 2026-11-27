package fr.atraxterra.dohy.client.wither;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.wither.PalaWither;
import fr.atraxterra.dohy.palamod.wither.WitherKind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Le rendu d'un wither du pillage.
 *
 * <p><b>Les cinq genres de PalaWither</b> (baby, passif, assoiffe, predateur, supreme)
 * sont dessines avec leurs modeles du jar ({@link WitherModels}), comme le faisait son
 * {@code RenderLindwormWither} :
 * <ul>
 *   <li>a leur taille d'origine (le modele est a l'echelle du bloc ; l'attribut d'echelle
 *       du genre donne la boite de collision du jar, voir {@link WitherKind#scale}) ;</li>
 *   <li>l'animation d'attente en boucle, sans regard vers la cible ;</li>
 *   <li>sous la moitie de leurs points de vie, l'armure : la copie {@code root2} du
 *       modele, translucide, en pleine lumiere, dont l'opacite bat de 0,5 a 1 en deux
 *       secondes (le supreme n'en a pas) ;</li>
 *   <li>l'anneau et les eclats du predateur et du supreme en pleine lumiere ;</li>
 *   <li>le flash rouge des degats et la bascule de la mort, comme le vanilla.</li>
 * </ul>
 * La charge du vanilla a la naissance (l'invulnerabilite) n'existe pas dans PalaWither :
 * le wither y retrecit comme celui du vanilla et clignote d'une teinte bleue, faute de
 * peau de charge dans le jar. Le supreme, lui, dort pendant son eveil : au-dessus de
 * lui, le compte a rebours de PalaWither ; a son reveil, son animation « spawn » ; dans
 * ses dernieres secondes, « death », jusqu'a l'explosion.
 *
 * <p><b>Le wither de base et l'amusant</b> restent sur le rendu vanilla : la peau du
 * vanilla, et celle de PalaMod pour l'amusant.
 */
public class PalaWitherRenderer extends WitherBossRenderer {

    private static final Identifier FUNNY = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
            "textures/entity/wither/funny_wither.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int WHITE = -1;
    /** La teinte de la charge, a la place de la peau bleue du vanilla. */
    private static final int CHARGE_TINT = 0xFF8FB0FF;
    /** Les os racines des modeles : le corps, son armure, l'anneau et les eclats. */
    private static final String ROOT = "root";
    private static final String ARMOR = "root2";
    private static final String[] EFFECTS = {"ring", "ring2", "vfx"};

    private final WitherKind kind;
    /** Ce que le client a vu de chaque supreme : la fin de son eveil, le debut de sa mort. */
    private final Map<WitherBoss, Phase> phases = new WeakHashMap<>();

    public PalaWitherRenderer(EntityRendererProvider.Context context, WitherKind kind) {
        super(context);
        this.kind = kind;
    }

    /** L'etat d'un wither du pillage : ce que le dessin de son modele de PalaWither demande. */
    public static class State extends WitherRenderState {
        String clip = "idle";
        float clipSeconds;
        boolean armored;
        float shrink = 1.0f;
        int tint = WHITE;
        /** Le supreme : son eveil restant, en ticks. */
        int wakeTicks;
    }

    private static final class Phase {
        boolean waking;
        float awokeAt = -1.0f;
        float dyingAt = -1.0f;
    }

    @Override
    public WitherRenderState createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WitherBoss wither, WitherRenderState state, float partialTick) {
        super.extractRenderState(wither, state, partialTick);
        if (!(state instanceof State pala) || !WitherModels.bedrock(kind)) {
            return;
        }
        float age = state.ageInTicks;
        pala.clip = "idle";
        pala.clipSeconds = age / 20.0f;
        pala.shrink = 1.0f;
        pala.tint = WHITE;
        pala.wakeTicks = 0;
        int ticks = Mth.floor(state.invulnerableTicks);
        if (kind == WitherKind.SUPREME) {
            pala.armored = false;
            supreme(wither, pala, age, ticks);
            return;
        }
        pala.armored = wither.isPowered();
        if (ticks > 0) {
            // La charge du vanilla : le wither retrecit, et clignote comme sa peau bleue.
            pala.shrink = 1.0f - state.invulnerableTicks / 880.0f;
            if (ticks > 80 || ticks / 5 % 2 != 1) {
                pala.tint = CHARGE_TINT;
            }
        }
    }

    private void supreme(WitherBoss wither, State pala, float age, int ticks) {
        boolean dying = wither instanceof PalaWither supreme && supreme.dying();
        BedrockModel model = WitherModels.wither(kind);
        synchronized (phases) {
            Phase phase = phases.computeIfAbsent(wither, key -> new Phase());
            if (ticks > 0) {
                phase.waking = true;
                pala.wakeTicks = ticks;
            } else if (phase.waking) {
                phase.waking = false;
                phase.awokeAt = age;
            }
            if (dying && phase.dyingAt < 0.0f) {
                phase.dyingAt = age;
            }
            if (dying) {
                pala.clip = "death";
                pala.clipSeconds = (age - phase.dyingAt) / 20.0f;
            } else if (phase.awokeAt >= 0.0f && model != null
                    && (age - phase.awokeAt) / 20.0f < model.length("spawn")) {
                pala.clip = "spawn";
                pala.clipSeconds = (age - phase.awokeAt) / 20.0f;
            }
        }
    }

    @Override
    public void submit(WitherRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        BedrockModel model = WitherModels.wither(kind);
        if (model == null || !(state instanceof State pala)) {
            super.submit(state, pose, collector, camera);
            return;
        }
        float size = state.scale / (float) kind.scale() * pala.shrink;
        if (!state.isInvisible) {
            draw(model, pala, pose, collector, size);
        }
        if (state.nameTag != null && state.nameTagAttachment != null) {
            collector.submitNameTag(pose, state.nameTagAttachment, 0, state.nameTag, !state.isDiscrete,
                    state.lightCoords, camera);
        }
        if (pala.wakeTicks > 0) {
            countdown(model, pala, pose, collector, camera, size);
        }
    }

    private void draw(BedrockModel model, State state, PoseStack pose, SubmitNodeCollector collector, float size) {
        pose.pushPose();
        pose.scale(size, size, size);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - state.bodyRot));
        if (state.deathTime > 0.0f) {
            // La bascule de la mort du vanilla (et du RenderLindwormWither de PalaWither).
            float tilt = Mth.sqrt(Math.max(0.0f, (state.deathTime - 1.0f) / 20.0f * 1.6f));
            pose.mulPose(Axis.ZP.rotationDegrees(Math.min(1.0f, tilt) * 90.0f));
        }
        // Le repere des modeles Bedrock du moteur des monstres : retourne, sol a 24 pixels.
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);
        Identifier texture = WitherModels.texture(kind);
        int light = state.lightCoords;
        int overlay = OverlayTexture.pack(0.0f, state.hasRedOverlay);
        String clip = state.clip;
        float seconds = state.clipSeconds;
        int tint = state.tint;
        collector.submitCustomGeometry(pose, model.renderType(texture), (last, buffer) -> {
            pose(model, clip, seconds, false);
            model.draw(last, buffer, light, overlay, tint);
        });
        if (model.glows()) {
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(texture), (last, buffer) -> {
                pose(model, clip, seconds, false);
                model.drawGlow(last, buffer, overlay);
            });
        }
        if (state.armored) {
            // L'armure de PalaWither : (sin + 1) / 2 + 0,5, plafonne a 1, sur une periode de deux secondes.
            double wave = (Math.sin(Math.PI * state.ageInTicks / 20.0d) + 1.0d) / 2.0d + 0.5d;
            int alpha = Math.round((float) Math.min(1.0d, wave) * 255.0f);
            int color = (alpha << 24) | 0xFFFFFF;
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(texture), (last, buffer) -> {
                pose(model, clip, seconds, true);
                model.draw(last, buffer, FULL_BRIGHT, overlay, color);
            });
        }
        pose.popPose();
    }

    /** La pose du moment, et ce qu'on montre : le corps et ses effets, ou l'armure seule. */
    private static void pose(BedrockModel model, String clip, float seconds, boolean armor) {
        model.resetPose();
        if (!model.animate(clip, seconds)) {
            model.animate("idle", seconds);
        }
        model.setVisible(ROOT, !armor);
        model.setVisible(ARMOR, armor);
        for (String effect : EFFECTS) {
            model.setVisible(effect, !armor);
        }
    }

    /** Le compte a rebours de l'eveil du supreme, au-dessus de lui, comme sur Paladium. */
    private static void countdown(BedrockModel model, State state, PoseStack pose, SubmitNodeCollector collector,
                                  CameraRenderState camera, float size) {
        long seconds = (state.wakeTicks + 19) / 20;
        String clock = String.format("%02d : %02d : %02d : %02d", seconds / 86400, seconds / 3600 % 24,
                seconds / 60 % 60, seconds % 60);
        Vec3 above = new Vec3(0.0d, model.bounds().maxY() / 16.0f * size + 0.6d, 0.0d);
        collector.submitNameTag(pose, above, -10, Component.literal(clock).withStyle(ChatFormatting.WHITE,
                ChatFormatting.BOLD), false, FULL_BRIGHT, camera);
        collector.submitNameTag(pose, above, 0, Component.literal("jours  heures  minutes  secondes")
                .withStyle(ChatFormatting.GRAY), false, FULL_BRIGHT, camera);
    }

    @Override
    public Identifier getTextureLocation(WitherRenderState state) {
        int ticks = Mth.floor(state.invulnerableTicks);
        boolean flashing = ticks > 0 && (ticks > 80 || ticks / 5 % 2 != 1);
        if (kind == WitherKind.FUNNY && !flashing) {
            return FUNNY;
        }
        return super.getTextureLocation(state);
    }
}
