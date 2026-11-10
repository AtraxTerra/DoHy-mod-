package fr.atraxterra.dohy.palamod.defense;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Une obsidienne de defense ({@code BlockHardenedObsidian}, {@code ObsiEffect}) :
 * l'obsidienne renforcee et les obsidiennes piegees.
 *
 * <p>Releve au bytecode : durete -1 (on ne la mine pas), resistance 100 en
 * 1.7.10, soit 60 aujourd'hui — la TNT ordinaire ne l'entame pas, il faut les
 * explosifs du pillage (lot 22d). On la retire intacte a l'outil magique
 * ({@link MagicalToolItem}, qui la rend en objet). Un wither la casse.
 *
 * <p>Detruite par une explosion ou par un monstre, une obsidienne piegee
 * declenche son {@link ObsidianEffect} ; retiree a l'outil magique, jamais.
 * Une explosion ne la rend pas en objet : le butin d'un assaut n'est pas les
 * pieges du defenseur.
 */
public class DefenseObsidianBlock extends Block {

    private final ObsidianEffect effect;

    public DefenseObsidianBlock(ObsidianEffect effect, Properties properties) {
        super(properties);
        this.effect = effect;
    }

    public ObsidianEffect effect() {
        return effect;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new DefenseObsidianBlock(effect, properties));
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    /** Le crochet de NeoForge, qui porte l'etat : une obsidienne a spike garde son orientation. */
    @Override
    public void onBlockExploded(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion) {
        super.onBlockExploded(state, level, pos, explosion);
        DefenseEvents.defer(level, pos, state);
    }
}
