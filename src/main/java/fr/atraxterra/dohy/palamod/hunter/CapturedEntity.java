package fr.atraxterra.dohy.palamod.hunter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Ce que garde une pierre de capture pleine ({@code used}, {@code entity},
 * {@code entityClass}, {@code nbt} dans l'original) : le type de la creature,
 * toutes ses donnees — nom, age, couleur, taille, effets, armure — et son nom
 * pour l'infobulle.
 */
public record CapturedEntity(Identifier type, CompoundTag data, Component name) {

    public static final Codec<CapturedEntity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("type").forGetter(CapturedEntity::type),
            CompoundTag.CODEC.fieldOf("data").forGetter(CapturedEntity::data),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(CapturedEntity::name)
    ).apply(instance, CapturedEntity::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapturedEntity> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, CapturedEntity::type,
            ByteBufCodecs.COMPOUND_TAG, CapturedEntity::data,
            ComponentSerialization.STREAM_CODEC, CapturedEntity::name,
            CapturedEntity::new);
}
