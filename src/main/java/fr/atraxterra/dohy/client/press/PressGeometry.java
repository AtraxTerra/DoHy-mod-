package fr.atraxterra.dohy.client.press;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * GENERE par tools/press_assets.py. Ne pas modifier a la main : relancer le script.
 *
 * <p>Les modeles de la presse a imprimer et de la table d'ecriture (PalaMod 10.3.0),
 * boite par boite, sur leurs textures d'origine. Repere des modeles d'entite : y vers
 * le bas. Toutes les boites sont en miroir, comme dans l'original.
 */
@SuppressWarnings("unused")
public final class PressGeometry {

    /** {@code ModelPrintPress}. */
    public static final ModelLayerLocation PRESS = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("palamod", "print_press"), "main");
    /** {@code ModelInkPlate}. */
    public static final ModelLayerLocation INK_PLATE = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("palamod", "print_press"), "ink");
    /** {@code ModelPrintPressBooks}. */
    public static final ModelLayerLocation PRESS_BOOKS = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("palamod", "print_press"), "books");
    /** {@code ModelTypeset}. */
    public static final ModelLayerLocation TYPESET = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("palamod", "type_machine"), "main");
    /** {@code ModelTypesetBase}. */
    public static final ModelLayerLocation TYPESET_BASE = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("palamod", "type_machine"), "base");
    /** {@code ModelTypesetBook}. */
    public static final ModelLayerLocation TYPESET_BOOK = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("palamod", "type_machine"), "book");

    public static final String[] PRESS_MAIN = {"LLeg", "RLeg", "frontbrace", "bottombrace", "backbrace", "pibrace", "pback", "table", "irshaft"};
    public static final String[] TYPESET_TOP = {"topedge", "buttomedge", "mid1edge", "mid2edge", "leftedge", "rightedge", "bcedge", "m2edge", "m3edge", "m4edge", "m1edge", "trcedge", "tlcedge", "mcb1edge", "mcb2edge", "mcb3edge", "mcb4edge", "tl1edge", "tl2edge", "tr1edge", "tr2edge", "tlmc2edge", "tlmc1edge", "trmc2edge", "trmc1edge"};
    public static final String[] EMPTY_BOOKS = {"Bbook1", "Bbook2", "Bbook3", "Bbook4"};
    public static final String[][] CHASE_LAYERS = {{"tlc1", "trc1", "tbc1", "ttc1"}, {"tlc2", "trc2", "tbc2", "ttc2"}, {"tlc3", "trc3", "tbc3", "ttc3"}, {"tlc4", "trc4", "tbc4", "ttc4"}};

    /** Echelle de rendu de la presse ({@code render(0.05F)}), rapportee au 1/16 des modeles vanilla. */
    public static final float PRESS_SCALE = 0.8F;
    /** Echelle de rendu de la table ({@code render(1/48F)}). */
    public static final float TYPESET_SCALE = 0.3333F;

    private PressGeometry() {
    }

    public static LayerDefinition createPressLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("LLeg", CubeListBuilder.create().mirror()
                .texOffs(31, 28).addBox(-9F, 4F, -8F, 3F, 19F, 16F),
                PartPose.offsetAndRotation(0F, 0F, -3F, 0F, 0F, 0F));
        root.addOrReplaceChild("RLeg", CubeListBuilder.create().mirror()
                .texOffs(31, 28).addBox(6F, 4F, -8F, 3F, 19F, 16F),
                PartPose.offsetAndRotation(0F, 0F, -3F, 0F, 0F, 0F));
        root.addOrReplaceChild("frontbrace", CubeListBuilder.create().mirror()
                .texOffs(95, 34).addBox(-6F, 8F, -10F, 12F, 11F, 4F),
                PartPose.offsetAndRotation(0F, 2F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("bottombrace", CubeListBuilder.create().mirror()
                .texOffs(89, 50).addBox(-6F, 12F, -2F, 12F, 7F, 7F),
                PartPose.offsetAndRotation(0F, 2F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("backbrace", CubeListBuilder.create().mirror()
                .texOffs(62, 34).addBox(-6F, 10F, 1F, 12F, 2F, 4F),
                PartPose.offsetAndRotation(0F, 2F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("pibrace", CubeListBuilder.create().mirror()
                .texOffs(37, 0).addBox(-1F, 1F, 1F, 2F, 6F, 4F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("pback", CubeListBuilder.create().mirror()
                .texOffs(73, 0).addBox(-5F, 2F, 0F, 10F, 12F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("table", CubeListBuilder.create().mirror()
                .texOffs(70, 24).addBox(-10F, 0F, -13F, 20F, 1F, 8F),
                PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("irshaft", CubeListBuilder.create().mirror()
                .texOffs(2, 42).addBox(-5F, 0F, 0F, 10F, 1F, 1F),
                PartPose.offsetAndRotation(0F, 4F, 5F, 0F, 0F, 0F));
        root.addOrReplaceChild("plate", CubeListBuilder.create().mirror()
                .texOffs(77, 14).addBox(-4F, 3F, -1F, 8F, 8F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("press", CubeListBuilder.create().mirror()
                .texOffs(50, 0).addBox(-5F, -14F, 0.5F, 10F, 10F, 1F)
                .texOffs(50, 12).addBox(-4F, -12F, -2.5F, 8F, 8F, 3F),
                PartPose.offsetAndRotation(0F, 15.5F, 1F, 0F, 0F, 0F));
        root.addOrReplaceChild("inkRollers", CubeListBuilder.create().mirror()
                .texOffs(9, 22).addBox(-6F, -7F, -2.5F, 1F, 1F, 4F)
                .texOffs(9, 22).addBox(5F, -7F, -2.5F, 1F, 1F, 4F)
                .texOffs(2, 22).addBox(5F, -6F, -1.5F, 1F, 10F, 2F)
                .texOffs(2, 22).addBox(-6F, -6F, -1.5F, 1F, 10F, 2F)
                .texOffs(1, 35).addBox(-5F, 2F, -2F, 10F, 3F, 3F)
                .texOffs(3, 19).addBox(-5F, -7F, 0F, 10F, 1F, 1F)
                .texOffs(3, 19).addBox(-5F, -7F, -2F, 10F, 1F, 1F),
                PartPose.offsetAndRotation(0F, 4F, 6F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition createInkPlateLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("inkplate", CubeListBuilder.create().mirror()
                .texOffs(0, 0).addBox(-3.5F, 0F, 0.5F, 7F, 1F, 7F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    public static LayerDefinition createPressBooksLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("Bbook1", CubeListBuilder.create().mirror()
                .texOffs(0, 0).addBox(-9F, -2F, -14F, 6F, 2F, 9F),
                PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("Bbook2", CubeListBuilder.create().mirror()
                .texOffs(0, 0).addBox(-10F, -4F, -13F, 6F, 2F, 9F),
                PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("Bbook3", CubeListBuilder.create().mirror()
                .texOffs(0, 0).addBox(-11F, -6F, -13F, 6F, 2F, 9F),
                PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("Bbook4", CubeListBuilder.create().mirror()
                .texOffs(0, 0).addBox(-9F, -8F, -14F, 6F, 2F, 9F),
                PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("Fbook", CubeListBuilder.create().mirror()
                .texOffs(0, 12).addBox(3F, -2F, -14F, 6F, 2F, 9F),
                PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    public static LayerDefinition createTypesetLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("topedge", CubeListBuilder.create().mirror()
                .texOffs(0, 57).addBox(-24F, 0F, 24F, 48F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("buttomedge", CubeListBuilder.create().mirror()
                .texOffs(0, 57).addBox(-24F, 0F, -23F, 48F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("mid1edge", CubeListBuilder.create().mirror()
                .texOffs(0, 57).addBox(-23F, 0F, -4F, 46F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("mid2edge", CubeListBuilder.create().mirror()
                .texOffs(0, 57).addBox(-23F, 0F, 5F, 46F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("leftedge", CubeListBuilder.create().mirror()
                .texOffs(0, 8).addBox(-24F, 0F, -22F, 1F, 2F, 46F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("rightedge", CubeListBuilder.create().mirror()
                .texOffs(0, 8).addBox(23F, 0F, -22F, 1F, 2F, 46F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("bcedge", CubeListBuilder.create().mirror()
                .texOffs(4, 22).addBox(-0.5F, 0F, -22F, 1F, 2F, 18F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("m2edge", CubeListBuilder.create().mirror()
                .texOffs(74, 42).addBox(-3F, 0F, -3F, 1F, 2F, 8F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("m3edge", CubeListBuilder.create().mirror()
                .texOffs(74, 42).addBox(2F, 0F, -3F, 1F, 2F, 8F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("m4edge", CubeListBuilder.create().mirror()
                .texOffs(74, 42).addBox(7F, 0F, -3F, 1F, 2F, 8F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("m1edge", CubeListBuilder.create().mirror()
                .texOffs(74, 42).addBox(-8F, 0F, -3F, 1F, 2F, 8F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("trcedge", CubeListBuilder.create().mirror()
                .texOffs(4, 22).addBox(9F, 0F, 6F, 1F, 2F, 18F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tlcedge", CubeListBuilder.create().mirror()
                .texOffs(4, 22).addBox(-10F, 0F, 6F, 1F, 2F, 18F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("mcb1edge", CubeListBuilder.create().mirror()
                .texOffs(7, 47).addBox(-23F, 0F, -1F, 15F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("mcb2edge", CubeListBuilder.create().mirror()
                .texOffs(7, 47).addBox(-23F, 0F, 2F, 15F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("mcb3edge", CubeListBuilder.create().mirror()
                .texOffs(7, 47).addBox(8F, 0F, -1F, 15F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("mcb4edge", CubeListBuilder.create().mirror()
                .texOffs(7, 47).addBox(8F, 0F, 2F, 15F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tl1edge", CubeListBuilder.create().mirror()
                .texOffs(9, 43).addBox(-23F, 0F, 20F, 13F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tl2edge", CubeListBuilder.create().mirror()
                .texOffs(9, 43).addBox(-23F, 0F, 16F, 13F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tr1edge", CubeListBuilder.create().mirror()
                .texOffs(9, 43).addBox(10F, 0F, 20F, 13F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tr2edge", CubeListBuilder.create().mirror()
                .texOffs(9, 43).addBox(10F, 0F, 16F, 13F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tlmc2edge", CubeListBuilder.create().mirror()
                .texOffs(50, 40).addBox(-15F, 0F, 6F, 1F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tlmc1edge", CubeListBuilder.create().mirror()
                .texOffs(50, 40).addBox(-19F, 0F, 6F, 1F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("trmc2edge", CubeListBuilder.create().mirror()
                .texOffs(50, 40).addBox(18F, 0F, 6F, 1F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("trmc1edge", CubeListBuilder.create().mirror()
                .texOffs(50, 40).addBox(14F, 0F, 6F, 1F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tlc1", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(-7F, 0F, 10F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("trc1", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(5F, 0F, 10F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tbc1", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-5F, 0F, 10F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("ttc1", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-5F, 0F, 18F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tlc2", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(-8F, -2F, 11F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("trc2", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(4F, -2F, 11F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tbc2", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-6F, -2F, 11F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("ttc2", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-6F, -2F, 19F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tlc3", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(-6F, -4F, 11F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("trc3", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(6F, -4F, 11F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tbc3", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-4F, -4F, 11F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("ttc3", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-4F, -4F, 19F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tlc4", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(-7F, -6F, 10F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("trc4", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(5F, -6F, 10F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("tbc4", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-5F, -6F, 10F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("ttc4", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-5F, -6F, 18F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("blPlate", CubeListBuilder.create().mirror()
                .texOffs(50, 0).addBox(-19F, 0F, -18F, 14F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("brPlate", CubeListBuilder.create().mirror()
                .texOffs(50, 0).addBox(5F, 0F, -18F, 14F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("blcRight", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(-7F, 0F, -18F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("blcLeft", CubeListBuilder.create().mirror()
                .texOffs(51, 20).addBox(-19F, 0F, -18F, 2F, 2F, 10F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("blcTop", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-17F, 0F, -10F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        root.addOrReplaceChild("blcBottom", CubeListBuilder.create().mirror()
                .texOffs(74, 26).addBox(-17F, 0F, -18F, 10F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition createTypesetBaseLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().mirror()
                .texOffs(0, 0).addBox(-24F, 2F, -23F, 48F, 42F, 48F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 256, 128);
    }

    public static LayerDefinition createTypesetBookLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("book", CubeListBuilder.create().mirror()
                .texOffs(0, 0).addBox(-22.5F, -3F, -20.5F, 21F, 5F, 15F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 32);
    }
}
