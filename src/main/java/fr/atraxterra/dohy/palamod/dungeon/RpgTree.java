package fr.atraxterra.dohy.palamod.dungeon;

/**
 * La forme de l'arbre de competences, la meme pour les quatre branches : dix noeuds, une racine
 * pres du centre, puis deux bras qui s'ecartent. Un noeud se prend quand son parent est pris.
 *
 * <pre>
 *          8                 (4,-2)
 *      6 - 4 - 2             (5,-1) (4,-1) (3,-1)
 *              1 - 0 - centre        (3,0) (2,0)
 *      7 - 5 - 3             (5,+1) (4,+1) (3,+1)
 *          9                 (4,+2)
 * </pre>
 *
 * Les positions comptent en cases depuis le centre : le long de la branche, puis de cote. Le
 * client et le serveur lisent la meme table.
 */
public final class RpgTree {

    public static final int NODES = 10;
    public static final int ALL = (1 << NODES) - 1;

    /** Le parent de chaque noeud, -1 pour la racine. */
    private static final int[] PARENT = {-1, 0, 1, 1, 2, 3, 4, 5, 4, 5};
    /** La distance au centre, le long de la branche. */
    private static final int[] ALONG = {2, 3, 3, 3, 4, 4, 5, 5, 4, 4};
    /** Le decalage de cote. */
    private static final int[] SIDE = {0, 0, -1, 1, -1, 1, -1, 1, -2, 2};
    /** Le prix de chaque noeud : 65 points la branche, comme avant. */
    private static final int[] COST = {2, 3, 4, 4, 6, 6, 10, 10, 10, 10};

    private RpgTree() {
    }

    public static int parent(int node) {
        return PARENT[node];
    }

    public static int along(int node) {
        return ALONG[node];
    }

    public static int side(int node) {
        return SIDE[node];
    }

    public static int cost(int node) {
        return COST[node];
    }

    public static boolean has(int mask, int node) {
        return (mask >> node & 1) != 0;
    }

    /** Le noeud se prend-il ? Pas encore a soi, et son parent est pris (ou reserve). */
    public static boolean open(int mask, int node) {
        int parent = PARENT[node];
        return !has(mask, node) && (parent < 0 || has(mask, parent));
    }

    /** Le prix des noeuds d'un masque. */
    public static int price(int mask) {
        int total = 0;
        for (int node = 0; node < NODES; node++) {
            if (has(mask, node)) {
                total += COST[node];
            }
        }
        return total;
    }

    /** Un noeud et tout ce qui en descend, dans ce masque. */
    public static int subtree(int mask, int node) {
        int result = 1 << node;
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int child = 0; child < NODES; child++) {
                int parent = PARENT[child];
                if (parent >= 0 && (result >> parent & 1) != 0 && (result >> child & 1) == 0 && has(mask, child)) {
                    result |= 1 << child;
                    grew = true;
                }
            }
        }
        return result & mask;
    }

    /** Un masque bien forme : chaque noeud pris a son parent pris. */
    public static int sane(int mask) {
        int result = 0;
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int node = 0; node < NODES; node++) {
                if (has(mask, node) && !has(result, node) && (PARENT[node] < 0 || has(result, PARENT[node]))) {
                    result |= 1 << node;
                    grew = true;
                }
            }
        }
        return result;
    }
}
