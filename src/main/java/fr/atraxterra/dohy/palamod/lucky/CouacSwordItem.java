package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * L'epee Couac ({@code ItemCouacSword}) : une epee en paladium qui caquette a chaque coup.
 * <b>Ecart</b> : le cri est celui de la poule du jeu, au lieu du son de PalaMod.
 */
public class CouacSwordItem extends Item {

    public CouacSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel level) {
            LuckyWorld.sound(level, target.getX(), target.getY(), target.getZ(), "entity.chicken.hurt",
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        super.hurtEnemy(stack, target, attacker);
    }
}
