package fr.atraxterra.dohy.palamod.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code BlockPaladiumEnderChest} : un coffre de l'End agrandi. Il ouvre le
 * coffre de l'End du joueur, le meme que le vanilla, avec une rangee de plus :
 * 36 cases au lieu de 27.
 *
 * <p>PalaMod agrandissait l'inventaire du coffre de l'End lui-meme, selon des
 * permissions du serveur : 4 rangees par defaut, 5 au plus. Sans permissions,
 * c'est le defaut qui est porte. La 4e rangee est rangee a part, sur le joueur ;
 * les 27 premieres cases sont celles du coffre de l'End vanilla. Les grades de
 * DoHy ajoutent des rangees a tous les coffres de l'Ender, celui-ci compris
 * ({@code grade.GradeEnder}).
 *
 * <p>Casse sans Toucher de soie, il rend 8 obsidiennes, comme le vanilla.
 */
public class PaladiumEnderChestBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<PaladiumEnderChestBlock> CODEC = simpleCodec(PaladiumEnderChestBlock::new);
    public static final int EXTRA_SLOTS = 9;
    /** {@code setBlockBounds(1/16, 0, 1/16, 15/16, 14/16, 15/16)}. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public PaladiumEnderChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above())) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            open(server);
            level.playSound(null, pos, SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.5f,
                    level.getRandom().nextFloat() * 0.1f + 0.9f);
        }
        return InteractionResult.SUCCESS;
    }

    private static void open(ServerPlayer player) {
        // Le meme coffre que les autres, avec au moins sa rangee en plus ; un grade peut en ajouter.
        fr.atraxterra.dohy.grade.GradeEnder.open(player, 4, null,
                Component.translatable("container.palamod.paladium_ender_chest"));
    }
}
