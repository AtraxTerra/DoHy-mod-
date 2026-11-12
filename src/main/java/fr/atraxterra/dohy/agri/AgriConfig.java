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
            config = result.getOrThrow();
            DoHy.LOGGER.info("Agriculture chargee : graine d'herbe {}, OGM {} + {} par niveau, tournee toutes les {} ticks",
                    config.grassSeedChance(), config.gmoBaseChance(), config.gmoChancePerLevel(),
                    config.feedIntervalTicks());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la configuration de l'agriculture", error);
            return false;
        }
    }

    public record Config(double grassSeedChance, int gmoMinLevel, int gmoParentSeeds, int gmoBoneMeal,
                         double gmoBaseChance, double gmoChancePerLevel, double gmoMaxChance, int gmoYield,
                         int gmoBonusLevel, long gmoFarmerXp, int feedIntervalTicks, int feedRadius,
                         int feedMaxAnimals, int troughCapacity, int waterCapacity, int waterPerBucket) {

        static final Config FALLBACK = new Config(0.04d, 20, 16, 4, 0.20d, 0.005d, 0.70d, 2, 60, 60L, 2400, 6, 6,
                64, 32, 8);

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("grass_seed_chance", 0.04d).forGetter(Config::grassSeedChance),
                Codec.INT.optionalFieldOf("gmo_min_level", 20).forGetter(Config::gmoMinLevel),
                Codec.INT.optionalFieldOf("gmo_parent_seeds", 16).forGetter(Config::gmoParentSeeds),
                Codec.INT.optionalFieldOf("gmo_bone_meal", 4).forGetter(Config::gmoBoneMeal),
                Codec.DOUBLE.optionalFieldOf("gmo_base_chance", 0.20d).forGetter(Config::gmoBaseChance),
                Codec.DOUBLE.optionalFieldOf("gmo_chance_per_level", 0.005d).forGetter(Config::gmoChancePerLevel),
                Codec.DOUBLE.optionalFieldOf("gmo_max_chance", 0.70d).forGetter(Config::gmoMaxChance),
                Codec.INT.optionalFieldOf("gmo_yield", 2).forGetter(Config::gmoYield),
                Codec.INT.optionalFieldOf("gmo_bonus_level", 60).forGetter(Config::gmoBonusLevel),
                Codec.LONG.optionalFieldOf("gmo_farmer_xp", 60L).forGetter(Config::gmoFarmerXp),
                Codec.INT.optionalFieldOf("feed_interval_ticks", 2400).forGetter(Config::feedIntervalTicks),
                Codec.INT.optionalFieldOf("feed_radius", 6).forGetter(Config::feedRadius),
                Codec.INT.optionalFieldOf("feed_max_animals", 6).forGetter(Config::feedMaxAnimals),
                Codec.INT.optionalFieldOf("trough_capacity", 64).forGetter(Config::troughCapacity),
                Codec.INT.optionalFieldOf("water_capacity", 32).forGetter(Config::waterCapacity),
                Codec.INT.optionalFieldOf("water_per_bucket", 8).forGetter(Config::waterPerBucket)
        ).apply(instance, Config::new));

        /** La chance de reussite d'un croisement a ce niveau de Fermier. */
        public double gmoChance(int farmerLevel) {
            return Math.max(0.0d, Math.min(gmoMaxChance, gmoBaseChance + gmoChancePerLevel * farmerLevel));
        }
    }
}
