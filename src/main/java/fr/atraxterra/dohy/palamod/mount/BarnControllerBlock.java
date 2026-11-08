package fr.atraxterra.dohy.palamod.mount;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le gestionnaire d'enclos ({@code BlockBarnController}) : un pupitre pose sur
 * le foin. Le clic droit verifie l'enclos ({@link BarnStructure}) a chaque
 * fois, puis ouvre la liste des montures.
 */
public class BarnControllerBlock extends BaseEntityBlock {

    public static final MapCodec<BarnControllerBlock> CODEC = simpleCodec(BarnControllerBlock::new);
    /** Le socle et le pupitre du modele. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 8, 16),
            Block.box(2, 8, 5, 14, 15.5, 14));

    public BarnControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarnControllerBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof BarnControllerBlockEntity barn) {
                barn.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof BarnControllerBlockEntity barn) {
            BarnStructure.Result result = barn.validate();
            if (!result.valid()) {
                MountTexts.error(server, "structure_invalid");
                server.sendSystemMessage(result.error().copy().withStyle(net.minecraft.ChatFormatting.RED));
            } else {
                MountMenus.openBarn(server, barn);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
