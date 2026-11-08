package fr.atraxterra.dohy.palamod.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
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
 * <p><b>L'interface est un coffre vanilla, et c'est un choix.</b> Une machine
 * demande normalement un {@code AbstractContainerMenu} et un {@code Screen}
 * cote client — donc du code de rendu, que ce mod evite depuis le depart (voir
 * {@code DESIGN.md} et le choix de {@code SkillBossBar}). En adossant
 * l'inventaire de la machine a un {@link ChestMenu}, on obtient une interface
 * fonctionnelle, deplacable au shift-clic, compatible avec les entonnoirs, et
 * <b>sans une ligne de code client</b>.
 *
 * <p>Ce qu'on y perd : pas de barre de progression ni de jauge dessinee. Les
 * machines disent donc leur etat autrement — le cobblebreaker par son palier
 * visible sur le bloc, le crusher par le remplissage de ses bacs. Un compromis
 * payant tant qu'aucune machine n'a d'etat invisible et essentiel.
 *
 * <p>La sauvegarde passe par {@code ValueInput} / {@code ValueOutput}, qui ont
 * remplace {@code CompoundTag} en 26.2.
 */
public abstract class MachineBlockEntity extends BlockEntity implements Container, MenuProvider {

    protected final NonNullList<ItemStack> items;
    private final Component title;

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
     * Le type de coffre a ouvrir.
     *
     * <p>Une rangee jusqu'a neuf emplacements, trois au-dela. C'est la seule
     * chose que la machine ait a dire sur son interface.
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        int rows = Math.max(1, (getContainerSize() + 8) / 9);
        MenuType<ChestMenu> type = rows <= 1 ? MenuType.GENERIC_9x1 : MenuType.GENERIC_9x3;
        return new ChestMenu(type, containerId, playerInventory, this, rows <= 1 ? 1 : 3);
    }

    /** Le travail d'un tick. Appele par le ticker du serveur uniquement. */
    public abstract void serverTick();
}
