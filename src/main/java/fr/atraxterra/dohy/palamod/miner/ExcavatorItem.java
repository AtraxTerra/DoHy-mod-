package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Excavatrice : une pelle qui creuse en 3x3.
 *
 * <p>{@code ItemExcavator extends ItemSpade} : elle garde tout de la pelle — la
 * vitesse, les chemins de terre — et casse en plus le plan autour du bloc vise.
 * Les materiaux acceptes sont ceux de la pelle (herbe, terre, sable, neige),
 * c'est-a-dire {@code #minecraft:mineable/shovel} en 26.2.
 *
 * <p><b>Non enchantable</b> : {@code getItemEnchantability} rend 0 et
 * {@code isBookEnchantable} faux. Elle n'est donc pas dans
 * {@code #minecraft:shovels}, qui ouvrirait la table et l'enclume.
 */
public class ExcavatorItem extends ShovelItem implements AreaTool {

    public ExcavatorItem(ToolMaterial material, Properties properties) {
        super(material, 1.5f, -3.0f, properties);
    }

    @Override
    public boolean areaActive(ItemStack stack) {
        return true;
    }

    @Override
    public boolean breaksInArea(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        // Les blocs du plan n'usent pas l'outil : un clic, une usure.
        return !AreaMining.isBreakingExtras() && super.mineBlock(stack, level, state, pos, miner);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }
}
