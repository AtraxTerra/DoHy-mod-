package fr.atraxterra.dohy.palamod.godpickaxe;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * La pioche des dieux ({@code ItemsRegister.GOD_PICKAXE}) et son amelioration
 * de fusion ({@code GOD_PICKAXE_AUTOSMELT}).
 *
 * <p>Aucun metier ne la verrouille ni ne la recompense, dans PalaMod comme
 * dans PalaJobs : elle se fabrique des le depart, avec les trois pioches.
 */
public final class PalaGodPickaxe {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GodPickaxeData>> DATA =
            COMPONENTS.registerComponentType("god_pickaxe", builder -> builder
                    .persistent(GodPickaxeData.CODEC).networkSynchronized(GodPickaxeData.STREAM_CODEC));

    /**
     * 8 de degats, quel que soit le niveau : 2 pour la pioche, 6 pour le
     * materiau paladium du constructeur. La vitesse d'attaque est celle des
     * pioches vanilla.
     */
    public static final DeferredItem<GodPickaxeItem> GOD_PICKAXE = ITEMS.registerItem(
            "god_pickaxe", properties -> new GodPickaxeItem(properties
                    .stacksTo(1)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                                    Identifier.withDefaultNamespace("base_attack_damage"), 7.0d,
                                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED, new AttributeModifier(
                                    Identifier.withDefaultNamespace("base_attack_speed"), -2.8d,
                                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                            .build())));

    public static final DeferredItem<Item> AUTO_SMELT = ITEMS.registerItem("god_pickaxe_auto_smelt", Item::new);

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(GOD_PICKAXE, AUTO_SMELT);

    private PalaGodPickaxe() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
    }
}
