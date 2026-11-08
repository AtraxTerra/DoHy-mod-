package fr.atraxterra.dohy.palamod.hunter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/** Une pile de creatures nees d'un spawner customise : combien, et de quel spawner. */
public record MobStack(int count, BlockPos origin) {

    public static final MapCodec<MobStack> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("count").forGetter(MobStack::count),
            BlockPos.CODEC.fieldOf("origin").forGetter(MobStack::origin)
    ).apply(instance, MobStack::new));
}
