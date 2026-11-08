package fr.atraxterra.dohy.palamod.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Coffre en amethyste ou en titane ({@code BlockModdedChest}).
 *
 * <p>Fer, durete 12, resistance 4,8, pioche en fer au moins. Boite de 1/16 a 15/16
 * en largeur et de 0 a 14/16 en hauteur, comme un coffre vanilla. Oriente vers le
 * joueur a la pose. Ne s'ouvre pas sous un bloc plein. Son contenu tombe quand on
 * le casse.
 *
 * <p>Le modele est fige, couvercle ferme : l'original animait un {@code ModelChest}
 * par un rendu special, mais n'appelait jamais l'ouverture — le couvercle ne se
 * soulevait pas. Un modele de bloc fait donc la meme chose sans code de rendu.
 */
public class PalaChestBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public static final MapCodec<PalaChestBlock> AMETHYST_CODEC =
            simpleCodec(properties -> new PalaChestBlock(ChestTier.AMETHYST, properties));
    public static final MapCodec<PalaChestBlock> TITANE_CODEC =
            simpleCodec(properties -> new PalaChestBlock(ChestTier.TITANE, properties));

    private final ChestTier tier;

    public PalaChestBlock(ChestTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    public ChestTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return tier == ChestTier.AMETHYST ? AMETHYST_CODEC : TITANE_CODEC;
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PalaChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        // onBlockActivated : rien si le bloc du dessus est un cube plein.
        BlockPos above = pos.above();
        if (level.getBlockState(above).isRedstoneConductor(level, above)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PalaChestBlockEntity chest) {
            player.openMenu(chest);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PalaChestBlockEntity chest) {
            Containers.dropContents(level, pos, chest);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
