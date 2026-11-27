package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * L'infernal knocker ({@code ItemInfernalKnocker}, Chasseur 45) : une epee de
 * bois (4 de degats) qui repousse fort, de quoi pousser un wither plus vite et
 * plus loin pendant un pillage.
 *
 * <p>Releve dans le code : a chaque coup porte, la cible recoit
 * {@code addVelocity(-sin(lacet) * 3 * 0.5, 0.1, cos(lacet) * 3 * 0.5)} selon
 * le regard de l'attaquant — un recul de puissance 3, appliquee sans tenir
 * compte de la resistance au recul (c'est ce qui fait bouger un wither). Elle
 * ne s'enchante pas ({@code isBookEnchantable} : non). La competence de
 * familier « Recul puissant » renforce la poussee ({@link #power}).
 */
public class InfernalKnockerItem extends Item {

    /** La puissance du recul ({@code ldc 3.0f}). */
    public static final float KNOCKBACK = 3.0f;

    public InfernalKnockerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        double strength = KNOCKBACK * 0.5d * power(attacker);
        float yaw = attacker.getYRot() * Mth.DEG_TO_RAD;
        target.push(-Mth.sin(yaw) * strength, 0.1d, Mth.cos(yaw) * strength);
        target.hurtMarked = true;
    }

    /** Le multiplicateur de la poussee : 1, plus la competence de familier si elle est equipee. */
    static double power(LivingEntity attacker) {
        return 1.0d + PetHooks.knockbackBonus(attacker);
    }
}
