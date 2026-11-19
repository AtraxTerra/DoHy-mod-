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
 * La reserve de l'hotel des ventes, chargee depuis le datapack
 * ({@code data/auction.json}, ecrit par {@code tools/market_offers.py}) : les
 * rayons, les offres possibles avec leur cote, les bornes des prix, les pseudos
 * des vendeurs, et les acheteurs de ce que le joueur met en vente.
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
     * @param buyers les acheteurs le prennent-ils quand le joueur le met en vente ?
     *               Non pour ce qui se fabrique surtout avec des matieres du shop.
     */
    public record Offer(String category, Identifier item, long quote, Range lot, Range stock, int weight,
                        Optional<Range> factor, boolean buyers) {
        static final Codec<Offer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("category").forGetter(Offer::category),
                Identifier.CODEC.fieldOf("item").forGetter(Offer::item),
                Codec.LONG.fieldOf("quote").forGetter(Offer::quote),
                Range.CODEC.fieldOf("lot").forGetter(Offer::lot),
                Range.CODEC.fieldOf("stock").forGetter(Offer::stock),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Offer::weight),
                Range.CODEC.optionalFieldOf("factor").forGetter(Offer::factor),
                Codec.BOOL.optionalFieldOf("buyers", true).forGetter(Offer::buyers)
        ).apply(instance, Offer::new));
    }

    /**
     * Un prix propose au joueur qui met en vente : la vente rapide, le prix
     * conseille, le prix fort.
     *
     * @param factor le prix, en part du prix conseille
     * @param hours  le temps qu'il faut en moyenne pour trouver preneur, en heures
     */
    public record Preset(String id, String label, double factor, double hours) {
        static final Codec<Preset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Preset::id),
                Codec.STRING.fieldOf("label").forGetter(Preset::label),
                Codec.DOUBLE.fieldOf("factor").forGetter(Preset::factor),
                Codec.DOUBLE.fieldOf("hours").forGetter(Preset::hours)
        ).apply(instance, Preset::new));
    }

    /**
     * Les acheteurs fictifs de l'hotel des ventes : ce que le joueur y met en vente.
     *
     * @param factor     le prix conseille, en part de la cote
     * @param slots      combien d'annonces le joueur peut avoir en meme temps
     * @param hours      au bout de combien d'heures une annonce invendue revient
     * @param presets    les prix proposes
     * @param categories un autre facteur pour les objets d'un rayon (les cles se revendent moins bien)
     */
    public record Buyers(double factor, int slots, double hours, List<Preset> presets,
                         java.util.Map<String, Double> categories) {
        static final Buyers DEFAULT = new Buyers(0.6d, 6, 48.0d, List.of(
                new Preset("quick", "Vente rapide", 0.85d, 1.0d / 3.0d),
                new Preset("fair", "Prix conseille", 1.0d, 3.0d),
                new Preset("high", "Prix fort", 1.15d, 27.0d)), java.util.Map.of("crates", 0.35d));

        static final Codec<Buyers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("factor", DEFAULT.factor()).forGetter(Buyers::factor),
                Codec.INT.optionalFieldOf("slots", DEFAULT.slots()).forGetter(Buyers::slots),
                Codec.DOUBLE.optionalFieldOf("hours", DEFAULT.hours()).forGetter(Buyers::hours),
                Preset.CODEC.listOf().optionalFieldOf("presets", DEFAULT.presets()).forGetter(Buyers::presets),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("categories", DEFAULT.categories())
                        .forGetter(Buyers::categories)
        ).apply(instance, Buyers::new));

        /** Le facteur du prix conseille pour un objet de ce rayon. */
        public double factor(String category) {
            return categories.getOrDefault(category, factor);
        }

        /** Le prix propose de cet identifiant, ou {@code null}. */
        public Preset preset(String id) {
            for (Preset preset : presets) {
                if (preset.id().equals(id)) {
                    return preset;
                }
            }
            return null;
        }
    }

    /**
     * @param perReroll  le prix du premier renouvellement paye de la journee ;
     *                   il double a chaque fois
     * @param maxRerolls combien de renouvellements payes par jour
     * @param buyers     les acheteurs de ce que le joueur met en vente
     */
    public record Config(long perReroll, int maxRerolls, List<Pricing> prices, List<String> sellers,
                         List<Category> categories, List<Offer> offers, Buyers buyers) {
        static final Config EMPTY = new Config(250_000L, 0, List.of(), List.of("?"), List.of(), List.of(),
                Buyers.DEFAULT);

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("per_reroll", 250_000L).forGetter(Config::perReroll),
                Codec.INT.optionalFieldOf("max_rerolls", 4).forGetter(Config::maxRerolls),
                Pricing.CODEC.listOf().fieldOf("prices").forGetter(Config::prices),
                Codec.STRING.listOf().fieldOf("sellers").forGetter(Config::sellers),
                Category.CODEC.listOf().fieldOf("categories").forGetter(Config::categories),
                Offer.CODEC.listOf().fieldOf("offers").forGetter(Config::offers),
                Buyers.CODEC.optionalFieldOf("buyers", Buyers.DEFAULT).forGetter(Config::buyers)
        ).apply(instance, Config::new));
    }
}
