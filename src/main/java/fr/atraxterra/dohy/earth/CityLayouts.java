package fr.atraxterra.dohy.earth;

import java.util.List;
import java.util.Map;

/**
 * Le plan des onze grandes capitales, d'apres la vraie ville : son fleuve,
 * ses parcs, une grande avenue, et ses monuments a leur place les uns par
 * rapport aux autres. Ce sont des schemas a l'echelle de la ville du jeu (144
 * blocs pour les plus grandes), dresses a la main : la Seine coule d'est en
 * ouest sous Notre-Dame, la tour est rive gauche a l'ouest, l'Arc au
 * nord-ouest au bout de son avenue. Les distances se comptent depuis le centre
 * de la ville, x vers l'est, z vers le sud ; les sites en chunks.
 */
public final class CityLayouts {

    private CityLayouts() {
    }

    /**
     * Un monument et sa place.
     *
     * @param dx   le chunk du milieu de la place, depuis celui de la ville
     * @param half 0 : un chunk ; 1 : trois chunks de cote
     * @param main vrai pour la place qui porte le nom de la ville
     */
    public record Site(int dx, int dz, int half, String monument, String label, boolean main) {
        public boolean covers(int cx, int cz) {
            return Math.abs(cx - dx) <= half && Math.abs(cz - dz) <= half;
        }
    }

    /**
     * @param river   les points du fleuve, {x, z} ; vide sans fleuve
     * @param avenues les grandes avenues, chacune une suite de points
     * @param parks   les chunks de parc, {dx, dz}
     */
    public record Layout(int[][] river, int riverHalf, int[][][] avenues, int[][] parks, List<Site> sites) {
    }

    private static Site big(int dx, int dz, String monument, String label) {
        return new Site(dx, dz, 1, monument, label, true);
    }

    private static Site small(int dx, int dz, String monument, String label) {
        return new Site(dx, dz, 0, monument, label, false);
    }

    private static final int[][][] NONE = {};

    private static final Map<String, Layout> LAYOUTS = Map.ofEntries(
            Map.entry("paris", new Layout(
                    new int[][] {{72, 34}, {40, 14}, {14, 12}, {4, 2}, {-56, 2}, {-72, 30}}, 5,
                    new int[][][] {{{-12, -18}, {-40, -30}}},
                    new int[][] {{-4, -1}, {0, 2}, {2, -2}},
                    List.of(big(-2, 2, "lattice_tower", "Tour Eiffel"),
                            small(1, 0, "cathedral", "Notre-Dame"),
                            small(-1, -1, "long_palace", "Le Louvre"),
                            small(-3, -2, "arch", "Arc de Triomphe"),
                            small(0, -4, "domed_church", "Sacre-Coeur")))),
            Map.entry("london", new Layout(
                    new int[][] {{-72, 16}, {-40, 22}, {-12, 14}, {8, 16}, {30, 8}, {52, 14}, {72, 8}}, 5,
                    NONE,
                    new int[][] {{-4, -2}, {-3, -3}, {2, -3}},
                    List.of(big(-2, -1, "clock_tower", "Westminster"),
                            small(1, -1, "domed_church", "Saint-Paul"),
                            small(3, 0, "keep", "Tour de Londres"),
                            small(-1, 2, "wheel", "La grande roue"),
                            small(-4, -1, "long_palace", "Buckingham"),
                            small(0, -2, "column", "Trafalgar")))),
            Map.entry("rome", new Layout(
                    new int[][] {{-26, -56}, {-22, -20}, {-30, 0}, {-22, 24}, {-26, 56}}, 4,
                    NONE,
                    new int[][] {{0, 3}, {2, -2}},
                    List.of(big(1, 1, "amphitheatre", "Colisee"),
                            small(-3, -1, "domed_church", "Saint-Pierre"),
                            small(0, -1, "temple", "Pantheon"),
                            small(-1, -3, "column", "Piazza del Popolo")))),
            Map.entry("moscow", new Layout(
                    new int[][] {{-72, -10}, {-40, 20}, {-10, 14}, {10, 22}, {30, 40}, {40, 72}}, 5,
                    NONE,
                    new int[][] {{0, 3}, {-1, 3}, {3, -2}},
                    List.of(big(0, -1, "onion_domes", "Saint-Basile"),
                            small(-2, 0, "fortress", "Kremlin"),
                            small(0, -3, "temple", "Bolchoi"),
                            small(-3, -1, "domed_church", "Christ-Sauveur")))),
            Map.entry("washington_d_c", new Layout(
                    new int[][] {{-56, 10}, {-30, 26}, {-10, 56}}, 5,
                    NONE,
                    new int[][] {{-2, 0}, {2, -2}},
                    List.of(big(0, 0, "obelisk_capitol", "Le Mall"),
                            small(-3, 0, "temple", "Memorial Lincoln"),
                            small(0, -3, "long_palace", "Maison-Blanche"),
                            small(1, 2, "domed_church", "Memorial Jefferson")))),
            Map.entry("beijing", new Layout(
                    new int[][] {}, 0,
                    new int[][][] {{{-72, 28}, {72, 28}}},
                    new int[][] {{0, -4}, {-1, -3}, {-2, -2}},
                    List.of(big(0, 0, "imperial_gate", "Tian'anmen"),
                            small(0, -2, "long_palace", "Cite interdite"),
                            small(1, 3, "round_temple", "Temple du Ciel"),
                            small(-3, 1, "column", "Tour du Tambour")))),
            Map.entry("cairo", new Layout(
                    new int[][] {{-10, 72}, {-4, 30}, {-12, 0}, {-2, -30}, {-8, -72}}, 6,
                    NONE,
                    new int[][] {{3, 1}},
                    List.of(big(-3, 1, "pyramids", "Gizeh"),
                            small(2, 0, "domed_church", "La Citadelle"),
                            small(1, -2, "tv_tower", "Tour du Caire"),
                            small(2, 2, "fortress", "Vieux Caire")))),
            Map.entry("berlin", new Layout(
                    new int[][] {{-56, -14}, {-20, -22}, {0, -18}, {24, -26}, {56, -20}}, 4,
                    NONE,
                    new int[][] {{-3, 0}, {-3, 1}},
                    List.of(big(-1, 1, "column_gate", "Porte de Brandebourg"),
                            small(2, 0, "tv_tower", "Tour de television"),
                            small(1, 0, "domed_church", "Cathedrale"),
                            small(1, 2, "long_palace", "Le Chateau")))),
            Map.entry("athens", new Layout(
                    new int[][] {}, 0,
                    NONE,
                    new int[][] {{2, -1}, {2, 0}, {-3, 0}},
                    List.of(big(0, 0, "acropolis", "Acropole"),
                            small(2, 1, "temple", "Temple de Zeus"),
                            small(1, -2, "long_palace", "Parlement"),
                            small(-2, -2, "column", "Agora")))),
            Map.entry("new_delhi", new Layout(
                    new int[][] {{36, -40}, {40, 0}, {36, 40}}, 4,
                    NONE,
                    new int[][] {{0, 2}},
                    List.of(big(0, 0, "triumph_arch", "Porte de l'Inde"),
                            small(-2, 0, "long_palace", "Palais presidentiel"),
                            small(1, -2, "fortress", "Fort Rouge")))),
            Map.entry("tokyo", new Layout(
                    new int[][] {{56, -72}, {50, -30}, {58, 0}, {52, 40}, {58, 72}}, 4,
                    NONE,
                    new int[][] {{-1, 0}, {-1, -1}, {-3, -1}},
                    List.of(big(1, -2, "pagoda", "Senso-ji"),
                            small(0, 0, "keep", "Palais imperial"),
                            new Site(-1, 2, 1, "lattice_red", "Tour de Tokyo", false),
                            small(-3, 1, "temple", "Sanctuaire Meiji")))));

    /** Le plan de cette ville, ou {@code null} : un damier et un palais au milieu. */
    public static Layout of(String id) {
        return LAYOUTS.get(id);
    }
}
