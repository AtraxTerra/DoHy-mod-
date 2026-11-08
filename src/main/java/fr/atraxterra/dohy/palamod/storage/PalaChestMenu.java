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
 * en (12 + col x 18, 8 + row x 18), inventaire du joueur en (39 + c x 18,
 * 174 + r x 18), barre d'action a y = 232. Le panneau fait 240 x 256.
 */
public class PalaChestMenu extends AbstractContainerMenu {

    public static final int CHEST_X = 12;
    public static final int CHEST_Y = 8;
    public static final int PLAYER_X = 39;
    public static final int PLAYER_Y = 174;
    public static final int HOTBAR_Y = 232;

    private final Container chest;
    private final ChestTier tier;

    /** Cote client : un conteneur vide de la bonne taille, rempli par le serveur. */
    public PalaChestMenu(MenuType<?> type, ChestTier tier, int containerId, Inventory inventory) {
        this(type, tier, containerId, inventory, new SimpleContainer(ChestTier.SIZE));
    }

    public PalaChestMenu(MenuType<?> type, ChestTier tier, int containerId, Inventory inventory,
                         Container chest) {
        super(type, containerId);
        checkContainerSize(chest, ChestTier.SIZE);
        this.chest = chest;
        this.tier = tier;

        for (int row = 0; row < ChestTier.ROWS; row++) {
            for (int column = 0; column < ChestTier.COLUMNS; column++) {
                addSlot(new Slot(chest, column + row * ChestTier.COLUMNS,
                        CHEST_X + column * 18, CHEST_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        PLAYER_X + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_X + column * 18, HOTBAR_Y));
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
        if (index < ChestTier.SIZE) {
            if (!moveItemStackTo(stack, ChestTier.SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, ChestTier.SIZE, false)) {
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
