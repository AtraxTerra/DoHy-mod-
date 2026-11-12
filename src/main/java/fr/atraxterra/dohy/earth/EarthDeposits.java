package fr.atraxterra.dohy.earth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Les gisements de la Terre : des minerais poses la ou le monde reel les
 * exploite — l'uranium au Kazakhstan, le petrole dans le Golfe, le diamant au
 * Botswana.
 *
 * <p><b>Pourquoi a l'arrivee du chunk et non a la generation.</b> Une carte de
 * la Terre est faite d'avance : ses chunks existent deja sur le disque, et ce
 * que le jeu leur ajoute encore en les chargeant depend de l'etat ou l'outil
 * de dessin les a laisses. Les gisements ne s'en remettent donc pas a la
 * generation : chaque chunk qui arrive est examine, et ceux qu'un gisement
 * concerne recoivent leurs filons quelques ticks plus tard, une seule fois —
 * le chunk en garde la marque. Cela vaut aussi pour un chunk deja visite avant
 * que le datapack soit pose.
 *
 * <p>Le tirage est fixe par la graine du monde et la place du chunk : un chunk
 * qu'aucun filon n'atteint n'est ni modifie ni marque, et retirer le meme
 * tirage a chaque chargement lui redonne la meme reponse.
 *
 * <p>La table est dans {@code data/dohy/earth/deposits.json}. Un minerai que
 * le jeu ne connait pas (le mod qui l'apporte n'est pas la) est ignore.
 */
public final class EarthDeposits {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "earth/deposits.json");

    /** Un rectangle du globe, en degres. */
    public record Box(double south, double north, double west, double east) {
        static final Codec<Box> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("south").forGetter(Box::south),
                Codec.DOUBLE.fieldOf("north").forGetter(Box::north),
                Codec.DOUBLE.fieldOf("west").forGetter(Box::west),
                Codec.DOUBLE.fieldOf("east").forGetter(Box::east)
        ).apply(instance, Box::new));

        boolean contains(double latitude, double longitude) {
            return latitude >= south && latitude <= north && longitude >= west && longitude <= east;
        }
    }

    /**
     * Un gisement.
     *
     * @param ore       le minerai, dans la pierre
     * @param deepslate sa forme dans l'ardoise des abimes ; sans elle, le meme bloc
     * @param countries les pays ou il se trouve, par leur code a trois lettres
     * @param boxes     et les rectangles du globe ou il se trouve aussi (une region d'un grand pays)
     * @param veins     filons par chunk, en moyenne
     * @param size      blocs d'un filon ; ignore si {@code radius} est pose
     * @param radius    au-dessus de zero : une poche ronde de ce rayon, au lieu d'un filon
     */
    public record Deposit(String id, Identifier ore, Optional<Identifier> deepslate, List<String> countries,
                          List<Box> boxes, int minY, int maxY, double veins, int size, int radius) {
        static final Codec<Deposit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Deposit::id),
                Identifier.CODEC.fieldOf("ore").forGetter(Deposit::ore),
                Identifier.CODEC.optionalFieldOf("deepslate").forGetter(Deposit::deepslate),
                Codec.STRING.listOf().optionalFieldOf("countries", List.of()).forGetter(Deposit::countries),
                Box.CODEC.listOf().optionalFieldOf("boxes", List.of()).forGetter(Deposit::boxes),
                Codec.INT.optionalFieldOf("min_y", -56).forGetter(Deposit::minY),
                Codec.INT.optionalFieldOf("max_y", 32).forGetter(Deposit::maxY),
                Codec.doubleRange(0.0d, 64.0d).optionalFieldOf("veins", 1.0d).forGetter(Deposit::veins),
                Codec.intRange(1, 64).optionalFieldOf("size", 6).forGetter(Deposit::size),
                Codec.intRange(0, 7).optionalFieldOf("radius", 0).forGetter(Deposit::radius)
        ).apply(instance, Deposit::new));
    }

    /**
     * @param version change-la pour que les chunks deja servis le soient a nouveau
     * @param perTick chunks traites par tick
     */
    public record Config(int version, int perTick, List<Deposit> deposits) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 1_000_000).optionalFieldOf("version", 1).forGetter(Config::version),
                Codec.intRange(1, 256).optionalFieldOf("per_tick", 12).forGetter(Config::perTick),
                Deposit.CODEC.listOf().optionalFieldOf("deposits", List.of()).forGetter(Config::deposits)
        ).apply(instance, Config::new));
    }

    /** Un gisement pret a servir : ses blocs trouves, ses pays en ensemble. */
    private record Ready(Deposit deposit, BlockState ore, BlockState deepslate, Set<String> countries, long salt) {
    }

    private static volatile int version = 1;
    private static volatile int perTick = 12;
    private static volatile List<Ready> ready = List.of();
    private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();

    private EarthDeposits() {
    }

    public static boolean load(MinecraftServer server) {
        ready = List.of();
        QUEUE.clear();
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            return true;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Gisements de la Terre illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            Config config = result.getOrThrow();
            List<Ready> list = new ArrayList<>();
            int skipped = 0;
            for (Deposit deposit : config.deposits()) {
                Block ore = BuiltInRegistries.BLOCK.getOptional(deposit.ore()).orElse(null);
                if (ore == null) {
                    skipped++;
                    continue;
                }
                Block deep = deposit.deepslate().flatMap(BuiltInRegistries.BLOCK::getOptional).orElse(ore);
                list.add(new Ready(deposit, ore.defaultBlockState(), deep.defaultBlockState(),
                        new HashSet<>(deposit.countries()), deposit.id().hashCode() * 0x9E3779B97F4A7C15L));
            }
            version = config.version();
            perTick = config.perTick();
            ready = List.copyOf(list);
            DoHy.LOGGER.info("Gisements de la Terre : {} en service, {} ignores (minerai absent du jeu)",
                    list.size(), skipped);
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des gisements de la Terre", error);
            return false;
        }
    }

    static void clear() {
        QUEUE.clear();
    }

    /** Un chunk de la carte vient d'arriver : a examiner s'il n'a pas deja ete servi. */
    static void arrived(LevelChunk chunk) {
        if (ready.isEmpty() || chunk.getData(DoHyEarth.DEPOSITS.get()) >= version) {
            return;
        }
        QUEUE.add(chunk.getPos().toLong());
    }

    /** Sert quelques chunks en attente. */
    static void tick(ServerLevel level, EarthData earth) {
        for (int count = 0; count < perTick && !QUEUE.isEmpty(); count++) {
            long key = QUEUE.removeFirstLong();
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null || chunk.getData(DoHyEarth.DEPOSITS.get()) >= version) {
                continue;
            }
            if (serve(level, earth, chunk) > 0) {
                chunk.setData(DoHyEarth.DEPOSITS.get(), version);
            }
        }
    }

    /** Pose les filons d'un chunk ; rend le nombre de filons tires (poses ou non : la roche peut manquer). */
    static int serve(ServerLevel level, EarthData earth, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int centerX = pos.getMinBlockX() + 8;
        int centerZ = pos.getMinBlockZ() + 8;
        EarthData.Country country = earth.countryAt(centerX, centerZ);
        double latitude = earth.latitude(centerZ);
        double longitude = earth.longitude(centerX);
        int drawn = 0;
        for (Ready rule : ready) {
            if (!applies(rule, country, latitude, longitude)) {
                continue;
            }
            RandomSource random = RandomSource.create(level.getSeed() ^ pos.toLong() * 0x5DEECE66DL ^ rule.salt());
            double veins = rule.deposit().veins();
            int count = (int) veins + (random.nextDouble() < veins - (int) veins ? 1 : 0);
            for (int vein = 0; vein < count; vein++) {
                place(level, pos, rule, random);
                drawn++;
            }
        }
        return drawn;
    }

    private static boolean applies(Ready rule, EarthData.Country country, double latitude, double longitude) {
        if (country != null && rule.countries().contains(country.code())) {
            return true;
        }
        for (Box box : rule.deposit().boxes()) {
            if (box.contains(latitude, longitude)) {
                return true;
            }
        }
        return false;
    }

    private static void place(ServerLevel level, ChunkPos chunk, Ready rule, RandomSource random) {
        Deposit deposit = rule.deposit();
        int margin = Math.max(1, deposit.radius());
        int x = chunk.getMinBlockX() + margin + random.nextInt(16 - 2 * margin);
        int z = chunk.getMinBlockZ() + margin + random.nextInt(16 - 2 * margin);
        int floor = Math.max(deposit.minY(), level.getMinY() + 1);
        int roof = Math.min(deposit.maxY(), level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 4);
        if (roof < floor) {
            return;
        }
        int y = floor + random.nextInt(roof - floor + 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (deposit.radius() > 0) {
            int radius = deposit.radius();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= radius * radius + 1) {
                            swap(level, pos.set(x + dx, y + dy, z + dz), rule);
                        }
                    }
                }
            }
            return;
        }
        // un filon : une marche au hasard qui reste dans son chunk
        for (int block = 0; block < deposit.size(); block++) {
            swap(level, pos.set(x, y, z), rule);
            x = Math.max(chunk.getMinBlockX(), Math.min(chunk.getMaxBlockX(), x + random.nextInt(3) - 1));
            y = Math.max(floor, Math.min(roof, y + random.nextInt(3) - 1));
            z = Math.max(chunk.getMinBlockZ(), Math.min(chunk.getMaxBlockZ(), z + random.nextInt(3) - 1));
        }
    }

    /** Ne remplace que la roche : un filon ne flotte pas dans une grotte et ne perce pas le sol. */
    private static void swap(ServerLevel level, BlockPos pos, Ready rule) {
        if (pos.getY() <= level.getMinY() || pos.getY() >= level.getMaxY()) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.STONE_ORE_REPLACEABLES)) {
            level.setBlock(pos, rule.ore(), Block.UPDATE_CLIENTS);
        } else if (state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
            level.setBlock(pos, rule.deepslate(), Block.UPDATE_CLIENTS);
        }
    }
}
