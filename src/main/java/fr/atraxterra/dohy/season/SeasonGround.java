package fr.atraxterra.dohy.season;

import fr.atraxterra.dohy.earth.DoHyEarth;
import fr.atraxterra.dohy.world.PlacedBlocks;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * La neige et la glace suivent la saison, meme sans personne pour regarder.
 *
 * <h2>Pourquoi le jeu ne le fait pas seul</h2>
 *
 * <p>Le jeu fait tomber la neige et geler l'eau quand il fait froid, mais ne
 * les fait jamais fondre au redoux : seule une torche y arrive. Et il ne
 * touche qu'aux chunks qui tournent, a deux pas du joueur. Sans aide, le
 * printemps laisserait partout les plaques de l'hiver.
 *
 * <h2>Deux passes</h2>
 * <ul>
 *   <li><b>Le balayage</b> : a chaque tick, une cinquantaine de chunks charges,
 *       deux colonnes au hasard dans chacun. Une couche de neige ou une glace
 *       ou il fait doux fond, du dessus des arbres jusqu'au sol. Tous les
 *       chunks charges y passent, ceux du spawn
 *       et les chunks forces compris : la fonte continue serveur sans joueur.
 *       Une plaque de neige disparait ainsi en quelques minutes.</li>
 *   <li><b>Le rattrapage</b> : un chunk qui revient en memoire est remis a la
 *       saison avant qu'on le voie, colonne par colonne — la neige fond s'il
 *       fait doux, et s'il fait froid il recoit la couche de neige et la glace
 *       de rive que l'hiver lui aurait donnees en son absence. Un chunk ne
 *       passe qu'une fois par palier (64 par annee, un peu plus de 10 h).</li>
 * </ul>
 *
 * <p>Un chunk que personne ne charge ne peut pas etre modifie : c'est le
 * rattrapage qui le remet d'aplomb, au moment ou il revient.
 *
 * <h2>Ce qui ne bouge jamais</h2>
 *
 * <p>Seules les couches de neige ({@code minecraft:snow}) et la glace simple
 * ({@code minecraft:ice}) a la surface fondent, et seulement la ou la
 * temperature, saison comprise, depasse {@link SeasonMath#MELT_LINE} : les
 * neiges des sommets et des poles tiennent. Les blocs poses par un joueur
 * ({@link PlacedBlocks}), les blocs de neige, la glace compacte et la glace
 * bleue ne sont jamais touches.
 */
@EventBusSubscriber
public final class SeasonGround {

    /** Paliers par annee : un chunk revu a son retour s'il ne l'a pas ete depuis le debut du palier. */
    static final int STEPS = 64;

    /** Le temps de rattrapage par tick. */
    private static final long BUDGET_NANOS = 2_000_000L;

    /** Le balayage : chunks par tick, colonnes par chunk. */
    private static final int CHUNKS_PER_TICK = 48;
    private static final int COLUMNS_PER_CHUNK = 2;

    private static final LongOpenHashSet LOADED = new LongOpenHashSet();
    private static final LongArrayList ORDER = new LongArrayList();
    private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();
    private static int cursor;

    private SeasonGround() {
    }

    static void clear() {
        LOADED.clear();
        ORDER.clear();
        PENDING.clear();
        cursor = 0;
    }

    /** Un chunk en un seul nombre : x dans les 32 bits bas, z dans les 32 bits hauts. */
    private static long key(LevelChunk chunk) {
        return ((chunk.getPos().getMinBlockX() >> 4) & 0xFFFFFFFFL)
                | ((long) (chunk.getPos().getMinBlockZ() >> 4) << 32);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && Seasons.covers(level)
                && event.getChunk() instanceof LevelChunk chunk) {
            long key = key(chunk);
            if (LOADED.add(key)) {
                ORDER.add(key);
            }
            PENDING.add(key);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel && event.getChunk() instanceof LevelChunk chunk) {
            long key = key(chunk);
            // l'ordre du balayage se nettoie en passant
            LOADED.remove(key);
            PENDING.remove(key);
        }
    }

    /** Le palier en cours : il change toutes les 1/64 d'annee. */
    static int step() {
        SeasonClimate.Clock clock = SeasonClimate.SERVER.clock();
        if (clock == null) {
            return -1;
        }
        long elapsed = SeasonClimate.SERVER.now() - clock.anchor();
        return (int) Math.floorMod(Math.floorDiv(elapsed * STEPS, clock.year()), (long) Integer.MAX_VALUE);
    }

    static void tick(ServerLevel level) {
        catchUp(level, step());
        sweep(level);
    }

    private static void catchUp(ServerLevel level, int step) {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        // un tour de file au plus par tick : un chunk pas encore accessible repasse en queue
        for (int tries = PENDING.size(); tries > 0 && System.nanoTime() < deadline; tries--) {
            long key = PENDING.removeFirstLong();
            if (!LOADED.contains(key)) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow((int) key, (int) (key >> 32));
            if (chunk == null) {
                // charge mais pas encore servi par le jeu (l'evenement de chargement le precede) :
                // on le reprendra, sinon il garderait sa neige jusqu'a son prochain retour
                PENDING.add(key);
                continue;
            }
            if (chunk.getData(DoHyEarth.SEASON_GROUND.get()) == step) {
                continue;
            }
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    column(level, minX + dx, minZ + dz, true);
                }
            }
            chunk.setData(DoHyEarth.SEASON_GROUND.get(), step);
        }
    }

    private static void sweep(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (int visited = 0; visited < CHUNKS_PER_TICK && !ORDER.isEmpty(); ) {
            if (cursor >= ORDER.size()) {
                cursor = 0;
            }
            long key = ORDER.getLong(cursor);
            if (!LOADED.contains(key)) {
                // retire en echangeant avec le dernier : l'ordre n'importe pas
                ORDER.set(cursor, ORDER.getLong(ORDER.size() - 1));
                ORDER.removeLong(ORDER.size() - 1);
                continue;
            }
            cursor++;
            visited++;
            LevelChunk chunk = level.getChunkSource().getChunkNow((int) key, (int) (key >> 32));
            if (chunk == null) {
                continue;
            }
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            for (int column = 0; column < COLUMNS_PER_CHUNK; column++) {
                column(level, minX + random.nextInt(16), minZ + random.nextInt(16), false);
            }
        }
    }

    /**
     * Met une colonne a la saison.
     *
     * @param catchUp vrai au retour du chunk : toute la neige fond d'un coup, et
     *                s'il fait froid la colonne recoit ce que l'hiver lui aurait donne ;
     *                faux au balayage : une couche a la fois, rien n'est ajoute.
     */
    private static void column(ServerLevel level, int x, int z, boolean catchUp) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        // du sommet de la colonne au sol : le dessus des arbres, les branches basses ou la neige a pu
        // se poser, le sol sous les feuilles, et le bloc du sol lui-meme (neige epaisse, glace)
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        int lowest = Math.min(top.getY(), ground.getY()) - 1;
        for (int y = top.getY(); y >= lowest; y--) {
            melt(level, new BlockPos(x, y, z), catchUp);
        }
        if (catchUp) {
            winter(level, top);
        }
    }

    /** Fait fondre la neige ou la glace de cette case s'il y fait doux. */
    private static void melt(ServerLevel level, BlockPos pos, boolean all) {
        BlockState state = level.getBlockState(pos);
        boolean snow = state.is(Blocks.SNOW);
        if (!snow && !state.is(Blocks.ICE)) {
            return;
        }
        if (Seasons.temperature(level, pos) < SeasonMath.MELT_LINE || PlacedBlocks.isPlayerPlaced(level, pos)) {
            return;
        }
        if (!snow) {
            level.setBlockAndUpdate(pos, IceBlock.meltsInto());
            return;
        }
        int layers = state.getValue(SnowLayerBlock.LAYERS);
        if (all || layers <= 1) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1), Block.UPDATE_ALL);
        }
    }

    /**
     * L'hiver qu'un chunk a manque : la glace de rive et une couche de neige, la
     * ou le jeu les aurait poses lui-meme (memes regles, saison comprise).
     */
    private static void winter(ServerLevel level, BlockPos top) {
        BlockPos below = top.below();
        Biome biome = level.getBiome(below).value();
        // les voisins doivent etre en memoire : lire un chunk absent le chargerait
        if (level.isAreaLoaded(below, 1) && biome.shouldFreeze(level, below)) {
            level.setBlockAndUpdate(below, Blocks.ICE.defaultBlockState());
        }
        if (biome.shouldSnow(level, top)) {
            level.setBlockAndUpdate(top, Blocks.SNOW.defaultBlockState());
        }
    }
}
