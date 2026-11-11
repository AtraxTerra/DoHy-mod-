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
 * black, ou default), son echelle (1 : la taille du modele) et, pour un
 * objet de decor (coffre, jarre, marchand), l'animation qu'il joue. Posee par le serveur a l'apparition, envoyee aux clients
 * qui voient le monstre ; le rendu remplace alors le monstre vanilla par le
 * modele.
 *
 * @param pose vide pour un monstre, qui s'anime d'apres ce qu'il fait. Sinon
 *             le nom d'une animation du modele, jouee en boucle ou tenue sur
 *             sa derniere image selon ce qu'en dit son fichier ;
 *             {@code "open>open_idle"} joue la premiere une fois, puis la
 *             seconde. Changer la pose relance l'animation chez les clients.
 */
public record RpgSkin(String model, String variant, float scale, String pose) {

    public static final RpgSkin NONE = new RpgSkin("", "", 1.0f, "");

    public RpgSkin(String model, String variant, float scale) {
        this(model, variant, scale, "");
    }

    public RpgSkin withPose(String next) {
        return new RpgSkin(model, variant, scale, next);
    }

    public static final MapCodec<RpgSkin> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("model").forGetter(RpgSkin::model),
            Codec.STRING.optionalFieldOf("variant", "default").forGetter(RpgSkin::variant),
            Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(RpgSkin::scale),
            Codec.STRING.optionalFieldOf("pose", "").forGetter(RpgSkin::pose)
    ).apply(instance, RpgSkin::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RpgSkin> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RpgSkin::model,
            ByteBufCodecs.STRING_UTF8, RpgSkin::variant,
            ByteBufCodecs.FLOAT, RpgSkin::scale,
            ByteBufCodecs.STRING_UTF8, RpgSkin::pose,
            RpgSkin::new);

    public boolean isNone() {
        return model.isEmpty();
    }
}
