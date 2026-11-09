package fr.atraxterra.dohy.palamod.pvp;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Un spike ({@code BlockSpike}) : une pyramide a degres qui blesse ce qui la
 * touche, comme un cactus.
 *
 * <p>Pose contre la face cliquee, pointe vers l'exterieur. Sa boite de
 * collision est le cube retreci d'un seizieme sur toutes les faces sauf celle
 * qui le porte : une entite qui s'y appuie entre donc dans le bloc, et prend
 * les degats ({@code onEntityCollidedWithBlock}, source « cactus »). 15 points
 * pour le spike en paladium.
 *
 * <p>Seul le spike en paladium est porte : c'est l'ingredient du stick de
 * dommage. Le test de l'arbre technologique de faction
 * ({@code canSpikeDealDamage}) n'a pas d'equivalent ici.
 */
public class SpikeBlock extends Block {

    public static final MapCodec<SpikeBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            com.mojang.serialization.Codec.FLOAT.fieldOf("damage").forGetter(spike -> spike.damage),
            propertiesCodec()).apply(instance, SpikeBlock::new));
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private static final double INSET = 1.0d;
    private static final Map<Direction, VoxelShape> SHAPES = shapes();

    private final float damage;

    public SpikeBlock(float damage, Properties properties) {
        super(properties);
        this.damage = damage;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.UP));
    }

    private static Map<Direction, VoxelShape> shapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            // La face portee est celle opposee a la pointe : elle reste pleine.
            Direction support = facing.getOpposite();
            double minX = support == Direction.WEST ? 0 : INSET;
            double maxX = support == Direction.EAST ? 16 : 16 - INSET;
            double minY = support == Direction.DOWN ? 0 : INSET;
            double maxY = support == Direction.UP ? 16 : 16 - INSET;
            double minZ = support == Direction.NORTH ? 0 : INSET;
            double maxZ = support == Direction.SOUTH ? 16 : 16 - INSET;
            map.put(facing, Block.box(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return map;
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
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
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean intersects) {
        if (level instanceof ServerLevel server) {
            entity.hurtServer(server, server.damageSources().cactus(), damage);
        }
    }
}
