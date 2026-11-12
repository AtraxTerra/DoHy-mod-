package fr.atraxterra.dohy.tech;

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

/** Les reglages du serveur de trading, charges depuis {@code data/dohy/trading_server.json}. */
@EventBusSubscriber
public final class TradingServerConfig {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "trading_server.json");

    private static volatile Config config = Config.FALLBACK;

    private TradingServerConfig() {
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
            DoHy.LOGGER.error("Configuration du serveur de trading introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Configuration du serveur de trading illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Serveur de trading : {} $ par heure, {} FE par tick, {} a la fois",
                    config.incomePerHour(), config.energyPerTick(), config.maxPerPlayer());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la configuration du serveur de trading", error);
            return false;
        }
    }

    /**
     * @param incomePerHour  l'argent produit par heure de fonctionnement
     * @param energyPerTick  l'energie consommee par tick, en FE
     * @param energyCapacity la reserve d'energie de la machine, en FE
     * @param energyMaxInput ce que la machine accepte par transfert, en FE
     * @param bufferHours    les heures de production que la machine garde avant de s'arreter
     * @param maxPerPlayer   le nombre de serveurs d'un meme joueur qui tournent a la fois
     */
    public record Config(long incomePerHour, int energyPerTick, int energyCapacity, int energyMaxInput,
                         int bufferHours, int maxPerPlayer) {

        static final Config FALLBACK = new Config(9_000L, 50, 100_000, 2_000, 8, 4);

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("income_per_hour", 9_000L).forGetter(Config::incomePerHour),
                Codec.INT.optionalFieldOf("energy_per_tick", 50).forGetter(Config::energyPerTick),
                Codec.INT.optionalFieldOf("energy_capacity", 100_000).forGetter(Config::energyCapacity),
                Codec.INT.optionalFieldOf("energy_max_input", 2_000).forGetter(Config::energyMaxInput),
                Codec.INT.optionalFieldOf("buffer_hours", 8).forGetter(Config::bufferHours),
                Codec.INT.optionalFieldOf("max_per_player", 4).forGetter(Config::maxPerPlayer)
        ).apply(instance, Config::new));

        /** Ce que la machine garde au plus avant de s'arreter. */
        public long bufferMax() {
            return incomePerHour * Math.max(1, bufferHours);
        }
    }
}
