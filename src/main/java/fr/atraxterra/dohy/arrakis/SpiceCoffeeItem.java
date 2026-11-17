package fr.atraxterra.dohy.arrakis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Le cafe d'epice des sietchs : il se boit, rend une gorgee d'eau et aiguise les sens (vision
 * nocturne, celerite, rapidite ; les effets viennent de sa consommation).
 */
public class SpiceCoffeeItem extends Item {

    public SpiceCoffeeItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            Hydration.drink(player, Hydration.SIP);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
