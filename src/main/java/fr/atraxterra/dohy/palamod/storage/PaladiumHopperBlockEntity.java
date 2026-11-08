package fr.atraxterra.dohy.palamod.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Le transfert de l'entonnoir en paladium.
 *
 * <p>Releve dans {@code TileEntityPaladiumHopper} :
 * <ul>
 *   <li>toutes les 8 ticks ({@code PMiner.HOPPER_RATE}), s'il n'est pas coupe ;</li>
 *   <li>source : l'inventaire <b>directement au-dessus</b>, et lui seul — aucun
 *       objet au sol n'est aspire ;</li>
 *   <li>destination : l'inventaire que vise l'entonnoir. Si c'est un autre
 *       entonnoir en paladium, on suit la chaine jusqu'au bout : les objets
 *       vont directement au dernier inventaire, les entonnoirs du milieu ne font
 *       rien eux-memes ;</li>
 *   <li>quantite : <b>toute la pile</b> d'un coup, repartie sur plusieurs cases
 *       de destination au besoin.</li>
 * </ul>
 *
 * <p><b>Ecart</b> : l'original se bloquait sur la premiere pile qui ne rentrait
 * pas. Ici on essaie les piles suivantes de la source. Les faces et les
 * restrictions d'insertion de la destination sont celles du jeu
 * ({@code HopperBlockEntity.addItem}).
 */
public class PaladiumHopperBlockEntity extends BlockEntity {

    /** {@code PMiner.HOPPER_RATE}. */
    private static final int PERIOD = 8;

    /** Garde-fou : une chaine d'entonnoirs plus longue ne se suit pas. */
    private static final int MAX_CHAIN = 64;

    private int ticks;

    public PaladiumHopperBlockEntity(BlockPos pos, BlockState state) {
        super(PalaStorage.PALADIUM_HOPPER.get(), pos, state);
    }

    void serverTick(BlockState state) {
        if (level == null || ++ticks % PERIOD != 0 || !state.getValue(PaladiumHopperBlock.ENABLED)) {
            return;
        }
        Container source = HopperBlockEntity.getContainerAt(level, worldPosition.above());
        if (source == null) {
            return;
        }
        Target target = target(state);
        if (target == null) {
            return;
        }

        for (int slot : extractable(source)) {
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = HopperBlockEntity.addItem(source, target.container(), stack.copy(),
                    target.face());
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0) {
                source.removeItem(slot, moved);
                source.setChanged();
                target.container().setChanged();
                return;
            }
        }
    }

    private record Target(Container container, Direction face) {
    }

    /** L'inventaire au bout de la chaine, et la face par laquelle on y entre. */
    private Target target(BlockState state) {
        Set<BlockPos> visited = new HashSet<>();
        BlockPos pos = worldPosition;
        Direction facing = state.getValue(PaladiumHopperBlock.FACING);
        for (int step = 0; step < MAX_CHAIN; step++) {
            visited.add(pos);
            BlockPos next = pos.relative(facing);
            if (visited.contains(next)) {
                return null;
            }
            BlockState nextState = level.getBlockState(next);
            if (nextState.getBlock() instanceof PaladiumHopperBlock) {
                pos = next;
                facing = nextState.getValue(PaladiumHopperBlock.FACING);
                continue;
            }
            Container container = HopperBlockEntity.getContainerAt(level, next);
            return container == null ? null : new Target(container, facing.getOpposite());
        }
        return null;
    }

    /** Les cases que la source laisse prendre par sa face du bas. */
    private static int[] extractable(Container source) {
        if (source instanceof WorldlyContainer worldly) {
            int[] slots = worldly.getSlotsForFace(Direction.DOWN);
            return java.util.Arrays.stream(slots)
                    .filter(slot -> worldly.canTakeItemThroughFace(slot, source.getItem(slot), Direction.DOWN))
                    .toArray();
        }
        return java.util.stream.IntStream.range(0, source.getContainerSize()).toArray();
    }
}
