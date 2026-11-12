package fr.atraxterra.dohy.client.agri;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.agri.TractorTool;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;

/**
 * Le tracteur : une caisse, quatre roues qui tournent avec le chemin parcouru,
 * et les quatre outils, dont un seul est montre a la fois. La geometrie est
 * dans {@link TractorGeometry}, ecrite par {@code tools/tractor_model.py}.
 */
public class TractorModel extends EntityModel<TractorRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "tractor"), "main");

    /** Les roues avant, plus petites, font plus de tours que les roues arriere pour le meme chemin. */
    private static final float REAR_TURN = 0.4f;
    private static final float FRONT_TURN = 0.667f;

    private final ModelPart[] rearWheels;
    private final ModelPart[] frontWheels;
    private final ModelPart hitch;
    private final ModelPart plow;
    private final ModelPart seeder;
    private final ModelPart spreader;
    private final ModelPart harvester;
    private final ModelPart reel;

    public TractorModel(ModelPart root) {
        super(root);
        this.rearWheels = new ModelPart[] {root.getChild("wheel_rear_right"), root.getChild("wheel_rear_left")};
        this.frontWheels = new ModelPart[] {root.getChild("wheel_front_right"), root.getChild("wheel_front_left")};
        this.hitch = root.getChild("hitch");
        this.plow = root.getChild("plow");
        this.seeder = root.getChild("seeder");
        this.spreader = root.getChild("spreader");
        this.harvester = root.getChild("harvester");
        this.reel = harvester.getChild("reel");
    }

    @Override
    public void setupAnim(TractorRenderState state) {
        super.setupAnim(state);
        for (ModelPart wheel : rearWheels) {
            wheel.xRot = state.walkAnimationPos * REAR_TURN;
        }
        for (ModelPart wheel : frontWheels) {
            wheel.xRot = state.walkAnimationPos * FRONT_TURN;
        }
        TractorTool tool = state.tool;
        hitch.visible = tool != TractorTool.NONE;
        plow.visible = tool == TractorTool.PLOW;
        seeder.visible = tool == TractorTool.SEEDER;
        spreader.visible = tool == TractorTool.SPREADER;
        harvester.visible = tool == TractorTool.HARVESTER;
        reel.xRot = state.working ? state.ageInTicks * 0.35f : 0.0f;
    }
}
