package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;

/**
 * Les deux caracteres d'une graine minerale, ceux que JobsReborn range dans le NBT
 * de la graine ({@code speed}, {@code offline}) et recopie dans le bloc plante
 * ({@code BotanistMineralSeedBlockData}) :
 *
 * <ul>
 *   <li>la <b>vitesse</b> ({@link SeedSpeed}) ;</li>
 *   <li><b>pousse hors ligne</b> : la plante compte le temps reel depuis qu'elle
 *       est plantee, y compris quand son chunk n'est pas charge (ou que le serveur
 *       est arrete). Sans ce caractere, elle ne pousse que chunk charge : le temps
 *       du monde qui passe pendant qu'elle est chargee, et rien d'autre.</li>
 * </ul>
 *
 * <p>Une graine donnee en recompense, ou sortie de l'onglet creatif, porte les
 * caracteres par defaut : moyenne, sans pousse hors ligne.
 */
public record SeedTraits(SeedSpeed speed, boolean offline) {

    public static final SeedTraits DEFAULT = new SeedTraits(SeedSpeed.MEDIUM, false);

    public static final Codec<SeedTraits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SeedSpeed.CODEC.optionalFieldOf("speed", SeedSpeed.MEDIUM).forGetter(SeedTraits::speed),
            Codec.BOOL.optionalFieldOf("offline", false).forGetter(SeedTraits::offline)
    ).apply(instance, SeedTraits::new));

    public static final StreamCodec<ByteBuf, SeedTraits> STREAM_CODEC = StreamCodec.composite(
            SeedSpeed.STREAM_CODEC, SeedTraits::speed,
            ByteBufCodecs.BOOL, SeedTraits::offline,
            SeedTraits::new);

    /** Chance qu'une graine recoltee pousse hors ligne : de 5 % (niveau 0) a 15 % (niveau 100). */
    static double offlineChance(int level) {
        return 0.05d + 0.001d * SeedSpeed.clampLevel(level);
    }

    /**
     * Les caracteres d'une graine tombee a la recolte. Comme chez Paladium
     * ({@code BotanistDropConfig.seed}), ils sont tires a neuf, au niveau du
     * joueur, et ne s'heritent pas de la plante recoltee.
     */
    static SeedTraits roll(int level, RandomSource random) {
        return new SeedTraits(SeedSpeed.roll(level, random), random.nextDouble() < offlineChance(level));
    }
}
