package fr.atraxterra.dohy.blackmarket;

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
import java.util.Random;

/**
 * La reserve du receleur, chargee depuis le datapack ({@code data/dohy/black_market.json},
 * ecrit par {@code tools/black_market.py}) : la marchandise volee, la
 * contrebande, le lot sans etiquette, ce qu'il rachete, et ses phrases.
 */
@EventBusSubscriber
public final class BlackMarketConfig {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "black_market.json");

    private static volatile Config config = Config.EMPTY;

    private BlackMarketConfig() {
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
            DoHy.LOGGER.error("Marche noir introuvable : data/{}/{}", LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Marche noir illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Marche noir charge : {} offres volees, {} de contrebande, {} rachats possibles",
                    config.stolen().offers().size(), config.contraband().offers().size(),
                    config.buyback().demands().size());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement du marche noir", error);
            return false;
        }
    }

    /** Deux bornes, {@code [min, max]}. */
    public record Range(double min, double max) {
        static final Codec<Range> CODEC = Codec.DOUBLE.listOf(2, 2)
                .xmap(list -> new Range(Math.min(list.get(0), list.get(1)), Math.max(list.get(0), list.get(1))),
                        range -> List.of(range.min(), range.max()));

        int pick(Random random) {
            int low = (int) Math.round(min);
            int high = (int) Math.round(max);
            return low + (high > low ? random.nextInt(high - low + 1) : 0);
        }

        double roll(Random random) {
            return min + (max - min) * random.nextDouble();
        }
    }

    /** Une offre possible : sa valeur boutique, son lot, son stock du jour, son poids au tirage. */
    public record Offer(Identifier item, long value, Range lot, Range stock, int weight, int jobXp) {
        static final Codec<Offer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Offer::item),
                Codec.LONG.fieldOf("value").forGetter(Offer::value),
                Range.CODEC.fieldOf("lot").forGetter(Offer::lot),
                Range.CODEC.fieldOf("stock").forGetter(Offer::stock),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Offer::weight),
                Codec.INT.optionalFieldOf("job_xp", 0).forGetter(Offer::jobXp)
        ).apply(instance, Offer::new));
    }

    /** Un rayon : combien d'offres par jour, a quel prix (en part de la valeur), tirees d'ou. */
    public record Shelf(int count, Range factor, List<Offer> offers) {
        static final Codec<Shelf> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("count").forGetter(Shelf::count),
                Range.CODEC.fieldOf("factor").forGetter(Shelf::factor),
                Offer.CODEC.listOf().fieldOf("offers").forGetter(Shelf::offers)
        ).apply(instance, Shelf::new));
    }

    public record Stack(Identifier item, int count) {
        static final Codec<Stack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Stack::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Stack::count)
        ).apply(instance, Stack::new));
    }

    /** Ce que peut contenir le lot sans etiquette. */
    public record Outcome(String label, int weight, List<Stack> items) {
        static final Codec<Outcome> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("label").forGetter(Outcome::label),
                Codec.INT.fieldOf("weight").forGetter(Outcome::weight),
                Stack.CODEC.listOf().fieldOf("items").forGetter(Outcome::items)
        ).apply(instance, Outcome::new));
    }

    public record Mystery(long price, int stock, List<Outcome> outcomes) {
        static final Mystery NONE = new Mystery(0L, 0, List.of());
        static final Codec<Mystery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("price").forGetter(Mystery::price),
                Codec.INT.fieldOf("stock").forGetter(Mystery::stock),
                Outcome.CODEC.listOf().fieldOf("outcomes").forGetter(Mystery::outcomes)
        ).apply(instance, Mystery::new));
    }

    /** Ce que le receleur peut racheter : la valeur, combien de pieces par jour, le poids au tirage. */
    public record Demand(Identifier item, long value, Range cap, int weight) {
        static final Codec<Demand> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Demand::item),
                Codec.LONG.fieldOf("value").forGetter(Demand::value),
                Range.CODEC.fieldOf("cap").forGetter(Demand::cap),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Demand::weight)
        ).apply(instance, Demand::new));
    }

    public record Buyback(int count, Range factor, List<Demand> demands) {
        static final Codec<Buyback> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("count").forGetter(Buyback::count),
                Range.CODEC.fieldOf("factor").forGetter(Buyback::factor),
                Demand.CODEC.listOf().fieldOf("demands").forGetter(Buyback::demands)
        ).apply(instance, Buyback::new));
    }

    /**
     * @param grief le poids du grief qu'une marchandise volee donne a la nation volee
     */
    public record Config(double grief, Shelf stolen, Shelf contraband, Mystery mystery, Buyback buyback,
                         List<String> quotes) {
        static final Shelf EMPTY_SHELF = new Shelf(0, new Range(1.0d, 1.0d), List.of());
        static final Config EMPTY = new Config(0.0d, EMPTY_SHELF, EMPTY_SHELF, Mystery.NONE,
                new Buyback(0, new Range(0.0d, 0.0d), List.of()), List.of());

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("grief", 4.0d).forGetter(Config::grief),
                Shelf.CODEC.fieldOf("stolen").forGetter(Config::stolen),
                Shelf.CODEC.fieldOf("contraband").forGetter(Config::contraband),
                Mystery.CODEC.fieldOf("mystery").forGetter(Config::mystery),
                Buyback.CODEC.fieldOf("buyback").forGetter(Config::buyback),
                Codec.STRING.listOf().optionalFieldOf("quotes", List.of()).forGetter(Config::quotes)
        ).apply(instance, Config::new));
    }
}
