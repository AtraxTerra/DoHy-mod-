package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le compresseur a cobblestone.
 *
 * <p>Ce n'est pas un objet fabrique : c'est un assemblage. Une void stone entre
 * deux pistons renforces wither, sur le meme axe et au meme niveau
 * ({@code TileEntityVoidStone.updateEntity}, axe X puis axe Z). Releve :
 * <ul>
 *   <li>les deux pistons alimentes, et moins de 64 obsidiennes sorties depuis
 *       la derniere coupure : jusqu'a 64 fois, si la reserve compte au moins 100
 *       pierres, elle en perd 100 et une obsidienne tombe au-dessus ;</li>
 *   <li>les deux pistons presents mais pas tous deux alimentes : le compteur
 *       revient a zero — couper puis remettre le courant rearme la machine.</li>
 * </ul>
 * Une activation peut donc sortir 64 obsidiennes d'un coup. Les 64 sortent ici
 * en une seule pile plutot qu'en 64 entites : le resultat au sol est le meme,
 * le serveur a 63 entites de moins a traiter.
 */
public class VoidStoneBlockEntity extends BlockEntity {

    /** {@code stone >= 100}, puis {@code stone -= 100}. */
    private static final int STONE_PER_OBSIDIAN = 100;

    /** {@code dropped < 64} et la boucle de 64. */
    private static final int MAX_PER_ACTIVATION = 64;

    /** {@code setPickupDelay(10)}. */
    private static final int PICKUP_DELAY = 10;

    private int stone;
    private int dropped;

    public VoidStoneBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMiner.VOID_STONE.get(), pos, state);
    }

    public int stone() {
        return stone;
    }

    public void setStone(int amount) {
        stone = Math.max(0, amount);
        setChanged();
    }

    void serverTick() {
        check(Direction.EAST, Direction.WEST);
        check(Direction.SOUTH, Direction.NORTH);
    }

    private void check(Direction one, Direction other) {
        if (level == null) {
            return;
        }
        BlockPos first = worldPosition.relative(one);
        BlockPos second = worldPosition.relative(other);
        if (!isPiston(first) || !isPiston(second)) {
            return;
        }
        if (!level.hasNeighborSignal(first) || !level.hasNeighborSignal(second)) {
            dropped = 0;
            return;
        }
        if (dropped >= MAX_PER_ACTIVATION) {
            return;
        }

        int produced = 0;
        for (int attempt = 0; attempt < MAX_PER_ACTIVATION && dropped < MAX_PER_ACTIVATION; attempt++) {
            if (stone < STONE_PER_OBSIDIAN) {
                break;
            }
            stone -= STONE_PER_OBSIDIAN;
            dropped++;
            produced++;
        }
        if (produced == 0) {
            return;
        }
        ItemEntity item = new ItemEntity(level, worldPosition.getX() + 0.5d, worldPosition.getY() + 1.0d,
                worldPosition.getZ() + 0.5d, new ItemStack(Items.OBSIDIAN, produced));
        item.setPickUpDelay(PICKUP_DELAY);
        level.addFreshEntity(item);
        setChanged();
    }

    private boolean isPiston(BlockPos pos) {
        return level.getBlockState(pos).is(PalaMiner.WITHERED_REINFORCED_PISTON.get());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stone = input.getIntOr("Stone", 0);
        dropped = input.getIntOr("Dropped", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Stone", stone);
        output.putInt("Dropped", dropped);
    }
}
