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

    private static volatile Config config = new Config(List.of(), Materials.NONE);

    private TagPricing() {
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            // Absence legitime : sans regle, seuls les prix explicites existent.
            config = new Config(List.of(), Materials.NONE);
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
        matchedByMaterial = 0;
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
                generated.put(id, entryFor(rule, id));
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
            if (matchedByMaterial > 0) {
                DoHy.LOGGER.info("  dont {} tarifes par rarete de matiere", matchedByMaterial);
            }
        }
        return generated;
    }

    /** Compteur de diagnostic du dernier appel a {@link #generate}. */
    private static int matchedByMaterial;

    /**
     * L'entree d'un item : par rarete de matiere si elle est reconnue, par la
     * regle de forme sinon.
     *
     * <p>Une matiere inconnue retombe donc sur l'ancien comportement, ce qui
     * laisse le mod fonctionner si NTM ajoute un metal que ce fichier ne
     * connait pas — au prix d'une matiere courante, ce qui est le defaut
     * prudent.
     */
    private static PriceEntry entryFor(Rule rule, Identifier id) {
        Materials materials = config.materials();
        if (materials.isEmpty()) {
            return rule.toEntry();
        }
        String rarity = materials.rarityOf(id.getPath());
        if (rarity == null || !materials.rarities().containsKey(rarity)) {
            return rule.toEntry();
        }
        matchedByMaterial++;
        // Une matiere qui ne se mine pas se produit, donc elle ne se vend
        // jamais, pas meme sous forme brute.
        boolean sellable = rule.sellable() && !materials.isNeverSellable(id.getPath());
        return materials.toEntry(rule, rarity, sellable);
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

    /** Les deux ancres de prix d'une rarete, au facteur de forme 1,0. */
    public record Rarity(long buyUnit, long sellUnit) {
        static final Codec<Rarity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("buy_unit").forGetter(Rarity::buyUnit),
                Codec.LONG.fieldOf("sell_unit").forGetter(Rarity::sellUnit)
        ).apply(instance, Rarity::new));
    }

    public record NeverSellable(List<String> keywords) {
        static final NeverSellable NONE = new NeverSellable(List.of());

        static final Codec<NeverSellable> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("keywords", List.of())
                        .forGetter(NeverSellable::keywords)
        ).apply(instance, NeverSellable::new));
    }

    /**
     * La seconde dimension du prix : la rarete de la matiere.
     *
     * <p>Les regles par tag ne voient que la <b>forme</b> d'un item, donc un
     * lingot de schrabidium coutait un lingot de cuivre. Ce bloc rattache un
     * item a une matiere par mot-cle dans son identifiant, et le prix devient
     * l'ancre de la rarete multipliee par le facteur de la forme.
     *
     * <p>Le rattachement se fait par mot-cle et non par identifiant complet
     * parce qu'on ne sait pas si le mod nomme ses items {@code uranium_ingot} ou
     * {@code ingot_uranium} : les deux contiennent « uranium ».
     */
    public record Materials(Map<String, Rarity> rarities,
                            Map<String, Double> forms,
                            Map<String, List<String>> assign,
                            NeverSellable neverSellable) {

        static final Materials NONE = new Materials(Map.of(), Map.of(), Map.of(), NeverSellable.NONE);

        static final Codec<Materials> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Rarity.CODEC)
                        .optionalFieldOf("rarities", Map.of()).forGetter(Materials::rarities),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                        .optionalFieldOf("forms", Map.of()).forGetter(Materials::forms),
                Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
                        .optionalFieldOf("assign", Map.of()).forGetter(Materials::assign),
                NeverSellable.CODEC.optionalFieldOf("never_sellable", NeverSellable.NONE)
                        .forGetter(Materials::neverSellable)
        ).apply(instance, Materials::new));

        boolean isEmpty() {
            return rarities.isEmpty() || assign.isEmpty();
        }

        /**
         * La rarete d'un item, ou {@code null} s'il n'est rattache a aucune
         * matiere connue.
         *
         * <p>Le mot-cle le plus long gagne : sans cette regle, un
         * « rare_earth_ingot » pourrait etre capture par un mot-cle plus court
         * d'une autre rarete, et l'ordre d'iteration d'une map decideraient du
         * prix.
         */
        String rarityOf(String path) {
            String lower = path.toLowerCase(java.util.Locale.ROOT);
            String best = null;
            int bestLength = 0;
            for (Map.Entry<String, List<String>> entry : assign.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (keyword.length() > bestLength && lower.contains(keyword)) {
                        best = entry.getKey();
                        bestLength = keyword.length();
                    }
                }
            }
            return best;
        }

        boolean isNeverSellable(String path) {
            String lower = path.toLowerCase(java.util.Locale.ROOT);
            return neverSellable.keywords().stream().anyMatch(lower::contains);
        }

        /**
         * L'entree d'un item rattache a une matiere.
         *
         * <p>La forme vient de la regle qui a capture l'item, et c'est elle qui
         * fournit aussi le tier : le tier porte la demi-saturation, le plancher
         * et le plafond, qui dependent de la rarete du <i>gisement</i> et non de
         * la valeur de la matiere.
         */
        PriceEntry toEntry(Rule rule, String rarityKey, boolean sellable) {
            Rarity rarity = rarities.get(rarityKey);
            double form = forms.getOrDefault(rule.tag(), 1.0d);
            long base = Math.max(1L, Math.round(rarity.sellUnit() * form));
            long buy = Math.max(1L, Math.round(rarity.buyUnit() * form));
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
    }

    public record Config(List<Rule> rules, Materials materials) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Rule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(Config::rules),
                Materials.CODEC.optionalFieldOf("materials", Materials.NONE)
                        .forGetter(Config::materials)
        ).apply(instance, Config::new));
    }
}
