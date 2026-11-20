package fr.atraxterra.dohy.farmbot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Optional;

/**
 * Le reglage d'un bot, garde sur l'objet quand on le range : sa dimension, sa zone, ses coffres, et
 * s'il etait en pause. Reposer le bot pres de sa zone la lui rend.
 */
public record BotSetup(String dimension, Optional<BotZone> zone, List<BlockPos> chests, boolean paused) {

    public static final BotSetup EMPTY = new BotSetup("", Optional.empty(), List.of(), false);

    public static final Codec<BotSetup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(BotSetup::dimension),
            BotZone.CODEC.optionalFieldOf("zone").forGetter(BotSetup::zone),
            BlockPos.CODEC.listOf().optionalFieldOf("chests", List.of()).forGetter(BotSetup::chests),
            Codec.BOOL.optionalFieldOf("paused", false).forGetter(BotSetup::paused)
    ).apply(instance, BotSetup::new));

    public static final StreamCodec<ByteBuf, BotSetup> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BotSetup::dimension,
            ByteBufCodecs.optional(BotZone.STREAM_CODEC), BotSetup::zone,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), BotSetup::chests,
            ByteBufCodecs.BOOL, BotSetup::paused,
            BotSetup::new);

    public BotSetup {
        chests = List.copyOf(chests);
    }
}
