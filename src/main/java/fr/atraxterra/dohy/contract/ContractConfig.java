package fr.atraxterra.dohy.contract;

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

/** Les contrats et les bonus de rythme, charges depuis le datapack. */
@EventBusSubscriber
public final class ContractConfig {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "contracts.json");

    private static volatile Config config = Config.FALLBACK;

    private ContractConfig() {
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
            DoHy.LOGGER.error("Contrats introuvables : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Contrats illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Contrats charges : {} quotidiens, {} hebdos",
                    config.daily().pool().size(), config.weekly().pool().size());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des contrats", error);
            return false;
        }
    }

    public record Entry(String id, String type, Optional<String> target, long amount) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("type").forGetter(Entry::type),
                Codec.STRING.optionalFieldOf("target").forGetter(Entry::target),
                Codec.LONG.fieldOf("amount").forGetter(Entry::amount)
        ).apply(instance, Entry::new));
    }

    public record Pool(int count, long rerollHours, double moneyPerLevel, long globalXp, List<Entry> pool) {
        static Codec<Pool> codec(int defaultCount, long defaultHours, double defaultMoney) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("count", defaultCount).forGetter(Pool::count),
                    Codec.LONG.optionalFieldOf("reroll_on_playtime_hours", defaultHours)
                            .forGetter(Pool::rerollHours),
                    // money_formula est une chaine lisible dans le fichier ; le
                    // mod n'evalue pas d'expression et utilise money_base.
                    Codec.DOUBLE.optionalFieldOf("money_base", defaultMoney).forGetter(Pool::moneyPerLevel),
                    Codec.LONG.optionalFieldOf("global_xp", 0L).forGetter(Pool::globalXp),
                    Entry.CODEC.listOf().optionalFieldOf("pool", List.of()).forGetter(Pool::pool)
            ).apply(instance, Pool::new));
        }
    }

    public record Demand(double multiplier, long durationHours, List<String> eligibleTiers,
                         long requireCollectionTier) {
        static final Codec<Demand> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("multiplier", 2.5d).forGetter(Demand::multiplier),
                Codec.LONG.optionalFieldOf("duration_playtime_hours", 24L).forGetter(Demand::durationHours),
                Codec.STRING.listOf().optionalFieldOf("eligible_tiers",
                        List.of("FARM", "FOOD", "MOB", "FOUND")).forGetter(Demand::eligibleTiers),
                Codec.LONG.optionalFieldOf("require_collection_tier", 1L)
                        .forGetter(Demand::requireCollectionTier)
        ).apply(instance, Demand::new));
    }

    public record FirstSale(double multiplier, long resetHours) {
        static final Codec<FirstSale> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("multiplier", 1.5d).forGetter(FirstSale::multiplier),
                Codec.LONG.optionalFieldOf("resets_on_playtime_hours", 24L).forGetter(FirstSale::resetHours)
        ).apply(instance, FirstSale::new));
    }

    public record Config(Pool daily, Pool weekly, Demand demand, FirstSale firstSale) {

        static final Config FALLBACK = new Config(
                new Pool(3, 24L, 9000.0d, 15L, List.of()),
                new Pool(1, 168L, 110000.0d, 250L, List.of()),
                new Demand(2.5d, 24L, List.of("FARM", "FOOD", "MOB", "FOUND"), 1L),
                new FirstSale(1.5d, 24L));

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Pool.codec(3, 24L, 9000.0d).optionalFieldOf("daily", FALLBACK.daily()).forGetter(Config::daily),
                Pool.codec(1, 168L, 110000.0d).optionalFieldOf("weekly", FALLBACK.weekly()).forGetter(Config::weekly),
                Demand.CODEC.optionalFieldOf("daily_demand", FALLBACK.demand()).forGetter(Config::demand),
                FirstSale.CODEC.optionalFieldOf("first_sale_bonus", FALLBACK.firstSale()).forGetter(Config::firstSale)
        ).apply(instance, Config::new));
    }
}
