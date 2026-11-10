package fr.atraxterra.dohy.palamod.trixium;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le compte a rebours d'un nuage ({@code TileEntityCloud}).
 *
 * <p><b>Ecart</b> : l'original ne sauvegardait pas sa duree. Un nuage dont le
 * chunk se dechargeait revenait sans compte a rebours et restait pour de bon.
 * Ici, le reste est sauvegarde, et un nuage sans duree connue part a la
 * premiere occasion.
 */
public class CloudBlockEntity extends BlockEntity {

    /** Ticks restants ; -1 tant que la fabrique ne l'a pas fixe. */
    private int remaining = -1;
    /** Ticks vecus sans duree : la pose la fixe au meme tick. */
    private int orphan;

    public CloudBlockEntity(BlockPos pos, BlockState state) {
        super(PalaTrixium.CLOUD_ENTITY.get(), pos, state);
    }

    public void setRemaining(int ticks) {
        remaining = Math.max(1, ticks);
        setChanged();
    }

    public int remaining() {
        return remaining;
    }

    void serverTick() {
        if (level == null) {
            return;
        }
        if (remaining < 0) {
            if (++orphan > 2) {
                level.removeBlock(worldPosition, false);
            }
            return;
        }
        if (--remaining <= 0) {
            level.removeBlock(worldPosition, false);
            return;
        }
        if (remaining % 20 == 0) {
            setChanged();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        remaining = input.getIntOr("Remaining", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Remaining", remaining);
    }
}
