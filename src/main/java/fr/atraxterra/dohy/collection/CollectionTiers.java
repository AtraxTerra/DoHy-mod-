package fr.atraxterra.dohy.collection;

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
import java.util.Optional;

/**
 * Les paliers de collection et leurs recompenses, charges depuis le datapack.
 *
 * <p>Deux courbes, parce que 5 000 blocs se font en une soiree avec une ferme
 * mais en trois semaines a la main : les items non renouvelables ont des paliers
 * bien plus bas que les farmables. Les items ne sont pas enumeres dans le
 * fichier — la courbe applicable se deduit du tier de l'item dans le catalogue
 * de prix, si bien qu'un item ajoute a {@code rates.csv} obtient sa collection
 * sans autre intervention.
 */
@EventBusSubscriber
public final class CollectionTiers {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "collections.json");

    private static volatile Config config = Config.FALLBACK;

    private CollectionTiers() {
    }

    public static Config config() {
        return config;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    /** Recharge la configuration. En cas d'echec, l'ancienne reste en service. */
    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Paliers de collection introuvables : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }

        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Paliers de collection illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Paliers de collection charges : {} courbes, {} exceptions",
                    config.curves().size(), config.exceptions().size());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des paliers de collection", error);
            return false;
        }
    }

    /** Une courbe de paliers, et les tiers de prix auxquels elle s'applique. */
    public record Curve(List<String> appliesToTiers, List<Long> tiers) {
        static final Codec<Curve> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("_applies_to_tiers", List.of())
                        .forGetter(Curve::appliesToTiers),
                Codec.LONG.listOf().fieldOf("tiers").forGetter(Curve::tiers)
        ).apply(instance, Curve::new));
    }

    /** La recompense d'un palier. */
    public record Reward(boolean unlockSell, double floorBonusPct, long globalXp) {
        static final Codec<Reward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("unlock_sell", true).forGetter(Reward::unlockSell),
                Codec.DOUBLE.optionalFieldOf("floor_bonus_pct", 0.0d).forGetter(Reward::floorBonusPct),
                Codec.LONG.optionalFieldOf("global_xp", 0L).forGetter(Reward::globalXp)
        ).apply(instance, Reward::new));
    }

    /** Un item qui ne suit pas la courbe de son tier. */
    public record Exception_(Optional<List<Long>> tiers, boolean floorBonusDisabled) {
        static final Codec<Exception_> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.listOf().optionalFieldOf("tiers").forGetter(Exception_::tiers),
                Codec.BOOL.optionalFieldOf("floor_bonus_disabled", false)
                        .forGetter(Exception_::floorBonusDisabled)
        ).apply(instance, Exception_::new));
    }

    /** Regles de prestige. Voir le commentaire de {@code collections.json}. */
    public record Prestige(int maxLevel, double s0BonusPerLevel,
                           double floorBonusPerLevel, long globalXp) {
        static final Codec<Prestige> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("max_level", 5).forGetter(Prestige::maxLevel),
                Codec.DOUBLE.optionalFieldOf("s0_bonus_per_level", 0.5d).forGetter(Prestige::s0BonusPerLevel),
                Codec.DOUBLE.optionalFieldOf("floor_bonus_per_level", 0.02d)
                        .forGetter(Prestige::floorBonusPerLevel),
                Codec.LONG.optionalFieldOf("global_xp", 500L).forGetter(Prestige::globalXp)
        ).apply(instance, Prestige::new));
    }

    public record Config(Map<String, Curve> curves, List<Reward> rewards,
                         Map<Identifier, Exception_> exceptions, Prestige prestige,
                         boolean sellRequiresCollection) {

        static final Prestige FALLBACK_PRESTIGE = new Prestige(5, 0.5d, 0.02d, 500L);

        /**
         * Repli si le fichier manque : les courbes de {@code DESIGN.md}, en dur.
         * Mieux vaut des paliers corrects qu'aucune collection.
         *
         * <p>Le repli ne verrouille pas la vente ({@code false} en dernier
         * argument) : un fichier manquant ne doit pas rendre l'economie
         * inutilisable, et c'est de toute facon le reglage retenu.
         */
        static final Config FALLBACK = new Config(
                Map.of(
                        "found", new Curve(List.of("FOUND", "RARE"),
                                List.of(10L, 50L, 100L, 500L, 1500L, 2500L,
                                        5000L, 10000L, 25000L, 50000L, 60000L, 70000L,
                                        80000L, 90000L, 100000L, 115000L, 130000L,
                                        145000L, 160000L, 180000L, 200000L, 220000L,
                                        240000L, 265000L, 300000L)),
                        "farmable", new Curve(List.of("FARM", "FOOD", "MOB"),
                                List.of(100L, 1000L, 5000L, 25000L, 100000L, 250000L,
                                        500000L, 1000000L, 2500000L, 5000000L, 6000000L,
                                        7000000L, 8000000L, 9000000L, 10000000L,
                                        11500000L, 13000000L, 14500000L, 16000000L,
                                        18000000L, 20000000L, 22000000L, 24000000L,
                                        26500000L, 30000000L))),
                List.of(
                        new Reward(true, 0.060d, 10L),
                        new Reward(true, 0.085d, 13L),
                        new Reward(true, 0.104d, 18L),
                        new Reward(true, 0.120d, 24L),
                        new Reward(true, 0.134d, 31L),
                        new Reward(true, 0.147d, 42L),
                        new Reward(true, 0.159d, 55L),
                        new Reward(true, 0.170d, 74L),
                        new Reward(true, 0.180d, 98L),
                        new Reward(true, 0.190d, 130L),
                        new Reward(true, 0.199d, 170L),
                        new Reward(true, 0.208d, 230L),
                        new Reward(true, 0.216d, 310L),
                        new Reward(true, 0.224d, 410L),
                        new Reward(true, 0.232d, 540L),
                        new Reward(true, 0.240d, 720L),
                        new Reward(true, 0.247d, 960L),
                        new Reward(true, 0.255d, 1270L),
                        new Reward(true, 0.262d, 1700L),
                        new Reward(true, 0.268d, 2260L),
                        new Reward(true, 0.275d, 3000L),
                        new Reward(true, 0.281d, 3990L),
                        new Reward(true, 0.288d, 5310L),
                        new Reward(true, 0.294d, 7060L),
                        new Reward(true, 0.300d, 9390L)),
                Map.of(),
                new Prestige(5, 0.5d, 0.02d, 500L),
                false);

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Curve.CODEC).fieldOf("curves").forGetter(Config::curves),
                Reward.CODEC.listOf().fieldOf("rewards_per_tier").forGetter(Config::rewards),
                Codec.unboundedMap(Identifier.CODEC, Exception_.CODEC)
                        .optionalFieldOf("exceptions", Map.of()).forGetter(Config::exceptions),
                Prestige.CODEC.optionalFieldOf("prestige", FALLBACK_PRESTIGE).forGetter(Config::prestige),
                // Par defaut false : la vente n'attend aucune collection. Voir
                // le commentaire de collections.json.
                Codec.BOOL.optionalFieldOf("sell_requires_collection", false)
                        .forGetter(Config::sellRequiresCollection)
        ).apply(instance, Config::new));

        /** Les paliers applicables a un item, selon son tier de prix. */
        public List<Long> tiersFor(Identifier id, String priceTier) {
            Exception_ exception = exceptions.get(id);
            if (exception != null && exception.tiers().isPresent()) {
                return exception.tiers().get();
            }
            for (Curve curve : curves.values()) {
                if (curve.appliesToTiers().stream().anyMatch(t -> t.equalsIgnoreCase(priceTier))) {
                    return curve.tiers();
                }
            }
            return List.of();
        }

        public boolean floorBonusAllowed(Identifier id) {
            Exception_ exception = exceptions.get(id);
            return exception == null || !exception.floorBonusDisabled();
        }

        public Reward reward(int tierIndex) {
            if (tierIndex < 0 || tierIndex >= rewards.size()) {
                return new Reward(false, 0.0d, 0L);
            }
            return rewards.get(tierIndex);
        }

        public int size() {
            return curves.size();
        }
    }
}
