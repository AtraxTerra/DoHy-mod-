package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** La machine a OGM : un clic ouvre l'ecran des croisements ({@link GmoMachineMenu}). */
public class GmoMachineBlock extends Block {

    public GmoMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            GmoMachineMenu.open(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
