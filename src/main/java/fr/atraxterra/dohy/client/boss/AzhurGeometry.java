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
 * La geometrie de {@code ModelAzhur} (PalaMod 10.3.0), traduite de son bytecode
 * par {@code tools/modelbase2java.py}, sans retouche des cubes. Ne pas modifier
 * a la main : regenerer.
 */
public final class AzhurGeometry {

    private AzhurGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        PartDefinition sandTail = bone.addOrReplaceChild("sandTail", CubeListBuilder.create(),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition sandTailPart = sandTail.addOrReplaceChild("sandTailPart", CubeListBuilder.create()
                .texOffs(112, 92).addBox(-2.0f, -16.0f, -2.0f, 4.0f, 32.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, -16.0f, 0.0f));
        PartDefinition sandTailPart2 = sandTail.addOrReplaceChild("sandTailPart2", CubeListBuilder.create()
                .texOffs(96, 93).addBox(-4.0f, -13.5f, -4.0f, 8.0f, 27.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, -18.5f, 0.0f));
        PartDefinition sandTailPart3 = sandTail.addOrReplaceChild("sandTailPart3", CubeListBuilder.create()
                .texOffs(80, 95).addBox(-6.0f, -10.5f, -6.0f, 12.0f, 21.0f, 12.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, -21.5f, 0.0f));
        PartDefinition sandTailPart4 = sandTail.addOrReplaceChild("sandTailPart4", CubeListBuilder.create()
                .texOffs(64, 98).addBox(-8.0f, -7.0f, -8.0f, 16.0f, 14.0f, 16.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, -25.0f, 0.0f));
        PartDefinition body = bone.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 46).addBox(-7.0f, -53.0f, -4.0f, 15.0f, 27.0f, 9.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-0.5f, 0.0f, 0.0f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0f, -16.0f, -8.0f, 16.0f, 16.0f, 16.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, -53.0f, 0.0f));
        PartDefinition horns = body.addOrReplaceChild("horns", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-8.0f, -10.0f, -2.0f, 4.0f, 10.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(2.0f, -10.0f, -2.0f, 4.0f, 10.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(1.0f, -68.0f, -6.0f, 0.4363f, 0.0f, 0.0f));
        PartDefinition armRight = body.addOrReplaceChild("armRight", CubeListBuilder.create()
                .texOffs(55, 43).addBox(-7.0f, -1.5f, -4.5f, 8.0f, 23.0f, 9.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-8.0f, -51.5f, 0.5f, 0.0f, 0.0f, 0.0873f));
        PartDefinition armLeft = body.addOrReplaceChild("armLeft", CubeListBuilder.create()
                .texOffs(55, 43).addBox(0.0f, -1.5f, -4.5f, 8.0f, 23.0f, 9.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(8.0f, -51.5f, 0.5f, 0.0f, 0.0f, -0.0873f));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Chaque piece du modele, par son nom d'origine. */
    public static Map<String, ModelPart> index(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart bone = root.getChild("bone");
        parts.put("bone", bone);
        ModelPart sandTail = bone.getChild("sandTail");
        parts.put("sandTail", sandTail);
        ModelPart sandTailPart = sandTail.getChild("sandTailPart");
        parts.put("sandTailPart", sandTailPart);
        ModelPart sandTailPart2 = sandTail.getChild("sandTailPart2");
        parts.put("sandTailPart2", sandTailPart2);
        ModelPart sandTailPart3 = sandTail.getChild("sandTailPart3");
        parts.put("sandTailPart3", sandTailPart3);
        ModelPart sandTailPart4 = sandTail.getChild("sandTailPart4");
        parts.put("sandTailPart4", sandTailPart4);
        ModelPart body = bone.getChild("body");
        parts.put("body", body);
        ModelPart head = body.getChild("head");
        parts.put("head", head);
        ModelPart horns = body.getChild("horns");
        parts.put("horns", horns);
        ModelPart armRight = body.getChild("armRight");
        parts.put("armRight", armRight);
        ModelPart armLeft = body.getChild("armLeft");
        parts.put("armLeft", armLeft);
        return parts;
    }
}
