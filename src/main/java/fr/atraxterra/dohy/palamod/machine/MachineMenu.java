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

    /** Indices des entiers synchronises. */
    public static final int DATA_WORK = 0;
    public static final int DATA_DURATION = 1;
    public static final int DATA_TANK = 2;
    public static final int DATA_TANK_GOAL = 3;
    public static final int DATA_WORK2 = 4;
    public static final int DATA_DURATION2 = 5;
    /** Quatre valeurs libres, propres a chaque machine : les bacs du crusher. */
    public static final int DATA_EXTRA = 6;
    public static final int EXTRA_COUNT = 4;
    /** Une valeur libre de plus : la recette affichee par le crusher. */
    public static final int DATA_SELECTED = DATA_EXTRA + EXTRA_COUNT;
    public static final int DATA_COUNT = DATA_SELECTED + 1;

    /** L'inventaire du joueur, en bas du panneau du kit (MachineKitScreen, 262 x 236). */
    public static final int PLAYER_X = 51;
    public static final int PLAYER_Y = 150;
    public static final int HOTBAR_Y = 210;

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
            addSlot(new MachineSlot(container, index, position.x(), position.y(),
                    crafts(layout) && !position.input()));
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

    /**
     * Une case qui demande au conteneur ce qu'elle accepte.
     *
     * <p>Un {@link Slot} vanilla repond oui a tout dans {@code mayPlace} : seul
     * le shift-clic de ce menu consultait {@code canPlaceItem}. Un clic direct
     * pouvait donc poser n'importe quoi n'importe ou — bloquer un bac de sortie
     * du cobblebreaker avec de la terre, ou laisser tomber sa pioche dans la
     * voidstone, qui la detruit. Le filtre s'applique maintenant aux deux
     * gestes, avec la meme regle.
     */
    private static final class MachineSlot extends Slot {

        private final Container machine;
        private final int machineIndex;
        private final boolean crafted;

        MachineSlot(Container machine, int index, int x, int y, boolean crafted) {
            super(machine, index, x, y);
            this.machine = machine;
            this.machineIndex = index;
            this.crafted = crafted;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return machine.canPlaceItem(machineIndex, stack);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            if (crafted && !stack.isEmpty()) {
                stack.onCraftedBy(player, stack.getCount());
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer server && !stack.isEmpty()) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                // Les succes : ce que le crusher a broye, les particules du cobblebreaker.
                if (machine instanceof CrusherBlockEntity && machineIndex == CrusherBlockEntity.SLOT_OUTPUT) {
                    fr.atraxterra.dohy.palamod.achievement.Achievements.count(server, "crusher:" + id, stack.getCount());
                }
                if (machine instanceof CobblebreakerBlockEntity && machineIndex >= CobblebreakerBlockEntity.SLOT_FIRST_OUTPUT
                        && id.endsWith("_particle")) {
                    fr.atraxterra.dohy.palamod.achievement.Achievements.count(server, "cobblebreaker:particles",
                            stack.getCount());
                }
            }
        }
    }

    /**
     * La palamachine fabrique, comme un etabli : ce qui sort de sa case de
     * resultat compte dans la statistique « objets fabriques », celle que lit
     * l'arbre de faction (les Unclaim Finders orange, rouge et bleu). Les autres
     * machines broient, cuisent, cassent : leurs sorties ne sont pas des
     * fabrications.
     */
    private static boolean crafts(MachineLayout layout) {
        return layout == MachineLayout.PALAMACHINE;
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

    /**
     * Une machine a boutons. Le clic d'un bouton de l'ecran
     * ({@code MachineKitScreen.Button}) arrive ici, cote serveur, par le paquet
     * que le jeu reserve aux boutons de menu (celui de la table d'enchantement).
     */
    public interface Buttons {
        boolean pressButton(net.minecraft.server.level.ServerPlayer player, int id);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (container instanceof Buttons buttons
                && player instanceof net.minecraft.server.level.ServerPlayer server) {
            return buttons.pressButton(server, id);
        }
        return super.clickMenuButton(player, id);
    }

    /** Avancement du cycle, entre 0 et 1. Zero quand la machine ne tourne pas. */
    public float progress() {
        int duration = data.get(DATA_DURATION);
        if (duration <= 0) {
            return 0.0f;
        }
        return Math.clamp(data.get(DATA_WORK) / (float) duration, 0.0f, 1.0f);
    }

    /** Fruits deja broyes dans le bac en cours. */
    /** La seconde fleche (le grinder en a deux). */
    public float progress2() {
        int duration = data.get(DATA_DURATION2);
        if (duration <= 0) {
            return 0.0f;
        }
        return Math.clamp(data.get(DATA_WORK2) / (float) duration, 0.0f, 1.0f);
    }

    public int tank() {
        return data.get(DATA_TANK);
    }

    /** Duree d'un cycle, en ticks ; 0 quand la machine n'a rien a faire. */
    public int duration() {
        return data.get(DATA_DURATION);
    }

    public int work() {
        return data.get(DATA_WORK);
    }

    public int extra(int index) {
        return data.get(DATA_EXTRA + index);
    }

    public int selected() {
        return data.get(DATA_SELECTED);
    }

    /** Fruits qu'il faut pour un lot, ou zero si la machine n'accumule rien. */
    public int tankGoal() {
        return data.get(DATA_TANK_GOAL);
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
            int moved = original.getCount() - stack.getCount();
            if (moved > 0 && slot instanceof MachineSlot machineSlot && machineSlot.crafted) {
                original.copyWithCount(moved).onCraftedBy(player, moved);
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
