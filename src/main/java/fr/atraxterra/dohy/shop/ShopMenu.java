package fr.atraxterra.dohy.shop;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Le shop, presente comme un coffre.
 *
 * <p>Choix d'implementation : une sous-classe de {@link ChestMenu} declaree avec
 * le type vanilla {@code GENERIC_9x6}. Le client ouvre donc son ecran de coffre
 * habituel et <b>aucun code client n'est necessaire</b> — ni ecran, ni
 * enregistrement de {@code MenuType}, ni paquet. C'est ce qui evite d'affronter
 * la reecriture du rendu en mode retenu de 26.2 pour un premier shop
 * fonctionnel.
 *
 * <p>Le conteneur ne contient que des copies d'affichage. Aucun clic n'est
 * delegue a {@code super}, donc aucun item ne peut etre deplace, duplique ou
 * perdu : chaque clic est traduit en operation economique, puis la page est
 * redessinee.
 *
 * <p>Contrepartie assumee : le client predit localement le deplacement d'items
 * avant d'etre corrige par le serveur, ce qui provoque un bref scintillement au
 * clic. C'est le comportement de tous les shops en coffre, et le prix a payer
 * pour n'avoir aucun code de rendu.
 */
public final class ShopMenu extends ChestMenu {

    /** Emplacements de marchandise : cinq rangees. */
    private static final int ITEM_SLOTS = 45;

    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_FIRST_TAB = 46;
    private static final int SLOT_BALANCE = 52;
    private static final int SLOT_NEXT = 53;

    private final ServerPlayer player;
    private final Container display;

    private ShopCategory category = ShopCategory.FARM;
    private int page;
    private List<Identifier> currentItems = List.of();

    public ShopMenu(int containerId, Inventory inventory, ServerPlayer player) {
        this(containerId, inventory, player, new SimpleContainer(54));
    }

    private ShopMenu(int containerId, Inventory inventory, ServerPlayer player, Container display) {
        super(MenuType.GENERIC_9x6, containerId, inventory, display, 6);
        this.player = player;
        this.display = display;
        refresh();
    }

    @Override
    public boolean stillValid(Player candidate) {
        return candidate == player;
    }

    /**
     * Aucun deplacement rapide : un shift-clic depuis l'inventaire du joueur ne
     * doit pas pousser d'items dans un conteneur d'affichage.
     */
    @Override
    public ItemStack quickMoveStack(Player candidate, int slot) {
        return ItemStack.EMPTY;
    }

    /**
     * Traduit chaque clic en operation economique.
     *
     * <p>{@code super.clicked} n'est jamais appele, ce qui rend structurellement
     * impossible la perte ou la duplication d'un item par le shop.
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player candidate) {
        if (!(candidate instanceof ServerPlayer serverPlayer) || serverPlayer != player) {
            return;
        }
        if (slotId < 0 || slotId >= 54) {
            // Clic hors de la grille du shop (inventaire du joueur, ou en
            // dehors de la fenetre) : on ignore, rien ne bouge.
            refresh();
            return;
        }

        if (slotId >= ITEM_SLOTS) {
            handleControl(slotId, serverPlayer);
            return;
        }

        int index = page * ITEM_SLOTS + slotId;
        if (index >= currentItems.size()) {
            return;
        }
        handleTrade(currentItems.get(index), button, input, serverPlayer);
    }

    private void handleControl(int slotId, ServerPlayer serverPlayer) {
        if (slotId == SLOT_PREVIOUS && page > 0) {
            page--;
            click(serverPlayer);
        } else if (slotId == SLOT_NEXT && (page + 1) * ITEM_SLOTS < currentItems.size()) {
            page++;
            click(serverPlayer);
        } else if (slotId >= SLOT_FIRST_TAB && slotId < SLOT_FIRST_TAB + ShopCategory.values().length) {
            ShopCategory target = ShopCategory.values()[slotId - SLOT_FIRST_TAB];
            if (target != category) {
                category = target;
                page = 0;
                click(serverPlayer);
            }
        }
        refresh();
    }

    private void handleTrade(Identifier id, int button, ContainerInput input, ServerPlayer serverPlayer) {
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (entry == null || item == null) {
            return;
        }

        boolean rightClick = button == 1;
        boolean shift = input == ContainerInput.QUICK_MOVE;

        if (rightClick) {
            buy(serverPlayer, item, entry, shift ? 16 : 1);
        } else if (entry.sellable()) {
            sell(serverPlayer, item, entry, shift ? Integer.MAX_VALUE : 1);
        } else {
            serverPlayer.sendSystemMessage(Component.literal("Cet item est en achat seul.")
                    .withStyle(ChatFormatting.GRAY));
        }
        refresh();
    }

    private void sell(ServerPlayer serverPlayer, Item item, PriceEntry entry, int wanted) {
        NonNullList<ItemStack> items = serverPlayer.getInventory().getNonEquipmentItems();
        int removed = 0;

        for (int slot = 0; slot < items.size() && removed < wanted; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int take = Math.min(stack.getCount(), wanted - removed);
            stack.shrink(take);
            removed += take;
        }

        if (removed == 0) {
            serverPlayer.sendSystemMessage(Component.literal("Tu n'en as pas.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        long payout = entry.basePrice() * removed;
        long balance = Economy.credit(serverPlayer, payout);
        sound(serverPlayer, true);
        serverPlayer.sendSystemMessage(Component.literal("Vendu " + removed + "x pour ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(payout)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    private void buy(ServerPlayer serverPlayer, Item item, PriceEntry entry, int wanted) {
        long unitPrice = entry.buyPrice();
        if (unitPrice <= 0L) {
            serverPlayer.sendSystemMessage(Component.literal("Cet item n'est pas en vente.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        long cost = unitPrice * wanted;
        if (!Economy.tryDebit(serverPlayer, cost)) {
            sound(serverPlayer, false);
            serverPlayer.sendSystemMessage(Component.literal("Il te manque "
                            + MoneyFormat.exact(cost - Economy.balance(serverPlayer)) + ".")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ItemStack bought = new ItemStack(item, wanted);
        if (!serverPlayer.getInventory().add(bought)) {
            // Inventaire plein : l'item tombe au sol plutot que de disparaitre
            // apres avoir ete paye.
            serverPlayer.drop(bought, false);
        }
        sound(serverPlayer, true);
        serverPlayer.sendSystemMessage(Component.literal("Achete " + wanted + "x pour ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(cost)).withStyle(ChatFormatting.GOLD)));
    }

    /** Redessine la page courante et la pousse au client. */
    private void refresh() {
        currentItems = itemsOf(category);

        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            int index = page * ITEM_SLOTS + slot;
            display.setItem(slot, index < currentItems.size()
                    ? renderOffer(currentItems.get(index))
                    : ItemStack.EMPTY);
        }

        int pages = Math.max(1, (currentItems.size() + ITEM_SLOTS - 1) / ITEM_SLOTS);

        display.setItem(SLOT_PREVIOUS, page > 0
                ? labelled(Items.ARROW, "Page precedente", List.of())
                : ItemStack.EMPTY);
        display.setItem(SLOT_NEXT, page + 1 < pages
                ? labelled(Items.ARROW, "Page suivante", List.of())
                : ItemStack.EMPTY);

        for (ShopCategory tab : ShopCategory.values()) {
            boolean active = tab == category;
            display.setItem(SLOT_FIRST_TAB + tab.ordinal(), labelled(
                    tab.icon(),
                    (active ? "> " : "") + tab.display(),
                    List.of(Component.literal(itemsOf(tab).size() + " item(s)")
                            .withStyle(ChatFormatting.DARK_GRAY))));
        }

        display.setItem(SLOT_BALANCE, labelled(Items.GOLD_INGOT,
                "Solde : " + MoneyFormat.exact(Economy.balance(player)),
                List.of(
                        Component.literal("Page " + (page + 1) + " / " + pages)
                                .withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("clic gauche : vendre 1").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("shift + gauche : tout vendre").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("clic droit : acheter 1").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("shift + droit : acheter 16").withStyle(ChatFormatting.DARK_GRAY))));

        broadcastChanges();
    }

    private List<Identifier> itemsOf(ShopCategory target) {
        var book = Prices.book();
        List<Identifier> ids = new ArrayList<>();
        book.items().forEach((id, entry) -> {
            if (target.key().equalsIgnoreCase(entry.category())) {
                ids.add(id);
            }
        });
        // Tri par prix croissant : la progression du joueur se lit dans la page,
        // et l'ordre reste stable d'une ouverture a l'autre.
        ids.sort(Comparator
                .<Identifier>comparingLong(id -> book.entry(id).map(PriceEntry::basePrice).orElse(0L))
                .thenComparing(Identifier::toString));
        return ids;
    }

    /** La vignette d'une offre : l'item reel, avec son tarif en description. */
    private ItemStack renderOffer(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        if (item == null || entry == null) {
            return ItemStack.EMPTY;
        }

        int owned = countOwned(item);
        List<Component> lore = new ArrayList<>();
        if (entry.sellable()) {
            lore.add(Component.literal("Vente : " + MoneyFormat.exact(entry.basePrice()))
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lore.add(Component.literal("Achat seul").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (entry.buyPrice() > 0L) {
            lore.add(Component.literal("Achat : " + MoneyFormat.exact(entry.buyPrice()))
                    .withStyle(ChatFormatting.RED));
        }
        lore.add(Component.literal("Tu en as : " + owned).withStyle(ChatFormatting.GRAY));

        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(64, owned)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private ItemStack labelled(Item item, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.YELLOW));
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    private int countOwned(Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void click(ServerPlayer serverPlayer) {
        serverPlayer.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.4f, 1.0f);
    }

    private void sound(ServerPlayer serverPlayer, boolean success) {
        serverPlayer.playNotifySound(
                success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                SoundSource.MASTER, 0.5f, success ? 1.4f : 1.0f);
    }
}
