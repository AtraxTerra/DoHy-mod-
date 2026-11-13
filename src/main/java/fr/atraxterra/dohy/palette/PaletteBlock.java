package fr.atraxterra.dohy.palette;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La palette d'armement : un plateau de bois ou s'empilent des caisses d'obus,
 * de munitions ou de missiles.
 *
 * <p>Posee contre d'autres palettes, au meme niveau, elle s'assemble avec
 * elles jusqu'a 3 x 3 ({@link PaletteGroups}) : un seul stock, une seule pile
 * de caisses. Un clic avec un obus, une munition ou un missile en main l'y
 * range (accroupi : tout ce que le sac en contient) ; un clic sans, ou avec
 * autre chose, ouvre le stock du groupe.
 *
 * <p>Sa forme suit la pile de caisses de sa case : la palette nue fait 3 px,
 * trois etages de caisses geantes un bloc et demi.
 */
public class PaletteBlock extends BaseEntityBlock {

    public static final MapCodec<PaletteBlock> CODEC = simpleCodec(PaletteBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Une forme par quart de pixel, de la palette nue a 24 px. */
    private static final VoxelShape[] SHAPES = new VoxelShape[24 * 4 + 1];

    public PaletteBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PaletteBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        float top = level.getBlockEntity(pos) instanceof PaletteBlockEntity pallet ? pallet.top() : CrateLayout.PALLET;
        int index = Math.max(0, Math.min(SHAPES.length - 1, Math.round(top * 4)));
        VoxelShape shape = SHAPES[index];
        if (shape == null) {
            shape = Block.box(0, 0, 0, 16, Math.max(1.0d, index / 4.0d), 16);
            SHAPES[index] = shape;
        }
        return shape;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // -- Assemblage ---------------------------------------------------------------------

    /** Une palette posee cherche ses voisines au tick suivant, quand son entite existe. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** Une palette retiree sans etre cassee (machine de Create...) : ses voisines se regroupent. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(pos.relative(side)) instanceof PaletteBlockEntity neighbor) {
                neighbor.requestRegroup();
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof PaletteBlockEntity pallet) {
            pallet.onScheduledTick(level);
        }
    }

    // -- Comparateur : le remplissage du groupe ---------------------------------------------

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof PaletteBlockEntity pallet
                ? AbstractContainerMenu.getRedstoneSignalFromContainer(pallet) : 0;
    }

    // -- Les mains ------------------------------------------------------------------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (PaletteFamily.of(stack) == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(player instanceof ServerPlayer server) || !(level.getBlockEntity(pos) instanceof PaletteBlockEntity pallet)) {
            return InteractionResult.SUCCESS;
        }
        int moved = load(pallet, stack, player);
        if (player.isShiftKeyDown()) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack other = inventory.getItem(slot);
                if (other != stack && PaletteFamily.of(other) != null) {
                    moved += load(pallet, other, player);
                }
            }
        }
        int capacity = pallet.getContainerSize();
        if (moved <= 0) {
            server.sendSystemMessage(Component.literal("Palettes pleines : " + capacity + " piles sur " + capacity + ".")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9f, 0.8f);
        server.sendSystemMessage(Component.literal("Palettes : " + pallet.usedSlots() + " / " + capacity
                + " piles").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.SUCCESS;
    }

    /** Range une pile du joueur ; rend le nombre d'objets ranges. */
    private static int load(PaletteBlockEntity pallet, ItemStack stack, Player player) {
        int before = stack.getCount();
        ItemStack left = pallet.insert(stack);
        int moved = before - left.getCount();
        if (moved > 0 && !player.getAbilities().instabuild) {
            stack.shrink(moved);
        }
        return moved;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof PaletteBlockEntity pallet) {
            PaletteMenu.open(server, pallet);
        }
        return InteractionResult.SUCCESS;
    }
}
