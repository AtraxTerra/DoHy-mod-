package fr.atraxterra.dohy.client.pet;

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
 * La geometrie de {@code ModelSnake} (PalaMod 10.3.0), traduite de son bytecode
 * par {@code tools/modelbase2java.py}, sans retouche des cubes. Ne pas modifier
 * a la main : regenerer.
 */
public final class SnakeGeometry {

    private SnakeGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offset(0.0f, 24.0f, -4.0f));
        PartDefinition tail5 = bone.addOrReplaceChild("tail5", CubeListBuilder.create()
                .texOffs(14, 11).addBox(-2.0f, -1.5f, 0.25f, 4.0f, 3.0f, 12.0f, new CubeDeformation(0.1f)),
                PartPose.offset(0.0f, -1.5f, 34.75f));
        PartDefinition tail6 = tail5.addOrReplaceChild("tail6", CubeListBuilder.create()
                .texOffs(0, 26).addBox(-2.0f, -1.5f, -0.25f, 4.0f, 3.0f, 12.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.0f, 12.5f));
        PartDefinition tail7 = tail6.addOrReplaceChild("tail7", CubeListBuilder.create()
                .texOffs(34, 26).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 12.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.0f, 11.75f));
        PartDefinition tail8 = tail7.addOrReplaceChild("tail8", CubeListBuilder.create()
                .texOffs(1, 47).addBox(-1.5f, -1.5f, 0.0f, 2.0f, 3.0f, 12.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.5f, 0.0f, 12.0f));
        PartDefinition tail4 = bone.addOrReplaceChild("tail4", CubeListBuilder.create()
                .texOffs(38, 9).addBox(-2.0f, -1.5f, -9.5f, 4.0f, 3.0f, 9.0f, new CubeDeformation(0.2f)),
                PartPose.offset(0.0f, -1.5f, 35.5f));
        PartDefinition tail3 = tail4.addOrReplaceChild("tail3", CubeListBuilder.create()
                .texOffs(38, 9).addBox(-2.0f, -1.5f, -9.0f, 4.0f, 3.0f, 9.0f, new CubeDeformation(0.2f)),
                PartPose.offset(0.0f, 0.0f, -9.5f));
        PartDefinition tail2 = tail3.addOrReplaceChild("tail2", CubeListBuilder.create()
                .texOffs(23, 0).addBox(-2.0f, -1.5f, -7.0f, 4.0f, 3.0f, 7.0f, new CubeDeformation(0.1f)),
                PartPose.offset(0.0f, 0.0f, -9.0f));
        PartDefinition tail1 = tail2.addOrReplaceChild("tail1", CubeListBuilder.create()
                .texOffs(46, 0).addBox(-2.0f, -1.5f, -5.5f, 4.0f, 3.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.0f, -6.5f));
        PartDefinition head = tail1.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.5f, -3.0f, -6.0f, 5.0f, 3.0f, 6.0f, new CubeDeformation(0.0f))
                .texOffs(0, 9).addBox(-2.0f, -3.0f, -11.0f, 4.0f, 2.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 1.5f, -5.5f));
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-2.0f, -0.5f, -5.0f, 4.0f, 1.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, -0.5f, -6.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Chaque piece du modele, par son nom d'origine. */
    public static Map<String, ModelPart> index(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart bone = root.getChild("bone");
        parts.put("bone", bone);
        ModelPart tail5 = bone.getChild("tail5");
        parts.put("tail5", tail5);
        ModelPart tail6 = tail5.getChild("tail6");
        parts.put("tail6", tail6);
        ModelPart tail7 = tail6.getChild("tail7");
        parts.put("tail7", tail7);
        ModelPart tail8 = tail7.getChild("tail8");
        parts.put("tail8", tail8);
        ModelPart tail4 = bone.getChild("tail4");
        parts.put("tail4", tail4);
        ModelPart tail3 = tail4.getChild("tail3");
        parts.put("tail3", tail3);
        ModelPart tail2 = tail3.getChild("tail2");
        parts.put("tail2", tail2);
        ModelPart tail1 = tail2.getChild("tail1");
        parts.put("tail1", tail1);
        ModelPart head = tail1.getChild("head");
        parts.put("head", head);
        ModelPart jaw = head.getChild("jaw");
        parts.put("jaw", jaw);
        return parts;
    }
}
