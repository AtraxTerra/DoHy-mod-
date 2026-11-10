package fr.atraxterra.dohy.palamod.defense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Le reservoir du pistolet a liquide : un fluide et un nombre de seaux.
 *
 * @param fluid  l'identifiant du fluide source, vide si le reservoir l'est
 * @param amount les seaux en reserve
 */
public record PistolLoad(String fluid, int amount) {

    public static final PistolLoad EMPTY = new PistolLoad("", 0);

    public static final Codec<PistolLoad> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("fluid").forGetter(PistolLoad::fluid),
            Codec.INT.fieldOf("amount").forGetter(PistolLoad::amount)
    ).apply(instance, PistolLoad::new));

    public static final StreamCodec<ByteBuf, PistolLoad> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PistolLoad::fluid,
            ByteBufCodecs.VAR_INT, PistolLoad::amount,
            PistolLoad::new);

    public boolean isEmpty() {
        return amount <= 0 || fluid.isEmpty();
    }

    public Fluid fluidType() {
        Identifier id = Identifier.tryParse(fluid);
        return id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(id);
    }

    public PistolLoad plus(Fluid source) {
        return new PistolLoad(BuiltInRegistries.FLUID.getKey(source).toString(), (isEmpty() ? 0 : amount) + 1);
    }

    public PistolLoad minus() {
        return amount <= 1 ? EMPTY : new PistolLoad(fluid, amount - 1);
    }
}
