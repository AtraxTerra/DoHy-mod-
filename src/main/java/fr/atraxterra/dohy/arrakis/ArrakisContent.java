package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * L'epice d'Arrakis : le sable qui la porte, le melange qu'on en tire, et la
 * moissonneuse qui le recolte. Tout existe meme sans NTM Space (les objets se
 * gardent d'un serveur a l'autre), mais seule Arrakis a des champs d'epice.
 */
public final class ArrakisContent {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DoHy.MOD_ID);

    /** Le sable d'epice : il coule comme du sable, et sa poussiere est rouille. */
    public static final DeferredBlock<ColoredFallingBlock> SPICE_SAND = BLOCKS.registerBlock("spice_sand",
            properties -> new ColoredFallingBlock(new ColorRGBA(0xFFC4662E), properties),
            properties -> properties.mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.SAND)
                    .instrument(NoteBlockInstrument.SNARE));
    public static final DeferredItem<BlockItem> SPICE_SAND_ITEM =
            ITEMS.registerSimpleBlockItem(SPICE_SAND, properties -> properties
                    .component(DataComponents.LORE, lore("tooltip.dohy.spice_sand.1")));

    /**
     * Le melange : ce que le comptoir d'Arrakeen rachete. Mange, il aiguise les
     * sens un moment — on voit la nuit, on va plus vite, on creuse plus vite.
     */
    public static final DeferredItem<Item> MELANGE = ITEMS.registerItem("melange", properties -> new Item(properties
            .rarity(Rarity.UNCOMMON)
            .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3f).alwaysEdible().build(),
                    Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                            new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0),
                            new MobEffectInstance(MobEffects.SPEED, 1200, 0),
                            new MobEffectInstance(MobEffects.HASTE, 1200, 0)))).build())
            .component(DataComponents.LORE, lore("tooltip.dohy.melange.1", "tooltip.dohy.melange.2"))));

    /** Une machine, pas une bete : hors des categories d'apparition, comme le tracteur. */
    public static final DeferredHolder<EntityType<?>, EntityType<SpiceHarvester>> HARVESTER = ENTITIES.register(
            "spice_harvester", key -> EntityType.Builder.<SpiceHarvester>of(SpiceHarvester::new, MobCategory.MISC)
                    .sized(2.7f, 2.4f)
                    .passengerAttachments(new Vec3(0.0d, 0.95d, -1.3d))
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final DeferredItem<HarvesterItem> HARVESTER_ITEM = ITEMS.registerItem("spice_harvester",
            HarvesterItem::new, properties -> properties.stacksTo(1).rarity(Rarity.RARE)
                    .component(DataComponents.LORE, lore("tooltip.dohy.spice_harvester.1",
                            "tooltip.dohy.spice_harvester.2")));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("arrakis",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DoHy.MOD_ID + ".arrakis"))
                    .icon(() -> new ItemStack(MELANGE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SPICE_SAND_ITEM.get());
                        output.accept(MELANGE.get());
                        output.accept(HARVESTER_ITEM.get());
                    })
                    .build());

    private ArrakisContent() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(ArrakisContent::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(HARVESTER.get(), SpiceHarvester.createAttributes().build());
    }

    public static boolean isSpice(BlockState state) {
        return state.is(SPICE_SAND.get());
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
