package fr.atraxterra.dohy.palamod.miner;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.equipment.PalaToolMaterials;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Les objets du metier de Mineur.
 *
 * <p>Les identifiants sont ceux que {@code data/jobs.json} verrouille deja :
 * {@code amethyst_excavator} au niveau 15, {@code magnet} au 35,
 * {@code moula_stone} au 40... Un caractere de difference laisserait le verrou
 * inerte sans la moindre erreur.
 *
 * <p>Les noms sont ecrits en toutes lettres : {@code tools/check_assets.py} trouve
 * les objets en lisant les appels {@code registerItem("...")} du source.
 */
public final class PalaMiner {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, PalaMod.NAMESPACE);

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);

    private static final List<DeferredItem<? extends Item>> ORDER = new ArrayList<>();

    /**
     * La pierre stockee dans une Voidstone minage — le {@code stone} du NBT de
     * PalaMod. Persistant (il suit l'objet dans un coffre et dans la sauvegarde)
     * et synchronise (l'infobulle le lit cote client).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_STONE =
            COMPONENTS.registerComponentType("stored_stone",
                    builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** L'effet de la potion de durete. */
    public static final DeferredHolder<MobEffect, HardnessEffect> HARDNESS =
            EFFECTS.register("hardness", HardnessEffect::new);

    // -- Excavatrices : les materiaux des pelles du meme metal ---------------
    public static final DeferredItem<ExcavatorItem> AMETHYST_EXCAVATOR = track(ITEMS.registerItem(
            "amethyst_excavator", properties -> new ExcavatorItem(PalaToolMaterials.AMETHYST, properties)));
    public static final DeferredItem<ExcavatorItem> TITANE_EXCAVATOR = track(ITEMS.registerItem(
            "titane_excavator", properties -> new ExcavatorItem(PalaToolMaterials.TITANE, properties)));
    public static final DeferredItem<ExcavatorItem> PALADIUM_EXCAVATOR = track(ITEMS.registerItem(
            "paladium_excavator", properties -> new ExcavatorItem(PalaToolMaterials.PALADIUM, properties)));

    // -- Outils de construction : cotes de getRadius(meta) -------------------
    public static final DeferredItem<BuilderWandItem> AMETHYST_BUILDER_WAND = track(ITEMS.registerItem(
            "amethyst_builder_wand", properties -> new BuilderWandItem(6, properties.stacksTo(1))));
    public static final DeferredItem<BuilderWandItem> TITANE_BUILDER_WAND = track(ITEMS.registerItem(
            "titane_builder_wand", properties -> new BuilderWandItem(9, properties.stacksTo(1))));
    public static final DeferredItem<BuilderWandItem> PALADIUM_BUILDER_WAND = track(ITEMS.registerItem(
            "paladium_builder_wand", properties -> new BuilderWandItem(11, properties.stacksTo(1))));
    public static final DeferredItem<BuilderWandItem> ENDIUM_BUILDER_WAND = track(ITEMS.registerItem(
            "endium_builder_wand", properties -> new BuilderWandItem(20, properties.stacksTo(1))));

    // -- Objets de poche -------------------------------------------------------
    // ItemMiner ne fixe pas de taille de pile : 64, comme l'original.
    public static final DeferredItem<MagnetItem> MAGNET = track(ITEMS.registerItem(
            "magnet", MagnetItem::new));
    public static final DeferredItem<Item> MOULA_STONE = track(ITEMS.registerItem(
            "moula_stone", Item::new));

    public static final DeferredItem<HardnessPotionItem> HARDNESS_POTION = track(ITEMS.registerItem(
            "hardness_potion", properties -> new HardnessPotionItem(properties
                    .stacksTo(1)
                    .usingConvertsTo(Items.GLASS_BOTTLE)
                    .component(DataComponents.CONSUMABLE, Consumable.builder()
                            // getMaxItemUseDuration : 20 ticks, une seconde.
                            .consumeSeconds(1.0f)
                            .animation(ItemUseAnimation.DRINK)
                            .sound(SoundEvents.GENERIC_DRINK)
                            .hasConsumeParticles(false)
                            .build()))));

    /**
     * Voidstone minage. Le niveau d'usage est 30 (6 x 5), pas celui de la
     * voidstone simple : c'est un autre objet.
     */
    public static final DeferredItem<VoidstoneMinageItem> VOIDSTONE_MINAGE = track(ITEMS.registerItem(
            "voidstone_minage", properties -> new VoidstoneMinageItem(properties.stacksTo(1))));

    // -- Compresseur a cobblestone -----------------------------------------
    //
    // « void_stone » et non « voidstone » comme dans PalaMod : l'objet
    // palamod:voidstone est deja la poubelle de poche, et un bloc homonyme sans
    // objet brouillerait le controle d'assets. Rien ne verrouille ce bloc.
    public static final DeferredBlock<VoidStoneBlock> VOID_STONE_BLOCK = BLOCKS.registerBlock(
            "void_stone", VoidStoneBlock::new, properties -> properties
                    .strength(5.0f)
                    .sound(SoundType.STONE));

    public static final DeferredBlock<WitheredPistonBlock> WITHERED_REINFORCED_PISTON = BLOCKS.registerBlock(
            "withered_reinforced_piston", WitheredPistonBlock::new, properties -> properties
                    .strength(5.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> WITHERED_REINFORCED_PISTON_ITEM = track(
            ITEMS.registerSimpleBlockItem(WITHERED_REINFORCED_PISTON, properties -> properties));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidStoneBlockEntity>> VOID_STONE =
            BLOCK_ENTITIES.register("void_stone",
                    () -> new BlockEntityType<>(VoidStoneBlockEntity::new, Set.of(VOID_STONE_BLOCK.get())));

    // -- Blocs du Mineur ---------------------------------------------------

    /**
     * Minerai de pepites d'endium ({@code BlockEndiumNuggetOre}). Durete 5,
     * resistance 6 000 000 (celle de la bedrock), n'importe quelle pioche ;
     * il lache toujours une seule pepite, sans fortune ni toucher de soie. Le
     * casser demande Mineur 100 : le bloc est dans {@code #palamod:break_locked}.
     */
    public static final DeferredBlock<Block> ENDIUM_NUGGET_ORE = BLOCKS.registerBlock(
            "endium_nugget_ore", Block::new, properties -> properties
                    .strength(5.0f, 6_000_000.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    /**
     * Cave block ({@code BlockCave}) : verre, durete 4, pioche. Il se declare
     * opaque alors que sa texture est trouee : le moteur ne dessine donc pas les
     * faces voisines collees a lui, et l'on voit les grottes a travers les
     * trous. C'est un x-ray volontaire, et c'est tout son interet. Ici : la
     * passe cutout, que le jeu choisit d'apres la texture depuis 26.1, et
     * l'occlusion gardee — surtout pas {@code noOcclusion()}.
     */
    public static final DeferredBlock<Block> CAVE_BLOCK = BLOCKS.registerBlock(
            "cave_block", Block::new, properties -> properties
                    .strength(4.0f)
                    .sound(SoundType.GLASS)
                    .requiresCorrectToolForDrops());

    /** Le meme, qui montre en plus les conteneurs vus a travers lui ({@link EndiumCaveVision}). */
    public static final DeferredBlock<EndiumCaveBlock> ENDIUM_CAVE_BLOCK = BLOCKS.registerBlock(
            "endium_cave_block", EndiumCaveBlock::new, properties -> properties
                    .strength(4.0f)
                    .sound(SoundType.GLASS)
                    .requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> ENDIUM_NUGGET_ORE_ITEM = track(
            ITEMS.registerSimpleBlockItem(ENDIUM_NUGGET_ORE, properties -> properties));
    public static final DeferredItem<BlockItem> CAVE_BLOCK_ITEM = track(
            ITEMS.registerSimpleBlockItem(CAVE_BLOCK, properties -> properties));
    public static final DeferredItem<BlockItem> ENDIUM_CAVE_BLOCK_ITEM = track(
            ITEMS.registerSimpleBlockItem(ENDIUM_CAVE_BLOCK, properties -> properties));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EndiumCaveBlockEntity>> ENDIUM_CAVE =
            BLOCK_ENTITIES.register("endium_cave_block",
                    () -> new BlockEntityType<>(EndiumCaveBlockEntity::new, Set.of(ENDIUM_CAVE_BLOCK.get())));

    private PalaMiner() {
    }

    /** L'ordre de l'onglet creatif. */
    public static List<DeferredItem<? extends Item>> ordered() {
        return List.copyOf(ORDER);
    }

    private static <T extends Item> DeferredItem<T> track(DeferredItem<T> item) {
        ORDER.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
    }
}
