package fr.atraxterra.dohy.palamod.pet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Ce que les clients savent du familier d'un joueur : son espece et s'il est
 * visible, pour le dessiner sur son epaule, et ce qui joue aussi cote client :
 * le vol du deltaplane ({@code glideTime} : « Aviateur », {@code glideSpeed} :
 * « Expert aerien », en fractions) et la vitesse de minage, que le client
 * calcule avant le serveur ({@code mineSpeed} : « Bonne pioche » ;
 * {@code obsidian} : le multiplicateur de « Pioche incassable » tant qu'elle
 * dure, 0 sinon). Le reste ({@link PetData}) ne quitte pas le serveur.
 */
public record PetLook(String kind, boolean visible, float glideTime, float glideSpeed, float mineSpeed,
                      float obsidian) {

    public static final PetLook NONE = new PetLook("", false, 0.0f, 0.0f, 0.0f, 0.0f);

    public static final MapCodec<PetLook> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("kind", "").forGetter(PetLook::kind),
            Codec.BOOL.optionalFieldOf("visible", false).forGetter(PetLook::visible),
            Codec.FLOAT.optionalFieldOf("glide_time", 0.0f).forGetter(PetLook::glideTime),
            Codec.FLOAT.optionalFieldOf("glide_speed", 0.0f).forGetter(PetLook::glideSpeed),
            Codec.FLOAT.optionalFieldOf("mine_speed", 0.0f).forGetter(PetLook::mineSpeed),
            Codec.FLOAT.optionalFieldOf("obsidian", 0.0f).forGetter(PetLook::obsidian)
    ).apply(instance, PetLook::new));

    public static final StreamCodec<ByteBuf, PetLook> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PetLook::kind,
            ByteBufCodecs.BOOL, PetLook::visible,
            ByteBufCodecs.FLOAT, PetLook::glideTime,
            ByteBufCodecs.FLOAT, PetLook::glideSpeed,
            ByteBufCodecs.FLOAT, PetLook::mineSpeed,
            ByteBufCodecs.FLOAT, PetLook::obsidian,
            PetLook::new);

    public Familiar familiar() {
        return Familiar.byId(kind);
    }

    /** A dessiner : un familier, et visible. */
    public boolean shown() {
        return visible && familiar() != null;
    }
}
