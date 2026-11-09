package fr.atraxterra.dohy.palamod.dimminer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Le cadre du portail du Mineur ({@code BlockMinerPortal$Size}).
 *
 * <p>Le {@code BlockPortal.Size} de 1.7.10, ou l'obsidienne devient de
 * l'obsidienne wither <b>chargee</b> (dix blocs de paladium). Interieur d'air,
 * de feu ou de portail, de 2 a 21 de large et de 3 a 21 de haut ; les coins
 * ne comptent pas. {@code NoCheckSize}, la meme forme sans exiger la charge,
 * dit si un portail ouvert tient encore debout.
 */
public final class MinerPortalShape {

    private static final int MAX = 21;

    private final Level level;
    private final Direction.Axis axis;
    private final Direction right;
    private final Direction left;
    private final boolean charged;
    private BlockPos bottomLeft;
    private int width;
    private int height;

    private MinerPortalShape(Level level, BlockPos start, Direction.Axis axis, boolean charged) {
        this.level = level;
        this.axis = axis;
        this.right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        this.left = right.getOpposite();
        this.charged = charged;

        BlockPos pos = start;
        int floor = Math.max(level.getMinY(), start.getY() - MAX);
        while (pos.getY() > floor && isInterior(level.getBlockState(pos.below()))) {
            pos = pos.below();
        }
        int toLeft = distanceToEdge(pos, left) - 1;
        if (toLeft >= 0) {
            bottomLeft = pos.relative(left, toLeft);
            width = distanceToEdge(bottomLeft, right);
            if (width < 2 || width > MAX) {
                bottomLeft = null;
                width = 0;
            }
        }
        if (bottomLeft != null) {
            height = measureHeight();
        }
    }

    /** La premiere forme valide, axe X puis axe Z ; {@code null} sinon. */
    public static MinerPortalShape find(Level level, BlockPos inside, boolean charged) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            MinerPortalShape shape = new MinerPortalShape(level, inside, axis, charged);
            if (shape.isValid()) {
                return shape;
            }
        }
        return null;
    }

    public static MinerPortalShape find(Level level, BlockPos inside, Direction.Axis axis, boolean charged) {
        MinerPortalShape shape = new MinerPortalShape(level, inside, axis, charged);
        return shape.isValid() ? shape : null;
    }

    public boolean isValid() {
        return bottomLeft != null && width >= 2 && width <= MAX && height >= 3 && height <= MAX;
    }

    /** Tout l'interieur est deja du portail. */
    public boolean isComplete() {
        for (BlockPos cell : interior()) {
            if (!level.getBlockState(cell).is(PalaDimMiner.MINER_PORTAL.get())) {
                return false;
            }
        }
        return true;
    }

    public Direction.Axis axis() {
        return axis;
    }

    private int distanceToEdge(BlockPos pos, Direction direction) {
        int i;
        for (i = 0; i < 22; i++) {
            BlockPos cell = pos.relative(direction, i);
            if (!isInterior(level.getBlockState(cell)) || !isFrame(level, cell.below(), charged)) {
                break;
            }
        }
        return isFrame(level, pos.relative(direction, i), charged) ? i : 0;
    }

    private int measureHeight() {
        int h;
        outer:
        for (h = 0; h < MAX; h++) {
            for (int i = 0; i < width; i++) {
                BlockPos cell = bottomLeft.relative(right, i).above(h);
                if (!isInterior(level.getBlockState(cell))) {
                    break outer;
                }
                if (i == 0 && !isFrame(level, cell.relative(left), charged)) {
                    break outer;
                }
                if (i == width - 1 && !isFrame(level, cell.relative(right), charged)) {
                    break outer;
                }
            }
        }
        for (int i = 0; i < width; i++) {
            if (!isFrame(level, bottomLeft.relative(right, i).above(h), charged)) {
                return 0;
            }
        }
        return h;
    }

    public List<BlockPos> interior() {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            for (int h = 0; h < height; h++) {
                cells.add(bottomLeft.relative(right, i).above(h));
            }
        }
        return cells;
    }

    /**
     * Remplit l'interieur de portail, puis marque {@code created} les blocs du
     * cadre qui le touchent : ceux-la ne rendent plus leur paladium a la casse.
     * Les coins, qui ne le touchent pas, le rendent.
     */
    public List<BlockPos> fill() {
        BlockState portal = PalaDimMiner.MINER_PORTAL.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
        List<BlockPos> cells = interior();
        for (BlockPos cell : cells) {
            level.setBlock(cell, portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (BlockPos cell : cells) {
            for (Direction direction : Direction.values()) {
                BlockPos side = cell.relative(direction);
                BlockState state = level.getBlockState(side);
                if (state.is(PalaDimMiner.WITHERED_OBSIDIAN.get()) && !state.getValue(WitheredObsidianBlock.CREATED)) {
                    level.setBlock(side, state.setValue(WitheredObsidianBlock.CREATED, true), Block.UPDATE_CLIENTS);
                }
            }
        }
        return cells;
    }

    static boolean isInterior(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(PalaDimMiner.MINER_PORTAL.get());
    }

    static boolean isFrame(BlockGetter level, BlockPos pos, boolean charged) {
        BlockState state = level.getBlockState(pos);
        return state.is(PalaDimMiner.WITHERED_OBSIDIAN.get())
                && (!charged || state.getValue(WitheredObsidianBlock.FEED) >= WitheredObsidianBlock.FULL);
    }
}
