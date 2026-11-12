package fr.atraxterra.dohy.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
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
            Config parsed = result.getOrThrow();
            List<Identifier> absent = new ArrayList<>();
            config = new Config(parsed.daily(), parsed.weekly(), parsed.demand(), parsed.firstSale(),
                    parsed.reset(), parsed.trixiumOrder().resolved(absent));
            DoHy.LOGGER.info("Contrats charges : {} quotidiens, {} hebdos, {} commandes de trixium",
                    config.daily().pool().size(), config.weekly().pool().size(),
                    config.trixiumOrder().pool().size());
            if (!absent.isEmpty()) {
                DoHy.LOGGER.warn("Commande de trixium : objets inconnus de ce jeu, ignores : {}", absent);
            }
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

    /** Une commande possible : l'objet a livrer et combien. */
    public record OrderEntry(Identifier item, int amount) {
        static final Codec<OrderEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(OrderEntry::item),
                Codec.INT.fieldOf("amount").forGetter(OrderEntry::amount)
        ).apply(instance, OrderEntry::new));
    }

    /**
     * La commande de trixium : un objet a livrer, qui change chaque jour.
     *
     * <p>Elle a <b>sa propre bascule</b>, a midi, et non celle de 5 h des autres
     * contrats : c'est la demande du joueur. Le reste — objet du jour, livraison,
     * serie — est dans {@link TrixiumOrder}.
     *
     * @param resetHour  l'heure reelle ou l'objet demande change
     * @param rewardItem ce que paie la commande
     * @param reward     combien
     * @param globalXp   l'XP globale d'une commande livree
     * @param streak     le nombre de jours de suite qui fait venir le God Villager ; 0 le coupe
     * @param pool       les commandes possibles ; apres chargement, celles dont l'objet existe
     */
    public record TrixiumOrderConfig(int resetHour, Identifier rewardItem, int reward, long globalXp,
                                     int streak, List<OrderEntry> pool) {

        static final Identifier TRIXIUM = Identifier.fromNamespaceAndPath("palamod", "trixium");

        static final TrixiumOrderConfig FALLBACK = new TrixiumOrderConfig(12, TRIXIUM, 16, 15L, 7, List.of());

        static final Codec<TrixiumOrderConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("reset_hour", 12).forGetter(TrixiumOrderConfig::resetHour),
                Identifier.CODEC.optionalFieldOf("reward_item", TRIXIUM).forGetter(TrixiumOrderConfig::rewardItem),
                Codec.INT.optionalFieldOf("reward", 16).forGetter(TrixiumOrderConfig::reward),
                Codec.LONG.optionalFieldOf("global_xp", 15L).forGetter(TrixiumOrderConfig::globalXp),
                Codec.INT.optionalFieldOf("god_villager_streak", 7).forGetter(TrixiumOrderConfig::streak),
                OrderEntry.CODEC.listOf().optionalFieldOf("pool", List.of()).forGetter(TrixiumOrderConfig::pool)
        ).apply(instance, TrixiumOrderConfig::new));

        /** La meme configuration, reduite aux commandes dont l'objet existe et dont la quantite est positive. */
        TrixiumOrderConfig resolved(List<Identifier> absent) {
            List<OrderEntry> found = new ArrayList<>();
            for (OrderEntry entry : pool) {
                if (!BuiltInRegistries.ITEM.containsKey(entry.item())) {
                    absent.add(entry.item());
                } else if (entry.amount() > 0) {
                    found.add(entry);
                }
            }
            return new TrixiumOrderConfig(resetHour, rewardItem, reward, globalXp, streak, List.copyOf(found));
        }
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

    public record Config(Pool daily, Pool weekly, Demand demand, FirstSale firstSale, Reset reset,
                         TrixiumOrderConfig trixiumOrder) {

        static final Config FALLBACK = new Config(
                new Pool(3, 9000.0d, 15L, List.of()),
                new Pool(1, 110000.0d, 250L, List.of()),
                new Demand(2.5d, List.of("FARM", "FOOD", "MOB", "FOUND"), 1L),
                new FirstSale(1.5d),
                new Reset(5, "MONDAY"),
                TrixiumOrderConfig.FALLBACK);

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Pool.codec(3, 9000.0d).optionalFieldOf("daily", FALLBACK.daily()).forGetter(Config::daily),
                Pool.codec(1, 110000.0d).optionalFieldOf("weekly", FALLBACK.weekly()).forGetter(Config::weekly),
                Demand.CODEC.optionalFieldOf("daily_demand", FALLBACK.demand()).forGetter(Config::demand),
                FirstSale.CODEC.optionalFieldOf("first_sale_bonus", FALLBACK.firstSale()).forGetter(Config::firstSale),
                Reset.CODEC.optionalFieldOf("reset", FALLBACK.reset()).forGetter(Config::reset),
                TrixiumOrderConfig.CODEC.optionalFieldOf("trixium_order", TrixiumOrderConfig.FALLBACK)
                        .forGetter(Config::trixiumOrder)
        ).apply(instance, Config::new));
    }
}
