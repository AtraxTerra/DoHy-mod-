package fr.atraxterra.dohy.collection;

import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.shop.ShopCategory;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.Hub;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Les collections, par categorie.
 *
 * <p>Deux vues, comme le musee. L'accueil montre l'avancement de chaque
 * categorie ; une categorie ouverte montre ses items avec leur compteur, leur
 * palier et leur prestige.
 *
 * <p>La grille reprend la disposition du shop — quarante-cinq emplacements puis
 * une rangee de commandes — parce que c'est l'ecran que le joueur connait deja.
 * Un prestige se prend au clic droit, au meme endroit qu'un achat dans le shop,
 * donc sans nouvelle convention a apprendre.
 */
public final class CollectionsMenu extends DisplayMenu {

    private static final int ITEM_SLOTS = 45;

    private static final int SLOT_HEADER = 4;
    private static final int[] SLOTS_CATEGORIES = {20, 22, 24, 29, 31, 33};

    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREVIOUS = 47;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_INFO = 53;

    /** Categorie ouverte, ou {@code null} pour l'accueil. */
    private ShopCategory category;
    private int page;
    private List<Identifier> items = List.of();

    public CollectionsMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 6);
        refresh();
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        if (slot == SLOT_CLOSE) {
            click(0.6f);
            player.closeContainer();
            return;
        }
        if (category == null) {
            for (int index = 0; index < SLOTS_CATEGORIES.length; index++) {
                if (SLOTS_CATEGORIES[index] == slot && index < ShopCategory.values().length) {
                    category = ShopCategory.values()[index];
                    page = 0;
                    click(0.9f);
                    return;
                }
            }
            return;
        }

        switch (slot) {
            case SLOT_BACK -> {
                category = null;
                page = 0;
                click(0.7f);
            }
            case SLOT_PREVIOUS -> {
                if (page > 0) {
                    page--;
                    click();
                }
            }
            case SLOT_NEXT -> {
                if ((page + 1) * ITEM_SLOTS < items.size()) {
                    page++;
                    click();
                }
            }
            default -> {
                if (slot < ITEM_SLOTS && button == 1) {
                    tryPrestige(slot);
                }
            }
        }
    }

    /** Le clic droit sur une collection complete passe un prestige. */
    private void tryPrestige(int slot) {
        int index = page * ITEM_SLOTS + slot;
        if (index >= items.size()) {
            return;
        }
        Identifier id = items.get(index);
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        if (entry == null) {
            return;
        }
        if (!CollectionApi.canPrestige(player, id, entry)) {
            failure();
            return;
        }
        String refusal = CollectionApi.prestige(player, id, entry);
        if (refusal == null) {
            success();
        } else {
            failure();
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    protected void build() {
        if (category == null) {
            buildHub();
        } else {
            buildGrid();
        }
    }

    private void buildHub() {
        frame();
        items = List.of();

        long unlockedTotal = 0;
        long trackedTotal = 0;
        int prestiges = 0;

        for (int index = 0; index < SLOTS_CATEGORIES.length
                && index < ShopCategory.values().length; index++) {
            ShopCategory tab = ShopCategory.values()[index];
            List<Identifier> ids = itemsOf(tab);
            int unlocked = 0;
            int complete = 0;
            int categoryPrestiges = 0;

            for (Identifier id : ids) {
                PriceEntry entry = Prices.book().entry(id).orElse(null);
                if (entry == null) {
                    continue;
                }
                int tier = CollectionApi.tierIndex(player, id, entry);
                if (tier >= 0) {
                    unlocked++;
                }
                if (CollectionApi.nextThreshold(player, id, entry) < 0) {
                    complete++;
                }
                categoryPrestiges += CollectionApi.prestigeLevel(player, id);
            }

            unlockedTotal += unlocked;
            trackedTotal += ids.size();
            prestiges += categoryPrestiges;

            set(SLOTS_CATEGORIES[index], Gui.button(tab.icon(), tab.display(), Gui.lore(
                    Gui.field("entamees", unlocked + " / " + ids.size()),
                    Gui.bar(unlocked, ids.size(), 20),
                    Gui.blank(),
                    complete > 0 ? Gui.good(complete + " collection(s) complete(s)")
                            : Gui.hint("aucune collection complete"),
                    categoryPrestiges > 0
                            ? Component.literal(categoryPrestiges + " niveau(x) de prestige")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                            : null,
                    Gui.blank(),
                    ids.isEmpty() ? Gui.hint("aucun item dans cette categorie")
                            : Gui.action("clic pour ouvrir"))));
        }

        set(SLOT_HEADER, Gui.button(Items.CHEST, "Tes collections", Gui.lore(
                Gui.field("entamees", unlockedTotal + " / " + trackedTotal),
                Gui.bar(unlockedTotal, trackedTotal, 20),
                Gui.blank(),
                Gui.field("prestiges cumules", String.valueOf(prestiges)),
                Gui.blank(),
                Gui.hint("le premier palier debloque la vente"),
                Gui.hint("les suivants relevent le prix plancher"),
                Gui.hint("un prestige remet le compteur a zero"),
                Gui.hint("et augmente la quantite ecoulable"))));

        set(SLOT_BACK, Gui.back("le menu"));
        set(SLOT_CLOSE, Gui.close());
    }

    private void buildGrid() {
        items = itemsOf(category);
        int pages = Math.max(1, (items.size() + ITEM_SLOTS - 1) / ITEM_SLOTS);

        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            int index = page * ITEM_SLOTS + slot;
            if (index < items.size()) {
                set(slot, tile(items.get(index)));
            }
        }

        footer(SLOT_BACK, SLOT_PREVIOUS, SLOT_CLOSE, SLOT_NEXT, SLOT_INFO);
        set(SLOT_BACK, Gui.back("les categories"));
        set(SLOT_CLOSE, Gui.close());
        if (page > 0) {
            set(SLOT_PREVIOUS, Gui.previousPage(page, pages));
        }
        if (page + 1 < pages) {
            set(SLOT_NEXT, Gui.nextPage(page + 2, pages));
        }
        set(SLOT_INFO, Gui.button(category.icon(), category.display(), Gui.lore(
                Gui.field("page", (page + 1) + " / " + pages),
                Gui.field("items", String.valueOf(items.size())),
                Gui.blank(),
                Gui.hint("clic droit sur une collection complete"),
                Gui.hint("pour passer un prestige"))));
    }

    /**
     * La vignette d'une collection.
     *
     * <p>L'item lui-meme sert d'icone, avec son nom traduit : jamais
     * d'identifiant. La pile porte le numero du palier, qui se lit sans ouvrir
     * la description.
     */
    private ItemStack tile(Identifier id) {
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (entry == null || item == null) {
            return ItemStack.EMPTY;
        }

        long count = CollectionApi.countOf(player, id);
        int tier = CollectionApi.tierIndex(player, id, entry);
        int total = CollectionTiers.config().tiersFor(id, entry.tier()).size();
        long next = CollectionApi.nextThreshold(player, id, entry);
        int prestige = CollectionApi.prestigeLevel(player, id);

        List<Component> lore = new ArrayList<>();
        lore.add(Gui.field("recoltes", MoneyFormat.exact(count)));
        lore.add(Gui.field("palier", (tier + 1) + " / " + total));
        if (next > 0) {
            lore.add(Gui.bar(count, next, 20));
            lore.add(Gui.hint("  prochain palier a " + MoneyFormat.exact(next)));
        } else {
            lore.add(Gui.good("tous les paliers franchis"));
        }

        lore.add(Gui.blank());
        if (!entry.sellable()) {
            lore.add(Gui.hint("item en achat seul : pas de vente a debloquer"));
        } else if (tier < 0) {
            lore.add(Gui.bad("vente verrouillee"));
            lore.add(Gui.hint("  il t'en manque " + MoneyFormat.exact(
                    CollectionApi.missingToUnlock(player, id, entry))));
        } else {
            lore.add(Gui.good("vente debloquee"));
            double floor = CollectionApi.floorBonus(player, id, entry);
            if (floor > 0) {
                lore.add(Gui.hint("  plancher releve de "
                        + Math.round(floor * 100) + " points"));
            }
        }

        if (prestige > 0) {
            lore.add(Gui.blank());
            lore.add(Component.literal("Prestige " + prestige).withStyle(ChatFormatting.LIGHT_PURPLE));
            lore.add(Gui.hint("  quantite ecoulable x"
                    + String.format("%.1f", CollectionApi.s0Multiplier(player, id))));
        }
        if (CollectionApi.canPrestige(player, id, entry)) {
            lore.add(Gui.blank());
            lore.add(Gui.action("clic droit : passer un prestige"));
            lore.add(Gui.hint("  le compteur repart a zero"));
        }

        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(64, tier + 1)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private List<Identifier> itemsOf(ShopCategory target) {
        var book = Prices.book();
        List<Identifier> ids = new ArrayList<>();
        book.items().forEach((id, entry) -> {
            if (target.key().equalsIgnoreCase(entry.category())) {
                ids.add(id);
            }
        });
        // Les collections les plus avancees d'abord : c'est l'ordre qui montre
        // ce qui est pres d'aboutir, donc ce qui vaut un dernier effort.
        ids.sort(Comparator
                .<Identifier>comparingInt(id -> -tierOf(id))
                .thenComparing(Identifier::toString));
        return ids;
    }

    private int tierOf(Identifier id) {
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        return entry == null ? -1 : CollectionApi.tierIndex(player, id, entry);
    }
}
