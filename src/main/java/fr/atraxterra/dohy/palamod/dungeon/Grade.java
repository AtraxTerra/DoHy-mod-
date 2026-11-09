package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;

/**
 * La note de fin de palier (wiki : « de D a S », selon « les degats, la
 * survie et l'implication dans les combats », et « une meilleure note
 * debloque un coffre plus interessant »). Seules D et S sont nommees ; C, B
 * et A sont supposees.
 *
 * <p>Le calcul est de DoHy :
 * {@code 0,35 degats + 0,45 survie + 0,20 implication}, chaque part de 0 a 1.
 * <ul>
 *   <li>degats : sa part des degats infliges aux monstres, rapportee a une
 *       part egale (seul, 1) ;</li>
 *   <li>survie : {@code 1 - vie perdue / (40 x salles de combat)} — deux
 *       barres de vie perdues par combat font 0 ;</li>
 *   <li>implication : sa part des monstres tues, rapportee a une part egale
 *       (seul, 1 s'il en a tue un, sinon 0,5).</li>
 * </ul>
 */
public enum Grade {

    D(0.0d, 0, 0.0f, ChatFormatting.GRAY),
    C(0.40d, 1, 0.10f, ChatFormatting.GREEN),
    B(0.60d, 2, 0.20f, ChatFormatting.AQUA),
    A(0.75d, 3, 0.35f, ChatFormatting.LIGHT_PURPLE),
    S(0.90d, 4, 0.50f, ChatFormatting.GOLD);

    private final double threshold;
    private final int extraRolls;
    private final float essenceChance;
    private final ChatFormatting color;

    Grade(double threshold, int extraRolls, float essenceChance, ChatFormatting color) {
        this.threshold = threshold;
        this.extraRolls = extraRolls;
        this.essenceChance = essenceChance;
        this.color = color;
    }

    /** Les tirages du coffre de la note. */
    public int extraRolls() {
        return extraRolls;
    }

    public float essenceChance() {
        return essenceChance;
    }

    public ChatFormatting color() {
        return color;
    }

    public static Grade of(double score) {
        Grade grade = D;
        for (Grade candidate : values()) {
            if (score >= candidate.threshold) {
                grade = candidate;
            }
        }
        return grade;
    }
}
