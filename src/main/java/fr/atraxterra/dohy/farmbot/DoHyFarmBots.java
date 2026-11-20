package fr.atraxterra.dohy.farmbot;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Les bots de farming : l'entite, l'objet qui la pose, la telecommande qui lui donne sa zone et ses
 * coffres, ce que ces objets gardent, et la liste des bots poses.
 */
public final class DoHyFarmBots {

    /** Le prix d'un bot a la Boutique. */
    public static final long BOT_PRICE = 100_000L;
    /** Le prix d'une telecommande, a la Boutique comme dans le menu d'un bot. */
    public static final long REMOTE_PRICE = 10_000L;

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<FarmBot>> FARM_BOT = ENTITIES.register("farm_bot",
            key -> EntityType.Builder.<FarmBot>of(FarmBot::new, MobCategory.MISC)
                    .sized(0.7f, 1.05f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    /** Le reglage d'un bot range (zone, coffres). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BotSetup>> SETUP =
            COMPONENTS.registerComponentType("bot_setup",
                    builder -> builder.persistent(BotSetup.CODEC).networkSynchronized(BotSetup.STREAM_CODEC));
    /** Le bot que pilote une telecommande. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> REMOTE_BOT =
            COMPONENTS.registerComponentType("remote_bot",
                    builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    /** Le premier coin d'une zone en cours de trace. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> REMOTE_CORNER =
            COMPONENTS.registerComponentType("remote_corner",
                    builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    public static final DeferredItem<FarmBotItem> FARM_BOT_ITEM = ITEMS.registerItem("farm_bot", FarmBotItem::new,
            properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BotRemoteItem> BOT_REMOTE = ITEMS.registerItem("bot_remote",
            BotRemoteItem::new, properties -> properties.stacksTo(1));

    /** Les bots poses de tous les joueurs, sur l'Overworld. */
    public static final Supplier<AttachmentType<BotRegistry>> REGISTRY = ATTACHMENTS.register("farm_bots",
            () -> AttachmentType.builder(() -> BotRegistry.EMPTY)
                    .serialize(BotRegistry.MAP_CODEC, registry -> !registry.isEmpty())
                    .build());

    private DoHyFarmBots() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(DoHyFarmBots::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(FARM_BOT.get(), FarmBot.createAttributes().build());
    }
}
