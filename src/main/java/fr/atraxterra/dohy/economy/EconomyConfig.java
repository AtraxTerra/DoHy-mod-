package fr.atraxterra.dohy.economy;

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
 * Les chiffres de la banque et de la penalite de mort.
 *
 * <p>Ils etaient en constantes Java ; les voila dans le datapack, comme tout le
 * reste de ce qui est chiffre. La raison est concrete : ces deux systemes sont
 * ceux qu'on regle le plus souvent a l'usage, et un reglage qui coute un build
 * de dix minutes est un reglage qu'on ne fait pas.
 */
@EventBusSubscriber
public final class EconomyConfig {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "economy.json");

    private static volatile Config config = Config.FALLBACK;

    private EconomyConfig() {
    }

    public static Config config() {
        return config;
    }

    public static Bank bank() {
        return config.bank();
    }

    public static Death death() {
        return config.death();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Configuration economique introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Configuration economique illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Economie chargee : interets {} %/jour plafonnes a {}, mort {} %",
                    config.bank().dailyRate() * 100, config.bank().dailyCap(),
                    config.death().rate() * 100);
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la configuration economique", error);
            return false;
        }
    }

    public record Bank(double depositFee, double dailyRate, long dailyCap, long insurancePrice) {
        static final Codec<Bank> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("deposit_fee", 0.03d).forGetter(Bank::depositFee),
                Codec.DOUBLE.optionalFieldOf("daily_rate", 0.0025d).forGetter(Bank::dailyRate),
                Codec.LONG.optionalFieldOf("daily_cap", 250_000L).forGetter(Bank::dailyCap),
                Codec.LONG.optionalFieldOf("insurance_price", 50_000L).forGetter(Bank::insurancePrice)
        ).apply(instance, Bank::new));

        /** Interet par jour reel a ce solde, plafond compris. */
        public long perDay(long banked) {
            return perDay(banked, 1.0d);
        }

        /**
         * Variante avec un multiplicateur de plafond.
         *
         * <p>La branche d'amelioration « Plafond de banque » releve le plafond
         * et <b>jamais le taux</b> : une grosse epargne rapporte donc plus, mais
         * toujours aussi lentement en proportion. Relever le taux aurait rendu
         * l'attente plus rentable que le jeu, ce que tout le calibrage de la
         * banque cherche a eviter.
         */
        public long perDay(long banked, double capMultiplier) {
            if (banked <= 0) {
                return 0L;
            }
            long cap = Math.round(dailyCap * Math.max(1.0d, capMultiplier));
            return Math.min(Math.round(banked * dailyRate), cap);
        }
    }

    public record Death(int freeDeaths, double rate, long capBase,
                        double capLifetimeRate, double insuranceReduction) {
        static final Codec<Death> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("free_deaths", 3).forGetter(Death::freeDeaths),
                Codec.DOUBLE.optionalFieldOf("rate", 0.08d).forGetter(Death::rate),
                Codec.LONG.optionalFieldOf("cap_base", 50_000L).forGetter(Death::capBase),
                Codec.DOUBLE.optionalFieldOf("cap_lifetime_rate", 0.01d).forGetter(Death::capLifetimeRate),
                Codec.DOUBLE.optionalFieldOf("insurance_reduction", 0.5d).forGetter(Death::insuranceReduction)
        ).apply(instance, Death::new));
    }

    public record Config(Bank bank, Death death) {

        /** Repli : les valeurs retenues dans {@code BALANCE.md}. */
        static final Config FALLBACK = new Config(
                new Bank(0.03d, 0.02d, 2_000_000L, 50_000L),
                new Death(3, 0.08d, 50_000L, 0.01d, 0.5d));

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Bank.CODEC.optionalFieldOf("bank", FALLBACK.bank()).forGetter(Config::bank),
                Death.CODEC.optionalFieldOf("death", FALLBACK.death()).forGetter(Config::death)
        ).apply(instance, Config::new));
    }
}
