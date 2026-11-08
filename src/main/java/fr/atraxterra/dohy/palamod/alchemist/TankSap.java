package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Le contenu d'un tank : une seve et une quantite en unites (1 000 la fiole).
 * Porte par la block entity, et par l'objet quand le tank est casse — le
 * {@code liquid} / {@code liquidLevel} du NBT de {@code BlockTank}.
 */
public record TankSap(Sap type, int amount) {

    public static final Codec<TankSap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Sap.CODEC.fieldOf("type").forGetter(TankSap::type),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(TankSap::amount)
    ).apply(instance, TankSap::new));

    public static final StreamCodec<ByteBuf, TankSap> STREAM_CODEC = StreamCodec.composite(
            Sap.STREAM_CODEC, TankSap::type,
            ByteBufCodecs.VAR_INT, TankSap::amount,
            TankSap::new);
}
