package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.skill.Skill;

/**
 * Les quatre metiers de Paladium, poses sur les skills du mod.
 *
 * <p><b>La decision de conception du chapitre</b> : un metier n'est pas une
 * progression de plus, c'est un <i>nom</i> donne a un skill existant. Mineur lit
 * {@code mining}, Chasseur lit {@code combat}, Fermier lit {@code farming}, et
 * l'Alchimiste a recu son propre skill faute d'equivalent. Il n'y a donc qu'une
 * barre d'XP par activite.
 *
 * <p>L'alternative — une table d'XP de metier a cote de celle des skills —
 * aurait demande au joueur de monter deux compteurs avec le meme geste, et au
 * mod de tenir deux sauvegardes qui finissent toujours par divergere. Le cout
 * reel de la fusion est que la table d'XP de {@code PalaJobs} (20 niveaux,
 * 22 123 XP pour le niveau 2) ne s'applique pas : c'est la courbe de
 * {@code skills.json} qui fait foi, sur 100 niveaux.
 *
 * <p>Heureusement, cela tombe juste : les niveaux de deblocage releves sur le
 * wiki sont ceux du Paladium Java, donc gradues sur 100 — cobblebreaker au 25,
 * crusher au 40, totem au 55, obsidienne infusee paladium au 100. Ils
 * s'appliquent tels quels a la courbe du mod, sans remise a l'echelle.
 */
public enum Job {

    MINER("miner", "Mineur", Skill.MINING),
    HUNTER("hunter", "Chasseur", Skill.COMBAT),
    FARMER("farmer", "Fermier", Skill.FARMING),
    ALCHEMIST("alchemist", "Alchimiste", Skill.ALCHEMY);

    private final String key;
    private final String display;
    private final Skill skill;

    Job(String key, String display, Skill skill) {
        this.key = key;
        this.display = display;
        this.skill = skill;
    }

    /** Cle employee par {@code jobs.json}. */
    public String key() {
        return key;
    }

    /** Nom affiche au joueur, celui de Paladium. */
    public String display() {
        return display;
    }

    /** Le skill qui porte l'XP et le niveau de ce metier. */
    public Skill skill() {
        return skill;
    }

    public static Job byKey(String candidate) {
        if (candidate == null) {
            return null;
        }
        for (Job job : values()) {
            if (job.key.equalsIgnoreCase(candidate)) {
                return job;
            }
        }
        return null;
    }

    /** Le metier qui porte ce skill, ou {@code null} si aucun. */
    public static Job of(Skill skill) {
        for (Job job : values()) {
            if (job.skill == skill) {
                return job;
            }
        }
        return null;
    }
}
