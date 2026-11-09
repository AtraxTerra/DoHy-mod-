package fr.atraxterra.dohy.palamod.storage;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Le menu des coffres en amethyste et en titane.
 *
 * <p>Coordonnees de {@code ModdedChestContainer} : case {@code col + row * 12}
 * en (12 + col x 18, 8 + row x 18), inventaire du joueur centre en
 * (x + c x 18, 174 + r x 18), barre d'action a y = 232. Le panneau fait
 * 240 x 256 pour 12 colonnes, 186 x 256 pour les 9 du coffre en or.
 */
public class PalaChestMenu extends AbstractContainerMenu {

    public static final int CHEST_X = 12;
    public static final int CHEST_Y = 8;
    public static final int PLAYER_Y = 174;
    public static final int HOTBAR_Y = 232;

    private final Container chest;
    private final ChestTier tier;

    /** Cote client : un conteneur vide de la bonne taille, rempli par le serveur. */
    public PalaChestMenu(MenuType<?> type, ChestTier tier, int containerId, Inventory inventory) {
        this(type, tier, containerId, inventory, new SimpleContainer(tier.size()));
    }

    public PalaChestMenu(MenuType<?> type, ChestTier tier, int containerId, Inventory inventory,
                         Container chest) {
        super(type, containerId);
        checkContainerSize(chest, tier.size());
        this.chest = chest;
        this.tier = tier;

        for (int row = 0; row < ChestTier.ROWS; row++) {
            for (int column = 0; column < tier.columns(); column++) {
                addSlot(new Slot(chest, column + row * tier.columns(),
                        CHEST_X + column * 18, CHEST_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        tier.playerX() + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, tier.playerX() + column * 18, HOTBAR_Y));
        }
    }

    public ChestTier tier() {
        return tier;
    }

    @Override
    public boolean stillValid(Player player) {
        return chest.stillValid(player);
    }

    /** Du coffre vers le joueur, du joueur vers les 108 cases. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int size = tier.size();
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, size, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
