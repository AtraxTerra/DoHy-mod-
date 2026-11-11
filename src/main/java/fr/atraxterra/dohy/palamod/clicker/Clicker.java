package fr.atraxterra.dohy.palamod.clicker;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.clicker.ClickerConfig.Building;
import fr.atraxterra.dohy.palamod.clicker.ClickerConfig.Condition;
import fr.atraxterra.dohy.palamod.clicker.ClickerConfig.ShopItem;
import fr.atraxterra.dohy.palamod.clicker.ClickerConfig.ShopType;
import fr.atraxterra.dohy.palamod.clicker.ClickerConfig.Upgrade;
import fr.atraxterra.dohy.palamod.clicker.ClickerConfig.UpgradeType;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Le clicker de Paladium (PalaClicker 6.1.2) : on clique un aliment pour
 * nourrir les namuus, qui rapportent des ClicCoins ; les ClicCoins achetent
 * des batiments, qui en produisent chaque seconde, des ameliorations, et les
 * offres du Clic Shop.
 *
 * <p>Les regles sont celles du jar, relevees au bytecode :
 *
 * <ul>
 *   <li>un clic vaut {@code 1 x (1 + somme des ameliorations du clic)}, et le
 *       serveur n'en accepte qu'un toutes les 50 ms
 *       ({@code BBPacketClickerClick}) ;</li>
 *   <li>un batiment produit {@code base x exemplaires x (1 + somme de ses
 *       ameliorations)} par seconde, ou {@code base = 0,1 x 1,8 ^ rang}
 *       ({@code PlayerClickerData.getRPS}) ;</li>
 *   <li>la production totale part de 0,5 par seconde ({@code ClickerTask}) et
 *       ne tourne que pour un joueur connecte ;</li>
 *   <li>un exemplaire de plus coute 10 % de plus, 99 au plus par batiment ;
 *       une offre du Clic Shop, un tiers de plus a chaque achat.</li>
 * </ul>
 *
 * <p>Deux ecarts. La production ne commence qu'a la premiere ouverture du
 * clicker (l'original credite 0,5 par seconde a tout joueur connecte, meme
 * s'il ne l'ouvre jamais). Et il faut posseder un batiment pour acheter le
 * suivant : l'original ne le cachait que dans l'ecran, le serveur l'acceptait.
 */
@EventBusSubscriber
public final class Clicker {

    /** {@code ClickerTask} : 0,5 ClicCoin par seconde avant tout batiment. */
    static final double BASE_RPS = 0.5d;
    static final int MAX_BUILDINGS = 99;
    static final int MAX_SHOP_PURCHASES = 99;
    /** Un clic toutes les 50 ms au plus : vingt par seconde. */
    private static final long CLICK_MILLIS = 50L;
    /** L'ecran ouvert recoit une vue fraiche a ce rythme, en ticks. */
    private static final int REFRESH_TICKS = 60;
    /** Les ameliorations montrees a la fois : les pretes d'abord, puis les moins cheres. */
    private static final int MAX_UPGRADES_SHOWN = 42;

    private Clicker() {
    }

    public static ClickerData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.CLICKER.get());
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    // -- Production ------------------------------------------------------------

    /** {@code PlayerClickerData.getClickValue}. */
    static double clickValue(ClickerData data) {
        double bonus = 0.0d;
        for (String id : data.upgrades) {
            Upgrade upgrade = ClickerConfig.upgrade(id);
            if (upgrade != null && upgrade.type() == UpgradeType.CLICK) {
                bonus += upgrade.modifier();
            }
        }
        return 1.0d + bonus;
    }

    /** L'aliment a cliquer : celui de la derniere amelioration du clic achetee, une pomme sinon. */
    private static ItemStack clickItem(ClickerData data) {
        ItemStack item = new ItemStack(Items.APPLE);
        for (String id : data.upgrades) {
            Upgrade upgrade = ClickerConfig.upgrade(id);
            if (upgrade != null && upgrade.type() == UpgradeType.CLICK) {
                item = ClickerConfig.stack(upgrade.item(), 1);
            }
        }
        return item;
    }

    /** {@code ClickerUpgrade.isTarget} : l'amelioration touche-t-elle ce batiment ? */
    private static boolean targets(Upgrade upgrade, Building building) {
        return switch (upgrade.type()) {
            case TERRAIN, CATEGORY -> building.categories().contains(upgrade.target());
            case BUILDING, MANY, POSTERIOR -> upgrade.target().equals(building.id());
            case GLOBAL -> true;
            case CLICK -> false;
        };
    }

    /** {@code ClickerUpgrade.apply} : la part que l'amelioration ajoute a la production du batiment. */
    private static double apply(ServerPlayer player, ClickerData data, Upgrade upgrade, Building building) {
        if (!targets(upgrade, building)) {
            return 0.0d;
        }
        return switch (upgrade.type()) {
            case GLOBAL, BUILDING, CATEGORY -> upgrade.modifier();
            case TERRAIN -> upgrade.modifier() * jobLevel(player, upgrade.job());
            case MANY -> upgrade.modifier() * data.count(building.id());
            case POSTERIOR -> building.index() == 0 ? 0.0d
                    : upgrade.modifier() * data.count(ClickerConfig.buildings().get(building.index() - 1).id());
            case CLICK -> 0.0d;
        };
    }

    private static int jobLevel(ServerPlayer player, String job) {
        try {
            return JobApi.level(player, Job.valueOf(job));
        } catch (IllegalArgumentException unknown) {
            return 0;
        }
    }

    private static double bonus(ServerPlayer player, ClickerData data, Building building) {
        double bonus = 0.0d;
        for (String id : data.upgrades) {
            Upgrade upgrade = ClickerConfig.upgrade(id);
            if (upgrade != null) {
                bonus += apply(player, data, upgrade, building);
            }
        }
        return bonus;
    }

    /** Recalcule la production : apres un achat, et de temps en temps (un niveau de metier peut avoir monte). */
    private static void recompute(ServerPlayer player, ClickerData data) {
        double total = BASE_RPS;
        data.rpsByBuilding.clear();
        for (Building building : ClickerConfig.buildings()) {
            int count = data.count(building.id());
            if (count <= 0) {
                continue;
            }
            double rps = building.baseRps() * count * (1.0d + bonus(player, data, building));
            data.rpsByBuilding.put(building.id(), rps);
            total += rps;
        }
        data.rps = total;
        data.rpsAt = now();
    }

    /** Une fois par seconde : la production des batiments de chaque joueur dont le clicker a servi. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        if (tick % 20 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ClickerData data = player.getExistingDataOrNull(DoHyAttachments.CLICKER.get());
            if (data == null || data.isEmpty()) {
                continue;
            }
            if (data.rps < 0.0d || now() - data.rpsAt >= 60L) {
                recompute(player, data);
            }
            data.rpsByBuilding.forEach((id, rps) -> data.production.merge(id, rps, Double::sum));
            data.add(data.rps);
            if (tick % REFRESH_TICKS == 0 && UiNet.isOpen(player, ClickerView.ID)) {
                UiNet.show(player, view(player, data));
            }
        }
    }

    // -- Ecran et actions ------------------------------------------------------

    public static void open(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        ClickerData data = data(player);
        if (data.started == 0L) {
            data.started = now();
        }
        recompute(player, data);
        UiNet.show(player, view(player, data));
    }

    public static void handle(ServerPlayer player, UiNet.Action action) {
        ClickerData data = data(player);
        if (data.isEmpty()) {
            return;
        }
        boolean changed = switch (action.verb()) {
            case "click" -> {
                click(data, action.amount());
                yield false;
            }
            case "building" -> buyBuilding(player, data, action.argument());
            case "upgrade" -> buyUpgrade(player, data, action.argument());
            case "shop" -> buyOffer(player, data, action.argument());
            default -> false;
        };
        if (changed) {
            recompute(player, data);
            UiNet.show(player, view(player, data));
        }
    }

    /**
     * Des clics, envoyes par paquets. Le serveur n'en garde qu'un par 50 ms
     * ecoulees depuis le paquet precedent ; les autres sont perdus, mais
     * comptes comme recus pour que l'ecran se recale.
     */
    private static void click(ClickerData data, int sent) {
        if (sent <= 0) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        long allowed = data.lastClick == 0L ? sent : (nowMillis - data.lastClick) / CLICK_MILLIS;
        data.lastClick = nowMillis;
        data.clicks += sent;
        long kept = Math.max(0L, Math.min(Math.min(sent, allowed), 40L));
        data.add(kept * clickValue(data));
    }

    private static boolean buyBuilding(ServerPlayer player, ClickerData data, String id) {
        Building building = ClickerConfig.building(id);
        if (building == null) {
            return false;
        }
        int count = data.count(id);
        double price = building.price(count);
        boolean previous = building.index() == 0
                || data.count(ClickerConfig.buildings().get(building.index() - 1).id()) > 0;
        if (!previous || count >= MAX_BUILDINGS || data.points < price) {
            return false;
        }
        data.add(-price);
        data.buildings.put(id, count + 1);
        chime(player, 1.2f);
        return true;
    }

    /** {@code ClickerUpgrade.checkConditions}. */
    private static boolean met(ClickerData data, Condition condition, long days) {
        return switch (condition.type()) {
            case QUANTITY -> data.total >= condition.value();
            case TIME -> days >= (long) condition.value();
            case BUILDING -> data.count(condition.id()) >= (int) condition.value();
            case UPGRADE -> data.has(condition.id());
        };
    }

    private static boolean ready(ClickerData data, Upgrade upgrade, long days) {
        for (Condition condition : upgrade.conditions()) {
            if (!met(data, condition, days)) {
                return false;
            }
        }
        return true;
    }

    private static boolean buyUpgrade(ServerPlayer player, ClickerData data, String id) {
        Upgrade upgrade = ClickerConfig.upgrade(id);
        if (upgrade == null || data.has(id) || data.points < upgrade.price()
                || !ready(data, upgrade, data.days(now()))) {
            return false;
        }
        data.add(-upgrade.price());
        data.upgrades.add(id);
        chime(player, 1.6f);
        return true;
    }

    private static void chime(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                0.5f, pitch);
    }

    // -- Clic Shop -------------------------------------------------------------

    /** Le rayon du joueur, retire s'il a expire ({@code PlayerClickerData.getShopItems}). */
    private static ClickerData.Shelf shelf(ServerPlayer player, ClickerData data, ShopType type) {
        ClickerData.Shelf shelf = data.shop.get(type.key());
        if (shelf == null || now() - shelf.updated > type.seconds()) {
            shelf = new ClickerData.Shelf();
            shelf.updated = now();
            shelf.items.addAll(draw(type, data, player.getRandom()));
            data.shop.put(type.key(), shelf);
        }
        return shelf;
    }

    /**
     * {@code ShopGlobalConfig.getRandomItem} : autant de tirages ponderes que
     * le rayon a de places, parmi les offres que les batiments du joueur
     * ouvrent ; un doublon ne compte pas, donc un rayon peut sortir incomplet.
     */
    private static List<String> draw(ShopType type, ClickerData data, RandomSource random) {
        List<ShopItem> pool = new ArrayList<>();
        int total = 0;
        for (ShopItem item : ClickerConfig.shop()) {
            if (item.type() == type && item.eligible(data.buildings)) {
                pool.add(item);
                total += item.weight();
            }
        }
        List<String> drawn = new ArrayList<>();
        if (pool.isEmpty()) {
            return drawn;
        }
        for (int slot = 0; slot < type.size(); slot++) {
            int pick = random.nextInt(total);
            for (ShopItem item : pool) {
                pick -= item.weight();
                if (pick < 0) {
                    if (!drawn.contains(item.id())) {
                        drawn.add(item.id());
                    }
                    break;
                }
            }
        }
        return drawn;
    }

    /** L'argument est « rayon:offre ». */
    private static boolean buyOffer(ServerPlayer player, ClickerData data, String argument) {
        int split = argument.indexOf(':');
        if (split < 0) {
            return false;
        }
        ShopType type;
        try {
            type = ShopType.valueOf(argument.substring(0, split).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
        String id = argument.substring(split + 1);
        ShopItem item = ClickerConfig.shopItem(id);
        ClickerData.Shelf shelf = shelf(player, data, type);
        if (item == null || item.type() != type || !shelf.items.contains(id)) {
            // L'offre a tourne entre-temps : l'ecran est remis a jour.
            return true;
        }
        int bought = shelf.bought.getOrDefault(id, 0);
        double price = item.price(bought);
        if (bought >= item.quantity() || bought >= MAX_SHOP_PURCHASES || data.points < price) {
            return false;
        }
        data.add(-price);
        shelf.bought.put(id, bought + 1);
        ItemStack stack = ClickerConfig.stack(item.item(), item.count());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        chime(player, 0.9f);
        return true;
    }

    // -- Vue -------------------------------------------------------------------

    static ClickerView view(ServerPlayer player, ClickerData data) {
        if (data.rps < 0.0d) {
            recompute(player, data);
        }
        long days = data.days(now());
        List<Building> all = ClickerConfig.buildings();

        // Les batiments possedes, puis le prochain : le reste est cache.
        List<ClickerView.Building> buildings = new ArrayList<>();
        int shown = 0;
        for (Building building : all) {
            int count = data.count(building.id());
            boolean reachable = building.index() == 0 || data.count(all.get(building.index() - 1).id()) > 0;
            if (count <= 0 && !reachable) {
                break;
            }
            shown++;
            List<String> bonuses = new ArrayList<>();
            for (String id : data.upgrades) {
                Upgrade upgrade = ClickerConfig.upgrade(id);
                if (upgrade != null && upgrade.type() != UpgradeType.CLICK && targets(upgrade, building)) {
                    bonuses.add(upgrade.name());
                }
            }
            List<ItemStack> unlocks = new ArrayList<>();
            for (ShopItem item : ClickerConfig.shop()) {
                if (item.requirement().containsKey(building.id()) && unlocks.size() < 12) {
                    unlocks.add(ClickerConfig.stack(item.item(), item.count()));
                }
            }
            List<String> categories = new ArrayList<>();
            for (String category : building.categories()) {
                categories.add(ClickerConfig.category(category));
            }
            buildings.add(new ClickerView.Building(building.id(), building.name(),
                    ClickerConfig.stack(building.item(), 1), count, building.price(count), building.baseRps(),
                    data.rpsByBuilding.getOrDefault(building.id(), 0.0d),
                    data.production.getOrDefault(building.id(), 0.0d), String.join(", ", categories), bonuses,
                    unlocks));
        }

        // Les ameliorations a portee : pas encore achetees, et dont les batiments
        // et les ameliorations requises sont connus du joueur.
        List<ClickerView.Upgrade> upgrades = new ArrayList<>();
        for (Upgrade upgrade : ClickerConfig.upgrades()) {
            if (data.has(upgrade.id()) || !inReach(data, upgrade)) {
                continue;
            }
            List<String> conditions = new ArrayList<>();
            boolean ready = true;
            for (Condition condition : upgrade.conditions()) {
                boolean met = met(data, condition, days);
                ready &= met;
                conditions.add((met ? "+" : "-") + describe(condition));
            }
            upgrades.add(new ClickerView.Upgrade(upgrade.id(), upgrade.name(), ClickerConfig.stack(upgrade.item(), 1),
                    badge(upgrade), upgrade.price(), ready, effect(upgrade), conditions));
        }
        upgrades.sort(Comparator.comparing((ClickerView.Upgrade upgrade) -> !upgrade.ready())
                .thenComparingDouble(ClickerView.Upgrade::price));
        if (upgrades.size() > MAX_UPGRADES_SHOWN) {
            upgrades = new ArrayList<>(upgrades.subList(0, MAX_UPGRADES_SHOWN));
        }

        List<ClickerView.Shelf> shop = new ArrayList<>();
        for (ShopType type : ShopType.values()) {
            ClickerData.Shelf shelf = shelf(player, data, type);
            List<ClickerView.Offer> offers = new ArrayList<>();
            for (String id : shelf.items) {
                ShopItem item = ClickerConfig.shopItem(id);
                if (item != null) {
                    int bought = shelf.bought.getOrDefault(id, 0);
                    offers.add(new ClickerView.Offer(id, ClickerConfig.stack(item.item(), item.count()),
                            item.price(bought), Math.max(0, item.quantity() - bought)));
                }
            }
            shop.add(new ClickerView.Shelf(type.key(), type.label(),
                    Math.max(0L, type.seconds() - (now() - shelf.updated)), offers));
        }

        return new ClickerView(data.points, data.rps(), clickValue(data), data.clicks, clickItem(data),
                (int) Math.min(Integer.MAX_VALUE, days), data.upgrades.size(), ClickerConfig.upgrades().size(),
                buildings, shown < all.size(), upgrades, shop);
    }

    /** Une amelioration se montre quand ses batiments sont possedes et ses ameliorations requises achetees. */
    private static boolean inReach(ClickerData data, Upgrade upgrade) {
        for (Condition condition : upgrade.conditions()) {
            switch (condition.type()) {
                case BUILDING -> {
                    if (data.count(condition.id()) <= 0) {
                        return false;
                    }
                }
                case UPGRADE -> {
                    if (!data.has(condition.id())) {
                        return false;
                    }
                }
                default -> {
                }
            }
        }
        // Une amelioration de terrain ou de categorie attend un batiment de sa categorie.
        if (upgrade.type() == UpgradeType.TERRAIN || upgrade.type() == UpgradeType.CATEGORY) {
            for (Building building : ClickerConfig.buildings()) {
                if (building.categories().contains(upgrade.target()) && data.count(building.id()) > 0) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private static String describe(Condition condition) {
        return switch (condition.type()) {
            case QUANTITY -> "Avoir récolté " + ClickerFormat.compact(condition.value()) + " ClicCoins";
            case TIME -> "Clicker ouvert depuis " + (long) condition.value() + " jours";
            case BUILDING -> {
                Building building = ClickerConfig.building(condition.id());
                yield "Posséder " + (int) condition.value() + " × " + (building == null ? condition.id() : building.name());
            }
            case UPGRADE -> {
                Upgrade upgrade = ClickerConfig.upgrade(condition.id());
                yield "Posséder l'amélioration " + (upgrade == null ? condition.id() : upgrade.name());
            }
        };
    }

    /** Le type en deux lettres, au coin de l'icone. */
    private static String badge(Upgrade upgrade) {
        return switch (upgrade.type()) {
            case CLICK -> "x2";
            case GLOBAL -> "G";
            case TERRAIN -> "T";
            case BUILDING -> upgrade.id().endsWith("_2") ? "II" : "I";
            case MANY -> "N";
            case POSTERIOR -> "P";
            case CATEGORY -> "C";
        };
    }

    private static String effect(Upgrade upgrade) {
        String percent = "+" + Math.round(upgrade.modifier() * 100.0d) + " %";
        Building building = ClickerConfig.building(upgrade.target());
        String name = building == null ? ClickerConfig.category(upgrade.target()) : building.name();
        return switch (upgrade.type()) {
            case CLICK -> "Double la production par clic";
            case GLOBAL -> percent + " de production pour tous les bâtiments";
            case TERRAIN -> percent + " pour la catégorie " + name + " par niveau du métier "
                    + jobName(upgrade.job());
            case BUILDING -> percent + " pour " + name;
            case MANY -> percent + " pour " + name + " par exemplaire possédé";
            case POSTERIOR -> percent + " pour " + name + " par exemplaire du bâtiment précédent";
            case CATEGORY -> percent + " pour la catégorie " + name;
        };
    }

    private static String jobName(String job) {
        try {
            return Job.valueOf(job).display();
        } catch (IllegalArgumentException unknown) {
            return job;
        }
    }
}
