package fr.atraxterra.dohy.palamod.dimminer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Les ruines a spawner de la dimension du Mineur.
 *
 * <p>Sur le serveur de minage de Paladium, les ames du marteau des cavernes se
 * recoltaient dans les salles a monstres du monde. La dimension de DoHy n'en a
 * pas (un seul biome, sans monstres) : ces ruines les remplacent, et sont
 * frequentes. Six creatures, six dessins, tires au sort a parts egales :
 * <ul>
 *   <li><b>zombie</b> : une crypte en pierre taillee moussue ;</li>
 *   <li><b>squelette</b> : un ossuaire d'ardoise aux piliers d'os ;</li>
 *   <li><b>sorciere</b> : une hutte d'epicea ;</li>
 *   <li><b>creeper</b> : un sanctuaire de gres aux visages ciseles ;</li>
 *   <li><b>vache</b> : un enclos de chene et son abri a foin ;</li>
 *   <li><b>cochon</b> : une porcherie d'epicea, dans la boue.</li>
 * </ul>
 * En surface ({@code buried} faux), la ruine se pose sur un sol a peu pres
 * plat et hors de l'eau, une lanterne pour la voir dans la nuit. Sous terre,
 * c'est une salle fermee de 7 x 7, taillee dans la roche, aux memes materiaux,
 * sans lumiere chez les monstres.
 *
 * <p><b>Les regles d'apparition.</b> La dimension est une nuit fixe ou aucun
 * monstre n'apparait a la lumiere du ciel, et ou les betes n'ont jamais assez
 * de lumiere.
 * <ul>
 *   <li>En surface, le spawner porte ses propres regles ({@code
 *       custom_spawn_rules}, lumiere de 0 a 15) : elles remplacent celles de la
 *       creature.</li>
 *   <li>Sous terre, ces regles ne valent rien : elles lisent la lumiere du ciel
 *       <i>assombrie par la nuit</i>, negative la ou le ciel ne porte pas, donc
 *       hors de toute plage permise (verifie en jeu : aucune case valide). Les
 *       salles suivent donc les regles ordinaires : noires pour les monstres,
 *       eclairees et sur herbe pour les betes.</li>
 * </ul>
 */
public class MinerRuinFeature extends Feature<NoneFeatureConfiguration> {

    /** Les six creatures, dans l'ordre du tirage. */
    private enum Kind {
        ZOMBIE, SKELETON, WITCH, CREEPER, COW, PIG;

        EntityType<?> type() {
            return switch (this) {
                case ZOMBIE -> EntityTypes.ZOMBIE;
                case SKELETON -> EntityTypes.SKELETON;
                case WITCH -> EntityTypes.WITCH;
                case CREEPER -> EntityTypes.CREEPER;
                case COW -> EntityTypes.COW;
                case PIG -> EntityTypes.PIG;
            };
        }
    }

    private static final int HALF = 3;

    private final boolean buried;

    public MinerRuinFeature(boolean buried) {
        super(NoneFeatureConfiguration.CODEC);
        this.buried = buried;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        Kind kind = Kind.values()[random.nextInt(Kind.values().length)];
        Site site = new Site(level, origin, random);
        if (buried) {
            if (!site.enclosed()) {
                return false;
            }
            room(site, kind);
        } else {
            if (!site.flat()) {
                return false;
            }
            site.level();
            switch (kind) {
                case ZOMBIE -> crypt(site);
                case SKELETON -> ossuary(site);
                case WITCH -> hut(site);
                case CREEPER -> shrine(site);
                case COW -> pen(site, Blocks.OAK_FENCE, Blocks.OAK_PLANKS, Blocks.OAK_SLAB, false);
                default -> pen(site, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, true);
            }
        }
        site.spawner(0, 0, 0, kind.type(), !buried);
        site.settle();
        return true;
    }

    // -- En surface -------------------------------------------------------------------

    /** Zombie : quatre murs de pierre taillee ebreches, un seuil au nord, des toiles. */
    private static void crypt(Site site) {
        site.floor(() -> site.pick(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE));
        site.walls(4, y -> y >= 3 ? 0.55f : y == 2 ? 0.25f : 0.0f,
                () -> site.pick(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
                        Blocks.STONE_BRICKS));
        site.door();
        for (int[] corner : new int[][]{{-2, -2}, {2, 2}}) {
            site.set(corner[0], 0, corner[1], Blocks.COBWEB.defaultBlockState());
            site.set(corner[0], 1, corner[1], Blocks.COBWEB.defaultBlockState());
        }
        site.set(2, 0, -2, Blocks.SOUL_LANTERN.defaultBlockState());
        site.set(-2, 0, 2, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    }

    /** Squelette : ardoise, quatre piliers d'os coiffes d'un crane, des murets entre eux. */
    private static void ossuary(Site site) {
        site.floor(() -> site.pick(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.COBBLED_DEEPSLATE));
        site.walls(2, y -> y == 1 ? 0.35f : 0.0f,
                () -> site.pick(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS));
        site.door();
        for (int x = -HALF; x <= HALF; x += 2 * HALF) {
            for (int z = -HALF; z <= HALF; z += 2 * HALF) {
                for (int y = 0; y < 4; y++) {
                    site.set(x, y, z, Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
        }
        site.set(-HALF, 4, -HALF, Blocks.SKELETON_SKULL.defaultBlockState());
        site.set(HALF, 4, HALF, Blocks.SKELETON_SKULL.defaultBlockState());
        site.set(HALF, 4, -HALF, Blocks.SOUL_LANTERN.defaultBlockState());
        site.set(-1, 0, 2, Blocks.BONE_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
    }

    /** Sorciere : une hutte d'epicea de 5 x 5 sous un toit debordant, chaudron et alambic. */
    private static void hut(Site site) {
        site.floor(() -> Blocks.COARSE_DIRT.defaultBlockState());
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                site.set(x, -1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                for (int y = 0; y < 3; y++) {
                    if (corner) {
                        site.set(x, y, z, Blocks.OAK_LOG.defaultBlockState());
                    } else if (edge) {
                        boolean window = y == 1 && (x == 0 || z == 0) && z != -2;
                        site.set(x, y, z, window ? Blocks.AIR.defaultBlockState()
                                : Blocks.SPRUCE_PLANKS.defaultBlockState());
                    }
                }
            }
        }
        site.set(0, 0, -2, Blocks.AIR.defaultBlockState());
        site.set(0, 1, -2, Blocks.AIR.defaultBlockState());
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                boolean rim = Math.abs(x) == HALF || Math.abs(z) == HALF;
                site.set(x, 3, z, rim ? Blocks.SPRUCE_SLAB.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        site.set(1, 0, 1, Blocks.CAULDRON.defaultBlockState());
        site.set(-1, 0, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        site.set(-1, 0, -1, Blocks.COBWEB.defaultBlockState());
        site.set(1, 2, -1, Blocks.COBWEB.defaultBlockState());
        site.set(2, 4, -2, Blocks.LANTERN.defaultBlockState());
        site.set(-HALF, 0, -HALF, Blocks.RED_MUSHROOM.defaultBlockState());
        site.set(HALF, 0, HALF, Blocks.BROWN_MUSHROOM.defaultBlockState());
    }

    /** Creeper : une dalle de gres, quatre piliers au visage cisele, un linteau en dalles. */
    private static void shrine(Site site) {
        site.floor(() -> site.pick(Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE));
        for (int x = -2; x <= 2; x += 4) {
            for (int z = -2; z <= 2; z += 4) {
                site.set(x, 0, z, Blocks.CUT_SANDSTONE.defaultBlockState());
                site.set(x, 1, z, Blocks.CHISELED_SANDSTONE.defaultBlockState());
                site.set(x, 2, z, Blocks.CUT_SANDSTONE.defaultBlockState());
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (rim && site.random.nextFloat() >= 0.2f) {
                    site.set(x, 3, z, Blocks.SANDSTONE_SLAB.defaultBlockState());
                }
            }
        }
        for (int[] spot : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-3, 2}, {3, -1}, {2, 3}}) {
            site.set(spot[0], 0, spot[1], Blocks.MOSS_CARPET.defaultBlockState());
        }
        site.set(-HALF, 0, -HALF, Blocks.CHISELED_SANDSTONE.defaultBlockState());
        site.set(HALF, 0, HALF, Blocks.CHISELED_SANDSTONE.defaultBlockState());
        site.set(HALF, 1, HALF, Blocks.LANTERN.defaultBlockState());
    }

    /** Vache ou cochon : un enclos, sa barriere au nord, un abri a foin au fond. */
    private static void pen(Site site, Block fence, Block planks, Block slab, boolean muddy) {
        site.floor(() -> muddy ? site.pick(Blocks.MUD, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.MUD)
                : site.pick(Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT));
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                if (Math.abs(x) == HALF || Math.abs(z) == HALF) {
                    site.shaped(x, 0, z, fence.defaultBlockState());
                }
            }
        }
        site.set(0, 0, -HALF, Blocks.AIR.defaultBlockState());
        // L'abri : deux poteaux, un toit, du foin dessous.
        for (int x = -2; x <= 2; x++) {
            for (int z = 1; z <= HALF; z++) {
                site.set(x, 2, z, z == 1 ? slab.defaultBlockState() : planks.defaultBlockState());
            }
        }
        for (int x = -2; x <= 2; x += 4) {
            site.shaped(x, 0, 1, fence.defaultBlockState());
            site.shaped(x, 1, 1, fence.defaultBlockState());
            site.shaped(x, 1, HALF, fence.defaultBlockState());
        }
        site.set(-1, 0, 2, Blocks.HAY_BLOCK.defaultBlockState());
        site.set(-2, 0, 2, Blocks.HAY_BLOCK.defaultBlockState());
        site.set(-2, 1, 2, Blocks.HAY_BLOCK.defaultBlockState());
        site.set(2, 0, 2, muddy ? Blocks.COMPOSTER.defaultBlockState() : Blocks.CAULDRON.defaultBlockState());
        site.set(HALF, 1, -HALF, Blocks.LANTERN.defaultBlockState());
    }

    // -- Sous terre -------------------------------------------------------------------

    /** Une salle fermee de 7 x 7 sur 4 de haut, aux materiaux de la creature. */
    private static void room(Site site, Kind kind) {
        for (int x = -HALF - 1; x <= HALF + 1; x++) {
            for (int z = -HALF - 1; z <= HALF + 1; z++) {
                for (int y = -1; y <= 4; y++) {
                    boolean shell = Math.abs(x) == HALF + 1 || Math.abs(z) == HALF + 1 || y == -1 || y == 4;
                    site.set(x, y, z, shell ? wall(site, kind, y) : Blocks.AIR.defaultBlockState());
                }
            }
        }
        if (kind == Kind.COW || kind == Kind.PIG) {
            // Quatre lanternes au plafond : plus de 8 de lumiere sur tout le sol, il en faut aux betes.
            BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
            for (int x = -2; x <= 2; x += 4) {
                for (int z = -2; z <= 2; z += 4) {
                    site.set(x, 3, z, lantern);
                }
            }
        }
        switch (kind) {
            case ZOMBIE -> {
                for (int[] corner : new int[][]{{-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}}) {
                    site.set(corner[0], 0, corner[1], Blocks.COBWEB.defaultBlockState());
                    site.set(corner[0], 3, corner[1], Blocks.COBWEB.defaultBlockState());
                }
            }
            case SKELETON -> {
                for (int[] corner : new int[][]{{-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}}) {
                    for (int y = 0; y < 4; y++) {
                        site.set(corner[0], y, corner[1], Blocks.BONE_BLOCK.defaultBlockState());
                    }
                }
                site.set(0, 0, 2, Blocks.SKELETON_SKULL.defaultBlockState());
            }
            case WITCH -> {
                for (int z = -HALF; z <= HALF; z++) {
                    site.set(-HALF, 0, z, Blocks.BOOKSHELF.defaultBlockState());
                    site.set(-HALF, 1, z, Blocks.BOOKSHELF.defaultBlockState());
                }
                site.set(2, 0, -2, Blocks.CAULDRON.defaultBlockState());
                site.set(2, 0, 0, Blocks.CRAFTING_TABLE.defaultBlockState());
                site.set(HALF, 3, -HALF, Blocks.COBWEB.defaultBlockState());
            }
            case CREEPER -> {
                for (int x = -2; x <= 2; x += 4) {
                    for (int z = -2; z <= 2; z += 4) {
                        site.set(x, 0, z, Blocks.CUT_SANDSTONE.defaultBlockState());
                        site.set(x, 1, z, Blocks.CHISELED_SANDSTONE.defaultBlockState());
                        site.set(x, 2, z, Blocks.CUT_SANDSTONE.defaultBlockState());
                        site.set(x, 3, z, Blocks.CUT_SANDSTONE.defaultBlockState());
                    }
                }
            }
            default -> {
                for (int x = -HALF; x <= -1; x++) {
                    site.set(x, 0, HALF, Blocks.HAY_BLOCK.defaultBlockState());
                }
                site.set(-HALF, 1, HALF, Blocks.HAY_BLOCK.defaultBlockState());
                site.set(-HALF, 0, -HALF, kind == Kind.PIG ? Blocks.COMPOSTER.defaultBlockState()
                        : Blocks.CAULDRON.defaultBlockState());
            }
        }
    }

    private static BlockState wall(Site site, Kind kind, int y) {
        return switch (kind) {
            case ZOMBIE -> site.pick(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
            case SKELETON -> site.pick(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES,
                    Blocks.DEEPSLATE_BRICKS);
            case WITCH -> y == -1 || y == 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                    : site.pick(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS);
            case CREEPER -> y == -1 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                    : site.pick(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE);
            case COW -> y == -1 ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : site.pick(Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG);
            // Le sol reste surtout de l'herbe : un cochon n'apparait que sur elle.
            case PIG -> y == -1 ? site.pick(Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.MUD)
                    : site.pick(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG);
        };
    }

    // -- Le chantier ------------------------------------------------------------------

    /** Le terrain d'une ruine : l'origine est la case du spawner, au-dessus du sol. */
    private static final class Site {
        final WorldGenLevel level;
        final BlockPos origin;
        final RandomSource random;
        final List<BlockPos> shaped = new ArrayList<>();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        Site(WorldGenLevel level, BlockPos origin, RandomSource random) {
            this.level = level;
            this.origin = origin;
            this.random = random;
        }

        BlockPos at(int x, int y, int z) {
            return cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        }

        void set(int x, int y, int z, BlockState state) {
            level.setBlock(at(x, y, z), state, Block.UPDATE_CLIENTS);
        }

        /** Un bloc dont la forme depend de ses voisins (barriere) : ajuste a la fin. */
        void shaped(int x, int y, int z, BlockState state) {
            set(x, y, z, state);
            shaped.add(at(x, y, z).immutable());
        }

        BlockState pick(Block... blocks) {
            return blocks[random.nextInt(blocks.length)].defaultBlockState();
        }

        private boolean solid(int x, int y, int z) {
            BlockState state = level.getBlockState(at(x, y, z));
            return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced();
        }

        /** En surface : sur la terre ferme, et moins de trois blocs d'ecart aux quatre coins. */
        boolean flat() {
            if (!solid(0, -1, 0) || !level.getFluidState(at(0, 0, 0)).isEmpty()) {
                return false;
            }
            for (int x = -HALF; x <= HALF; x += HALF) {
                for (int z = -HALF; z <= HALF; z += HALF) {
                    int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX() + x, origin.getZ() + z);
                    int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX() + x,
                            origin.getZ() + z);
                    if (Math.abs(ground - origin.getY()) > 2 || surface - ground > 12
                            || !level.getFluidState(at(x, ground - origin.getY(), z)).isEmpty()) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Sous terre : de la roche aux huit coins, et pas de liquide au centre. */
        boolean enclosed() {
            if (origin.getY() - 2 <= level.getMinY() || !level.getFluidState(at(0, 0, 0)).isEmpty()
                    || !level.getFluidState(at(0, 3, 0)).isEmpty()) {
                return false;
            }
            int surface = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());
            if (origin.getY() + 8 > surface) {
                return false;
            }
            int rock = 0;
            for (int x = -HALF - 1; x <= HALF + 1; x += 2 * HALF + 2) {
                for (int z = -HALF - 1; z <= HALF + 1; z += 2 * HALF + 2) {
                    if (solid(x, -1, z)) {
                        rock++;
                    }
                    if (solid(x, 4, z)) {
                        rock++;
                    }
                }
            }
            return rock >= 6;
        }

        /** Degage l'emprise au-dessus du sol et la pose sur des fondations. */
        void level() {
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    for (int y = 0; y <= 7; y++) {
                        if (!level.getBlockState(at(x, y, z)).isAir()) {
                            set(x, y, z, Blocks.AIR.defaultBlockState());
                        }
                    }
                    for (int y = -2; y >= -5; y--) {
                        if (solid(x, y, z)) {
                            break;
                        }
                        set(x, y, z, Blocks.COBBLESTONE.defaultBlockState());
                    }
                }
            }
        }

        void floor(java.util.function.Supplier<BlockState> block) {
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    set(x, -1, z, block.get());
                }
            }
        }

        /** Les quatre murs, {@code height} de haut ; {@code gap} donne par rangee la part de trous. */
        void walls(int height, java.util.function.IntFunction<Float> gap, java.util.function.Supplier<BlockState> block) {
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    if (Math.abs(x) != HALF && Math.abs(z) != HALF) {
                        continue;
                    }
                    for (int y = 0; y < height; y++) {
                        if (random.nextFloat() < gap.apply(y)) {
                            break;
                        }
                        set(x, y, z, block.get());
                    }
                }
            }
        }

        /** Le seuil, au milieu du mur nord. */
        void door() {
            set(0, 0, -HALF, Blocks.AIR.defaultBlockState());
            set(0, 1, -HALF, Blocks.AIR.defaultBlockState());
        }

        /**
         * Le spawner. {@code free} : avec ses propres regles d'apparition (toute
         * lumiere, tout sol), pour la surface ; sinon celles de la creature.
         */
        void spawner(int x, int y, int z, EntityType<?> type, boolean free) {
            BlockPos pos = at(x, y, z).immutable();
            level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
                return;
            }
            if (!free) {
                spawner.setEntityId(type, random);
                return;
            }
            CompoundTag entity = new CompoundTag();
            entity.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            InclusiveRange<Integer> any = new InclusiveRange<>(0, 15);
            SpawnData data = new SpawnData(entity, Optional.of(new SpawnData.CustomSpawnRules(any, any)),
                    Optional.empty());
            CompoundTag tag = new CompoundTag();
            SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, data).result().ifPresent(encoded -> tag.put("SpawnData", encoded));
            // Niveau nul : sinon le spawner demande une mise a jour du bloc en pleine generation.
            spawner.getSpawner().load(null, pos, TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(),
                    tag));
        }

        /** Les barrieres se raccordent a leurs voisines. */
        void settle() {
            for (BlockPos pos : shaped) {
                BlockState state = level.getBlockState(pos);
                level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), Block.UPDATE_CLIENTS);
            }
        }
    }
}
