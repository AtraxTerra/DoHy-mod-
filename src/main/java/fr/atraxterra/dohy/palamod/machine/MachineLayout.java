package fr.atraxterra.dohy.palamod.machine;

import java.util.List;

/**
 * Ou se trouvent les cases d'une machine, en pixels dans le panneau.
 *
 * <p><b>Ces nombres sont aussi dans {@code tools/gen_gui.py}</b>, qui dessine
 * les creux du fond a ces positions. Deux endroits, un seul chiffre : un slot
 * dessine a gauche et declare a droite donnerait une interface ou les objets
 * flottent a cote de leur case, et <b>rien ne le signalerait</b> — ni la
 * compilation, ni le chargement.
 *
 * <p>D'ou le manifeste {@code data/gui_layout.json}, emis par le generateur et
 * compare a ces constantes par {@code tools/check_assets.py}. C'est le meme
 * principe que pour les assets : quand une donnee doit vivre a deux endroits,
 * on fait verifier l'accord par la CI plutot que par l'attention.
 */
public record MachineLayout(List<SlotPos> slots, Sprite arrow, Sprite flame, TextPos counter, Sprite arrow2) {

    /** Une machine a une seule fleche : toutes sauf le grinder. */
    public MachineLayout(List<SlotPos> slots, Sprite arrow, Sprite flame, TextPos counter) {
        this(slots, arrow, flame, counter, null);
    }

    // arrow, flame et counter peuvent etre null : une machine sans cycle
    // (la voidstone) n'a ni jauge ni compteur. arrow2 est la seconde fleche du
    // grinder, qui forge et ameliore en meme temps, sur deux minuteurs.

    /**
     * Ou ecrire le compteur, centre horizontalement sur ce x.
     *
     * <p>{@code null} quand la machine n'a rien a compter. Le cobblebreaker est
     * dans ce cas : son unique etat cache est la cadence, et la fleche la dit
     * deja.
     */
    public record TextPos(int x, int y) {
    }

    /**
     * Une case.
     *
     * <p>{@code input} dit si le shift-clic du joueur peut y deposer quelque
     * chose. Les bacs de sortie repondent non : une pierre envoyee dans un bac
     * de sortie serait hors d'atteinte de la machine.
     */
    public record SlotPos(int x, int y, boolean input) {
    }

    /**
     * Un sprite de progression : ou le poser, ou le lire, et comment il se remplit.
     *
     * <p>{@code vertical} est l'axe, {@code fillUp} le sens. Les deux ne vont pas
     * ensemble : la fleche du cobblebreaker est verticale et se remplit <b>du
     * haut vers le bas</b>, parce qu'elle pointe vers le bas ; la flamme du
     * crusher est verticale aussi mais <b>monte</b>. Confondre les deux
     * remplirait la fleche a l'envers.
     */
    public record Sprite(int x, int y, int u, int v, int fullV, int width, int height,
                         boolean vertical, boolean fillUp) {
    }

    // -- Sprites communs, ranges hors panneau dans la texture ----------------

    private static final int ARROW_U = 176;
    private static final int ARROW_V = 0;
    private static final int ARROW_FULL_V = 17;
    private static final int ARROW_W = 24;
    private static final int ARROW_H = 17;

    private static final int FLAME_U = 176;
    private static final int FLAME_V = 34;
    private static final int FLAME_FULL_V = 48;
    private static final int FLAME_W = 14;
    private static final int FLAME_H = 14;

    /**
     * Le cobblebreaker : une entree, six sorties.
     *
     * <p>Les six bacs sont ranges du metal le plus courant au plus rare, de
     * gauche a droite — l'ordre dans lequel ils se remplissent, et celui des
     * tranches du tirage. Un joueur qui regarde sa machine voit donc la rarete
     * de gauche a droite sans qu'on la lui explique.
     */
    public static final MachineLayout COBBLEBREAKER = new MachineLayout(
            List.of(
                    new SlotPos(80, 18, true),
                    new SlotPos(34, 54, false),
                    new SlotPos(52, 54, false),
                    new SlotPos(70, 54, false),
                    new SlotPos(88, 54, false),
                    new SlotPos(106, 54, false),
                    new SlotPos(124, 54, false)),
            new Sprite(76, 36, ARROW_U, ARROW_V, ARROW_FULL_V, ARROW_W, ARROW_H, true, false),
            null, null);

    /**
     * Le crusher : fruit au-dessus du charbon, sortie a droite.
     *
     * <p>C'est la geometrie <b>exacte</b> du fourneau vanilla — memes
     * coordonnees de cases, de flamme et de fleche — que tout le monde lit sans
     * explication, et le crusher fait la meme chose : une matiere, un
     * combustible, un resultat. La premiere disposition s'en ecartait, et sa case
     * de combustible mordait sur le libelle « Inventaire ».
     *
     * <p>Plus un compteur, que le fourneau n'a pas : le crusher accumule des
     * fruits avant de rendre quoi que ce soit — jusqu'a 128 pour l'orangeblue.
     * Sans ce chiffre affiche, un joueur ne peut pas savoir s'il en est au
     * troisieme ou au cent-vingtieme.
     */
    public static final MachineLayout CRUSHER = new MachineLayout(
            List.of(
                    new SlotPos(56, 17, true),
                    new SlotPos(56, 53, true),
                    new SlotPos(116, 35, false)),
            new Sprite(79, 34, ARROW_U, ARROW_V, ARROW_FULL_V, ARROW_W, ARROW_H, false, false),
            new Sprite(57, 37, FLAME_U, FLAME_V, FLAME_FULL_V, FLAME_W, FLAME_H, true, true),
            new TextPos(91, 58));

    /**
     * La voidstone : une case, au centre, et rien d'autre.
     *
     * <p>Pas de fleche ni de compteur : ce qu'on y pose disparait, il n'y a
     * aucun etat a montrer. {@code arrow} est donc {@code null}, que
     * {@code MachineScreen} sait ignorer.
     */
    public static final MachineLayout VOIDSTONE = new MachineLayout(
            List.of(new SlotPos(80, 35, true)),
            null, null, null);

    /**
     * La Palafurnace : la geometrie du fourneau vanilla, plus la case
     * d'ameliorations a gauche ({@code PaladiumFurnaceContainer} : entree
     * (56,17), combustible (56,53), sortie (116,35), ameliorations (19,35)).
     */
    public static final MachineLayout PALAFURNACE = new MachineLayout(
            List.of(
                    new SlotPos(56, 17, true),
                    new SlotPos(56, 53, true),
                    new SlotPos(116, 35, false),
                    new SlotPos(19, 35, true)),
            new Sprite(79, 34, ARROW_U, ARROW_V, ARROW_FULL_V, ARROW_W, ARROW_H, false, false),
            new Sprite(57, 37, FLAME_U, FLAME_V, FLAME_FULL_V, FLAME_W, FLAME_H, true, true),
            null);

    /**
     * La Palaforge ({@code ForgeContainer}) : la piece en haut (58,15), les
     * lingots en dessous (58,58), une fleche qui descend entre les deux, et le
     * combustible a droite (115,45) sous sa flamme.
     */
    public static final MachineLayout PALAFORGE = new MachineLayout(
            List.of(
                    new SlotPos(58, 15, true),
                    new SlotPos(58, 58, false),
                    new SlotPos(115, 45, true)),
            new Sprite(54, 36, ARROW_U, ARROW_V, ARROW_FULL_V, ARROW_W, ARROW_H, true, false),
            new Sprite(116, 27, FLAME_U, FLAME_V, FLAME_FULL_V, FLAME_W, FLAME_H, true, true),
            null);

    /**
     * Le grinder ({@code GrinderContainer}, resserre dans 176 x 166) : sortie,
     * forge A, forge B, modificateur, outil a ameliorer, paladium — l'ordre des
     * cases de {@code GrinderLogic}. Deux fleches, la forge et l'amelioration,
     * qui avancent chacune sur son minuteur ; la jauge et le compteur disent le
     * reservoir de paladium (sur 100).
     */
    public static final MachineLayout GRINDER = new MachineLayout(
            List.of(
                    new SlotPos(78, 18, false),
                    new SlotPos(8, 18, true),
                    new SlotPos(26, 18, true),
                    new SlotPos(8, 50, true),
                    new SlotPos(60, 50, true),
                    new SlotPos(120, 18, true)),
            new Sprite(48, 18, ARROW_U, ARROW_V, ARROW_FULL_V, ARROW_W, ARROW_H, false, false),
            new Sprite(121, 38, FLAME_U, FLAME_V, FLAME_FULL_V, FLAME_W, FLAME_H, true, true),
            new TextPos(128, 56),
            new Sprite(30, 50, ARROW_U, ARROW_V, ARROW_FULL_V, ARROW_W, ARROW_H, false, false));
}
