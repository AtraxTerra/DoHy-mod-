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
public record MachineLayout(List<SlotPos> slots, Sprite arrow, Sprite flame, TextPos counter) {

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
     * <p>C'est la disposition du fourneau vanilla, que tout le monde sait lire
     * sans explication — et le crusher fait la meme chose : une matiere, un
     * combustible, un resultat.
     *
     * <p>Plus un compteur, que le fourneau n'a pas : le crusher accumule des
     * fruits avant de rendre quoi que ce soit — jusqu'a 128 pour l'orangeblue.
     * Sans ce chiffre affiche, un joueur ne peut pas savoir s'il en est au
     * troisieme ou au cent-vingtieme.
     */
    public static final MachineLayout CRUSHER = new MachineLayout(
            List.of(
                    new SlotPos(44, 24, true),
                    new SlotPos(44, 60, true),
                    new SlotPos(116, 42, false)),
            new Sprite(76, 44, ARROW_U, ARROW_V, ARROW_FULL_V, ARROW_W, ARROW_H, false, false),
            new Sprite(45, 44, FLAME_U, FLAME_V, FLAME_FULL_V, FLAME_W, FLAME_H, true, true),
            new TextPos(88, 66));
}
