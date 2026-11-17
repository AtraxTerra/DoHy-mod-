package fr.atraxterra.dohy.cryo;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * La chambre a poser : elle prend trois blocs sur trois et cinq de haut, centree
 * sur la case visee, la console tournee vers le joueur.
 *
 * <p>La pose passe par {@code useOn}, comme celle de tout bloc : les protections
 * de zone voient les quarante-cinq cases et peuvent toutes les refuser.
 */
public class CryoChamberItem extends BlockItem {

    public CryoChamberItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos base = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (base.getY() < level.getMinY() || base.getY() + 4 > level.getMaxY()) {
            return refuse(player, "Pas la place ici : la chambre fait cinq blocs de haut.");
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = base.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.canBeReplaced() && !(x == 0 && y == 0 && z == 0 && state.canBeReplaced(context))) {
                        return refuse(player, "Il faut un espace libre de 3 x 3 sur 5 de haut.");
                    }
                    if (player != null && !player.mayUseItemAt(pos, Direction.UP, context.getItemInHand())) {
                        return refuse(player, "Tu ne peux pas construire ici.");
                    }
                }
            }
        }
        AABB room = new AABB(base.getX() - 1, base.getY(), base.getZ() - 1, base.getX() + 2, base.getY() + 5,
                base.getZ() + 2);
        if (!level.getEntitiesOfClass(LivingEntity.class, room).isEmpty()) {
            return refuse(player, "Degage la place : quelqu'un se tient dans l'emprise.");
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockState part = getBlock().defaultBlockState().setValue(CryoChamberBlock.FACING, facing);
        for (int y = 0; y < 5; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlock(base.offset(x, y, z), part.setValue(CryoChamberBlock.PX, x + 1)
                            .setValue(CryoChamberBlock.PY, y).setValue(CryoChamberBlock.PZ, z + 1), Block.UPDATE_ALL);
                }
            }
        }
        level.playSound(null, base.above(2), SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
        level.playSound(null, base.above(2), SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.8f, 0.9f);
        ItemStack stack = context.getItemInHand();
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult refuse(Player player, String message) {
        if (player instanceof ServerPlayer server) {
            server.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(message)
                    .withStyle(ChatFormatting.RED)));
        }
        return InteractionResult.FAIL;
    }
}
