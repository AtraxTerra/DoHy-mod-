package fr.atraxterra.dohy.economy.price;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tarification par tag, pour integrer un mod sans enumerer ses items.
 *
 * <h2>Le probleme que ca resout</h2>
 *
 * <p>NTM NEXT apporte des centaines d'items : 86 lingots, 71 poudres, 46
 * nuggets, 11 matieres brutes. Les lister un par un dans {@code prices.json}
 * serait a refaire a chaque version du mod. Mais le mod declare proprement les
 * sous-tags de convention ({@code c:ingots/uranium}, {@code c:raw_materials/lead}…),
 * qui nomment a la fois la forme et la matiere d'un item.
 *
 * <p>Si le mod n'est pas installe, les tags sont vides et rien n'est genere.
 * Aucune dependance de compilation, aucune classe du mod referencee.
 *
 * <h2>Un catalogue ferme</h2>
 *
 * <p>Seules les matieres <b>cataloguees</b> dans {@code tag_prices.json} entrent
 * au shop, et seulement sous deux formes : matiere brute et lingot. Un
 * item est rattache a sa matiere par le sous-tag exact
 * {@code c:<forme>/<matiere>}, jamais par un mot-cle dans son identifiant :
 * {@code ingot_th232} est du thorium et {@code ingot_schraranium} n'est pas de
 * l'uranium. Une matiere absente du catalogue — isotope, dechet, produit
 * intermediaire, alliage — n'a pas de prix du tout, plutot qu'un prix de
 * matiere courante qui ne lui correspond pas.
 *
 * <h2>On vend ce qu'on mine, on achete ce qui manque</h2>
 *
 * <p>Les chaines de traitement de NTM <b>multiplient la matiere</b> : plusieurs
 * lingots par minerai. La matiere brute se vend donc et ne s'achete pas (achetee
 * puis traitee, elle donnerait un lingot moins cher que le lingot), le lingot
 * s'achete et ne se vend pas (vendu, il ferait de ce rendement un revenu).
 * L'exploit est ferme par construction, sans audit recette par recette.
 *
 * <h2>Les pieces de machine</h2>
 *
 * <p>Plaques, fils, bobines, tuyaux, boulons, moteurs et circuits n'ont pas de
 * sous-tag de matiere utile : ils sont listes un par un dans
 * {@code components}, avec un prix d'achat calcule hors du jeu par
 * {@code tools/gen_ntm_prices.py} a partir des recettes du mod. Ils ne se
 * vendent pas.
 *
 * <h2>Le prix suit le wiki de NTM</h2>
 *
 * <p>Chaque matiere porte un des huit crans de « Difficulty of Production » du
 * wiki du mod (nucleartech.wiki), de Very Easy a Extremely Hard. L'achat est
 * multiplie par 5 a chaque cran, la vente par 2,83 seulement : un achat est un
 * puits a argent, qu'on ne peut pas exploiter, une vente est un revenu.
 */
public final class TagPricing {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "tag_prices.json");

    private static final Map<String, Integer> S0 = Map.of(
            "FOOD", 2_000,
            "FARM", 2_000,
            "MOB", 256,
            "FOUND", 64,
            "RARE", 8);

    private static final Map<String, Double> FLOOR = Map.of(
            "FOOD", 0.15d,
            "FARM", 0.15d,
            "MOB", 0.20d,
            "FOUND", 0.35d,
            "RARE", 0.50d);

    private static volatile Config config = Config.EMPTY;

    private TagPricing() {
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            // Absence legitime : sans regle, seuls les prix explicites existent.
            config = Config.EMPTY;
            return true;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Regles de tarification par tag illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            Config loaded = result.getOrThrow();
            // Un cran mal orthographie ferait disparaitre la matiere du shop sans
            // bruit : on le dit au chargement plutot qu'au joueur qui la cherche.
            loaded.materials().forEach((name, material) -> {
                if (!loaded.levels().containsKey(material.level())) {
                    DoHy.LOGGER.error("Matiere {} : cran de rarete inconnu {} (connus : {})",
                            name, material.level(), loaded.levels().keySet());
                }
            });
            config = loaded;
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des regles de tarification par tag", error);
            return false;
        }
    }

    /**
     * Genere les entrees des matieres cataloguees et des pieces de machine.
     *
     * <p>Parcourt le registre des items une seule fois, au demarrage, et ne
     * touche jamais a un item deja tarife explicitement : {@code prices.json}
     * reste toujours prioritaire sur une regle de tag.
     *
     * @return les entrees generees, a fusionner dans le catalogue
     */
    public static Map<Identifier, PriceEntry> generate(Map<Identifier, PriceEntry> explicit) {
        Map<Identifier, PriceEntry> generated = new LinkedHashMap<>();
        Config current = config;
        List<Candidate> candidates = candidates(current);
        for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.listElements().toList()) {
            // ResourceKey.location() n'existe plus depuis le renommage en
            // Identifier : on passe par le registre, dont getKey est confirme.
            Identifier id = BuiltInRegistries.ITEM.getKey(holder.value());
            if (explicit.containsKey(id)) {
                continue;
            }
            for (Candidate candidate : candidates) {
                if (!holder.is(candidate.tag())) {
                    continue;
                }
                // Premier sous-tag qui contient l'item : l'ordre des regles fait
                // la priorite. Une matiere ni vendable ni achetable sous cette
                // forme (brute et declaree non vendable) n'a pas d'entree, et ne
                // doit pas retomber sur une autre forme.
                if (candidate.entry() != null) {
                    generated.put(id, candidate.entry());
                    DoHy.LOGGER.debug("Tarif par tag : {} = {} {} ({}), vente {}, achat {}",
                            id, candidate.material(), candidate.rule().tag(), candidate.level(),
                            candidate.entry().sellable() ? candidate.entry().basePrice() : 0L,
                            candidate.entry().buyPrice());
                }
                break;
            }
        }

        int components = 0;
        for (Map.Entry<String, Long> component : current.components().entrySet()) {
            Identifier id = Identifier.tryParse(component.getKey());
            // Un identifiant inconnu est une piece d'une autre version du mod, ou
            // le mod absent : rien a faire, le shop ne montre que ce qui existe.
            if (id == null || component.getValue() <= 0L || explicit.containsKey(id)
                    || generated.containsKey(id) || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
                continue;
            }
            generated.put(id, componentEntry(component.getValue()));
            components++;
        }

        if (!generated.isEmpty()) {
            long sellable = generated.values().stream().filter(PriceEntry::sellable).count();
            long buyable = generated.values().stream().filter(entry -> entry.buyPrice() > 0).count();
            DoHy.LOGGER.info("Tarification par tag : {} items generes ({} a la vente, {} a l'achat), dont {} pieces de machine",
                    generated.size(), sellable, buyable, components);
        }
        return generated;
    }

    /**
     * Une piece de machine : achat seul, au prix calcule par
     * {@code tools/gen_ntm_prices.py}.
     *
     * <p>Le prix de base ne sert qu'au tri du shop, puisque la piece ne se vend
     * pas. Il garde le rapport achat / vente d'un lingot du cran EASY, pour que
     * pieces et lingots se rangent dans le meme ordre de valeur.
     */
    private static PriceEntry componentEntry(long buy) {
        return new PriceEntry(
                "NTM",
                "FARM",
                Math.max(1L, Math.round(buy / 30.0d)),
                S0.get("FARM"),
                FLOOR.get("FARM"),
                1.8d,
                buy,
                false);
    }

    /**
     * Les sous-tags a tester, dans l'ordre : regle par regle, matiere par
     * matiere. L'entree de prix est calculee une fois ici, pas par item.
     */
    private static List<Candidate> candidates(Config current) {
        List<Candidate> candidates = new ArrayList<>();
        // TreeMap : un ordre stable d'un demarrage a l'autre, quel que soit
        // celui du fichier, pour qu'un item dans deux sous-tags tombe toujours
        // sur la meme matiere.
        Map<String, Material> sorted = new TreeMap<>(current.materials());
        for (Rule rule : current.rules()) {
            Identifier form = Identifier.bySeparator(rule.tag(), ':');
            for (Map.Entry<String, Material> material : sorted.entrySet()) {
                Level level = current.levels().get(material.getValue().level());
                if (level == null) {
                    continue;
                }
                TagKey<Item> tag = TagKey.create(BuiltInRegistries.ITEM.key(),
                        Identifier.fromNamespaceAndPath(form.getNamespace(),
                                form.getPath() + "/" + material.getKey()));
                candidates.add(new Candidate(rule, material.getKey(), material.getValue().level(), tag,
                        entryFor(rule, material.getValue(), level, current.forms())));
            }
        }
        return candidates;
    }

    /**
     * Le prix d'une matiere sous une forme : l'ancre du cran multipliee par le
     * facteur de la forme. Le prix de vente sert de reference meme quand
     * l'item ne se vend pas (tri du shop).
     *
     * @return {@code null} si l'item ne se vend ni ne s'achete sous cette forme
     */
    private static PriceEntry entryFor(Rule rule, Material material, Level level, Map<String, Double> forms) {
        double form = forms.getOrDefault(rule.tag(), 1.0d);
        // Une matiere qui ne se mine pas se produit : elle ne se vend jamais,
        // pas meme sous forme brute.
        boolean sellable = rule.sellable() && material.sellable();
        long buy = rule.buyable() ? Math.max(1L, Math.round(level.buyUnit() * form)) : 0L;
        if (!sellable && buy <= 0L) {
            return null;
        }
        long base = Math.max(1L, Math.round(level.sellUnit() * form));
        return new PriceEntry(
                rule.category(),
                rule.tier(),
                base,
                S0.getOrDefault(rule.tier(), 1_000),
                FLOOR.getOrDefault(rule.tier(), 0.15d),
                1.8d,
                buy,
                sellable);
    }

    private record Candidate(Rule rule, String material, String level, TagKey<Item> tag, PriceEntry entry) {
    }

    /**
     * Une forme mise en vente : le tag parent ({@code c:ingots}) dont les
     * sous-tags par matiere sont lus, et ce qu'on peut en faire au shop.
     */
    public record Rule(String tag, String category, String tier, boolean sellable, boolean buyable) {

        static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("tag").forGetter(Rule::tag),
                Codec.STRING.optionalFieldOf("category", "NTM").forGetter(Rule::category),
                Codec.STRING.optionalFieldOf("tier", "FARM").forGetter(Rule::tier),
                Codec.BOOL.optionalFieldOf("sellable", false).forGetter(Rule::sellable),
                Codec.BOOL.optionalFieldOf("buyable", false).forGetter(Rule::buyable)
        ).apply(instance, Rule::new));
    }

    /** Les deux ancres de prix d'un cran, au facteur de forme 1,0 (le lingot). */
    public record Level(long buyUnit, long sellUnit) {
        static final Codec<Level> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("buy_unit").forGetter(Level::buyUnit),
                Codec.LONG.fieldOf("sell_unit").forGetter(Level::sellUnit)
        ).apply(instance, Level::new));
    }

    /**
     * Une matiere cataloguee : son cran de rarete, et si sa forme brute se vend.
     * Le champ {@code wiki} ne sert qu'a la relecture du fichier.
     */
    public record Material(String level, boolean sellable, String wiki) {
        static final Codec<Material> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("level").forGetter(Material::level),
                Codec.BOOL.optionalFieldOf("sellable", true).forGetter(Material::sellable),
                Codec.STRING.optionalFieldOf("wiki", "").forGetter(Material::wiki)
        ).apply(instance, Material::new));
    }

    /**
     * Le fichier entier. {@code components} associe un identifiant d'item a son
     * prix d'achat ; le bloc {@code costing}, qui sert a le calculer, n'est lu
     * que par l'outil et ignore ici.
     */
    public record Config(Map<String, Level> levels,
                         Map<String, Double> forms,
                         Map<String, Material> materials,
                         List<Rule> rules,
                         Map<String, Long> components) {

        static final Config EMPTY = new Config(Map.of(), Map.of(), Map.of(), List.of(), Map.of());

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Level.CODEC)
                        .optionalFieldOf("levels", Map.of()).forGetter(Config::levels),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                        .optionalFieldOf("forms", Map.of()).forGetter(Config::forms),
                Codec.unboundedMap(Codec.STRING, Material.CODEC)
                        .optionalFieldOf("materials", Map.of()).forGetter(Config::materials),
                Rule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(Config::rules),
                Codec.unboundedMap(Codec.STRING, Codec.LONG)
                        .optionalFieldOf("components", Map.of()).forGetter(Config::components)
        ).apply(instance, Config::new));
    }
}
