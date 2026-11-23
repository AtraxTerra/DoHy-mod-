package fr.atraxterra.dohy.faction.tech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Les neuf domaines de technologie de la nation (chapitre 23, lot 23b), charges depuis le
 * datapack : un fichier par domaine dans {@code data/dohy/tech/}, plus les machines laissees
 * libres ({@code data/dohy/tech_free.json}). Recharges par {@code /dohy reload}, verifies par
 * {@code tools/check_tech_catalogue.py}.
 *
 * <p>Un palier deverrouille la pose de ses machines (des motifs, {@code *} accepte, lot 23d) et
 * donne ses bonus. Les domaines repris de l'ancien arbre (Gouvernement, Economie, Defense) se
 * gagnent par leurs anciens paliers ({@code legacy}) ; les autres se recherchent, contre de
 * l'argent de la banque nationale et des pieces de NTM au lancement, puis les points des
 * laboratoires ({@code cost}, lot 23c).
 */
@EventBusSubscriber
public final class TechConfig {

    /** Une piece a fournir : « 32 plaques d'acier ». Un objet d'un mod absent ne se demande pas. */
    public record ItemCost(String id, int count, String label) {
        static final Codec<ItemCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(ItemCost::id),
                Codec.INT.fieldOf("count").forGetter(ItemCost::count),
                Codec.STRING.fieldOf("label").forGetter(ItemCost::label)
        ).apply(instance, ItemCost::new));

        public Optional<Item> item() {
            Identifier key = Identifier.tryParse(id);
            return key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
        }
    }

    /**
     * Ce que coute une recherche : l'argent de la banque et les pieces au lancement, puis les points
     * de recherche que produisent les laboratoires (lot 23c).
     */
    public record Cost(long money, long points, List<ItemCost> items) {
        static final Codec<Cost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("money", 0L).forGetter(Cost::money),
                Codec.LONG.optionalFieldOf("points", 1L).forGetter(Cost::points),
                ItemCost.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Cost::items)
        ).apply(instance, Cost::new));
    }

    /** Un bonus : son type (lu par {@link TechPerks}), sa valeur, son libelle. */
    public record Bonus(String type, int value, String label) {
        static final Codec<Bonus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(Bonus::type),
                Codec.INT.fieldOf("value").forGetter(Bonus::value),
                Codec.STRING.fieldOf("label").forGetter(Bonus::label)
        ).apply(instance, Bonus::new));
    }

    /** Une machine du palier, et le nom a afficher quand son mod n'est pas installe. */
    public record Machine(String id, String name) {
        static final Codec<Machine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Machine::id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Machine::name)
        ).apply(instance, Machine::new));

        public Optional<Block> block() {
            Identifier key = Identifier.tryParse(id);
            return key == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(key);
        }
    }

    /**
     * Un palier.
     *
     * @param start    acquis d'office : ses machines sont libres et il compte comme prerequis
     * @param requires les paliers d'autres domaines qu'il demande (« energie:2 »)
     * @param unlocks  les machines dont il deverrouille la pose (motifs)
     * @param machines les memes, une par une, avec leur nom
     * @param cost     la recherche, pour un domaine nouveau ; vide pour un domaine repris
     * @param legacy   les anciens paliers qui le font (« global:3 »), pour un domaine repris
     */
    public record Tier(int tier, String name, boolean start, List<String> requires, List<String> unlocks,
                       List<Machine> machines, List<Bonus> bonuses, Optional<Cost> cost, List<String> legacy) {
        static final Codec<Tier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("tier").forGetter(Tier::tier),
                Codec.STRING.fieldOf("name").forGetter(Tier::name),
                Codec.BOOL.optionalFieldOf("start", false).forGetter(Tier::start),
                Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(Tier::requires),
                Codec.STRING.listOf().optionalFieldOf("unlocks", List.of()).forGetter(Tier::unlocks),
                Machine.CODEC.listOf().optionalFieldOf("machines", List.of()).forGetter(Tier::machines),
                Bonus.CODEC.listOf().optionalFieldOf("bonuses", List.of()).forGetter(Tier::bonuses),
                Cost.CODEC.optionalFieldOf("cost").forGetter(Tier::cost),
                Codec.STRING.listOf().optionalFieldOf("legacy", List.of()).forGetter(Tier::legacy)
        ).apply(instance, Tier::new));

        /** « Energie II · Vapeur ». */
        public String title(Domain domain) {
            return domain.name() + " " + roman(tier) + " · " + name;
        }

        /** Vrai si le motif d'un de ses deblocages couvre cet identifiant. */
        public boolean unlocks(String id) {
            for (String pattern : unlocks) {
                if (matches(pattern, id)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Les machines du palier : celles du jeu que designent ses motifs (le mod est installe),
         * puis celles de la liste que le jeu ne connait pas (le mod manque).
         */
        public List<Machine> resolved() {
            List<Machine> found = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (String pattern : unlocks) {
                if (pattern.indexOf('*') < 0) {
                    continue;
                }
                for (Identifier key : BuiltInRegistries.BLOCK.keySet()) {
                    String id = key.toString();
                    if (matches(pattern, id) && !ids.contains(id)) {
                        ids.add(id);
                        found.add(new Machine(id, ""));
                    }
                }
            }
            for (Machine machine : machines) {
                if (!ids.contains(machine.id())) {
                    ids.add(machine.id());
                    found.add(machine);
                }
            }
            found.sort(Comparator.comparingInt(machine -> machine.block().isPresent() ? 0 : 1));
            return found;
        }
    }

    /** Un domaine : ses paliers dans l'ordre. */
    public record Domain(String id, String name, String summary, List<String> icons, String color, int order,
                         List<Tier> tiers) {
        static final Codec<Domain> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Domain::id),
                Codec.STRING.fieldOf("name").forGetter(Domain::name),
                Codec.STRING.optionalFieldOf("summary", "").forGetter(Domain::summary),
                Codec.STRING.listOf().optionalFieldOf("icons", List.of()).forGetter(Domain::icons),
                Codec.STRING.optionalFieldOf("color", "#55545C").forGetter(Domain::color),
                Codec.INT.optionalFieldOf("order", 0).forGetter(Domain::order),
                Tier.CODEC.listOf().fieldOf("tiers").forGetter(Domain::tiers)
        ).apply(instance, Domain::new));

        public Tier tier(int number) {
            for (Tier tier : tiers) {
                if (tier.tier() == number) {
                    return tier;
                }
            }
            return null;
        }

        public int max() {
            return tiers.size();
        }

        /** Repris de l'ancien arbre : ses paliers se gagnent par les anciens. */
        public boolean merged() {
            for (Tier tier : tiers) {
                if (!tier.legacy().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        /** Les paliers acquis d'office, a la suite depuis le premier. */
        public int startLevel() {
            int level = 0;
            for (Tier tier : tiers) {
                if (!tier.start() || tier.tier() != level + 1) {
                    break;
                }
                level = tier.tier();
            }
            return level;
        }

        public int accent() {
            try {
                return 0xFF000000 | Integer.parseInt(color.startsWith("#") ? color.substring(1) : color, 16);
            } catch (NumberFormatException error) {
                return 0xFF55545C;
            }
        }

        /** La premiere icone que le jeu connait : la machine de NTM si le mod est la, sinon l'objet de secours. */
        public ItemStack icon() {
            for (String id : icons) {
                Identifier key = Identifier.tryParse(id);
                Optional<Item> item = key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
                if (item.isPresent() && item.get() != Items.AIR) {
                    return new ItemStack(item.get());
                }
            }
            return new ItemStack(Items.BOOK);
        }
    }

    /** Les machines laissees libres : plantes, ruines, fluides, decor. */
    record Free(List<String> patterns) {
        static final Free EMPTY = new Free(List.of());
        static final Codec<Free> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("patterns", List.of()).forGetter(Free::patterns)
        ).apply(instance, Free::new));
    }

    private static final String FOLDER = "tech";
    private static final Identifier FREE = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "tech_free.json");

    private static volatile List<Domain> domains = List.of();
    private static volatile List<String> free = List.of();

    private TechConfig() {
    }

    /** Les domaines, dans l'ordre de l'ecran. */
    public static List<Domain> domains() {
        return domains;
    }

    public static Domain domain(String id) {
        for (Domain domain : domains) {
            if (domain.id().equals(id)) {
                return domain;
            }
        }
        return null;
    }

    /** Les motifs des machines que l'arbre laisse libres. */
    public static List<String> free() {
        return free;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static boolean load(MinecraftServer server) {
        Map<Identifier, Resource> found = server.getResourceManager().listResources(FOLDER,
                id -> id.getPath().endsWith(".json"));
        List<Domain> loaded = new ArrayList<>();
        boolean ok = true;
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            if (!entry.getKey().getNamespace().equals(DoHy.MOD_ID)) {
                continue;
            }
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                var result = Domain.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
                if (result.isError()) {
                    DoHy.LOGGER.error("Technologies : {} illisible : {}", entry.getKey(),
                            result.error().map(Object::toString).orElse("erreur inconnue"));
                    ok = false;
                    continue;
                }
                Domain domain = result.getOrThrow();
                List<Tier> tiers = new ArrayList<>(domain.tiers());
                tiers.sort(Comparator.comparingInt(Tier::tier));
                loaded.add(new Domain(domain.id(), domain.name(), domain.summary(), domain.icons(), domain.color(),
                        domain.order(), List.copyOf(tiers)));
            } catch (Exception error) {
                DoHy.LOGGER.error("Technologies : echec du chargement de {}", entry.getKey(), error);
                ok = false;
            }
        }
        if (loaded.isEmpty()) {
            DoHy.LOGGER.error("Technologies introuvables : data/{}/{}/*.json", DoHy.MOD_ID, FOLDER);
            return false;
        }
        loaded.sort(Comparator.comparingInt(Domain::order).thenComparing(Domain::id));
        List<String> patterns = List.of();
        var resource = server.getResourceManager().getResource(FREE);
        if (resource.isPresent()) {
            try (BufferedReader reader = resource.get().openAsReader()) {
                var result = Free.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
                if (result.isError()) {
                    DoHy.LOGGER.error("Technologies : machines libres illisibles : {}",
                            result.error().map(Object::toString).orElse("erreur inconnue"));
                    ok = false;
                } else {
                    patterns = List.copyOf(result.getOrThrow().patterns());
                }
            } catch (Exception error) {
                DoHy.LOGGER.error("Technologies : echec du chargement des machines libres", error);
                ok = false;
            }
        }
        domains = List.copyOf(loaded);
        free = patterns;
        int tiers = 0;
        int machines = 0;
        for (Domain domain : loaded) {
            tiers += domain.tiers().size();
            for (Tier tier : domain.tiers()) {
                machines += tier.machines().size();
            }
        }
        DoHy.LOGGER.info("Technologies chargees : {} domaines, {} paliers, {} machines, {} motifs libres",
                loaded.size(), tiers, machines, patterns.size());
        return ok;
    }

    // -- Petits outils ----------------------------------------------------------------------------

    private static final Map<String, Pattern> PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Un motif d'identifiant, {@code *} pour n'importe quelle suite de caracteres. */
    public static boolean matches(String pattern, String id) {
        if (pattern.indexOf('*') < 0) {
            return pattern.equals(id);
        }
        return PATTERNS.computeIfAbsent(pattern, key -> {
            String[] parts = key.split("\\*", -1);
            StringBuilder regex = new StringBuilder();
            for (int index = 0; index < parts.length; index++) {
                if (index > 0) {
                    regex.append(".*");
                }
                regex.append(Pattern.quote(parts[index]));
            }
            return Pattern.compile(regex.toString());
        }).matcher(id).matches();
    }

    private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII"};

    public static String roman(int tier) {
        return tier >= 0 && tier < ROMAN.length ? ROMAN[tier] : String.valueOf(tier);
    }
}
