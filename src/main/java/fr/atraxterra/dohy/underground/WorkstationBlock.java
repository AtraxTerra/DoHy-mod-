package fr.atraxterra.dohy.underground;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Les postes de travail de l'economie souterraine (lot 24c) : le laboratoire clandestin, la
 * table de conditionnement et l'etabli d'armurier. Un clic ouvre leur ecran, ou chaque recette
 * puise dans l'inventaire du joueur et y rend le produit ({@link WorkstationMenus}).
 *
 * <p>Ce sont des modeles en relief (meuble, plateau, verrerie, balance, etau...) : leur forme
 * suit ces pieces, tournee comme le poste ({@link TurnedShapes}).
 */
public class WorkstationBlock extends Block {

    public enum Kind {
        /** Feuilles et bidons en produits finis. */
        LAB,
        /** Produits en sachets, sachets en colis. */
        PACKAGING,
        /** Pieces et fer en caisses scellees. */
        ARMS
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final MapCodec<WorkstationBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(WorkstationBlock::kind)
    ).apply(instance, WorkstationBlock::new));

    /** Les formes de chaque poste, tourne vers le nord, en pixels : celles des pieces de son modele. */
    private static final Map<Kind, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(Map.of(
            Kind.LAB, TurnedShapes.of(
                    new double[]{0, 0, 0, 16, 12, 16},
                    new double[]{0, 12, 14, 16, 16, 16},
                    new double[]{2, 12, 3, 7, 16, 8},
                    new double[]{8, 12, 4, 11, 16, 7},
                    new double[]{11.5, 12, 2, 15, 15, 4.5},
                    new double[]{11.5, 12, 9, 14.5, 16, 12}),
            Kind.PACKAGING, TurnedShapes.of(
                    new double[]{0, 12, 0, 16, 14, 16},
                    new double[]{1, 0, 1, 3, 12, 3},
                    new double[]{13, 0, 1, 15, 12, 3},
                    new double[]{1, 0, 13, 3, 12, 15},
                    new double[]{13, 0, 13, 15, 12, 15},
                    new double[]{1.5, 3, 1.5, 14.5, 4, 14.5},
                    new double[]{2, 14, 2, 7, 15.5, 7},
                    new double[]{9, 14, 2, 15, 16, 5.5},
                    new double[]{3, 14, 9, 8, 16, 14},
                    new double[]{10.5, 14, 9.5, 13.5, 15, 12.5}),
            Kind.ARMS, TurnedShapes.of(
                    new double[]{0, 12, 0, 16, 14, 16},
                    new double[]{0, 0, 1, 7, 12, 15},
                    new double[]{13, 0, 1, 15, 12, 3},
                    new double[]{13, 0, 13, 15, 12, 15},
                    new double[]{7, 4, 2, 15, 5, 14},
                    new double[]{10, 14, 1.5, 14, 16, 5.5},
                    new double[]{1.5, 14, 8.5, 7.5, 16, 14},
                    new double[]{8.5, 14, 9, 12.5, 15, 13})));

    private final Kind kind;

    public WorkstationBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(kind).get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            WorkstationMenus.of(kind).openAt(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
