package fr.atraxterra.dohy.client.pet;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * La geometrie des trois familiers, propres au mod : ecrite par
 * {@code tools/pet_assets.py}. Ne pas modifier a la main : regenerer.
 */
public final class FamiliarGeometry {

    private FamiliarGeometry() {
    }

    /** Pyrolynx, texture de 32 x 32. */
    public static LayerDefinition createEmberLynxLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).mirror(false).addBox(-2.0f, 0.0f, -3.0f, 4.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 18.0f, 0.0f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 9).mirror(false).addBox(-2.0f, -3.0f, -3.0f, 4.0f, 3.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(14, 9).mirror(false).addBox(-1.0f, -1.0f, -4.0f, 2.0f, 1.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.0f, -3.0f));
        PartDefinition earLeft = head.addOrReplaceChild("ear_left", CubeListBuilder.create()
                .texOffs(20, 9).mirror(false).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 2.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-1.5f, -3.0f, -1.5f, 0.0f, 0.0f, -0.1745f));
        PartDefinition earRight = head.addOrReplaceChild("ear_right", CubeListBuilder.create()
                .texOffs(20, 9).mirror().addBox(-0.5f, -2.0f, -0.5f, 1.0f, 2.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(1.5f, -3.0f, -1.5f, 0.0f, 0.0f, 0.1745f));
        PartDefinition legFrontLeft = body.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
                .texOffs(24, 9).mirror(false).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-1.0f, 3.0f, -2.0f));
        PartDefinition legFrontRight = body.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
                .texOffs(24, 9).mirror().addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(1.0f, 3.0f, -2.0f));
        PartDefinition legBackLeft = body.addOrReplaceChild("leg_back_left", CubeListBuilder.create()
                .texOffs(24, 9).mirror(false).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-1.0f, 3.0f, 2.0f));
        PartDefinition legBackRight = body.addOrReplaceChild("leg_back_right", CubeListBuilder.create()
                .texOffs(24, 9).mirror().addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(1.0f, 3.0f, 2.0f));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(0, 15).mirror(false).addBox(-0.5f, -0.5f, 0.0f, 1.0f, 1.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 0.5f, 3.0f, -0.8727f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Mordillon, texture de 32 x 32. */
    public static LayerDefinition createPupLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).mirror(false).addBox(-2.0f, 0.0f, -2.5f, 4.0f, 4.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 17.0f, 0.0f));
        PartDefinition scarf = body.addOrReplaceChild("scarf", CubeListBuilder.create()
                .texOffs(18, 0).mirror(false).addBox(-2.0f, -0.5f, -1.0f, 4.0f, 1.0f, 3.0f, new CubeDeformation(0.3f))
                .texOffs(18, 4).mirror(false).addBox(-0.5f, 0.5f, -1.3f, 1.0f, 2.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.0f, -2.5f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 9).mirror(false).addBox(-2.0f, -4.0f, -3.0f, 4.0f, 4.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(14, 9).mirror(false).addBox(-1.0f, -2.0f, -5.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.5f, -2.5f));
        PartDefinition earLeft = head.addOrReplaceChild("ear_left", CubeListBuilder.create()
                .texOffs(22, 9).mirror(false).addBox(-1.0f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-2.0f, -3.5f, -1.5f, 0.0f, 0.0f, 0.2094f));
        PartDefinition earRight = head.addOrReplaceChild("ear_right", CubeListBuilder.create()
                .texOffs(22, 9).mirror().addBox(0.0f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(2.0f, -3.5f, -1.5f, 0.0f, 0.0f, -0.2094f));
        PartDefinition legFrontLeft = body.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
                .texOffs(26, 9).mirror(false).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-1.0f, 4.0f, -1.5f));
        PartDefinition legFrontRight = body.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
                .texOffs(26, 9).mirror().addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(1.0f, 4.0f, -1.5f));
        PartDefinition legBackLeft = body.addOrReplaceChild("leg_back_left", CubeListBuilder.create()
                .texOffs(26, 9).mirror(false).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-1.0f, 4.0f, 1.5f));
        PartDefinition legBackRight = body.addOrReplaceChild("leg_back_right", CubeListBuilder.create()
                .texOffs(26, 9).mirror().addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(1.0f, 4.0f, 1.5f));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(0, 16).mirror(false).addBox(-0.5f, -0.5f, 0.0f, 1.0f, 1.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 1.0f, 2.5f, -0.6981f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Feu-follet, texture de 32 x 32. */
    public static LayerDefinition createWispLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition flame = root.addOrReplaceChild("flame", CubeListBuilder.create()
                .texOffs(0, 0).mirror(false).addBox(-3.0f, -2.0f, -3.0f, 6.0f, 4.0f, 6.0f, new CubeDeformation(0.0f))
                .texOffs(0, 10).mirror(false).addBox(-2.0f, -5.0f, -2.0f, 4.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 19.0f, 0.0f));
        PartDefinition tip = flame.addOrReplaceChild("tip", CubeListBuilder.create()
                .texOffs(16, 10).mirror(false).addBox(-1.0f, -2.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(24, 10).mirror(false).addBox(-0.5f, -4.0f, -0.5f, 1.0f, 2.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, -5.0f, 0.0f, 0.0f, 0.7854f, 0.0f));
        PartDefinition sparks = flame.addOrReplaceChild("sparks", CubeListBuilder.create(),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition sparkA = sparks.addOrReplaceChild("spark_a", CubeListBuilder.create()
                .texOffs(28, 10).mirror(false).addBox(-0.5f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(4.5f, -3.0f, 0.0f));
        PartDefinition sparkB = sparks.addOrReplaceChild("spark_b", CubeListBuilder.create()
                .texOffs(28, 10).mirror(false).addBox(-0.5f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-4.5f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 32, 32);
    }
}
