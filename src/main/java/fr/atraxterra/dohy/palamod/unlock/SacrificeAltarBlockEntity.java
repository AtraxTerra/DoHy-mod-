package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ce que porte un autel de sacrifice : une pierre de capture pleine, ou rien. */
public class SacrificeAltarBlockEntity extends BlockEntity {

    private ItemStack offering = ItemStack.EMPTY;

    public SacrificeAltarBlockEntity(BlockPos pos, BlockState state) {
        super(PalaUnlocks.SACRIFICE_ALTAR_TYPE.get(), pos, state);
    }

    public ItemStack offering() {
        return offering;
    }

    void offer(ItemStack stone) {
        offering = stone;
        refresh();
    }

    /** Rend la pierre posee (vide s'il n'y en a pas) et libere l'autel. */
    ItemStack take() {
        ItemStack stone = offering;
        offering = ItemStack.EMPTY;
        refresh();
        return stone;
    }

    /** Le sacrifice : la creature de la pierre est donnee, la pierre part avec elle. */
    void consume() {
        offering = ItemStack.EMPTY;
        refresh();
    }

    private void refresh() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            boolean active = !offering.isEmpty();
            if (state.hasProperty(SacrificeAltarBlock.ACTIVE) && state.getValue(SacrificeAltarBlock.ACTIVE) != active) {
                level.setBlock(worldPosition, state.setValue(SacrificeAltarBlock.ACTIVE, active), 3);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        offering = input.read("Offering", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!offering.isEmpty()) {
            output.store("Offering", ItemStack.CODEC, offering);
        }
    }
}
