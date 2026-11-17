package fr.atraxterra.dohy.client.arrakis;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/sandworm_model.py. Ne pas modifier a la main : relancer le script.
 *
 * <p>La geometrie du ver des sables, dessinee pour Berskar, a la moitie de sa taille.
 */
@SuppressWarnings("unused")
public final class SandWormGeometry {

    /** Anneaux du corps, sous la tete ; chacun est l'enfant du precedent. */
    public static final int SEGMENTS = 9;
    /** Crocs autour de la gueule. */
    public static final int TUSKS = 8;
    /** Hauteur du ver debout, en pixels du modele (avant le doublement du rendu). */
    public static final float LENGTH = 185F;

    private SandWormGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition segment0 = root.addOrReplaceChild("segment0", CubeListBuilder.create()
                .texOffs(0, 135).addBox(-15F, -16F, -6.2132F, 30F, 15F, 12.4264F, new CubeDeformation(0F))
                .texOffs(396, 0).addBox(-6.2132F, -16F, -15F, 12.4264F, 15F, 30F, new CubeDeformation(0F))
                .texOffs(146, 164).addBox(-16.5F, -3F, -6.8345F, 33F, 3F, 13.669F, new CubeDeformation(0F))
                .texOffs(418, 52).addBox(-6.8345F, -3F, -16.5F, 13.669F, 3F, 33F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition segment0BodyO = segment0.addOrReplaceChild("segment0_body_o", CubeListBuilder.create()
                .texOffs(0, 135).addBox(-15F, -14.98F, -6.2132F, 30F, 14.96F, 12.4264F, new CubeDeformation(0F))
                .texOffs(396, 0).addBox(-6.2132F, -14.98F, -15F, 12.4264F, 14.96F, 30F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment0FoldO = segment0.addOrReplaceChild("segment0_fold_o", CubeListBuilder.create()
                .texOffs(146, 164).addBox(-16.5F, -2.98F, -6.8345F, 33F, 2.96F, 13.669F, new CubeDeformation(0F))
                .texOffs(418, 52).addBox(-6.8345F, -2.98F, -16.5F, 13.669F, 2.96F, 33F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment0Plate0 = segment0.addOrReplaceChild("segment0_plate0", CubeListBuilder.create()
                .texOffs(338, 164).addBox(-4.5F, -12.8238F, 14.4F, 9F, 7F, 2.2F, new CubeDeformation(0F))
                .texOffs(0, 183).addBox(-3.5F, -10.8238F, 16.4F, 7F, 4F, 0.8F, new CubeDeformation(0F))
                .texOffs(412, 164).addBox(-1F, -12.3238F, 17F, 2F, 6F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.0218F, 0F));
        PartDefinition segment0Plate2 = segment0.addOrReplaceChild("segment0_plate2", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -10.4422F, 14.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -8.4422F, 16.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -9.9422F, 17F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.5686F, 0F));
        PartDefinition segment0Wart0 = segment0.addOrReplaceChild("segment0_wart0", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -9.9431F, 14.6F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 4.3593F, 0F));
        PartDefinition segment0Wart1 = segment0.addOrReplaceChild("segment0_wart1", CubeListBuilder.create()
                .texOffs(41, 183).addBox(-1F, -9.0828F, 14.6F, 2F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.7423F, 0F));
        PartDefinition segment0Wart2 = segment0.addOrReplaceChild("segment0_wart2", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -6.965F, 14.6F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.6437F, 0F));
        PartDefinition segment1 = segment0.addOrReplaceChild("segment1", CubeListBuilder.create()
                .texOffs(87, 135).addBox(-15F, -16F, -6.2132F, 30F, 15F, 12.4264F, new CubeDeformation(0F))
                .texOffs(0, 52).addBox(-6.2132F, -16F, -15F, 12.4264F, 15F, 30F, new CubeDeformation(0F))
                .texOffs(146, 164).addBox(-16.5F, -3F, -6.8345F, 33F, 3F, 13.669F, new CubeDeformation(0F))
                .texOffs(418, 52).addBox(-6.8345F, -3F, -16.5F, 13.669F, 3F, 33F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition segment1BodyO = segment1.addOrReplaceChild("segment1_body_o", CubeListBuilder.create()
                .texOffs(87, 135).addBox(-15F, -14.98F, -6.2132F, 30F, 14.96F, 12.4264F, new CubeDeformation(0F))
                .texOffs(0, 52).addBox(-6.2132F, -14.98F, -15F, 12.4264F, 14.96F, 30F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment1FoldO = segment1.addOrReplaceChild("segment1_fold_o", CubeListBuilder.create()
                .texOffs(146, 164).addBox(-16.5F, -2.98F, -6.8345F, 33F, 2.96F, 13.669F, new CubeDeformation(0F))
                .texOffs(418, 52).addBox(-6.8345F, -2.98F, -16.5F, 13.669F, 2.96F, 33F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment1Plate0 = segment1.addOrReplaceChild("segment1_plate0", CubeListBuilder.create()
                .texOffs(338, 164).addBox(-4.5F, -12.8031F, 14.4F, 9F, 7F, 2.2F, new CubeDeformation(0F))
                .texOffs(0, 183).addBox(-3.5F, -10.8031F, 16.4F, 7F, 4F, 0.8F, new CubeDeformation(0F))
                .texOffs(412, 164).addBox(-1F, -12.3031F, 17F, 2F, 6F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.0533F, 0F));
        PartDefinition segment1Plate2 = segment1.addOrReplaceChild("segment1_plate2", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -10.535F, 14.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -8.535F, 16.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -10.035F, 17F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.6385F, 0F));
        PartDefinition segment1Wart0 = segment1.addOrReplaceChild("segment1_wart0", CubeListBuilder.create()
                .texOffs(30, 183).addBox(-1.5F, -9.7608F, 14.6F, 3F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.4837F, 0F));
        PartDefinition segment1Wart1 = segment1.addOrReplaceChild("segment1_wart1", CubeListBuilder.create()
                .texOffs(50, 183).addBox(-2F, -8.681F, 14.6F, 4F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.8551F, 0F));
        PartDefinition segment1Wart2 = segment1.addOrReplaceChild("segment1_wart2", CubeListBuilder.create()
                .texOffs(74, 183).addBox(-1F, -10.097F, 14.6F, 2F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 4.376F, 0F));
        PartDefinition segment2 = segment1.addOrReplaceChild("segment2", CubeListBuilder.create()
                .texOffs(0, 135).addBox(-15F, -16F, -6.2132F, 30F, 15F, 12.4264F, new CubeDeformation(0F))
                .texOffs(396, 0).addBox(-6.2132F, -16F, -15F, 12.4264F, 15F, 30F, new CubeDeformation(0F))
                .texOffs(146, 164).addBox(-16.5F, -3F, -6.8345F, 33F, 3F, 13.669F, new CubeDeformation(0F))
                .texOffs(418, 52).addBox(-6.8345F, -3F, -16.5F, 13.669F, 3F, 33F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition segment2BodyO = segment2.addOrReplaceChild("segment2_body_o", CubeListBuilder.create()
                .texOffs(0, 135).addBox(-15F, -14.98F, -6.2132F, 30F, 14.96F, 12.4264F, new CubeDeformation(0F))
                .texOffs(396, 0).addBox(-6.2132F, -14.98F, -15F, 12.4264F, 14.96F, 30F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment2FoldO = segment2.addOrReplaceChild("segment2_fold_o", CubeListBuilder.create()
                .texOffs(146, 164).addBox(-16.5F, -2.98F, -6.8345F, 33F, 2.96F, 13.669F, new CubeDeformation(0F))
                .texOffs(418, 52).addBox(-6.8345F, -2.98F, -16.5F, 13.669F, 2.96F, 33F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment2Plate0 = segment2.addOrReplaceChild("segment2_plate0", CubeListBuilder.create()
                .texOffs(338, 164).addBox(-4.5F, -13.0556F, 14.4F, 9F, 7F, 2.2F, new CubeDeformation(0F))
                .texOffs(0, 183).addBox(-3.5F, -11.0556F, 16.4F, 7F, 4F, 0.8F, new CubeDeformation(0F))
                .texOffs(412, 164).addBox(-1F, -12.5556F, 17F, 2F, 6F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.0233F, 0F));
        PartDefinition segment2Plate1 = segment2.addOrReplaceChild("segment2_plate1", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -10.7671F, 14.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -8.7671F, 16.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -10.2671F, 17F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.6827F, 0F));
        PartDefinition segment2Plate2 = segment2.addOrReplaceChild("segment2_plate2", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -11.2199F, 14.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -9.2199F, 16.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -10.7199F, 17F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.614F, 0F));
        PartDefinition segment2Wart0 = segment2.addOrReplaceChild("segment2_wart0", CubeListBuilder.create()
                .texOffs(74, 183).addBox(-1F, -10.3542F, 14.6F, 2F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.6622F, 0F));
        PartDefinition segment2Wart1 = segment2.addOrReplaceChild("segment2_wart1", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -7.4114F, 14.6F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.8607F, 0F));
        PartDefinition segment2Wart2 = segment2.addOrReplaceChild("segment2_wart2", CubeListBuilder.create()
                .texOffs(17, 183).addBox(-2F, -9.2792F, 14.6F, 4F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.9875F, 0F));
        PartDefinition segment3 = segment2.addOrReplaceChild("segment3", CubeListBuilder.create()
                .texOffs(285, 98).addBox(-16.5F, -16F, -6.8345F, 33F, 15F, 13.669F, new CubeDeformation(0F))
                .texOffs(206, 0).addBox(-6.8345F, -16F, -16.5F, 13.669F, 15F, 33F, new CubeDeformation(0F))
                .texOffs(0, 164).addBox(-18F, -3F, -7.4558F, 36F, 3F, 14.9117F, new CubeDeformation(0F))
                .texOffs(315, 52).addBox(-7.4558F, -3F, -18F, 14.9117F, 3F, 36F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition segment3BodyO = segment3.addOrReplaceChild("segment3_body_o", CubeListBuilder.create()
                .texOffs(285, 98).addBox(-16.5F, -14.98F, -6.8345F, 33F, 14.96F, 13.669F, new CubeDeformation(0F))
                .texOffs(206, 0).addBox(-6.8345F, -14.98F, -16.5F, 13.669F, 14.96F, 33F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment3FoldO = segment3.addOrReplaceChild("segment3_fold_o", CubeListBuilder.create()
                .texOffs(0, 164).addBox(-18F, -2.98F, -7.4558F, 36F, 2.96F, 14.9117F, new CubeDeformation(0F))
                .texOffs(315, 52).addBox(-7.4558F, -2.98F, -18F, 14.9117F, 2.96F, 36F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment3Plate0 = segment3.addOrReplaceChild("segment3_plate0", CubeListBuilder.create()
                .texOffs(338, 164).addBox(-4.5F, -12.716F, 15.9F, 9F, 7F, 2.2F, new CubeDeformation(0F))
                .texOffs(0, 183).addBox(-3.5F, -10.716F, 17.9F, 7F, 4F, 0.8F, new CubeDeformation(0F))
                .texOffs(412, 164).addBox(-1F, -12.216F, 18.5F, 2F, 6F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.0088F, 0F));
        PartDefinition segment3Plate1 = segment3.addOrReplaceChild("segment3_plate1", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -12.0568F, 15.9F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -10.0568F, 17.9F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -11.5568F, 18.5F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.6155F, 0F));
        PartDefinition segment3Wart0 = segment3.addOrReplaceChild("segment3_wart0", CubeListBuilder.create()
                .texOffs(41, 183).addBox(-1F, -13.8739F, 16.1F, 2F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.6429F, 0F));
        PartDefinition segment3Wart1 = segment3.addOrReplaceChild("segment3_wart1", CubeListBuilder.create()
                .texOffs(50, 183).addBox(-2F, -9.7694F, 16.1F, 4F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.6781F, 0F));
        PartDefinition segment3Wart2 = segment3.addOrReplaceChild("segment3_wart2", CubeListBuilder.create()
                .texOffs(50, 183).addBox(-2F, -10.2235F, 16.1F, 4F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.0019F, 0F));
        PartDefinition segment4 = segment3.addOrReplaceChild("segment4", CubeListBuilder.create()
                .texOffs(380, 98).addBox(-16.5F, -16F, -6.8345F, 33F, 15F, 13.669F, new CubeDeformation(0F))
                .texOffs(301, 0).addBox(-6.8345F, -16F, -16.5F, 13.669F, 15F, 33F, new CubeDeformation(0F))
                .texOffs(0, 164).addBox(-18F, -3F, -7.4558F, 36F, 3F, 14.9117F, new CubeDeformation(0F))
                .texOffs(315, 52).addBox(-7.4558F, -3F, -18F, 14.9117F, 3F, 36F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition segment4BodyO = segment4.addOrReplaceChild("segment4_body_o", CubeListBuilder.create()
                .texOffs(380, 98).addBox(-16.5F, -14.98F, -6.8345F, 33F, 14.96F, 13.669F, new CubeDeformation(0F))
                .texOffs(301, 0).addBox(-6.8345F, -14.98F, -16.5F, 13.669F, 14.96F, 33F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment4FoldO = segment4.addOrReplaceChild("segment4_fold_o", CubeListBuilder.create()
                .texOffs(0, 164).addBox(-18F, -2.98F, -7.4558F, 36F, 2.96F, 14.9117F, new CubeDeformation(0F))
                .texOffs(315, 52).addBox(-7.4558F, -2.98F, -18F, 14.9117F, 2.96F, 36F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment4Plate0 = segment4.addOrReplaceChild("segment4_plate0", CubeListBuilder.create()
                .texOffs(338, 164).addBox(-4.5F, -13.9624F, 15.9F, 9F, 7F, 2.2F, new CubeDeformation(0F))
                .texOffs(0, 183).addBox(-3.5F, -11.9624F, 17.9F, 7F, 4F, 0.8F, new CubeDeformation(0F))
                .texOffs(412, 164).addBox(-1F, -13.4624F, 18.5F, 2F, 6F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.0774F, 0F));
        PartDefinition segment4Plate1 = segment4.addOrReplaceChild("segment4_plate1", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -11.7371F, 15.9F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -9.7371F, 17.9F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -11.2371F, 18.5F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.6596F, 0F));
        PartDefinition segment4Wart0 = segment4.addOrReplaceChild("segment4_wart0", CubeListBuilder.create()
                .texOffs(30, 183).addBox(-1.5F, -13.985F, 16.1F, 3F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.3652F, 0F));
        PartDefinition segment4Wart1 = segment4.addOrReplaceChild("segment4_wart1", CubeListBuilder.create()
                .texOffs(41, 183).addBox(-1F, -10.8062F, 16.1F, 2F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.9237F, 0F));
        PartDefinition segment4Wart2 = segment4.addOrReplaceChild("segment4_wart2", CubeListBuilder.create()
                .texOffs(30, 183).addBox(-1.5F, -14.7873F, 16.1F, 3F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.9585F, 0F));
        PartDefinition segment5 = segment4.addOrReplaceChild("segment5", CubeListBuilder.create()
                .texOffs(285, 98).addBox(-16.5F, -16F, -6.8345F, 33F, 15F, 13.669F, new CubeDeformation(0F))
                .texOffs(206, 0).addBox(-6.8345F, -16F, -16.5F, 13.669F, 15F, 33F, new CubeDeformation(0F))
                .texOffs(0, 164).addBox(-18F, -3F, -7.4558F, 36F, 3F, 14.9117F, new CubeDeformation(0F))
                .texOffs(315, 52).addBox(-7.4558F, -3F, -18F, 14.9117F, 3F, 36F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition segment5BodyO = segment5.addOrReplaceChild("segment5_body_o", CubeListBuilder.create()
                .texOffs(285, 98).addBox(-16.5F, -14.98F, -6.8345F, 33F, 14.96F, 13.669F, new CubeDeformation(0F))
                .texOffs(206, 0).addBox(-6.8345F, -14.98F, -16.5F, 13.669F, 14.96F, 33F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment5FoldO = segment5.addOrReplaceChild("segment5_fold_o", CubeListBuilder.create()
                .texOffs(0, 164).addBox(-18F, -2.98F, -7.4558F, 36F, 2.96F, 14.9117F, new CubeDeformation(0F))
                .texOffs(315, 52).addBox(-7.4558F, -2.98F, -18F, 14.9117F, 2.96F, 36F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment5Plate0 = segment5.addOrReplaceChild("segment5_plate0", CubeListBuilder.create()
                .texOffs(338, 164).addBox(-4.5F, -13.1845F, 15.9F, 9F, 7F, 2.2F, new CubeDeformation(0F))
                .texOffs(0, 183).addBox(-3.5F, -11.1845F, 17.9F, 7F, 4F, 0.8F, new CubeDeformation(0F))
                .texOffs(412, 164).addBox(-1F, -12.6845F, 18.5F, 2F, 6F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.0018F, 0F));
        PartDefinition segment5Plate2 = segment5.addOrReplaceChild("segment5_plate2", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -12.0984F, 15.9F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -10.0984F, 17.9F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -11.5984F, 18.5F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.6997F, 0F));
        PartDefinition segment5Wart0 = segment5.addOrReplaceChild("segment5_wart0", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -10.647F, 16.1F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 4.2999F, 0F));
        PartDefinition segment5Wart1 = segment5.addOrReplaceChild("segment5_wart1", CubeListBuilder.create()
                .texOffs(17, 183).addBox(-2F, -9.2327F, 16.1F, 4F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.5284F, 0F));
        PartDefinition segment5Wart2 = segment5.addOrReplaceChild("segment5_wart2", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -13.4035F, 16.1F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.1605F, 0F));
        PartDefinition segment6 = segment5.addOrReplaceChild("segment6", CubeListBuilder.create()
                .texOffs(79, 98).addBox(-18F, -16F, -7.4558F, 36F, 15F, 14.9117F, new CubeDeformation(0F))
                .texOffs(0, 0).addBox(-7.4558F, -16F, -18F, 14.9117F, 15F, 36F, new CubeDeformation(0F))
                .texOffs(368, 135).addBox(-19.5F, -3F, -8.0772F, 39F, 3F, 16.1543F, new CubeDeformation(0F))
                .texOffs(202, 52).addBox(-8.0772F, -3F, -19.5F, 16.1543F, 3F, 39F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition segment6BodyO = segment6.addOrReplaceChild("segment6_body_o", CubeListBuilder.create()
                .texOffs(79, 98).addBox(-18F, -14.98F, -7.4558F, 36F, 14.96F, 14.9117F, new CubeDeformation(0F))
                .texOffs(0, 0).addBox(-7.4558F, -14.98F, -18F, 14.9117F, 14.96F, 36F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment6FoldO = segment6.addOrReplaceChild("segment6_fold_o", CubeListBuilder.create()
                .texOffs(368, 135).addBox(-19.5F, -2.98F, -8.0772F, 39F, 2.96F, 16.1543F, new CubeDeformation(0F))
                .texOffs(202, 52).addBox(-8.0772F, -2.98F, -19.5F, 16.1543F, 2.96F, 39F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment6Plate0 = segment6.addOrReplaceChild("segment6_plate0", CubeListBuilder.create()
                .texOffs(338, 164).addBox(-4.5F, -13.6628F, 17.4F, 9F, 7F, 2.2F, new CubeDeformation(0F))
                .texOffs(0, 183).addBox(-3.5F, -11.6628F, 19.4F, 7F, 4F, 0.8F, new CubeDeformation(0F))
                .texOffs(412, 164).addBox(-1F, -13.1628F, 20F, 2F, 6F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.0203F, 0F));
        PartDefinition segment6Plate1 = segment6.addOrReplaceChild("segment6_plate1", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -11.3719F, 17.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -9.3719F, 19.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -10.8719F, 20F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.6575F, 0F));
        PartDefinition segment6Plate2 = segment6.addOrReplaceChild("segment6_plate2", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -12.3725F, 17.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -10.3725F, 19.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -11.8725F, 20F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.5651F, 0F));
        PartDefinition segment6Wart0 = segment6.addOrReplaceChild("segment6_wart0", CubeListBuilder.create()
                .texOffs(30, 183).addBox(-1.5F, -13.6689F, 17.6F, 3F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.0081F, 0F));
        PartDefinition segment6Wart1 = segment6.addOrReplaceChild("segment6_wart1", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -14.1562F, 17.6F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.6358F, 0F));
        PartDefinition segment6Wart2 = segment6.addOrReplaceChild("segment6_wart2", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -13.5244F, 17.6F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.2637F, 0F));
        PartDefinition segment7 = segment6.addOrReplaceChild("segment7", CubeListBuilder.create()
                .texOffs(182, 98).addBox(-18F, -16F, -7.4558F, 36F, 15F, 14.9117F, new CubeDeformation(0F))
                .texOffs(103, 0).addBox(-7.4558F, -16F, -18F, 14.9117F, 15F, 36F, new CubeDeformation(0F))
                .texOffs(368, 135).addBox(-19.5F, -3F, -8.0772F, 39F, 3F, 16.1543F, new CubeDeformation(0F))
                .texOffs(202, 52).addBox(-8.0772F, -3F, -19.5F, 16.1543F, 3F, 39F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition segment7BodyO = segment7.addOrReplaceChild("segment7_body_o", CubeListBuilder.create()
                .texOffs(182, 98).addBox(-18F, -14.98F, -7.4558F, 36F, 14.96F, 14.9117F, new CubeDeformation(0F))
                .texOffs(103, 0).addBox(-7.4558F, -14.98F, -18F, 14.9117F, 14.96F, 36F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment7FoldO = segment7.addOrReplaceChild("segment7_fold_o", CubeListBuilder.create()
                .texOffs(368, 135).addBox(-19.5F, -2.98F, -8.0772F, 39F, 2.96F, 16.1543F, new CubeDeformation(0F))
                .texOffs(202, 52).addBox(-8.0772F, -2.98F, -19.5F, 16.1543F, 2.96F, 39F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment7Plate0 = segment7.addOrReplaceChild("segment7_plate0", CubeListBuilder.create()
                .texOffs(338, 164).addBox(-4.5F, -14.2304F, 17.4F, 9F, 7F, 2.2F, new CubeDeformation(0F))
                .texOffs(0, 183).addBox(-3.5F, -12.2304F, 19.4F, 7F, 4F, 0.8F, new CubeDeformation(0F))
                .texOffs(412, 164).addBox(-1F, -13.7304F, 20F, 2F, 6F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.0689F, 0F));
        PartDefinition segment7Plate1 = segment7.addOrReplaceChild("segment7_plate1", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -10.8641F, 17.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -8.8641F, 19.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -10.3641F, 20F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.6574F, 0F));
        PartDefinition segment7Plate2 = segment7.addOrReplaceChild("segment7_plate2", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -10.9284F, 17.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -8.9284F, 19.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -10.4284F, 20F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.6598F, 0F));
        PartDefinition segment7Wart0 = segment7.addOrReplaceChild("segment7_wart0", CubeListBuilder.create()
                .texOffs(17, 183).addBox(-2F, -14.1562F, 17.6F, 4F, 3F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.2551F, 0F));
        PartDefinition segment7Wart1 = segment7.addOrReplaceChild("segment7_wart1", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -9.9309F, 17.6F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.3542F, 0F));
        PartDefinition segment7Wart2 = segment7.addOrReplaceChild("segment7_wart2", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -9.37F, 17.6F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.7463F, 0F));
        PartDefinition segment8 = segment7.addOrReplaceChild("segment8", CubeListBuilder.create()
                .texOffs(79, 98).addBox(-18F, -16F, -7.4558F, 36F, 15F, 14.9117F, new CubeDeformation(0F))
                .texOffs(0, 0).addBox(-7.4558F, -16F, -18F, 14.9117F, 15F, 36F, new CubeDeformation(0F))
                .texOffs(368, 135).addBox(-19.5F, -3F, -8.0772F, 39F, 3F, 16.1543F, new CubeDeformation(0F))
                .texOffs(202, 52).addBox(-8.0772F, -3F, -19.5F, 16.1543F, 3F, 39F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition segment8BodyO = segment8.addOrReplaceChild("segment8_body_o", CubeListBuilder.create()
                .texOffs(79, 98).addBox(-18F, -14.98F, -7.4558F, 36F, 14.96F, 14.9117F, new CubeDeformation(0F))
                .texOffs(0, 0).addBox(-7.4558F, -14.98F, -18F, 14.9117F, 14.96F, 36F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -1F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment8FoldO = segment8.addOrReplaceChild("segment8_fold_o", CubeListBuilder.create()
                .texOffs(368, 135).addBox(-19.5F, -2.98F, -8.0772F, 39F, 2.96F, 16.1543F, new CubeDeformation(0F))
                .texOffs(202, 52).addBox(-8.0772F, -2.98F, -19.5F, 16.1543F, 2.96F, 39F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition segment8Plate0 = segment8.addOrReplaceChild("segment8_plate0", CubeListBuilder.create()
                .texOffs(338, 164).addBox(-4.5F, -12.6264F, 17.4F, 9F, 7F, 2.2F, new CubeDeformation(0F))
                .texOffs(0, 183).addBox(-3.5F, -10.6264F, 19.4F, 7F, 4F, 0.8F, new CubeDeformation(0F))
                .texOffs(412, 164).addBox(-1F, -12.1264F, 20F, 2F, 6F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.0508F, 0F));
        PartDefinition segment8Plate1 = segment8.addOrReplaceChild("segment8_plate1", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -11.2224F, 17.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -9.2224F, 19.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -10.7224F, 20F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.6585F, 0F));
        PartDefinition segment8Plate2 = segment8.addOrReplaceChild("segment8_plate2", CubeListBuilder.create()
                .texOffs(380, 164).addBox(-3F, -12.3764F, 17.4F, 6F, 5F, 2.2F, new CubeDeformation(0F))
                .texOffs(83, 183).addBox(-2F, -10.3764F, 19.4F, 4F, 2F, 0.8F, new CubeDeformation(0F))
                .texOffs(486, 164).addBox(-1F, -11.8764F, 20F, 2F, 4F, 1.2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.6793F, 0F));
        PartDefinition segment8Wart0 = segment8.addOrReplaceChild("segment8_wart0", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -13.3306F, 17.6F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.9149F, 0F));
        PartDefinition segment8Wart1 = segment8.addOrReplaceChild("segment8_wart1", CubeListBuilder.create()
                .texOffs(50, 183).addBox(-2F, -6.4425F, 17.6F, 4F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.2806F, 0F));
        PartDefinition segment8Wart2 = segment8.addOrReplaceChild("segment8_wart2", CubeListBuilder.create()
                .texOffs(63, 183).addBox(-1.5F, -9.0431F, 17.6F, 3F, 2F, 1.4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.9228F, 0F));
        PartDefinition head = segment8.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(253, 135).addBox(-20F, -3F, -8.2843F, 40F, 3F, 16.5685F, new CubeDeformation(0F))
                .texOffs(87, 52).addBox(-8.2843F, -3F, -20F, 16.5685F, 3F, 40F, new CubeDeformation(0F))
                .texOffs(174, 135).addBox(-13.4F, -3F, -5.5505F, 26.8F, 9F, 11.1009F, new CubeDeformation(0F))
                .texOffs(0, 98).addBox(-5.5505F, -3F, -13.4F, 11.1009F, 9F, 26.8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition headFoldO = head.addOrReplaceChild("head_fold_o", CubeListBuilder.create()
                .texOffs(253, 135).addBox(-20F, -2.98F, -8.2843F, 40F, 2.96F, 16.5685F, new CubeDeformation(0F))
                .texOffs(87, 52).addBox(-8.2843F, -2.98F, -20F, 16.5685F, 2.96F, 40F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition headWall0 = head.addOrReplaceChild("head_wall0", CubeListBuilder.create()
                .texOffs(103, 164).addBox(-7.7558F, -14F, 13F, 15.5117F, 13F, 5F, new CubeDeformation(0F))
                .texOffs(287, 164).addBox(-9.3056F, -15F, 16F, 18.6112F, 4F, 5.5F, new CubeDeformation(0F))
                .texOffs(434, 164).addBox(-6.7558F, -14F, 11.5F, 13.5117F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition headWall1 = head.addOrReplaceChild("head_wall1", CubeListBuilder.create()
                .texOffs(103, 164).addBox(-7.7558F, -14F, 13F, 15.5117F, 13F, 5F, new CubeDeformation(0F))
                .texOffs(287, 164).addBox(-9.3056F, -15F, 16F, 18.6112F, 4F, 5.5F, new CubeDeformation(0F))
                .texOffs(434, 164).addBox(-6.7558F, -14F, 11.5F, 13.5117F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.7854F, 0F));
        PartDefinition headWall2 = head.addOrReplaceChild("head_wall2", CubeListBuilder.create()
                .texOffs(103, 164).addBox(-7.7558F, -14F, 13F, 15.5117F, 13F, 5F, new CubeDeformation(0F))
                .texOffs(287, 164).addBox(-9.3056F, -15F, 16F, 18.6112F, 4F, 5.5F, new CubeDeformation(0F))
                .texOffs(434, 164).addBox(-6.7558F, -14F, 11.5F, 13.5117F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.5708F, 0F));
        PartDefinition headWall3 = head.addOrReplaceChild("head_wall3", CubeListBuilder.create()
                .texOffs(103, 164).addBox(-7.7558F, -14F, 13F, 15.5117F, 13F, 5F, new CubeDeformation(0F))
                .texOffs(287, 164).addBox(-9.3056F, -15F, 16F, 18.6112F, 4F, 5.5F, new CubeDeformation(0F))
                .texOffs(434, 164).addBox(-6.7558F, -14F, 11.5F, 13.5117F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.3562F, 0F));
        PartDefinition headWall4 = head.addOrReplaceChild("head_wall4", CubeListBuilder.create()
                .texOffs(103, 164).addBox(-7.7558F, -14F, 13F, 15.5117F, 13F, 5F, new CubeDeformation(0F))
                .texOffs(287, 164).addBox(-9.3056F, -15F, 16F, 18.6112F, 4F, 5.5F, new CubeDeformation(0F))
                .texOffs(434, 164).addBox(-6.7558F, -14F, 11.5F, 13.5117F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.1416F, 0F));
        PartDefinition headWall5 = head.addOrReplaceChild("head_wall5", CubeListBuilder.create()
                .texOffs(103, 164).addBox(-7.7558F, -14F, 13F, 15.5117F, 13F, 5F, new CubeDeformation(0F))
                .texOffs(287, 164).addBox(-9.3056F, -15F, 16F, 18.6112F, 4F, 5.5F, new CubeDeformation(0F))
                .texOffs(434, 164).addBox(-6.7558F, -14F, 11.5F, 13.5117F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.927F, 0F));
        PartDefinition headWall6 = head.addOrReplaceChild("head_wall6", CubeListBuilder.create()
                .texOffs(103, 164).addBox(-7.7558F, -14F, 13F, 15.5117F, 13F, 5F, new CubeDeformation(0F))
                .texOffs(287, 164).addBox(-9.3056F, -15F, 16F, 18.6112F, 4F, 5.5F, new CubeDeformation(0F))
                .texOffs(434, 164).addBox(-6.7558F, -14F, 11.5F, 13.5117F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 4.7124F, 0F));
        PartDefinition headWall7 = head.addOrReplaceChild("head_wall7", CubeListBuilder.create()
                .texOffs(103, 164).addBox(-7.7558F, -14F, 13F, 15.5117F, 13F, 5F, new CubeDeformation(0F))
                .texOffs(287, 164).addBox(-9.3056F, -15F, 16F, 18.6112F, 4F, 5.5F, new CubeDeformation(0F))
                .texOffs(434, 164).addBox(-6.7558F, -14F, 11.5F, 13.5117F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.4978F, 0F));
        PartDefinition headThroatO = head.addOrReplaceChild("head_throat_o", CubeListBuilder.create()
                .texOffs(174, 135).addBox(-13.4F, -8.98F, -5.5505F, 26.8F, 8.96F, 11.1009F, new CubeDeformation(0F))
                .texOffs(0, 98).addBox(-5.5505F, -8.98F, -13.4F, 11.1009F, 8.96F, 26.8F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0.7854F, 0F));
        PartDefinition teeth = head.addOrReplaceChild("teeth", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition tooth0 = teeth.addOrReplaceChild("tooth0", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition tooth1 = teeth.addOrReplaceChild("tooth1", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.3927F, 0F));
        PartDefinition tooth2 = teeth.addOrReplaceChild("tooth2", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.5236F, 0F));
        PartDefinition tooth3 = teeth.addOrReplaceChild("tooth3", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.9163F, 0F));
        PartDefinition tooth4 = teeth.addOrReplaceChild("tooth4", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.0472F, 0F));
        PartDefinition tooth5 = teeth.addOrReplaceChild("tooth5", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.4399F, 0F));
        PartDefinition tooth6 = teeth.addOrReplaceChild("tooth6", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.5708F, 0F));
        PartDefinition tooth7 = teeth.addOrReplaceChild("tooth7", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.9635F, 0F));
        PartDefinition tooth8 = teeth.addOrReplaceChild("tooth8", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.0944F, 0F));
        PartDefinition tooth9 = teeth.addOrReplaceChild("tooth9", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.4871F, 0F));
        PartDefinition tooth10 = teeth.addOrReplaceChild("tooth10", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.618F, 0F));
        PartDefinition tooth11 = teeth.addOrReplaceChild("tooth11", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.0107F, 0F));
        PartDefinition tooth12 = teeth.addOrReplaceChild("tooth12", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.1416F, 0F));
        PartDefinition tooth13 = teeth.addOrReplaceChild("tooth13", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.5343F, 0F));
        PartDefinition tooth14 = teeth.addOrReplaceChild("tooth14", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.6652F, 0F));
        PartDefinition tooth15 = teeth.addOrReplaceChild("tooth15", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 4.0579F, 0F));
        PartDefinition tooth16 = teeth.addOrReplaceChild("tooth16", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 4.1888F, 0F));
        PartDefinition tooth17 = teeth.addOrReplaceChild("tooth17", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 4.5815F, 0F));
        PartDefinition tooth18 = teeth.addOrReplaceChild("tooth18", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 4.7124F, 0F));
        PartDefinition tooth19 = teeth.addOrReplaceChild("tooth19", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.1051F, 0F));
        PartDefinition tooth20 = teeth.addOrReplaceChild("tooth20", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.236F, 0F));
        PartDefinition tooth21 = teeth.addOrReplaceChild("tooth21", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.6287F, 0F));
        PartDefinition tooth22 = teeth.addOrReplaceChild("tooth22", CubeListBuilder.create()
                .texOffs(399, 164).addBox(-1F, -12F, 8.5F, 2F, 4F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.7596F, 0F));
        PartDefinition tooth23 = teeth.addOrReplaceChild("tooth23", CubeListBuilder.create()
                .texOffs(421, 164).addBox(-1F, -6F, 6.5F, 2F, 3F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 6.1523F, 0F));
        PartDefinition tuskArm0 = head.addOrReplaceChild("tusk_arm0", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.3927F, 0F));
        PartDefinition tusk0 = tuskArm0.addOrReplaceChild("tusk0", CubeListBuilder.create()
                .texOffs(241, 164).addBox(-3F, -7F, -3F, 6F, 10F, 6F, new CubeDeformation(0F))
                .texOffs(467, 164).addBox(-3.5F, -1F, 2.5F, 7F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -15F, 16.5F, 0F, 0F, 0F));
        PartDefinition fangMid0 = tusk0.addOrReplaceChild("fang_mid0", CubeListBuilder.create()
                .texOffs(266, 164).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -7F, 0F, 0.3F, 0F, 0F));
        PartDefinition fangTip0 = fangMid0.addOrReplaceChild("fang_tip0", CubeListBuilder.create()
                .texOffs(363, 164).addBox(-2F, -6F, -2F, 4F, 6F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -8F, 0F, 0.34F, 0F, 0F));
        PartDefinition fangPoint0 = fangTip0.addOrReplaceChild("fang_point0", CubeListBuilder.create()
                .texOffs(495, 164).addBox(-1F, -4F, -1F, 2F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, 0F, 0.3F, 0F, 0F));
        PartDefinition tuskArm1 = head.addOrReplaceChild("tusk_arm1", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.1781F, 0F));
        PartDefinition tusk1 = tuskArm1.addOrReplaceChild("tusk1", CubeListBuilder.create()
                .texOffs(241, 164).addBox(-3F, -7F, -3F, 6F, 10F, 6F, new CubeDeformation(0F))
                .texOffs(467, 164).addBox(-3.5F, -1F, 2.5F, 7F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -15F, 16.5F, 0F, 0F, 0F));
        PartDefinition fangMid1 = tusk1.addOrReplaceChild("fang_mid1", CubeListBuilder.create()
                .texOffs(266, 164).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -7F, 0F, 0.3F, 0F, 0F));
        PartDefinition fangTip1 = fangMid1.addOrReplaceChild("fang_tip1", CubeListBuilder.create()
                .texOffs(363, 164).addBox(-2F, -6F, -2F, 4F, 6F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -8F, 0F, 0.34F, 0F, 0F));
        PartDefinition fangPoint1 = fangTip1.addOrReplaceChild("fang_point1", CubeListBuilder.create()
                .texOffs(495, 164).addBox(-1F, -4F, -1F, 2F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, 0F, 0.3F, 0F, 0F));
        PartDefinition tuskArm2 = head.addOrReplaceChild("tusk_arm2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.9635F, 0F));
        PartDefinition tusk2 = tuskArm2.addOrReplaceChild("tusk2", CubeListBuilder.create()
                .texOffs(241, 164).addBox(-3F, -7F, -3F, 6F, 10F, 6F, new CubeDeformation(0F))
                .texOffs(467, 164).addBox(-3.5F, -1F, 2.5F, 7F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -15F, 16.5F, 0F, 0F, 0F));
        PartDefinition fangMid2 = tusk2.addOrReplaceChild("fang_mid2", CubeListBuilder.create()
                .texOffs(266, 164).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -7F, 0F, 0.3F, 0F, 0F));
        PartDefinition fangTip2 = fangMid2.addOrReplaceChild("fang_tip2", CubeListBuilder.create()
                .texOffs(363, 164).addBox(-2F, -6F, -2F, 4F, 6F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -8F, 0F, 0.34F, 0F, 0F));
        PartDefinition fangPoint2 = fangTip2.addOrReplaceChild("fang_point2", CubeListBuilder.create()
                .texOffs(495, 164).addBox(-1F, -4F, -1F, 2F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, 0F, 0.3F, 0F, 0F));
        PartDefinition tuskArm3 = head.addOrReplaceChild("tusk_arm3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 2.7489F, 0F));
        PartDefinition tusk3 = tuskArm3.addOrReplaceChild("tusk3", CubeListBuilder.create()
                .texOffs(241, 164).addBox(-3F, -7F, -3F, 6F, 10F, 6F, new CubeDeformation(0F))
                .texOffs(467, 164).addBox(-3.5F, -1F, 2.5F, 7F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -15F, 16.5F, 0F, 0F, 0F));
        PartDefinition fangMid3 = tusk3.addOrReplaceChild("fang_mid3", CubeListBuilder.create()
                .texOffs(266, 164).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -7F, 0F, 0.3F, 0F, 0F));
        PartDefinition fangTip3 = fangMid3.addOrReplaceChild("fang_tip3", CubeListBuilder.create()
                .texOffs(363, 164).addBox(-2F, -6F, -2F, 4F, 6F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -8F, 0F, 0.34F, 0F, 0F));
        PartDefinition fangPoint3 = fangTip3.addOrReplaceChild("fang_point3", CubeListBuilder.create()
                .texOffs(495, 164).addBox(-1F, -4F, -1F, 2F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, 0F, 0.3F, 0F, 0F));
        PartDefinition tuskArm4 = head.addOrReplaceChild("tusk_arm4", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 3.5343F, 0F));
        PartDefinition tusk4 = tuskArm4.addOrReplaceChild("tusk4", CubeListBuilder.create()
                .texOffs(241, 164).addBox(-3F, -7F, -3F, 6F, 10F, 6F, new CubeDeformation(0F))
                .texOffs(467, 164).addBox(-3.5F, -1F, 2.5F, 7F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -15F, 16.5F, 0F, 0F, 0F));
        PartDefinition fangMid4 = tusk4.addOrReplaceChild("fang_mid4", CubeListBuilder.create()
                .texOffs(266, 164).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -7F, 0F, 0.3F, 0F, 0F));
        PartDefinition fangTip4 = fangMid4.addOrReplaceChild("fang_tip4", CubeListBuilder.create()
                .texOffs(363, 164).addBox(-2F, -6F, -2F, 4F, 6F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -8F, 0F, 0.34F, 0F, 0F));
        PartDefinition fangPoint4 = fangTip4.addOrReplaceChild("fang_point4", CubeListBuilder.create()
                .texOffs(495, 164).addBox(-1F, -4F, -1F, 2F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, 0F, 0.3F, 0F, 0F));
        PartDefinition tuskArm5 = head.addOrReplaceChild("tusk_arm5", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 4.3197F, 0F));
        PartDefinition tusk5 = tuskArm5.addOrReplaceChild("tusk5", CubeListBuilder.create()
                .texOffs(241, 164).addBox(-3F, -7F, -3F, 6F, 10F, 6F, new CubeDeformation(0F))
                .texOffs(467, 164).addBox(-3.5F, -1F, 2.5F, 7F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -15F, 16.5F, 0F, 0F, 0F));
        PartDefinition fangMid5 = tusk5.addOrReplaceChild("fang_mid5", CubeListBuilder.create()
                .texOffs(266, 164).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -7F, 0F, 0.3F, 0F, 0F));
        PartDefinition fangTip5 = fangMid5.addOrReplaceChild("fang_tip5", CubeListBuilder.create()
                .texOffs(363, 164).addBox(-2F, -6F, -2F, 4F, 6F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -8F, 0F, 0.34F, 0F, 0F));
        PartDefinition fangPoint5 = fangTip5.addOrReplaceChild("fang_point5", CubeListBuilder.create()
                .texOffs(495, 164).addBox(-1F, -4F, -1F, 2F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, 0F, 0.3F, 0F, 0F));
        PartDefinition tuskArm6 = head.addOrReplaceChild("tusk_arm6", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.1051F, 0F));
        PartDefinition tusk6 = tuskArm6.addOrReplaceChild("tusk6", CubeListBuilder.create()
                .texOffs(241, 164).addBox(-3F, -7F, -3F, 6F, 10F, 6F, new CubeDeformation(0F))
                .texOffs(467, 164).addBox(-3.5F, -1F, 2.5F, 7F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -15F, 16.5F, 0F, 0F, 0F));
        PartDefinition fangMid6 = tusk6.addOrReplaceChild("fang_mid6", CubeListBuilder.create()
                .texOffs(266, 164).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -7F, 0F, 0.3F, 0F, 0F));
        PartDefinition fangTip6 = fangMid6.addOrReplaceChild("fang_tip6", CubeListBuilder.create()
                .texOffs(363, 164).addBox(-2F, -6F, -2F, 4F, 6F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -8F, 0F, 0.34F, 0F, 0F));
        PartDefinition fangPoint6 = fangTip6.addOrReplaceChild("fang_point6", CubeListBuilder.create()
                .texOffs(495, 164).addBox(-1F, -4F, -1F, 2F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, 0F, 0.3F, 0F, 0F));
        PartDefinition tuskArm7 = head.addOrReplaceChild("tusk_arm7", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 5.8905F, 0F));
        PartDefinition tusk7 = tuskArm7.addOrReplaceChild("tusk7", CubeListBuilder.create()
                .texOffs(241, 164).addBox(-3F, -7F, -3F, 6F, 10F, 6F, new CubeDeformation(0F))
                .texOffs(467, 164).addBox(-3.5F, -1F, 2.5F, 7F, 4F, 1.5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -15F, 16.5F, 0F, 0F, 0F));
        PartDefinition fangMid7 = tusk7.addOrReplaceChild("fang_mid7", CubeListBuilder.create()
                .texOffs(266, 164).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -7F, 0F, 0.3F, 0F, 0F));
        PartDefinition fangTip7 = fangMid7.addOrReplaceChild("fang_tip7", CubeListBuilder.create()
                .texOffs(363, 164).addBox(-2F, -6F, -2F, 4F, 6F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -8F, 0F, 0.34F, 0F, 0F));
        PartDefinition fangPoint7 = fangTip7.addOrReplaceChild("fang_point7", CubeListBuilder.create()
                .texOffs(495, 164).addBox(-1F, -4F, -1F, 2F, 4F, 2F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, 0F, 0.3F, 0F, 0F));
        return LayerDefinition.create(mesh, 512, 512);
    }
}
