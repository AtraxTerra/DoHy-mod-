package fr.atraxterra.dohy.palamod.mount;

import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.KitMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * L'inventaire de la monture ({@code MountContainer}, GUI 4) dans un coffre de
 * cinq rangees : la case de corne en haut a gauche, puis les 36 cases de la
 * monture, dont seules les {@code max(1, 36 x niveau / 100)} premieres
 * s'ouvrent. Une case fermee garde ce qu'elle contenait (une monture de plus
 * haut niveau l'avait remplie) sans qu'on puisse y toucher.
 *
 * <p>Le contenu est celui du joueur ({@link MountData}), lu et ecrit en direct.
 * Le client garde le coffre vanilla ; le serveur refuse ce qui n'a pas sa
 * place, et resynchronise.
 */
public class MountInventoryMenu extends ChestMenu {

    private static final int ROWS = 5;
    private static final int HORN = 0;
    private static final int FIRST = 9;
    /** {@code canInteractWith} : a moins de 10 blocs. */
    private static final double RANGE = 10.0d;

    private final ServerPlayer owner;

    public MountInventoryMenu(int containerId, Inventory inventory, ServerPlayer owner) {
        this(containerId, inventory, owner, new Live(MountManager.data(owner)));
    }

    private MountInventoryMenu(int containerId, Inventory inventory, ServerPlayer owner, Live container) {
        super(KitMenus.Use.MOUNT_INVENTORY.type(), containerId, inventory, container, ROWS);
        this.owner = owner;
        for (int index = 0; index < ROWS * 9; index++) {
            Slot old = slots.get(index);
            Slot replacement;
            if (index == HORN) {
                replacement = new Slot(container, index, old.x, old.y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return MountHornItem.tierOf(stack) != null;
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                };
            } else if (container.isOpen(index)) {
                replacement = new Slot(container, index, old.x, old.y);
            } else {
                replacement = new Locked(container, index, old.x, old.y);
            }
            replacement.index = index;
            slots.set(index, replacement);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (player != owner) {
            return false;
        }
        Dancarok mount = MountManager.summoned(owner);
        return mount != null && owner.distanceTo(mount) <= RANGE;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (slots.get(index) instanceof Locked) {
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof Locked) && super.canTakeItemForPickAll(stack, slot);
    }

    /**
     * Le coffre lit et ecrit directement les donnees du joueur : aucune copie
     * a resynchroniser. Une monture tuee menu ouvert, ou une corne qui casse
     * en plein coup, vide les donnees ; le coffre le voit aussitot, et rien ne
     * peut revenir de lui a la fermeture.
     */
    private static final class Live implements Container {

        private final MountData data;
        private final int open;
        private final ItemStack filler = Gui.filler();
        private final ItemStack locked = Gui.button(Items.GLASS_PANE, Gui.bad("Case fermee"),
                Gui.lore(Gui.hint("Une case de plus tous les trois niveaux environ")));

        Live(MountData data) {
            this.data = data;
            this.open = data.slots();
        }

        boolean isOpen(int index) {
            return index >= FIRST && index - FIRST < open;
        }

        private boolean writable(int index) {
            return index == HORN || isOpen(index);
        }

        @Override
        public int getContainerSize() {
            return ROWS * 9;
        }

        @Override
        public boolean isEmpty() {
            return data.horn().isEmpty() && data.inventory().stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int index) {
            if (index == HORN) {
                return data.horn();
            }
            if (index < FIRST || index >= getContainerSize()) {
                return filler;
            }
            ItemStack stored = data.inventory().get(index - FIRST);
            return isOpen(index) || !stored.isEmpty() ? stored : locked;
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            if (!writable(index)) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = getItem(index);
            if (stack.isEmpty() || count <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = stack.split(count);
            if (stack.isEmpty()) {
                setItem(index, ItemStack.EMPTY);
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            if (!writable(index)) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = getItem(index);
            setItem(index, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            if (index == HORN) {
                data.setHorn(stack);
            } else if (isOpen(index)) {
                data.inventory().set(index - FIRST, stack);
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }

    /** Une case fermee : rien n'y entre, rien n'en sort, rien ne s'y empile. */
    private static final class Locked extends Slot {

        Locked(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 0;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 0;
        }
    }
}
