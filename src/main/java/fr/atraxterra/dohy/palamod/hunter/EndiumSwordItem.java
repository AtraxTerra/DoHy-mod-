package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Epee en endium ({@code BaseItemSword} au materiau endium).
 *
 * <p>Releve au bytecode : l'usure est plafonnee, l'epee ne casse jamais ; et a
 * bout d'usure, un clic gauche sur une entite est annule tout entier
 * ({@code onLeftClickEntity} rend vrai) — ni degats, ni recul, ni usure. Parer et
 * casser des blocs marchent toujours. La reparer la relance. C'est le seul outil
 * en endium qui cesse vraiment de servir : la pioche et la hache, a bout, ne
 * perdent que leur pouvoir.
 *
 * <p>Comme les autres outils en endium, le plafond est un point sous le maximum :
 * en 26.2 une pile disparait quand l'usure l'<i>atteint</i>. Le refus de frapper
 * est dans {@link HunterEvents}.
 */
public class EndiumSwordItem extends Item {

    public EndiumSwordItem(Properties properties) {
        super(properties);
    }

    /** A bout : la barre montre 1, et l'epee ne frappe plus. */
    public static boolean isWornOut(ItemStack stack) {
        return stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        super.setDamage(stack, Math.min(damage, stack.getMaxDamage() - 1));
    }
}
