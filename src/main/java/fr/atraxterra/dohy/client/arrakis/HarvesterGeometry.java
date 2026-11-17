package fr.atraxterra.dohy.client.arrakis;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/harvester_model.py. Ne pas modifier a la main : relancer le script.
 *
 * <p>La geometrie de la moissonneuse d'epice, dessinee pour Berskar.
 */
@SuppressWarnings("unused")
public final class HarvesterGeometry {

    private HarvesterGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(119, 0).addBox(-20F, -14F, -26F, 40F, 5F, 52F, new CubeDeformation(0F))
                .texOffs(0, 60).addBox(-18F, -30F, -12F, 36F, 16F, 36F, new CubeDeformation(0F))
                .texOffs(208, 60).addBox(-16F, -35F, -9F, 32F, 5F, 31F, new CubeDeformation(0F))
                .texOffs(91, 113).addBox(-10F, -36F, -2F, 20F, 1F, 16F, new CubeDeformation(0F))
                .texOffs(145, 60).addBox(-9F, -38F, -27F, 18F, 24F, 13F, new CubeDeformation(0F))
                .texOffs(164, 113).addBox(-10F, -40F, -28F, 20F, 2F, 15F, new CubeDeformation(0F))
                .texOffs(259, 145).addBox(-8F, -33F, -27.5F, 3F, 2F, 0.5F, new CubeDeformation(0F))
                .texOffs(259, 145).addBox(5F, -33F, -27.5F, 3F, 2F, 0.5F, new CubeDeformation(0F))
                .texOffs(250, 145).addBox(-1F, -42F, -22F, 2F, 2F, 2F, new CubeDeformation(0F))
                .texOffs(156, 133).addBox(6F, -50F, -17F, 1F, 10F, 1F, new CubeDeformation(0F))
                .texOffs(335, 60).addBox(11F, -36F, -27F, 6F, 27F, 6F, new CubeDeformation(0F))
                .texOffs(0, 113).addBox(10F, -39F, -27F, 8F, 3F, 16F, new CubeDeformation(0F))
                .texOffs(360, 60).addBox(18F, -21F, -6F, 3F, 7F, 18F, new CubeDeformation(0F))
                .texOffs(445, 60).addBox(-21F, -22F, 2F, 3F, 6F, 14F, new CubeDeformation(0F))
                .texOffs(82, 113).addBox(14F, -46F, 20F, 2F, 16F, 2F, new CubeDeformation(0F))
                .texOffs(226, 145).addBox(13.5F, -47F, 19.5F, 3F, 1F, 3F, new CubeDeformation(0F))
                .texOffs(82, 113).addBox(-16F, -46F, 20F, 2F, 16F, 2F, new CubeDeformation(0F))
                .texOffs(226, 145).addBox(-16.5F, -47F, 19.5F, 3F, 1F, 3F, new CubeDeformation(0F))
                .texOffs(424, 133).addBox(-18F, -13F, 24F, 36F, 4F, 4F, new CubeDeformation(0F))
                .texOffs(436, 60).addBox(12F, -33F, 26F, 3F, 20F, 1F, new CubeDeformation(0F))
                .texOffs(304, 0).addBox(10.5F, -13F, -27F, 12F, 1F, 54F, new CubeDeformation(0F))
                .texOffs(304, 0).addBox(-22.5F, -13F, -27F, 12F, 1F, 54F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition cyclone = root.addOrReplaceChild("cyclone", CubeListBuilder.create()
                .texOffs(49, 113).addBox(-5F, -6F, -3F, 10F, 12F, 6F, new CubeDeformation(0F))
                .texOffs(403, 60).addBox(-3F, -6F, -5F, 6F, 12F, 10F, new CubeDeformation(0F))
                .texOffs(94, 145).addBox(-2F, -8F, -2F, 4F, 2F, 4F, new CubeDeformation(0F))
                .texOffs(209, 145).addBox(5F, -3F, -1F, 6F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-9F, -17F, 14F, 0F, 0F, 0F));
        PartDefinition cycloneCross = cyclone.addOrReplaceChild("cyclone_cross", CubeListBuilder.create()
                .texOffs(49, 113).addBox(-5F, -5.5F, -3F, 10F, 11F, 6F, new CubeDeformation(0F))
                .texOffs(403, 60).addBox(-3F, -5.5F, -5F, 6F, 11F, 10F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition trackRight = root.addOrReplaceChild("track_right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(11F, -12F, -24F, 11F, 11F, 48F, new CubeDeformation(0F))
                .texOffs(93, 133).addBox(11F, -10F, -28F, 11F, 7F, 4F, new CubeDeformation(0F))
                .texOffs(93, 133).addBox(11F, -10F, 24F, 11F, 7F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition sprocketFrontRight = root.addOrReplaceChild("sprocket_front_right", CubeListBuilder.create()
                .texOffs(281, 113).addBox(-0.75F, -5.5F, -2F, 1.5F, 11F, 4F, new CubeDeformation(0F))
                .texOffs(254, 113).addBox(-0.75F, -2F, -5.5F, 1.5F, 4F, 11F, new CubeDeformation(0F))
                .texOffs(111, 145).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(22.75F, 17.5F, -22F, 0F, 0F, 0F));
        PartDefinition sprocketFrontRightCross = sprocketFrontRight.addOrReplaceChild("sprocket_front_right_cross", CubeListBuilder.create()
                .texOffs(281, 113).addBox(-0.5F, -5.5F, -2F, 1F, 11F, 4F, new CubeDeformation(0F))
                .texOffs(254, 113).addBox(-0.5F, -2F, -5.5F, 1F, 4F, 11F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition sprocketRearRight = root.addOrReplaceChild("sprocket_rear_right", CubeListBuilder.create()
                .texOffs(281, 113).addBox(-0.75F, -5.5F, -2F, 1.5F, 11F, 4F, new CubeDeformation(0F))
                .texOffs(254, 113).addBox(-0.75F, -2F, -5.5F, 1.5F, 4F, 11F, new CubeDeformation(0F))
                .texOffs(111, 145).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(22.75F, 17.5F, 22F, 0F, 0F, 0F));
        PartDefinition sprocketRearRightCross = sprocketRearRight.addOrReplaceChild("sprocket_rear_right_cross", CubeListBuilder.create()
                .texOffs(281, 113).addBox(-0.5F, -5.5F, -2F, 1F, 11F, 4F, new CubeDeformation(0F))
                .texOffs(254, 113).addBox(-0.5F, -2F, -5.5F, 1F, 4F, 11F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition rollerFrontRight = root.addOrReplaceChild("roller_front_right", CubeListBuilder.create()
                .texOffs(145, 133).addBox(-0.75F, -4F, -1.5F, 1.5F, 8F, 3F, new CubeDeformation(0F))
                .texOffs(124, 133).addBox(-0.75F, -1.5F, -4F, 1.5F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(239, 145).addBox(-1.25F, -1F, -1F, 2.5F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(22.75F, 19F, -8F, 0F, 0F, 0F));
        PartDefinition rollerFrontRightCross = rollerFrontRight.addOrReplaceChild("roller_front_right_cross", CubeListBuilder.create()
                .texOffs(145, 133).addBox(-0.5F, -4F, -1.5F, 1F, 8F, 3F, new CubeDeformation(0F))
                .texOffs(124, 133).addBox(-0.5F, -1.5F, -4F, 1F, 3F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition rollerRearRight = root.addOrReplaceChild("roller_rear_right", CubeListBuilder.create()
                .texOffs(145, 133).addBox(-0.75F, -4F, -1.5F, 1.5F, 8F, 3F, new CubeDeformation(0F))
                .texOffs(124, 133).addBox(-0.75F, -1.5F, -4F, 1.5F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(239, 145).addBox(-1.25F, -1F, -1F, 2.5F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(22.75F, 19F, 8F, 0F, 0F, 0F));
        PartDefinition rollerRearRightCross = rollerRearRight.addOrReplaceChild("roller_rear_right_cross", CubeListBuilder.create()
                .texOffs(145, 133).addBox(-0.5F, -4F, -1.5F, 1F, 8F, 3F, new CubeDeformation(0F))
                .texOffs(124, 133).addBox(-0.5F, -1.5F, -4F, 1F, 3F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition trackLeft = root.addOrReplaceChild("track_left", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-22F, -12F, -24F, 11F, 11F, 48F, new CubeDeformation(0F))
                .texOffs(93, 133).addBox(-22F, -10F, -28F, 11F, 7F, 4F, new CubeDeformation(0F))
                .texOffs(93, 133).addBox(-22F, -10F, 24F, 11F, 7F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition sprocketFrontLeft = root.addOrReplaceChild("sprocket_front_left", CubeListBuilder.create()
                .texOffs(281, 113).addBox(-0.75F, -5.5F, -2F, 1.5F, 11F, 4F, new CubeDeformation(0F))
                .texOffs(254, 113).addBox(-0.75F, -2F, -5.5F, 1.5F, 4F, 11F, new CubeDeformation(0F))
                .texOffs(111, 145).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-22.75F, 17.5F, -22F, 0F, 0F, 0F));
        PartDefinition sprocketFrontLeftCross = sprocketFrontLeft.addOrReplaceChild("sprocket_front_left_cross", CubeListBuilder.create()
                .texOffs(281, 113).addBox(-0.5F, -5.5F, -2F, 1F, 11F, 4F, new CubeDeformation(0F))
                .texOffs(254, 113).addBox(-0.5F, -2F, -5.5F, 1F, 4F, 11F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition sprocketRearLeft = root.addOrReplaceChild("sprocket_rear_left", CubeListBuilder.create()
                .texOffs(281, 113).addBox(-0.75F, -5.5F, -2F, 1.5F, 11F, 4F, new CubeDeformation(0F))
                .texOffs(254, 113).addBox(-0.75F, -2F, -5.5F, 1.5F, 4F, 11F, new CubeDeformation(0F))
                .texOffs(111, 145).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-22.75F, 17.5F, 22F, 0F, 0F, 0F));
        PartDefinition sprocketRearLeftCross = sprocketRearLeft.addOrReplaceChild("sprocket_rear_left_cross", CubeListBuilder.create()
                .texOffs(281, 113).addBox(-0.5F, -5.5F, -2F, 1F, 11F, 4F, new CubeDeformation(0F))
                .texOffs(254, 113).addBox(-0.5F, -2F, -5.5F, 1F, 4F, 11F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition rollerFrontLeft = root.addOrReplaceChild("roller_front_left", CubeListBuilder.create()
                .texOffs(145, 133).addBox(-0.75F, -4F, -1.5F, 1.5F, 8F, 3F, new CubeDeformation(0F))
                .texOffs(124, 133).addBox(-0.75F, -1.5F, -4F, 1.5F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(239, 145).addBox(-1.25F, -1F, -1F, 2.5F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-22.75F, 19F, -8F, 0F, 0F, 0F));
        PartDefinition rollerFrontLeftCross = rollerFrontLeft.addOrReplaceChild("roller_front_left_cross", CubeListBuilder.create()
                .texOffs(145, 133).addBox(-0.5F, -4F, -1.5F, 1F, 8F, 3F, new CubeDeformation(0F))
                .texOffs(124, 133).addBox(-0.5F, -1.5F, -4F, 1F, 3F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition rollerRearLeft = root.addOrReplaceChild("roller_rear_left", CubeListBuilder.create()
                .texOffs(145, 133).addBox(-0.75F, -4F, -1.5F, 1.5F, 8F, 3F, new CubeDeformation(0F))
                .texOffs(124, 133).addBox(-0.75F, -1.5F, -4F, 1.5F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(239, 145).addBox(-1.25F, -1F, -1F, 2.5F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-22.75F, 19F, 8F, 0F, 0F, 0F));
        PartDefinition rollerRearLeftCross = rollerRearLeft.addOrReplaceChild("roller_rear_left_cross", CubeListBuilder.create()
                .texOffs(145, 133).addBox(-0.5F, -4F, -1.5F, 1F, 8F, 3F, new CubeDeformation(0F))
                .texOffs(124, 133).addBox(-0.5F, -1.5F, -4F, 1F, 3F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition intake = root.addOrReplaceChild("intake", CubeListBuilder.create()
                .texOffs(294, 113).addBox(-22F, -2F, -37F, 44F, 1.5F, 9F, new CubeDeformation(0F))
                .texOffs(0, 133).addBox(-22F, -11F, -29F, 44F, 9F, 2F, new CubeDeformation(0F))
                .texOffs(401, 113).addBox(-22F, -12F, -37F, 44F, 2F, 9F, new CubeDeformation(0F))
                .texOffs(235, 113).addBox(-22F, -10F, -37F, 1F, 8F, 8F, new CubeDeformation(0F))
                .texOffs(235, 113).addBox(21F, -10F, -37F, 1F, 8F, 8F, new CubeDeformation(0F))
                .texOffs(79, 145).addBox(16F, -11F, -28F, 3F, 3F, 4F, new CubeDeformation(0F))
                .texOffs(79, 145).addBox(-19F, -11F, -28F, 3F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition drum = intake.addOrReplaceChild("drum", CubeListBuilder.create()
                .texOffs(124, 145).addBox(-20F, -1F, -1F, 40F, 2F, 2F, new CubeDeformation(0F))
                .texOffs(254, 133).addBox(-19F, -4F, -0.5F, 38F, 8F, 1F, new CubeDeformation(0F))
                .texOffs(161, 133).addBox(-19F, -0.5F, -4F, 38F, 1F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, -33F, 0F, 0F, 0F));
        PartDefinition drumCross = drum.addOrReplaceChild("drum_cross", CubeListBuilder.create()
                .texOffs(0, 145).addBox(-19F, -3.5F, -0.25F, 38F, 7F, 0.5F, new CubeDeformation(0F))
                .texOffs(333, 133).addBox(-19F, -0.25F, -3.5F, 38F, 0.5F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        return LayerDefinition.create(mesh, 512, 256);
    }
}
