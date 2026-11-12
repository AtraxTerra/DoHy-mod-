package fr.atraxterra.dohy.spawn;

import fr.atraxterra.dohy.palamod.box.BoxBlock;
import fr.atraxterra.dohy.palamod.box.BoxKind;
import fr.atraxterra.dohy.palamod.box.PalaBoxes;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Le dessin du spawn.
 *
 * <p>Le lieu : un massif rocheux flottant au-dessus des nuages, entoure de
 * pitons. Sur le massif, un parvis de beton et un seul batiment, brutaliste :
 * trois gradins aux bouts arrondis, en retrait l'un sur l'autre, cernes de
 * bandeaux et de terrasses plantees ; au-dessus, une masse haute rayee
 * d'ailettes ; a l'est, un anneau en porte-a-faux sur pilotis ; a l'ouest, un
 * pylone relie aux gradins par trois passerelles superposees. Un pont part du
 * parvis vers une plateforme ronde posee sur son propre piton : c'est la qu'on
 * arrive.
 *
 * <p>Dedans : une nef basse en travers, puis l'atrium — un puits de beton de
 * quarante-six blocs sous une verriere — ou les six box attendent dans leurs
 * niches. De part et d'autre, deux travees de bornes ; deux etages de
 * galeries regardent l'atrium et ouvrent sur les terrasses.
 *
 * <p>Tout est calcule, rien n'est lu : la roche vient de quelques sinus, le
 * beton de boites et de distances. Le resultat est une liste de blocs, que
 * {@link SpawnApi} pose par tranches pour ne pas figer le jeu. Les blocs sont
 * nommes par leur identifiant : un bloc absent de cette version du jeu
 * devient de la pierre au lieu d'empecher le mod de se charger.
 */
final class SpawnBuilder {

    /** A augmenter des que le dessin change : un spawn d'une autre version est reconstruit. */
    static final int VERSION = 6;

    /** Le niveau du sol du parvis : on marche a {@code FLOOR + 1}. */
    static final int FLOOR = 200;
    /** Le centre de la plateforme d'arrivee. */
    static final int PAD_Z = 78;
    /** En dessous, un joueur est tombe : il est remis sur la plateforme. */
    static final int VOID_Y = 140;
    /**
     * Le volume ou le spawn a pu batir, toutes versions confondues : il est
     * vide avant une reconstruction, pitons compris.
     */
    static final int CLEAR_X = 118;
    static final int CLEAR_Z_MIN = -118;
    static final int CLEAR_Z_MAX = 164;
    static final int CLEAR_Y_MIN = 106;
    static final int CLEAR_Y_MAX = 300;

    /**
     * Les deux tableaux de l'atrium : {@link SpawnBoards} en reecrit le texte.
     * Ils flottent a trois blocs du mur : un texte tourne vers le joueur, pose
     * contre une paroi, s'y enfonce des qu'on le regarde de biais.
     */
    static final BlockPos BOARD_CONTEST = new BlockPos(-6, FLOOR + 9, -19);
    static final BlockPos BOARD_PROFILE = new BlockPos(6, FLOOR + 9, -19);
    static final float BOARD_SCALE = 0.9f;

    /** Une etiquette a poser : ou, quoi, de quelle couleur, a quelle taille. */
    record Label(BlockPos pos, String text, int color, float scale) {
    }

    /** Le plan : les blocs dans l'ordre ou ils ont ete dessines, et les etiquettes. */
    record Plan(Long2ObjectLinkedOpenHashMap<BlockState> blocks, List<Label> labels) {
    }

    private final Long2ObjectLinkedOpenHashMap<BlockState> blocks = new Long2ObjectLinkedOpenHashMap<>();
    private final List<Label> labels = new ArrayList<>();

    // -- La palette --------------------------------------------------------------------

    private final BlockState concrete = block("light_gray_concrete");
    private final BlockState darkConcrete = block("gray_concrete");
    private final BlockState black = block("black_concrete");
    private final BlockState yellow = block("yellow_concrete");
    private final BlockState white = block("white_concrete");
    private final BlockState red = block("red_concrete");
    private final BlockState steel = block("polished_deepslate");
    private final BlockState iron = block("iron_block");
    private final BlockState bars = block("iron_bars");
    private final BlockState post = block("polished_deepslate_wall");
    private final BlockState pipe = block("waxed_exposed_copper");
    private final BlockState lamp = with(block("waxed_copper_bulb"), "lit", "true");
    private final BlockState flood = block("sea_lantern");
    private final BlockState stone = block("stone");
    private final BlockState andesite = block("andesite");
    private final BlockState tuff = block("tuff");
    private final BlockState cobble = block("cobblestone");
    private final BlockState grass = block("grass_block");
    private final BlockState moss = block("moss_block");
    /** La pierre du batiment : le mur, ses bandeaux, ses rives, ses sols. */
    private final BlockState wall = block("stone");
    private final BlockState rib = block("stone_bricks");
    private final BlockState edge = block("smooth_stone");
    private final BlockState paving = block("polished_andesite");
    private final BlockState window = block("gray_stained_glass");
    private final BlockState skylight = block("glass");
    private final BlockState half = block("smooth_stone_slab");
    private final BlockState corbel = with(block("stone_brick_slab"), "type", "top");
    /** L'interieur : des murs carreles, un sol de brique dans l'atrium, du bois pour les mains courantes. */
    private final BlockState tile = block("polished_andesite");
    private final BlockState brick = block("bricks");
    private final BlockState rail = block("spruce_slab");
    private final BlockState pot = block("chiseled_stone_bricks");
    private final BlockState blossom = with(block("flowering_azalea_leaves"), "persistent", "true");
    private final BlockState lowWall = block("stone_brick_wall");
    private final BlockState dirt = block("dirt");
    private final BlockState coarse = block("coarse_dirt");
    private final BlockState podzol = block("podzol");
    private final BlockState full = with(block("smooth_stone_slab"), "type", "double");
    private final BlockState bush = with(block("oak_leaves"), "persistent", "true");
    private final BlockState shrub = with(block("azalea_leaves"), "persistent", "true");
    private final BlockState needles = with(block("spruce_leaves"), "persistent", "true");
    private final BlockState trunk = block("spruce_log");
    private final BlockState chain = block("iron_chain", "chain");
    private final BlockState lantern = with(block("lantern"), "hanging", "true");
    private final BlockState ladder = with(block("ladder"), "facing", "east");

    /** Le premier de ces blocs que le jeu connait ; de la pierre si aucun. */
    private static BlockState block(String... names) {
        for (String name : names) {
            var found = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(name));
            if (found.isPresent()) {
                return found.get().defaultBlockState();
            }
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** Regle une propriete par son nom ; sans effet si le bloc ne l'a pas. */
    private static BlockState with(BlockState state, String name, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property == null ? state : set(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    // -- Les gestes ------------------------------------------------------------------------

    private void put(int x, int y, int z, BlockState state) {
        blocks.put(BlockPos.asLong(x, y, z), state);
    }

    private boolean has(int x, int y, int z) {
        return blocks.containsKey(BlockPos.asLong(x, y, z));
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    put(x, y, z, state);
                }
            }
        }
    }

    /** Creuse : ce qui etait dessine la redevient du vide. */
    private void carve(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    blocks.remove(BlockPos.asLong(x, y, z));
                }
            }
        }
    }

    /** Un cylindre vertical, plein ou creux (paroi d'un bloc). */
    private void cylinder(int cx, int cz, int y1, int y2, double radius, boolean hollow, BlockState state) {
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius + 0.3d || (hollow && distance < radius - 0.9d)) {
                    continue;
                }
                for (int y = y1; y <= y2; y++) {
                    put(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73428767 ^ y * 912931 ^ z * 42317861;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ (h >>> 15)) & 0x7fffffff;
    }

    private BlockState rock(int x, int y, int z, boolean top) {
        int roll = hash(x, y, z) % 100;
        if (top) {
            return roll < 55 ? grass : roll < 75 ? moss : stone;
        }
        return roll < 55 ? stone : roll < 75 ? andesite : roll < 90 ? tuff : cobble;
    }

    /**
     * Un piton : une pointe en bas, un ventre au tiers, un sommet plat. Seule
     * l'ecorce est posee — l'interieur ne se voit pas, et le creuser divise par
     * quatre les blocs a poser.
     */
    private void spire(int cx, int cz, int bottom, int top, double belly, double crown, int seed) {
        int height = top - bottom;
        int reach = (int) Math.ceil(Math.max(belly, crown)) + 4;
        for (int y = bottom; y <= top; y++) {
            double t = (y - bottom) / (double) height;
            double radius = t < 0.4d ? belly * Math.sqrt(t / 0.4d) : belly + (crown - belly) * ((t - 0.4d) / 0.6d);
            boolean cap = y > top - 3;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    double angle = Math.atan2(dz, dx);
                    double edge = radius + 1.6d * Math.sin(3 * angle + y * 0.09d + seed)
                            + 1.1d * Math.sin(5 * angle - y * 0.06d + seed * 2)
                            + 0.9d * Math.sin(7 * angle + y * 0.21d + seed * 3);
                    if (cap) {
                        // le sommet reste a peu pres rond : c'est une assise
                        edge = radius + 0.8d * Math.sin(3 * angle + seed);
                    }
                    if (distance <= edge && (cap || distance >= edge - 2.6d)) {
                        put(cx + dx, y, cz + dz, rock(cx + dx, y, cz + dz, y == top));
                    }
                }
            }
        }
    }

    /** Un epicea : un tronc, des etages d'aiguilles qui s'elargissent vers le bas. */
    private void spruce(int x, int y, int z, int height) {
        for (int level = 1; level <= height - 2; level++) {
            int radius = 1 + (level - 1) % 2 + (level - 1) / 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius + 1 && !has(x + dx, y + height - level, z + dz)) {
                        put(x + dx, y + height - level, z + dz, needles);
                    }
                }
            }
        }
        fill(x, y, z, x, y + height - 1, z, trunk);
        put(x, y + height, z, needles);
    }

    // -- Le plan ---------------------------------------------------------------------------

    // -- La carte du monde ------------------------------------------------------------------

    /** La dalle de la carte commence ici, au bout de la passerelle qui part de la plateforme. */
    static final int MAP_Z = 98;
    /** Le puits de saut : un carre de cinq blocs, quelque part dans l'ocean de la carte. */
    static final int PIT_HALF = 2;

    /** Ou tombe la carte dans le spawn, et ou s'ouvre son puits. */
    record MapSite(int width, int height, int pitX, int pitZ) {
        /** Le x du pixel le plus a l'ouest : la carte est centree, l'ouest a la gauche de qui arrive. */
        int west() {
            return (width - 1) / 2;
        }

        /** Le z du pixel le plus au nord : le nord est au fond. */
        int north() {
            return MAP_Z + height - 1;
        }

        /** La colonne de la carte sous ce x, ou -1. */
        int column(int x) {
            int column = west() - x;
            return column >= 0 && column < width ? column : -1;
        }

        /** La ligne de la carte sous ce z, ou -1. */
        int row(int z) {
            int row = north() - z;
            return row >= 0 && row < height ? row : -1;
        }

        boolean inPit(double x, double z) {
            return Math.abs(Math.floor(x) - pitX) <= PIT_HALF && Math.abs(Math.floor(z) - pitZ) <= PIT_HALF;
        }
    }

    /**
     * Ou poser la carte et son puits pour ce dessin, ou {@code null} sans dessin.
     * Le puits s'ouvre dans la plus grande etendue de mer proche du centre : on
     * ne perce pas un continent.
     */
    static MapSite site(int[][] mosaic) {
        if (mosaic == null || mosaic.length == 0 || mosaic[0].length == 0) {
            return null;
        }
        int height = mosaic.length;
        int width = mosaic[0].length;
        int bestRow = height / 2;
        int bestColumn = width / 2;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int row = 4; row < height - 4; row++) {
            for (int column = 4; column < width - 4; column++) {
                int sea = 0;
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dx = -4; dx <= 4; dx++) {
                        if (mosaic[row + dz][column + dx] == 0) {
                            sea++;
                        }
                    }
                }
                if (sea < 81) {
                    continue;
                }
                // au plus pres du tiers gauche de la carte, a mi-hauteur : l'Atlantique d'une carte du monde
                double score = -Math.hypot(column - width * 0.38d, (row - height * 0.42d) * 1.5d);
                if (score > bestScore) {
                    bestScore = score;
                    bestRow = row;
                    bestColumn = column;
                }
            }
        }
        MapSite site = new MapSite(width, height, 0, 0);
        return new MapSite(width, height, site.west() - bestColumn, site.north() - bestRow);
    }

    /**
     * La carte du monde : une dalle posee derriere la plateforme d'arrivee,
     * un pixel par bloc, chaque pays d'une des sept teintes d'une carte
     * politique, la mer en bleu. On y entre par le sud, le nord au fond : elle
     * se lit a l'endroit en arrivant. Un puits y est ouvert dans l'ocean.
     */
    private void worldMap(int[][] mosaic) {
        MapSite site = site(mosaic);
        if (site == null) {
            return;
        }
        BlockState[] tints = {block("blue_concrete"), block("lime_concrete"), block("yellow_concrete"),
                block("orange_terracotta"), block("pink_concrete"), block("white_concrete"),
                block("light_gray_concrete"), block("green_concrete")};
        BlockState sea = block("blue_concrete");
        BlockState shallows = block("light_blue_concrete");
        BlockState seaLight = block("blue_stained_glass");
        int west = site.west();
        int east = west - site.width() + 1;
        int south = MAP_Z;
        int north = site.north();
        // la place d'abord : aucun piton ne traverse la dalle ni ne la surplombe
        carve(east - 3, FLOOR - 1, PAD_Z + 15, west + 3, CLEAR_Y_MAX, north + 3);
        // la passerelle, de la plateforme a la dalle
        for (int z = PAD_Z + 12; z < south - 2; z++) {
            for (int x = -3; x <= 3; x++) {
                put(x, FLOOR, z, Math.abs(x) == 3 ? steel : x == 0 && Math.floorMod(z, 4) < 2 ? yellow : concrete);
                put(x, FLOOR - 1, z, Math.abs(x) == 3 ? steel : darkConcrete);
            }
            put(3, FLOOR + 1, z, darkConcrete);
            put(-3, FLOOR + 1, z, darkConcrete);
        }
        carve(-2, FLOOR + 1, PAD_Z + 12, 2, FLOOR + 2, PAD_Z + 14);
        // la dalle : un cadre d'acier, un marchepied tout autour, la carte au milieu
        for (int x = east - 3; x <= west + 3; x++) {
            for (int z = south - 3; z <= north + 3; z++) {
                boolean frame = x < east - 1 || x > west + 1 || z < south - 1 || z > north + 1;
                boolean rim = x == east - 3 || x == west + 3 || z == south - 3 || z == north + 3;
                put(x, FLOOR - 1, z, rim || Math.floorMod(x + z, 8) == 0 ? steel : darkConcrete);
                put(x, FLOOR, z, rim ? steel : frame ? concrete : darkConcrete);
                if (rim && !(z == south - 3 && Math.abs(x) <= 2)) {
                    put(x, FLOOR + 1, z, darkConcrete);
                    if (Math.floorMod(x + z, 6) == 0) {
                        put(x, FLOOR + 2, z, lamp);
                    }
                }
            }
        }
        for (int row = 0; row < site.height(); row++) {
            for (int column = 0; column < site.width(); column++) {
                int x = west - column;
                int z = north - row;
                int tint = mosaic[row][column];
                if (tint > 0) {
                    put(x, FLOOR, z, tints[Math.min(tint, tints.length - 1)]);
                    continue;
                }
                boolean coast = false;
                for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int r = row + side[1];
                    int c = column + side[0];
                    coast |= r >= 0 && c >= 0 && r < site.height() && c < site.width() && mosaic[r][c] > 0;
                }
                // la mer : plus claire le long des cotes, eclairee par en dessous de loin en loin
                if (!coast && Math.floorMod(x, 7) == 3 && Math.floorMod(z, 7) == 3) {
                    put(x, FLOOR, z, seaLight);
                    put(x, FLOOR - 1, z, flood);
                } else {
                    put(x, FLOOR, z, coast ? shallows : sea);
                }
            }
        }
        // le puits : un trou de cinq blocs, cercle de jaune, par ou l'on saute
        int pitX = site.pitX();
        int pitZ = site.pitZ();
        for (int x = pitX - PIT_HALF - 1; x <= pitX + PIT_HALF + 1; x++) {
            for (int z = pitZ - PIT_HALF - 1; z <= pitZ + PIT_HALF + 1; z++) {
                boolean hole = Math.abs(x - pitX) <= PIT_HALF && Math.abs(z - pitZ) <= PIT_HALF;
                if (hole) {
                    carve(x, FLOOR - 1, z, x, FLOOR, z);
                } else {
                    put(x, FLOOR, z, Math.floorMod(x + z, 2) == 0 ? yellow : black);
                    put(x, FLOOR - 1, z, steel);
                }
            }
        }
        for (int corner = 0; corner < 4; corner++) {
            int x = pitX + (corner % 2 == 0 ? -1 : 1) * (PIT_HALF + 1);
            int z = pitZ + (corner < 2 ? -1 : 1) * (PIT_HALF + 1);
            lampPost(x, FLOOR + 1, z, 2);
        }
        labels.add(new Label(new BlockPos(pitX, FLOOR + 5, pitZ), "SAUT", 0xFAC400, 3.0f));
        labels.add(new Label(new BlockPos(pitX, FLOOR + 3, pitZ), "Saute : tu seras parachute au hasard sur la Terre",
                0xFFFFFF, 1.0f));
        labels.add(new Label(new BlockPos(0, FLOOR + 4, south - 4), "LA TERRE", 0xFFFFFF, 2.5f));
        labels.add(new Label(new BlockPos(0, FLOOR + 2, south - 4), "Marche sur la carte : elle te dit le pays",
                0xFAC400, 1.0f));
    }

    /**
     * @param mosaic la carte du monde a dessiner derriere la plateforme, une teinte par pixel
     *               (0 : la mer), le nord en premiere ligne ; {@code null} : pas de carte
     */
    static Plan plan(int[][] mosaic) {
        SpawnBuilder builder = new SpawnBuilder();
        builder.rocks();
        builder.forecourt();
        builder.bridge();
        builder.pad();
        builder.tiers();
        builder.core();
        builder.garden();
        builder.boxes();
        builder.nave();
        builder.services();
        builder.decor();
        builder.doors();
        builder.porch();
        builder.base();
        builder.greenery();
        builder.ring();
        builder.pylon();
        builder.industry();
        builder.worldMap(mosaic);
        return new Plan(builder.blocks, builder.labels);
    }

    /** La roche d'abord : tout le reste se pose dessus et la remplace ou elle gene. */
    private void rocks() {
        spire(0, -6, 118, FLOOR - 4, 26.0d, 58.0d, 1);
        spire(0, PAD_Z, 128, FLOOR - 3, 8.0d, 15.5d, 2);
        int[][] ring = {
                // x, z, sommet, ventre, couronne, mat (1) ou non (0)
                {-66, -44, 246, 15, 8, 1}, {68, -40, 236, 14, 8, 0}, {-72, 18, 222, 13, 7, 1},
                {74, 24, 258, 16, 9, 1}, {-52, 62, 232, 13, 7, 0}, {54, 70, 214, 12, 7, 1},
                {0, -84, 286, 19, 11, 0}, {-90, -62, 206, 12, 7, 0}, {92, -66, 228, 13, 8, 1},
                {36, -74, 252, 11, 6, 0}, {-32, -76, 226, 10, 6, 0}, {-34, 106, 210, 10, 6, 1},
                {36, 110, 226, 11, 7, 0},
        };
        int seed = 3;
        for (int[] rock : ring) {
            spire(rock[0], rock[1], rock[2] - 96, rock[2], rock[3], rock[4], seed++);
            if (rock[5] == 1) {
                mast(rock[0], rock[2] + 1, rock[1]);
            }
        }
        // des epiceas sur la roche qui deborde du parvis
        for (int gx = -56; gx <= 56; gx += 5) {
            for (int gz = -62; gz <= 50; gz += 5) {
                int roll = hash(gx, 7, gz);
                int x = gx + roll % 5 - 2;
                int z = gz + (roll >> 4) % 5 - 2;
                if (Math.hypot(x, z + 6) > 53.0d || plaza(x, z) < 1.1d || (Math.abs(x) < 9 && z > 0)
                        || (roll >> 8) % 100 < 30) {
                    continue;
                }
                spruce(x, FLOOR - 3, z, 5 + (roll >> 16) % 5);
            }
        }
    }

    /** Un mat d'eclairage : une aiguille de beton, un panneau de projecteurs tourne vers le parvis. */
    private void mast(int x, int y, int z) {
        fill(x, y, z, x, y + 27, z, concrete);
        fill(x, y, z, x, y + 2, z, steel);
        boolean alongX = Math.abs(x) > Math.abs(z);
        int toward = alongX ? -Integer.signum(x) : -Integer.signum(z);
        for (int side = -2; side <= 2; side++) {
            for (int up = 25; up <= 28; up++) {
                boolean frame = Math.abs(side) == 2 || up == 25 || up == 28;
                int px = alongX ? x + toward : x + side;
                int pz = alongX ? z + side : z + toward;
                put(px, y + up, pz, frame ? black : flood);
            }
        }
    }

    // -- Le parvis, le pont, la plateforme -------------------------------------------------

    /** Le parvis est une ellipse : en dessous de 1, on est dessus. */
    private static double plaza(int x, int z) {
        double dx = x / 54.0d;
        double dz = (z + 6) / 44.0d;
        return dx * dx + dz * dz;
    }

    private static boolean inPlaza(int x, int z) {
        return plaza(x, z) <= 1.0d;
    }

    private BlockState soil(int x, int z) {
        int roll = hash(x, 11, z) % 100;
        return roll < 68 ? grass : roll < 80 ? moss : roll < 91 ? coarse : podzol;
    }

    /**
     * Le parvis : un plateau ovale de terre et d'herbe, tenu par un mur de
     * pierre, avec une allee dallee du pont au porche et des epiceas.
     */
    private void forecourt() {
        for (int x = -55; x <= 55; x++) {
            for (int z = -51; z <= 39; z++) {
                if (!inPlaza(x, z)) {
                    continue;
                }
                boolean rim = !inPlaza(x + 1, z) || !inPlaza(x - 1, z) || !inPlaza(x, z + 1) || !inPlaza(x, z - 1);
                boolean nearRim = !inPlaza(x + 2, z) || !inPlaza(x - 2, z) || !inPlaza(x, z + 2)
                        || !inPlaza(x, z - 2);
                fill(x, FLOOR - 3, z, x, FLOOR - 1, z, rim ? rib : nearRim ? wall : dirt);
                put(x, FLOOR, z, rim || nearRim ? rib : soil(x, z));
                // le muret, ouvert sur le pont
                if (rim && !(z > 30 && Math.abs(x) <= 2)) {
                    put(x, FLOOR + 1, z, lowWall);
                }
            }
        }
        // l'allee, du pont au porche
        for (int z = 7; z <= 36; z++) {
            for (int x = -3; x <= 3; x++) {
                put(x, FLOOR, z, Math.abs(x) == 3 ? rib : x == 0 && Math.floorMod(z, 6) == 3 ? lamp : paving);
            }
        }
        // devant le porche, l'allee s'evase en tablier
        for (int z = 13; z <= 24; z++) {
            int width = Math.min(10, 3 + 24 - z);
            for (int x = -width; x <= width; x++) {
                if (Math.abs(x) >= 3) {
                    put(x, FLOOR, z, Math.abs(x) == width ? rib : paving);
                }
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int z = 16; z <= 34; z += 6) {
                lampPost(5 * side, FLOOR + 1, z, 3);
            }
        }
        // des lampadaires en couronne sur le plateau
        for (int step = 0; step < 16; step++) {
            double angle = Math.PI / 8 * step + Math.PI / 16;
            int x = (int) Math.round(Math.cos(angle) * 42.0d);
            int z = -6 + (int) Math.round(Math.sin(angle) * 34.0d);
            if (reach(0, x, z) > TIERS[0][2] + 10.5d && Math.abs(x) > 7) {
                lampPost(x, FLOOR + 1, z, 3);
            }
        }
        // des epiceas et des buissons, la ou rien d'autre ne viendra
        for (int x = -50; x <= 50; x++) {
            for (int z = -34; z <= 34; z++) {
                if (plaza(x, z) > 0.84d || reach(0, x, z) < TIERS[0][2] + 10.5d || Math.abs(x) < 7
                        || (x < -36 && z > -26 && z < -6) || Math.hypot(x - 35, z + 15) < 16.5d) {
                    continue;
                }
                int roll = hash(x, 13, z) % 1000;
                if (roll < 22 && Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0) {
                    spruce(x, FLOOR + 1, z, 5 + roll % 4);
                } else if (roll >= 940 && !has(x, FLOOR + 1, z)) {
                    put(x, FLOOR + 1, z, roll % 2 == 0 ? bush : shrub);
                }
            }
        }
    }

    /** Le pont : un tablier, deux poutres, des tuyaux qui courent dessous, des lampadaires. */
    private void bridge() {
        for (int z = 37; z <= 66; z++) {
            for (int x = -3; x <= 3; x++) {
                BlockState deck = x == 0 && Math.floorMod(z, 4) < 2 ? yellow : concrete;
                put(x, FLOOR, z, Math.abs(x) == 3 ? steel : deck);
                put(x, FLOOR - 1, z, Math.abs(x) == 3 || Math.floorMod(z, 4) == 0 ? steel : darkConcrete);
            }
            for (int side = -1; side <= 1; side += 2) {
                put(3 * side, FLOOR + 1, z, darkConcrete);
                put(3 * side, FLOOR + 2, z, bars);
                put(5 * side, FLOOR - 2, z, Math.floorMod(z, 6) == 0 ? iron : pipe);
                if (Math.floorMod(z, 6) == 0) {
                    put(4 * side, FLOOR - 2, z, steel);
                }
                if (Math.floorMod(z, 8) == 4) {
                    lampPost(3 * side, FLOOR + 3, z, 2);
                }
            }
        }
        // des portiques d'acier, un tous les huit blocs
        for (int z = 40; z <= 64; z += 8) {
            for (int side = -1; side <= 1; side += 2) {
                fill(4 * side, FLOOR - 1, z, 4 * side, FLOOR + 6, z, steel);
                put(2 * side, FLOOR + 6, z, lamp);
            }
            fill(-4, FLOOR + 7, z, 4, FLOOR + 7, z, steel);
        }
        // deux beches de beton sous le milieu du pont
        for (int side = -1; side <= 1; side += 2) {
            for (int step = 0; step <= 14; step++) {
                fill(2 * side, FLOOR - 2 - step, 58 - step / 2, 3 * side, FLOOR - 2 - step, 59 + step / 2, concrete);
            }
        }
    }

    private void lampPost(int x, int y, int z, int height) {
        fill(x, y, z, x, y + height - 1, z, post);
        put(x, y + height, z, lamp);
    }

    /** La plateforme d'arrivee : un disque marque d'un cercle jaune, ouvert vers le pont. */
    private void pad() {
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 13.4d) {
                    continue;
                }
                int x = dx;
                int z = PAD_Z + dz;
                boolean rim = distance > 12.4d;
                fill(x, FLOOR - 2, z, x, FLOOR - 1, z, rim ? steel : darkConcrete);
                BlockState top = darkConcrete;
                if (distance > 8.4d && distance <= 9.6d) {
                    top = yellow;
                } else if ((dx == 0 || dz == 0) && distance <= 6.0d) {
                    top = white;
                } else if (distance <= 9.0d && Math.floorMod(dx, 5) == 0 && Math.floorMod(dz, 5) == 0) {
                    top = lamp;
                }
                put(x, FLOOR, z, rim ? steel : top);
                if (rim && !(dz < 0 && Math.abs(dx) <= 3)) {
                    put(x, FLOOR + 1, z, darkConcrete);
                    if (Math.floorMod(dx + dz, 4) == 0) {
                        put(x, FLOOR + 2, z, lamp);
                    }
                }
            }
        }
        put(0, FLOOR, PAD_Z, lamp);
        terminal(5, FLOOR + 1, PAD_Z + 5, Direction.NORTH, SpawnTerminalBlock.Kind.RETURN,
                new BlockPos(5, FLOOR + 2, PAD_Z + 5));
        labels.add(new Label(new BlockPos(0, FLOOR + 5, PAD_Z - 9), "SPAWN", 0xFFFFFF, 3.0f));
        labels.add(new Label(new BlockPos(0, FLOOR + 3, PAD_Z - 9), "Le hall des box est au bout du pont", 0xFAC400,
                1.0f));
    }

    private void terminal(int x, int y, int z, Direction facing, SpawnTerminalBlock.Kind kind, BlockPos label) {
        put(x, y, z, DoHySpawn.TERMINAL.get().defaultBlockState()
                .setValue(SpawnTerminalBlock.FACING, facing).setValue(SpawnTerminalBlock.KIND, kind));
        labels.add(new Label(label, kind.label(), kind.color(), 1.0f));
    }

    private BlockState accent(BoxKind kind) {
        return block(switch (kind) {
            case AMETHYST -> "purple_concrete";
            case PALADIUM -> "orange_concrete";
            case ENDIUM -> "blue_concrete";
            case SAHARIA -> "yellow_concrete";
            case TRIXIUM -> "magenta_concrete";
            case HALLOWEEN -> "red_concrete";
        });
    }

    private static int accentColor(BoxKind kind) {
        return switch (kind) {
            case AMETHYST -> 0xB06CFF;
            case PALADIUM -> 0xFF8A2A;
            case ENDIUM -> 0x4C7DFF;
            case SAHARIA -> 0xFFD83A;
            case TRIXIUM -> 0xFF5AD0;
            case HALLOWEEN -> 0xFF4A3A;
        };
    }

    private BlockState accentFor(SpawnTerminalBlock.Kind kind) {
        return block(switch (kind) {
            case SHOP -> "lime_concrete";
            case BOUTIQUE -> "yellow_concrete";
            case MARKET -> "orange_concrete";
            case BANK -> "light_blue_concrete";
            case CONTRACTS -> "blue_concrete";
            case MUSEUM -> "magenta_concrete";
            case RETURN -> "red_concrete";
            case DAILY -> "yellow_concrete";
            case PORTALS -> "purple_concrete";
        });
    }

    // -- Le batiment : sa geometrie -------------------------------------------------------

    /** Les trois gradins : axe (z), demi-longueur, rayon, niveau du sol au-dessus de FLOOR. */
    private static final int[][] TIERS = {{-14, 18, 20, 0}, {-16, 13, 17, 7}, {-18, 8, 14, 14}};
    /** D'un sol au suivant : six blocs de vide, un de dalle. */
    private static final int STOREY = 7;
    /** Ou l'on est par rapport a un gradin. */
    private static final int OUT = 0;
    private static final int LEDGE = 1;
    private static final int SKIN = 2;
    private static final int LINING = 3;
    private static final int INSIDE = 4;
    /** La masse haute, et le puits de l'atrium qu'elle enferme. */
    private static final int CORE_X = 10;
    private static final int CORE_NORTH = -29;
    private static final int CORE_SOUTH = -9;
    private static final int CORE_TOP = FLOOR + 46;

    /** La distance a l'axe d'un gradin : un segment, d'ou les bouts arrondis. */
    private static double reach(int tier, int x, int z) {
        double dx = Math.max(0, Math.abs(x) - TIERS[tier][1]);
        double dz = z - TIERS[tier][0];
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Dehors, debord de la dalle, peau de pierre, doublage de beton, ou dedans. */
    private static int zone(int tier, int x, int z) {
        double distance = reach(tier, x, z);
        int radius = TIERS[tier][2];
        if (distance > radius + 1.5d) {
            return OUT;
        }
        return distance > radius + 0.5d ? LEDGE : distance > radius - 0.5d ? SKIN
                : distance > radius - 1.5d ? LINING : INSIDE;
    }

    /** Le tour d'un gradin, deroule : il espace les meneaux et les piles. */
    private static int seam(int tier, int x, int z) {
        int half = TIERS[tier][1];
        int out = Math.abs(x) - half;
        if (out <= 0) {
            return Math.abs(x);
        }
        return half + (int) Math.round(Math.atan2(out, Math.abs(z - TIERS[tier][0])) * TIERS[tier][2]);
    }

    private static boolean inCore(int x, int z) {
        return Math.abs(x) <= CORE_X && z >= CORE_NORTH && z <= CORE_SOUTH;
    }

    /** Le vide de l'atrium : la masse haute moins ses deux epaisseurs de mur. */
    private static boolean inVoid(int x, int z) {
        return Math.abs(x) <= CORE_X - 2 && z >= CORE_NORTH + 2 && z <= CORE_SOUTH - 2;
    }

    private BlockState floorAt(int x, int z) {
        if (Math.floorMod(x, 6) == 1 && Math.floorMod(z, 6) == 1) {
            return lamp;
        }
        return Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0 ? rib : edge;
    }

    /**
     * Les trois gradins. Chacun : un mur a deux peaux (pierre dehors, beton
     * dedans), un bandeau vitre en retrait, une dalle qui deborde et porte le
     * garde-corps de la terrasse du dessus. La dalle d'un gradin est le sol du
     * suivant ; ses lampes eclairent donc le plafond d'en bas et le sol d'en haut.
     */
    private void tiers() {
        for (int tier = 0; tier < TIERS.length; tier++) {
            int axis = TIERS[tier][0];
            int span = TIERS[tier][1] + TIERS[tier][2] + 2;
            int depth = TIERS[tier][2] + 2;
            int base = FLOOR + TIERS[tier][3];
            int roof = base + STOREY;
            for (int x = -span; x <= span; x++) {
                for (int z = axis - depth; z <= axis + depth; z++) {
                    int zone = zone(tier, x, z);
                    if (zone == OUT) {
                        continue;
                    }
                    if (zone == LEDGE) {
                        // le debord et son garde-corps, ponctue de lampes
                        put(x, roof - 1, z, corbel);
                        put(x, roof, z, edge);
                        put(x, roof + 1, z, Math.floorMod(seam(tier, x, z), 6) == 3 ? lamp : rib);
                        put(x, roof + 2, z, half);
                        continue;
                    }
                    int above = tier + 1 < TIERS.length ? zone(tier + 1, x, z) : inCore(x, z) ? INSIDE : OUT;
                    boolean bulb = Math.floorMod(x, 6) == 1 && Math.floorMod(z, 6) == 1;
                    if (above == INSIDE) {
                        put(x, roof, z, floorAt(x, z));
                    } else if (above == SKIN || above == LINING) {
                        put(x, roof, z, bulb ? lamp : rib);
                    } else {
                        put(x, roof, z, bulb ? lamp : Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0 ? rib : edge);
                    }
                    if (zone == INSIDE) {
                        if (tier == 0) {
                            put(x, base, z, floorAt(x, z));
                        }
                        // le plafond a caissons : des poutres croisees tous les trois blocs
                        if (Math.floorMod(x, 3) == 0 || Math.floorMod(z, 3) == 0) {
                            put(x, roof - 1, z, wall);
                        }
                        continue;
                    }
                    int seam = seam(tier, x, z);
                    boolean mullion = Math.floorMod(seam, 4) == 0;
                    boolean pier = Math.floorMod(seam, 12) == 0;
                    for (int row = 1; row < STOREY; row++) {
                        boolean band = row >= 2 && row <= 4;
                        if (zone == SKIN) {
                            if (!band) {
                                put(x, base + row, z, row == 2 ? wall : rib);
                            } else if (pier) {
                                put(x, base + row, z, wall);
                            }
                        } else {
                            put(x, base + row, z, band && !mullion ? window : row == 1 || row == 6 ? rib : tile);
                        }
                    }
                }
            }
        }
    }

    /**
     * La masse haute et l'atrium. Deux epaisseurs de mur autour d'un vide de
     * dix-sept blocs de cote, du sol a la verriere : en bas un portail et des
     * niches, aux etages des baies ouvertes sur les galeries, au-dessus des
     * fentes de verre entre des ailettes.
     */
    private void core() {
        for (int x = -CORE_X; x <= CORE_X; x++) {
            for (int z = CORE_NORTH; z <= CORE_SOUTH; z++) {
                if (inVoid(x, z)) {
                    carve(x, FLOOR + 1, z, x, CORE_TOP, z);
                    boolean beam = Math.floorMod(x, 4) == 0 || Math.floorMod(z + 19, 4) == 0;
                    put(x, CORE_TOP + 1, z, beam ? wall : skylight);
                    boolean line = Math.abs(x) == 8 || Math.abs(z + 19) == 8 || Math.floorMod(x, 4) == 0
                            || Math.floorMod(z + 19, 4) == 0;
                    put(x, FLOOR, z, line ? rib : brick);
                    continue;
                }
                boolean skin = Math.abs(x) == CORE_X || z == CORE_NORTH || z == CORE_SOUTH;
                for (int y = FLOOR + 1; y <= CORE_TOP; y++) {
                    put(x, y, z, skin && y > FLOOR + 3 * STOREY ? wall : (y - FLOOR) % STOREY == 0 ? rib : tile);
                }
                put(x, CORE_TOP + 1, z, edge);
                if (skin) {
                    put(x, CORE_TOP + 2, z, rib);
                }
            }
        }
        // le portail de l'atrium, sous un linteau de pierre taillee
        carve(-8, FLOOR + 1, CORE_SOUTH - 1, 8, FLOOR + 5, CORE_SOUTH);
        fill(-8, FLOOR + 6, CORE_SOUTH - 1, 8, FLOOR + 6, CORE_SOUTH, rib);
        fill(-8, FLOOR, CORE_SOUTH - 1, 8, FLOOR, CORE_SOUTH, rib);
        // les galeries : de grandes baies sur l'atrium, une main courante de bois, des lanternes
        for (int level = 1; level <= 2; level++) {
            int base = FLOOR + level * STOREY;
            for (int side = -1; side <= 1; side += 2) {
                for (int z = CORE_NORTH + 2; z <= CORE_SOUTH - 2; z++) {
                    if (Math.abs(z + 19) <= 1) {
                        continue;
                    }
                    carve(9 * side, base + 2, z, 9 * side, base + 5, z);
                    carve(10 * side, base + 1, z, 10 * side, base + 5, z);
                    put(9 * side, base + 1, z, rib);
                    put(9 * side, base + 2, z, rail);
                    if (Math.floorMod(z, 2) == 0 && Math.abs(z + 19) != 8) {
                        put(9 * side, base + 5, z, lantern);
                    }
                }
            }
            carve(-8, base + 2, CORE_SOUTH - 1, 8, base + 5, CORE_SOUTH - 1);
            carve(-8, base + 1, CORE_SOUTH, 8, base + 5, CORE_SOUTH);
            fill(-8, base + 1, CORE_SOUTH - 1, 8, base + 1, CORE_SOUTH - 1, rib);
            fill(-8, base + 2, CORE_SOUTH - 1, 8, base + 2, CORE_SOUTH - 1, rail);
            for (int x = -6; x <= 6; x += 4) {
                put(x, base + 5, CORE_SOUTH - 1, lantern);
            }
        }
        int low = FLOOR + 3 * STOREY + 1;
        int shoulder = FLOOR + 34;
        // les fentes : le verre au nu interieur, la pierre creusee devant
        for (int y = low + 2; y <= CORE_TOP - 2; y++) {
            for (int offset = -6; offset <= 6; offset += 4) {
                if (y > shoulder + 1) {
                    for (int side = -1; side <= 1; side += 2) {
                        put(9 * side, y, -19 + offset, window);
                        carve(10 * side, y, -19 + offset, 10 * side, y, -19 + offset);
                    }
                }
                put(offset, y, CORE_NORTH + 1, window);
                carve(offset, y, CORE_NORTH, offset, y, CORE_NORTH);
            }
            for (int x = -3; x <= 3; x += 2) {
                put(x, y, CORE_SOUTH - 1, window);
                carve(x, y, CORE_SOUTH, x, y, CORE_SOUTH);
            }
        }
        // les epaules : deux masses basses contre les flancs, et leurs contreforts en pente
        for (int side = -1; side <= 1; side += 2) {
            fill(11 * side, low, CORE_NORTH + 2, 14 * side, shoulder, CORE_SOUTH - 2, wall);
            fill(11 * side, shoulder, CORE_NORTH + 2, 14 * side, shoulder, CORE_SOUTH - 2, rib);
            fill(11 * side, shoulder + 1, CORE_NORTH + 2, 14 * side, shoulder + 1, CORE_SOUTH - 2, half);
            for (int offset = -8; offset <= 8; offset += 4) {
                int z = -19 + offset;
                for (int y = low; y <= shoulder + 2; y++) {
                    // les deux du bout sont plus courts : on passe entre eux et le garde-corps
                    int reach = 15 + (shoulder + 2 - y) / (Math.abs(offset) == 8 ? 7 : 3);
                    fill(15 * side, y, z, reach * side, y, z, y > shoulder ? rib : wall);
                    put(reach * side, y, z, rib);
                }
                // au-dessus de l'epaule, l'ailette continue jusqu'au toit
                fill(11 * side, shoulder + 1, z, 11 * side, CORE_TOP + 3, z, wall);
                fill(12 * side, shoulder + 1, z, 12 * side, shoulder + 6, z, wall);
                put(11 * side, CORE_TOP + 3, z, rib);
                put(12 * side, shoulder + 6, z, rib);
            }
        }
        for (int offset = -8; offset <= 8; offset += 4) {
            for (int y = low; y <= CORE_TOP + 3; y++) {
                int reach = CORE_NORTH - 1 - Math.max(0, (FLOOR + 27 - y) / 3);
                fill(offset, y, CORE_NORTH - 1, offset, y, Math.max(reach, CORE_NORTH - 2), wall);
            }
            put(offset, CORE_TOP + 3, CORE_NORTH - 1, rib);
        }
        // au sud, deux piles en avant de la facade, plus hautes que le toit
        for (int side = -1; side <= 1; side += 2) {
            fill(5 * side, low, CORE_SOUTH + 1, 10 * side, CORE_TOP + 4, CORE_SOUTH + 1, wall);
            fill(6 * side, low, CORE_SOUTH + 2, 9 * side, CORE_TOP - 2, CORE_SOUTH + 2, wall);
            fill(5 * side, CORE_TOP + 4, CORE_SOUTH + 1, 10 * side, CORE_TOP + 4, CORE_SOUTH + 1, rib);
            fill(6 * side, CORE_TOP - 2, CORE_SOUTH + 2, 9 * side, CORE_TOP - 2, CORE_SOUTH + 2, rib);
        }
        // la lame de lumiere : toute la hauteur du mur du fond
        for (int y = FLOOR + 2; y <= CORE_TOP - 1; y++) {
            put(0, y, CORE_NORTH + 1, flood);
            put(-1, y, CORE_NORTH + 1, steel);
            put(1, y, CORE_NORTH + 1, steel);
        }
        // la coursive derriere l'atrium : des appliques a chaque niveau
        for (int level = 0; level <= 2; level++) {
            for (int x = -6; x <= 6; x += 6) {
                put(x, FLOOR + level * STOREY + 4, CORE_NORTH, flood);
            }
        }
        // des poutres en travers du puits, des lanternes au bout de leurs chaines
        for (int z = -23; z <= -15; z += 8) {
            fill(-8, FLOOR + 27, z, 8, FLOOR + 28, z, wall);
        }
        for (int side = -1; side <= 1; side += 2) {
            fill(4 * side, FLOOR + 37, CORE_NORTH + 2, 4 * side, FLOOR + 38, CORE_SOUTH - 2, wall);
            for (int z = -23; z <= -15; z += 8) {
                fill(4 * side, FLOOR + 23, z, 4 * side, FLOOR + 26, z, chain);
                put(4 * side, FLOOR + 22, z, lantern);
            }
            fill(4 * side, FLOOR + 32, -19, 4 * side, FLOOR + 36, -19, chain);
            put(4 * side, FLOOR + 31, -19, lantern);
        }
        // une antenne sur un angle du toit
        fill(-9, CORE_TOP + 2, CORE_NORTH + 1, -9, CORE_TOP + 4, CORE_NORTH + 1, steel);
        fill(-9, CORE_TOP + 5, CORE_NORTH + 1, -9, CORE_TOP + 16, CORE_NORTH + 1, bars);
        put(-9, CORE_TOP + 17, CORE_NORTH + 1, red);
        labels.add(new Label(new BlockPos(0, FLOOR + 13, -24), "DOHY", 0xEF3826, 6.0f));
    }

    /** Une jardiniere de pierre taillee, de trois blocs de cote, plantee en son milieu. */
    private void planter(int cx, int cz, int floor, int height) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean centre = dx == 0 && dz == 0;
                fill(cx + dx, floor + 1, cz + dz, cx + dx, floor + height, cz + dz, centre ? dirt : rib);
            }
        }
        put(cx, floor + height, cz, moss);
        put(cx, floor + height + 1, cz, shrub);
        put(cx, floor + height + 2, cz, blossom);
    }

    /**
     * Le jardin de l'atrium : un massif au centre, quatre jardinieres, deux
     * bancs de bois, deux murs verts de part et d'autre de la lame de lumiere,
     * et de la verdure qui retombe des galeries.
     */
    private void garden() {
        // le massif central : un bac de deux blocs de haut, un buisson en boule
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                fill(dx, FLOOR + 1, -19 + dz, dx, FLOOR + 2, -19 + dz, ring ? rib : dirt);
                if (!ring) {
                    put(dx, FLOOR + 2, -19 + dz, hash(dx, 6, dz) % 3 == 0 ? moss : grass);
                    put(dx, FLOOR + 3, -19 + dz, hash(dx, 8, dz) % 3 == 0 ? blossom : shrub);
                    if (dx == 0 || dz == 0) {
                        put(dx, FLOOR + 4, -19 + dz, hash(dx, 9, dz) % 2 == 0 ? blossom : bush);
                    }
                }
            }
        }
        put(0, FLOOR + 5, -19, shrub);
        for (int side = -1; side <= 1; side += 2) {
            planter(4 * side, -24, FLOOR, 1);
            planter(4 * side, -14, FLOOR, 1);
            fill(3 * side, FLOOR + 1, -20, 3 * side, FLOOR + 1, -18, rail);
            // un mur vert : un bac, puis du feuillage sur trente blocs
            fill(4 * side, FLOOR + 1, CORE_NORTH + 2, 5 * side, FLOOR + 2, CORE_NORTH + 2, rib);
            for (int y = FLOOR + 3; y <= FLOOR + 32; y++) {
                for (int x = 4; x <= 5; x++) {
                    put(x * side, y, CORE_NORTH + 2, hash(x * side, y, 3) % 4 == 0 ? shrub : bush);
                }
            }
        }
        // la verdure des galeries : elle deborde du garde-corps et retombe d'un bloc ou deux
        for (int level = 1; level <= 2; level++) {
            int base = FLOOR + level * STOREY;
            for (int along = -8; along <= 8; along++) {
                int[][] spots = {{8, -19 + along}, {-8, -19 + along}, {along, CORE_SOUTH - 2}};
                for (int[] spot : spots) {
                    int roll = hash(spot[0], base, spot[1]) % 100;
                    if (roll >= 42 || (spot[0] != along && Math.abs(along) <= 1)) {
                        continue;
                    }
                    for (int drop = 0; drop <= roll % 2; drop++) {
                        if (!has(spot[0], base + 1 - drop, spot[1])) {
                            put(spot[0], base + 1 - drop, spot[1], roll % 3 == 0 ? shrub : bush);
                        }
                    }
                }
            }
        }
    }

    /**
     * Les six box : trois niches de chaque cote de l'atrium, creusees dans le
     * mur, peintes a la couleur de la box, eclairees par le haut. La box est
     * devant, sur un socle d'acier.
     */
    private void boxes() {
        List<DeferredBlock<BoxBlock>> boxes = PalaBoxes.BOXES;
        for (int index = 0; index < boxes.size() && index < 6; index++) {
            int side = index < 3 ? -1 : 1;
            int z = -25 + (index % 3) * 6;
            BoxBlock box = boxes.get(index).get();
            BoxKind kind = box.kind();
            carve(9 * side, FLOOR + 1, z - 1, 9 * side, FLOOR + 5, z + 1);
            fill(10 * side, FLOOR + 1, z - 1, 10 * side, FLOOR + 5, z + 1, accent(kind));
            fill(9 * side, FLOOR + 6, z - 1, 9 * side, FLOOR + 6, z + 1, flood);
            fill(8 * side, FLOOR + 1, z - 1, 8 * side, FLOOR + 1, z + 1, steel);
            put(9 * side, FLOOR + 1, z, steel);
            put(8 * side, FLOOR + 2, z, box.defaultBlockState()
                    .setValue(BoxBlock.FACING, side < 0 ? Direction.EAST : Direction.WEST));
            put(6 * side, FLOOR, z - 1, lamp);
            put(6 * side, FLOOR, z + 1, lamp);
            labels.add(new Label(new BlockPos(8 * side, FLOOR + 4, z), "BOX " + kind.label().toUpperCase(),
                    accentColor(kind), 1.6f));
        }
        labels.add(new Label(new BlockPos(0, FLOOR + 5, CORE_SOUTH + 2), "HALL DES BOX", 0xFFFFFF, 2.2f));
        labels.add(new Label(new BlockPos(0, FLOOR + 4, CORE_SOUTH + 2),
                "Clic sur une box avec sa cle  -  les cles sont a la borne BOUTIQUE", 0xC9D2DA, 0.8f));
    }

    /**
     * La nef et ses deux travees : une colonnade sous une poutre, trois steles
     * de bornes par travee, des plantes en pot, et les escaliers — deux par
     * cote, du sol a la premiere galerie puis a la seconde.
     */
    private void nave() {
        SpawnTerminalBlock.Kind[][] kinds = {
                {SpawnTerminalBlock.Kind.SHOP, SpawnTerminalBlock.Kind.BOUTIQUE, SpawnTerminalBlock.Kind.MARKET},
                {SpawnTerminalBlock.Kind.BANK, SpawnTerminalBlock.Kind.CONTRACTS, SpawnTerminalBlock.Kind.MUSEUM},
        };
        for (int index = 0; index < 2; index++) {
            int side = index == 0 ? 1 : -1;
            for (int x = 11; zone(0, x, CORE_SOUTH) == INSIDE; x++) {
                fill(x * side, FLOOR + 6, CORE_SOUTH - 1, x * side, FLOOR + 6, CORE_SOUTH, wall);
            }
            for (int column = 16; column <= 30; column += 7) {
                fill(column * side, FLOOR + 1, CORE_SOUTH - 1, (column + 1) * side, FLOOR + 5, CORE_SOUTH, rib);
                // une plante en pot au pied de chaque colonne
                put(column * side, FLOOR + 1, CORE_SOUTH + 1, pot);
                put(column * side, FLOOR + 2, CORE_SOUTH + 1, shrub);
                put(column * side, FLOOR + 3, CORE_SOUTH + 1, blossom);
            }
            for (int slot = 0; slot < 3; slot++) {
                int x = (13 + 7 * slot) * side;
                SpawnTerminalBlock.Kind kind = kinds[index][slot];
                fill(x - 1, FLOOR + 1, -20, x + 1, FLOOR + 3, -20, tile);
                fill(x - 1, FLOOR + 4, -20, x + 1, FLOOR + 4, -20, accentFor(kind));
                fill(x - 1, FLOOR + 1, -20, x + 1, FLOOR + 1, -20, rib);
                terminal(x, FLOOR + 2, -20, Direction.SOUTH, kind, new BlockPos(x, FLOOR + 5, -20));
                put(x, FLOOR, -18, lamp);
            }
            labels.add(new Label(new BlockPos(20 * side, FLOOR + 5, CORE_SOUTH + 2),
                    index == 0 ? "COMMERCE" : "CARRIERE", 0xFFFFFF, 2.0f));
            stair(side, -29, -28, 19, FLOOR + 1);
            stair(side, -21, -20, 19, FLOOR + 1 + STOREY);
            // un banc de bois sur la premiere galerie, face a l'atrium
            fill(3 * side, FLOOR + STOREY + 1, -6, 7 * side, FLOOR + STOREY + 1, -6, rail);
        }
        // la borne du retour, a droite en sortant
        put(-6, FLOOR + 1, 3, rib);
        terminal(-6, FLOOR + 2, 3, Direction.NORTH, SpawnTerminalBlock.Kind.RETURN, new BlockPos(-6, FLOOR + 3, 3));
    }

    /**
     * Les services du portail et les tableaux de l'atrium : la borne de la
     * recompense quotidienne et celle des departs, encastrees dans les deux
     * piles du portail ; au premier etage, deux panneaux noirs sur les piles
     * du milieu, devant lesquels flottent le classement du concours et le
     * profil du joueur.
     */
    private void services() {
        SpawnTerminalBlock.Kind[] kinds = {SpawnTerminalBlock.Kind.DAILY, SpawnTerminalBlock.Kind.PORTALS};
        for (int index = 0; index < 2; index++) {
            int x = index == 0 ? -9 : 9;
            terminal(x, FLOOR + 2, CORE_SOUTH, Direction.SOUTH, kinds[index],
                    new BlockPos(x, FLOOR + 4, CORE_SOUTH + 1));
            put(x, FLOOR + 3, CORE_SOUTH, accentFor(kinds[index]));
            put(x, FLOOR, CORE_SOUTH + 2, lamp);
            fill(x, FLOOR + 9, -20, x, FLOOR + 12, -18, black);
        }
        labels.add(new Label(BOARD_CONTEST, "...", 0xFFFFFF, BOARD_SCALE));
        labels.add(new Label(BOARD_PROFILE, "...", 0xFFFFFF, BOARD_SCALE));
        labels.add(new Label(new BlockPos(BOARD_CONTEST.getX(), FLOOR + 12, -19), "CONCOURS DE TRIXIUM", 0xC77DFF,
                1.1f));
        labels.add(new Label(new BlockPos(BOARD_PROFILE.getX(), FLOOR + 12, -19), "TON PROFIL", 0xFAC400, 1.1f));
    }

    /**
     * Le decor : un tapis rouge de l'entree a l'atrium, des murs de tonneaux
     * dans les travees, deux bassins de brique dans la nef ; au premier etage
     * un salon (un ecran, des bancs) et deux bibliotheques ; au second, des
     * jardinieres et des bancs.
     */
    private void decor() {
        BlockState barrel = with(block("barrel"), "facing", "up");
        BlockState shelf = block("bookshelf");
        BlockState carpet = block("red_carpet");
        BlockState water = block("water");
        BlockState lily = block("lily_pad");
        BlockState table = with(rail, "type", "top");
        int first = FLOOR + STOREY;
        int second = FLOOR + 2 * STOREY;
        fill(-1, FLOOR + 1, -15, 1, FLOOR + 1, 6, carpet);
        for (int side = -1; side <= 1; side += 2) {
            // les tonneaux : un mur de reserve contre la masse haute, rythme de pilastres
            for (int z = -26; z <= -12; z++) {
                fill(11 * side, FLOOR + 1, z, 11 * side, FLOOR + 4, z, Math.floorMod(z, 5) == 4 ? rib : barrel);
                put(11 * side, FLOOR + 5, z, rib);
            }
            // le bassin : une margelle de brique, de l'eau, deux nenuphars
            for (int x = 9; x <= 14; x++) {
                for (int z = 0; z <= 3; z++) {
                    put(x * side, FLOOR + 1, z, x == 9 || x == 14 || z == 0 || z == 3 ? brick : water);
                }
            }
            put(11 * side, FLOOR + 2, 1, lily);
            put(13 * side, FLOOR + 2, 2, lily);
            // la bibliotheque du premier etage : deux rayonnages, une table, une jardiniere
            for (int row = 15; row <= 21; row += 6) {
                fill(row * side, first + 1, -12, row * side, first + 3, -6, shelf);
                fill(row * side, first + 4, -12, row * side, first + 4, -6, rail);
            }
            fill(18 * side, first + 1, -10, 18 * side, first + 1, -8, table);
            planter(25 * side, -10, first, 1);
            // le second etage : une jardiniere et son banc
            planter(15 * side, -11, second, 1);
            fill(13 * side, second + 1, -9, 16 * side, second + 1, -9, rail);
        }
        // le salon du premier etage : un ecran a mire, un banc devant
        fill(-3, first + 1, -2, 3, first + 4, -2, black);
        String[] bars = {"white_concrete", "yellow_concrete", "light_blue_concrete", "lime_concrete",
                "magenta_concrete"};
        for (int index = 0; index < bars.length; index++) {
            fill(index - 2, first + 2, -2, index - 2, first + 3, -2, block(bars[index]));
        }
        fill(-2, first + 1, -5, 2, first + 1, -5, rail);
        fill(-2, second + 1, -7, 2, second + 1, -7, rail);
    }

    /**
     * Un escalier d'un etage : sept marches de pierre taillee vers le centre
     * du batiment, pleines jusqu'au sol, entre deux limons massifs. La dalle
     * et les poutres du dessus sont ouvertes la ou la tete passerait ; la
     * tremie recoit un garde-corps.
     */
    private void stair(int side, int z1, int z2, int bottom, int base) {
        BlockState tread = with(block("stone_brick_stairs"), "facing", side > 0 ? "west" : "east");
        for (int step = 0; step < STOREY; step++) {
            int x = (bottom - step) * side;
            int level = base + step;
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                if (z < z1 || z > z2) {
                    // le limon : un bloc plus haut que la marche
                    fill(x, base, z, x, level + 1, z, rib);
                    if (step >= 1) {
                        guard(x, base + 7, z);
                    }
                    continue;
                }
                if (step >= 2) {
                    carve(x, base + 5, z, x, base + 6, z);
                }
                if (step > 0) {
                    fill(x, base, z, x, level - 1, z, rib);
                }
                put(x, level, z, tread);
            }
        }
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            guard((bottom - 1) * side, base + 7, z);
        }
        // en haut, le limon arrive a hauteur de garde-corps : il recoit sa main courante
        put((bottom - 6) * side, base + 8, z1 - 1, rail);
        put((bottom - 6) * side, base + 8, z2 + 1, rail);
    }

    /** Un garde-corps de pierre sous une main courante de bois, la ou il y a un sol et rien dessus. */
    private void guard(int x, int y, int z) {
        if (has(x, y - 1, z) && !has(x, y, z)) {
            put(x, y, z, rib);
            if (!has(x, y + 1, z)) {
                put(x, y + 1, z, rail);
            }
        }
    }

    /**
     * Le socle : deux bandes plantees au pied du premier gradin, en marches,
     * tenues par des murs de pierre qui en suivent la courbe. Elles
     * s'interrompent devant le porche et autour du pylone ; derriere, il n'y
     * en a qu'une.
     */
    private void base() {
        for (int x = -50; x <= 50; x++) {
            for (int z = -44; z <= 16; z++) {
                double distance = reach(0, x, z) - TIERS[0][2];
                // derriere le batiment, une seule bande : l'industrie a besoin de place
                if (distance <= 0.5d || distance > (z < -26 ? 4.5d : 8.5d) || !inPlaza(x, z)
                        || plaza(x, z) > 0.9d || Math.hypot(Math.abs(x) - 34, z + 39) < 7.0d) {
                    continue;
                }
                boolean porch = z > 0 && Math.abs(x) <= 11;
                boolean passage = x < 0 && z >= -22 && z <= -10;
                boolean cheek = (porch && Math.abs(x) == 11) || (passage && (z == -22 || z == -10))
                        || (distance > 4.5d && z == -26);
                if ((porch || passage) && !cheek) {
                    continue;
                }
                int height = distance <= 4.5d ? 2 : 1;
                boolean kerb = cheek || (distance > 3.5d && distance <= 4.5d) || distance > 7.5d;
                if (kerb) {
                    fill(x, FLOOR + 1, z, x, FLOOR + height, z, rib);
                    put(x, FLOOR + height + 1, z, half);
                    continue;
                }
                fill(x, FLOOR + 1, z, x, FLOOR + height, z, dirt);
                put(x, FLOOR + height, z, hash(x, 2, z) % 4 == 0 ? moss : grass);
                int roll = hash(x, 4, z) % 100;
                if (roll < 28) {
                    put(x, FLOOR + height + 1, z, roll < 17 ? bush : shrub);
                }
            }
        }
    }

    /** Les portes : l'entree, puis de chaque galerie vers sa terrasse, au sud et au bout. */
    private void doors() {
        carve(-4, FLOOR + 1, 5, 4, FLOOR + 5, 6);
        fill(-4, FLOOR, 5, 4, FLOOR, 6, darkConcrete);
        for (int side = -1; side <= 1; side += 2) {
            carve(9 * side, FLOOR + 8, 0, 10 * side, FLOOR + 10, 1);
            carve(29 * side, FLOOR + 8, -17, 30 * side, FLOOR + 10, -15);
            carve(5 * side, FLOOR + 15, -5, 6 * side, FLOOR + 17, -4);
            carve(21 * side, FLOOR + 15, -17, 22 * side, FLOOR + 17, -15);
        }
    }

    /** Le porche : deux voiles, une dalle epaisse qui sert de balcon a la premiere terrasse. */
    private void porch() {
        for (int side = -1; side <= 1; side += 2) {
            fill(8 * side, FLOOR + 1, 7, 8 * side, FLOOR + 6, 12, wall);
            fill(8 * side, FLOOR + 8, 8, 8 * side, FLOOR + 8, 12, rib);
            fill(8 * side, FLOOR + 9, 8, 8 * side, FLOOR + 9, 12, half);
            put(4 * side, FLOOR + 7, 10, lamp);
            put(6 * side, FLOOR, 9, lamp);
        }
        fill(-8, FLOOR + 7, 7, 8, FLOOR + 7, 12, edge);
        fill(-7, FLOOR + 6, 12, 7, FLOOR + 6, 12, rib);
        fill(-8, FLOOR + 8, 12, 8, FLOOR + 8, 12, rib);
        fill(-8, FLOOR + 9, 12, 8, FLOOR + 9, 12, half);
        carve(-7, FLOOR + 8, 7, 7, FLOOR + 9, 7);
        for (int side = -1; side <= 1; side += 2) {
            put(4 * side, FLOOR + 7, 10, lamp);
        }
        for (int x = -7; x <= 7; x++) {
            for (int z = 7; z <= 12; z++) {
                if (Math.abs(x) > 3) {
                    put(x, FLOOR, z, paving);
                }
            }
        }
        labels.add(new Label(new BlockPos(0, FLOOR + 11, 13), "DOHY", 0xEF3826, 5.0f));
    }

    /**
     * La verdure des terrasses : des buissons le long des garde-corps, cote
     * sud, et quelques epiceas — dont un qui pousse au milieu de l'anneau.
     */
    private void greenery() {
        for (int tier = 0; tier < TIERS.length; tier++) {
            int axis = TIERS[tier][0];
            int span = TIERS[tier][1] + TIERS[tier][2];
            int roof = FLOOR + TIERS[tier][3] + STOREY;
            for (int x = -span; x <= span; x++) {
                for (int z = axis - TIERS[tier][2] + 5; z <= axis + TIERS[tier][2]; z++) {
                    if (zone(tier, x, z) != SKIN || (tier == 0 && Math.abs(x) <= 9 && z > 0) || (tier == 2 && z < -8)
                            || (x < 0 && z >= -20 && z <= -12) || (x > 0 && Math.hypot(x - 35, z + 15) < 15.0d)) {
                        continue;
                    }
                    int roll = hash(x, roof, z) % 100;
                    if (roll < 38 && !has(x, roof + 1, z)) {
                        put(x, roof + 1, z, roll < 22 ? bush : shrub);
                        if (roll < 5) {
                            put(x, roof + 2, z, bush);
                        }
                    }
                }
            }
        }
        int[][] trees = {
                // x, z, niveau du toit au-dessus de FLOOR, hauteur
                {35, -15, 7, 10}, {-34, -4, 7, 6}, {-24, -6, 14, 5}, {25, -26, 14, 6}, {20, -21, 21, 5},
                {-20, -21, 21, 6}, {30, 0, 7, 5},
        };
        for (int[] tree : trees) {
            put(tree[0], FLOOR + tree[2], tree[1], moss);
            spruce(tree[0], FLOOR + tree[2] + 1, tree[1], tree[3]);
        }
    }

    /**
     * L'anneau : une galerie ronde au niveau du toit du deuxieme gradin, qui
     * deborde a l'est au-dessus de la premiere terrasse et du parvis, portee
     * par des pilotis.
     */
    private void ring() {
        int cx = 35;
        int cz = -15;
        int level = FLOOR + 2 * STOREY;
        double outer = 13.5d;
        double inner = 8.5d;
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                if (distance > outer || distance < inner || zone(2, x, z) >= SKIN) {
                    continue;
                }
                boolean lip = distance > outer - 1.0d || distance < inner + 1.0d;
                if (zone(1, x, z) != OUT) {
                    // sur le toit du gradin : rien a porter, il suffit d'ouvrir le garde-corps
                    if (!lip) {
                        carve(x, level + 1, z, x, level + 2, z);
                    }
                    continue;
                }
                put(x, level, z, lip ? rib : edge);
                if (lip) {
                    put(x, level - 1, z, wall);
                    put(x, level + 1, z, rib);
                    put(x, level + 2, z, half);
                }
            }
        }
        for (int step = 0; step < 12; step++) {
            double angle = Math.PI / 6 * step;
            int x = cx + (int) Math.round(Math.cos(angle) * 11.0d);
            int z = cz + (int) Math.round(Math.sin(angle) * 11.0d);
            if (zone(1, x, z) != OUT) {
                continue;
            }
            fill(x, zone(0, x, z) != OUT ? FLOOR + STOREY + 1 : FLOOR + 1, z, x, level - 1, z, wall);
            put(x, level, z, lamp);
        }
    }

    /**
     * Le pylone et ses trois passerelles : deux tubes fermes, fendus d'un
     * bandeau ouvert, vers les deux galeries ; un pont decouvert vers le toit
     * du troisieme gradin. Une echelle monte dans le pylone jusqu'au belvedere.
     */
    private void pylon() {
        int x1 = -49;
        int x2 = -41;
        int z1 = -19;
        int z2 = -13;
        int top = FLOOR + 44;
        for (int y = FLOOR + 1; y < top; y++) {
            boolean course = (y - FLOOR) % STOREY == 0;
            boolean open = y >= FLOOR + 40 && y <= FLOOR + 42;
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    boolean alongX = x == x1 || x == x2;
                    boolean alongZ = z == z1 || z == z2;
                    if (!alongX && !alongZ) {
                        carve(x, y, z, x, y, z);
                    } else if (!open || (alongX && alongZ)) {
                        put(x, y, z, course ? rib : wall);
                    }
                }
            }
        }
        fill(x1 - 1, top, z1 - 1, x2 + 1, top, z2 + 1, edge);
        fill(x1, top + 1, z1, x2, top + 1, z2, rib);
        put(-45, top - 1, -16, flood);
        fill(-45, top + 2, -16, -45, top + 8, -16, bars);
        put(-45, top + 9, -16, red);
        for (int level : new int[] {STOREY, 2 * STOREY, 3 * STOREY, 38}) {
            fill(x1 + 1, FLOOR + level, z1 + 1, x2 - 1, FLOOR + level, z2 - 1, edge);
            put(-45, FLOOR + level, -16, lamp);
        }
        fill(x1 + 1, FLOOR + 1, -16, x1 + 1, FLOOR + 38, -16, ladder);
        // des fentes de verre sur les faces nord et ouest
        for (int y = FLOOR + 3; y <= FLOOR + 36; y++) {
            if ((y - FLOOR) % STOREY == 0) {
                continue;
            }
            put(-45, y, z1, window);
            put(x1, y, -18, window);
            put(x1, y, -14, window);
        }
        // des pilastres sur les faces nord et sud
        for (int x = x1; x <= x2; x += 2) {
            fill(x, FLOOR + 1, z2 + 1, x, top - 1, z2 + 1, wall);
            fill(x, FLOOR + 1, z1 - 1, x, top - 1, z1 - 1, wall);
        }
        int[] levels = {STOREY, 2 * STOREY, 3 * STOREY};
        int[] ends = {-31, -23, -23};
        int[] passage = {-35, -27, 0};
        for (int index = 0; index < 3; index++) {
            int level = FLOOR + levels[index];
            carve(x2, level + 1, -17, x2, level + 3, -15);
            for (int x = x2 + 1; x <= ends[index]; x++) {
                for (int z = z1; z <= z2; z++) {
                    boolean side = z == z1 || z == z2;
                    if (!has(x, level, z)) {
                        put(x, level, z, side ? rib : edge);
                    }
                    if (index == 2) {
                        if (side) {
                            put(x, level + 1, z, rib);
                        } else {
                            carve(x, level + 1, z, x, level + 2, z);
                        }
                        continue;
                    }
                    carve(x, level + 1, z, x, level + 4, z);
                    if (side) {
                        // la ou la passerelle coupe une terrasse, on passe dessous
                        if (x != passage[index] && x != passage[index] + 1) {
                            put(x, level + 1, z, rib);
                        }
                        put(x, level + 4, z, wall);
                    }
                    put(x, level + 5, z, side ? rib : edge);
                }
            }
            if (index < 2) {
                put(x2 + 4, level + 5, -16, lamp);
                put(ends[index] - 2, level + 5, -16, lamp);
            }
        }
        // l'entree du pylone, au pied
        carve(x2, FLOOR + 1, -17, x2, FLOOR + 3, -15);
        // le signe : une dalle debout au bout des passerelles, face a l'arrivee
        fill(-37, FLOOR + STOREY + 1, -12, -31, FLOOR + 27, -11, wall);
        fill(-37, FLOOR + 27, -12, -31, FLOOR + 27, -11, rib);
        carve(-35, FLOOR + STOREY + 1, -12, -34, FLOOR + STOREY + 3, -11);
        String[] glyph = {"XXXX.", "X...X", "X...X", "X...X", "X...X", "X...X", "XXXX."};
        for (int row = 0; row < glyph.length; row++) {
            for (int column = 0; column < 5; column++) {
                if (glyph[row].charAt(column) == 'X') {
                    put(-36 + column, FLOOR + 24 - row, -11, white);
                }
            }
        }
    }

    /** L'industrie, derriere le batiment : deux cheminees qui fument, deux cuves, les tuyaux qui les relient. */
    private void industry() {
        for (int side = -1; side <= 1; side += 2) {
            int cx = 34 * side;
            int cz = -39;
            cylinder(cx, cz, FLOOR + 1, FLOOR + 40, 3.0d, true, concrete);
            cylinder(cx, cz, FLOOR + 1, FLOOR + 2, 3.6d, true, steel);
            cylinder(cx, cz, FLOOR + 33, FLOOR + 35, 3.0d, true, red);
            cylinder(cx, cz, FLOOR + 36, FLOOR + 37, 3.0d, true, white);
            cylinder(cx, cz, FLOOR + 38, FLOOR + 38, 3.0d, false, block("hay_block"));
            put(cx, FLOOR + 39, cz, with(with(block("campfire"), "lit", "true"), "signal_fire", "true"));
            // la cuve
            int tx = 22 * side;
            int tz = -45;
            cylinder(tx, tz, FLOOR + 1, FLOOR + 6, 2.6d, false, white);
            cylinder(tx, tz, FLOOR + 3, FLOOR + 3, 2.6d, true, darkConcrete);
            cylinder(tx, tz, FLOOR + 7, FLOOR + 7, 1.6d, false, iron);
            // le tuyau : de la cuve vers la cheminee
            fill(tx, FLOOR + 8, tz, tx, FLOOR + 9, tz, pipe);
            fill(tx, FLOOR + 9, tz, tx, FLOOR + 9, cz, pipe);
            fill(tx, FLOOR + 9, cz, cx - 3 * side, FLOOR + 9, cz, pipe);
            put(tx, FLOOR + 9, cz, iron);
            put(tx + 6 * side, FLOOR + 9, cz, iron);
            fill(tx, FLOOR + 1, cz, tx, FLOOR + 8, cz, post);
        }
    }
}
