package fr.atraxterra.dohy.auction;

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
import java.util.Optional;

/**
 * La reserve du marche, chargee depuis le datapack ({@code data/auction.json},
 * ecrit par {@code tools/market_offers.py}) : les rayons, les offres possibles
 * avec leur cote, les bornes des prix et les pseudos des vendeurs.
 */
@EventBusSubscriber
public final class AuctionConfig {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "auction.json");

    private static volatile Config config = Config.EMPTY;

    private AuctionConfig() {
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
            DoHy.LOGGER.error("Reserve du marche introuvable : data/{}/{}", LOCATION.getNamespace(),
                    LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Reserve du marche illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Marche charge : {} offres dans {} rayons", config.offers().size(),
                    config.categories().size());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement du marche", error);
            return false;
        }
    }

    /** Deux bornes, {@code [min, max]}. */
    public record Range(double min, double max) {
        static final Codec<Range> CODEC = Codec.DOUBLE.listOf(2, 2)
                .xmap(list -> new Range(Math.min(list.get(0), list.get(1)), Math.max(list.get(0), list.get(1))),
                        range -> List.of(range.min(), range.max()));

        int pick(java.util.Random random) {
            int low = (int) Math.round(min);
            int high = (int) Math.round(max);
            return low + (high > low ? random.nextInt(high - low + 1) : 0);
        }

        double roll(java.util.Random random) {
            return min + (max - min) * random.nextDouble();
        }
    }

    /**
     * Un rayon de l'ecran.
     *
     * @param icon  le losange de l'intertitre
     * @param count combien d'annonces il recoit chaque jour
     */
    public record Category(String id, String title, String icon, int count) {
        static final Codec<Category> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Category::id),
                Codec.STRING.fieldOf("title").forGetter(Category::title),
                Codec.STRING.optionalFieldOf("icon", "resources").forGetter(Category::icon),
                Codec.INT.fieldOf("count").forGetter(Category::count)
        ).apply(instance, Category::new));
    }

    /**
     * Une facon de fixer un prix : la bonne affaire, le prix courant, le
     * vendeur gourmand.
     *
     * @param chance sa frequence, relative aux autres
     * @param factor les bornes du prix, en part de la cote
     */
    public record Pricing(String label, int chance, Range factor) {
        static final Codec<Pricing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("label").forGetter(Pricing::label),
                Codec.INT.fieldOf("chance").forGetter(Pricing::chance),
                Range.CODEC.fieldOf("factor").forGetter(Pricing::factor)
        ).apply(instance, Pricing::new));
    }

    /**
     * Une offre possible.
     *
     * @param quote  la cote d'un objet : sa valeur boutique
     * @param lot    combien d'objets par annonce
     * @param stock  combien de lots l'annonce propose
     * @param weight sa chance d'etre tiree dans son rayon
     * @param factor ses propres bornes de prix, sinon celles de {@link Config#prices}
     */
    public record Offer(String category, Identifier item, long quote, Range lot, Range stock, int weight,
                        Optional<Range> factor) {
        static final Codec<Offer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("category").forGetter(Offer::category),
                Identifier.CODEC.fieldOf("item").forGetter(Offer::item),
                Codec.LONG.fieldOf("quote").forGetter(Offer::quote),
                Range.CODEC.fieldOf("lot").forGetter(Offer::lot),
                Range.CODEC.fieldOf("stock").forGetter(Offer::stock),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Offer::weight),
                Range.CODEC.optionalFieldOf("factor").forGetter(Offer::factor)
        ).apply(instance, Offer::new));
    }

    /**
     * @param perReroll  le prix du premier renouvellement paye de la journee ;
     *                   il double a chaque fois
     * @param maxRerolls combien de renouvellements payes par jour
     */
    public record Config(long perReroll, int maxRerolls, List<Pricing> prices, List<String> sellers,
                         List<Category> categories, List<Offer> offers) {
        static final Config EMPTY = new Config(250_000L, 0, List.of(), List.of("?"), List.of(), List.of());

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("per_reroll", 250_000L).forGetter(Config::perReroll),
                Codec.INT.optionalFieldOf("max_rerolls", 4).forGetter(Config::maxRerolls),
                Pricing.CODEC.listOf().fieldOf("prices").forGetter(Config::prices),
                Codec.STRING.listOf().fieldOf("sellers").forGetter(Config::sellers),
                Category.CODEC.listOf().fieldOf("categories").forGetter(Config::categories),
                Offer.CODEC.listOf().fieldOf("offers").forGetter(Config::offers)
        ).apply(instance, Config::new));
    }
}
