package fr.atraxterra.dohy.palamod.machine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Le menu d'une machine : des cases, et deux nombres synchronises.
 *
 * <p>Il remplace le coffre vanilla du chapitre precedent. Ce qu'on gagne a
 * l'ecrire : une <b>disposition propre a chaque machine</b> (une entree en haut
 * et six sorties en rangee pour le cobblebreaker, fruit et charbon a gauche
 * pour le crusher) et surtout une <b>barre de progression</b>, impossible avec
 * un coffre puisque rien n'y transite entre le serveur et le client en dehors
 * des objets.
 *
 * <p><b>Les deux nombres.</b> {@link ContainerData} est le seul canal qui
 * synchronise des entiers d'un menu ouvert sans ecrire de paquet reseau :
 * {@code broadcastChanges} les envoie quand ils changent. On y met l'avancement
 * du cycle et sa duree totale ; l'ecran n'a qu'a faire le rapport. La duree y
 * figure parce qu'elle depend du palier du cobblebreaker, que le client ne
 * connait pas autrement.
 *
 * <p>Le constructeur client, celui que prend {@code RegisterMenuScreensEvent},
 * ne recoit rien du serveur : il fabrique un inventaire vide de la bonne taille
 * et des donnees a zero, que la synchronisation remplit aussitot.
 */
public class MachineMenu extends AbstractContainerMenu {

    /** Indices des deux entiers synchronises. */
    public static final int DATA_WORK = 0;
    public static final int DATA_DURATION = 1;
    public static final int DATA_COUNT = 2;

    /** Coordonnees vanilla de l'inventaire du joueur. Voir tools/gen_gui.py. */
    private static final int PLAYER_X = 8;
    private static final int PLAYER_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final Container container;
    private final ContainerData data;
    private final MachineLayout layout;

    public MachineMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                       Container container, ContainerData data, MachineLayout layout) {
        super(type, containerId);
        this.container = container;
        this.data = data;
        this.layout = layout;

        checkContainerSize(container, layout.slots().size());
        container.startOpen(playerInventory.player);

        for (int index = 0; index < layout.slots().size(); index++) {
            MachineLayout.SlotPos position = layout.slots().get(index);
            addSlot(new Slot(container, index, position.x(), position.y()));
        }
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    /** Constructeur client : rien n'est connu encore, la synchronisation suit. */
    public MachineMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                       MachineLayout layout) {
        this(type, containerId, playerInventory,
                new SimpleContainer(layout.slots().size()),
                new SimpleContainerData(DATA_COUNT), layout);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_X + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_X + column * 18, HOTBAR_Y));
        }
    }

    public MachineLayout layout() {
        return layout;
    }

    /** Avancement du cycle, entre 0 et 1. Zero quand la machine ne tourne pas. */
    public float progress() {
        int duration = data.get(DATA_DURATION);
        if (duration <= 0) {
            return 0.0f;
        }
        return Math.clamp(data.get(DATA_WORK) / (float) duration, 0.0f, 1.0f);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    /**
     * Le shift-clic.
     *
     * <p>Depuis le joueur, l'objet va dans les cases de la machine ou il est
     * accepte — et <b>seulement celles-la</b> : envoyer une pierre dans un bac
     * de sortie la rendrait irrecuperable par la machine. Depuis la machine, il
     * revient dans le sac.
     *
     * <p>La boucle rend {@code ItemStack.EMPTY} quand la pile est epuisee, ce
     * qui est la condition d'arret que le jeu attend ; sans elle, le shift-clic
     * boucle a l'infini.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = layout.slots().size();

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!insertIntoMachine(stack)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** Insere dans la premiere case d'entree qui accepte cet objet. */
    private boolean insertIntoMachine(ItemStack stack) {
        for (int index = 0; index < layout.slots().size(); index++) {
            if (!layout.slots().get(index).input()) {
                continue;
            }
            if (container.canPlaceItem(index, stack)
                    && moveItemStackTo(stack, index, index + 1, false)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
