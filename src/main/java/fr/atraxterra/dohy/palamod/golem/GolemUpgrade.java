package fr.atraxterra.dohy.palamod.golem;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * L'arbre de competences du golem ({@code ModGuardianUpgrades.init}).
 *
 * <p>Chaque amelioration a un niveau, un cout en points de capacite et un
 * parent ; Guerriere et Farmeur s'excluent. Le golem gagne un point par
 * niveau, 40 au plus.
 *
 * <p>Non portees, parce qu'elles ne font rien dans GuardianGolem 6.1.0 ou
 * dependent d'un service externe : Lanceur d'alerte (webhook Discord),
 * Demembrement ({@code earthquake()} est vide), Multiplication 1 a 3 (il n'y a
 * pas de limite par chunk), Autocrafter (jamais ajoute a l'arbre). Mecagolem
 * reste, sans effet comme dans l'original, parce que Discret et Golem en boite
 * en dependent.
 */
public enum GolemUpgrade implements StringRepresentable {
    GOLEMANCY("golemancy", 1, null, Branch.ROOT,
            "Golemancie", "La racine de l'arbre : obligatoire pour tout le reste."),
    UTILS("utils", 0, GOLEMANCY, Branch.UTILS,
            "Utilitaire", "Ouvre la voie utilitaire."),
    WAR("war", 0, GOLEMANCY, Branch.WAR,
            "Guerriere", "Ouvre la voie guerriere. Exclut la voie farmeur."),
    FARMER("farmer", 0, GOLEMANCY, Branch.FARMER,
            "Farmeur", "Ouvre la voie farmeur. Exclut la voie guerriere."),

    INNOVATIVE("utils.innovative", 1, UTILS, Branch.UTILS,
            "Regime novateur", "Le golem mange aussi le findium et ses blocs."),
    GREEDY("utils.greedy", 1, INNOVATIVE, Branch.UTILS,
            "Gourmand", "Double l'XP gagnee par le golem."),
    MECAGOLEM("utils.mecagolem", 1, GREEDY, Branch.UTILS,
            "Mecagolem", "Sans effet, comme dans l'original ; ouvre Discret et Golem en boite."),
    DISCREET("utils.discreet", 1, MECAGOLEM, Branch.UTILS,
            "Discret", "La barre de vie du golem n'est plus affichee."),
    INBOX("utils.inbox", 1, MECAGOLEM, Branch.UTILS,
            "Golem en boite", "Le golem peut travailler, en mini golem, dans une boite a golem."),
    STOCKAGE("utils.stockage", 2, GREEDY, Branch.UTILS,
            "Stockeur", "Un inventaire de 16 cases, qui tombe a la mort du golem."),
    HOUSEWORK("utils.housework", 1, STOCKAGE, Branch.UTILS,
            "Golem de menage", "Ramasse les objets au sol et les range dans son inventaire."),
    WITHER_HUNTER("utils.witherhunter", 2, GREEDY, Branch.UTILS,
            "Wither hunter", "Au menu des choix : chasser les withers, ou se faire ignorer d'eux."),

    FIGHTER("wars.fighter", 5, WAR, Branch.WAR,
            "Combattant", "Degats de base 6,6 au lieu de 6."),
    BERSERKER("wars.berserker", 2, FIGHTER, Branch.WAR,
            "Berserker", "20 PV de base en plus."),
    FIGHTER2("wars.fighter2", 3, BERSERKER, Branch.WAR,
            "Combattant 2", "Degats de base 7,8."),
    ADVANCED_RESISTANCE("wars.advanced_resistance", 2, FIGHTER2, Branch.WAR,
            "Resistance avancee", "Au menu des choix : mieux resister aux coups ou aux projectiles."),
    SPECIALIZED_RESISTANCE("wars.specialized_resistance", 2, ADVANCED_RESISTANCE, Branch.WAR,
            "Resistance specialisee", "Au menu des choix : explosions, feu, magie ou noyade."),
    CARNAGE("wars.carnage", 3, BERSERKER, Branch.WAR,
            "Carnage", "Frappe deux fois par seconde au lieu d'une."),
    ACCELERATION("wars.acceleration", 2, CARNAGE, Branch.WAR,
            "Acceleration", "Vitesse de deplacement +15 %."),
    LAST_BREATH("wars.last_breath", 3, ACCELERATION, Branch.WAR,
            "Dernier souffle", "A 10 % de vie, un sursaut ; configurable au menu des choix."),
    MAD_GOLEM("wars.mad_golem", 10, LAST_BREATH, Branch.WAR,
            "Golem fou", "Encaisse 60 % de degats en moins, mais attaque tout le monde, son maitre compris. Plus d'interface, et perdu a sa mort."),
    MAGICIAN("wars.magician", 2, BERSERKER, Branch.WAR,
            "Magicien", "Toutes les 29 secondes, un sort sur sa cible ; configurable au menu des choix."),
    DESYNCHRONIZATION("wars.desynchronization", 3, MAGICIAN, Branch.WAR,
            "Desynchronisation", "Chaque coup frappe aussi le joueur le plus proche de lui."),

    PACIFICATION("farmer.pacification", 1, FARMER, Branch.FARMER,
            "Pacification", "Le golem ne se bat plus et ne bouge plus."),
    INDUSTRIAL_REGIME("farmer.industrial_regime", 1, PACIFICATION, Branch.FARMER,
            "Regime industriel", "Ouvre les createurs : une croquette toutes les 8 minutes."),
    IRON_MAKER("farmer.iron_maker", 2, INDUSTRIAL_REGIME, Branch.FARMER,
            "Ferero-createur", "Une croquette donne 10 lingots de fer."),
    AMETHYST_MAKER("farmer.amethyst_maker", 2, IRON_MAKER, Branch.FARMER,
            "Ametho-createur", "Une croquette donne 5 lingots d'amethyste."),
    TITANE_MAKER("farmer.titane_maker", 2, AMETHYST_MAKER, Branch.FARMER,
            "Titano-createur", "Une croquette donne 3 lingots de titane."),
    PALADIUM_MAKER("farmer.paladium_maker", 3, TITANE_MAKER, Branch.FARMER,
            "Palado-createur", "Une croquette donne 1 lingot de paladium."),
    SHIELD("farmer.shield", 2, INDUSTRIAL_REGIME, Branch.FARMER,
            "Bouclier", "Le golem ne prend plus de degats d'explosion.");

    public static final Codec<GolemUpgrade> CODEC = StringRepresentable.fromEnum(GolemUpgrade::values);

    public enum Branch { ROOT, UTILS, WAR, FARMER }

    private final String key;
    private final int cost;
    private final GolemUpgrade parent;
    private final Branch branch;
    private final String label;
    private final String description;

    GolemUpgrade(String key, int cost, GolemUpgrade parent, Branch branch, String label, String description) {
        this.key = key;
        this.cost = cost;
        this.parent = parent;
        this.branch = branch;
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String key() {
        return key;
    }

    public int cost() {
        return cost;
    }

    public GolemUpgrade parent() {
        return parent;
    }

    public Branch branch() {
        return branch;
    }

    @Override
    public String getSerializedName() {
        return key.replace('.', '_');
    }

    /** Les makers, du meilleur au moins bon : croquette -> metal. */
    public static final GolemUpgrade[] MAKERS = {PALADIUM_MAKER, TITANE_MAKER, AMETHYST_MAKER, IRON_MAKER};
}
