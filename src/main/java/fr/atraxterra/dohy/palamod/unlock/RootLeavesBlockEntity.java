package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * La racine qui descend ({@code TileEntityRoot}).
 *
 * <p>Releve dans le code : a sa naissance, trois buches sous le feuillage et
 * le bout de la racine dessous ({@code spawnStructure}). Puis, toutes les
 * vingt ticks ({@code canUpdateTimer}), le bloc sous le bout est remplace —
 * sans rien rendre — et le bout descend d'un cran ({@code replaceBlocks}).
 * Un bloc interdit l'arrete ({@code ROOT_BLACKLISTED_BLOCKS}) : le feuillage
 * passe au rouge ({@code setBlocked}).
 *
 * <p>La liste du code ({@code JobsConstants.ROOT_BLACKLISTED_BLOCKS}) : la
 * bedrock et l'obsidienne. Le feuillage rougit donc a la bedrock comme a
 * l'obsidienne : c'est la profondeur de la racine qui dit s'il y a un bunker.
 * Et la racine n'est arretee que tant que l'obstacle est la : chaque seconde
 * le code revoit le bloc du dessous, et repart s'il est parti.
 *
 * <p>Ici, sont interdits : la bedrock et ce qui ne se casse pas, l'obsidienne
 * (et l'obsidienne pleureuse), et tout bloc qui porte des donnees — un coffre,
 * une machine — pour que la racine ne detruise jamais un contenu.
 */
public class RootLeavesBlockEntity extends BlockEntity {

    /** Trois buches, puis le bout ({@code spawnStructure} : y-1 a y-3, puis y-4). */
    static final int LOGS = 3;
    /** Un cran toutes les vingt ticks. */
    private static final int PERIOD = 20;

    /** La hauteur du bout de la racine. */
    private int tip = Integer.MIN_VALUE;
    private boolean blocked;

    public RootLeavesBlockEntity(BlockPos pos, BlockState state) {
        super(PalaUnlocks.ROOT_LEAVES_TYPE.get(), pos, state);
    }

    /** La graine a fini de pousser : le feuillage a sa place, les buches et le bout dessous. */
    static void sprout(ServerLevel level, BlockPos seed) {
        level.setBlock(seed, PalaUnlocks.ROOT_LEAVES.get().defaultBlockState(), 3);
        int placed = 0;
        for (int depth = 1; depth <= LOGS; depth++) {
            BlockPos pos = seed.below(depth);
            if (forbidden(level, pos)) {
                break;
            }
            level.setBlock(pos, PalaUnlocks.ROOT_LOG.get().defaultBlockState(), 3);
            placed = depth;
        }
        BlockPos tipPos = seed.below(placed + 1);
        boolean stuck = placed < LOGS || forbidden(level, tipPos);
        if (!stuck) {
            level.setBlock(tipPos, PalaUnlocks.ROOT_LAST.get().defaultBlockState(), 3);
        }
        if (level.getBlockEntity(seed) instanceof RootLeavesBlockEntity leaves) {
            leaves.tip = stuck ? tipPos.getY() + 1 : tipPos.getY();
            leaves.setBlocked(stuck);
        }
        level.playSound(null, seed, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % PERIOD != 0L
                || tip == Integer.MIN_VALUE) {
            return;
        }
        BlockPos current = new BlockPos(worldPosition.getX(), tip, worldPosition.getZ());
        if (!server.getBlockState(current).is(PalaUnlocks.ROOT_LAST.get())) {
            // Le bout a ete casse ou remplace : la racine s'arrete la.
            setBlocked(true);
            return;
        }
        BlockPos next = current.below();
        if (next.getY() < server.getMinY() || forbidden(server, next)) {
            setBlocked(true);
            return;
        }
        if (blocked) {
            // l'obstacle est parti : la racine repart
            setBlocked(false);
        }
        server.setBlock(current, PalaUnlocks.ROOT_INFERIOR.get().defaultBlockState(), 3);
        server.setBlock(next, PalaUnlocks.ROOT_LAST.get().defaultBlockState(), 3);
        tip = next.getY();
        setChanged();
    }

    /** Ce que la racine ne traverse pas. */
    static boolean forbidden(Level level, BlockPos pos) {
        if (pos.getY() < level.getMinY()) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        return state.getDestroySpeed(level, pos) < 0.0f
                || state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
                || state.hasBlockEntity();
    }

    private void setBlocked(boolean value) {
        blocked = value;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(RootLeavesBlock.BLOCKED) && state.getValue(RootLeavesBlock.BLOCKED) != value) {
                level.setBlock(worldPosition, state.setValue(RootLeavesBlock.BLOCKED, value), 3);
            }
        }
    }

    /** Le feuillage casse : les buches et toute la racine dessous s'en vont. */
    static void clearBelow(Level level, BlockPos leaves) {
        BlockPos pos = leaves.below();
        while (pos.getY() >= level.getMinY()) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(PalaUnlocks.ROOT_LOG.get()) && !(state.getBlock() instanceof RootColumnBlock)) {
                return;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            pos = pos.below();
        }
    }

    /** Une racine cassee : ce qui pend dessous, jusqu'au bout, s'en va. */
    static void clearColumn(Level level, BlockPos from) {
        BlockPos pos = from;
        while (pos.getY() >= level.getMinY() && level.getBlockState(pos).getBlock() instanceof RootColumnBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            pos = pos.below();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tip = input.getIntOr("Tip", Integer.MIN_VALUE);
        blocked = input.getBooleanOr("Blocked", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Tip", tip);
        output.putBoolean("Blocked", blocked);
    }
}
