package fr.atraxterra.dohy.earth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.HologramBlockEntity;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Les lieux de la Terre : les zones irradiees (Tchernobyl, Fukushima) et les
 * bases de lancement (Baikonour, Cap Canaveral, Kourou, Jiuquan).
 *
 * <p>Un lieu est donne par sa latitude et sa longitude : il vaut pour toute
 * carte calee sur le globe ({@link EarthData}), et la table vit dans le mod,
 * {@code data/dohy/earth/sites.json}.
 *
 * <p><b>Une zone irradiee</b> recoit, a l'arrivee de ses chunks, un biome a
 * elle — la radiation y vient de NTM NEXT, qui irradie les biomes que lui
 * designent ses donnees ({@code data/dohy/hbm/biome_radiation}) : rien de la
 * radiation n'est code ici, et sans NTM NEXT la zone n'est qu'un decor. Au
 * centre, la terre et les arbres morts de NTM NEXT et, sur le site du
 * reacteur, un tas de corium. Les minerais rares de la zone sont des
 * gisements ({@link EarthDeposits}, champ {@code sites}).
 *
 * <p><b>Une base de lancement</b> recoit une aire de beton, et elle seule
 * accepte les pas de tir des fusees ({@code launch_blocks}) : ailleurs sur la
 * carte, leur pose est refusee et le joueur apprend ou est la base la plus
 * proche.
 */
public final class EarthSites {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "earth/sites.json");

    public static final String IRRADIATED = "irradiated";
    public static final String LAUNCH = "launch";

    /**
     * Un lieu.
     *
     * @param radius     rayon du lieu, en blocs
     * @param coreRadius rayon du coeur d'une zone irradiee, en blocs
     * @param biome      le biome de la zone (irradiee) ; {@code core_biome} celui de son coeur
     */
    public record Site(String id, String name, String kind, double latitude, double longitude, int radius,
                       int coreRadius, Optional<Identifier> biome, Optional<Identifier> coreBiome) {
        static final Codec<Site> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Site::id),
                Codec.STRING.fieldOf("name").forGetter(Site::name),
                Codec.STRING.fieldOf("kind").forGetter(Site::kind),
                Codec.doubleRange(-90.0d, 90.0d).fieldOf("latitude").forGetter(Site::latitude),
                Codec.doubleRange(-180.0d, 180.0d).fieldOf("longitude").forGetter(Site::longitude),
                Codec.intRange(8, 512).fieldOf("radius").forGetter(Site::radius),
                Codec.intRange(0, 512).optionalFieldOf("core_radius", 0).forGetter(Site::coreRadius),
                Identifier.CODEC.optionalFieldOf("biome").forGetter(Site::biome),
                Identifier.CODEC.optionalFieldOf("core_biome").forGetter(Site::coreBiome)
        ).apply(instance, Site::new));

        public boolean irradiated() {
            return IRRADIATED.equals(kind);
        }

        public boolean launch() {
            return LAUNCH.equals(kind);
        }
    }

    /**
     * @param version      change-la pour que les chunks deja servis le soient a nouveau
     * @param launchBlocks les blocs qui ne se posent que sur une base de lancement
     */
    public record Config(int version, List<Site> sites, List<Identifier> launchBlocks) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 1_000_000).optionalFieldOf("version", 1).forGetter(Config::version),
                Site.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(Config::sites),
                Identifier.CODEC.listOf().optionalFieldOf("launch_blocks", List.of()).forGetter(Config::launchBlocks)
        ).apply(instance, Config::new));
    }

    /** Un lieu place sur la carte : son centre en blocs. */
    public record Placed(Site site, int x, int z) {
        public double distance(double px, double pz) {
            return Math.hypot(px - x, pz - z);
        }

        public boolean contains(double px, double pz) {
            return distance(px, pz) <= site.radius();
        }

        boolean inCore(double px, double pz) {
            return distance(px, pz) <= site.coreRadius();
        }
    }

    private static volatile Config config = new Config(1, List.of(), List.of());
    private static volatile List<Placed> placed = List.of();
    private static volatile EarthData placedFor;
    private static volatile Set<Block> launchBlocks = Set.of();
    private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();

    private EarthSites() {
    }

    public static boolean load(MinecraftServer server) {
        config = new Config(1, List.of(), List.of());
        placed = List.of();
        placedFor = null;
        launchBlocks = Set.of();
        QUEUE.clear();
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            return true;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Lieux de la Terre illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            List<Block> blocks = new ArrayList<>();
            for (Identifier id : config.launchBlocks()) {
                BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
            }
            launchBlocks = Set.copyOf(blocks);
            DoHy.LOGGER.info("Lieux de la Terre : {} lieux, {} pas de tir reconnus sur {}", config.sites().size(),
                    blocks.size(), config.launchBlocks().size());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des lieux de la Terre", error);
            return false;
        }
    }

    static void clear() {
        QUEUE.clear();
        placed = List.of();
        placedFor = null;
    }

    /** Les lieux places sur la carte de ce monde ; vide sans carte. */
    public static List<Placed> placed() {
        EarthData earth = EarthData.get();
        if (earth == null) {
            return List.of();
        }
        if (placedFor != earth) {
            List<Placed> list = new ArrayList<>();
            for (Site site : config.sites()) {
                list.add(new Placed(site, (int) Math.round(earth.x(site.longitude())),
                        (int) Math.round(earth.z(site.latitude()))));
            }
            placed = List.copyOf(list);
            placedFor = earth;
        }
        return placed;
    }

    /** Le lieu ou est ce point, ou {@code null}. */
    public static Placed at(double x, double z) {
        for (Placed site : placed()) {
            if (site.contains(x, z)) {
                return site;
            }
        }
        return null;
    }

    /** Le lieu de cet identifiant, ou {@code null}. */
    static Placed byId(String id) {
        for (Placed site : placed()) {
            if (site.site().id().equals(id)) {
                return site;
            }
        }
        return null;
    }

    /** Vrai si ce bloc ne se pose que sur une base de lancement. */
    public static boolean isLaunchBlock(Block block) {
        return launchBlocks.contains(block);
    }

    /** La base de lancement la plus proche de ce point, ou {@code null} s'il n'y en a aucune. */
    public static Placed nearestBase(double x, double z) {
        Placed best = null;
        for (Placed site : placed()) {
            if (site.site().launch() && (best == null || site.distance(x, z) < best.distance(x, z))) {
                best = site;
            }
        }
        return best;
    }

    /** « nord-est », du point vers le lieu ; le nord vers les z negatifs. */
    public static String direction(double fromX, double fromZ, Placed to) {
        double angle = Math.toDegrees(Math.atan2(to.x() - fromX, -(to.z() - fromZ)));
        String[] names = {"nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest"};
        return names[Math.floorMod((int) Math.round(angle / 45.0d), 8)];
    }

    // -- L'arrivee des chunks --------------------------------------------------------------

    /** Un chunk de la carte vient d'arriver : a servir s'il touche un lieu et ne l'a pas deja ete. */
    static void arrived(LevelChunk chunk) {
        if (config.sites().isEmpty() || chunk.getData(DoHyEarth.SITES.get()) >= config.version()) {
            return;
        }
        int centerX = chunk.getPos().getMinBlockX() + 8;
        int centerZ = chunk.getPos().getMinBlockZ() + 8;
        for (Placed site : placed()) {
            // un chunk touche un lieu si son centre en est a moins du rayon plus une demi-diagonale
            if (site.distance(centerX, centerZ) <= site.site().radius() + 12) {
                QUEUE.add((chunk.getPos().getMinBlockX() >> 4 & 0xFFFFFFFFL)
                        | ((long) (chunk.getPos().getMinBlockZ() >> 4) << 32));
                return;
            }
        }
    }

    static void tick(ServerLevel level) {
        List<ChunkAccess> resend = new ArrayList<>();
        for (int count = 0; count < 2 && !QUEUE.isEmpty(); count++) {
            long key = QUEUE.removeFirstLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow((int) key, (int) (key >> 32));
            if (chunk == null || chunk.getData(DoHyEarth.SITES.get()) >= config.version()) {
                continue;
            }
            if (serve(level, chunk)) {
                resend.add(chunk);
            }
            chunk.setData(DoHyEarth.SITES.get(), config.version());
        }
        if (!resend.isEmpty()) {
            level.getChunkSource().chunkMap.resendBiomesForChunks(resend);
        }
    }

    /** Sert un chunk ; vrai si ses biomes ont change. */
    static boolean serve(ServerLevel level, LevelChunk chunk) {
        boolean biomes = false;
        for (Placed site : placed()) {
            if (site.distance(chunk.getPos().getMinBlockX() + 8, chunk.getPos().getMinBlockZ() + 8)
                    > site.site().radius() + 12) {
                continue;
            }
            if (site.site().irradiated()) {
                biomes |= irradiate(level, chunk, site);
                if (chunk.getPos().contains(new BlockPos(site.x(), 0, site.z()))) {
                    reactor(level, chunk, site);
                }
            } else if (site.site().launch() && chunk.getPos().contains(new BlockPos(site.x(), 0, site.z()))) {
                apron(level, chunk, site);
            }
        }
        return biomes;
    }

    private static Optional<Holder.Reference<Biome>> biome(ServerLevel level, Optional<Identifier> id) {
        return id.flatMap(key -> level.registryAccess().lookupOrThrow(Registries.BIOME)
                .get(ResourceKey.create(Registries.BIOME, key)));
    }

    private static Optional<BlockState> ntm(String name) {
        if (!ModList.get().isLoaded("hbm")) {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("hbm", name))
                .map(Block::defaultBlockState);
    }

    /**
     * La zone : son biome sur les terres (la mer garde le sien), et au coeur
     * la terre, les arbres et les plantes morts de NTM NEXT.
     */
    private static boolean irradiate(ServerLevel level, LevelChunk chunk, Placed site) {
        Optional<Holder.Reference<Biome>> zone = biome(level, site.site().biome());
        Optional<Holder.Reference<Biome>> core = biome(level, site.site().coreBiome()).or(() -> zone);
        boolean changed = false;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        if (zone.isPresent()) {
            boolean[] touched = {false};
            chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
                Holder<Biome> current = chunk.getNoiseBiome(qx, qy, qz);
                int x = (qx << 2) + 2;
                int z = (qz << 2) + 2;
                if (!site.contains(x, z) || underWater(chunk, x - minX, z - minZ)) {
                    return current;
                }
                touched[0] = true;
                return site.inCore(x, z) ? core.get() : zone.get();
            }, level.getChunkSource().randomState().sampler());
            if (touched[0]) {
                chunk.markUnsaved();
                changed = true;
            }
        }
        Optional<BlockState> earth = ntm("waste_earth");
        Optional<BlockState> log = ntm("waste_log");
        Optional<BlockState> leaves = ntm("waste_leaves");
        Optional<BlockState> plant = ntm("plant_dead_grass");
        if (earth.isEmpty()) {
            return changed;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                if (!site.inCore(x, z)) {
                    continue;
                }
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
                int bottom = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, dx, dz) - 3;
                for (int y = top; y >= bottom; y--) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    BlockState dead = null;
                    if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
                        dead = earth.get();
                    } else if (state.is(BlockTags.LOGS)) {
                        dead = log.orElse(null);
                    } else if (state.is(BlockTags.LEAVES)) {
                        dead = leaves.orElse(Blocks.AIR.defaultBlockState());
                    } else if ((state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS))
                            && state.getFluidState().isEmpty() && !state.isAir()) {
                        // une plante morte sur la terre, rien sur le haut d'une herbe haute
                        boolean onSoil = level.getBlockState(pos.below()).is(BlockTags.DIRT);
                        dead = onSoil ? plant.orElse(Blocks.AIR.defaultBlockState()) : Blocks.AIR.defaultBlockState();
                    }
                    if (dead != null) {
                        level.setBlock(pos, dead, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return changed;
    }

    private static boolean underWater(LevelChunk chunk, int localX, int localZ) {
        int lx = Math.max(0, Math.min(15, localX));
        int lz = Math.max(0, Math.min(15, localZ));
        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
        BlockPos top = new BlockPos(chunk.getPos().getMinBlockX() + lx, surface - 1, chunk.getPos().getMinBlockZ() + lz);
        return chunk.getBlockState(top).getFluidState().is(FluidTags.WATER);
    }

    /**
     * Le reacteur : un tas de corium et de gravats de corium sur la surface,
     * dans un reste d'enceinte de beton, et son etiquette. Le corium est celui
     * de NTM NEXT : le tenir en main irradie. Sans NTM NEXT, rien.
     */
    private static void reactor(ServerLevel level, LevelChunk chunk, Placed site) {
        Optional<BlockState> corium = ntm("block_corium");
        Optional<BlockState> rubble = ntm("block_corium_cobble");
        if (corium.isEmpty() || rubble.isEmpty()) {
            return;
        }
        // au milieu du chunk du lieu : l'enceinte (13 blocs) y tient sans toucher aux voisins
        int centerX = chunk.getPos().getMinBlockX() + 8;
        int centerZ = chunk.getPos().getMinBlockZ() + 8;
        int ground = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, 8, 8) + 1;
        RandomSource random = RandomSource.create(level.getSeed() ^ site.site().id().hashCode());
        BlockState concrete = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                double distance = Math.hypot(dx, dz);
                // le socle, a plat
                level.setBlock(new BlockPos(x, ground - 1, z), distance < 5.5d ? concrete : cracked,
                        Block.UPDATE_CLIENTS);
                for (int y = ground; y < ground + 12; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                // l'enceinte eventree : un mur de 13 blocs de cote, haut de 2 a 7, perce
                boolean wall = Math.max(Math.abs(dx), Math.abs(dz)) == 6;
                if (wall) {
                    int height = 2 + random.nextInt(6);
                    if (random.nextInt(5) == 0) {
                        height = 0;
                    }
                    for (int y = 0; y < height; y++) {
                        level.setBlock(new BlockPos(x, ground + y, z), random.nextInt(3) == 0 ? cracked : concrete,
                                Block.UPDATE_CLIENTS);
                    }
                    continue;
                }
                // le tas : du corium au centre, des gravats autour
                int pile = (int) Math.round(3.2d - distance * 0.6d + random.nextDouble() * 0.8d);
                for (int y = 0; y < pile; y++) {
                    BlockState state = distance < 1.8d && y < 2 ? corium.get() : rubble.get();
                    level.setBlock(new BlockPos(x, ground + y, z), state, Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockPos label = new BlockPos(centerX, ground + 8, centerZ);
        level.setBlock(label, DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(label) instanceof HologramBlockEntity hologram) {
            hologram.set(site.site().name() + " - reacteur detruit", 0xE5484D, 1.4f);
        }
    }

    /**
     * L'aire de lancement : tout le chunk du centre de la base, a plat, en
     * beton, marque d'un cercle jaune, degage au-dessus ; et le nom de la base.
     */
    private static void apron(ServerLevel level, LevelChunk chunk, Placed site) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int[] heights = new int[256];
        for (int i = 0; i < 256; i++) {
            heights[i] = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, i % 16, i / 16);
        }
        java.util.Arrays.sort(heights);
        int ground = Math.max(level.getSeaLevel(), heights[128]);
        BlockState concrete = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState rim = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState mark = Blocks.YELLOW_CONCRETE.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                double distance = Math.hypot(dx - 7.5d, dz - 7.5d);
                boolean edge = dx == 0 || dz == 0 || dx == 15 || dz == 15;
                BlockState top = edge ? rim : distance > 5.0d && distance < 6.2d ? mark
                        : (dx == 7 || dx == 8) && (dz == 7 || dz == 8) ? light : concrete;
                for (int y = ground - 4; y < ground; y++) {
                    level.setBlock(new BlockPos(x, y, z), stone, Block.UPDATE_CLIENTS);
                }
                level.setBlock(new BlockPos(x, ground, z), top, Block.UPDATE_CLIENTS);
                for (int y = ground + 1; y <= ground + 24; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        BlockPos label = new BlockPos(minX + 8, ground + 4, minZ + 8);
        level.setBlock(label, DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(label) instanceof HologramBlockEntity hologram) {
            hologram.set(site.site().name() + " - base de lancement", 0xFAC400, 1.6f);
        }
    }
}
