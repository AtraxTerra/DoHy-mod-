package fr.atraxterra.dohy.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Les modeles 3D de l'armure antique, tels que Palamod Recreated (SILWOX /
 * Solea Pixel) les a faits pour son « Ancient armor » : traduits de leur
 * bytecode ({@code Modelancien_helmet} et suivants) par
 * {@code tools/model2java.py}, sans retouche des cubes.
 *
 * <p>Deux changements : les pieds des bottes ({@code left_foot},
 * {@code right_foot}) prennent le nom des jambes, et chaque piece recoit les
 * parties du modele humanoide qu'elle ne couvre pas, vides — 26.2 les
 * reclame toutes, le chapeau sous la tete. Texture commune de 256 x 256 :
 * {@code textures/entity/antique_armor.png}.
 */
final class AntiqueArmorModels {

    private AntiqueArmorModels() {
    }

    static LayerDefinition helmet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p1 = mesh.getRoot();
        PartDefinition p2 = p1.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition p3 = p2.addOrReplaceChild("Head_normal", CubeListBuilder.create().texOffs(118, 144).addBox(-4.0f, -8.7111f, -4.0f, 8.0f, 8.0f, 8.0f, new CubeDeformation(0.62f)).texOffs(150, 144).addBox(-5.0f, -8.7111f, -4.0f, 10.0f, 1.0f, 9.0f, new CubeDeformation(0.2f)), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition p4 = p3.addOrReplaceChild("Head_normal_r1", CubeListBuilder.create().texOffs(238, 235).addBox(-1.0f, 1.0f, -1.5f, 1.0f, 3.0f, 3.0f, new CubeDeformation(0.305f)).texOffs(170, 242).addBox(-1.0f, -2.0f, -1.5f, 1.0f, 6.0f, 3.0f, new CubeDeformation(0.3f)), PartPose.offsetAndRotation(-4.0667f, -9.3556f, -5.3889f, 0.3321f, 0.8449f, 0.2524f));
        PartDefinition p5 = p3.addOrReplaceChild("Head_normal_r2", CubeListBuilder.create().texOffs(132, 244).addBox(-2.0f, 0.5f, -0.5f, 4.0f, 5.0f, 1.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-1.9556f, -5.2111f, -5.0333f, 0.0f, 0.3927f, 0.3927f));
        PartDefinition p6 = p3.addOrReplaceChild("Head_normal_r3", CubeListBuilder.create().texOffs(128, 218).addBox(-5.0f, -4.0f, 4.0f, 4.0f, 3.0f, 1.0f, new CubeDeformation(0.61f)), PartPose.offsetAndRotation(-0.8889f, -4.7111f, -9.7111f, 0.0f, 0.3927f, 0.0f));
        PartDefinition p7 = p3.addOrReplaceChild("Head_normal_r4", CubeListBuilder.create().texOffs(118, 218).addBox(1.0f, -4.0f, 4.0f, 4.0f, 3.0f, 1.0f, new CubeDeformation(0.61f)), PartPose.offsetAndRotation(0.8889f, -4.7111f, -9.7111f, 0.0f, -0.3927f, 0.0f));
        PartDefinition p8 = p3.addOrReplaceChild("Head_normal_r5", CubeListBuilder.create().texOffs(240, 129).addBox(-2.0f, 0.5f, -0.5f, 4.0f, 5.0f, 1.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(2.1333f, -5.2111f, -5.0333f, 0.0f, -0.3927f, -0.3927f));
        PartDefinition p9 = p3.addOrReplaceChild("Head_normal_r6", CubeListBuilder.create().texOffs(162, 242).addBox(-1.0f, -2.0f, -1.5f, 1.0f, 6.0f, 3.0f, new CubeDeformation(0.3f)).texOffs(238, 157).addBox(-1.0f, 1.0f, -1.5f, 1.0f, 3.0f, 3.0f, new CubeDeformation(0.305f)), PartPose.offsetAndRotation(4.6444f, -9.5333f, -4.6778f, 0.3321f, -0.8449f, -0.2524f));
        PartDefinition p10 = p3.addOrReplaceChild("Head_normal_r7", CubeListBuilder.create().texOffs(236, 173).addBox(-1.0f, -5.0f, -1.5f, 1.0f, 9.0f, 3.0f, new CubeDeformation(0.29f)).texOffs(178, 242).addBox(-1.0f, -2.0f, -1.5f, 1.0f, 6.0f, 3.0f, new CubeDeformation(0.301f)), PartPose.offsetAndRotation(0.4667f, -9.3556f, -6.2111f, 0.2182f, 0.0f, 0.0f));
        PartDefinition p11 = p3.addOrReplaceChild("Head_normal_r8", CubeListBuilder.create().texOffs(118, 173).addBox(-2.0f, -6.0f, -4.0f, 4.0f, 5.0f, 9.0f, new CubeDeformation(0.61f)), PartPose.offsetAndRotation(0.0f, -3.7111f, 0.0f, -0.2182f, 0.0f, 0.0f));
        PartDefinition p12 = p3.addOrReplaceChild("glow3", CubeListBuilder.create(), PartPose.offset(0.0f, -4.7111f, 0.0f));
        // Les parties du modele humanoide que cette piece ne couvre pas.
        p1.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);
        p2.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    static LayerDefinition chestplate() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p1 = mesh.getRoot();
        PartDefinition p2 = p1.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition p3 = p2.addOrReplaceChild("Body_normal", CubeListBuilder.create().texOffs(150, 154).addBox(-4.0f, -0.5333f, -2.0f, 8.0f, 12.0f, 5.0f, new CubeDeformation(0.5f)).texOffs(196, 151).addBox(-2.0f, 1.2889f, -3.1778f, 4.0f, 9.0f, 5.0f, new CubeDeformation(0.65f)).texOffs(168, 171).addBox(-3.4998f, 9.308f, -2.491f, 7.0f, 2.0f, 8.0f, new CubeDeformation(0.655f)), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition p4 = p3.addOrReplaceChild("Body_normal_r1", CubeListBuilder.create().texOffs(150, 242).addBox(-1.5f, -2.5f, -1.5f, 3.0f, 5.0f, 3.0f, new CubeDeformation(0.64f)), PartPose.offsetAndRotation(-0.0333f, -0.7539f, 5.7488f, -0.3778f, -0.7537f, 0.2648f));
        PartDefinition p5 = p3.addOrReplaceChild("Body_normal_r2", CubeListBuilder.create().texOffs(202, 230).addBox(-6.0f, 0.0f, 2.5f, 5.0f, 6.0f, 4.0f, new CubeDeformation(0.681f)), PartPose.offsetAndRotation(2.8462f, -3.5333f, 1.1f, -0.0928f, -0.3477f, 0.0317f));
        PartDefinition p6 = p3.addOrReplaceChild("Body_normal_r3", CubeListBuilder.create().texOffs(162, 223).addBox(-2.0f, -2.0f, -3.0f, 4.0f, 4.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 10.8222f, -1.6444f, 0.0f, 0.0f, 0.7854f));
        PartDefinition p7 = p3.addOrReplaceChild("Body_normal_r4", CubeListBuilder.create().texOffs(226, 202).addBox(1.0f, 0.0f, 2.5f, 5.0f, 6.0f, 4.0f, new CubeDeformation(0.68f)), PartPose.offsetAndRotation(-2.8462f, -3.5333f, 1.1f, -0.0928f, 0.3477f, -0.0317f));
        PartDefinition p8 = p3.addOrReplaceChild("Body_normal_r5", CubeListBuilder.create().texOffs(196, 181).addBox(-4.0f, -2.5f, -2.0f, 8.0f, 5.0f, 4.0f, new CubeDeformation(0.55f)), PartPose.offsetAndRotation(0.0f, 2.6111f, -2.5333f, 0.2618f, 0.0f, 0.0f));
        PartDefinition p9 = p3.addOrReplaceChild("Body_normal_r6", CubeListBuilder.create().texOffs(194, 190).addBox(-4.0f, 0.0f, -1.5f, 8.0f, 5.0f, 5.0f, new CubeDeformation(0.65f)), PartPose.offsetAndRotation(0.0f, 2.9333f, 0.9667f, -0.0873f, 0.0f, 0.0f));
        PartDefinition p10 = p3.addOrReplaceChild("Body_normal_r7", CubeListBuilder.create().texOffs(118, 131).addBox(-6.0f, 0.0f, -3.5f, 12.0f, 6.0f, 7.0f, new CubeDeformation(0.68f)), PartPose.offsetAndRotation(0.0f, -3.5333f, 1.5f, -0.0873f, 0.0f, 0.0f));
        PartDefinition p11 = p3.addOrReplaceChild("glow4", CubeListBuilder.create(), PartPose.offset(0.0f, 10.8222f, -1.6444f));
        PartDefinition p12 = p11.addOrReplaceChild("glow4_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, -2.0f, -3.0f, 4.0f, 4.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7854f));
        PartDefinition p13 = p1.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(4.0f, 2.7f, 0.0f));
        PartDefinition p14 = p13.addOrReplaceChild("LeftArm_normal", CubeListBuilder.create(), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition p15 = p14.addOrReplaceChild("leftArm5", CubeListBuilder.create(), PartPose.offset(3.6444f, 3.6111f, 0.0f));
        PartDefinition p16 = p15.addOrReplaceChild("leftArm5_r1", CubeListBuilder.create().texOffs(182, 223).mirror().addBox(1.0f, -1.5f, -3.0f, 4.0f, 4.0f, 6.0f, new CubeDeformation(0.3f)).mirror(false), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.5708f));
        PartDefinition p17 = p15.addOrReplaceChild("leftArm5_r2", CubeListBuilder.create().texOffs(134, 232).mirror().addBox(4.0f, -2.5f, -4.0f, 1.0f, 5.0f, 7.0f, new CubeDeformation(0.3f)).mirror(false), PartPose.offsetAndRotation(-0.1778f, 0.0f, 0.5333f, 0.0f, 0.0f, 1.5708f));
        PartDefinition p18 = p15.addOrReplaceChild("leftArm5_r3", CubeListBuilder.create().texOffs(140, 212).mirror().addBox(0.0f, -1.5f, -3.0f, 5.0f, 4.0f, 6.0f, new CubeDeformation(0.05f)).mirror(false).texOffs(186, 233).mirror().addBox(0.0f, -2.5f, -3.0f, 1.0f, 5.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(0.1778f, -2.0667f, 0.0f, 0.0f, 0.0f, 1.5708f));
        PartDefinition p19 = p14.addOrReplaceChild("leftArm6", CubeListBuilder.create().texOffs(236, 166).mirror().addBox(-2.4547f, -1.3398f, -3.4092f, 4.0f, 2.0f, 5.0f, new CubeDeformation(0.5f)).mirror(false), PartPose.offset(2.1658f, 4.2731f, 0.9425f));
        PartDefinition p20 = p19.addOrReplaceChild("leftArm6_r1", CubeListBuilder.create().texOffs(238, 143).mirror().addBox(-4.0f, -2.99f, -2.4867f, 2.0f, 2.0f, 5.0f, new CubeDeformation(0.01f)).mirror(false), PartPose.offsetAndRotation(7.1897f, -5.4509f, 0.1353f, -0.3927f, 0.0f, 0.1309f));
        PartDefinition p21 = p14.addOrReplaceChild("leftArm7", CubeListBuilder.create().texOffs(210, 200).mirror().addBox(-2.0f, -6.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.5f)).mirror(false), PartPose.offset(2.0667f, 3.4667f, 0.0f));
        PartDefinition p22 = p14.addOrReplaceChild("shoulder_normal2", CubeListBuilder.create(), PartPose.offset(5.7111f, -2.5333f, 0.5444f));
        PartDefinition p23 = p22.addOrReplaceChild("shoulder_normal2_r1", CubeListBuilder.create().texOffs(226, 190).mirror().addBox(-4.0f, -3.99f, -2.4867f, 1.0f, 5.0f, 7.0f, new CubeDeformation(0.011f)).mirror(false), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.3927f, 0.0f, 0.1309f));
        PartDefinition p24 = p22.addOrReplaceChild("shoulder_normal2_r2", CubeListBuilder.create().texOffs(218, 122).mirror().addBox(-4.0f, -1.0f, -2.4867f, 7.0f, 2.0f, 5.0f, new CubeDeformation(0.01f)).mirror(false), PartPose.offsetAndRotation(0.0f, 0.0332f, 1.416f, -0.3927f, 0.0f, 0.1309f));
        PartDefinition p25 = p22.addOrReplaceChild("shoulder_normal2_r3", CubeListBuilder.create().texOffs(220, 235).mirror().addBox(-4.0f, -2.99f, -2.4867f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.01f)).mirror(false), PartPose.offsetAndRotation(0.8222f, 0.3556f, 0.5333f, -0.3927f, 0.0f, 0.1309f));
        PartDefinition p26 = p22.addOrReplaceChild("shoulder_normal2_r4", CubeListBuilder.create().texOffs(168, 233).mirror().addBox(-4.0f, -3.0f, -3.2222f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(0.8222f, 0.4762f, -2.0381f, 0.3927f, 0.0f, 0.1309f));
        PartDefinition p27 = p22.addOrReplaceChild("shoulder_normal2_r5", CubeListBuilder.create().texOffs(118, 229).mirror().addBox(-4.0f, -4.0f, -4.2222f, 1.0f, 5.0f, 7.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(-0.0041f, 0.108f, -1.6337f, 0.3927f, 0.0f, 0.1309f));
        PartDefinition p28 = p22.addOrReplaceChild("shoulder_normal2_r6", CubeListBuilder.create().texOffs(118, 222).mirror().addBox(-4.0f, -1.0f, -2.6f, 7.0f, 2.0f, 5.0f, new CubeDeformation(0.3f)).mirror(false), PartPose.offsetAndRotation(0.0f, 0.0f, -2.5111f, 0.3927f, 0.0f, 0.1309f));
        PartDefinition p29 = p22.addOrReplaceChild("shoulder_normal2_r7", CubeListBuilder.create().texOffs(214, 159).mirror().addBox(-4.0f, -1.0f, -2.6f, 7.0f, 2.0f, 5.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(0.0f, 0.0f, -2.3333f, 0.3927f, 0.0f, 0.1309f));
        PartDefinition p30 = p22.addOrReplaceChild("shoulder_normal2_r8", CubeListBuilder.create().texOffs(214, 152).mirror().addBox(-4.0f, -1.0f, -2.4867f, 7.0f, 2.0f, 5.0f, new CubeDeformation(0.3f)).mirror(false), PartPose.offsetAndRotation(0.0f, 0.0332f, 1.5938f, -0.3927f, 0.0f, 0.1309f));
        PartDefinition p31 = p22.addOrReplaceChild("shoulder_normal2_r9", CubeListBuilder.create().texOffs(238, 150).mirror().addBox(-4.0f, -3.0f, -2.2222f, 2.0f, 2.0f, 5.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(3.6444f, 1.4762f, -2.0381f, 0.3927f, 0.0f, 0.1309f));
        PartDefinition p32 = p22.addOrReplaceChild("glow1", CubeListBuilder.create(), PartPose.offset(0.8222f, 0.4762f, -2.0381f));
        PartDefinition p33 = p32.addOrReplaceChild("glow1_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -3.0f, -3.2222f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.3927f, 0.0f, 0.1309f));
        PartDefinition p34 = p32.addOrReplaceChild("glow1_r2", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -2.99f, -2.4867f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.01f)), PartPose.offsetAndRotation(0.0f, -0.1206f, 2.5714f, -0.3927f, 0.0f, 0.1309f));
        PartDefinition p35 = p1.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-4.0f, 2.7f, 0.0f));
        PartDefinition p36 = p35.addOrReplaceChild("RightArm_normal", CubeListBuilder.create(), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition p37 = p36.addOrReplaceChild("rightArm5", CubeListBuilder.create(), PartPose.offset(-3.6444f, 3.6111f, 0.0f));
        PartDefinition p38 = p37.addOrReplaceChild("rightArm5_r1", CubeListBuilder.create().texOffs(182, 223).addBox(-5.0f, -1.5f, -3.0f, 4.0f, 4.0f, 6.0f, new CubeDeformation(0.3f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.5708f));
        PartDefinition p39 = p37.addOrReplaceChild("rightArm5_r2", CubeListBuilder.create().texOffs(134, 232).addBox(-5.0f, -2.5f, -4.0f, 1.0f, 5.0f, 7.0f, new CubeDeformation(0.3f)), PartPose.offsetAndRotation(0.1778f, 0.0f, 0.5333f, 0.0f, 0.0f, -1.5708f));
        PartDefinition p40 = p37.addOrReplaceChild("rightArm5_r3", CubeListBuilder.create().texOffs(140, 212).addBox(-5.0f, -1.5f, -3.0f, 5.0f, 4.0f, 6.0f, new CubeDeformation(0.05f)).texOffs(186, 233).addBox(-1.0f, -2.5f, -3.0f, 1.0f, 5.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-0.1778f, -2.0667f, 0.0f, 0.0f, 0.0f, -1.5708f));
        PartDefinition p41 = p36.addOrReplaceChild("rightArm6", CubeListBuilder.create().texOffs(236, 166).addBox(-1.5453f, -1.3398f, -3.4092f, 4.0f, 2.0f, 5.0f, new CubeDeformation(0.5f)), PartPose.offset(-2.1658f, 4.2731f, 0.9425f));
        PartDefinition p42 = p41.addOrReplaceChild("rightArm6_r1", CubeListBuilder.create().texOffs(238, 143).addBox(2.0f, -2.99f, -2.4867f, 2.0f, 2.0f, 5.0f, new CubeDeformation(0.01f)), PartPose.offsetAndRotation(-7.1897f, -5.4509f, 0.1353f, -0.3927f, 0.0f, -0.1309f));
        PartDefinition p43 = p36.addOrReplaceChild("rightArm7", CubeListBuilder.create().texOffs(210, 200).addBox(-2.0f, -6.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.5f)), PartPose.offset(-2.0667f, 3.4667f, 0.0f));
        PartDefinition p44 = p36.addOrReplaceChild("shoulder_normal3", CubeListBuilder.create(), PartPose.offset(-5.7111f, -2.5333f, 0.5444f));
        PartDefinition p45 = p44.addOrReplaceChild("shoulder_normal3_r1", CubeListBuilder.create().texOffs(226, 190).addBox(3.0f, -3.99f, -2.4867f, 1.0f, 5.0f, 7.0f, new CubeDeformation(0.011f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.3927f, 0.0f, -0.1309f));
        PartDefinition p46 = p44.addOrReplaceChild("shoulder_normal3_r2", CubeListBuilder.create().texOffs(218, 122).addBox(-3.0f, -1.0f, -2.4867f, 7.0f, 2.0f, 5.0f, new CubeDeformation(0.01f)), PartPose.offsetAndRotation(0.0f, 0.0332f, 1.416f, -0.3927f, 0.0f, -0.1309f));
        PartDefinition p47 = p44.addOrReplaceChild("shoulder_normal3_r3", CubeListBuilder.create().texOffs(220, 235).addBox(1.0f, -2.99f, -2.4867f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.01f)), PartPose.offsetAndRotation(-0.8222f, 0.3556f, 0.5333f, -0.3927f, 0.0f, -0.1309f));
        PartDefinition p48 = p44.addOrReplaceChild("shoulder_normal3_r4", CubeListBuilder.create().texOffs(168, 233).addBox(1.0f, -3.0f, -3.2222f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-0.8222f, 0.4762f, -2.0381f, 0.3927f, 0.0f, -0.1309f));
        PartDefinition p49 = p44.addOrReplaceChild("shoulder_normal3_r5", CubeListBuilder.create().texOffs(118, 229).addBox(3.0f, -4.0f, -4.2222f, 1.0f, 5.0f, 7.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0041f, 0.108f, -1.6337f, 0.3927f, 0.0f, -0.1309f));
        PartDefinition p50 = p44.addOrReplaceChild("shoulder_normal3_r6", CubeListBuilder.create().texOffs(118, 222).addBox(-3.0f, -1.0f, -2.6f, 7.0f, 2.0f, 5.0f, new CubeDeformation(0.3f)), PartPose.offsetAndRotation(0.0f, 0.0f, -2.5111f, 0.3927f, 0.0f, -0.1309f));
        PartDefinition p51 = p44.addOrReplaceChild("shoulder_normal3_r7", CubeListBuilder.create().texOffs(214, 159).addBox(-3.0f, -1.0f, -2.6f, 7.0f, 2.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.0f, -2.3333f, 0.3927f, 0.0f, -0.1309f));
        PartDefinition p52 = p44.addOrReplaceChild("shoulder_normal3_r8", CubeListBuilder.create().texOffs(214, 152).addBox(-3.0f, -1.0f, -2.4867f, 7.0f, 2.0f, 5.0f, new CubeDeformation(0.3f)), PartPose.offsetAndRotation(0.0f, 0.0332f, 1.5938f, -0.3927f, 0.0f, -0.1309f));
        PartDefinition p53 = p44.addOrReplaceChild("shoulder_normal3_r9", CubeListBuilder.create().texOffs(238, 150).addBox(2.0f, -3.0f, -2.2222f, 2.0f, 2.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-3.6444f, 1.4762f, -2.0381f, 0.3927f, 0.0f, -0.1309f));
        PartDefinition p54 = p44.addOrReplaceChild("glow2", CubeListBuilder.create(), PartPose.offset(-0.8222f, 0.4762f, -2.0381f));
        PartDefinition p55 = p54.addOrReplaceChild("glow2_r1", CubeListBuilder.create().texOffs(0, 0).addBox(1.0f, -3.0f, -3.2222f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.3927f, 0.0f, -0.1309f));
        PartDefinition p56 = p54.addOrReplaceChild("glow2_r2", CubeListBuilder.create().texOffs(0, 0).addBox(1.0f, -2.99f, -2.4867f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.01f)), PartPose.offsetAndRotation(0.0f, -0.1206f, 2.5714f, -0.3927f, 0.0f, -0.1309f));
        // Les parties du modele humanoide que cette piece ne couvre pas.
        p1.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO)
                .addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    static LayerDefinition leggings() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p1 = mesh.getRoot();
        PartDefinition p2 = p1.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.1f, 12.0f, 0.0f));
        PartDefinition p3 = p2.addOrReplaceChild("LeftLeg_normal", CubeListBuilder.create(), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition p4 = p3.addOrReplaceChild("LeftLeg_robe", CubeListBuilder.create(), PartPose.offset(2.8333f, -1.8889f, 1.6f));
        PartDefinition p5 = p4.addOrReplaceChild("LeftLeg_robe_r1", CubeListBuilder.create().texOffs(118, 160).mirror().addBox(0.5f, -4.0f, -1.0f, 4.0f, 8.0f, 5.0f, new CubeDeformation(0.65f)).mirror(false), PartPose.offsetAndRotation(-3.9336f, 6.7302f, -0.091f, 0.2618f, 0.0f, 0.0f));
        PartDefinition p6 = p4.addOrReplaceChild("LeftLeg_robe_r2", CubeListBuilder.create().texOffs(160, 122).addBox(-0.1f, -12.0f, -3.0f, 5.0f, 12.0f, 6.0f, new CubeDeformation(0.559f)), PartPose.offsetAndRotation(0.0f, 10.0f, 0.0f, 0.0f, 0.0f, -0.3927f));
        PartDefinition p7 = p4.addOrReplaceChild("LeftLeg_robe_r3", CubeListBuilder.create().texOffs(176, 154).addBox(-0.1f, -12.0f, -3.0f, 5.0f, 12.0f, 5.0f, new CubeDeformation(0.559f)).texOffs(142, 202).addBox(3.9f, -10.0f, -2.3556f, 3.0f, 2.0f, 6.0f, new CubeDeformation(0.559f)), PartPose.offsetAndRotation(-1.0f, 10.0f, 0.3556f, 0.0f, 0.0f, -0.3927f));
        PartDefinition p8 = p3.addOrReplaceChild("LeftLeg_genoux", CubeListBuilder.create().texOffs(194, 200).addBox(-2.1417f, -4.377f, 0.5944f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.551f)), PartPose.offset(0.775f, 3.4881f, -2.3944f));
        PartDefinition p9 = p8.addOrReplaceChild("LeftLeg_genoux_r1", CubeListBuilder.create().texOffs(172, 218).addBox(-1.5f, -1.5f, -0.5f, 3.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0806f, -1.5214f, -0.5722f, 0.0f, 0.0f, 0.7854f));
        PartDefinition p10 = p8.addOrReplaceChild("LeftLeg_genoux_r2", CubeListBuilder.create().texOffs(162, 218).addBox(-3.5f, -3.5f, -0.5f, 5.0f, 5.0f, 0.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0806f, 0.8341f, -0.75f, 0.0f, 0.0f, 0.7854f));
        PartDefinition p11 = p8.addOrReplaceChild("LeftLeg_genoux_r3", CubeListBuilder.create().texOffs(212, 177).addBox(-1.5f, -1.5f, -0.5f, 3.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0806f, 0.4786f, -0.5722f, 0.0f, 0.0f, 0.7854f));
        PartDefinition p12 = p1.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.1f, 12.0f, 0.0f));
        PartDefinition p13 = p12.addOrReplaceChild("LeftLeg_normal2", CubeListBuilder.create(), PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition p14 = p13.addOrReplaceChild("LeftLeg_robe2", CubeListBuilder.create(), PartPose.offset(-2.8333f, -1.8889f, 1.6f));
        PartDefinition p15 = p14.addOrReplaceChild("LeftLeg_robe2_r1", CubeListBuilder.create().texOffs(118, 160).addBox(-4.5f, -4.0f, -1.0f, 4.0f, 8.0f, 5.0f, new CubeDeformation(0.65f)), PartPose.offsetAndRotation(3.9336f, 6.7302f, -0.091f, 0.2618f, 0.0f, 0.0f));
        PartDefinition p16 = p14.addOrReplaceChild("LeftLeg_robe2_r2", CubeListBuilder.create().texOffs(160, 122).mirror().addBox(-4.9f, -12.0f, -3.0f, 5.0f, 12.0f, 6.0f, new CubeDeformation(0.559f)).mirror(false), PartPose.offsetAndRotation(0.0f, 10.0f, 0.0f, 0.0f, 0.0f, 0.3927f));
        PartDefinition p17 = p14.addOrReplaceChild("LeftLeg_robe2_r3", CubeListBuilder.create().texOffs(176, 154).mirror().addBox(-4.9f, -12.0f, -3.0f, 5.0f, 12.0f, 5.0f, new CubeDeformation(0.559f)).mirror(false).texOffs(142, 202).mirror().addBox(-6.9f, -10.0f, -3.3556f, 3.0f, 2.0f, 7.0f, new CubeDeformation(0.559f)).mirror(false), PartPose.offsetAndRotation(1.0f, 10.0f, 0.3556f, 0.0f, 0.0f, 0.3927f));
        PartDefinition p18 = p13.addOrReplaceChild("LeftLeg_genoux2", CubeListBuilder.create().texOffs(194, 200).mirror().addBox(-1.8583f, -4.377f, 0.5944f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.551f)).mirror(false), PartPose.offset(-0.775f, 3.4881f, -2.3944f));
        PartDefinition p19 = p18.addOrReplaceChild("LeftLeg_genoux2_r1", CubeListBuilder.create().texOffs(172, 218).mirror().addBox(-1.5f, -1.5f, -0.5f, 3.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(-0.0806f, -1.5214f, -0.5722f, 0.0f, 0.0f, -0.7854f));
        PartDefinition p20 = p18.addOrReplaceChild("LeftLeg_genoux2_r2", CubeListBuilder.create().texOffs(162, 218).mirror().addBox(-1.5f, -3.5f, -0.5f, 5.0f, 5.0f, 0.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(-0.0806f, 0.8341f, -0.75f, 0.0f, 0.0f, -0.7854f));
        PartDefinition p21 = p18.addOrReplaceChild("LeftLeg_genoux2_r3", CubeListBuilder.create().texOffs(212, 177).mirror().addBox(-1.5f, -1.5f, -0.5f, 3.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(-0.0806f, 0.4786f, -0.5722f, 0.0f, 0.0f, -0.7854f));
        // Les parties du modele humanoide que cette piece ne couvre pas.
        p1.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO)
                .addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    static LayerDefinition boots() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p1 = mesh.getRoot();
        PartDefinition p2 = p1.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.0f, 12.0f, 0.0f));
        PartDefinition p3 = p2.addOrReplaceChild("leftfoot_normal", CubeListBuilder.create().texOffs(146, 190).addBox(-3.1f, -6.0667f, -3.0f, 6.0f, 6.0f, 6.0f, new CubeDeformation(0.0f)).texOffs(156, 140).addBox(-3.1f, -3.0667f, -4.0f, 6.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)), PartPose.offset(0.9f, 12.5f, 0.0f));
        PartDefinition p4 = p3.addOrReplaceChild("leftfoot_normal_r1", CubeListBuilder.create().texOffs(168, 181).addBox(-3.5f, -1.0f, -3.5f, 7.0f, 2.0f, 7.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-0.1222f, -5.0667f, -1.0333f, -0.0873f, 0.0f, 0.0f));
        PartDefinition p5 = p1.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.0f, 12.0f, 0.0f));
        PartDefinition p6 = p5.addOrReplaceChild("leftfoot_normal2", CubeListBuilder.create().texOffs(146, 190).mirror().addBox(-2.9f, -6.0667f, -3.0f, 6.0f, 6.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(156, 140).mirror().addBox(-2.9f, -3.0667f, -4.0f, 6.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset(-0.9f, 12.5f, 0.0f));
        PartDefinition p7 = p6.addOrReplaceChild("leftfoot_normal2_r1", CubeListBuilder.create().texOffs(168, 181).mirror().addBox(-3.5f, -1.0f, -3.5f, 7.0f, 2.0f, 7.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation(0.1222f, -5.0667f, -1.0333f, -0.0873f, 0.0f, 0.0f));
        // Les parties du modele humanoide que cette piece ne couvre pas.
        p1.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        p1.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO)
                .addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }
}
