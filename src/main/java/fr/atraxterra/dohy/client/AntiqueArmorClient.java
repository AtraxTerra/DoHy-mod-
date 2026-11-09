package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeonGear;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.function.Supplier;

/**
 * L'armure antique portee : chaque piece remplace le modele d'armure plat par
 * son modele 3D ({@link AntiqueArmorModels}) et sa texture.
 *
 * <p>NeoForge recopie sur le modele rendu les poses et la visibilite du
 * modele d'armure d'origine ({@code ClientHooks.copyModelProperties}) : la
 * piece suit les mouvements du joueur et ne montre que ses parties.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AntiqueArmorClient {

    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
            "textures/entity/antique_armor.png");

    private AntiqueArmorClient() {
    }

    @SubscribeEvent
    public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        register(event, PalaDungeonGear.ANTIQUE_HELMET.get(), AntiqueArmorModels::helmet);
        register(event, PalaDungeonGear.ANTIQUE_CHESTPLATE.get(), AntiqueArmorModels::chestplate);
        register(event, PalaDungeonGear.ANTIQUE_LEGGINGS.get(), AntiqueArmorModels::leggings);
        register(event, PalaDungeonGear.ANTIQUE_BOOTS.get(), AntiqueArmorModels::boots);
    }

    private static void register(RegisterClientExtensionsEvent event, Item item, Supplier<LayerDefinition> layer) {
        event.registerItem(new IClientItemExtensions() {
            private HumanoidModel<HumanoidRenderState> model;

            @Override
            public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType type, Model original) {
                if (model == null) {
                    model = new HumanoidModel<>(layer.get().bakeRoot());
                }
                return model;
            }

            @Override
            public Identifier getArmorTexture(ItemStack stack, EquipmentClientInfo.LayerType type,
                                              EquipmentClientInfo.Layer layerInfo, Identifier fallback) {
                return TEXTURE;
            }
        }, item);
    }
}
