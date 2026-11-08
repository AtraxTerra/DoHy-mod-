package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Extracteur de seve ({@code BlockExtractor}). Pose devant une buche d'arbre
 * de PalaMod — il vise le bloc dans la direction du regard du joueur qui le
 * pose — il en tire la seve dans une fiole.
 *
 * <ul>
 *   <li>clic droit avec une fiole <b>vide</b> : il la prend et demarre ;</li>
 *   <li>clic droit : marche / arret, si la cible est une buche a seve ;</li>
 *   <li>accroupi, clic droit : rend la fiole, et l'XP d'extraction si elle a
 *       ete remplie ;</li>
 *   <li>un sixieme de fiole toutes les 11 ticks ; 144 sixiemes par extracteur
 *       (24 fioles, 4 buches), apres quoi il se brise en rendant sa fiole.</li>
 * </ul>
 *
 * <p><b>Ecart</b> : use, l'original se rendait en objet a 0 utilisation, pour
 * la reparation a la palamachine, non portee ; il disparait ici.
 */
public class ExtractorBlock extends BaseEntityBlock {

    public static final MapCodec<ExtractorBlock> CODEC = simpleCodec(ExtractorBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

    /** {@code setBlockBounds} par angle : du centre a la face qui touche la buche. */
    private static final VoxelShape EAST = box(4.8d, 0.0d, 4.8d, 16.0d, 10.4d, 11.2d);
    private static final VoxelShape SOUTH = box(4.8d, 0.0d, 4.8d, 11.2d, 10.4d, 16.0d);
    private static final VoxelShape WEST = box(0.0d, 0.0d, 4.8d, 12.0d, 10.4d, 11.2d);
    private static final VoxelShape NORTH = box(4.8d, 0.0d, 0.0d, 11.2d, 10.4d, 11.2d);

    public ExtractorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST).setValue(ENABLED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENABLED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case NORTH -> NORTH;
            default -> EAST;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExtractorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof ExtractorBlockEntity extractor) {
                extractor.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PalaAlchemist.FLASK.get()) || player.isShiftKeyDown()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExtractorBlockEntity extractor) {
            if (!extractor.insert(player, hand, stack)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExtractorBlockEntity extractor
                && player instanceof ServerPlayer server) {
            if (player.isShiftKeyDown()) {
                extractor.retrieve(server);
            } else {
                extractor.toggle();
            }
        }
        return InteractionResult.SUCCESS;
    }
}
