package fr.atraxterra.dohy.palette;

import java.util.ArrayList;
import java.util.List;

/**
 * La pile de caisses d'un groupe de palettes : ou poser chaque caisse.
 *
 * <p>Chaque palette porte 16 piles, montrees en trois etages de caisses au
 * plus. Un etage d'une palette vaut donc un tiers de ses 16 piles ; il porte
 * les caisses d'une seule famille :
 * <ul>
 *   <li><b>obus</b> : deux caisses longues cote a cote ;</li>
 *   <li><b>munitions</b> : six boites metalliques, deux de long sur trois de large ;</li>
 *   <li><b>missiles</b> : une caisse geante. Les caisses geantes voisines d'une
 *       meme rangee, au meme etage, n'en font qu'une : sur trois palettes
 *       alignees, la caisse court sur les trois.</li>
 * </ul>
 *
 * <p>Les etages se remplissent palette par palette, de bas en haut, comme sur
 * un vrai quai : on monte une pile jusqu'a trois caisses avant d'entamer la
 * palette suivante. Les missiles d'abord (les plus lourds, en bas), puis les
 * obus, puis les munitions. Le dernier etage d'une famille peut etre
 * incomplet ; la famille suivante repart alors sur une palette neuve, pour
 * qu'aucune caisse ne flotte au-dessus d'un trou.
 *
 * <p>Les longueurs suivent l'axe long du groupe (l'axe de la palette posee,
 * pour un groupe carre). Les mesures sont en pixels (1/16 de bloc), depuis le
 * coin nord-ouest du groupe, au pied des palettes.
 *
 * <p>Pur Java, sans Minecraft : {@code tools/PaletteLayoutTest} le verifie seul.
 */
public final class CrateLayout {

    /** Les piles d'une palette. */
    public static final int SLOTS = 16;
    /** Les caisses montent jusqu'a trois de haut. */
    public static final int LAYERS = 3;
    /** L'epaisseur de la palette, sous les caisses. */
    public static final float PALLET = 3.0f;

    public static final int OBUS = 0;
    public static final int MUNITIONS = 1;
    public static final int MISSILES = 2;
    public static final int FAMILIES = 3;

    /** La hauteur d'un etage, par famille. Trois etages de missiles : 3 + 21 = 24 px, un bloc et demi. */
    public static final float[] HEIGHT = {5.0f, 4.5f, 7.0f};

    /** L'ordre de chargement : les plus lourds en bas. */
    private static final int[] ORDER = {MISSILES, OBUS, MUNITIONS};

    private static final float EPS = 1.0e-4f;

    private CrateLayout() {
    }

    /**
     * Une caisse, en pixels depuis le coin nord-ouest du groupe.
     *
     * @param alongX   sa longueur suit l'axe x (sinon z)
     * @param segments pour une caisse geante, le nombre de palettes qu'elle couvre
     */
    public record Crate(int family, float x0, float y0, float z0, float x1, float y1, float z1,
                        boolean alongX, int segments) {
    }

    /**
     * Le resultat : les caisses, et le sommet de chaque palette du groupe
     * (index {@code dx + dz * w}) pour sa forme de collision.
     */
    public record Layout(List<Crate> crates, float[] tops) {
    }

    /**
     * Pose les caisses d'un groupe de {@code w x d} palettes.
     *
     * @param stacks l'occupation par famille, en piles (une demi-pile compte 0,5)
     */
    public static Layout compute(int w, int d, boolean alongX, float[] stacks) {
        int cells = Math.max(1, w * d);
        float[] tops = new float[cells];
        java.util.Arrays.fill(tops, PALLET);
        int[] layers = new int[cells];
        List<Crate> crates = new ArrayList<>();
        List<float[]> giants = new ArrayList<>();
        int lineLength = alongX ? w : d;
        float perLayer = (float) SLOTS / LAYERS;
        int cursor = 0;
        for (int family : ORDER) {
            float left = family < stacks.length ? stacks[family] : 0.0f;
            while (left > EPS && cursor < cells) {
                float fill = Math.min(1.0f, left / perLayer);
                left -= perLayer;
                int line = cursor / lineLength;
                int along = cursor % lineLength;
                int dx = alongX ? along : line;
                int dz = alongX ? line : along;
                int cell = dx + dz * w;
                float y0 = tops[cell];
                if (family == MISSILES) {
                    giants.add(new float[]{line, along, y0});
                } else {
                    place(crates, family, dx, dz, y0, fill, alongX);
                }
                tops[cell] = y0 + HEIGHT[family];
                layers[cell]++;
                if (layers[cell] >= LAYERS || fill < 1.0f - EPS) {
                    cursor++;
                }
            }
        }
        mergeGiants(crates, giants, alongX);
        return new Layout(crates, tops);
    }

    /** Les caisses longues ou les boites d'un etage, autant que le remplissage en demande. */
    private static void place(List<Crate> crates, int family, int dx, int dz, float y0, float fill,
                              boolean alongX) {
        float y1 = y0 + HEIGHT[family];
        if (family == OBUS) {
            int count = (int) Math.ceil(fill * 2.0f - EPS);
            for (int i = 0; i < Math.max(1, count); i++) {
                crates.add(crate(family, dx, dz, 1.0f, 15.0f, 1.0f + i * 7.5f, 7.5f + i * 7.5f, y0, y1, alongX, 1));
            }
        } else {
            int count = (int) Math.ceil(fill * 6.0f - EPS);
            for (int i = 0; i < Math.max(1, count); i++) {
                int a = i / 3;
                int b = i % 3;
                crates.add(crate(family, dx, dz, 1.0f + a * 7.5f, 7.5f + a * 7.5f, 1.0f + b * 5.0f,
                        5.0f + b * 5.0f, y0, y1, alongX, 1));
            }
        }
    }

    /**
     * Une caisse, donnee le long de l'axe ({@code u}) et en travers ({@code v}),
     * dans la palette {@code (dx, dz)}.
     */
    private static Crate crate(int family, int dx, int dz, float u0, float u1, float v0, float v1,
                               float y0, float y1, boolean alongX, int segments) {
        float baseX = dx * 16.0f;
        float baseZ = dz * 16.0f;
        if (alongX) {
            return new Crate(family, baseX + u0, y0, baseZ + v0, baseX + u1, y1, baseZ + v1, true, segments);
        }
        return new Crate(family, baseX + v0, y0, baseZ + u0, baseX + v1, y1, baseZ + u1, false, segments);
    }

    /** Les caisses geantes voisines d'une rangee, au meme etage, n'en font qu'une. */
    private static void mergeGiants(List<Crate> crates, List<float[]> giants, boolean alongX) {
        giants.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0])
                : Math.abs(a[2] - b[2]) > EPS ? Float.compare(a[2], b[2]) : Float.compare(a[1], b[1]));
        int index = 0;
        while (index < giants.size()) {
            float[] first = giants.get(index);
            int end = index;
            while (end + 1 < giants.size()) {
                float[] next = giants.get(end + 1);
                if (next[0] != first[0] || Math.abs(next[2] - first[2]) > EPS
                        || next[1] != giants.get(end)[1] + 1) {
                    break;
                }
                end++;
            }
            int line = (int) first[0];
            int start = (int) first[1];
            int segments = end - index + 1;
            int dx = alongX ? start : line;
            int dz = alongX ? line : start;
            float y0 = first[2];
            crates.add(crate(MISSILES, dx, dz, 1.0f, segments * 16.0f - 1.0f, 1.0f, 15.0f, y0,
                    y0 + HEIGHT[MISSILES], alongX, segments));
            index = end + 1;
        }
    }
}
