package fr.atraxterra.dohy.pass;

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

/**
 * Le pass mensuel, charge depuis le datapack ({@code data/dohy/pass.json}) :
 * les niveaux et leur XP, le pool des quetes du jour, les recompenses de
 * chaque niveau, et les douze themes (un par mois) avec leurs quetes du mois.
 */
@EventBusSubscriber
public final class PassConfig {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "pass.json");

    private static volatile Config config = Config.EMPTY;

    private PassConfig() {
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
            DoHy.LOGGER.error("Pass introuvable : data/{}/{}", LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Pass illisible : {}", result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Pass charge : {} niveaux, {} quetes du jour possibles, {} themes",
                    config.levels(), config.daily().pool().size(), config.themes().size());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement du pass", error);
            return false;
        }
    }

    /**
     * Une quete : les memes objectifs que les contrats ({@code ContractType}),
     * et l'XP de pass qu'elle rapporte.
     */
    public record Quest(String id, String type, Optional<String> target, long amount, long xp, Optional<String> label,
                        Optional<String> icon) {
        static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Quest::id),
                Codec.STRING.fieldOf("type").forGetter(Quest::type),
                Codec.STRING.optionalFieldOf("target").forGetter(Quest::target),
                Codec.LONG.fieldOf("amount").forGetter(Quest::amount),
                Codec.LONG.fieldOf("xp").forGetter(Quest::xp),
                Codec.STRING.optionalFieldOf("label").forGetter(Quest::label),
                Codec.STRING.optionalFieldOf("icon").forGetter(Quest::icon)
        ).apply(instance, Quest::new));
    }

    /** Une pile d'objets ; {@code jobXp} regle l'XP d'une bouteille de metier. */
    public record Stack(Identifier item, int count, int jobXp) {
        static final Codec<Stack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Stack::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Stack::count),
                Codec.INT.optionalFieldOf("job_xp", 0).forGetter(Stack::jobXp)
        ).apply(instance, Stack::new));
    }

    /** Ce que donne un niveau : de l'argent, des objets, et au dernier la relique du mois. */
    public record Reward(int level, long money, List<Stack> items, boolean relic) {
        static final Codec<Reward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("level").forGetter(Reward::level),
                Codec.LONG.optionalFieldOf("money", 0L).forGetter(Reward::money),
                Stack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Reward::items),
                Codec.BOOL.optionalFieldOf("relic", false).forGetter(Reward::relic)
        ).apply(instance, Reward::new));
    }

    /** Le theme d'un mois : son nom, sa couleur, son icone, sa phrase, et ses quetes du mois. */
    public record Theme(int month, String name, int color, String icon, String tagline, List<Quest> quests) {
        static final Codec<Theme> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("month").forGetter(Theme::month),
                Codec.STRING.fieldOf("name").forGetter(Theme::name),
                Codec.STRING.xmap(hex -> 0xFF000000 | Integer.parseInt(hex, 16),
                        color -> String.format("%06X", color & 0xFFFFFF)).fieldOf("color").forGetter(Theme::color),
                Codec.STRING.fieldOf("icon").forGetter(Theme::icon),
                Codec.STRING.optionalFieldOf("tagline", "").forGetter(Theme::tagline),
                Quest.CODEC.listOf().fieldOf("quests").forGetter(Theme::quests)
        ).apply(instance, Theme::new));
    }

    /** Les quetes du jour : combien, et le pool ou elles sont tirees. */
    public record Daily(int count, List<Quest> pool) {
        static final Codec<Daily> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("count", 3).forGetter(Daily::count),
                Quest.CODEC.listOf().fieldOf("pool").forGetter(Daily::pool)
        ).apply(instance, Daily::new));
    }

    public record Config(int levels, long xpPerLevel, Daily daily, List<Reward> rewards, List<Theme> themes) {
        static final Config EMPTY = new Config(35, 1000L, new Daily(3, List.of()), List.of(), List.of());

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("levels", 35).forGetter(Config::levels),
                Codec.LONG.optionalFieldOf("xp_per_level", 1000L).forGetter(Config::xpPerLevel),
                Daily.CODEC.fieldOf("daily").forGetter(Config::daily),
                Reward.CODEC.listOf().fieldOf("rewards").forGetter(Config::rewards),
                Theme.CODEC.listOf().fieldOf("themes").forGetter(Config::themes)
        ).apply(instance, Config::new));

        /** Le theme d'un mois (1 a 12), ou {@code null}. */
        public Theme theme(int month) {
            for (Theme theme : themes) {
                if (theme.month() == month) {
                    return theme;
                }
            }
            return null;
        }

        /** La recompense d'un niveau, ou {@code null}. */
        public Reward reward(int level) {
            for (Reward reward : rewards) {
                if (reward.level() == level) {
                    return reward;
                }
            }
            return null;
        }
    }
}
