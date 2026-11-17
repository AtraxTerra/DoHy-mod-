package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Les eruptions d'epice.
 *
 * <p>Toutes les six a dix minutes, tant qu'un joueur est dans le desert, le sable
 * gronde quelque part a quelques dizaines de blocs de l'un d'eux : une colonne de
 * fumee monte, et huit secondes plus tard l'eruption crache un nuage rouille.
 * Le sable alentour devient du sable d'epice — un nouveau champ, a recolter —
 * et quelques pincees de melange retombent au centre pour qui arrive le premier.
 *
 * <p>Jamais dans la cuvette d'Arrakeen, jamais dans un chunk que personne ne
 * charge. C'est ce qui renouvelle l'epice : les champs nes avec le monde, la
 * moissonneuse les vide.
 */
@EventBusSubscriber(modid = DoHy.MOD_ID)
public final class SpiceBlows {

    private static final int MIN_INTERVAL = 6 * 60 * 20;
    private static final int MAX_INTERVAL = 10 * 60 * 20;
    /** Le grondement, avant l'eruption. */
    private static final int WARNING = 8 * 20;
    /** Arrakeen et sa cuvette : pas d'eruption a moins de ce rayon du centre. */
    private static final double SAFE_RADIUS = 520.0d;
    private static final int NEAR = 40;
    private static final int FAR = 130;
    /** Les joueurs a cette distance l'entendent et sont prevenus. */
    private static final double HEARD = 400.0d;
    private static final int SPICE = 0xE8742C;
    private static final String[] DIRECTIONS = {"nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest",
            "nord-ouest"};

    private static int countdown = -1;
    private static Blow pending;

    private SpiceBlows() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == DoHyArrakis.DIMENSION) {
            tick(level);
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        countdown = -1;
        pending = null;
    }

    private static void tick(ServerLevel level) {
        if (pending != null) {
            pending.tick(level);
            if (pending.done) {
                pending = null;
            }
            return;
        }
        List<ServerPlayer> desert = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.isAlive()
                    && Math.hypot(player.getX(), player.getZ()) > SAFE_RADIUS - NEAR) {
                desert.add(player);
            }
        }
        if (desert.isEmpty()) {
            return;
        }
        RandomSource random = level.getRandom();
        if (countdown < 0) {
            countdown = Mth.nextInt(random, MIN_INTERVAL, MAX_INTERVAL);
        }
        if (--countdown > 0) {
            return;
        }
        countdown = Mth.nextInt(random, MIN_INTERVAL, MAX_INTERVAL);
        ServerPlayer target = desert.get(random.nextInt(desert.size()));
        BlockPos spot = spot(level, target, NEAR, FAR);
        if (spot != null) {
            start(level, spot);
        }
    }

    /** Le centre de l'eruption qui gronde en ce moment, ou {@code null}. */
    public static BlockPos pending() {
        Blow blow = pending;
        return blow == null ? null : blow.center;
    }

    /** {@code /arrakis epice} : une eruption pres de ce joueur, apres le grondement. Faux si rien ne convient. */
    public static boolean trigger(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != DoHyArrakis.DIMENSION
                || pending != null) {
            return false;
        }
        BlockPos spot = spot(level, player, 20, 48);
        if (spot == null) {
            return false;
        }
        start(level, spot);
        return true;
    }

    /** Un point de sable nu, dans un chunk charge, hors de la cuvette d'Arrakeen. */
    private static BlockPos spot(ServerLevel level, ServerPlayer player, int near, int far) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            double distance = near + random.nextDouble() * (far - near);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (Math.hypot(x, z) < SAFE_RADIUS || !level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            BlockState top = level.getBlockState(new BlockPos(x, y, z));
            // du sable nu : une eruption fait un champ neuf, pas un champ de plus sur un autre
            if (top.is(Blocks.SAND)) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    private static void start(ServerLevel level, BlockPos center) {
        pending = new Blow(center, 7 + level.getRandom().nextInt(6));
        DoHy.LOGGER.info("Arrakis : eruption d'epice en {} {}", center.getX(), center.getZ());
        for (ServerPlayer player : level.players()) {
            if (heard(player, center)) {
                player.sendSystemMessage(Component.literal("Le sable gronde au " + direction(player, center)
                        + ", a " + distance(player, center) + " blocs...").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private static boolean heard(ServerPlayer player, BlockPos center) {
        return Math.hypot(player.getX() - center.getX(), player.getZ() - center.getZ()) <= HEARD;
    }

    private static int distance(ServerPlayer player, BlockPos center) {
        return (int) Math.round(Math.hypot(player.getX() - center.getX(), player.getZ() - center.getZ()));
    }

    /** Le point cardinal ou se trouve {@code center}, vu du joueur. */
    static String direction(ServerPlayer player, BlockPos center) {
        double dx = center.getX() + 0.5d - player.getX();
        double dz = center.getZ() + 0.5d - player.getZ();
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int index = Math.floorMod((int) Math.round(degrees / 45.0d), 8);
        return DIRECTIONS[index];
    }

    /** Un son pour ce joueur seul : un son lointain lui arrive de la bonne direction, a douze blocs. */
    private static void sound(ServerPlayer player, SoundEvent sound, BlockPos at, float volume, float pitch) {
        double dx = at.getX() + 0.5d - player.getX();
        double dy = at.getY() + 0.5d - player.getEyeY();
        double dz = at.getZ() + 0.5d - player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double px = at.getX() + 0.5d;
        double py = at.getY() + 0.5d;
        double pz = at.getZ() + 0.5d;
        if (distance > 12.0d) {
            px = player.getX() + dx / distance * 12.0d;
            py = player.getEyeY() + dy / distance * 12.0d;
            pz = player.getZ() + dz / distance * 12.0d;
        }
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.WEATHER, px, py, pz, volume, pitch, player.getRandom().nextLong()));
    }

    /** Des particules pour tous ceux qui l'entendent, visibles de loin. */
    private static void particles(ServerLevel level, ParticleOptions particle, double x, double y, double z,
                                  int count, double spread, double spreadY, double speed, BlockPos center) {
        for (ServerPlayer player : level.players()) {
            if (heard(player, center)) {
                level.sendParticles(player, particle, true, true, x, y, z, count, spread, spreadY, spread, speed);
            }
        }
    }

    private static final class Blow {

        private final BlockPos center;
        private final int radius;
        private int ticks = WARNING;
        private boolean done;

        Blow(BlockPos center, int radius) {
            this.center = center;
            this.radius = radius;
        }

        void tick(ServerLevel level) {
            ticks--;
            if (!level.hasChunkAt(center)) {
                done = true;
                return;
            }
            double x = center.getX() + 0.5d;
            double z = center.getZ() + 0.5d;
            if (ticks % 4 == 0) {
                particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, center.getY() + 1.0d, z, 2, 0.6d, 0.2d,
                        0.01d, center);
                particles(level, new DustParticleOptions(SPICE, 3.0f), x, center.getY() + 1.5d, z, 6, 1.5d, 0.6d,
                        0.02d, center);
            }
            if (ticks % 20 == 0 && ticks > 0) {
                for (ServerPlayer player : level.players()) {
                    if (heard(player, center)) {
                        sound(player, SoundEvents.WARDEN_HEARTBEAT, center, 2.0f, 0.5f);
                    }
                }
            }
            if (ticks <= 0) {
                erupt(level);
                done = true;
            }
        }

        private void erupt(ServerLevel level) {
            RandomSource random = level.getRandom();
            double x = center.getX() + 0.5d;
            double z = center.getZ() + 0.5d;
            int converted = 0;
            double[] phase = {random.nextDouble() * 6.3d, random.nextDouble() * 6.3d, random.nextDouble() * 6.3d};
            BlockState spice = ArrakisContent.SPICE_SAND.get().defaultBlockState();
            for (int dx = -radius - 3; dx <= radius + 3; dx++) {
                for (int dz = -radius - 3; dz <= radius + 3; dz++) {
                    double angle = Math.atan2(dz, dx);
                    double edge = radius * (0.82d + 0.12d * Math.sin(3.0d * angle + phase[0])
                            + 0.08d * Math.sin(5.0d * angle + phase[1]) + 0.05d * Math.sin(8.0d * angle + phase[2]));
                    if (Math.hypot(dx, dz) > edge) {
                        continue;
                    }
                    int bx = center.getX() + dx;
                    int bz = center.getZ() + dz;
                    if (!level.hasChunkAt(new BlockPos(bx, 0, bz))) {
                        continue;
                    }
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
                    for (int depth = 0; depth <= 1; depth++) {
                        BlockPos pos = new BlockPos(bx, top - depth, bz);
                        if (level.getBlockState(pos).is(Blocks.SAND)) {
                            level.setBlock(pos, spice, Block.UPDATE_CLIENTS);
                            converted++;
                        }
                    }
                }
            }
            particles(level, ParticleTypes.EXPLOSION_EMITTER, x, center.getY() + 1.0d, z, 1, 0.0d, 0.0d, 0.0d,
                    center);
            for (int h = 0; h <= 24; h += 2) {
                particles(level, new DustParticleOptions(SPICE, 4.0f), x, center.getY() + 1.0d + h, z, 14,
                        1.2d + h * 0.12d, 0.6d, 0.03d, center);
            }
            particles(level, new BlockParticleOption(ParticleTypes.BLOCK, spice), x, center.getY() + 1.5d, z, 120,
                    radius * 0.4d, 1.0d, 0.3d, center);
            for (int i = 0, n = 6 + random.nextInt(7); i < n; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0d;
                ItemEntity item = new ItemEntity(level, x, center.getY() + 1.5d, z,
                        new ItemStack(ArrakisContent.MELANGE.get(), 1 + random.nextInt(3)),
                        Math.cos(angle) * 0.25d, 0.55d + random.nextDouble() * 0.35d, Math.sin(angle) * 0.25d);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
            for (ServerPlayer player : level.players()) {
                if (heard(player, center)) {
                    sound(player, SoundEvents.GENERIC_EXPLODE.value(), center, 3.0f, 0.55f);
                    sound(player, SoundEvents.SAND_BREAK, center, 2.0f, 0.5f);
                    player.sendSystemMessage(Component.literal("Eruption d'epice au " + direction(player, center)
                            + " (" + center.getX() + ", " + center.getZ() + ") : un nouveau champ est ne.")
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            DoHy.LOGGER.info("Arrakis : eruption d'epice en {} {}, {} blocs de sable d'epice", center.getX(),
                    center.getZ(), converted);
        }
    }
}
