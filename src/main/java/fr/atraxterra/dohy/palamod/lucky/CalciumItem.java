package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * La brique de lait ({@code ItemCalcium}, un seau de lait a dix doses) : chaque gorgee
 * retire tous les effets et use une dose ; la dixieme vide la brique. Taper une vache
 * la remplit ({@link LuckyItems#onAttack}).
 */
public class CalciumItem extends Item {

    /** {@code setMaxDamage(10)}, et la brique part apres la dixieme gorgee ({@code damage > 9}). */
    public static final int USES = 10;

    public CalciumItem(Properties properties) {
        super(properties);
    }

    /** Sans appeler le parent : le composant de consommation ferait disparaitre la brique. */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            entity.removeAllEffects();
            stack.hurtAndBreak(1, entity, entity.getUsedItemHand());
        }
        return stack;
    }
}
