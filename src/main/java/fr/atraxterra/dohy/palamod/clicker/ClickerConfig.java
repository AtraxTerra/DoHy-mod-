package fr.atraxterra.dohy.palamod.clicker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.atraxterra.dohy.DoHy;
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
import java.util.Locale;
import java.util.Map;

/**
 * Les tables du clicker : batiments, ameliorations, Clic Shop.
 *
 * <p>{@code data/palamod/clicker.json}, ecrit par {@code tools/clicker_data.py}.
 * Chez Paladium ces tables sont trois fichiers de configuration du serveur
 * ({@code BuildingGlobalConfig}, {@code UpgradeGlobalConfig},
 * {@code ShopGlobalConfig}) ; les regles qui les lisent sont dans
 * {@link Clicker}.
 */
@EventBusSubscriber
public final class ClickerConfig {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath("palamod", "clicker.json");

    /** Un batiment. {@code index} : son rang, qui fixe sa production de base. */
    public record Building(String id, String name, String item, double price, List<String> categories, int index) {

        /** {@code ClickerBuilding.getRPS} : {@code (float) (0.1f * 1.8f ^ rang)}, par exemplaire et par seconde. */
        public double baseRps() {
            return (float) (0.10000000149011612d * Math.pow(1.7999999523162842d, index));
        }

        /** {@code ClickerBuilding.getPrice(int)} : le prix du prochain exemplaire, +10 % par exemplaire possede. */
        public double price(int owned) {
            return price * Math.pow(1.100000023841858d, owned);
        }
    }

    public enum UpgradeType { CLICK, GLOBAL, TERRAIN, BUILDING, MANY, POSTERIOR, CATEGORY }

    public enum ConditionType { QUANTITY, TIME, BUILDING, UPGRADE }

    /** Une condition d'achat : pieces recoltees, jours de saison, batiments possedes, amelioration possedee. */
    public record Condition(ConditionType type, double value, String id) {
    }

    /**
     * Une amelioration.
     *
     * @param target   un batiment (BUILDING, MANY, POSTERIOR) ou une categorie (TERRAIN, CATEGORY)
     * @param modifier la part ajoutee : 1.0 = +100 %
     * @param job      le metier dont le niveau compte (TERRAIN), vide sinon
     */
    public record Upgrade(String id, String name, String item, double price, UpgradeType type, String target,
                          double modifier, String job, List<Condition> conditions) {
    }

    /** Un rayon du Clic Shop : le nombre d'offres tirees, et leur duree en heures. */
    public enum ShopType {
        WEEKLY(8, 168, "Offre de la semaine"),
        DAILY(4, 24, "Offre du jour"),
        JOBS(4, 168, "Offre métier");

        private final int size;
        private final int hours;
        private final String label;

        ShopType(int size, int hours, String label) {
            this.size = size;
            this.hours = hours;
            this.label = label;
        }

        public int size() {
            return size;
        }

        public long seconds() {
            return hours * 3600L;
        }

        public String label() {
            return label;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Une offre du Clic Shop.
     *
     * @param quantity    le nombre d'achats par rotation
     * @param weight      son poids dans le tirage
     * @param requirement les batiments qu'il faut posseder pour qu'elle sorte
     * @param forbidden   les batiments qui l'empechent de sortir
     */
    public record ShopItem(String id, ShopType type, String item, int count, double price, int quantity, int weight,
                           Map<String, Integer> requirement, Map<String, Integer> forbidden) {

        /** {@code ClickerShopItem.getPrice(int)} : un tiers du prix en plus a chaque achat. */
        public double price(int bought) {
            return price + price * bought / 3.0d;
        }

        /** L'offre peut-elle sortir pour un joueur qui possede ces batiments ? */
        public boolean eligible(Map<String, Integer> owned) {
            for (Map.Entry<String, Integer> entry : forbidden.entrySet()) {
                if (owned.getOrDefault(entry.getKey(), 0) >= entry.getValue()) {
                    return false;
                }
            }
            for (Map.Entry<String, Integer> entry : requirement.entrySet()) {
                if (owned.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static List<Building> buildings = List.of();
    private static Map<String, Building> buildingsById = Map.of();
    private static List<Upgrade> upgrades = List.of();
    private static Map<String, Upgrade> upgradesById = Map.of();
    private static List<ShopItem> shop = List.of();
    private static Map<String, String> categories = Map.of();

    private ClickerConfig() {
    }

    public static List<Building> buildings() {
        return buildings;
    }

    public static Building building(String id) {
        return buildingsById.get(id);
    }

    public static List<Upgrade> upgrades() {
        return upgrades;
    }

    public static Upgrade upgrade(String id) {
        return upgradesById.get(id);
    }

    public static List<ShopItem> shop() {
        return shop;
    }

    public static ShopItem shopItem(String id) {
        for (ShopItem item : shop) {
            if (item.id().equals(id)) {
                return item;
            }
        }
        return null;
    }

    /** Le nom affiche d'une categorie de batiments. */
    public static String category(String key) {
        return categories.getOrDefault(key, key);
    }

    /** L'objet d'une table, a l'unite ; une barriere si son identifiant n'existe pas. */
    public static ItemStack stack(String id, int count) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(Items.BARRIER);
        return new ItemStack(item, count);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    static void load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.warn("Tables du clicker introuvables : data/palamod/clicker.json");
            return;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject root = GsonHelper.parse(reader);
            Map<String, String> names = new HashMap<>();
            for (var entry : GsonHelper.getAsJsonObject(root, "categories").entrySet()) {
                names.put(entry.getKey(), entry.getValue().getAsString());
            }
            List<Building> list = new ArrayList<>();
            Map<String, Building> byId = new LinkedHashMap<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "buildings")) {
                JsonObject json = element.getAsJsonObject();
                Building building = new Building(GsonHelper.getAsString(json, "id"),
                        GsonHelper.getAsString(json, "name"), item(GsonHelper.getAsString(json, "item")),
                        GsonHelper.getAsDouble(json, "price"), strings(json, "categories"), list.size());
                list.add(building);
                byId.put(building.id(), building);
            }
            List<Upgrade> upgradeList = new ArrayList<>();
            Map<String, Upgrade> upgradeById = new LinkedHashMap<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "upgrades")) {
                JsonObject json = element.getAsJsonObject();
                List<Condition> conditions = new ArrayList<>();
                for (JsonElement raw : GsonHelper.getAsJsonArray(json, "conditions", new JsonArray())) {
                    JsonObject condition = raw.getAsJsonObject();
                    ConditionType type = ConditionType.valueOf(
                            GsonHelper.getAsString(condition, "type").toUpperCase(Locale.ROOT));
                    conditions.add(new Condition(type,
                            GsonHelper.getAsDouble(condition, type == ConditionType.BUILDING ? "count" : "value", 0.0d),
                            GsonHelper.getAsString(condition, "id", "")));
                }
                Upgrade upgrade = new Upgrade(GsonHelper.getAsString(json, "id"),
                        GsonHelper.getAsString(json, "name"), item(GsonHelper.getAsString(json, "item")),
                        GsonHelper.getAsDouble(json, "price"),
                        UpgradeType.valueOf(GsonHelper.getAsString(json, "type").toUpperCase(Locale.ROOT)),
                        GsonHelper.getAsString(json, "target", ""), GsonHelper.getAsDouble(json, "modifier"),
                        GsonHelper.getAsString(json, "job", ""), List.copyOf(conditions));
                upgradeList.add(upgrade);
                upgradeById.put(upgrade.id(), upgrade);
            }
            List<ShopItem> shopList = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "shop")) {
                JsonObject json = element.getAsJsonObject();
                shopList.add(new ShopItem(GsonHelper.getAsString(json, "id"),
                        ShopType.valueOf(GsonHelper.getAsString(json, "type").toUpperCase(Locale.ROOT)),
                        item(GsonHelper.getAsString(json, "item")), GsonHelper.getAsInt(json, "count", 1),
                        GsonHelper.getAsDouble(json, "price"), GsonHelper.getAsInt(json, "quantity", 1),
                        Math.max(1, GsonHelper.getAsInt(json, "weight", 1)), counts(json, "requirement"),
                        counts(json, "forbidden")));
            }
            categories = names;
            buildings = List.copyOf(list);
            buildingsById = byId;
            upgrades = List.copyOf(upgradeList);
            upgradesById = upgradeById;
            shop = List.copyOf(shopList);
            DoHy.LOGGER.info("Clicker : {} batiments, {} ameliorations, {} offres", buildings.size(),
                    upgrades.size(), shop.size());
        } catch (Exception error) {
            DoHy.LOGGER.error("Tables du clicker illisibles", error);
        }
    }

    /** Verifie l'identifiant d'un objet au chargement : une faute se voit dans le journal, pas en jeu. */
    private static String item(String id) {
        if (BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).isEmpty()) {
            DoHy.LOGGER.warn("Clicker : objet inconnu {}", id);
        }
        return id;
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key, new JsonArray())) {
            out.add(element.getAsString());
        }
        return List.copyOf(out);
    }

    private static Map<String, Integer> counts(JsonObject json, String key) {
        Map<String, Integer> out = new HashMap<>();
        if (json.has(key)) {
            for (var entry : json.getAsJsonObject(key).entrySet()) {
                out.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
        return Map.copyOf(out);
    }
}
