package fr.atraxterra.dohy.palette;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test des palettes, sans Minecraft : le decoupage en groupes d'au plus 3 x 3
 * ({@link PalettePlan}) et la pile de caisses ({@link CrateLayout}).
 *
 * <pre>
 *   javac -d /tmp/palette src/main/java/fr/atraxterra/dohy/palette/PalettePlan.java \
 *     src/main/java/fr/atraxterra/dohy/palette/CrateLayout.java tools/PaletteLayoutTest.java
 *   java -cp /tmp/palette fr.atraxterra.dohy.palette.PaletteLayoutTest
 * </pre>
 */
public final class PaletteLayoutTest {

    private static int failures;

    private PaletteLayoutTest() {
    }

    public static void main(String[] args) {
        plan();
        crates();
        if (failures > 0) {
            System.out.println(failures + " echec(s)");
            System.exit(1);
        }
        System.out.println("Palettes : tout est bon.");
    }

    // -- Le decoupage --------------------------------------------------------------

    private static void plan() {
        check("3 x 3 : un seul groupe", PalettePlan.partition(square(0, 0, 3, 3)),
                List.of(new PalettePlan.Rect(0, 0, 3, 3)));
        check("4 en ligne vers l'est : 3 + 1", PalettePlan.partition(square(0, 0, 4, 1)),
                List.of(new PalettePlan.Rect(0, 0, 3, 1), new PalettePlan.Rect(3, 0, 1, 1)));
        check("4 en ligne vers le sud : 3 + 1", PalettePlan.partition(square(5, -2, 1, 4)),
                List.of(new PalettePlan.Rect(5, -2, 1, 3), new PalettePlan.Rect(5, 1, 1, 1)));
        check("2 x 3 : un seul groupe", PalettePlan.partition(square(0, 0, 2, 3)),
                List.of(new PalettePlan.Rect(0, 0, 2, 3)));
        List<PalettePlan.Rect> big = PalettePlan.partition(square(0, 0, 6, 6));
        check("6 x 6 : quatre groupes", big.size(), 4);
        for (PalettePlan.Rect rect : big) {
            check("6 x 6 : des groupes de 3 x 3", rect.size(), 9);
        }
        // Un L : la grande branche d'abord, puis le reste.
        Set<Long> ell = new HashSet<>();
        ell.add(PalettePlan.key(0, 0));
        ell.add(PalettePlan.key(1, 0));
        ell.add(PalettePlan.key(2, 0));
        ell.add(PalettePlan.key(0, 1));
        ell.add(PalettePlan.key(0, 2));
        check("un L", PalettePlan.partition(ell),
                List.of(new PalettePlan.Rect(0, 0, 3, 1), new PalettePlan.Rect(0, 1, 1, 2)));
        // Une colonne de 3 sous une palette seule : on ouvre vers le sud.
        Set<Long> tee = new HashSet<>(square(0, 0, 1, 3));
        tee.add(PalettePlan.key(1, 1));
        List<PalettePlan.Rect> teeRects = PalettePlan.partition(tee);
        check("un T couche : la colonne d'abord", teeRects.get(0), new PalettePlan.Rect(0, 0, 1, 3));
        // Coordonnees negatives : les cles tiennent.
        check("cle negative x", PalettePlan.keyX(PalettePlan.key(-7, 12)), -7);
        check("cle negative z", PalettePlan.keyZ(PalettePlan.key(7, -12)), -12);
    }

    private static Set<Long> square(int x, int z, int w, int d) {
        Set<Long> cells = new HashSet<>();
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                cells.add(PalettePlan.key(x + dx, z + dz));
            }
        }
        return cells;
    }

    // -- La pile de caisses --------------------------------------------------------

    private static void crates() {
        // Une palette pleine de missiles : trois caisses geantes empilees.
        CrateLayout.Layout full = CrateLayout.compute(1, 1, true, stacks(0, 0, 16));
        check("1 palette, 16 piles de missiles : 3 caisses", full.crates().size(), 3);
        check("... jusqu'a 24 px", full.tops()[0], 24.0f);
        // Trois palettes alignees : trois caisses de 46 px, une par etage.
        CrateLayout.Layout line = CrateLayout.compute(3, 1, true, stacks(0, 0, 48));
        check("3 palettes, missiles : 3 caisses geantes", line.crates().size(), 3);
        for (CrateLayout.Crate crate : line.crates()) {
            check("... qui courent sur les trois", crate.x1() - crate.x0(), 46.0f);
            check("... en trois segments", crate.segments(), 3);
        }
        // Le long de z, la longueur suit z.
        CrateLayout.Layout column = CrateLayout.compute(1, 2, false, stacks(0, 0, 32));
        for (CrateLayout.Crate crate : column.crates()) {
            check("colonne : caisse le long de z", crate.z1() - crate.z0(), 30.0f);
        }
        // Une seule cartouche : une boite.
        CrateLayout.Layout one = CrateLayout.compute(1, 1, true, stacks(0, 1.0f / 64, 0));
        check("une cartouche : une boite", one.crates().size(), 1);
        // Une palette d'obus pleine : six caisses longues, 3 + 15 = 18 px.
        CrateLayout.Layout shells = CrateLayout.compute(1, 1, true, stacks(16, 0, 0));
        check("16 piles d'obus : 6 caisses", shells.crates().size(), 6);
        check("... jusqu'a 18 px", shells.tops()[0], 18.0f);
        // Pleine de munitions : 18 boites, 3 + 13,5 = 16,5 px.
        CrateLayout.Layout ammo = CrateLayout.compute(1, 1, true, stacks(0, 16, 0));
        check("16 piles de munitions : 18 boites", ammo.crates().size(), 18);
        check("... jusqu'a 16,5 px", ammo.tops()[0], 16.5f);
        // Rien : rien.
        CrateLayout.Layout empty = CrateLayout.compute(3, 3, true, stacks(0, 0, 0));
        check("vide : aucune caisse", empty.crates().size(), 0);
        check("vide : la palette seule", empty.tops()[4], CrateLayout.PALLET);

        // Un melange sur 2 x 2 : rien ne flotte.
        CrateLayout.Layout mixed = CrateLayout.compute(2, 2, true, stacks(10, 3, 8));
        noFloating("melange 2 x 2", mixed);
        // Missiles 8 piles : un etage plein et un demi sur la 1re palette.
        check("melange : missiles d'abord, palette 1", mixed.tops()[0], 3.0f + 7.0f + 7.0f);
        // Obus 10 piles : la 2e palette (un etage plein, un presque plein).
        check("melange : puis les obus, palette 2", mixed.tops()[1], 3.0f + 5.0f + 5.0f);
        // Munitions 3 piles : un etage incomplet sur la 3e (rangee du sud, a l'ouest).
        check("melange : puis les munitions, palette 3", mixed.tops()[2], 3.0f + 4.5f);
        check("melange : la 4e reste nue", mixed.tops()[3], CrateLayout.PALLET);

        // Trop plein : ce qui ne tient pas n'est pas dessine, sans erreur.
        CrateLayout.Layout over = CrateLayout.compute(1, 1, true, stacks(16, 16, 16));
        check("trop plein : 3 etages au plus", over.tops()[0] <= 3.0f + 3 * 7.0f, true);

        // Toute caisse reste dans l'emprise du groupe.
        for (int w = 1; w <= 3; w++) {
            for (int d = 1; d <= 3; d++) {
                for (int mix = 0; mix < 5; mix++) {
                    float total = 16.0f * w * d;
                    float[] s = stacks(total * (mix % 3) / 7, total * ((mix + 1) % 3) / 9, total * (mix % 2) / 4);
                    CrateLayout.Layout layout = CrateLayout.compute(w, d, w >= d, s);
                    noFloating(w + "x" + d + " melange " + mix, layout);
                    for (CrateLayout.Crate crate : layout.crates()) {
                        boolean inside = crate.x0() >= 0 && crate.z0() >= 0 && crate.x1() <= 16 * w
                                && crate.z1() <= 16 * d && crate.y1() <= 24.0f + 1.0e-3f;
                        check(w + "x" + d + " : caisse dans l'emprise", inside, true);
                    }
                }
            }
        }
    }

    /** Chaque caisse repose sur la palette ou sur une caisse dont le dessus la porte entierement. */
    private static void noFloating(String label, CrateLayout.Layout layout) {
        List<CrateLayout.Crate> all = new ArrayList<>(layout.crates());
        for (CrateLayout.Crate crate : all) {
            if (Math.abs(crate.y0() - CrateLayout.PALLET) < 1.0e-3f) {
                continue;
            }
            // Le dessous de la caisse doit etre couvert par des dessus d'etage a sa hauteur :
            // on le verifie aux quatre coins (un pixel en retrait).
            float[][] corners = {
                {crate.x0() + 1, crate.z0() + 1}, {crate.x1() - 1, crate.z0() + 1},
                {crate.x0() + 1, crate.z1() - 1}, {crate.x1() - 1, crate.z1() - 1},
            };
            for (float[] corner : corners) {
                boolean held = false;
                int cellX = (int) (corner[0] / 16);
                int cellZ = (int) (corner[1] / 16);
                for (CrateLayout.Crate other : all) {
                    if (Math.abs(other.y1() - crate.y0()) < 1.0e-3f
                            && (int) ((other.x0() + other.x1()) / 32) == cellX
                            && (int) ((other.z0() + other.z1()) / 32) == cellZ) {
                        held = true;
                    }
                    // une caisse geante couvre plusieurs palettes
                    if (Math.abs(other.y1() - crate.y0()) < 1.0e-3f && other.x0() <= corner[0]
                            && other.x1() >= corner[0] && other.z0() <= corner[1] && other.z1() >= corner[1]) {
                        held = true;
                    }
                }
                if (!held) {
                    failures++;
                    System.out.println("ECHEC " + label + " : une caisse flotte en " + crate);
                    return;
                }
            }
        }
    }

    private static float[] stacks(float obus, float munitions, float missiles) {
        return new float[]{obus, munitions, missiles};
    }

    private static void check(String label, Object actual, Object expected) {
        boolean ok = actual instanceof Float a && expected instanceof Float e
                ? Math.abs(a - e) < 1.0e-3f
                : actual.equals(expected);
        if (!ok) {
            failures++;
            System.out.println("ECHEC " + label + " : " + actual + " au lieu de " + expected);
        }
    }
}
