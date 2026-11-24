package fr.atraxterra.dohy.agri.machine;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ce que les blocs de commande des grandes machines ({@link BigMachine}) font en
 * commun : se poser deployes s'il y a la place, poser et retirer leurs parties,
 * prendre la boite de leur case. Chaque bloc (silo, machines electriques, machine
 * a OGM, pompe) y renvoie depuis ses propres methodes.
 */
public final class BigMachines {

    /** La machine est deployee : ses parties l'entourent. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private BigMachines() {
    }

    /**
     * L'etat d'une machine qu'on pose : tournee vers le joueur, deployee — ou rien
     * (la pose est refusee) si la place manque.
     */
    public static BlockState placement(BigMachine machine, BlockState state, BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!machine.fits(context.getLevel(), context.getClickedPos(), facing)) {
            if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
                machine.tellNoRoom(context.getPlayer());
            }
            return null;
        }
        return state.setValue(FACING, facing).setValue(FORMED, true);
    }

    /**
     * Une machine deployee vient d'etre posee : ses parties la rejoignent. Si une
     * case s'est remplie entre-temps (pose par commande, par une autre machine), elle
     * reste en un bloc.
     */
    public static void onPlace(BigMachine machine, BlockState state, Level level, BlockPos pos, BlockState oldState) {
        if (level.isClientSide() || oldState.is(state.getBlock()) || !state.getValue(FORMED)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        if (machine.fits(level, pos, facing)) {
            machine.placeParts(level, pos, facing);
        } else {
            level.setBlock(pos, state.setValue(FORMED, false), Block.UPDATE_ALL);
        }
    }

    /** La commande est partie : ses parties aussi, sans rien faire tomber. */
    public static void onRemove(BigMachine machine, BlockState state, ServerLevel level, BlockPos pos) {
        if (state.getValue(FORMED)) {
            machine.removeParts(level, pos, state.getValue(FACING));
        }
    }

    /** La boite de la commande : celle de sa case si elle est deployee, sinon celle du petit modele. */
    public static VoxelShape shape(BigMachine machine, BlockState state, VoxelShape compact) {
        if (!state.getValue(FORMED)) {
            return compact;
        }
        return machine.shape(state.getValue(FACING), machine.controllerX(), machine.controllerY(),
                machine.controllerZ());
    }

    /**
     * Une machine posee avant les grandes machines (un bloc) se deploie au clic, si
     * la place est libre. Rend vrai si elle vient de se deployer.
     */
    public static boolean unfold(BigMachine machine, BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || state.getValue(FORMED)) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        if (!machine.fits(level, pos, facing)) {
            if (player instanceof ServerPlayer server) {
                server.sendSystemMessage(Component.literal("Pour la deployer en grand, libere la place autour : "
                        + machine.width() + " x " + machine.depth() + " au sol, " + machine.height() + " de haut.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }
        level.setBlock(pos, state.setValue(FORMED, true), Block.UPDATE_ALL);
        machine.placeParts(level, pos, facing);
        return true;
    }
}
