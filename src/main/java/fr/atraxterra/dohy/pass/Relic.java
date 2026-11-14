package fr.atraxterra.dohy.pass;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * Les douze reliques du pass, une par mois : l'objet exclusif du dernier
 * niveau. Tenue en main secondaire, une relique donne son effet (niveau I) ;
 * une seule a la fois, donc.
 *
 * <p>Les effets sont dans le code et non dans le fichier du pass : la bulle
 * d'aide d'une relique se lit chez le client, qui n'a pas ce fichier sur un
 * serveur dedie.
 */
public enum Relic {
    JANVIER(1, "relic_janvier", "Cristal de givre", MobEffects.RESISTANCE, "Resistance"),
    FEVRIER(2, "relic_fevrier", "Coeur de cristal", MobEffects.HEALTH_BOOST, "Bonus de vie"),
    MARS(3, "relic_mars", "Trefle porte-bonheur", MobEffects.LUCK, "Chance"),
    AVRIL(4, "relic_avril", "Oeuf d'or", MobEffects.JUMP_BOOST, "Saut ameliore"),
    MAI(5, "relic_mai", "Brin de muguet", MobEffects.HASTE, "Celerite"),
    JUIN(6, "relic_juin", "Lyre de l'ete", MobEffects.SPEED, "Vitesse"),
    JUILLET(7, "relic_juillet", "Coquillage des vacances", MobEffects.WATER_BREATHING, "Apnee"),
    AOUT(8, "relic_aout", "Soleil de plomb", MobEffects.FIRE_RESISTANCE, "Resistance au feu"),
    SEPTEMBRE(9, "relic_septembre", "Lanterne de la rentree", MobEffects.NIGHT_VISION, "Vision nocturne"),
    OCTOBRE(10, "relic_octobre", "Citrouille maudite", MobEffects.STRENGTH, "Force"),
    NOVEMBRE(11, "relic_novembre", "Feuille d'automne", MobEffects.SLOW_FALLING, "Chute lente"),
    DECEMBRE(12, "relic_decembre", "Etoile de Noel", MobEffects.REGENERATION, "Regeneration");

    private final int month;
    private final String path;
    private final String title;
    private final Holder<MobEffect> effect;
    private final String effectName;

    Relic(int month, String path, String title, Holder<MobEffect> effect, String effectName) {
        this.month = month;
        this.path = path;
        this.title = title;
        this.effect = effect;
        this.effectName = effectName;
    }

    public int month() {
        return month;
    }

    /** L'identifiant de l'objet, dans {@code dohy}. */
    public String path() {
        return path;
    }

    /** Son nom, sans accents. */
    public String title() {
        return title;
    }

    public Holder<MobEffect> effect() {
        return effect;
    }

    public String effectName() {
        return effectName;
    }

    /** La relique d'un mois, de 1 a 12. */
    public static Relic of(int month) {
        return values()[Math.floorMod(month - 1, 12)];
    }
}
