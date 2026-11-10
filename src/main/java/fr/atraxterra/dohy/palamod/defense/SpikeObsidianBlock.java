package fr.atraxterra.dohy.palamod.defense;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Une obsidienne a spike ({@code SpikeObsi}) : detruite, elle laisse a sa place
 * le spike de son metal, pointe vers sa face avant. Le piege se revele quand
 * le mur tombe.
 */
public class SpikeObsidianBlock extends DirectionalObsidianBlock {

    private final Supplier<? extends Block> spike;

    public SpikeObsidianBlock(Supplier<? extends Block> spike, Properties properties) {
        super(ObsidianEffect.SPIKE, properties);
        this.spike = spike;
    }

    public Block spike() {
        return spike.get();
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new SpikeObsidianBlock(spike, properties));
    }
}
