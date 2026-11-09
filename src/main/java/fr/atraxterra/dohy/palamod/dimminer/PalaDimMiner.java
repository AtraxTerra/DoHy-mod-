package fr.atraxterra.dohy.palamod.dimminer;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * La dimension du Mineur ({@code fr.paladium.palamod.modules.miner.dimminer}).
 *
 * <p>Dans Paladium, c'etait un serveur a part du reseau, rejoint par
 * BungeeCord. En solo, c'est une vraie dimension, {@code palamod:miner} :
 * un overworld genere normalement, de nuit, sans monstres, ou les minerais de
 * Paladium sont trois fois plus nombreux (donnees dans
 * {@code data/palamod/dimension}).
 */
public final class PalaDimMiner {

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "miner"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);

    /**
     * {@code BlockWitheredObsidian} : roche, durete 50 ({@code Blocks.register}
     * ecrase le 5 du constructeur), {@code setResistance(100)} soit 60 en
     * 26.2, n'importe quelle pioche. Eclaire de sa charge. Immobile, comme
     * tout bloc a block entity en 1.7.10 : pousse par un piston, un cadre
     * echapperait a la casse d'un bloc sur trois, qui vise des positions.
     */
    public static final DeferredBlock<Block> WITHERED_OBSIDIAN = BLOCKS.registerBlock(
            "withered_obsidian", WitheredObsidianBlock::new, properties -> properties
                    .strength(50.0f, 60.0f)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(WitheredObsidianBlock.FEED)));
    public static final DeferredItem<BlockItem> WITHERED_OBSIDIAN_ITEM =
            ITEMS.registerSimpleBlockItem(WITHERED_OBSIDIAN, properties -> properties);

    /** {@code BlockMinerPortal} : incassable, traversable, lumiere 0,75 soit 11, son de verre. */
    public static final DeferredBlock<Block> MINER_PORTAL = BLOCKS.registerBlock(
            "miner_portal", MinerPortalBlock::new, properties -> properties
                    .noCollision()
                    .strength(-1.0f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable());

    /**
     * {@code BlockMiner} « Minerai de Pierre d'Invocation » : durete 5, se rend
     * lui-meme, cuit en pierre d'invocation. Elle ne servait qu'aux runes de
     * donjon de PalaRPG, absent : on le porte parce qu'il fait partie des
     * minerais comptes par la dimension.
     */
    public static final DeferredBlock<Block> INVOKER_ORE = BLOCKS.registerSimpleBlock(
            "invoker_ore", properties -> properties
                    .strength(5.0f, 5.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE));
    public static final DeferredItem<BlockItem> INVOKER_ORE_ITEM =
            ITEMS.registerSimpleBlockItem(INVOKER_ORE, properties -> properties);
    public static final DeferredItem<Item> INVOKER_STONE = ITEMS.registerItem("invoker_stone", Item::new);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MinerPortalBlockEntity>> MINER_PORTAL_ENTITY =
            BLOCK_ENTITIES.register("miner_portal",
                    () -> new BlockEntityType<>(MinerPortalBlockEntity::new, Set.of(MINER_PORTAL.get())));

    /** La session du joueur. Copiee a la mort, comme les proprietes PERSISTANT de l'original. */
    public static final Supplier<AttachmentType<DimMinerData>> DATA = ATTACHMENTS.register(
            "dim_miner", () -> AttachmentType.builder(DimMinerData::new)
                    .serialize(DimMinerData.MAP_CODEC, data -> !data.isBlank())
                    .copyOnDeath()
                    .build());

    /** La prochaine section de la spirale, gardee sur l'overworld. */
    public static final Supplier<AttachmentType<Integer>> NEXT_SECTION = ATTACHMENTS.register(
            "dim_miner_next_section", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("next"), value -> value > 0)
                    .build());

    public static final List<DeferredItem<? extends Item>> ORDERED =
            List.of(WITHERED_OBSIDIAN_ITEM, INVOKER_ORE_ITEM, INVOKER_STONE);

    private PalaDimMiner() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
