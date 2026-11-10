package fr.atraxterra.dohy.palamod.wither;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * La fleche de mi-vie ({@code MID_LIFE}) : tiree a l'arc en paladium, elle
 * ote a un wither la moitie de sa vie ({@code WitherEvents.onImpact}). Ni le
 * predateur ni le supreme n'y sont sensibles.
 */
public class MidLifeArrowItem extends ArrowItem {

    public static final String TAG = "palamod_mid_life";

    public MidLifeArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
        Arrow arrow = new Arrow(level, shooter, ammo.copyWithCount(1), weapon);
        arrow.addTag(TAG);
        return arrow;
    }
}
