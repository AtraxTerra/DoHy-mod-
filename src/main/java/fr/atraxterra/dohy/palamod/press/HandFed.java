package fr.atraxterra.dohy.palamod.press;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Une machine qu'on garnit d'un clic droit, objet en main : la presse et la
 * table d'ecriture de PalaMod n'avaient pas d'interface, on y deposait
 * l'encre, la plaque ou les livres en cliquant dessus. Le geste reste ; la main
 * vide ouvre l'interface.
 */
public interface HandFed {

    /** Range ce que tient le joueur dans la case qui l'accepte ; rend vrai si quelque chose y est entre. */
    boolean feed(ServerPlayer player, ItemStack held);

    /**
     * Verse {@code held} dans {@code current} sans depasser {@code limit} ; rend la pile
     * de la case apres coup, ou {@code null} si rien n'est entre. Un joueur en creatif
     * garde ce qu'il tient, comme quand il pose un bloc.
     */
    static ItemStack merge(ServerPlayer player, ItemStack current, ItemStack held, int limit) {
        int room = current.isEmpty() ? limit : limit - current.getCount();
        if (room <= 0 || (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, held))) {
            return null;
        }
        int moved = Math.min(room, held.getCount());
        ItemStack result = current.isEmpty() ? held.copyWithCount(moved) : current;
        if (!current.isEmpty()) {
            result.grow(moved);
        }
        if (!player.hasInfiniteMaterials()) {
            held.shrink(moved);
        }
        return result;
    }
}
