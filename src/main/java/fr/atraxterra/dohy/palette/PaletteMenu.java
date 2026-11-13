package fr.atraxterra.dohy.palette;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Le stock d'un groupe de palettes : une rangee de 16 cases par palette.
 *
 * <p>Un groupe de 3 x 3 fait neuf rangees, trop pour tenir avec l'inventaire
 * dans un ecran en interface x4 : on en montre six a la fois, et la molette (ou
 * les fleches) fait defiler. La fenetre est tenue cote serveur : les cases
 * visibles sont celles des rangees {@code offset} a {@code offset + 5}, et le
 * jeu renvoie leur contenu au client a chaque defilement, comme pour tout
 * changement de case.
 *
 * <p>Deux boutons ({@link #clickMenuButton}) : 0 monte d'une rangee, 1 descend.
 */
public class PaletteMenu extends AbstractContainerMenu {

    public static final int COLUMNS = 16;
    public static final int VISIBLE = 6;
    public static final int WINDOW = COLUMNS * VISIBLE;
    public static final int GRID_X = 12;
    public static final int GRID_Y = 22;
    public static final int PANEL_WIDTH = GRID_X * 2 + COLUMNS * 18;

    public static final int BUTTON_UP = 0;
    public static final int BUTTON_DOWN = 1;

    private static final double REACH = 8.0d;

    private final int groupW;
    private final int groupD;
    private final int rows;
    private final DataSlot offset;
    private final ContainerData totals;
    private final Container window;
    private final PaletteBlockEntity origin;
    private final List<PaletteBlockEntity> members;

    /** Cote client : la taille du groupe vient du serveur, le contenu suit. */
    public PaletteMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readVarInt(), buffer.readVarInt(), null);
    }

    private PaletteMenu(int containerId, Inventory inventory, int groupW, int groupD, PaletteBlockEntity origin) {
        super(DoHyPalette.MENU.get(), containerId);
        this.groupW = Math.max(1, groupW);
        this.groupD = Math.max(1, groupD);
        this.rows = this.groupW * this.groupD;
        this.origin = origin;
        this.members = origin == null ? List.of() : origin.members();
        this.offset = DataSlot.standalone();
        if (origin == null) {
            this.window = new SimpleContainer(WINDOW);
            this.totals = new SimpleContainerData(PaletteFamily.values().length);
        } else {
            this.window = new Window();
            this.totals = new Totals();
        }
        addDataSlot(offset);
        addDataSlots(totals);

        for (int row = 0; row < VISIBLE; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new WindowSlot(window, row * COLUMNS + column, GRID_X + column * 18, GRID_Y + row * 18));
            }
        }
        int playerX = (PANEL_WIDTH - 9 * 18) / 2;
        int playerY = playerY();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, playerX + column * 18, playerY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, playerX + column * 18, playerY + 58));
        }
    }

    /** Ouvre le stock du groupe de cette palette. */
    public static void open(ServerPlayer player, PaletteBlockEntity pallet) {
        List<PaletteBlockEntity> members = pallet.members();
        int w = members.size() == 1 ? 1 : pallet.groupW();
        int d = members.size() == 1 ? 1 : pallet.groupD();
        Component title = Component.translatable("block.dohy.palette");
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new PaletteMenu(id, inventory, w, d, pallet),
                title), buffer -> {
            buffer.writeVarInt(w);
            buffer.writeVarInt(d);
        });
    }

    // -- Ce que l'ecran lit ----------------------------------------------------------------

    public int groupW() {
        return groupW;
    }

    public int groupD() {
        return groupD;
    }

    public int rows() {
        return rows;
    }

    public int offset() {
        return offset.get();
    }

    /** Les rangees montrees : six au plus. */
    public int shownRows() {
        return Math.min(VISIBLE, rows);
    }

    public boolean scrollable() {
        return rows > VISIBLE;
    }

    /** Le nombre d'objets du groupe pour une famille. */
    public int total(PaletteFamily family) {
        return totals.get(family.ordinal());
    }

    /** La hauteur de l'inventaire du joueur : sous les rangees montrees. */
    public int playerY() {
        return GRID_Y + shownRows() * 18 + 14;
    }

    public int panelHeight() {
        return playerY() + 58 + 18 + 7;
    }

    // -- Defilement ------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int max = Math.max(0, rows - VISIBLE);
        int now = offset.get();
        if (id == BUTTON_UP && now > 0) {
            offset.set(now - 1);
            return true;
        }
        if (id == BUTTON_DOWN && now < max) {
            offset.set(now + 1);
            return true;
        }
        return false;
    }

    // -- Les cases -------------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        if (origin == null) {
            return true;
        }
        for (PaletteBlockEntity member : members) {
            if (member.isRemoved()) {
                return false;
            }
        }
        return origin.members().size() == members.size()
                && player.distanceToSqr(origin.getBlockPos().getCenter()) <= REACH * REACH;
    }

    /** Des palettes vers le sac ; du sac vers les rangees montrees. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < WINDOW) {
            if (!moveItemStackTo(stack, WINDOW, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (PaletteFamily.of(stack) == null || !moveItemStackTo(stack, 0, WINDOW, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** Une case de la fenetre : active si sa rangee existe dans le groupe. */
    private final class WindowSlot extends Slot {

        WindowSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        private boolean exists() {
            int row = getContainerSlot() / COLUMNS;
            return row < shownRows() && offset.get() + row < rows;
        }

        @Override
        public boolean isActive() {
            return exists();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return exists() && PaletteFamily.of(stack) != null;
        }

        @Override
        public boolean mayPickup(Player player) {
            return exists();
        }
    }

    /** Cote serveur : les cases visibles, prises dans le stock du groupe. */
    private final class Window implements Container {

        private int index(int slot) {
            return offset.get() * COLUMNS + slot;
        }

        private boolean inside(int slot) {
            return slot >= 0 && slot < WINDOW && index(slot) < origin.getContainerSize();
        }

        @Override
        public int getContainerSize() {
            return WINDOW;
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < WINDOW; slot++) {
                if (!getItem(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return inside(slot) ? origin.getItem(index(slot)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return inside(slot) ? origin.removeItem(index(slot), count) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return inside(slot) ? origin.removeItemNoUpdate(index(slot)) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (inside(slot)) {
                origin.setItem(index(slot), stack);
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return inside(slot) && origin.canPlaceItem(index(slot), stack);
        }

        @Override
        public void setChanged() {
            origin.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return PaletteMenu.this.stillValid(player);
        }

        @Override
        public void clearContent() {
        }
    }

    /** Cote serveur : le compte d'objets par famille, envoye a l'ecran quand il change. */
    private final class Totals implements ContainerData {

        @Override
        public int get(int index) {
            PaletteFamily[] families = PaletteFamily.values();
            return index >= 0 && index < families.length ? Math.min(Short.MAX_VALUE, origin.count(families[index])) : 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return PaletteFamily.values().length;
        }
    }
}
