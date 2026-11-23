package fr.atraxterra.dohy.faction.tech;

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

/**
 * Les chiffres du laboratoire de recherche (lot 23c), charges depuis le datapack
 * ({@code data/dohy/research_lab.json}) et recharges par {@code /dohy reload}.
 */
@EventBusSubscriber
public final class ResearchLabConfig {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "research_lab.json");

    private static volatile Config config = Config.FALLBACK;

    private ResearchLabConfig() {
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
            DoHy.LOGGER.error("Laboratoire de recherche : configuration introuvable (data/{}/{})",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Laboratoire de recherche : configuration illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Laboratoire de recherche : {} FE par tick, {} points par heure, reserve de {} points",
                    config.energyPerTick(), config.pointsPerHour(), config.bufferPoints());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Laboratoire de recherche : echec du chargement de la configuration", error);
            return false;
        }
    }

    /**
     * @param energyPerTick   l'energie consommee par tick de marche, en FE (avant les bonus de l'Energie)
     * @param energyCapacity  la reserve d'energie du laboratoire, en FE
     * @param energyMaxInput  ce qu'il accepte par transfert, en FE
     * @param pointsPerHour   les points produits par heure de marche, avant les bonus
     * @param bufferPoints    les points qu'il garde quand aucune recherche ne les prend
     * @param fuelPerTick     l'energie que rend un tick de combustible brule, en FE
     * @param fuelMaxTicks    le combustible qu'il garde, en ticks de combustion
     * @param countryPercent  ce qu'ajoute chaque pays tenu, en pour cent
     * @param capitalPercent  ce qu'ajoute la capitale, en pour cent
     * @param countryCap      le plafond de l'apport des pays, en pour cent
     */
    public record Config(int energyPerTick, int energyCapacity, int energyMaxInput, long pointsPerHour,
                         long bufferPoints, int fuelPerTick, int fuelMaxTicks, int countryPercent,
                         int capitalPercent, int countryCap) {

        static final Config FALLBACK = new Config(20, 100_000, 2_000, 3_600L, 3_600L, 10, 102_400, 5, 10, 100);

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("energy_per_tick", 20).forGetter(Config::energyPerTick),
                Codec.INT.optionalFieldOf("energy_capacity", 100_000).forGetter(Config::energyCapacity),
                Codec.INT.optionalFieldOf("energy_max_input", 2_000).forGetter(Config::energyMaxInput),
                Codec.LONG.optionalFieldOf("points_per_hour", 3_600L).forGetter(Config::pointsPerHour),
                Codec.LONG.optionalFieldOf("buffer_points", 3_600L).forGetter(Config::bufferPoints),
                Codec.INT.optionalFieldOf("fuel_fe_per_tick", 10).forGetter(Config::fuelPerTick),
                Codec.INT.optionalFieldOf("fuel_max_ticks", 102_400).forGetter(Config::fuelMaxTicks),
                Codec.INT.optionalFieldOf("country_percent", 5).forGetter(Config::countryPercent),
                Codec.INT.optionalFieldOf("capital_percent", 10).forGetter(Config::capitalPercent),
                Codec.INT.optionalFieldOf("country_cap_percent", 100).forGetter(Config::countryCap)
        ).apply(instance, Config::new));
    }
}
