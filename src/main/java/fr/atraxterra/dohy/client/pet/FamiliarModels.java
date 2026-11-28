package fr.atraxterra.dohy.client.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.pet.Familiar;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

/**
 * Les trois familiers, prets a dessiner : sur l'epaule de leur maitre
 * ({@link PetShoulderLayer}) et dans la cage ({@link PetCageRenderer}). Pas
 * d'entite : chaque appel pose les angles du moment, puis dessine.
 */
public final class FamiliarModels {

    public static final Map<Familiar, ModelLayerLocation> LAYERS = layers();

    private final Map<Familiar, ModelPart> roots = new EnumMap<>(Familiar.class);

    private static Map<Familiar, ModelLayerLocation> layers() {
        Map<Familiar, ModelLayerLocation> layers = new EnumMap<>(Familiar.class);
        for (Familiar familiar : Familiar.values()) {
            layers.put(familiar, new ModelLayerLocation(
                    Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "familiar_" + familiar.id()), "main"));
        }
        return layers;
    }

    public FamiliarModels(EntityModelSet models) {
        for (Familiar familiar : Familiar.values()) {
            roots.put(familiar, models.bakeLayer(LAYERS.get(familiar)));
        }
    }

    public static Identifier texture(Familiar familiar) {
        return Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
                "textures/entity/familiar/" + familiar.id() + ".png");
    }

    /** Le portrait du menu, 96 x 96. */
    public static Identifier portrait(String id) {
        return Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "textures/gui/pet/" + id + ".png");
    }

    /**
     * Les angles du moment : la queue et les oreilles bougent, le volant ondule et
     * fait tourner ses etincelles. {@code walk} : la marche du maitre (0 a 1).
     */
    private void animate(Familiar familiar, ModelPart root, float age, float walk) {
        root.getAllParts().forEach(ModelPart::resetPose);
        switch (familiar) {
            case EMBER_LYNX, PUP -> {
                ModelPart body = root.getChild("body");
                ModelPart tail = body.getChild("tail");
                tail.yRot = Mth.sin(age * 0.18f) * 0.35f;
                tail.xRot += Mth.sin(age * 0.09f) * 0.08f;
                ModelPart head = body.getChild("head");
                head.yRot = Mth.sin(age * 0.035f) * 0.3f;
                head.xRot = Mth.sin(age * 0.05f + 1.0f) * 0.06f;
                float step = Mth.cos(age * 0.6f) * 0.5f * walk;
                body.getChild("leg_front_left").xRot = step;
                body.getChild("leg_back_right").xRot = step;
                body.getChild("leg_front_right").xRot = -step;
                body.getChild("leg_back_left").xRot = -step;
                if (familiar == Familiar.PUP) {
                    head.getChild("ear_left").zRot += Mth.sin(age * 0.2f) * 0.08f;
                    head.getChild("ear_right").zRot -= Mth.sin(age * 0.2f) * 0.08f;
                }
            }
            case WISP -> {
                ModelPart flame = root.getChild("flame");
                flame.y += Mth.sin(age * 0.12f) * 1.2f;
                flame.yRot = age * 0.04f;
                flame.getChild("tip").zRot = Mth.sin(age * 0.3f) * 0.12f;
                flame.getChild("sparks").yRot = -age * 0.15f;
            }
        }
    }

    /**
     * Dessine le familier dans le repere courant (pieds a y = 24 unites, la tete vers -z,
     * comme un modele d'entite deja retourne par son rendu). Le volant brille dans le noir.
     */
    public void submit(Familiar familiar, PoseStack pose, SubmitNodeCollector collector, int light, float age,
                       float walk) {
        ModelPart root = roots.get(familiar);
        if (root == null) {
            return;
        }
        animate(familiar, root, age, walk);
        int used = familiar.flying() ? 0xF000F0 : light;
        collector.submitCustomGeometry(pose, familiar.flying()
                        ? RenderTypes.entityTranslucent(texture(familiar)) : RenderTypes.entityCutout(texture(familiar)),
                (last, buffer) -> {
                    PoseStack stack = new PoseStack();
                    stack.last().set(last);
                    root.render(stack, buffer, used, OverlayTexture.NO_OVERLAY);
                });
    }
}
