package fr.atraxterra.dohy.client.war;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.garrison.GarrisonRole;
import fr.atraxterra.dohy.faction.garrison.GarrisonSoldier;
import fr.atraxterra.dohy.faction.war.Soldier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;

/**
 * Un soldat de ta garnison : le corps et les visages des soldats des nations ({@link SoldierRenderer}),
 * le cuir teint a la banniere de ta nation, l'armure de l'equipement achete. L'archer qui vise leve
 * son arc.
 */
public class GarrisonSoldierRenderer
        extends HumanoidMobRenderer<GarrisonSoldier, SoldierRenderState, HumanoidModel<SoldierRenderState>> {

    private static final Identifier[] TEXTURES = new Identifier[Soldier.VARIANTS];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/entity/soldier/soldier_" + i + ".png");
        }
    }

    public GarrisonSoldierRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(),
                HumanoidModel::new), context.getEquipmentRenderer()));
    }

    @Override
    public SoldierRenderState createRenderState() {
        return new SoldierRenderState();
    }

    @Override
    public void extractRenderState(GarrisonSoldier soldier, SoldierRenderState state, float partialTick) {
        super.extractRenderState(soldier, state, partialTick);
        state.variant = soldier.variant();
        state.role = soldier.role().ordinal();
        if (soldier.role() == GarrisonRole.ARCHER && soldier.isAggressive() && soldier.getMainHandItem().is(Items.BOW)) {
            if (soldier.getMainArm() == HumanoidArm.RIGHT) {
                state.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            } else {
                state.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
        }
    }

    @Override
    public Identifier getTextureLocation(SoldierRenderState state) {
        return TEXTURES[Math.floorMod(state.variant, TEXTURES.length)];
    }
}
