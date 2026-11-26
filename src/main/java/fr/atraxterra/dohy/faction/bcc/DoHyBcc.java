package fr.atraxterra.dohy.faction.bcc;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Le Base Claim Controller (wiki « Java | Base Claim Controller ») : le bloc, son
 * objet, son coffre de 55 cases et ce que le joueur en garde.
 */
public final class DoHyBcc {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, DoHy.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    /** Indestructible : ni a la main, ni aux explosions ; il ne part qu'au pillage. */
    public static final DeferredBlock<BccBlock> BCC = BLOCKS.registerBlock("base_claim_controller", BccBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_RED).strength(-1.0f, 3_600_000.0f)
                    .sound(SoundType.NETHERITE_BLOCK).noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> 7));
    public static final DeferredItem<BccItem> BCC_ITEM = ITEMS.registerItem("base_claim_controller",
            properties -> new BccItem(BCC.get(), properties.stacksTo(1).rarity(Rarity.EPIC)
                    .useBlockDescriptionPrefix()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BccBlockEntity>> BCC_TYPE =
            TYPES.register("base_claim_controller",
                    () -> new BlockEntityType<>(BccBlockEntity::new, Set.<Block>of(BCC.get())));
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> BCC_MENU =
            MENUS.register("base_claim_controller", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(DoHyBcc.BCC_MENU.get(), containerId, inventory,
                            BccBlockEntity.LAYOUT),
                    FeatureFlags.VANILLA_SET));

    public static final Supplier<AttachmentType<BccData>> DATA = ATTACHMENTS.register("bcc",
            () -> AttachmentType.builder(BccData::new)
                    .serialize(BccData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    private DoHyBcc() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        MENUS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    static Item item() {
        return BCC_ITEM.get();
    }
}
