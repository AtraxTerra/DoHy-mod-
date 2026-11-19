package fr.atraxterra.dohy.client.throne;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * GENERE par tools/throne_assets.py. Ne pas modifier a la main : relancer le script.
 *
 * <p>La geometrie de {@code ModelJobThrone} (PalaMod 10.3.0), boite par boite, sur
 * sa texture de 128 x 128. Repere des modeles d'entite : y vers le bas, sol a 24.
 */
@SuppressWarnings("unused")
public final class ThroneGeometry {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("palamod", "throne"), "main");

    private ThroneGeometry() {
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition group = root.addOrReplaceChild("group", CubeListBuilder.create()
                .texOffs(0, 41).addBox(-12F, 3F, -8F, 12F, 6F, 14F)
                .texOffs(0, 0).addBox(-15F, -20F, 6F, 18F, 29F, 5F)
                .texOffs(46, 0).addBox(-13F, 9F, -5F, 14F, 3F, 13F),
                PartPose.offsetAndRotation(6F, 12F, -1F, 0F, 0F, 0F));
        PartDefinition bone = group.addOrReplaceChild("bone", CubeListBuilder.create()
                .texOffs(52, 72).addBox(-2F, -8.5F, -6.5F, 4F, 5F, 4F)
                .texOffs(22, 63).addBox(-7F, -10.5F, -1.5F, 4F, 7F, 4F)
                .texOffs(22, 63).addBox(3F, -10.5F, -1.5F, 4F, 7F, 4F),
                PartPose.offsetAndRotation(-6F, -23.5F, 8.5F, 0F, 0F, 0F));
        PartDefinition cubeR1 = bone.addOrReplaceChild("cube_r1", CubeListBuilder.create()
                .texOffs(32, 20).addBox(-7F, -3.5F, -7F, 14F, 7F, 14F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition bone2 = group.addOrReplaceChild("bone2", CubeListBuilder.create()
                .texOffs(0, 61).addBox(-5.2591F, 4.8572F, -7F, 4F, 9F, 14F),
                PartPose.offsetAndRotation(5.2591F, -4.8572F, -1F, 0F, 0F, 0F));
        PartDefinition cubeR2 = bone2.addOrReplaceChild("cube_r2", CubeListBuilder.create()
                .texOffs(64, 41).addBox(-3F, -0.5F, -7F, 6F, 1F, 14F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0.3927F));
        PartDefinition cubeR3 = bone2.addOrReplaceChild("cube_r3", CubeListBuilder.create()
                .texOffs(66, 56).addBox(-0.5F, -3F, -7F, 1F, 6F, 14F),
                PartPose.offsetAndRotation(-4.573F, 1.8942F, 0F, 0F, 0F, 0.3927F));
        PartDefinition cubeR4 = bone2.addOrReplaceChild("cube_r4", CubeListBuilder.create()
                .texOffs(36, 67).addBox(-0.5F, -0.5F, -7F, 1F, 1F, 14F),
                PartPose.offsetAndRotation(-3.2336F, -1.3394F, 0F, 0F, 0F, 0.3927F));
        PartDefinition cubeR5 = bone2.addOrReplaceChild("cube_r5", CubeListBuilder.create()
                .texOffs(36, 45).addBox(-3F, -3F, -8F, 6F, 6F, 16F),
                PartPose.offsetAndRotation(-1.3394F, 3.2336F, -1F, 0F, 0F, 0.3927F));
        PartDefinition bone3 = group.addOrReplaceChild("bone3", CubeListBuilder.create()
                .texOffs(0, 61).addBox(1.2591F, 4.8572F, -7F, 4F, 9F, 14F),
                PartPose.offsetAndRotation(-17.2591F, -4.8572F, -1F, 0F, 0F, 0F));
        PartDefinition cubeR6 = bone3.addOrReplaceChild("cube_r6", CubeListBuilder.create()
                .texOffs(64, 41).addBox(-3F, -0.5F, -7F, 6F, 1F, 14F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, -0.3927F));
        PartDefinition cubeR7 = bone3.addOrReplaceChild("cube_r7", CubeListBuilder.create()
                .texOffs(66, 56).addBox(-0.5F, -3F, -7F, 1F, 6F, 14F),
                PartPose.offsetAndRotation(4.573F, 1.8942F, 0F, 0F, 0F, -0.3927F));
        PartDefinition cubeR8 = bone3.addOrReplaceChild("cube_r8", CubeListBuilder.create()
                .texOffs(36, 67).addBox(-0.5F, -0.5F, -7F, 1F, 1F, 14F),
                PartPose.offsetAndRotation(3.2336F, -1.3394F, 0F, 0F, 0F, -0.3927F));
        PartDefinition cubeR9 = bone3.addOrReplaceChild("cube_r9", CubeListBuilder.create()
                .texOffs(36, 45).addBox(-3F, -3F, -8F, 6F, 6F, 16F),
                PartPose.offsetAndRotation(1.3394F, 3.2336F, -1F, 0F, 0F, -0.3927F));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
