package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Ce que contient une fiole : une seve et un remplissage en sixiemes, de 1 a 6
 * (le meta de {@code ItemFlask}). Une fiole vide n'a pas ce composant.
 *
 * <p><b>Ecart</b> : dans PalaMod, une fiole versee gardait son type de seve
 * vide ; elle ne s'empilait plus avec les fioles neuves et refusait les buches
 * d'un autre arbre. Ici, vide veut dire vide.
 */
public record FlaskSap(Sap type, int level) {

    public static final int FULL = 6;

    public static final Codec<FlaskSap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Sap.CODEC.fieldOf("type").forGetter(FlaskSap::type),
            Codec.intRange(1, FULL).fieldOf("level").forGetter(FlaskSap::level)
    ).apply(instance, FlaskSap::new));

    public static final StreamCodec<ByteBuf, FlaskSap> STREAM_CODEC = StreamCodec.composite(
            Sap.STREAM_CODEC, FlaskSap::type,
            ByteBufCodecs.VAR_INT, FlaskSap::level,
            FlaskSap::new);

    public int units() {
        return Sap.units(level);
    }

    public boolean isFull() {
        return level >= FULL;
    }
}
