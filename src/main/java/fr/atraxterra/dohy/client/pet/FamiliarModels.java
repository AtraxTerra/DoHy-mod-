package fr.atraxterra.dohy.client.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.pet.Familiar;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les familiers de PalaPet, prets a dessiner : sur l'epaule de leur maitre
 * ({@link PetShoulderLayer}) et dans la cage ({@link PetCageRenderer}). Pas
 * d'entite : chaque dessin pose l'animation du moment, puis dessine.
 *
 * <p>Les modeles sont ceux du jar ({@code assets/palamod/pet/<familier>}, lus par
 * {@link BedrockModel}), aux tailles tres diverses (un pingouin de 14 pixels, une
 * chauve-souris de 48 d'envergure) : chacun est mis a l'echelle de la meme place,
 * pose par sa boite la plus basse et centre. Ses animations « idle » et « walk »
 * se melangent selon la marche du maitre.
 */
public final class FamiliarModels {

    /** La place d'un familier, en blocs : hauteur, largeur, longueur. */
    private static final float FIT_HEIGHT = 0.55f;
    private static final float FIT_WIDTH = 0.7f;
    private static final float FIT_LENGTH = 0.8f;

    /** Les textures du jar qui ont des pixels a demi transparents : le dome de l'OVNI, les ailes du moustique. */
    private static final Set<Familiar> TRANSLUCENT = EnumSet.of(Familiar.UFO, Familiar.TALIKUS_MOSQUITO);

    private static final Map<Familiar, Optional<BedrockModel>> MODELS = new EnumMap<>(Familiar.class);

    public FamiliarModels() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, path);
    }

    static synchronized BedrockModel model(Familiar familiar) {
        return MODELS.computeIfAbsent(familiar, key -> Optional.ofNullable(BedrockModel.load(
                id("pet/" + key.id() + "/model.json"), id("pet/" + key.id() + "/animation.json")))).orElse(null);
    }

    /** Au rechargement des ressources. */
    static synchronized void clear() {
        MODELS.clear();
    }

    public static Identifier texture(Familiar familiar) {
        return id("textures/entity/familiar/" + familiar.id() + ".png");
    }

    /** Le portrait du menu, 96 x 96 (rendu depuis le modele du jar par tools/palapet_assets.py). */
    public static Identifier portrait(String id) {
        Familiar familiar = Familiar.byId(id);
        return id("textures/gui/pet/" + (familiar == null ? id : familiar.id()) + ".png");
    }

    /**
     * Dessine le familier dans le repere courant (pieds a y = 24 unites, la tete vers -z,
     * comme un modele d'entite deja retourne par son rendu). {@code walk} : la marche du
     * maitre (0 a 1).
     */
    public void submit(Familiar familiar, PoseStack pose, SubmitNodeCollector collector, int light, float age,
                       float walk) {
        BedrockModel model = model(familiar);
        if (model == null) {
            return;
        }
        BedrockModel.Bounds bounds = model.bounds();
        float scale = Math.min(1.0f, Math.min(FIT_HEIGHT * 16.0f / bounds.height(),
                Math.min(FIT_WIDTH * 16.0f / bounds.width(), FIT_LENGTH * 16.0f / bounds.length())));
        pose.pushPose();
        // A l'echelle de la place, pose par sa boite la plus basse et centre sur ses boites.
        pose.translate(0.0f, 1.5f, 0.0f);
        pose.scale(scale, scale, scale);
        pose.translate(-bounds.centerX() / 16.0f, -1.5f + bounds.minY() / 16.0f, -bounds.centerZ() / 16.0f);
        float seconds = age / 20.0f;
        float walking = Mth.clamp(walk, 0.0f, 1.0f);
        Identifier texture = texture(familiar);
        collector.submitCustomGeometry(pose, TRANSLUCENT.contains(familiar)
                        ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityCutout(texture),
                (last, buffer) -> {
                    model.resetPose();
                    if (walking < 0.999f) {
                        model.animate("idle", seconds, 1.0f - walking);
                    }
                    if (walking > 0.001f) {
                        model.animate("walk", seconds, walking);
                    }
                    model.draw(last, buffer, light, OverlayTexture.NO_OVERLAY);
                });
        pose.popPose();
    }
}
