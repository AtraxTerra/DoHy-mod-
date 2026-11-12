package fr.atraxterra.dohy.spawn;

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

/**
 * Les reglages du spawn, lus dans {@code data/dohy/spawn.json} : faut-il y
 * envoyer un nouveau joueur, y faire reapparaitre celui qui n'a pas de lit,
 * et ce que rapporte la recompense quotidienne. Comme les autres reglages
 * chiffres du mod, ils se changent sans recompiler.
 */
@EventBusSubscriber
public final class SpawnConfig {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "spawn.json");

    private static volatile Config config = Config.FALLBACK;

    private SpawnConfig() {
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
            DoHy.LOGGER.error("Reglages du spawn introuvables : data/{}/{}", LOCATION.getNamespace(),
                    LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Reglages du spawn illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow().checked();
            DoHy.LOGGER.info("Spawn : arrivee des nouveaux joueurs {}, reapparition {}, recompense sur {} jours",
                    config.firstJoin(), config.respawn(), config.daily().amounts().size());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des reglages du spawn", error);
            return false;
        }
    }

    /**
     * La recompense quotidienne.
     *
     * @param amounts    ce que rapporte chaque jour d'une serie, dans l'ordre ; la serie reprend au debut ensuite
     * @param levelBonus la part ajoutee par niveau global, comme pour les contrats
     */
    public record Daily(List<Long> amounts, double levelBonus) {
        static final Daily FALLBACK = new Daily(List.of(2000L, 3000L, 4000L, 5000L, 6000L, 7000L, 15000L), 0.35d);
        static final Codec<Daily> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.listOf().optionalFieldOf("amounts", FALLBACK.amounts()).forGetter(Daily::amounts),
                Codec.DOUBLE.optionalFieldOf("level_bonus", FALLBACK.levelBonus()).forGetter(Daily::levelBonus)
        ).apply(instance, Daily::new));
    }

    /**
     * @param firstJoin un joueur qui arrive dans le monde pour la premiere fois est envoye au spawn
     * @param respawn   apres une mort, sans lit ni ancre, on reapparait au spawn
     */
    public record Config(boolean firstJoin, boolean respawn, Daily daily) {
        static final Config FALLBACK = new Config(true, true, Daily.FALLBACK);
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("first_join", true).forGetter(Config::firstJoin),
                Codec.BOOL.optionalFieldOf("respawn", true).forGetter(Config::respawn),
                Daily.CODEC.optionalFieldOf("daily", Daily.FALLBACK).forGetter(Config::daily)
        ).apply(instance, Config::new));

        /** Une serie vide ou des montants negatifs rendraient la borne muette : on retombe sur le reglage d'origine. */
        Config checked() {
            boolean sane = !daily.amounts().isEmpty() && daily.levelBonus() >= 0.0d;
            for (long amount : daily.amounts()) {
                sane &= amount >= 0L;
            }
            return sane ? this : new Config(firstJoin, respawn, Daily.FALLBACK);
        }
    }
}
