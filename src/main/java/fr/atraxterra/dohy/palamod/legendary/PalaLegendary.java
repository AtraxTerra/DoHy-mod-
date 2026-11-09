package fr.atraxterra.dohy.palamod.legendary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
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
 * Les pierres legendaires et le gant en endium ({@code LegendaryStone},
 * {@code ItemEndiumGauntlet}, {@code EndiumGauntletRecipe}).
 *
 * <p>Dans PalaMod, la pierre aleatoire venait des lucky blocks et des
 * evenements du serveur, le gant complet d'un lucky block rarissime. Ici,
 * des caisses ({@code data/crates.json}). Les noms sont ecrits en toutes
 * lettres pour {@code check_assets.py}.
 */
public final class PalaLegendary {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, PalaMod.NAMESPACE);

    // -- Composants et pieces jointes -------------------------------------------

    /** {@code LAST_USE} d'une pierre : sa derniere utilisation, en millisecondes reelles. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> LAST_USE =
            COMPONENTS.registerComponentType("legendary_last_use", builder -> builder
                    .persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Le contenu du gant. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GauntletData>> GAUNTLET =
            COMPONENTS.registerComponentType("gauntlet", builder -> builder
                    .persistent(GauntletData.CODEC).networkSynchronized(GauntletData.STREAM_CODEC));

    /**
     * {@code legendary_<EFFET>} : le verrou de 10 minutes de la teleportation et
     * du chaos, par joueur. Il suit la mort — l'original le perdait, ce qui
     * offrait une mort volontaire pour le lever.
     */
    public static final Supplier<AttachmentType<Map<String, Long>>> PLAYER_LOCKS = ATTACHMENTS.register(
            "legendary_locks", () -> AttachmentType.<Map<String, Long>>builder(() -> new HashMap<>())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("last")
                                    .xmap(map -> (Map<String, Long>) new HashMap<>(map), map -> map),
                            map -> !map.isEmpty())
                    .copyOnDeath()
                    .build());

    // -- Objets -----------------------------------------------------------------

    public static final DeferredItem<LegendaryStoneItem> RANDOM = ITEMS.registerItem("legendary_stone_random",
            properties -> new LegendaryStoneItem(LegendaryEffect.RANDOM, properties.stacksTo(1)));
    public static final DeferredItem<LegendaryStoneItem> TELEPORTATION = ITEMS.registerItem(
            "legendary_stone_teleportation",
            properties -> new LegendaryStoneItem(LegendaryEffect.TELEPORTATION, properties.stacksTo(1)));
    public static final DeferredItem<LegendaryStoneItem> INVISIBILITY = ITEMS.registerItem(
            "legendary_stone_invisibility",
            properties -> new LegendaryStoneItem(LegendaryEffect.INVISIBILITY, properties.stacksTo(1)));
    public static final DeferredItem<LegendaryStoneItem> FORTUNE = ITEMS.registerItem("legendary_stone_fortune",
            properties -> new LegendaryStoneItem(LegendaryEffect.FORTUNE, properties.stacksTo(1)));
    public static final DeferredItem<LegendaryStoneItem> POWER = ITEMS.registerItem("legendary_stone_power",
            properties -> new LegendaryStoneItem(LegendaryEffect.POWER, properties.stacksTo(1)));
    public static final DeferredItem<LegendaryStoneItem> JOBS = ITEMS.registerItem("legendary_stone_jobs",
            properties -> new LegendaryStoneItem(LegendaryEffect.JOBS, properties.stacksTo(1)));
    public static final DeferredItem<LegendaryStoneItem> CHAOS = ITEMS.registerItem("legendary_stone_chaos",
            properties -> new LegendaryStoneItem(LegendaryEffect.CHAOS, properties.stacksTo(1)));

    public static final DeferredItem<EndiumGauntletItem> ENDIUM_GAUNTLET = ITEMS.registerItem("endium_gauntlet",
            properties -> new EndiumGauntletItem(properties.stacksTo(1)));

    /** L'ordre de l'enumeration, et celui de l'onglet. */
    private static final List<DeferredItem<LegendaryStoneItem>> STONES =
            List.of(RANDOM, TELEPORTATION, INVISIBILITY, FORTUNE, POWER, JOBS, CHAOS);

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            RANDOM, TELEPORTATION, INVISIBILITY, FORTUNE, POWER, JOBS, CHAOS, ENDIUM_GAUNTLET);

    // -- Recette du gant --------------------------------------------------------

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<EndiumGauntletRecipe>> GAUNTLET_RECIPE =
            SERIALIZERS.register("endium_gauntlet", () -> new RecipeSerializer<>(
                    MapCodec.unit(EndiumGauntletRecipe.INSTANCE), StreamCodec.unit(EndiumGauntletRecipe.INSTANCE)));

    private PalaLegendary() {
    }

    /** La pierre d'un effet. */
    public static Item stone(LegendaryEffect effect) {
        return STONES.get(effect.ordinal()).get();
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        SERIALIZERS.register(modBus);
    }
}
