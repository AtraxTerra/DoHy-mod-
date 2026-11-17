package fr.atraxterra.dohy.cryo;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La chambre cryogenique : trois blocs sur trois, cinq de haut, une seule machine.
 *
 * <p>Chaque case de la chambre est ce bloc, qui sait ou il est dans la colonne
 * ({@link #PX}, {@link #PY}, {@link #PZ} : la case du maitre est (1, 0, 1), au
 * centre du socle). Seul le maitre porte la machine ; les trois morceaux du
 * dessin sont poses sur la colonne du centre (voir
 * {@code tools/cryo_chamber_assets.py}), les autres cases n'ont que leur forme.
 *
 * <p>Casser une case demonte toute la chambre : l'objet revient, avec ce qu'elle
 * contenait. Elle resiste aux explosions et ne se pousse pas : rien ne doit
 * pouvoir en laisser une moitie.
 */
public class CryoChamberBlock extends BaseEntityBlock {

    public static final IntegerProperty PX = IntegerProperty.create("px", 0, 2);
    public static final IntegerProperty PY = IntegerProperty.create("py", 0, 4);
    public static final IntegerProperty PZ = IntegerProperty.create("pz", 0, 2);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final MapCodec<CryoChamberBlock> CODEC = simpleCodec(CryoChamberBlock::new);

    /**
     * Les volumes de la chambre, en pixels de modele : le socle, la cuve, le
     * chapiteau, la trappe et l'emetteur. Les formes des cases en sont la
     * decoupe.
     */
    private static final double[][] VOLUMES = {
            {0, 0, 0, 48, 17, 48},
            {4, 17, 4, 44, 62, 44},
            {2, 62, 2, 46, 74, 46},
            {8, 74, 8, 40, 77, 40},
            {20, 77, 20, 28, 80, 28},
    };
    private static final VoxelShape[] SHAPES = new VoxelShape[45];

    static {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 3; z++) {
                    VoxelShape shape = Shapes.empty();
                    for (double[] v : VOLUMES) {
                        double x0 = Math.max(v[0], x * 16) - x * 16;
                        double y0 = Math.max(v[1], y * 16) - y * 16;
                        double z0 = Math.max(v[2], z * 16) - z * 16;
                        double x1 = Math.min(v[3], x * 16 + 16) - x * 16;
                        double y1 = Math.min(v[4], y * 16 + 16) - y * 16;
                        double z1 = Math.min(v[5], z * 16 + 16) - z * 16;
                        if (x1 > x0 && y1 > y0 && z1 > z0) {
                            shape = Shapes.or(shape, Block.box(x0, y0, z0, x1, y1, z1));
                        }
                    }
                    SHAPES[index(x, y, z)] = shape.isEmpty() ? Shapes.block() : shape.optimize();
                }
            }
        }
    }

    public CryoChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PX, 1).setValue(PY, 0).setValue(PZ, 1)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,
            BlockState> builder) {
        builder.add(PX, PY, PZ, FACING);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    private static int index(int x, int y, int z) {
        return (y * 3 + z) * 3 + x;
    }

    /** La case qui porte la machine : au centre du socle. */
    public static boolean isMaster(BlockState state) {
        return state.getValue(PX) == 1 && state.getValue(PY) == 0 && state.getValue(PZ) == 1;
    }

    /** La position du maitre, depuis n'importe quelle case. */
    public static BlockPos master(BlockPos pos, BlockState state) {
        return pos.offset(1 - state.getValue(PX), -state.getValue(PY), 1 - state.getValue(PZ));
    }

    /** La lumiere de la cuve : la colonne du centre, a hauteur du verre. */
    public static int light(BlockState state) {
        int y = state.getValue(PY);
        return state.getValue(PX) == 1 && state.getValue(PZ) == 1 && y >= 1 && y <= 3 ? 8 : 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[index(state.getValue(PX), state.getValue(PY), state.getValue(PZ))];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new CryoChamberBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!isMaster(state)) {
            return null;
        }
        if (level.isClientSide()) {
            return (tickLevel, pos, tickState, entity) -> {
                if (entity instanceof CryoChamberBlockEntity chamber) {
                    chamber.clientTick();
                }
            };
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof CryoChamberBlockEntity chamber) {
                chamber.serverTick();
            }
        };
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            dismantle(level, pos, state, player == null || !player.getAbilities().instabuild);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Demonte la chambre depuis la case {@code pos} : son contenu tombe, l'objet
     * revient s'il le faut, les autres cases disparaissent sans rien lacher.
     */
    public static void dismantle(Level level, BlockPos pos, BlockState state, boolean drop) {
        BlockPos master = master(pos, state);
        if (level.getBlockEntity(master) instanceof CryoChamberBlockEntity chamber) {
            Containers.dropContents(level, master, chamber);
            chamber.clearContent();
        }
        if (drop) {
            Block.popResource(level, pos, new ItemStack(DoHyCryo.CHAMBER_ITEM.get()));
        }
        Block block = state.getBlock();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos part = master.offset(x, y, z);
                    if (!part.equals(pos) && level.getBlockState(part).is(block)) {
                        level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /** Le givre qui tombe dans la cuve quand elle tourne. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!isMaster(state) || !(level.getBlockEntity(pos) instanceof CryoChamberBlockEntity chamber)
                || !chamber.running()) {
            return;
        }
        for (int k = 0; k < 2; k++) {
            double x = pos.getX() + 0.5d + (random.nextDouble() - 0.5d) * 1.9d;
            double z = pos.getZ() + 0.5d + (random.nextDouble() - 0.5d) * 1.9d;
            double y = pos.getY() + 1.2d + random.nextDouble() * 2.6d;
            level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.0d, -0.02d, 0.0d);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5d, pos.getY() + 1.15d, pos.getZ() + 0.5d,
                    (random.nextDouble() - 0.5d) * 0.02d, 0.04d, (random.nextDouble() - 0.5d) * 0.02d);
        }
    }
}
