package fr.atraxterra.dohy.client.boss;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * La geometrie de {@code ModelBeepBoop} (PalaMod 10.3.0), traduite de son bytecode
 * par {@code tools/modelbase2java.py}, sans retouche des cubes. Ne pas modifier
 * a la main : regenerer.
 */
public final class BeepBoopGeometry {

    private BeepBoopGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        PartDefinition body = bone.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-11.1667f, -27.5833f, -7.1667f, 22.0f, 15.0f, 13.0f, new CubeDeformation(1.0f)),
                PartPose.offsetAndRotation(0.1667f, -17.4167f, 0.1667f, 0.1745f, 0.0f, 0.0f));
        PartDefinition machinery = body.addOrReplaceChild("machinery", CubeListBuilder.create()
                .texOffs(0, 67).addBox(-11.0f, -28.2056f, 6.0801f, 9.0f, 16.0f, 7.0f, new CubeDeformation(0.0f))
                .texOffs(122, 111).addBox(-1.0f, -28.2056f, 9.0801f, 2.0f, 16.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(122, 111).addBox(2.0f, -28.2056f, 9.0801f, 2.0f, 16.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(122, 111).addBox(5.0f, -28.2056f, 9.0801f, 2.0f, 16.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(116, 83).addBox(8.0f, -28.2056f, 6.0801f, 2.0f, 16.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(90, 122).addBox(-2.0f, -28.3041f, 6.0975f, 10.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(90, 122).addBox(-2.0f, -14.0724f, 6.2597f, 10.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(34, 78).addBox(-9.0f, -37.4162f, 7.6733f, 2.0f, 10.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(43, 81).addBox(-6.0f, -33.4162f, 7.6733f, 3.0f, 6.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(0, 94).addBox(-3.5f, -34.4162f, 7.6733f, 1.0f, 1.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(0, 91).addBox(-6.0f, -34.3293f, 10.1657f, 3.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 91).addBox(-6.0f, -34.503f, 7.1809f, 3.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 94).addBox(-6.5f, -34.4162f, 7.6733f, 1.0f, 1.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition bobyBottom = body.addOrReplaceChild("bobyBottom", CubeListBuilder.create()
                .texOffs(68, 0).addBox(-7.7f, -3.4f, -2.2f, 15.0f, 5.0f, 5.0f, new CubeDeformation(0.0f))
                .texOffs(0, 49).addBox(-2.7f, -12.4f, -2.2f, 5.0f, 9.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-0.4667f, 0.8167f, 0.0333f));
        PartDefinition gear1 = bobyBottom.addOrReplaceChild("gear1", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.4286f, -0.1429f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.4286f, -0.1429f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-11.5714f, -0.1429f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-9.5714f, -0.1429f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-0.1286f, -12.2571f, -0.2f));
        PartDefinition part1 = gear1.addOrReplaceChild("part1", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-12.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-10.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.4286f, 0.8571f, 0.0f, 0.0f, -0.7854f, 0.0f));
        PartDefinition part2 = gear1.addOrReplaceChild("part2", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-12.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-10.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.4286f, 0.8571f, 0.0f, 0.0f, -1.5708f, 0.0f));
        PartDefinition part3 = gear1.addOrReplaceChild("part3", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-12.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-10.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.4286f, 0.8571f, 0.0f, 0.0f, -2.3562f, 0.0f));
        PartDefinition gear2 = bobyBottom.addOrReplaceChild("gear2", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.4286f, 0.1429f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.4286f, 0.1429f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-11.5714f, 0.1429f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-9.5714f, 0.1429f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-0.1286f, -9.5429f, -0.2f));
        PartDefinition part4 = gear2.addOrReplaceChild("part4", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-12.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-10.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.4286f, 1.1429f, 0.0f, 0.0f, -0.7854f, 0.0f));
        PartDefinition part5 = gear2.addOrReplaceChild("part5", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-12.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-10.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.4286f, 1.1429f, 0.0f, 0.0f, -1.5708f, 0.0f));
        PartDefinition part6 = gear2.addOrReplaceChild("part6", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-12.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-10.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.4286f, 1.1429f, 0.0f, 0.0f, -2.3562f, 0.0f));
        PartDefinition gear3 = bobyBottom.addOrReplaceChild("gear3", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.4286f, -0.5714f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.4286f, -0.5714f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-11.5714f, -0.5714f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-9.5714f, -0.5714f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-0.1286f, -5.8286f, -0.2f));
        PartDefinition part7 = gear3.addOrReplaceChild("part7", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-12.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-10.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.4286f, 0.4286f, 0.0f, 0.0f, -0.7854f, 0.0f));
        PartDefinition part8 = gear3.addOrReplaceChild("part8", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-12.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-10.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.4286f, 0.4286f, 0.0f, 0.0f, -1.5708f, 0.0f));
        PartDefinition part9 = gear3.addOrReplaceChild("part9", CubeListBuilder.create()
                .texOffs(38, 54).addBox(5.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(10.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(52, 48).addBox(-12.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(38, 54).addBox(-10.0f, -1.0f, -4.0f, 5.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.4286f, 0.4286f, 0.0f, 0.0f, -2.3562f, 0.0f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0f, -10.5f, -5.0f, 12.0f, 10.0f, 10.0f, new CubeDeformation(0.0f))
                .texOffs(52, 0).addBox(-1.5f, -4.0f, -1.5f, 3.0f, 6.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-0.1667f, -29.5833f, -0.1667f));
        PartDefinition lent = head.addOrReplaceChild("lent", CubeListBuilder.create()
                .texOffs(68, 10).addBox(5.0f, -7.0f, -6.0f, 1.0f, 4.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(75, 10).addBox(-2.0f, -7.0f, -6.0f, 1.0f, 4.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(88, 11).addBox(-2.0f, -3.0f, -6.0f, 8.0f, 1.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(88, 11).addBox(-2.0f, -8.0f, -6.0f, 8.0f, 1.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-2.0f, -0.5f, -0.5f));
        PartDefinition gear4 = head.addOrReplaceChild("gear4", CubeListBuilder.create()
                .texOffs(26, 61).addBox(-1.0f, -12.0f, 0.0f, 4.0f, 1.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(32, 59).addBox(0.0f, -12.0f, 2.0f, 2.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(32, 59).addBox(0.0f, -12.0f, -6.0f, 2.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(26, 61).addBox(-1.0f, -12.0f, -5.0f, 4.0f, 1.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-1.0f, 11.75f, 1.5f));
        PartDefinition part10 = gear4.addOrReplaceChild("part10", CubeListBuilder.create()
                .texOffs(26, 61).addBox(-2.0f, -0.5f, 1.5f, 4.0f, 1.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(32, 59).addBox(-1.0f, -0.5f, 3.5f, 2.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(32, 59).addBox(-1.0f, -0.5f, -4.5f, 2.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(26, 61).addBox(-2.0f, -0.5f, -3.5f, 4.0f, 1.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(1.0f, -11.5f, -1.5f, 0.0f, -1.0472f, 0.0f));
        PartDefinition part11 = gear4.addOrReplaceChild("part11", CubeListBuilder.create()
                .texOffs(26, 61).addBox(-2.0f, -0.5f, 1.5f, 4.0f, 1.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(32, 59).addBox(-1.0f, -0.5f, 3.5f, 2.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(32, 59).addBox(-1.0f, -0.5f, -4.5f, 2.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(26, 61).addBox(-2.0f, -0.5f, -3.5f, 4.0f, 1.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(1.0f, -11.5f, -1.5f, 0.0f, -2.0944f, 0.0f));
        PartDefinition armRight = body.addOrReplaceChild("armRight", CubeListBuilder.create()
                .texOffs(98, 43).addBox(-2.5f, -4.5f, -5.0f, 5.0f, 9.0f, 10.0f, new CubeDeformation(0.0f))
                .texOffs(118, 63).addBox(-1.5f, 4.5f, 2.0f, 3.0f, 9.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(118, 63).addBox(-1.5f, 4.5f, -3.0f, 3.0f, 9.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-14.6667f, -22.0833f, -1.1667f, -0.1745f, 0.0f, 0.0f));
        PartDefinition forearmRight = armRight.addOrReplaceChild("forearmRight", CubeListBuilder.create()
                .texOffs(91, 64).addBox(-2.5f, -4.5f, -4.0f, 5.0f, 7.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(118, 63).addBox(-1.5f, 2.5304f, 1.6527f, 3.0f, 9.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(118, 63).addBox(-1.5f, 2.5304f, -3.3473f, 3.0f, 9.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 16.0f, 0.0f, -0.1745f, 0.0f, 0.0f));
        PartDefinition drill = forearmRight.addOrReplaceChild("drill", CubeListBuilder.create()
                .texOffs(0, 116).addBox(-4.0f, -2.0f, -4.0f, 8.0f, 4.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 10.0f, 0.0f));
        PartDefinition drillPart1 = drill.addOrReplaceChild("drillPart1", CubeListBuilder.create()
                .texOffs(45, 120).addBox(-3.5f, -0.5f, -3.5f, 7.0f, 1.0f, 7.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 2.5868f, 0.0076f));
        PartDefinition drillPart2 = drill.addOrReplaceChild("drillPart2", CubeListBuilder.create()
                .texOffs(45, 120).addBox(-3.0f, -1.0f, -3.0f, 6.0f, 2.0f, 6.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 4.1736f, 0.0152f));
        PartDefinition drillPart3 = drill.addOrReplaceChild("drillPart3", CubeListBuilder.create()
                .texOffs(48, 121).addBox(-2.5f, -1.0f, -2.5f, 5.0f, 2.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 6.0295f, -0.1943f));
        PartDefinition drillPart4 = drill.addOrReplaceChild("drillPart4", CubeListBuilder.create()
                .texOffs(48, 121).addBox(-2.0f, -1.0f, -2.0f, 4.0f, 2.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 7.8701f, -0.2301f));
        PartDefinition drillPart5 = drill.addOrReplaceChild("drillPart5", CubeListBuilder.create()
                .texOffs(48, 121).addBox(-1.5f, -1.0f, -1.5f, 3.0f, 2.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 9.7107f, -0.2659f));
        PartDefinition drillPart6 = drill.addOrReplaceChild("drillPart6", CubeListBuilder.create()
                .texOffs(48, 121).addBox(-1.0f, -2.5f, -1.0f, 2.0f, 5.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 10.5589f, -0.3885f));
        PartDefinition armLeft = body.addOrReplaceChild("armLeft", CubeListBuilder.create()
                .texOffs(98, 43).addBox(1.125f, -4.875f, -5.5f, 5.0f, 9.0f, 10.0f, new CubeDeformation(0.0f))
                .texOffs(118, 63).addBox(2.125f, 4.125f, 1.5f, 3.0f, 9.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(118, 63).addBox(2.125f, 4.125f, -3.5f, 3.0f, 9.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(10.7083f, -21.7083f, -0.6667f, -0.1745f, 0.0f, 0.0f));
        PartDefinition forearmLeft = armLeft.addOrReplaceChild("forearmLeft", CubeListBuilder.create()
                .texOffs(91, 64).addBox(-2.5f, -4.5f, -4.0f, 5.0f, 7.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(118, 63).addBox(-1.5f, 2.5304f, 1.6527f, 3.0f, 9.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(118, 63).addBox(-1.5f, 2.5304f, -3.3473f, 3.0f, 9.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(3.625f, 15.625f, -0.5f, -0.1745f, 0.0f, 0.0f));
        PartDefinition legRight = bone.addOrReplaceChild("legRight", CubeListBuilder.create()
                .texOffs(104, 17).addBox(-3.5f, -2.8333f, -4.0417f, 5.0f, 7.0f, 7.0f, new CubeDeformation(0.0f))
                .texOffs(104, 31).addBox(-3.5f, 10.1667f, -0.0417f, 5.0f, 7.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(88, 31).addBox(-3.5f, 13.1667f, -3.0417f, 5.0f, 4.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(120, 31).addBox(-3.25f, 4.1667f, 0.2083f, 2.0f, 7.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(96, 19).addBox(-2.0f, 4.1667f, -2.7917f, 2.0f, 10.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(120, 31).addBox(-0.75f, 4.1667f, 0.2083f, 2.0f, 7.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-6.5f, -17.4167f, 1.0417f));
        PartDefinition legLeft = bone.addOrReplaceChild("legLeft", CubeListBuilder.create()
                .texOffs(104, 17).addBox(-1.5f, -2.8333f, -4.0417f, 5.0f, 7.0f, 7.0f, new CubeDeformation(0.0f))
                .texOffs(104, 31).addBox(-1.5f, 10.1667f, -0.0417f, 5.0f, 7.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(88, 31).addBox(-1.5f, 13.1667f, -3.0417f, 5.0f, 4.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(120, 31).addBox(-1.25f, 4.1667f, 0.2083f, 2.0f, 7.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(96, 19).addBox(0.0f, 4.1667f, -2.7917f, 2.0f, 10.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(120, 31).addBox(1.25f, 4.1667f, 0.2083f, 2.0f, 7.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offset(6.5f, -17.4167f, 1.0417f));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Chaque piece du modele, par son nom d'origine. */
    public static Map<String, ModelPart> index(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart bone = root.getChild("bone");
        parts.put("bone", bone);
        ModelPart body = bone.getChild("body");
        parts.put("body", body);
        ModelPart machinery = body.getChild("machinery");
        parts.put("machinery", machinery);
        ModelPart bobyBottom = body.getChild("bobyBottom");
        parts.put("bobyBottom", bobyBottom);
        ModelPart gear1 = bobyBottom.getChild("gear1");
        parts.put("gear1", gear1);
        ModelPart part1 = gear1.getChild("part1");
        parts.put("part1", part1);
        ModelPart part2 = gear1.getChild("part2");
        parts.put("part2", part2);
        ModelPart part3 = gear1.getChild("part3");
        parts.put("part3", part3);
        ModelPart gear2 = bobyBottom.getChild("gear2");
        parts.put("gear2", gear2);
        ModelPart part4 = gear2.getChild("part4");
        parts.put("part4", part4);
        ModelPart part5 = gear2.getChild("part5");
        parts.put("part5", part5);
        ModelPart part6 = gear2.getChild("part6");
        parts.put("part6", part6);
        ModelPart gear3 = bobyBottom.getChild("gear3");
        parts.put("gear3", gear3);
        ModelPart part7 = gear3.getChild("part7");
        parts.put("part7", part7);
        ModelPart part8 = gear3.getChild("part8");
        parts.put("part8", part8);
        ModelPart part9 = gear3.getChild("part9");
        parts.put("part9", part9);
        ModelPart head = body.getChild("head");
        parts.put("head", head);
        ModelPart lent = head.getChild("lent");
        parts.put("lent", lent);
        ModelPart gear4 = head.getChild("gear4");
        parts.put("gear4", gear4);
        ModelPart part10 = gear4.getChild("part10");
        parts.put("part10", part10);
        ModelPart part11 = gear4.getChild("part11");
        parts.put("part11", part11);
        ModelPart armRight = body.getChild("armRight");
        parts.put("armRight", armRight);
        ModelPart forearmRight = armRight.getChild("forearmRight");
        parts.put("forearmRight", forearmRight);
        ModelPart drill = forearmRight.getChild("drill");
        parts.put("drill", drill);
        ModelPart drillPart1 = drill.getChild("drillPart1");
        parts.put("drillPart1", drillPart1);
        ModelPart drillPart2 = drill.getChild("drillPart2");
        parts.put("drillPart2", drillPart2);
        ModelPart drillPart3 = drill.getChild("drillPart3");
        parts.put("drillPart3", drillPart3);
        ModelPart drillPart4 = drill.getChild("drillPart4");
        parts.put("drillPart4", drillPart4);
        ModelPart drillPart5 = drill.getChild("drillPart5");
        parts.put("drillPart5", drillPart5);
        ModelPart drillPart6 = drill.getChild("drillPart6");
        parts.put("drillPart6", drillPart6);
        ModelPart armLeft = body.getChild("armLeft");
        parts.put("armLeft", armLeft);
        ModelPart forearmLeft = armLeft.getChild("forearmLeft");
        parts.put("forearmLeft", forearmLeft);
        ModelPart legRight = bone.getChild("legRight");
        parts.put("legRight", legRight);
        ModelPart legLeft = bone.getChild("legLeft");
        parts.put("legLeft", legLeft);
        return parts;
    }
}
