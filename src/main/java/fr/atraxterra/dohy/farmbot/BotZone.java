package fr.atraxterra.dohy.farmbot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/**
 * La zone d'un bot de farming : le rectangle entre les deux coins cliques a la telecommande, d'un
 * bloc sous le plus bas des deux a trois blocs au-dessus du plus haut. Un coin clique sur la terre
 * labouree couvre ainsi la culture qui pousse dessus, et la canne a sucre jusqu'a trois de haut.
 */
public record BotZone(BlockPos min, BlockPos max) {

    /** Le plus grand rectangle, de cote. */
    public static final int MAX_SIDE = 48;
    /** Ce que la zone prend sous le coin le plus bas et au-dessus du plus haut. */
    static final int BELOW = 1;
    static final int ABOVE = 3;
    /** La plus grande hauteur, marges comprises. */
    public static final int MAX_HEIGHT = 12;

    public static final Codec<BotZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("min").forGetter(BotZone::min),
            BlockPos.CODEC.fieldOf("max").forGetter(BotZone::max)
    ).apply(instance, BotZone::new));

    public static final StreamCodec<ByteBuf, BotZone> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BotZone::min, BlockPos.STREAM_CODEC, BotZone::max, BotZone::new);

    /** La zone de deux coins cliques. */
    public static BotZone of(BlockPos a, BlockPos b) {
        return new BotZone(
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()) - BELOW,
                        Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()) + ABOVE,
                        Math.max(a.getZ(), b.getZ())));
    }

    public int width() {
        return max.getX() - min.getX() + 1;
    }

    public int length() {
        return max.getZ() - min.getZ() + 1;
    }

    public int height() {
        return max.getY() - min.getY() + 1;
    }

    public int volume() {
        return width() * length() * height();
    }

    /** Trop grande pour un bot ? */
    public boolean tooBig() {
        return width() > MAX_SIDE || length() > MAX_SIDE || height() > MAX_HEIGHT;
    }

    /** La hauteur ou dessiner son contour : celle des cultures posees sur les coins cliques. */
    public double groundY() {
        return min.getY() + BELOW + 1.05d;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY()
                && pos.getY() <= max.getY() && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** La distance horizontale d'un point au rectangle, 0 dedans. */
    public double distance(double x, double z) {
        double dx = Math.max(0.0d, Math.max(min.getX() - x, x - (max.getX() + 1)));
        double dz = Math.max(0.0d, Math.max(min.getZ() - z, z - (max.getZ() + 1)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    public BlockPos center() {
        return new BlockPos((min.getX() + max.getX()) / 2, min.getY() + BELOW + 1, (min.getZ() + max.getZ()) / 2);
    }

    /** La position numero {@code index} de la zone, pour la parcourir sans tout garder en memoire. */
    BlockPos at(int index) {
        int w = width();
        int l = length();
        int x = index % w;
        int z = (index / w) % l;
        int y = index / (w * l);
        return new BlockPos(min.getX() + x, min.getY() + y, min.getZ() + z);
    }

    /** « 12 x 20 blocs ». */
    public String label() {
        return width() + " x " + length() + " blocs";
    }
}
