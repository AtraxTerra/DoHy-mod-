package fr.atraxterra.dohy.museum;

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

/** Categories, recompenses et exclusions du musee, chargees depuis le datapack. */
@EventBusSubscriber
public final class MuseumConfig {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "museum.json");

    private static volatile Config config = Config.FALLBACK;

    private MuseumConfig() {
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
            DoHy.LOGGER.error("Configuration du musee introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Configuration du musee illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Musee charge : {} categories, tiers exclus {}",
                    config.categories().size(), config.excludeTiers());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la configuration du musee", error);
            return false;
        }
    }

    /** Une recompense de completion. Memes types que les perks de skill. */
    public record Reward(String type, Optional<String> attribute, double amount,
                         Optional<String> operation) {
        static final Codec<Reward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(Reward::type),
                Codec.STRING.optionalFieldOf("attribute").forGetter(Reward::attribute),
                Codec.DOUBLE.optionalFieldOf("amount", 0.0d).forGetter(Reward::amount),
                Codec.STRING.optionalFieldOf("operation").forGetter(Reward::operation)
        ).apply(instance, Reward::new));
    }

    public record Section(String display, List<Reward> rewards) {
        static final Codec<Section> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("display", "").forGetter(Section::display),
                Reward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Section::rewards)
        ).apply(instance, Section::new));
    }

    public record Config(long globalXpPerDonation, List<String> excludeTiers,
                         Map<String, Section> categories, Section patron) {

        static final Section FALLBACK_PATRON = new Section("Mecene", List.of());

        static final Config FALLBACK = new Config(25L, List.of("RARE"), Map.of(), FALLBACK_PATRON);

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("global_xp_per_donation", 25L)
                        .forGetter(Config::globalXpPerDonation),
                Codec.STRING.listOf().optionalFieldOf("exclude_tiers", List.of("RARE"))
                        .forGetter(Config::excludeTiers),
                Codec.unboundedMap(Codec.STRING, Section.CODEC)
                        .optionalFieldOf("categories", Map.of()).forGetter(Config::categories),
                Section.CODEC.optionalFieldOf("patron", FALLBACK_PATRON).forGetter(Config::patron)
        ).apply(instance, Config::new));

        /** Vrai si ce tier est hors des categories obligatoires. */
        public boolean isExcludedTier(String tier) {
            return excludeTiers().stream().anyMatch(t -> t.equalsIgnoreCase(tier));
        }

        public Section section(String category) {
            return categories().getOrDefault(category, new Section(category, List.of()));
        }
    }
}
