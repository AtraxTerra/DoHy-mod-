package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * L'agriculture avancee de DoHy, sous l'espace de noms {@code dohy} et dans son
 * propre onglet — pas dans celui de PalaMod : rien ici ne vient de Paladium.
 *
 * <p>Reprise de NationsGlory : des cereales (huit, plus le ble du jeu), dont
 * trois OGM qui ne sortent que de la machine a OGM ; un pain par cereale, a
 * effet ; et un elevage a nourrir, mangeoire et abreuvoir, dont les produits
 * entrent dans les pains d'OGM.
 */
public final class DoHyAgri {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DoHy.MOD_ID);

    /** Tout ce qu'une mangeoire accepte : les grains d'ici et le ble. */
    public static final TagKey<Item> CEREALS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "cereals"));

    // -- Les cereales : une culture, une semence, un grain --------------------------

    private static final Map<Cereal, DeferredBlock<AgriCropBlock>> CROPS = new EnumMap<>(Cereal.class);
    private static final Map<Cereal, DeferredItem<BlockItem>> SEEDS = new EnumMap<>(Cereal.class);
    private static final Map<Cereal, DeferredItem<Item>> GRAINS = new EnumMap<>(Cereal.class);
    private static final Map<Cereal, DeferredItem<Item>> BREADS = new EnumMap<>(Cereal.class);

    static {
        for (Cereal cereal : Cereal.values()) {
            DeferredBlock<AgriCropBlock> crop = BLOCKS.registerBlock(cereal.key() + "_crop",
                    properties -> new AgriCropBlock(properties, cereal),
                    properties -> properties.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak()
                            .sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
            CROPS.put(cereal, crop);
            SEEDS.put(cereal, ITEMS.registerItem(cereal.key() + "_seeds",
                    properties -> new BlockItem(crop.get(), properties),
                    properties -> cereal.gmo() ? properties.rarity(Rarity.UNCOMMON) : properties));
            GRAINS.put(cereal, ITEMS.registerItem(cereal.key(), Item::new,
                    properties -> cereal.gmo() ? properties.rarity(Rarity.UNCOMMON) : properties));
            BREADS.put(cereal, ITEMS.registerItem(cereal.key() + "_bread", properties -> bread(cereal, properties)));
        }
    }

    // -- L'elevage ---------------------------------------------------------------------

    public static final DeferredItem<Item> CREAM = ITEMS.registerItem("cream", Item::new);
    public static final DeferredItem<Item> LARD = ITEMS.registerItem("lard", Item::new);

    public static final DeferredBlock<FeederBlock> TROUGH = BLOCKS.registerBlock("trough",
            properties -> new FeederBlock(properties, FeederBlock.Kind.TROUGH),
            properties -> properties.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<FeederBlock> WATER_DISPENSER = BLOCKS.registerBlock("water_dispenser",
            properties -> new FeederBlock(properties, FeederBlock.Kind.WATER),
            properties -> properties.mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.STONE).noOcclusion());
    public static final DeferredItem<BlockItem> TROUGH_ITEM =
            ITEMS.registerSimpleBlockItem(TROUGH, properties -> properties);
    public static final DeferredItem<BlockItem> WATER_DISPENSER_ITEM =
            ITEMS.registerSimpleBlockItem(WATER_DISPENSER, properties -> properties);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeederBlockEntity>> FEEDER_TYPE =
            TYPES.register("feeder", () -> new BlockEntityType<>(FeederBlockEntity::new,
                    Set.of(TROUGH.get(), WATER_DISPENSER.get())));

    // -- La machine a OGM ---------------------------------------------------------------

    public static final DeferredBlock<GmoMachineBlock> GMO_MACHINE = BLOCKS.registerBlock("gmo_machine",
            GmoMachineBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> GMO_MACHINE_ITEM =
            ITEMS.registerSimpleBlockItem(GMO_MACHINE, properties -> properties.rarity(Rarity.UNCOMMON));

    // -- L'onglet ------------------------------------------------------------------------

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("agriculture",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DoHy.MOD_ID + ".agriculture"))
                    .icon(() -> new ItemStack(GRAINS.get(Cereal.CORN).get()))
                    .displayItems((parameters, output) -> ordered().forEach(item -> output.accept(item.get())))
                    .build());

    private DoHyAgri() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        TABS.register(modBus);
    }

    /** L'onglet, dans l'ordre de la filiere : semences, grains, pains, elevage, machine. */
    private static List<Supplier<? extends Item>> ordered() {
        List<Supplier<? extends Item>> list = new ArrayList<>();
        for (Cereal cereal : Cereal.values()) {
            list.add(SEEDS.get(cereal));
        }
        for (Cereal cereal : Cereal.values()) {
            list.add(GRAINS.get(cereal));
        }
        for (Cereal cereal : Cereal.values()) {
            list.add(BREADS.get(cereal));
        }
        list.add(CREAM);
        list.add(LARD);
        list.add(TROUGH_ITEM);
        list.add(WATER_DISPENSER_ITEM);
        list.add(GMO_MACHINE_ITEM);
        return list;
    }

    public static AgriCropBlock crop(Cereal cereal) {
        return CROPS.get(cereal).get();
    }

    public static Item grain(Cereal cereal) {
        return GRAINS.get(cereal).get();
    }

    public static Item bread(Cereal cereal) {
        return BREADS.get(cereal).get();
    }

    /** Les semences d'une cereale par sa cle ; {@code wheat} rend celles du ble de Minecraft. */
    public static Item seeds(String key) {
        for (Cereal cereal : Cereal.values()) {
            if (cereal.key().equals(key)) {
                return SEEDS.get(cereal).get();
            }
        }
        return Items.WHEAT_SEEDS;
    }

    /** Les semences qui peuvent tomber de l'herbe : celles des cereales naturelles. */
    public static List<Item> naturalSeeds() {
        List<Item> list = new ArrayList<>();
        for (Cereal cereal : Cereal.values()) {
            if (!cereal.gmo()) {
                list.add(SEEDS.get(cereal).get());
            }
        }
        return list;
    }

    /**
     * Le pain d'une cereale.
     *
     * <p>Un pain naturel nourrit comme le pain du jeu et donne un petit effet
     * d'une minute ou deux. Un pain d'OGM, qui demande en plus un produit
     * d'elevage, nourrit mieux et donne des effets de niveau II pendant trois
     * minutes : c'est une potion qui se mange, et il est toujours mangeable.
     */
    private static Item bread(Cereal cereal, Item.Properties properties) {
        List<MobEffectInstance> effects = switch (cereal) {
            case CORN -> List.of(new MobEffectInstance(MobEffects.HASTE, 1800, 0));
            case OAT -> List.of(new MobEffectInstance(MobEffects.SPEED, 1800, 0));
            case RYE -> List.of(new MobEffectInstance(MobEffects.RESISTANCE, 1200, 0));
            case BARLEY -> List.of(new MobEffectInstance(MobEffects.STRENGTH, 1200, 0));
            case SOY -> List.of(new MobEffectInstance(MobEffects.REGENERATION, 300, 0));
            case FONIO -> List.of(new MobEffectInstance(MobEffects.SPEED, 3600, 1),
                    new MobEffectInstance(MobEffects.JUMP_BOOST, 3600, 1));
            case SORGHUM -> List.of(new MobEffectInstance(MobEffects.STRENGTH, 3600, 1),
                    new MobEffectInstance(MobEffects.HASTE, 3600, 1));
            case KAMUT -> List.of(new MobEffectInstance(MobEffects.REGENERATION, 400, 1),
                    new MobEffectInstance(MobEffects.ABSORPTION, 3600, 1),
                    new MobEffectInstance(MobEffects.RESISTANCE, 3600, 0));
        };
        FoodProperties.Builder food = new FoodProperties.Builder()
                .nutrition(cereal.gmo() ? 8 : 5).saturationModifier(cereal.gmo() ? 0.8f : 0.6f);
        if (cereal.gmo()) {
            food.alwaysEdible();
            properties = properties.rarity(Rarity.RARE);
        }
        return new Item(properties.food(food.build(),
                Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(effects)).build()));
    }
}
