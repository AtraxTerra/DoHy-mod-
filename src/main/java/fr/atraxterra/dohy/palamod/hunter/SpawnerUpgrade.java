package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.world.item.ItemStack;

/**
 * Les quatre ameliorations du controleur ({@code SpawnerUpgradeRegistry}).
 *
 * <ul>
 *   <li><b>vitesse</b> : un dixieme de temps en moins entre deux vagues ;</li>
 *   <li><b>butin</b> : dix chances sur cent, par amelioration, qu'une creature
 *       rende son butin une seconde fois (jamais un slime) ;</li>
 *   <li><b>slime</b> : 0,11 chance sur cent, par amelioration et par creature
 *       de la vague, d'un slime en plus ;</li>
 *   <li><b>capacite</b> : ouvre les deux cases verrouillees et autorise deux
 *       ameliorations identiques. Une seule par controleur.</li>
 * </ul>
 * Les niveaux de metier (Mineur 85, Alchimiste 90 et 75, Chasseur 75) sont
 * dans {@code data/jobs.json}, sur l'objet.
 */
public enum SpawnerUpgrade {

    SPEED, MORE, LOOTING, SLIME;

    public static SpawnerUpgrade of(ItemStack stack) {
        return stack.getItem() instanceof SpawnerUpgradeItem item ? item.upgrade() : null;
    }
}
