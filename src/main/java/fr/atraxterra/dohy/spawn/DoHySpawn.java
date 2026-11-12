package fr.atraxterra.dohy.spawn;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.palamod.dungeon.ReturnPoint;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
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
 * Le spawn : une dimension a part, {@code dohy:spawn}, ou l'on vient ouvrir
 * ses box et passer aux guichets.
 *
 * <p>Comme sur les serveurs dont le mod s'inspire, c'est un lieu et non un
 * menu : un batiment brutaliste pose sur un massif rocheux au-dessus des
 * nuages. On y arrive par {@code /spawn} (ou la carte du menu), sur une
 * plateforme ronde ; un pont mene au parvis, puis a l'atrium des six box et
 * aux bornes. Rien ne s'y casse, rien ne s'y pose, on n'y prend aucun degat.
 *
 * <p>Le lieu n'est pas un fichier de structure : {@link SpawnBuilder} le
 * dessine bloc par bloc a la premiere visite, et le redessine quand son numero
 * de version change.
 */
public final class DoHySpawn {

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "spawn"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    /** La borne : un guichet qui ouvre un ecran du mod. Un seul bloc, une propriete par service. */
    public static final DeferredBlock<SpawnTerminalBlock> TERMINAL = BLOCKS.registerBlock("terminal",
            SpawnTerminalBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(3.0f, 1200.0f).sound(SoundType.METAL)
                    .lightLevel(state -> 9));
    public static final DeferredItem<BlockItem> TERMINAL_ITEM =
            ITEMS.registerSimpleBlockItem(TERMINAL, properties -> properties);

    /** Une etiquette flottante : un bloc invisible qui tient un texte en l'air. */
    public static final DeferredBlock<HologramBlock> HOLOGRAM = BLOCKS.registerBlock("hologram",
            HologramBlock::new,
            properties -> properties.mapColor(MapColor.NONE).noCollision().noOcclusion().strength(-1.0f, 3600000.0f)
                    .noLootTable().pushReaction(PushReaction.BLOCK));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HologramBlockEntity>> HOLOGRAM_TYPE =
            TYPES.register("hologram", () -> new BlockEntityType<>(HologramBlockEntity::new, Set.of(HOLOGRAM.get())));

    /** D'ou vient le joueur : pose a l'arrivee au spawn, effacee au retour. */
    public static final Supplier<AttachmentType<ReturnPoint>> RETURN = ATTACHMENTS.register(
            "spawn_return", () -> AttachmentType.builder(() -> ReturnPoint.NONE)
                    .serialize(ReturnPoint.MAP_CODEC, point -> !point.isNone())
                    .copyOnDeath()
                    .build());

    /** La version du spawn construit, gardee sur sa dimension ; 0 tant qu'il n'existe pas. */
    public static final Supplier<AttachmentType<Integer>> BUILT = ATTACHMENTS.register(
            "spawn_built", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("version"), value -> value > 0)
                    .build());

    private DoHySpawn() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
