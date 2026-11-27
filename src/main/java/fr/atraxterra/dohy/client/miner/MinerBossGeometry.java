package fr.atraxterra.dohy.client.miner;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE depuis le constructeur de {@code MinerBossModel} (PalaMod 10.3.0) : ses ModelRenderer,
 * points de rotation, angles et ModelBox, releves au bytecode. Ne pas modifier a la main.
 */
public final class MinerBossGeometry {

    private MinerBossGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition body = bone.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 56).addBox(-8F, -6F, -6F, 16F, 12F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -19F, 1F, -0.48F, 0F, 0F));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(49, 0).addBox(-6F, -5F, 6F, 12F, 10F, 4F, new CubeDeformation(0F)).texOffs(82, 0).addBox(-1F, -4F, 10F, 6F, 4F, 2F, new CubeDeformation(0F)).texOffs(82, 0).addBox(-3F, 0F, 10F, 6F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition back = body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(106, 21).addBox(3F, -9.3923F, -14.8301F, 8F, 2F, 3F, new CubeDeformation(0F)).texOffs(110, 26).addBox(7F, -9.3923F, -11.8301F, 3F, 2F, 6F, new CubeDeformation(0F)).texOffs(102, 0).addBox(-5F, -9.3923F, -13.8301F, 5F, 2F, 8F, new CubeDeformation(0F)).texOffs(0, 20).addBox(-13F, -7.3923F, -19.8301F, 26F, 16F, 20F, new CubeDeformation(0F)).texOffs(94, 10).addBox(-2F, -10.3923F, -11.8301F, 9F, 3F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -2.0694F, -5.2829F, 0.1745F, 0F, 0F));
        back.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-8F, -4.7972F, -7.8149F, 16F, 12F, 8F, new CubeDeformation(0F)).texOffs(0, 21).addBox(-4F, 6.2028F, -8.8149F, 4F, 7F, 2F, new CubeDeformation(0F)).texOffs(0, 30).addBox(0F, 6.2028F, -8.8149F, 2F, 4F, 4F, new CubeDeformation(0F)).texOffs(12, 23).addBox(3F, 6.2028F, -8.8149F, 2F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 3.1958F, -18.2777F, 0.3927F, 0F, 0F));
        PartDefinition armRightUtil = back.addOrReplaceChild("arm_right_util", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-12F, -5.4371F, -11.4143F, 0F, 0F, 0F));
        PartDefinition armRight = armRightUtil.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(100, 112).addBox(-4F, -3.107F, -3.5534F, 6F, 8F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-1F, 2.5441F, -0.0323F, 0.2182F, -0.1309F, 0.7418F));
        PartDefinition armRight2 = armRight.addOrReplaceChild("arm_right2", CubeListBuilder.create().texOffs(75, 112).addBox(-2.9259F, 0.8961F, -2.9714F, 6F, 10F, 6F, new CubeDeformation(0F)).texOffs(51, 119).addBox(-1.9259F, 0.8961F, -3.9714F, 2F, 4F, 1F, new CubeDeformation(0F)).texOffs(50, 125).addBox(-1.9259F, 5.8961F, -3.9714F, 2F, 2F, 1F, new CubeDeformation(0F)).texOffs(50, 125).addBox(-0.9259F, 4.8961F, -3.9714F, 2F, 2F, 1F, new CubeDeformation(0F)).texOffs(62, 122).addBox(-3.9259F, 0.8961F, -1.9714F, 1F, 4F, 2F, new CubeDeformation(0F)).texOffs(68, 120).addBox(-3.9259F, 0.8961F, 0.0286F, 1F, 6F, 2F, new CubeDeformation(0F)).texOffs(56, 124).addBox(-3.9259F, 6.8961F, -1.9714F, 1F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-2.0741F, 3.9969F, 0.418F, 0F, 0F, -0.3054F));
        PartDefinition armRight3 = armRight2.addOrReplaceChild("arm_right3", CubeListBuilder.create().texOffs(92, 79).addBox(-4.2396F, -0.4665F, -5.2365F, 8F, 20F, 10F, new CubeDeformation(0F)).texOffs(50, 99).addBox(-3.2396F, 11.5335F, 4.7635F, 4F, 8F, 2F, new CubeDeformation(0F)).texOffs(50, 90).addBox(0.7604F, 7.5335F, 3.7635F, 2F, 7F, 2F, new CubeDeformation(0F)).texOffs(62, 99).addBox(-5.2396F, 13.5335F, -6.2365F, 5F, 6F, 4F, new CubeDeformation(0F)).texOffs(80, 95).addBox(-2.2396F, 7.5335F, -7.2365F, 4F, 12F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0.3137F, 10.3626F, 0.2651F, 0F, 0F, -0.48F));
        PartDefinition armRightCrystal = armRight3.addOrReplaceChild("arm_right_crystal", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        armRightCrystal.addOrReplaceChild("arm_right_crystal_part_1", CubeListBuilder.create().texOffs(110, 72).addBox(-6.4992F, -0.9645F, -1.0593F, 7F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-4.7404F, 5.4979F, 0.8228F, 0F, 0F, 0.5236F));
        armRightCrystal.addOrReplaceChild("arm_right_crystal_part_2", CubeListBuilder.create().texOffs(116, 62).addBox(-3.4992F, -0.9645F, -1.0593F, 4F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-4.7404F, 5.4979F, 0.8228F, 0F, 0.3927F, -0.1309F));
        armRightCrystal.addOrReplaceChild("arm_right_crystal_part_3", CubeListBuilder.create().texOffs(112, 67).addBox(-5.4992F, -0.9645F, -1.0593F, 6F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-4.7404F, 5.4979F, 0.8228F, 0F, -0.4363F, -0.0436F));
        PartDefinition armRightCrystal2 = armRight3.addOrReplaceChild("arm_right_crystal2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.1187F, 10.9843F, 0.5749F, 0F, 0.8727F, 0F));
        armRightCrystal2.addOrReplaceChild("arm_right_crystal_part_4", CubeListBuilder.create().texOffs(91, 72).addBox(-6.4992F, -0.9645F, -1.0593F, 7F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-4.7404F, 5.4979F, 0.8228F, 0F, 0F, 0.5236F));
        armRightCrystal2.addOrReplaceChild("arm_right_crystal_part_5", CubeListBuilder.create().texOffs(96, 62).addBox(-3.4992F, -0.9645F, -1.0593F, 4F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-4.7404F, 5.4979F, 0.8228F, 0F, 0.3927F, -0.1309F));
        armRightCrystal2.addOrReplaceChild("arm_right_crystal_part_6", CubeListBuilder.create().texOffs(93, 67).addBox(-5.4992F, -0.9645F, -1.0593F, 6F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-4.7404F, 5.4979F, 0.8228F, 0F, -0.4363F, -0.0436F));
        PartDefinition armRightCrystal3 = armRight3.addOrReplaceChild("arm_right_crystal3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(3.1084F, 11.8953F, 1.6849F, 0F, 2.5307F, 0F));
        armRightCrystal3.addOrReplaceChild("arm_right_crystal_part_7", CubeListBuilder.create().texOffs(116, 54).addBox(-3.4992F, -0.9645F, -1.0593F, 4F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0.2247F, 0.4979F, -0.3382F, 0F, 0F, 0.5236F));
        armRightCrystal3.addOrReplaceChild("arm_right_crystal_part_8", CubeListBuilder.create().texOffs(116, 54).addBox(-3.4992F, -0.9645F, -1.0593F, 4F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0.2247F, 0.4979F, -0.3382F, 0F, 0.3927F, -0.1309F));
        armRightCrystal3.addOrReplaceChild("arm_right_crystal_part_9", CubeListBuilder.create().texOffs(114, 49).addBox(-4.4992F, -0.9645F, -1.0593F, 5F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0.2247F, 0.4979F, -0.3382F, 0F, -0.4363F, -0.0436F));
        PartDefinition armLeftUtil = back.addOrReplaceChild("arm_left_util", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-12F, -5.4371F, -11.4143F, 0F, 0F, 0F));
        PartDefinition armLeft = armLeftUtil.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(100, 112).addBox(-2F, -3.107F, -3.5534F, 6F, 8F, 8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(25F, 2.5441F, -0.0323F, 0.2182F, 0.1309F, -0.7418F));
        PartDefinition armLeft2 = armLeft.addOrReplaceChild("arm_left2", CubeListBuilder.create().texOffs(75, 112).addBox(-3.0741F, 0.8961F, -2.9714F, 6F, 10F, 6F, new CubeDeformation(0F)).texOffs(51, 119).addBox(-0.0741F, 0.8961F, -3.9714F, 2F, 4F, 1F, new CubeDeformation(0F)).texOffs(50, 125).addBox(-0.0741F, 5.8961F, -3.9714F, 2F, 2F, 1F, new CubeDeformation(0F)).texOffs(50, 125).addBox(-1.0741F, 4.8961F, -3.9714F, 2F, 2F, 1F, new CubeDeformation(0F)).texOffs(62, 122).addBox(2.9259F, 0.8961F, -1.9714F, 1F, 4F, 2F, new CubeDeformation(0F)).texOffs(68, 120).addBox(2.9259F, 0.8961F, 0.0286F, 1F, 6F, 2F, new CubeDeformation(0F)).texOffs(56, 124).addBox(2.9259F, 6.8961F, -1.9714F, 1F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(2.0741F, 3.9969F, 0.418F, 0F, 0F, 0.3054F));
        PartDefinition armLeft3 = armLeft2.addOrReplaceChild("arm_left3", CubeListBuilder.create().texOffs(92, 79).addBox(-3.7604F, -0.4665F, -5.2365F, 8F, 20F, 10F, new CubeDeformation(0F)).texOffs(50, 99).addBox(-0.7604F, 11.5335F, 4.7635F, 4F, 8F, 2F, new CubeDeformation(0F)).texOffs(50, 90).addBox(-2.7604F, 7.5335F, 3.7635F, 2F, 7F, 2F, new CubeDeformation(0F)).texOffs(62, 99).addBox(0.2396F, 13.5335F, -6.2365F, 5F, 6F, 4F, new CubeDeformation(0F)).texOffs(80, 95).addBox(-1.7604F, 7.5335F, -7.2365F, 4F, 12F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-0.3137F, 10.3626F, 0.2651F, 0F, 0F, 0.48F));
        PartDefinition armLeftCrystal = armLeft3.addOrReplaceChild("arm_left_crystal", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        armLeftCrystal.addOrReplaceChild("arm_left_crystal_part", CubeListBuilder.create().texOffs(110, 72).addBox(-0.5008F, -0.9645F, -1.0593F, 7F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(4.7404F, 5.4979F, 0.8228F, 0F, 0F, -0.5236F));
        armLeftCrystal.addOrReplaceChild("arm_left_crystal_part2", CubeListBuilder.create().texOffs(116, 62).addBox(-0.5008F, -0.9645F, -1.0593F, 4F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(4.7404F, 5.4979F, 0.8228F, 0F, -0.3927F, 0.1309F));
        armLeftCrystal.addOrReplaceChild("arm_left_crystal_part3", CubeListBuilder.create().texOffs(112, 67).addBox(-0.5008F, -0.9645F, -1.0593F, 6F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(4.7404F, 5.4979F, 0.8228F, 0F, 0.4363F, 0.0436F));
        PartDefinition armLeftCrystal2 = armLeft3.addOrReplaceChild("arm_left_crystal2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-0.1187F, 10.9843F, 0.5749F, 0F, -0.8727F, 0F));
        armLeftCrystal2.addOrReplaceChild("arm_left_crystal_part4", CubeListBuilder.create().texOffs(91, 72).addBox(-0.5008F, -0.9645F, -1.0593F, 7F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(4.7404F, 5.4979F, 0.8228F, 0F, 0F, -0.5236F));
        armLeftCrystal2.addOrReplaceChild("arm_left_crystal_part5", CubeListBuilder.create().texOffs(96, 62).addBox(-0.5008F, -0.9645F, -1.0593F, 4F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(4.7404F, 5.4979F, 0.8228F, 0F, -0.3927F, 0.1309F));
        armLeftCrystal2.addOrReplaceChild("arm_left_crystal_part6", CubeListBuilder.create().texOffs(93, 67).addBox(-0.5008F, -0.9645F, -1.0593F, 6F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(4.7404F, 5.4979F, 0.8228F, 0F, 0.4363F, 0.0436F));
        PartDefinition armLeftCrystal3 = armLeft3.addOrReplaceChild("arm_left_crystal3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-3.1084F, 11.8953F, 1.6849F, 0F, -2.5307F, 0F));
        armLeftCrystal3.addOrReplaceChild("arm_left_crystal_part7", CubeListBuilder.create().texOffs(116, 54).addBox(-0.5008F, -0.9645F, -1.0593F, 4F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-0.2247F, 0.4979F, -0.3382F, 0F, 0F, -0.5236F));
        armLeftCrystal3.addOrReplaceChild("arm_left_crystal_part8", CubeListBuilder.create().texOffs(116, 54).addBox(-0.5008F, -0.9645F, -1.0593F, 4F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-0.2247F, 0.4979F, -0.3382F, 0F, -0.3927F, 0.1309F));
        armLeftCrystal3.addOrReplaceChild("arm_left_crystal_part9", CubeListBuilder.create().texOffs(114, 49).addBox(-0.5008F, -0.9645F, -1.0593F, 5F, 2F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-0.2247F, 0.4979F, -0.3382F, 0F, 0.4363F, 0.0436F));
        PartDefinition backCrystal = back.addOrReplaceChild("back_crystal", CubeListBuilder.create(),
                PartPose.offsetAndRotation(7F, -6.9407F, -6.6457F, 0F, 0F, 0F));
        backCrystal.addOrReplaceChild("back_crystal_part", CubeListBuilder.create().texOffs(27, 83).addBox(-1F, -6.6619F, -0.9484F, 2F, 7F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, 0.1745F, 0F, 0F));
        backCrystal.addOrReplaceChild("back_crystal_part2", CubeListBuilder.create().texOffs(18, 85).addBox(-1F, -4.6619F, -0.9484F, 2F, 5F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, -0.1309F, 0F, 0.5236F));
        backCrystal.addOrReplaceChild("back_crystal_part3", CubeListBuilder.create().texOffs(36, 81).addBox(-1F, -10.6619F, 0.0516F, 2F, 9F, 2F, new CubeDeformation(0.5F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, -0.2618F, 0F, 0F));
        backCrystal.addOrReplaceChild("back_crystal_part4", CubeListBuilder.create().texOffs(27, 83).addBox(-1F, -6.6619F, -0.9484F, 2F, 7F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, -0.48F, 0F, -0.4363F));
        backCrystal.addOrReplaceChild("back_crystal_part5", CubeListBuilder.create().texOffs(36, 81).addBox(-1F, -8.6619F, -0.9484F, 2F, 9F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, 0.0436F, 0F, -0.3491F));
        backCrystal.addOrReplaceChild("back_crystal_part6", CubeListBuilder.create().texOffs(18, 85).addBox(-1F, -4.6619F, -0.9484F, 2F, 5F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, 0.5672F, 0F, 0.2182F));
        PartDefinition backCrystal2 = back.addOrReplaceChild("back_crystal_2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2F, -6.0901F, -12.6687F, 0F, -1.2654F, -0.3054F));
        backCrystal2.addOrReplaceChild("back_crystal_part7", CubeListBuilder.create().texOffs(58, 71).addBox(-1F, -6.6619F, -0.9484F, 2F, 7F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, 0.1745F, 0F, 0F));
        backCrystal2.addOrReplaceChild("back_crystal_part8", CubeListBuilder.create().texOffs(58, 63).addBox(-1F, -4.6619F, -0.9484F, 2F, 5F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, 0.5672F, 0F, 0.2182F));
        backCrystal2.addOrReplaceChild("back_crystal_part9", CubeListBuilder.create().texOffs(67, 69).addBox(-1F, -8.6619F, -0.9484F, 2F, 9F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, 0.0436F, 0F, -0.3491F));
        backCrystal2.addOrReplaceChild("back_crystal_part10", CubeListBuilder.create().texOffs(58, 71).addBox(-1F, -6.6619F, -0.9484F, 2F, 7F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, -0.48F, 0F, -0.4363F));
        backCrystal2.addOrReplaceChild("back_crystal_part11", CubeListBuilder.create().texOffs(67, 69).addBox(-1F, -10.6619F, 0.0516F, 2F, 9F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, -0.2618F, 0F, 0F));
        backCrystal2.addOrReplaceChild("back_crystal_part12", CubeListBuilder.create().texOffs(58, 63).addBox(-1F, -4.6619F, -0.9484F, 2F, 5F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -0.2F, -0.6833F, -0.1309F, 0F, 0.5236F));
        PartDefinition legLeft = bone.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(0, 111).addBox(0F, -3.25F, -2.5F, 4F, 11F, 6F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(8F, -16.75F, 0.5F, -0.3054F, 0F, 0F));
        legLeft.addOrReplaceChild("leg_left2", CubeListBuilder.create().texOffs(20, 110).addBox(-3F, -0.5F, -3.5F, 6F, 11F, 7F, new CubeDeformation(0F)).texOffs(0, 94).addBox(-4F, 6.5F, -5.5F, 4F, 4F, 4F, new CubeDeformation(0F)).texOffs(8, 107).addBox(0F, 8.5F, 3.5F, 2F, 2F, 2F, new CubeDeformation(0F)).texOffs(0, 102).addBox(-2F, 3.5F, 3.5F, 2F, 7F, 2F, new CubeDeformation(0F)).texOffs(16, 105).addBox(2F, 7.5F, -4.5F, 2F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(2F, 6.25F, 0.5F, 0.3054F, 0F, 0F));
        PartDefinition legRight = bone.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(0, 111).addBox(-4F, -3.25F, -2.5F, 4F, 11F, 6F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-8F, -16.75F, 0.5F, -0.3054F, 0F, 0F));
        legRight.addOrReplaceChild("leg_right2", CubeListBuilder.create().texOffs(20, 110).addBox(-3F, -0.5F, -3.5F, 6F, 11F, 7F, new CubeDeformation(0F)).texOffs(0, 94).addBox(0F, 6.5F, -5.5F, 4F, 4F, 4F, new CubeDeformation(0F)).texOffs(8, 107).addBox(-2F, 8.5F, 3.5F, 2F, 2F, 2F, new CubeDeformation(0F)).texOffs(0, 102).addBox(0F, 3.5F, 3.5F, 2F, 7F, 2F, new CubeDeformation(0F)).texOffs(16, 105).addBox(-4F, 7.5F, -4.5F, 2F, 3F, 3F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-2F, 6.25F, 0.5F, 0.3054F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
