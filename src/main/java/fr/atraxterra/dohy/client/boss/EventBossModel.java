package fr.atraxterra.dohy.client.boss;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.boss.BossKind;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Les cinq boss d'event, sur leur geometrie d'origine (voir les classes
 * {@code *Geometry}, traduites du PalaMod).
 *
 * <p>Les mouvements reprennent les {@code setRotationAngles} du PalaMod, relus
 * au bytecode : jambes et bras au balancier de la marche (0,6662, comme le
 * modele biped de 1.7.10), tete qui suit le regard, et pour chacun sa piece
 * vivante — engrenages et foret de Beep Boop, queue de sable d'Azhur, nuque de
 * Morsula, crochets d'Arachna. Les etoiles de Tobalt ne se montrent que quand il
 * est etourdi.
 */
public class EventBossModel extends EntityModel<EventBossRenderState> {

    private static final float DEG = Mth.DEG_TO_RAD;

    private final BossKind kind;
    private final Map<String, ModelPart> parts;

    public EventBossModel(ModelPart root, BossKind kind) {
        super(root);
        this.kind = kind;
        this.parts = switch (kind) {
            case BEEP_BOOP -> BeepBoopGeometry.index(root);
            case TOBALT -> TobaltGeometry.index(root);
            case AZHUR -> AzhurGeometry.index(root);
            case MORSULA -> MorsulaGeometry.index(root);
            case ARACHNA -> ArachnaGeometry.index(root);
        };
    }

    public static ModelLayerLocation layer(BossKind kind) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "boss_" + kind.id()), "main");
    }

    public static LayerDefinition definition(BossKind kind) {
        return switch (kind) {
            case BEEP_BOOP -> BeepBoopGeometry.createBodyLayer();
            case TOBALT -> TobaltGeometry.createBodyLayer();
            case AZHUR -> AzhurGeometry.createBodyLayer();
            case MORSULA -> MorsulaGeometry.createBodyLayer();
            case ARACHNA -> ArachnaGeometry.createBodyLayer();
        };
    }

    private ModelPart part(String name) {
        return parts.get(name);
    }

    private void spin(String name, float amount) {
        ModelPart part = parts.get(name);
        if (part != null) {
            part.yRot += amount;
        }
    }

    @Override
    public void setupAnim(EventBossRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos;
        float speed = Math.min(state.walkAnimationSpeed, 1.0f);
        float age = state.ageInTicks;
        float swing = Mth.cos(pos * 0.6662f) * speed;
        float swingBack = Mth.cos(pos * 0.6662f + Mth.PI) * speed;
        switch (kind) {
            case BEEP_BOOP -> {
                for (String drill : new String[]{"drillPart1", "drillPart3", "drillPart5"}) {
                    spin(drill, age / 2.0f);
                }
                for (String drill : new String[]{"drillPart2", "drillPart4", "drillPart6"}) {
                    spin(drill, age / 5.0f);
                }
                spin("gear1", age / 20.0f);
                spin("gear2", -age / 20.0f);
                spin("gear3", age / 20.0f);
                part("head").yRot += state.yRot * DEG;
                part("legRight").xRot += Mth.cos(pos) * speed;
                part("legLeft").xRot -= Mth.cos(pos) * speed;
                part("armLeft").xRot += swing;
                part("armRight").xRot += swingBack;
                part("forearmLeft").xRot += swingBack * 0.5f;
                part("forearmRight").xRot += swing * 0.5f;
            }
            case TOBALT -> {
                part("head").yRot += state.yRot * DEG;
                part("head").xRot += state.xRot * DEG;
                float step = state.charging ? Mth.cos(pos * 2.0f) * speed : Mth.cos(pos) * speed;
                part("legRight").xRot += step;
                part("armRight").xRot += step;
                part("legLeft").xRot -= step;
                part("armLeft").xRot -= step;
                part("stars").visible = state.stunned;
                part("stars2").visible = state.stunned;
                spin("stars", age / 4.0f);
                spin("stars2", -age / 4.0f);
            }
            case AZHUR -> {
                part("head").yRot += state.yRot * DEG;
                part("head").xRot += state.xRot * DEG;
                spin("sandTail", age / 20.0f);
                spin("sandTailPart", age / 8.0f);
                spin("sandTailPart2", -age / 10.0f);
                spin("sandTailPart3", age / 12.0f);
                spin("sandTailPart4", -age / 14.0f);
                part("armLeft").xRot += swing;
                part("armRight").xRot += swingBack;
                // Le djinn flotte : un leger roulis du corps.
                part("body").zRot += Mth.sin(age / 20.0f) * 0.04f;
            }
            case MORSULA -> {
                float breath = Mth.cos(age * 0.75f * 0.6662f / 10.0f) * 0.05f;
                for (String neck : new String[]{"neck", "neck2", "head"}) {
                    part(neck).yRot += state.yRot * DEG / 3.0f;
                    part(neck).xRot += state.xRot * DEG / 3.0f + swing / 6.0f + breath;
                }
                part("tail3").yRot += swingBack / 8.0f;
                part("tail4").yRot += swingBack / 4.0f;
                part("armRight").yRot -= swing;
                part("armLeft").yRot += swing;
            }
            case ARACHNA -> {
                float legs = Mth.cos(pos) * 0.25f * speed * 2.0f;
                for (String leg : new String[]{"RLeg1", "RLeg2", "LLeg3", "LLeg4"}) {
                    part(leg).zRot += legs;
                }
                for (String leg : new String[]{"RLeg3", "RLeg4", "LLeg", "LLeg2"}) {
                    part(leg).zRot -= legs;
                }
                part("back").zRot += Mth.cos(pos) * speed * 5.0f * DEG;
                part("back").yRot += Mth.cos(pos) * speed * 6.0f * DEG;
                part("abdomen").zRot += Mth.cos(pos) * speed * 8.0f * DEG;
                float fangs = Mth.cos(age / (state.charging ? 3.0f : 9.0f)) * 8.0f * DEG;
                part("fangLeft").zRot += fangs;
                part("fangRight").zRot -= fangs;
            }
        }
    }
}
