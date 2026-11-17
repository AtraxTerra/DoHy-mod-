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
                .texOffs(0, 0).addBox(-12F, -13F, -26F, 24F, 10F, 52F, new CubeDeformation(0F))
                .texOffs(0, 63).addBox(-18F, -30F, -8F, 36F, 17F, 34F, new CubeDeformation(0F))
                .texOffs(360, 63).addBox(-17F, -32F, -7F, 34F, 2F, 32F, new CubeDeformation(0F))
                .texOffs(315, 149).addBox(-8F, -33F, 3F, 16F, 1F, 13F, new CubeDeformation(0F))
                .texOffs(232, 180).addBox(-17F, -36F, -7F, 1F, 4F, 1F, new CubeDeformation(0F))
                .texOffs(232, 180).addBox(16F, -36F, -7F, 1F, 4F, 1F, new CubeDeformation(0F))
                .texOffs(232, 180).addBox(-17F, -36F, 24F, 1F, 4F, 1F, new CubeDeformation(0F))
                .texOffs(232, 180).addBox(16F, -36F, 24F, 1F, 4F, 1F, new CubeDeformation(0F))
                .texOffs(232, 180).addBox(-17F, -36F, 8F, 1F, 4F, 1F, new CubeDeformation(0F))
                .texOffs(232, 180).addBox(16F, -36F, 8F, 1F, 4F, 1F, new CubeDeformation(0F))
                .texOffs(0, 115).addBox(-17F, -37F, -7F, 1F, 1F, 32F, new CubeDeformation(0F))
                .texOffs(0, 115).addBox(16F, -37F, -7F, 1F, 1F, 32F, new CubeDeformation(0F))
                .texOffs(367, 180).addBox(-16F, -37F, 24F, 32F, 1F, 1F, new CubeDeformation(0F))
                .texOffs(81, 167).addBox(18F, -24F, 2F, 0.5F, 6F, 6F, new CubeDeformation(0F))
                .texOffs(81, 167).addBox(18F, -24F, 13F, 0.5F, 6F, 6F, new CubeDeformation(0F))
                .texOffs(67, 115).addBox(-21F, -23F, 2F, 3F, 9F, 18F, new CubeDeformation(0F))
                .texOffs(407, 115).addBox(12F, -20F, -26F, 6F, 7F, 12F, new CubeDeformation(0F))
                .texOffs(258, 63).addBox(-18F, -36F, -28F, 16F, 23F, 18F, new CubeDeformation(0F))
                .texOffs(141, 115).addBox(-19F, -38F, -29F, 18F, 2F, 20F, new CubeDeformation(0F))
                .texOffs(237, 180).addBox(-18.5F, -35F, -30.5F, 17F, 1F, 2.5F, new CubeDeformation(0F))
                .texOffs(322, 180).addBox(-17.5F, -39.5F, -28.5F, 2F, 1.5F, 1.5F, new CubeDeformation(0F))
                .texOffs(322, 180).addBox(-13.5F, -39.5F, -28.5F, 2F, 1.5F, 1.5F, new CubeDeformation(0F))
                .texOffs(322, 180).addBox(-7.5F, -39.5F, -28.5F, 2F, 1.5F, 1.5F, new CubeDeformation(0F))
                .texOffs(322, 180).addBox(-3.5F, -39.5F, -28.5F, 2F, 1.5F, 1.5F, new CubeDeformation(0F))
                .texOffs(331, 180).addBox(-5F, -40F, -12F, 2F, 2F, 2F, new CubeDeformation(0F))
                .texOffs(96, 167).addBox(-17F, -49F, -11.5F, 1F, 11F, 1F, new CubeDeformation(0F))
                .texOffs(445, 180).addBox(-21F, -31F, -27F, 3F, 1F, 1F, new CubeDeformation(0F))
                .texOffs(53, 180).addBox(-22.5F, -32F, -27.5F, 1.5F, 5F, 2F, new CubeDeformation(0F))
                .texOffs(330, 167).addBox(-18.5F, -26F, -16F, 0.5F, 9F, 1F, new CubeDeformation(0F))
                .texOffs(349, 180).addBox(-17F, -18F, -28.5F, 3F, 2F, 0.5F, new CubeDeformation(0F))
                .texOffs(349, 180).addBox(-7F, -18F, -28.5F, 3F, 2F, 0.5F, new CubeDeformation(0F))
                .texOffs(349, 115).addBox(-2F, -14F, -28F, 6F, 1F, 20F, new CubeDeformation(0F))
                .texOffs(327, 63).addBox(4F, -38F, -27F, 8F, 29F, 8F, new CubeDeformation(0F))
                .texOffs(218, 115).addBox(3F, -42F, -28F, 10F, 4F, 18F, new CubeDeformation(0F))
                .texOffs(0, 180).addBox(5F, -38F, -10F, 6F, 5F, 4F, new CubeDeformation(0F))
                .texOffs(275, 115).addBox(6F, -35F, -6F, 2F, 2F, 20F, new CubeDeformation(0F))
                .texOffs(278, 180).addBox(-5F, -35F, 14F, 13F, 2F, 2F, new CubeDeformation(0F))
                .texOffs(0, 167).addBox(-18F, -13F, 26F, 36F, 8F, 4F, new CubeDeformation(0F))
                .texOffs(358, 180).addBox(-17F, -11F, 30F, 3F, 2F, 0.5F, new CubeDeformation(0F))
                .texOffs(358, 180).addBox(14F, -11F, 30F, 3F, 2F, 0.5F, new CubeDeformation(0F))
                .texOffs(402, 115).addBox(-3F, -32F, 26F, 1F, 19F, 1F, new CubeDeformation(0F))
                .texOffs(402, 115).addBox(2F, -32F, 26F, 1F, 19F, 1F, new CubeDeformation(0F))
                .texOffs(434, 180).addBox(-2F, -15.75F, 26.25F, 4F, 0.75F, 0.75F, new CubeDeformation(0F))
                .texOffs(434, 180).addBox(-2F, -18.75F, 26.25F, 4F, 0.75F, 0.75F, new CubeDeformation(0F))
                .texOffs(434, 180).addBox(-2F, -21.75F, 26.25F, 4F, 0.75F, 0.75F, new CubeDeformation(0F))
                .texOffs(434, 180).addBox(-2F, -24.75F, 26.25F, 4F, 0.75F, 0.75F, new CubeDeformation(0F))
                .texOffs(434, 180).addBox(-2F, -27.75F, 26.25F, 4F, 0.75F, 0.75F, new CubeDeformation(0F))
                .texOffs(434, 180).addBox(-2F, -30.75F, 26.25F, 4F, 0.75F, 0.75F, new CubeDeformation(0F))
                .texOffs(76, 149).addBox(9F, -45F, 21F, 2F, 15F, 2F, new CubeDeformation(0F))
                .texOffs(309, 180).addBox(8.5F, -46F, 20.5F, 3F, 1F, 3F, new CubeDeformation(0F))
                .texOffs(76, 149).addBox(13F, -45F, 21F, 2F, 15F, 2F, new CubeDeformation(0F))
                .texOffs(309, 180).addBox(12.5F, -46F, 20.5F, 3F, 1F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition cyclone = root.addOrReplaceChild("cyclone", CubeListBuilder.create()
                .texOffs(444, 115).addBox(-15F, -46F, 13.925F, 10F, 14F, 4.15F, new CubeDeformation(0F))
                .texOffs(110, 115).addBox(-12.075F, -46F, 11F, 4.15F, 14F, 10F, new CubeDeformation(0F))
                .texOffs(149, 180).addBox(-14F, -47.5F, 14.34F, 8F, 1.5F, 3.32F, new CubeDeformation(0F))
                .texOffs(275, 167).addBox(-11.66F, -47.5F, 12F, 3.32F, 1.5F, 8F, new CubeDeformation(0F))
                .texOffs(223, 180).addBox(-11F, -50.5F, 15F, 2F, 3F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition cycloneBodyY = cyclone.addOrReplaceChild("cyclone_body_y", CubeListBuilder.create()
                .texOffs(444, 115).addBox(-5F, -13.95F, -2.075F, 10F, 13.9F, 4.15F, new CubeDeformation(0F))
                .texOffs(110, 115).addBox(-2.075F, -13.95F, -5F, 4.15F, 13.9F, 10F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-10F, -32F, 16F, 0F, 0.7854F, 0F));
        PartDefinition cycloneTopY = cyclone.addOrReplaceChild("cyclone_top_y", CubeListBuilder.create()
                .texOffs(149, 180).addBox(-4F, -1.45F, -1.66F, 8F, 1.4F, 3.32F, new CubeDeformation(0F))
                .texOffs(275, 167).addBox(-1.66F, -1.45F, -4F, 3.32F, 1.4F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-10F, -46F, 16F, 0F, 0.7854F, 0F));
        PartDefinition trackRight = root.addOrReplaceChild("track_right", CubeListBuilder.create()
                .texOffs(141, 63).addBox(12F, -12F, -24F, 10F, 2F, 48F, new CubeDeformation(0F))
                .texOffs(141, 63).addBox(12F, -2F, -24F, 10F, 2F, 48F, new CubeDeformation(0F))
                .texOffs(153, 0).addBox(13F, -10F, -24F, 8F, 8F, 48F, new CubeDeformation(0F))
                .texOffs(45, 149).addBox(12F, -12F, -26.49F, 10F, 12F, 4.98F, new CubeDeformation(0F))
                .texOffs(0, 149).addBox(12F, -8.49F, -30F, 10F, 4.98F, 12F, new CubeDeformation(0F))
                .texOffs(45, 149).addBox(12F, -12F, 21.51F, 10F, 12F, 4.98F, new CubeDeformation(0F))
                .texOffs(0, 149).addBox(12F, -8.49F, 18F, 10F, 4.98F, 12F, new CubeDeformation(0F))
                .texOffs(266, 0).addBox(11.5F, -13.5F, -27F, 11F, 1F, 54F, new CubeDeformation(0F))
                .texOffs(187, 180).addBox(11.5F, -12.5F, -27.5F, 11F, 3.5F, 0.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition trackEndRight0X = trackRight.addOrReplaceChild("track_end_right_0_x", CubeListBuilder.create()
                .texOffs(45, 149).addBox(0.05F, -6F, -2.49F, 9.9F, 12F, 4.98F, new CubeDeformation(0F))
                .texOffs(0, 149).addBox(0.05F, -2.49F, -6F, 9.9F, 4.98F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(12F, -6F, -24F, 0.7854F, 0F, 0F));
        PartDefinition trackEndRight1X = trackRight.addOrReplaceChild("track_end_right_1_x", CubeListBuilder.create()
                .texOffs(45, 149).addBox(0.05F, -6F, -2.49F, 9.9F, 12F, 4.98F, new CubeDeformation(0F))
                .texOffs(0, 149).addBox(0.05F, -2.49F, -6F, 9.9F, 4.98F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(12F, -6F, 24F, 0.7854F, 0F, 0F));
        PartDefinition wheelRight1 = root.addOrReplaceChild("wheel_right1", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.75F, -3.5F, -1.4525F, 1.5F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.75F, -1.4525F, -3.5F, 1.5F, 2.905F, 7F, new CubeDeformation(0F))
                .texOffs(174, 180).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(21.75F, 18.5F, -14F, 0F, 0F, 0F));
        PartDefinition wheelRight1X = wheelRight1.addOrReplaceChild("wheel_right1_x", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.65F, -3.5F, -1.4525F, 1.3F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.65F, -1.4525F, -3.5F, 1.3F, 2.905F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition wheelRight2 = root.addOrReplaceChild("wheel_right2", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.75F, -3.5F, -1.4525F, 1.5F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.75F, -1.4525F, -3.5F, 1.5F, 2.905F, 7F, new CubeDeformation(0F))
                .texOffs(174, 180).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(21.75F, 18.5F, -5F, 0F, 0F, 0F));
        PartDefinition wheelRight2X = wheelRight2.addOrReplaceChild("wheel_right2_x", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.65F, -3.5F, -1.4525F, 1.3F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.65F, -1.4525F, -3.5F, 1.3F, 2.905F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition wheelRight3 = root.addOrReplaceChild("wheel_right3", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.75F, -3.5F, -1.4525F, 1.5F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.75F, -1.4525F, -3.5F, 1.5F, 2.905F, 7F, new CubeDeformation(0F))
                .texOffs(174, 180).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(21.75F, 18.5F, 4F, 0F, 0F, 0F));
        PartDefinition wheelRight3X = wheelRight3.addOrReplaceChild("wheel_right3_x", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.65F, -3.5F, -1.4525F, 1.3F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.65F, -1.4525F, -3.5F, 1.3F, 2.905F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition wheelRight4 = root.addOrReplaceChild("wheel_right4", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.75F, -3.5F, -1.4525F, 1.5F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.75F, -1.4525F, -3.5F, 1.5F, 2.905F, 7F, new CubeDeformation(0F))
                .texOffs(174, 180).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(21.75F, 18.5F, 13F, 0F, 0F, 0F));
        PartDefinition wheelRight4X = wheelRight4.addOrReplaceChild("wheel_right4_x", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.65F, -3.5F, -1.4525F, 1.3F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.65F, -1.4525F, -3.5F, 1.3F, 2.905F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition hubFrontRight = root.addOrReplaceChild("hub_front_right", CubeListBuilder.create()
                .texOffs(395, 149).addBox(-0.4F, -4.5F, -1.8675F, 0.8F, 9F, 3.735F, new CubeDeformation(0F))
                .texOffs(374, 149).addBox(-0.4F, -1.8675F, -4.5F, 0.8F, 3.735F, 9F, new CubeDeformation(0F))
                .texOffs(40, 180).addBox(-0.9F, -2F, -2F, 1.8F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(22.4F, 18F, -24F, 0F, 0F, 0F));
        PartDefinition hubFrontRightX = hubFrontRight.addOrReplaceChild("hub_front_right_x", CubeListBuilder.create()
                .texOffs(395, 149).addBox(-0.3F, -4.5F, -1.8675F, 0.6F, 9F, 3.735F, new CubeDeformation(0F))
                .texOffs(374, 149).addBox(-0.3F, -1.8675F, -4.5F, 0.6F, 3.735F, 9F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition hubRearRight = root.addOrReplaceChild("hub_rear_right", CubeListBuilder.create()
                .texOffs(395, 149).addBox(-0.4F, -4.5F, -1.8675F, 0.8F, 9F, 3.735F, new CubeDeformation(0F))
                .texOffs(374, 149).addBox(-0.4F, -1.8675F, -4.5F, 0.8F, 3.735F, 9F, new CubeDeformation(0F))
                .texOffs(40, 180).addBox(-0.9F, -2F, -2F, 1.8F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(22.4F, 18F, 24F, 0F, 0F, 0F));
        PartDefinition hubRearRightX = hubRearRight.addOrReplaceChild("hub_rear_right_x", CubeListBuilder.create()
                .texOffs(395, 149).addBox(-0.3F, -4.5F, -1.8675F, 0.6F, 9F, 3.735F, new CubeDeformation(0F))
                .texOffs(374, 149).addBox(-0.3F, -1.8675F, -4.5F, 0.6F, 3.735F, 9F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition trackLeft = root.addOrReplaceChild("track_left", CubeListBuilder.create()
                .texOffs(141, 63).addBox(-22F, -12F, -24F, 10F, 2F, 48F, new CubeDeformation(0F))
                .texOffs(141, 63).addBox(-22F, -2F, -24F, 10F, 2F, 48F, new CubeDeformation(0F))
                .texOffs(153, 0).addBox(-21F, -10F, -24F, 8F, 8F, 48F, new CubeDeformation(0F))
                .texOffs(45, 149).addBox(-22F, -12F, -26.49F, 10F, 12F, 4.98F, new CubeDeformation(0F))
                .texOffs(0, 149).addBox(-22F, -8.49F, -30F, 10F, 4.98F, 12F, new CubeDeformation(0F))
                .texOffs(45, 149).addBox(-22F, -12F, 21.51F, 10F, 12F, 4.98F, new CubeDeformation(0F))
                .texOffs(0, 149).addBox(-22F, -8.49F, 18F, 10F, 4.98F, 12F, new CubeDeformation(0F))
                .texOffs(266, 0).addBox(-22.5F, -13.5F, -27F, 11F, 1F, 54F, new CubeDeformation(0F))
                .texOffs(187, 180).addBox(-22.5F, -12.5F, -27.5F, 11F, 3.5F, 0.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition trackEndLeft0X = trackLeft.addOrReplaceChild("track_end_left_0_x", CubeListBuilder.create()
                .texOffs(45, 149).addBox(0.05F, -6F, -2.49F, 9.9F, 12F, 4.98F, new CubeDeformation(0F))
                .texOffs(0, 149).addBox(0.05F, -2.49F, -6F, 9.9F, 4.98F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-22F, -6F, -24F, 0.7854F, 0F, 0F));
        PartDefinition trackEndLeft1X = trackLeft.addOrReplaceChild("track_end_left_1_x", CubeListBuilder.create()
                .texOffs(45, 149).addBox(0.05F, -6F, -2.49F, 9.9F, 12F, 4.98F, new CubeDeformation(0F))
                .texOffs(0, 149).addBox(0.05F, -2.49F, -6F, 9.9F, 4.98F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-22F, -6F, 24F, 0.7854F, 0F, 0F));
        PartDefinition wheelLeft1 = root.addOrReplaceChild("wheel_left1", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.75F, -3.5F, -1.4525F, 1.5F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.75F, -1.4525F, -3.5F, 1.5F, 2.905F, 7F, new CubeDeformation(0F))
                .texOffs(174, 180).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-21.75F, 18.5F, -14F, 0F, 0F, 0F));
        PartDefinition wheelLeft1X = wheelLeft1.addOrReplaceChild("wheel_left1_x", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.65F, -3.5F, -1.4525F, 1.3F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.65F, -1.4525F, -3.5F, 1.3F, 2.905F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition wheelLeft2 = root.addOrReplaceChild("wheel_left2", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.75F, -3.5F, -1.4525F, 1.5F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.75F, -1.4525F, -3.5F, 1.5F, 2.905F, 7F, new CubeDeformation(0F))
                .texOffs(174, 180).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-21.75F, 18.5F, -5F, 0F, 0F, 0F));
        PartDefinition wheelLeft2X = wheelLeft2.addOrReplaceChild("wheel_left2_x", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.65F, -3.5F, -1.4525F, 1.3F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.65F, -1.4525F, -3.5F, 1.3F, 2.905F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition wheelLeft3 = root.addOrReplaceChild("wheel_left3", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.75F, -3.5F, -1.4525F, 1.5F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.75F, -1.4525F, -3.5F, 1.5F, 2.905F, 7F, new CubeDeformation(0F))
                .texOffs(174, 180).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-21.75F, 18.5F, 4F, 0F, 0F, 0F));
        PartDefinition wheelLeft3X = wheelLeft3.addOrReplaceChild("wheel_left3_x", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.65F, -3.5F, -1.4525F, 1.3F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.65F, -1.4525F, -3.5F, 1.3F, 2.905F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition wheelLeft4 = root.addOrReplaceChild("wheel_left4", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.75F, -3.5F, -1.4525F, 1.5F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.75F, -1.4525F, -3.5F, 1.5F, 2.905F, 7F, new CubeDeformation(0F))
                .texOffs(174, 180).addBox(-1.25F, -1.5F, -1.5F, 2.5F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-21.75F, 18.5F, 13F, 0F, 0F, 0F));
        PartDefinition wheelLeft4X = wheelLeft4.addOrReplaceChild("wheel_left4_x", CubeListBuilder.create()
                .texOffs(319, 167).addBox(-0.65F, -3.5F, -1.4525F, 1.3F, 7F, 2.905F, new CubeDeformation(0F))
                .texOffs(300, 167).addBox(-0.65F, -1.4525F, -3.5F, 1.3F, 2.905F, 7F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition hubFrontLeft = root.addOrReplaceChild("hub_front_left", CubeListBuilder.create()
                .texOffs(395, 149).addBox(-0.4F, -4.5F, -1.8675F, 0.8F, 9F, 3.735F, new CubeDeformation(0F))
                .texOffs(374, 149).addBox(-0.4F, -1.8675F, -4.5F, 0.8F, 3.735F, 9F, new CubeDeformation(0F))
                .texOffs(40, 180).addBox(-0.9F, -2F, -2F, 1.8F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-22.4F, 18F, -24F, 0F, 0F, 0F));
        PartDefinition hubFrontLeftX = hubFrontLeft.addOrReplaceChild("hub_front_left_x", CubeListBuilder.create()
                .texOffs(395, 149).addBox(-0.3F, -4.5F, -1.8675F, 0.6F, 9F, 3.735F, new CubeDeformation(0F))
                .texOffs(374, 149).addBox(-0.3F, -1.8675F, -4.5F, 0.6F, 3.735F, 9F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition hubRearLeft = root.addOrReplaceChild("hub_rear_left", CubeListBuilder.create()
                .texOffs(395, 149).addBox(-0.4F, -4.5F, -1.8675F, 0.8F, 9F, 3.735F, new CubeDeformation(0F))
                .texOffs(374, 149).addBox(-0.4F, -1.8675F, -4.5F, 0.8F, 3.735F, 9F, new CubeDeformation(0F))
                .texOffs(40, 180).addBox(-0.9F, -2F, -2F, 1.8F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-22.4F, 18F, 24F, 0F, 0F, 0F));
        PartDefinition hubRearLeftX = hubRearLeft.addOrReplaceChild("hub_rear_left_x", CubeListBuilder.create()
                .texOffs(395, 149).addBox(-0.3F, -4.5F, -1.8675F, 0.6F, 9F, 3.735F, new CubeDeformation(0F))
                .texOffs(374, 149).addBox(-0.3F, -1.8675F, -4.5F, 0.6F, 3.735F, 9F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        PartDefinition intake = root.addOrReplaceChild("intake", CubeListBuilder.create()
                .texOffs(85, 149).addBox(-22F, -2F, -42F, 44F, 1.5F, 13F, new CubeDeformation(0F))
                .texOffs(340, 180).addBox(-20F, -2F, -44F, 2F, 1.5F, 2F, new CubeDeformation(0F))
                .texOffs(340, 180).addBox(-15F, -2F, -44F, 2F, 1.5F, 2F, new CubeDeformation(0F))
                .texOffs(340, 180).addBox(-10F, -2F, -44F, 2F, 1.5F, 2F, new CubeDeformation(0F))
                .texOffs(340, 180).addBox(-5F, -2F, -44F, 2F, 1.5F, 2F, new CubeDeformation(0F))
                .texOffs(340, 180).addBox(0F, -2F, -44F, 2F, 1.5F, 2F, new CubeDeformation(0F))
                .texOffs(340, 180).addBox(5F, -2F, -44F, 2F, 1.5F, 2F, new CubeDeformation(0F))
                .texOffs(340, 180).addBox(10F, -2F, -44F, 2F, 1.5F, 2F, new CubeDeformation(0F))
                .texOffs(340, 180).addBox(15F, -2F, -44F, 2F, 1.5F, 2F, new CubeDeformation(0F))
                .texOffs(340, 180).addBox(20F, -2F, -44F, 2F, 1.5F, 2F, new CubeDeformation(0F))
                .texOffs(406, 149).addBox(-22F, -12F, -30F, 44F, 10F, 2F, new CubeDeformation(0F))
                .texOffs(200, 149).addBox(-22F, -13F, -42F, 44F, 2F, 13F, new CubeDeformation(0F))
                .texOffs(320, 115).addBox(-22.5F, -11F, -42F, 1F, 9F, 13F, new CubeDeformation(0F))
                .texOffs(320, 115).addBox(21.5F, -11F, -42F, 1F, 9F, 13F, new CubeDeformation(0F))
                .texOffs(21, 180).addBox(14F, -12F, -28F, 3F, 3F, 6F, new CubeDeformation(0F))
                .texOffs(212, 180).addBox(14.75F, -11.25F, -30.5F, 1.5F, 1.5F, 3F, new CubeDeformation(0F))
                .texOffs(21, 180).addBox(-17F, -12F, -28F, 3F, 3F, 6F, new CubeDeformation(0F))
                .texOffs(212, 180).addBox(-16.25F, -11.25F, -30.5F, 1.5F, 1.5F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition drum = intake.addOrReplaceChild("drum", CubeListBuilder.create()
                .texOffs(62, 180).addBox(-20F, -1.5F, -1.5F, 40F, 3F, 3F, new CubeDeformation(0F))
                .texOffs(196, 167).addBox(-19F, -4.5F, -0.5F, 38F, 9F, 1F, new CubeDeformation(0F))
                .texOffs(101, 167).addBox(-19F, -0.5F, -4.5F, 38F, 1F, 9F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6.5F, -36F, 0F, 0F, 0F));
        PartDefinition drumX = drum.addOrReplaceChild("drum_x", CubeListBuilder.create()
                .texOffs(428, 167).addBox(-19F, -4F, -0.25F, 38F, 8F, 0.5F, new CubeDeformation(0F))
                .texOffs(335, 167).addBox(-19F, -0.25F, -4F, 38F, 0.5F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.7854F, 0F, 0F));
        return LayerDefinition.create(mesh, 512, 256);
    }
}
