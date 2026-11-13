package fr.atraxterra.dohy.earth;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * Les monuments des capitales, au milieu de leur place.
 *
 * <p>Onze grandes capitales ont un monument dessine pour le jeu, d'apres un
 * monument ancien de la ville : une tour en treillis a Paris, une tour de
 * l'horloge a Londres, un amphitheatre a Rome, une cathedrale a bulbes a
 * Moscou, un obelisque et un capitole a Washington, une porte imperiale a
 * Pekin, des pyramides au Caire, une porte a colonnes a Berlin, un temple sur
 * son rocher a Athenes, un arc de triomphe a New Delhi, une pagode a Tokyo.
 * Ce sont des evocations a l'echelle de la place (48 x 48 blocs), pas des
 * maquettes. Les autres capitales ont un palais national, dans le style de
 * leur region.
 */
public final class CityMonuments {

    private CityMonuments() {
    }

    private static BlockState b(String name) {
        return CityBlocks.get(name);
    }

    private static BlockState with(String name, String... properties) {
        BlockState state = b(name);
        for (int i = 0; i + 1 < properties.length; i += 2) {
            state = CityBlocks.with(state, properties[i], properties[i + 1]);
        }
        return state;
    }

    /** Pose le monument d'une place et son nom ; la place est deja pavee. */
    public static void build(CityBuilder.Plan plan, CityBuilder.City city, CityLayouts.Site site, int baseY,
                             String countryName) {
        int x = city.centerX() + site.dx() * 16;
        int z = city.centerZ() + site.dz() * 16;
        int y = baseY + 1;
        Random random = new Random(city.seed() ^ site.label().hashCode());
        int labelZ;
        switch (site.monument()) {
            case "lattice_tower" -> labelZ = latticeTower(plan, x, y, z, "brown_terracotta", "brown_terracotta");
            case "lattice_red" -> labelZ = latticeTower(plan, x, y, z, "red_concrete", "white_concrete");
            case "clock_tower" -> labelZ = clockTower(plan, x, y, z);
            case "amphitheatre" -> labelZ = amphitheatre(plan, x, y, z);
            case "onion_domes" -> labelZ = onionDomes(plan, x, y, z);
            case "obelisk_capitol" -> labelZ = obeliskCapitol(plan, x, y, z);
            case "imperial_gate" -> labelZ = imperialGate(plan, x, y, z);
            case "pyramids" -> labelZ = pyramids(plan, x, y, z);
            case "column_gate" -> labelZ = columnGate(plan, x, y, z);
            case "acropolis" -> labelZ = acropolis(plan, x, y, z);
            case "triumph_arch" -> labelZ = triumphArch(plan, x, y, z);
            case "pagoda" -> labelZ = pagoda(plan, x, y, z);
            case "cathedral" -> labelZ = cathedral(plan, x, y, z);
            case "long_palace" -> labelZ = longPalace(plan, city, x, y, z);
            case "arch" -> labelZ = arch(plan, x, y, z);
            case "domed_church" -> labelZ = domedChurch(plan, city, x, y, z);
            case "wheel" -> labelZ = wheel(plan, x, y, z);
            case "keep" -> labelZ = keep(plan, x, y, z);
            case "temple" -> labelZ = temple(plan, x, y, z);
            case "round_temple" -> labelZ = roundTemple(plan, x, y, z);
            case "tv_tower" -> labelZ = tvTower(plan, x, y, z);
            case "fortress" -> labelZ = fortress(plan, x, y, z);
            case "column" -> labelZ = pillar(plan, x, y, z);
            default -> labelZ = palace(plan, city, site.half() < 1, x, y, z, random);
        }
        labelZ = Math.min(labelZ, z + (site.half() >= 1 ? 20 : 4));
        if (site.main()) {
            plan.label(x, y + 5, labelZ, city.name().toUpperCase(java.util.Locale.ROOT), 0xFFFFFF, 3.0f);
            plan.label(x, y + 3, labelZ, "Capitale : " + countryName, 0xFAC400, 1.5f);
        }
        if (!site.label().equals(city.name())) {
            plan.label(x, y + (site.main() ? 2 : 3), labelZ, site.label(), 0xE0E0E0, 1.2f);
        }
    }

    // -- Les petits monuments : une place d'un chunk, dix blocs de cote ----------------------

    /** Une cathedrale gothique : deux tours en facade, une nef, une fleche. */
    private static int cathedral(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState stone = b("stone_bricks");
        BlockState light = b("smooth_stone");
        BlockState roof = b("deepslate_tiles");
        for (int side = -1; side <= 1; side += 2) {
            int tx = x + side * 3 - (side < 0 ? 0 : 1);
            box(plan, tx - 1, y, z + 2, tx + 1, y + 15, z + 4, stone);
            box(plan, tx, y + 10, z + 4, tx, y + 13, z + 4, b("air"));
            box(plan, tx - 1, y + 16, z + 2, tx + 1, y + 16, z + 4, light);
        }
        box(plan, x - 2, y, z - 5, x + 1, y + 9, z + 3, stone);
        box(plan, x - 1, y, z - 4, x, y + 8, z + 2, b("air"));
        for (int level = 0; level < 3; level++) {
            box(plan, x - 2 + level, y + 10 + level, z - 5, x + 1 - level, y + 10 + level, z + 3, roof);
        }
        for (int pz = z - 4; pz <= z + 1; pz += 2) {
            plan.set(x - 2, y + 4, pz, b("blue_stained_glass"));
            plan.set(x + 1, y + 4, pz, b("blue_stained_glass"));
            plan.set(x - 2, y + 5, pz, b("blue_stained_glass"));
            plan.set(x + 1, y + 5, pz, b("blue_stained_glass"));
        }
        // la rosace et le portail
        box(plan, x - 1, y + 6, z + 3, x, y + 7, z + 3, b("magenta_stained_glass"));
        box(plan, x - 1, y, z + 3, x, y + 2, z + 4, b("air"));
        column(plan, x, y + 12, y + 20, z - 2, roof);
        plan.set(x, y + 21, z - 2, b("lightning_rod"));
        return z + 5;
    }

    /** Un palais en long, aux couleurs de la region. */
    private static int longPalace(CityBuilder.Plan plan, CityBuilder.City city, int x, int y, int z) {
        boolean east = "asie".equals(city.style());
        BlockState wall = b(east ? "red_terracotta" : "orient".equals(city.style()) ? "smooth_sandstone"
                : "ameriques".equals(city.style()) ? "white_concrete" : "smooth_sandstone");
        BlockState trim = b(east ? "yellow_terracotta" : "ameriques".equals(city.style()) ? "smooth_quartz"
                : "cut_sandstone");
        BlockState roof = b(east ? "yellow_terracotta" : "deepslate_tiles");
        box(plan, x - 5, y, z - 3, x + 4, y + 6, z + 1, wall);
        box(plan, x - 4, y, z - 2, x + 3, y + 5, z, b("air"));
        for (int px = x - 4; px <= x + 3; px += 2) {
            plan.set(px, y + 1, z + 1, b("glass"));
            plan.set(px, y + 4, z + 1, b("glass"));
            plan.set(px, y + 1, z - 3, b("glass"));
            plan.set(px, y + 4, z - 3, b("glass"));
        }
        box(plan, x - 5, y + 3, z - 3, x + 4, y + 3, z + 1, trim);
        box(plan, x - 4, y + 3, z - 2, x + 3, y + 3, z, b("spruce_planks"));
        box(plan, x - 5, y + 7, z - 3, x + 4, y + 7, z + 1, roof);
        box(plan, x - 4, y + 8, z - 2, x + 3, y + 8, z, roof);
        // le pavillon central et sa porte
        box(plan, x - 2, y, z + 1, x + 1, y + 8, z + 2, trim);
        box(plan, x - 1, y, z + 1, x, y + 2, z + 2, b("air"));
        box(plan, x - 2, y + 9, z, x + 1, y + 9, z + 2, roof);
        return z + 4;
    }

    /** Un arc de triomphe a une arche. */
    private static int arch(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState stone = b("smooth_sandstone");
        BlockState cut = b("cut_sandstone");
        box(plan, x - 4, y, z - 2, x + 3, y + 11, z + 1, stone);
        for (int i = -2; i <= 1; i++) {
            int height = 6 + ((i == -2 || i == 1) ? 0 : 1);
            box(plan, x + i, y, z - 2, x + i, y + height, z + 1, b("air"));
        }
        box(plan, x - 4, y + 4, z - 1, x + 3, y + 5, z, b("air"));
        box(plan, x - 4, y, z - 2, x - 3, y + 11, z + 1, stone);
        box(plan, x + 2, y, z - 2, x + 3, y + 11, z + 1, stone);
        box(plan, x - 4, y + 9, z - 2, x + 3, y + 9, z + 1, cut);
        box(plan, x - 5, y + 12, z - 3, x + 4, y + 12, z + 2, cut);
        box(plan, x - 4, y + 13, z - 2, x + 3, y + 13, z + 1, stone);
        return z + 4;
    }

    /** Une eglise a coupole ; une mosquee a minarets en Orient. */
    private static int domedChurch(CityBuilder.Plan plan, CityBuilder.City city, int x, int y, int z) {
        boolean orient = "orient".equals(city.style());
        BlockState wall = b(orient ? "smooth_sandstone" : "smooth_quartz");
        BlockState domeBlock = b(orient ? "cyan_terracotta" : "europe".equals(city.style()) ? "light_gray_concrete"
                : "smooth_quartz");
        box(plan, x - 4, y, z - 4, x + 3, y + 6, z + 3, wall);
        box(plan, x - 3, y, z - 3, x + 2, y + 5, z + 2, b("air"));
        for (int i = -3; i <= 2; i += 2) {
            plan.set(x + i, y + 3, z + 3, b("glass"));
            plan.set(x + i, y + 3, z - 4, b("glass"));
            plan.set(x - 4, y + 3, z + i, b("glass"));
            plan.set(x + 3, y + 3, z + i, b("glass"));
        }
        for (int i = -3; i <= 2; i++) {
            for (int j = -3; j <= 2; j++) {
                double d = Math.hypot(i + 0.5d, j + 0.5d);
                if (d <= 3.6d && d > 2.4d) {
                    column(plan, x + i, y + 7, y + 9, z + j, wall);
                }
            }
        }
        for (int dy = 0; dy <= 4; dy++) {
            double r = Math.sqrt(Math.max(0.0d, 3.6d * 3.6d - dy * dy * 0.9d));
            for (int i = -4; i <= 3; i++) {
                for (int j = -4; j <= 3; j++) {
                    if (Math.hypot(i + 0.5d, j + 0.5d) <= r) {
                        plan.set(x + i, y + 10 + dy, z + j, domeBlock);
                    }
                }
            }
        }
        column(plan, x, y + 15, y + 16, z, wall);
        plan.set(x, y + 17, z, b("gold_block"));
        box(plan, x - 1, y, z + 3, x, y + 2, z + 3, b("air"));
        if (orient) {
            for (int side = -1; side <= 1; side += 2) {
                int mx = side < 0 ? x - 5 : x + 4;
                column(plan, mx, y, y + 16, z + 4, wall);
                plan.set(mx, y + 17, z + 4, domeBlock);
            }
        }
        return z + 5;
    }

    /** Une grande roue. */
    private static int wheel(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState steel = b("white_concrete");
        BlockState spoke = b("iron_bars");
        int cy = y + 14;
        for (int i = -12; i <= 12; i++) {
            for (int j = -12; j <= 12; j++) {
                double d = Math.hypot(i, j);
                if (d <= 11.6d && d >= 10.6d) {
                    plan.set(x + i / 1, cy + j, z, steel);
                } else if (d < 10.6d && (i == 0 || j == 0 || Math.abs(i) == Math.abs(j))) {
                    plan.set(x + i, cy + j, z, spoke);
                }
            }
        }
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8.0d;
            int i = (int) Math.round(Math.cos(a) * 11.6d);
            int j = (int) Math.round(Math.sin(a) * 11.6d);
            plan.set(x + i, cy + j, z + 1, b("light_blue_stained_glass"));
        }
        for (int dy = 0; dy < 14; dy++) {
            plan.set(x - 3 + dy / 5, y + dy, z - 1, steel);
            plan.set(x + 3 - dy / 5, y + dy, z - 1, steel);
        }
        plan.set(x, cy, z - 1, steel);
        return z + 4;
    }

    /** Un donjon : une tour carree a creneaux, quatre tourelles. */
    private static int keep(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState stone = b("stone_bricks");
        BlockState pale = b("smooth_stone");
        box(plan, x - 4, y, z - 4, x + 3, y + 9, z + 3, stone);
        box(plan, x - 3, y + 1, z - 3, x + 2, y + 9, z + 2, b("air"));
        box(plan, x - 3, y + 5, z - 3, x + 2, y + 5, z + 2, b("spruce_planks"));
        for (int i = -4; i <= 3; i++) {
            if (Math.floorMod(i, 2) == 0) {
                plan.set(x + i, y + 10, z - 4, stone);
                plan.set(x + i, y + 10, z + 3, stone);
                plan.set(x - 4, y + 10, z + i, stone);
                plan.set(x + 3, y + 10, z + i, stone);
            }
        }
        int[][] corners = {{-5, -5}, {4, -5}, {-5, 4}, {4, 4}};
        for (int[] corner : corners) {
            column(plan, x + corner[0], y, y + 12, z + corner[1], pale);
            plan.set(x + corner[0], y + 13, z + corner[1], b("deepslate_tiles"));
        }
        box(plan, x - 1, y, z + 3, x, y + 2, z + 3, b("air"));
        return z + 5;
    }

    /** Un temple a colonnes et fronton. */
    private static int temple(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState marble = b("smooth_quartz");
        BlockState pillar = b("quartz_pillar");
        box(plan, x - 5, y, z - 4, x + 4, y, z + 3, marble);
        for (int px = x - 5; px <= x + 4; px += 3) {
            column(plan, px, y + 1, y + 6, z + 3, pillar);
            column(plan, px, y + 1, y + 6, z - 4, pillar);
        }
        column(plan, x - 5, y + 1, y + 6, z, pillar);
        column(plan, x + 4, y + 1, y + 6, z, pillar);
        box(plan, x - 3, y + 1, z - 2, x + 2, y + 6, z + 1, marble);
        box(plan, x - 2, y + 1, z - 1, x + 1, y + 5, z + 1, b("air"));
        box(plan, x - 5, y + 7, z - 4, x + 4, y + 7, z + 3, marble);
        for (int level = 0; level < 3; level++) {
            box(plan, x - 5 + level * 2, y + 8 + level, z - 4, x + 4 - level * 2, y + 8 + level, z + 3, marble);
        }
        return z + 5;
    }

    /** Un temple rond a trois toits. */
    private static int roundTemple(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState white = b("smooth_quartz");
        BlockState red = b("red_terracotta");
        BlockState blue = b("blue_terracotta");
        disc(plan, x, y, z, 4.8d, white);
        disc(plan, x, y + 1, z, 4.0d, white);
        double[] radii = {3.4d, 2.6d, 1.8d};
        int level = y + 2;
        for (double radius : radii) {
            for (int dy = 0; dy < 3; dy++) {
                for (int i = -4; i <= 4; i++) {
                    for (int j = -4; j <= 4; j++) {
                        double d = Math.hypot(i, j);
                        if (d <= radius && d > radius - 1.1d) {
                            plan.set(x + i, level + dy, z + j, red);
                        }
                    }
                }
            }
            disc(plan, x, level + 3, z, radius + 1.2d, blue);
            level += 4;
        }
        plan.set(x, level, z, b("gold_block"));
        return z + 5;
    }

    /** Une tour de television : un fut, une boule, une antenne. */
    private static int tvTower(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState shaft = b("light_gray_concrete");
        for (int dy = 0; dy < 34; dy++) {
            double r = dy < 6 ? 2.2d - dy * 0.2d : 1.0d;
            disc(plan, x, y + dy, z, r, shaft);
        }
        for (int dy = -3; dy <= 3; dy++) {
            double r = Math.sqrt(Math.max(0.0d, 12.5d - dy * dy));
            disc(plan, x, y + 37 + dy, z, r, dy == 0 ? b("light_blue_stained_glass") : b("white_concrete"));
        }
        column(plan, x, y + 41, y + 52, z, b("iron_bars"));
        column(plan, x, y + 41, y + 44, z, b("red_concrete"));
        plan.set(x, y + 53, z, b("lightning_rod"));
        return z + 4;
    }

    /** Une enceinte de brique a creneaux, une tour a fleche au milieu de la courtine. */
    private static int fortress(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState brick = b("bricks");
        BlockState dark = b("red_nether_bricks");
        for (int i = -5; i <= 4; i++) {
            for (int dy = 0; dy < 6; dy++) {
                plan.set(x + i, y + dy, z - 5, brick);
                plan.set(x + i, y + dy, z + 4, brick);
                plan.set(x - 5, y + dy, z + i, brick);
                plan.set(x + 4, y + dy, z + i, brick);
            }
            if (Math.floorMod(i, 2) == 0) {
                plan.set(x + i, y + 6, z - 5, brick);
                plan.set(x + i, y + 6, z + 4, brick);
                plan.set(x - 5, y + 6, z + i, brick);
                plan.set(x + 4, y + 6, z + i, brick);
            }
        }
        box(plan, x - 1, y, z + 4, x, y + 3, z + 4, b("air"));
        box(plan, x - 2, y, z + 3, x + 1, y + 13, z + 4, brick);
        box(plan, x - 1, y, z + 3, x, y + 3, z + 4, b("air"));
        for (int level = 0; level < 5; level++) {
            int half = level < 2 ? 1 : 0;
            box(plan, x - 1 - half + (half == 0 ? 1 : 0), y + 14 + level, z + 3, x + half, y + 14 + level, z + 4,
                    b("green_terracotta"));
        }
        plan.set(x, y + 19, z + 3, b("gold_block"));
        box(plan, x - 3, y, z - 3, x + 2, y + 4, z - 1, dark);
        box(plan, x - 2, y + 5, z - 3, x + 1, y + 5, z - 1, b("gold_block"));
        return z + 6;
    }

    /** Une colonne sur son socle, une statue en haut. */
    private static int pillar(CityBuilder.Plan plan, int x, int y, int z) {
        box(plan, x - 2, y, z - 2, x + 1, y + 1, z + 1, b("smooth_stone"));
        box(plan, x - 1, y + 2, z - 1, x, y + 3, z, b("polished_andesite"));
        column(plan, x, y + 4, y + 17, z, b("quartz_pillar"));
        column(plan, x - 1, y + 4, y + 17, z - 1, b("quartz_pillar"));
        column(plan, x, y + 4, y + 17, z - 1, b("quartz_pillar"));
        column(plan, x - 1, y + 4, y + 17, z, b("quartz_pillar"));
        box(plan, x - 2, y + 18, z - 2, x + 1, y + 18, z + 1, b("smooth_quartz"));
        column(plan, x, y + 19, y + 21, z, b("oxidized_copper"));
        return z + 4;
    }

    // -- Les outils -------------------------------------------------------------------------

    private static void box(CityBuilder.Plan plan, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    plan.set(x, y, z, state);
                }
            }
        }
    }

    private static void column(CityBuilder.Plan plan, int x, int y0, int y1, int z, BlockState state) {
        for (int y = y0; y <= y1; y++) {
            plan.set(x, y, z, state);
        }
    }

    /** Une demi-sphere pleine. */
    private static void dome(CityBuilder.Plan plan, int cx, int y, int cz, double radius, BlockState state) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= r; dy++) {
                    if (dx * dx + dz * dz + dy * dy <= radius * radius) {
                        plan.set(cx + dx, y + dy, cz + dz, state);
                    }
                }
            }
        }
    }

    /** Un disque plein. */
    private static void disc(CityBuilder.Plan plan, int cx, int y, int cz, double radius, BlockState state) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    plan.set(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /** Une pyramide pleine a degres d'un bloc ; base de cote {@code size}, impair. */
    private static int pyramid(CityBuilder.Plan plan, int cx, int y, int cz, int size, BlockState a, BlockState b) {
        int half = size / 2;
        int level = 0;
        for (int h = half; h >= 0; h--, level++) {
            box(plan, cx - h, y + level, cz - h, cx + h, y + level, cz + h, level % 3 == 2 ? b : a);
        }
        return y + level;
    }

    /** Un bulbe : renfle puis effile, de couleurs en spirale. */
    private static void onion(CityBuilder.Plan plan, int cx, int y, int cz, double radius, BlockState a,
                              BlockState b) {
        int height = (int) Math.round(radius * 2.6d);
        for (int dy = 0; dy <= height; dy++) {
            double t = (double) dy / height;
            double r = radius * Math.sin(Math.PI * Math.min(1.0d, t * 1.25d)) * (1.0d - 0.15d * t);
            if (t > 0.8d) {
                r = radius * 0.35d * (1.0d - (t - 0.8d) / 0.2d);
            }
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz <= r * r + 0.5d) {
                        double angle = Math.atan2(dz, dx) + dy * 0.45d;
                        boolean stripe = Math.floorMod((int) Math.floor(angle / (Math.PI / 3)), 2) == 0;
                        plan.set(cx + dx, y + dy, cz + dz, stripe ? a : b);
                    }
                }
            }
        }
        column(plan, cx, y + height + 1, y + height + 3, cz, b("gold_block"));
    }

    // -- Paris : une tour en treillis --------------------------------------------------------

    private static int latticeTower(CityBuilder.Plan plan, int x, int y, int z, String main, String second) {
        BlockState iron = b(main);
        BlockState band = b(second);
        BlockState lattice = b("iron_bars");
        BlockState deck = b("dark_oak_planks");
        BlockState rail = b("dark_oak_fence");
        int legTop = 60;
        for (int dy = 0; dy <= legTop; dy++) {
            double t = (double) dy / legTop;
            double offset = 2.0d + 15.0d * Math.pow(1.0d - t, 1.7d);
            int o = (int) Math.round(offset);
            int thick = dy < 16 ? 2 : dy < 36 ? 1 : 0;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    for (int a = 0; a <= thick; a++) {
                        for (int c = 0; c <= thick; c++) {
                            plan.set(x + sx * (o - a), y + dy, z + sz * (o - c), dy / 8 % 2 == 0 ? iron : band);
                        }
                    }
                }
            }
            // le treillis des faces, un rang sur trois
            if (dy % 3 == 0 && dy > 18) {
                for (int i = -o + 1; i < o; i++) {
                    if ((i + dy) % 2 == 0) {
                        plan.set(x + i, y + dy, z - o, lattice);
                        plan.set(x + i, y + dy, z + o, lattice);
                        plan.set(x - o, y + dy, z + i, lattice);
                        plan.set(x + o, y + dy, z + i, lattice);
                    }
                }
            }
        }
        // les quatre arches du bas
        int base = 17;
        for (int i = -base + 3; i <= base - 3; i++) {
            double u = (double) i / (base - 3);
            int h = 9 + (int) Math.round(4.0d * (1.0d - u * u));
            int o = (int) Math.round(2.0d + 15.0d * Math.pow(1.0d - (double) h / legTop, 1.7d));
            plan.set(x + i, y + h, z - o, iron);
            plan.set(x + i, y + h, z + o, iron);
            plan.set(x - o, y + h, z + i, iron);
            plan.set(x + o, y + h, z + i, iron);
        }
        // les plateformes
        int[][] decks = {{18, 0}, {38, 0}};
        for (int[] level : decks) {
            int dy = level[0];
            int o = (int) Math.round(2.0d + 15.0d * Math.pow(1.0d - (double) dy / legTop, 1.7d)) + 1;
            for (int i = -o; i <= o; i++) {
                for (int j = -o; j <= o; j++) {
                    boolean ring = Math.max(Math.abs(i), Math.abs(j)) >= o - 2;
                    if (ring) {
                        plan.set(x + i, y + dy, z + j, deck);
                        if (Math.max(Math.abs(i), Math.abs(j)) == o) {
                            plan.set(x + i, y + dy + 1, z + j, rail);
                        }
                    }
                }
            }
        }
        box(plan, x - 2, y + legTop, z - 2, x + 2, y + legTop, z + 2, deck);
        box(plan, x - 1, y + legTop + 1, z - 1, x + 1, y + legTop + 3, z + 1, iron);
        column(plan, x, y + legTop + 4, y + legTop + 12, z, lattice);
        plan.set(x, y + legTop + 13, z, b("lightning_rod"));
        // l'escalier : une echelle dans un pied, jusqu'au premier etage
        for (int dy = 0; dy < 18; dy++) {
            plan.set(x - 16 + dy / 3, y + dy, z - 15, with("ladder", "facing", "south"));
        }
        return z + 22;
    }

    // -- Londres : la tour de l'horloge et le palais ----------------------------------------

    private static int clockTower(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState stone = b("smooth_sandstone");
        BlockState band = b("cut_sandstone");
        BlockState dark = b("deepslate_tiles");
        BlockState face = b("white_concrete");
        BlockState hand = b("black_concrete");
        BlockState gold = b("gold_block");
        int tx = x + 14;
        int tz = z - 6;
        for (int dy = 0; dy < 30; dy++) {
            for (int i = -3; i <= 3; i++) {
                for (int j = -3; j <= 3; j++) {
                    boolean side = Math.abs(i) == 3 || Math.abs(j) == 3;
                    if (!side) {
                        continue;
                    }
                    boolean rib = Math.abs(i) == 3 && Math.abs(j) == 3 || i == 0 || j == 0;
                    plan.set(tx + i, y + dy, tz + j, dy % 6 == 5 ? band : rib ? band : stone);
                }
            }
        }
        // l'horloge sur les quatre faces
        int cy = y + 24;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (Math.abs(i) + Math.abs(j) <= 3) {
                    BlockState s = (i == 0 && j >= 0) || (j == 0 && i >= 0 && i <= 1) ? hand : face;
                    plan.set(tx + i, cy + j, tz - 4, s);
                    plan.set(tx + i, cy + j, tz + 4, s);
                    plan.set(tx - 4, cy + j, tz + i, s);
                    plan.set(tx + 4, cy + j, tz + i, s);
                }
            }
        }
        // le beffroi et la fleche
        for (int dy = 30; dy < 34; dy++) {
            for (int i = -3; i <= 3; i += 6) {
                for (int j = -3; j <= 3; j += 6) {
                    plan.set(tx + i, y + dy, tz + j, band);
                }
            }
        }
        box(plan, tx - 3, y + 34, tz - 3, tx + 3, y + 34, tz + 3, band);
        int top = pyramid(plan, tx, y + 35, tz, 7, dark, dark);
        column(plan, tx, top, top + 5, tz, dark);
        plan.set(tx, top + 6, tz, gold);
        // le palais, en long, a l'ouest de la tour
        int px0 = x - 20;
        int px1 = x + 9;
        int pz0 = z - 10;
        int pz1 = z - 2;
        for (int px = px0; px <= px1; px++) {
            for (int pz = pz0; pz <= pz1; pz++) {
                boolean side = px == px0 || px == px1 || pz == pz0 || pz == pz1;
                for (int dy = 0; dy < 12; dy++) {
                    if (side) {
                        boolean window = dy % 4 >= 1 && dy % 4 <= 2 && (px + pz) % 2 == 0 && px != px0 && px != px1;
                        plan.set(px, y + dy, pz, window ? b("glass") : (px - px0) % 4 == 0 ? band : stone);
                    } else if (dy % 4 == 3) {
                        plan.set(px, y + dy, pz, b("spruce_planks"));
                    }
                }
                plan.set(px, y + 12, pz, dark);
                if (side && (px - px0) % 4 == 0) {
                    column(plan, px, y + 13, y + 15, pz, band);
                }
            }
        }
        return z + 14;
    }

    // -- Rome : l'amphitheatre ---------------------------------------------------------------

    private static int amphitheatre(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState stone = b("smooth_sandstone");
        BlockState dark = b("sandstone");
        BlockState seat = b("stone_bricks");
        double a = 20.5d;
        double c = 16.5d;
        for (int dx = -21; dx <= 21; dx++) {
            for (int dz = -17; dz <= 17; dz++) {
                double e = (dx * dx) / (a * a) + (dz * dz) / (c * c);
                if (e > 1.0d) {
                    continue;
                }
                double inner = (dx * dx) / ((a - 3.2d) * (a - 3.2d)) + (dz * dz) / ((c - 3.2d) * (c - 3.2d));
                double angle = Math.atan2(dz, dx);
                // le quart sud-est a perdu son dernier etage
                boolean ruined = angle > 0.2d && angle < 1.5d;
                int height = ruined ? 12 : 19;
                if (inner >= 1.0d) {
                    // le mur exterieur : trois rangs d'arcades
                    boolean outer = (dx * dx) / ((a - 1.0d) * (a - 1.0d)) + (dz * dz) / ((c - 1.0d) * (c - 1.0d))
                            >= 1.0d;
                    int arc = (int) Math.floor((angle + Math.PI) * 18.0d / Math.PI);
                    for (int dy = 0; dy <= height; dy++) {
                        int tier = dy % 6;
                        boolean opening = outer && arc % 2 == 0 && tier >= 1 && tier <= 4 && dy < 18;
                        if (!opening) {
                            plan.set(x + dx, y + dy, z + dz, tier == 0 ? dark : stone);
                        }
                    }
                    if (ruined) {
                        plan.set(x + dx, y + height + 1, z + dz, b("sandstone_slab"));
                    }
                } else {
                    // les gradins vers l'arene
                    double depth = Math.sqrt(inner);
                    int step = (int) Math.floor((depth - 0.55d) * 18.0d);
                    if (depth < 0.55d) {
                        plan.set(x + dx, y - 1, z + dz, b("sand"));
                    } else {
                        box(plan, x + dx, y, z + dz, x + dx, y + Math.max(0, step), z + dz, seat);
                    }
                }
            }
        }
        return z + 21;
    }

    // -- Moscou : la cathedrale a bulbes ----------------------------------------------------

    private static int onionDomes(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState brick = b("bricks");
        BlockState white = b("white_concrete");
        // le corps central : une tour octogonale a toit en tente
        for (int dy = 0; dy < 22; dy++) {
            for (int i = -4; i <= 4; i++) {
                for (int j = -4; j <= 4; j++) {
                    if (Math.abs(i) + Math.abs(j) <= 6) {
                        boolean side = Math.abs(i) == 4 || Math.abs(j) == 4 || Math.abs(i) + Math.abs(j) == 6;
                        if (side || dy == 0) {
                            plan.set(x + i, y + dy, z + j, dy % 7 == 6 ? white : brick);
                        }
                    }
                }
            }
        }
        for (int dy = 0; dy < 10; dy++) {
            double r = 4.0d * (1.0d - dy / 10.0d);
            disc(plan, x, y + 22 + dy, z, Math.max(0.5d, r), dy % 2 == 0 ? b("green_concrete") : white);
        }
        onion(plan, x, y + 32, z, 2.4d, b("yellow_concrete"), b("gold_block"));
        // quatre tours a bulbe autour
        String[][] colours = {{"red_concrete", "lime_concrete"}, {"blue_concrete", "white_concrete"},
                {"yellow_concrete", "green_concrete"}, {"orange_concrete", "cyan_concrete"}};
        int[][] spots = {{-9, -9}, {9, -9}, {-9, 9}, {9, 9}};
        for (int k = 0; k < 4; k++) {
            int tx = x + spots[k][0];
            int tz = z + spots[k][1];
            for (int dy = 0; dy < 18; dy++) {
                for (int i = -2; i <= 2; i++) {
                    for (int j = -2; j <= 2; j++) {
                        if (Math.abs(i) == 2 || Math.abs(j) == 2 || dy == 0) {
                            plan.set(tx + i, y + dy, tz + j, dy % 5 == 4 ? white : brick);
                        }
                    }
                }
            }
            box(plan, tx - 2, y + 18, tz - 2, tx + 2, y + 18, tz + 2, white);
            onion(plan, tx, y + 19, tz, 3.2d, b(colours[k][0]), b(colours[k][1]));
        }
        // les galeries qui relient les tours
        for (int i = -9; i <= 9; i++) {
            for (int dy = 0; dy < 6; dy++) {
                BlockState s = dy == 5 ? white : brick;
                plan.set(x + i, y + dy, z - 9, s);
                plan.set(x + i, y + dy, z + 9, s);
                plan.set(x - 9, y + dy, z + i, s);
                plan.set(x + 9, y + dy, z + i, s);
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            plan.set(x, y + dy, z + 9, b("air"));
            plan.set(x, y + dy, z + 4, b("air"));
        }
        return z + 18;
    }

    // -- Washington : l'obelisque et le capitole --------------------------------------------

    private static int obeliskCapitol(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState marble = b("quartz_block");
        BlockState pillar = b("quartz_pillar");
        BlockState white = b("smooth_quartz");
        int ox = x;
        int oz = z - 13;
        box(plan, ox - 3, y, oz - 3, ox + 3, y, oz + 3, b("smooth_stone"));
        box(plan, ox - 2, y + 1, oz - 2, ox + 2, y + 40, oz + 2, marble);
        int top = pyramid(plan, ox, y + 41, oz, 5, white, white);
        plan.set(ox, top, oz, b("lightning_rod"));
        // le capitole : une aile, un portique, un dome sur son tambour
        int cz0 = z + 4;
        int cz1 = z + 14;
        box(plan, x - 18, y, cz0, x + 18, y + 9, cz1, white);
        for (int px = x - 18; px <= x + 18; px++) {
            for (int dy = 2; dy <= 7; dy += 4) {
                if ((px - x) % 2 == 0) {
                    plan.set(px, y + dy, cz0, b("glass"));
                    plan.set(px, y + dy, cz1, b("glass"));
                }
            }
        }
        box(plan, x - 17, y + 10, cz0 + 1, x + 17, y + 10, cz1 - 1, b("smooth_quartz_slab"));
        for (int px = x - 6; px <= x + 6; px += 2) {
            column(plan, px, y, y + 9, cz0 - 3, pillar);
        }
        box(plan, x - 7, y + 10, cz0 - 3, x + 7, y + 10, cz0, white);
        box(plan, x - 7, y - 0, cz0 - 3, x + 7, y - 0, cz0 - 1, b("smooth_stone"));
        for (int dy = 10; dy < 16; dy++) {
            for (int i = -5; i <= 5; i++) {
                for (int j = -5; j <= 5; j++) {
                    double d = Math.sqrt(i * i + j * j);
                    if (d <= 5.2d && d > 4.0d) {
                        plan.set(x + i, y + dy, z + 9 + j, (i + j) % 2 == 0 ? pillar : white);
                    }
                }
            }
        }
        dome(plan, x, y + 16, z + 9, 5.2d, white);
        column(plan, x, y + 22, y + 24, z + 9, white);
        plan.set(x, y + 25, z + 9, b("gold_block"));
        for (int dy = 1; dy <= 3; dy++) {
            plan.set(x, y + dy, cz0, b("air"));
        }
        return z - 3;
    }

    // -- Pekin : la porte imperiale ----------------------------------------------------------

    private static int imperialGate(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState red = b("red_terracotta");
        BlockState wall = b("red_concrete");
        BlockState roof = b("yellow_terracotta");
        BlockState ridge = b("gold_block");
        BlockState beam = b("dark_oak_planks");
        BlockState base = b("smooth_stone");
        // le socle aux cinq portes
        int z0 = z - 6;
        int z1 = z + 4;
        box(plan, x - 20, y, z0, x + 20, y + 9, z1, wall);
        int[] gates = {-12, -6, 0, 6, 12};
        for (int g : gates) {
            int half = g == 0 ? 2 : 1;
            int height = g == 0 ? 6 : 4;
            box(plan, x + g - half, y, z0, x + g + half, y + height, z1, b("air"));
            for (int i = -half; i <= half; i++) {
                plan.set(x + g + i, y + height + 1, z0, base);
            }
        }
        box(plan, x - 20, y + 10, z0, x + 20, y + 10, z1, base);
        for (int px = x - 20; px <= x + 20; px += 2) {
            plan.set(px, y + 11, z0, b("stone_brick_wall"));
            plan.set(px, y + 11, z1, b("stone_brick_wall"));
        }
        // la salle : colonnes rouges, deux toits a etages
        int hz0 = z0 + 2;
        int hz1 = z1 - 2;
        for (int px = x - 13; px <= x + 13; px += 2) {
            column(plan, px, y + 11, y + 16, hz0, red);
            column(plan, px, y + 11, y + 16, hz1, red);
        }
        box(plan, x - 13, y + 17, hz0, x + 13, y + 17, hz1, beam);
        roofTier(plan, x, y + 18, (hz0 + hz1) / 2, 16, 5, roof, ridge);
        box(plan, x - 10, y + 19, hz0 + 1, x + 10, y + 21, hz1 - 1, red);
        roofTier(plan, x, y + 22, (hz0 + hz1) / 2, 12, 4, roof, ridge);
        // les lions de pierre et les mats
        column(plan, x - 7, y, y + 2, z1 + 6, b("polished_andesite"));
        column(plan, x + 7, y, y + 2, z1 + 6, b("polished_andesite"));
        return z1 + 12;
    }

    /** Un toit chinois en gradins : chaque rang rentre d'un bloc, avancee aux angles. */
    private static void roofTier(CityBuilder.Plan plan, int x, int y, int z, int halfW, int halfD, BlockState roof,
                                 BlockState ridge) {
        int level = 0;
        while (halfD - level >= 0) {
            for (int i = -(halfW - level); i <= halfW - level; i++) {
                for (int j = -(halfD - level); j <= halfD - level; j++) {
                    boolean rim = Math.abs(i) == halfW - level || Math.abs(j) == halfD - level;
                    if (rim) {
                        plan.set(x + i, y + level, z + j, halfD - level == 0 ? ridge : roof);
                    }
                }
            }
            // les angles se relevent
            if (level == 0) {
                plan.set(x - halfW, y + 1, z - halfD, roof);
                plan.set(x + halfW, y + 1, z - halfD, roof);
                plan.set(x - halfW, y + 1, z + halfD, roof);
                plan.set(x + halfW, y + 1, z + halfD, roof);
            }
            level++;
        }
    }

    // -- Le Caire : les pyramides ------------------------------------------------------------

    private static int pyramids(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState sand = b("smooth_sandstone");
        BlockState band = b("sandstone");
        int top = pyramid(plan, x - 6, y, z - 6, 29, sand, band);
        plan.set(x - 6, top - 1, z - 6, b("gold_block"));
        pyramid(plan, x + 12, y, z + 8, 17, sand, band);
        pyramid(plan, x - 13, y, z + 14, 11, sand, band);
        // une entree dans la grande
        box(plan, x - 6, y + 4, z - 6 + 14, x - 6, y + 5, z - 6 + 10, b("air"));
        return z + 21;
    }

    // -- Berlin : la porte a colonnes --------------------------------------------------------

    private static int columnGate(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState stone = b("smooth_sandstone");
        BlockState pillar = b("quartz_pillar");
        BlockState green = b("oxidized_copper");
        box(plan, x - 15, y, z - 4, x + 15, y, z + 4, b("smooth_stone"));
        for (int px = x - 13; px <= x + 13; px += 5) {
            for (int pz = z - 3; pz <= z + 3; pz += 6) {
                box(plan, px - 1, y + 1, pz, px, y + 13, pz, pillar);
            }
        }
        box(plan, x - 15, y + 14, z - 4, x + 15, y + 16, z + 4, stone);
        box(plan, x - 8, y + 17, z - 3, x + 8, y + 18, z + 3, stone);
        // le quadrige : quatre chevaux et un char, en cuivre vert
        for (int k = -3; k <= 3; k += 2) {
            box(plan, x + k, y + 19, z - 1, x + k, y + 20, z + 1, green);
            plan.set(x + k, y + 21, z - 1, green);
        }
        box(plan, x - 1, y + 19, z + 2, x + 1, y + 21, z + 2, green);
        column(plan, x, y + 22, y + 24, z + 2, green);
        // les pavillons de garde
        for (int side = -1; side <= 1; side += 2) {
            int px = x + side * 20;
            box(plan, px - 3, y, z - 4, px + 3, y + 8, z + 4, stone);
            box(plan, px - 2, y + 1, z - 4, px + 2, y + 6, z - 4, b("air"));
            for (int i = -2; i <= 2; i += 2) {
                column(plan, px + i, y + 1, y + 6, z - 4, pillar);
            }
            box(plan, px - 3, y + 9, z - 4, px + 3, y + 9, z + 4, b("smooth_sandstone_slab"));
        }
        return z + 12;
    }

    // -- Athenes : le temple sur son rocher --------------------------------------------------

    private static int acropolis(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState rock = b("stone");
        BlockState rough = b("andesite");
        BlockState marble = b("smooth_quartz");
        BlockState pillar = b("quartz_pillar");
        // le rocher, en talus
        for (int dx = -21; dx <= 21; dx++) {
            for (int dz = -17; dz <= 15; dz++) {
                double e = Math.max(Math.abs(dx) / 21.0d, Math.abs(dz + 1) / 16.0d);
                int h = (int) Math.round(7.0d * Math.min(1.0d, (1.0d - e) * 4.0d));
                if (h > 0) {
                    box(plan, x + dx, y, z + dz, x + dx, y + h - 1, z + dz, (dx + dz) % 3 == 0 ? rough : rock);
                }
            }
        }
        // la rampe d'acces, au sud
        for (int i = 0; i < 8; i++) {
            box(plan, x - 1, y, z + 16 - i, x + 1, y + i, z + 16 - i, b("stone_bricks"));
        }
        int ty = y + 7;
        box(plan, x - 13, ty, z - 7, x + 13, ty, z + 7, marble);
        Random random = new Random(1838L);
        for (int px = x - 12; px <= x + 12; px += 2) {
            for (int pz = z - 6; pz <= z + 6; pz += 2) {
                boolean edge = px == x - 12 || px == x + 12 || pz == z - 6 || pz == z + 6;
                if (!edge) {
                    continue;
                }
                // quelques colonnes sont tombees
                int height = random.nextInt(7) == 0 ? 2 + random.nextInt(3) : 9;
                column(plan, px, ty + 1, ty + height, pz, pillar);
            }
        }
        // l'entablement et ce qui reste du fronton, cote ouest seulement
        box(plan, x - 13, ty + 10, z - 7, x - 1, ty + 10, z + 7, marble);
        box(plan, x + 6, ty + 10, z - 7, x + 13, ty + 10, z - 5, marble);
        for (int level = 0; level < 4; level++) {
            box(plan, x - 13, ty + 11 + level, z - 6 + level * 2, x - 13, ty + 11 + level, z + 6 - level * 2, marble);
        }
        // la cella, en partie
        box(plan, x - 7, ty + 1, z - 3, x + 3, ty + 6, z - 3, marble);
        box(plan, x - 7, ty + 1, z + 3, x - 2, ty + 7, z + 3, marble);
        return z + 22;
    }

    // -- New Delhi : l'arc de triomphe -------------------------------------------------------

    private static int triumphArch(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState red = b("red_sandstone");
        BlockState smooth = b("smooth_red_sandstone");
        BlockState cut = b("cut_red_sandstone");
        box(plan, x - 9, y, z - 3, x + 9, y + 22, z + 3, smooth);
        // l'arche : une ouverture en plein cintre
        for (int i = -3; i <= 3; i++) {
            int height = 12 + (int) Math.round(Math.sqrt(9.0d - i * i) * 1.3d);
            box(plan, x + i, y, z - 3, x + i, y + height, z + 3, b("air"));
        }
        for (int dy = 0; dy <= 22; dy += 5) {
            box(plan, x - 9, y + dy, z - 3, x - 4, y + dy, z - 3, cut);
            box(plan, x + 4, y + dy, z - 3, x + 9, y + dy, z - 3, cut);
        }
        box(plan, x - 10, y + 22, z - 4, x + 10, y + 23, z + 4, red);
        box(plan, x - 7, y + 24, z - 3, x + 7, y + 26, z + 3, smooth);
        dome(plan, x, y + 27, z, 3.0d, cut);
        // la flamme et l'allee
        for (int i = 6; i < 20; i++) {
            plan.set(x - 2, y - 1, z + i, b("smooth_stone"));
            plan.set(x + 2, y - 1, z + i, b("smooth_stone"));
        }
        box(plan, x - 1, y, z + 9, x + 1, y, z + 11, b("blackstone"));
        plan.set(x, y + 1, z + 10, b("campfire"));
        return z + 16;
    }

    // -- Tokyo : la pagode -------------------------------------------------------------------

    private static int pagoda(CityBuilder.Plan plan, int x, int y, int z) {
        BlockState red = b("red_concrete");
        BlockState white = b("white_concrete");
        BlockState roof = b("deepslate_tiles");
        BlockState eave = b("deepslate_tile_slab");
        box(plan, x - 6, y, z - 6, x + 6, y + 1, z + 6, b("stone_bricks"));
        int level = y + 2;
        int[] halves = {4, 4, 3, 3, 2};
        for (int storey = 0; storey < 5; storey++) {
            int h = halves[storey];
            for (int dy = 0; dy < 3; dy++) {
                for (int i = -h; i <= h; i++) {
                    for (int j = -h; j <= h; j++) {
                        boolean side = Math.abs(i) == h || Math.abs(j) == h;
                        if (side) {
                            boolean post = Math.abs(i) == h && Math.abs(j) == h;
                            plan.set(x + i, level + dy, z + j, post ? red : dy == 1 && (i + j) % 2 == 0 ? red : white);
                        }
                    }
                }
            }
            // l'avancee du toit : deux rangs, le bord releve
            int e = h + 2;
            box(plan, x - e, level + 3, z - e, x + e, level + 3, z + e, roof);
            for (int i = -e; i <= e; i++) {
                plan.set(x + i, level + 4, z - e, eave);
                plan.set(x + i, level + 4, z + e, eave);
                plan.set(x - e, level + 4, z + i, eave);
                plan.set(x + e, level + 4, z + i, eave);
            }
            box(plan, x - h, level + 4, z - h, x + h, level + 4, z + h, roof);
            level += 5;
        }
        column(plan, x, level, level + 7, z, b("gold_block"));
        for (int dy = 1; dy <= 6; dy += 2) {
            disc(plan, x, level + dy, z, 1.0d, b("iron_bars"));
            plan.set(x, level + dy, z, b("gold_block"));
        }
        // un portique rouge devant
        int tz = z + 15;
        column(plan, x - 4, y, y + 7, tz, red);
        column(plan, x + 4, y, y + 7, tz, red);
        box(plan, x - 6, y + 8, tz, x + 6, y + 8, tz, b("black_concrete"));
        box(plan, x - 5, y + 6, tz, x + 5, y + 6, tz, red);
        box(plan, x - 6, y + 9, tz, x - 6, y + 9, tz, b("black_concrete"));
        box(plan, x + 6, y + 9, tz, x + 6, y + 9, tz, b("black_concrete"));
        return z + 19;
    }

    // -- Les autres : le palais national -----------------------------------------------------

    private static int palace(CityBuilder.Plan plan, CityBuilder.City city, boolean small, int x, int y, int z,
                              Random random) {
        String style = city.style();
        BlockState wall = b(switch (style) {
            case "orient" -> "smooth_sandstone";
            case "afrique" -> "white_terracotta";
            case "asie" -> "white_concrete";
            case "latam" -> "white_concrete";
            case "ameriques" -> "quartz_block";
            default -> "smooth_quartz";
        });
        BlockState trim = b(switch (style) {
            case "orient" -> "cut_sandstone";
            case "afrique" -> "brown_terracotta";
            case "asie" -> "red_terracotta";
            case "latam" -> "yellow_terracotta";
            default -> "polished_andesite";
        });
        BlockState domeBlock = b(switch (style) {
            case "orient" -> "cyan_terracotta";
            case "afrique" -> "orange_terracotta";
            case "asie" -> "deepslate_tiles";
            case "latam" -> "light_blue_terracotta";
            default -> "oxidized_copper";
        });
        int halfW = small ? 6 : 13;
        int halfD = small ? 3 : 5;
        int pz = z - (small ? 2 : 6);
        int height = small ? 6 : 10;
        for (int i = -halfW; i <= halfW; i++) {
            for (int j = -halfD; j <= halfD; j++) {
                boolean side = Math.abs(i) == halfW || Math.abs(j) == halfD;
                for (int dy = 0; dy < height; dy++) {
                    if (side) {
                        boolean window = dy % 5 >= 1 && dy % 5 <= 2 && i % 2 == 0 && Math.abs(i) < halfW;
                        plan.set(x + i, y + dy, pz + j, window ? b("glass")
                                : Math.abs(i) == halfW || dy == height - 1 ? trim : wall);
                    } else if (dy % 5 == 4) {
                        plan.set(x + i, y + dy, pz + j, b("spruce_planks"));
                    }
                }
                plan.set(x + i, y + height, pz + j, side ? trim : wall);
            }
        }
        // le portique et l'entree, cote sud
        int front = pz + halfD;
        int porch = small ? 1 : 2;
        for (int i = -3; i <= 3; i += 2) {
            column(plan, x + i, y, y + height - 1, front + porch, b("quartz_pillar"));
        }
        box(plan, x - 4, y + height, front, x + 4, y + height, front + porch, trim);
        box(plan, x - 1, y, front, x + 1, y + 2, front, b("air"));
        if ("asie".equals(style)) {
            roofTier(plan, x, y + height + 1, pz, halfW - 1, halfD - 1, domeBlock, b("gold_block"));
        } else {
            double radius = small ? 2.6d : 4.6d;
            disc(plan, x, y + height + 1, pz, radius + 0.6d, trim);
            dome(plan, x, y + height + 2, pz, radius, domeBlock);
            column(plan, x, y + height + 3 + (int) radius, y + height + 4 + (int) radius, pz, trim);
            if ("orient".equals(style) && !small) {
                for (int side = -1; side <= 1; side += 2) {
                    int mx = x + side * (halfW + 3);
                    for (int dy = 0; dy < 20; dy++) {
                        disc(plan, mx, y + dy, pz, 1.2d, dy == 14 ? trim : wall);
                    }
                    dome(plan, mx, y + 20, pz, 1.5d, domeBlock);
                }
            }
        }
        // les mats aux couleurs de la ville, de part et d'autre de l'entree
        String[] banners = {"red_banner", "blue_banner", "white_banner", "green_banner", "yellow_banner"};
        for (int side = -1; side <= 1; side += 2) {
            int fx = x + side * (small ? 5 : 9);
            int fz = front + (small ? 2 : 7);
            column(plan, fx, y, y + 6, fz, b("iron_bars"));
            plan.set(fx, y + 7, fz, b(banners[random.nextInt(banners.length)]));
        }
        // une fontaine sur la place des grandes villes
        if (!small) {
            int fz = z + 12;
            disc(plan, x, y - 1, fz, 4.0d, b("stone_bricks"));
            for (int i = -4; i <= 4; i++) {
                for (int j = -4; j <= 4; j++) {
                    double d = Math.sqrt(i * i + j * j);
                    if (d <= 4.0d && d > 3.0d) {
                        plan.set(x + i, y, fz + j, b("stone_brick_slab"));
                    } else if (d <= 3.0d) {
                        plan.set(x + i, y, fz + j, b("water"));
                    }
                }
            }
            column(plan, x, y, y + 2, fz, b("stone_brick_wall"));
            plan.set(x, y + 3, fz, b("sea_lantern"));
        }
        return small ? z + 3 : z + 20;
    }
}
