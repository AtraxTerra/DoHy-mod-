package fr.atraxterra.dohy.grade;

/**
 * Premium, l'abonnement de Paladium : trente jours, qui s'ajoutent au grade qu'on a (ou a
 * l'absence de grade). Ici, il s'achete a la Boutique avec l'argent du jeu ; racheter prolonge
 * de trente jours. Le temps est reel : il court aussi monde ferme, comme un abonnement.
 *
 * <p>Ce qu'il ajoute vient de la page « Par grade » du wiki de Paladium. Ce qui n'a de sens que
 * sur un reseau de serveurs (rejoindre un serveur sans passer par le lobby, le serveur de test,
 * un home depuis n'importe quel serveur) n'a pas d'equivalent ici.
 */
public final class Premium {

    public static final long PRICE = 15_000_000L;
    public static final int DAYS = 30;
    /** Au-dela, racheter ne prolonge plus : une annee d'avance suffit. */
    public static final int MAX_DAYS = 360;
    public static final long DAY_MILLIS = 86_400_000L;
    public static final int COLOR = 0xFF2BE0A8;

    public static final int HOMES = 5;
    public static final int RTP = 3;
    public static final int ENDER_ROWS = 1;
    public static final double JOB_XP = 0.05d;
    public static final double PASS_XP = 0.05d;
    public static final double VANILLA_XP = 0.10d;
    public static final double TAX_CUT = 0.10d;

    private Premium() {
    }
}
