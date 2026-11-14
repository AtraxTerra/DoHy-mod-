package fr.atraxterra.dohy.earth.disaster;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Un ouragan (un typhon, un cyclone selon le bassin) : il nait en mer, suit la
 * route de son bassin a un bloc par seconde, remonte vers le pole passee sa
 * latitude de recourbure et faiblit sur les terres.
 *
 * <p>Sa structure : un oeil calme et clair, un mur ou le vent et l'orage sont
 * au plus fort, des bandes de pluie jusqu'a 105 blocs (categorie 1) ou 165
 * (categorie 5). Il tourne dans le sens inverse des aiguilles d'une montre au
 * nord, dans l'autre sens au sud ; son vent pousse ce qui est dehors, et rien
 * de ce qui est a l'abri. Autour des joueurs, il arrache des feuilles et, des
 * la categorie 3, des arbres entiers ; sur la cote il fait monter la mer d'un
 * a trois blocs, et quand il est passe, une epave et du bois flotte
 * s'echouent sur la plage.
 */
final class Hurricane extends Disaster {

    /** Un bloc par seconde. */
    static final double SPEED = 1.0d / 20.0d;
    private static final double STEP = 20.0d;

    final DisasterConfig.Basin basin;
    int category;
    private final double[] trackX;
    private final double[] trackZ;
    private final double length;
    private double travelled;
    private double landTravel;
    private final boolean southern;
    private final Set<UUID> inside = new HashSet<>();
    private final Set<UUID> inEye = new HashSet<>();
    private final Map<UUID, Double> closest = new HashMap<>();
    private final Set<UUID> wrecked = new HashSet<>();
    private final List<BlockPos> surge = new ArrayList<>();
    /** La maree au plus, en blocs d'eau. */
    private static final int SURGE = 12000;
    /** La derniere epave echouee, ou {@code null}. */
    BlockPos wreckAt;
    private int stripped;
    private int uprooted;
    private int flooded;
    private int wrecks;

    private Hurricane(int id, DisasterConfig.Basin basin, String stormName, int category, double[] trackX,
                      double[] trackZ, RandomSource random) {
        super(id, DisasterKind.HURRICANE, word(basin) + " " + stormName, basin.name(), trackX[0], trackZ[0], random);
        this.basin = basin;
        this.category = category;
        this.trackX = trackX;
        this.trackZ = trackZ;
        this.southern = basin.southern();
        double total = 0.0d;
        for (int i = 0; i + 1 < trackX.length; i++) {
            total += Math.hypot(trackX[i + 1] - trackX[i], trackZ[i + 1] - trackZ[i]);
        }
        this.length = total;
    }

    static String word(DisasterConfig.Basin basin) {
        String word = basin.word();
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    /**
     * Une tempete qui nait dans le bassin et suit sa route. Avec un joueur vise, sa route passe sur lui
     * (a quelques blocs pres) {@code passSeconds} apres sa naissance.
     */
    static Hurricane create(int id, EarthData earth, DisasterConfig.Basin basin, String stormName, int category,
                            ServerPlayer target, int passSeconds, double lifeBlocks, boolean exact,
                            RandomSource random) {
        DisasterConfig.Genesis genesis = basin.genesis().get(random.nextInt(basin.genesis().size()));
        double heading = genesis.heading() + random.nextDouble() * 20.0d - 10.0d;
        double startX;
        double startZ;
        if (target != null) {
            // la route passe sur le joueur : on remonte le temps depuis lui, au cap qu'aurait la tempete la-bas
            double latitude = earth.latitude(target.getZ());
            boolean beyond = basin.southern() ? latitude <= basin.recurveLatitude()
                    : latitude >= basin.recurveLatitude();
            double local = beyond ? (basin.southern() ? 145.0d : 35.0d) : heading;
            double back = passSeconds * 20.0d * SPEED;
            // a quelques blocs du joueur, ou droit sur lui (commande d'essai)
            double offset = exact ? 0.0d : random.nextDouble() * 24.0d - 12.0d;
            double rad = Math.toRadians(local);
            startX = target.getX() - Math.sin(rad) * back + Math.cos(rad) * offset;
            startZ = target.getZ() + Math.cos(rad) * back + Math.sin(rad) * offset;
            heading = local;
        } else {
            double latitude = genesis.south() + random.nextDouble() * (genesis.north() - genesis.south());
            double longitude = genesis.west() + random.nextDouble() * (genesis.east() - genesis.west());
            startX = earth.x(longitude);
            startZ = earth.z(latitude);
        }
        List<double[]> points = new ArrayList<>();
        double px = startX;
        double pz = startZ;
        points.add(new double[] {px, pz});
        int steps = (int) Math.ceil(lifeBlocks / STEP);
        for (int i = 0; i < steps; i++) {
            double latitude = earth.latitude(pz);
            if (target == null || i * STEP > passSeconds * 20.0d * SPEED) {
                if (!basin.southern() && latitude >= basin.recurveLatitude()) {
                    // au nord, la tempete tourne a droite vers le nord-est
                    double wanted = 40.0d;
                    double diff = ((wanted - heading) % 360.0d + 540.0d) % 360.0d - 180.0d;
                    heading += Math.max(-4.0d, Math.min(4.0d, diff));
                } else if (basin.southern() && latitude <= basin.recurveLatitude()) {
                    double wanted = 140.0d;
                    double diff = ((wanted - heading) % 360.0d + 540.0d) % 360.0d - 180.0d;
                    heading += Math.max(-4.0d, Math.min(4.0d, diff));
                }
                heading += random.nextDouble() * 3.0d - 1.5d;
            }
            double rad = Math.toRadians(heading);
            px += Math.sin(rad) * STEP;
            pz -= Math.cos(rad) * STEP;
            points.add(new double[] {px, pz});
        }
        double[] xs = new double[points.size()];
        double[] zs = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i)[0];
            zs[i] = points.get(i)[1];
        }
        return new Hurricane(id, basin, stormName, category, xs, zs, random);
    }

    // -- La structure --------------------------------------------------------------------------

    double eye() {
        return 8.0d + category;
    }

    double wall() {
        return eye() + 22.0d;
    }

    @Override
    double radius() {
        return 90.0d + 15.0d * category;
    }

    /** La force du vent a cette distance du centre, de 0 a 1. */
    double windFactor(double distance) {
        double eye = eye();
        double wall = wall();
        double radius = radius();
        if (distance > radius) {
            return 0.0d;
        }
        if (distance < eye) {
            return 0.08d;
        }
        if (distance < wall) {
            return 0.6d + 0.4d * (distance - eye) / (wall - eye);
        }
        double fade = Math.max(0.0d, Math.min(1.0d, (radius - distance) / (radius * 0.15d)));
        return Math.pow(wall / distance, 0.7d) * fade;
    }

    /** Le vent en ce point : la direction (tournante, un peu vers le centre) fois la poussee par tick. */
    double[] wind(double px, double pz) {
        double rx = px - x;
        double rz = pz - z;
        double distance = Math.hypot(rx, rz);
        double factor = windFactor(distance);
        if (factor <= 0.0d || distance < 0.01d) {
            return new double[] {0.0d, 0.0d, 0.0d};
        }
        double tx = southern ? -rz : rz;
        double tz = southern ? rx : -rx;
        double dx = tx / distance - rx / distance * 0.25d;
        double dz = tz / distance - rz / distance * 0.25d;
        double norm = Math.hypot(dx, dz);
        double push = (0.01d + 0.008d * category) * factor;
        return new double[] {dx / norm * push, dz / norm * push, factor};
    }

    @Override
    float[] weather(ServerPlayer player) {
        double distance = distance(player);
        double radius = radius();
        if (distance > radius) {
            return null;
        }
        if (distance < eye()) {
            return new float[] {0.0f, 0.0f};
        }
        float rain = (float) Math.max(0.0d, Math.min(1.0d, (radius - distance) / (radius * 0.12d)));
        float thunder = distance <= wall() + 40.0d ? 1.0f : 0.35f * rain;
        return new float[] {rain, thunder};
    }

    @Override
    float shake(ServerPlayer player) {
        double[] wind = wind(player.getX(), player.getZ());
        return exposed(player) && wind[2] > 0.6d ? (float) (0.08d * wind[2] * category / 5.0d) : 0.0f;
    }

    /** Le vent que le client applique au joueur : rien a l'abri. */
    double[] playerWind(ServerPlayer player) {
        if (!exposed(player)) {
            return null;
        }
        double[] wind = wind(player.getX(), player.getZ());
        return wind[2] <= 0.0d ? null : wind;
    }

    static boolean exposed(ServerPlayer player) {
        return player.level().canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
    }

    @Override
    DisasterNet.Entry entry() {
        int ahead = Math.min(trackX.length - 1, 12);
        double position = travelled / STEP;
        int from = (int) Math.floor(position);
        List<Integer> track = new ArrayList<>();
        for (int i = from + 1; i < trackX.length && track.size() < ahead * 2; i += 3) {
            track.add((int) trackX[i]);
            track.add((int) trackZ[i]);
        }
        int[] array = track.stream().mapToInt(Integer::intValue).toArray();
        int left = (int) Math.max(0, (length - travelled) / SPEED / 20.0d);
        return new DisasterNet.Entry(id, DisasterNet.HURRICANE, name, place, (int) x, (int) z, (int) radius(),
                (int) eye(), category, 1, left, array);
    }

    /** La position a cette distance le long de la route. */
    private void locate() {
        double remaining = travelled;
        for (int i = 0; i + 1 < trackX.length; i++) {
            double segment = Math.hypot(trackX[i + 1] - trackX[i], trackZ[i + 1] - trackZ[i]);
            if (remaining <= segment) {
                double f = segment <= 0.0d ? 0.0d : remaining / segment;
                x = trackX[i] + (trackX[i + 1] - trackX[i]) * f;
                z = trackZ[i] + (trackZ[i + 1] - trackZ[i]) * f;
                return;
            }
            remaining -= segment;
        }
        x = trackX[trackX.length - 1];
        z = trackZ[trackZ.length - 1];
    }

    void begin(ServerLevel level, ServerPlayer target, int passSeconds) {
        String heading = Disasters.heading(trackX[0], trackZ[0], trackX[Math.min(4, trackX.length - 1)],
                trackZ[Math.min(4, trackZ.length - 1)]);
        for (ServerPlayer player : level.players()) {
            boolean coming = target != null && target.getUUID().equals(player.getUUID());
            if (coming) {
                tell(player, name + " (categorie " + category + ") arrive sur toi : il passera dans environ "
                        + Disasters.duration(passSeconds) + ". Mets-toi a l'abri, loin de la cote.", ChatFormatting.RED);
            } else {
                double distance = distance(player);
                tell(player, name + " (categorie " + category + ") nait dans le bassin " + place + ", cap "
                        + heading + " (" + Disasters.blocks((int) distance) + " blocs "
                        + Disasters.direction(player.getX(), player.getZ(), x, z) + ").",
                        distance < 1500.0d ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            }
        }
    }

    @Override
    void tick(ServerLevel level) {
        ticks++;
        travelled += SPEED;
        locate();
        if (travelled >= length) {
            end(level, "s'eloigne et se dissipe");
            return;
        }
        EarthData earth = EarthData.get();
        if (earth != null && earth.landAt((int) x, (int) z)) {
            landTravel += SPEED;
            if (landTravel >= 220.0d) {
                landTravel = 0.0d;
                category--;
                if (category <= 0) {
                    end(level, "faiblit sur les terres et se dissipe");
                    return;
                }
                for (ServerPlayer player : near(level, x, z, radius() + 200.0d)) {
                    tell(player, name + " faiblit sur les terres : categorie " + category + ".", ChatFormatting.GOLD);
                }
            }
        }
        double radius = radius();
        List<ServerPlayer> players = near(level, x, z, radius + 160.0d);
        for (ServerPlayer player : players) {
            double distance = distance(player);
            UUID uuid = player.getUUID();
            closest.merge(uuid, distance, Math::min);
            if (distance <= radius) {
                if (inside.add(uuid)) {
                    title(player, name, ChatFormatting.RED, "Categorie " + category + " - mets-toi a l'abri");
                }
                if (distance < eye()) {
                    if (inEye.add(uuid)) {
                        tell(player, "Tu es dans l'oeil " + de() + " : le calme ne durera pas.", ChatFormatting.AQUA);
                    }
                } else {
                    inEye.remove(uuid);
                }
                effects(level, player, distance);
            } else if (inside.remove(uuid)) {
                tell(player, name + " s'eloigne.", ChatFormatting.GRAY);
            }
            wreck(level, player, distance);
        }
        if (ticks % 40 == 0) {
            recede(level);
            pushEntities(level, players);
        }
    }

    /** « de l'ouragan Alice ». */
    private String de() {
        String word = basin.word();
        return "aeiouy".indexOf(word.charAt(0)) >= 0 ? "de l'" + word + " " + name.substring(name.indexOf(' ') + 1)
                : "du " + word + " " + name.substring(name.indexOf(' ') + 1);
    }

    private void end(ServerLevel level, String how) {
        for (ServerPlayer player : near(level, x, z, radius() + 400.0d)) {
            tell(player, name + " " + how + ".", ChatFormatting.GRAY);
        }
        // la mer redescend
        for (BlockPos pos : surge) {
            revert(level, pos);
        }
        surge.clear();
        if (ledger != null) {
            DoHy.LOGGER.info("{} fini : {} feuilles arrachees, {} arbres deracines, {} blocs de mer montee, {} epaves, "
                    + "{} blocs changes", name, stripped, uprooted, flooded, wrecks, ledger.changed);
        }
        finish(level.getServer());
    }

    /** Pres d'un joueur : la pluie de debris, les feuilles, les arbres, la mer qui monte, les eclairs. */
    private void effects(ServerLevel level, ServerPlayer player, double distance) {
        double factor = windFactor(distance);
        double[] wind = wind(player.getX(), player.getZ());
        // des feuilles qui volent dans le vent
        if (ticks % 2 == 0 && factor > 0.3d && exposed(player)) {
            BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState();
            for (int i = 0; i < 3; i++) {
                double px = player.getX() + random.nextDouble() * 24.0d - 12.0d - wind[0] * 120.0d;
                double pz = player.getZ() + random.nextDouble() * 24.0d - 12.0d - wind[1] * 120.0d;
                level.sendParticles(player, new BlockParticleOption(ParticleTypes.BLOCK, leaf), true, true, px,
                        player.getY() + random.nextDouble() * 6.0d, pz, 0, wind[0] * 20.0d, 0.02d, wind[1] * 20.0d,
                        1.0d);
            }
        }
        if (ticks % 60 == 0 && factor > 0.5d && exposed(player)) {
            sound(player, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), player.getX() + wind[0] * 200.0d,
                    player.getEyeY(), player.getZ() + wind[1] * 200.0d, 0.6f, 0.5f);
        }
        if (distance >= eye() && distance <= wall() + 30.0d && random.nextInt(90) == 0) {
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                double a = random.nextDouble() * Math.PI * 2.0d;
                double r = 14.0d + random.nextDouble() * 34.0d;
                int bx = (int) Math.floor(player.getX() + Math.cos(a) * r);
                int bz = (int) Math.floor(player.getZ() + Math.sin(a) * r);
                bolt.snapTo(bx + 0.5d, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz), bz + 0.5d, 0.0f,
                        0.0f);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        if (ticks % 10 == 0 && factor > 0.5d) {
            strip(level, player, wind);
        }
        if (category >= 3 && ticks % 60 == 30 && factor > 0.75d) {
            uproot(level, player, wind);
        }
        if (ticks % 5 == 0 && distance <= wall() + 50.0d) {
            flood(level, player);
        }
    }

    /** Les feuilles arrachees, emportees par le vent. */
    private void strip(ServerLevel level, ServerPlayer player, double[] wind) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 2 * category; i++) {
            int bx = (int) Math.floor(player.getX() + random.nextInt(65) - 32);
            int bz = (int) Math.floor(player.getZ() + random.nextInt(65) - 32);
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
            BlockState state = level.getBlockState(pos.set(bx, top, bz));
            if (state.is(BlockTags.LEAVES) && DisasterTerrain.set(level, ledger(), pos.immutable(),
                    Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)) {
                stripped++;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), bx + 0.5d, top + 0.5d,
                        bz + 0.5d, 0, wind[0] * 25.0d, 0.05d, wind[1] * 25.0d, 1.0d);
            }
        }
    }

    /** Un arbre deracine : tronc et feuillage enleves, le tronc couche au sol dans le sens du vent. */
    private void uproot(ServerLevel level, ServerPlayer player, double[] wind) {
        DisasterTerrain.Ledger ledger = ledger();
        for (int attempt = 0; attempt < 6; attempt++) {
            int bx = (int) Math.floor(player.getX() + random.nextInt(57) - 28);
            int bz = (int) Math.floor(player.getZ() + random.nextInt(57) - 28);
            if (Math.abs(bx - player.getX()) < 4.0d && Math.abs(bz - player.getZ()) < 4.0d) {
                continue;
            }
            int ground = ground(level, bx, bz);
            BlockPos base = new BlockPos(bx, ground + 1, bz);
            BlockState trunk = level.getBlockState(base);
            if (!trunk.is(BlockTags.LOGS) || !DisasterTerrain.surrounded(ledger, bx, bz)) {
                continue;
            }
            List<BlockPos> tree = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            queue.add(base);
            seen.add(base);
            int height = 0;
            while (!queue.isEmpty() && tree.size() < 320) {
                BlockPos pos = queue.poll();
                BlockState state = level.getBlockState(pos);
                boolean log = state.is(BlockTags.LOGS);
                if (!log && !state.is(BlockTags.LEAVES)) {
                    continue;
                }
                if (!DisasterTerrain.editable(level, ledger, pos)) {
                    tree.clear();
                    break;
                }
                tree.add(pos);
                if (log && pos.getX() == bx && pos.getZ() == bz) {
                    height = Math.max(height, pos.getY() - base.getY() + 1);
                }
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (next.getY() >= base.getY() && Math.abs(next.getX() - bx) <= 7 && Math.abs(next.getZ() - bz) <= 7
                            && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (tree.isEmpty() || height == 0) {
                continue;
            }
            for (int i = tree.size() - 1; i >= 0; i--) {
                DisasterTerrain.set(level, ledger, tree.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            // le tronc couche, dans le sens du vent
            boolean alongX = Math.abs(wind[0]) >= Math.abs(wind[1]);
            int sx = alongX ? (int) Math.signum(wind[0]) : 0;
            int sz = alongX ? 0 : (int) Math.signum(wind[1]);
            BlockState lying = trunk.hasProperty(RotatedPillarBlock.AXIS)
                    ? trunk.setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z) : trunk;
            for (int i = 1; i <= Math.min(height, 6); i++) {
                int lx = bx + sx * i;
                int lz = bz + sz * i;
                BlockPos at = new BlockPos(lx, ground(level, lx, lz) + 1, lz);
                if (soft(level.getBlockState(at))) {
                    DisasterTerrain.set(level, ledger, at, lying, Block.UPDATE_CLIENTS);
                }
            }
            uprooted++;
            for (ServerPlayer near : near(level, bx, bz, 48.0d)) {
                sound(near, SoundEvents.WOOD_BREAK, bx, ground + 2, bz, 2.0f, 0.5f);
            }
            return;
        }
    }

    /**
     * La mer monte sur la cote : d'un bloc (categories 1 et 2) a trois (categorie
     * 5). Une seule nappe, qui part de la mer : la mer monte pres du rivage, avec
     * un bord en pente vers le large, et deborde sur la terre de proche en proche.
     */
    private void flood(ServerLevel level, ServerPlayer player) {
        // assez d'eau pour une longue cote ; au-dela, la mer ne monte plus
        if (surge.size() >= SURGE) {
            return;
        }
        int surface = level.getSeaLevel() - 1;
        int rise = category <= 2 ? 1 : category <= 4 ? 2 : 3;
        int top = surface + rise;
        DisasterTerrain.Ledger ledger = ledger();
        ArrayDeque<BlockPos> grown = new ArrayDeque<>();
        for (int i = 0; i < 6 * category; i++) {
            int bx = (int) Math.floor(player.getX() + random.nextInt(73) - 36);
            int bz = (int) Math.floor(player.getZ() + random.nextInt(73) - 36);
            int ground = ground(level, bx, bz);
            // un creux de plus d'un bloc sous la mer reste sec : l'eau ne s'y deverse pas d'un coup
            if (ground < surface - 1 || ground >= top) {
                continue;
            }
            for (int y = ground + 1; y <= top; y++) {
                BlockPos pos = new BlockPos(bx, y, bz);
                if (!surgeAt(level, ledger, pos, surface, rise)) {
                    break;
                }
                grown.add(pos);
            }
        }
        // la mer gagne de proche en proche autour de ce qui vient de monter
        for (int step = 0; step < 8 * category && !grown.isEmpty(); step++) {
            BlockPos from = grown.poll();
            if (surgeAt(level, ledger, from.above(), surface, rise)) {
                grown.add(from.above());
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = from.relative(direction);
                if (Math.abs(next.getX() - player.getX()) > 48.0d || Math.abs(next.getZ() - player.getZ()) > 48.0d) {
                    continue;
                }
                if (Floods.free(level, next) && surgeAt(level, ledger, next.below(), surface, rise)) {
                    grown.add(next.below());
                }
                if (surgeAt(level, ledger, next, surface, rise)) {
                    grown.add(next);
                }
            }
        }
    }

    /** Pose l'eau de la maree ici si elle y tient ; vrai si c'est fait (la maree peut gagner plus loin). */
    private boolean surgeAt(ServerLevel level, DisasterTerrain.Ledger ledger, BlockPos pos, int surface, int rise) {
        if (pos.getY() > surface + rise) {
            return false;
        }
        BlockState water = Floods.overWater(level, pos, surface) ? Floods.over(level, pos, surface, rise)
                : Floods.beside(level, pos);
        if (water == null || !DisasterTerrain.set(level, ledger, pos, water, DisasterTerrain.FLAGS)) {
            return false;
        }
        surge.add(pos);
        flooded++;
        return true;
    }

    /** Derriere la tempete, la mer redescend. */
    private void recede(ServerLevel level) {
        double radius = radius();
        surge.removeIf(pos -> {
            if (Math.hypot(pos.getX() - x, pos.getZ() - z) <= radius) {
                return false;
            }
            revert(level, pos);
            return true;
        });
    }

    private void revert(ServerLevel level, BlockPos pos) {
        // l'eau de la maree, ou la glace qu'elle est devenue dans un biome froid
        if (ledger == null || !level.isLoaded(pos) || !level.getFluidState(pos).is(FluidTags.WATER)
                && !level.getBlockState(pos).is(Blocks.ICE)) {
            return;
        }
        BlockState before = DisasterTerrain.original(ledger, pos);
        if (before != null && before.getFluidState().isEmpty()) {
            level.setBlock(pos, before, Block.UPDATE_CLIENTS);
        }
    }

    /** Le vent emporte aussi les betes et les objets au sol autour des joueurs. */
    private void pushEntities(ServerLevel level, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (distance(player) > radius()) {
                continue;
            }
            AABB box = player.getBoundingBox().inflate(32.0d, 12.0d, 32.0d);
            for (Entity entity : level.getEntities((Entity) null, box, found -> !(found instanceof ServerPlayer)
                    && (found instanceof LivingEntity || found instanceof ItemEntity))) {
                if (!level.canSeeSky(entity.blockPosition().above())) {
                    continue;
                }
                double[] wind = wind(entity.getX(), entity.getZ());
                // quarante ticks d'un coup : le vent souffle par rafales sur ce qui n'est pas un joueur
                entity.push(wind[0] * 12.0d, entity.onGround() ? 0.08d * wind[2] : 0.0d, wind[1] * 12.0d);
            }
        }
    }

    /** L'ouragan est passe : une epave et du bois flotte sur la plage la plus proche du joueur. */
    private void wreck(ServerLevel level, ServerPlayer player, double distance) {
        UUID uuid = player.getUUID();
        Double nearest = closest.get(uuid);
        if (nearest == null || nearest > radius() * 0.5d || distance < radius() * 0.6d || wrecked.contains(uuid)) {
            return;
        }
        wrecked.add(uuid);
        int sea = level.getSeaLevel();
        DisasterTerrain.Ledger ledger = ledger();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 400; attempt++) {
            int bx = (int) Math.floor(player.getX() + random.nextInt(81) - 40);
            int bz = (int) Math.floor(player.getZ() + random.nextInt(81) - 40);
            int ground = ground(level, bx, bz);
            if (ground < sea - 1 || ground > sea + 3) {
                continue;
            }
            BlockState sand = level.getBlockState(pos.set(bx, ground, bz));
            if (!sand.is(BlockTags.SAND) && !sand.is(Blocks.GRAVEL)) {
                continue;
            }
            boolean shore = false;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (level.getFluidState(new BlockPos(bx, sea - 1, bz).relative(direction, 3)).is(FluidTags.WATER)) {
                    shore = true;
                }
            }
            if (!shore) {
                continue;
            }
            if (build(level, ledger, bx, ground + 1, bz)) {
                wrecks++;
                wreckAt = new BlockPos(bx, ground + 1, bz);
                tell(player, "Une epave s'est echouee sur la plage, a " + (int) Math.hypot(bx - player.getX(),
                        bz - player.getZ()) + " blocs " + Disasters.direction(player.getX(), player.getZ(), bx, bz)
                        + " : fouille-la avant le retour du terrain.", ChatFormatting.GOLD);
                driftwood(level, ledger, bx, bz);
                return;
            }
        }
    }

    /** Une coque brisee de sept blocs, un mat couche, un coffre de tresor et un tonneau de vivres. */
    private boolean build(ServerLevel level, DisasterTerrain.Ledger ledger, int bx, int y, int bz) {
        boolean alongX = random.nextBoolean();
        BlockState planks = random.nextBoolean() ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                : Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                alongX ? Direction.Axis.X : Direction.Axis.Z);
        List<BlockPos> hull = new ArrayList<>();
        for (int i = -3; i <= 3; i++) {
            for (int j = -1; j <= 1; j++) {
                int px = bx + (alongX ? i : j);
                int pz = bz + (alongX ? j : i);
                hull.add(new BlockPos(px, y - 1, pz));
            }
        }
        for (BlockPos pos : hull) {
            if (!DisasterTerrain.editable(level, ledger, pos) || !DisasterTerrain.editable(level, ledger, pos.above())) {
                return false;
            }
        }
        for (BlockPos pos : hull) {
            DisasterTerrain.set(level, ledger, pos, random.nextInt(6) == 0 ? Blocks.AIR.defaultBlockState() : planks,
                    Block.UPDATE_CLIENTS);
        }
        // les bords, a moitie arraches
        for (int i = -3; i <= 3; i++) {
            for (int side : new int[] {-1, 1}) {
                if (random.nextInt(3) == 0) {
                    continue;
                }
                int px = bx + (alongX ? i : side);
                int pz = bz + (alongX ? side : i);
                DisasterTerrain.set(level, ledger, new BlockPos(px, y, pz), planks, Block.UPDATE_CLIENTS);
            }
        }
        // le mat couche en travers
        for (int i = -2; i <= 2; i++) {
            int px = bx + (alongX ? 0 : i);
            int pz = bz + (alongX ? i : 0);
            BlockState mast = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                    alongX ? Direction.Axis.Z : Direction.Axis.X);
            BlockPos at = new BlockPos(px, y + 1, pz);
            if (soft(level.getBlockState(at))) {
                DisasterTerrain.set(level, ledger, at, i == 0 ? log : mast, Block.UPDATE_CLIENTS);
            }
        }
        BlockPos chest = new BlockPos(bx + (alongX ? 2 : 0), y, bz + (alongX ? 0 : 2));
        BlockPos barrel = new BlockPos(bx + (alongX ? -2 : 0), y, bz + (alongX ? 0 : -2));
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,
                alongX ? Direction.WEST : Direction.NORTH);
        if (DisasterTerrain.set(level, ledger, chest, chestState, Block.UPDATE_CLIENTS)) {
            RandomizableContainer.setBlockEntityLootTable(level, random, chest, BuiltInLootTables.SHIPWRECK_TREASURE);
        }
        if (DisasterTerrain.set(level, ledger, barrel, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS)) {
            RandomizableContainer.setBlockEntityLootTable(level, random, barrel, BuiltInLootTables.SHIPWRECK_SUPPLY);
        }
        return true;
    }

    /** Du bois flotte rejete sur la plage autour de l'epave. */
    private void driftwood(ServerLevel level, DisasterTerrain.Ledger ledger, int bx, int bz) {
        int sea = level.getSeaLevel();
        for (int i = 0; i < 10; i++) {
            int px = bx + random.nextInt(25) - 12;
            int pz = bz + random.nextInt(25) - 12;
            int ground = ground(level, px, pz);
            if (ground < sea - 1 || ground > sea + 3) {
                continue;
            }
            BlockPos at = new BlockPos(px, ground + 1, pz);
            if (soft(level.getBlockState(at)) && level.getFluidState(at).isEmpty()) {
                DisasterTerrain.set(level, ledger, at, Blocks.OAK_LOG.defaultBlockState().setValue(
                        RotatedPillarBlock.AXIS, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z),
                        Block.UPDATE_CLIENTS);
            }
        }
    }
}
