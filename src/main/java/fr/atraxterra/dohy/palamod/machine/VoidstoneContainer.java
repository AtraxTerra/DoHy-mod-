package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * La case de la voidstone : tout ce qu'on y pose disparait.
 *
 * <p>Releve sur {@code VoidStoneInventory} : {@code getStackInSlot} rend
 * toujours {@code null} et {@code setInventorySlotContents} ne garde rien. Ce
 * n'est donc pas un inventaire, c'est un trou — une poubelle de poche, pour les
 * mineurs qui remplissent leur sac de pierre.
 *
 * <p><b>Ce qu'elle refuse</b>, et c'est ce qui la rend sure a utiliser :
 * {@code isItemValidForSlot} rejette les outils ({@code ItemTool}) et la
 * voidstone elle-meme. Un shift-clic de travers sur sa pioche ne la detruit
 * pas. Le test 26.2 porte sur le composant {@code TOOL}, que portent aussi les
 * epees et les houes : un peu plus large que l'original, dans le sens de la
 * prudence.
 */
public final class VoidstoneContainer implements Container {

    /** Une case, comme l'original. Compare a la disposition par check_assets.py. */
    public static final int SLOT_COUNT = 1;

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    /** Le coeur de la voidstone : ce qui arrive ici n'est garde nulle part. */
    @Override
    public void setItem(int slot, ItemStack stack) {
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return !stack.has(DataComponents.TOOL) && !stack.is(PalaItems.VOIDSTONE.get());
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
