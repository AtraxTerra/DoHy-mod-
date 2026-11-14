package fr.atraxterra.dohy.earth.disaster;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Une eruption : le volcan se reveille (fumee, secousses, cinq minutes
 * d'alerte), entre en eruption douze minutes — bombes de lave, coulees qui
 * descendent les pentes, panache de cendres, eclairs dans le panache — puis la
 * lave refroidit.
 *
 * <p>La lave refroidie devient du basalte ou affleurent du soufre, de la
 * fluorine, de l'amiante, de la molybdenite et des gemmes volcaniques (les
 * minerais de basalte de NTM NEXT ; sans lui, du soufre, de la blackstone doree
 * et de l'obsidienne pleureuse) ; une bombe sur sept retombe en minerai
 * volcanique. La lave ne met le feu a rien. Tout revient avec le terrain.
 */
final class Eruption extends Disaster {

    private static final int LENGTH = 12 * 60 * 20;
    private static final int FUMES = 3 * 60 * 20;
    private static final double AREA = 56.0d;

    final DisasterConfig.PlacedVolcano volcano;
    private final int warning;
    private boolean physical;
    private boolean prepared;
    private int top;
    private final List<int[]> heads = new ArrayList<>();
    final List<BlockPos> cooled = new ArrayList<>();
    private int bombs;
    private int lava;
    private int ores;
    private int cooling = -1;
    /** Le passage du refroidissement (0, 1, 2), et les blocs figes pendant celui-ci. */
    private int pass;
    private int converted;
    private int boom;

    Eruption(int id, DisasterConfig.PlacedVolcano volcano, String country, int warningTicks, RandomSource random) {
        super(id, DisasterKind.ERUPTION, "Eruption " + de(volcano.volcano().name()), volcano.volcano().name()
                + (country.isEmpty() ? "" : " (" + country + ")"), volcano.x(), volcano.z(), random);
        this.volcano = volcano;
        // au moins un tick : l'entree en eruption se fait toujours dans un tick a elle
        this.warning = Math.max(1, warningTicks);
    }

    /** « de l'Etna », « du Vesuve », « du Mont Fuji ». */
    static String de(String name) {
        if (name.startsWith("Mont ") || name.startsWith("Piton")) {
            return "du " + name;
        }
        char first = Character.toLowerCase(name.charAt(0));
        if ("aeiouhy".indexOf(first) >= 0) {
            return "de l'" + name;
        }
        return "du " + name;
    }

    /** « L'Etna », « Le Vesuve ». */
    String subject() {
        String name = volcano.volcano().name();
        if (name.startsWith("Mont ") || name.startsWith("Piton") || name.startsWith("Montagne")) {
            return name.startsWith("Montagne") ? "La " + name : "Le " + name;
        }
        return "aeiouhyAEIOUHY".indexOf(name.charAt(0)) >= 0 ? "L'" + name : "Le " + name;
    }

    private boolean warning() {
        return ticks < warning;
    }

    private boolean erupting() {
        return ticks >= warning && ticks < warning + LENGTH;
    }

    @Override
    double radius() {
        return erupting() ? 180.0d : 96.0d;
    }

    @Override
    DisasterNet.Entry entry() {
        int phase = warning() ? 0 : erupting() ? 1 : 2;
        int seconds = warning() ? (warning - ticks) / 20 : Math.max(0, (warning + LENGTH + FUMES - ticks) / 20);
        return new DisasterNet.Entry(id, DisasterNet.ERUPTION, name, place, volcano.x(), volcano.z(),
                (int) radius(), 0, 0, phase, seconds, new int[0]);
    }

    @Override
    float shake(ServerPlayer player) {
        double distance = distance(player);
        if (warning()) {
            return distance < 80.0d ? (float) (0.12d * (1.0d - distance / 80.0d)) : 0.0f;
        }
        if (erupting()) {
            double base = distance < 96.0d ? 0.16d * (1.0d - distance / 96.0d) : 0.0d;
            double burst = boom > 0 && distance < 160.0d ? 0.35d * (1.0d - distance / 160.0d) : 0.0d;
            return (float) Math.max(base, burst);
        }
        return 0.0f;
    }

    @Override
    float ash(ServerPlayer player) {
        double distance = distance(player);
        if (erupting()) {
            double ramp = Math.min(1.0d, (ticks - warning) / 400.0d);
            return (float) (ramp * 0.9d * Math.max(0.0d, 1.0d - distance / 180.0d));
        }
        if (!warning()) {
            return (float) (0.3d * Math.max(0.0d, 1.0d - distance / 100.0d));
        }
        return 0.0f;
    }

    void begin(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            double distance = distance(player);
            String where = " (" + Disasters.blocks((int) distance) + " blocs "
                    + Disasters.direction(player.getX(), player.getZ(), x, z) + ")";
            if (warning > 20) {
                tell(player, subject() + " se reveille : eruption dans " + Disasters.duration(warning / 20) + "."
                        + where, distance < 600.0d ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            }
        }
    }

    @Override
    void tick(ServerLevel level) {
        ticks++;
        if (boom > 0) {
            boom--;
        }
        if (ticks == warning - 1200 && warning > 1200) {
            for (ServerPlayer player : near(level, x, z, 600.0d)) {
                tell(player, subject() + " entre en eruption dans une minute !", ChatFormatting.RED);
            }
        }
        if (ticks == warning) {
            for (ServerPlayer player : level.players()) {
                double distance = distance(player);
                if (distance <= 200.0d) {
                    title(player, "Eruption !", ChatFormatting.RED, volcano.volcano().name()
                            + " - eloigne-toi du cratere");
                }
                tell(player, subject() + " entre en eruption ! (" + Disasters.blocks((int) distance) + " blocs "
                        + Disasters.direction(player.getX(), player.getZ(), x, z) + ")",
                        distance < 600.0d ? ChatFormatting.RED : ChatFormatting.GRAY);
                if (distance < 400.0d) {
                    sound(player, SoundEvents.GEYSER_ERUPTION_START, x, top + 4, z, 4.0f, 0.5f);
                }
            }
        }
        smoke(level);
        if (warning()) {
            return;
        }
        if (erupting()) {
            if (!physical && ticks % 20 == 0) {
                physical = canStart(level);
            }
            if (physical && !prepared) {
                prepared = DisasterTerrain.prepare(level, ledger(), x, z, AREA + 16.0d);
                if (prepared) {
                    crater(level);
                }
            }
            if (physical && prepared) {
                erupt(level);
            }
            sounds(level);
            return;
        }
        // la lave refroidit, puis les fumerolles
        if (physical && prepared && cooling < Integer.MAX_VALUE) {
            cool(level);
        }
        if (ticks >= warning + LENGTH + FUMES) {
            finishWith(level);
        }
    }

    /** L'eruption ne touche au terrain que si le cone est la et qu'un joueur est a moins de 192 blocs. */
    private boolean canStart(ServerLevel level) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(volcano.x() >> 4, volcano.z() >> 4);
        return chunk != null && Volcanoes.served(chunk) && !near(level, x, z, 192.0d).isEmpty();
    }

    /** Le haut du lac de lave, et les points du bord d'ou partent les coulees. */
    private void crater(ServerLevel level) {
        top = level.getHeight(Heightmap.Types.WORLD_SURFACE, volcano.x(), volcano.z()) - 1;
        double[] low = new double[16];
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0d;
            low[i] = ground(level, (int) Math.floor(x + Math.cos(a) * 8.0d), (int) Math.floor(z + Math.sin(a) * 8.0d));
        }
        int flows = 2 + random.nextInt(2);
        boolean[] used = new boolean[16];
        for (int f = 0; f < flows; f++) {
            int best = -1;
            for (int i = 0; i < 16; i++) {
                boolean clear = true;
                for (int j = 0; j < 16; j++) {
                    if (used[j] && Math.min(Math.abs(i - j), 16 - Math.abs(i - j)) < 4) {
                        clear = false;
                    }
                }
                if (clear && (best < 0 || low[i] < low[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            used[best] = true;
            double a = best * Math.PI / 8.0d;
            heads.add(new int[] {(int) Math.floor(x + Math.cos(a) * 6.0d), (int) Math.floor(z + Math.sin(a) * 6.0d),
                    0});
        }
        DoHy.LOGGER.info("Eruption {} : cratere a y={}, {} coulees", volcano.volcano().name(), top, heads.size());
    }

    private void erupt(ServerLevel level) {
        int t = ticks - warning;
        // les bombes
        if (t % 8 == 0) {
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                bomb(level);
            }
        }
        // les coulees
        if (t % 16 == 0) {
            for (int[] head : heads) {
                flow(level, head);
            }
        }
        // les eclairs dans le panache
        if (random.nextInt(140) == 0) {
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(x + random.nextInt(13) - 6, top + 12 + random.nextInt(8), z + random.nextInt(13) - 6,
                        0.0f, 0.0f);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
    }

    /** Une bombe de lave : un bloc lance du cratere, qui retombe dans un rayon de 8 a 38 blocs. */
    private void bomb(ServerLevel level) {
        BlockPos from = new BlockPos(volcano.x() + random.nextInt(3) - 1, top + 2, volcano.z() + random.nextInt(3) - 1);
        if (!level.getBlockState(from).isAir()) {
            return;
        }
        int roll = random.nextInt(100);
        BlockState state = roll < 70 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                : roll < 85 ? hbm("ore_volcano", Blocks.MAGMA_BLOCK.defaultBlockState())
                : hbm("basalt", Blocks.BLACKSTONE.defaultBlockState());
        level.setBlock(from, state, Block.UPDATE_CLIENTS);
        FallingBlockEntity falling = FallingBlockEntity.fall(level, from, state);
        falling.dropItem = false;
        falling.setHurtsEntities(1.5f, 12);
        double a = random.nextDouble() * Math.PI * 2.0d;
        double h = 0.2d + random.nextDouble() * 0.7d;
        falling.setDeltaMovement(Math.cos(a) * h, 0.9d + random.nextDouble() * 0.6d, Math.sin(a) * h);
        bombs++;
        level.sendParticles(ParticleTypes.LAVA, from.getX() + 0.5d, from.getY() + 0.5d, from.getZ() + 0.5d, 6, 0.6d,
                0.4d, 0.6d, 0.1d);
    }

    /** Une coulee avance d'un bloc vers le plus bas de ses voisins, en s'eloignant du cratere. */
    private void flow(ServerLevel level, int[] head) {
        if (head[2] >= 48) {
            return;
        }
        int bestX = 0;
        int bestZ = 0;
        int bestY = Integer.MAX_VALUE;
        double fromCenter = Math.hypot(head[0] - x, head[1] - z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = head[0] + dx;
                int nz = head[1] + dz;
                if (Math.hypot(nx - x, nz - z) < fromCenter - 0.2d) {
                    continue;
                }
                int y = ground(level, nx, nz);
                if (level.getFluidState(new BlockPos(nx, y, nz)).is(FluidTags.LAVA)) {
                    continue;
                }
                int score = y * 4 + random.nextInt(3);
                if (score < bestY) {
                    bestY = score;
                    bestX = nx;
                    bestZ = nz;
                }
            }
        }
        if (bestY == Integer.MAX_VALUE || !DisasterTerrain.surrounded(ledger, bestX, bestZ)
                || Math.hypot(bestX - x, bestZ - z) > AREA - 6.0d) {
            head[2] = 48;
            return;
        }
        int y = ground(level, bestX, bestZ);
        BlockPos pos = new BlockPos(bestX, y, bestZ);
        FluidState fluid = level.getFluidState(pos.above());
        if (fluid.is(FluidTags.WATER) || level.getFluidState(pos).is(FluidTags.WATER)) {
            // la lave touche l'eau : elle fige, et la coulee s'arrete
            BlockPos water = level.getFluidState(pos.above()).is(FluidTags.WATER) ? pos.above() : pos;
            DisasterTerrain.set(level, ledger, water, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.sendParticles(ParticleTypes.CLOUD, bestX + 0.5d, y + 1.5d, bestZ + 0.5d, 20, 0.6d, 0.6d, 0.6d,
                    0.05d);
            for (ServerPlayer player : near(level, bestX, bestZ, 48.0d)) {
                sound(player, SoundEvents.FIRE_EXTINGUISH, bestX, y, bestZ, 1.5f, 0.6f);
            }
            head[2] = 48;
            return;
        }
        BlockPos above = pos.above();
        BlockState plant = level.getBlockState(above);
        if (!plant.isAir() && soft(plant)) {
            DisasterTerrain.set(level, ledger, above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        if (DisasterTerrain.set(level, ledger, pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL)) {
            lava++;
        }
        head[0] = bestX;
        head[1] = bestZ;
        head[2]++;
    }

    /** Le panache : fumee visible de loin pendant l'alerte, cendres et etincelles pendant l'eruption. */
    private void smoke(ServerLevel level) {
        if (ticks % 10 != 0) {
            return;
        }
        boolean erupting = erupting();
        boolean fumes = !warning() && !erupting;
        int smokeTop = top > 0 ? top : level.getChunkSource().getChunkNow(volcano.x() >> 4, volcano.z() >> 4) == null
                ? level.getSeaLevel() + 30 : level.getHeight(Heightmap.Types.WORLD_SURFACE, volcano.x(), volcano.z());
        for (ServerPlayer player : near(level, x, z, erupting ? 400.0d : 260.0d)) {
            if (fumes) {
                if (!cooled.isEmpty() && distance(player) < 128.0d) {
                    for (int i = 0; i < 3; i++) {
                        BlockPos pos = cooled.get(random.nextInt(cooled.size()));
                        particles(level, player, ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5d,
                                pos.getY() + 1.2d, pos.getZ() + 0.5d, 1, 0.2d, 0.1d, 0.02d);
                    }
                }
                particles(level, player, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, smokeTop + 2, z, 2, 1.5d, 1.0d, 0.03d);
                continue;
            }
            particles(level, player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, smokeTop + 3, z, erupting ? 10 : 3,
                    erupting ? 2.5d : 1.2d, erupting ? 4.0d : 1.5d, erupting ? 0.12d : 0.05d);
            if (erupting) {
                particles(level, player, ParticleTypes.LARGE_SMOKE, x, smokeTop + 8, z, 8, 4.0d, 6.0d, 0.08d);
                if (distance(player) < 160.0d) {
                    particles(level, player, ParticleTypes.LAVA, x, smokeTop + 1, z, 6, 2.0d, 1.0d, 0.2d);
                }
                float ash = ash(player);
                if (ash > 0.05f) {
                    particles(level, player, ParticleTypes.ASH, player.getX(), player.getY() + 6.0d, player.getZ(),
                            (int) (40 * ash), 8.0d, 4.0d, 0.0d);
                    particles(level, player, ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 5.0d,
                            player.getZ(), (int) (20 * ash), 8.0d, 4.0d, 0.0d);
                }
            }
        }
    }

    private void sounds(ServerLevel level) {
        if (random.nextInt(50) == 0) {
            boom = 12;
            for (ServerPlayer player : near(level, x, z, 320.0d)) {
                sound(player, SoundEvents.GENERIC_EXPLODE.value(), x, top + 4, z,
                        (float) Math.max(0.3d, 3.0d * (1.0d - distance(player) / 320.0d)), 0.45f);
            }
        }
        if (ticks % 200 == 0) {
            for (ServerPlayer player : near(level, x, z, 260.0d)) {
                sound(player, SoundEvents.GEYSER_ERUPTION_ACTIVE, x, top + 2, z, 2.5f, 0.55f);
            }
        }
        if (ticks % 30 == 0) {
            for (ServerPlayer player : near(level, x, z, 64.0d)) {
                sound(player, SoundEvents.LAVA_POP, x, top, z, 1.5f, 0.8f);
            }
        }
    }

    /**
     * La lave refroidit, quelques chunks par tick : en roche volcanique et en
     * minerais. Pendant le premier passage, la lave des chunks pas encore vus
     * coule encore un peu dans ceux deja figes ; un second passage (un troisieme
     * au besoin) enleve ces langues de lave restees sans source.
     */
    private void cool(ServerLevel level) {
        if (cooling < 0) {
            cooling = 0;
        }
        List<DisasterTerrain.Capture> chunks = new ArrayList<>(ledger.chunks.values());
        long deadline = System.nanoTime() + 8_000_000L;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (cooling < chunks.size() && System.nanoTime() < deadline) {
            DisasterTerrain.Capture capture = chunks.get(cooling++);
            LevelChunk chunk = level.getChunkSource().getChunkNow(capture.chunkX, capture.chunkZ);
            if (chunk == null) {
                continue;
            }
            int x0 = capture.chunkX << 4;
            int z0 = capture.chunkZ << 4;
            for (int y = capture.minY; y < capture.minY + capture.height; y++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        pos.set(x0 + lx, y, z0 + lz);
                        FluidState fluid = chunk.getFluidState(pos);
                        if (!fluid.is(FluidTags.LAVA) || Math.hypot(pos.getX() - x, pos.getZ() - z) > AREA + 16.0d) {
                            continue;
                        }
                        // le lac du cratere reste : c'est le volcan
                        if (Math.hypot(pos.getX() - x, pos.getZ() - z) < 4.5d && y <= top) {
                            continue;
                        }
                        // une lave que l'eruption n'a pas versee (une poche du sous-sol) reste aussi
                        BlockState before = DisasterTerrain.original(ledger, pos);
                        if (before != null && before.getFluidState().is(FluidTags.LAVA)) {
                            continue;
                        }
                        BlockState rock;
                        if (fluid.isSource()) {
                            rock = rock();
                        } else if (pass == 0 && fluid.getAmount() >= 5) {
                            rock = hbm("basalt", Blocks.BASALT.defaultBlockState());
                        } else {
                            // une langue de lave sans source : elle disparait
                            rock = Blocks.AIR.defaultBlockState();
                        }
                        level.setBlock(pos, rock, Block.UPDATE_CLIENTS);
                        converted++;
                        if (!rock.isAir() && cooled.size() < 96 && random.nextInt(4) == 0) {
                            cooled.add(pos.immutable());
                        }
                    }
                }
            }
        }
        if (cooling >= chunks.size() && converted > 0 && pass < 2) {
            // un autre passage, pour la lave qui a coule pendant celui-ci
            DoHy.LOGGER.debug("Eruption {} : passage {} du refroidissement, {} blocs figes", volcano.volcano().name(),
                    pass, converted);
            pass++;
            converted = 0;
            cooling = 0;
            return;
        }
        if (cooling >= chunks.size()) {
            cooling = Integer.MAX_VALUE;
            DoHy.LOGGER.info("Eruption {} refroidie : {} bombes, {} blocs de coulee, {} minerais", volcano.volcano()
                    .name(), bombs, lava, ores);
            for (ServerPlayer player : near(level, x, z, 260.0d)) {
                tell(player, "La lave " + de(volcano.volcano().name()) + " refroidit : le basalte garde du soufre, "
                        + "de la fluorine et des gemmes volcaniques. Le terrain reviendra dans " + Disasters.hours()
                        + ".", ChatFormatting.GOLD);
            }
        }
    }

    /** La roche d'une lave figee : surtout du basalte, du magma, de l'obsidienne, et des minerais. */
    private BlockState rock() {
        int roll = random.nextInt(100);
        if (roll < 64) {
            return hbm("basalt", Blocks.BASALT.defaultBlockState());
        }
        if (roll < 74) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        if (roll < 82) {
            return Blocks.OBSIDIAN.defaultBlockState();
        }
        ores++;
        if (ModList.get().isLoaded("hbm")) {
            int ore = random.nextInt(100);
            String id = ore < 35 ? "ore_basalt_sulfur" : ore < 55 ? "ore_basalt_fluorite" : ore < 70
                    ? "ore_basalt_asbestos" : ore < 85 ? "ore_basalt_molysite" : "ore_basalt_gem";
            return hbm(id, Blocks.BASALT.defaultBlockState());
        }
        int ore = random.nextInt(100);
        return ore < 45 ? vanilla("sulfur", Blocks.BASALT.defaultBlockState())
                : ore < 80 ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                : Blocks.CRYING_OBSIDIAN.defaultBlockState();
    }

    private void finishWith(ServerLevel level) {
        if (physical) {
            DoHy.LOGGER.info("Eruption {} finie : {} blocs changes", volcano.volcano().name(),
                    ledger == null ? 0 : ledger.changed);
        }
        finish(level.getServer());
    }

    static BlockState hbm(String name, BlockState fallback) {
        if (!ModList.get().isLoaded("hbm")) {
            return fallback;
        }
        return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("hbm", name))
                .map(Block::defaultBlockState).orElse(fallback);
    }

    static BlockState vanilla(String name, BlockState fallback) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(name))
                .map(Block::defaultBlockState).orElse(fallback);
    }
}
