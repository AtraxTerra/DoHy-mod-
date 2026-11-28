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
 * La geometrie de {@code ModelSnail} (PalaMod 10.3.0), traduite de son bytecode
 * par {@code tools/modelbase2java.py}, sans retouche des cubes. Ne pas modifier
 * a la main : regenerer.
 */
public final class SnailGeometry {

    private SnailGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create()
                .texOffs(10, 21).addBox(-1.5f, -3.0f, -7.0f, 3.0f, 3.0f, 8.0f, new CubeDeformation(0.0f))
                .texOffs(7, 0).addBox(-1.5f, -1.0f, -8.0f, 1.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(7, 0).addBox(0.5f, -1.0f, -8.0f, 1.0f, 1.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 21).addBox(-1.0f, -2.0f, 1.0f, 2.0f, 2.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 24.0f, 1.0f));
        PartDefinition eyes = bone.addOrReplaceChild("eyes", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, -2.25f, -6.25f, 0.5236f, 0.0f, 0.0f));
        PartDefinition rEye = eyes.addOrReplaceChild("rEye", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -2.5f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-1.0f, -1.0f, 0.0f, 0.0f, 0.0f, -0.1745f));
        PartDefinition lEye = eyes.addOrReplaceChild("lEye", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -2.5f, -0.5f, 1.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.1745f));
        PartDefinition shell = bone.addOrReplaceChild("shell", CubeListBuilder.create()
                .texOffs(12, 0).addBox(-1.5f, -2.5858f, -2.5858f, 3.0f, 7.0f, 7.0f, new CubeDeformation(0.25f)),
                PartPose.offsetAndRotation(0.0f, -4.0f, -1.0f, 1.309f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Chaque piece du modele, par son nom d'origine. */
    public static Map<String, ModelPart> index(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart bone = root.getChild("bone");
        parts.put("bone", bone);
        ModelPart eyes = bone.getChild("eyes");
        parts.put("eyes", eyes);
        ModelPart rEye = eyes.getChild("rEye");
        parts.put("rEye", rEye);
        ModelPart lEye = eyes.getChild("lEye");
        parts.put("lEye", lEye);
        ModelPart shell = bone.getChild("shell");
        parts.put("shell", shell);
        return parts;
    }
}
