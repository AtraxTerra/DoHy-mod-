package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Durete : de l'XP de Mineur sur chaque obsidienne cassee.
 *
 * <p>{@code PMiner.HARDNESS} est un {@code CustomPotion(41, true, CYAN)}. Le
 * {@code true} le declarait nefaste, ce qui en 1.7.10 ne servait qu'a teinter son
 * nom en rouge ; en 26.2 la categorie decide aussi de ce que le lait et les
 * balises en font. L'effet est un bonus : il est declare benefique.
 *
 * <p>Il n'a aucun comportement propre : {@link MinerXpEvents} le lit.
 */
public class HardnessEffect extends MobEffect {

    /** {@code java.awt.Color.CYAN}. */
    private static final int COLOR = 0x00FFFF;

    public HardnessEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
