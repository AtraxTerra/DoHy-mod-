package fr.atraxterra.dohy.palamod.dimminer;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * L'obsidienne wither ({@code BlockWitheredObsidian},
 * {@code TileEntityWitheredObsidian}), le cadre du portail du Mineur.
 *
 * <p>Elle ne se fabrique pas : un piston renforce wither alimente change
 * l'obsidienne voisine en obsidienne wither. Clic droit avec un <b>bloc de
 * paladium</b> : elle le mange (son de repas), jusqu'a dix ; au dixieme, un
 * eclair et le tonnerre. Elle eclaire d'autant que de blocs mangés. Un feu
 * allume sur un cadre de blocs charges ouvre le portail.
 *
 * <p>Cassee, elle se rend elle-meme, et ses blocs de paladium tant qu'elle
 * n'a pas servi a ouvrir un portail ({@code created}) — d'ou les coins, qui
 * ne touchent pas le portail, rendus seuls apres l'ouverture.
 *
 * <p>Le {@code TileEntity} de l'original devient deux proprietes d'etat :
 * {@link #FEED} (0 a 10) et {@link #CREATED}.
 */
public class WitheredObsidianBlock extends Block {

    public static final int FULL = 10;
    public static final IntegerProperty FEED = IntegerProperty.create("feed", 0, FULL);
    public static final BooleanProperty CREATED = BooleanProperty.create("created");

    public WitheredObsidianBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FEED, 0).setValue(CREATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FEED, CREATED);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PalaBlocks.PALADIUM_BLOCK.get().asItem())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        int feed = state.getValue(FEED);
        if (feed >= FULL || touchesPortal(level, pos)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            stack.consume(1, player);
            feed++;
            server.setBlock(pos, state.setValue(FEED, feed), Block.UPDATE_ALL);
            if (feed >= FULL) {
                server.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 10.0f, 1.0f);
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(server, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    // Un vrai eclair dans l'original ; ici pour l'image, sans feu ni degats.
                    bolt.snapTo(pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d, 0.0f, 0.0f);
                    bolt.setVisualOnly(true);
                    server.addFreshEntity(bolt);
                }
            } else {
                server.playSound(null, pos, SoundEvents.GENERIC_EAT.value(), SoundSource.BLOCKS, 1.0f, 0.5f);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** {@code hasPortal} : un portail du Mineur sur l'une des six faces. */
    static boolean touchesPortal(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).is(PalaDimMiner.MINER_PORTAL.get())) {
                return true;
            }
        }
        return false;
    }

    /** Un feu pose au-dessus : on essaie d'ouvrir le portail au tick suivant. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor,
                                     RandomSource random) {
        if (direction == Direction.UP && neighbor.is(BlockTags.FIRE)) {
            ticks.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    /**
     * Le meme essai quand le feu arrive sans mise a jour de forme (commande,
     * mecanisme) : l'original testait le feu a chaque tick de sa block entity.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   Orientation orientation, boolean moving) {
        if (!level.isClientSide() && level.getBlockState(pos.above()).is(BlockTags.FIRE)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        if (!level.getBlockState(above).is(BlockTags.FIRE)) {
            return;
        }
        MinerPortalShape shape = MinerPortalShape.find(level, above, true);
        if (shape != null) {
            shape.fill();
            level.playSound(null, above, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }
}
