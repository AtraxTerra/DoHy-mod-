package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Les effets de PalaMod portes par ce lot ({@code PRegister_Potions}) : les
 * trois impregnations (wither, feu, poison) qui passent a chaque coup porte,
 * et le givre des fleches glacantes. Ils ne font rien seuls : c'est
 * {@link UnlockEvents} qui les lit.
 */
public class UnlockEffect extends MobEffect {

    public UnlockEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
