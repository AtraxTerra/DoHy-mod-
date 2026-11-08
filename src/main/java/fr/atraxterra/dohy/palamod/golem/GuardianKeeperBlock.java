package fr.atraxterra.dohy.palamod.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** {@code BlockGuardianKeeper} : ouvre les neuf cases du gardien de qui clique. */
public class GuardianKeeperBlock extends Block {

    public GuardianKeeperBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            GolemKeeper.open(server);
        }
        return InteractionResult.SUCCESS;
    }
}
