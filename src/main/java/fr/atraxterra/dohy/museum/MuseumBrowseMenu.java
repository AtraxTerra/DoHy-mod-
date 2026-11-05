package fr.atraxterra.dohy.museum;

import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
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
import java.util.List;

/**
 * Le musee, en vitrine navigable.
 *
 * <p>Conteneur d'affichage comme le shop : les clics ne sont jamais delegues a
 * {@code super}, donc aucune piece ne peut sortir d'une vitrine.
 *
 * <p>Deux vues. L'accueil presente les ailes avec leur avancement et la
 * recompense a la cle. Une aile ouverte affiche sa grille : les pieces exposees
 * apparaissent telles quelles, les manquantes en vitrail gris portant leur nom.
 * C'est ce contraste qui fait l'interet d'une vitrine — on voit le trou, pas une
 * liste de ce qu'on possede deja.
 */
public final class MuseumBrowseMenu extends ChestMenu {

    private static final int ITEM_SLOTS = 45;

    private static final int SLOT_HEADER = 4;
    private static final int SLOT_FIRST_WING = 10;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_DEPOSIT = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_FOOTER = 53;

    /** Clef conventionnelle du panneau mecene. */
    private static final String PATRON = "patron";

    private final ServerPlayer player;
    private final Container display;

    /** Aile ouverte, ou {@code null} pour l'accueil. */
    private String wing;
    private int page;
    private List<String> wings = List.of();

    public MuseumBrowseMenu(int containerId, Inventory inventory, ServerPlayer player) {
        this(containerId, inventory, player, new SimpleContainer(54));
    }

    private MuseumBrowseMenu(int containerId, Inventory inventory,
                             ServerPlayer player, Container display) {
        super(MenuType.GENERIC_9x6, containerId, inventory, display, 6);
        this.player = player;
        this.display = display;
        refresh();
    }

    @Override
    public boolean stillValid(Player candidate) {
        return candidate == player;
    }

    @Override
    public ItemStack quickMoveStack(Player candidate, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player candidate) {
        if (!(candidate instanceof ServerPlayer serverPlayer) || serverPlayer != player) {
            return;
        }
        if (slotId < 0 || slotId >= 54) {
            refresh();
            return;
        }

        if (slotId == SLOT_DEPOSIT) {
            openDeposit(serverPlayer);
            return;
        }

        if (wing == null) {
            int index = slotId - SLOT_FIRST_WING;
            if (index >= 0 && index < wings.size()) {
                wing = wings.get(index);
                page = 0;
                click(0.9f);
            }
        } else if (slotId == SLOT_BACK) {
            wing = null;
            page = 0;
            click(0.7f);
        } else if (slotId == SLOT_PREVIOUS && page > 0) {
            page--;
            click(1.0f);
        } else if (slotId == SLOT_NEXT && (page + 1) * ITEM_SLOTS < contents().size()) {
            page++;
            click(1.0f);
        }
        refresh();
    }

    /**
     * Ouvre le guichet de dons.
     *
     * <p>Appel direct : l'appelant retourne immediatement apres, donc la
     * fermeture de ce menu n'intervient pas au milieu du traitement d'un clic.
     * Ni {@code ServerPlayer.server} ni {@code getServer()} ne sont accessibles
     * en 26.2, donc differer au tick suivant demanderait de passer par le
     * niveau — inutile ici.
     */
    private void openDeposit(ServerPlayer serverPlayer) {
        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new MuseumMenu(containerId, inventory, serverPlayer),
                Component.literal("Musee — depose, ferme pour exposer")));
    }

    private List<Identifier> contents() {
        if (wing == null) {
            return List.of();
        }
        return PATRON.equals(wing) ? MuseumApi.patronItems() : MuseumApi.expected(wing);
    }

    private void refresh() {
        for (int slot = 0; slot < 54; slot++) {
            display.setItem(slot, ItemStack.EMPTY);
        }
        if (wing == null) {
            renderHub();
        } else {
            renderWing();
        }
        display.setItem(SLOT_DEPOSIT, labelled(Items.HOPPER, "Deposer des pieces", List.of(
                Component.literal("ouvre le guichet de dons").withStyle(ChatFormatting.GRAY),
                Component.literal("un seul exemplaire est preleve")
                        .withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("le reste t'est rendu").withStyle(ChatFormatting.DARK_GRAY))));
        broadcastChanges();
    }

    private void renderHub() {
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
            lore.add(Component.literal(bar(done, expected.size()) + "  " + done + " / " + expected.size())
                    .withStyle(wingComplete ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            lore.add(Component.empty());
            if (patron) {
                lore.add(Component.literal("Optionnel — pieces uniques")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                lore.add(Component.literal("aucune aile ne les exige")
                        .withStyle(ChatFormatting.DARK_GRAY));
                lore.add(Component.empty());
            }
            section.rewards().forEach(reward -> lore.add(Component.literal(
                            (wingComplete ? "✔ " : "a la cle : ") + MuseumApi.describe(reward))
                    .withStyle(wingComplete ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
            lore.add(Component.empty());
            lore.add(Component.literal("clic pour ouvrir la vitrine").withStyle(ChatFormatting.YELLOW));

            display.setItem(SLOT_FIRST_WING + index, labelled(
                    icon(key, patron),
                    (wingComplete ? "✔ " : "") + name,
                    lore));
        }

        long value = museumValue();
        display.setItem(SLOT_HEADER, labelled(Items.ENCHANTING_TABLE, "Musee", List.of(
                Component.literal(bar(totalDone, totalExpected) + "  " + totalDone
                                + " / " + totalExpected + " pieces")
                        .withStyle(ChatFormatting.GRAY),
                Component.literal(complete + " aile(s) complete(s)").withStyle(ChatFormatting.AQUA),
                Component.empty(),
                Component.literal("valeur exposee : " + MoneyFormat.exact(value))
                        .withStyle(ChatFormatting.GOLD),
                Component.empty(),
                Component.literal("Un don est definitif.").withStyle(ChatFormatting.DARK_GRAY))));
    }

    private void renderWing() {
        boolean patron = PATRON.equals(wing);
        List<Identifier> expected = contents();
        int pages = Math.max(1, (expected.size() + ITEM_SLOTS - 1) / ITEM_SLOTS);

        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            int index = page * ITEM_SLOTS + slot;
            if (index >= expected.size()) {
                continue;
            }
            display.setItem(slot, vitrine(expected.get(index)));
        }

        MuseumConfig.Section section = patron
                ? MuseumConfig.config().patron()
                : MuseumConfig.config().section(wing);
        String name = section.display().isEmpty() ? wing : section.display();
        int done = donatedAmong(expected);

        display.setItem(SLOT_BACK, labelled(Items.ARROW, "Retour", List.of(
                Component.literal("vers l'accueil du musee").withStyle(ChatFormatting.GRAY))));
        if (page > 0) {
            display.setItem(SLOT_PREVIOUS, labelled(Items.PAPER, "Page precedente", List.of()));
        }
        if (page + 1 < pages) {
            display.setItem(SLOT_NEXT, labelled(Items.PAPER, "Page suivante", List.of()));
        }
        display.setItem(SLOT_FOOTER, labelled(icon(wing, patron), name, List.of(
                Component.literal(bar(done, expected.size()) + "  " + done + " / " + expected.size())
                        .withStyle(ChatFormatting.GRAY),
                Component.literal("page " + (page + 1) + " / " + pages)
                        .withStyle(ChatFormatting.DARK_GRAY))));
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
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Expose").withStyle(ChatFormatting.AQUA),
                    entry == null ? Component.empty()
                            : Component.literal("valeur " + MoneyFormat.exact(entry.basePrice()))
                            .withStyle(ChatFormatting.DARK_GRAY))));
            return stack;
        }

        // Les blocs colores ont ete unifies en 26.x : GRAY_STAINED_GLASS_PANE
        // n'existe plus, et teinter STAINED_GLASS_PANE demanderait un composant
        // de couleur non verifie. Le vitrail neutre rend le meme service.
        ItemStack pane = new ItemStack(Items.GLASS_PANE);
        pane.set(DataComponents.CUSTOM_NAME,
                itemName.copy().withStyle(ChatFormatting.DARK_GRAY));
        pane.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Manquant").withStyle(ChatFormatting.RED),
                Component.literal("apporte-en un au guichet").withStyle(ChatFormatting.DARK_GRAY))));
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

    /** Barre de progression en caracteres pleins. */
    private static String bar(int done, int total) {
        int width = 20;
        int filled = total <= 0 ? 0 : Math.round((float) done / total * width);
        return "▏".repeat(Math.max(0, filled))
                + "·".repeat(Math.max(0, width - filled));
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
            case "COMPONENT" -> Items.CRAFTING_TABLE;
            case "NTM" -> Items.ANVIL;
            default -> Items.BOOK;
        };
    }

    private ItemStack labelled(Item item, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(ChatFormatting.YELLOW));
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    private void click(float pitch) {
        play(SoundEvents.UI_BUTTON_CLICK.value(), pitch);
    }

    private void play(SoundEvent sound, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.MASTER, 0.4f, pitch);
    }
}
