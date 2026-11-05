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

    /**
     * Un contrat du pool.
     *
     * <p>{@code label} et {@code icon} ne servent qu'a l'affichage et sont
     * facultatifs : sans eux, {@link ContractDisplay} derive un intitule depuis
     * la cible. Les renseigner evite les tournures approximatives — « miner
     * 2000 #minecraft:base_stone_overworld » n'est pas une phrase.
     */
    public record Entry(String id, String type, Optional<String> target, long amount,
                        Optional<String> label, Optional<String> icon) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("type").forGetter(Entry::type),
                Codec.STRING.optionalFieldOf("target").forGetter(Entry::target),
                Codec.LONG.fieldOf("amount").forGetter(Entry::amount),
                Codec.STRING.optionalFieldOf("label").forGetter(Entry::label),
                Codec.STRING.optionalFieldOf("icon").forGetter(Entry::icon)
        ).apply(instance, Entry::new));
    }

    public record Pool(int count, double moneyPerLevel, long globalXp, List<Entry> pool) {
        static Codec<Pool> codec(int defaultCount, double defaultMoney) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("count", defaultCount).forGetter(Pool::count),
                    // money_formula est une chaine lisible dans le fichier ; le
                    // mod n'evalue pas d'expression et utilise money_base.
                    Codec.DOUBLE.optionalFieldOf("money_base", defaultMoney).forGetter(Pool::moneyPerLevel),
                    Codec.LONG.optionalFieldOf("global_xp", 0L).forGetter(Pool::globalXp),
                    Entry.CODEC.listOf().optionalFieldOf("pool", List.of()).forGetter(Pool::pool)
            ).apply(instance, Pool::new));
        }
    }

    public record Demand(double multiplier, List<String> eligibleTiers,
                         long requireCollectionTier) {
        static final Codec<Demand> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("multiplier", 2.5d).forGetter(Demand::multiplier),
                Codec.STRING.listOf().optionalFieldOf("eligible_tiers",
                        List.of("FARM", "FOOD", "MOB", "FOUND")).forGetter(Demand::eligibleTiers),
                Codec.LONG.optionalFieldOf("require_collection_tier", 1L)
                        .forGetter(Demand::requireCollectionTier)
        ).apply(instance, Demand::new));
    }

    public record FirstSale(double multiplier) {
        static final Codec<FirstSale> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("multiplier", 1.5d).forGetter(FirstSale::multiplier)
        ).apply(instance, FirstSale::new));
    }

    /**
     * Les frontieres de periode, en temps reel.
     *
     * <p>Une seule bascule quotidienne sert aux contrats du jour, a la demande
     * et au bonus de premiere vente : trois horloges differentes donneraient
     * trois renouvellements a trois moments, et plus personne ne saurait quand
     * revenir.
     */
    public record Reset(int dailyHour, String weeklyDay) {
        static final Codec<Reset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("daily_hour", 5).forGetter(Reset::dailyHour),
                Codec.STRING.optionalFieldOf("weekly_day", "MONDAY").forGetter(Reset::weeklyDay)
        ).apply(instance, Reset::new));
    }

    public record Config(Pool daily, Pool weekly, Demand demand, FirstSale firstSale, Reset reset) {

        static final Config FALLBACK = new Config(
                new Pool(3, 9000.0d, 15L, List.of()),
                new Pool(1, 110000.0d, 250L, List.of()),
                new Demand(2.5d, List.of("FARM", "FOOD", "MOB", "FOUND"), 1L),
                new FirstSale(1.5d),
                new Reset(5, "MONDAY"));

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Pool.codec(3, 9000.0d).optionalFieldOf("daily", FALLBACK.daily()).forGetter(Config::daily),
                Pool.codec(1, 110000.0d).optionalFieldOf("weekly", FALLBACK.weekly()).forGetter(Config::weekly),
                Demand.CODEC.optionalFieldOf("daily_demand", FALLBACK.demand()).forGetter(Config::demand),
                FirstSale.CODEC.optionalFieldOf("first_sale_bonus", FALLBACK.firstSale()).forGetter(Config::firstSale),
                Reset.CODEC.optionalFieldOf("reset", FALLBACK.reset()).forGetter(Config::reset)
        ).apply(instance, Config::new));
    }
}
