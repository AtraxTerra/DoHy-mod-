package fr.atraxterra.dohy.earth.disaster;

import fr.atraxterra.dohy.earth.DoHyEarth;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.HologramBlockEntity;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Les volcans de la carte : a l'arrivee de ses chunks, chaque volcan recoit son
 * cone et son cratere, une fois pour toutes (comme le reacteur de Tchernobyl
 * ou l'aire d'une base de lancement).
 *
 * <p>Le cone : un renflement de 18 blocs au plus (moins sur un relief deja
 * haut) sur 28 blocs de rayon, pose sur le terrain tel qu'il est — chaque
 * colonne monte depuis sa propre surface, donc rien ne se lit hors du chunk.
 * Pres du sommet, de la blackstone, du basalte et du magma ; plus bas, du tuf,
 * du basalte et du gravier ; au pied, de la terre sterile et de l'andesite qui
 * se fondent dans le paysage. La vegetation du cone est retiree. Au centre, un
 * cratere de six blocs de rayon et son lac de lave, du soufre sur le rebord, et
 * le nom du volcan au-dessus.
 */
final class Volcanoes {

    /** Change-la pour que les chunks deja servis le soient a nouveau. */
    static final int VERSION = 1;
    static final int RADIUS = 28;
    private static final int CRATER = 6;
    private static final int HEIGHT = 18;

    private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();

    private Volcanoes() {
    }

    static void clear() {
        QUEUE.clear();
    }

    /** Vrai si ce chunk a deja recu sa part de volcan (ou n'en a pas). */
    static boolean served(LevelChunk chunk) {
        return chunk.getData(DoHyEarth.VOLCANO.get()) >= VERSION;
    }

    static void arrived(LevelChunk chunk) {
        if (served(chunk)) {
            return;
        }
        int centerX = chunk.getPos().getMinBlockX() + 8;
        int centerZ = chunk.getPos().getMinBlockZ() + 8;
        for (DisasterConfig.PlacedVolcano volcano : DisasterConfig.placed().volcanoes()) {
            if (volcano.distance(centerX, centerZ) <= RADIUS + 12) {
                QUEUE.add(DisasterTerrain.key(chunk.getPos().x(), chunk.getPos().z()));
                return;
            }
        }
    }

    static void tick(ServerLevel level) {
        for (int count = 0; count < 2 && !QUEUE.isEmpty(); count++) {
            long key = QUEUE.removeFirstLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow(DisasterTerrain.keyX(key), DisasterTerrain.keyZ(key));
            if (chunk == null || served(chunk)) {
                continue;
            }
            for (DisasterConfig.PlacedVolcano volcano : DisasterConfig.placed().volcanoes()) {
                if (volcano.distance(chunk.getPos().getMinBlockX() + 8, chunk.getPos().getMinBlockZ() + 8)
                        <= RADIUS + 12) {
                    build(level, chunk, volcano);
                }
            }
            chunk.setData(DoHyEarth.VOLCANO.get(), VERSION);
        }
    }

    /** Le renflement du cone a cette distance du centre, pour une colonne dont la surface est en {@code surface}. */
    private static int bump(double distance, int surface, int sea) {
        if (distance >= RADIUS) {
            return 0;
        }
        double t = 1.0d - distance / RADIUS;
        double smooth = t * t * (3.0d - 2.0d * t);
        // sur un relief deja haut, le cone se fait plus modeste
        double height = Math.max(6.0d, HEIGHT - Math.max(0, surface - sea - 30) / 2.0d);
        return (int) Math.round(height * smooth);
    }

    private static void build(ServerLevel level, LevelChunk chunk, DisasterConfig.PlacedVolcano volcano) {
        RandomSource random = RandomSource.create(level.getSeed() ^ volcano.volcano().id().hashCode()
                ^ DisasterTerrain.key(chunk.getPos().x(), chunk.getPos().z()));
        int sea = level.getSeaLevel();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockState basalt = Eruption.hbm("basalt", Blocks.BASALT.defaultBlockState());
        BlockState sulfur = Eruption.vanilla("sulfur", Eruption.vanilla("yellow_terracotta", Blocks.SANDSTONE.defaultBlockState()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean centerChunk = chunk.getPos().getMinBlockX() <= volcano.x() && volcano.x() < minX + 16
                && chunk.getPos().getMinBlockZ() <= volcano.z() && volcano.z() < minZ + 16;
        int centerTop = 0;
        if (centerChunk) {
            int surface = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, volcano.x() & 15, volcano.z() & 15) - 1;
            centerTop = Math.max(surface, sea) + bump(0.0d, Math.max(surface, sea), sea);
        }
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                double distance = Math.hypot(x + 0.5d - volcano.x(), z + 0.5d - volcano.z());
                if (distance >= RADIUS) {
                    continue;
                }
                int surface = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, dx, dz) - 1;
                int ground = Math.max(surface, sea);
                // la vegetation du cone part, jusqu'a vingt-quatre blocs au-dessus du sol
                for (int y = surface + 1; y <= surface + 24; y++) {
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || !state.isAir()
                            && state.getFluidState().isEmpty() && (state.canBeReplaced()
                            || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS))) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                int top = ground + bump(distance, ground, sea);
                for (int y = surface + 1; y <= top; y++) {
                    BlockState here = chunk.getBlockState(pos.set(x, y, z));
                    if (!here.isAir() && !here.getFluidState().is(FluidTags.WATER)) {
                        continue;
                    }
                    level.setBlock(pos, y == top ? skin(distance, random, basalt) : Blocks.STONE.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
                // la peau du cone, meme la ou il ne monte pas
                BlockState current = chunk.getBlockState(pos.set(x, top, z));
                if (current.is(BlockTags.DIRT) || current.is(Blocks.GRASS_BLOCK) || current.is(BlockTags.SAND)
                        || current.is(Blocks.STONE) || current.is(Blocks.GRAVEL) || current.is(Blocks.PODZOL)) {
                    level.setBlock(pos, skin(distance, random, basalt), Block.UPDATE_CLIENTS);
                }
                if (centerChunk && distance < CRATER + 3) {
                    crater(level, pos, x, z, distance, centerTop, random, basalt, sulfur);
                }
            }
        }
        if (centerChunk) {
            BlockPos label = new BlockPos(volcano.x(), centerTop + 10, volcano.z());
            level.setBlock(label, DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(label) instanceof HologramBlockEntity hologram) {
                hologram.set(volcano.volcano().name() + " - volcan actif", 0xFF6A1A, 1.4f);
            }
        }
    }

    /** La roche de surface, selon la distance au cratere. */
    private static BlockState skin(double distance, RandomSource random, BlockState basalt) {
        int roll = random.nextInt(100);
        if (distance < 10.0d) {
            return roll < 40 ? Blocks.BLACKSTONE.defaultBlockState() : roll < 80 ? basalt
                    : roll < 90 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        if (distance < 19.0d) {
            return roll < 45 ? Blocks.TUFF.defaultBlockState() : roll < 75 ? basalt
                    : roll < 90 ? Blocks.GRAVEL.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        }
        return roll < 35 ? Blocks.COARSE_DIRT.defaultBlockState() : roll < 60 ? Blocks.ANDESITE.defaultBlockState()
                : roll < 75 ? Blocks.TUFF.defaultBlockState() : roll < 88 ? Blocks.GRAVEL.defaultBlockState()
                : Blocks.GRASS_BLOCK.defaultBlockState();
    }

    /** Le cratere : creuse sous le sommet, borde de blackstone, un lac de lave au fond, du soufre sur le rebord. */
    private static void crater(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, double distance,
                               int centerTop, RandomSource random, BlockState basalt, BlockState sulfur) {
        int floor = centerTop - 6;
        if (distance < CRATER) {
            // creuse en cuvette : plus profond au centre
            int bottom = floor + (int) Math.round(distance * 0.6d);
            for (int y = bottom + 1; y <= centerTop + 2; y++) {
                level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            boolean lake = distance < 3.6d;
            level.setBlock(pos.set(x, bottom, z), lake ? Blocks.LAVA.defaultBlockState()
                    : random.nextBoolean() ? Blocks.BLACKSTONE.defaultBlockState() : basalt, Block.UPDATE_CLIENTS);
            level.setBlock(pos.set(x, bottom - 1, z), lake ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(pos.set(x, bottom - 2, z), Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (lake) {
                // le lac tient dans sa cuvette : ses voisins sont de la roche
                for (int y = bottom; y > bottom - 3; y--) {
                    level.setBlock(pos.set(x, y - 1, z), y == bottom ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        } else if (random.nextInt(100) < 22) {
            // du soufre sur le rebord
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            level.setBlock(pos.set(x, top, z), sulfur, Block.UPDATE_CLIENTS);
        }
    }
}
