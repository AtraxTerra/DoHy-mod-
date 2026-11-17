package fr.atraxterra.dohy.client.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;

/**
 * La moissonneuse d'epice : sa caisse, ses chenilles dont les roues tournent
 * avec le chemin parcouru, et le tambour de sa bouche, qui tourne quand elle est
 * ouverte. La geometrie est dans {@link HarvesterGeometry}, ecrite par
 * {@code tools/harvester_model.py}.
 */
public class HarvesterModel extends EntityModel<HarvesterRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "spice_harvester"), "main");

    /** Les barbotins, plus grands, font moins de tours que les galets pour le meme chemin. */
    private static final float SPROCKET_TURN = 0.55f;
    private static final float ROLLER_TURN = 0.75f;

    private final ModelPart[] sprockets;
    private final ModelPart[] rollers;
    private final ModelPart drum;

    public HarvesterModel(ModelPart root) {
        super(root);
        this.sprockets = new ModelPart[] {root.getChild("sprocket_front_right"), root.getChild("sprocket_rear_right"),
                root.getChild("sprocket_front_left"), root.getChild("sprocket_rear_left")};
        this.rollers = new ModelPart[] {root.getChild("roller_front_right"), root.getChild("roller_rear_right"),
                root.getChild("roller_front_left"), root.getChild("roller_rear_left")};
        this.drum = root.getChild("intake").getChild("drum");
    }

    @Override
    public void setupAnim(HarvesterRenderState state) {
        super.setupAnim(state);
        for (ModelPart sprocket : sprockets) {
            sprocket.xRot = state.walkAnimationPos * SPROCKET_TURN;
        }
        for (ModelPart roller : rollers) {
            roller.xRot = state.walkAnimationPos * ROLLER_TURN;
        }
        drum.xRot = state.working ? -state.ageInTicks * 0.45f : 0.0f;
    }
}
