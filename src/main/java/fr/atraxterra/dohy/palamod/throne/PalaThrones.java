package fr.atraxterra.dohy.palamod.throne;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Set;

/**
 * Les trones de metier (PalaJobs, {@code BlockThroneBase} et ses quatre blocs).
 *
 * <p>Ce que dit le code d'origine :
 * <ul>
 *   <li>un trone par metier, verrouille au <b>niveau 95</b> (la table des
 *       verrous l'avait deja : elle s'applique a la pose et au clic droit) ;</li>
 *   <li>la recette {@code EOE / OFO / EOE} : fragments d'endium aux coins, bloc
 *       de findium au centre, et l'objet du metier sur les bords ;</li>
 *   <li>clic droit : on s'assoit ({@code EntitySittableBlock}, 0,6 bloc) ;</li>
 *   <li>le premier qui le pose y grave son nom ({@code owner} du NBT), qui
 *       s'affiche en rouge au-dessus du trone et le suit quand on le casse ;</li>
 *   <li>durete 1, materiau roche ; la boite de selection va de 0,5 a 2 blocs.</li>
 * </ul>
 */
public final class PalaThrones {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);

    /**
     * Le nom grave sur le trone ({@code owner}). Persistant, et synchronise pour
     * l'infobulle.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> OWNER =
            COMPONENTS.registerComponentType("throne_owner",
                    builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final DeferredBlock<ThroneBlock> MINER_THRONE = BLOCKS.registerBlock(
            "miner_throne", properties -> new ThroneBlock(Job.MINER, properties), PalaThrones::properties);
    public static final DeferredBlock<ThroneBlock> FARMER_THRONE = BLOCKS.registerBlock(
            "farmer_throne", properties -> new ThroneBlock(Job.FARMER, properties), PalaThrones::properties);
    public static final DeferredBlock<ThroneBlock> HUNTER_THRONE = BLOCKS.registerBlock(
            "hunter_throne", properties -> new ThroneBlock(Job.HUNTER, properties), PalaThrones::properties);
    public static final DeferredBlock<ThroneBlock> ALCHEMIST_THRONE = BLOCKS.registerBlock(
            "alchemist_throne", properties -> new ThroneBlock(Job.ALCHEMIST, properties), PalaThrones::properties);

    public static final DeferredItem<ThroneItem> MINER_THRONE_ITEM = ITEMS.registerItem(
            "miner_throne", properties -> new ThroneItem(MINER_THRONE.get(), item(properties)));
    public static final DeferredItem<ThroneItem> FARMER_THRONE_ITEM = ITEMS.registerItem(
            "farmer_throne", properties -> new ThroneItem(FARMER_THRONE.get(), item(properties)));
    public static final DeferredItem<ThroneItem> HUNTER_THRONE_ITEM = ITEMS.registerItem(
            "hunter_throne", properties -> new ThroneItem(HUNTER_THRONE.get(), item(properties)));
    public static final DeferredItem<ThroneItem> ALCHEMIST_THRONE_ITEM = ITEMS.registerItem(
            "alchemist_throne", properties -> new ThroneItem(ALCHEMIST_THRONE.get(), item(properties)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThroneBlockEntity>> THRONE_ENTITY =
            BLOCK_ENTITIES.register("throne", () -> new BlockEntityType<>(ThroneBlockEntity::new, Set.of(
                    MINER_THRONE.get(), FARMER_THRONE.get(), HUNTER_THRONE.get(), ALCHEMIST_THRONE.get())));

    /**
     * Le siege invisible ou s'assoit le joueur ({@code EntitySittableBlock}). Il
     * n'est jamais sauvegarde : un joueur deconnecte se releve.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ThroneSeat>> SEAT = ENTITIES.register(
            "throne_seat", key -> EntityType.Builder.<ThroneSeat>of(ThroneSeat::new, MobCategory.MISC)
                    .sized(0.0f, 0.0f)
                    .noSave()
                    .noSummon()
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            MINER_THRONE_ITEM, FARMER_THRONE_ITEM, HUNTER_THRONE_ITEM, ALCHEMIST_THRONE_ITEM);

    private PalaThrones() {
    }

    /** {@code Material.rock}, {@code setHardness(1F)} ; ni lumiere ni occlusion : le modele deborde. */
    private static BlockBehaviour.Properties properties(BlockBehaviour.Properties properties) {
        return properties.strength(1.0f).sound(SoundType.STONE).noOcclusion();
    }

    /** Un trone par emplacement, comme les autres objets uniques de PalaJobs. */
    private static Item.Properties item(Item.Properties properties) {
        return properties.stacksTo(1).useBlockDescriptionPrefix();
    }

    /** Le trone d'un metier. */
    public static DeferredBlock<ThroneBlock> of(Job job) {
        return switch (job) {
            case MINER -> MINER_THRONE;
            case FARMER -> FARMER_THRONE;
            case HUNTER -> HUNTER_THRONE;
            case ALCHEMIST -> ALCHEMIST_THRONE;
        };
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }
}
