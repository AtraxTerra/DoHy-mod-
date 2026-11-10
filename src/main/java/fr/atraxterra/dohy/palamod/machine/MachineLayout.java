package fr.atraxterra.dohy.palamod.machine;

import java.util.List;

/**
 * Ou se trouvent les cases d'une machine, en pixels dans le panneau.
 *
 * <p>Le panneau est celui du kit d'interface (chapitre 21) : 262 x 236, le
 * titre en haut a gauche, la machine dessous, l'inventaire du joueur en bas
 * ({@link MachineMenu#PLAYER_X}). L'ecran dessine le cadre de chaque case a la
 * position de la case elle-meme : il n'y a plus de texture de fond ou un creux
 * dessine pourrait diverger d'une case declaree.
 *
 * <p>L'ordre des cases est celui de la logique de chaque machine (les
 * constantes {@code SLOT_*} de son entite) : on ne change ici que leur place.
 */
public record MachineLayout(List<SlotPos> slots) {

    /**
     * Une case.
     *
     * <p>{@code input} dit si le shift-clic du joueur peut y deposer quelque
     * chose. Les bacs de sortie repondent non : une pierre envoyee dans un bac
     * de sortie serait hors d'atteinte de la machine.
     */
    public record SlotPos(int x, int y, boolean input) {
    }

    /** Le cobblebreaker : la pierre a gauche, les six particules en ligne, de la plus courante a la plus rare. */
    public static final MachineLayout COBBLEBREAKER = new MachineLayout(List.of(
            new SlotPos(40, 72, true),
            new SlotPos(104, 72, false),
            new SlotPos(128, 72, false),
            new SlotPos(152, 72, false),
            new SlotPos(176, 72, false),
            new SlotPos(200, 72, false),
            new SlotPos(224, 72, false)));

    /** Le crusher : les recettes a gauche, le fruit au-dessus du charbon, le resultat a droite. */
    public static final MachineLayout CRUSHER = new MachineLayout(List.of(
            new SlotPos(178, 56, true),
            new SlotPos(178, 102, true),
            new SlotPos(228, 79, false)));

    /** La voidstone : une case au centre, et rien d'autre. */
    public static final MachineLayout VOIDSTONE = new MachineLayout(List.of(
            new SlotPos(123, 76, true)));

    /** La Palafurnace : entree, combustible, sortie, et les ameliorations a droite. */
    public static final MachineLayout PALAFURNACE = new MachineLayout(List.of(
            new SlotPos(40, 56, true),
            new SlotPos(40, 102, true),
            new SlotPos(100, 79, false),
            new SlotPos(170, 56, true)));

    /** La Palaforge : la piece en haut, les lingots a droite, le combustible en bas. */
    public static final MachineLayout PALAFORGE = new MachineLayout(List.of(
            new SlotPos(40, 58, true),
            new SlotPos(108, 79, false),
            new SlotPos(40, 104, true)));

    /**
     * Le grinder : sortie, forge A, forge B, modificateur, outil a ameliorer,
     * paladium — l'ordre de {@code GrinderLogic}.
     */
    public static final MachineLayout GRINDER = new MachineLayout(List.of(
            new SlotPos(110, 56, false),
            new SlotPos(24, 56, true),
            new SlotPos(44, 56, true),
            new SlotPos(24, 108, true),
            new SlotPos(88, 108, true),
            new SlotPos(160, 56, true)));

    /** La machine a croquettes : la lave, les trois ingredients, quatre sorties. */
    public static final MachineLayout FOOD_MACHINE = new MachineLayout(List.of(
            new SlotPos(24, 104, true),
            new SlotPos(84, 56, true),
            new SlotPos(84, 80, true),
            new SlotPos(84, 104, true),
            new SlotPos(196, 68, false),
            new SlotPos(218, 68, false),
            new SlotPos(196, 90, false),
            new SlotPos(218, 90, false)));

    /**
     * La palamachine : la piece au centre d'un quinconce, les quatre
     * ingredients aux coins (1 et 3 en haut, 2 et 4 en bas), le resultat.
     */
    public static final MachineLayout PALAMACHINE = new MachineLayout(List.of(
            new SlotPos(60, 79, true),
            new SlotPos(38, 57, true),
            new SlotPos(38, 101, true),
            new SlotPos(82, 57, true),
            new SlotPos(82, 101, true),
            new SlotPos(196, 79, false)));

    /** La boite a golem : trois mini golems, leurs croquettes dessous, cinq sorties. */
    public static final MachineLayout GOLEM_BOX = new MachineLayout(List.of(
            new SlotPos(24, 56, true),
            new SlotPos(46, 56, true),
            new SlotPos(68, 56, true),
            new SlotPos(24, 104, true),
            new SlotPos(46, 104, true),
            new SlotPos(68, 104, true),
            new SlotPos(160, 64, false),
            new SlotPos(182, 64, false),
            new SlotPos(204, 64, false),
            new SlotPos(171, 88, false),
            new SlotPos(193, 88, false)));
}
