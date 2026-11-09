package fr.atraxterra.dohy.palamod.pvp;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Les objets « pour le PvP » de PalaMod : les sticks, les anneaux, l'aile et
 * le deltaplane, et ce qu'il faut pour les fabriquer — la palamachine, les
 * orbes de force et de saut, le slimepad et le spike en paladium.
 *
 * <p>Relevé dans {@code PRegister_Items}, {@code PRegister_Blocks},
 * {@code ItemStickBase}, {@code BaseItemRing}, {@code ItemHangGlider} et
 * {@code PaladiumMachineLogic}. Les noms sont ecrits en toutes lettres pour
 * {@code check_assets.py}.
 */
public final class PalaPvp {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);

    // -- Composants -------------------------------------------------------------

    /** {@code repair} d'un stick : les reparations deja faites a la palamachine, cinq au plus. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STICK_REPAIRS =
            COMPONENTS.registerComponentType("stick_repairs", builder -> builder
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /**
     * {@code repaired} d'une piece d'equipement en paladium : ce qu'il reste a
     * reparer par les anneaux. Pose au premier passage, a la capacite de la piece.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RING_REPAIR =
            COMPONENTS.registerComponentType("ring_repair", builder -> builder
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** {@code Uses} du deltaplane : ouvert ou replie. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GLIDER_OPEN =
            COMPONENTS.registerComponentType("glider_open", builder -> builder
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /**
     * {@code timer-<type>} : quand chaque stick sera de nouveau pret, en
     * millisecondes. Sur le joueur et pas sur l'objet : deux sticks du meme
     * type partagent leur recharge, meme celui qu'on n'a pas utilise.
     */
    public static final Supplier<AttachmentType<Map<String, Long>>> STICK_COOLDOWNS = ATTACHMENTS.register(
            "stick_cooldowns", () -> AttachmentType.<Map<String, Long>>builder(() -> new HashMap<>())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("ready")
                            .xmap(map -> (Map<String, Long>) new HashMap<>(map), map -> map),
                            map -> !map.isEmpty())
                    .copyOnDeath()
                    .build());

    // -- Sticks -----------------------------------------------------------------

    public static final DeferredItem<StickItem> HEAL_STICK = stick("heal_stick", StickType.HEAL);
    public static final DeferredItem<StickItem> SPEED_STICK = stick("speed_stick", StickType.SPEED);
    public static final DeferredItem<StickItem> STRENGTH_STICK = stick("strength_stick", StickType.STRENGTH);
    public static final DeferredItem<StickItem> JUMP_STICK = stick("jump_stick", StickType.JUMP);
    public static final DeferredItem<StickItem> GOD_STICK = stick("god_stick", StickType.GOD);
    public static final DeferredItem<StickItem> DAMAGE_STICK = stick("damage_stick", StickType.DAMAGE);
    public static final DeferredItem<StickItem> HYPER_JUMP_STICK = stick("hyper_jump_stick", StickType.HYPER_JUMP);

    /** Les sept sticks, dans l'ordre de {@code StickType}. */
    public static final List<DeferredItem<StickItem>> STICKS = List.of(
            HEAL_STICK, SPEED_STICK, STRENGTH_STICK, JUMP_STICK, GOD_STICK, DAMAGE_STICK, HYPER_JUMP_STICK);

    /** Les orbes qui manquaient : force (poudre de blaze) et saut (verrue du Nether). */
    public static final DeferredItem<Item> STRENGTH_ORB = ITEMS.registerItem("strength_orb", Item::new);
    public static final DeferredItem<Item> JUMP_ORB = ITEMS.registerItem("jump_orb", Item::new);

    // -- Anneaux ----------------------------------------------------------------

    public static final DeferredItem<RingItem> SMALL_RING = ring("small_ring", 400, false);
    public static final DeferredItem<RingItem> MEDIUM_RING = ring("medium_ring", 1000, false);
    public static final DeferredItem<RingItem> BIG_RING = ring("big_ring", 3200, false);
    public static final DeferredItem<RingItem> ENDIUM_SMALL_RING = ring("endium_small_ring", 400, true);
    public static final DeferredItem<RingItem> ENDIUM_MEDIUM_RING = ring("endium_medium_ring", 1000, true);
    public static final DeferredItem<RingItem> ENDIUM_BIG_RING = ring("endium_big_ring", 3200, true);

    // -- Deltaplane -------------------------------------------------------------

    public static final DeferredItem<Item> WING = ITEMS.registerItem("wing", Item::new);
    public static final DeferredItem<HangGliderItem> HANG_GLIDER = ITEMS.registerItem(
            "hang_glider", properties -> new HangGliderItem(properties.stacksTo(1)));

    // -- Blocs ------------------------------------------------------------------

    /** {@code PALADIUM_MACHINE_BLOCK} : fer, durete 12, resistance 8 (soit 4,8), pioche. */
    public static final DeferredBlock<PalamachineBlock> PALAMACHINE = BLOCKS.registerBlock(
            "palamachine", PalamachineBlock::new, properties -> properties
                    .strength(12.0f, 4.8f).sound(SoundType.METAL).mapColor(MapColor.COLOR_RED)
                    .requiresCorrectToolForDrops());
    /** {@code BaseBlockSlimePad} : roche, durete 12, resistance 8 (soit 4,8), pioche en fer. */
    public static final DeferredBlock<SlimepadBlock> SLIMEPAD = BLOCKS.registerBlock(
            "slimepad", SlimepadBlock::new, properties -> properties
                    .strength(12.0f, 4.8f).sound(SoundType.SLIME_BLOCK).mapColor(MapColor.GRASS)
                    .requiresCorrectToolForDrops().noOcclusion());
    /**
     * {@code SPIKE_PALADIUM} : fer, durete 12, resistance 100 (soit 60 : le
     * drapeau « resistant » du constructeur), pioche en fer, 15 points de
     * degats au contact.
     */
    public static final DeferredBlock<SpikeBlock> PALADIUM_SPIKE = BLOCKS.registerBlock(
            "paladium_spike", properties -> new SpikeBlock(15.0f, properties), properties -> properties
                    .strength(12.0f, 60.0f).sound(SoundType.METAL).mapColor(MapColor.COLOR_RED)
                    .requiresCorrectToolForDrops().noOcclusion());

    public static final List<DeferredBlock<? extends Block>> ORDERED_BLOCKS =
            List.of(PALAMACHINE, SLIMEPAD, PALADIUM_SPIKE);
    private static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = ORDERED_BLOCKS.stream()
            .map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PalamachineBlockEntity>>
            PALAMACHINE_ENTITY = BLOCK_ENTITIES.register("palamachine",
                    () -> new BlockEntityType<>(PalamachineBlockEntity::new, Set.<Block>of(PALAMACHINE.get())));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> PALAMACHINE_MENU =
            MENUS.register("palamachine", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(PalaPvp.PALAMACHINE_MENU.get(), containerId,
                            inventory, MachineLayout.PALAMACHINE),
                    FeatureFlags.VANILLA_SET));

    /** L'ordre de l'onglet. */
    public static final List<DeferredItem<? extends Item>> ORDERED_ITEMS = List.of(
            HEAL_STICK, SPEED_STICK, STRENGTH_STICK, JUMP_STICK, GOD_STICK, DAMAGE_STICK, HYPER_JUMP_STICK,
            STRENGTH_ORB, JUMP_ORB,
            SMALL_RING, MEDIUM_RING, BIG_RING, ENDIUM_SMALL_RING, ENDIUM_MEDIUM_RING, ENDIUM_BIG_RING,
            WING, HANG_GLIDER);

    private PalaPvp() {
    }

    public static List<DeferredItem<BlockItem>> blockItems() {
        return BLOCK_ITEMS;
    }

    private static DeferredItem<StickItem> stick(String name, StickType type) {
        return ITEMS.registerItem(name, properties -> new StickItem(type, properties.durability(type.durability())));
    }

    private static DeferredItem<RingItem> ring(String name, int durability, boolean endium) {
        return ITEMS.registerItem(name, properties -> new RingItem(endium, properties.durability(durability)));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
