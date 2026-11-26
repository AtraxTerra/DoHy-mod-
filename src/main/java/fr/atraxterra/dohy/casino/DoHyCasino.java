package fr.atraxterra.dohy.casino;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
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

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Le casino et la loterie (lot 27) : la machine aleatoire, le de, le monnayeur et la caisse des
 * gains, les comptes des casinos et la loterie du spawn (gardes sur l'overworld).
 */
public final class DoHyCasino {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    public static final DeferredBlock<RandomMachineBlock> RANDOM_MACHINE = BLOCKS.registerBlock("random_machine",
            RandomMachineBlock::new, properties -> properties.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 6.0f)
                    .sound(SoundType.METAL).isRedstoneConductor((state, level, pos) -> false)
                    .lightLevel(state -> state.getValue(RandomMachineBlock.OPEN) ? 7 : 0));
    public static final DeferredBlock<DiceBlock> DICE = BLOCKS.registerBlock("dice", DiceBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_RED).strength(1.0f, 3.0f).sound(SoundType.BONE_BLOCK)
                    .isRedstoneConductor((state, level, pos) -> false));
    public static final DeferredBlock<CoinSlotBlock> COIN_SLOT = BLOCKS.registerBlock("coin_slot",
            CoinSlotBlock::new, properties -> properties.mapColor(MapColor.GOLD).strength(2.5f, 6.0f)
                    .sound(SoundType.METAL).isRedstoneConductor((state, level, pos) -> false)
                    .lightLevel(state -> state.getValue(CoinSlotBlock.POWERED) ? 7 : 0));
    public static final DeferredBlock<PayoutBoxBlock> PAYOUT_BOX = BLOCKS.registerBlock("payout_box",
            PayoutBoxBlock::new, properties -> properties.mapColor(MapColor.COLOR_GREEN).strength(2.5f, 6.0f)
                    .sound(SoundType.METAL).isRedstoneConductor((state, level, pos) -> false)
                    .lightLevel(state -> state.getValue(PayoutBoxBlock.POWERED) ? 7 : 0));

    public static final DeferredItem<BlockItem> RANDOM_MACHINE_ITEM = ITEMS.registerSimpleBlockItem(RANDOM_MACHINE);
    public static final DeferredItem<BlockItem> DICE_ITEM = ITEMS.registerSimpleBlockItem(DICE);
    public static final DeferredItem<BlockItem> COIN_SLOT_ITEM = ITEMS.registerSimpleBlockItem(COIN_SLOT);
    public static final DeferredItem<BlockItem> PAYOUT_BOX_ITEM = ITEMS.registerSimpleBlockItem(PAYOUT_BOX);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CasinoBlockEntity>> TYPE =
            TYPES.register("casino", () -> new BlockEntityType<>(CasinoBlockEntity::new, Set.<Block>of(
                    RANDOM_MACHINE.get(), DICE.get(), COIN_SLOT.get(), PAYOUT_BOX.get())));

    /** Les comptes des casinos, sur l'overworld. */
    public static final Supplier<AttachmentType<CasinoBook>> BOOK = ATTACHMENTS.register("casino_book",
            () -> AttachmentType.builder(CasinoBook::new)
                    .serialize(CasinoBook.MAP_CODEC, book -> !book.isEmpty())
                    .build());
    /** La loterie du spawn, sur l'overworld. */
    public static final Supplier<AttachmentType<LotteryData>> LOTTERY = ATTACHMENTS.register("lottery",
            () -> AttachmentType.builder(LotteryData::new)
                    .serialize(LotteryData.MAP_CODEC, data -> !data.isEmpty())
                    .build());

    private DoHyCasino() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    /** Les quatre objets, pour l'onglet des machines. */
    public static List<Item> items() {
        return List.of(RANDOM_MACHINE_ITEM.get(), DICE_ITEM.get(), COIN_SLOT_ITEM.get(), PAYOUT_BOX_ITEM.get());
    }
}
