package fr.atraxterra.dohy.palamod.reserve;

import fr.atraxterra.dohy.faction.reserve.GoldReserves;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le coffre-fort de faction. Mains nues, un clic droit reconnait la reserve
 * d'or dont il fait partie et l'ouvre ; un bloc en main, il se pose comme
 * d'habitude (pour batir la reserve contre lui).
 */
public class FactionSafeBlock extends Block {

    public FactionSafeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            GoldReserves.use(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
