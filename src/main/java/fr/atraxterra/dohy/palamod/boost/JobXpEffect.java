package fr.atraxterra.dohy.palamod.boost;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Double ou Triple XP de metier : un effet sans comportement propre, que
 * {@code JobApi.grant} lit ({@link PalaXpPotions#multiplier}).
 */
public class JobXpEffect extends MobEffect {

    public JobXpEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }
}
