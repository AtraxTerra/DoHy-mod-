package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code BlockGuardian} : la tete du golem. Pose sur un T renverse de blocs de
 * paladium — un dessous, un de chaque cote sur la meme rangee — et frappe avec
 * une pierre de gardien : les quatre blocs disparaissent, le golem se leve.
 *
 * <pre>
 *   P G P
 *     P
 * </pre>
 */
public class GuardianBlock extends Block {

    public GuardianBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PalaGolem.GUARDIAN_STONE.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer owner)) {
            return InteractionResult.SUCCESS;
        }
        Direction.Axis axis = structureAxis(level, pos);
        if (axis == null) {
            owner.sendSystemMessage(Component.translatable("message.palamod.golem.structure")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        GuardianGolem golem = PalaGolem.GUARDIAN_GOLEM.get().create(server, EntitySpawnReason.MOB_SUMMONED);
        if (golem == null) {
            return InteractionResult.FAIL;
        }
        Direction side = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        for (BlockPos part : new BlockPos[]{pos, pos.below(), pos.relative(side), pos.relative(side.getOpposite())}) {
            level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        GolemData data = stack.get(PalaGolem.GOLEM_DATA.get());
        if (data != null) {
            golem.load(data);
        }
        golem.setOwner(owner);
        golem.setHealth(golem.getMaxHealth());
        golem.snapTo(pos.getX() + 0.5d, pos.getY() - 1.0d, pos.getZ() + 0.5d, player.getYRot() + 180.0f, 0.0f);
        golem.startLoading();
        server.addFreshEntity(golem);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    /** L'axe des bras du T, ou {@code null} si la structure n'y est pas. */
    static Direction.Axis structureAxis(Level level, BlockPos pos) {
        if (!paladium(level, pos.below())) {
            return null;
        }
        if (paladium(level, pos.east()) && paladium(level, pos.west())) {
            return Direction.Axis.X;
        }
        if (paladium(level, pos.north()) && paladium(level, pos.south())) {
            return Direction.Axis.Z;
        }
        return null;
    }

    private static boolean paladium(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(PalaBlocks.PALADIUM_BLOCK.get());
    }
}
