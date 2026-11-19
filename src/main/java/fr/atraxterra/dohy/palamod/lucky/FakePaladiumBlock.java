package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Le faux bloc de paladium de « Ouaiche t'es relou » ({@code BlockFakePaladium}
 * et {@code EventsManager.onBreakPalaRelou}).
 *
 * <p>Casse, il ne cede pas : il saute a la surface, a 21 blocs au plus, et une
 * trainee de poussiere rouge montre ou. Quinze fois. A la seizieme, il se casse
 * pour de bon et rend un vrai bloc de paladium. La metadonnee de PalaMod comptait
 * les sauts : ici, la propriete {@code hits}.
 */
public class FakePaladiumBlock extends Block {

    public static final IntegerProperty HITS = IntegerProperty.create("hits", 0, 15);
    /** {@code blockMetadata < 15} : quinze sauts avant de ceder. */
    static final int JUMPS = 15;
    private static final int RANGE = 21;
    private static final int TRIES = 50;

    /** La couleur de la trainee : le rouge du paladium, ou le vert du paladium vert. */
    private final int dust;

    public FakePaladiumBlock(Properties properties) {
        this(0xEF3926, properties);
    }

    public FakePaladiumBlock(int dust, Properties properties) {
        super(properties);
        this.dust = dust;
        registerDefaultState(stateDefinition.any().setValue(HITS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HITS);
    }

    /** Le bloc a-t-il encore des sauts en reserve ? */
    static boolean jumps(BlockState state) {
        return state.getValue(HITS) < JUMPS;
    }

    /** Le saut : le bloc quitte sa place pour un coin de surface libre. */
    static void jump(ServerPlayer player, ServerLevel level, BlockPos from, BlockState state) {
        RandomSource random = level.getRandom();
        BlockPos to = null;
        for (int attempt = 0; attempt < TRIES && to == null; attempt++) {
            int x = from.getX() - RANGE + random.nextInt(RANGE * 2);
            int z = from.getZ() - RANGE + random.nextInt(RANGE * 2);
            BlockPos candidate = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            if (!candidate.equals(from) && level.getBlockState(candidate).canBeReplaced()
                    && LuckyWorld.canEdit(player, level, candidate)) {
                to = candidate;
            }
        }
        BlockState next = state.setValue(HITS, Math.min(JUMPS, state.getValue(HITS) + 1));
        if (to == null) {
            level.setBlock(from, next, Block.UPDATE_ALL);
            return;
        }
        level.removeBlock(from, false);
        level.setBlock(to, next, Block.UPDATE_ALL);
        // La trainee, un point par bloc parcouru.
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 end = Vec3.atCenterOf(to);
        Vec3 step = end.subtract(start).normalize();
        double length = start.distanceTo(end);
        int color = state.getBlock() instanceof FakePaladiumBlock fake ? fake.dust : 0xEF3926;
        DustParticleOptions dust = new DustParticleOptions(color, 1.2f);
        for (double walked = 0.0d; walked < length; walked += 1.0d) {
            Vec3 at = start.add(step.scale(walked));
            level.sendParticles(dust, at.x, at.y, at.z, 3, 0.1d, 0.1d, 0.1d, 0.0d);
        }
        level.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.BLOCKS,
                0.6f, 1.4f);
    }
}
