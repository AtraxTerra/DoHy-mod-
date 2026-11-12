package fr.atraxterra.dohy.agri;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.List;
import java.util.Map;

/** Les reglages de l'agriculture, charges depuis {@code data/dohy/agriculture.json}. */
@EventBusSubscriber
public final class AgriConfig {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "agriculture.json");

    private static volatile Config config = Config.FALLBACK;

    private AgriConfig() {
    }

    public static Config config() {
        return config;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Configuration de l'agriculture introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Configuration de l'agriculture illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            Config parsed = result.getOrThrow();
            if (parsed.gmo().pool().size() < parsed.gmo().ingredients()) {
                DoHy.LOGGER.error("Agriculture : la recherche demande {} ingredients et le datapack n'en propose que {}",
                        parsed.gmo().ingredients(), parsed.gmo().pool().size());
                return false;
            }
            config = parsed;
            DoHy.LOGGER.info("Agriculture chargee : {} pains, {} ingredients de recherche, tournee toutes les {} ticks",
                    config.breads().size(), config.gmo().pool().size(), config.feedIntervalTicks());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la configuration de l'agriculture", error);
            return false;
        }
    }

    /** Les seuils de temperature de biome qui separent les trois climats. */
    public record Climate(double coldBelow, double hotFrom) {
        static final Climate FALLBACK = new Climate(0.4d, 0.9d);

        static final Codec<Climate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("cold_below", 0.4d).forGetter(Climate::coldBelow),
                Codec.DOUBLE.optionalFieldOf("hot_from", 0.9d).forGetter(Climate::hotFrom)
        ).apply(instance, Climate::new));
    }

    /**
     * Un ingredient possible d'une recette d'OGM.
     *
     * @param item l'objet
     * @param clue la devinette donnee quand il manque a la composition
     */
    public record Ingredient(Identifier item, String clue) {
        static final Codec<Ingredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Ingredient::item),
                Codec.STRING.fieldOf("clue").forGetter(Ingredient::clue)
        ).apply(instance, Ingredient::new));
    }

    /**
     * La recherche de la machine a OGM.
     *
     * @param minLevel        niveau de Fermier pour s'en servir
     * @param ingredients     nombre d'ingredients d'une recette
     * @param maxQuantity     quantite la plus haute qu'une recette peut demander
     * @param rangeWidth      largeur de la fourchette donnee par un indice de quantite
     * @param seedsPerCraft   graines rendues par une fabrication reussie
     * @param recipeLifetime  graines fabriquees au bout desquelles la recette change
     * @param craftXp         XP de Fermier d'une fabrication
     * @param discoveryXp     XP de Fermier de la decouverte d'une recette
     * @param countHintLevel  niveau de Fermier ou un echec dit aussi combien de quantites sont justes
     * @param secondClueLevel niveau de Fermier ou un echec donne un second indice
     * @param pool            les ingredients possibles
     */
    public record Gmo(int minLevel, int ingredients, int maxQuantity, int rangeWidth, int seedsPerCraft,
                      int recipeLifetime, long craftXp, long discoveryXp, int countHintLevel, int secondClueLevel,
                      List<Ingredient> pool) {
        static final Gmo FALLBACK = new Gmo(20, 9, 16, 4, 8, 2000, 60L, 600L, 30, 60, List.of());

        static final Codec<Gmo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("min_level", 20).forGetter(Gmo::minLevel),
                Codec.intRange(1, 12).optionalFieldOf("ingredients", 9).forGetter(Gmo::ingredients),
                Codec.intRange(1, 64).optionalFieldOf("max_quantity", 16).forGetter(Gmo::maxQuantity),
                Codec.intRange(1, 64).optionalFieldOf("range_width", 4).forGetter(Gmo::rangeWidth),
                Codec.intRange(1, 64).optionalFieldOf("seeds_per_craft", 8).forGetter(Gmo::seedsPerCraft),
                Codec.intRange(1, 1_000_000).optionalFieldOf("recipe_lifetime", 2000).forGetter(Gmo::recipeLifetime),
                Codec.LONG.optionalFieldOf("craft_xp", 60L).forGetter(Gmo::craftXp),
                Codec.LONG.optionalFieldOf("discovery_xp", 600L).forGetter(Gmo::discoveryXp),
                Codec.INT.optionalFieldOf("count_hint_level", 30).forGetter(Gmo::countHintLevel),
                Codec.INT.optionalFieldOf("second_clue_level", 60).forGetter(Gmo::secondClueLevel),
                Ingredient.CODEC.listOf().optionalFieldOf("pool", List.of()).forGetter(Gmo::pool)
        ).apply(instance, Gmo::new));

        /** La devinette d'un ingredient ; son identifiant s'il n'en a pas. */
        public String clue(Identifier item) {
            for (Ingredient ingredient : pool) {
                if (ingredient.item().equals(item)) {
                    return ingredient.clue();
                }
            }
            return item.toString();
        }
    }

    /**
     * Les machines electriques : moulin, machine a pain, biogenerateur.
     *
     * @param capacity         reserve d'energie de chaque machine, en FE
     * @param maxInput         FE acceptes par tick
     * @param millRatio        cereales moulues pour une farine
     * @param millEnergy       FE par farine
     * @param biogenPerCereal  FE tires d'une cereale brulee
     * @param biogenPerTick    FE produits par tick pendant la combustion
     * @param biogenHopper     cereales que le biogenerateur garde en attente
     * @param biogenPush       FE pousses par tick vers chaque machine voisine
     * @param bonusBreadLevel  niveau de Fermier ou une fournee peut rendre un pain de plus
     * @param bonusBreadChance chance de ce pain de plus
     */
    public record Machines(int capacity, int maxInput, int millRatio, int millEnergy, int biogenPerCereal,
                           int biogenPerTick, int biogenHopper, int biogenPush, int bonusBreadLevel,
                           double bonusBreadChance) {
        static final Machines FALLBACK = new Machines(20_000, 1_000, 3, 120, 400, 20, 64, 200, 40, 0.10d);

        static final Codec<Machines> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 100_000_000).optionalFieldOf("capacity", 20_000).forGetter(Machines::capacity),
                Codec.intRange(1, 100_000_000).optionalFieldOf("max_input", 1_000).forGetter(Machines::maxInput),
                Codec.intRange(1, 64).optionalFieldOf("mill_ratio", 3).forGetter(Machines::millRatio),
                Codec.intRange(0, 1_000_000).optionalFieldOf("mill_energy", 120).forGetter(Machines::millEnergy),
                Codec.intRange(1, 1_000_000).optionalFieldOf("biogen_per_cereal", 400).forGetter(Machines::biogenPerCereal),
                Codec.intRange(1, 1_000_000).optionalFieldOf("biogen_per_tick", 20).forGetter(Machines::biogenPerTick),
                Codec.intRange(1, 4096).optionalFieldOf("biogen_hopper", 64).forGetter(Machines::biogenHopper),
                Codec.intRange(0, 1_000_000).optionalFieldOf("biogen_push", 200).forGetter(Machines::biogenPush),
                Codec.INT.optionalFieldOf("bonus_bread_level", 40).forGetter(Machines::bonusBreadLevel),
                Codec.DOUBLE.optionalFieldOf("bonus_bread_chance", 0.10d).forGetter(Machines::bonusBreadChance)
        ).apply(instance, Machines::new));
    }

    /**
     * Une fournee de la machine a pain.
     *
     * @param id     le pain, sans espace de noms ({@code corn_bread})
     * @param inputs ce qu'une fournee consomme
     * @param count  pains rendus
     * @param energy FE de la fournee
     */
    public record Batch(String id, Map<Identifier, Integer> inputs, int count, int energy) {
        static final Codec<Batch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Batch::id),
                Codec.unboundedMap(Identifier.CODEC, Codec.intRange(1, 64)).fieldOf("inputs").forGetter(Batch::inputs),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Batch::count),
                Codec.intRange(0, 1_000_000).optionalFieldOf("energy", 300).forGetter(Batch::energy)
        ).apply(instance, Batch::new));
    }

    /** Un minerai que le flair du mineur peut faire tomber, et son poids. */
    public record Drop(Identifier item, int weight) {
        static final Codec<Drop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Drop::item),
                Codec.intRange(1, 1_000_000).optionalFieldOf("weight", 1).forGetter(Drop::weight)
        ).apply(instance, Drop::new));
    }

    /**
     * Les effets propres aux pains.
     *
     * @param lifeStealPerLevel part des degats infliges rendue en vie, par niveau de vol de vigueur
     * @param lastStandHealth   vie laissee par le dernier rempart
     * @param prospectChance    chance, pour une pierre cassee, de lacher un minerai
     * @param prospectCooldown  ticks entre deux minerais : un outil de zone ne doit pas en faire une rente
     * @param prospectDrops     les minerais possibles
     */
    public record Effects(double lifeStealPerLevel, double lastStandHealth, double prospectChance,
                          int prospectCooldown, List<Drop> prospectDrops) {
        static final Effects FALLBACK = new Effects(0.10d, 2.0d, 0.15d, 400, List.of());

        static final Codec<Effects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("life_steal_per_level", 0.10d).forGetter(Effects::lifeStealPerLevel),
                Codec.DOUBLE.optionalFieldOf("last_stand_health", 2.0d).forGetter(Effects::lastStandHealth),
                Codec.DOUBLE.optionalFieldOf("prospect_chance", 0.15d).forGetter(Effects::prospectChance),
                Codec.INT.optionalFieldOf("prospect_cooldown_ticks", 400).forGetter(Effects::prospectCooldown),
                Drop.CODEC.listOf().optionalFieldOf("prospect_drops", List.of()).forGetter(Effects::prospectDrops)
        ).apply(instance, Effects::new));
    }

    public record Config(double grassSeedChance, Climate climate, Gmo gmo, Machines machines, List<Batch> breads,
                         Effects effects, int feedIntervalTicks, int feedRadius, int feedMaxAnimals, int feedRation,
                         int troughCapacity, int waterCapacity, int waterPerBucket) {

        static final Config FALLBACK = new Config(0.04d, Climate.FALLBACK, Gmo.FALLBACK, Machines.FALLBACK,
                List.of(), Effects.FALLBACK, 2400, 6, 6, 4, 64, 32, 8);

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("grass_seed_chance", 0.04d).forGetter(Config::grassSeedChance),
                Climate.CODEC.optionalFieldOf("climate", Climate.FALLBACK).forGetter(Config::climate),
                Gmo.CODEC.optionalFieldOf("gmo", Gmo.FALLBACK).forGetter(Config::gmo),
                Machines.CODEC.optionalFieldOf("machines", Machines.FALLBACK).forGetter(Config::machines),
                Batch.CODEC.listOf().optionalFieldOf("breads", List.of()).forGetter(Config::breads),
                Effects.CODEC.optionalFieldOf("effects", Effects.FALLBACK).forGetter(Config::effects),
                Codec.INT.optionalFieldOf("feed_interval_ticks", 2400).forGetter(Config::feedIntervalTicks),
                Codec.INT.optionalFieldOf("feed_radius", 6).forGetter(Config::feedRadius),
                Codec.INT.optionalFieldOf("feed_max_animals", 6).forGetter(Config::feedMaxAnimals),
                Codec.intRange(1, 64).optionalFieldOf("feed_ration", 4).forGetter(Config::feedRation),
                Codec.INT.optionalFieldOf("trough_capacity", 64).forGetter(Config::troughCapacity),
                Codec.INT.optionalFieldOf("water_capacity", 32).forGetter(Config::waterCapacity),
                Codec.INT.optionalFieldOf("water_per_bucket", 8).forGetter(Config::waterPerBucket)
        ).apply(instance, Config::new));

        /** La fournee d'un pain, ou {@code null}. */
        public Batch batch(String id) {
            for (Batch batch : breads) {
                if (batch.id().equals(id)) {
                    return batch;
                }
            }
            return null;
        }
    }
}
