package fr.atraxterra.dohy.palamod.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Socle des machines : un inventaire, une sauvegarde, et une interface.
 *
 * <p><b>L'interface etait un coffre vanilla, elle ne l'est plus.</b> Le coffre
 * avait l'avantage de ne demander aucune ligne de code client, et il a tenu le
 * temps d'un chapitre. Sa limite etait structurelle : un coffre ne synchronise
 * que des objets, donc aucune jauge, aucune cadence. Une machine dont on ne
 * voit pas si elle tourne force le joueur a deviner — et le cobblebreaker, dont
 * la cadence est justement ce que les paliers ameliorent, ne montrait pas son
 * amelioration. D'ou {@link MachineMenu} et son ecran.
 *
 * <p><b>Les deux entiers synchronises.</b> {@link ContainerData} est le canal
 * que le jeu ouvre entre un menu serveur et son copie client. Le socle en
 * expose deux : l'avancement du cycle et sa duree totale. La duree y figure
 * parce que celle du cobblebreaker depend de son palier, que le client ne peut
 * pas deduire. L'ecran ne fait que le rapport des deux, ce qui evite d'avoir a
 * decrire les jauges ici.
 *
 * <p>Le {@code set} de cet objet ne fait rien, et c'est correct : cote serveur
 * le jeu ne lit que {@code get}, et le client travaille sur une
 * {@code SimpleContainerData} a lui, fabriquee par le constructeur client du
 * menu. Les deux instances ne se croisent jamais.
 *
 * <p>La sauvegarde passe par {@code ValueInput} / {@code ValueOutput}, qui ont
 * remplace {@code CompoundTag} en 26.2.
 */
public abstract class MachineBlockEntity extends BlockEntity
        implements Container, MenuProvider, ServerTicking {

    protected final NonNullList<ItemStack> items;
    private final Component title;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case MachineMenu.DATA_WORK -> work();
                case MachineMenu.DATA_DURATION -> cycleDuration();
                case MachineMenu.DATA_TANK -> tankFill();
                case MachineMenu.DATA_TANK_GOAL -> tankGoal();
                case MachineMenu.DATA_WORK2 -> work2();
                case MachineMenu.DATA_DURATION2 -> cycleDuration2();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Volontairement vide : voir la note de la classe.
        }

        @Override
        public int getCount() {
            return MachineMenu.DATA_COUNT;
        }
    };

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                 int size, Component title) {
        super(type, pos, state);
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        this.title = title;
    }

    // -- Sauvegarde ----------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    // -- Inventaire ----------------------------------------------------------

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize());
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }

    /**
     * Qui peut garder l'interface ouverte.
     *
     * <p>La distance est celle des coffres vanilla. Sans ce controle, une
     * interface resterait ouverte apres que le joueur se soit eloigne ou que la
     * machine ait ete cassee, et les clics continueraient d'operer sur un
     * inventaire fantome.
     */
    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /** Tout l'inventaire, pour le laisser tomber quand la machine est cassee. */
    public NonNullList<ItemStack> contents() {
        return items;
    }

    // -- Interface -----------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return title;
    }

    /**
     * Ouvre le menu de cette machine.
     *
     * <p>La machine fournit son type, sa disposition et son inventaire ; tout
     * le reste est dans {@link MachineMenu}, qui est le meme pour les deux.
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MachineMenu(menuType(), containerId, playerInventory, this, data, layout());
    }

    /** Le type enregistre, celui auquel le client associe un ecran. */
    protected abstract MenuType<MachineMenu> menuType();

    /** Ou sont les cases et les jauges. Partage avec le generateur d'assets. */
    protected abstract MachineLayout layout();

    /** Ticks deja faits dans le cycle en cours. */
    public abstract int work();

    /**
     * Ticks que dure un cycle complet.
     *
     * <p>Zero quand la machine ne peut pas tourner : l'ecran y lit « aucune
     * progression » sans avoir a connaitre les conditions de chaque machine.
     */
    public abstract int cycleDuration();

    /**
     * Elements deja accumules, pour une machine qui accumule.
     *
     * <p>Zero par defaut : le cobblebreaker rend son lot a chaque cycle et n'a
     * rien a compter. Seul le crusher redefinit ces deux-la.
     */
    public int tankFill() {
        return 0;
    }

    /** Elements qu'il faut pour un lot, ou zero si la machine n'accumule rien. */
    public int tankGoal() {
        return 0;
    }

    /** Le second cycle, pour une machine a deux fleches. */
    public int work2() {
        return 0;
    }

    public int cycleDuration2() {
        return 0;
    }

    /**
     * Le joueur peut-il ouvrir l'interface ? Une machine a multibloc (le
     * grinder) refuse tant que sa structure n'est pas complete.
     */
    public boolean canOpen(ServerPlayer player) {
        return true;
    }

    @Override
    public abstract void serverTick();
}
