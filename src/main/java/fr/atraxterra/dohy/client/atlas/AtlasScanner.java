package fr.atraxterra.dohy.client.atlas;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Releve le terrain des chunks que le client a recus : pour chaque colonne, la
 * couleur de carte du bloc du dessus, eclaircie ou assombrie selon la pente
 * (comme les cartes du jeu), et l'eau plus sombre quand elle est profonde.
 *
 * <p>Quelques chunks par tick, du plus proche au plus lointain ; les chunks
 * autour du joueur sont relus toutes les cinq secondes (ce qu'il construit).
 */
final class AtlasScanner {

    private static final int PER_TICK = 6;
    private static final long REFRESH = 100L;
    private static final long FAR_REFRESH = 1200L;

    /** Le tick du dernier releve de chaque chunk. */
    private final Long2LongOpenHashMap scanned = new Long2LongOpenHashMap();

    void clear() {
        scanned.clear();
    }

    void tick(ClientLevel level, int playerChunkX, int playerChunkZ, int radius, AtlasMap map) {
        long now = level.getGameTime();
        int budget = PER_TICK;
        for (int ring = 0; ring <= radius && budget > 0; ring++) {
            for (int dx = -ring; dx <= ring && budget > 0; dx++) {
                for (int dz = -ring; dz <= ring && budget > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int cx = playerChunkX + dx;
                    int cz = playerChunkZ + dz;
                    long key = (cx & 0xFFFFFFFFL) | ((long) cz << 32);
                    long last = scanned.getOrDefault(key, Long.MIN_VALUE);
                    long wait = ring <= 1 ? REFRESH : FAR_REFRESH;
                    if (last != Long.MIN_VALUE && now - last < wait && now >= last) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null || chunk.isEmpty()) {
                        continue;
                    }
                    scan(level, chunk, map);
                    scanned.put(key, now);
                    budget--;
                }
            }
        }
    }

    private static void scan(ClientLevel level, LevelChunk chunk, AtlasMap map) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        boolean ceiling = level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] north = new int[16];
        for (int lx = 0; lx < 16; lx++) {
            north[lx] = surface(level, chunk, minX + lx, minZ - 1, ceiling, pos);
        }
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = minX + lx;
                int z = minZ + lz;
                int y = surface(level, chunk, x, z, ceiling, pos);
                if (y == Integer.MIN_VALUE) {
                    north[lx] = y;
                    continue;
                }
                int color = color(level, chunk, x, y, z, pos);
                if (color != 0) {
                    int above = north[lx];
                    double shade = above == Integer.MIN_VALUE || above == y ? 1.0d : y > above ? 1.12d : 0.84d;
                    map.set(x, z, shaded(color, shade));
                }
                north[lx] = y;
            }
        }
    }

    /** La hauteur du bloc du dessus ; sous un plafond (le Nether), le premier sol sous la place libre. */
    private static int surface(ClientLevel level, LevelChunk chunk, int x, int z, boolean ceiling,
                               BlockPos.MutableBlockPos pos) {
        if ((x >> 4) != chunk.getPos().getMinBlockX() >> 4 || (z >> 4) != chunk.getPos().getMinBlockZ() >> 4) {
            LevelChunk other = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (other == null || other.isEmpty()) {
                return Integer.MIN_VALUE;
            }
            chunk = other;
        }
        if (!ceiling) {
            return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        }
        int y = Math.min(chunk.getMaxY() - 1, 120);
        boolean air = false;
        for (; y > chunk.getMinY(); y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (state.isAir()) {
                air = true;
            } else if (air) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int color(ClientLevel level, LevelChunk chunk, int x, int y, int z, BlockPos.MutableBlockPos pos) {
        for (int step = 0; step < 4; step++, y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (state.getFluidState().is(FluidTags.WATER)) {
                int depth = 1;
                while (depth < 12 && chunk.getBlockState(pos.set(x, y - depth, z)).getFluidState().is(FluidTags.WATER)) {
                    depth++;
                }
                return 0xFF000000 | shaded(MapColor.WATER.col, 1.05d - depth * 0.045d);
            }
            MapColor color = state.getMapColor(level, pos);
            if (color != MapColor.NONE && color.col != 0) {
                return 0xFF000000 | color.col;
            }
        }
        return 0;
    }

    static int shaded(int color, double factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
