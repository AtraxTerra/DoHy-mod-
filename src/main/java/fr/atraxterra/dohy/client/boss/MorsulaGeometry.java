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
 * La geometrie de {@code ModelMorsula} (PalaMod 10.3.0), traduite de son bytecode
 * par {@code tools/modelbase2java.py}, sans retouche des cubes. Ne pas modifier
 * a la main : regenerer.
 */
public final class MorsulaGeometry {

    private MorsulaGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offset(0.0f, -8.5f, 5.0f));
        PartDefinition body = bone.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10.5f, -31.0f, -12.0f, 22.0f, 16.0f, 22.0f, new CubeDeformation(8.0f)),
                PartPose.offset(0.0f, 25.5f, -5.0f));
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(0, 38).addBox(-14.25f, -8.5f, -12.75f, 29.0f, 23.0f, 8.0f, new CubeDeformation(2.0f)),
                PartPose.offset(0.25f, -27.0f, -17.25f));
        PartDefinition neck2 = neck.addOrReplaceChild("neck2", CubeListBuilder.create()
                .texOffs(0, 76).addBox(-12.75f, -8.5f, -12.25f, 26.0f, 18.0f, 9.0f, new CubeDeformation(1.0f)),
                PartPose.offsetAndRotation(0.0f, 3.5f, -11.5f, 0.0873f, 0.0f, 0.0f));
        PartDefinition head = neck2.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 105).addBox(-11.75f, -5.5f, -13.75f, 24.0f, 16.0f, 11.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, -3.0f, -9.5f, 0.1745f, 0.0f, 0.0f));
        PartDefinition teeth = head.addOrReplaceChild("teeth", CubeListBuilder.create(),
                PartPose.offset(-0.25f, 26.5f, -21.75f));
        PartDefinition teeth1 = teeth.addOrReplaceChild("teeth1", CubeListBuilder.create()
                .texOffs(168, 0).addBox(-4.0f, -6.5f, -2.0f, 8.0f, 13.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(6.0f, -18.5f, 7.0f, 0.0f, 0.0f, -0.6109f));
        PartDefinition teeth11 = teeth1.addOrReplaceChild("teeth11", CubeListBuilder.create()
                .texOffs(150, 21).addBox(-16.2092f, -1.6649f, -2.0f, 18.0f, 3.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-1.2633f, 2.1782f, 2.0f, 0.0f, 0.0f, -0.9599f));
        PartDefinition teeth2 = teeth.addOrReplaceChild("teeth2", CubeListBuilder.create()
                .texOffs(168, 0).addBox(-4.4096f, -6.2132f, -2.0f, 8.0f, 13.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-3.5f, -18.5f, 7.0f, 0.0f, 0.0f, 0.6109f));
        PartDefinition teeth22 = teeth2.addOrReplaceChild("teeth22", CubeListBuilder.create()
                .texOffs(150, 21).addBox(-16.6791f, -1.8359f, -2.0f, 18.0f, 3.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.9486f, 1.8502f, 2.0f, 0.0f, 0.0f, -2.1817f));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(78, 0).addBox(-9.0f, -6.3746f, 2.789f, 18.0f, 10.0f, 7.0f, new CubeDeformation(8.0f)),
                PartPose.offsetAndRotation(0.5f, -22.1011f, 17.8952f, -0.3491f, 0.0f, 0.0f));
        PartDefinition tail2 = tail.addOrReplaceChild("tail2", CubeListBuilder.create()
                .texOffs(90, 20).addBox(-6.0f, -5.4136f, 2.789f, 12.0f, 4.0f, 7.0f, new CubeDeformation(8.0f)),
                PartPose.offsetAndRotation(0.0f, 0.8499f, 15.2078f, -0.3491f, 0.0f, 0.0f));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                .texOffs(92, 32).addBox(-5.0f, -4.4136f, 2.789f, 9.0f, 2.0f, 9.0f, new CubeDeformation(7.0f)),
                PartPose.offsetAndRotation(0.0f, -1.0921f, 19.9702f, 0.3491f, 0.0f, 0.0f));
        PartDefinition tail4 = tail3.addOrReplaceChild("tail4", CubeListBuilder.create()
                .texOffs(92, 32).addBox(-5.0f, -4.4136f, 2.789f, 9.0f, 2.0f, 9.0f, new CubeDeformation(6.0f)),
                PartPose.offsetAndRotation(0.0f, 0.0795f, 20.2483f, 0.3491f, 0.0f, 0.0f));
        PartDefinition tail5 = tail4.addOrReplaceChild("tail5", CubeListBuilder.create()
                .texOffs(48, 44).addBox(-5.5f, -2.5f, 0.5f, 11.0f, 3.0f, 29.0f, new CubeDeformation(1.0f)),
                PartPose.offsetAndRotation(-4.5f, -2.9136f, 14.289f, 0.0f, -0.6109f, 0.0f));
        PartDefinition tail6 = tail4.addOrReplaceChild("tail6", CubeListBuilder.create()
                .texOffs(48, 44).addBox(1.2181f, -2.5f, -2.1206f, 11.0f, 3.0f, 29.0f, new CubeDeformation(1.0f)),
                PartPose.offsetAndRotation(-1.5f, -2.9136f, 20.289f, 0.0f, 0.6109f, 0.0f));
        PartDefinition spike2 = tail.addOrReplaceChild("spike2", CubeListBuilder.create()
                .texOffs(151, 63).addBox(-1.5f, -21.75f, -2.0f, 3.0f, 10.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(168, 58).addBox(-3.0f, -11.75f, -3.0f, 6.0f, 13.0f, 6.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-0.5f, -11.4644f, 2.8801f, -0.9599f, 0.0f, 0.0f));
        PartDefinition spike = body.addOrReplaceChild("spike", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, -40.0f, -11.0f, -0.8727f, 0.0f, 0.0f));
        PartDefinition right = spike.addOrReplaceChild("right", CubeListBuilder.create()
                .texOffs(176, 33).addBox(-2.0f, -16.0f, -2.0f, 4.0f, 17.0f, 4.0f, new CubeDeformation(1.0f))
                .texOffs(162, 39).addBox(-1.5f, -28.0f, -1.5f, 3.0f, 11.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(9.5f, 1.6428f, 1.766f, 0.0f, 0.0f, 0.2618f));
        PartDefinition left = spike.addOrReplaceChild("left", CubeListBuilder.create()
                .texOffs(176, 33).addBox(-2.0f, -16.0f, -2.0f, 4.0f, 17.0f, 4.0f, new CubeDeformation(1.0f))
                .texOffs(162, 39).addBox(-1.5f, -28.0f, -1.5f, 3.0f, 11.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-9.5f, 1.6428f, 1.766f, 0.0f, 0.0f, -0.2618f));
        PartDefinition armRight = bone.addOrReplaceChild("armRight", CubeListBuilder.create()
                .texOffs(100, 86).addBox(-2.0f, -0.25f, -5.25f, 1.0f, 29.0f, 13.0f, new CubeDeformation(2.0f)),
                PartPose.offsetAndRotation(-19.5f, 1.75f, -11.75f, 0.0f, 0.0f, 0.3491f));
        PartDefinition armRight2 = armRight.addOrReplaceChild("armRight2", CubeListBuilder.create()
                .texOffs(70, 93).addBox(-0.5f, -0.5f, -2.5f, 1.0f, 22.0f, 13.0f, new CubeDeformation(2.0f)),
                PartPose.offsetAndRotation(-2.184f, 30.3706f, -2.75f, 0.0f, 0.0f, 1.2217f));
        PartDefinition armLeft = bone.addOrReplaceChild("armLeft", CubeListBuilder.create()
                .texOffs(100, 86).addBox(1.0f, -0.25f, -5.25f, 1.0f, 29.0f, 13.0f, new CubeDeformation(2.0f)),
                PartPose.offsetAndRotation(19.5f, 1.75f, -11.75f, 0.0f, 0.0f, -0.3491f));
        PartDefinition armLeft2 = armLeft.addOrReplaceChild("armLeft2", CubeListBuilder.create()
                .texOffs(70, 93).addBox(-0.5f, -0.5f, -2.5f, 1.0f, 22.0f, 13.0f, new CubeDeformation(2.0f)),
                PartPose.offsetAndRotation(2.184f, 30.3706f, -2.75f, 0.0f, 0.0f, -1.2217f));
        return LayerDefinition.create(mesh, 192, 192);
    }

    /** Chaque piece du modele, par son nom d'origine. */
    public static Map<String, ModelPart> index(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart bone = root.getChild("bone");
        parts.put("bone", bone);
        ModelPart body = bone.getChild("body");
        parts.put("body", body);
        ModelPart neck = body.getChild("neck");
        parts.put("neck", neck);
        ModelPart neck2 = neck.getChild("neck2");
        parts.put("neck2", neck2);
        ModelPart head = neck2.getChild("head");
        parts.put("head", head);
        ModelPart teeth = head.getChild("teeth");
        parts.put("teeth", teeth);
        ModelPart teeth1 = teeth.getChild("teeth1");
        parts.put("teeth1", teeth1);
        ModelPart teeth11 = teeth1.getChild("teeth11");
        parts.put("teeth11", teeth11);
        ModelPart teeth2 = teeth.getChild("teeth2");
        parts.put("teeth2", teeth2);
        ModelPart teeth22 = teeth2.getChild("teeth22");
        parts.put("teeth22", teeth22);
        ModelPart tail = body.getChild("tail");
        parts.put("tail", tail);
        ModelPart tail2 = tail.getChild("tail2");
        parts.put("tail2", tail2);
        ModelPart tail3 = tail2.getChild("tail3");
        parts.put("tail3", tail3);
        ModelPart tail4 = tail3.getChild("tail4");
        parts.put("tail4", tail4);
        ModelPart tail5 = tail4.getChild("tail5");
        parts.put("tail5", tail5);
        ModelPart tail6 = tail4.getChild("tail6");
        parts.put("tail6", tail6);
        ModelPart spike2 = tail.getChild("spike2");
        parts.put("spike2", spike2);
        ModelPart spike = body.getChild("spike");
        parts.put("spike", spike);
        ModelPart right = spike.getChild("right");
        parts.put("right", right);
        ModelPart left = spike.getChild("left");
        parts.put("left", left);
        ModelPart armRight = bone.getChild("armRight");
        parts.put("armRight", armRight);
        ModelPart armRight2 = armRight.getChild("armRight2");
        parts.put("armRight2", armRight2);
        ModelPart armLeft = bone.getChild("armLeft");
        parts.put("armLeft", armLeft);
        ModelPart armLeft2 = armLeft.getChild("armLeft2");
        parts.put("armLeft2", armLeft2);
        return parts;
    }
}
