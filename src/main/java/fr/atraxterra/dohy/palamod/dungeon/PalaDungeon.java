package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Les donjons de la V12 de Paladium (« Univers des donjons » du wiki) : le
 * coeur jouable — cles, paliers, salles, monstres, boss et coffres.
 *
 * <p>Il n'y a pas de code d'origine : les donjons sont arrives apres la
 * 10.3.0 de PalaMod, et le wiki ne decrit que les regles et les recompenses.
 * Tout le reste — la forme des salles, les chiffres des monstres, les
 * capacites des boss, le contenu des salles boost et marchande — est de DoHy,
 * et documente comme tel dans PALAMOD.md.
 *
 * <p>Une dimension vide, {@code palamod:dungeon}, accueille les instances :
 * une region par partie, les salles construites a la volee par
 * {@link DungeonRoom} puis effacees. Les noms sont ecrits en toutes lettres
 * pour {@code check_assets.py}.
 */
public final class PalaDungeon {

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "dungeon"));

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);

    // -- Composants et pieces jointes -------------------------------------------

    /** L'XP de metier d'une bouteille ; absente, 1000 (« Bottle Farmer 1000xp »). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> JOB_XP =
            COMPONENTS.registerComponentType("job_xp", builder -> builder
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Le dernier palier reussi de chaque donjon, par cle de donjon. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> PROGRESS = ATTACHMENTS.register(
            "dungeon_progress", () -> AttachmentType.<Map<String, Integer>>builder(() -> new HashMap<>())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("cleared")
                                    .xmap(map -> (Map<String, Integer>) new HashMap<>(map), map -> map),
                            map -> !map.isEmpty())
                    .copyOnDeath()
                    .build());

    /**
     * Ou ramener un joueur : pose a l'entree, effacee a la sortie. Elle sert
     * a celui qui se reconnecte dans la dimension apres un arret du serveur,
     * les parties n'etant gardees qu'en memoire.
     */
    public static final Supplier<AttachmentType<ReturnPoint>> RETURN = ATTACHMENTS.register(
            "dungeon_return", () -> AttachmentType.builder(() -> ReturnPoint.NONE)
                    .serialize(ReturnPoint.MAP_CODEC, point -> !point.isNone())
                    .copyOnDeath()
                    .build());

    /** La prochaine region libre de la dimension, gardee sur l'overworld. */
    public static final Supplier<AttachmentType<Integer>> NEXT_REGION = ATTACHMENTS.register(
            "dungeon_next_region", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("next"), value -> value > 0)
                    .build());

    // -- Cles ---------------------------------------------------------------------

    public static final DeferredItem<DungeonKeyItem> TALIKUS_KEY = ITEMS.registerItem("talikus_key",
            properties -> new DungeonKeyItem(Dungeon.TALIKUS, properties.stacksTo(16)));
    public static final DeferredItem<DungeonKeyItem> NIMBRIA_KEY = ITEMS.registerItem("nimbria_key",
            properties -> new DungeonKeyItem(Dungeon.NIMBRIA, properties.stacksTo(16)));
    public static final DeferredItem<DungeonKeyItem> VITALYS_KEY = ITEMS.registerItem("vitalys_key",
            properties -> new DungeonKeyItem(Dungeon.VITALYS, properties.stacksTo(16)));
    public static final DeferredItem<DungeonKeyItem> MANELIOS_KEY = ITEMS.registerItem("manelios_key",
            properties -> new DungeonKeyItem(Dungeon.MANELIOS, properties.stacksTo(16)));

    // -- Sacs ---------------------------------------------------------------------

    /** « Dungeon Leather Backpack » : 18 piles. */
    public static final DeferredItem<DungeonBackpackItem> LEATHER_BACKPACK = ITEMS.registerItem(
            "dungeon_leather_backpack", properties -> new DungeonBackpackItem(18, properties.stacksTo(1)));
    /** « Backpack de donjon tier 2 » (Nimbria, paliers 40 et 50) : 27 piles. */
    public static final DeferredItem<DungeonBackpackItem> BACKPACK_T2 = ITEMS.registerItem(
            "dungeon_backpack_t2", properties -> new DungeonBackpackItem(27, properties.stacksTo(1)));
    /** « Backpack de donjon tier 3 » (Nimbria, paliers 60 et 70) : 36 piles. */
    public static final DeferredItem<DungeonBackpackItem> BACKPACK_T3 = ITEMS.registerItem(
            "dungeon_backpack_t3", properties -> new DungeonBackpackItem(36, properties.stacksTo(1)));

    // -- Bouteilles d'XP de metier --------------------------------------------------

    public static final DeferredItem<JobBottleItem> FARMER_BOTTLE = ITEMS.registerItem("job_bottle_farmer",
            properties -> new JobBottleItem(Job.FARMER, properties.stacksTo(64)));
    public static final DeferredItem<JobBottleItem> MINER_BOTTLE = ITEMS.registerItem("job_bottle_miner",
            properties -> new JobBottleItem(Job.MINER, properties.stacksTo(64)));
    public static final DeferredItem<JobBottleItem> HUNTER_BOTTLE = ITEMS.registerItem("job_bottle_hunter",
            properties -> new JobBottleItem(Job.HUNTER, properties.stacksTo(64)));
    public static final DeferredItem<JobBottleItem> ALCHEMIST_BOTTLE = ITEMS.registerItem("job_bottle_alchemist",
            properties -> new JobBottleItem(Job.ALCHEMIST, properties.stacksTo(64)));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            TALIKUS_KEY, NIMBRIA_KEY, VITALYS_KEY, MANELIOS_KEY,
            LEATHER_BACKPACK, BACKPACK_T2, BACKPACK_T3,
            FARMER_BOTTLE, MINER_BOTTLE, HUNTER_BOTTLE, ALCHEMIST_BOTTLE);

    private PalaDungeon() {
    }

    public static Item key(Dungeon dungeon) {
        return switch (dungeon) {
            case TALIKUS -> TALIKUS_KEY.get();
            case NIMBRIA -> NIMBRIA_KEY.get();
            case VITALYS -> VITALYS_KEY.get();
            case MANELIOS -> MANELIOS_KEY.get();
        };
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
