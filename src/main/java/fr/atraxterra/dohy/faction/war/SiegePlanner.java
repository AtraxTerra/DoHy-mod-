package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.base.NtmDefenses;
import fr.atraxterra.dohy.palamod.defense.DefenseObsidianBlock;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import fr.atraxterra.dohy.palamod.pvp.SpikeBlock;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le planificateur de siege : par ou entrer chez toi.
 *
 * <p><b>Le releve</b> ({@link #capture}, sur le fil du serveur) : un cube
 * autour de ton coffre, de la hauteur de tes murs, ou chaque bloc devient une
 * classe — air, eau, lave, porte en bois, bloc a creuser (tendre, moyen, dur),
 * bloc a faire sauter (a la TNT, a la TNT compacte, a l'endium pour
 * l'obsidienne), bloc infranchissable (roche-mere, conteneurs et machines,
 * que le siege epargne) — et des marques : trop haut pour y monter (barriere,
 * muret), dangereux (pics, magma, cactus), piege (obsidiennes piegees, mines),
 * grimpable (tes echelles, tes lianes), dans ton claim, dans un claim
 * d'autrui (on n'y casse rien). Les tourelles marquent les colonnes qu'elles
 * couvrent, et chaque soldat tombe marque sa case : l'armee apprend de ses
 * morts.
 *
 * <p><b>La recherche</b> ({@link #search}, sur un fil a part) : un A* sur les
 * positions ou un soldat tient debout, depuis toutes les cases d'ou un camp
 * peut partir (hors de tes claims, a 16 a 48 blocs de leur bord) jusqu'au
 * coffre. Le cout d'un pas est un temps : marcher, nager, sauter d'un bloc,
 * se laisser tomber (au-dela de trois blocs, la chute se paie), poser une
 * planche sur le vide ou la lave, creuser ou faire sauter ce qui bloque,
 * poser une echelle contre un mur, creuser vers le bas pour passer sous une
 * muraille. Ce qui fait mal coute plus cher. Le chemin le moins cher dit tout
 * le plan : ou dresser le camp, ou percer, ou grimper, ou jeter un pont —
 * une porte ouverte, une breche deja faite ou un tunnel sous un mur d'obsidienne
 * en sortent d'eux-memes.
 */
final class SiegePlanner {

    // -- Les classes de bloc ---------------------------------------------------------------------

    static final byte AIR = 0;
    static final byte WATER = 1;
    static final byte LAVA = 2;
    static final byte DOOR = 3;
    static final byte SOFT = 4;
    static final byte MEDIUM = 5;
    static final byte HARD = 6;
    static final byte BLAST = 7;
    static final byte HEAVY = 8;
    static final byte OBSIDIAN = 9;
    static final byte NEVER = 10;

    // -- Les marques -----------------------------------------------------------------------------

    static final byte TALL = 1;
    static final byte DANGER = 2;
    static final byte TRAP = 4;
    static final byte CLIMB = 8;
    static final byte OWNED = 16;
    static final byte FORBID = 32;

    // -- Les pas ---------------------------------------------------------------------------------

    static final byte M_START = 0;
    static final byte M_WALK = 1;
    static final byte M_SWIM = 2;
    static final byte M_DROP = 3;
    static final byte M_BRIDGE = 4;
    static final byte M_UP = 5;
    static final byte M_LADDER = 6;
    static final byte M_DIG_DOWN = 7;
    static final byte M_CLIMB = 8;

    /** Les charges : TNT, TNT compacte, TNT en endium. */
    static final float CHARGE_TNT = 4.0f;
    static final float CHARGE_BIG = 6.0f;
    static final float CHARGE_ENDIUM = 8.8f;

    private static final int MAX_DROP = 6;
    private static final int MAX_LADDER = 12;
    private static final int MAX_EXPANDED = 500_000;
    private static final float INF = Float.MAX_VALUE;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Ce que la nation sait faire : les charges qu'elle a, ses ingenieurs. */
    record Capabilities(boolean heavy, boolean endium) {
    }

    /** Le releve d'une zone de siege. */
    static final class Snapshot {
        final int x0;
        final int y0;
        final int z0;
        final int w;
        final int h;
        final int l;
        final byte[] cls;
        final byte[] flags;
        /** Le danger de chaque colonne : tourelles, morts. */
        final float[] column;
        /** La hauteur ou l'on tient debout en surface, par colonne (y local), -1 sans sol. */
        final int[] surface;
        /** Les colonnes d'ou un camp peut partir. */
        final boolean[] seed;
        final int vx;
        final int vy;
        final int vz;

        Snapshot(int x0, int y0, int z0, int w, int h, int l, int vx, int vy, int vz) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.w = w;
            this.h = h;
            this.l = l;
            this.cls = new byte[w * h * l];
            this.flags = new byte[w * h * l];
            this.column = new float[w * l];
            this.surface = new int[w * l];
            this.seed = new boolean[w * l];
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }

        int index(int x, int y, int z) {
            return (y * l + z) * w + x;
        }

        boolean inside(int x, int y, int z) {
            return x >= 0 && x < w && z >= 0 && z < l && y >= 0 && y < h;
        }

        byte cls(int x, int y, int z) {
            return inside(x, y, z) ? cls[index(x, y, z)] : NEVER;
        }

        byte flags(int x, int y, int z) {
            return inside(x, y, z) ? flags[index(x, y, z)] : 0;
        }

        BlockPos world(int x, int y, int z) {
            return new BlockPos(x0 + x, y0 + y, z0 + z);
        }
    }

    /** Un pas du chemin : ou l'on arrive, comment, et ce qu'il a fallu ouvrir. */
    record Step(BlockPos pos, byte move, int arg, List<BlockPos> broken, BlockPos fill) {
    }

    enum Kind {
        BREACH, LADDER, BRIDGE
    }

    /**
     * Un obstacle a lever avant d'avancer.
     *
     * @param first  le premier pas qui le demande ; les ouvriers travaillent depuis le pas d'avant
     * @param cells  les blocs a ouvrir, les echelles a poser, les planches a jeter
     * @param charge pour une breche : 0 a la pioche, sinon la puissance de la charge
     * @param facing pour une echelle : le cote ou elle regarde
     */
    record Obstacle(Kind kind, int first, List<BlockPos> cells, float charge, Direction facing) {
        String describe() {
            return switch (kind) {
                case BREACH -> charge <= 0.0f ? "creuser " + cells.size() + " bloc(s)"
                        : charge >= CHARGE_ENDIUM ? "percer l'obsidienne a l'endium"
                        : charge >= CHARGE_BIG ? "faire sauter " + cells.size() + " bloc(s) a la TNT compacte"
                        : "faire sauter " + cells.size() + " bloc(s) a la TNT";
                case LADDER -> "une echelle de " + cells.size() + " blocs";
                case BRIDGE -> "un pont de " + cells.size() + " planche(s)";
            };
        }
    }

    /** Le plan : le chemin depuis le camp, et les obstacles dans l'ordre. */
    record Plan(List<Step> steps, List<Obstacle> obstacles, double cost, int expanded, double hardness) {
        BlockPos camp() {
            return steps.getFirst().pos();
        }

        BlockPos end() {
            return steps.getLast().pos();
        }

        String summary() {
            if (obstacles.isEmpty()) {
                return "aucun obstacle : le chemin est ouvert";
            }
            List<String> parts = new ArrayList<>();
            for (Obstacle obstacle : obstacles) {
                parts.add(obstacle.describe());
            }
            return obstacles.size() + " obstacle(s) : " + String.join(", ", parts);
        }
    }

    private SiegePlanner() {
    }

    // -- Le releve -------------------------------------------------------------------------------

    /**
     * Releve la zone autour du coffre. Les chunks absents de la memoire comptent
     * comme infranchissables.
     *
     * @param owner  le joueur assiege : ses claims se percent, ceux des autres non
     * @param deaths ou sont tombes des soldats de ce siege
     * @param intel  la part des pieges que la nation connait (sa ruse)
     */
    static Snapshot capture(ServerLevel level, BlockPos vault, UUID owner, int radius, List<BlockPos> deaths,
                            double intel, long salt) {
        int x0 = vault.getX() - radius;
        int z0 = vault.getZ() - radius;
        int w = radius * 2 + 1;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int x = 0; x < w; x += 3) {
            for (int z = 0; z < w; z += 3) {
                LevelChunk chunk = level.getChunkSource().getChunkNow((x0 + x) >> 4, (z0 + z) >> 4);
                if (chunk == null) {
                    continue;
                }
                int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (x0 + x) & 15, (z0 + z) & 15) + 1;
                lowest = Math.min(lowest, top);
                highest = Math.max(highest, top);
            }
        }
        if (lowest == Integer.MAX_VALUE) {
            lowest = vault.getY();
            highest = vault.getY();
        }
        int bottom = Math.max(level.getMinY() + 1, Math.min(lowest - 8, vault.getY() - 20));
        int top = Math.min(level.getMaxY() - 1, Math.max(highest + 12, vault.getY() + 16));
        if (top - bottom > 72) {
            bottom = Math.max(level.getMinY() + 1, vault.getY() - 32);
            top = Math.min(level.getMaxY() - 1, bottom + 72);
        }
        int h = top - bottom + 1;
        Snapshot snap = new Snapshot(x0, bottom, z0, w, h, w, radius, vault.getY() - bottom, radius);

        Map<BlockState, Short> cache = new IdentityHashMap<>();
        int chunkFrom = x0 >> 4;
        int chunkTo = (x0 + w - 1) >> 4;
        int chunkFromZ = z0 >> 4;
        int chunkToZ = (z0 + w - 1) >> 4;
        int cw = chunkTo - chunkFrom + 1;
        int cl = chunkToZ - chunkFromZ + 1;
        // 0 hors claim, 1 a toi, 2 a autrui (ou base ennemie), 3 absent de la memoire
        byte[] claims = new byte[cw * cl];
        List<BlockPos> turrets = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int cx = chunkFrom; cx <= chunkTo; cx++) {
            for (int cz = chunkFromZ; cz <= chunkToZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                int ci = (cz - chunkFromZ) * cw + (cx - chunkFrom);
                int fromX = Math.max(x0, cx << 4) - x0;
                int toX = Math.min(x0 + w - 1, (cx << 4) + 15) - x0;
                int fromZ = Math.max(z0, cz << 4) - z0;
                int toZ = Math.min(z0 + w - 1, (cz << 4) + 15) - z0;
                if (chunk == null) {
                    claims[ci] = 3;
                    for (int y = 0; y < h; y++) {
                        for (int z = fromZ; z <= toZ; z++) {
                            for (int x = fromX; x <= toX; x++) {
                                snap.cls[snap.index(x, y, z)] = NEVER;
                            }
                        }
                    }
                    for (int z = fromZ; z <= toZ; z++) {
                        for (int x = fromX; x <= toX; x++) {
                            snap.surface[z * w + x] = -1;
                        }
                    }
                    continue;
                }
                ClaimOwner claim = chunk.getData(Factions.CLAIM.get());
                byte mark = 0;
                if (claim.claimed()) {
                    claims[ci] = (byte) (claim.ownedBy(owner) && !claim.enemy() ? 1 : 2);
                    mark = claims[ci] == 1 ? OWNED : FORBID;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (NtmDefenses.isTurret(entity)) {
                        turrets.add(entity.getBlockPos());
                    }
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int y = 0; y < h; y++) {
                    int worldY = bottom + y;
                    int sectionIndex = chunk.getSectionIndex(worldY);
                    LevelChunkSection section = sectionIndex >= 0 && sectionIndex < sections.length
                            ? sections[sectionIndex] : null;
                    boolean empty = section == null || section.hasOnlyAir();
                    for (int z = fromZ; z <= toZ; z++) {
                        for (int x = fromX; x <= toX; x++) {
                            int index = snap.index(x, y, z);
                            if (empty) {
                                snap.cls[index] = AIR;
                                snap.flags[index] = mark;
                                continue;
                            }
                            int wx = x0 + x;
                            int wz = z0 + z;
                            BlockState state = section.getBlockState(wx & 15, worldY & 15, wz & 15);
                            Short known = cache.get(state);
                            if (known == null) {
                                known = classify(state, level, cursor.set(wx, worldY, wz));
                                cache.put(state, known);
                            }
                            int packed = known;
                            byte flag = (byte) ((packed >> 8) | mark);
                            // Un piege cache (mine, plaque, obsidienne piegee) echappe parfois aux espions.
                            if ((flag & TRAP) != 0 && hidden(wx, worldY, wz, intel, salt)) {
                                flag &= (byte) ~TRAP;
                            }
                            snap.cls[index] = (byte) (packed & 0xFF);
                            snap.flags[index] = flag;
                        }
                    }
                }
                for (int z = fromZ; z <= toZ; z++) {
                    for (int x = fromX; x <= toX; x++) {
                        int feet = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (x0 + x) & 15,
                                (z0 + z) & 15) + 1 - bottom;
                        snap.surface[z * w + x] = feet >= 1 && feet < h - 2 ? feet : -1;
                    }
                }
            }
        }

        // Les camps possibles : hors de tes claims, a un a trois chunks de leur bord.
        int[] distance = chunkDistance(claims, cw, cl);
        boolean any = false;
        for (int z = 0; z < w; z++) {
            for (int x = 0; x < w; x++) {
                int ci = (((z0 + z) >> 4) - chunkFromZ) * cw + (((x0 + x) >> 4) - chunkFrom);
                int d = distance[ci];
                int feet = snap.surface[z * w + x];
                if (claims[ci] == 0 && d >= 2 && d <= 3 && feet > 0 && campable(snap, x, feet, z)) {
                    snap.seed[z * w + x] = true;
                    any = true;
                }
            }
        }
        if (!any) {
            // Une base qui couvre toute la zone : l'armee debarque sur ses bords.
            for (int z = 0; z < w; z++) {
                for (int x = 0; x < w; x++) {
                    boolean border = x < 3 || z < 3 || x >= w - 3 || z >= w - 3;
                    int feet = snap.surface[z * w + x];
                    if (border && feet > 0 && campable(snap, x, feet, z)) {
                        snap.seed[z * w + x] = true;
                    }
                }
            }
        }

        // Le danger des colonnes : ce que couvrent les tourelles, ou sont tombes les soldats.
        for (BlockPos turret : turrets) {
            spread(snap, turret.getX() - x0, turret.getZ() - z0, 22, 1.6f);
        }
        for (BlockPos death : deaths) {
            spread(snap, death.getX() - x0, death.getZ() - z0, 3, 22.0f);
        }
        return snap;
    }

    /** Ce piege echappe-t-il aux espions de la nation ? Le meme tirage a chaque releve du siege. */
    private static boolean hidden(int x, int y, int z, double intel, long salt) {
        long hash = (x * 73_856_093L) ^ (y * 19_349_663L) ^ (z * 83_492_791L) ^ salt;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        double roll = (hash & 0xFFFF) / 65_536.0d;
        return roll > intel;
    }

    private static void spread(Snapshot snap, int cx, int cz, int radius, float weight) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = cx + dx;
                int z = cz + dz;
                if (x < 0 || z < 0 || x >= snap.w || z >= snap.l) {
                    continue;
                }
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= radius) {
                    snap.column[z * snap.w + x] += (float) (weight * (radius - d + 1.0d) / (radius + 1.0d))
                            * (radius > 6 ? 18.0f : 1.0f);
                }
            }
        }
    }

    /** La distance de chaque chunk a tes claims, en chunks (Tchebychev). */
    private static int[] chunkDistance(byte[] claims, int cw, int cl) {
        int[] distance = new int[cw * cl];
        java.util.Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < claims.length; i++) {
            if (claims[i] == 1) {
                distance[i] = 0;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int cx = i % cw;
            int cz = i / cw;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = cx + dx;
                    int nz = cz + dz;
                    if (nx < 0 || nz < 0 || nx >= cw || nz >= cl) {
                        continue;
                    }
                    int j = nz * cw + nx;
                    if (distance[j] > distance[i] + 1) {
                        distance[j] = distance[i] + 1;
                        queue.add(j);
                    }
                }
            }
        }
        return distance;
    }

    private static boolean campable(Snapshot snap, int x, int feet, int z) {
        byte below = snap.cls(x, feet - 1, z);
        return snap.cls(x, feet, z) == AIR && snap.cls(x, feet + 1, z) == AIR && below >= SOFT && below <= NEVER
                && (snap.flags(x, feet - 1, z) & (TALL | DANGER | FORBID)) == 0;
    }

    /** La classe d'un bloc dans les 8 bits bas, ses marques dans les 8 bits hauts. */
    static short classify(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir()) {
            return AIR;
        }
        int flags = 0;
        if (state.is(BlockTags.CLIMBABLE)) {
            flags |= CLIMB;
        }
        if (dangerous(state)) {
            flags |= DANGER;
        }
        if (trapped(state)) {
            flags |= TRAP;
        }
        FluidState fluid = state.getFluidState();
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            if (fluid.is(FluidTags.LAVA)) {
                return (short) (LAVA | flags << 8);
            }
            if (fluid.is(FluidTags.WATER)) {
                return (short) (WATER | flags << 8);
            }
            return (short) (AIR | flags << 8);
        }
        if (shape.max(Direction.Axis.Y) > 1.0d) {
            flags |= TALL;
        }
        if (state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.getBlock() instanceof FenceGateBlock) {
            return (short) (DOOR | flags << 8);
        }
        if (state.is(PalaPillage.ENDIUM_TNT_BREAKABLE)) {
            return (short) (OBSIDIAN | flags << 8);
        }
        if (state.hasBlockEntity()) {
            return (short) (NEVER | flags << 8);
        }
        float hardness = state.getDestroySpeed(level, pos);
        float resistance = state.getBlock().getExplosionResistance();
        byte kind;
        if (hardness < 0.0f) {
            kind = NEVER;
        } else if (hardness <= 0.8f && resistance <= 3.0f) {
            kind = SOFT;
        } else if (hardness <= 2.0f && resistance <= 6.5f) {
            kind = MEDIUM;
        } else if (hardness <= 3.5f && resistance <= 9.5f) {
            kind = HARD;
        } else if (resistance <= 12.0f) {
            kind = BLAST;
        } else if (resistance <= 40.0f) {
            kind = HEAVY;
        } else {
            kind = NEVER;
        }
        return (short) (kind | flags << 8);
    }

    /** Ce qui blesse qui le touche. */
    static boolean dangerous(BlockState state) {
        return state.getBlock() instanceof SpikeBlock || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(BlockTags.FIRE) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)
                || state.is(BlockTags.CAMPFIRES);
    }

    /** Ce qui se declenche : obsidiennes piegees, mines, plaques de pression, fils. */
    static boolean trapped(BlockState state) {
        if (state.getBlock() instanceof DefenseObsidianBlock || state.is(BlockTags.PRESSURE_PLATES)
                || state.is(Blocks.TRIPWIRE)) {
            return true;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return NtmDefenses.MOD.equals(id.getNamespace()) && id.getPath().startsWith("mine");
    }

    // -- La recherche ----------------------------------------------------------------------------

    private static boolean passable(byte cls) {
        return cls == AIR || cls == WATER || cls == DOOR;
    }

    private static boolean solid(byte cls) {
        return cls >= SOFT;
    }

    private static boolean standable(Snapshot s, int x, int y, int z) {
        return solid(s.cls(x, y, z)) && (s.flags(x, y, z) & TALL) == 0;
    }

    /** Le temps pour ouvrir une case, en ticks ; INF si on ne peut pas. */
    private static float open(Snapshot s, Capabilities caps, int x, int y, int z) {
        if (!s.inside(x, y, z)) {
            return INF;
        }
        int index = s.index(x, y, z);
        byte cls = s.cls[index];
        if (cls == AIR || cls == WATER) {
            return 0.0f;
        }
        if (cls == DOOR) {
            return 5.0f;
        }
        if (cls == LAVA || cls == NEVER || (s.flags[index] & FORBID) != 0) {
            return INF;
        }
        float trap = (s.flags[index] & TRAP) != 0 ? 60.0f : 0.0f;
        return trap + switch (cls) {
            case SOFT -> 12.0f;
            case MEDIUM -> 26.0f;
            case HARD -> 44.0f;
            case BLAST -> 70.0f;
            case HEAVY -> caps.heavy() ? 120.0f : INF;
            case OBSIDIAN -> caps.endium() ? 260.0f : INF;
            default -> INF;
        };
    }

    /** Ce que coute de se tenir la : pieges connus, pics, tourelles, morts. */
    private static float danger(Snapshot s, int x, int y, int z) {
        float cost = s.column[z * s.w + x];
        int marks = s.flags(x, y, z) | s.flags(x, y - 1, z) | s.flags(x + 1, y, z) | s.flags(x - 1, y, z)
                | s.flags(x, y, z + 1) | s.flags(x, y, z - 1);
        if ((marks & DANGER) != 0) {
            cost += 40.0f;
        }
        if ((marks & TRAP) != 0) {
            cost += 60.0f;
        }
        return cost;
    }

    private static float heuristic(Snapshot s, int x, int y, int z) {
        double dx = x - s.vx;
        double dz = z - s.vz;
        return (float) (4.0d * Math.sqrt(dx * dx + dz * dz) + Math.abs(y - s.vy));
    }

    private static boolean goal(Snapshot s, int x, int y, int z) {
        return Math.abs(x - s.vx) <= 1 && Math.abs(z - s.vz) <= 1 && Math.abs(y - s.vy) <= 1;
    }

    /** Le tas binaire de l'A* : le cout estime dans les 32 bits hauts, la case dans les bas. */
    private static final class Heap {
        long[] items = new long[4096];
        int size;

        void push(float priority, int index) {
            if (size == items.length) {
                items = java.util.Arrays.copyOf(items, size * 2);
            }
            long item = ((long) Float.floatToIntBits(priority) << 32) | (index & 0xFFFFFFFFL);
            int at = size++;
            while (at > 0) {
                int parent = (at - 1) >>> 1;
                if (items[parent] <= item) {
                    break;
                }
                items[at] = items[parent];
                at = parent;
            }
            items[at] = item;
        }

        long pop() {
            long top = items[0];
            long last = items[--size];
            int at = 0;
            while (true) {
                int child = at * 2 + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && items[child + 1] < items[child]) {
                    child++;
                }
                if (items[child] >= last) {
                    break;
                }
                items[at] = items[child];
                at = child;
            }
            items[at] = last;
            return top;
        }
    }

    /** Cherche le chemin le moins cher du camp au coffre ; {@code null} si la base est imprenable. */
    static Plan search(Snapshot s, Capabilities caps) {
        Int2FloatOpenHashMap cost = new Int2FloatOpenHashMap();
        cost.defaultReturnValue(INF);
        Int2IntOpenHashMap parent = new Int2IntOpenHashMap();
        parent.defaultReturnValue(-1);
        Int2ByteOpenHashMap move = new Int2ByteOpenHashMap();
        IntOpenHashSet closed = new IntOpenHashSet();
        Heap open = new Heap();
        for (int z = 0; z < s.l; z++) {
            for (int x = 0; x < s.w; x++) {
                if (s.seed[z * s.w + x]) {
                    int y = s.surface[z * s.w + x];
                    int index = s.index(x, y, z);
                    cost.put(index, 0.0f);
                    parent.put(index, -1);
                    move.put(index, M_START);
                    open.push(heuristic(s, x, y, z), index);
                }
            }
        }
        int expanded = 0;
        int found = -1;
        int plane = s.w * s.l;
        while (open.size > 0 && expanded < MAX_EXPANDED) {
            long item = open.pop();
            int index = (int) item;
            if (!closed.add(index)) {
                continue;
            }
            expanded++;
            int y = index / plane;
            int rest = index - y * plane;
            int z = rest / s.w;
            int x = rest - z * s.w;
            if (goal(s, x, y, z)) {
                found = index;
                break;
            }
            float g = cost.get(index);
            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int nz = z + dir[1];
                if (nx < 0 || nz < 0 || nx >= s.w || nz >= s.l) {
                    continue;
                }
                expandLevel(s, caps, x, y, z, nx, nz, g, cost, parent, move, open, closed);
                expandUp(s, caps, x, y, z, nx, nz, g, cost, parent, move, open, closed);
                expandLadder(s, x, y, z, dir, g, cost, parent, move, open, closed);
            }
            // Creuser vers le bas : passer sous une muraille.
            float down = open(s, caps, x, y - 1, z);
            if (down < INF && down > 0.0f && standable(s, x, y - 2, z)) {
                relax(s, x, y - 1, z, g + 4.0f + down, index, M_DIG_DOWN, 0, cost, parent, move, open, closed);
            }
            // Monter a tes echelles, a tes lianes.
            if ((s.flags(x, y, z) & CLIMB) != 0 && passable(s.cls(x, y + 1, z)) && passable(s.cls(x, y + 2, z))) {
                relax(s, x, y + 1, z, g + 5.0f, index, M_CLIMB, 0, cost, parent, move, open, closed);
            }
        }
        if (found < 0) {
            return null;
        }
        return extract(s, found, cost.get(found), parent, move, expanded);
    }

    private static void relax(Snapshot s, int x, int y, int z, float g, int from, byte kind, int arg,
                              Int2FloatOpenHashMap cost, Int2IntOpenHashMap parent, Int2ByteOpenHashMap move,
                              Heap open, IntOpenHashSet closed) {
        if (!s.inside(x, y, z) || y < 1 || y >= s.h - 2) {
            return;
        }
        int index = s.index(x, y, z);
        if (closed.contains(index)) {
            return;
        }
        float total = g + danger(s, x, y, z);
        if (total >= cost.get(index)) {
            return;
        }
        cost.put(index, total);
        parent.put(index, from);
        move.put(index, (byte) (kind | (arg << 4)));
        open.push(total + heuristic(s, x, y, z), index);
    }

    /** Un pas sur le meme niveau : marcher, nager, tomber plus bas, ou jeter une planche. */
    private static void expandLevel(Snapshot s, Capabilities caps, int x, int y, int z, int nx, int nz, float g,
                                    Int2FloatOpenHashMap cost, Int2IntOpenHashMap parent, Int2ByteOpenHashMap move,
                                    Heap open, IntOpenHashSet closed) {
        float feet = open(s, caps, nx, y, nz);
        float head = open(s, caps, nx, y + 1, nz);
        if (feet >= INF || head >= INF) {
            return;
        }
        float clear = feet + head;
        int from = s.index(x, y, z);
        byte floor = s.cls(nx, y - 1, nz);
        byte here = s.cls(nx, y, nz);
        if (here == WATER || floor == WATER) {
            relax(s, nx, y, nz, g + 9.0f + clear, from, M_SWIM, 0, cost, parent, move, open, closed);
            return;
        }
        if (standable(s, nx, y - 1, nz) || (s.flags(nx, y, nz) & CLIMB) != 0) {
            relax(s, nx, y, nz, g + 4.0f + clear, from, M_WALK, 0, cost, parent, move, open, closed);
            return;
        }
        if (floor == LAVA) {
            if ((s.flags(nx, y - 1, nz) & FORBID) == 0) {
                relax(s, nx, y, nz, g + 34.0f + clear, from, M_BRIDGE, 0, cost, parent, move, open, closed);
            }
            return;
        }
        if (!passable(floor)) {
            return;
        }
        // Le vide sous le pas : on tombe jusqu'au premier sol, ou l'on jette une planche.
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            int ty = y - drop;
            if (!s.inside(nx, ty, nz)) {
                break;
            }
            byte cell = s.cls(nx, ty, nz);
            if (!passable(cell)) {
                break;
            }
            byte under = s.cls(nx, ty - 1, nz);
            if (cell == WATER || under == WATER || standable(s, nx, ty - 1, nz)) {
                float fall = 4.0f + drop + (drop > 3 ? 10.0f * (drop - 3) : 0.0f);
                relax(s, nx, ty, nz, g + fall + clear, from, M_DROP, 0, cost, parent, move, open, closed);
                break;
            }
        }
        if (floor == AIR && (s.flags(nx, y - 1, nz) & FORBID) == 0) {
            relax(s, nx, y, nz, g + 14.0f + clear, from, M_BRIDGE, 0, cost, parent, move, open, closed);
        }
    }

    /** Monter d'un bloc, en ouvrant au besoin ce qui gene (un escalier creuse dans un talus). */
    private static void expandUp(Snapshot s, Capabilities caps, int x, int y, int z, int nx, int nz, float g,
                                 Int2FloatOpenHashMap cost, Int2IntOpenHashMap parent, Int2ByteOpenHashMap move,
                                 Heap open, IntOpenHashSet closed) {
        if (!standable(s, nx, y, nz)) {
            return;
        }
        float above = open(s, caps, x, y + 2, z);
        float feet = open(s, caps, nx, y + 1, nz);
        float head = open(s, caps, nx, y + 2, nz);
        if (above >= INF || feet >= INF || head >= INF) {
            return;
        }
        relax(s, nx, y + 1, nz, g + 7.0f + above + feet + head, s.index(x, y, z), M_UP, 0, cost, parent, move,
                open, closed);
    }

    /** Poser une echelle contre un mur de 2 a 12 blocs, et passer par-dessus. */
    private static void expandLadder(Snapshot s, int x, int y, int z, int[] dir, float g, Int2FloatOpenHashMap cost,
                                     Int2IntOpenHashMap parent, Int2ByteOpenHashMap move, Heap open,
                                     IntOpenHashSet closed) {
        int nx = x + dir[0];
        int nz = z + dir[1];
        if (passable(s.cls(nx, y, nz)) || passable(s.cls(nx, y + 1, nz)) || s.cls(x, y, z) == WATER) {
            return;
        }
        int existing = 0;
        for (int height = 1; height <= MAX_LADDER; height++) {
            int column = s.index(x, y + height - 1, z);
            if (!s.inside(x, y + height + 1, z)) {
                return;
            }
            byte climb = s.cls(x, y + height + 1, z);
            if (!passable(s.cls[column]) || s.cls[column] == WATER || !passable(climb) || climb == WATER
                    || (s.flags[column] & FORBID) != 0) {
                return;
            }
            if ((s.flags[column] & CLIMB) != 0) {
                existing++;
            }
            byte wall = s.cls(nx, y + height - 1, nz);
            if (!solid(wall)) {
                return;
            }
            if (height >= 2 && standable(s, nx, y + height - 1, nz) && s.cls(nx, y + height, nz) == AIR
                    && s.cls(nx, y + height + 1, nz) == AIR) {
                float price = 20.0f + 9.0f * height - 6.0f * existing;
                relax(s, nx, y + height, nz, g + price, s.index(x, y, z), M_LADDER, height, cost, parent, move,
                        open, closed);
                return;
            }
        }
    }

    // -- Le plan ---------------------------------------------------------------------------------

    private static Plan extract(Snapshot s, int found, float total, Int2IntOpenHashMap parent,
                                Int2ByteOpenHashMap move, int expanded) {
        List<Integer> chain = new ArrayList<>();
        for (int at = found; at >= 0; at = parent.get(at)) {
            chain.add(at);
            if (chain.size() > 4096) {
                break;
            }
        }
        java.util.Collections.reverse(chain);
        int plane = s.w * s.l;
        List<Step> steps = new ArrayList<>();
        int px = 0;
        int py = 0;
        int pz = 0;
        int broken = 0;
        for (int i = 0; i < chain.size(); i++) {
            int index = chain.get(i);
            int y = index / plane;
            int rest = index - y * plane;
            int z = rest / s.w;
            int x = rest - z * s.w;
            byte packed = move.get(index);
            byte kind = (byte) (packed & 0x0F);
            int arg = (packed >> 4) & 0x0F;
            List<BlockPos> open = new ArrayList<>();
            BlockPos fill = null;
            if (i > 0) {
                switch (kind) {
                    case M_WALK, M_SWIM, M_BRIDGE -> {
                        addIfSolid(s, open, x, y, z);
                        addIfSolid(s, open, x, y + 1, z);
                        if (kind == M_BRIDGE) {
                            fill = s.world(x, y - 1, z);
                        }
                    }
                    case M_DROP -> {
                        addIfSolid(s, open, x, py, z);
                        addIfSolid(s, open, x, py + 1, z);
                    }
                    case M_UP -> {
                        addIfSolid(s, open, px, py + 2, pz);
                        addIfSolid(s, open, x, y, z);
                        addIfSolid(s, open, x, y + 1, z);
                    }
                    case M_DIG_DOWN -> addIfSolid(s, open, px, py - 1, pz);
                    default -> {
                    }
                }
            }
            broken += open.size();
            steps.add(new Step(s.world(x, y, z), kind, arg, List.copyOf(open), fill));
            px = x;
            py = y;
            pz = z;
        }
        List<Obstacle> obstacles = obstacles(steps);
        BlockPos camp = steps.getFirst().pos();
        double straight = Math.max(1.0d, Math.sqrt(camp.distSqr(s.world(s.vx, s.vy, s.vz))));
        double hardness = Math.max(0.0d, Math.min(1.0d, Math.log(Math.max(1.0d, total / (straight * 4.0d)))
                / Math.log(12.0d)));
        return new Plan(List.copyOf(steps), List.copyOf(obstacles), total, expanded, hardness);
    }

    private static void addIfSolid(Snapshot s, List<BlockPos> out, int x, int y, int z) {
        byte cls = s.cls(x, y, z);
        if (cls != AIR && cls != WATER) {
            out.add(s.world(x, y, z));
        }
    }

    /** Les obstacles dans l'ordre du chemin : breches, echelles, ponts. */
    static List<Obstacle> obstacles(List<Step> steps) {
        List<Obstacle> out = new ArrayList<>();
        int i = 1;
        while (i < steps.size()) {
            Step step = steps.get(i);
            if (step.move() == M_LADDER) {
                Step base = steps.get(i - 1);
                int dx = base.pos().getX() - step.pos().getX();
                int dz = base.pos().getZ() - step.pos().getZ();
                Direction facing = dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH
                        : Direction.NORTH;
                List<BlockPos> rungs = new ArrayList<>();
                for (int k = 0; k < step.arg(); k++) {
                    rungs.add(base.pos().above(k));
                }
                out.add(new Obstacle(Kind.LADDER, i, List.copyOf(rungs), 0.0f, facing));
                i++;
                continue;
            }
            if (!step.broken().isEmpty()) {
                int first = i;
                List<BlockPos> cells = new ArrayList<>();
                while (i < steps.size() && !steps.get(i).broken().isEmpty() && cells.size() < 8
                        && steps.get(i).move() != M_LADDER) {
                    cells.addAll(steps.get(i).broken());
                    i++;
                }
                out.add(new Obstacle(Kind.BREACH, first, List.copyOf(cells), 0.0f, null));
                continue;
            }
            if (step.move() == M_BRIDGE) {
                int first = i;
                List<BlockPos> cells = new ArrayList<>();
                while (i < steps.size() && steps.get(i).move() == M_BRIDGE && steps.get(i).broken().isEmpty()) {
                    cells.add(steps.get(i).fill());
                    i++;
                }
                out.add(new Obstacle(Kind.BRIDGE, first, List.copyOf(cells), 0.0f, null));
                continue;
            }
            i++;
        }
        return out;
    }

    /** La charge qu'il faut pour ces blocs : 0 s'ils se creusent tous, sinon la plus forte. */
    static float charge(ServerLevel level, List<BlockPos> cells) {
        float needed = 0.0f;
        for (BlockPos pos : cells) {
            BlockState state = level.getBlockState(pos);
            short packed = classify(state, level, pos);
            byte cls = (byte) (packed & 0xFF);
            float charge = switch (cls) {
                case BLAST -> CHARGE_TNT;
                case HEAVY -> CHARGE_BIG;
                case OBSIDIAN -> CHARGE_ENDIUM;
                default -> 0.0f;
            };
            needed = Math.max(needed, charge);
        }
        return needed;
    }
}
