package fr.atraxterra.dohy.faction.enterprise;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Les secteurs d'activite des entreprises (lot 24b) : six legaux, onze illegaux. Un secteur fixe
 * ce que l'entreprise fait chaque heure ({@link Role}), son prix, ses frais, ses employes par
 * niveau, son risque de depart et ce qu'il faut pour l'ouvrir.
 *
 * <p>La fabrication reste abstraite : une plantation donne de la matiere, un laboratoire la
 * transforme, un reseau la vend. Aucun procede reel n'est decrit, seulement des etapes de jeu.
 *
 * @param base   selon le role : des $ par heure (revenus), des unites par heure (production,
 *               transformation, vente), une capacite (entrepot) ou des $ blanchis par heure
 * @param cost   les frais par heure au niveau 1 (loyer, entretien, materiel), sans les salaires
 * @param staff  les employes PNJ par niveau
 * @param risk   le risque de depart d'une affaire illegale, sur 100
 */
public enum Sector {
    FERME("Ferme", "Ferme", true, Role.REVENUE, 20_000L, 420.0d, 120.0d, 3, 0.0d, Need.NONE, true, true,
            () -> Items.WHEAT, 0xFFFAC400, "Cultures et elevage : la saison compte"),
    CARRIERE("Carriere", "Carriere", true, Role.REVENUE, 35_000L, 650.0d, 200.0d, 4, 0.0d, Need.NONE, false, true,
            () -> Items.IRON_PICKAXE, 0xFF9AA0A6, "Pierre et minerai : +20 % avec une de tes mines dans le pays"),
    USINE("Usine", "Usine", true, Role.REVENUE, 60_000L, 1_100.0d, 380.0d, 6, 0.0d, Need.INFLUENCE_20, false, true,
            () -> Items.BLAST_FURNACE, 0xFFE8590C, "Production en grand : il faut 20 d'influence dans le pays"),
    COMMERCE("Commerce", "Commerce", true, Role.REVENUE, 30_000L, 560.0d, 160.0d, 3, 0.0d, Need.NONE, false, false,
            () -> Items.EMERALD, 0xFF22E558, "Boutiques : l'influence dans le pays compte double"),
    BANQUE("Banque", "Banque", true, Role.REVENUE, 120_000L, 1_900.0d, 500.0d, 4, 0.0d, Need.HELD, false, false,
            () -> Items.GOLD_BLOCK, 0xFFFAC400, "Seulement dans un pays que tient ta nation"),
    TRANSPORT("Transport", "Transport", true, Role.REVENUE, 45_000L, 750.0d, 260.0d, 4, 0.0d, Need.NONE, false, true,
            () -> Items.MINECART, 0xFF27C4E8, "Logistique : aide tes usines, tes commerces et ta contrebande"),

    PLANTATION("Plantation clandestine", "Plantation", false, Role.GROW, 30_000L, 1.0d, 150.0d, 4, 12.0d,
            Need.CLIMATE, true, true, () -> Items.FERN, 0xFF22E558, "Fait pousser la matiere premiere d'une drogue"),
    LABORATOIRE("Laboratoire clandestin", "Laboratoire", false, Role.LAB, 60_000L, 16.0d, 300.0d, 3, 18.0d,
            Need.NONE, false, true, () -> Items.BREWING_STAND, 0xFFEC4FB5,
            "Transforme la matiere premiere en produit fini"),
    ENTREPOT("Entrepot", "Entrepot", false, Role.STORE, 25_000L, 800.0d, 100.0d, 2, 8.0d, Need.NONE, false, true,
            () -> Items.BARREL, 0xFFA0522D, "Garde les stocks : plus de place, deux fois moins de saisies"),
    RESEAU("Reseau de distribution", "Reseau", false, Role.SELL, 40_000L, 24.0d, 220.0d, 6, 20.0d, Need.NONE,
            false, false, () -> Items.LEAD, 0xFFEF3826, "Ecoule les produits finis, au prix de la rue"),
    ATELIER("Atelier d'armes clandestin", "Atelier", false, Role.ARMS, 90_000L, 4.0d, 450.0d, 4, 22.0d, Need.NONE,
            false, true, () -> Items.ANVIL, 0xFF9AA0A6, "Monte des caisses d'armes avec le fer du coffre de la banque"),
    TRAFIC("Trafic d'armes", "Trafic", false, Role.ARMS_SELL, 70_000L, 5.0d, 300.0d, 3, 25.0d, Need.NONE, false,
            false, () -> Items.CROSSBOW, 0xFFEF3826, "Revend les caisses d'armes, plus cher en temps de guerre"),
    CONTREBANDE("Contrebande", "Contrebande", false, Role.SMUGGLE, 50_000L, 900.0d, 250.0d, 4, 16.0d, Need.NONE,
            false, false, () -> Items.COMPASS, 0xFF27C4E8, "Passe les frontieres : chaque pays ou tu es ouvre une route"),
    FACADE("Societe ecran", "Facade", false, Role.LAUNDER, 45_000L, 3_000.0d, 180.0d, 3, 6.0d, Need.NONE, false,
            false, () -> Items.PAPER, 0xFF9AA0A6, "Blanchit l'argent sale vers la banque nationale"),
    CASINO("Casino clandestin", "Casino", false, Role.CASINO, 80_000L, 1_300.0d, 400.0d, 6, 14.0d, Need.NONE, false,
            false, () -> Items.RABBIT_FOOT, 0xFFFAC400, "Rapporte de l'argent sale et en blanchit un peu"),
    RACKET("Racket", "Racket", false, Role.RACKET, 35_000L, 900.0d, 150.0d, 5, 18.0d, Need.INFLUENCE_30, false,
            false, () -> Items.IRON_BARS, 0xFFEF3826, "Protection forcee : il faut 30 d'influence, rapporte selon elle"),
    RECEL("Recel", "Recel", false, Role.FENCE, 30_000L, 600.0d, 120.0d, 2, 10.0d, Need.NONE, false, false,
            () -> Items.CHEST, 0xFF8B5CF6, "Ecoule le vole : le receleur du spawn paie aussi plus cher");

    /** Ce que fait l'entreprise chaque heure. */
    public enum Role {
        /** Des $ par heure : propres pour une entreprise legale. */
        REVENUE,
        /** De la matiere premiere d'une drogue, par heure. */
        GROW,
        /** Matiere premiere en produit fini, par heure. */
        LAB,
        /** De la place pour les stocks. */
        STORE,
        /** Des produits finis vendus, par heure. */
        SELL,
        /** Des caisses d'armes, par heure. */
        ARMS,
        /** Des caisses d'armes vendues, par heure. */
        ARMS_SELL,
        /** De l'argent sale par heure, plus avec chaque pays ou la nation fait affaire. */
        SMUGGLE,
        /** De l'argent sale blanchi par heure, et un petit revenu propre. */
        LAUNDER,
        /** De l'argent sale par heure, et un peu de blanchiment. */
        CASINO,
        /** De l'argent sale par heure, selon l'influence dans le pays. */
        RACKET,
        /** De l'argent sale par heure, et le receleur paie plus. */
        FENCE
    }

    /** Ce qu'il faut pour ouvrir l'entreprise a l'endroit ou l'on est. */
    public enum Need {
        NONE, INFLUENCE_20, INFLUENCE_30, HELD, CLIMATE
    }

    private final String label;
    private final String shortLabel;
    private final boolean legal;
    private final Role role;
    private final long price;
    private final double base;
    private final double cost;
    private final int staff;
    private final double risk;
    private final Need need;
    private final boolean seasonal;
    private final boolean physical;
    private final Supplier<Item> icon;
    private final int color;
    private final String summary;

    Sector(String label, String shortLabel, boolean legal, Role role, long price, double base, double cost,
           int staff, double risk, Need need, boolean seasonal, boolean physical, Supplier<Item> icon, int color,
           String summary) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.legal = legal;
        this.role = role;
        this.price = price;
        this.base = base;
        this.cost = cost;
        this.staff = staff;
        this.risk = risk;
        this.need = need;
        this.seasonal = seasonal;
        this.physical = physical;
        this.icon = icon;
        this.color = color;
        this.summary = summary;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public boolean legal() {
        return legal;
    }

    public Role role() {
        return role;
    }

    /** Le prix d'ouverture. */
    public long price() {
        return price;
    }

    public double base() {
        return base;
    }

    public double cost() {
        return cost;
    }

    /** Les employes par niveau. */
    public int staff() {
        return staff;
    }

    public double risk() {
        return risk;
    }

    public Need need() {
        return need;
    }

    /** La saison pese sur ce qu'elle produit. */
    public boolean seasonal() {
        return seasonal;
    }

    /** Une catastrophe dans le pays la frappe de plein fouet (des murs, des champs, des machines). */
    public boolean physical() {
        return physical;
    }

    /** Une drogue par entreprise : plantation et laboratoire. */
    public boolean lined() {
        return role == Role.GROW || role == Role.LAB;
    }

    public ItemStack icon() {
        return new ItemStack(icon.get());
    }

    public int color() {
        return color;
    }

    public String summary() {
        return summary;
    }

    public static Sector of(String name) {
        for (Sector sector : values()) {
            if (sector.name().equals(name)) {
                return sector;
            }
        }
        return null;
    }
}
