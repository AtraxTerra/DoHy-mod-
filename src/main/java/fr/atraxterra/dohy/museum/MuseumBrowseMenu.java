package fr.atraxterra.dohy.museum;

import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
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
import java.util.List;

/**
 * Le musee, en vitrine navigable.
 *
 * <p>Deux vues. L'accueil presente les ailes avec leur avancement et la
 * recompense a la cle. Une aile ouverte affiche sa grille : les pieces exposees
 * apparaissent telles quelles, les manquantes en vitrail gris portant leur nom.
 * C'est ce contraste qui fait l'interet d'une vitrine — on voit le trou, pas une
 * liste de ce qu'on possede deja.
 */
public final class MuseumBrowseMenu extends DisplayMenu {

    private static final int ITEM_SLOTS = 45;

    private static final int SLOT_HEADER = 4;
    private static final int SLOT_FIRST_WING = 19;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREVIOUS = 47;
    private static final int SLOT_DEPOSIT = 49;
    private static final int SLOT_NEXT = 51;
    private static final int SLOT_INFO = 53;

    /** Clef conventionnelle du panneau mecene. */
    private static final String PATRON = "patron";

    /** Aile ouverte, ou {@code null} pour l'accueil. */
    private String wing;
    private int page;
    private List<String> wings = List.of();

    public MuseumBrowseMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 6);
        refresh();
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        if (slot == SLOT_DEPOSIT) {
            click(0.9f);
            open("Musee — depose, ferme pour exposer",
                    (containerId, inventory) -> new MuseumMenu(containerId, inventory, player));
            return;
        }

        if (wing == null) {
            if (slot == SLOT_BACK) {
                click(0.7f);
                Hub.open(player);
                return;
            }
            int index = slot - SLOT_FIRST_WING;
            if (index >= 0 && index < wings.size() && slot < SLOT_FIRST_WING + 7) {
                wing = wings.get(index);
                page = 0;
                click(0.9f);
            }
            return;
        }

        switch (slot) {
            case SLOT_BACK -> {
                wing = null;
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
                if ((page + 1) * ITEM_SLOTS < contents().size()) {
                    page++;
                    click();
                }
            }
            default -> {
                // Une vitrine ne se clique pas : on vient la regarder.
            }
        }
    }

    @Override
    protected void build() {
        if (wing == null) {
            renderHub();
        } else {
            renderWing();
        }
        set(SLOT_DEPOSIT, Gui.button(Items.HOPPER, "Deposer des pieces", Gui.lore(
                Gui.hint("ouvre le guichet de dons"),
                Gui.hint("un seul exemplaire est preleve"),
                Gui.hint("le reste t'est rendu"),
                Gui.blank(),
                Gui.action("clic pour ouvrir"))));
    }

    private List<Identifier> contents() {
        if (wing == null) {
            return List.of();
        }
        return PATRON.equals(wing) ? MuseumApi.patronItems() : MuseumApi.expected(wing);
    }

    private void renderHub() {
        frame();
        wings = new ArrayList<>(MuseumApi.categories());
        if (!MuseumApi.patronItems().isEmpty()) {
            wings.add(PATRON);
        }

        int totalExpected = 0;
        int totalDone = 0;
        int complete = 0;

        for (int index = 0; index < wings.size() && index < 7; index++) {
            String key = wings.get(index);
            boolean patron = PATRON.equals(key);
            List<Identifier> expected = patron ? MuseumApi.patronItems() : MuseumApi.expected(key);
            int done = donatedAmong(expected);
            boolean wingComplete = !expected.isEmpty() && done == expected.size();

            if (!patron) {
                totalExpected += expected.size();
                totalDone += done;
            }
            if (wingComplete) {
                complete++;
            }

            MuseumConfig.Section section = patron
                    ? MuseumConfig.config().patron()
                    : MuseumConfig.config().section(key);
            String name = section.display().isEmpty() ? key : section.display();

            List<Component> lore = new ArrayList<>();
            lore.add(Gui.bar(done, expected.size(), 20));
            lore.add(Gui.blank());
            if (patron) {
                lore.add(Component.literal("Optionnel — pieces uniques")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                lore.add(Gui.hint("aucune aile ne les exige"));
                lore.add(Gui.blank());
            }
            section.rewards().forEach(reward -> lore.add(Component.literal(
                            (wingComplete ? "✔ " : "a la cle : ") + MuseumApi.describe(reward))
                    .withStyle(wingComplete ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
            lore.add(Gui.blank());
            lore.add(Gui.action("clic pour ouvrir la vitrine"));

            set(SLOT_FIRST_WING + index, Gui.button(icon(key, patron),
                    Component.literal((wingComplete ? "✔ " : "") + name)
                            .withStyle(wingComplete ? ChatFormatting.GOLD : ChatFormatting.YELLOW),
                    lore));
        }

        set(SLOT_HEADER, Gui.button(Items.ENCHANTING_TABLE, "Musee", Gui.lore(
                Gui.field("pieces exposees", totalDone + " / " + totalExpected),
                Gui.bar(totalDone, totalExpected, 20),
                Gui.blank(),
                complete > 0
                        ? Gui.good(complete + " aile(s) complete(s)")
                        : Gui.hint("aucune aile complete"),
                Gui.field("valeur exposee", MoneyFormat.exact(museumValue())),
                Gui.blank(),
                Gui.hint("un don est definitif"))));

        set(SLOT_BACK, Gui.back("le menu"));
    }

    private void renderWing() {
        boolean patron = PATRON.equals(wing);
        List<Identifier> expected = contents();
        int pages = Math.max(1, (expected.size() + ITEM_SLOTS - 1) / ITEM_SLOTS);

        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            int index = page * ITEM_SLOTS + slot;
            if (index < expected.size()) {
                set(slot, vitrine(expected.get(index)));
            }
        }

        MuseumConfig.Section section = patron
                ? MuseumConfig.config().patron()
                : MuseumConfig.config().section(wing);
        String name = section.display().isEmpty() ? wing : section.display();
        int done = donatedAmong(expected);

        footer(SLOT_BACK, SLOT_PREVIOUS, SLOT_DEPOSIT, SLOT_NEXT, SLOT_INFO);
        set(SLOT_BACK, Gui.back("l'accueil du musee"));
        if (page > 0) {
            set(SLOT_PREVIOUS, Gui.previousPage(page, pages));
        }
        if (page + 1 < pages) {
            set(SLOT_NEXT, Gui.nextPage(page + 2, pages));
        }
        set(SLOT_INFO, Gui.button(icon(wing, patron), name, Gui.lore(
                Gui.bar(done, expected.size(), 20),
                Gui.field("page", (page + 1) + " / " + pages))));
    }

    /**
     * Une vitrine : la piece si elle est exposee, un vitrail gris sinon.
     *
     * <p>Montrer le manque plutot que l'acquis est tout l'interet de la vue —
     * une liste de ce qu'on possede deja n'indique pas quoi chercher.
     */
    private ItemStack vitrine(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        boolean donated = MuseumApi.data(player).has(id);
        Component itemName = new ItemStack(item).getHoverName();

        if (donated) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME,
                    itemName.copy().withStyle(ChatFormatting.AQUA));
            stack.set(DataComponents.LORE, new ItemLore(Gui.lore(
                    Gui.good("Expose"),
                    entry == null ? null
                            : Gui.hint("valeur " + MoneyFormat.exact(entry.basePrice())))));
            return stack;
        }

        // Les blocs colores ont ete unifies en 26.x : GRAY_STAINED_GLASS_PANE
        // n'existe plus, et teinter STAINED_GLASS_PANE demanderait un composant
        // de couleur non verifie. Le vitrail neutre rend le meme service.
        ItemStack pane = new ItemStack(Items.GLASS_PANE);
        pane.set(DataComponents.CUSTOM_NAME,
                itemName.copy().withStyle(ChatFormatting.DARK_GRAY));
        pane.set(DataComponents.LORE, new ItemLore(Gui.lore(
                Gui.bad("Manquant"),
                Gui.hint("apporte-en un au guichet"))));
        return pane;
    }

    /** Somme des prix de base des pieces exposees. Un chiffre a faire grandir. */
    private long museumValue() {
        long total = 0L;
        for (Identifier id : MuseumApi.data(player).all()) {
            PriceEntry entry = Prices.book().entry(id).orElse(null);
            if (entry != null) {
                total += entry.basePrice();
            }
        }
        return total;
    }

    private int donatedAmong(List<Identifier> expected) {
        int count = 0;
        for (Identifier id : expected) {
            if (MuseumApi.data(player).has(id)) {
                count++;
            }
        }
        return count;
    }

    private static Item icon(String key, boolean patron) {
        if (patron) {
            return Items.NETHER_STAR;
        }
        return switch (key) {
            case "FOOD" -> Items.BREAD;
            case "FOUND" -> Items.DIAMOND;
            case "FARM" -> Items.WHEAT;
            case "MOB_DROP" -> Items.ROTTEN_FLESH;
            case "NTM" -> Items.ANVIL;
            default -> Items.BOOK;
        };
    }
}
