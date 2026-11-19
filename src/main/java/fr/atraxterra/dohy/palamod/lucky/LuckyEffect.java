package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Un effet d'evenement ({@code CustomPotion}) : il ne fait rien par lui-meme.
 * Le serveur ({@code LuckyWorldEvents}) et le client ({@code LuckyClientEvents})
 * regardent s'il est actif. Le lait l'enleve, comme dans PalaMod.
 */
public class LuckyEffect extends MobEffect {

    public LuckyEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
