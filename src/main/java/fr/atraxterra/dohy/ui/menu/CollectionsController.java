package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.collection.CollectionTiers;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.shop.ShopCategory;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les collections : les six categories, puis les objets d'une categorie en
 * vignettes. Clic droit sur une collection complete : un prestige.
 */
public final class CollectionsController extends MenuController {

    public static final CollectionsController INSTANCE = new CollectionsController();

    /** La categorie ouverte par joueur ; absente pour l'accueil. */
    private final Map<UUID, ShopCategory> open = new ConcurrentHashMap<>();

    private CollectionsController() {
        super("collections");
    }

    @Override
    public void open(ServerPlayer player) {
        open.remove(player.getUUID());
        super.open(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        ShopCategory category = open.get(player.getUUID());
        return category == null ? categories(player) : items(player, category);
    }

    private MenuView categories(ServerPlayer player) {
        long started = 0;
        long tracked = 0;
        int prestiges = 0;
        List<Card> cards = new ArrayList<>();
        for (ShopCategory category : ShopCategory.values()) {
            List<Identifier> ids = itemsOf(player, category);
            int unlocked = 0;
            int complete = 0;
            int categoryPrestiges = 0;
            for (Identifier id : ids) {
                PriceEntry entry = Prices.book().entry(id).orElse(null);
                if (entry == null) {
                    continue;
                }
                if (CollectionApi.tierIndex(player, id, entry) >= 0) {
                    unlocked++;
                }
                if (CollectionApi.nextThreshold(player, id, entry) < 0) {
                    complete++;
                }
                categoryPrestiges += CollectionApi.prestigeLevel(player, id);
            }
            started += unlocked;
            tracked += ids.size();
            prestiges += categoryPrestiges;
            Card card = Card.of(new ItemStack(category.icon()), category.display(), List.of(
                            "Entamees " + unlocked + " / " + ids.size(),
                            complete > 0 ? complete + " complete(s)" : "Aucune complete"))
                    .accent(0xFF2F5BEA).progress(unlocked, ids.size())
                    .badge(categoryPrestiges > 0 ? "P" + categoryPrestiges : "");
            cards.add(ids.isEmpty() ? card.state(MenuView.DISABLED) : card.action("category:" + category.key()));
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("ENTAMEES", started + " / " + tracked, 0xFFFFFFFF),
                new MenuView.Stat("PRESTIGES", String.valueOf(prestiges), 0xFFEC4FB5));
        return new MenuView(id(), "Collections", "Recolter releve le prix plancher", stats,
                List.of(new MenuView.Section("CATEGORIES", "resources", MenuView.GRID, cards)), true);
    }

    private MenuView items(ServerPlayer player, ShopCategory category) {
        List<Card> tiles = new ArrayList<>();
        int unlocked = 0;
        List<Identifier> ids = itemsOf(player, category);
        for (Identifier id : ids) {
            PriceEntry entry = Prices.book().entry(id).orElse(null);
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (entry == null || item == null) {
                continue;
            }
            long count = CollectionApi.countOf(player, id);
            int tier = CollectionApi.tierIndex(player, id, entry);
            int tiers = CollectionTiers.config().tiersFor(id, entry.tier()).size();
            long next = CollectionApi.nextThreshold(player, id, entry);
            int prestige = CollectionApi.prestigeLevel(player, id);
            if (tier >= 0) {
                unlocked++;
            }
            List<String> lines = new ArrayList<>();
            lines.add("Recoltes : " + MoneyFormat.exact(count));
            lines.add("Palier " + (tier + 1) + " / " + tiers);
            List<String> tooltip = new ArrayList<>();
            tooltip.add(next > 0 ? "Prochain palier a " + MoneyFormat.exact(next) : "Tous les paliers franchis");
            if (!entry.sellable()) {
                tooltip.add("Achat seul : pas de vente a debloquer");
            } else if (tier < 0) {
                tooltip.add("Vente verrouillee : il en manque "
                        + MoneyFormat.exact(CollectionApi.missingToUnlock(player, id, entry)));
            } else {
                double floor = CollectionApi.floorBonus(player, id, entry);
                tooltip.add(floor > 0 ? "Vendable, plancher +" + Math.round(floor * 100) + " points" : "Vendable");
            }
            if (prestige > 0) {
                tooltip.add("Prestige " + prestige);
            }
            boolean canPrestige = CollectionApi.canPrestige(player, id, entry);
            Card card = Card.of(new ItemStack(item), new ItemStack(item).getHoverName().getString(), lines)
                    .tooltip(tooltip)
                    .badge(prestige > 0 ? "P" + prestige : tier >= 0 ? String.valueOf(tier + 1) : "");
            if (next > 0) {
                card = card.progress(count, next);
            }
            if (next < 0) {
                card = card.state(MenuView.DONE);
            } else if (tier < 0 && entry.sellable()) {
                // Vente verrouillee : un liseré rouge, l'objet reste lisible.
                card = card.accent(0xFF8A2A22);
            }
            if (canPrestige) {
                card = card.accent(0xFFEC4FB5).right("prestige:" + id + "|passer un prestige");
            }
            tiles.add(card);
        }
        List<MenuView.Stat> stats = List.of(new MenuView.Stat("ENTAMEES", unlocked + " / " + ids.size(), 0xFFFFFFFF));
        return new MenuView(id(), category.display(), "Clic droit sur une collection complete : prestige", stats,
                List.of(new MenuView.Section("", "", MenuView.TILES, tiles)), true);
    }

    /** Les plus avancees d'abord : ce qui est pres d'aboutir merite un dernier effort. */
    private static List<Identifier> itemsOf(ServerPlayer player, ShopCategory category) {
        List<Identifier> ids = new ArrayList<>();
        Prices.book().items().forEach((id, entry) -> {
            if (category.key().equalsIgnoreCase(entry.category())) {
                ids.add(id);
            }
        });
        ids.sort(Comparator.<Identifier>comparingInt(id -> {
            PriceEntry entry = Prices.book().entry(id).orElse(null);
            return entry == null ? 1 : -CollectionApi.tierIndex(player, id, entry);
        }).thenComparing(Identifier::toString));
        return ids;
    }

    @Override
    protected void back(ServerPlayer player) {
        if (open.remove(player.getUUID()) != null) {
            super.open(player);
        } else {
            super.back(player);
        }
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("category:")) {
            ShopCategory category = ShopCategory.byKey(key.substring(9));
            if (category != null) {
                open.put(player.getUUID(), category);
            }
            return;
        }
        if (key.startsWith("prestige:")) {
            Identifier id = Identifier.tryParse(key.substring(9));
            PriceEntry entry = id == null ? null : Prices.book().entry(id).orElse(null);
            if (entry == null || !CollectionApi.canPrestige(player, id, entry)) {
                failure(player);
                return;
            }
            String refusal = CollectionApi.prestige(player, id, entry);
            if (refusal == null) {
                success(player);
            } else {
                failure(player);
                player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            }
        }
    }

    static Item fallback() {
        return Items.PAPER;
    }
}
