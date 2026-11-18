package fr.atraxterra.dohy.client.hunter;

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
 * La geometrie de {@code ModelElephant} (PalaMod 10.3.0), traduite de son bytecode
 * par {@code tools/modelbase2java.py}, sans retouche des cubes. Ne pas modifier
 * a la main : regenerer.
 *
 * <p>Le modele d'origine change de taille de texture en cours de route : chaque
 * racine a la sienne, donc sa propre couche ici.
 */
public final class ElephantGeometry {

    private ElephantGeometry() {
    }

    /** La racine {@code bone}, texture de 64 x 64. */
    public static LayerDefinition createBoneLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create()
                .texOffs(6, 37).addBox(-7.0f, -18.0f, -9.0f, 12.0f, 10.0f, 17.0f, new CubeDeformation(0.0f)),
                PartPose.offset(1.0f, 24.0f, 0.0f));
        PartDefinition armRight = bone.addOrReplaceChild("armRight", CubeListBuilder.create()
                .texOffs(44, 0).addBox(-2.0f, -2.5f, -2.0f, 5.0f, 13.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-6.0f, -10.5f, -6.0f));
        PartDefinition armLeft = bone.addOrReplaceChild("armLeft", CubeListBuilder.create()
                .texOffs(44, 0).addBox(-3.0f, -2.5f, -2.0f, 5.0f, 13.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offset(4.0f, -10.5f, -6.0f));
        PartDefinition legLeft = bone.addOrReplaceChild("legLeft", CubeListBuilder.create()
                .texOffs(48, 38).addBox(-2.0f, -0.5f, -3.25f, 4.0f, 11.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offset(4.0f, -10.5f, 7.0f));
        PartDefinition legRight = bone.addOrReplaceChild("legRight", CubeListBuilder.create()
                .texOffs(48, 38).addBox(-3.0f, -0.5f, -3.0f, 4.0f, 11.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-5.0f, -10.5f, 6.75f));
        PartDefinition tail = bone.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(1, 56).addBox(-0.5f, -1.0f, -0.5f, 1.0f, 6.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-1.0f, -15.0f, 8.25f, 0.1309f, 0.0f, 0.0f));
        PartDefinition head = bone.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0f, -8.75f, -5.3333f, 8.0f, 11.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-1.0f, -15.25f, -8.6667f));
        PartDefinition tuskRight = head.addOrReplaceChild("tuskRight", CubeListBuilder.create()
                .texOffs(0, 48).addBox(-1.0f, -1.0f, -2.5f, 2.0f, 2.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-2.5f, 1.25f, -4.8333f, 0.5672f, 0.1745f, 0.0f));
        PartDefinition tuskLeft = head.addOrReplaceChild("tuskLeft", CubeListBuilder.create()
                .texOffs(0, 48).addBox(-1.0f, -1.0f, -2.5f, 2.0f, 2.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(2.5f, 1.25f, -4.8333f, 0.5672f, -0.1745f, 0.0f));
        PartDefinition trunk = head.addOrReplaceChild("trunk", CubeListBuilder.create()
                .texOffs(16, 24).addBox(-1.5f, -1.5f, -1.5f, 3.0f, 9.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, -0.25f, -4.8333f, -0.1745f, 0.0f, 0.0f));
        PartDefinition trunk2 = trunk.addOrReplaceChild("trunk2", CubeListBuilder.create()
                .texOffs(1, 20).addBox(-1.5f, -0.5f, -1.5f, 3.0f, 6.0f, 3.0f, new CubeDeformation(-0.5f)),
                PartPose.offsetAndRotation(0.0f, 7.0f, 0.0f, 0.5236f, 0.0f, 0.0f));
        PartDefinition earRight = head.addOrReplaceChild("earRight", CubeListBuilder.create()
                .texOffs(8, 46).addBox(-5.0f, -1.25f, -1.0f, 5.0f, 2.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(1, 38).addBox(-5.0f, 0.75f, 0.0f, 7.0f, 7.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-4.0f, -5.5f, -1.3333f, 0.0f, 0.3054f, 0.2618f));
        PartDefinition earLeft = head.addOrReplaceChild("earLeft", CubeListBuilder.create()
                .texOffs(8, 46).addBox(0.0f, -1.25f, -1.0f, 5.0f, 2.0f, 2.0f, new CubeDeformation(0.0f))
                .texOffs(1, 30).addBox(-2.0f, 0.75f, 0.0f, 7.0f, 7.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(4.0f, -5.5f, -1.3333f, 0.0f, -0.3054f, -0.2618f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Chaque piece de {@code bone}, par son nom d'origine. */
    public static Map<String, ModelPart> indexBone(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart bone = root.getChild("bone");
        parts.put("bone", bone);
        ModelPart armRight = bone.getChild("armRight");
        parts.put("armRight", armRight);
        ModelPart armLeft = bone.getChild("armLeft");
        parts.put("armLeft", armLeft);
        ModelPart legLeft = bone.getChild("legLeft");
        parts.put("legLeft", legLeft);
        ModelPart legRight = bone.getChild("legRight");
        parts.put("legRight", legRight);
        ModelPart tail = bone.getChild("tail");
        parts.put("tail", tail);
        ModelPart head = bone.getChild("head");
        parts.put("head", head);
        ModelPart tuskRight = head.getChild("tuskRight");
        parts.put("tuskRight", tuskRight);
        ModelPart tuskLeft = head.getChild("tuskLeft");
        parts.put("tuskLeft", tuskLeft);
        ModelPart trunk = head.getChild("trunk");
        parts.put("trunk", trunk);
        ModelPart trunk2 = trunk.getChild("trunk2");
        parts.put("trunk2", trunk2);
        ModelPart earRight = head.getChild("earRight");
        parts.put("earRight", earRight);
        ModelPart earLeft = head.getChild("earLeft");
        parts.put("earLeft", earLeft);
        return parts;
    }

    /** La racine {@code boneA}, texture de 128 x 128. */
    public static LayerDefinition createBoneALayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition boneA = root.addOrReplaceChild("boneA", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.2f, -15.2f, -11.2f, 14.0f, 16.0f, 24.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-0.8f, 12.2f, 2.2f));
        PartDefinition armRightA = boneA.addOrReplaceChild("armRightA", CubeListBuilder.create()
                .texOffs(104, 0).addBox(-3.0f, -5.0f, -3.0f, 6.0f, 16.0f, 6.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-4.2f, 0.8f, -7.7f));
        PartDefinition armLeftA = boneA.addOrReplaceChild("armLeftA", CubeListBuilder.create()
                .texOffs(104, 0).addBox(-3.0f, -5.0f, -3.0f, 6.0f, 16.0f, 6.0f, new CubeDeformation(0.0f)),
                PartPose.offset(5.8f, 0.8f, -7.7f));
        PartDefinition legLeftA = boneA.addOrReplaceChild("legLeftA", CubeListBuilder.create()
                .texOffs(78, 0).addBox(-3.0f, -4.0f, -3.0f, 6.0f, 15.0f, 6.0f, new CubeDeformation(0.0f)),
                PartPose.offset(5.8f, 0.8f, 9.3f));
        PartDefinition legRightA = boneA.addOrReplaceChild("legRightA", CubeListBuilder.create()
                .texOffs(78, 0).addBox(-3.0f, -4.0f, -3.0f, 6.0f, 15.0f, 6.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-4.2f, 0.8f, 9.3f));
        PartDefinition tailA = boneA.addOrReplaceChild("tailA", CubeListBuilder.create()
                .texOffs(78, 23).addBox(-0.5f, -1.5f, -0.5f, 1.0f, 9.0f, 1.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.8f, -10.5f, 12.75f, 0.2618f, 0.0f, 0.0f));
        PartDefinition tail2A = tailA.addOrReplaceChild("tail2A", CubeListBuilder.create()
                .texOffs(78, 34).addBox(-1.0f, 0.0f, 0.0f, 2.0f, 4.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 7.1464f, 0.1124f, -0.1745f, 0.0f, 0.0f));
        PartDefinition headA = boneA.addOrReplaceChild("headA", CubeListBuilder.create()
                .texOffs(87, 23).addBox(-5.0357f, -7.8571f, -8.4286f, 10.0f, 13.0f, 10.0f, new CubeDeformation(0.0f))
                .texOffs(95, 47).addBox(-4.0357f, -8.8571f, -7.4286f, 8.0f, 1.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.7857f, -11.1429f, -10.5714f));
        PartDefinition trunkA = headA.addOrReplaceChild("trunkA", CubeListBuilder.create()
                .texOffs(56, 43).addBox(-2.0f, -3.0f, -2.0f, 4.0f, 8.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-0.0357f, 4.1429f, -7.4286f, -0.3054f, 0.0f, 0.0f));
        PartDefinition trunk2A = trunkA.addOrReplaceChild("trunk2A", CubeListBuilder.create()
                .texOffs(38, 41).addBox(-2.0f, -1.0f, -2.0f, 4.0f, 9.0f, 4.0f, new CubeDeformation(-0.25f)),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, 0.5672f, 0.0f, 0.0f));
        PartDefinition trunk3A = trunk2A.addOrReplaceChild("trunk3A", CubeListBuilder.create()
                .texOffs(24, 43).addBox(-1.5f, -0.178f, -1.2158f, 3.0f, 6.0f, 3.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 7.0639f, -0.0554f, 0.8727f, 0.0f, 0.0f));
        PartDefinition tuskRightA = headA.addOrReplaceChild("tuskRightA", CubeListBuilder.create()
                .texOffs(75, 59).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 8.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-3.7857f, 5.1429f, -8.4286f, -0.6981f, 0.0f, 0.2618f));
        PartDefinition tuskLeftA = headA.addOrReplaceChild("tuskLeftA", CubeListBuilder.create()
                .texOffs(75, 59).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 8.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(3.7143f, 5.1429f, -8.4286f, -0.6981f, 0.0f, -0.2618f));
        PartDefinition earRightA = headA.addOrReplaceChild("earRightA", CubeListBuilder.create()
                .texOffs(107, 57).addBox(-9.4f, -2.15f, 0.1f, 10.0f, 13.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-5.6357f, -5.7071f, -3.5286f, 0.0f, 0.2618f, 0.0f));
        PartDefinition earRightPartA = earRightA.addOrReplaceChild("earRightPartA", CubeListBuilder.create()
                .texOffs(77, 47).addBox(-5.5f, -1.0f, -1.0f, 5.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(1.1f, -0.15f, 0.1f, 0.0f, 0.0f, 0.1309f));
        PartDefinition earRightPart2A = earRightPartA.addOrReplaceChild("earRightPart2A", CubeListBuilder.create()
                .texOffs(76, 52).addBox(-6.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-5.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.3491f));
        PartDefinition earLeftA = headA.addOrReplaceChild("earLeftA", CubeListBuilder.create()
                .texOffs(86, 57).addBox(0.0f, -2.15f, 0.1f, 10.0f, 13.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(4.9643f, -5.7071f, -3.5286f, 0.0f, -0.2618f, 0.0f));
        PartDefinition earLeftPartA = earLeftA.addOrReplaceChild("earLeftPartA", CubeListBuilder.create()
                .texOffs(77, 47).addBox(0.0f, -1.0f, -1.0f, 5.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, -0.15f, 0.1f, 0.0f, 0.0f, -0.1309f));
        PartDefinition earLeftPart2A = earLeftPartA.addOrReplaceChild("earLeftPart2A", CubeListBuilder.create()
                .texOffs(76, 52).addBox(-0.5f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.3491f));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Chaque piece de {@code boneA}, par son nom d'origine. */
    public static Map<String, ModelPart> indexBoneA(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart boneA = root.getChild("boneA");
        parts.put("boneA", boneA);
        ModelPart armRightA = boneA.getChild("armRightA");
        parts.put("armRightA", armRightA);
        ModelPart armLeftA = boneA.getChild("armLeftA");
        parts.put("armLeftA", armLeftA);
        ModelPart legLeftA = boneA.getChild("legLeftA");
        parts.put("legLeftA", legLeftA);
        ModelPart legRightA = boneA.getChild("legRightA");
        parts.put("legRightA", legRightA);
        ModelPart tailA = boneA.getChild("tailA");
        parts.put("tailA", tailA);
        ModelPart tail2A = tailA.getChild("tail2A");
        parts.put("tail2A", tail2A);
        ModelPart headA = boneA.getChild("headA");
        parts.put("headA", headA);
        ModelPart trunkA = headA.getChild("trunkA");
        parts.put("trunkA", trunkA);
        ModelPart trunk2A = trunkA.getChild("trunk2A");
        parts.put("trunk2A", trunk2A);
        ModelPart trunk3A = trunk2A.getChild("trunk3A");
        parts.put("trunk3A", trunk3A);
        ModelPart tuskRightA = headA.getChild("tuskRightA");
        parts.put("tuskRightA", tuskRightA);
        ModelPart tuskLeftA = headA.getChild("tuskLeftA");
        parts.put("tuskLeftA", tuskLeftA);
        ModelPart earRightA = headA.getChild("earRightA");
        parts.put("earRightA", earRightA);
        ModelPart earRightPartA = earRightA.getChild("earRightPartA");
        parts.put("earRightPartA", earRightPartA);
        ModelPart earRightPart2A = earRightPartA.getChild("earRightPart2A");
        parts.put("earRightPart2A", earRightPart2A);
        ModelPart earLeftA = headA.getChild("earLeftA");
        parts.put("earLeftA", earLeftA);
        ModelPart earLeftPartA = earLeftA.getChild("earLeftPartA");
        parts.put("earLeftPartA", earLeftPartA);
        ModelPart earLeftPart2A = earLeftPartA.getChild("earLeftPart2A");
        parts.put("earLeftPart2A", earLeftPart2A);
        return parts;
    }

    /** La racine {@code boneB}, texture de 160 x 160. */
    public static LayerDefinition createBoneBLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition boneB = root.addOrReplaceChild("boneB", CubeListBuilder.create()
                .texOffs(20, 80).addBox(-10.0f, -32.0f, -17.0f, 22.0f, 32.0f, 48.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-1.0f, 4.0f, -2.0f));
        PartDefinition armRightB = boneB.addOrReplaceChild("armRightB", CubeListBuilder.create()
                .texOffs(120, 80).addBox(-5.0f, -4.0f, -5.0f, 10.0f, 36.0f, 10.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-7.0f, -12.0f, -9.0f));
        PartDefinition armLeftB = boneB.addOrReplaceChild("armLeftB", CubeListBuilder.create()
                .texOffs(120, 80).addBox(-5.0f, -4.0f, -5.0f, 10.0f, 36.0f, 10.0f, new CubeDeformation(0.0f)),
                PartPose.offset(9.0f, -12.0f, -9.0f));
        PartDefinition legLeftB = boneB.addOrReplaceChild("legLeftB", CubeListBuilder.create()
                .texOffs(120, 80).addBox(-5.0f, -4.0f, -5.0f, 10.0f, 36.0f, 10.0f, new CubeDeformation(0.0f)),
                PartPose.offset(9.0f, -12.0f, 23.0f));
        PartDefinition legRightB = boneB.addOrReplaceChild("legRightB", CubeListBuilder.create()
                .texOffs(120, 80).addBox(-5.0f, -4.0f, -5.0f, 10.0f, 36.0f, 10.0f, new CubeDeformation(0.0f)),
                PartPose.offset(-7.0f, -12.0f, 23.0f));
        PartDefinition tailB = boneB.addOrReplaceChild("tailB", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 16.0f, 2.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(1.0f, -26.0f, 31.0f, 0.1309f, 0.0f, 0.0f));
        PartDefinition tail2B = tailB.addOrReplaceChild("tail2B", CubeListBuilder.create()
                .texOffs(10, 0).addBox(-2.0f, -1.0f, 0.0f, 4.0f, 8.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 14.0f, 0.0f, -0.0873f, 0.0f, 0.0f));
        PartDefinition headB = boneB.addOrReplaceChild("headB", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0f, -12.0f, -13.0f, 18.0f, 22.0f, 19.0f, new CubeDeformation(0.0f))
                .texOffs(55, 0).addBox(-7.5f, -13.0f, -12.75f, 8.0f, 1.0f, 18.0f, new CubeDeformation(0.0f))
                .texOffs(55, 0).addBox(1.5f, -13.0f, -12.75f, 8.0f, 1.0f, 18.0f, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, -32.0f, -18.0f));
        PartDefinition tuskRightB = headB.addOrReplaceChild("tuskRightB", CubeListBuilder.create()
                .texOffs(76, 22).addBox(-2.0f, -2.0f, -13.5f, 4.0f, 4.0f, 15.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-6.0f, 8.0f, -11.5f, 0.4883f, 0.3006f, 0.0547f));
        PartDefinition tuskRight2B = tuskRightB.addOrReplaceChild("tuskRight2B", CubeListBuilder.create()
                .texOffs(93, 65).addBox(-2.0f, -2.0f, -7.5f, 4.0f, 4.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 0.0f, -13.0f, 0.0f, -0.5236f, 0.0f));
        PartDefinition tuskLeftB = headB.addOrReplaceChild("tuskLeftB", CubeListBuilder.create()
                .texOffs(76, 22).addBox(-2.0f, -2.0f, -13.5f, 4.0f, 4.0f, 15.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(8.0f, 8.0f, -11.5f, 0.5338f, -0.298f, -0.0681f));
        PartDefinition tuskLeft2B = tuskLeftB.addOrReplaceChild("tuskLeft2B", CubeListBuilder.create()
                .texOffs(93, 65).addBox(-2.0f, -2.0f, -7.5f, 4.0f, 4.0f, 8.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 0.0f, -13.0f, 0.0f, 0.4363f, 0.0f));
        PartDefinition trunkB = headB.addOrReplaceChild("trunkB", CubeListBuilder.create()
                .texOffs(38, 107).addBox(-3.5f, -3.5f, -3.5f, 7.0f, 12.0f, 7.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(1.0f, 7.5f, -12.5f, -0.1309f, 0.0f, 0.0f));
        PartDefinition trunk2B = trunkB.addOrReplaceChild("trunk2B", CubeListBuilder.create()
                .texOffs(41, 83).addBox(-3.0f, -1.5f, -2.5f, 6.0f, 15.0f, 6.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 9.0f, -0.5f, 0.1745f, 0.0f, 0.0f));
        PartDefinition trunk3B = trunk2B.addOrReplaceChild("trunk3B", CubeListBuilder.create()
                .texOffs(15, 110).addBox(-2.5f, -0.5f, -2.5f, 5.0f, 10.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 13.0f, 0.5f, 0.3491f, 0.0f, 0.0f));
        PartDefinition trunk4B = trunk3B.addOrReplaceChild("trunk4B", CubeListBuilder.create()
                .texOffs(17, 92).addBox(-2.5f, 0.5f, -2.5f, 5.0f, 9.0f, 5.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, 7.6934f, -0.5412f, 0.5672f, 0.0f, 0.0f));
        PartDefinition earRightB = headB.addOrReplaceChild("earRightB", CubeListBuilder.create()
                .texOffs(132, 0).addBox(-10.0f, -2.0f, -2.0f, 10.0f, 4.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(124, 10).addBox(-17.0f, -2.0f, 0.0f, 18.0f, 31.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-8.0f, -8.0f, -2.0f, 0.0f, 0.5672f, 0.0436f));
        PartDefinition earRight2B = earRightB.addOrReplaceChild("earRight2B", CubeListBuilder.create()
                .texOffs(132, 0).addBox(-10.0f, -2.0f, -2.0f, 10.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-9.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.6545f));
        PartDefinition earLeftB = headB.addOrReplaceChild("earLeftB", CubeListBuilder.create()
                .texOffs(132, 0).addBox(0.0f, -2.0f, -2.0f, 10.0f, 4.0f, 4.0f, new CubeDeformation(0.0f))
                .texOffs(124, 42).addBox(-1.0f, -2.0f, 0.0f, 18.0f, 31.0f, 0.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(10.0f, -8.0f, -2.0f, 0.0f, -0.5672f, -0.0436f));
        PartDefinition earLeft2B = earLeftB.addOrReplaceChild("earLeft2B", CubeListBuilder.create()
                .texOffs(132, 0).addBox(-1.0f, -2.0f, -2.0f, 10.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(10.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.6545f));
        return LayerDefinition.create(mesh, 160, 160);
    }

    /** Chaque piece de {@code boneB}, par son nom d'origine. */
    public static Map<String, ModelPart> indexBoneB(ModelPart root) {
        Map<String, ModelPart> parts = new HashMap<>();
        ModelPart boneB = root.getChild("boneB");
        parts.put("boneB", boneB);
        ModelPart armRightB = boneB.getChild("armRightB");
        parts.put("armRightB", armRightB);
        ModelPart armLeftB = boneB.getChild("armLeftB");
        parts.put("armLeftB", armLeftB);
        ModelPart legLeftB = boneB.getChild("legLeftB");
        parts.put("legLeftB", legLeftB);
        ModelPart legRightB = boneB.getChild("legRightB");
        parts.put("legRightB", legRightB);
        ModelPart tailB = boneB.getChild("tailB");
        parts.put("tailB", tailB);
        ModelPart tail2B = tailB.getChild("tail2B");
        parts.put("tail2B", tail2B);
        ModelPart headB = boneB.getChild("headB");
        parts.put("headB", headB);
        ModelPart tuskRightB = headB.getChild("tuskRightB");
        parts.put("tuskRightB", tuskRightB);
        ModelPart tuskRight2B = tuskRightB.getChild("tuskRight2B");
        parts.put("tuskRight2B", tuskRight2B);
        ModelPart tuskLeftB = headB.getChild("tuskLeftB");
        parts.put("tuskLeftB", tuskLeftB);
        ModelPart tuskLeft2B = tuskLeftB.getChild("tuskLeft2B");
        parts.put("tuskLeft2B", tuskLeft2B);
        ModelPart trunkB = headB.getChild("trunkB");
        parts.put("trunkB", trunkB);
        ModelPart trunk2B = trunkB.getChild("trunk2B");
        parts.put("trunk2B", trunk2B);
        ModelPart trunk3B = trunk2B.getChild("trunk3B");
        parts.put("trunk3B", trunk3B);
        ModelPart trunk4B = trunk3B.getChild("trunk4B");
        parts.put("trunk4B", trunk4B);
        ModelPart earRightB = headB.getChild("earRightB");
        parts.put("earRightB", earRightB);
        ModelPart earRight2B = earRightB.getChild("earRight2B");
        parts.put("earRight2B", earRight2B);
        ModelPart earLeftB = headB.getChild("earLeftB");
        parts.put("earLeftB", earLeftB);
        ModelPart earLeft2B = earLeftB.getChild("earLeft2B");
        parts.put("earLeft2B", earLeft2B);
        return parts;
    }
}
