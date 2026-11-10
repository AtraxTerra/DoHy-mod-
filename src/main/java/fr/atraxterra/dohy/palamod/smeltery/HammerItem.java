package fr.atraxterra.dohy.palamod.smeltery;

import fr.atraxterra.dohy.palamod.miner.AreaMining;
import fr.atraxterra.dohy.palamod.miner.AreaTool;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * Marteau ({@code ItemHammer}) : une pioche qui casse en 3 x 3, neuf fois plus
 * lente ({@code getDigSpeed / 9}). Celui en paladium prend les modificateurs du
 * grinder : vitesse ({@code x (niveau + 1)}), fortune, fusion
 * ({@link SmelteryEvents}).
 *
 * <p><b>Ecart, a la demande du joueur</b> : PalaMod ne l'enchantait pas. Il
 * s'enchante ici comme une pioche ({@code #minecraft:pickaxes}) ; la Fortune du
 * grinder s'ajoute a celle de la table sans la baisser ({@code ItemStack.enchant}
 * garde le plus haut niveau).
 */
public class HammerItem extends Item implements AreaTool, ModifiableTool {

    /** {@code getDigSpeed(...) / 9.0F}. */
    private static final float SLOWDOWN = 9.0f;

    private final boolean modifiable;

    public HammerItem(boolean modifiable, Properties properties) {
        super(properties);
        this.modifiable = modifiable;
    }

    public boolean modifiable() {
        return modifiable;
    }

    @Override
    public GrinderModifier.ToolKind kind() {
        return GrinderModifier.ToolKind.HAMMER;
    }

    @Override
    public int baseSlots() {
        return 3;
    }

    @Override
    public boolean areaActive(ItemStack stack) {
        return true;
    }

    @Override
    public boolean breaksInArea(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float speed = super.getDestroySpeed(stack, state) / SLOWDOWN;
        return speed * (1 + PalaSmeltery.modifiers(stack).level(GrinderModifier.SPEED));
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        // Les blocs du plan n'usent pas l'outil : un clic, une usure.
        return !AreaMining.isBreakingExtras() && super.mineBlock(stack, level, state, pos, miner);
    }



    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (modifiable) {
            PalaSmeltery.describe(stack, this, tooltip);
        }
    }
}
