package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.faction.event.EventRun;
import net.minecraft.world.item.DyeColor;

/**
 * Le caractere des douze nations rivales : fixe, lie a leur nom.
 *
 * <p>Chaque trait va de 0 a 1 et pese sur une decision de l'IA strategique
 * ({@link WarStrategy}) :
 *
 * <ul>
 *   <li><b>agressivite</b> : l'envie de se battre pour elle-meme, l'elan d'un
 *       assaut (moral de depart), la part de l'armee engagee ;</li>
 *   <li><b>avidite</b> : l'attrait de ta banque, le tribut exige, ce qu'un
 *       assaut emporte ;</li>
 *   <li><b>prudence</b> : le poids de tes defenses et des pertes attendues ;</li>
 *   <li><b>rancune</b> : le poids des griefs, et le temps qu'ils mettent a
 *       s'effacer ;</li>
 *   <li><b>honneur</b> : menacer avant de frapper, accepter un tribut, payer
 *       des reparations quand elle perd ;</li>
 *   <li><b>ruse</b> : l'art du siege — elle voit mieux tes pieges, garde des
 *       charges d'endium pour l'obsidienne, apprend de ses morts.</li>
 * </ul>
 *
 * <p>La doctrine fixe la composition de l'armee ; le tresor et l'armee de
 * depart, sa taille avant toute guerre.
 */
public record NationTraits(String name, DyeColor color, double aggression, double greed, double caution,
                           double grudge, double honor, double cunning, Doctrine doctrine, long treasury, int army,
                           String voice) {

    /** Ce qu'une nation aligne : fantassins, archers, sapeurs, ingenieurs, medecins (en part de l'armee engagee). */
    public enum Doctrine {
        EQUILIBREE("equilibree", 0.40d, 0.25d, 0.12d, 0.13d, 0.10d),
        SIEGE("de siege", 0.30d, 0.15d, 0.25d, 0.20d, 0.10d),
        ARCHERS("d'archers", 0.30d, 0.45d, 0.08d, 0.10d, 0.07d),
        CHOC("de choc", 0.58d, 0.15d, 0.10d, 0.10d, 0.07d);

        private final String label;
        final double infantry;
        final double archers;
        final double sappers;
        final double engineers;
        final double medics;

        Doctrine(String label, double infantry, double archers, double sappers, double engineers, double medics) {
            this.label = label;
            this.infantry = infantry;
            this.archers = archers;
            this.sappers = sappers;
            this.engineers = engineers;
            this.medics = medics;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Dans l'ordre de {@link EventRun#rivalPool()}. La « voix » colore leurs
     * messages : brutale, courtoise, mielleuse, froide.
     */
    private static final NationTraits[] TABLE = {
            entry(0, 0.45d, 0.85d, 0.50d, 0.55d, 0.35d, 0.80d, Doctrine.EQUILIBREE, 40_000L, 40, "mielleuse"),
            entry(1, 0.80d, 0.55d, 0.30d, 0.60d, 0.65d, 0.35d, Doctrine.CHOC, 55_000L, 60, "brutale"),
            entry(2, 0.50d, 0.70d, 0.55d, 0.70d, 0.15d, 0.95d, Doctrine.SIEGE, 45_000L, 35, "froide"),
            entry(3, 0.70d, 0.45d, 0.40d, 0.75d, 0.70d, 0.55d, Doctrine.CHOC, 35_000L, 50, "brutale"),
            entry(4, 0.65d, 0.35d, 0.45d, 0.40d, 0.90d, 0.45d, Doctrine.EQUILIBREE, 50_000L, 45, "courtoise"),
            entry(5, 0.60d, 0.80d, 0.35d, 0.50d, 0.40d, 0.50d, Doctrine.SIEGE, 65_000L, 50, "mielleuse"),
            entry(6, 0.30d, 0.50d, 0.75d, 0.30d, 0.85d, 0.60d, Doctrine.ARCHERS, 70_000L, 40, "courtoise"),
            entry(7, 0.35d, 0.60d, 0.70d, 0.45d, 0.60d, 0.85d, Doctrine.ARCHERS, 60_000L, 35, "froide"),
            entry(8, 0.75d, 0.50d, 0.25d, 0.55d, 0.55d, 0.25d, Doctrine.SIEGE, 80_000L, 55, "brutale"),
            entry(9, 0.85d, 0.40d, 0.30d, 0.50d, 0.60d, 0.40d, Doctrine.CHOC, 40_000L, 55, "brutale"),
            entry(10, 0.55d, 0.65d, 0.50d, 0.90d, 0.25d, 0.70d, Doctrine.EQUILIBREE, 45_000L, 40, "froide"),
            entry(11, 0.80d, 0.60d, 0.35d, 0.80d, 0.30d, 0.50d, Doctrine.CHOC, 50_000L, 50, "brutale"),
    };

    private static NationTraits entry(int index, double aggression, double greed, double caution, double grudge,
                                   double honor, double cunning, Doctrine doctrine, long treasury, int army,
                                   String voice) {
        EventRun.Rival rival = EventRun.rivalPool().get(index);
        return new NationTraits(rival.name(), rival.color(), aggression, greed, caution, grudge, honor, cunning,
                doctrine, treasury, army, voice);
    }

    public static int count() {
        return TABLE.length;
    }

    public static NationTraits of(int nation) {
        return TABLE[Math.floorMod(nation, TABLE.length)];
    }

    /** Le nom en un mot, pour les commandes : « corbeaux », « ombre-pourpre ». */
    public String slug() {
        String base = plural() ? name.substring(4) : name;
        return base.toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
    }

    /** La nation dont le nom (ou le rang, de 1 a 12) commence ainsi ; -1 sinon. */
    public static int find(String text) {
        String wanted = text.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
        try {
            int index = Integer.parseInt(wanted) - 1;
            return index >= 0 && index < TABLE.length ? index : -1;
        } catch (NumberFormatException notNumber) {
            // un nom
        }
        for (int i = 0; i < TABLE.length; i++) {
            if (TABLE[i].slug().equals(wanted)) {
                return i;
            }
        }
        for (int i = 0; i < TABLE.length; i++) {
            if (TABLE[i].slug().startsWith(wanted) || TABLE[i].name().toLowerCase(java.util.Locale.ROOT)
                    .contains(wanted.replace('-', ' '))) {
                return i;
            }
        }
        return -1;
    }

    /** L'armee la plus grande qu'elle entretienne : une fois et demie sa taille de depart. */
    public int maxArmy() {
        return army + army / 2;
    }

    /** « Les Corbeaux » se conjuguent au pluriel. */
    public boolean plural() {
        return name.startsWith("Les ");
    }

    /** Le verbe accorde a la nation : « Nordheim declare », « Les Corbeaux declarent ». */
    public String verb(String singular, String pluralForm) {
        return name + " " + (plural() ? pluralForm : singular);
    }

    /** « de Nordheim », « des Corbeaux », « d'Arcanum ». */
    public String de() {
        return fr.atraxterra.dohy.earth.EarthNations.de(name);
    }

    /** Ce qu'on dit d'elle, en clair, quand on la connait un peu. */
    public String reputation() {
        StringBuilder out = new StringBuilder();
        if (greed >= 0.7d) {
            out.append("avide");
        } else if (aggression >= 0.75d) {
            out.append("belliqueuse");
        } else if (caution >= 0.65d) {
            out.append("prudente");
        } else {
            out.append("ambitieuse");
        }
        if (grudge >= 0.75d) {
            out.append(", rancuniere");
        } else if (honor >= 0.8d) {
            out.append(", d'honneur");
        } else if (honor <= 0.3d) {
            out.append(", sans parole");
        }
        if (cunning >= 0.8d) {
            out.append(", rusee");
        }
        return out.toString();
    }
}
