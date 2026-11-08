package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** Les deux blocs d'un arbre de PalaMod : {@code WorldTreeNGen(leaves, wood)}. */
public record PalaTreeConfiguration(BlockState log, BlockState leaves) implements FeatureConfiguration {

    public static final Codec<PalaTreeConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("log").forGetter(PalaTreeConfiguration::log),
            BlockState.CODEC.fieldOf("leaves").forGetter(PalaTreeConfiguration::leaves)
    ).apply(instance, PalaTreeConfiguration::new));
}
