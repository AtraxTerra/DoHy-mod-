package fr.atraxterra.dohy.faction.garrison;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.function.Supplier;

/**
 * La garnison : des soldats a toi, recrutes et payes par la banque de ta nation, qui gardent ta
 * base, t'accompagnent et tiennent ton coffre pendant un assaut ({@link Garrisons}).
 */
public final class DoHyGarrison {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DoHy.MOD_ID);

    /** La caserne : une par joueur, dans ses claims. */
    public static final DeferredBlock<BarracksBlock> BARRACKS = BLOCKS.registerBlock("barracks", BarracksBlock::new,
            properties -> properties.mapColor(MapColor.STONE).strength(5.0f, 1200.0f).sound(SoundType.STONE)
                    .requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> BARRACKS_ITEM =
            ITEMS.registerSimpleBlockItem(BARRACKS, properties -> properties);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BarracksBlockEntity>> BARRACKS_TYPE =
            TYPES.register("barracks", () -> new BlockEntityType<>(BarracksBlockEntity::new, Set.of(BARRACKS.get())));

    /** Le soldat de la garnison : jamais d'apparition naturelle, jamais ecrit sur disque. */
    public static final DeferredHolder<EntityType<?>, EntityType<GarrisonSoldier>> SOLDIER = ENTITIES.register(
            "garrison_soldier", key -> EntityType.Builder.<GarrisonSoldier>of(GarrisonSoldier::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    /** La garnison du joueur : elle le suit a la mort. */
    public static final Supplier<AttachmentType<Garrison>> GARRISON = ATTACHMENTS.register(
            "garrison", () -> AttachmentType.builder(Garrison::empty)
                    .serialize(Garrison.CODEC.fieldOf("garrison"), garrison -> !garrison.isEmpty())
                    .copyOnDeath()
                    .build());

    private DoHyGarrison() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(DoHyGarrison::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SOLDIER.get(), GarrisonSoldier.createAttributes().build());
    }
}
