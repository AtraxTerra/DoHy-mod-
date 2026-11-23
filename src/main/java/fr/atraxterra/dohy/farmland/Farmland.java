package fr.atraxterra.dohy.farmland;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.farmland.FarmlandMap.Zone;
import fr.atraxterra.dohy.palamod.dungeon.ReturnPoint;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.HologramBlockEntity;
import fr.atraxterra.dohy.spawn.SpawnApi;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La farmland (lot 24e) : la dimension du Chasseur.
 *
 * <p>D'apres le wiki de Paladium : une carte construite et indestructible, avec
 * un lac, une foret, un coin du Nether et un coin de l'End ; PvP inactif, PvE
 * actif ; on peut y mourir, mais la faim ne baisse pas ; les commandes de faction
 * n'y marchent pas. N'y apparaissent que les creatures du metier de Chasseur
 * ({@link FarmlandSpawner}) ; les animaux y vivent dix minutes si personne ne les
 * attaque.
 *
 * <p>La carte n'est pas un fichier de structure : {@link FarmlandMap} et
 * {@link FarmlandDecor} la dessinent bloc par bloc a la premiere visite, quelques
 * milliers de blocs par tick, puis peignent ses biomes ; elle est redessinee quand
 * {@link #VERSION} change. On y entre par la borne DONJONS du spawn (carte
 * « Farmland ») ou, au grade Heros, par {@code /farmland} ; on en sort par la borne
 * RETOUR du camp ou {@code /spawn}.
 */
public final class Farmland {

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "farmland"));

    /** La version de la carte : la changer la fait redessiner. */
    static final int VERSION = 1;
    /** Les blocs poses par tick pendant la construction. */
    private static final int PER_TICK = 8000;
    /** Tranches de x videes par tick avant une reconstruction. */
    private static final int CLEAR_SLICES = 3;
    /** Le rayon du ticket qui garde la carte chargee pendant la construction. */
    private static final int HOLD = 8;

    /** L'arrivee : au sud du camp, face au nord. */
    public static final Vec3 ARRIVAL = new Vec3(0.5d, FarmlandMap.G + 1.0d, 7.5d);

    private static Job job;

    private Farmland() {
    }

    public static boolean inFarmland(Entity entity) {
        return entity.level().dimension() == DIMENSION;
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DIMENSION);
    }

    /** Vrai si la carte de cette version est posee. */
    public static boolean ready(ServerLevel level) {
        return level.getData(DoHyFarmland.BUILT.get()) == VERSION;
    }

    /** Vrai tant qu'une construction est en cours. */
    public static boolean building() {
        return job != null;
    }

    /** On ne la rejoint que depuis les dimensions du jeu, le spawn, la Mine et Arrakis. */
    static boolean reachable(ServerPlayer player) {
        return player.level().dimension() == Level.OVERWORLD || player.level().dimension() == Level.NETHER
                || player.level().dimension() == Level.END || SpawnApi.inSpawn(player)
                || fr.atraxterra.dohy.mine.MineService.inMine(player)
                || fr.atraxterra.dohy.arrakis.DoHyArrakis.onArrakis(player);
    }

    /** Vers le camp de la farmland, apres l'avoir construite s'il le faut. */
    public static void enter(ServerPlayer player) {
        if (inFarmland(player)) {
            tell(player, "Tu es deja a la farmland.", ChatFormatting.GRAY);
            return;
        }
        if (!reachable(player)) {
            tell(player, "La farmland ne se rejoint pas d'ici.", ChatFormatting.RED);
            return;
        }
        ServerLevel level = level(player.level().getServer());
        if (level == null) {
            tell(player, "La farmland n'existe pas dans ce monde.", ChatFormatting.RED);
            return;
        }
        if (!ready(level)) {
            if (job == null) {
                job = new Job(level, level.getData(DoHyFarmland.BUILT.get()) > 0);
                DoHy.LOGGER.info("Farmland : construction de la version {} ({} chunks)", VERSION, job.chunks.size());
            }
            job.waiting.add(player.getUUID());
            tell(player, "La farmland se construit, tu y seras envoye dans quelques secondes.", ChatFormatting.GOLD);
            return;
        }
        send(player, level);
    }

    private static void send(ServerPlayer player, ServerLevel level) {
        if (!SpawnApi.inSpawn(player)) {
            // comme si l'on etait passe par le spawn : /spawn, puis la borne RETOUR, ramenent ici
            player.setData(DoHySpawn.RETURN.get(), ReturnPoint.of(player));
        }
        player.closeContainer();
        toHub(player, level);
        tell(player, "Bienvenue a la farmland : les creatures du Chasseur, et elles seules.", ChatFormatting.GOLD);
        tell(player, "Rien ne s'y casse, la faim n'y baisse pas, ni PvP ni commandes de faction. "
                + "Sortie : la borne RETOUR ou /spawn.", ChatFormatting.GRAY);
    }

    /** Pose le joueur au camp, face au nord. */
    static void toHub(ServerPlayer player, ServerLevel level) {
        player.fallDistance = 0.0f;
        player.teleport(new TeleportTransition(level, ARRIVAL, Vec3.ZERO, 180.0f, 0.0f,
                TeleportTransition.DO_NOTHING));
    }

    /** La borne RETOUR du camp, ou {@code /spawn} : vers la plateforme du spawn. */
    public static void leave(ServerPlayer player) {
        player.closeContainer();
        SpawnApi.backToPad(player);
    }

    /** Hors de la carte : au-dela des falaises, ou tombe dans le vide. */
    public static boolean outOfBounds(Entity entity) {
        return entity.getY() < FarmlandMap.BOTTOM - 12
                || FarmlandMap.radius(entity.getX(), entity.getZ()) > FarmlandMap.RIM + 1.0d;
    }

    /** Le coin ou se trouve une entite. */
    public static Zone zoneOf(Entity entity) {
        return FarmlandMap.zone((int) Math.floor(entity.getX()), (int) Math.floor(entity.getZ()));
    }

    static void tick(MinecraftServer server) {
        if (job == null) {
            return;
        }
        if (job.step()) {
            Job done = job;
            job = null;
            done.level.setData(DoHyFarmland.BUILT.get(), VERSION);
            DoHy.LOGGER.info("Farmland : construite{}", B.MISSING.isEmpty() ? "" : " (blocs inconnus : "
                    + B.MISSING + ")");
            for (UUID id : done.waiting) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && player.isAlive() && !inFarmland(player) && reachable(player)) {
                    send(player, done.level);
                }
            }
        }
    }

    static void stop() {
        job = null;
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    // -- Les biomes -------------------------------------------------------------------------------

    private static String biomeName(Zone zone) {
        return switch (zone) {
            case FOREST -> "forest";
            case MEADOW -> "meadow";
            case MUSHROOM -> "mushroom_fields";
            case HILLS -> "windswept_hills";
            case JUNGLE -> "bamboo_jungle";
            case LAKE -> "river";
            case BEACH -> "beach";
            case SWAMP -> "swamp";
            case NETHER -> "crimson_forest";
            case END -> "the_end";
            case VOID -> "the_void";
            default -> "plains";
        };
    }

    private static Map<Zone, Holder<Biome>> biomes(ServerLevel level) {
        Map<Zone, Holder<Biome>> out = new EnumMap<>(Zone.class);
        var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        for (Zone zone : Zone.values()) {
            registry.get(ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(biomeName(zone))))
                    .ifPresent(holder -> out.put(zone, holder));
        }
        return out;
    }

    /** Peint les biomes d'un chunk selon les coins (la ceinture prend celui qu'elle borde). */
    private static void paint(ServerLevel level, LevelChunk chunk, Map<Zone, Holder<Biome>> biomes) {
        chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
            int x = (qx << 2) + 2;
            int z = (qz << 2) + 2;
            Zone zone = FarmlandMap.zone(x, z);
            if (zone == Zone.RIM) {
                zone = FarmlandMap.inner(x, z);
            }
            Holder<Biome> biome = biomes.get(zone);
            return biome != null ? biome : chunk.getNoiseBiome(qx, qy, qz);
        }, level.getChunkSource().randomState().sampler());
        chunk.markUnsaved();
    }

    /** Barreaux, vitres, barrieres, murets, escaliers, chorus : leur forme suit leurs voisins. */
    private static boolean shaped(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_fence") || path.endsWith("pane") || path.endsWith("_wall") || path.endsWith("bars")
                || path.endsWith("_stairs") || path.equals("chorus_plant");
    }

    // -- La construction --------------------------------------------------------------------------

    /** Une construction en cours : vider l'ancienne carte s'il y en a une, poser, raccorder, etiqueter. */
    private static final class Job {
        private final ServerLevel level;
        private final FarmlandDecor.Decor decor;
        private final Map<Zone, Holder<Biome>> biomes;
        private final Set<UUID> waiting = new LinkedHashSet<>();
        private final List<long[]> chunks = new ArrayList<>();
        private final List<BlockPos> shapedBlocks = new ArrayList<>();
        private final List<ChunkAccess> painted = new ArrayList<>();
        private int chunkIndex;
        private LongArrayList positions;
        private List<BlockState> states;
        private int placeIndex;
        private int currentX;
        private int currentZ;
        private int shapeIndex;
        private boolean clearing;
        private int clearX = -FarmlandMap.RIM - 2;
        private boolean finished;

        Job(ServerLevel level, boolean rebuild) {
            this.level = level;
            this.decor = FarmlandDecor.build();
            this.biomes = biomes(level);
            int reach = (FarmlandMap.RIM >> 4) + 1;
            for (int cx = -reach; cx <= reach; cx++) {
                for (int cz = -reach; cz <= reach; cz++) {
                    double nearX = Math.max(cx * 16, Math.min(0, cx * 16 + 15));
                    double nearZ = Math.max(cz * 16, Math.min(0, cz * 16 + 15));
                    if (FarmlandMap.radius(nearX, nearZ) <= FarmlandMap.RIM + 1) {
                        chunks.add(new long[] {cx, cz});
                    }
                }
            }
            // le camp d'abord : on pourra y arriver avant que les bords soient finis
            chunks.sort((a, b) -> Long.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
            level.getChunkSource().addTicketWithRadius(TicketType.FORCED, ChunkPos.containing(BlockPos.ZERO), HOLD);
            this.clearing = rebuild;
            if (rebuild) {
                FarmlandSpawner.discardAll(level);
            }
        }

        /** Avance d'un tick ; vrai quand tout est fini. */
        boolean step() {
            if (clearing) {
                clear();
                return false;
            }
            int budget = PER_TICK;
            while (budget > 0) {
                if (positions == null) {
                    if (chunkIndex >= chunks.size()) {
                        break;
                    }
                    long[] chunk = chunks.get(chunkIndex++);
                    generate((int) chunk[0], (int) chunk[1]);
                }
                while (budget > 0 && placeIndex < positions.size()) {
                    BlockPos pos = BlockPos.of(positions.getLong(placeIndex));
                    BlockState state = states.get(placeIndex);
                    placeIndex++;
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    budget--;
                    if (shaped(state)) {
                        shapedBlocks.add(pos);
                    }
                }
                if (placeIndex >= positions.size()) {
                    LevelChunk chunk = level.getChunk(currentX, currentZ);
                    paint(level, chunk, biomes);
                    painted.add(chunk);
                    positions = null;
                    states = null;
                }
            }
            if (positions != null || chunkIndex < chunks.size()) {
                return false;
            }
            if (shapeIndex < shapedBlocks.size()) {
                for (int count = 0; count < PER_TICK && shapeIndex < shapedBlocks.size(); count++) {
                    BlockPos pos = shapedBlocks.get(shapeIndex++);
                    BlockState state = level.getBlockState(pos);
                    BlockState joined = Block.updateFromNeighbourShapes(state, level, pos);
                    if (joined != state) {
                        level.setBlock(pos, joined, Block.UPDATE_CLIENTS);
                    }
                }
                return false;
            }
            if (!finished) {
                finished = true;
                for (FarmlandDecor.Label label : decor.labels) {
                    level.setBlock(label.pos(), DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(label.pos()) instanceof HologramBlockEntity hologram) {
                        hologram.set(label.text(), label.color(), label.scale());
                    }
                }
                level.getChunkSource().chunkMap.resendBiomesForChunks(painted);
                FarmlandSpawner.caveFloor(decor.caveFloor);
                return false;
            }
            level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, ChunkPos.containing(BlockPos.ZERO),
                    HOLD);
            return true;
        }

        /** Les blocs d'un chunk : le sol colonne par colonne, puis les decors qui y tombent. */
        private void generate(int cx, int cz) {
            positions = new LongArrayList();
            states = new ArrayList<>();
            placeIndex = 0;
            currentX = cx;
            currentZ = cz;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    FarmlandMap.column(cx * 16 + lx, cz * 16 + lz, (x, y, z, state) -> {
                        positions.add(BlockPos.asLong(x, y, z));
                        states.add(state);
                    });
                }
            }
            LongArrayList extra = decor.byChunk.get(FarmlandDecor.chunkKey(cx, cz));
            if (extra != null) {
                for (int i = 0; i < extra.size(); i++) {
                    long key = extra.getLong(i);
                    positions.add(key);
                    states.add(decor.blocks.get(key));
                }
            }
        }

        /** Vide tout le volume de l'ancienne carte, quelques tranches de x par tick. */
        private void clear() {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int edge = FarmlandMap.RIM + 2;
            for (int slice = 0; slice < CLEAR_SLICES && clearX <= edge; slice++, clearX++) {
                for (int z = -edge; z <= edge; z++) {
                    for (int y = FarmlandMap.BOTTOM - 8; y <= FarmlandMap.G + 48; y++) {
                        pos.set(clearX, y, z);
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
            if (clearX > edge) {
                clearing = false;
            }
        }
    }
}
