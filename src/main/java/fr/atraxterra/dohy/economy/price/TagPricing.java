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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tarification par tag, pour integrer un mod sans enumerer ses items.
 *
 * <h2>Le probleme que ca resout</h2>
 *
 * <p>NTM NEXT apporte des centaines d'items : 92 lingots, 77 poudres, 48
 * nuggets, 14 matieres brutes. Les lister un par un dans {@code prices.json}
 * serait a refaire a chaque version du mod, et faux des la premiere. Mais le mod
 * declare proprement les tags de convention ({@code c:ingots},
 * {@code c:raw_materials}, {@code c:plates}…), donc une regle par tag suffit.
 *
 * <p>Avantage decisif : si le mod n'est pas installe, les tags sont vides et
 * rien n'est genere. Aucune dependance de compilation, aucun chargement
 * conditionnel, aucune classe du mod referencee.
 *
 * <h2>Pourquoi NTM ne vend que sa matiere brute</h2>
 *
 * <p>Les chaines de traitement de NTM <b>multiplient la matiere</b> : plusieurs
 * lingots par minerai, plus des sous-produits. Rendre les lingots, poudres ou
 * plaques vendables transformerait ce rendement en revenu infini. Seule la
 * matiere brute est donc vendable, et le reste en achat seul. L'exploit est
 * ferme par construction, sans audit recette par recette.
 */
public final class TagPricing {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "tag_prices.json");

    /** Revenu horaire vise par tier. Doit suivre {@code tools/gen_prices.py}. */
    private static final Map<String, Long> TARGET_RATE = Map.of(
            "FOOD", 12_000L,
            "FARM", 18_000L,
            "MOB", 25_000L,
            "FOUND", 40_000L,
            "RARE", 60_000L);

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

    private static volatile Config config = new Config(List.of());

    private TagPricing() {
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            // Absence legitime : sans regle, seuls les prix explicites existent.
            config = new Config(List.of());
            return true;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Regles de tarification par tag illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des regles de tarification par tag", error);
            return false;
        }
    }

    /**
     * Genere les entrees manquantes a partir des regles.
     *
     * <p>Parcourt le registre des items une seule fois, au demarrage, et ne
     * touche jamais a un item deja tarife explicitement : {@code prices.json}
     * reste toujours prioritaire sur une regle de tag.
     *
     * @return les entrees generees, a fusionner dans le catalogue
     */
    public static Map<Identifier, PriceEntry> generate(Map<Identifier, PriceEntry> explicit) {
        Map<Identifier, PriceEntry> generated = new LinkedHashMap<>();
        if (config.rules().isEmpty()) {
            return generated;
        }

        for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.listElements().toList()) {
            // ResourceKey.location() n'existe plus depuis le renommage en
            // Identifier : on passe par le registre, dont getKey est confirme.
            Identifier id = BuiltInRegistries.ITEM.getKey(holder.value());
            if (explicit.containsKey(id)) {
                continue;
            }
            for (Rule rule : config.rules()) {
                TagKey<Item> tag = TagKey.create(BuiltInRegistries.ITEM.key(),
                        Identifier.bySeparator(rule.tag(), ':'));
                if (!holder.is(tag)) {
                    continue;
                }
                generated.put(id, rule.toEntry());
                // Premiere regle qui matche : l'ordre du fichier fait la
                // priorite, ce qui permet de placer les cas particuliers avant
                // les regles generales.
                break;
            }
        }

        if (!generated.isEmpty()) {
            long sellable = generated.values().stream().filter(PriceEntry::sellable).count();
            DoHy.LOGGER.info("Tarification par tag : {} items generes ({} vendables, {} en achat seul)",
                    generated.size(), sellable, generated.size() - sellable);
        }
        return generated;
    }

    public record Rule(String tag, String category, String tier, double ratePerHour,
                       boolean sellable, double buyMarkup) {

        static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("tag").forGetter(Rule::tag),
                Codec.STRING.optionalFieldOf("category", "NTM").forGetter(Rule::category),
                Codec.STRING.optionalFieldOf("tier", "FARM").forGetter(Rule::tier),
                Codec.DOUBLE.fieldOf("rate_per_hour").forGetter(Rule::ratePerHour),
                Codec.BOOL.optionalFieldOf("sellable", false).forGetter(Rule::sellable),
                Codec.DOUBLE.optionalFieldOf("buy_markup", 4.0d).forGetter(Rule::buyMarkup)
        ).apply(instance, Rule::new));

        /**
         * Applique le meme modele que le generateur de prix : le prix decoule du
         * temps necessaire pour obtenir l'item.
         */
        PriceEntry toEntry() {
            long target = TARGET_RATE.getOrDefault(tier, 18_000L);
            long base = Math.max(1L, Math.round(target / Math.max(1.0d, ratePerHour)));
            return new PriceEntry(
                    category,
                    tier,
                    base,
                    S0.getOrDefault(tier, 1_000),
                    FLOOR.getOrDefault(tier, 0.15d),
                    1.8d,
                    Math.max(1L, Math.round(base * buyMarkup)),
                    sellable);
        }
    }

    public record Config(List<Rule> rules) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Rule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(Config::rules)
        ).apply(instance, Config::new));
    }
}
