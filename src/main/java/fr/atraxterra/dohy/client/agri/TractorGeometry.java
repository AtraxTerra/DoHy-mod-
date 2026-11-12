package fr.atraxterra.dohy.client.agri;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/tractor_model.py. Ne pas modifier a la main : relancer le script.
 *
 * <p>La geometrie du tracteur et de ses quatre outils, dessinee pour DoHy.
 */
@SuppressWarnings("unused")
public final class TractorGeometry {

    private TractorGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -11F, -20F, 10F, 4F, 36F, new CubeDeformation(0F))
                .texOffs(0, 41).addBox(-6F, -20F, -21F, 12F, 9F, 18F, new CubeDeformation(0F))
                .texOffs(0, 132).addBox(-5F, -19F, -22F, 10F, 7F, 1F, new CubeDeformation(0F))
                .texOffs(93, 132).addBox(-4F, -10F, -24F, 8F, 4F, 3F, new CubeDeformation(0F))
                .texOffs(58, 120).addBox(3F, -29F, -15F, 2F, 9F, 2F, new CubeDeformation(0F))
                .texOffs(147, 89).addBox(-6F, -22F, -3F, 12F, 11F, 3F, new CubeDeformation(0F))
                .texOffs(0, 69).addBox(-8F, -11F, 0F, 16F, 2F, 17F, new CubeDeformation(0F))
                .texOffs(0, 120).addBox(-4F, -14F, 6F, 8F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(67, 120).addBox(-4F, -22F, 12F, 8F, 8F, 2F, new CubeDeformation(0F))
                .texOffs(98, 41).addBox(8F, -22F, -2F, 7F, 2F, 19F, new CubeDeformation(0F))
                .texOffs(61, 41).addBox(7F, -20F, 0F, 1F, 9F, 17F, new CubeDeformation(0F))
                .texOffs(138, 89).addBox(6F, -36F, 15F, 2F, 14F, 2F, new CubeDeformation(0F))
                .texOffs(98, 41).addBox(-15F, -22F, -2F, 7F, 2F, 19F, new CubeDeformation(0F))
                .texOffs(61, 41).addBox(-8F, -20F, 0F, 1F, 9F, 17F, new CubeDeformation(0F))
                .texOffs(138, 89).addBox(-8F, -36F, 15F, 2F, 14F, 2F, new CubeDeformation(0F))
                .texOffs(183, 141).addBox(-8F, -38F, 15F, 16F, 2F, 2F, new CubeDeformation(0F))
                .texOffs(220, 141).addBox(-1F, -40F, 15F, 2F, 2F, 2F, new CubeDeformation(0F))
                .texOffs(142, 141).addBox(-9F, -7F, -15F, 18F, 2F, 2F, new CubeDeformation(0F))
                .texOffs(97, 141).addBox(-10F, -11F, 7F, 20F, 2F, 2F, new CubeDeformation(0F))
                .texOffs(230, 132).addBox(-1F, -24F, -2F, 2F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition steer = body.addOrReplaceChild("steer", CubeListBuilder.create()
                .texOffs(68, 132).addBox(-3F, -0.5F, -3F, 6F, 1F, 6F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -24F, 0F, -0.5F, 0F, 0F));
        PartDefinition wheelRearRight = root.addOrReplaceChild("wheel_rear_right", CubeListBuilder.create()
                .texOffs(211, 0).addBox(-2.5F, -10F, -4F, 5F, 20F, 8F, new CubeDeformation(0F))
                .texOffs(160, 0).addBox(-2.5F, -4F, -10F, 5F, 8F, 20F, new CubeDeformation(0F))
                .texOffs(109, 89).addBox(-3F, -4F, -4F, 6F, 8F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(11.5F, 14F, 8F, 0F, 0F, 0F));
        PartDefinition wheelRearRightCross = wheelRearRight.addOrReplaceChild("wheel_rear_right_cross", CubeListBuilder.create()
                .texOffs(211, 0).addBox(-2.25F, -10F, -4F, 4.5F, 20F, 8F, new CubeDeformation(0F))
                .texOffs(160, 0).addBox(-2.25F, -4F, -10F, 4.5F, 8F, 20F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition wheelRearLeft = root.addOrReplaceChild("wheel_rear_left", CubeListBuilder.create()
                .texOffs(211, 0).addBox(-2.5F, -10F, -4F, 5F, 20F, 8F, new CubeDeformation(0F))
                .texOffs(160, 0).addBox(-2.5F, -4F, -10F, 5F, 8F, 20F, new CubeDeformation(0F))
                .texOffs(109, 89).addBox(-3F, -4F, -4F, 6F, 8F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-11.5F, 14F, 8F, 0F, 0F, 0F));
        PartDefinition wheelRearLeftCross = wheelRearLeft.addOrReplaceChild("wheel_rear_left_cross", CubeListBuilder.create()
                .texOffs(211, 0).addBox(-2.25F, -10F, -4F, 4.5F, 20F, 8F, new CubeDeformation(0F))
                .texOffs(160, 0).addBox(-2.25F, -4F, -10F, 4.5F, 8F, 20F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition wheelFrontRight = root.addOrReplaceChild("wheel_front_right", CubeListBuilder.create()
                .texOffs(148, 69).addBox(-2F, -6F, -2.5F, 4F, 12F, 5F, new CubeDeformation(0F))
                .texOffs(115, 69).addBox(-2F, -2.5F, -6F, 4F, 5F, 12F, new CubeDeformation(0F))
                .texOffs(23, 132).addBox(-2.5F, -2F, -2F, 5F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(9F, 18F, -14F, 0F, 0F, 0F));
        PartDefinition wheelFrontRightCross = wheelFrontRight.addOrReplaceChild("wheel_front_right_cross", CubeListBuilder.create()
                .texOffs(148, 69).addBox(-1.75F, -6F, -2.5F, 3.5F, 12F, 5F, new CubeDeformation(0F))
                .texOffs(115, 69).addBox(-1.75F, -2.5F, -6F, 3.5F, 5F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition wheelFrontLeft = root.addOrReplaceChild("wheel_front_left", CubeListBuilder.create()
                .texOffs(148, 69).addBox(-2F, -6F, -2.5F, 4F, 12F, 5F, new CubeDeformation(0F))
                .texOffs(115, 69).addBox(-2F, -2.5F, -6F, 4F, 5F, 12F, new CubeDeformation(0F))
                .texOffs(23, 132).addBox(-2.5F, -2F, -2F, 5F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-9F, 18F, -14F, 0F, 0F, 0F));
        PartDefinition wheelFrontLeftCross = wheelFrontLeft.addOrReplaceChild("wheel_front_left_cross", CubeListBuilder.create()
                .texOffs(148, 69).addBox(-1.75F, -6F, -2.5F, 3.5F, 12F, 5F, new CubeDeformation(0F))
                .texOffs(115, 69).addBox(-1.75F, -2.5F, -6F, 3.5F, 5F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition hitch = root.addOrReplaceChild("hitch", CubeListBuilder.create()
                .texOffs(42, 132).addBox(-1F, -8F, 16F, 2F, 2F, 6F, new CubeDeformation(0F))
                .texOffs(213, 120).addBox(-1F, -14F, 16F, 2F, 2F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition plow = root.addOrReplaceChild("plow", CubeListBuilder.create()
                .texOffs(131, 132).addBox(-23F, -12F, 22F, 46F, 3F, 3F, new CubeDeformation(0F))
                .texOffs(59, 132).addBox(-21F, -9F, 23F, 2F, 6F, 2F, new CubeDeformation(0F))
                .texOffs(33, 120).addBox(-22F, -3F, 21F, 4F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(116, 132).addBox(-22F, -7F, 26F, 4F, 4F, 3F, new CubeDeformation(0F))
                .texOffs(59, 132).addBox(-11F, -9F, 23F, 2F, 6F, 2F, new CubeDeformation(0F))
                .texOffs(33, 120).addBox(-12F, -3F, 21F, 4F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(116, 132).addBox(-12F, -7F, 26F, 4F, 4F, 3F, new CubeDeformation(0F))
                .texOffs(59, 132).addBox(-1F, -9F, 23F, 2F, 6F, 2F, new CubeDeformation(0F))
                .texOffs(33, 120).addBox(-2F, -3F, 21F, 4F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(116, 132).addBox(-2F, -7F, 26F, 4F, 4F, 3F, new CubeDeformation(0F))
                .texOffs(59, 132).addBox(9F, -9F, 23F, 2F, 6F, 2F, new CubeDeformation(0F))
                .texOffs(33, 120).addBox(8F, -3F, 21F, 4F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(116, 132).addBox(8F, -7F, 26F, 4F, 4F, 3F, new CubeDeformation(0F))
                .texOffs(59, 132).addBox(19F, -9F, 23F, 2F, 6F, 2F, new CubeDeformation(0F))
                .texOffs(33, 120).addBox(18F, -3F, 21F, 4F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(116, 132).addBox(18F, -7F, 26F, 4F, 4F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition seeder = root.addOrReplaceChild("seeder", CubeListBuilder.create()
                .texOffs(0, 89).addBox(-23F, -17F, 21F, 46F, 8F, 8F, new CubeDeformation(0F))
                .texOffs(119, 106).addBox(-24F, -18F, 20F, 48F, 1F, 10F, new CubeDeformation(0F))
                .texOffs(232, 120).addBox(-22F, -9F, 24F, 2F, 7F, 2F, new CubeDeformation(0F))
                .texOffs(232, 120).addBox(-15F, -9F, 24F, 2F, 7F, 2F, new CubeDeformation(0F))
                .texOffs(232, 120).addBox(-8F, -9F, 24F, 2F, 7F, 2F, new CubeDeformation(0F))
                .texOffs(232, 120).addBox(-1F, -9F, 24F, 2F, 7F, 2F, new CubeDeformation(0F))
                .texOffs(232, 120).addBox(6F, -9F, 24F, 2F, 7F, 2F, new CubeDeformation(0F))
                .texOffs(232, 120).addBox(13F, -9F, 24F, 2F, 7F, 2F, new CubeDeformation(0F))
                .texOffs(232, 120).addBox(20F, -9F, 24F, 2F, 7F, 2F, new CubeDeformation(0F))
                .texOffs(178, 89).addBox(-24F, -7F, 23F, 2F, 7F, 7F, new CubeDeformation(0F))
                .texOffs(178, 89).addBox(22F, -7F, 23F, 2F, 7F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition spreader = root.addOrReplaceChild("spreader", CubeListBuilder.create()
                .texOffs(93, 0).addBox(-8F, -18F, 21F, 16F, 11F, 17F, new CubeDeformation(0F))
                .texOffs(151, 41).addBox(-9F, -19F, 20F, 18F, 1F, 19F, new CubeDeformation(0F))
                .texOffs(0, 146).addBox(-24F, -5F, 38F, 48F, 1F, 2F, new CubeDeformation(0F))
                .texOffs(239, 132).addBox(-1F, -7F, 36F, 2F, 3F, 2F, new CubeDeformation(0F))
                .texOffs(67, 69).addBox(9F, -9F, 25F, 3F, 9F, 9F, new CubeDeformation(0F))
                .texOffs(67, 69).addBox(-12F, -9F, 25F, 3F, 9F, 9F, new CubeDeformation(0F))
                .texOffs(91, 150).addBox(-9F, -5F, 29F, 18F, 1F, 1F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition harvester = root.addOrReplaceChild("harvester", CubeListBuilder.create()
                .texOffs(0, 106).addBox(-24F, -4F, 20F, 48F, 2F, 11F, new CubeDeformation(0F))
                .texOffs(114, 120).addBox(-24F, -12F, 30F, 48F, 8F, 1F, new CubeDeformation(0F))
                .texOffs(92, 69).addBox(-24F, -12F, 20F, 1F, 8F, 10F, new CubeDeformation(0F))
                .texOffs(92, 69).addBox(23F, -12F, 20F, 1F, 8F, 10F, new CubeDeformation(0F))
                .texOffs(101, 146).addBox(-24F, -2F, 19F, 48F, 1F, 1F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition reel = harvester.addOrReplaceChild("reel", CubeListBuilder.create()
                .texOffs(0, 141).addBox(-23F, -1F, -1F, 46F, 2F, 2F, new CubeDeformation(0F))
                .texOffs(0, 150).addBox(-22F, -4.5F, -0.5F, 44F, 1F, 1F, new CubeDeformation(0F))
                .texOffs(0, 150).addBox(-22F, 3.5F, -0.5F, 44F, 1F, 1F, new CubeDeformation(0F))
                .texOffs(0, 150).addBox(-22F, -0.5F, -4.5F, 44F, 1F, 1F, new CubeDeformation(0F))
                .texOffs(0, 150).addBox(-22F, -0.5F, 3.5F, 44F, 1F, 1F, new CubeDeformation(0F))
                .texOffs(109, 120).addBox(-22F, -4.5F, -0.5F, 1F, 9F, 1F, new CubeDeformation(0F))
                .texOffs(88, 120).addBox(-22F, -0.5F, -4.5F, 1F, 1F, 9F, new CubeDeformation(0F))
                .texOffs(109, 120).addBox(21F, -4.5F, -0.5F, 1F, 9F, 1F, new CubeDeformation(0F))
                .texOffs(88, 120).addBox(21F, -0.5F, -4.5F, 1F, 1F, 9F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -9F, 24F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 256, 256);
    }
}
