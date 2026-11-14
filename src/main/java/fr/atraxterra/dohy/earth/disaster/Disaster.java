package fr.atraxterra.dohy.earth.disaster;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Une catastrophe en cours : sa place, son age, son releve de terrain, et ce
 * qu'elle fait ressentir aux joueurs (secousse, cendre, meteo locale).
 *
 * <p>Elle n'agit sur les blocs que la ou un joueur se trouve : loin de tout
 * joueur, elle ne fait que passer sur l'atlas et dans les nouvelles.
 */
abstract class Disaster {

    final int id;
    final DisasterKind kind;
    /** Ce que disent les messages : « Seisme de magnitude 6,4 », « Ouragan Alice ». */
    final String name;
    /** La ou elle frappe : « Japon », « Etna (Italie) ». */
    final String place;
    final long started = System.currentTimeMillis();
    final RandomSource random;
    double x;
    double z;
    int ticks;
    boolean done;
    DisasterTerrain.Ledger ledger;

    Disaster(int id, DisasterKind kind, String name, String place, double x, double z, RandomSource random) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.place = place;
        this.x = x;
        this.z = z;
        this.random = random;
    }

    /** Un tick de la catastrophe, sur le monde de la carte. */
    abstract void tick(ServerLevel level);

    /** Sa portee : jusqu'ou elle se ressent, en blocs. */
    abstract double radius();

    /** Ce qu'en voit l'atlas. */
    abstract DisasterNet.Entry entry();

    /** La secousse ressentie en ce point, de 0 a 1. */
    float shake(ServerPlayer player) {
        return 0.0f;
    }

    /** La cendre dans l'air en ce point, de 0 a 1. */
    float ash(ServerPlayer player) {
        return 0.0f;
    }

    /** La pluie et l'orage voulus pour ce joueur, ou {@code null} si elle ne change pas sa meteo. */
    float[] weather(ServerPlayer player) {
        return null;
    }

    /** Le libelle des messages du terrain : « seisme du Japon ». */
    String label() {
        return kind.key() + " (" + place + ")";
    }

    /** Ouvre le releve de terrain de la catastrophe s'il ne l'est pas. */
    DisasterTerrain.Ledger ledger() {
        if (ledger == null) {
            ledger = DisasterTerrain.open(started + "_" + kind.key() + "_" + id, label());
        }
        return ledger;
    }

    /** La fin : le terrain reviendra dans le delai des reglages. */
    void finish(MinecraftServer server) {
        done = true;
        if (ledger != null) {
            long delay = (long) (DisasterConfig.settings().restoreHours() * 3_600_000.0d);
            DisasterTerrain.close(server, ledger, System.currentTimeMillis() + delay);
        }
    }

    // -- Les joueurs --------------------------------------------------------------------------

    /** Les joueurs de la carte a moins de {@code range} blocs de ce point (hors spectateurs). */
    static List<ServerPlayer> near(ServerLevel level, double x, double z, double range) {
        List<ServerPlayer> found = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && Math.hypot(player.getX() - x, player.getZ() - z) <= range) {
                found.add(player);
            }
        }
        return found;
    }

    double distance(ServerPlayer player) {
        return Math.hypot(player.getX() - x, player.getZ() - z);
    }

    /** Un son pour ce joueur seul : un son lointain lui arrive de la bonne direction, a douze blocs. */
    static void sound(ServerPlayer player, SoundEvent sound, double sx, double sy, double sz, float volume,
                      float pitch) {
        double dx = sx - player.getX();
        double dy = sy - player.getEyeY();
        double dz = sz - player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double px = sx;
        double py = sy;
        double pz = sz;
        if (distance > 12.0d) {
            px = player.getX() + dx / distance * 12.0d;
            py = player.getEyeY() + dy / distance * 12.0d;
            pz = player.getZ() + dz / distance * 12.0d;
        }
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.WEATHER, px, py, pz, volume, pitch, player.getRandom().nextLong()));
    }

    /** Des particules pour ce joueur, visibles de loin. */
    static void particles(ServerLevel level, ServerPlayer player, ParticleOptions particle, double px, double py,
                          double pz, int count, double spread, double spreadY, double speed) {
        level.sendParticles(player, particle, true, true, px, py, pz, count, spread, spreadY, spread, speed);
    }

    static void title(ServerPlayer player, String title, ChatFormatting color, String subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 16));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(color)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(subtitle).withStyle(ChatFormatting.GRAY)));
    }

    static void tell(ServerPlayer player, String message, ChatFormatting color) {
        player.sendSystemMessage(Disasters.prefix().append(Component.literal(message).withStyle(color)));
    }

    // -- Le terrain ---------------------------------------------------------------------------

    /** Le premier bloc solide sous le ciel (feuilles et plantes non comprises) : son y. */
    static int ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /** Vrai si ce bloc est de la nature : terre, roche, sable, gravier, plantes, arbres. */
    static boolean natural(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.MUD) || state.is(Blocks.CLAY)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.CALCITE) || state.is(Blocks.TUFF) || state.is(Blocks.BASALT);
    }

    /** Vrai si ce bloc peut etre remplace comme de l'air : air, herbes, fleurs, neige. */
    static boolean soft(BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()
                || state.is(BlockTags.REPLACEABLE_BY_TREES) && !state.is(BlockTags.LEAVES)
                && state.getFluidState().isEmpty();
    }

    static BlockPos.MutableBlockPos at(BlockPos.MutableBlockPos pos, double x, int y, double z) {
        return pos.set((int) Math.floor(x), y, (int) Math.floor(z));
    }
}
