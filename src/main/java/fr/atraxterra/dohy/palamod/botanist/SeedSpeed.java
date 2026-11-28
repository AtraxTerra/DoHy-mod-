package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * La vitesse de pousse d'une graine minerale ({@code BotanistMineralSeedSpeed} de
 * JobsReborn : {@code SLOW}, {@code MEDIUM}, {@code FAST}, « Lente », « Moyenne »,
 * « Rapide »).
 *
 * <p>Chez Paladium, le temps de pousse de chaque vitesse est une expression de la
 * configuration du serveur, absente du jar client. Les rapports sont donc
 * calibres ici : la vitesse moyenne pousse au temps de base de la graine
 * ({@link MineralSeed#baseGrowthTicks()}), la lente en deux fois plus, la rapide
 * en deux fois moins.
 *
 * <p>Le tirage d'une graine recoltee suit le niveau de Fermier, comme les
 * expressions de Paladium suivent le niveau du joueur ({@code level}) : 10 % de
 * rapides au niveau 0, 30 % au niveau 100 ; les lentes font le chemin inverse.
 */
public enum SeedSpeed implements StringRepresentable {
    SLOW("slow", 2.0d, ChatFormatting.RED),
    MEDIUM("medium", 1.0d, ChatFormatting.GOLD),
    FAST("fast", 0.5d, ChatFormatting.GREEN);

    public static final Codec<SeedSpeed> CODEC = StringRepresentable.fromEnum(SeedSpeed::values);
    public static final StreamCodec<ByteBuf, SeedSpeed> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(index -> values()[Math.floorMod(index, values().length)], SeedSpeed::ordinal);

    private final String id;
    private final double growthFactor;
    private final ChatFormatting color;

    SeedSpeed(String id, double growthFactor, ChatFormatting color) {
        this.id = id;
        this.growthFactor = growthFactor;
        this.color = color;
    }

    /** Multiplie le temps de base de la graine. */
    public double growthFactor() {
        return growthFactor;
    }

    public ChatFormatting color() {
        return color;
    }

    /** {@code tooltip.palamod.mineral_seed.speed.<id>}. */
    public String translationKey() {
        return "tooltip.palamod.mineral_seed.speed." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Chance d'une graine rapide a ce niveau de Fermier : de 10 % a 30 %. */
    static double fastChance(int level) {
        return 0.10d + 0.002d * clampLevel(level);
    }

    /** Chance d'une graine lente a ce niveau de Fermier : de 30 % a 10 %. */
    static double slowChance(int level) {
        return 0.30d - 0.002d * clampLevel(level);
    }

    /** La vitesse d'une graine qui tombe a la recolte. */
    static SeedSpeed roll(int level, RandomSource random) {
        double draw = random.nextDouble();
        if (draw < fastChance(level)) {
            return FAST;
        }
        if (draw < fastChance(level) + slowChance(level)) {
            return SLOW;
        }
        return MEDIUM;
    }

    static int clampLevel(int level) {
        return Math.max(0, Math.min(100, level));
    }
}
