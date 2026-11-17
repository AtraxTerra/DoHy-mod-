package fr.atraxterra.dohy.cryo;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * La cryogenie : la chambre cryogenique, l'ADN des betes paisibles et la seringue
 * qui le preleve.
 *
 * <p>On preleve l'ADN d'une bete a la seringue ; la chambre, nourrie de glace, en
 * tire le butin de la bete a chaque cycle. Quatre fioles d'un meme ADN et d'un
 * meme grade fusionnent en une du grade suivant, jusqu'au grade V.
 */
public final class DoHyCryo {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, DoHy.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    // -- L'ADN ---------------------------------------------------------------------------------

    /** La bete d'une fiole d'ADN. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> DNA_MOB =
            COMPONENTS.registerComponentType("dna_mob", builder -> builder
                    .persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));

    /** Le grade d'une fiole d'ADN, de 1 a 5. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DNA_GRADE =
            COMPONENTS.registerComponentType("dna_grade", builder -> builder
                    .persistent(ExtraCodecs.intRange(1, Dna.MAX_GRADE)).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Le dernier prelevement d'ADN fait sur une bete, en temps de jeu ; sauvegarde avec la bete. */
    public static final Supplier<AttachmentType<Long>> DNA_DRAWN = ATTACHMENTS.register("dna_drawn",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("tick")).build());

    public static final DeferredItem<DnaItem> DNA = ITEMS.registerItem("dna", DnaItem::new,
            properties -> properties.stacksTo(16));

    public static final DeferredItem<DnaSyringeItem> SYRINGE = ITEMS.registerItem("dna_syringe",
            DnaSyringeItem::new, properties -> properties.durability(64));

    // -- La chambre ----------------------------------------------------------------------------

    public static final DeferredBlock<CryoChamberBlock> CHAMBER = BLOCKS.registerBlock("cryo_chamber",
            CryoChamberBlock::new, properties -> properties.mapColor(MapColor.METAL).strength(5.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().noLootTable()
                    .pushReaction(PushReaction.BLOCK).lightLevel(CryoChamberBlock::light));

    public static final DeferredItem<CryoChamberItem> CHAMBER_ITEM = ITEMS.registerItem("cryo_chamber",
            properties -> new CryoChamberItem(CHAMBER.get(), properties), properties -> properties.stacksTo(1));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CryoChamberBlockEntity>> CHAMBER_TYPE =
            TYPES.register("cryo_chamber", () -> new BlockEntityType<>(CryoChamberBlockEntity::new,
                    Set.of(CHAMBER.get())));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MENU = MENUS.register("cryo_chamber",
            () -> new MenuType<>((containerId, inventory) -> new MachineMenu(DoHyCryo.MENU.get(), containerId,
                    inventory, CryoChamberBlockEntity.LAYOUT), FeatureFlags.VANILLA_SET));

    private DoHyCryo() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(DoHyCryo::onCapabilities);
        modBus.addListener(DoHyCryo::onCreativeTab);
        NeoForge.EVENT_BUS.addListener(DoHyCryo::onRightClick);
    }

    /**
     * Les entonnoirs et les tuyaux : chaque case de la chambre donne acces a
     * l'inventaire du maitre — la glace entre, le butin sort.
     */
    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, entity, side) -> {
            if (!(level.getBlockEntity(CryoChamberBlock.master(pos, state)) instanceof CryoChamberBlockEntity chamber)) {
                return null;
            }
            return side == null ? VanillaContainerWrapper.of(chamber) : new WorldlyContainerWrapper(chamber, side);
        }, CHAMBER.get());
    }

    /** Dans l'onglet de l'agriculture : la chambre, la seringue, et quelques fioles pour le mode creatif. */
    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(fr.atraxterra.dohy.agri.DoHyAgri.TAB.getKey())) {
            return;
        }
        event.accept(CHAMBER_ITEM.get());
        event.accept(SYRINGE.get());
        for (EntityType<?> type : List.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.CHICKEN, EntityTypes.PIG)) {
            event.accept(Dna.stack(type, 1), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        event.accept(Dna.stack(EntityTypes.COW, Dna.MAX_GRADE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    /**
     * Un clic sur n'importe quelle case de la chambre ouvre la machine du maitre.
     * Accroupi avec un objet en main, on laisse poser le bloc, comme pour un coffre.
     */
    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(CHAMBER.get())) {
            return;
        }
        if (player.isShiftKeyDown() && !event.getItemStack().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos master = CryoChamberBlock.master(event.getPos(), state);
        if (event.getLevel().getBlockEntity(master) instanceof CryoChamberBlockEntity chamber
                && chamber.canOpen(player)) {
            player.openMenu(chamber);
        }
    }
}
