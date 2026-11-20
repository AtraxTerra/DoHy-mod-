package fr.atraxterra.dohy.grade;

/**
 * Les grades de Paladium : Heros, Legende et Divinite.
 *
 * <p>Sur Paladium, un grade s'achete en euros sur la boutique du site ; il donne du confort,
 * jamais de la puissance (« ils ne permettent pas d'ameliorer vos performances ni vos
 * capacites », dit le wiki). Ici, il s'achete a la Boutique avec l'argent du jeu, une fois pour
 * toutes. Monter d'un grade coute la difference de prix.
 *
 * <p>Chaque grade porte ses propres valeurs, pas un supplement : Legende donne 10 homes, pas
 * 5 de plus que Heros. Les chiffres sont ceux de la page « Par grade » du wiki de Paladium
 * (version Java), sauf l'Ender Chest : le jeu en donne deja 27 cases sans grade, et chaque grade
 * y ajoute une rangee, comme sur Paladium. {@code docs/PALAMOD.md}, section 42, detaille les ecarts.
 */
public enum PlayerGrade {
    NONE("", "Joueur", 0xFFB8BCC6, 0L, 2, 1, 3, 0.0d, 0.0d, 0.0d, 0.0d, 0),
    HEROS("heros", "Heros", 0xFF3FA9F5, 10_000_000L, 5, 5, 4, 0.05d, 0.05d, 0.10d, 0.0d, 10),
    LEGENDE("legende", "Legende", 0xFFFAC400, 100_000_000L, 10, 10, 5, 0.10d, 0.10d, 0.20d, 0.10d, 10),
    DIVINITE("divinite", "Divinite", 0xFFC86BFA, 1_000_000_000L, 15, 10, 6, 0.15d, 0.15d, 0.30d, 0.20d, 10);

    private final String id;
    private final String label;
    private final int color;
    private final long price;
    private final int homes;
    private final int rtp;
    private final int enderRows;
    private final double jobXp;
    private final double passXp;
    private final double vanillaXp;
    private final double taxCut;
    private final int auctionSlots;

    PlayerGrade(String id, String label, int color, long price, int homes, int rtp, int enderRows, double jobXp,
                double passXp, double vanillaXp, double taxCut, int auctionSlots) {
        this.id = id;
        this.label = label;
        this.color = color;
        this.price = price;
        this.homes = homes;
        this.rtp = rtp;
        this.enderRows = enderRows;
        this.jobXp = jobXp;
        this.passXp = passXp;
        this.vanillaXp = vanillaXp;
        this.taxCut = taxCut;
        this.auctionSlots = auctionSlots;
    }

    /** L'identifiant sauvegarde ({@code ""} sans grade). */
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** La couleur du grade : pseudo, prefixe, cartes. */
    public int color() {
        return color;
    }

    /** Le prix plein, depuis aucun grade. */
    public long price() {
        return price;
    }

    public int homes() {
        return homes;
    }

    /** Les teleportations au hasard ({@code /rtp}) par mois. */
    public int rtp() {
        return rtp;
    }

    /** Les rangees du coffre de l'Ender (3 sans grade, comme le jeu). */
    public int enderRows() {
        return enderRows;
    }

    /** Le bonus d'XP de metier, 0,05 pour +5 %. */
    public double jobXp() {
        return jobXp;
    }

    /** Le bonus d'XP du pass (les points du Palapass de Paladium). */
    public double passXp() {
        return passXp;
    }

    /** Le bonus d'XP classique, celle des orbes. */
    public double vanillaXp() {
        return vanillaXp;
    }

    /** La part de la taxe du marche international qui disparait, 0,10 pour -10 %. */
    public double taxCut() {
        return taxCut;
    }

    /** Les annonces en plus a l'hotel des ventes. */
    public int auctionSlots() {
        return auctionSlots;
    }

    public boolean atLeast(PlayerGrade other) {
        return ordinal() >= other.ordinal();
    }

    /** Le grade d'un identifiant sauvegarde ; aucun pour un identifiant inconnu. */
    public static PlayerGrade byId(String id) {
        if (id == null || id.isEmpty()) {
            return NONE;
        }
        for (PlayerGrade grade : values()) {
            if (grade.id.equals(id)) {
                return grade;
            }
        }
        return NONE;
    }

    /** Les trois grades qui s'achetent, du moins cher au plus cher. */
    public static PlayerGrade[] buyable() {
        return new PlayerGrade[] {HEROS, LEGENDE, DIVINITE};
    }
}
