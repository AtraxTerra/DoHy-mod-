package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.world.entity.LivingEntity;

/**
 * Le point d'appui des familiers sur les objets de ce lot. Les familiers
 * (cage, apprivoisement, competences) arrivent au lot suivant ; leurs
 * competences liront et modifieront ces valeurs. Sans familier, rien ne change.
 */
public final class PetHooks {

    private PetHooks() {
    }

    /** « Recul puissant » : la part de poussee en plus de l'infernal knocker (0 sans familier). */
    public static double knockbackBonus(LivingEntity attacker) {
        return 0.0d;
    }
}
