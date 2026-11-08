package fr.atraxterra.dohy.palamod.godpickaxe;

import fr.atraxterra.dohy.palamod.equipment.PalaToolMaterials;
import net.minecraft.world.item.ToolMaterial;

/**
 * La table de {@code ItemGodPickaxe.initLevels} et la formule d'XP.
 *
 * <p>Le niveau est <i>interne</i> (0 a 99) ; le joueur voit niveau + 1. Il n'est
 * jamais stocke : on le recalcule depuis l'XP, comme dans PalaMod.
 *
 * <p>Seuil : {@code (int) (80 * n^2.3 / 1.5)}. Un bloc rapporte 1 XP, et le
 * niveau 100 demande plus de 2 074 720 XP — un peu plus de deux millions de
 * blocs. A ce niveau l'XP ne compte plus.
 *
 * <p><b>Ecart</b> : PalaMod testait {@code xpFor(i) < xp < xpFor(i + 1)}, deux
 * inegalites strictes. Pile sur un seuil — ce qui arrive a chaque palier,
 * l'XP montant de 1 en 1 — aucun niveau ne repondait et la pioche retombait au
 * niveau 1 le temps d'un bloc, enchantements effaces. Ici le niveau est le plus
 * haut dont le seuil est depasse : rien ne change ailleurs.
 */
public final class GodPickaxeLevels {

    /** Niveau interne le plus haut : le niveau 100 affiche. */
    public static final int MAX = 99;

    /**
     * Un palier : le materiau (vitesse et recolte), l'Efficacite, la Fortune.
     *
     * <p>Les materiaux vanilla gardent les valeurs de 1.7.10 (bois 2, pierre 4,
     * fer 6, diamant 8, or 12) ; ceux de PalaMod sont ceux de
     * {@link PalaToolMaterials}. L'or recolte comme le bois : aux niveaux 21 a
     * 25, la pioche est plus rapide mais ne prend plus le fer ni le diamant.
     * C'est l'original, et le wiki le dit.
     */
    public record Tier(ToolMaterial material, int efficiency, int fortune) {
    }

    private static final Tier[] TIERS = new Tier[MAX + 1];

    static {
        fill(0, 5, new Tier(ToolMaterial.WOOD, 0, 0));
        fill(5, 10, new Tier(ToolMaterial.STONE, 0, 0));
        fill(10, 15, new Tier(ToolMaterial.IRON, 0, 0));
        fill(15, 20, new Tier(ToolMaterial.DIAMOND, 0, 0));
        fill(20, 25, new Tier(ToolMaterial.GOLD, 0, 0));
        fill(25, 30, new Tier(PalaToolMaterials.AMETHYST, 0, 0));
        fill(30, 35, new Tier(PalaToolMaterials.TITANE, 0, 0));
        fill(35, 40, new Tier(PalaToolMaterials.PALADIUM, 0, 0));
        for (int efficiency = 1; efficiency <= 5; efficiency++) {
            int from = 35 + 5 * efficiency;
            fill(from, from + 5, new Tier(PalaToolMaterials.PALADIUM, efficiency, 0));
        }
        for (int fortune = 1; fortune <= 3; fortune++) {
            int from = 60 + 5 * fortune;
            fill(from, from + 5, new Tier(PalaToolMaterials.PALADIUM, 5, fortune));
        }
        fill(80, MAX + 1, new Tier(PalaToolMaterials.GREEN_PALADIUM, 5, 3));
    }

    private GodPickaxeLevels() {
    }

    private static void fill(int from, int to, Tier tier) {
        for (int level = from; level < to; level++) {
            TIERS[level] = tier;
        }
    }

    /** {@code getXpForLevel}. */
    public static int xpFor(int level) {
        return (int) (80.0d * Math.pow(level, 2.3d) / 1.5d);
    }

    /** Le niveau interne le plus haut dont le seuil est depasse. */
    public static int levelFor(double xp) {
        int level = 0;
        while (level < MAX && xp > xpFor(level + 1)) {
            level++;
        }
        return level;
    }

    public static Tier tier(int level) {
        return TIERS[Math.clamp(level, 0, MAX)];
    }

    /** {@code (L / 5) % 20} : une texture tous les cinq niveaux. */
    public static int texture(int level) {
        return Math.clamp(level, 0, MAX) / 5;
    }

    /** Ce qui manque avant le niveau suivant, 0 au niveau maximal. */
    public static double missing(double xp) {
        int level = levelFor(xp);
        return level >= MAX ? 0.0d : Math.max(0.0d, xpFor(level + 1) - xp);
    }

    /** Avancement dans le niveau courant, de 0 a 1. */
    public static float progress(double xp) {
        int level = levelFor(xp);
        if (level >= MAX) {
            return 1.0f;
        }
        double from = level == 0 ? 0.0d : xpFor(level);
        double to = xpFor(level + 1);
        return (float) Math.clamp((xp - from) / (to - from), 0.0d, 1.0d);
    }
}
