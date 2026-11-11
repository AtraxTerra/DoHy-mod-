package fr.atraxterra.dohy.palamod.trixium;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
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
 * Le concours Trixium (lot 23a) : ses deux objets exclusifs et la progression
 * du joueur.
 *
 * <p>Le module {@code trixium} de PalaMod en declare quatre. La <b>selle
 * humaine</b> ne sert qu'a monter sur un autre joueur : ecartee (choix du
 * joueur). Le <b>pinceau de peinture</b> est enregistre sans texture ni usage
 * dans le jar : rien a porter.
 */
public final class PalaTrixium {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    /**
     * La duree de pose choisie sur une fabrique a nuage, en secondes : le
     * {@code duration} du NBT de {@code ItemCloud}. Persistante, et synchronisee
     * pour le nom et l'infobulle.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CLOUD_DURATION =
            COMPONENTS.registerComponentType("cloud_duration",
                    builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Le nuage pose ({@code BlockCloud}) : materiau tissu, durete 0, ne lache
     * rien ({@code quantityDropped} rend 0 ; sa table de butin est vide). Un
     * piston le detruit.
     */
    public static final DeferredBlock<CloudBlock> CLOUD_BLOCK = BLOCKS.registerBlock(
            "cloud", CloudBlock::new, properties -> properties
                    .instabreak()
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.DESTROY));

    public static final DeferredItem<CloudItem> CLOUD = ITEMS.registerItem(
            "cloud", properties -> new CloudItem(CLOUD_BLOCK.get(), properties.stacksTo(1)));

    public static final DeferredItem<PocketEnderchestItem> POCKET_ENDERCHEST = ITEMS.registerItem(
            "pocket_enderchest", properties -> new PocketEnderchestItem(properties.stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CloudBlockEntity>> CLOUD_ENTITY =
            BLOCK_ENTITIES.register("cloud",
                    () -> new BlockEntityType<>(CloudBlockEntity::new, Set.of(CLOUD_BLOCK.get())));

    /** Le concours du joueur : sa saison, son depot, ses paliers recuperes. */
    public static final Supplier<AttachmentType<TrixiumData>> DATA = ATTACHMENTS.register("trixium",
            () -> AttachmentType.builder(() -> TrixiumData.EMPTY)
                    .serialize(TrixiumData.MAP_CODEC, data -> !data.equals(TrixiumData.EMPTY))
                    .copyOnDeath()
                    .build());

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(CLOUD, POCKET_ENDERCHEST);

    private PalaTrixium() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
