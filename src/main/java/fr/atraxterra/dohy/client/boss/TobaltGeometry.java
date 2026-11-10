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
 * La geometrie de {@code ModelTobalt} (PalaMod 10.3.0), traduite de son bytecode
 * par {@code tools/modelbase2java.py}, sans retouche des cubes. Ne pas modifier
 * a la main : regenerer.
 */
public final class TobaltGeometry {

    private TobaltGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offset(6.0f, 24.0f, -8.0f));
        PartDefinition head = bone.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0f, -7.0f, -13.0f, 12.0f, 10.0f, 12.0f, new CubeDeformation(2.0f))
                .texOffs(0, 29).addBox(-8.5f, 2.75f, -14.0f, 2.0f, 6.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(0, 29).addBox(6.5f, 2.75f, -14.0f, 2.0f, 6.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-6.0f, -18.0f, -30.0f));
        PartDefinition stars = head.addOrReplaceChild("stars", CubeListBuilder.create()
                .texOffs(51, 0).addBox(-6.0f, -4.9167f, 10.8333f, 12.0f, 10.0f, 0.0f, new CubeDeformation(0.0f))
                .texOffs(51, 0).addBox(-6.0f, -4.9167f, -11.1667f, 12.0f, 10.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, -14.8333f, -8.8333f, 0.0f, -1.5708f, 0.0f));
        PartDefinition stars2 = stars.addOrReplaceChild("stars2", CubeListBuilder.create()
                .texOffs(51, 0).addBox(-6.0f, -5.0f, 11.0f, 12.0f, 10.0f, 0.0f, new CubeDeformation(0.0f))
                .texOffs(51, 0).addBox(-6.0f, -5.0f, -11.0f, 12.0f, 10.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 0.0833f, -0.1667f, 0.0f, -1.5708f, 0.0f));
        PartDefinition shell = bone.addOrReplaceChild("shell", CubeListBuilder.create()
                .texOffs(46, 46).addBox(-35.0f, -36.0f, -25.0f, 70.0f, 72.0f, 35.0f, new CubeDeformation(0.0f))
                .texOffs(65, 65).addBox(-35.0f, -36.0f, -15.0f, 70.0f, 72.0f, 16.0f, new CubeDeformation(3.0f))
                .texOffs(115, 48).addBox(-38.0f, -36.0f, -25.0f, 3.0f, 72.0f, 33.0f, new CubeDeformation(-2.0f))
                .texOffs(48, 48).addBox(-35.0f, -38.0f, -25.0f, 70.0f, 2.0f, 33.0f, new CubeDeformation(-2.0f))
                .texOffs(48, 48).addBox(-35.0f, 36.0f, -25.0f, 70.0f, 1.0f, 33.0f, new CubeDeformation(-2.0f))
                .texOffs(134, 66).addBox(34.0f, -36.0f, -23.0f, 4.0f, 72.0f, 15.0f, new CubeDeformation(-1.0f))
                .texOffs(71, 167).addBox(-40.0f, -40.0f, -1.0f, 80.0f, 80.0f, 9.0f, new CubeDeformation(0.0f))
                .texOffs(218, 184).addBox(40.0f, -27.0f, -1.0f, 2.0f, 56.0f, 9.0f, new CubeDeformation(0.0f))
                .texOffs(224, 186).addBox(-42.0f, -27.0f, -1.0f, 2.0f, 56.0f, 9.0f, new CubeDeformation(0.0f))
                .texOffs(112, 238).addBox(-28.0f, 40.0f, -1.0f, 56.0f, 2.0f, 9.0f, new CubeDeformation(0.0f))
                .texOffs(112, 237).addBox(-28.0f, -42.0f, -1.0f, 56.0f, 2.0f, 9.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(17.0f, -5.0f, -37.0f, 8.0f, 11.0f, 12.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(7.0f, 14.0f, -37.0f, 8.0f, 11.0f, 12.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(15.0f, 0.0f, -47.0f, 8.0f, 22.0f, 22.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(12.0f, -7.0f, -55.0f, 3.0f, 22.0f, 26.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-12.0f, 16.0f, -50.0f, 18.0f, 7.0f, 13.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-12.0f, 13.0f, -55.0f, 18.0f, 3.0f, 13.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-12.0f, 10.0f, -64.0f, 18.0f, 3.0f, 14.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-19.0f, 10.0f, -55.0f, 11.0f, 7.0f, 18.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(23.0f, -3.0f, -36.0f, 3.0f, 22.0f, 11.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(30.0f, 13.0f, -26.0f, 9.0f, 6.0f, 12.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(23.0f, 0.0f, -43.0f, 3.0f, 14.0f, 7.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-27.0f, -4.0f, -48.0f, 9.0f, 11.0f, 23.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-26.0f, -3.0f, -50.0f, 6.0f, 8.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-25.0f, -2.0f, -52.0f, 4.0f, 6.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-25.0f, 3.0f, -48.0f, 9.0f, 11.0f, 23.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-22.0f, 14.0f, -37.0f, 8.0f, 11.0f, 12.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-30.0f, 21.0f, -26.0f, 5.0f, 4.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-22.0f, -19.0f, -43.0f, 8.0f, 15.0f, 18.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-16.0f, -23.0f, -50.0f, 8.0f, 8.0f, 25.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(4.0f, -23.0f, -39.0f, 17.0f, 12.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(98, 115).addBox(18.0f, -11.0f, -28.0f, 15.0f, 6.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(98, 115).addBox(15.0f, -11.0f, -33.0f, 2.0f, 6.0f, 5.0f, new CubeDeformation(0.0f))
                .texOffs(98, 115).addBox(33.0f, -11.0f, -28.0f, 6.0f, 6.0f, 16.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(10.0f, -13.0f, -49.0f, 11.0f, 12.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(3.0f, -18.0f, -55.0f, 11.0f, 12.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(7.0f, -1.0f, -71.0f, 5.0f, 14.0f, 21.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(8.0f, 0.0f, -73.0f, 3.0f, 10.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-17.0f, -1.0f, -58.0f, 5.0f, 14.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(4.0f, -15.0f, -58.0f, 7.0f, 4.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-8.0f, 18.0f, -30.0f, 6.0f, 12.0f, 5.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-7.0f, 13.0f, -58.0f, 4.0f, 7.0f, 18.0f, new CubeDeformation(0.0f))
                .texOffs(98, 106).addBox(-13.0f, 23.0f, -27.0f, 4.0f, 17.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-2.0f, 17.0f, -40.0f, 11.0f, 8.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(5.0f, 14.0f, -51.0f, 11.0f, 8.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-20.0f, 14.0f, -41.0f, 7.0f, 8.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-8.0f, -23.0f, -55.0f, 17.0f, 7.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-19.0f, -14.0f, -55.0f, 9.0f, 15.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-16.0f, -14.0f, -70.0f, 6.0f, 8.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-13.0f, -7.0f, -67.0f, 6.0f, 8.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(96, 114).addBox(-15.0f, 13.0f, -40.0f, 13.0f, 8.0f, 14.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(6.0f, -14.0f, -64.0f, 5.0f, 15.0f, 15.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-11.0f, -19.0f, -70.0f, 9.0f, 7.0f, 21.0f, new CubeDeformation(0.0f))
                .texOffs(0, 214).addBox(-3.0f, -19.0f, -63.0f, 9.0f, 7.0f, 14.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-23.0f, -23.0f, -39.0f, 13.0f, 8.0f, 14.0f, new CubeDeformation(0.0f))
                .texOffs(98, 101).addBox(-12.0f, -40.0f, -31.0f, 7.0f, 18.0f, 12.0f, new CubeDeformation(0.0f))
                .texOffs(98, 101).addBox(-12.0f, -21.0f, -41.0f, 7.0f, 18.0f, 12.0f, new CubeDeformation(0.0f))
                .texOffs(98, 101).addBox(-12.0f, -13.0f, -61.0f, 19.0f, 23.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(98, 101).addBox(-17.0f, 0.0f, -61.0f, 5.0f, 10.0f, 3.0f, new CubeDeformation(0.0f))
                .texOffs(98, 101).addBox(-18.0f, 0.0f, -61.0f, 1.0f, 10.0f, 13.0f, new CubeDeformation(0.0f))
                .texOffs(98, 101).addBox(-20.0f, 0.0f, -49.0f, 2.0f, 10.0f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(98, 101).addBox(-26.0f, 6.0f, -49.0f, 6.0f, 3.0f, 11.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-28.0f, -5.0f, -39.0f, 18.0f, 8.0f, 14.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-40.0f, -5.0f, -28.0f, 18.0f, 8.0f, 14.0f, new CubeDeformation(0.0f))
                .texOffs(0, 197).addBox(-34.0f, -4.0f, -32.0f, 7.0f, 6.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(0, 190).addBox(-5.0f, -27.0f, -45.0f, 15.0f, 8.0f, 20.0f, new CubeDeformation(0.0f))
                .texOffs(0, 190).addBox(-12.0f, -34.0f, -29.0f, 22.0f, 8.0f, 5.0f, new CubeDeformation(0.0f))
                .texOffs(0, 190).addBox(21.0f, -34.0f, -29.0f, 6.0f, 8.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-6.0f, -25.0f, 8.0f, -1.5708f, 0.0f, 0.0f));
        PartDefinition armRight = bone.addOrReplaceChild("armRight", CubeListBuilder.create()
                .texOffs(204, 0).addBox(-6.5f, 0.5f, -6.5f, 13.0f, 17.0f, 13.0f, new CubeDeformation(0.0f)),
                PartPose.offset(23.5f, -17.5f, -14.5f));
        PartDefinition armLeft = bone.addOrReplaceChild("armLeft", CubeListBuilder.create()
                .texOffs(204, 0).addBox(-6.5f, 0.5f, -6.5f, 13.0f, 17.0f, 13.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-35.5f, -17.5f, -14.5f));
        PartDefinition legLeft = bone.addOrReplaceChild("legLeft", CubeListBuilder.create()
                .texOffs(204, 0).addBox(-6.5f, 0.5f, -6.5f, 13.0f, 17.0f, 13.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-35.5f, -17.5f, 27.5f));
        PartDefinition legRight = bone.addOrReplaceChild("legRight", CubeListBuilder.create()
                .texOffs(204, 0).addBox(-6.5f, 0.5f, -6.5f, 13.0f, 17.0f, 13.0f, new CubeDeformation(0.0f)),
                PartPose.offset(23.5f, -17.5f, 27.5f));
        return LayerDefinition.create(mesh, 256, 256);
    }

    /** Chaque piece du modele, par son nom d'origine. */
    public static Map<String, ModelPart> index(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart bone = root.getChild("bone");
        parts.put("bone", bone);
        ModelPart head = bone.getChild("head");
        parts.put("head", head);
        ModelPart stars = head.getChild("stars");
        parts.put("stars", stars);
        ModelPart stars2 = stars.getChild("stars2");
        parts.put("stars2", stars2);
        ModelPart shell = bone.getChild("shell");
        parts.put("shell", shell);
        ModelPart armRight = bone.getChild("armRight");
        parts.put("armRight", armRight);
        ModelPart armLeft = bone.getChild("armLeft");
        parts.put("armLeft", armLeft);
        ModelPart legLeft = bone.getChild("legLeft");
        parts.put("legLeft", legLeft);
        ModelPart legRight = bone.getChild("legRight");
        parts.put("legRight", legRight);
        return parts;
    }
}
