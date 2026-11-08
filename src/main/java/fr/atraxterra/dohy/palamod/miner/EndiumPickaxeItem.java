package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pioche en endium : 3x3, et elle ne casse jamais.
 *
 * <p>Releve dans {@code BaseItemPickaxe} quand le materiau est l'endium :
 * <ul>
 *   <li>{@code setDamage} plafonne l'usure au maximum, donc la pile n'est jamais
 *       detruite ;</li>
 *   <li>{@code onBlockDestroyed} ne lance le 3x3 que si l'usure est sous le
 *       maximum : a bout, la pioche redevient une pioche simple ;</li>
 *   <li>le plan prend en compte la fortune de l'outil.</li>
 * </ul>
 *
 * <p>En 26.2 une pile est detruite des que l'usure <b>atteint</b> le maximum
 * (en 1.7.10, quand elle le depassait). Le plafond est donc pose un point plus
 * bas : la barre de durabilite montre 1, la pioche mine toujours, et c'est a
 * ce point que le 3x3 s'arrete. La reparer le relance.
 */
public class EndiumPickaxeItem extends Item implements AreaTool {

    public EndiumPickaxeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean areaActive(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage() - 1;
    }

    @Override
    public boolean breaksInArea(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE);
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        super.setDamage(stack, Math.min(damage, stack.getMaxDamage() - 1));
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        return !AreaMining.isBreakingExtras() && super.mineBlock(stack, level, state, pos, miner);
    }
}
