package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.equipment.PalaEquipment;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Les interfaces du golem : l'accueil, l'arbre de competences, les choix, le
 * stockage.
 *
 * <p>GuardianGolem dessinait un arbre sur une image de 3 000 x 4 500 qu'on
 * faisait defiler. Ici l'arbre tient dans un coffre de six rangees, une
 * colonne de trois cases par voie, comme les autres interfaces de DoHy : rien
 * ne s'y deplace, chaque clic est une action.
 */
public final class GolemMenus {

    private GolemMenus() {
    }

    public static void openMain(ServerPlayer player, GuardianGolem golem) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new Main(containerId, inventory, player, golem),
                Component.literal("Golem gardien")));
    }

    static boolean usable(ServerPlayer player, GuardianGolem golem) {
        return golem.isAlive() && !golem.has(GolemUpgrade.MAD_GOLEM)
                && player.distanceToSqr(golem) <= GuardianGolem.MENU_RANGE * GuardianGolem.MENU_RANGE * 4;
    }

    private static ItemStack glint(ItemStack stack) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    // -- Accueil ---------------------------------------------------------------

    static final class Main extends DisplayMenu {

        private static final int INFO = 13;
        private static final int TREE = 29;
        private static final int CHOICES = 31;
        private static final int STORAGE = 33;
        private static final int CLOSE = 40;

        private final GuardianGolem golem;

        Main(int containerId, Inventory inventory, ServerPlayer player, GuardianGolem golem) {
            super(containerId, inventory, player, 5);
            this.golem = golem;
            refresh();
        }

        @Override
        protected void build() {
            frame();
            int level = golem.golemLevel();
            List<Component> lore = new ArrayList<>();
            lore.add(Gui.field("Proprietaire", golem.describeOwner()));
            lore.add(Gui.field("Niveau", level + " / " + GuardianGolem.MAX_LEVEL));
            if (level < GuardianGolem.MAX_LEVEL) {
                lore.add(Gui.bar(golem.subXp(), GuardianGolem.xpForLevel(level + 1), 20));
            }
            lore.add(Gui.field("Points de capacite", String.valueOf(golem.points())));
            lore.add(Gui.field("Vie", Math.round(golem.getHealth()) + " / " + Math.round(golem.getMaxHealth())));
            lore.add(Gui.field("Degats", String.format(Locale.ROOT, "%.2f",
                    golem.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))));
            if (!golem.whitelist().isEmpty()) {
                lore.add(Gui.field("Liste blanche", String.join(", ", golem.whitelist())));
            }
            if (golem.isLoading()) {
                lore.add(Gui.bad("En chargement"));
            }
            lore.add(Gui.blank());
            lore.add(Gui.hint("Nourrissez-le de lingots ou de blocs (accroupi : toute la pile)."));
            set(INFO, Gui.button(PalaGolem.GUARDIAN_STONE.get(), Component.literal("Golem gardien")
                    .withStyle(ChatFormatting.GOLD), lore));
            set(TREE, Gui.button(Items.BOOK, "Arbre de competences", List.of(
                    Gui.field("Points", String.valueOf(golem.points())), Gui.action("Cliquer pour ouvrir"))));
            if (Choices.available(golem)) {
                set(CHOICES, Gui.button(Items.COMPARATOR, "Choix", List.of(Gui.action("Cliquer pour ouvrir"))));
            }
            if (golem.has(GolemUpgrade.STOCKAGE)) {
                set(STORAGE, Gui.button(Items.CHEST, "Inventaire du golem", List.of(
                        Gui.action("Cliquer pour ouvrir"))));
            }
            set(CLOSE, Gui.close());
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            if (!usable(player, golem)) {
                player.closeContainer();
                return;
            }
            switch (slot) {
                case TREE -> {
                    click();
                    open("Arbre de competences", (id, inventory) -> new Tree(id, inventory, player, golem));
                }
                case CHOICES -> {
                    if (Choices.available(golem)) {
                        click();
                        open("Choix du golem", (id, inventory) -> new Choices(id, inventory, player, golem));
                    }
                }
                case STORAGE -> {
                    if (golem.has(GolemUpgrade.STOCKAGE)) {
                        click();
                        openStorage(player, golem);
                    }
                }
                case CLOSE -> player.closeContainer();
                default -> {
                }
            }
        }
    }

    public static void openStorage(ServerPlayer player, GuardianGolem golem) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new FilteredChestMenu(MenuType.GENERIC_9x2, containerId,
                        inventory, golem.storage(), 2),
                Component.literal("Inventaire du golem")));
    }

    // -- Arbre -------------------------------------------------------------

    static final class Tree extends DisplayMenu {

        private static final int ROOT = 4;
        private static final int INFO = 0;
        private static final int BACK = 8;

        private final GuardianGolem golem;
        private final Map<Integer, GolemUpgrade> bySlot = new java.util.HashMap<>();

        Tree(int containerId, Inventory inventory, ServerPlayer player, GuardianGolem golem) {
            super(containerId, inventory, player, 6);
            this.golem = golem;
            int[] firstColumn = {0, 0, 3, 6};
            Map<GolemUpgrade.Branch, Integer> placed = new EnumMap<>(GolemUpgrade.Branch.class);
            for (GolemUpgrade upgrade : GolemUpgrade.values()) {
                if (upgrade.branch() == GolemUpgrade.Branch.ROOT) {
                    bySlot.put(ROOT, upgrade);
                    continue;
                }
                int index = placed.merge(upgrade.branch(), 1, Integer::sum) - 1;
                int row = 1 + index / 3;
                int column = firstColumn[upgrade.branch().ordinal()] + index % 3;
                bySlot.put(row * 9 + column, upgrade);
            }
            refresh();
        }

        @Override
        protected void build() {
            set(INFO, Gui.button(Items.EXPERIENCE_BOTTLE, Component.literal("Points de capacite")
                    .withStyle(ChatFormatting.GOLD), List.of(
                    Gui.field("Disponibles", String.valueOf(golem.points())),
                    Gui.hint("Un point par niveau du golem."))));
            set(BACK, Gui.back("le golem"));
            bySlot.forEach((slot, upgrade) -> set(slot, icon(upgrade)));
        }

        private ItemStack icon(GolemUpgrade upgrade) {
            boolean owned = golem.has(upgrade);
            boolean buyable = golem.canBuy(upgrade);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(upgrade.description()).withStyle(ChatFormatting.GRAY));
            lore.add(Gui.blank());
            lore.add(Gui.field("Cout", upgrade.cost() + (upgrade.cost() > 1 ? " points" : " point")));
            if (upgrade.parent() != null) {
                lore.add(Gui.field("Pre-requis", upgrade.parent().label()));
            }
            if (owned) {
                lore.add(Gui.good("Acquise"));
            } else if (buyable) {
                lore.add(Gui.action("Cliquer pour l'acheter"));
            } else {
                lore.add(Gui.bad("Indisponible"));
            }
            ChatFormatting color = owned ? ChatFormatting.GREEN : buyable ? ChatFormatting.YELLOW : ChatFormatting.RED;
            ItemStack stack = Gui.button(iconItem(upgrade), Component.literal(upgrade.label()).withStyle(color), lore);
            return owned ? glint(stack) : stack;
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            if (!usable(player, golem)) {
                player.closeContainer();
                return;
            }
            if (slot == BACK) {
                click();
                open("Golem gardien", (id, inventory) -> new Main(id, inventory, player, golem));
                return;
            }
            GolemUpgrade upgrade = bySlot.get(slot);
            if (upgrade == null || golem.has(upgrade)) {
                return;
            }
            if (!golem.canManage(player) || !golem.buy(upgrade)) {
                failure();
                return;
            }
            success();
            if (upgrade == GolemUpgrade.MAD_GOLEM) {
                player.closeContainer();
            }
        }
    }

    /** {@code ModGuardianUpgrades} : les icones de l'original, au plus proche. */
    static Item iconItem(GolemUpgrade upgrade) {
        return switch (upgrade) {
            case GOLEMANCY -> Items.GOLDEN_APPLE;
            case UTILS -> Items.NETHER_STAR;
            case WAR -> Items.DIAMOND_SWORD;
            case FARMER -> Items.DIAMOND_HOE;
            case INNOVATIVE -> Items.BOOK;
            case GREEDY -> Items.COOKED_BEEF;
            case MECAGOLEM -> Items.REDSTONE;
            case DISCREET -> Items.ENDER_PEARL;
            case INBOX -> PalaGolem.MINI_GOLEM.get();
            case STOCKAGE -> Items.CHEST;
            case HOUSEWORK -> Items.PAPER;
            case WITHER_HUNTER -> Items.WITHER_SKELETON_SKULL;
            case FIGHTER -> PalaEquipment.PALADIUM_SWORD.get();
            case BERSERKER -> Items.GOLDEN_CARROT;
            case FIGHTER2 -> PalaEquipment.GREEN_PALADIUM_SWORD.get();
            case ADVANCED_RESISTANCE -> PalaEquipment.PALADIUM_CHESTPLATE.get();
            case SPECIALIZED_RESISTANCE -> Items.POTION;
            case CARNAGE -> Items.FEATHER;
            case ACCELERATION -> PalaItems.SPEED_ORB.get();
            case LAST_BREATH -> Items.TOTEM_OF_UNDYING;
            case MAD_GOLEM -> Items.TNT;
            case MAGICIAN -> Items.BOW;
            case DESYNCHRONIZATION -> Items.PLAYER_HEAD;
            case PACIFICATION -> Items.WOODEN_HOE;
            case INDUSTRIAL_REGIME -> PalaGolem.CROQUETTE.get();
            case IRON_MAKER -> Items.IRON_INGOT;
            case AMETHYST_MAKER -> PalaItems.AMETHYST_INGOT.get();
            case TITANE_MAKER -> PalaItems.TITANE_INGOT.get();
            case PALADIUM_MAKER -> PalaItems.PALADIUM_INGOT.get();
            case SHIELD -> Items.SHIELD;
        };
    }

    // -- Choix ---------------------------------------------------------------

    static final class Choices extends DisplayMenu {

        private static final int BACK = 45;

        private final GuardianGolem golem;

        Choices(int containerId, Inventory inventory, ServerPlayer player, GuardianGolem golem) {
            super(containerId, inventory, player, 6);
            this.golem = golem;
            refresh();
        }

        static boolean available(GuardianGolem golem) {
            for (GolemChoice choice : GolemChoice.values()) {
                if (golem.has(choice.gate())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void build() {
            int row = 0;
            for (GolemChoice choice : GolemChoice.values()) {
                if (!golem.has(choice.gate())) {
                    continue;
                }
                set(row * 9, Gui.button(iconItem(choice.gate()), Component.literal(choice.label())
                        .withStyle(ChatFormatting.GOLD), List.of()));
                for (int option = 1; option <= choice.options(); option++) {
                    boolean active = golem.choice(choice) == option;
                    ItemStack stack = Gui.button(active ? Items.EMERALD : Items.FLINT,
                            Component.literal(choice.option(option))
                                    .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                            List.of(active ? Gui.good("Choisi") : Gui.action("Cliquer pour choisir")));
                    set(row * 9 + 1 + option, active ? glint(stack) : stack);
                }
                row++;
            }
            set(BACK, Gui.back("le golem"));
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            if (!usable(player, golem)) {
                player.closeContainer();
                return;
            }
            if (slot == BACK) {
                click();
                open("Golem gardien", (id, inventory) -> new Main(id, inventory, player, golem));
                return;
            }
            int row = slot / 9;
            int option = slot % 9 - 1;
            int index = 0;
            for (GolemChoice choice : GolemChoice.values()) {
                if (!golem.has(choice.gate())) {
                    continue;
                }
                if (index++ == row) {
                    if (option >= 1 && option <= choice.options() && golem.canManage(player)) {
                        golem.setChoice(choice, option);
                        click();
                    }
                    return;
                }
            }
        }
    }
}
