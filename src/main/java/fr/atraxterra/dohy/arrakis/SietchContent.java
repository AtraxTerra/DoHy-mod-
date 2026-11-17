package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce que gardent les sietchs : leurs habitants, et ce qu'ils echangent contre l'eau et
 * l'epice — le paracompas, la grande gourde, le distille de sietch, le krys taille dans une
 * dent de ver, le cafe d'epice.
 */
public final class SietchContent {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DoHy.MOD_ID);

    /** Un habitant : jamais d'apparition naturelle, seuls les sietchs les installent. */
    public static final DeferredHolder<EntityType<?>, EntityType<SietchDweller>> DWELLER = ENTITIES.register(
            "sietch_dweller", key -> EntityType.Builder.<SietchDweller>of(SietchDweller::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    /** Le paracompas : sur Arrakis, son aiguille montre le sietch le plus proche. */
    public static final DeferredItem<ParacompassItem> PARACOMPASS = ITEMS.registerItem("paracompass",
            ParacompassItem::new, properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, lore("tooltip.dohy.paracompass.1", "tooltip.dohy.paracompass.2")));

    /** La gourde de sietch : seize gorgees. */
    public static final DeferredItem<CanteenItem> CANTEEN = ITEMS.registerItem("sietch_canteen",
            properties -> new CanteenItem(properties, CanteenItem.SIETCH_CAPACITY), properties -> properties.stacksTo(1)
                    .rarity(Rarity.RARE)
                    .component(DataComponents.LORE, lore("tooltip.dohy.sietch_canteen.1", "tooltip.dohy.canteen.1")));

    /** Le distille cousu a nouveau par les tisserands d'un sietch : il garde davantage l'eau du corps. */
    public static final ArmorMaterial SIETCH_MATERIAL = new ArmorMaterial(ArmorMaterials.LEATHER.durability() * 5,
            ArmorMaterials.LEATHER.defense(), ArmorMaterials.LEATHER.enchantmentValue() + 3,
            SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f, ArmorMaterials.LEATHER.repairIngredient(),
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "sietch_stillsuit")));
    public static final DeferredItem<Item> SIETCH_MASK = suit("sietch_mask", ArmorType.HELMET);
    public static final DeferredItem<Item> SIETCH_SUIT = suit("sietch_suit", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> SIETCH_LEGGINGS = suit("sietch_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> SIETCH_BOOTS = suit("sietch_boots", ArmorType.BOOTS);

    /** Ce qui repare un krys : une dent de ver. */
    public static final TagKey<Item> KRYS_REPAIR = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "krys_repair"));
    /** La matiere du krys : la dent d'un ver, plus dure que le diamant, et qui garde son fil. */
    public static final ToolMaterial KRYS_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1800, 8.0f,
            4.0f, 18, KRYS_REPAIR);
    /** Le krys : huit de degats, plus vif qu'une epee. */
    public static final DeferredItem<Item> KRYS = ITEMS.registerItem("krys", properties -> new Item(properties
            .sword(KRYS_MATERIAL, 3.0f, -1.8f)
            .rarity(Rarity.EPIC)
            .component(DataComponents.LORE, lore("tooltip.dohy.krys.1", "tooltip.dohy.krys.2"))));

    /** Le cafe d'epice : une gorgee d'eau, et les sens aiguises un moment. */
    public static final DeferredItem<SpiceCoffeeItem> SPICE_COFFEE = ITEMS.registerItem("spice_coffee",
            SpiceCoffeeItem::new, properties -> properties.stacksTo(16).rarity(Rarity.UNCOMMON)
                    .food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2f).alwaysEdible().build(),
                            Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                                    new MobEffectInstance(MobEffects.NIGHT_VISION, 1800, 0),
                                    new MobEffectInstance(MobEffects.HASTE, 2400, 1),
                                    new MobEffectInstance(MobEffects.SPEED, 1200, 0)))).build())
                    .component(DataComponents.LORE, lore("tooltip.dohy.spice_coffee.1", "tooltip.dohy.spice_coffee.2")));

    private SietchContent() {
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(SietchContent::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DWELLER.get(), SietchDweller.createAttributes().build());
    }

    /** Le piege a vent qu'on pose au sommet d'un sietch, vide. */
    static BlockState windtrap() {
        return ArrakisContent.WINDTRAP.get().defaultBlockState();
    }

    /** Vrai pour une piece du distille de sietch. */
    static boolean isSietchPiece(Item item) {
        return item == SIETCH_MASK.get() || item == SIETCH_SUIT.get() || item == SIETCH_LEGGINGS.get()
                || item == SIETCH_BOOTS.get();
    }

    private static DeferredItem<Item> suit(String name, ArmorType type) {
        return ITEMS.registerItem(name, properties -> new Item(properties.humanoidArmor(SIETCH_MATERIAL, type)
                .rarity(Rarity.RARE)
                .component(DataComponents.LORE, lore("tooltip.dohy.sietch_stillsuit.1", "tooltip.dohy." + name))));
    }

    private static ItemLore lore(String... keys) {
        List<Component> lines = new ArrayList<>();
        for (String key : keys) {
            lines.add(Component.translatable(key)
                    .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
        }
        return new ItemLore(lines);
    }
}
