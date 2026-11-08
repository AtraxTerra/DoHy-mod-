package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Un outil qui casse un plan de 3x3 autour du bloc vise.
 *
 * <p>Deux familles dans PalaMod : les excavatrices ({@code ItemExcavator}, pelles)
 * et la pioche en endium ({@code BaseItemPickaxe} quand le materiau est
 * l'endium). Les deux appellent la meme boucle de {@code ToolHandler}, avec le
 * meme plan et le meme filtre de durete ; seule change la famille de blocs
 * acceptee. {@link AreaMining} porte la boucle, chaque outil dit ce qu'il prend.
 */
public interface AreaTool {

    /** Faux quand l'outil ne doit plus casser en zone (pioche en endium a bout). */
    boolean areaActive(ItemStack stack);

    /** Vrai si ce bloc fait partie de la famille que l'outil casse en zone. */
    boolean breaksInArea(BlockState state);
}
