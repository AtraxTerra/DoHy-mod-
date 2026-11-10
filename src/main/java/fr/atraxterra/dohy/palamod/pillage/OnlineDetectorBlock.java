package fr.atraxterra.dohy.palamod.pillage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;

/**
 * Le detecteur de presence ({@code BlockOnlineDetector}).
 *
 * <p>Sur Paladium, on y tapait le nom d'une faction, et il s'allumait — avec
 * un signal de redstone — quand l'un de ses membres etait connecte : de quoi
 * savoir si une base est gardee avant d'y entrer. Seul en jeu, il n'y a
 * personne d'autre a guetter : il s'allume quand un <b>monstre</b> rode a
 * seize blocs. Pose a l'entree d'une base, il previent qu'elle est gardee ;
 * pose chez soi, il previent qu'on vient.
 *
 * <p>Un tic programme par seconde, sans entite de bloc.
 */
public class OnlineDetectorBlock extends Block {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<OnlineDetectorBlock> CODEC = simpleCodec(OnlineDetectorBlock::new);

    static final double RANGE = 16.0d;
    private static final int PERIOD = 20;

    public OnlineDetectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(state.getBlock())) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean watched = !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(RANGE),
                entity -> entity instanceof Enemy && entity.isAlive()).isEmpty();
        if (watched != state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, watched), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, PERIOD);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(LIT) ? 15 : 0;
    }
}
