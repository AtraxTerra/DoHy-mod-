package fr.atraxterra.dohy.guide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.guide.plan.Blueprints;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L'encyclopedie : une fiche par systeme du mod, dans le datapack
 * ({@code data/dohy/encyclopedia.json}).
 *
 * <p>Une fiche dit a quoi sert le systeme, comment s'y mettre, ses commandes
 * et ses touches, ses voisins ; elle peut montrer la progression du joueur
 * ({@code progress}, voir {@link EncyclopediaProgress}) et donner les plans de
 * ses structures ({@code plans}). Elle se decouvre quand le joueur croise le
 * systeme ({@code discover} : un objet dans le sac, une dimension, un menu
 * ouvert) — elle est alors marquee « nouvelle » jusqu'a sa lecture.
 *
 * <p>Les textes sont sans accents : les polices du kit d'interface n'en ont
 * pas.
 */
@EventBusSubscriber
public final class Encyclopedia {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "encyclopedia.json");

    public record Category(String id, String name, int color, String icon) {
        static final Codec<Category> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Category::id),
                Codec.STRING.fieldOf("name").forGetter(Category::name),
                Codec.STRING.xmap(hex -> 0xFF000000 | Integer.parseInt(hex, 16),
                        color -> String.format("%06X", color & 0xFFFFFF)).fieldOf("color").forGetter(Category::color),
                Codec.STRING.fieldOf("icon").forGetter(Category::icon)
        ).apply(instance, Category::new));

        public ItemStack iconStack() {
            return stack(icon);
        }
    }

    public record Section(String title, List<String> text) {
        static final Codec<Section> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("title", "").forGetter(Section::title),
                Codec.STRING.listOf().fieldOf("text").forGetter(Section::text)
        ).apply(instance, Section::new));
    }

    /**
     * Ce qui decouvre une fiche.
     *
     * @param always     decouverte des le depart
     * @param items      un de ces objets dans le sac (identifiants, ou {@code #tag})
     * @param dimensions une de ces dimensions
     * @param views      un de ces menus du kit ouvert (identifiant de vue)
     */
    public record Discover(boolean always, List<String> items, List<String> dimensions, List<String> views) {
        static final Discover NONE = new Discover(false, List.of(), List.of(), List.of());
        static final Codec<Discover> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("always", false).forGetter(Discover::always),
                Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Discover::items),
                Codec.STRING.listOf().optionalFieldOf("dimensions", List.of()).forGetter(Discover::dimensions),
                Codec.STRING.listOf().optionalFieldOf("views", List.of()).forGetter(Discover::views)
        ).apply(instance, Discover::new));
    }

    public record Entry(String id, String category, int order, String icon, String title, List<String> card,
                        List<String> summary, List<Section> sections, List<String> commands, List<String> see,
                        String progress, List<String> plans, Discover discover) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("category").forGetter(Entry::category),
                Codec.INT.optionalFieldOf("order", 0).forGetter(Entry::order),
                Codec.STRING.fieldOf("icon").forGetter(Entry::icon),
                Codec.STRING.fieldOf("title").forGetter(Entry::title),
                Codec.STRING.listOf().optionalFieldOf("card", List.of()).forGetter(Entry::card),
                Codec.STRING.listOf().fieldOf("summary").forGetter(Entry::summary),
                Section.CODEC.listOf().optionalFieldOf("sections", List.of()).forGetter(Entry::sections),
                Codec.STRING.listOf().optionalFieldOf("commands", List.of()).forGetter(Entry::commands),
                Codec.STRING.listOf().optionalFieldOf("see", List.of()).forGetter(Entry::see),
                Codec.STRING.optionalFieldOf("progress", "").forGetter(Entry::progress),
                Codec.STRING.listOf().optionalFieldOf("plans", List.of()).forGetter(Entry::plans),
                Discover.CODEC.optionalFieldOf("discover", Discover.NONE).forGetter(Entry::discover)
        ).apply(instance, Entry::new));

        public ItemStack iconStack() {
            return stack(icon);
        }
    }

    record Book(List<Category> categories, List<Entry> entries) {
        static final Book EMPTY = new Book(List.of(), List.of());
        static final Codec<Book> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Category.CODEC.listOf().fieldOf("categories").forGetter(Book::categories),
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Book::entries)
        ).apply(instance, Book::new));
    }

    private static volatile Book book = Book.EMPTY;
    private static volatile Map<String, Entry> byId = Map.of();

    private Encyclopedia() {
    }

    public static List<Category> categories() {
        return book.categories();
    }

    public static List<Entry> entries() {
        return book.entries();
    }

    public static Entry entry(String id) {
        return byId.get(id);
    }

    public static Category category(String id) {
        for (Category category : book.categories()) {
            if (category.id().equals(id)) {
                return category;
            }
        }
        return new Category(id, id, 0xFF55545C, "minecraft:book");
    }

    /** Les fiches d'une categorie, dans leur ordre. */
    public static List<Entry> inCategory(String category) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : book.entries()) {
            if (entry.category().equals(category)) {
                out.add(entry);
            }
        }
        out.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return out;
    }

    static ItemStack stack(String id) {
        Identifier key = Identifier.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
        return item == null || item == Items.AIR ? new ItemStack(Items.BOOK) : new ItemStack(item);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Encyclopedie introuvable : data/{}/{}", LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Book.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Encyclopedie illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            Book loaded = result.getOrThrow();
            Map<String, Entry> index = new LinkedHashMap<>();
            for (Entry entry : loaded.entries()) {
                if (index.put(entry.id(), entry) != null) {
                    DoHy.LOGGER.warn("Encyclopedie : fiche en double « {} »", entry.id());
                }
            }
            book = loaded;
            byId = index;
            report(loaded, index);
            EncyclopediaEvents.reindex();
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de l'encyclopedie", error);
            return false;
        }
    }

    /** Ce que les fiches promettent sans tenir : un voisin, une icone, un plan qui n'existent pas. */
    private static void report(Book loaded, Map<String, Entry> index) {
        Map<String, Integer> problems = new HashMap<>();
        for (Entry entry : loaded.entries()) {
            for (String other : entry.see()) {
                if (!index.containsKey(other)) {
                    DoHy.LOGGER.warn("Encyclopedie : « {} » renvoie a une fiche inconnue « {} »", entry.id(), other);
                    problems.merge("voisins", 1, Integer::sum);
                }
            }
            Identifier icon = Identifier.tryParse(entry.icon());
            if (icon == null || !BuiltInRegistries.ITEM.containsKey(icon)) {
                DoHy.LOGGER.warn("Encyclopedie : icone inconnue « {} » pour « {} »", entry.icon(), entry.id());
                problems.merge("icones", 1, Integer::sum);
            }
            for (String plan : entry.plans()) {
                if (Blueprints.get(plan) == null) {
                    DoHy.LOGGER.warn("Encyclopedie : plan inconnu « {} » pour « {} »", plan, entry.id());
                    problems.merge("plans", 1, Integer::sum);
                }
            }
        }
        DoHy.LOGGER.info("Encyclopedie : {} fiches en {} categories{}", loaded.entries().size(),
                loaded.categories().size(), problems.isEmpty() ? "" : ", problemes " + problems);
    }
}
