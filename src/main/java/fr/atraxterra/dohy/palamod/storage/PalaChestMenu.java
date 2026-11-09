package fr.atraxterra.dohy.palamod.storage;

import fr.atraxterra.dohy.palamod.pvp.RingItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Le menu des coffres de PalaMod et du coffre en or.
 *
 * <p>Coordonnees de {@code ModdedChestContainer} : case {@code col + row * 12}
 * en (12 + col x 18, 8 + row x 18), inventaire du joueur centre en
 * (x + c x 18, 174 + r x 18), barre d'action a y = 232. Le panneau fait
 * 240 x 256 pour 12 colonnes, 186 x 256 pour les 9 du coffre en or.
 *
 * <p>Les cases d'anneau ({@code SlotRing}, {@code SlotRingEndium}) forment une
 * colonne a droite de la grille. L'original les posait en x = 228, a cheval
 * sur le bord de son panneau de 240 ; le panneau s'elargit ici d'une colonne
 * pour les contenir.
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
        this(type, tier, containerId, inventory, new SimpleContainer(tier.containerSize()));
    }

    public PalaChestMenu(MenuType<?> type, ChestTier tier, int containerId, Inventory inventory,
                         Container chest) {
        super(type, containerId);
        checkContainerSize(chest, tier.containerSize());
        this.chest = chest;
        this.tier = tier;

        for (int row = 0; row < ChestTier.ROWS; row++) {
            for (int column = 0; column < tier.columns(); column++) {
                addSlot(new Slot(chest, column + row * tier.columns(),
                        CHEST_X + column * 18, CHEST_Y + row * 18));
            }
        }
        for (int ring = 0; ring < tier.ringSlots(); ring++) {
            addSlot(new RingSlot(chest, tier, tier.size() + ring, tier.ringX(), CHEST_Y + ring * 18));
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

    /** Une case d'anneau : un anneau du metal du coffre, un seul. */
    private static final class RingSlot extends Slot {

        private final ChestTier tier;

        RingSlot(Container chest, ChestTier tier, int index, int x, int y) {
            super(chest, index, x, y);
            this.tier = tier;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isRing(tier, stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static boolean isRing(ChestTier tier, ItemStack stack) {
        return stack.getItem() instanceof RingItem ring && ring.endium() == tier.endiumRings();
    }

    @Override
    public boolean stillValid(Player player) {
        return chest.stillValid(player);
    }

    /**
     * Du coffre vers le joueur ; du joueur vers les anneaux pour un anneau du
     * bon metal, puis vers la grille.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int size = tier.size();
        int chestEnd = tier.containerSize();
        if (index < chestEnd) {
            if (!moveItemStackTo(stack, chestEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(isRing(tier, stack) && moveItemStackTo(stack, size, chestEnd, false))
                && !moveItemStackTo(stack, 0, size, false)) {
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
