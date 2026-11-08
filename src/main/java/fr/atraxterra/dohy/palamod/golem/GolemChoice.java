package fr.atraxterra.dohy.palamod.golem;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Le menu des choix ({@code Choices}) : une option par amelioration
 * configurable, numerotee a partir de 1.
 *
 * <p>Les libelles disent ce que fait le code, pas les descriptions de
 * l'original, fausses par endroits (dernier souffle, un niveau d'effet trop bas).
 *
 * <p>Non portes : ENGINE (Mecagolem, sans effet) et IRON_MAKER (aucun effet).
 *
 * <p>Valeurs par defaut : celles que GuardianGolem retrouvait apres la
 * premiere sauvegarde — 2 (aucune preference) pour la resistance avancee, 1
 * pour les autres. Avant la premiere sauvegarde, l'original repondait 0 ; un
 * golem changeait donc de comportement au redemarrage.
 */
public enum GolemChoice implements StringRepresentable {
    WITHER("wither", 2, GolemUpgrade.WITHER_HUNTER, 1, "Withers",
            "Chasser les withers (et frapper les monstres x1,3)", "Se faire ignorer des withers"),
    ADVANCED_RESISTANCE("advanced_resistance", 3, GolemUpgrade.ADVANCED_RESISTANCE, 2, "Resistance avancee",
            "Coups x0,8, projectiles x1,33", "Aucune preference", "Projectiles x0,7, coups x1,2"),
    SPECIALIZED_RESISTANCE("specialized_resistance", 4, GolemUpgrade.SPECIALIZED_RESISTANCE, 1,
            "Resistance specialisee", "Explosions x0,3", "Feu x0,3", "Magie x0,4", "Noyade x0,3"),
    LAST_BREATH("last_breath", 4, GolemUpgrade.LAST_BREATH, 1, "Dernier souffle",
            "Force IV, 5 s", "Regeneration III, 10 s", "Faiblesse III et cecite II a l'attaquant, 10 s",
            "Wither II a l'attaquant, 10 s"),
    MAGICIAN("magician", 4, GolemUpgrade.MAGICIAN, 1, "Magicien",
            "Repousser la cible", "Lenteur III, 3 s", "Nausee II, 5 s", "Enflammer, 5 s");

    public static final Codec<GolemChoice> CODEC = StringRepresentable.fromEnum(GolemChoice::values);

    private final String key;
    private final int options;
    private final GolemUpgrade gate;
    private final int fallback;
    private final String label;
    private final String[] optionLabels;

    GolemChoice(String key, int options, GolemUpgrade gate, int fallback, String label, String... optionLabels) {
        this.key = key;
        this.options = options;
        this.gate = gate;
        this.fallback = fallback;
        this.label = label;
        this.optionLabels = optionLabels;
    }

    public String label() {
        return label;
    }

    /** Le libelle de l'option, de 1 a {@link #options()}. */
    public String option(int value) {
        return optionLabels[Math.clamp(value - 1, 0, optionLabels.length - 1)];
    }

    public String key() {
        return key;
    }

    public int options() {
        return options;
    }

    public GolemUpgrade gate() {
        return gate;
    }

    public int fallback() {
        return fallback;
    }

    @Override
    public String getSerializedName() {
        return key;
    }
}
