package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * L'apparence d'un monstre de donjon : un modele de Paladium
 * ({@code assets/palarpg/entity/<model>}), sa couleur (yellow, orange, red,
 * black, ou default) et son echelle (1 : la taille du modele). Posee par le serveur a l'apparition, envoyee aux clients
 * qui voient le monstre ; le rendu remplace alors le monstre vanilla par le
 * modele.
 */
public record RpgSkin(String model, String variant, float scale) {

    public static final RpgSkin NONE = new RpgSkin("", "", 1.0f);

    public static final MapCodec<RpgSkin> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("model").forGetter(RpgSkin::model),
            Codec.STRING.optionalFieldOf("variant", "default").forGetter(RpgSkin::variant),
            Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(RpgSkin::scale)
    ).apply(instance, RpgSkin::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RpgSkin> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RpgSkin::model,
            ByteBufCodecs.STRING_UTF8, RpgSkin::variant,
            ByteBufCodecs.FLOAT, RpgSkin::scale,
            RpgSkin::new);

    public boolean isNone() {
        return model.isEmpty();
    }
}
