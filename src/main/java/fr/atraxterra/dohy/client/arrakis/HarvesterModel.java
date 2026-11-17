package fr.atraxterra.dohy.client.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;

/**
 * La moissonneuse d'epice : sa caisse, ses chenilles dont les galets et les
 * barbotins tournent avec le chemin parcouru, et le tambour de sa bouche, qui
 * tourne quand elle est ouverte. La geometrie est dans {@link HarvesterGeometry},
 * ecrite par {@code tools/harvester_model.py}.
 */
public class HarvesterModel extends EntityModel<HarvesterRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "spice_harvester"), "main");

    /** Les barbotins, plus grands, font moins de tours que les galets pour le meme chemin. */
    private static final float HUB_TURN = 0.45f;
    private static final float WHEEL_TURN = 0.6f;

    private final ModelPart[] hubs;
    private final ModelPart[] wheels;
    private final ModelPart drum;

    public HarvesterModel(ModelPart root) {
        super(root);
        this.hubs = new ModelPart[] {root.getChild("hub_front_right"), root.getChild("hub_rear_right"),
                root.getChild("hub_front_left"), root.getChild("hub_rear_left")};
        this.wheels = new ModelPart[8];
        for (int i = 0; i < 4; i++) {
            wheels[i] = root.getChild("wheel_right" + (i + 1));
            wheels[i + 4] = root.getChild("wheel_left" + (i + 1));
        }
        this.drum = root.getChild("intake").getChild("drum");
    }

    @Override
    public void setupAnim(HarvesterRenderState state) {
        super.setupAnim(state);
        for (ModelPart hub : hubs) {
            hub.xRot = state.walkAnimationPos * HUB_TURN;
        }
        for (ModelPart wheel : wheels) {
            wheel.xRot = state.walkAnimationPos * WHEEL_TURN;
        }
        drum.xRot = state.working ? -state.ageInTicks * 0.45f : 0.0f;
    }
}
