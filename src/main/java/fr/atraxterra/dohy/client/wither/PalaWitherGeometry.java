package fr.atraxterra.dohy.client.wither;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.wither.WitherKind;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Les silhouettes des withers du pillage, d'apres les rendus du wiki
 * (« Réceptacle à Wither »). Chacune reprend le squelette du wither vanilla
 * (memes noms de pieces, pour que {@code WitherBossModel} l'anime) et y ajoute
 * ses pieces :
 * <ul>
 *   <li>predateur : deux cornes en L sur la tete centrale ;</li>
 *   <li>supreme : des epines sur les trois tetes et deux grands bras griffus
 *       qui pendent du bout des epaules ;</li>
 *   <li>assoiffe : des fleches plantees dans les cranes et les cotes ;</li>
 *   <li>passif : des pousses d'herbe sur les tetes.</li>
 * </ul>
 * Le baby garde la silhouette vanilla (l'attribut d'echelle le rapetisse) et
 * n'a que sa peau. Les pieces ajoutees lisent la texture 64 x 64 a partir de
 * la ligne 36 (au-dessus, la colonne vertebrale du vanilla descend jusqu'a
 * la ligne 35), peinte par {@code tools/wither_skins.py}.
 */
public final class PalaWitherGeometry {

    private static final Set<WitherKind> SKINNED = EnumSet.of(WitherKind.BABY, WitherKind.PASSIVE,
            WitherKind.THIRSTY, WitherKind.PREDATOR, WitherKind.SUPREME);
    private static final Set<WitherKind> GLOWING = EnumSet.of(WitherKind.BABY, WitherKind.PREDATOR,
            WitherKind.SUPREME);
    private static final Map<WitherKind, ModelLayerLocation> LAYERS = new EnumMap<>(WitherKind.class);

    static {
        for (WitherKind kind : new WitherKind[]{WitherKind.PASSIVE, WitherKind.THIRSTY, WitherKind.PREDATOR,
                WitherKind.SUPREME}) {
            LAYERS.put(kind, new ModelLayerLocation(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, kind.id()),
                    "main"));
        }
    }

    private PalaWitherGeometry() {
    }

    /** La couche de modele du genre, ou {@code null} s'il garde la silhouette vanilla. */
    public static ModelLayerLocation layer(WitherKind kind) {
        return LAYERS.get(kind);
    }

    /** A-t-il sa propre peau ({@code textures/entity/wither/<id>.png}) ? */
    public static boolean skinned(WitherKind kind) {
        return SKINNED.contains(kind);
    }

    /** A-t-il des yeux lumineux ({@code <id>_eyes.png}) ? */
    public static boolean glowing(WitherKind kind) {
        return GLOWING.contains(kind);
    }

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions event) {
        LAYERS.forEach((kind, layer) -> event.registerLayerDefinition(layer, () -> create(kind)));
    }

    static LayerDefinition create(WitherKind kind) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Le squelette du wither vanilla, a l'identique.
        root.addOrReplaceChild("shoulders",
                CubeListBuilder.create().texOffs(0, 16).addBox(-10F, 3.9F, -0.5F, 20F, 3F, 3F), PartPose.ZERO);
        float rib = 0.20420352F;
        PartDefinition ribcage = root.addOrReplaceChild("ribcage", CubeListBuilder.create()
                        .texOffs(0, 22).addBox(0F, 0F, 0F, 3F, 10F, 3F)
                        .texOffs(24, 22).addBox(-4F, 1.5F, 0.5F, 11F, 2F, 2F)
                        .texOffs(24, 22).addBox(-4F, 4F, 0.5F, 11F, 2F, 2F)
                        .texOffs(24, 22).addBox(-4F, 6.5F, 0.5F, 11F, 2F, 2F),
                PartPose.offsetAndRotation(-2F, 6.9F, -0.5F, rib, 0F, 0F));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(12, 22).addBox(0F, 0F, 0F, 3F, 6F, 3F),
                PartPose.offsetAndRotation(-2F, 6.9F + Mth.cos(rib) * 10F, -0.5F + Mth.sin(rib) * 10F,
                        0.83252203F, 0F, 0F));
        PartDefinition center = root.addOrReplaceChild("center_head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -4F, -4F, 8F, 8F, 8F), PartPose.ZERO);
        PartDefinition right = root.addOrReplaceChild("right_head",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4F, -4F, -4F, 6F, 6F, 6F), PartPose.offset(-8F, 4F, 0F));
        PartDefinition left = root.addOrReplaceChild("left_head",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4F, -4F, -4F, 6F, 6F, 6F), PartPose.offset(10F, 4F, 0F));

        switch (kind) {
            case PREDATOR -> {
                horn(center, "right_horn", -1);
                horn(center, "left_horn", 1);
            }
            case SUPREME -> {
                center.addOrReplaceChild("spikes", CubeListBuilder.create()
                        .texOffs(44, 36).addBox(-3.5F, -8F, -0.5F, 1F, 5F, 1F)
                        .texOffs(44, 36).addBox(-0.5F, -8.5F, -2.5F, 1F, 5F, 1F)
                        .texOffs(44, 36).addBox(2.5F, -8F, -0.5F, 1F, 5F, 1F), PartPose.ZERO);
                for (PartDefinition head : new PartDefinition[]{right, left}) {
                    head.addOrReplaceChild("spikes", CubeListBuilder.create()
                            .texOffs(44, 36).addBox(-1.5F, -8F, -1.5F, 1F, 5F, 1F), PartPose.ZERO);
                }
                arm(root, "right_arm", -1);
                arm(root, "left_arm", 1);
            }
            case THIRSTY -> {
                arrow(center, "arrow_0", -1.5F, -4F, 1F, 0.35F, -0.3F);
                arrow(center, "arrow_1", 2F, -4F, -1.5F, -0.4F, 0.45F);
                arrow(right, "arrow_2", -1F, -4F, -1F, 0.3F, 0.5F);
                arrow(ribcage, "arrow_3", 1.5F, 4F, 0.5F, 1.3F, 0F);
                arrow(ribcage, "arrow_4", 5F, 2.5F, 1.5F, 0.9F, -0.3F);
            }
            case PASSIVE -> {
                center.addOrReplaceChild("sprouts", CubeListBuilder.create()
                        .texOffs(0, 36).addBox(-2.5F, -7F, -1.5F, 1F, 3F, 1F)
                        .texOffs(4, 36).addBox(1F, -8F, 0.5F, 1F, 4F, 1F)
                        .texOffs(8, 36).addBox(-0.5F, -6F, 2F, 1F, 2F, 1F)
                        .texOffs(4, 36).addBox(2.5F, -8F, -2.5F, 1F, 4F, 1F), PartPose.ZERO);
                for (PartDefinition head : new PartDefinition[]{right, left}) {
                    head.addOrReplaceChild("sprouts", CubeListBuilder.create()
                            .texOffs(0, 36).addBox(-2.5F, -7F, -2.5F, 1F, 3F, 1F)
                            .texOffs(8, 36).addBox(0F, -6F, 0F, 1F, 2F, 1F), PartPose.ZERO);
                }
            }
            default -> {
            }
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Une corne en L : un moignon qui sort du coin du crane, un fut dresse, une pointe. */
    private static void horn(PartDefinition head, String name, int side) {
        float stub = side > 0 ? -1F : -2F;
        float shaft = side > 0 ? 0F : -2F;
        float tip = side > 0 ? 0.5F : -1.5F;
        head.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(8, 36).addBox(stub, -1F, -1F, 3F, 2F, 2F)
                        .texOffs(0, 36).addBox(shaft, -7F, -1F, 2F, 6F, 2F)
                        .texOffs(20, 36).addBox(tip, -10F, -0.5F, 1F, 3F, 1F),
                PartPose.offsetAndRotation(side * 5F, -2F, 0F, 0F, 0F, side * 0.2F));
    }

    /** Un bras du supreme : epaulette epineuse, bras, avant-bras tendu vers l'avant, trois griffes. */
    private static void arm(PartDefinition root, String name, int side) {
        PartDefinition arm = root.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(16, 36).addBox(-2F, -1F, -2F, 4F, 12F, 4F)
                        .texOffs(0, 52).addBox(-2.5F, -3.5F, -2.5F, 5F, 5F, 5F)
                        .texOffs(44, 36).addBox(-1.5F, -8.5F, -0.5F, 1F, 5F, 1F)
                        .texOffs(44, 36).addBox(0.5F, -7.5F, -1.5F, 1F, 5F, 1F),
                PartPose.offsetAndRotation(side * 11.5F, 5.4F, 1F, PalaWitherModel.ARM_PITCH, 0F,
                        -side * PalaWitherModel.ARM_SPREAD));
        arm.addOrReplaceChild("forearm", CubeListBuilder.create()
                        .texOffs(32, 36).addBox(-1.5F, 0F, -1.5F, 3F, 12F, 3F)
                        .texOffs(48, 36).addBox(-1.5F, 11.5F, -1.5F, 1F, 4F, 1F)
                        .texOffs(48, 36).addBox(0.5F, 11.5F, -1.5F, 1F, 4F, 1F)
                        .texOffs(48, 36).addBox(-0.5F, 11.5F, 0.5F, 1F, 4F, 1F),
                PartPose.offsetAndRotation(0F, 10.5F, 0F, -0.45F, 0F, 0F));
    }

    /** Une fleche plantee : le fut monte de 6, la pointe s'enfonce de 2. */
    private static void arrow(PartDefinition part, String name, float x, float y, float z, float pitch, float roll) {
        part.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 36).addBox(-0.5F, -6F, -0.5F, 1F, 8F, 1F),
                PartPose.offsetAndRotation(x, y, z, pitch, 0F, roll));
    }
}
