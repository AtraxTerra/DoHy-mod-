package fr.atraxterra.dohy.client.unlock;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.resources.Identifier;

/**
 * Le bateau en bambou a l'image : le modele de PalaJobs ({@code ModelBambooBoat}),
 * qui est celui du bateau vanilla de 1.7.10 — un fond et quatre bords, sans
 * pagaies —, et sa texture ({@code palajobs:textures/entity/bamboo_boat.png},
 * 64 x 32).
 *
 * <p>Le reste est le rendu des bateaux de 26.2 ({@link AbstractBoatRenderer}) :
 * l'orientation, le roulis quand on le frappe, les bulles. Le modele de 1.7.10
 * est, comme celui de 26.2, long selon X : il se pose tel quel.
 */
public class BambooBoatRenderer extends AbstractBoatRenderer {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "bamboo_boat"), "main");

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "textures/entity/bamboo_boat.png");

    private final EntityModel<BoatRenderState> model;

    public BambooBoatRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
        this.model = new Model(context.bakeLayer(LAYER));
    }

    @Override
    protected EntityModel<BoatRenderState> model() {
        return model;
    }

    /**
     * {@code ModelBambooBoat} : {@code byte} 24, 6, 20, 4 — la longueur, la
     * hauteur des bords, la largeur et la hauteur du pivot. Le fond (texture
     * en 0, 8) est couche d'un quart de tour sur X ; les bords (0, 0) sont
     * tournes autour de Y : 270 degres, 90, 180 et 0.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float quarter = (float) (Math.PI / 2.0d);
        root.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 8)
                .addBox(-12.0f, -8.0f, -3.0f, 24.0f, 16.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 4.0f, 0.0f, quarter, 0.0f, 0.0f));
        root.addOrReplaceChild("back", side(), PartPose.offsetAndRotation(-11.0f, 4.0f, 0.0f, 0.0f, quarter * 3.0f, 0.0f));
        root.addOrReplaceChild("front", side(), PartPose.offsetAndRotation(11.0f, 4.0f, 0.0f, 0.0f, quarter, 0.0f));
        root.addOrReplaceChild("right", side(), PartPose.offsetAndRotation(0.0f, 4.0f, -9.0f, 0.0f, quarter * 2.0f, 0.0f));
        root.addOrReplaceChild("left", side(), PartPose.offset(0.0f, 4.0f, 9.0f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static CubeListBuilder side() {
        return CubeListBuilder.create().texOffs(0, 0).addBox(-10.0f, -7.0f, -1.0f, 20.0f, 6.0f, 2.0f);
    }

    /** Le modele n'a rien d'anime : pas de pagaies sur un bateau de 1.7.10. */
    private static final class Model extends EntityModel<BoatRenderState> {
        private Model(ModelPart root) {
            super(root);
        }
    }
}
