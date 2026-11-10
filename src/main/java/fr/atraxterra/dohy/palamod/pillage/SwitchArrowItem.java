package fr.atraxterra.dohy.palamod.pillage;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * La fleche de Switch ({@code PotionSwitchArrow}) : tiree a l'arc en paladium,
 * elle echange de place le tireur et la creature touchee — de quoi tirer un
 * gardien hors de son mur, ou passer de l'autre cote. D'un autre arc, c'est une
 * fleche ordinaire.
 *
 * <p>Une fleche vanilla marquee ; l'echange se fait a l'impact
 * ({@code PillageEvents.onImpact}). Ramassee, elle redevient fleche de Switch.
 */
public class SwitchArrowItem extends ArrowItem {

    public static final String TAG = "palamod_switch";

    public SwitchArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
        Arrow arrow = new Arrow(level, shooter, ammo.copyWithCount(1), weapon);
        arrow.addTag(TAG);
        return arrow;
    }
}
