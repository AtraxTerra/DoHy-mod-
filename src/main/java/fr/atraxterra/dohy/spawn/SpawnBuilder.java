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
 * pitons. Sur le massif, une dalle de beton — la place — porte le hall des
 * box (six travees entre des voiles de beton, sous un toit en porte-a-faux,
 * adosse a une tour), deux pavillons de bornes, deux cheminees qui fument, des
 * cuves et leurs tuyaux. Un pont part de la place vers une plateforme ronde
 * posee sur son propre piton : c'est la qu'on arrive. Des mats d'eclairage
 * coiffent les pitons.
 *
 * <p>Tout est calcule, rien n'est lu : la roche vient de quelques sinus, le
 * beton de boites. Le resultat est une liste de blocs, que {@link SpawnApi}
 * pose par tranches pour ne pas figer le jeu. Les blocs sont nommes par leur
 * identifiant : un bloc absent de cette version du jeu devient de la pierre
 * au lieu d'empecher le mod de se charger.
 */
final class SpawnBuilder {

    /** A augmenter des que le dessin change : un spawn d'une autre version est reconstruit. */
    static final int VERSION = 1;

    /** Le niveau du sol de la place : on marche a {@code FLOOR + 1}. */
    static final int FLOOR = 200;
    /** Le centre de la plateforme d'arrivee. */
    static final int PAD_Z = 78;
    /** En dessous, un joueur est tombe : il est remis sur la plateforme. */
    static final int VOID_Y = 140;
    /** Le volume bati, vide avant une reconstruction : x et z de part et d'autre, puis y. */
    static final int CLEAR_XZ = 46;
    static final int CLEAR_Y_MIN = 197;
    static final int CLEAR_Y_MAX = 264;

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
    private final BlockState glass = block("yellow_stained_glass");
    private final BlockState stone = block("stone");
    private final BlockState andesite = block("andesite");
    private final BlockState tuff = block("tuff");
    private final BlockState cobble = block("cobblestone");
    private final BlockState grass = block("grass_block");
    private final BlockState moss = block("moss_block");

    private static BlockState block(String name) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(name))
                .map(Block::defaultBlockState).orElseGet(Blocks.STONE::defaultBlockState);
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

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    put(x, y, z, state);
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

    // -- Le plan ---------------------------------------------------------------------------

    static Plan plan() {
        SpawnBuilder builder = new SpawnBuilder();
        builder.rocks();
        builder.plaza();
        builder.bridge();
        builder.pad();
        builder.hall();
        builder.tower();
        builder.pavilions();
        builder.industry();
        builder.centre();
        return new Plan(builder.blocks, builder.labels);
    }

    /** La roche d'abord : tout le reste se pose dessus et la remplace ou elle gene. */
    private void rocks() {
        spire(0, 0, 122, FLOOR - 4, 20.0d, 41.0d, 1);
        spire(0, PAD_Z, 128, FLOOR - 3, 8.0d, 15.5d, 2);
        int[][] ring = {
                // x, z, sommet, ventre, couronne, mat (1) ou non (0)
                {-74, -52, 252, 15, 8, 1}, {78, -46, 238, 13, 7, 0}, {-98, 22, 217, 12, 7, 1},
                {100, 30, 262, 16, 9, 1}, {-62, 88, 230, 12, 7, 0}, {66, 98, 207, 11, 7, 1},
                {0, -108, 276, 17, 10, 0}, {-118, -72, 199, 11, 6, 0}, {120, -82, 224, 12, 7, 1},
                {42, -90, 242, 10, 6, 0}, {-36, -94, 214, 9, 6, 0},
        };
        int seed = 3;
        for (int[] rock : ring) {
            spire(rock[0], rock[1], rock[2] - 96, rock[2], rock[3], rock[4], seed++);
            if (rock[5] == 1) {
                mast(rock[0], rock[2] + 1, rock[1]);
            }
        }
    }

    /** Un mat d'eclairage : une aiguille de beton, un panneau de projecteurs tourne vers la place. */
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

    private static boolean inPlaza(int x, int z) {
        return Math.abs(x) <= 34 && Math.abs(z) <= 34 && Math.abs(x) + Math.abs(z) <= 50;
    }

    /** La place : une dalle a pans coupes, ses joints, ses lampes de sol, son garde-corps. */
    private void plaza() {
        for (int x = -34; x <= 34; x++) {
            for (int z = -34; z <= 34; z++) {
                if (!inPlaza(x, z)) {
                    continue;
                }
                boolean rim = !inPlaza(x + 1, z) || !inPlaza(x - 1, z) || !inPlaza(x, z + 1) || !inPlaza(x, z - 1);
                boolean nearRim = !inPlaza(x + 2, z) || !inPlaza(x - 2, z) || !inPlaza(x, z + 2)
                        || !inPlaza(x, z - 2);
                fill(x, FLOOR - 3, z, x, FLOOR - 1, z, rim ? steel : darkConcrete);
                boolean joint = Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0;
                BlockState top = joint ? darkConcrete : concrete;
                if (Math.floorMod(x, 12) == 0 && Math.floorMod(z, 12) == 0) {
                    top = lamp;
                }
                if (nearRim && !rim) {
                    top = ((x + z) & 1) == 0 ? yellow : black;
                }
                put(x, FLOOR, z, rim ? steel : top);
                // le garde-corps, ouvert sur le pont
                if (rim && !(z == 34 && Math.abs(x) <= 3)) {
                    put(x, FLOOR + 1, z, darkConcrete);
                    put(x, FLOOR + 2, z, bars);
                }
            }
        }
    }

    /** Le pont : un tablier, deux poutres, des tuyaux qui courent dessous, des lampadaires. */
    private void bridge() {
        for (int z = 35; z <= 66; z++) {
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
                if (Math.floorMod(z, 8) == 2) {
                    lampPost(3 * side, FLOOR + 3, z, 2);
                }
            }
        }
        // deux beches de beton sous le milieu du pont
        for (int side = -1; side <= 1; side += 2) {
            for (int step = 0; step <= 14; step++) {
                fill(2 * side, FLOOR - 2 - step, 50 - step / 2, 3 * side, FLOOR - 2 - step, 51 + step / 2, concrete);
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
        terminal(5, FLOOR + 1, PAD_Z + 5, Direction.NORTH, SpawnTerminalBlock.Kind.RETURN);
        labels.add(new Label(new BlockPos(0, FLOOR + 5, PAD_Z - 9), "SPAWN", 0xFFFFFF, 3.0f));
        labels.add(new Label(new BlockPos(0, FLOOR + 3, PAD_Z - 9), "Le hall des box est au bout du pont", 0xFAC400,
                1.0f));
    }

    private void terminal(int x, int y, int z, Direction facing, SpawnTerminalBlock.Kind kind) {
        put(x, y, z, DoHySpawn.TERMINAL.get().defaultBlockState()
                .setValue(SpawnTerminalBlock.FACING, facing).setValue(SpawnTerminalBlock.KIND, kind));
        labels.add(new Label(new BlockPos(x, y + 1, z), kind.label(), kind.color(), 1.0f));
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

    /**
     * Le hall des box : un mur, sept voiles de beton au sommet en biseau, six
     * travees, un toit epais qui deborde. Chaque travee a sa box sur un socle,
     * sa bande de couleur et son nom.
     */
    private void hall() {
        int back = -30;
        // le sol du hall
        fill(-25, FLOOR, back, 25, FLOOR, -15, darkConcrete);
        for (int x = -25; x <= 25; x++) {
            put(x, FLOOR, -15, ((x & 1) == 0) ? yellow : black);
        }
        // le mur du fond, strie comme un beton banche
        for (int y = FLOOR + 1; y <= FLOOR + 15; y++) {
            fill(-25, y, back, 25, y, back + 2, (y - FLOOR) % 4 == 0 ? darkConcrete : concrete);
        }
        // les voiles
        for (int fin = -24; fin <= 24; fin += 8) {
            for (int z = back + 3; z <= -19; z++) {
                int height = z <= -23 ? 14 : 14 - (z + 23) * 2;
                fill(fin, FLOOR + 1, z, fin, FLOOR + height, z, concrete);
            }
        }
        // le toit, en porte-a-faux
        fill(-27, FLOOR + 16, back - 1, 27, FLOOR + 18, -14, darkConcrete);
        fill(-27, FLOOR + 17, -14, 27, FLOOR + 17, -14, concrete);
        fill(-27, FLOOR + 19, back - 1, 27, FLOOR + 19, -22, concrete);
        // les travees
        List<DeferredBlock<BoxBlock>> boxes = PalaBoxes.BOXES;
        for (int index = 0; index < boxes.size() && index < 6; index++) {
            int cx = -20 + index * 8;
            BoxBlock box = boxes.get(index).get();
            BoxKind kind = box.kind();
            fill(cx - 2, FLOOR + 4, back + 2, cx + 2, FLOOR + 5, back + 2, accent(kind));
            fill(cx - 3, FLOOR + 10, back + 2, cx + 3, FLOOR + 10, back + 2, accent(kind));
            fill(cx - 1, FLOOR + 1, -25, cx + 1, FLOOR + 1, -23, steel);
            put(cx, FLOOR + 2, -24, box.defaultBlockState().setValue(BoxBlock.FACING, Direction.SOUTH));
            put(cx, FLOOR + 15, -21, lamp);
            put(cx, FLOOR + 15, -17, lamp);
            labels.add(new Label(new BlockPos(cx, FLOOR + 7, -24), "BOX " + kind.label().toUpperCase(),
                    accentColor(kind), 1.6f));
        }
        labels.add(new Label(new BlockPos(0, FLOOR + 21, -15), "HALL DES BOX", 0xFFFFFF, 4.0f));
        labels.add(new Label(new BlockPos(0, FLOOR + 3, -14), "Clic sur une box avec sa cle  -  les cles sont a la borne BOUTIQUE",
                0xC9D2DA, 0.9f));
    }

    /** La tour derriere le hall : une masse de beton fendue de lumiere, une antenne. */
    private void tower() {
        fill(-9, FLOOR - 3, -45, 9, FLOOR + 45, -32, concrete);
        for (int y = FLOOR + 1; y <= FLOOR + 45; y += 5) {
            fill(-9, y, -45, 9, y, -32, darkConcrete);
        }
        for (int x = -6; x <= 6; x += 3) {
            for (int y = FLOOR + 22; y <= FLOOR + 42; y++) {
                if ((y - FLOOR) % 5 == 1) {
                    continue;
                }
                put(x, y, -32, glass);
                put(x, y, -33, flood);
                put(x, y, -45, glass);
                put(x, y, -44, flood);
            }
        }
        fill(-11, FLOOR + 46, -47, 11, FLOOR + 47, -30, darkConcrete);
        fill(0, FLOOR + 48, -38, 0, FLOOR + 62, -38, bars);
        fill(-1, FLOOR + 48, -39, 1, FLOOR + 50, -37, steel);
        put(0, FLOOR + 63, -38, red);
    }

    /** Les deux pavillons de bornes : un toit plat sur quatre gros poteaux, trois guichets. */
    private void pavilions() {
        SpawnTerminalBlock.Kind[][] kinds = {
                {SpawnTerminalBlock.Kind.SHOP, SpawnTerminalBlock.Kind.BOUTIQUE, SpawnTerminalBlock.Kind.MARKET},
                {SpawnTerminalBlock.Kind.BANK, SpawnTerminalBlock.Kind.CONTRACTS, SpawnTerminalBlock.Kind.MUSEUM},
        };
        for (int index = 0; index < 2; index++) {
            int side = index == 0 ? 1 : -1;
            fill(21 * side, FLOOR, -9, 32 * side, FLOOR, 9, darkConcrete);
            fill(32 * side, FLOOR + 1, -9, 32 * side, FLOOR + 5, 9, concrete);
            fill(20 * side, FLOOR + 6, -10, 33 * side, FLOOR + 7, 10, darkConcrete);
            fill(20 * side, FLOOR + 7, -10, 20 * side, FLOOR + 7, 10, concrete);
            for (int corner = -1; corner <= 1; corner += 2) {
                fill(21 * side, FLOOR + 1, 8 * corner, 22 * side, FLOOR + 5, 9 * corner, concrete);
                fill(30 * side, FLOOR + 1, 8 * corner, 31 * side, FLOOR + 5, 9 * corner, concrete);
            }
            for (int slot = 0; slot < 3; slot++) {
                int z = -5 + slot * 5;
                fill(30 * side, FLOOR + 1, z - 1, 31 * side, FLOOR + 1, z + 1, steel);
                terminal(30 * side, FLOOR + 2, z, side > 0 ? Direction.WEST : Direction.EAST, kinds[index][slot]);
                put(31 * side, FLOOR + 3, z, accentFor(kinds[index][slot]));
                put(27 * side, FLOOR + 5, z, lamp);
            }
            labels.add(new Label(new BlockPos(20 * side, FLOOR + 9, 0), index == 0 ? "COMMERCE" : "CARRIERE",
                    0xFFFFFF, 2.2f));
        }
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
        });
    }

    /** L'industrie : deux cheminees qui fument, deux cuves, les tuyaux qui les relient. */
    private void industry() {
        for (int side = -1; side <= 1; side += 2) {
            int cx = 20 * side;
            int cz = 24;
            cylinder(cx, cz, FLOOR + 1, FLOOR + 40, 3.0d, true, concrete);
            cylinder(cx, cz, FLOOR + 1, FLOOR + 2, 3.6d, true, steel);
            cylinder(cx, cz, FLOOR + 33, FLOOR + 35, 3.0d, true, red);
            cylinder(cx, cz, FLOOR + 36, FLOOR + 37, 3.0d, true, white);
            cylinder(cx, cz, FLOOR + 38, FLOOR + 38, 3.0d, false, block("hay_block"));
            put(cx, FLOOR + 39, cz, with(with(block("campfire"), "lit", "true"), "signal_fire", "true"));
            // la cuve
            int tx = 27 * side;
            int tz = 15;
            cylinder(tx, tz, FLOOR + 1, FLOOR + 6, 2.6d, false, white);
            cylinder(tx, tz, FLOOR + 3, FLOOR + 3, 2.6d, true, darkConcrete);
            cylinder(tx, tz, FLOOR + 7, FLOOR + 7, 1.6d, false, iron);
            // le tuyau : de la cuve vers la cheminee, puis le long du garde-corps
            fill(tx, FLOOR + 8, tz, tx, FLOOR + 9, tz, pipe);
            fill(tx, FLOOR + 9, tz, tx, FLOOR + 9, cz, pipe);
            fill(tx, FLOOR + 9, cz, cx + 3 * side, FLOOR + 9, cz, pipe);
            put(tx, FLOOR + 9, cz, iron);
            put(tx, FLOOR + 9, tz + 4, iron);
            fill(tx, FLOOR + 1, cz, tx, FLOOR + 8, cz, post);
        }
    }

    /** Le centre de la place : un pylone de beton eclaire, et le mot de bienvenue. */
    private void centre() {
        cylinder(0, 0, FLOOR + 1, FLOOR + 1, 5.2d, false, steel);
        fill(-1, FLOOR + 2, -1, 1, FLOOR + 12, 1, concrete);
        for (int y = FLOOR + 4; y <= FLOOR + 10; y += 3) {
            put(1, y, 0, lamp);
            put(-1, y, 0, lamp);
            put(0, y, 1, lamp);
            put(0, y, -1, lamp);
        }
        fill(-2, FLOOR + 13, -2, 2, FLOOR + 13, 2, darkConcrete);
        for (int corner = 0; corner < 8; corner++) {
            double angle = Math.PI / 4 * corner + Math.PI / 8;
            lampPost((int) Math.round(Math.cos(angle) * 13), FLOOR + 1, (int) Math.round(Math.sin(angle) * 13), 3);
        }
        terminal(0, FLOOR + 2, 4, Direction.SOUTH, SpawnTerminalBlock.Kind.RETURN);
        put(0, FLOOR + 1, 4, steel);
        labels.add(new Label(new BlockPos(0, FLOOR + 15, 0), "DOHY", 0xEF3826, 5.0f));
    }
}
