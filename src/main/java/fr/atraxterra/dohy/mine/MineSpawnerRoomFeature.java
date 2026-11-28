package fr.atraxterra.dohy.mine;

import fr.atraxterra.dohy.palamod.dimminer.MinerRuinFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Arrays;

/**
 * Les salles a spawner de la Mine.
 *
 * <p>Demande du joueur (2026-11-28) : « dans la mine ajoute la generation des spawners a mobs,
 * uniquement des spawners pour les mobs des metiers de chasseur et alchimiste, un taux assez
 * commun mais pas trop ». Une salle close de 7 x 7 sur 4 de haut, taillee dans la roche comme les
 * cryptes de la dimension du Mineur ({@link MinerRuinFeature.Site}), le spawner du jeu au milieu,
 * aux materiaux de sa creature. Le tirage, sur 100 :
 * <ul>
 *   <li>les monstres, dans le noir : zombie 15, squelette 13, araignee 12, araignee venimeuse 9,
 *       creeper 8, sorciere 5, cube de magma 5, blaze 3 ;</li>
 *   <li>les betes, sur l'herbe et sous quatre lanternes (il leur faut plus de 8 de lumiere) :
 *       vache 7, cochon 7, mouton 6, poule 6, lapin 4.</li>
 * </ul>
 * Toutes sont dans les tables d'XP du Chasseur ou de l'Alchimiste ({@code data/job_progression.json}).
 * Ecartes : les boss (dragon, wither, warden, gardien ancien) ; le gardien, le ghast, le shulker, la
 * brute piglin, l'enderman et le squelette wither, creatures de l'eau, du Nether ou de l'End qui
 * n'ont rien a faire dans une mine ; la chauve-souris et le cube de soufre, sans interet a la
 * chasse ; le serpent, l'escargot et l'elephant du PalaMod, appats et monture des familiers.
 *
 * <p><b>Le taux</b> : une chance sur six par chunk, entre y = -48 et 112. Les places refusees
 * (galerie trop large, lave, salle d'arrivee) laissent une salle tous les sept chunks environ, un
 * peu plus d'une a portee du detecteur de spawners (9 chunks).
 *
 * <p><b>Les materiaux</b> evitent la pierre, l'ardoise des abimes, l'andesite, la diorite, le
 * granite, le tuf et la calcite : les minerais, poses apres les salles, les remplaceraient, et le
 * lichen luisant y pousserait, dont la lumiere suffit a eteindre une salle a monstres (la Mine
 * n'en fait naitre qu'a une lumiere nulle). Ni terre simple au plafond : une source d'eau s'y
 * ouvrirait.
 */
public class MineSpawnerRoomFeature extends Feature<NoneFeatureConfiguration> {

    /** Les creatures, et leur part du tirage. */
    private enum Kind {
        ZOMBIE(15), SKELETON(13), SPIDER(12), CAVE_SPIDER(9), CREEPER(8), WITCH(5), MAGMA_CUBE(5), BLAZE(3),
        COW(7), PIG(7), SHEEP(6), CHICKEN(6), RABBIT(4);

        private static final Kind[] VALUES = values();
        private static final int TOTAL = Arrays.stream(VALUES).mapToInt(kind -> kind.weight).sum();

        private final int weight;

        Kind(int weight) {
            this.weight = weight;
        }

        EntityType<?> type() {
            return switch (this) {
                case ZOMBIE -> EntityTypes.ZOMBIE;
                case SKELETON -> EntityTypes.SKELETON;
                case SPIDER -> EntityTypes.SPIDER;
                case CAVE_SPIDER -> EntityTypes.CAVE_SPIDER;
                case CREEPER -> EntityTypes.CREEPER;
                case WITCH -> EntityTypes.WITCH;
                case MAGMA_CUBE -> EntityTypes.MAGMA_CUBE;
                case BLAZE -> EntityTypes.BLAZE;
                case COW -> EntityTypes.COW;
                case PIG -> EntityTypes.PIG;
                case SHEEP -> EntityTypes.SHEEP;
                case CHICKEN -> EntityTypes.CHICKEN;
                case RABBIT -> EntityTypes.RABBIT;
            };
        }

        /** Une bete : de l'herbe sous les pattes et de la lumiere, ou rien n'apparait. */
        boolean beast() {
            return ordinal() >= COW.ordinal();
        }

        static Kind draw(RandomSource random) {
            int roll = random.nextInt(TOTAL);
            for (Kind kind : VALUES) {
                roll -= kind.weight;
                if (roll < 0) {
                    return kind;
                }
            }
            return ZOMBIE;
        }
    }

    /** L'interieur : de -3 a 3 en x et en z, de 0 a 3 en hauteur ; la coque autour. */
    private static final int HALF = 3;
    private static final int EDGE = HALF + 1;
    private static final int HEIGHT = 4;

    /** Aucune salle a moins de 24 blocs d'une salle d'arrivee. */
    private static final int CLEARANCE = 24;

    public MineSpawnerRoomFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        if (nearArrival(origin) || !dry(level, origin)) {
            return false;
        }
        MinerRuinFeature.Site site = new MinerRuinFeature.Site(level, origin, context.random());
        if (!site.enclosed()) {
            return false;
        }
        Kind kind = Kind.draw(site.random);
        room(site, kind);
        site.spawner(0, 0, 0, kind.type(), false);
        site.settle();
        return true;
    }

    /**
     * A l'ecart des salles d'arrivee : il y en a une par tranche, en z = 0, tous les
     * {@link MineService#SPACING} blocs en x ({@link MineService#base}).
     */
    private static boolean nearArrival(BlockPos origin) {
        int offset = Math.floorMod(origin.getX() - MineService.base(0), MineService.SPACING);
        int dx = Math.min(offset, MineService.SPACING - offset);
        return dx <= CLEARANCE && Math.abs(origin.getZ()) <= CLEARANCE;
    }

    /**
     * Loin de la lave : les salles de bois prendraient feu, et un lac coupe par la salle
     * laisserait sa lave contre les murs.
     */
    private static boolean dry(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -EDGE - 1; x <= EDGE + 1; x++) {
            for (int z = -EDGE - 1; z <= EDGE + 1; z++) {
                for (int y = -2; y <= HEIGHT + 1; y++) {
                    if (level.getFluidState(cursor.setWithOffset(origin, x, y, z)).is(FluidTags.LAVA)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // -- La salle ---------------------------------------------------------------------

    private static void room(MinerRuinFeature.Site site, Kind kind) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -EDGE; x <= EDGE; x++) {
            for (int z = -EDGE; z <= EDGE; z++) {
                for (int y = -1; y <= HEIGHT; y++) {
                    boolean shell = Math.abs(x) == EDGE || Math.abs(z) == EDGE || y == -1 || y == HEIGHT;
                    site.set(x, y, z, shell ? shell(site, kind, y) : air);
                }
            }
        }
        if (kind.beast()) {
            // Quatre lanternes au plafond : plus de 8 de lumiere sur tout le sol.
            BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
            for (int x = -2; x <= 2; x += 4) {
                for (int z = -2; z <= 2; z += 4) {
                    site.set(x, HEIGHT - 1, z, lantern);
                }
            }
        }
        switch (kind) {
            case ZOMBIE -> crypt(site);
            case SKELETON -> ossuary(site);
            case SPIDER -> nest(site);
            case CAVE_SPIDER -> gallery(site);
            case CREEPER -> shrine(site);
            case WITCH -> laboratory(site);
            case MAGMA_CUBE -> forge(site);
            case BLAZE -> altar(site);
            case COW -> barn(site);
            case PIG -> sty(site);
            case SHEEP -> fold(site);
            case CHICKEN -> coop(site);
            case RABBIT -> burrow(site);
        }
    }

    /** La coque : le sol ({@code y = -1}), les murs, le plafond ({@code y = HEIGHT}). */
    private static BlockState shell(MinerRuinFeature.Site site, Kind kind, int y) {
        boolean floor = y == -1;
        boolean ceiling = y == HEIGHT;
        return switch (kind) {
            case ZOMBIE -> floor ? site.pick(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE)
                    : site.pick(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
                            Blocks.MOSSY_STONE_BRICKS);
            case SKELETON -> floor ? site.pick(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES)
                    : site.pick(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES,
                            Blocks.DEEPSLATE_BRICKS);
            case SPIDER -> floor ? site.pick(Blocks.COARSE_DIRT, Blocks.MOSSY_COBBLESTONE)
                    : site.pick(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE);
            case CAVE_SPIDER -> floor || ceiling ? Blocks.OAK_PLANKS.defaultBlockState()
                    : site.pick(Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG);
            case CREEPER -> floor ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                    : ceiling ? Blocks.CUT_SANDSTONE.defaultBlockState()
                    : site.pick(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE);
            case WITCH -> floor ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                    : site.pick(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.STRIPPED_DARK_OAK_LOG);
            // Une braise sur quatre dans le sol : le cube de magma ne craint ni la chaleur ni la lumiere.
            case MAGMA_CUBE -> floor ? site.pick(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE,
                    Blocks.MAGMA_BLOCK)
                    : site.pick(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
                            Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS);
            case BLAZE -> floor || ceiling ? Blocks.NETHER_BRICKS.defaultBlockState()
                    : site.pick(Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS);
            case COW -> floor ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : site.pick(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG);
            // Surtout de l'herbe : un cochon n'apparait que sur elle.
            case PIG -> floor ? site.pick(Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.MUD)
                    : site.pick(Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.STRIPPED_MANGROVE_LOG);
            case SHEEP -> floor ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : site.pick(Blocks.BIRCH_PLANKS, Blocks.BIRCH_PLANKS, Blocks.STRIPPED_BIRCH_LOG);
            case CHICKEN -> floor ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : site.pick(Blocks.ACACIA_PLANKS, Blocks.ACACIA_PLANKS, Blocks.STRIPPED_ACACIA_LOG);
            case RABBIT -> floor ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : ceiling ? Blocks.ROOTED_DIRT.defaultBlockState()
                    : site.pick(Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.PACKED_MUD);
        };
    }

    // -- Les decors, apres la coque et les lanternes ------------------------------------

    /** Zombie : une crypte, des toiles a trois coins, une tete sur un socle au quatrieme. */
    private static void crypt(MinerRuinFeature.Site site) {
        BlockState web = Blocks.COBWEB.defaultBlockState();
        for (int[] corner : new int[][]{{-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}}) {
            site.set(corner[0], 0, corner[1], web);
            site.set(corner[0], HEIGHT - 1, corner[1], web);
        }
        site.set(HALF, 0, HALF, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        site.set(HALF, 1, HALF, Blocks.ZOMBIE_HEAD.defaultBlockState());
    }

    /** Squelette : un ossuaire, trois piliers d'os, un crane, un os couche. */
    private static void ossuary(MinerRuinFeature.Site site) {
        for (int[] corner : new int[][]{{-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}}) {
            for (int y = 0; y < HEIGHT; y++) {
                site.set(corner[0], y, corner[1], Blocks.BONE_BLOCK.defaultBlockState());
            }
        }
        site.set(HALF, 0, HALF, Blocks.SKELETON_SKULL.defaultBlockState());
        site.set(-1, 0, 2, Blocks.BONE_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
    }

    /** Araignee : un nid, des toiles du sol au plafond dans les coins, d'autres sous la voute. */
    private static void nest(MinerRuinFeature.Site site) {
        BlockState web = Blocks.COBWEB.defaultBlockState();
        for (int x = -HALF; x <= HALF; x += 2 * HALF) {
            for (int z = -HALF; z <= HALF; z += 2 * HALF) {
                for (int y = 0; y < HEIGHT; y++) {
                    site.set(x, y, z, web);
                }
            }
        }
        scatter(site, web, 14, HEIGHT - 2, HEIGHT - 1);
    }

    /** Araignee venimeuse : une galerie de mine boisee, deux cadres, des rails, des toiles. */
    private static void gallery(MinerRuinFeature.Site site) {
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState beam = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        for (int z = -2; z <= 2; z += 4) {
            for (int y = 0; y < HEIGHT - 1; y++) {
                site.shaped(-HALF, y, z, fence);
                site.shaped(HALF, y, z, fence);
            }
            for (int x = -HALF; x <= HALF; x++) {
                site.set(x, HEIGHT - 1, z, beam);
            }
        }
        for (int z = -HALF; z <= HALF; z++) {
            site.set(-2, 0, z, Blocks.RAIL.defaultBlockState());
        }
        scatter(site, Blocks.COBWEB.defaultBlockState(), 10, 0, HEIGHT - 1);
    }

    /** Creeper : un sanctuaire de gres, quatre piliers au visage cisele. */
    private static void shrine(MinerRuinFeature.Site site) {
        for (int x = -2; x <= 2; x += 4) {
            for (int z = -2; z <= 2; z += 4) {
                site.set(x, 0, z, Blocks.CUT_SANDSTONE.defaultBlockState());
                site.set(x, 1, z, Blocks.CHISELED_SANDSTONE.defaultBlockState());
                site.set(x, 2, z, Blocks.CUT_SANDSTONE.defaultBlockState());
                site.set(x, 3, z, Blocks.CUT_SANDSTONE.defaultBlockState());
            }
        }
    }

    /**
     * Sorciere : un laboratoire, une bibliotheque au mur ouest, chaudron, alambic et etabli. L'alambic
     * luit de 1 : sa case seule, deja prise, a de la lumiere.
     */
    private static void laboratory(MinerRuinFeature.Site site) {
        for (int z = -HALF; z <= HALF; z++) {
            site.set(-HALF, 0, z, Blocks.BOOKSHELF.defaultBlockState());
            site.set(-HALF, 1, z, Blocks.BOOKSHELF.defaultBlockState());
        }
        site.set(2, 0, -2, Blocks.CAULDRON.defaultBlockState());
        site.set(2, 0, 2, Blocks.BREWING_STAND.defaultBlockState());
        site.set(HALF, 0, 0, Blocks.CRAFTING_TABLE.defaultBlockState());
        site.set(HALF, 1, 0, Blocks.POTTED_RED_MUSHROOM.defaultBlockState());
        site.set(HALF, HEIGHT - 1, -HALF, Blocks.COBWEB.defaultBlockState());
    }

    /** Cube de magma : une forge de pierre noire, quatre colonnes de basalte. */
    private static void forge(MinerRuinFeature.Site site) {
        for (int x = -HALF; x <= HALF; x += 2 * HALF) {
            for (int z = -HALF; z <= HALF; z += 2 * HALF) {
                for (int y = 0; y < HEIGHT; y++) {
                    site.set(x, y, z, Blocks.BASALT.defaultBlockState());
                }
            }
        }
    }

    /** Blaze : un autel de briques du Nether, quatre poteaux, un carre de verrues sur sable des ames. */
    private static void altar(MinerRuinFeature.Site site) {
        BlockState fence = Blocks.NETHER_BRICK_FENCE.defaultBlockState();
        for (int x = -HALF; x <= HALF; x += 2 * HALF) {
            for (int z = -HALF; z <= HALF; z += 2 * HALF) {
                for (int y = 0; y < HEIGHT - 1; y++) {
                    site.shaped(x, y, z, fence);
                }
                site.set(x, HEIGHT - 1, z, Blocks.CHISELED_NETHER_BRICKS.defaultBlockState());
            }
        }
        BlockState wart = Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3);
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                site.set(x, -1, z, Blocks.SOUL_SAND.defaultBlockState());
                site.set(x, 0, z, wart);
            }
        }
    }

    /** Vache : une etable d'epicea, du foin au fond, un abreuvoir. */
    private static void barn(MinerRuinFeature.Site site) {
        for (int x = -HALF; x <= -1; x++) {
            site.set(x, 0, HALF, Blocks.HAY_BLOCK.defaultBlockState());
        }
        site.set(-HALF, 1, HALF, Blocks.HAY_BLOCK.defaultBlockState());
        site.set(HALF, 0, HALF, trough());
    }

    /** Cochon : une porcherie de paletuvier dans la boue, un composteur, des carottes. */
    private static void sty(MinerRuinFeature.Site site) {
        site.set(-HALF, 0, -HALF, Blocks.COMPOSTER.defaultBlockState());
        site.set(-HALF, 0, HALF, Blocks.HAY_BLOCK.defaultBlockState());
        crops(site, HALF - 1, HALF - 1);
    }

    /** Mouton : une bergerie de bouleau, des balles de laine et de foin, un abreuvoir. */
    private static void fold(MinerRuinFeature.Site site) {
        site.set(-HALF, 0, HALF, Blocks.WHITE_WOOL.defaultBlockState());
        site.set(-HALF + 1, 0, HALF, Blocks.WHITE_WOOL.defaultBlockState());
        site.set(-HALF, 1, HALF, Blocks.LIGHT_GRAY_WOOL.defaultBlockState());
        site.set(-HALF, 0, HALF - 1, Blocks.HAY_BLOCK.defaultBlockState());
        site.set(HALF, 0, -HALF, Blocks.HAY_BLOCK.defaultBlockState());
        site.set(HALF, 0, HALF, trough());
    }

    /** Poule : un poulailler d'acacia, des nids de foin, un perchoir le long du mur est. */
    private static void coop(MinerRuinFeature.Site site) {
        for (int x = -HALF; x <= -1; x++) {
            site.set(x, 0, HALF, Blocks.HAY_BLOCK.defaultBlockState());
        }
        BlockState fence = Blocks.ACACIA_FENCE.defaultBlockState();
        for (int z = -2; z <= 1; z++) {
            site.shaped(HALF, 1, z, fence);
        }
        site.set(HALF, 0, -HALF, Blocks.COMPOSTER.defaultBlockState());
    }

    /** Lapin : un terrier de boue sechee sous les racines, un carre de carottes. */
    private static void burrow(MinerRuinFeature.Site site) {
        scatter(site, Blocks.HANGING_ROOTS.defaultBlockState(), 10, HEIGHT - 1, HEIGHT - 1);
        crops(site, -HALF, -HALF);
    }

    /** Un abreuvoir : un chaudron plein d'eau. */
    private static BlockState trough() {
        return Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3);
    }

    /** Un carre de 2 x 2 de carottes mures sur de la terre labouree, a partir de (x, z). */
    private static void crops(MinerRuinFeature.Site site, int x0, int z0) {
        BlockState ripe = Blocks.CARROTS.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7);
        for (int x = x0; x < x0 + 2; x++) {
            for (int z = z0; z < z0 + 2; z++) {
                site.set(x, -1, z, Blocks.FARMLAND.defaultBlockState());
                site.set(x, 0, z, ripe);
            }
        }
    }

    /** Seme {@code count} fois {@code state} dans les cases vides, entre deux hauteurs, hors du spawner. */
    private static void scatter(MinerRuinFeature.Site site, BlockState state, int count, int low, int high) {
        for (int i = 0; i < count; i++) {
            int x = site.random.nextInt(2 * HALF + 1) - HALF;
            int z = site.random.nextInt(2 * HALF + 1) - HALF;
            int y = low + site.random.nextInt(high - low + 1);
            if ((x != 0 || z != 0 || y != 0) && site.empty(x, y, z)) {
                site.set(x, y, z, state);
            }
        }
    }
}
