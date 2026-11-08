package fr.atraxterra.dohy.palamod.godpickaxe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Ce que la pioche des dieux retient : {@code god_pickaxe_xp} et la liste
 * {@code pickaxe_upgrades} du NBT de PalaMod.
 *
 * <p>La liste se reduit a un booleen : la seule amelioration qui fasse quelque
 * chose, la fusion automatique, n'a qu'un niveau. L'autre, « big hole », n'est
 * lue par aucun code de PalaMod 10.3.0 et n'a pas de recette ; elle n'est pas
 * portee.
 */
public record GodPickaxeData(double xp, boolean autoSmelt) {

    public static final GodPickaxeData EMPTY = new GodPickaxeData(0.0d, false);

    public static final Codec<GodPickaxeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("xp", 0.0d).forGetter(GodPickaxeData::xp),
            Codec.BOOL.optionalFieldOf("auto_smelt", false).forGetter(GodPickaxeData::autoSmelt)
    ).apply(instance, GodPickaxeData::new));

    public static final StreamCodec<ByteBuf, GodPickaxeData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, GodPickaxeData::xp,
            ByteBufCodecs.BOOL, GodPickaxeData::autoSmelt,
            GodPickaxeData::new);

    public int level() {
        return GodPickaxeLevels.levelFor(xp);
    }

    public GodPickaxeData withXp(double next) {
        return new GodPickaxeData(next, autoSmelt);
    }

    public GodPickaxeData withAutoSmelt() {
        return new GodPickaxeData(xp, true);
    }
}
