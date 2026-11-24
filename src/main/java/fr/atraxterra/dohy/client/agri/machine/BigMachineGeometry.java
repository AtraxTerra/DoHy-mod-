package fr.atraxterra.dohy.client.agri.machine;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * GENERE par tools/agri_machine_assets.py. Ne pas modifier a la main : relancer le script.
 *
 * <p>Les pieces mobiles des grandes machines de l'agriculture, en pixels du repere de la
 * machine (y vers le haut, la face avant au nord), chacune autour de son pivot.
 */
@SuppressWarnings("unused")
public final class BigMachineGeometry {

    /** Une piece mobile : son nom dans le modele, son axe, sa vitesse (degres par tick). */
    public record Spin(String part, char axis, float speed, boolean always, boolean glow) {
    }

    public static final ModelLayerLocation SILO = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("dohy", "machine_silo"), "main");
    public static final Spin[] SILO_SPINS = {new Spin("vent", 'y', 2.0F, true, false)};

    public static final ModelLayerLocation FLOUR_MILL = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("dohy", "machine_flour_mill"), "main");
    public static final Spin[] FLOUR_MILL_SPINS = {new Spin("runner", 'y', 4.0F, false, false)};

    public static final ModelLayerLocation BREAD_MACHINE = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("dohy", "machine_bread_machine"), "main");
    public static final Spin[] BREAD_MACHINE_SPINS = {new Spin("fire", 'y', 0.0F, false, true), new Spin("wheel", 'x', 6.0F, false, false)};

    public static final ModelLayerLocation BIOGENERATOR = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("dohy", "machine_biogenerator"), "main");
    public static final Spin[] BIOGENERATOR_SPINS = {new Spin("flywheel", 'x', 8.0F, false, false)};

    public static final ModelLayerLocation GMO_MACHINE = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("dohy", "machine_gmo_machine"), "main");
    public static final Spin[] GMO_MACHINE_SPINS = {new Spin("whisk", 'y', 5.0F, true, false)};

    private BigMachineGeometry() {
    }

    public static LayerDefinition createSilo() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("vent", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3F, 0F, -3F, 6F, 3F, 6F)
                .texOffs(24, 0).addBox(-8F, 1F, -1F, 16F, 1F, 2F)
                .texOffs(60, 0).addBox(-1F, 1F, -8F, 2F, 1F, 16F)
                , PartPose.offset(24F, 77F, 24F));
        return LayerDefinition.create(mesh, 128, 32);
    }

    public static LayerDefinition createFlourMill() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("runner", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-18F, 0F, -13F, 36F, 7F, 26F)
                .texOffs(0, 33).addBox(-13F, 0F, -18F, 26F, 7F, 36F)
                .texOffs(0, 76).addBox(-4F, 7F, -4F, 8F, 2F, 8F)
                .texOffs(32, 76).addBox(-2F, 9F, -2F, 4F, 21F, 4F)
                .texOffs(48, 76).addBox(-12F, 24F, -8F, 24F, 4F, 16F)
                .texOffs(0, 101).addBox(-8F, 24F, -12F, 16F, 4F, 24F)
                .texOffs(80, 101).addBox(-15F, 24F, -2F, 3F, 4F, 4F)
                .texOffs(94, 101).addBox(12F, 24F, -2F, 3F, 4F, 4F)
                .texOffs(108, 101).addBox(-2F, 24F, -15F, 4F, 4F, 3F)
                .texOffs(0, 129).addBox(-2F, 24F, 12F, 4F, 4F, 3F)
                .texOffs(14, 129).addBox(-3F, 23F, -3F, 6F, 6F, 6F)
                , PartPose.offset(24F, 20F, 24F));
        return LayerDefinition.create(mesh, 128, 256);
    }

    public static LayerDefinition createBreadMachine() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("fire", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7F, -3F, 0F, 14F, 12F, 1F)
                , PartPose.offset(24F, 16F, 3F));
        root.addOrReplaceChild("wheel", CubeListBuilder.create()
                .texOffs(30, 0).addBox(-1F, -6F, -3F, 2F, 12F, 6F)
                .texOffs(46, 0).addBox(-1F, -3F, -6F, 2F, 6F, 12F)
                .texOffs(74, 0).addBox(-1F, -8F, -1F, 2F, 16F, 2F)
                .texOffs(82, 0).addBox(-1F, 0F, -8F, 2F, 2F, 16F)
                .texOffs(0, 18).addBox(-2F, -2F, -2F, 4F, 4F, 4F)
                , PartPose.offset(46F, 20F, 25F));
        return LayerDefinition.create(mesh, 128, 32);
    }

    public static LayerDefinition createBiogenerator() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("flywheel", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2F, -2F, -2F, 3F, 4F, 4F)
                .texOffs(14, 0).addBox(-1F, -8F, -1F, 2F, 16F, 2F)
                .texOffs(22, 0).addBox(-1F, -1F, -8F, 2F, 2F, 16F)
                .texOffs(58, 0).addBox(-1F, 7F, -5F, 2F, 2F, 10F)
                .texOffs(82, 0).addBox(-1F, -9F, -5F, 2F, 2F, 10F)
                .texOffs(106, 0).addBox(-1F, -6F, -9F, 2F, 12F, 2F)
                .texOffs(114, 0).addBox(-1F, -6F, 7F, 2F, 12F, 2F)
                , PartPose.offset(47F, 17F, 13F));
        return LayerDefinition.create(mesh, 128, 32);
    }

    public static LayerDefinition createGmoMachine() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("whisk", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2F, 0F, -2F, 4F, 17F, 4F)
                .texOffs(16, 0).addBox(-10F, 0F, -1F, 20F, 3F, 2F)
                .texOffs(60, 0).addBox(-1F, 0F, -10F, 2F, 3F, 20F)
                .texOffs(0, 23).addBox(-8F, 5F, -1F, 16F, 2F, 2F)
                .texOffs(36, 23).addBox(-1F, 5F, -8F, 2F, 2F, 16F)
                , PartPose.offset(24F, 14F, 24F));
        return LayerDefinition.create(mesh, 128, 64);
    }
}
