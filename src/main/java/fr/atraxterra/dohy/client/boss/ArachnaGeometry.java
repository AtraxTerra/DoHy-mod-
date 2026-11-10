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
 * La geometrie de {@code ModelArachna} (PalaMod 10.3.0), traduite de son bytecode
 * par {@code tools/modelbase2java.py}, sans retouche des cubes. Ne pas modifier
 * a la main : regenerer.
 */
public final class ArachnaGeometry {

    private ArachnaGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offset(-64.0f, 18.0f, 0.0f));
        PartDefinition body = bone.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(35, 0).addBox(56.0f, -28.0f, -8.0f, 16.0f, 16.0f, 16.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition back = body.addOrReplaceChild("back", CubeListBuilder.create()
                .texOffs(0, 33).addBox(-5.0f, -6.75f, 0.75f, 10.0f, 11.0f, 9.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(64.0f, -19.25f, 6.25f, 0.0873f, 0.0f, 0.0f));
        PartDefinition abdomen = back.addOrReplaceChild("abdomen", CubeListBuilder.create()
                .texOffs(39, 34).addBox(-6.0f, -6.3333f, 29.3333f, 12.0f, 12.0f, 6.0f, new CubeDeformation(0.0f))
                .texOffs(39, 34).addBox(-6.0f, -5.3333f, -0.6667f, 12.0f, 12.0f, 6.0f, new CubeDeformation(0.0f))
                .texOffs(0, 54).addBox(-8.0f, -8.3333f, 1.3333f, 16.0f, 17.0f, 28.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, -1.5038f, 7.4205f, 0.1745f, 0.0f, 0.0f));
        PartDefinition mushroom = abdomen.addOrReplaceChild("mushroom", CubeListBuilder.create()
                .texOffs(62, 75).addBox(-1.7f, -0.8f, -1.3f, 3.0f, 2.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(61, 59).addBox(-2.7f, -10.8f, -2.3f, 5.0f, 10.0f, 5.0f, new CubeDeformation(0.0f))
                .texOffs(62, 75).addBox(-1.7f, -12.8f, -1.3f, 3.0f, 2.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(76, 33).addBox(-6.361f, -16.5564f, -5.7963f, 12.0f, 4.0f, 12.0f, new CubeDeformation(0.0f))
                .texOffs(76, 50).addBox(-4.361f, -18.5564f, -3.7963f, 8.0f, 2.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-4.3f, -9.3063f, 9.6837f, -0.4363f, 0.0f, -0.3054f));
        PartDefinition mushroom2 = abdomen.addOrReplaceChild("mushroom2", CubeListBuilder.create()
                .texOffs(83, 62).addBox(-2.0f, -10.6933f, 7.3294f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(83, 62).addBox(-2.0f, -15.6933f, 7.3294f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(83, 67).addBox(-2.5f, -14.5639f, 6.8123f, 3.0f, 5.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(90, 78).addBox(-5.5f, -18.5639f, 3.8123f, 9.0f, 3.0f, 9.0f, new CubeDeformation(0.0f))
                .texOffs(97, 68).addBox(-4.5f, -19.5639f, 4.8123f, 7.0f, 1.0f, 7.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.9659f, -0.2588f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7.0f, -27.0f, -11.0f, 14.0f, 14.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(0, 18).addBox(-5.0f, -26.5f, -12.0f, 4.0f, 4.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(0, 18).addBox(1.0f, -26.5f, -12.0f, 4.0f, 4.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offset(64.0f, 0.0f, 0.0f));
        PartDefinition fangRight = head.addOrReplaceChild("fangRight", CubeListBuilder.create()
                .texOffs(15, 18).addBox(-2.5f, -0.5f, -2.5f, 5.0f, 10.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(1.5f, -19.5f, -11.5f, 0.0f, 0.0f, -0.4363f));
        PartDefinition fangRight2 = fangRight.addOrReplaceChild("fangRight2", CubeListBuilder.create()
                .texOffs(0, 25).addBox(0.5f, -0.5f, -1.5f, 2.0f, 5.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-0.2394f, 7.612f, 0.0f, 0.0f, 0.0f, 1.0036f));
        PartDefinition fangLeft = head.addOrReplaceChild("fangLeft", CubeListBuilder.create()
                .texOffs(15, 18).addBox(-2.5f, -0.5f, -2.5f, 5.0f, 10.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-1.5f, -19.5f, -11.5f, 0.0f, 0.0f, 0.4363f));
        PartDefinition fangLeft2 = fangLeft.addOrReplaceChild("fangLeft2", CubeListBuilder.create()
                .texOffs(0, 25).addBox(-2.5f, -0.5f, -1.5f, 2.0f, 5.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.2394f, 7.612f, 0.0f, 0.0f, 0.0f, -1.0036f));
        PartDefinition legsRight = body.addOrReplaceChild("legsRight", CubeListBuilder.create(),
                PartPose.offset(-16.0f, 0.0f, 0.0f));
        PartDefinition RLeg1 = legsRight.addOrReplaceChild("RLeg1", CubeListBuilder.create()
                .texOffs(88, 0).addBox(-16.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(73.0f, -14.0f, -2.0f, 0.0f, -0.3927f, 0.9163f));
        PartDefinition RLeg1part = RLeg1.addOrReplaceChild("RLeg1part", CubeListBuilder.create()
                .texOffs(88, 8).addBox(-1.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.25f)),
                PartPose.offsetAndRotation(-15.6812f, 0.6756f, 0.2821f, 0.2729f, 0.286f, 2.3521f));
        PartDefinition RLeg1part2 = RLeg1part.addOrReplaceChild("RLeg1part2", CubeListBuilder.create()
                .texOffs(0, 100).addBox(-32.0f, -2.0f, -2.0f, 32.0f, 4.0f, 4.0f, new CubeDeformation(0.5f)),
                PartPose.offsetAndRotation(15.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.9199f));
        PartDefinition RLeg1part3 = RLeg1part2.addOrReplaceChild("RLeg1part3", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-1.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-31.0f, 0.0f, 0.0f, 0.0f, 0.0f, 2.3562f));
        PartDefinition RLeg2 = legsRight.addOrReplaceChild("RLeg2", CubeListBuilder.create()
                .texOffs(88, 0).addBox(-16.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(73.0f, -14.0f, -2.0f, 0.5045f, 0.0388f, 0.8235f));
        PartDefinition RLeg2part = RLeg2.addOrReplaceChild("RLeg2part", CubeListBuilder.create()
                .texOffs(88, 8).addBox(-1.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.25f)),
                PartPose.offsetAndRotation(-15.6812f, 0.6756f, 0.2821f, 0.2729f, 0.286f, 2.3521f));
        PartDefinition RLeg2part2 = RLeg2part.addOrReplaceChild("RLeg2part2", CubeListBuilder.create()
                .texOffs(0, 100).addBox(-32.0f, -2.0f, -2.0f, 32.0f, 4.0f, 4.0f, new CubeDeformation(0.5f)),
                PartPose.offsetAndRotation(15.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.9199f));
        PartDefinition RLeg2part3 = RLeg2part2.addOrReplaceChild("RLeg2part3", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-1.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-31.0f, 0.0f, 0.0f, 0.0f, 0.0f, 2.3562f));
        PartDefinition RLeg3 = legsRight.addOrReplaceChild("RLeg3", CubeListBuilder.create()
                .texOffs(88, 0).addBox(-16.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(73.0f, -14.0f, 0.0f, 0.7f, 0.2149f, 0.8484f));
        PartDefinition RLeg3part = RLeg3.addOrReplaceChild("RLeg3part", CubeListBuilder.create()
                .texOffs(88, 8).addBox(-1.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.25f)),
                PartPose.offsetAndRotation(-15.6812f, 0.6756f, 0.2821f, 0.2729f, 0.286f, 2.3521f));
        PartDefinition RLeg3part2 = RLeg3part.addOrReplaceChild("RLeg3part2", CubeListBuilder.create()
                .texOffs(0, 100).addBox(-32.0f, -2.0f, -2.0f, 32.0f, 4.0f, 4.0f, new CubeDeformation(0.5f)),
                PartPose.offsetAndRotation(15.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.9199f));
        PartDefinition RLeg3part3 = RLeg3part2.addOrReplaceChild("RLeg3part3", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-1.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-31.0f, 0.0f, 0.0f, 0.0f, 0.0f, 2.3562f));
        PartDefinition RLeg4 = legsRight.addOrReplaceChild("RLeg4", CubeListBuilder.create()
                .texOffs(88, 0).addBox(-16.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(73.0f, -14.0f, 1.0f, 1.3813f, 0.6356f, 1.1453f));
        PartDefinition RLeg4part = RLeg4.addOrReplaceChild("RLeg4part", CubeListBuilder.create()
                .texOffs(88, 8).addBox(-1.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.25f)),
                PartPose.offsetAndRotation(-15.6812f, 0.6756f, 0.2821f, 0.2729f, 0.286f, 2.3521f));
        PartDefinition RLeg4part2 = RLeg4part.addOrReplaceChild("RLeg4part2", CubeListBuilder.create()
                .texOffs(0, 100).addBox(-32.0f, -2.0f, -2.0f, 32.0f, 4.0f, 4.0f, new CubeDeformation(0.5f)),
                PartPose.offsetAndRotation(15.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.9199f));
        PartDefinition RLeg4part3 = RLeg4part2.addOrReplaceChild("RLeg4part3", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-1.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-31.0f, 0.0f, 0.0f, 0.0f, 0.0f, 2.3562f));
        PartDefinition legsLeft = body.addOrReplaceChild("legsLeft", CubeListBuilder.create(),
                PartPose.offset(144.0f, 0.0f, 0.0f));
        PartDefinition LLeg = legsLeft.addOrReplaceChild("LLeg", CubeListBuilder.create()
                .texOffs(88, 0).addBox(0.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-73.0f, -14.0f, -2.0f, 0.0f, 0.3927f, -0.9163f));
        PartDefinition LLeg1part = LLeg.addOrReplaceChild("LLeg1part", CubeListBuilder.create()
                .texOffs(88, 8).addBox(-15.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.25f)),
                PartPose.offsetAndRotation(15.6812f, 0.6756f, 0.2821f, 0.2729f, -0.286f, -2.3521f));
        PartDefinition LLeg1part2 = LLeg1part.addOrReplaceChild("LLeg1part2", CubeListBuilder.create()
                .texOffs(0, 100).addBox(0.0f, -2.0f, -2.0f, 32.0f, 4.0f, 4.0f, new CubeDeformation(0.5f)),
                PartPose.offsetAndRotation(-15.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.9199f));
        PartDefinition LLeg1part3 = LLeg1part2.addOrReplaceChild("LLeg1part3", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-5.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(31.0f, 0.0f, 0.0f, 0.0f, 0.0f, -2.3562f));
        PartDefinition LLeg2 = legsLeft.addOrReplaceChild("LLeg2", CubeListBuilder.create()
                .texOffs(88, 0).addBox(0.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-73.0f, -14.0f, -2.0f, 0.5045f, -0.0388f, -0.8235f));
        PartDefinition LLeg2part = LLeg2.addOrReplaceChild("LLeg2part", CubeListBuilder.create()
                .texOffs(88, 8).addBox(-15.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.25f)),
                PartPose.offsetAndRotation(15.6812f, 0.6756f, 0.2821f, 0.2729f, -0.286f, -2.3521f));
        PartDefinition LLeg2part2 = LLeg2part.addOrReplaceChild("LLeg2part2", CubeListBuilder.create()
                .texOffs(0, 100).addBox(0.0f, -2.0f, -2.0f, 32.0f, 4.0f, 4.0f, new CubeDeformation(0.5f)),
                PartPose.offsetAndRotation(-15.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.9199f));
        PartDefinition LLeg2part3 = LLeg2part2.addOrReplaceChild("LLeg2part3", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-5.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(31.0f, 0.0f, 0.0f, 0.0f, 0.0f, -2.3562f));
        PartDefinition LLeg3 = legsLeft.addOrReplaceChild("LLeg3", CubeListBuilder.create()
                .texOffs(88, 0).addBox(0.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-73.0f, -14.0f, 0.0f, 0.7f, -0.2149f, -0.8484f));
        PartDefinition LLeg3part = LLeg3.addOrReplaceChild("LLeg3part", CubeListBuilder.create()
                .texOffs(88, 8).addBox(-15.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.25f)),
                PartPose.offsetAndRotation(15.6812f, 0.6756f, 0.2821f, 0.2729f, -0.286f, -2.3521f));
        PartDefinition LLeg3part2 = LLeg3part.addOrReplaceChild("LLeg3part2", CubeListBuilder.create()
                .texOffs(0, 100).addBox(0.0f, -2.0f, -2.0f, 32.0f, 4.0f, 4.0f, new CubeDeformation(0.5f)),
                PartPose.offsetAndRotation(-15.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.9199f));
        PartDefinition LLeg3part3 = LLeg3part2.addOrReplaceChild("LLeg3part3", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-5.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(31.0f, 0.0f, 0.0f, 0.0f, 0.0f, -2.3562f));
        PartDefinition LLeg4 = legsLeft.addOrReplaceChild("LLeg4", CubeListBuilder.create()
                .texOffs(88, 0).addBox(0.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-73.0f, -14.0f, 1.0f, 1.3813f, -0.6356f, -1.1453f));
        PartDefinition LLeg4part = LLeg4.addOrReplaceChild("LLeg4part", CubeListBuilder.create()
                .texOffs(88, 8).addBox(-15.0f, -2.0f, -2.0f, 16.0f, 4.0f, 4.0f, new CubeDeformation(0.25f)),
                PartPose.offsetAndRotation(15.6812f, 0.6756f, 0.2821f, 0.2729f, -0.286f, -2.3521f));
        PartDefinition LLeg4part2 = LLeg4part.addOrReplaceChild("LLeg4part2", CubeListBuilder.create()
                .texOffs(0, 100).addBox(0.0f, -2.0f, -2.0f, 32.0f, 4.0f, 4.0f, new CubeDeformation(0.5f)),
                PartPose.offsetAndRotation(-15.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.9199f));
        PartDefinition LLeg4part3 = LLeg4part2.addOrReplaceChild("LLeg4part3", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-5.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(31.0f, 0.0f, 0.0f, 0.0f, 0.0f, -2.3562f));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Chaque piece du modele, par son nom d'origine. */
    public static Map<String, ModelPart> index(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart bone = root.getChild("bone");
        parts.put("bone", bone);
        ModelPart body = bone.getChild("body");
        parts.put("body", body);
        ModelPart back = body.getChild("back");
        parts.put("back", back);
        ModelPart abdomen = back.getChild("abdomen");
        parts.put("abdomen", abdomen);
        ModelPart mushroom = abdomen.getChild("mushroom");
        parts.put("mushroom", mushroom);
        ModelPart mushroom2 = abdomen.getChild("mushroom2");
        parts.put("mushroom2", mushroom2);
        ModelPart head = body.getChild("head");
        parts.put("head", head);
        ModelPart fangRight = head.getChild("fangRight");
        parts.put("fangRight", fangRight);
        ModelPart fangRight2 = fangRight.getChild("fangRight2");
        parts.put("fangRight2", fangRight2);
        ModelPart fangLeft = head.getChild("fangLeft");
        parts.put("fangLeft", fangLeft);
        ModelPart fangLeft2 = fangLeft.getChild("fangLeft2");
        parts.put("fangLeft2", fangLeft2);
        ModelPart legsRight = body.getChild("legsRight");
        parts.put("legsRight", legsRight);
        ModelPart RLeg1 = legsRight.getChild("RLeg1");
        parts.put("RLeg1", RLeg1);
        ModelPart RLeg1part = RLeg1.getChild("RLeg1part");
        parts.put("RLeg1part", RLeg1part);
        ModelPart RLeg1part2 = RLeg1part.getChild("RLeg1part2");
        parts.put("RLeg1part2", RLeg1part2);
        ModelPart RLeg1part3 = RLeg1part2.getChild("RLeg1part3");
        parts.put("RLeg1part3", RLeg1part3);
        ModelPart RLeg2 = legsRight.getChild("RLeg2");
        parts.put("RLeg2", RLeg2);
        ModelPart RLeg2part = RLeg2.getChild("RLeg2part");
        parts.put("RLeg2part", RLeg2part);
        ModelPart RLeg2part2 = RLeg2part.getChild("RLeg2part2");
        parts.put("RLeg2part2", RLeg2part2);
        ModelPart RLeg2part3 = RLeg2part2.getChild("RLeg2part3");
        parts.put("RLeg2part3", RLeg2part3);
        ModelPart RLeg3 = legsRight.getChild("RLeg3");
        parts.put("RLeg3", RLeg3);
        ModelPart RLeg3part = RLeg3.getChild("RLeg3part");
        parts.put("RLeg3part", RLeg3part);
        ModelPart RLeg3part2 = RLeg3part.getChild("RLeg3part2");
        parts.put("RLeg3part2", RLeg3part2);
        ModelPart RLeg3part3 = RLeg3part2.getChild("RLeg3part3");
        parts.put("RLeg3part3", RLeg3part3);
        ModelPart RLeg4 = legsRight.getChild("RLeg4");
        parts.put("RLeg4", RLeg4);
        ModelPart RLeg4part = RLeg4.getChild("RLeg4part");
        parts.put("RLeg4part", RLeg4part);
        ModelPart RLeg4part2 = RLeg4part.getChild("RLeg4part2");
        parts.put("RLeg4part2", RLeg4part2);
        ModelPart RLeg4part3 = RLeg4part2.getChild("RLeg4part3");
        parts.put("RLeg4part3", RLeg4part3);
        ModelPart legsLeft = body.getChild("legsLeft");
        parts.put("legsLeft", legsLeft);
        ModelPart LLeg = legsLeft.getChild("LLeg");
        parts.put("LLeg", LLeg);
        ModelPart LLeg1part = LLeg.getChild("LLeg1part");
        parts.put("LLeg1part", LLeg1part);
        ModelPart LLeg1part2 = LLeg1part.getChild("LLeg1part2");
        parts.put("LLeg1part2", LLeg1part2);
        ModelPart LLeg1part3 = LLeg1part2.getChild("LLeg1part3");
        parts.put("LLeg1part3", LLeg1part3);
        ModelPart LLeg2 = legsLeft.getChild("LLeg2");
        parts.put("LLeg2", LLeg2);
        ModelPart LLeg2part = LLeg2.getChild("LLeg2part");
        parts.put("LLeg2part", LLeg2part);
        ModelPart LLeg2part2 = LLeg2part.getChild("LLeg2part2");
        parts.put("LLeg2part2", LLeg2part2);
        ModelPart LLeg2part3 = LLeg2part2.getChild("LLeg2part3");
        parts.put("LLeg2part3", LLeg2part3);
        ModelPart LLeg3 = legsLeft.getChild("LLeg3");
        parts.put("LLeg3", LLeg3);
        ModelPart LLeg3part = LLeg3.getChild("LLeg3part");
        parts.put("LLeg3part", LLeg3part);
        ModelPart LLeg3part2 = LLeg3part.getChild("LLeg3part2");
        parts.put("LLeg3part2", LLeg3part2);
        ModelPart LLeg3part3 = LLeg3part2.getChild("LLeg3part3");
        parts.put("LLeg3part3", LLeg3part3);
        ModelPart LLeg4 = legsLeft.getChild("LLeg4");
        parts.put("LLeg4", LLeg4);
        ModelPart LLeg4part = LLeg4.getChild("LLeg4part");
        parts.put("LLeg4part", LLeg4part);
        ModelPart LLeg4part2 = LLeg4part.getChild("LLeg4part2");
        parts.put("LLeg4part2", LLeg4part2);
        ModelPart LLeg4part3 = LLeg4part2.getChild("LLeg4part3");
        parts.put("LLeg4part3", LLeg4part3);
        return parts;
    }
}
